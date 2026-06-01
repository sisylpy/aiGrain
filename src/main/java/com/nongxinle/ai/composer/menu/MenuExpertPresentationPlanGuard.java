package com.nongxinle.ai.composer.menu;

import com.alibaba.fastjson2.JSON;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuOptimizationPlan;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 菜单专家展示计划守卫：JSON 解析后校验，失败则 fallback 确定性 card。
 */
public final class MenuExpertPresentationPlanGuard {

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

    private MenuExpertPresentationPlanGuard() {}

    public static String validate(MenuExpertPresentationPlan presentation, AiRunState state, MenuOperationAnswerPlan plan) {
        if (presentation == null || plan == null) {
            return "missing_presentation_or_plan";
        }
        if (!StringUtils.hasText(presentation.getMainSummary()) || presentation.getMainSummary().trim().length() < 20) {
            return "main_summary_too_short";
        }
        String serialized = JSON.toJSONString(presentation);
        for (String fragment : INTERNAL_CODE_FRAGMENTS) {
            if (serialized.contains(fragment)) {
                return "internal_code_leak";
            }
        }
        MenuExpertPresentationFactIndex index = MenuExpertPresentationFactIndex.from(state, plan);
        if (presentationReferencesDishes(presentation) && index.allowedDishNames().isEmpty()) {
            return "missing_menu_fact_pack";
        }
        String dishError = validateDishes(presentation, index);
        if (dishError != null) {
            return dishError;
        }
        String nextStepError = validateNextSteps(presentation, index);
        if (nextStepError != null) {
            return nextStepError;
        }
        String textBlob = collectTextBlob(presentation);
        if (textBlob.contains("亏损") && !index.anyDishHasNegativeProfit()) {
            return "loss_without_evidence";
        }
        if (MenuExpertPresentationPlanGuardSupport.mentionsForcedDelist(textBlob) && !index.planAllowsDelistLanguage()) {
            return "forced_delist_without_support";
        }
        MenuOptimizationPlan optimization = plan.getMenuOptimizationPlan();
        Map<String, Object> limits =
                optimization != null && optimization.getCapabilityLimits() != null
                        ? optimization.getCapabilityLimits()
                        : Map.of();
        if (MenuOperationCapabilityLimitsTextSupport.mentionsUnavailableCapabilityAsFact(textBlob, limits)) {
            return "unsupported_capability_as_fact";
        }
        return null;
    }

    private static String validateDishes(MenuExpertPresentationPlan presentation, MenuExpertPresentationFactIndex index) {
        if (presentation.getFocusSections() == null) {
            return null;
        }
        for (MenuExpertPresentationPlan.MenuExpertPresentationFocusSection section : presentation.getFocusSections()) {
            if (section == null || section.getDishes() == null) {
                continue;
            }
            for (MenuExpertPresentationPlan.MenuExpertPresentationDish dish : section.getDishes()) {
                if (dish == null || !StringUtils.hasText(dish.getDishName())) {
                    continue;
                }
                String name = dish.getDishName().trim();
                if (!index.isKnownDish(name)) {
                    return "unknown_dish:" + name;
                }
                MenuExpertFactDishRow facts = index.dishFacts(name);
                if (facts != null
                        && !metricMatches(
                                facts.blendedGrossMarginRateOnListPrice(),
                                dish.getBlendedGrossMarginRateOnListPrice())) {
                    return "metric_mismatch_margin:" + name;
                }
                if (facts != null
                        && !metricMatches(facts.actualProfitAmount(), dish.getActualProfitAmount())) {
                    return "metric_mismatch_profit:" + name;
                }
            }
        }
        return null;
    }

    /** LLM 未输出数字时不校验；仅当输出了具体数字时才要求与事实包一致。 */
    private static boolean metricMatches(String expected, String actual) {
        if (!StringUtils.hasText(actual)) {
            return true;
        }
        if (!StringUtils.hasText(expected)) {
            return false;
        }
        return expected.trim().equals(actual.trim());
    }

    /**
     * nextSteps 允许换表达，不做逐字匹配；只禁止引用事实包外菜品、以及把不可用能力写成已具备事实。
     */
    private static String validateNextSteps(MenuExpertPresentationPlan presentation, MenuExpertPresentationFactIndex index) {
        List<String> steps = presentation.getNextSteps();
        if (steps == null || steps.isEmpty()) {
            return null;
        }
        MenuOptimizationPlan optimization =
                index.plan() != null ? index.plan().getMenuOptimizationPlan() : null;
        Map<String, Object> limits =
                optimization != null && optimization.getCapabilityLimits() != null
                        ? optimization.getCapabilityLimits()
                        : Map.of();
        for (String step : steps) {
            if (!StringUtils.hasText(step)) {
                continue;
            }
            String trimmed = step.trim();
            for (String quotedDish : MenuExpertPresentationPlanGuardSupport.extractQuotedDishNames(trimmed)) {
                if (!index.isKnownDish(quotedDish)) {
                    return "next_step_unknown_dish:" + quotedDish;
                }
            }
            if (MenuOperationCapabilityLimitsTextSupport.mentionsUnavailableCapabilityAsFact(trimmed, limits)) {
                return "next_step_unsupported_capability";
            }
        }
        return null;
    }

    private static String collectTextBlob(MenuExpertPresentationPlan presentation) {
        StringBuilder sb = new StringBuilder();
        appendText(sb, presentation.getMainSummary());
        if (presentation.getKeyFindings() != null) {
            for (String finding : presentation.getKeyFindings()) {
                appendText(sb, finding);
            }
        }
        if (presentation.getFocusSections() != null) {
            for (MenuExpertPresentationPlan.MenuExpertPresentationFocusSection section : presentation.getFocusSections()) {
                if (section == null) {
                    continue;
                }
                appendText(sb, section.getSectionTitle());
                appendText(sb, section.getSectionSummary());
                appendText(sb, section.getSuggestedAction());
                appendText(sb, section.getReason());
                if (section.getDishes() != null) {
                    for (MenuExpertPresentationPlan.MenuExpertPresentationDish dish : section.getDishes()) {
                        if (dish == null) {
                            continue;
                        }
                        appendText(sb, dish.getSuggestedAction());
                        appendText(sb, dish.getReason());
                    }
                }
            }
        }
        appendText(sb, presentation.getCapabilityBoundaryZh());
        if (presentation.getNextSteps() != null) {
            for (String step : presentation.getNextSteps()) {
                appendText(sb, step);
            }
        }
        return sb.toString();
    }

    private static boolean presentationReferencesDishes(MenuExpertPresentationPlan presentation) {
        if (presentation.getFocusSections() == null) {
            return false;
        }
        for (MenuExpertPresentationPlan.MenuExpertPresentationFocusSection section : presentation.getFocusSections()) {
            if (section != null && section.getDishes() != null && !section.getDishes().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static void appendText(StringBuilder sb, String text) {
        if (StringUtils.hasText(text)) {
            sb.append(text.trim()).append('\n');
        }
    }
}
