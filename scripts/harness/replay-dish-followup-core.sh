#!/usr/bin/env bash
# 菜品毛利 + 原料下钻分层回归（2 case）
# 用法:
#   ./scripts/harness/replay-dish-followup-core.sh
#   API_BASE=... REPLAY_OUT_DIR=... ./scripts/harness/replay-dish-followup-core.sh
#
# 前置: 服务已启动；ai.harness.replay-enabled=true；建议安装 jq
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
: "${API_BASE:=http://localhost:8090/api}"
: "${REPLAY_OUT_DIR:=${REPO_ROOT}/out/replay-dish-followup-core}"

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

run_case_payload "DISH_PROFIT_AGENT_GRAPH_CORE" "DISH_PROFIT_AGENT_GRAPH_CORE" \
  "上个月哪个菜毛利率最低？" \
  "核桃芽菜西芹毛利怎么样？" \
  "这个月哪个菜毛利率最高？"

run_case_payload "DISH_LOW_MARGIN_DRILLDOWN_INGREDIENT_COST_2" \
  "DISH_LOW_MARGIN_DRILLDOWN_INGREDIENT_COST_2" \
  "上个月哪个菜毛利率最低？" \
  "具体是哪些原料拖累了毛利？"

replay_harness_print_pass_footer \
  DISH_PROFIT_AGENT_GRAPH_CORE \
  DISH_LOW_MARGIN_DRILLDOWN_INGREDIENT_COST_2
