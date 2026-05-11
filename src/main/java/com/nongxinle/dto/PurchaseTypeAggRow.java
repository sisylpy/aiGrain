package com.nongxinle.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 按 {@code gb_DPG_purchase_type} 分组的采购入库行数与金额小计（与 {@code queryGbPurchaseGoodsCount} 同 join 口径）。
 * <p>口径借鉴旧版采购统计：{@code gb_DPG_purchase_type} 常见值见
 * {@link com.nongxinle.utils.GbConstants.PurchaseOrderType}（如 1=自采、5=供货商订货），退货等通过 {@code typeNotEqual} 排除。</p>
 */
@Data
public class PurchaseTypeAggRow {
    private Integer purchaseType;
    private Integer lineCount;
    private BigDecimal lineSubtotal;
}
