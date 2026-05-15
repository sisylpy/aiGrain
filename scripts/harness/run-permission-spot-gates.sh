#!/usr/bin/env bash
# D-13 permission spot gates: four frozen personas, PROBE GRAPH_RUN replay, scripted assertions.
# Usage: bash scripts/harness/run-permission-spot-gates.sh
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8090/api}"
URL="${BASE_URL%/}/ai/harness/replay"
FROZEN="${FROZEN_CLOCK_DATE:-2026-05-15}"
OUT_DIR="${OUT_DIR:-out/permission-spot-gate-$(date +%Y%m%d-%H%M%S)}"

mkdir -p "$OUT_DIR"
SUMMARY="$OUT_DIR/summary.txt"
any_fail=0

write_request() {
  python3 -c "
import json, sys

persona, frozen = sys.argv[1], sys.argv[2]
COMMON = {
    'caseId': 'PROBE',
    'ignoreExpectations': True,
    'replayMode': 'GRAPH_RUN',
    'strictStoreSqlMatch': False,
    'frozenClockDate': frozen,
}
MSGS = {
    'GROUP_MANAGER': [
        '这个月营业额多少？',
        '哪个门店问题最大？',
        'AAA 和汀兰餐厅哪个经营更好，主要原因是什么？',
    ],
    'PURCHASER': [
        '这个月采购金额多少？',
        '这个月营业额多少？',
        '哪个门店问题最大？',
    ],
    'WAREHOUSE': [
        '这个月出库金额多少？',
        '库存情况怎么样？',
        '这个月营业额多少？',
        '哪个门店问题最大？',
    ],
    'STORE_MANAGER': [
        '这个月营业额多少？',
        'AAA 和汀兰餐厅哪个经营更好，主要原因是什么？',
        '哪个门店问题最大？',
    ],
}
if persona == 'GROUP_MANAGER':
    body = {'userId': 3, 'distributerId': 2, 'scopeMode': 'GROUP'}
elif persona == 'PURCHASER':
    body = {'userId': 2, 'departmentId': 3, 'scopeMode': 'STORE', 'distributerId': 2}
elif persona == 'WAREHOUSE':
    body = {'userId': 1, 'departmentId': 1, 'scopeMode': 'STORE', 'distributerId': 2}
elif persona == 'STORE_MANAGER':
    body = {'userId': 4, 'departmentId': 1, 'scopeMode': 'STORE', 'distributerId': 2}
else:
    raise SystemExit('unknown persona')

body.update(COMMON)
body['messages'] = MSGS[persona]
print(json.dumps(body, ensure_ascii=False))
" "$1" "$FROZEN" >"$OUT_DIR/${1}.request.json"
}

