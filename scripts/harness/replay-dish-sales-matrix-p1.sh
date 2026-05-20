#!/usr/bin/env bash
# 菜品销量 Matrix P1 回归（DISH_SALES_MATRIX_P1）
# 用法（无需 chmod +x）:
#   bash scripts/harness/replay-dish-sales-matrix-p1.sh
#   API_BASE=... REPLAY_OUT_DIR=... bash scripts/harness/replay-dish-sales-matrix-p1.sh
#
# 前置: 服务已启动；ai.harness.replay-enabled=true；建议安装 jq
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
: "${API_BASE:=http://localhost:8090/api}"
: "${REPLAY_OUT_DIR:=${REPO_ROOT}/out/replay-dish-sales-matrix-p1}"

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

run_case_payload "DISH_SALES_MATRIX_P1" "DISH_SALES_MATRIX_P1" \
  "这个月哪个菜卖得最好？" \
  "哪个菜销量最高？" \
  "哪个菜销量最低？" \
  "核桃芽菜西芹这个月卖了多少份？" \
  "AAA 门店哪个菜卖得最多？" \
  "AAA 门店核桃芽菜西芹卖了多少？" \
  "那上个月呢？" \
  "那哪个菜最高？" \
  "那毛利呢？" \
  "菜品销量趋势怎么样？"

replay_harness_print_matrix_p1_footer "DISH_SALES_MATRIX_P1"
