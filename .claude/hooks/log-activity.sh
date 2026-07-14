#!/bin/sh
# PreToolUse 훅 — 자율 실행 "실시간 활동로그".
#
# 목적: 자율 실행 중 에이전트가 "어떤 도구로 어떤 파일/명령을 쓰는지"를 한 줄씩 남겨,
#       메인 폴더에서 `tail -f .activity.log` 로 실시간 관찰할 수 있게 한다.
#
# docs/logs/ 와의 차이: 저건 영구 개발이력(커밋), 이건 휘발성 진행관찰(gitignore).
#
# 설계:
#   - 로그 경로는 런처가 export한 $ACTIVITY_LOG (메인 repo의 .activity.log). 없으면 조용히 종료
#     → 자율 실행이 아닌 일반 세션에선 사실상 no-op (오버헤드 최소).
#   - JSON 정밀 파싱 없이 sed로 필드만 추출 (python/jq 불필요).
#   - 항상 exit 0 — 관찰용이라 절대 도구를 막지 않는다.

LOG="${ACTIVITY_LOG:-}"
[ -z "$LOG" ] && exit 0

input=$(cat)
tool=$(printf '%s' "$input" | sed -n 's/.*"tool_name"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
[ -z "$tool" ] && exit 0

# 도구별 의미 있는 필드 하나만 뽑는다 (파일경로 / 명령 / 패턴)
file=$(printf '%s' "$input" | sed -n 's/.*"file_path"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
cmd=$(printf  '%s' "$input" | sed -n 's/.*"command"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
pat=$(printf  '%s' "$input" | sed -n 's/.*"pattern"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')

# 절대경로 → 작업폴더(워크트리) 기준 상대경로로 (긴 접두 제거 + 경로 힌트 유지)
rel="${file#"$PWD"/}"

case "$tool" in
	Read)      line="    · $rel 읽는중" ;;
	Write)     line="    · $rel 작성중" ;;
	Edit)      line="    · $rel 수정중" ;;
	Bash)      line="    · \$ $(printf '%s' "$cmd" | cut -c1-80)" ;;
	Grep|Glob) line="    · 탐색: $pat" ;;
	*)         line="    · $tool" ;;
esac

printf '%s\n' "$line" >> "$LOG" 2>/dev/null
exit 0
