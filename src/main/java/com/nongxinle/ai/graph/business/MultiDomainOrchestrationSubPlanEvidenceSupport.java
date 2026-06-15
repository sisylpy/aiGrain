package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.dto.business.DailyRevenueAnswerPlan;
import com.nongxinle.ai.dto.business.DishProfitAnswerPlan;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.dto.business.StockReduceAnswerPlan;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 多域编排子域 AnswerPlan 的 Diagnosis 证据合同 SSOT：按 planType 校验最低核心事实，
 * 区分 VALID / EXPLICIT_EMPTY / INVALID。
 */
public final class MultiDomainOrchestrationSubPlanEvidenceSupport {

    public static final String SHELL_PLAN_TYPE_UNKNOWN = "UNKNOWN";

    private static final Set<String> ORCHESTRATION_PURCHASE_PLAN_TYPES =
            Set.of(PurchaseAnswerPlan.TYPE_PURCHASE_OVERVIEW);

    private static final Set<String> ORCHESTRATION_STOCK_PLAN_TYPES =
            Set.of(StockReduceAnswerPlan.TYPE_STOCK_REDUCE_OVERVIEW);

    private static final Set<String> ORCHESTRATION_REVENUE_PLAN_TYPES =
            Set.of(DailyRevenueAnswerPlan.TYPE_REVENUE_OVERVIEW);

    private static final Set<String> ORCHESTRATION_DISH_PLAN_TYPES =
            Set.of(
                    DishProfitAnswerPlan.TYPE_BUSINESS_DIAGNOSIS_DISH_OVERVIEW,
                    DishProfitAnswerPlan.TYPE_AGGREGATED_DISH_PORTFOLIO_FALLBACK);

    private MultiDomainOrchestrationSubPlanEvidenceSupport() {}

    public static OrchestrationSubPlanEvidenceStatus evaluate(PurchaseAnswerPlan plan) {
        if (plan == null) {
            return OrchestrationSubPlanEvidenceStatus.INVALID;
        }
        if (isInvalidShell(plan.getDebug(), plan.getPlanType())) {
            return OrchestrationSubPlanEvidenceStatus.INVALID;
        }
        String planType = plan.getPlanType();
        if (ORCHESTRATION_PURCHASE_PLAN_TYPES.contains(planType)) {
            return evaluatePurchaseOverview(plan);
        }
        return evaluateGenericSummaryOrRows(plan.getSummary(), plan.getFocusRows());
    }

    public static OrchestrationSubPlanEvidenceStatus evaluate(StockReduceAnswerPlan plan) {
        if (plan == null) {
            return OrchestrationSubPlanEvidenceStatus.INVALID;
        }
        if (isInvalidShell(plan.getDebug(), plan.getPlanType())) {
            return OrchestrationSubPlanEvidenceStatus.INVALID;
        }
        String planType = plan.getPlanType();
        if (ORCHESTRATION_STOCK_PLAN_TYPES.contains(planType)) {
            return evaluateStockReduceOverview(plan);
        }
        return evaluateGenericSummaryOrRows(plan.getSummary(), plan.getFocusRows());
    }

    public static OrchestrationSubPlanEvidenceStatus evaluate(DailyRevenueAnswerPlan plan) {
        if (plan == null) {
            return OrchestrationSubPlanEvidenceStatus.INVALID;
        }
        if (isInvalidShell(plan.getDebug(), plan.getPlanType())) {
            return OrchestrationSubPlanEvidenceStatus.INVALID;
        }
        String planType = plan.getPlanType();
        if (ORCHESTRATION_REVENUE_PLAN_TYPES.contains(planType)) {
            return evaluateRevenueOverview(plan);
        }
        return evaluateGenericSummaryOrRows(plan.getSummary(), plan.getFocusRows());
    }

    public static OrchestrationSubPlanEvidenceStatus evaluate(DishProfitAnswerPlan plan) {
        if (plan == null) {
            return OrchestrationSubPlanEvidenceStatus.INVALID;
        }
        if (isInvalidShell(plan.getDebug(), plan.getPlanType())) {
            return OrchestrationSubPlanEvidenceStatus.INVALID;
        }
        if (DishProfitAnswerPlan.TYPE_DISH_PROFIT_RANKING_NO_DATA.equals(plan.getPlanType())) {
            return OrchestrationSubPlanEvidenceStatus.EXPLICIT_EMPTY;
        }
        String planType = plan.getPlanType();
        if (ORCHESTRATION_DISH_PLAN_TYPES.contains(planType)) {
            return evaluateDishOverview(plan);
        }
        return evaluateDishFocusRows(plan.getFocusRows());
    }

    public static boolean isConsumable(PurchaseAnswerPlan plan) {
        return evaluate(plan) == OrchestrationSubPlanEvidenceStatus.VALID;
    }

