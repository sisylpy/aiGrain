package com.nongxinle.service;

import com.nongxinle.entity.GbDepartmentOrdersEntity;
import com.nongxinle.entity.GbDistributerGoodsEntity;
import com.nongxinle.entity.GbDistributerPurchaseGoodsEntity;

import java.util.List;
import java.util.Map;

/**
 * 门店订货（Jj）流程中与采购商品行关联、日期与分类层级填充的公共逻辑。
 */
public interface GbJjOrderPurchaseLinkService {

    enum PurchaseGoodsLinkMode {
        /** 总是新建采购行（如农鑫商品建档并订货） */
        ALWAYS_NEW,
        /** 按采购部门 + 规格 + 商品查重后合并或新建 */
        MERGE_BY_PUR_DEPARTMENT,
        /** 按供货商（或 status=0）+ 规格 + 商品查重后合并或新建 */
        MERGE_BY_SUPPLIER_OR_STATUS
    }

    /**
     * 写入订货申请日期、到货日期等公共时间字段（不含订单类型等业务字段）。
     */
    void applyJjOrderTimestamps(GbDepartmentOrdersEntity order);

    /**
     * 根据批发商商品上的父级分类 ID，解析并写入订单上的父/祖/曾祖及农鑫侧大类 ID。
     */
    void applyDisGoodsCategoryHierarchyToOrder(GbDepartmentOrdersEntity order, Integer dgDfgGoodsFatherId);

    /**
     * 按模式关联或新建采购商品行，并设置订单 {@code gbDoPurchaseGoodsId}；
     * 根据模式会更新订单表和/或仅填充实体由调用方统一 update。
     *
     * @return 最终关联的采购商品实体
     */
    GbDistributerPurchaseGoodsEntity resolvePurchaseGoodsLineForJjOrder(
            GbDepartmentOrdersEntity order,
            GbDistributerGoodsEntity disGoods,
            PurchaseGoodsLinkMode mode);

    /**
     * 供货商自动进货批次（原 Controller 内 {@code autoAddPurchaseBatch} 逻辑）。
     */
    Map<String, Object> ensureSupplierPurchaseBatchForJjOrder(
            GbDepartmentOrdersEntity ordersEntity,
            GbDistributerGoodsEntity goodsEntity);

    /**
     * 入库完成后，将未勾选的订单拆到新采购商品行并累计数量（原 {@code finishPurGoodsToStock} 内逻辑）。
     */
    void moveUnconfirmedOrdersToNewPurchaseGoods(
            GbDistributerPurchaseGoodsEntity finishedPurchaseTemplate,
            List<GbDepartmentOrdersEntity> unChoiceOrderList,
            GbDistributerGoodsEntity disGoods);
}
