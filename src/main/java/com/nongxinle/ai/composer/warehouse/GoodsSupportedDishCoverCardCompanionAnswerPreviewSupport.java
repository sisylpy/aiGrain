package com.nongxinle.ai.composer.warehouse;

import com.nongxinle.ai.dto.business.GoodsSupportedDishCoverAnswerPlan;
import org.springframework.util.StringUtils;

/** 原料关联菜品卡已生成时，answerPreview 只保留一句短引导。 */
public final class GoodsSupportedDishCoverCardCompanionAnswerPreviewSupport {

    private GoodsSupportedDishCoverCardCompanionAnswerPreviewSupport() {}

    public static boolean shouldUseShortPreview(GoodsSupportedDishCoverAnswerPlan plan) {
        return plan != null && GoodsSupportedDishCoverAnswerPlan.TYPE.equals(plan.getPlanType());
    }

    public static String composeCardCompanionHint(GoodsSupportedDishCoverAnswerPlan plan) {
        if (plan == null) {
            return "";
        }
        String goods = resolveGoodsLabel(plan);
        if (plan.getKnownGaps() != null && plan.getKnownGaps().contains("no_linked_dish_for_goods")) {
            return goods + "暂无关联菜品配方，无法估算还能做几份；当前库存见下方卡片。";
        }
        if (StringUtils.hasText(plan.getFirstImpactedDishName())
                && StringUtils.hasText(plan.getFirstImpactedCoverDays())) {
            return goods
                    + "按当前库存与近 "
                    + resolveBaselineDays(plan)
                    + " 天销量，最先受影响的是「"
                    + plan.getFirstImpactedDishName().trim()
                    + "」（约 "
                    + plan.getFirstImpactedCoverDays().trim()
                    + " 天）。详情见下方卡片。";
        }
        if (StringUtils.hasText(plan.getCurrentStockQty())) {
            return goods + "当前库存与关联菜品明细见下方卡片。";
        }
        return goods + "当前库存与关联菜品见下方卡片。";
    }

    private static String resolveGoodsLabel(GoodsSupportedDishCoverAnswerPlan plan) {
        if (StringUtils.hasText(plan.getGoodsName())) {
            return "「" + plan.getGoodsName().trim() + "」";
        }
        return "该原料";
    }

    private static int resolveBaselineDays(GoodsSupportedDishCoverAnswerPlan plan) {
        if (plan.getSummary() != null && plan.getSummary().get("salesBaselineDays") instanceof Number n) {
            return Math.max(1, n.intValue());
        }
        return 7;
    }
}