evaluate_persona() {
  python3 -c '
import json, re, sys

persona, jpath, http_s = sys.argv[1], sys.argv[2], sys.argv[3]

EXPECTED_ROUNDS = {
    "GROUP_MANAGER": 3,
    "PURCHASER": 3,
    "WAREHOUSE": 4,
    "STORE_MANAGER": 3,
}
EXPECTED_SCOPE = {
    "GROUP_MANAGER": "GROUP",
    "PURCHASER": "PURCHASER",
    "WAREHOUSE": "WAREHOUSE",
    "STORE_MANAGER": "STORE",
}[persona]

# 正向越权 / 占位话术（字面匹配）
POSITIVE_FORBIDDEN_ALL = (
    "营业额为0",
    "营业额为 0",
    "综合风险评分",
    "菜品毛利结论",
    "数据不足",
    "集团口径",
)

# 集团与排名类正向禁词（须先剔除「非/不/不作为/不做/不按 + 同内核」否定说明，否则会误命中子串）
POSITIVE_FORBIDDEN_RANK = (
    "全集团经营排名",
    "全部门店排名",
    "排名第一",
)

NEGATION_PREFIXES_RANK = ("非", "不是", "不作为", "不做", "不按")


def mask_negated_rank_context(text):
    """将否定型边界说明整段替换为占位符，避免「非全集团经营排名」误命中「全集团经营排名」。"""
    if not isinstance(text, str) or not text:
        return text
    spans = []
    for core in POSITIVE_FORBIDDEN_RANK:
        for pfx in NEGATION_PREFIXES_RANK:
            spans.append(pfx + core)
    spans.sort(key=len, reverse=True)
    out = text
    for sp in spans:
        if sp in out:
            out = out.replace(sp, "\uff03" * len(sp))
    return out

# PURCHASER：禁止库房收尾话术
FORBIDDEN_WAREHOUSE_WOR_PURCHASER = ("库房端",)

# 否定型边界：对 RANK 禁词先 `mask_negated_rank_context` 再匹配；ALL 仍用原文（与「非…」无子串包含关系者）

def squish_cell(s, n=420):
    if s is None:
        return "-"
    t = str(s).replace("\t", " ").replace("\r", " ").replace("\n", " ")
    while "  " in t:
        t = t.replace("  ", " ")
    t = t.strip()
    return t[:n].rstrip() + ("..." if len(t) > n else "")


def merge_round(rd):
    s = rd.get("resolvedQueryContextSummary") or {}
    p = rd.get("probe") or {}
    m = dict(s)
    for k, v in p.items():
        if v is not None:
            m[k] = v
    return m


def load_replay(path):
    with open(path, "r", encoding="utf-8") as f:
        resp = json.load(f)
    if isinstance(resp, dict):
        rep = resp.get("replay")
        if isinstance(rep, dict) and isinstance(rep.get("rounds"), list):
            return rep, None
        if isinstance(resp.get("rounds"), list):
            return resp, None
    return None, "missing_replay_root"


def store_names_visible(m):
    vs = m.get("visibleStores")
    out = []
    if isinstance(vs, list):
        for row in vs:
            if isinstance(row, dict) and row.get("storeName"):
                out.append(str(row.get("storeName")))
    return out


def whale_ids(m):
    w = m.get("visibleWarehouseIds")
    out = []
    if isinstance(w, list):
        for x in w:
            try:
                out.append(int(x))
            except (TypeError, ValueError):
                continue
    return out


def denials_blob(rd):
    d = merge_round(rd).get("permissionDenials")
    if not d:
        return ""
    return json.dumps(d, ensure_ascii=False)


def denials_blob_upper(rd):
    return denials_blob(rd).upper()


def revenue_denial_ok(blob):
    if not blob:
        return False
    u = blob.upper()
    l = blob.lower()
    return ("VIEW_REVENUE" in u) or ("revenue_query" in l)


def positive_forbidden_hits(persona, text):
    if not isinstance(text, str):
        text = ""
    found = []
    text_rank = mask_negated_rank_context(text)
    for tok in POSITIVE_FORBIDDEN_ALL:
        if tok in text:
            found.append(tok)
    for tok in POSITIVE_FORBIDDEN_RANK:
        if tok in text_rank:
            found.append(tok)
    if persona == "PURCHASER":
        for tok in FORBIDDEN_WAREHOUSE_WOR_PURCHASER:
            if tok in text:
                found.append(tok)
    # WAREHOUSE：不将「库房端」计入 forbiddenHits
    if not found:
        return [], None
    uniq = []
    seen = set()
    for h in found:
        if h not in seen:
            seen.add(h)
            uniq.append(h)
    return uniq, "forbidden_hits_positive:" + ",".join(uniq[:20])


def purchaser_revenue_denied_but_money_like(rd):
    """营业额轮已有结构化拒绝时，正文仍不应像给出了具体营业额结论（金额/具象数字）。"""
    m = merge_round(rd)
    b = denials_blob(rd)
    if not revenue_denial_ok(b):
        return False
    fat = (str(m.get("finalAnswerText") or "") + str(m.get("answerPreview") or "")).strip()
    if not fat:
        return False
    if re.search(
        r"(?:¥|￥|元|万块|万元|万\s|千\s*[元块]|[\d○零一二三四五六七八九十百千]+(?:\.\d+)?\s*[万元])",
        fat,
    ):
        boundary_only = ("无权", "无权限", "不可见", "无法查看", "无法提供", "未授权",
                         "不包含", "无法获取", "暂无法", "没有权限")
        if any(k in fat for k in boundary_only) and not re.search(
            r"(?:营业额|营收|销售金额)[^。\n]{0,40}?[0-9]{4,}",
            fat,
        ):
            return False
        if re.search(
            r"(?:¥|￥|元|万块|万元|万\s).*?[0-9]{2,}|[0-9]{4,}\s*[元万块万元]",
            fat,
        ):
            return True
    if (
        ("营业额" in fat or "营收" in fat)
        and re.search(r"[0-9]{5,}", fat)
        and all(k not in fat for k in ("无权", "无权限", "无法查看"))
    ):
        return True
    return False


def all_final_text(rounds):
    chunks = []
    for rd in rounds:
        m = merge_round(rd)
        t = m.get("finalAnswerText")
        if t is None:
            t = m.get("answerPreview")
        if isinstance(t, str) and t:
            chunks.append(t)
    return "\n".join(chunks)


def print_row(persona_gate, rn, hs, escope, ascope, psig, fhit, st, fres, ks):
    gate_id = "D13_" + persona_gate
    print("\t".join([
        persona_gate,
        gate_id,
        str(rn),
        str(hs),
        escope or "?",
        ascope or "?",
        squish_cell(psig),
        fhit if fhit else "-",
        st,
        squish_cell(fres),
        squish_cell(ks, 560),
    ]))


HTTP = int(http_s) if http_s.isdigit() else -1
want_n = EXPECTED_ROUNDS[persona]

if HTTP != 200:
    print_row(persona, want_n, HTTP, "?", "?", "?", "-", "AUTO_FAIL", "http_not_200", "-")
    raise SystemExit(0)

try:
    root, err = load_replay(jpath)
except Exception as e:
    msg = str(e).replace("\t", " ").replace("\n", " ")
    print_row(persona, want_n, HTTP, EXPECTED_SCOPE, "?", "?", "-", "AUTO_FAIL", "json_parse_failed:" + msg, "-")
    raise SystemExit(0)

if root is None or err:
    print_row(persona, want_n, HTTP, EXPECTED_SCOPE, "?", "?", "-", "AUTO_FAIL", err or "no_replay", "-")
    raise SystemExit(0)

rounds = tuple(root.get("rounds") or [])
rounds_eff = [r for r in rounds if isinstance(r, dict) and str(r.get("message") or "").strip()]

scopes = [str(merge_round(rd).get("scopeType") or "").strip() for rd in rounds_eff]
scope_actual_set = "|".join(sorted({s for s in scopes if s})) or "?"
perm_sig = " || ".join(squish_cell(denials_blob_upper(r), 180) for r in rounds_eff)
text_all = all_final_text(rounds_eff)
forbidden_hit_tokens, forbid_early_reason = positive_forbidden_hits(persona, text_all)

TINGLAN_DIGIT_LEAK_RE = re.compile(r"汀兰[^。\n]{0,120}?[0-9]{4,}")

review_note = []
reject_reason = None

if forbid_early_reason:
    reject_reason = forbid_early_reason
elif len(rounds_eff) < want_n:
    reject_reason = "round_count_low want=%d got=%d" % (want_n, len(rounds_eff))
elif any(sc != EXPECTED_SCOPE for sc in scopes if sc):
    reject_reason = "scopeType_mismatch want=%s got=%s" % (EXPECTED_SCOPE, scope_actual_set)
else:
    blank_idxs = []
    for rd in rounds_eff:
        if merge_round(rd).get("finalAnswerTextBlank") is True:
            blank_idxs.append(str(rd.get("roundIndex")))
    if blank_idxs:
        reject_reason = "finalAnswerTextBlank_true rounds=" + ",".join(blank_idxs)

    elif persona == "GROUP_MANAGER":
        last_m = merge_round(rounds_eff[-1])
        sns = store_names_visible(last_m)
        ok_aaa = any("AAA" in x for x in sns)
        ok_tl = any("汀兰" in x for x in sns)
        if not (ok_aaa and ok_tl):
            reject_reason = "visibleStores_missing_AAA_or_汀兰 got=" + repr(sns)

    elif persona == "PURCHASER":
        m0 = merge_round(rounds_eff[0])
        purch_ok = (
            m0.get("purchaseOverviewPath") is True
            or m0.get("harnessReplayPurchaseAnswerPlanProbePresent") is True
            or "purchase" in str(m0.get("effectivePathCode") or "").lower()
        )
        if not purch_ok:
            reject_reason = "purchase_round_missing_purchase_signal"
        else:
            b1 = denials_blob(rounds_eff[1])
            m1 = merge_round(rounds_eff[1])
            if not revenue_denial_ok(b1):
                if m1.get("revenueOverviewPath") is True:
                    reject_reason = "revenue_round_no_denial_but_revenue_path_true"
                elif not b1:
                    reject_reason = "revenue_round_missing_permissionDenials"
                else:
                    reject_reason = "revenue_denial_not_recognizable:" + squish_cell(b1, 140)
            else:
                sns = store_names_visible(merge_round(rounds_eff[-1]))
                vis_ok = (
                    sns
                    and all("汀兰" in nm for nm in sns)
                    and not any(nm.strip() == "AAA" for nm in sns)
                )
                if not vis_ok:
                    reject_reason = "visibleStores_should_be_汀兰_only got=" + repr(sns)
                elif purchaser_revenue_denied_but_money_like(rounds_eff[1]):
                    reject_reason = "revenue_denied_but_money_like_conclusion"

    elif persona == "WAREHOUSE":
        if not any(1 in whale_ids(merge_round(r)) for r in rounds_eff):
            reject_reason = "visibleWarehouseIds_missing_1"
        else:
            rv_blob = denials_blob(rounds_eff[2])
            m_rev = merge_round(rounds_eff[2])
            if not revenue_denial_ok(rv_blob):
                if m_rev.get("revenueOverviewPath") is True:
                    reject_reason = "warehouse_revenue_round_no_denial_but_revenue_path"
                elif not rv_blob:
                    reject_reason = "warehouse_revenue_round_missing_permissionDenials"
                else:
                    reject_reason = "warehouse_revenue_denial_not_recognizable:" + squish_cell(rv_blob, 140)
            else:
                mstk = merge_round(rounds_eff[0])
                mstk2 = merge_round(rounds_eff[1])
                st_ok = mstk.get("stockReduceQueryPath") is True or "stock_reduce" in str(
                    mstk.get("effectivePathCode") or ""
                ).lower()
                wh_ok = mstk2.get("warehouseStockOverviewPath") is True or "warehouse_stock" in str(
                    mstk2.get("effectivePathCode") or ""
                ).lower()
                if not st_ok:
                    review_note.append("stock_outbound_path_ambiguous")
                if not wh_ok:
                    review_note.append("inventory_path_ambiguous")

    elif persona == "STORE_MANAGER":
        union_sns = []
        for rd in rounds_eff:
            union_sns.extend(store_names_visible(merge_round(rd)))
        if not union_sns:
            reject_reason = "visibleStores_empty_for_STORE_MANAGER"
        elif not any("AAA" in x for x in union_sns):
            reject_reason = "visibleStores_missing_AAA got=" + repr(sorted(set(union_sns)))
        elif any("汀兰" in x for x in union_sns):
            reject_reason = "visibleStores_should_not_include_汀兰 got=" + repr(sorted(set(union_sns)))
        elif TINGLAN_DIGIT_LEAK_RE.search(text_all):
            reject_reason = "suspected_汀兰_numeric_leak"
        elif "VIEW_REVENUE" in denials_blob_upper(rounds_eff[0]):
            reject_reason = "store_manager_revenue_round_unexpected_denial"
        else:
            for rd in rounds_eff:
                msg = str(rd.get("message") or "")
                if "汀兰" not in msg:
                    continue
                fat = str(merge_round(rd).get("finalAnswerText") or "") + str(
                    merge_round(rd).get("answerPreview") or ""
                )
                if denials_blob_upper(rd):
                    continue
                if "汀兰" in fat and re.search(r"[0-9]{4,}", fat) and not any(
                    k in fat for k in ("权限", "无权", "不可见", "无法查看", "无权限", "当前权限")
                ):
                    reject_reason = "cross_store_round_boundary_weak_with_numbers"
                    break

hits_col = ",".join(forbidden_hit_tokens) if forbidden_hit_tokens else "-"
perm_sig_fin = squish_cell(perm_sig)
key_signals = squish_cell(text_all[:600] + ("..." if len(text_all) > 600 else ""), 560)

if reject_reason:
    print_row(persona, len(rounds_eff), HTTP, EXPECTED_SCOPE, scope_actual_set,
              perm_sig_fin, hits_col, "AUTO_FAIL", reject_reason, key_signals)
    raise SystemExit(0)

for rd in rounds_eff:
    msg = str(rd.get("message") or "")
    if persona == "STORE_MANAGER" and "汀兰" in msg:
        if not denials_blob(rd):
            fat = str(merge_round(rd).get("finalAnswerText") or "")
            if "汀兰" in fat and not any(
                k in fat for k in ("权限", "无权", "不可见", "无法", "无权限", "当前权限")
            ):
                review_note.append("cross_store_boundary_text_unclear")

st = "NEED_REVIEW" if review_note else "AUTO_PASS"
fres = ";".join(sorted(set(review_note))) if review_note else "-"
print_row(persona, len(rounds_eff), HTTP, EXPECTED_SCOPE, scope_actual_set,
          perm_sig_fin, hits_col, st, fres, key_signals)
' "$1" "$2" "$3"
}

