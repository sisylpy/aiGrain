package com.nongxinle.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 与已移除的旧单 Agent Chat 中「采购方式供货属性摘要」同一拆分维度在 SQL 中的聚合结果（见 {@code PurchaseOverviewTool} / {@code docs/API_INTEGRATION.md}）
 *（{@code queryGbPurchaseGoodsCount} 同 join/筛选）。
 * <p>{@code methodBucket}：{@code supplier_channel}（type=5，或 type=1 且 {@code gb_DPG_purchase_nx_supplier_id} 为正）、
 * {@code self_strict}（type=1 且 nx 为 null 或 -1）、{@code other}（其余 {@code purchase_type}）。</p>
 */
@Data
public class PurchaseMethodLegacyAggRow {
    private String methodBucket;
    private Integer lineCount;
    private BigDecimal lineSubtotal;
    /** ∑{@code gb_DPG_buy_quantity}，与 {@code lineSubtotal} 同桶。 */
    private BigDecimal lineQuantity;
}
