#!/usr/bin/env bash
# 营业额 Matrix P1 回归（REVENUE_MATRIX_P1）
# 用法（无需 chmod +x）:
#   bash scripts/harness/replay-revenue-matrix-p1.sh
#   API_BASE=... REPLAY_OUT_DIR=... bash scripts/harness/replay-revenue-matrix-p1.sh
#
# 前置: 服务已启动；ai.harness.replay-enabled=true；建议安装 jq
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
: "${API_BASE:=http://localhost:8090/api}"
: "${REPLAY_OUT_DIR:=${REPO_ROOT}/out/replay-revenue-matrix-p1}"

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

run_case_payload "REVENUE_MATRIX_P1" "REVENUE_MATRIX_P1" \
  "这个月营业额多少？" \
  "哪个门店营业额最高？" \
  "AAA 门店这个月营业额多少？" \
  "AAA 和汀兰餐厅哪个营业额高？" \
  "上个月营业额多少？" \
  "那上个月呢？" \
  "那哪个门店最高？" \
  "本月和上月比怎么样？" \
  "哪天营业额最高？" \
  "营业额趋势怎么样？"

replay_harness_print_matrix_p1_footer "REVENUE_MATRIX_P1"
