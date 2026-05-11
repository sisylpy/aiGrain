package com.nongxinle.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 按采购入库部门聚合的入库金额（与 {@code queryGbPurchaseGoodsCount} join 口径一致）。 */
@Data
public class DepartmentPurchaseAggRow {
    private Integer departmentId;
    private BigDecimal purchaseSubtotal;
}
