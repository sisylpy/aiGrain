package com.nongxinle.ai.composer.menu;

import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuOperationDisplayCard;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuPortfolioClassification;
import com.nongxinle.ai.graph.business.MenuPortfolioSalesEvidenceSupport;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/** 菜单经营主业务卡片已生成时，answerPreview 只保留一句短引导，避免与 card 重复。 */
public final class MenuOperationCardCompanionAnswerPreviewSupport {

    private static final String HIGH_SALES_LOW_MARGIN_DEFAULT_HINT =
            "已筛出畅销但利润效率偏低的菜品，下方卡片展示重点菜品、风险原因和建议动作。";

    private static final String ACTION_RECOMMENDATION_HINT =
            "已根据当前菜单数据整理优化方案，重点分组与本周建议请查看下方卡片。";

    private MenuOperationCardCompanionAnswerPreviewSupport() {}

    public static boolean shouldUseShortPreview(MenuOperationAnswerPlan plan) {
        if (plan == null || !StringUtils.hasText(plan.getPlanType())) {
            return false;
        }
        return switch (plan.getPlanType().trim()) {
            case MenuOperationAnswerPlan.TYPE_MENU_OPERATION_OVERVIEW ->
                    plan.getMenuPortfolioClassification() != null
                            || hasDisplayCard(
                                    plan.getDisplayCards(),
                                    MenuOperationAnswerPlan.CARD_TYPE_MENU_PORTFOLIO_QUADRANT);
            case MenuOperationAnswerPlan.TYPE_MENU_DISH_HIGH_SALES_LOW_PROFIT ->
                    hasDisplayCard(
                                    plan.getDisplayCards(),
                                    MenuOperationAnswerPlan.CARD_TYPE_MENU_HIGH_SALES_LOW_MARGIN)
                            || plan.getRiskDishes() != null;
            case MenuOperationAnswerPlan.TYPE_MENU_ACTION_RECOMMENDATION ->
                    plan.getMenuOptimizationPlan() != null
                            || hasDisplayCard(
                                    plan.getDisplayCards(),
                                    MenuOperationAnswerPlan.CARD_TYPE_MENU_ACTION_RECOMMENDATION);
            default -> false;
        };
    }

    public static String composeCardCompanionHint(MenuOperationAnswerPlan plan) {
        if (plan == null || !StringUtils.hasText(plan.getPlanType())) {
            return MenuOperationPortfolioExpressionSupport.composeOverviewDefaultHint(plan);
        }
        return switch (plan.getPlanType().trim()) {
            case MenuOperationAnswerPlan.TYPE_MENU_OPERATION_OVERVIEW -> composeOverviewHint(plan);
            case MenuOperationAnswerPlan.TYPE_MENU_DISH_HIGH_SALES_LOW_PROFIT ->
                    composeHighSalesLowMarginHint(plan);
            case MenuOperationAnswerPlan.TYPE_MENU_ACTION_RECOMMENDATION -> ACTION_RECOMMENDATION_HINT;
            default -> MenuOperationPortfolioExpressionSupport.composeOverviewDefaultHint(plan);
        };
    }

    /** LLM 展示计划与 deterministic fallback 共用同一短引导。 */
    public static String composeActionRecommendationHint() {
        return ACTION_RECOMMENDATION_HINT;
    }

    private static String composeOverviewHint(MenuOperationAnswerPlan plan) {
        if (MenuPortfolioSalesEvidenceSupport.isNoSalesPortfolioPeriod(plan)) {
            return MenuPortfolioSalesEvidenceSupport.EMPTY_PORTFOLIO_MESSAGE;
        }
        MenuPortfolioClassification portfolio = plan.getMenuPortfolioClassification();
        if (portfolio == null || portfolio.getTotalDishCount() <= 0) {
            return MenuOperationPortfolioExpressionSupport.composeOverviewDefaultHint(plan);
        }
        return MenuOperationPortfolioExpressionSupport.composeOverviewShortHint(plan);
    }

    private static String composeHighSalesLowMarginHint(MenuOperationAnswerPlan plan) {
        int count = resolveHighSalesLowMarginCount(plan);
        if (count <= 0) {
            return HIGH_SALES_LOW_MARGIN_DEFAULT_HINT;
        }
        return MenuOperationPortfolioExpressionSupport.resolveTimePhrase(plan)
                + "发现 "
                + count
                + " 道畅销但毛利偏低的菜，建议优先复核成本、用量和定价，详情见下方卡片。";
    }

    private static int resolveHighSalesLowMarginCount(MenuOperationAnswerPlan plan) {
        if (plan.getRiskDishes() != null && !plan.getRiskDishes().isEmpty()) {
            return plan.getRiskDishes().size();
        }
        Map<String, Object> summaryFacts = plan.getSummaryFacts();
        if (summaryFacts == null) {
            return 0;
        }
        Object raw = summaryFacts.get("highSalesLowProfitCount");
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw != null) {
            try {
                return Integer.parseInt(raw.toString().trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static boolean hasDisplayCard(List<MenuOperationDisplayCard> displayCards, String cardType) {
        if (displayCards == null || displayCards.isEmpty() || !StringUtils.hasText(cardType)) {
            return false;
        }
        for (MenuOperationDisplayCard card : displayCards) {
            if (card != null && cardType.equals(card.getCardType())) {
                return true;
            }
        }
        return false;
    }

}
