package com.nongxinle.ai.composer.menu;

import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuOptimizationDishItem;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuOptimizationPlan;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuOptimizationPriorityGroup;
import com.nongxinle.ai.gateway.LlmGatewayFailureMarker;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 菜单专家叙述输出守卫：失败或违反基本约束时回退确定性 answerPreview。
 */
public final class MenuOperationExpertNarrativeOutputGuard {

    private static final String[] INTERNAL_CODE_FRAGMENTS = {
        "NOT_IN_P1",
        "knownGap",
        "toolResults",
        "PRIORITY_HANDLE",
        "STABLE_PROMOTE",
        "INCREASE_EXPOSURE",
        "WATCH_ADJUST",
        "MENU_ACTION_RECOMMENDATION",
        "MENU_OPERATION_OVERVIEW",
        "DISH_INGREDIENT_COST_BREAKDOWN_NOT_IN_P1",
        "MENU_PRICING_ADVICE_NOT_IN_P1",
        "MENU_SINGLE_ANALYSIS_NOT_IN_P1"
    };

    private MenuOperationExpertNarrativeOutputGuard() {}

    public static boolean accepts(String narrative, MenuOperationAnswerPlan plan) {
        if (!StringUtils.hasText(narrative) || plan == null) {
            return false;
        }
        if (LlmGatewayFailureMarker.isMarked(narrative)) {
            return false;
        }
        String text = narrative.trim();
        if (text.length() < 80) {
            return false;
        }
        for (String fragment : INTERNAL_CODE_FRAGMENTS) {
            if (text.contains(fragment)) {
                return false;
            }
        }
        if (claimsLossWithoutEvidence(text, plan)) {
            return false;
        }
        if (forbiddenDelistAdvice(text, plan)) {
            return false;
        }
        MenuOptimizationPlan optimization = plan.getMenuOptimizationPlan();
        Map<String, Object> limits =
                optimization != null && optimization.getCapabilityLimits() != null
                        ? optimization.getCapabilityLimits()
                        : Map.of();
        if (MenuOperationCapabilityLimitsTextSupport.mentionsUnavailableCapabilityAsFact(text, limits)) {
            return false;
        }
        return true;
    }

    private static boolean claimsLossWithoutEvidence(String text, MenuOperationAnswerPlan plan) {
        if (!text.contains("亏损")) {
            return false;
        }
        return !anyDishHasNegativeProfit(plan);
    }

    private static boolean anyDishHasNegativeProfit(MenuOperationAnswerPlan plan) {
        MenuOptimizationPlan optimization = plan.getMenuOptimizationPlan();
        if (optimization == null) {
            return false;
        }
        if (anyNegativeProfit(optimization.getCostReviewDishes())) {
            return true;
        }
        if (anyNegativeProfit(optimization.getProtectDishes())) {
            return true;
        }
        if (anyNegativeProfit(optimization.getPromotionDishes())) {
            return true;
        }
        if (anyNegativeProfit(optimization.getWatchListDishes())) {
            return true;
        }
        List<MenuOptimizationPriorityGroup> groups = optimization.getPriorityGroups();
        if (groups == null) {
            return false;
        }
        for (MenuOptimizationPriorityGroup group : groups) {
            if (group != null && anyNegativeProfit(group.getDishes())) {
                return true;
            }
        }
        return false;
    }

    private static boolean anyNegativeProfit(List<MenuOptimizationDishItem> dishes) {
        if (dishes == null || dishes.isEmpty()) {
            return false;
        }
        for (MenuOptimizationDishItem dish : dishes) {
            if (dish != null && isNegativeAmount(dish.getActualProfitAmount())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNegativeAmount(String amount) {
        if (!StringUtils.hasText(amount)) {
            return false;
        }
        String normalized = amount.trim().replace(",", "").replace("元", "");
        if (normalized.startsWith("-")) {
            return true;
        }
        try {
            return new BigDecimal(normalized).compareTo(BigDecimal.ZERO) < 0;
        } catch (NumberFormatException ignore) {
            return false;
        }
    }

    private static boolean forbiddenDelistAdvice(String text, MenuOperationAnswerPlan plan) {
        String lc = text.toLowerCase(Locale.ROOT);
        boolean mentionsDelist =
                text.contains("建议下架")
                        || text.contains("应该下架")
                        || text.contains("直接下架")
                        || text.contains("立即下架")
                        || lc.contains("must delist");
        if (!mentionsDelist) {
            return false;
        }
        return !planAllowsDelistLanguage(plan);
    }

    private static boolean planAllowsDelistLanguage(MenuOperationAnswerPlan plan) {
        MenuOptimizationPlan optimization = plan.getMenuOptimizationPlan();
        if (optimization == null) {
            return false;
        }
        if (containsDelistHintFromDishes(optimization.getWatchListDishes())) {
            return true;
        }
        List<MenuOptimizationPriorityGroup> groups = optimization.getPriorityGroups();
        if (groups == null) {
            return false;
        }
        for (MenuOptimizationPriorityGroup group : groups) {
            if (group == null) {
                continue;
            }
            if (containsDelistHint(group.getSuggestedAction()) || containsDelistHint(group.getReason())) {
                return true;
            }
            if (containsDelistHintFromDishes(group.getDishes())) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsDelistHintFromDishes(List<MenuOptimizationDishItem> dishes) {
        if (dishes == null) {
            return false;
        }
        for (MenuOptimizationDishItem dish : dishes) {
            if (dish == null) {
                continue;
            }
            if (containsDelistHint(dish.getSuggestedActionLabel()) || containsDelistHint(dish.getReason())) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsDelistHint(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String t = value.trim();
        return t.contains("下架") || t.contains("淘汰") || t.contains("观察") || t.contains("调整");
    }
}
