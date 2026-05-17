package com.nongxinle.ai.agent.business;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.DailyRevenueAnswerPlan;
import com.nongxinle.ai.dto.business.DiagnosisPlan;
import com.nongxinle.ai.dto.business.DishProfitAnswerPlan;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.dto.business.StockReduceAnswerPlan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DiagnosisAgent v1（确定性）：只读四类 {@code *AnswerPlan}，不写库、不解析用户原文、不遍历失败 tool payload。
 */
public final class BusinessDiagnosisAgentV1 {

    /** 出库/核销合计相对营业额偏高（比值口径，仅存 AnswerPlan 中已有合计）。 */
    private static final double COST_PRESSURE_RATIO = 0.42;
    /** 采购合计相对营业额偏高 */
    private static final double PURCHASE_PRESSURE_RATIO = 0.42;
    /** 毛利偏低（小数 0~1）；若 AnswerPlan 给百分数 &gt;1 会先归一。 */
    private static final double WEAK_MARGIN = 0.32;
    /** 门店视角：采购或出库相对该店营收偏高 */
    private static final double STORE_PRESSURE_RATIO = 0.38;

    static final String FINDING_COST_PRESSURE = "COST_PRESSURE";
    static final String FINDING_PURCHASE_PRESSURE = "PURCHASE_PRESSURE";
    static final String FINDING_STOCK_REDUCE_ABNORMAL = "STOCK_REDUCE_ABNORMAL";
    static final String FINDING_LOW_DISH_MARGIN = "LOW_DISH_MARGIN";
    static final String FINDING_PROFIT_QUALITY_RISK = "PROFIT_QUALITY_RISK";

    /** {@link DiagnosisPlan#getDebug()}：与 Harness 扁平探针对齐。 */
    public static final String DEBUG_DIAGNOSIS_QUESTION_TYPE = "diagnosisQuestionType";
    public static final String DEBUG_DIAGNOSIS_TOP_STORE_NAME = "diagnosisTopStoreName";
    public static final String DEBUG_DIAGNOSIS_TOP_STORE_REASONS = "diagnosisTopStoreReasons";
    public static final String DEBUG_DIAGNOSIS_RANKING_ROWS_COUNT = "diagnosisRankingRowsCount";
    public static final String DIAGNOSIS_QUESTION_STORE_PRIORITY_RANKING = "STORE_PRIORITY_RANKING";

    private static final Pattern STORE_NAME_FROM_DETAIL =
            Pattern.compile("门店「([^」]+)」");

    static final String EVID_REVENUE = "REVENUE";
    static final String EVID_PURCHASE = "PURCHASE";
    static final String EVID_STOCK_REDUCE = "STOCK_REDUCE";
    static final String EVID_DISH_PROFIT = "DISH_PROFIT";
    static final String EVID_MULTI = "MULTI_DOMAIN";

    private static final String SEV_HIGH = "HIGH";
    private static final String SEV_MEDIUM = "MEDIUM";
    private static final String SEV_LOW = "LOW";

    private BusinessDiagnosisAgentV1() {
    }

