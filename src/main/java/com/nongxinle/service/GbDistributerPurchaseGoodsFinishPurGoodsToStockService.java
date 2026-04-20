package com.nongxinle.service;

import com.nongxinle.entity.GbDistributerPurchaseGoodsEntity;

/**
 * 采购完成入库（finishPurGoodsToStock）：更新已选订单、采购行、未选订单迁移、写入部门库存。
 */
public interface GbDistributerPurchaseGoodsFinishPurGoodsToStockService {

    void finishPurGoodsToStock(GbDistributerPurchaseGoodsEntity purGoods);
}
