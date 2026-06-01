package com.nongxinle.ai.composer.menu;

import com.nongxinle.ai.dto.business.DishIngredientCoverAnswerPlan;
import com.nongxinle.ai.graph.business.DishIngredientCoverSalesBaseline;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/** 单菜配料可支撑天数卡已生成时，answerPreview 只保留一句短引导，与 card / knownGaps 状态一致。 */
public final class DishIngredientCoverCardCompanionAnswerPreviewSupport {

    static final String GAP_NO_SALES_IN_WINDOW = "no_sales_in_window_cannot_compute_cover_days";
    static final String GAP_NO_RECIPE = "no_recipe_for_dish";

    private DishIngredientCoverCardCompanionAnswerPreviewSupport() {}

    public static boolean shouldUseShortPreview(DishIngredientCoverAnswerPlan plan) {
        return plan != null && DishIngredientCoverAnswerPlan.TYPE.equals(plan.getPlanType());
    }

    public static String composeCardCompanionHint(DishIngredientCoverAnswerPlan plan) {
        if (plan == null) {
            return "";
        }
        String dishLabel = resolveDishLabel(plan);
        if (StringUtils.hasText(plan.getDishCoverDays())) {
            return composeWithCoverDays(dishLabel, plan.getDishCoverDays().trim(), plan.getBottleneckIngredientName());
        }
        if (hasKnownGap(plan, GAP_NO_SALES_IN_WINDOW)) {
            return composeNoSalesInWindowHint(
                    dishLabel, hasIngredientRows(plan), resolveSalesBaselineDays(plan));
        }
        if (hasKnownGap(plan, GAP_NO_RECIPE)) {
            return dishLabel + "暂无配方数据，无法列出配料可支撑天数；请先维护菜品配方。";
        }
        return dishLabel + "暂时无法按当前数据推算配料可支撑天数，详情见下方卡片。";
    }

    private static String composeWithCoverDays(String dishLabel, String coverDays, String bottleneck) {
        if (StringUtils.hasText(bottleneck)) {
            return dishLabel
                    + "按当前销量与库存，大约还能支撑 "
                    + coverDays
                    + " 天；最先不够的是「"
                    + bottleneck.trim()
                    + "」。详情见下方卡片。";
        }
        return dishLabel + "按当前销量与库存，大约还能支撑 " + coverDays + " 天。详情见下方卡片。";
    }

    private static String composeNoSalesInWindowHint(String dishLabel, boolean hasIngredientRows, int baselineDays) {
        int days = baselineDays > 0 ? baselineDays : DishIngredientCoverSalesBaseline.DEFAULT_BASELINE_DAYS;
        String core =
                dishLabel
                        + "最近 "
                        + days
                        + " 天没有该菜销量，暂不能按销售节奏估算可用天数";
        if (hasIngredientRows) {
            return core + "；下方卡片仍可查看各配料当前库存。";
        }
        return core + "。";
    }

    private static String resolveDishLabel(DishIngredientCoverAnswerPlan plan) {
        if (plan == null || !StringUtils.hasText(plan.getDishName())) {
            return "该菜品";
        }
        return plan.getDishName().trim();
    }

    private static boolean hasKnownGap(DishIngredientCoverAnswerPlan plan, String gapCode) {
        if (plan == null || !StringUtils.hasText(gapCode)) {
            return false;
        }
        List<String> gaps = plan.getKnownGaps();
        if (gaps == null || gaps.isEmpty()) {
            return false;
        }
        for (String gap : gaps) {
            if (gapCode.equals(gap)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasIngredientRows(DishIngredientCoverAnswerPlan plan) {
        if (plan == null) {
            return false;
        }
        List<Map<String, Object>> rows = plan.getIngredientRows();
        return rows != null && !rows.isEmpty();
    }

    private static int resolveSalesBaselineDays(DishIngredientCoverAnswerPlan plan) {
        if (plan != null && plan.getSummary() != null) {
            Object raw = plan.getSummary().get("salesBaselineDays");
            if (raw instanceof Number n && n.intValue() > 0) {
                return n.intValue();
            }
            if (raw != null) {
                try {
                    int parsed = Integer.parseInt(raw.toString().trim());
                    if (parsed > 0) {
                        return parsed;
                    }
                } catch (NumberFormatException ignored) {
                    // fall through
                }
            }
        }
        return DishIngredientCoverSalesBaseline.DEFAULT_BASELINE_DAYS;
    }
}
