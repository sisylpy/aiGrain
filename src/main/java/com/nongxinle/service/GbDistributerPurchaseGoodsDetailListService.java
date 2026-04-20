package com.nongxinle.service;

import java.util.Map;

/**
 * 批发商采购商品明细列表（按日期区间汇总）。
 */
public interface GbDistributerPurchaseGoodsDetailListService {

    Map<String, Object> buildPurGoodsDetailList(Integer disGoodsId, String startDate, String stopDate);
}