    public static boolean isConsumable(StockReduceAnswerPlan plan) {
        return evaluate(plan) == OrchestrationSubPlanEvidenceStatus.VALID;
    }

    public static boolean isConsumable(DailyRevenueAnswerPlan plan) {
        return evaluate(plan) == OrchestrationSubPlanEvidenceStatus.VALID;
    }

    public static boolean isConsumable(DishProfitAnswerPlan plan) {
        return evaluate(plan) == OrchestrationSubPlanEvidenceStatus.VALID;
    }

    public static boolean isShellPlanType(String planType) {
        if (!StringUtils.hasText(planType)) {
            return true;
        }
        return SHELL_PLAN_TYPE_UNKNOWN.equalsIgnoreCase(planType.trim());
    }

    private static OrchestrationSubPlanEvidenceStatus evaluatePurchaseOverview(PurchaseAnswerPlan plan) {
        Map<String, Object> summary = plan.getSummary();
        if (hasPositiveMetric(summary, "totalAmount")
                || hasPositiveMetric(summary, "selfPurchaseAmount")
                || hasPositiveMetric(summary, "supplierPurchaseAmount")
                || hasPositiveInt(summary, "totalCount")
                || hasRankingFocusEvidence(plan.getFocusRows())) {
            return OrchestrationSubPlanEvidenceStatus.VALID;
        }
        if (hasExplicitZeroPurchaseOverview(summary)) {
            return OrchestrationSubPlanEvidenceStatus.EXPLICIT_EMPTY;
        }
        return OrchestrationSubPlanEvidenceStatus.INVALID;
    }

    private static OrchestrationSubPlanEvidenceStatus evaluateStockReduceOverview(StockReduceAnswerPlan plan) {
        Map<String, Object> summary = plan.getSummary();
        Double grand = readDouble(summary, "grandTotalFourTypes");
        if (grand == null && hasOverviewAmountFocusRow(plan.getFocusRows())) {
            grand = firstFocusRowAmount(plan.getFocusRows());
        }
        if (grand != null && grand > 0) {
            return OrchestrationSubPlanEvidenceStatus.VALID;
        }
        if (grand != null
                && grand == 0
                && (hasExplicitZeroStockOverview(summary) || hasOverviewAmountFocusRow(plan.getFocusRows()))) {
            return OrchestrationSubPlanEvidenceStatus.EXPLICIT_EMPTY;
        }
        return OrchestrationSubPlanEvidenceStatus.INVALID;
    }

    private static OrchestrationSubPlanEvidenceStatus evaluateRevenueOverview(DailyRevenueAnswerPlan plan) {
        Map<String, Object> summary = plan.getSummary();
        if (hasPositiveMetric(summary, "totalRevenue") || hasOverviewRevenueFocusRow(plan.getFocusRows())) {
            return OrchestrationSubPlanEvidenceStatus.VALID;
        }
        if (hasExplicitZeroRevenueOverview(summary, plan.getFocusRows())) {
            return OrchestrationSubPlanEvidenceStatus.EXPLICIT_EMPTY;
        }
        return OrchestrationSubPlanEvidenceStatus.INVALID;
    }

    private static OrchestrationSubPlanEvidenceStatus evaluateDishOverview(DishProfitAnswerPlan plan) {
        OrchestrationSubPlanEvidenceStatus fromRows = evaluateDishFocusRows(plan.getFocusRows());
        if (fromRows != OrchestrationSubPlanEvidenceStatus.INVALID) {
            return fromRows;
        }
        return OrchestrationSubPlanEvidenceStatus.INVALID;
    }

