package com.nongxinle.dto;

import com.nongxinle.entity.GbDepartmentGoodsStockEntity;

/**
 * 订货端部门库存调整（制作 / 损耗 / 退货 / 废弃 / 员工餐）统一入参。
 * <p>{@code kind} 规范值见 {@link com.nongxinle.utils.GbDepGoodsStockAdjustKind}（大小写不敏感，须精确匹配）。</p>
 * <p>员工餐：{@code stock.gbDgsMyEmployeeMealWeight} 为本次实际使用数量（基础单位）。</p>
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
