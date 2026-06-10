package com.nongxinle.ai.composer.warehouse;

import com.nongxinle.ai.dto.business.GoodsStockBatchDetailAnswerPlan;
import com.nongxinle.ai.dto.business.GoodsSupportedDishCoverAnswerPlan;
import com.nongxinle.ai.inventory.CoverDaysSalesBaselinePresentationSupport;
import org.springframework.util.StringUtils;

/** WH-K：关联菜品卡 + 库存批次卡同轮挂载时的短引导。 */
public final class GoodsAnchorInventoryBundleCardCompanionAnswerPreviewSupport {

    private GoodsAnchorInventoryBundleCardCompanionAnswerPreviewSupport() {}

    public static boolean shouldUseShortPreview(
            GoodsSupportedDishCoverAnswerPlan coverPlan, GoodsStockBatchDetailAnswerPlan batchPlan) {
        return GoodsSupportedDishCoverCardCompanionAnswerPreviewSupport.shouldUseShortPreview(coverPlan)
                && GoodsStockBatchDetailCardCompanionAnswerPreviewSupport.shouldUseShortPreview(batchPlan);
    }

    public static String composeCardCompanionHint(
            GoodsSupportedDishCoverAnswerPlan coverPlan, GoodsStockBatchDetailAnswerPlan batchPlan) {
        if (!shouldUseShortPreview(coverPlan, batchPlan)) {
            return "";
        }
        String goods = resolveGoodsLabel(coverPlan, batchPlan);
        String coverHint = trimTrailingCardRef(
                GoodsSupportedDishCoverCardCompanionAnswerPreviewSupport.composeCardCompanionHint(coverPlan));
        String batchHint = trimTrailingCardRef(
                GoodsStockBatchDetailCardCompanionAnswerPreviewSupport.composeCardCompanionHint(batchPlan));

        boolean coverNoLinked =
                coverPlan.getKnownGaps() != null
                        && coverPlan.getKnownGaps().contains("no_linked_dish_for_goods");
        boolean batchEmpty =
                batchPlan.getKnownGaps() != null
                        && batchPlan.getKnownGaps().contains("no_active_stock_batch");

        if (coverNoLinked && batchEmpty) {
            return goods + "暂无关联菜品配方，当前也无剩余库存批次；详情见下方两张卡片。";
        }
        if (coverNoLinked) {
            return goods + "暂无关联菜品配方；" + batchHint.replace(goods, "").trim();
        }
        if (batchEmpty) {
            return CoverDaysSalesBaselinePresentationSupport.joinClauses(
                            coverHint, "当前无剩余库存批次，批次区见下方卡片。")
                    + "。";
        }
        if (StringUtils.hasText(coverPlan.getFirstImpactedDishName())
                && StringUtils.hasText(coverPlan.getFirstImpactedCoverDays())) {
            Object activeCount =
                    batchPlan.getSummary() != null ? batchPlan.getSummary().get("activeBatchCount") : null;
            if (activeCount instanceof Number n && n.intValue() > 0) {
                return goods
                        + "关联菜品与 "
                        + n.intValue()
                        + " 个库存批次已分别汇总，详情见下方两张卡片。";
            }
        }
        return goods + "关联菜品与库存批次明细见下方两张卡片。";
    }

    private static String resolveGoodsLabel(
            GoodsSupportedDishCoverAnswerPlan coverPlan, GoodsStockBatchDetailAnswerPlan batchPlan) {
        if (coverPlan != null && StringUtils.hasText(coverPlan.getGoodsName())) {
            return "「" + coverPlan.getGoodsName().trim() + "」";
        }
        if (batchPlan != null && StringUtils.hasText(batchPlan.getGoodsName())) {
            return "「" + batchPlan.getGoodsName().trim() + "」";
        }
        return "该原料";
    }

    private static String trimTrailingCardRef(String hint) {
        if (!StringUtils.hasText(hint)) {
            return "";
        }
        String s = hint.trim();
        if (s.endsWith("详见下方卡片。")) {
            return s.substring(0, s.length() - "详见下方卡片。".length()).trim();
        }
        if (s.endsWith("见下方卡片。")) {
            return s.substring(0, s.length() - "见下方卡片。".length()).trim();
        }
        return s;
    }
}
