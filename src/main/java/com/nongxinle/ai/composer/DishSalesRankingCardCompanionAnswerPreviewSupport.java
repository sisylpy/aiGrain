package com.nongxinle.ai.composer;

import com.nongxinle.ai.dto.business.DishSalesAnswerPlan;
import com.nongxinle.ai.graph.business.DishSalesRankingSalesEvidenceSupport;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/** 菜品销量排行主卡已生成时，Composer 只保留一句短引导，避免与 card 重复。 */
public final class DishSalesRankingCardCompanionAnswerPreviewSupport {

    private DishSalesRankingCardCompanionAnswerPreviewSupport() {}

    public static boolean isRankingPlanType(String planType) {
        if (!StringUtils.hasText(planType)) {
            return false;
        }
        String pt = planType.trim();
        return DishSalesAnswerPlan.TYPE_DISH_SALES_COUNT_RANKING_HIGH.equals(pt)
                || DishSalesAnswerPlan.TYPE_DISH_SALES_AMOUNT_RANKING_HIGH.equals(pt)
                || DishSalesAnswerPlan.TYPE_DISH_SALES_COUNT_RANKING_LOW.equals(pt);
    }

    public static boolean shouldUseShortPreview(DishSalesAnswerPlan plan) {
        if (plan == null || !isRankingPlanType(plan.getPlanType())) {
            return false;
        }
        if (DishSalesRankingSalesEvidenceSupport.isNoDataRankingPlan(plan)) {
            return false;
        }
        List<Map<String, Object>> rows = plan.getRankingRows();
        return rows != null
                && !rows.isEmpty()
                && DishSalesRankingSalesEvidenceSupport.hasRankingEvidenceForMetric(
                        plan.getMetricType(), rows);
    }

    public static String composeNoDataMessage() {
        return DishSalesRankingSalesEvidenceSupport.EMPTY_RANKING_MESSAGE;
    }

    public static String composeCardCompanionHint(DishSalesAnswerPlan plan) {
        if (plan == null) {
            return "已整理菜品销量排行，详见下方卡片。";
        }
        String time = blankToNull(plan.getTimeLabel());
        if (!StringUtils.hasText(time)) {
            return "已整理菜品销量排行，详见下方卡片。";
        }
        return "已整理" + time.trim() + "菜品销量排行，详见下方卡片。";
    }

    private static String blankToNull(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.trim();
    }
}
