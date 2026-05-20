#!/usr/bin/env bash
# 供 scripts/harness/replay-*.sh source；不要单独直接执行。
# 需要调用方已: set -euo pipefail

: "${API_BASE:=http://localhost:8090/api}"
REPLAY_HARNESS_URL="${API_BASE%/}/ai/harness/replay"

# 与 run-local-replay-regression-bundle.sh 一致的默认请求体基底（可被 REPLAY_HARNESS_BASE_JSON 覆盖）
if [[ -z "${REPLAY_HARNESS_BASE_JSON:-}" ]]; then
  REPLAY_HARNESS_BASE_JSON='{"userId":3,"distributerId":2,"scopeMode":"GROUP","frozenClockDate":"2026-05-17","strictStoreSqlMatch":false}'
fi

replay_harness_require_curl_python3() {
  if ! command -v curl >/dev/null 2>&1; then
    echo "需要 curl" >&2
    return 1
  fi
  if ! command -v python3 >/dev/null 2>&1; then
    echo "需要 python3（用于生成 JSON）" >&2
    return 1
  fi
}

replay_harness_summarize_file() {
  local json_file="$1"
  local title="$2"
  echo ""
  echo "────────── 摘要: ${title} ──────────"
  if ! command -v jq >/dev/null 2>&1; then
    echo "(未安装 jq，跳过摘要。完整 JSON 已写入: ${json_file})"
    return 0
  fi
  jq -r '
    if (.code // 0) != 0 then
      "!! API code=\(.code) msg=\(.msg // "null")"
    else
      empty
    end,
    (if .replay == null then
      "!! 响应缺少 replay 字段"
    else
      "caseId=\(.replay.caseId // "null") overallPass=\(.replay.overallPass // "null") exploreProbeReplay=\(.replay.exploreProbeReplay // "null") conversationId=\(.replay.conversationId // "null")"
    end),
    ( .replay.rounds[]? |
      [
        "r\(.roundIndex)",
        (.message | if length > 42 then .[0:42] + "…" else . end),
        "intent=\(.resolvedQueryContextSummary.effectiveIntentCode // "null")",
        "path=\(.resolvedQueryContextSummary.effectivePathCode // "null")",
        "timeSource=\(.resolvedQueryContextSummary.timeSource // .resolvedQueryContextSummary.effectiveTimeWindowSource // "null")",
        "purchaseSource=\(.resolvedQueryContextSummary.purchaseSourceType // "null")",
        "consumed=\(.resolvedQueryContextSummary.consumedAnswerPlans // "null" | if type == "array" then join(",") else tostring end)",
        "revPlan=\(.resolvedQueryContextSummary.revenueAnswerPlanPresent // "null")/\(.resolvedQueryContextSummary.revenueAnswerPlanType // "-")",
        "purPlan=\(.resolvedQueryContextSummary.purchaseAnswerPlanPresent // "null")/\(.resolvedQueryContextSummary.purchaseAnswerPlanType // "-")",
        "stkPlan=\(.resolvedQueryContextSummary.stockReduceAnswerPlanPresent // "null")/\(.resolvedQueryContextSummary.stockReduceAnswerPlanType // "-")",
        "dishPlan=\(.resolvedQueryContextSummary.dishProfitAnswerPlanPresent // "null")/\(.resolvedQueryContextSummary.dishProfitAnswerPlanType // "-")",
        "diagQ=\(.resolvedQueryContextSummary.diagnosisQuestionType // "null")",
        "whUsed=\(.resolvedQueryContextSummary.warehouseStockAgentUsed // "null")",
        "supUsed=\(.resolvedQueryContextSummary.supplierAnalysisAgentUsed // "null")",
        "ansBlank=\(.resolvedQueryContextSummary.finalAnswerTextBlank // "null")"
      ] | join(" | ")
    )
  ' "$json_file" 2>/dev/null || echo "(jq 解析失败: ${json_file})"
}

# 参数: $1=out stem（写入 REPLAY_OUT_DIR/$1.json）, $2=完整 JSON payload 字符串
replay_harness_run_one() {
  local out_name="$1"
  local payload="$2"
  local out_path="${REPLAY_OUT_DIR}/${out_name}.json"
  echo ""
  echo ">>> POST ${out_name} -> ${out_path}"
  local http_code
  http_code="$(
    curl -sS -o "$out_path" -w '%{http_code}' \
      -X POST "$REPLAY_HARNESS_URL" \
      -H 'Content-Type: application/json; charset=utf-8' \
      -H 'Accept: application/json' \
      --data-binary "$payload"
  )" || true
  if [[ "$http_code" != "200" ]]; then
    echo "!! HTTP ${http_code}（若 404 可能未开启 ai.harness.replay-enabled）"
    head -c 800 "$out_path" 2>/dev/null | cat >&2 || true
    echo >&2
    return 1
  fi
  replay_harness_summarize_file "$out_path" "$out_name"
}

# 参数: 各 case 的 out stem（与 .json 文件名前缀一致，勿传入目录路径）
replay_harness_print_pass_footer() {
  echo ""
  echo "────────── caseId + overallPass + failureCount ──────────"
  if ! command -v jq >/dev/null 2>&1; then
    echo "(未安装 jq，跳过汇总。见目录: ${REPLAY_OUT_DIR})"
    echo "建议: bash scripts/harness/replay-*-matrix-p1.sh（无需 chmod +x）"
    return 0
  fi
  local stem
  for stem in "$@"; do
    local f="${REPLAY_OUT_DIR}/${stem}.json"
    if [[ ! -f "$f" ]]; then
      echo "caseId=${stem} overallPass=null failureCount=(missing file: ${f})"
      continue
    fi
    jq -r '
      .replay as $r |
      ($r.expectationFailures // []) as $f |
      "caseId=\($r.caseId // "'"${stem}"'") overallPass=\(
        if $r.overallPass != null then $r.overallPass
        elif ($f | length) == 0 then true
        else false end
      ) failureCount=\($f | length)"
    ' "$f" 2>/dev/null || echo "caseId=${stem} overallPass=null failureCount=(jq parse error)"
  done
  echo ""
  echo "完成。原始 JSON 目录: ${REPLAY_OUT_DIR}"
  echo "API: ${REPLAY_HARNESS_URL}"
}

# Matrix P1 单 case 脚本统一收尾（与 replay-harness_print_pass_footer 相同，仅别名便于各域脚本调用）
replay_harness_print_matrix_p1_footer() {
  replay_harness_print_pass_footer "$1"
}
