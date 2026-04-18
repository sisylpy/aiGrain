package com.nongxinle.dto;

import com.nongxinle.entity.GbDepartmentGoodsStockEntity;

/**
 * 订货端部门库存调整（制作 / 损耗 / 退货 / 废弃）统一入参。
 * <p>{@code kind}：produce、loss、return、waste（大小写不敏感）。</p>
 */
public class GbDepGoodsStockAdjustRequest {

    private String kind;
    private GbDepartmentGoodsStockEntity stock;

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public GbDepartmentGoodsStockEntity getStock() {
        return stock;
    }

    public void setStock(GbDepartmentGoodsStockEntity stock) {
        this.stock = stock;
    }
}
