package com.nongxinle.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nongxinle.dto.GbDepGoodsStockAdjustRequest;
import com.nongxinle.dto.GbDepGoodsStockAdjustResult;
import com.nongxinle.entity.GbDepartmentGoodsStockEntity;
import com.nongxinle.entity.GbDepartmentGoodsStockSimpleEntity;
import com.nongxinle.entity.GbDistributerFatherGoodsEntity;
import com.nongxinle.entity.GbDistributerGoodsEntity;

import java.util.List;
import java.util.Map;

/**
 * 部门商品库存Service接口
 */
public interface GbDepartmentGoodsStockService extends IService<GbDepartmentGoodsStockEntity> {

    /**
     * 根据参数查询部门库存
     */
    List<GbDepartmentGoodsStockEntity> queryGoodsStockByParams(Map<String, Object> map);

    /**
     * 门店时段统计：与树查询条件一致的一次性库存行列表
     */
    List<GbDepartmentGoodsStockEntity> queryGoodsStockListForMendianPeriod(Map<String, Object> map);

    /**
     * 查询库存商品数量
     */
    Integer queryGoodsStockCount(Map<String, Object> map);

    /**
     * 部门库存「剩余成本」合计：SUM({@code gb_dgs_rest_subtotal})。
     * <p>常用键：{@code disId}、{@code date}/{@code startDate}/{@code stopDate}（按 {@code gb_dgs_date}）、{@code dayuStatus} 等。
     * <p>若传入 {@code purchaseLinkDate}（及 {@code disId}），则只统计采购表 {@code gb_DPG_purchase_date} 为该日、且可选
     * {@code purDayuStatus} 时 {@code gb_DPG_status &gt; purDayuStatus} 的采购行所关联的库存（{@code gb_dgs_gb_pur_goods_id}）。
     */
    Double queryDepGoodsRestTotal(Map<String, Object> map);

    /**
     * 与 {@link #queryDepGoodsRestTotal} 条件一致，汇总剩余重量
     */
    Double queryDepGoodsRestWeightTotal(Map<String, Object> map);

    /**
     * 入库批次采购成本合计 SUM({@code gb_dgs_subtotal})，筛选条件与 {@link #queryGoodsStockCount} 一致。
     */
    Double queryDepGoodsSubtotal(Map<String, Object> map);

    /**
     * 入库批次重量合计 SUM({@code gb_dgs_weight})，筛选条件与 {@link #queryGoodsStockCount} 一致。
     */
    Double queryDepStockWeightTotal(Map<String, Object> map);

    /**
     * 查询部门库存树形父商品
     */
    List<GbDistributerFatherGoodsEntity> queryDepStockTreeFatherGoodsByParams(Map<String, Object> map);

    /**
     * 根据参数查询分销商商品库存
     */
    List<GbDistributerGoodsEntity> queryDisGoodsStockByParams(Map<String, Object> map);

    /**
     * 查询部门商品损耗总量
     */
    Double queryDepGoodsWasteTotal(Map<String, Object> map);

    /**
     * 查询分销库存商品数量
     */
    Integer queryDisStockGoodsCount(Map<String, Object> map);

    /**
     * 根据参数查询部门库存（简化版）
     */
    List<GbDepartmentGoodsStockSimpleEntity> queryGoodsStockSimpleByParams(Map<String, Object> map);

    /**
     * 订货端部门库存调整（制作 / 损耗 / 退货 / 废弃），与原先四个接口业务一致。
     * 编排逻辑在 {@link GbDepartmentGoodsStockLedgerService}。
     */
    GbDepGoodsStockAdjustResult adjustDepGoodsStock(GbDepGoodsStockAdjustRequest request);

    /**
     * 按部门订单 ID 查询退货产生的库存行（gb_dgs_gb_department_order_id）。
     */
    GbDepartmentGoodsStockEntity queryReturnStockItemByOrderId(Integer orderId);

    Double queryDepStockRestSubtotal(Map<String, Object> queryMap);
}