    /**
     * 在 {@link DiagnosisPlan} 已具备 evidenceRows/consumed/missing 骨架后追加规则产出；无副作用于 state。
     */
    public static void enrich(AiRunState state, DiagnosisPlan plan,
            PurchaseAnswerPlan pPurchase,
            StockReduceAnswerPlan pStock,
            DishProfitAnswerPlan pDish,
            DailyRevenueAnswerPlan pRevenue) {
        if (state == null || plan == null) {
            return;
        }
        if (!state.isBusinessDiagnosisPath()) {
            return;
        }

        plan.setDiagnosisType(DiagnosisPlan.DIAGNOSIS_TYPE_V1_AGGREGATE);

        List<Map<String, Object>> findings = new ArrayList<>();

        Double revenueTotal = summarizeRevenueAmount(pRevenue);
        Double purchaseTotal = summarizePurchaseAmount(pPurchase);
        Double outboundGrand = summarizeStockGrandTotal(pStock);
        Double waste = summarizeStockMinor(pStock, "wasteTotal");
        Double loss = summarizeStockMinor(pStock, "lossTotal");
        Double ret = summarizeStockMinor(pStock, "returnTotal");

        // 1) 出库合计相对营业额 — MULTI_DOMAIN
        if (revenueTotal != null && revenueTotal > 0 && outboundGrand != null && outboundGrand > 0) {
            double r = outboundGrand / revenueTotal;
            if (r >= COST_PRESSURE_RATIO) {
                findings.add(finding(
                        FINDING_COST_PRESSURE,
                        "成本与出库压力",
                        String.format(Locale.CHINA, "出库/核销四类合计约 %.2f 元，约占同期营业额口径 %.1f%%（营业额合计约 %.2f 元）；成本压力偏大。",
                                outboundGrand, r * 100, revenueTotal),
                        r >= COST_PRESSURE_RATIO + 0.08 ? SEV_HIGH : SEV_MEDIUM,
                        EVID_MULTI,
                        "stockReduce.summary.grandTotalFourTypes / revenue.summary.totalRevenue",
                        "结合配方与备货复核高出库品类，核对是否集中生产/退货与售价覆盖。"));
                noteEvidence(plan, EVID_MULTI, "outbound_to_revenue_ratio", r);
                noteEvidence(plan, EVID_REVENUE, "totalRevenue", revenueTotal);
                noteEvidence(plan, EVID_STOCK_REDUCE, "grandTotalFourTypes", outboundGrand);
            }
        }

        // 2) 损耗 / 废弃 / 退货 — STOCK_REDUCE（仅 summary 已有分项时触发）
        if (pStock != null && pStock.getSummary() != null) {
            double abnormal = nz(waste) + nz(loss) + nz(ret);
            if (abnormal > 0) {
                findings.add(finding(
                        FINDING_STOCK_REDUCE_ABNORMAL,
                        "出库异常或损耗分项存在",
                        String.format(Locale.CHINA, "废弃/损耗/退货分项合计约 %.2f 元（AnswerPlan 已给分项）。",
                                abnormal),
                        abnormal >= 3000 ? SEV_MEDIUM : SEV_LOW,
                        EVID_STOCK_REDUCE,
                        "wasteTotal+lossTotal+returnTotal",
                        "核查报损单据、退回原因与库区操作记录。"));
                noteEvidence(plan, EVID_STOCK_REDUCE, "wasteTotal", nz(waste));
                noteEvidence(plan, EVID_STOCK_REDUCE, "lossTotal", nz(loss));
                noteEvidence(plan, EVID_STOCK_REDUCE, "returnTotal", nz(ret));
            }
        }

        // 3) 采购相对营业额偏高 — MULTI_DOMAIN（跨营业额 + 采购）
        if (revenueTotal != null && revenueTotal > 0 && purchaseTotal != null && purchaseTotal > 0) {
            double rp = purchaseTotal / revenueTotal;
            if (rp >= PURCHASE_PRESSURE_RATIO) {
                findings.add(finding(
                        FINDING_PURCHASE_PRESSURE,
                        "采购支出相对营业额偏高",
                        String.format(Locale.CHINA,
                                "采购金额合计约 %.2f 元，约占同期营业额口径 %.1f%%（营业额合计约 %.2f 元）。",
                                purchaseTotal, rp * 100, revenueTotal),
                        rp >= PURCHASE_PRESSURE_RATIO + 0.1 ? SEV_HIGH : SEV_MEDIUM,
                        EVID_MULTI,
                        "purchase.summary.totalAmount / revenue.summary.totalRevenue",
                        "对照畅销结构与供货商价格，复盘订货批次或议价。"));
                noteEvidence(plan, EVID_PURCHASE, "purchase_to_revenue_ratio", rp);
                noteEvidence(plan, EVID_PURCHASE, "totalAmount", purchaseTotal);
                noteEvidence(plan, EVID_REVENUE, "totalRevenue", revenueTotal);
            }
        }

        // 4) 菜品毛利偏低 — DISH_PROFIT
        if (pDish != null && pDish.getFocusRows() != null && !pDish.getFocusRows().isEmpty()) {
            Map<String, Object> r0 = pDish.getFocusRows().get(0);
            if (weakMarginRow(r0)) {
                Double m = blendedMarginNormalized(r0);
                String dishHint = nzStr(r0.get("dishName"));
                findings.add(finding(
                        FINDING_LOW_DISH_MARGIN,
                        "菜品毛利偏低风险",
                        (dishHint.isEmpty() ? "代表性菜品"
                                : "「" + dishHint + "」")
                                + (m != null ? String.format(Locale.CHINA, " 综合毛利率约 %.1f%%，低于阈值参考。", m * 100)
                                : " 综合毛利率处于偏低区间（来自 AnswerPlan 已选行）。"),
                        SEV_MEDIUM,
                        EVID_DISH_PROFIT,
                        "blendedGrossMarginRateOnListPrice",
                        "检查定价与成本波动，复核低毛利菜是否适合做引流组合。"));
                if (m != null) {
                    noteEvidence(plan, EVID_DISH_PROFIT, "blended_margin_ratio", m);
                }
            }
        }

        // 5) 门店：营收头部的店采购/出库同步偏高 — MULTI_DOMAIN
        maybeProfitQualityRisk(pRevenue, pPurchase, pStock, findings, plan);

        // 无明显问题时：不写虚构风险
        if (findings.isEmpty()) {
            plan.setRiskLevel(SEV_LOW);
            plan.setDiagnosisLevel("NORMAL");
            plan.setOverallJudgement("暂无明显多域结构性异常（在已挂载子域 AnswerPlan 范围内）；仍建议持续关注损耗、备货与滞销菜结构。");
            Map<String, Object> nf = finding(
                    "NO_MAJOR_FINDING",
                    "未发现强信号",
                    "在已挂载的营业额、采购、出库与菜品计划中，未发现满足当前确定性阈值的结构性风险；可能与数据缺失或非排行口径有关。",
                    SEV_LOW,
                    EVID_MULTI,
                    "rules_v1_no_trigger",
                    "保持常规复盘：关注损耗、备货与毛利尾部菜品。");
            plan.getFocusFindings().clear();
            plan.getFocusFindings().add(nf);
        } else {
            aggregateRisk(plan, findings);
            plan.getFocusFindings().clear();
            plan.getFocusFindings().addAll(findings);

            boolean anyHigh = findings.stream().anyMatch(f -> SEV_HIGH.equals(str(f.get("severity"))));
            boolean anyMedium = findings.stream().anyMatch(f -> SEV_MEDIUM.equals(str(f.get("severity"))));
            if (anyHigh && plan.getTimeLabel() != null && !plan.getTimeLabel().isBlank()) {
                plan.setOverallJudgement("「" + plan.getTimeLabel() + "」范围内检出 "
                        + findings.size() + " 条需优先关注的风险信号；下文列证据来源与建议动作。");
            } else if (anyHigh) {
                plan.setOverallJudgement("检出 " + findings.size() + " 条需优先关注的风险信号；下文列证据来源与建议动作。");
            } else if (anyMedium) {
                plan.setOverallJudgement("存在若干中等关注项（采购 / 出库 / 毛利或损耗），可按表逐项跟进。");
            } else {
                plan.setOverallJudgement("以低优先级提示为主；可关注损耗明细与备货结构。");
            }
        }

        // riskRows / suggestedActions — 仅从 findings 抄写
        plan.getRiskRows().clear();
        plan.getActionSuggestions().clear();
        for (Map<String, Object> f : plan.getFocusFindings()) {
            if (f == null || "NO_MAJOR_FINDING".equals(f.get("findingType"))) {
                continue;
            }
            LinkedHashMap<String, Object> rr = new LinkedHashMap<>();
            rr.put("title", f.get("title"));
            rr.put("severity", f.get("severity"));
            rr.put("findingType", f.get("findingType"));
            rr.put("evidenceSource", f.get("evidenceSource"));
            plan.getRiskRows().add(rr);
            LinkedHashMap<String, Object> act = new LinkedHashMap<>();
            act.put("action", Objects.toString(f.get("suggestedAction"), ""));
            act.put("findingType", f.get("findingType"));
            act.put("evidenceSource", f.get("evidenceSource"));
            if (!str(act.get("action")).isBlank()) {
                plan.getActionSuggestions().add(act);
            }
        }

        maybeStampStorePriorityRankingDebug(state, plan, pRevenue);
    }

