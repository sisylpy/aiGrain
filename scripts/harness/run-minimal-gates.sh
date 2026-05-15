#!/usr/bin/env bash
# D-12 minimal harness gates: POST built-in replay cases, write JSON artifacts + summary.
# Usage: bash scripts/harness/run-minimal-gates.sh
# Env: BASE_URL (default http://localhost:8090/api)
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8090/api}"
URL="${BASE_URL%/}/ai/harness/replay"
FROZEN="${FROZEN_CLOCK_DATE:-2026-05-15}"
OUT_DIR="${OUT_DIR:-out/harness-gate-$(date +%Y%m%d-%H%M%S)}"

mkdir -p "$OUT_DIR"
SUMMARY="$OUT_DIR/summary.txt"
any_fail=0

: > "$SUMMARY"
echo "# caseId	replayMode	overallPass	failedRounds	firstFailedField	status" >> "$SUMMARY"

body_for_case() {
  python3 -c "
import json
import sys

case_id = sys.argv[1]
frozen = sys.argv[2]

# GROUP_MANAGER fixture (D-12): userId=3 + distributerId + GROUP; no departmentId (avoid STORE/warehouse narrowing).
common = {
    'userId': 3,
    'distributerId': 2,
    'scopeMode': 'GROUP',
    'frozenClockDate': frozen,
    'strictStoreSqlMatch': False,
    'caseId': case_id,
}

msgs = {
    'V2_SEMANTIC_MAINLINE_CORE_10': [
        '上个月哪个菜毛利率最低？',
        '核桃芽菜西芹毛利怎么样？',
        'AAA 和汀兰餐厅哪个营业额高？',
        '那采购呢？',
        '那出库呢？',
        'AAA 和汀兰餐厅哪个出库金额高？',
        '这个月经营得怎么样？',
        '那上个月呢？',
        'AAA 和汀兰餐厅哪个经营情况好？',
        '这个月营业额多少？',
    ],
    'BUSINESS_OVERVIEW_MULTI_AGENT_CORE_3': [
        '这个月经营得怎么样？',
        '那上个月呢？',
        'AAA 和汀兰餐厅哪个经营情况好？',
    ],
    'BUSINESS_DIAGNOSIS_V1_CORE_3': [
        '这个月哪里有问题？',
        'AAA 门店这个月成本为什么偏高？',
        'AAA 和汀兰餐厅哪个经营情况更好，原因是什么？',
    ],
    'REVENUE_AGENT_GRAPH_CORE': [
        '这个月营业额多少？',
        '上个月呢？',
        'AAA 这个月营业额多少？',
    ],
    'PURCHASE_AGENT_GRAPH_CORE': [
        '这个月采购金额多少？',
        '上个月呢？',
        'AAA 这个月采购金额多少？',
    ],
    'STOCK_REDUCE_AGENT_GRAPH_CORE': [
        '这个月出库多少钱？',
        '上个月呢？',
        'AAA 这个月出库多少钱？',
    ],
    'DISH_PROFIT_AGENT_GRAPH_CORE': [
        '上个月哪个菜毛利率最低？',
        '核桃芽菜西芹毛利怎么样？',
        '这个月哪个菜毛利率最高？',
    ],
}

body = dict(common)
body['messages'] = msgs[case_id]
print(json.dumps(body, ensure_ascii=False))
" "$1" "$FROZEN"
}

