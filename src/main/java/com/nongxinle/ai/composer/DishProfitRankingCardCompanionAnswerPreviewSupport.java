package com.nongxinle.ai.composer;

import com.nongxinle.ai.dto.business.DishProfitAnswerPlan;
import com.nongxinle.ai.graph.business.DishProfitAnswerPlanCardSupport;
import com.nongxinle.ai.graph.business.DishProfitRankingSalesEvidenceSupport;
import org.springframework.util.StringUtils;

/**
 * 菜品成本/毛利排行主卡已生成时，Composer 只保留一句短引导，避免与 card 重复。
 */
public final class DishProfitRankingCardCompanionAnswerPreviewSupport {

    private DishProfitRankingCardCompanionAnswerPreviewSupport() {}

    public static boolean shouldUseShortPreview(DishProfitAnswerPlan plan) {
        if (plan == null || !DishProfitAnswerPlanCardSupport.isRankingPlanType(plan.getPlanType())) {
            return false;
        }
        if (DishProfitRankingSalesEvidenceSupport.isNoDataRankingPlan(plan)) {
            return false;
        }
        return !DishProfitAnswerPlanCardSupport.mergeRankingRows(plan).isEmpty();
    }

    public static String composeCardCompanionHint(DishProfitAnswerPlan plan) {
        if (plan == null) {
            return "已整理菜品利润排行，详见下方卡片。";
        }
        String requested =
                DishProfitRankingSalesEvidenceSupport.resolveRequestedRankingPlanType(plan);
        String planType =
                StringUtils.hasText(requested) ? requested : blankToNull(plan.getPlanType());
        String metricPhrase = rankingMetricPhrase(planType);
        String time = blankToNull(plan.getTimeLabel());
        if (!StringUtils.hasText(time)) {
            return "已整理" + metricPhrase + "排行，详见下方卡片。";
        }
        return "已整理" + time.trim() + metricPhrase + "排行，详见下方卡片。";
    }

    private static String rankingMetricPhrase(String planType) {
        if (DishProfitAnswerPlan.TYPE_DISH_HIGHEST_MARGIN.equals(planType)
                || DishProfitAnswerPlan.TYPE_DISH_LOWEST_MARGIN.equals(planType)) {
            return "菜品毛利率";
        }
        if (DishProfitAnswerPlan.TYPE_DISH_HIGHEST_PROFIT_AMOUNT.equals(planType)
                || DishProfitAnswerPlan.TYPE_DISH_LOWEST_PROFIT_AMOUNT.equals(planType)) {
            return "菜品利润额";
        }
        if (DishProfitAnswerPlan.TYPE_DISH_HIGHEST_ACTUAL_COST.equals(planType)) {
            return "菜品实际成本";
        }
        return "菜品利润";
    }

    private static String blankToNull(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.trim();
    }
}