    private static void maybeStampStorePriorityRankingDebug(
            AiRunState state,
            DiagnosisPlan plan,
            DailyRevenueAnswerPlan pRevenue) {
        if (state == null
                || plan == null
                || state.getResolvedQueryContext() == null) {
            return;
        }
        AiResolvedQueryIntent qi = state.getResolvedQueryContext().getQueryIntent();
        String raw = qi != null ? qi.getStructuredIntentDetail() : null;
        if (!AiQuerySemanticLexicon.isStorePriorityRankingStructuredDetail(raw)) {
            return;
        }
        Map<String, Object> dbg = plan.getDebug();
        dbg.put(DEBUG_DIAGNOSIS_QUESTION_TYPE, DIAGNOSIS_QUESTION_STORE_PRIORITY_RANKING);

        String topStore = extractStoreNameForStorePriorityRanking(plan, pRevenue);
        if (topStore != null && !topStore.isBlank()) {
            dbg.put(DEBUG_DIAGNOSIS_TOP_STORE_NAME, topStore.trim());
        } else {
            dbg.put(DEBUG_DIAGNOSIS_TOP_STORE_NAME, null);
        }

        List<String> reasonTitles = new ArrayList<>();
        for (Map<String, Object> f : plan.getFocusFindings()) {
            if (f == null || "NO_MAJOR_FINDING".equals(f.get("findingType"))) {
                continue;
            }
            String ttl = nzStr(f.get("title"));
            if (!ttl.isEmpty()) {
                reasonTitles.add(ttl);
            }
        }
        dbg.put(DEBUG_DIAGNOSIS_TOP_STORE_REASONS, reasonTitles.isEmpty() ? List.of() : reasonTitles);
        dbg.put(DEBUG_DIAGNOSIS_RANKING_ROWS_COUNT, plan.getRiskRows().size());
    }

