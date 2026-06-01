package com.nongxinle.ai.composer.warehouse;

import com.nongxinle.ai.dto.business.WarehouseAnswerPlan;

/**
 * 库存风险列表卡：Composer 仅输出短导语，明细在 Card payload。
 */
public final class WarehouseInventoryRiskCardCompanionAnswerPreviewSupport {

    private WarehouseInventoryRiskCardCompanionAnswerPreviewSupport() {}

    public static boolean shouldUseShortPreview(WarehouseAnswerPlan plan) {
        return plan != null
                && WarehouseAnswerPlan.TYPE_WAREHOUSE_LOW_STOCK_RISK.equals(plan.getPlanType());
    }

    public static String composeCardCompanionHint(WarehouseAnswerPlan plan) {
        if (plan == null) {
            return "";
        }
        String scope = plan.getScopeLabel() == null ? "当前范围" : plan.getScopeLabel().trim();
        int count = plan.getFocusRows() == null ? 0 : plan.getFocusRows().size();
        if (count <= 0 && plan.getSummary() != null && plan.getSummary().get("emptyRiskList") != null) {
            return scope + "当前库存口径下未识别到需重点关注的库存偏少项；详情见下方卡片。";
        }
        return scope + "共 " + count + " 项原料/商品建议关注库存风险（按可支撑天数排序）；详情见下方卡片。";
    }
}
