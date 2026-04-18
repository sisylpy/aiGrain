package com.nongxinle.service;

import com.nongxinle.dto.GbDepGoodsStockAdjustRequest;
import com.nongxinle.dto.GbDepGoodsStockAdjustResult;
import com.nongxinle.entity.GbDepartmentOrdersEntity;

/**
 * 部门库存与扣减（reduce）编排：同一事务内维护 stock、reduce、日报、部门商品汇总等。
 */
public interface GbDepartmentGoodsStockLedgerService {

    /**
     * 订货端调整库存（制作 / 损耗 / 退货 / 废弃），写入 reduce 并更新关联数据。
     */
    GbDepGoodsStockAdjustResult adjustDepGoodsStock(GbDepGoodsStockAdjustRequest request);

    /**
     * 删除一条扣减记录并回滚库存及相关数据（采购、订单、日报等按类型处理）。
     * 成功时 {@link GbDepGoodsStockAdjustResult#getData()} 中含 {@code "data"} -> {@link com.nongxinle.entity.GbDepartmentDisGoodsEntity}。
     */
    GbDepGoodsStockAdjustResult removeReduceAndRevert(Integer reduceId);

    /**
     * Jj 部门订货：当 {@code stockIsZero} 时，将该部门商品下「剩余为 0」的库存批次按生产全部入账 reduce，并扣减部门商品汇总。
     */
    void clearDepGoodsStockWhenJjStockIsZero(GbDepartmentOrdersEntity orders);
}
