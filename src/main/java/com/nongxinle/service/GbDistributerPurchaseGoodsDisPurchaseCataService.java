package com.nongxinle.service;

import java.util.Map;

/**
 * 批发商端按商品大类的采购分类统计（disGetPurchaseCata）。
 */
public interface GbDistributerPurchaseGoodsDisPurchaseCataService {

    /**
     * @param purUserId  预留与前端入参一致，当前统计逻辑未使用
     * @param supplierId 预留与前端入参一致，当前统计逻辑未使用
     * @return 放入 R.data 的 map；无采购且无扣减时返回 null
     */
    Map<String, Object> buildDisPurchaseCata(Integer disId, String startDate, String stopDate,
                                             String purUserId, String supplierId);
}
