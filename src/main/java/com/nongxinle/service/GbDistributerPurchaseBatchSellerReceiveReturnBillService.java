package com.nongxinle.service;

import com.nongxinle.entity.GbDistributerPurchaseBatchEntity;

/**
 * 供货商确认退货单（sellerReceiveReturnBill）。
 */
public interface GbDistributerPurchaseBatchSellerReceiveReturnBillService {

    void sellerReceiveReturnBill(GbDistributerPurchaseBatchEntity batchEntity);
}
