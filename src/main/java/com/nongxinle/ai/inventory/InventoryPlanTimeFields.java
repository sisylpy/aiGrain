package com.nongxinle.ai.inventory;

import lombok.Builder;
import lombok.Value;

/**
 * AnswerPlan / Card 统一库存时间展示字段。
 */
@Value
@Builder
public class InventoryPlanTimeFields {

    InventoryQueryTimeKind inventoryQueryTimeKind;

    /** 库存快照锚定日（ISO yyyy-MM-dd）。 */
    String asOfDate;

    /** 卡片副标题 / 用户可见库存口径（不含「X 至 Y」区间）。 */
    String stockSnapshotLabel;

    /**
     * 流水 / 耗用 / 销量基线区间文案；仅 {@link InventoryQueryTimeKind#HYBRID_SNAPSHOT_WITH_PERIOD_BASELINE} 时有值。
     */
    String periodFlowLabel;

    /**
     * 内部销量/耗用基线（debug / summary）；不对用户主文案与 contextBar 展示。
     */
    String internalBaselineLabel;

    /**
     * 兼容字段：对用户展示时等同 {@link #stockSnapshotLabel}，不再写入查询时间窗「起至止」。
     */
    String timeLabel;

    /** 卡片 subtitle 首选 {@link #stockSnapshotLabel}。 */
    public String cardSubtitle() {
        return stockSnapshotLabel == null || stockSnapshotLabel.isBlank() ? timeLabel : stockSnapshotLabel;
    }
}
