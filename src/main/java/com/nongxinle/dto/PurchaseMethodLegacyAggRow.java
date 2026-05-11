package com.nongxinle.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 与旧版 {@link com.nongxinle.service.impl.GbAiChatServiceImpl#appendPurchaseSupplyMixSummary} 同一拆分维度在 SQL 中的聚合结果
 *（{@code queryGbPurchaseGoodsCount} 同 join/筛选）。
 * <p>{@code methodBucket}：{@code supplier_channel}（type=5，或 type=1 且 {@code gb_DPG_purchase_nx_supplier_id} 为正）、
 * {@code self_strict}（type=1 且 nx 为 null 或 -1）、{@code other}（其余 {@code purchase_type}）。</p>
 */
@Data
public class PurchaseMethodLegacyAggRow {
    private String methodBucket;
    private Integer lineCount;
    private BigDecimal lineSubtotal;
}
