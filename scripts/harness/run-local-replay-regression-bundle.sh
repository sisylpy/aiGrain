#!/usr/bin/env bash
# 本地 Harness Replay 回归：内置 case + PROBE 7 轮故事线
# 用法:
#   ./scripts/harness/run-local-replay-regression-bundle.sh
#   API_BASE=http://localhost:8080/api REPLAY_OUT_DIR=~/Desktop/my-replay ./scripts/harness/run-local-replay-regression-bundle.sh
#
# 前置: 服务已启动；application 中 ai.harness.replay-enabled=true；建议安装 jq（macOS: brew install jq）
set -euo pipefail

: "${API_BASE:=http://localhost:8080/api}"
: "${REPLAY_OUT_DIR:=$HOME/Desktop/aigrain-replay-results}"

URL="${API_BASE%/}/ai/harness/replay"
mkdir -p "$REPLAY_OUT_DIR"

if ! command -v curl >/dev/null 2>&1; then
  echo "需要 curl" >&2
  exit 1
fi
if ! command -v python3 >/dev/null 2>&1; then
  echo "需要 python3（用于生成 JSON）" >&2
  exit 1
fi

summarize_file() {
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

run_one() {
  local out_name="$1"
  local payload="$2"
  local out_path="${REPLAY_OUT_DIR}/${out_name}.json"
  echo ""
  echo ">>> POST ${out_name} -> ${out_path}"
  local http_code
  http_code="$(
    curl -sS -o "$out_path" -w '%{http_code}' \
      -X POST "$URL" \
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
  summarize_file "$out_path" "$out_name"
}

BASE_JSON='{"userId":3,"distributerId":2,"scopeMode":"GROUP","frozenClockDate":"2026-05-17","strictStoreSqlMatch":false}'

# --- 内置 case（不使用 ignoreExpectations）---
run_one "V2_SEMANTIC_MAINLINE_CORE_10" "$(python3 - "$BASE_JSON" <<'PY'
import json, sys
base = json.loads(sys.argv[1])
msgs = [
    "上个月哪个菜毛利率最低？",
    "核桃芽菜西芹毛利怎么样？",
    "AAA 和汀兰餐厅哪个营业额高？",
    "那采购呢？",
    "那出库呢？",
    "AAA 和汀兰餐厅哪个出库金额高？",
    "这个月经营得怎么样？",
    "那上个月呢？",
    "AAA 和汀兰餐厅哪个经营情况好？",
    "这个月营业额多少？",
]
base.update({"caseId": "V2_SEMANTIC_MAINLINE_CORE_10", "messages": msgs})
print(json.dumps(base, ensure_ascii=False))
PY
)"

run_one "BUSINESS_OVERVIEW_MULTI_AGENT_CORE_3" "$(python3 - "$BASE_JSON" <<'PY'
import json, sys
base = json.loads(sys.argv[1])
msgs = ["这个月经营得怎么样？", "那上个月呢？", "AAA 和汀兰餐厅哪个经营情况好？"]
base.update({"caseId": "BUSINESS_OVERVIEW_MULTI_AGENT_CORE_3", "messages": msgs})
print(json.dumps(base, ensure_ascii=False))
PY
)"

run_one "BUSINESS_DIAGNOSIS_V1_CORE_3" "$(python3 - "$BASE_JSON" <<'PY'
import json, sys
base = json.loads(sys.argv[1])
msgs = [
    "这个月哪里有问题？",
    "AAA 门店这个月成本为什么偏高？",
    "AAA 和汀兰餐厅哪个经营情况更好，原因是什么？",
]
base.update({"caseId": "BUSINESS_DIAGNOSIS_V1_CORE_3", "messages": msgs})
print(json.dumps(base, ensure_ascii=False))
PY
)"

run_one "PURCHASE_MULTITURN_1" "$(python3 - "$BASE_JSON" <<'PY'
import json, sys
base = json.loads(sys.argv[1])
msgs = [
    "这个月采购多少钱？",
    "上个月呢？",
    "AAA 呢？",
    "自采购呢？",
    "汀兰餐厅呢？",
    "供货商订货呢？",
    "哪个供货商金额最高？",
]
base.update({"caseId": "PURCHASE_MULTITURN_1", "messages": msgs})
print(json.dumps(base, ensure_ascii=False))
PY
)"

run_one "REVENUE_AGENT_GRAPH_CORE" "$(python3 - "$BASE_JSON" <<'PY'
import json, sys
base = json.loads(sys.argv[1])
msgs = ["这个月营业额多少？", "上个月呢？", "AAA 这个月营业额多少？"]
base.update({"caseId": "REVENUE_AGENT_GRAPH_CORE", "messages": msgs})
print(json.dumps(base, ensure_ascii=False))
PY
)"

run_one "PURCHASE_AGENT_GRAPH_CORE" "$(python3 - "$BASE_JSON" <<'PY'
import json, sys
base = json.loads(sys.argv[1])
msgs = ["这个月采购金额多少？", "上个月呢？", "AAA 这个月采购金额多少？"]
base.update({"caseId": "PURCHASE_AGENT_GRAPH_CORE", "messages": msgs})
print(json.dumps(base, ensure_ascii=False))
PY
)"

run_one "STOCK_REDUCE_AGENT_GRAPH_CORE" "$(python3 - "$BASE_JSON" <<'PY'
import json, sys
base = json.loads(sys.argv[1])
msgs = ["这个月出库金额多少？", "上个月呢？", "AAA 这个月出库金额多少？"]
base.update({"caseId": "STOCK_REDUCE_AGENT_GRAPH_CORE", "messages": msgs})
print(json.dumps(base, ensure_ascii=False))
PY
)"

run_one "DISH_PROFIT_AGENT_GRAPH_CORE" "$(python3 - "$BASE_JSON" <<'PY'
import json, sys
base = json.loads(sys.argv[1])
msgs = [
    "上个月哪个菜毛利率最低？",
    "核桃芽菜西芹毛利怎么样？",
    "这个月哪个菜毛利率最高？",
]
base.update({"caseId": "DISH_PROFIT_AGENT_GRAPH_CORE", "messages": msgs})
print(json.dumps(base, ensure_ascii=False))
PY
)"

# --- PROBE 7 轮：仅本段使用 ignoreExpectations + caseId PROBE ---
run_one "PROBE_STORY_7_MULTITURN" "$(python3 - "$BASE_JSON" <<'PY'
import json, sys
base = json.loads(sys.argv[1])
msgs = [
    "这个月经营得怎么样？",
    "那采购呢？",
    "自采呢？",
    "那出库呢？",
    "哪个门店问题最大？",
    "库房库存呢？",
    "这个月哪个供应商供货金额最高？",
]
base.update({
    "caseId": "PROBE",
    "ignoreExpectations": True,
    "messages": msgs,
})
print(json.dumps(base, ensure_ascii=False))
PY
)"

echo ""
echo "完成。原始响应目录: ${REPLAY_OUT_DIR}"
echo "API: ${URL}"
