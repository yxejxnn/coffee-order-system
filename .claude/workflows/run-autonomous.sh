#!/bin/sh
# 자율 모드 진입점(런처).
#
# 하는 일: (1) dev에서 워크트리 + feature 브랜치를 만들고, (2) 그 워크트리 안에서
#          claude 세션을 띄워 plan-generate-evaluate-loop 워크플로우를 자율 실행한다.
#
# 왜 런처가 필요한가:
#   claude 세션은 "자기가 켜진 폴더" 안에서만 매끄럽게 동작한다(폴더 샌드박스).
#   그래서 "메인 세션이 바깥 워크트리로 손을 뻗기"(→ 파일 접근마다 권한 프롬프트 폭주)가 아니라,
#   "워크트리에서 새 세션을 띄운다". → 그 세션의 서브에이전트(plan/generate/evaluate)가
#   전부 워크트리 소속이 되어, 프롬프트 없이 워크트리 [plan → generate → evaluate] 로 돈다.
#
# 역할 분리:
#   - 이 스크립트(런처)   = 워크트리 "생성" + 세션 "기동"   (1회성 셋업, 진입점)
#   - pgel.js 의 Guard    = 워크트리인지 "검증"만          (상시 불변식, 안전망)
#
# 사용:  sh .claude/workflows/run-autonomous.sh <작업>
#   예)  sh .claude/workflows/run-autonomous.sh schedule/create
#        → 워크트리 ../<저장소명>-schedule-create, 브랜치 feature/schedule-create 에서 자율 실행

set -e

TASK="${1:?사용법: sh run-autonomous.sh <작업>  (예: schedule/create)}"
SLUG=$(echo "$TASK" | tr '/' '-')     # schedule/create → schedule-create
# 저장소명을 git 루트 폴더명에서 자동 도출 → 하네스가 어느 프로젝트에 얹혀도 재사용 가능
REPO=$(basename "$(git rev-parse --show-toplevel)")
WT="../$REPO-$SLUG"                    # 작업별 워크트리 (repo 밖 sibling)
BR="feature/$SLUG"                    # 작업별 브랜치 (feature/ 접두)
BASE="dev"                            # dev에서 분기

# 1) 워크트리 + 브랜치 생성 (이미 있으면 재사용)
if git worktree list | grep -qF "$REPO-$SLUG"; then
	echo "워크트리 재사용: $WT"
else
	git worktree add "$WT" -b "$BR" "$BASE"
	echo "워크트리 생성: $WT  (브랜치 $BR, $BASE 기준)"
fi

# 2) 워크트리 안에서 claude 세션을 띄워 워크플로우를 자율 실행
#    - cd 로 워크트리에 진입 → 이 새 세션의 작업 폴더 = 워크트리 → 에이전트들이 전부 워크트리 소속
#    - -p (headless): 사람 개입 없이 1회 실행. 격리된 워크트리(+ master는 pre-commit 훅 보호)라 권한은 건너뛴다.
#
# claude는 node 18+ 필요. 이 스크립트를 부른 셸의 node가 낮으면(예: nvm default가 옛 버전) claude가
# 크래시하므로, nvm을 소싱해 node를 default(권장 20+)로 보정한다. (nvm이 없으면 현재 node 그대로 진행)
set +e
export NVM_DIR="${NVM_DIR:-$HOME/.nvm}"
[ -s "$NVM_DIR/nvm.sh" ] && . "$NVM_DIR/nvm.sh" >/dev/null 2>&1
nvm use default >/dev/null 2>&1 || nvm use 20 >/dev/null 2>&1
set -e

# ⚠️ claude는 "정상 실행"까지 확인한다 — 위 보정 후에도 안 되면 안내로 넘어간다.
if command -v claude >/dev/null 2>&1 && claude --version >/dev/null 2>&1; then
	echo "워크트리 세션에서 자율 실행 시작… ($WT)"
	# 실시간 활동로그: 메인 repo(=지금 pwd)에 두어, 당신이 dev 폴더에서 `tail -f .activity.log` 로 관찰
	#   - cd 전에 절대경로로 잡아 export → nested 세션의 훅·에이전트가 이 경로에 append
	ACTIVITY_LOG="$(pwd)/.activity.log"
	export ACTIVITY_LOG
	printf '════════ 자율 실행 시작: %s ════════\n' "$TASK" > "$ACTIVITY_LOG"   # 새로 시작 + 시작 배너
	echo "실시간 관찰:  다른 터미널에서  tail -f .activity.log"
	cd "$WT"
	# print 모드가 긴 오케스트레이션을 끝까지 기다리게 한다.
	#   기본 대기 상한 = 10분(600000ms, v2.1.182~). 워크플로우가 그걸 넘으면 print 세션이 먼저 종료돼
	#   "중간에 멈춤"이 된다. 0 = 상한 해제(무한 대기).
	export CLAUDE_CODE_PRINT_BG_WAIT_CEILING_MS=0
	# 안전망: 무한 대기의 hang 방지용 외곽 상한(30분). timeout/gtimeout 있으면 사용, 없으면 그대로 실행.
	TO=""
	command -v gtimeout >/dev/null 2>&1 && TO="gtimeout 30m"
	[ -z "$TO" ] && command -v timeout >/dev/null 2>&1 && TO="timeout 30m"
	# claude가 non-zero로 끝나도(실패·중단·타임아웃) "끝" 배너는 반드시 찍히도록 set +e 로 감싼다.
	set +e
	$TO claude -p "plan-generate-evaluate-loop 워크플로우를 feature=\"$TASK\" 로 실행해줘. 이미 워크트리 안이다." \
		--dangerously-skip-permissions
	rc=$?
	set -e
	printf '\n════════ 자율 실행 끝 (종료코드 %s) ════════\n결과 확인: cd %s && git log --oneline -5\n' "$rc" "$WT" >> "$ACTIVITY_LOG"
	echo "자율 실행 종료 (exit $rc). 결과 확인: cd $WT && git log --oneline -5"
else
	# claude가 없거나 크래시(주로 구버전 node) → 워크트리는 남기고 원인·해결·수동실행 안내
	cat <<EOF

⚠️ claude CLI가 정상 실행되지 않아 자동 기동을 건너뛴다. 워크트리는 준비됐다.
   현재 node: $(node -v 2>/dev/null || echo '?')  (claude는 node 18+ 필요)
   → nvm이면:  nvm alias default 20 && nvm use 20   (그 뒤 다시 이 스크립트 실행)
   수동 실행:
     cd $WT
     claude
     # 세션에서: plan-generate-evaluate-loop 워크플로우를 feature="$TASK" 로 실행
EOF
fi
