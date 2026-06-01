package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.constants.AiInsightDishProfitScope;
import com.nongxinle.service.GbDepFoodBusinessInsightService;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 营业额卡底部 Agent 只读 fact pack：本期 P + 约 30 天基线 B + 日历对比 C（辅助）。
 * 候选池按相对「平时」的变化与影响额选取，不按本期销量 Top 排序。
 */
final class BusinessOverviewDishSalesReasonFactBuilder {

    static final int BASELINE_DAY_COUNT = 30;
    /** LLM fact pack 菜品行上限；优先保留本期有销量与 baseline-only 菜。 */
    static final int FACT_PACK_DISH_ROW_CAP = 150;
    static final double REVENUE_SIMILAR_THRESHOLD = 0.05;
    static final double USUAL_SELLER_PERCENTILE = 0.75;

    static final String TAG_SURGE = "SURGE";
    static final String TAG_USUAL_UNDERPERFORM = "USUAL_UNDERPERFORM";
    static final String TAG_HIGH_IMPACT = "HIGH_IMPACT";
    static final String TAG_ZERO_THIS_PERIOD = "ZERO_THIS_PERIOD";

    static final String DIR_HIGHER = "HIGHER";
    static final String DIR_LOWER = "LOWER";
    static final String DIR_SIMILAR = "SIMILAR";
    static final String DIR_UNKNOWN = "UNKNOWN";

    static final String CHANGE_UP = "UP";
    static final String CHANGE_DOWN = "DOWN";
    static final String CHANGE_FLAT = "FLAT";

    private BusinessOverviewDishSalesReasonFactBuilder() {}

    static Map<String, Object> build(
            AiRunState state,
            BusinessStatusCardBuildRequest req,
            GbDepFoodBusinessInsightService insightService,
            ToolDepartmentResolutionSupport departmentResolutionSupport) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (state == null || insightService == null || req == null) {
            return out;
        }
        Integer disId = resolveDisId(state);
        Integer depFatherId = resolveDepFatherId(state, departmentResolutionSupport);
        String end = req.getEndDate();
        if (disId == null || depFatherId == null || !StringUtils.hasText(end)) {
            return out;
        }
        String endDate = end.trim();
        String startDate = StringUtils.hasText(req.getStartDate()) ? req.getStartDate().trim() : endDate;
        long periodDayCount = req.getPeriodDayCount() != null && req.getPeriodDayCount() > 0
                ? req.getPeriodDayCount()
                : dayCountInclusive(startDate, endDate);

        out.put("reportLabel", req.getReportLabel());
        out.put("timeExpression", req.getTimeExpression());
        out.put("timeLabel", req.getTimeLabel());
        out.put("startDate", startDate);
        out.put("endDate", endDate);
        out.put("periodDayCount", periodDayCount);
        out.put("compareLabel", req.getCompareLabel());

        Map<String, Object> periodInsight = loadInsight(insightService, disId, depFatherId, startDate, endDate);
        BigDecimal periodRevenue = extractTotalRevenue(periodInsight);
        if (periodRevenue == null
                && state.getRevenueAnswerPlan() != null
                && state.getRevenueAnswerPlan().getSummary() != null) {
            periodRevenue = nz(parseDecimal(state.getRevenueAnswerPlan().getSummary().get("totalRevenue")));
        }
        out.put("periodRevenue", formatDecimal(periodRevenue));

        LocalDate periodStart = LocalDate.parse(startDate);
        LocalDate baselineEnd = periodStart.minusDays(1);
        LocalDate baselineStart = baselineEnd.minusDays(BASELINE_DAY_COUNT - 1L);
        String baselineStartStr = baselineStart.toString();
        String baselineEndStr = baselineEnd.toString();
        long baselineDayCount = dayCountInclusive(baselineStartStr, baselineEndStr);

        Map<String, Object> baselineInsight =
                loadInsight(insightService, disId, depFatherId, baselineStartStr, baselineEndStr);
        BigDecimal baselineRevenue = nz(extractTotalRevenue(baselineInsight));

        Map<String, DishAgg> periodByKey = aggregateDishRows(dishRows(periodInsight));
        Map<String, DishAgg> baselineByKey = aggregateDishRows(dishRows(baselineInsight));