summarize_response() {
  python3 -c "
import json, sys

def resolve_replay_root(resp):
    \"\"\"R wraps replay in [\\\"replay\\\"]; also accept a bare replay payload at top level.\"\"\"
    if not isinstance(resp, dict):
        return None
    rep = resp.get('replay')
    if isinstance(rep, dict) and rep:
        return rep
    return resp

def count_failed_rounds(rounds):
    n = 0
    for r in rounds or []:
        if isinstance(r, dict) and r.get('pass') is False:
            n += 1
    return n

def squash_cell(s, max_len=160):
    if s is None:
        return ''
    t = str(s).replace('\t', ' ').replace('\r', ' ').replace('\n', ' ')
    while '  ' in t:
        t = t.replace('  ', ' ')
    t = t.strip()
    if len(t) > max_len:
        t = t[:max_len].rstrip() + '...'
    return t


def first_failed_mismatch(rounds):
    for r in rounds or []:
        if not isinstance(r, dict):
            continue
        ff = r.get('failedFields')
        if not isinstance(ff, list):
            continue
        for m in ff:
            if not isinstance(m, dict):
                continue
            f = m.get('field', '?')
            exp = m.get('expected')
            act = m.get('actual')
            return squash_cell(f'{f} expected={exp} actual={act}')
    return '-'

path = sys.argv[1]
try:
    with open(path, 'r', encoding='utf-8') as f:
        response = json.load(f)
except Exception:
    print('ERROR\t-1\tjson_parse\tFAIL')
    sys.exit(0)

root = resolve_replay_root(response)
if root is None or not isinstance(root, dict):
    print('null\t0\tmissing_replay_root\tNEED_REVIEW')
    sys.exit(0)

op = root.get('overallPass')
rounds = root.get('rounds')
if not isinstance(rounds, list):
    rounds = []
failed = count_failed_rounds(rounds)
first_fail = first_failed_mismatch(rounds)
explore = root.get('exploreProbeReplay')

if explore is True:
    ov = 'null' if op is None else ('true' if op is True else 'false')
    status = 'NEED_REVIEW'
    if first_fail == '-':
        first_fail = 'probe_no_expectation'
elif op is True:
    ov = 'true'
    status = 'PASS'
elif op is False:
    ov = 'false'
    status = 'FAIL'
else:
    ov = 'null'
    status = 'NEED_REVIEW'

if first_fail != '-':
    first_fail = squash_cell(first_fail)

print(f'{ov}\t{failed}\t{first_fail}\t{status}')
" "$1"
}

replay_mode_label() {
  case="$1"
  if [[ "$case" == "V2_SEMANTIC_MAINLINE_CORE_10" ]]; then
    echo "RESOLVER_ONLY(server-default)"
  else
    echo "GRAPH_RUN(server-default)"
  fi
}

for cid in \
  V2_SEMANTIC_MAINLINE_CORE_10 \
  BUSINESS_OVERVIEW_MULTI_AGENT_CORE_3 \
  BUSINESS_DIAGNOSIS_V1_CORE_3 \
  REVENUE_AGENT_GRAPH_CORE \
  PURCHASE_AGENT_GRAPH_CORE \
  STOCK_REDUCE_AGENT_GRAPH_CORE \
  DISH_PROFIT_AGENT_GRAPH_CORE
do
  safe="${cid//\//_}"
  raw="$OUT_DIR/${safe}.json"
  pretty="$OUT_DIR/${safe}.pretty.json"
  body="$(body_for_case "$cid")"

  rm -f "$raw" || true
  code=""
  code="$(curl -sS -o "$raw" -w '%{http_code}' -X POST "$URL" \
    -H 'Content-Type: application/json;charset=UTF-8' \
    --data-binary "$body")" || true

  if [[ "$code" != "200" ]]; then
    printf '%s\t%s\t%s\t%s\t%s\t%s\n' \
      "$cid" "$(replay_mode_label "$cid")" "ERROR" "-1" "http_${code}" "FAIL" >> "$SUMMARY"
    any_fail=1
    echo "WARN: $cid HTTP $code (body in $raw)" >&2
    continue
  fi

  python3 -m json.tool "$raw" > "$pretty" 2>/dev/null || cp "$raw" "$pretty"

  rmode="$(replay_mode_label "$cid")"
  IFS=$'\t' read -r overall_pass failed_cnt first_fail status <<< "$(summarize_response "$raw")"
  if [[ "$overall_pass" == "ERROR" && "$status" == "FAIL" && "$first_fail" == "json_parse" ]]; then
    printf '%s\t%s\t%s\t%s\t%s\t%s\n' \
      "$cid" "$rmode" "ERROR" "$failed_cnt" "$first_fail" "$status" >> "$SUMMARY"
    any_fail=1
    continue
  fi
  printf '%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$cid" "$rmode" "$overall_pass" "$failed_cnt" "$first_fail" "$status" >> "$SUMMARY"

  if [[ "$status" == "FAIL" ]]; then
    any_fail=1
  fi
done

echo "Wrote: $OUT_DIR"
echo "Summary: $SUMMARY"
cat "$SUMMARY"

if [[ "$any_fail" -ne 0 ]]; then
  exit 1
fi
exit 0
