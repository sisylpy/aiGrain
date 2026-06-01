package com.nongxinle.ai.composer.warehouse;

import com.nongxinle.ai.dto.business.WarehouseAnswerPlan;
import com.nongxinle.ai.graph.business.WarehouseAnswerPlanCardSupport;
import org.springframework.util.StringUtils;

/**
 * 账面库存金额排行卡：Composer 仅输出短导语，排行明细在 Card payload。
 */
public final class WarehouseStockRankingCardCompanionAnswerPreviewSupport {

    private WarehouseStockRankingCardCompanionAnswerPreviewSupport() {}

    public static boolean shouldUseShortPreview(WarehouseAnswerPlan plan) {
        if (plan == null || !WarehouseAnswerPlanCardSupport.isStockAmountRankingPlanType(plan.getPlanType())) {
            return false;
        }
        return hasRankingRows(plan) || isEmptyRankingPlan(plan);
    }

    public static String composeCardCompanionHint(WarehouseAnswerPlan plan) {
        if (plan == null) {
            return "已整理账面库存金额排行，详见下方卡片。";
        }
        String scope = blankToDefault(plan.getScopeLabel(), "当前范围");
        String snapshot = blankToNull(plan.getStockSnapshotLabel());
        String rankingPhrase = rankingPhraseForPlanType(plan.getPlanType());
        if (isEmptyRankingPlan(plan)) {
            if (StringUtils.hasText(snapshot)) {
                return scope + "，" + snapshot.trim() + "，当前暂无" + rankingPhrase + "数据；详情见下方卡片。";
            }
            return scope + "当前暂无" + rankingPhrase + "数据；详情见下方卡片。";
        }
        if (StringUtils.hasText(snapshot)) {
            return scope + "，" + snapshot.trim() + "，" + rankingPhrase + "详见下方卡片。";
        }
        return scope + rankingPhrase + "详见下方卡片。";
    }

    private static boolean hasRankingRows(WarehouseAnswerPlan plan) {
        int focus = plan.getFocusRows() == null ? 0 : plan.getFocusRows().size();
        int secondary = plan.getSecondaryRows() == null ? 0 : plan.getSecondaryRows().size();
        return focus + secondary > 0;
    }

    private static boolean isEmptyRankingPlan(WarehouseAnswerPlan plan) {
        return !hasRankingRows(plan);
    }

    private static String rankingPhraseForPlanType(String planType) {
        if (WarehouseAnswerPlan.TYPE_WAREHOUSE_GOODS_AMOUNT_RANKING_LOW.equals(planType)) {
            return "账面库存金额偏低商品排行，";
        }
        if (WarehouseAnswerPlan.TYPE_WAREHOUSE_GOODS_AMOUNT_RANKING_HIGH.equals(planType)) {
            return "账面库存金额偏高商品排行，";
        }
        if (WarehouseAnswerPlan.TYPE_WAREHOUSE_STORE_AMOUNT_RANKING.equals(planType)) {
            return "门店账面库存金额排行，";
        }
        return "账面库存金额排行，";
    }

    private static String blankToDefault(String raw, String fallback) {
        if (!StringUtils.hasText(raw)) {
            return fallback;
        }
        return raw.trim();
    }

    private static String blankToNull(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.trim();
    }
}
