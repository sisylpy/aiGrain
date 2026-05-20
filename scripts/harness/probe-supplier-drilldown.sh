#!/usr/bin/env bash
# D-13.1 供货商排行 → 商品明细：一键 GRAPH_RUN Replay probe（三轮固定话术）
#
# 用法:
#   ./scripts/harness/probe-supplier-drilldown.sh
#   API_BASE=http://localhost:8090/api ./scripts/harness/probe-supplier-drilldown.sh
#
# 前置: 服务已启动；application 中 ai.harness.replay-enabled=true；建议安装 jq
set -euo pipefail

: "${API_BASE:=http://localhost:8090/api}"
URL="${API_BASE%/}/ai/harness/replay"

TS="$(date +%Y%m%d-%H%M%S)"
OUT="${PROBE_SUPPLIER_DRILLDOWN_OUT:-$HOME/Desktop/aigrain-probe-supplier-drilldown-${TS}.json}"

if ! command -v curl >/dev/null 2>&1; then
  echo "需要 curl" >&2
  exit 1
fi
if ! command -v python3 >/dev/null 2>&1; then
  echo "需要 python3" >&2
  exit 1
fi

BASE_JSON='{"userId":3,"distributerId":2,"scopeMode":"GROUP","frozenClockDate":"2026-05-17","strictStoreSqlMatch":false}'

PAYLOAD="$(python3 -c '
import json, sys
base = json.loads(sys.argv[1])
msgs = [
    "这个月哪个供应商供货金额最高",
    "上个月呢",
    "采购了哪些商品？单价分别是多少？",
]
base.update({"caseId": "PURCHASE_SUPPLIER_RANKING_DRILLDOWN_GOODS_UNIT_PRICE_3", "messages": msgs})
print(json.dumps(base, ensure_ascii=False))
' "$BASE_JSON")"

echo ">>> POST ${URL}"
echo ">>> 写入: ${OUT}"

http_code="$(
  curl -sS -o "$OUT" -w '%{http_code}' \
    -X POST "$URL" \
    -H 'Content-Type: application/json; charset=utf-8' \
    -H 'Accept: application/json' \
    --data-binary "$PAYLOAD"
)" || true

if [[ "$http_code" != "200" ]]; then
  echo "!! HTTP ${http_code}" >&2
  head -c 1200 "$OUT" 2>/dev/null | cat >&2 || true
  echo >&2
  exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "未安装 jq：完整 JSON 已写入 ${OUT}"
  exit 0
fi

echo ""
jq -r '
  if (.code // 0) != 0 then
    "!! API code=\(.code) msg=\(.msg // "null")"
  else
    empty
  end
' "$OUT"

echo ""
echo "════════ D-13.1 probe 摘要 ════════"
jq -r '
  "caseId=\(.replay.caseId // "null")",
  "overallPass=\(.replay.overallPass // "null")",
  "conversationId=\(.replay.conversationId // "null")",
  (.replay.rounds[]?
    | "--- round \(.roundIndex) ---",
      "message=\(.message)",
      (.resolvedQueryContextSummary // {}
        | "structuredIntentDetailWire=\(.structuredIntentDetailWire // "null")",
          "purchaseSourceType=\(.purchaseSourceType // "null")",
          "purchaseAnswerPlanType=\(.purchaseAnswerPlanType // .harnessReplayPurchaseAnswerPlanType // "null")",
          "followUpAction=\(.followUpAction // "null")",
          "followUpTargetEntityType=\(.followUpTargetEntityType // "null")",
          "followUpTargetEntityName=\(.followUpTargetEntityName // "null")",
          "followUpDetailWanted=\(.followUpDetailWanted // "null")",
          "resultAnchorsCount=\(.resultAnchorsCount // "null")",
          "prev.resultAnchorsCount=\(.previousTurnSummary.resultAnchorsCount // "null")"
      )
  )
' "$OUT"

echo ""
echo "完整 JSON: ${OUT}"