        BigDecimal baselineDailyAvgRevenue =
                baselineDayCount > 0
                        ? baselineRevenue.divide(BigDecimal.valueOf(baselineDayCount), 4, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
        BigDecimal expectedRevenueFromBaseline =
                baselineDailyAvgRevenue.multiply(BigDecimal.valueOf(periodDayCount));
        BigDecimal revenueDelta =
                periodRevenue != null ? periodRevenue.subtract(expectedRevenueFromBaseline) : null;
        Double revenueDeltaPercent = revenueDeltaPercent(revenueDelta, expectedRevenueFromBaseline);
        String revenueDirection =
                resolveRevenueDirection(periodRevenue, baselineRevenue, baselineDayCount, revenueDeltaPercent);

        Map<String, Object> baselineWindow = new LinkedHashMap<>();
        baselineWindow.put("startDate", baselineStartStr);
        baselineWindow.put("endDate", baselineEndStr);
        baselineWindow.put("dayCount", baselineDayCount);
        out.put("baselineWindow", baselineWindow);
        out.put("baselineDailyAvgRevenue", formatDecimal(baselineDailyAvgRevenue));
        out.put("expectedRevenueFromBaseline", formatDecimal(expectedRevenueFromBaseline));
        out.put("revenueDelta", formatDecimal(revenueDelta));
        out.put("revenueDeltaPercent", revenueDeltaPercent);
        out.put("revenueDirection", revenueDirection);

        Map<String, Object> analysisCaliber = new LinkedHashMap<>();
        analysisCaliber.put("baselineMode", "ROLLING_30D_BEFORE_PERIOD");
        analysisCaliber.put(
                "dishCompareUnit",
                "periodQty vs expectedPeriodQty (baselineDailyAvg * periodDayCount)");
        analysisCaliber.put(
                "note",
                "平时=查询开始前连续约"
                        + BASELINE_DAY_COUNT
                        + "天日均，按本期天数换算应有量");
        out.put("analysisCaliber", analysisCaliber);

        String compareStart = req.getCompareStartDate();
        String compareEnd = req.getCompareEndDate();
        if (StringUtils.hasText(compareStart) && StringUtils.hasText(compareEnd)) {
            Map<String, Object> compareInsight =
                    loadInsight(insightService, disId, depFatherId, compareStart.trim(), compareEnd.trim());
            out.put("comparePeriodRevenue", formatDecimal(extractTotalRevenue(compareInsight)));
            out.put("comparePeriodStartDate", compareStart.trim());
            out.put("comparePeriodEndDate", compareEnd.trim());
            out.put(
                    "comparePeriodDayCount",
                    dayCountInclusive(compareStart.trim(), compareEnd.trim()));
        } else {
            out.put("comparePeriodRevenue", null);
            out.put("comparePeriodStartDate", null);
            out.put("comparePeriodEndDate", null);
            out.put("comparePeriodDayCount", null);
        }

        BigDecimal usualSellerThreshold =
                percentileBaselineDailyQty(baselineByKey, baselineDayCount, USUAL_SELLER_PERCENTILE);
        List<DishCompareRow> allRows = buildCompareRows(
                periodByKey, baselineByKey, baselineDayCount, periodDayCount, usualSellerThreshold);
        FactPackAssembly assembly = assembleFactPackRows(allRows);

        out.put("dishCompareCandidates", assembly.rows());
        out.put("periodDishSales", assembly.rows());
        out.put("factPackDiagnostics", buildDiagnostics(assembly, baselineStartStr, baselineEndStr, baselineDayCount));

        Map<String, Object> selectionHints = new LinkedHashMap<>();
        selectionHints.put("maxItemsForLlm", 5);
        selectionHints.put("revenueDirection", revenueDirection);
        selectionHints.put(
                "note",
                "dishCompareCandidates 为完整事实行（非预筛 Top 菜），请从中选最多 5 条写入 items");
        selectionHints.put(
                "ifRevenueHigher",
                "优先选 candidateTag=SURGE 或 changeDirection=UP 且 |amountDiff| 大");
        selectionHints.put(
                "ifRevenueLower",
                "优先选 USUAL_UNDERPERFORM / ZERO_THIS_PERIOD / baselineOnly=true");
        selectionHints.put("ifRevenueSimilar", "整体接近平时，可选 |amountDiff| 最大的少量波动菜");
        out.put("selectionHints", selectionHints);

        return out;
    }

