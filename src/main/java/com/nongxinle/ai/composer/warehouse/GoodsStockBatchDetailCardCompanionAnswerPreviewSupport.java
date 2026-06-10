package com.nongxinle.ai.composer.warehouse;

import com.nongxinle.ai.dto.business.GoodsStockBatchDetailAnswerPlan;
import org.springframework.util.StringUtils;

/** 库存批次明细卡已生成时，answerPreview 只保留一句短引导。 */
public final class GoodsStockBatchDetailCardCompanionAnswerPreviewSupport {

    private GoodsStockBatchDetailCardCompanionAnswerPreviewSupport() {}

    public static boolean shouldUseShortPreview(GoodsStockBatchDetailAnswerPlan plan) {
        return plan != null && GoodsStockBatchDetailAnswerPlan.TYPE.equals(plan.getPlanType());
    }

    public static String composeCardCompanionHint(GoodsStockBatchDetailAnswerPlan plan) {
        if (plan == null) {
            return "";
        }
        String goods = resolveGoodsLabel(plan);
        if (plan.getKnownGaps() != null && plan.getKnownGaps().contains("no_active_stock_batch")) {
            return goods + "当前无剩余库存批次，详见下方卡片。";
        }
        Object activeCount = plan.getSummary() != null ? plan.getSummary().get("activeBatchCount") : null;
        if (activeCount instanceof Number n && n.intValue() > 0) {
            return goods + "当前仍有 " + n.intValue() + " 个库存批次，明细见下方卡片。";
        }
        if (StringUtils.hasText(plan.getStockSnapshotLabel())) {
            return goods + plan.getStockSnapshotLabel().trim() + "，详见下方卡片。";
        }
        return goods + "库存批次明细见下方卡片。";
    }

    private static String resolveGoodsLabel(GoodsStockBatchDetailAnswerPlan plan) {
        if (StringUtils.hasText(plan.getGoodsName())) {
            return "「" + plan.getGoodsName().trim() + "」";
        }
        return "该原料";
    }
}
