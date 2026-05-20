#!/usr/bin/env bash
# 采购主线 + 供货/商品 drilldown 分层回归（7 case，约数分钟级，替代频繁全量 bundle）
# 用法:
#   ./scripts/harness/replay-purchase-followup-core.sh
#   API_BASE=http://localhost:8090/api REPLAY_OUT_DIR=./out/my-replay ./scripts/harness/replay-purchase-followup-core.sh
#
# 前置: 服务已启动；ai.harness.replay-enabled=true；建议安装 jq
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
: "${API_BASE:=http://localhost:8090/api}"
: "${REPLAY_OUT_DIR:=${REPO_ROOT}/out/replay-purchase-followup-core}"

# shellcheck source=scripts/harness/replay-harness-common.sh
source "${SCRIPT_DIR}/replay-harness-common.sh"

mkdir -p "$REPLAY_OUT_DIR"
replay_harness_require_curl_python3

run_case_payload() {
  local out_name="$1"
  local case_id="$2"
  shift 2
  local payload
  payload="$(python3 -c 'import json,sys; base=json.loads(sys.argv[1]); cid=sys.argv[2]; msgs=sys.argv[3:]; base.update({"caseId":cid,"messages":msgs}); print(json.dumps(base,ensure_ascii=False))' "$REPLAY_HARNESS_BASE_JSON" "$case_id" "$@")"
  replay_harness_run_one "$out_name" "$payload"
}

run_case_payload "PURCHASE_AGENT_GRAPH_CORE" "PURCHASE_AGENT_GRAPH_CORE" \
  "这个月采购金额多少？" "上个月呢？" "AAA 这个月采购金额多少？"

run_case_payload "PURCHASE_MULTITURN_1" "PURCHASE_MULTITURN_1" \
  "这个月采购多少钱？" \
  "上个月呢？" \
  "AAA 呢？" \
  "自采购呢？" \
  "汀兰餐厅呢？" \
  "供货商订货呢？" \
  "哪个供货商金额最高？"

run_case_payload "PURCHASE_SUPPLIER_RANKING_DRILLDOWN_GOODS_UNIT_PRICE_3" \
  "PURCHASE_SUPPLIER_RANKING_DRILLDOWN_GOODS_UNIT_PRICE_3" \
  "这个月哪个供应商供货金额最高" \
  "上个月呢" \
  "采购了哪些商品？单价分别是多少？"

run_case_payload "PURCHASE_GOODS_RANKING_DRILLDOWN_SUPPLIER_UNIT_PRICE_2" \
  "PURCHASE_GOODS_RANKING_DRILLDOWN_SUPPLIER_UNIT_PRICE_2" \
  "这个月采购金额最高的商品是什么？" \
  "这个商品是哪些供应商供的？单价分别是多少？"

run_case_payload "PURCHASE_GOODS_RANKING_SOURCE_BREAKDOWN_2" \
  "PURCHASE_GOODS_RANKING_SOURCE_BREAKDOWN_2" \
  "这个月采购金额最高的商品是什么？" \
  "这个商品自采了多少，供货商订了多少？"

run_case_payload "PURCHASE_SUPPLIER_FACET_GOODS_RANKING_SOURCE_BREAKDOWN_2" \
  "PURCHASE_SUPPLIER_FACET_GOODS_RANKING_SOURCE_BREAKDOWN_2" \
  "供货商供货的商品里，哪个商品的采购金额最大？" \
  "这个商品总共采购多少，其中自采多少、供货商多少？"

run_case_payload "PURCHASE_SUPPLIER_FACET_GOODS_AMOUNT_RANKING_IGNORE_ANCHOR_2" \
  "PURCHASE_SUPPLIER_FACET_GOODS_AMOUNT_RANKING_IGNORE_ANCHOR_2" \
  "这个月商品哪个采购金额最大？" \
  "供货商供货的商品里，哪个商品的采购金额最大？"

run_case_payload "PURCHASE_SUPPLIER_CHANNEL_OVERVIEW_GOODS_DETAIL_2" \
  "PURCHASE_SUPPLIER_CHANNEL_OVERVIEW_GOODS_DETAIL_2" \
  "上个月订货在供货商那里订了多少" \
  "定了什么东西？"

run_case_payload "PURCHASE_SUPPLIER_ANCHOR_THEN_SOURCE_AMOUNT_SUMMARY_2" \
  "PURCHASE_SUPPLIER_ANCHOR_THEN_SOURCE_AMOUNT_SUMMARY_2" \
  "上个月哪个供应商供货金额最高？" \
  "上个月在供货商那里订了多少钱的货"

replay_harness_print_pass_footer \
  PURCHASE_AGENT_GRAPH_CORE \
  PURCHASE_MULTITURN_1 \
  PURCHASE_SUPPLIER_RANKING_DRILLDOWN_GOODS_UNIT_PRICE_3 \
  PURCHASE_GOODS_RANKING_DRILLDOWN_SUPPLIER_UNIT_PRICE_2 \
  PURCHASE_GOODS_RANKING_SOURCE_BREAKDOWN_2 \
  PURCHASE_SUPPLIER_FACET_GOODS_RANKING_SOURCE_BREAKDOWN_2 \
  PURCHASE_SUPPLIER_FACET_GOODS_AMOUNT_RANKING_IGNORE_ANCHOR_2 \
  PURCHASE_SUPPLIER_CHANNEL_OVERVIEW_GOODS_DETAIL_2 \
  PURCHASE_SUPPLIER_ANCHOR_THEN_SOURCE_AMOUNT_SUMMARY_2