    static FactPackAssembly assembleFactPackRows(List<DishCompareRow> allRows) {
        if (allRows == null || allRows.isEmpty()) {
            return new FactPackAssembly(List.of(), 0, 0, 0, 0, false);
        }
        List<DishCompareRow> included = new ArrayList<>();
        for (DishCompareRow row : allRows) {
            if (shouldIncludeInFactPack(row)) {
                included.add(row);
            }
        }
        int periodActiveCount = countWhere(included, r -> r.periodQty.signum() > 0);
        int baselineOnlyCount =
                countWhere(included, r -> r.baselineTotalQty.signum() > 0 && r.periodQty.signum() == 0);

        boolean capApplied = included.size() > FACT_PACK_DISH_ROW_CAP;
        List<DishCompareRow> selected =
                capApplied ? capFactPackRows(included, FACT_PACK_DISH_ROW_CAP) : included;

        List<Map<String, Object>> mapped = new ArrayList<>();
        for (DishCompareRow row : sortFactPackRows(selected)) {
            mapped.add(toFactPackRowMap(row));
        }
        return new FactPackAssembly(
                mapped,
                allRows.size(),
                included.size(),
                periodActiveCount,
                baselineOnlyCount,
                capApplied);
    }

    static boolean shouldIncludeInFactPack(DishCompareRow row) {
        if (row == null) {
            return false;
        }
        if (row.periodQty.signum() > 0) {
            return true;
        }
        if (row.baselineTotalQty.signum() <= 0) {
            return false;
        }
        if (row.periodQty.signum() == 0) {
            return true;
        }
        return row.usualSeller && row.qtyDiff.signum() < 0;
    }

    static List<DishCompareRow> capFactPackRows(List<DishCompareRow> rows, int cap) {
        List<DishCompareRow> periodActive = new ArrayList<>();
        List<DishCompareRow> baselineOnly = new ArrayList<>();
        List<DishCompareRow> rest = new ArrayList<>();
        for (DishCompareRow row : rows) {
            if (row.periodQty.signum() > 0) {
                periodActive.add(row);
            } else if (row.baselineTotalQty.signum() > 0 && row.periodQty.signum() == 0) {
                baselineOnly.add(row);
            } else {
                rest.add(row);
            }
        }
        periodActive.sort(
                Comparator.comparing((DishCompareRow r) -> r.periodSalesAmount, Comparator.reverseOrder())
                        .thenComparing(r -> r.periodQty, Comparator.reverseOrder()));
        baselineOnly.sort(
                Comparator.comparing((DishCompareRow r) -> r.baselineTotalQty, Comparator.reverseOrder())
                        .thenComparing(r -> r.amountDiff.abs(), Comparator.reverseOrder()));
        rest.sort(
                Comparator.comparing((DishCompareRow r) -> r.amountDiff.abs(), Comparator.reverseOrder())
                        .thenComparing(r -> r.qtyDiff.abs(), Comparator.reverseOrder()));

        List<DishCompareRow> picked = new ArrayList<>();
        appendUniqueUpTo(picked, periodActive, cap);
        appendUniqueUpTo(picked, baselineOnly, cap);
        appendUniqueUpTo(picked, rest, cap);
        return picked;
    }

    private static void appendUniqueUpTo(List<DishCompareRow> target, List<DishCompareRow> source, int cap) {
        for (DishCompareRow row : source) {
            if (target.size() >= cap) {
                return;
            }
            if (!containsRow(target, row)) {
                target.add(row);
            }
        }
    }

    private static boolean containsRow(List<DishCompareRow> list, DishCompareRow row) {
        for (DishCompareRow r : list) {
            if (Objects.equals(r.dishName, row.dishName)) {
                return true;
            }
        }
        return false;
    }

    static List<DishCompareRow> sortFactPackRows(List<DishCompareRow> rows) {
        List<DishCompareRow> sorted = new ArrayList<>(rows);
        sorted.sort(
                Comparator.comparing((DishCompareRow r) -> r.amountDiff.abs(), Comparator.reverseOrder())
                        .thenComparing(r -> r.periodSalesAmount, Comparator.reverseOrder())
                        .thenComparing(r -> r.dishName, Comparator.nullsLast(String::compareTo)));
        return sorted;
    }

