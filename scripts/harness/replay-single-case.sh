#!/usr/bin/env bash
# 只跑单个 Harness replay case（与 bundle 相同 API / 输出 JSON 形态）
# 用法:
#   ./scripts/harness/replay-single-case.sh <caseId> "第一轮用户句" "第二轮..." ...
# 示例:
#   ./scripts/harness/replay-single-case.sh PURCHASE_AGENT_GRAPH_CORE \
#     "这个月采购金额多少？" "上个月呢？" "AAA 这个月采购金额多少？"
#
# 环境变量（可选）: API_BASE, REPLAY_OUT_DIR, REPLAY_HARNESS_BASE_JSON
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
: "${API_BASE:=http://localhost:8090/api}"
: "${REPLAY_OUT_DIR:=${REPO_ROOT}/out/replay-single-case}"

# shellcheck source=scripts/harness/replay-harness-common.sh
source "${SCRIPT_DIR}/replay-harness-common.sh"

usage() {
  echo "用法: $0 <caseId> <message1> [message2 ...]" >&2
  echo "示例: $0 PURCHASE_AGENT_GRAPH_CORE \"这个月采购金额多少？\" \"上个月呢？\"" >&2
  exit 1
}

[[ $# -ge 2 ]] || usage

mkdir -p "$REPLAY_OUT_DIR"
replay_harness_require_curl_python3

case_id="$1"
shift

payload="$(python3 -c 'import json,sys; base=json.loads(sys.argv[1]); cid=sys.argv[2]; msgs=sys.argv[3:]; base.update({"caseId":cid,"messages":msgs}); print(json.dumps(base,ensure_ascii=False))' "$REPLAY_HARNESS_BASE_JSON" "$case_id" "$@")"

replay_harness_run_one "$case_id" "$payload"
replay_harness_print_pass_footer "$case_id"
