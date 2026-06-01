package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.dto.business.WarehouseAnswerPlan;

/**
 * Card subtitle / payload 库存时间字段投影。
 */
final class WarehouseAnswerPlanCardTimeSupport {

    private WarehouseAnswerPlanCardTimeSupport() {}

    static String cardSubtitle(WarehouseAnswerPlan plan) {
        if (plan == null) {
            return "";
        }
        if (plan.getStockSnapshotLabel() != null && !plan.getStockSnapshotLabel().isBlank()) {
            return plan.getStockSnapshotLabel().trim();
        }
        return plan.getTimeLabel() == null ? "" : plan.getTimeLabel();
    }
}
