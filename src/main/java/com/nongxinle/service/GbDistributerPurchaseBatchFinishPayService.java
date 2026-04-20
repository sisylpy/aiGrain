package com.nongxinle.service;

/**
 * 批发商采购批次完成付款（生成付款记录并更新批次、商品、订单状态）。
 */
public interface GbDistributerPurchaseBatchFinishPayService {

    void finishPayPurchaseBatchGb(String ids, Integer gbDisId, String total, Integer supplierId, Integer userId);
}