    /**
     * 门店优先级追问：优先 PROFIT_QUALITY_RISK 解析店名；其次 evidenceItems.store_profit_quality；
     * 再退化营业额门店排行首行（仅当 planType 已为门店排行，不重算排序）。
     */
    public static String extractStoreNameForStorePriorityRanking(
            DiagnosisPlan plan,
            DailyRevenueAnswerPlan pRevenue) {
        if (plan == null) {
            return null;
        }
        for (Map<String, Object> f : plan.getFocusFindings()) {
            if (f == null) {
                continue;
            }
            if (!FINDING_PROFIT_QUALITY_RISK.equals(String.valueOf(f.get("findingType")))) {
                continue;
            }
            Matcher m = STORE_NAME_FROM_DETAIL.matcher(nzStr(f.get("detail")));
            if (m.find()) {
                return m.group(1).trim();
            }
        }
        for (Map<String, Object> row : plan.getEvidenceItems()) {
            if (row == null) {
                continue;
            }
            if ("store_profit_quality".equals(String.valueOf(row.get("label")))) {
                String v = nzStr(row.get("value"));
                if (!v.isEmpty()) {
                    return v.trim();
                }
            }
        }
        if (pRevenue != null
                && DailyRevenueAnswerPlan.TYPE_REVENUE_STORE_AMOUNT_RANKING.equals(planTypeSafe(pRevenue))) {
            Map<String, Object> r0 = pickTopMatchingRowSummary(pRevenue.getFocusRows());
            if (r0 != null) {
                String n = nzStr(r0.get("storeDisplayName"));
                if (n.isEmpty()) {
                    n = nzStr(r0.get("storeName"));
                }
                if (!n.isEmpty()) {
                    return n.trim();
                }
            }
        }
        return null;
    }

    private static void aggregateRisk(DiagnosisPlan plan, List<Map<String, Object>> findings) {
        int score = 0;
        for (Map<String, Object> f : findings) {
            String s = str(f.get("severity"));
            score = switch (s) {
                case SEV_HIGH -> Math.max(score, 3);
                case SEV_MEDIUM -> Math.max(score, 2);
                default -> Math.max(score, 1);
            };
        }
        String rk = score >= 3 ? "HIGH" : score >= 2 ? "MEDIUM" : "LOW";
        plan.setRiskLevel(rk);
        plan.setDiagnosisLevel(score >= 3 ? "RISK" : score >= 2 ? "WARNING" : "NOTICE");
    }

