// 오케스트레이션 (완전 자율): Plan → Generate → Evaluate → 루프 → (통과 시)Finalize.
// 휴먼 게이트 없음 — 계획을 사람이 승인하지 않고 바로 구현으로 넘어간다.
//
// 실행 전제: 이 워크플로우는 "워크트리 세션 안에서" 실행된다.
//   진입점은 런처 `run-autonomous.sh` 이며, 런처가 워크트리를 만들고 그 안에서 claude를 띄워 이 워크플로우를 부른다.
//   → 그래서 여기서는 세션의 작업 폴더 = 워크트리다. 모든 경로는 그냥 상대경로로 쓰면 된다(권한 프롬프트 없음).
//
// 역할 분리:
//   - run-autonomous.sh (런처) = 워크트리 "생성" + 세션 "기동"   (1회성 셋업)
//   - 아래 Guard              = 워크트리인지 "검증"만          (안전망: 런처를 안 거치고 메인에서 돌면 중단)
//
// 트레이드오프: 빠름("작업 던지면 결과만"). 대신 "구현 전 검토" 통제점이 없다.
//   남는 안전장치: 테스트(Evaluate)가 correctness gate · 루프 상한(3) · 계획/로그 문서는 남아 사후 검토 가능.
//
// 사용: run-autonomous.sh 가 args로 대상 기능을 넘긴다. 예) { feature: "schedule/create" }

export const meta = {
  name: 'plan-generate-evaluate-loop',
  description: '휴먼 게이트 없이 Plan→Generate→Evaluate→루프→Finalize를 완전 자율로 실행한다 (워크트리 세션 전제).',
  phases: [
    { title: 'Guard', detail: '워크트리 세션인지 검증 (아니면 중단)' },
    { title: 'Plan', detail: '계획 문서 + API/테이블 명세 작성 (승인 없이 바로 진행)' },
    { title: 'Generate', detail: '계획대로 구현 + 로그(시도)' },
    { title: 'Evaluate', detail: './gradlew test + 로그(결과), 실패 시 루프' },
    { title: 'Finalize', detail: '통과 시 feature 브랜치 커밋 후 dev로 merge (PR 대체)' },
  ],
}

const feature = (args && args.feature) || 'sample/feature'
const slug = feature.replace('/', '-')        // schedule/create → schedule-create
const action = feature.split('/').pop()       // schedule/create → create
const ongoing = `docs/dev/ongoing/${slug}.md`
const logPath = `docs/logs/${feature}/001-${action}.md`
const MAX_ATTEMPTS = 3

// 실시간 활동로그(선택): 런처가 $ACTIVITY_LOG 를 export하면 각 단계 "시작"을 거기에 남긴다.
// (도구별 파일 활동은 PreToolUse 훅 log-activity.sh 가 별도로 남긴다 → 마커 사이에 끼어 보임)
const markStart = (who) =>
  `[활동로그] 맨 먼저 \`[ -n "$ACTIVITY_LOG" ] && echo "▶ ${who}" >> "$ACTIVITY_LOG"\` 를 실행하라 (환경변수 없으면 아무 일도 안 함). 그다음 아래 작업을 하라.\n`

// --- 가드: "워크트리 세션인지" 검증만 한다 (생성은 런처 run-autonomous.sh 몫). ---
// 런처를 거치면 이미 워크트리 안이므로 통과한다. 메인에서 직접 돌리면 중단시켜 런처로 안내한다.
phase('Guard')
const loc = await agent(
  `현재 작업트리가 linked worktree인지 판단하라.\n` +
  `\`git rev-parse --git-dir\`와 \`git rev-parse --git-common-dir\`를 실행해 두 경로가 다르면 워크트리(inWorktree=true), 같으면 메인 작업트리(false)다.\n` +
  `JSON만 반환.`,
  { label: 'guard:check', phase: 'Guard',
    schema: { type: 'object', properties: { inWorktree: { type: 'boolean' } }, required: ['inWorktree'] } }
)
if (!loc || loc.inWorktree !== true) {
  log(`❌ 중단: 자율 모드는 워크트리 세션에서만 실행한다. 메인에서 직접 부르지 말고 런처로 실행하라:\n` +
      `   sh .claude/workflows/run-autonomous.sh ${feature}`)
  return { aborted: true, reason: 'not in a git worktree — use run-autonomous.sh', feature }
}
log('✅ 워크트리 세션 확인 — 진행한다.')

// --- Plan (한 번, 게이트 없이 바로 다음으로) ---
phase('Plan')
await agent(
  markStart('Plan 에이전트 시작') +
  `당신은 Plan 단계다. docs/workflow/plan-guide.md 와 docs/dev-doc-guide.md 를 따른다.\n` +
  `대상: ${feature}. ${ongoing} 계획 문서를 만들고, 필요하면 docs/api/·docs/db/ 명세 초안도 작성한다.\n` +
  `무엇을·어떻게·통과 기준을 명확히 하라. 코드는 작성하지 마라 (구현은 Generate 몫).\n` +
  `⚠️ 이 워크플로우는 휴먼 게이트가 없다 — 승인을 기다리지 말고 계획만 완성하고 종료하라.`,
  { label: `plan:${feature}`, phase: 'Plan' }
)

