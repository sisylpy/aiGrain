package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜单四象限 AnswerPlan 的通用销量证据门禁：仅依据 tool / insight 数值字段，不做 NL 猜测。
 */
public final class MenuPortfolioSalesEvidenceSupport {

    public static final String NO_DATA_REASON_NO_DISH_SALES_FOR_PERIOD = "NO_DISH_SALES_FOR_PERIOD";

    public static final String DEBUG_SALES_EVIDENCE_AVAILABLE = "menuPortfolioSalesEvidenceAvailable";
    public static final String DEBUG_NO_DATA_REASON = "menuPortfolioNoDataReason";
    public static final String DEBUG_SOLD_DISH_COUNT = "soldDishCount";
    public static final String DEBUG_TOTAL_SALES_AMOUNT = "totalSalesAmount";
    public static final String DEBUG_TOTAL_LIST_PRICE_REVENUE = "totalListPriceRevenue";
    public static final String DEBUG_TOTAL_SOLD_PORTIONS = "totalSoldPortions";

    public static final String SUMMARY_FACT_NO_DATA_REASON = "menuPortfolioNoDataReason";
    public static final String SUMMARY_FACT_SOLD_DISH_COUNT = "soldDishCount";

    public static final String KNOWN_GAP_NO_SALES = "MENU_PORTFOLIO_NO_SALES_FOR_PERIOD";

    public static final String EMPTY_PORTFOLIO_MESSAGE =
            "该时间范围内没有查询到菜品销量，暂不能做菜单结构分析。";

    private MenuPortfolioSalesEvidenceSupport() {}

    public record Assessment(
            boolean salesEvidenceAvailable,
            int analyzedDishCount,
            int soldDishCount,
            BigDecimal totalSoldPortions,
            BigDecimal totalSalesAmount,
            BigDecimal totalListPriceRevenue) {}

    /**
     * 从 dishRows（已聚合）与 businessInsightSummary 汇总销量证据。
     */
    public static Assessment assess(List<Map<String, Object>> dishRows, Map<String, Object> insight) {
        BigDecimal totalSoldPortions = BigDecimal.ZERO;
        BigDecimal totalSalesAmount = BigDecimal.ZERO;
        int soldDishCount = 0;
        int analyzed = 0;
        if (dishRows != null) {
            for (Map<String, Object> row : dishRows) {
                if (row == null) {
                    continue;
                }
                analyzed++;
                BigDecimal qty = parseDecimal(row.get("soldPortionsTotal"));
                BigDecimal rev = parseDecimal(row.get("listPriceRevenue"));
                if (rev.compareTo(BigDecimal.ZERO) == 0) {
                    rev = parseDecimal(row.get("salesAmount"));
                }
                totalSoldPortions = totalSoldPortions.add(qty);
                totalSalesAmount = totalSalesAmount.add(rev);
                if (hasRowSalesEvidence(qty, rev)) {
                    soldDishCount++;
                }
            }
        }
        BigDecimal insightRevenue = parseDecimal(insight == null ? null : insight.get("totalListPriceRevenue"));
        BigDecimal portfolioRevenue =
                totalSalesAmount.compareTo(BigDecimal.ZERO) > 0 ? totalSalesAmount : insightRevenue;
        boolean available =
                totalSoldPortions.compareTo(BigDecimal.ZERO) > 0
                        || totalSalesAmount.compareTo(BigDecimal.ZERO) > 0
                        || insightRevenue.compareTo(BigDecimal.ZERO) > 0;
        return new Assessment(
                available, analyzed, soldDishCount, totalSoldPortions, totalSalesAmount, portfolioRevenue);
    }