: >"$SUMMARY"
echo "# persona	gateId	roundCount	httpStatus	expectedScope	actualScopes	permissionSignals	forbiddenHits	status	failedReason	keySignals" >>"$SUMMARY"

for persona in GROUP_MANAGER PURCHASER WAREHOUSE STORE_MANAGER; do
  write_request "$persona"
  req="$OUT_DIR/${persona}.request.json"
  raw="$OUT_DIR/${persona}.json"
  pretty="$OUT_DIR/${persona}.pretty.json"

  rm -f "$raw" || true
  code=""
  code="$(curl -sS -o "$raw" -w '%{http_code}' -X POST "$URL" \
    -H 'Content-Type: application/json;charset=UTF-8' \
    --data-binary @"$req")" || true

  if [[ ! -s "$raw" ]]; then
    echo "WARN: empty body for ${persona} HTTP=${code:-?}" >&2
  fi
  python3 -m json.tool "$raw" >"$pretty" 2>/dev/null || cp "$raw" "$pretty"

  line="$(evaluate_persona "$persona" "$raw" "${code:-000}")"
  printf '%s\n' "$line" >>"$SUMMARY"
  status="$(printf '%s' "$line" | cut -f9)"
  if [[ "$status" == "AUTO_FAIL" ]]; then
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