    private static OrchestrationSubPlanEvidenceStatus evaluateDishFocusRows(List<Map<String, Object>> focusRows) {
        if (focusRows == null || focusRows.isEmpty()) {
            return OrchestrationSubPlanEvidenceStatus.INVALID;
        }
        for (Map<String, Object> row : focusRows) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            if (hasDishCoreFact(row)) {
                if (hasPositiveDishMetric(row)) {
                    return OrchestrationSubPlanEvidenceStatus.VALID;
                }
                if (hasExplicitZeroDishRow(row)) {
                    return OrchestrationSubPlanEvidenceStatus.EXPLICIT_EMPTY;
                }
            }
        }
        return OrchestrationSubPlanEvidenceStatus.INVALID;
    }

    private static OrchestrationSubPlanEvidenceStatus evaluateGenericSummaryOrRows(
            Map<String, Object> summary, List<Map<String, Object>> focusRows) {
        if (summary != null) {
            for (Object v : summary.values()) {
                if (v instanceof Number n && n.doubleValue() > 0) {
                    return OrchestrationSubPlanEvidenceStatus.VALID;
                }
            }
        }
        if (focusRows != null && !focusRows.isEmpty()) {
            return OrchestrationSubPlanEvidenceStatus.VALID;
        }
        return OrchestrationSubPlanEvidenceStatus.INVALID;
    }

    private static boolean isInvalidShell(Map<String, Object> debug, String planType) {
        if (isShellPlanType(planType)) {
            return true;
        }
        if (debug == null || debug.isEmpty()) {
            return false;
        }
        return hasText(debug.get("earlyReturnReason")) || hasText(debug.get("failureReason"));
    }

    private static boolean hasExplicitZeroPurchaseOverview(Map<String, Object> summary) {
        if (summary == null || summary.isEmpty()) {
            return false;
        }
        Double totalAmount = readDouble(summary, "totalAmount");
        Integer totalCount = readInt(summary, "totalCount");
        return totalAmount != null
                && totalAmount == 0
                && totalCount != null
                && totalCount == 0;
    }

    private static boolean hasExplicitZeroStockOverview(Map<String, Object> summary) {
        return readDouble(summary, "produceTotal") != null
                && readDouble(summary, "wasteTotal") != null
                && readDouble(summary, "lossTotal") != null
                && readDouble(summary, "returnTotal") != null;
    }

    private static boolean hasExplicitZeroRevenueOverview(
            Map<String, Object> summary, List<Map<String, Object>> focusRows) {
        Double totalRevenue = readDouble(summary, "totalRevenue");
        Integer days = readInt(summary, "days");
        if (totalRevenue != null && totalRevenue == 0 && days != null) {
            return true;
        }
        if (focusRows != null) {
            for (Map<String, Object> row : focusRows) {
                if (row == null) {
                    continue;
                }
                if ("overview".equals(stringLoose(row.get("role")))) {
                    Double rev = readDouble(row, "totalRevenue");
                    if (rev != null && rev == 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean hasDishCoreFact(Map<String, Object> row) {
        return hasText(row.get("dishName"))
                || hasText(row.get("actualRevenue"))
                || hasText(row.get("salesQuantity"))
                || hasText(row.get("blendedGrossMarginRateOnListPrice"));
    }

    private static boolean hasPositiveDishMetric(Map<String, Object> row) {
        return hasPositiveMetric(row, "actualRevenue")
                || hasPositiveMetric(row, "salesQuantity")
                || hasPositiveMetric(row, "actualCostAmount")
                || hasPositiveMetric(row, "theoryCostAmount");
    }

    private static boolean hasExplicitZeroDishRow(Map<String, Object> row) {
        Double salesQty = readDouble(row, "salesQuantity");
        Double revenue = readDouble(row, "actualRevenue");
        return salesQty != null && salesQty == 0 && revenue != null && revenue == 0;
    }

    private static boolean hasRankingFocusEvidence(List<Map<String, Object>> focusRows) {
        if (focusRows == null || focusRows.isEmpty()) {
            return false;
        }
        for (Map<String, Object> row : focusRows) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            if (hasText(row.get("goodsName"))
                    || hasText(row.get("supplierName"))
                    || hasText(row.get("storeName"))
                    || hasPositiveMetric(row, "totalPurchaseAmount")
                    || hasPositiveMetric(row, "purchaseSubtotal")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasOverviewAmountFocusRow(List<Map<String, Object>> focusRows) {
        if (focusRows == null) {
            return false;
        }
        for (Map<String, Object> row : focusRows) {
            if (row == null) {
                continue;
            }
            if (row.containsKey("amount") && readDouble(row, "amount") != null) {
                return true;
            }
        }
        return false;
    }

    private static Double firstFocusRowAmount(List<Map<String, Object>> focusRows) {
        if (focusRows == null) {
            return null;
        }
        for (Map<String, Object> row : focusRows) {
            if (row == null) {
                continue;
            }
            Double amount = readDouble(row, "amount");
            if (amount != null) {
                return amount;
            }
        }
        return null;
    }

    private static boolean hasOverviewRevenueFocusRow(List<Map<String, Object>> focusRows) {
        if (focusRows == null) {
            return false;
        }
        for (Map<String, Object> row : focusRows) {
            if (row == null) {
                continue;
            }
            if ("overview".equals(stringLoose(row.get("role"))) && hasPositiveMetric(row, "totalRevenue")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasPositiveMetric(Map<String, Object> map, String key) {
        Double v = readDouble(map, key);
        return v != null && v > 0;
    }

    private static boolean hasNonNegativeMetric(Map<String, Object> map, String key) {
        Double v = readDouble(map, key);
        return v != null && v >= 0;
    }

    private static boolean hasPositiveInt(Map<String, Object> map, String key) {
        Integer v = readInt(map, key);
        return v != null && v > 0;
    }

    private static Double readDouble(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        Object v = map.get(key);
        if (v == null) {
            return null;
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

    private static Integer readInt(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        Object v = map.get(key);
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

    private static String stringLoose(Object v) {
        if (v == null) {
            return null;
        }
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static boolean hasText(Object v) {
        return v != null && StringUtils.hasText(v.toString());
    }
}
