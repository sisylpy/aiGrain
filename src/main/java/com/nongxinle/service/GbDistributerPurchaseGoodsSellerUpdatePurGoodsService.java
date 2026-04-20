package com.nongxinle.service;

import com.nongxinle.entity.GbDistributerPurchaseGoodsEntity;

/**
 * 供货商修改采购商品（sellerUpdatePurGoods）：更新采购行、关联订单小计与状态、回写批次小计。
 */
public interface GbDistributerPurchaseGoodsSellerUpdatePurGoodsService {

    GbDistributerPurchaseGoodsEntity sellerUpdatePurGoods(GbDistributerPurchaseGoodsEntity purchaseGoodsEntity);
}
