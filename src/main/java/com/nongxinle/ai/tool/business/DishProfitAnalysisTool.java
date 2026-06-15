package com.nongxinle.ai.tool.business;

import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.graph.business.MenuPortfolioSalesEvidenceSupport;
import com.nongxinle.ai.tool.AiTool;
import com.nongxinle.ai.tool.ToolRequest;
import com.nongxinle.ai.tool.ToolResult;
import com.nongxinle.service.GbDepFoodBusinessInsightService;
import com.nongxinle.utils.GbDepartmentGoodsStockReduceSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import java.util.Collection;
import java.util.Collections;

import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_AI_ROLE_CODE;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_RESOLVED_DEPARTMENT_IDS;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_DISH_NAME_FOCUS_HINT;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_DISH_PROFIT_STRUCTURED_DETAIL;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_QUERY_DISTRIBUTER_ID;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_QUERY_REAL_DEPARTMENT_IDS;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_STORE_TO_DEPARTMENT_IDS;

/**
 * 菜品毛利/经营透视：直接复用 {@link GbDepFoodBusinessInsightService#buildInsight}，
 * 供 {@code dish_profit_path}、{@code dish_sales_query_path}（D-8）与成本链结构化汇总。
 * Historical removed：独立 {@code dish_sales_query} Tool（{@code DishSalesQueryTool}）已删除，D-8 与成本链均执行本 Tool。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DishProfitAnalysisTool implements AiTool {

    private static final int MAX_DISH_ROWS = 150;

    private final GbDepFoodBusinessInsightService depFoodBusinessInsightService;
    @Override
    public String name() {
        return AiBusinessToolIds.DISH_PROFIT_ANALYSIS;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ToolResult execute(ToolRequest request) {
        DishProfitToolScopeSupport.BaseArgs base = DishProfitToolScopeSupport.parseBaseArgs(request);
        Map<String, Object> args = base.args();
        Long dept = base.departmentFatherId();
        Long disLong = base.disId();
        String start = base.startDate();
        String stop = base.stopDate();
        String hint = str(args.get(AiBusinessToolIds.ARG_USER_QUESTION_HINT));
        String structuredDetail = str(args.get(ARG_DISH_PROFIT_STRUCTURED_DETAIL));
        String dishFocus = str(args.get(ARG_DISH_NAME_FOCUS_HINT));

        if (base.missingRequired()) {
            Map<String, Object> data = new LinkedHashMap<>();
            return ToolResult.builder()
                    .success(false)
                    .message("missing disId/departmentFatherId/dates")
                    .data(AiBusinessToolResponses.envelope(name(), false, false, start, stop, dept, disLong, data,
                            "参数不完整"))
                    .build();
        }

        DishProfitToolScopeSupport.ResolvedScope scope = DishProfitToolScopeSupport.resolveScope(disLong, dept, args);
        int disId = scope.disId();
        int depFatherIdInt = scope.depFatherIdInt();
        dept = scope.departmentFatherId();
        boolean groupWideAgg = scope.groupWideMendianAggregate();
        List<Integer> resolvedAllow = DishProfitToolScopeSupport.normalizeResolvedDeptIds(args.get(ARG_RESOLVED_DEPARTMENT_IDS));
        Collection<Integer> scopeAllow = resolvedAllow.isEmpty() ? null : resolvedAllow;
        String aiRoleCodeLog = str(args.get(ARG_AI_ROLE_CODE));

        try {
            Map<String, Object> insight =
                    depFoodBusinessInsightService.buildInsight(disId, depFatherIdInt, start, stop, null, scopeAllow);
            @SuppressWarnings("unchecked")
            List<Integer> scopeDepIdsProbe =
                    insight.get("scopeDepIds") instanceof List ? (List<Integer>) insight.get("scopeDepIds") : List.of();

            Map<String, Object> cov = coverageMap(insight.get("dishProfitStoreCoverage"));
            List<Map<String, Object>> dishes = (List<Map<String, Object>>) insight.get("dishes");

            List<String> visNames = flattenStoreNames(cov.get("coveredStores"));
            List<String> missNames = flattenStoreNames(cov.get("dataMissingStores"));
            @SuppressWarnings("unchecked")
            List<Object> deptSalesProbe =
                    cov.get("dishSalesDepartmentIds") instanceof List ? (List<Object>) cov.get("dishSalesDepartmentIds")
                            : List.of();

            List<String> sampleKeys = dishSampleFoodKeys(dishes, 10);
            List<String> sampleNames = dishSampleNames(dishes, 10);

            log.info("[DishProfit-debug] runId={} userId={} aiRoleCode={} disId={} requestDepartmentFatherId={} "
                            + "groupWideAgg={} resolvedScopeAllowFilterCount={} resolvedScopeDeptIds(forLogOnly)={} "
                            + "insightScopeSubDepCount={} dishSalesDeptIdsDistinct={} dishSalesRowCount={} resolvedDishCount={} "
                            + "sampleFoodIdKeys={} sampleDishNamesFromFoodNameField={} coveredStoreNamesSubset={} dataMissingStoresSubset={}",
                    request.getRunId(), request.getUserId(), aiRoleCodeLog, disId, depFatherIdInt, groupWideAgg,
                    resolvedAllow.size(), truncateList(resolvedAllow, 48), scopeDepIdsProbe.size(),
                    deptSalesProbe.size(), cov.get("dishSalesRowCount"),
                    dishes == null ? 0 : dishes.size(), sampleKeys, sampleNames,
                    truncateListStrings(visNames, 24), truncateListStrings(missNames, 24));

            boolean truncatedNote = dishes != null && dishes.size() > MAX_DISH_ROWS;
            List<Map<String, Object>> slice = new ArrayList<>();
            if (dishes != null) {
                slice.addAll(dishes.subList(0, Math.min(dishes.size(), MAX_DISH_ROWS)));
            }
            List<Map<String, Object>> presented = applyDishProfitPresentation(slice, structuredDetail, dishFocus);
            @SuppressWarnings("unchecked")
            Map<String, Object> bisRaw = insight.get("businessInsightSummary") instanceof Map
                    ? (Map<String, Object>) insight.get("businessInsightSummary")
                    : null;

            List<Map<String, Object>> compactRows = new ArrayList<>();
            BigDecimal totalRev = BigDecimal.ZERO;
            BigDecimal totalTheory = BigDecimal.ZERO;
            BigDecimal totalActual = BigDecimal.ZERO;
            BigDecimal totalActual123 = BigDecimal.ZERO;
            BigDecimal qtyAll = BigDecimal.ZERO;

            for (Map<String, Object> raw : presented) {
                Map<String, Object> row = summarizeDishRow(raw);
                compactRows.add(row);
                totalRev = totalRev.add(GbDepartmentGoodsStockReduceSupport.coerceDecimal(row.get("actualRevenue")));
                totalTheory = totalTheory.add(
                        GbDepartmentGoodsStockReduceSupport.coerceDecimal(row.get("theoryCostAmount")));
                totalActual = totalActual.add(
                        GbDepartmentGoodsStockReduceSupport.coerceDecimal(row.get("actualCostAmount")));
                totalActual123 = totalActual123.add(
                        GbDepartmentGoodsStockReduceSupport.coerceDecimal(row.get("actualCostTotalAmount123")));
                qtyAll = qtyAll.add(GbDepartmentGoodsStockReduceSupport.coerceDecimal(row.get("soldPortionsTotal")));
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("dishRows", compactRows);
            data.put("dishLineCountFull", dishes == null ? 0 : dishes.size());
            data.put("dishLineReturned", compactRows.size());
            data.put("actualRevenueTotal", nzPlain(totalRev));
            data.put("soldPortionsTotal", nzPlainQty(qtyAll));
            data.put("totalTheoreticalCost", nzPlain(totalTheory));
            data.put("totalActualCostType1", nzPlain(totalActual));
            data.put("totalActualCostTotalAmount123", nzPlain(totalActual123));
            BigDecimal gp123 = totalRev.subtract(totalActual123).setScale(2, RoundingMode.HALF_UP);
            data.put("portfolioGrossProfitAmountType123", nzPlain(gp123));
            BigDecimal gp = totalRev.subtract(totalActual).setScale(2, RoundingMode.HALF_UP);
            data.put("portfolioGrossProfitAmount", nzPlain(gp));
            data.put("userQuestionHint", hint);
            data.put("dishProfitStructuredDetail", structuredDetail);
            data.put("dishNameFocusHint", dishFocus);
            Map<String, Object> targetInsightRow = resolveTargetDishInsightRow(dishes, presented, dishFocus);
            if (targetInsightRow != null && !targetInsightRow.isEmpty()) {
                data.put("targetDishInsightRow", targetInsightRow);
            }
            data.put("buildInsightUsed", true);
            data.put("businessInsightSummary", bisRaw);
            String portfolioBlended =
                    bisRaw == null ? null : str(bisRaw.get("blendedGrossMarginRateOnListPrice"));
            data.put("portfolioGrossMarginRateTheoryOnListPrice",
                    bisRaw == null ? null : bisRaw.get("blendedGrossMarginRateTheoryOnListPrice"));
            data.put("portfolioBlendedGrossMarginRateOnListPrice",
                    bisRaw == null ? null : bisRaw.get("blendedGrossMarginRateOnListPrice"));
            data.put("portfolioComprehensiveGrossMarginRateOnListPrice",
                    bisRaw == null ? null : bisRaw.get("comprehensiveGrossMarginRateOnListPrice"));
            if (portfolioBlended != null && !portfolioBlended.isEmpty()) {
                data.put("portfolioGrossMarginRate", portfolioBlended.contains("%") ? portfolioBlended : portfolioBlended + "%");
            } else {
                data.put("portfolioGrossMarginRate", "暂无");
            }
            data.put("salesDishCount", MenuPortfolioSalesEvidenceSupport.countSoldDishesFromRows(presented));
            data.put("riskLevel", bisRaw == null ? null : bisRaw.get("insightRiskLevel"));
            data.put("dishProfitStoreCoverage", insight.get("dishProfitStoreCoverage"));
            data.put("scopeOutboundSubtotals", insight.get("scopeOutboundSubtotals"));
            data.put("bossColumnHintsZh", insight.get("bossColumnHintsZh"));
            data.put("businessInsightSummaryChinese", insight.get("businessInsightSummaryChinese"));

            Map<String, Object> bir = new LinkedHashMap<>();
            bir.put("storeRootDepartmentFatherId", depFatherIdInt);
            bir.put("queryScopeKind", scope.queryScopeKind().isEmpty() ? null : scope.queryScopeKind());
            bir.put("queryStoreIds", new ArrayList<>(scope.queryStoreIds()));
            bir.put("queryRealDepartmentIds",
                    new ArrayList<>(DishProfitToolScopeSupport.normalizeResolvedDeptIds(args.get(ARG_QUERY_REAL_DEPARTMENT_IDS))));
            bir.put("queryDistributerId", toIntegerOrNull(args.get(ARG_QUERY_DISTRIBUTER_ID)));
            bir.put("storeToDepartmentIds", args.get(ARG_STORE_TO_DEPARTMENT_IDS));
            bir.put("buildInsightInputStoreRootIds",
                    scope.queryStoreIds().isEmpty() ? List.of(depFatherIdInt) : new ArrayList<>(scope.queryStoreIds()));
            bir.put("buildInsightInputDepartmentIdsAllowFilter",
                    scopeAllow == null ? Collections.emptyList() : new ArrayList<>(scopeAllow));
            bir.put("disId", disId);
            bir.put("startDate", start);
            bir.put("stopDate", stop);
            bir.put("scopeDepartmentIdsAllowFilter",
                    scopeAllow == null ? Collections.emptyList() : new ArrayList<>(scopeAllow));
            bir.put("effectiveSqlDepartmentIds", scopeAllow == null ? Collections.emptyList() : new ArrayList<>(scopeAllow));
            bir.put("sqlIdsSemantics",
                    "buildInsight allow 与 AiResolvedDataScope.dishProfitSqlDepartmentIds 同源：department_id IN，非门店列表");
            bir.put("groupWideMendianAggregate", groupWideAgg);
            bir.put("scopeDepIdsAfterBuildInsight", new ArrayList<>(scopeDepIdsProbe));
            data.put("buildInsightRequest", bir);
            data.put("usedBuildInsight", true);

            boolean mock = compactRows.isEmpty();
            String note = mock ? "无菜品行，请确认菜谱与区间内销量是否已维护"
                    : (truncatedNote ? "菜品行较多，本轮仅截取前 " + MAX_DISH_ROWS + " 条用于分析" : null);
            return ToolResult.builder()
                    .success(true)
                    .message(mock ? "empty_dishes" : "ok")
                    .data(AiBusinessToolResponses.envelope(name(), true, mock, start, stop, dept, disLong, data, note))
                    .build();
        } catch (Exception e) {
            log.warn("[DishProfitAnalysisTool] runId={}: {}", request.getRunId(), e.toString());
            Map<String, Object> data = new LinkedHashMap<>();
            data.putAll(AiBusinessToolResponses.mockPayload(e.getMessage()));
            return ToolResult.builder()
                    .success(false)
                    .message(e.getMessage())
                    .data(AiBusinessToolResponses.envelope(name(), false, true, start, stop, dept, disLong, data,
                            "查询异常：" + e.getMessage()))
                    .build();
        }
    }

    private static Map<String, Object> summarizeDishRow(Map<String, Object> raw) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("foodId", raw.get("foodId"));
        row.put("dishName", raw.getOrDefault("foodName", ""));
        row.put("actualRevenue", str(raw.get("actualRevenue")));
        row.put("soldPortionsTotal", str(raw.get("soldPortionsTotal")));
        row.put("theoryCostAmount", str(raw.get("theoryCostAmount")));
        row.put("actualCostAmount", str(raw.get("actualCostAmount")));
        row.put("productionActualCostAmount", str(raw.get("actualCostAmount")));
        row.put("grossMarginRateOnListPrice", raw.get("grossMarginRateOnListPrice"));
        row.put("grossMarginRateTheoryOnListPrice", raw.get("grossMarginRateTheoryOnListPrice"));
        row.put("blendedGrossMarginRateOnListPrice", raw.get("blendedGrossMarginRateOnListPrice"));
        row.put("actualCostTotalAmount123", raw.get("actualCostTotalAmount123"));
        row.put("totalActualCostAmount123", raw.get("actualCostTotalAmount123"));
        row.put("actualCostPerPortion123", raw.get("actualCostPerPortion123"));
        row.put("grossMarginLevel", raw.get("grossMarginLevel"));
        row.put("diffCostAmount", raw.get("diffCostAmount"));
        row.put("devianceRate", raw.get("devianceRate"));
        return row;
    }

    /**
     * 按结构化追问或点名菜收窄/重排展示行；汇总口径仍以 {@link GbDepFoodBusinessInsightService#buildInsight} 的
     * {@code businessInsightSummary} 为准（不在此重算集团/范围毛利率）。
     */
    private static List<Map<String, Object>> applyDishProfitPresentation(List<Map<String, Object>> rawSlice,
            String structuredWire, String dishFocus) {
        if (rawSlice == null || rawSlice.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> work = new ArrayList<>(rawSlice);
        if (StringUtils.hasText(dishFocus)) {
            String needle = dishFocus.trim();
            List<Map<String, Object>> narrowed = work.stream()
                    .filter(m -> str(m.get("foodName")).contains(needle))
                    .collect(Collectors.toList());
            // 焦点菜名与门店名/噪声对齐导致滤空时，回退为全量行，避免 dishRows 空而 insight 仍有 dishRowCount
            work = narrowed.isEmpty() ? new ArrayList<>(rawSlice) : narrowed;
        }
        if (work.isEmpty()) {
            return work;
        }
        String sw = structuredWire == null ? "" : structuredWire.trim();
        Comparator<Map<String, Object>> cmp = null;
        if (AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_RANKING_LOW_MARGIN.equals(sw)) {
            cmp = Comparator.comparing(DishProfitAnalysisTool::blendedMarginSortKeyAsc,
                    Comparator.nullsLast(Comparator.naturalOrder()));
        } else if (AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_RANKING_HIGH_MARGIN.equals(sw)) {
            cmp = Comparator.comparing(DishProfitAnalysisTool::blendedMarginSortKeyAsc,
                    Comparator.nullsLast(Comparator.naturalOrder())).reversed();
        } else if (AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_RANKING_HIGH_PROFIT_AMOUNT.equals(sw)) {
            cmp = Comparator.comparing(DishProfitAnalysisTool::grossProfitAmountSortKey,
                    Comparator.nullsLast(Comparator.naturalOrder())).reversed();
        } else if (AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_RANKING_LOW_PROFIT_AMOUNT.equals(sw)) {
            cmp = Comparator.comparing(DishProfitAnalysisTool::grossProfitAmountSortKey,
                    Comparator.nullsLast(Comparator.naturalOrder()));
        } else if (AiQuerySemanticLexicon.STRUCTURED_DISH_ACTUAL_COST_RANKING_HIGH.equals(sw)) {
            cmp = Comparator.comparing((Map<String, Object> m) ->
                    com.nongxinle.ai.graph.business.DishProfitActualCostSemanticsSupport.displayActualCost(m))
                    .reversed();
        } else if (AiQuerySemanticLexicon.STRUCTURED_DISH_GAP_RANKING_MAX.equals(sw)) {
            // 与 AnswerPlan / Harness 对齐：signed(actual - theory)，DESC = 实际高于理论最多优先
            cmp = Comparator.comparing(DishProfitAnalysisTool::theoryActualGapSignedAmount,
                    Comparator.nullsLast(Comparator.naturalOrder())).reversed();
        } else if (AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH.equals(sw)
                || AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_RANKING_HIGH.equals(sw)) {
            cmp = Comparator.comparing((Map<String, Object> m) ->
                    GbDepartmentGoodsStockReduceSupport.coerceDecimal(m.get("soldPortionsTotal")))
                    .reversed();
        } else if (AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_AMOUNT_RANKING_HIGH.equals(sw)) {
            cmp = Comparator.comparing((Map<String, Object> m) ->
                    GbDepartmentGoodsStockReduceSupport.coerceDecimal(m.get("actualRevenue")))
                    .reversed();
        } else if (AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_COUNT_RANKING_LOW.equals(sw)
                || AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_RANKING_LOW.equals(sw)) {
            cmp = Comparator.comparing((Map<String, Object> m) ->
                    GbDepartmentGoodsStockReduceSupport.coerceDecimal(m.get("soldPortionsTotal")));
        }
        if (cmp != null) {
            work.sort(cmp);
        }
        return work;
    }

    /** 标价收入 − 展示用实际成本（与 AnswerPlan grossProfitAmount 一致）。 */
    private static BigDecimal grossProfitAmountSortKey(Map<String, Object> raw) {
        BigDecimal rev = GbDepartmentGoodsStockReduceSupport.coerceDecimal(raw.get("actualRevenue"));
        BigDecimal actual =
                com.nongxinle.ai.graph.business.DishProfitActualCostSemanticsSupport.displayActualCost(raw);
        if (rev == null || actual == null) {
            return null;
        }
        return rev.subtract(actual);
    }

    private static BigDecimal blendedMarginSortKeyAsc(Map<String, Object> raw) {
        String primary = str(raw.get("blendedGrossMarginRateOnListPrice"));
        if (primary.isEmpty()) {
            primary = str(raw.get("grossMarginRateOnListPrice"));
        }
        if (primary.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(primary.trim());
        } catch (Exception e) {
            return null;
        }
    }

    /** 理论 vs 实际成本差额（signed）：displayActualCost − theoryCostAmount */
    private static BigDecimal theoryActualGapSignedAmount(Map<String, Object> raw) {
        return com.nongxinle.ai.graph.business.DishProfitActualCostSemanticsSupport.gapDisplayActualMinusTheory(raw);
    }

    private static Map<String, Object> resolveTargetDishInsightRow(
            List<Map<String, Object>> fullDishes, List<Map<String, Object>> presented, String dishFocus) {
        List<Map<String, Object>> searchPool = fullDishes != null && !fullDishes.isEmpty() ? fullDishes : presented;
        if (searchPool == null || searchPool.isEmpty()) {
            return null;
        }
        Map<String, Object> raw = null;
        if (presented != null && presented.size() == 1) {
            raw = presented.get(0);
        } else if (StringUtils.hasText(dishFocus)) {
            String needle = dishFocus.trim();
            for (Map<String, Object> row : searchPool) {
                if (row != null && str(row.get("foodName")).contains(needle)) {
                    raw = row;
                    break;
                }
            }
        }
        if (raw == null && searchPool.size() == 1) {
            raw = searchPool.get(0);
        }
        return raw == null ? null : summarizeInsightRow(raw);
    }

    private static Map<String, Object> summarizeInsightRow(Map<String, Object> raw) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("foodId", raw.get("foodId"));
        row.put("dishName", raw.getOrDefault("foodName", ""));
        row.put("listPrice", str(raw.get("listPrice")));
        row.put("grossMarginStandardTarget", str(raw.get("grossMarginStandardTarget")));
        row.put("grossMarginStandardBandLower", str(raw.get("grossMarginStandardBandLower")));
        row.put("grossMarginStandardBandUpper", str(raw.get("grossMarginStandardBandUpper")));
        row.put("actualCostPerPortion123", raw.get("actualCostPerPortion123"));
        row.put("blendedGrossMarginRateOnListPrice", raw.get("blendedGrossMarginRateOnListPrice"));
        row.put("grossMarginLevel", raw.get("grossMarginLevel"));
        return row;
    }

    private static String nzPlain(BigDecimal v) {
        if (v == null) {
            return "0";
        }
        return v.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static String nzPlainQty(BigDecimal v) {
        if (v == null) {
            return "0";
        }
        return v.stripTrailingZeros().toPlainString();
    }

    private static String str(Object v) {
        return v == null ? "" : v.toString().trim();
    }

    private static Integer toIntegerOrNull(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(v.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }


    private static Map<String, Object> coverageMap(Object raw) {
        if (!(raw instanceof Map)) {
            return Map.of();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> cast = (Map<String, Object>) raw;
        return cast;
    }

    private static List<String> flattenStoreNames(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> row) {
                Object sn = row.get("storeName");
                if (sn != null && !sn.toString().isBlank()) {
                    out.add(sn.toString().trim());
                }
            }
        }
        return out;
    }

    private static List<String> dishSampleFoodKeys(List<Map<String, Object>> dishes, int max) {
        if (dishes == null || dishes.isEmpty() || max <= 0) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (int i = 0; i < dishes.size() && i < max; i++) {
            Map<String, Object> m = dishes.get(i);
            out.add("foodId=" + m.get("foodId") + ",gbDepFoodId=" + m.get("gbDepFoodId"));
        }
        return out;
    }

    private static List<String> dishSampleNames(List<Map<String, Object>> dishes, int max) {
        if (dishes == null || dishes.isEmpty() || max <= 0) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (int i = 0; i < dishes.size() && i < max; i++) {
            Map<String, Object> m = dishes.get(i);
            Object n = m.get("foodName");
            out.add(n == null ? "" : n.toString().trim());
        }
        return out;
    }

    private static List<Integer> truncateList(List<Integer> in, int cap) {
        if (in == null || in.isEmpty() || cap <= 0) {
            return List.of();
        }
        if (in.size() <= cap) {
            return in;
        }
        return List.copyOf(in.subList(0, cap));
    }

    private static List<String> truncateListStrings(List<String> in, int cap) {
        if (in == null || in.isEmpty() || cap <= 0) {
            return List.of();
        }
        if (in.size() <= cap) {
            return in;
        }
        return List.copyOf(in.subList(0, cap));
    }
}
