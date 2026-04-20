package com.nongxinle.service;

import java.util.Map;

/**
 * 批发商端按日期的采购与支出统计（disGetPurchaseDate）。
 */
public interface GbDistributerPurchaseGoodsDisPurchaseDateService {

    /**
     * @return 放入 R.data 的 map；区间内无采购且无扣减记录时返回 null
     */
    Map<String, Object> buildDisPurchaseDate(Integer disId, String startDate, String stopDate);
}
