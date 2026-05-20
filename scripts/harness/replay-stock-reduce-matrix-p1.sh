#!/usr/bin/env bash
# 出库 Matrix P1 回归（STOCK_REDUCE_MATRIX_P1）
# 用法（无需 chmod +x）:
#   bash scripts/harness/replay-stock-reduce-matrix-p1.sh
#   API_BASE=... REPLAY_OUT_DIR=... bash scripts/harness/replay-stock-reduce-matrix-p1.sh
#
# 前置: 服务已启动；ai.harness.replay-enabled=true；建议安装 jq
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
: "${API_BASE:=http://localhost:8090/api}"
: "${REPLAY_OUT_DIR:=${REPO_ROOT}/out/replay-stock-reduce-matrix-p1}"

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

run_case_payload "STOCK_REDUCE_MATRIX_P1" "STOCK_REDUCE_MATRIX_P1" \
  "本月出库金额多少？" \
  "哪个门店出库金额最高？" \
  "生产耗用金额多少？" \
  "废弃金额多少？" \
  "损失金额多少？" \
  "退货金额多少？" \
  "哪个商品废弃最多？" \
  "AAA 门店出库情况怎么样？" \
  "那废弃呢？" \
  "那损失呢？" \
  "那哪个商品废弃最多？"

replay_harness_print_matrix_p1_footer "STOCK_REDUCE_MATRIX_P1"