    static Map<String, Object> buildDiagnostics(
            FactPackAssembly assembly,
            String baselineStart,
            String baselineEnd,
            long baselineDayCount) {
        Map<String, Object> diag = new LinkedHashMap<>();
        diag.put("dishCompareRowCount", assembly.rows().size());
        diag.put("unionDishCount", assembly.unionDishCount());
        diag.put("includedBeforeCapCount", assembly.includedBeforeCapCount());
        diag.put("periodActiveDishCount", assembly.periodActiveCount());
        diag.put("baselineOnlyDishCount", assembly.baselineOnlyCount());
        diag.put("includesBaselineOnlyDishes", assembly.baselineOnlyCount() > 0);
        diag.put("rowCapApplied", assembly.rowCapApplied());
        diag.put("rowCapLimit", FACT_PACK_DISH_ROW_CAP);
        diag.put("baselineWindowStartDate", baselineStart);
        diag.put("baselineWindowEndDate", baselineEnd);
        diag.put("baselineDayCount", baselineDayCount);
        return diag;
    }

    private static int countWhere(List<DishCompareRow> rows, java.util.function.Predicate<DishCompareRow> pred) {
        int n = 0;
        for (DishCompareRow row : rows) {
            if (pred.test(row)) {
                n++;
            }
        }
        return n;
    }

    record FactPackAssembly(
            List<Map<String, Object>> rows,
            int unionDishCount,
            int includedBeforeCapCount,
            int periodActiveCount,
            int baselineOnlyCount,
            boolean rowCapApplied) {}

    private static List<DishCompareRow> buildCompareRows(
            Map<String, DishAgg> periodByKey,
            Map<String, DishAgg> baselineByKey,
            long baselineDayCount,
            long periodDayCount,
            BigDecimal usualSellerThreshold) {
        List<String> keys = new ArrayList<>();
        keys.addAll(periodByKey.keySet());
        for (String k : baselineByKey.keySet()) {
            if (!keys.contains(k)) {
                keys.add(k);
            }
        }
        BigDecimal baselineDays = BigDecimal.valueOf(Math.max(1, baselineDayCount));
        BigDecimal periodDays = BigDecimal.valueOf(Math.max(1, periodDayCount));

        List<DishCompareRow> rows = new ArrayList<>();
        for (String key : keys) {
            DishAgg period = periodByKey.get(key);
            DishAgg baseline = baselineByKey.get(key);
            String dishName =
                    firstNonBlank(
                            period != null ? period.dishName : null,
                            baseline != null ? baseline.dishName : null);
            if (!StringUtils.hasText(dishName)) {
                continue;
            }
            BigDecimal periodQty = period != null ? period.totalQty : BigDecimal.ZERO;
            BigDecimal periodAmount = period != null ? period.totalAmount : BigDecimal.ZERO;
            BigDecimal baselineTotalQty = baseline != null ? baseline.totalQty : BigDecimal.ZERO;
            BigDecimal baselineTotalAmount = baseline != null ? baseline.totalAmount : BigDecimal.ZERO;
            BigDecimal baselineDailyAvgQty =
                    baselineTotalQty.divide(baselineDays, 4, RoundingMode.HALF_UP);
            BigDecimal baselineDailyAvgAmount =
                    baselineTotalAmount.divide(baselineDays, 4, RoundingMode.HALF_UP);
            BigDecimal expectedPeriodQty = baselineDailyAvgQty.multiply(periodDays);
            BigDecimal expectedPeriodAmount = baselineDailyAvgAmount.multiply(periodDays);
            BigDecimal qtyDiff = periodQty.subtract(expectedPeriodQty);
            BigDecimal amountDiff = periodAmount.subtract(expectedPeriodAmount);

            if (periodQty.signum() == 0
                    && baselineTotalQty.signum() == 0
                    && periodAmount.signum() == 0
                    && baselineTotalAmount.signum() == 0) {
                continue;
            }

            boolean usualSeller =
                    baselineDailyAvgQty.compareTo(BigDecimal.ZERO) > 0
                            && baselineDailyAvgQty.compareTo(usualSellerThreshold) >= 0;
            String changeDirection = resolveChangeDirection(qtyDiff);
            String candidateTag = resolveCandidateTag(usualSeller, periodQty, qtyDiff);

            DishCompareRow row = new DishCompareRow();
            row.dishName = dishName;
            row.foodId = period != null ? period.foodId : baseline != null ? baseline.foodId : null;
            row.periodQty = periodQty;
            row.periodSalesAmount = periodAmount;
            row.baselineTotalQty = baselineTotalQty;
            row.baselineTotalAmount = baselineTotalAmount;
            row.baselineDailyAvgQty = baselineDailyAvgQty;
            row.baselineDailyAvgAmount = baselineDailyAvgAmount;
            row.expectedPeriodQty = expectedPeriodQty;
            row.expectedPeriodAmount = expectedPeriodAmount;
            row.qtyDiff = qtyDiff;
            row.amountDiff = amountDiff;
            row.usualSeller = usualSeller;
            row.changeDirection = changeDirection;
            row.candidateTag = candidateTag;
            rows.add(row);
        }
        return rows;
    }

