package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 在 {@link BusinessToolExecutionNode} 完成 {@link AiBusinessToolIds#PURCHASE_OVERVIEW} 后，
 * 基于 Tool 已排序/已过滤的结果生成 {@link PurchaseAnswerPlan}（不重查 SQL）。
 */
@Slf4j
public final class PurchaseAnswerPlanBuilder {

    private PurchaseAnswerPlanBuilder() {
    }

    public static void attachIfApplicable(AiRunState state) {
        if (state == null) {
            return;
        }
        boolean plannedPurchaseOverview = state.getDataPlanTools() != null
                && state.getDataPlanTools().contains(AiBusinessToolIds.PURCHASE_OVERVIEW);
        Map<String, Object> overview = extractPurchaseOverviewPayload(state);
        if (overview.isEmpty()) {
            if (plannedPurchaseOverview) {
                log.warn("[PurchaseAnswerPlan] skip empty overview runId={} toolResultKeys={} hasPurchaseEnvelope={}",
                        state.getRunId(),
                        state.getToolResults() == null ? null : state.getToolResults().keySet(),
                        state.getToolResults() != null
                                && state.getToolResults().containsKey(AiBusinessToolIds.PURCHASE_OVERVIEW));
            }
            return;
        }
        // 仅在有「非空 error 文案」时跳过（避免某些序列化层写入 error=null 误杀）
        if (overviewHasBlockingError(overview)) {
            if (plannedPurchaseOverview) {
                log.warn("[PurchaseAnswerPlan] skip overview.error runId={} err={}",
                        state.getRunId(), overview.get("error"));
            }
            return;
        }
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        try {
            PurchaseAnswerPlan plan = build(state, overview, rq);
            state.setPurchaseAnswerPlan(plan);
            log.info("[PurchaseAnswerPlan] attached runId={} type={} focusSize={} secondarySize={}",
                    state.getRunId(),
                    plan.getPlanType(),
                    plan.getFocusRows() == null ? 0 : plan.getFocusRows().size(),
                    plan.getSecondaryRows() == null ? 0 : plan.getSecondaryRows().size());
        } catch (Exception ex) {
            log.warn("[PurchaseAnswerPlan] attach failed runId={}", state.getRunId(), ex);
            state.setPurchaseAnswerPlan(null);
        }
    }

    static PurchaseAnswerPlan build(AiRunState state, Map<String, Object> overview, AiResolvedQueryContext rq) {
        String wire = "";
        String pst = AiQuerySemanticLexicon.SOURCE_ALL;
        if (rq != null && rq.getQueryIntent() != null) {
            AiResolvedQueryIntent qi = rq.getQueryIntent();
            if (qi.getStructuredIntentDetail() != null && !qi.getStructuredIntentDetail().isBlank()) {
                wire = qi.getStructuredIntentDetail().trim();
            }
            if (qi.getPurchaseSourceType() != null && !qi.getPurchaseSourceType().isBlank()) {
                pst = qi.getPurchaseSourceType().trim();
            }
        }

        String planType = resolvePlanType(wire, pst);
        String scopeLabel = resolveScopeLabel(overview, rq);
        String timeLabel = resolveTimeLabel(state, rq);

        Map<String, Object> summary = buildSummary(overview);
        List<Map<String, Object>> focusRows = new ArrayList<>();
        List<Map<String, Object>> secondaryRows = new ArrayList<>();
        fillRows(planType, overview, focusRows, secondaryRows);

        LinkedHashMap<String, Object> debug = new LinkedHashMap<>();
        debug.put("structuredIntentDetailWire", wire.isEmpty() ? null : wire);
        debug.put("resolvedPlanType", planType);
        debug.put("source", "PurchaseOverviewTool");
        if (PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_AMOUNT_RANKING.equals(planType)) {
            debug.put("sortKey", "totalPurchaseAmount");
            debug.put("sortDirection", "DESC");
        } else if (PurchaseAnswerPlan.TYPE_PURCHASE_STORE_AMOUNT_RANKING.equals(planType)) {
            debug.put("sortKey", "purchaseSubtotalPerStore");
            debug.put("sortDirection", "DESC");
        } else if (PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_AMOUNT_RANKING.equals(planType)) {
            debug.put("sortKey", "totalPurchaseAmount");
            debug.put("sortDirection", "DESC");
        } else if (PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_COUNT_RANKING.equals(planType)) {
            debug.put("sortKey", "purchaseTimes");
            debug.put("sortDirection", "DESC");
        }

        return PurchaseAnswerPlan.builder()
                .planType(planType)
                .scopeLabel(scopeLabel)
                .timeLabel(timeLabel)
                .purchaseSourceType(pst)
                .summary(summary)
                .focusRows(focusRows)
                .secondaryRows(secondaryRows)
                .debug(debug)
                .build();
    }

    /**
     * 仅依据解析层下发的 structuredIntentDetail wire（及采购来源枚举），禁止读取用户原文推断排行语义。
     */
    static String resolvePlanType(String structuredWire, String purchaseSourceType) {
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(structuredWire);
        String wire = canon != null ? canon.trim() : (structuredWire == null ? "" : structuredWire.trim());
        String pst = purchaseSourceType == null ? AiQuerySemanticLexicon.SOURCE_ALL : purchaseSourceType.trim();

        if (AiQuerySemanticLexicon.isSupplierAmountRankingDetail(wire)) {
            return PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_AMOUNT_RANKING;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_STORE_AMOUNT_RANKING.equals(wire)) {
            return PurchaseAnswerPlan.TYPE_PURCHASE_STORE_AMOUNT_RANKING;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_AMOUNT_RANKING.equals(wire)) {
            return PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_AMOUNT_RANKING;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_COUNT_RANKING.equals(wire)) {
            return PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_COUNT_RANKING;
        }

        boolean self = AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE.equals(pst);
        boolean sup = AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE.equals(pst);

        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_ANOMALY.equals(wire)
                || AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_AMOUNT_SPIKE.equals(wire)) {
            if (self) {
                return PurchaseAnswerPlan.TYPE_PURCHASE_SELF_OVERVIEW;
            }
            if (sup) {
                return PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_OVERVIEW;
            }
            return PurchaseAnswerPlan.TYPE_PURCHASE_OVERVIEW;
        }

        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_AMOUNT_QUERY.equals(wire)) {
            if (self) {
                return PurchaseAnswerPlan.TYPE_PURCHASE_SELF_OVERVIEW;
            }
            if (sup) {
                return PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_OVERVIEW;
            }
            return PurchaseAnswerPlan.TYPE_PURCHASE_OVERVIEW;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_SUMMARY.equals(wire)
                || AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY.equals(wire)) {
            if (self) {
                return PurchaseAnswerPlan.TYPE_PURCHASE_SELF_OVERVIEW;
            }
            if (sup) {
                return PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_OVERVIEW;
            }
            return PurchaseAnswerPlan.TYPE_PURCHASE_OVERVIEW;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_OVERVIEW_SUMMARY.equals(wire)) {
            if (self) {
                return PurchaseAnswerPlan.TYPE_PURCHASE_SELF_OVERVIEW;
            }
            if (sup) {
                return PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_OVERVIEW;
            }
            return PurchaseAnswerPlan.TYPE_PURCHASE_OVERVIEW;
        }
        if (self) {
            return PurchaseAnswerPlan.TYPE_PURCHASE_SELF_OVERVIEW;
        }
        if (sup) {
            return PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_OVERVIEW;
        }
        return PurchaseAnswerPlan.TYPE_PURCHASE_OVERVIEW;
    }

    private static void fillRows(String planType, Map<String, Object> overview,
            List<Map<String, Object>> focusRows, List<Map<String, Object>> secondaryRows) {
        switch (planType) {
            case PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_AMOUNT_RANKING ->
                    splitTopRows(castRowList(overview.get("topSuppliers")), focusRows, secondaryRows);
            case PurchaseAnswerPlan.TYPE_PURCHASE_STORE_AMOUNT_RANKING ->
                    mergeAndSortPurchaseStoreRows(overview, focusRows, secondaryRows);
            case PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_AMOUNT_RANKING ->
                    splitTopRows(firstNonEmptyRowList(overview,
                            "goodsPurchaseAmountTop",
                            "goodsAmountTop",
                            "purchaseGoodsAmountTop"),
                            focusRows, secondaryRows);
            case PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_COUNT_RANKING ->
                    splitTopRows(firstNonEmptyRowList(overview,
                            "goodsPurchaseFrequencyTop",
                            "goodsFrequencyTop",
                            "goodsPurchaseCountTop",
                            "purchaseGoodsFrequencyTop"),
                            focusRows, secondaryRows);
            default -> {
                LinkedHashMap<String, Object> core = new LinkedHashMap<>();
                core.put("totalPurchaseAmount", overview.get("totalPurchaseAmount"));
                core.put("purchaseOrderCount", overview.get("purchaseOrderCount"));
                focusRows.add(core);
            }
        }
    }

    /**
     * 多店并排采购金额对比：{@link com.nongxinle.ai.tool.business.PurchaseOverviewTool} 写入的 coveredStores /
     * dataMissingStores（有额度的店在前，金额为 0 的店在后，按额度降序）。
     */
    private static void mergeAndSortPurchaseStoreRows(Map<String, Object> overview,
            List<Map<String, Object>> focusRows,
            List<Map<String, Object>> secondaryRows) {
        List<Map<String, Object>> merged = new ArrayList<>();
        merged.addAll(castRowList(overview.get("coveredStores")));
        merged.addAll(castRowList(overview.get("dataMissingStores")));
        merged.sort(Comparator.<Map<String, Object>, Double>comparing(
                        r -> parseDoubleLoose(r.get("purchaseSubtotal")))
                .reversed());
        splitTopRows(merged, focusRows, secondaryRows);
    }

    /** 按候选 key 顺序读取 ToolResult 中已有排行列表，不重查 SQL。 */
    private static List<Map<String, Object>> firstNonEmptyRowList(Map<String, Object> overview, String... keys) {
        if (keys == null) {
            return List.of();
        }
        for (String k : keys) {
            List<Map<String, Object>> rows = castRowList(overview.get(k));
            if (!rows.isEmpty()) {
                return rows;
            }
        }
        return List.of();
    }

    private static void splitTopRows(List<Map<String, Object>> ordered,
            List<Map<String, Object>> focusRows,
            List<Map<String, Object>> secondaryRows) {
        if (ordered == null || ordered.isEmpty()) {
            return;
        }
        focusRows.add(copyRowShallow(ordered.get(0)));
        for (int i = 1; i < ordered.size(); i++) {
            secondaryRows.add(copyRowShallow(ordered.get(i)));
        }
    }

    private static Map<String, Object> buildSummary(Map<String, Object> overview) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("totalAmount", parseDoubleLoose(overview.get("totalPurchaseAmount")));
        Object cnt = overview.get("purchaseOrderCount");
        if (cnt instanceof Number n) {
            m.put("totalCount", n.intValue());
        } else if (cnt != null) {
            try {
                m.put("totalCount", Integer.parseInt(cnt.toString().trim()));
            } catch (Exception e) {
                m.put("totalCount", 0);
            }
        } else {
            m.put("totalCount", 0);
        }
        appendPurchaseMethodSummary(m, overview);
        return m;
    }

    private static void appendPurchaseMethodSummary(Map<String, Object> summary, Map<String, Object> overview) {
        Object br = overview.get("purchaseMethodBreakdown");
        if (!(br instanceof List<?> list)) {
            return;
        }
        BigDecimalHolder selfAmt = new BigDecimalHolder();
        BigDecimalHolder supAmt = new BigDecimalHolder();
        int selfLines = 0;
        int supLines = 0;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> row)) {
                continue;
            }
            Object lab = row.get("label");
            String label = lab == null ? "" : lab.toString().trim();
            Object amtY = row.get("amountYuan");
            Object lc = row.get("lineCount");
            double amt = parseDoubleLoose(amtY);
            int lines = lc instanceof Number ? ((Number) lc).intValue() : parseIntLoose(lc);
            if ("自采".equals(label)) {
                selfAmt.add(amt);
                selfLines += lines;
            } else if ("供货商采购".equals(label)) {
                supAmt.add(amt);
                supLines += lines;
            }
        }
        summary.put("selfPurchaseAmount", selfAmt.value);
        summary.put("supplierPurchaseAmount", supAmt.value);
        summary.put("selfPurchaseLineCount", selfLines);
        summary.put("supplierPurchaseLineCount", supLines);
    }

    private static final class BigDecimalHolder {
        double value;

        void add(double v) {
            value += v;
        }
    }

    private static int parseIntLoose(Object v) {
        if (v == null) {
            return 0;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(v.toString().trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static double parseDoubleLoose(Object v) {
        if (v == null) {
            return 0.0;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(v.toString().trim());
        } catch (Exception e) {
            return 0.0;
        }
    }

    private static String resolveScopeLabel(Map<String, Object> overview, AiResolvedQueryContext rq) {
        Object b = overview.get("queryScopeBanner");
        if (b != null && !b.toString().isBlank()) {
            return b.toString().trim();
        }
        if (rq != null && rq.getQueryScopeBanner() != null && !rq.getQueryScopeBanner().isBlank()) {
            return rq.getQueryScopeBanner().trim();
        }
        return "";
    }

    private static String resolveTimeLabel(AiRunState state, AiResolvedQueryContext rq) {
        if (rq != null && rq.getTimeWindowLabel() != null && !rq.getTimeWindowLabel().isBlank()) {
            return rq.getTimeWindowLabel().trim();
        }
        String start = state.getStatStartDate();
        String end = state.getStatEndDate();
        if (start != null && end != null && !start.isBlank() && !end.isBlank()) {
            return start + " 至 " + end;
        }
        return "";
    }

    private static boolean overviewHasBlockingError(Map<String, Object> overview) {
        if (overview == null || overview.isEmpty()) {
            return false;
        }
        Object err = overview.get("error");
        return err != null && !err.toString().isBlank();
    }

    /**
     * 少数链路会把 Tool payload 的 {@code data} 落成 JSON 字符串；此处尽力还原为 Map 再走 purchaseOverview 路径。
     */
    private static Object unwrapDataMaybeJsonString(Object data) {
        if (data instanceof String s && !s.isBlank()) {
            try {
                return JSON.parseObject(s);
            } catch (Exception ignore) {
                return data;
            }
        }
        return data;
    }

    /**
     * 浅层遍历 Map 值，查找嵌套的 {@code purchaseOverview}（兼容双重 envelope 等异常形状）。
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepFindPurchaseOverview(Object node, int depthLeft) {
        if (depthLeft <= 0 || node == null) {
            return Map.of();
        }
        if (node instanceof Map<?, ?> m) {
            Object po = m.get("purchaseOverview");
            if (po instanceof Map<?, ?> pom && !pom.isEmpty()) {
                return new LinkedHashMap<>((Map<String, Object>) pom);
            }
            for (Object v : m.values()) {
                Map<String, Object> hit = deepFindPurchaseOverview(v, depthLeft - 1);
                if (!hit.isEmpty()) {
                    return hit;
                }
            }
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractPurchaseOverviewPayload(AiRunState state) {
        Object env = state.getToolResults().get(AiBusinessToolIds.PURCHASE_OVERVIEW);
        if (!(env instanceof Map)) {
            return Map.of();
        }
        Map<String, Object> envMap = (Map<String, Object>) env;
        Object data = unwrapDataMaybeJsonString(envMap.get("data"));
        if (data instanceof Map<?, ?> dm) {
            Object po = dm.get("purchaseOverview");
            if (po instanceof Map<?, ?> pom) {
                Map<String, Object> raw = (Map<String, Object>) pom;
                return raw.isEmpty() ? Map.of() : new LinkedHashMap<>(raw);
            }
            // 兼容：data 即 overview（扁平字段）
            Map<String, Object> asDataMap = (Map<String, Object>) dm;
            if (asDataMap.containsKey("totalPurchaseAmount") || asDataMap.containsKey("purchaseOrderCount")) {
                return new LinkedHashMap<>(asDataMap);
            }
        }
        Object poTop = envMap.get("purchaseOverview");
        if (poTop instanceof Map<?, ?> pom) {
            Map<String, Object> raw = (Map<String, Object>) pom;
            return raw.isEmpty() ? Map.of() : new LinkedHashMap<>(raw);
        }
        Map<String, Object> deep = deepFindPurchaseOverview(envMap, 5);
        return deep.isEmpty() ? Map.of() : deep;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castRowList(Object v) {
        if (!(v instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) {
                out.add((Map<String, Object>) m);
            }
        }
        return out;
    }

    private static LinkedHashMap<String, Object> copyRowShallow(Map<String, Object> row) {
        return row == null ? new LinkedHashMap<>() : new LinkedHashMap<>(row);
    }
}