// --- Generate → Evaluate 루프 (순서는 코드로 고정 → 건너뛰기 불가) ---
let passed = false
let lastReason = ''

for (let attempt = 1; attempt <= MAX_ATTEMPTS && !passed; attempt++) {
  phase('Generate')
  await agent(
    markStart(`Generate 에이전트 시작 (${attempt}회차)`) +
    `당신은 Generate 단계다. docs/workflow/generate-guide.md 와 docs/code-convention.md 를 따른다.\n` +
    `${ongoing} 계획대로만 구현한다 (범위 확장 금지).\n` +
    `재시도(${attempt}회차)면 ${logPath} 의 이전 Attempt를 먼저 읽어 같은 접근을 반복하지 마라.\n` +
    `구현 후 ./gradlew compileJava 로 컴파일을 확인하고, 이번 시도를 ${logPath} 에 '시도'로 기록하라.`,
    { label: `generate:attempt-${attempt}`, phase: 'Generate' }
  )

  phase('Evaluate')
  const result = await agent(
    markStart(`Evaluate 에이전트 시작 (${attempt}회차)`) +
    `당신은 Evaluate 단계다. docs/workflow/evaluate-guide.md 를 따른다.\n` +
    `1) ./gradlew test 로 검증한다 (실패가 로직 문제인지 DB 미가동인지 구분).\n` +
    `2) 결과물이 계획대로인지, code-convention·정책을 지켰는지 확인한다.\n` +
    `3) generator의 '시도'에 이어 '결과·원인·증거'를 ${logPath} 에 append한다.\n` +
    `4) 통과 시 design.md 갱신 + ongoing 문서를 changes/00X로 채번 이동한다.\n` +
    `통과 여부를 JSON으로 반환하라.`,
    {
      label: `evaluate:attempt-${attempt}`,
      phase: 'Evaluate',
      schema: {
        type: 'object',
        properties: {
          passed: { type: 'boolean' },
          reason: { type: 'string' },
        },
        required: ['passed'],
      },
    }
  )

  passed = result && result.passed === true
  lastReason = (result && result.reason) || ''
  log(`Attempt ${attempt}: ${passed ? '✅ 통과' : '❌ 실패 — ' + lastReason}`)
}

if (!passed) {
  // 미통과 → 커밋/merge하지 않고 워크트리에 남긴다 (사람이 검토 후 판단).
  log(`${MAX_ATTEMPTS}회 시도 후에도 미통과 (${lastReason}). 커밋/merge하지 않는다. 워크트리에서 검토하라.`)
  return { feature, passed, attempts: MAX_ATTEMPTS, lastReason, merged: false }
}

// --- Finalize: 통과했을 때만 → feature 브랜치 커밋 후 dev로 merge (PR 대체, GitHub 연동 생략) ---
phase('Finalize')
const fin = await agent(
  markStart('Finalize 에이전트 시작 (커밋·merge)') +
  `Evaluate가 통과했다. 자율 모드 마무리로 결과를 커밋하고 dev에 merge하라.\n` +
  `1) 현재 워크트리(브랜치 feature/${slug})에서 커밋: \`git add -A && git commit -m "feat: ${feature} (자율 모드)"\`\n` +
  `2) dev로 merge: 메인 작업트리 경로를 \`git worktree list --porcelain\`의 **첫 번째(메인) worktree**로 찾고, 그 폴더에서\n` +
  `   \`git switch dev\`(이미 dev면 무방) 후 \`git merge --no-ff feature/${slug} -m "merge: ${feature} (자율 모드)"\` 를 실행한다.\n` +
  `   - pre-commit 훅은 master/main만 막으므로 dev merge는 허용된다.\n` +
  `   - merge 충돌이 나면 \`git merge --abort\` 하고 merged=false로 사실대로 보고하라 (강제 진행 금지).\n` +
  `3) 커밋 여부·merge 여부·비고를 JSON으로 반환하라.`,
  { label: `finalize:${feature}`, phase: 'Finalize',
    schema: { type: 'object', properties: { committed: { type: 'boolean' }, merged: { type: 'boolean' }, note: { type: 'string' } }, required: ['committed', 'merged'] } }
)

if (fin && fin.merged) {
  log(`✅ dev에 merge 완료 — ${feature}.`)
} else {
  log(`⚠️ 커밋은 됐으나 merge 미완: ${(fin && fin.note) || '원인 미상'}. 워크트리에서 수동 확인.`)
}

return { feature, passed, attempts: MAX_ATTEMPTS, committed: !!(fin && fin.committed), merged: !!(fin && fin.merged) }

// 참고:
// - 지금은 agent()가 기본 워크플로우 서브에이전트를 쓴다. planner/generator/evaluator가 로드되면
//   agent(..., { agentType: 'planner' }) 처럼 명명 서브에이전트로 바꿀 수 있다.
// - 반복 실패 시 자동 re-plan을 넣고 싶으면, 루프 안에서 접근 변경이 필요할 때 planner를 다시 호출하도록 확장.
