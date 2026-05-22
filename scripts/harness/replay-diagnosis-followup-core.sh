#!/usr/bin/env bash
# 经营诊断 + 门店优先级分层回归（2 case）
# 用法:
#   ./scripts/harness/replay-diagnosis-followup-core.sh
#   API_BASE=... REPLAY_OUT_DIR=... ./scripts/harness/replay-diagnosis-followup-core.sh
#
# 前置: 服务已启动；ai.harness.replay-enabled=true；建议安装 jq
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
: "${API_BASE:=http://localhost:8090/api}"
: "${REPLAY_OUT_DIR:=${REPO_ROOT}/out/replay-diagnosis-followup-core}"

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

run_case_payload "BUSINESS_DIAGNOSIS_V1_CORE_3" "BUSINESS_DIAGNOSIS_V1_CORE_3" \
  "这个月哪里有问题？" \
  "AAA 门店这个月成本为什么偏高？" \
  "AAA 和汀兰餐厅哪个经营情况更好，原因是什么？"

run_case_payload "BUSINESS_STORE_PRIORITY_REASON_EXPLANATION_3" \
  "BUSINESS_STORE_PRIORITY_REASON_EXPLANATION_3" \
  "这个月经营得怎么样？" \
  "哪个门店问题最大？" \
  "具体是什么问题？"

replay_harness_print_pass_footer \
  BUSINESS_DIAGNOSIS_V1_CORE_3 \
  BUSINESS_STORE_PRIORITY_REASON_EXPLANATION_3
