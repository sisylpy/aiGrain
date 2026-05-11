package com.nongxinle.service;

import java.util.Map;

/**
 * 批发商端「进货中」批次与订单统计（jingjingGetBuyingGoodsGb）。
 */
public interface GbDistributerPurchaseBatchJingjingBuyingGoodsService {

    Map<String, Object> buildBuyingGoodsGb(Integer disId);

    Map<String, Object> buildStoreBuyingGoods(Integer purFatherId);
}