    public static int countSoldDishesFromRows(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            BigDecimal qty = parseDecimal(row.get("soldPortionsTotal"));
            BigDecimal rev = parseDecimal(row.get("listPriceRevenue"));
            if (rev.compareTo(BigDecimal.ZERO) == 0) {
                rev = parseDecimal(row.get("salesAmount"));
            }
            if (hasRowSalesEvidence(qty, rev)) {
                count++;
            }
        }
        return count;
    }

    public static void writeEvidenceDebug(
            Assessment assessment, LinkedHashMap<String, Object> debug, Map<String, Object> summaryFacts) {
        if (debug == null || assessment == null) {
            return;
        }
        debug.put(DEBUG_SALES_EVIDENCE_AVAILABLE, assessment.salesEvidenceAvailable());
        debug.put(DEBUG_SOLD_DISH_COUNT, assessment.soldDishCount());
        debug.put(DEBUG_TOTAL_SOLD_PORTIONS, formatAmount(assessment.totalSoldPortions()));
        debug.put(DEBUG_TOTAL_SALES_AMOUNT, formatAmount(assessment.totalSalesAmount()));
        debug.put(DEBUG_TOTAL_LIST_PRICE_REVENUE, formatAmount(assessment.totalListPriceRevenue()));
        if (!assessment.salesEvidenceAvailable()) {
            debug.put(DEBUG_NO_DATA_REASON, NO_DATA_REASON_NO_DISH_SALES_FOR_PERIOD);
        }
        if (summaryFacts != null) {
            summaryFacts.put(SUMMARY_FACT_SOLD_DISH_COUNT, assessment.soldDishCount());
            if (!assessment.salesEvidenceAvailable()) {
                summaryFacts.put(SUMMARY_FACT_NO_DATA_REASON, NO_DATA_REASON_NO_DISH_SALES_FOR_PERIOD);
            }
        }
    }

    public static boolean isNoSalesPortfolioPeriod(MenuOperationAnswerPlan plan) {
        if (plan == null) {
            return false;
        }
        Map<String, Object> facts = plan.getSummaryFacts();
        if (facts != null
                && NO_DATA_REASON_NO_DISH_SALES_FOR_PERIOD.equals(
                        stringOrNull(facts.get(SUMMARY_FACT_NO_DATA_REASON)))) {
            return true;
        }
        Map<String, Object> debug = plan.getDebug();
        if (debug != null && Boolean.FALSE.equals(debug.get(DEBUG_SALES_EVIDENCE_AVAILABLE))) {
            return true;
        }
        return false;
    }

    public static boolean hasPortfolioQuadrantEmptyCard(MenuOperationAnswerPlan plan) {
        if (plan == null || !MenuOperationAnswerPlan.TYPE_MENU_OPERATION_OVERVIEW.equals(plan.getPlanType())) {
            return false;
        }
        if (plan.getMenuPortfolioClassification() != null) {
            return false;
        }
        return isNoSalesPortfolioPeriod(plan)
                && hasDisplayCard(plan, MenuOperationAnswerPlan.CARD_TYPE_MENU_PORTFOLIO_QUADRANT);
    }

    private static boolean hasDisplayCard(MenuOperationAnswerPlan plan, String cardType) {
        if (plan.getDisplayCards() == null || !StringUtils.hasText(cardType)) {
            return false;
        }
        for (MenuOperationAnswerPlan.MenuOperationDisplayCard card : plan.getDisplayCards()) {
            if (card != null && cardType.equals(card.getCardType())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasRowSalesEvidence(BigDecimal soldPortions, BigDecimal salesAmount) {
        return soldPortions.compareTo(BigDecimal.ZERO) > 0 || salesAmount.compareTo(BigDecimal.ZERO) > 0;
    }

    private static String stringOrNull(Object v) {
        if (v == null || !StringUtils.hasText(v.toString())) {
            return null;
        }
        return v.toString().trim();
    }

    static BigDecimal parseDecimal(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        if (v instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        String s = v.toString().trim().replace(",", "");
        if (s.isEmpty() || "—".equals(s) || "-".equals(s)) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private static String formatAmount(BigDecimal v) {
        if (v == null) {
            return "0";
        }
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