    private static Map<String, Object> toFactPackRowMap(DishCompareRow row) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("dishName", row.dishName);
        if (row.foodId != null) {
            item.put("foodId", row.foodId);
        }
        item.put("candidateTag", row.candidateTag);
        item.put("changeDirection", row.changeDirection);
        item.put("usualSeller", row.usualSeller);
        item.put("presenceInPeriod", row.periodQty.signum() > 0);
        item.put("baselineOnly", row.baselineTotalQty.signum() > 0 && row.periodQty.signum() == 0);
        item.put("periodQty", formatDecimal(row.periodQty));
        item.put("periodSalesAmount", formatDecimal(row.periodSalesAmount));
        item.put("baselineTotalQty", formatDecimal(row.baselineTotalQty));
        item.put("baselineTotalAmount", formatDecimal(row.baselineTotalAmount));
        item.put("baselineDailyAvgQty", formatDecimal(row.baselineDailyAvgQty));
        item.put("baselineDailyAvgAmount", formatDecimal(row.baselineDailyAvgAmount));
        item.put("expectedPeriodQty", formatDecimal(row.expectedPeriodQty));
        item.put("expectedPeriodAmount", formatDecimal(row.expectedPeriodAmount));
        item.put("qtyDiff", formatDecimal(row.qtyDiff));
        item.put("amountDiff", formatDecimal(row.amountDiff));
        return item;
    }

    static String resolveCandidateTag(boolean usualSeller, BigDecimal periodQty, BigDecimal qtyDiff) {
        if (usualSeller && periodQty.signum() == 0) {
            return TAG_ZERO_THIS_PERIOD;
        }
        if (usualSeller && qtyDiff.signum() < 0) {
            return TAG_USUAL_UNDERPERFORM;
        }
        if (qtyDiff.signum() > 0) {
            return TAG_SURGE;
        }
        return TAG_HIGH_IMPACT;
    }

    static String resolveChangeDirection(BigDecimal qtyDiff) {
        if (qtyDiff.compareTo(new BigDecimal("0.01")) > 0) {
            return CHANGE_UP;
        }
        if (qtyDiff.compareTo(new BigDecimal("-0.01")) < 0) {
            return CHANGE_DOWN;
        }
        return CHANGE_FLAT;
    }

    static String resolveRevenueDirection(
            BigDecimal periodRevenue,
            BigDecimal baselineRevenue,
            long baselineDayCount,
            Double revenueDeltaPercent) {
        if (periodRevenue == null || baselineDayCount < 7 || baselineRevenue.signum() == 0) {
            return DIR_UNKNOWN;
        }
        if (revenueDeltaPercent == null) {
            return DIR_UNKNOWN;
        }
        if (Math.abs(revenueDeltaPercent) <= REVENUE_SIMILAR_THRESHOLD) {
            return DIR_SIMILAR;
        }
        return revenueDeltaPercent > 0 ? DIR_HIGHER : DIR_LOWER;
    }

    static Double revenueDeltaPercent(BigDecimal revenueDelta, BigDecimal expectedRevenue) {
        if (revenueDelta == null || expectedRevenue == null || expectedRevenue.signum() == 0) {
            return null;
        }
        return revenueDelta
                .divide(expectedRevenue, 4, RoundingMode.HALF_UP)
                .doubleValue();
    }

    static BigDecimal percentileBaselineDailyQty(
            Map<String, DishAgg> baselineByKey, long baselineDayCount, double percentile) {
        List<BigDecimal> dailyAvgs = new ArrayList<>();
        BigDecimal days = BigDecimal.valueOf(Math.max(1, baselineDayCount));
        for (DishAgg agg : baselineByKey.values()) {
            if (agg.totalQty.signum() > 0) {
                dailyAvgs.add(agg.totalQty.divide(days, 4, RoundingMode.HALF_UP));
            }
        }
        if (dailyAvgs.isEmpty()) {
            return BigDecimal.ZERO;
        }
        dailyAvgs.sort(Comparator.naturalOrder());
        int idx = (int) Math.ceil(percentile * dailyAvgs.size()) - 1;
        idx = Math.max(0, Math.min(dailyAvgs.size() - 1, idx));
        return dailyAvgs.get(idx);
    }

    private static Map<String, DishAgg> aggregateDishRows(List<Map<String, Object>> rows) {
        Map<String, DishAgg> byKey = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String dishName = dishName(row);
            if (!StringUtils.hasText(dishName)) {
                continue;
            }
            String key = dishKey(row, dishName);
            DishAgg agg = byKey.computeIfAbsent(key, k -> new DishAgg());
            agg.dishName = dishName;
            agg.foodId = foodId(row);
            agg.totalQty =
                    agg.totalQty.add(
                            nz(parseDecimal(firstPresent(row, "soldPortionsTotal", "salesQty", "gbDfSalesQuantity"))));
            agg.totalAmount =
                    agg.totalAmount.add(
                            nz(parseDecimal(firstPresent(row, "listPriceRevenue", "salesAmount", "gbDfSalesAmount"))));
        }
        return byKey;
    }

    private static String dishKey(Map<String, Object> row, String dishName) {
        Integer fid = foodId(row);
        return fid != null ? "id:" + fid : "name:" + dishName;
    }

    private static Integer foodId(Map<String, Object> row) {
        Object v = row.get("foodId");
        if (v instanceof Integer i) {
            return i;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        return null;
    }

    private static BigDecimal extractTotalRevenue(Map<String, Object> insight) {
        if (insight == null) {
            return null;
        }
        BigDecimal direct = parseDecimal(insight.get("totalListPriceRevenue"));
        if (direct != null) {
            return direct;
        }
        Object summary = insight.get("businessInsightSummary");
        if (summary instanceof Map<?, ?> m) {
            return parseDecimal(m.get("totalListPriceRevenue"));
        }
        return null;
    }

    private static long dayCountInclusive(String start, String end) {
        return Math.max(1, ChronoUnit.DAYS.between(LocalDate.parse(start), LocalDate.parse(end)) + 1);
    }

    private static Map<String, Object> loadInsight(
            GbDepFoodBusinessInsightService insightService,
            Integer disId,
            Integer depFatherId,
            String start,
            String end) {
        try {
            return insightService.buildInsight(disId, depFatherId, start, end, null);
        } catch (RuntimeException e) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> dishRows(Map<String, Object> insight) {
        if (insight == null || !(insight.get("dishes") instanceof List<?> list)) {
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

    private static String dishName(Map<String, Object> row) {
        return firstNonBlank(row.get("dishName"), row.get("foodName"), row.get("gbDfName"));
    }

    private static Object firstPresent(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            if (row.get(key) != null) {
                return row.get(key);
            }
        }
        return null;
    }

    private static Integer resolveDisId(AiRunState state) {
        if (state.getDistributerId() != null && state.getDistributerId() > 0) {
            return state.getDistributerId().intValue();
        }
        return null;
    }

    private static Integer resolveDepFatherId(
            AiRunState state, ToolDepartmentResolutionSupport departmentResolutionSupport) {
        if (BusinessToolExecutionNode.shouldRouteGroupWideDishInsight(state)) {
            return AiInsightDishProfitScope.DEP_FATHER_ID_GROUP_WIDE_Mendian_AGGREGATE_UNDER_DIS_ID;
        }
        Long raw = state.getDepartmentId();
        Long resolved = departmentResolutionSupport != null
                ? departmentResolutionSupport.resolveBuildInsightDepartmentFatherId(state, raw)
                : raw;
        return resolved != null && resolved > 0 ? resolved.intValue() : null;
    }

    private static BigDecimal parseDecimal(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        if (v instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(v.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String formatDecimal(BigDecimal v) {
        if (v == null) {
            return null;
        }
        return v.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static String firstNonBlank(Object... values) {
        for (Object v : values) {
            if (v == null) {
                continue;
            }
            String s = v.toString().trim();
            if (StringUtils.hasText(s)) {
                return s;
            }
        }
        return null;
    }

    static final class DishAgg {
        String dishName;
        Integer foodId;
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
    }

    static final class DishCompareRow {
        String dishName;
        Integer foodId;
        BigDecimal periodQty;
        BigDecimal periodSalesAmount;
        BigDecimal baselineTotalQty;
        BigDecimal baselineTotalAmount;
        BigDecimal baselineDailyAvgQty;
        BigDecimal baselineDailyAvgAmount;
        BigDecimal expectedPeriodQty;
        BigDecimal expectedPeriodAmount;
        BigDecimal qtyDiff;
        BigDecimal amountDiff;
        boolean usualSeller;
        String changeDirection;
        String candidateTag;
    }
}