    private static void maybeProfitQualityRisk(
            DailyRevenueAnswerPlan pRevenue,
            PurchaseAnswerPlan pPurchase,
            StockReduceAnswerPlan pStock,
            List<Map<String, Object>> findingsOut,
            DiagnosisPlan plan) {
        if (!(DailyRevenueAnswerPlan.TYPE_REVENUE_STORE_AMOUNT_RANKING.equals(planTypeSafe(pRevenue))
                && PurchaseAnswerPlan.TYPE_PURCHASE_STORE_AMOUNT_RANKING.equals(planTypeSafe(pPurchase)))) {
            return;
        }
        if (!(StockReduceAnswerPlan.TYPE_STOCK_REDUCE_STORE_AMOUNT_RANKING.equals(planTypeSafe(pStock)))) {
            return;
        }
        Map<String, Object> rv = pickTopMatchingRowSummary(pRevenue != null ? pRevenue.getFocusRows() : null);
        Map<String, Object> pv = pickTopPurchaseRowForKey(pPurchase != null ? pPurchase.getFocusRows() : null,
                rv);
        Map<String, Object> sv = pickStockRowForKey(pStock != null ? pStock.getFocusRows() : null, rv);
        if (rv == null || pv == null || sv == null) {
            return;
        }
        double rAmt = dblNz(rowAmt(rv, "revenueAmount"));
        double pAmt = dblNz(rowAmt(pv, "purchaseSubtotal"));
        double sAmt = dblNz(rowAmt(sv, "grandTotalFourTypes"));
        if (!(rAmt > 0)) {
            return;
        }
        boolean pressure = pAmt / rAmt >= STORE_PRESSURE_RATIO || sAmt / rAmt >= STORE_PRESSURE_RATIO;
        if (!pressure) {
            return;
        }
        String label = nzStr(pv.get("storeDisplayName")).isEmpty()
                ? nzStr(rv.get("storeName"))
                : nzStr(pv.get("storeDisplayName"));
        if (label.isEmpty()) {
            label = nzStr(pv.get("label"));
        }
        if (label.isEmpty()) {
            label = "头部门店";
        }
        findingsOut.add(finding(
                FINDING_PROFIT_QUALITY_RISK,
                "利润质量待关注（高营收伴高流出）",
                String.format(Locale.CHINA,
                        "门店「%s」营业额领先，但同期采购 %.2f 元、出库合计 %.2f 元相对该店营收比值偏高（仅基于 AnswerPlan 门店排行行）。",
                        label, pAmt, sAmt),
                SEV_MEDIUM,
                EVID_MULTI,
                "compare_store rankings revenue/purchase/outbound",
                "对该店做备货与出品结构复盘，核对是否高客流但成本同步抬升。"));
        noteEvidence(plan, EVID_MULTI, "store_profit_quality", label);
        noteEvidence(plan, EVID_REVENUE, "top_store_revenue", rAmt);
        noteEvidence(plan, EVID_PURCHASE, "matching_store_purchase", pAmt);
        noteEvidence(plan, EVID_STOCK_REDUCE, "matching_store_outbound", sAmt);
    }

    private static Map<String, Object> pickTopMatchingRowSummary(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        for (Map<String, Object> r : rows) {
            String key = multiStoreKey(r);
            if (key != null && rowAmt(r, "revenueAmount") != null && rowAmt(r, "revenueAmount") > 0) {
                return r;
            }
        }
        return rows.get(0);
    }

    private static Map<String, Object> pickTopPurchaseRowForKey(List<Map<String, Object>> rows,
            Map<String, Object> ref) {
        if (rows == null || rows.isEmpty() || ref == null) {
            return null;
        }
        String k = multiStoreKey(ref);
        Map<String, Object> matched = rows.stream().filter(r -> Objects.equals(multiStoreKey(r), k)).findFirst()
                .orElse(null);
        if (matched != null) {
            return matched;
        }
        return rows.get(0);
    }

    private static Map<String, Object> pickStockRowForKey(List<Map<String, Object>> rows,
            Map<String, Object> ref) {
        if (rows == null || rows.isEmpty() || ref == null) {
            return null;
        }
        String k = multiStoreKey(ref);
        Map<String, Object> matched = rows.stream().filter(r -> Objects.equals(multiStoreKey(r), k)).findFirst()
                .orElse(null);
        if (matched != null) {
            return matched;
        }
        return rows.stream()
                .max(Comparator.comparingDouble(m -> dblNz(rowAmt(m, "grandTotalFourTypes"))))
                .orElse(null);
    }

