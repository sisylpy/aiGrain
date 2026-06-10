package com.nongxinle.ai.composer.warehouse;

import com.nongxinle.ai.dto.business.GoodsSupportedDishCoverAnswerPlan;
import com.nongxinle.ai.inventory.CoverDaysSalesBaselinePresentationSupport;
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
            return CoverDaysSalesBaselinePresentationSupport.composeGoodsCoverDaysSuccessPreview(
                    goods,
                    resolveBaselinePeriodPhrase(plan),
                    plan.getFirstImpactedDishName().trim(),
                    plan.getFirstImpactedCoverDays().trim());
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

    private static String resolveBaselinePeriodPhrase(GoodsSupportedDishCoverAnswerPlan plan) {
        String fromSummary =
                CoverDaysSalesBaselinePresentationSupport.readPeriodPhraseFromPlanSummary(plan.getSummary());
        return CoverDaysSalesBaselinePresentationSupport.defaultPeriodPhraseOr(fromSummary);
    }
}