    /** 对齐多店 AnswerPlan：优先 dept 类 id，其次名称文本。 */
    private static String multiStoreKey(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        for (String k : List.of(
                "storeDepartmentId",
                "departmentFatherId",
                "deptFatherId",
                "purchaseDepartmentFatherId",
                "departmentId",
                "deptId")) {
            Object v = row.get(k);
            if (v != null && !v.toString().isBlank()) {
                return k + ":" + v.toString().trim();
            }
        }
        Object name = row.get("storeDisplayName");
        if (name == null) {
            name = row.get("storeName");
        }
        if (name == null) {
            name = row.get("label");
        }
        if (name != null && !name.toString().isBlank()) {
            return "n:" + name.toString().trim();
        }
        return null;
    }

    private static Double rowAmt(Map<String, Object> row, String key) {
        if (row == null) {
            return null;
        }
        Object v = row.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof String sv) {
            if (sv.isBlank() || "—".equals(sv) || "暂无".equals(sv)) {
                return null;
            }
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(v.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean weakMarginRow(Map<String, Object> row) {
        Double m = blendedMarginNormalized(row);
        return m != null && m <= WEAK_MARGIN;
    }

    private static Double blendedMarginNormalized(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        Object raw = row.get("blendedGrossMarginRateOnListPrice");
        if (raw == null || raw instanceof String s && ("—".equals(s) || "暂无".equals(s) || s.isBlank())) {
            return null;
        }
        double d;
        if (raw instanceof Number n) {
            d = n.doubleValue();
        } else {
            try {
                d = Double.parseDouble(raw.toString().trim().replace(",", ""));
            } catch (Exception e) {
                return null;
            }
        }
        if (d > 1.5 && d <= 100d) {
            d = d / 100d;
        }
        return d >= 0 && d <= 1.5 ? d : null;
    }

    private static Double summarizeRevenueAmount(DailyRevenueAnswerPlan plan) {
        if (plan == null || plan.getSummary() == null) {
            return null;
        }
        return getDouble(plan.getSummary().get("totalRevenue"));
    }

    private static Double summarizePurchaseAmount(PurchaseAnswerPlan plan) {
        if (plan == null || plan.getSummary() == null) {
            return null;
        }
        return getDouble(plan.getSummary().get("totalAmount"));
    }

    private static Double summarizeStockGrandTotal(StockReduceAnswerPlan plan) {
        if (plan == null || plan.getSummary() == null) {
            return null;
        }
        return getDouble(plan.getSummary().get("grandTotalFourTypes"));
    }

    private static Double summarizeStockMinor(StockReduceAnswerPlan plan, String field) {
        if (plan == null || plan.getSummary() == null) {
            return null;
        }
        return getDouble(plan.getSummary().get(field));
    }

    private static Double getDouble(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(v.toString().trim().replace(",", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private static String planTypeSafe(Object p) {
        if (p == null) {
            return "";
        }
        if (p instanceof DailyRevenueAnswerPlan rp) {
            return rp.getPlanType() == null ? "" : rp.getPlanType();
        }
        if (p instanceof PurchaseAnswerPlan pp) {
            return pp.getPlanType() == null ? "" : pp.getPlanType();
        }
        if (p instanceof StockReduceAnswerPlan sp) {
            return sp.getPlanType() == null ? "" : sp.getPlanType();
        }
        return "";
    }

    private static void noteEvidence(DiagnosisPlan plan, String source, String label, Object value) {
        if (plan == null || value == null) {
            return;
        }
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("evidenceSource", source);
        row.put("label", label);
        row.put("value", value instanceof Number ? ((Number) value).doubleValue() : value.toString());
        plan.getEvidenceItems().add(row);
    }

    private static Map<String, Object> finding(
            String type,
            String title,
            String detail,
            String severity,
            String evidenceSource,
            String relatedMetric,
            String action) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("findingType", type);
        m.put("title", title);
        m.put("detail", detail);
        m.put("severity", severity);
        m.put("evidenceSource", evidenceSource);
        m.put("relatedMetric", relatedMetric);
        m.put("suggestedAction", action);
        return m;
    }

    private static double nz(Double d) {
        return d != null ? d : 0d;
    }

    private static double dblNz(Double d) {
        return d != null ? d.doubleValue() : 0d;
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString().trim();
    }

    private static String nzStr(Object o) {
        return str(o);
    }
}
