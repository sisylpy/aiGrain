package com.nongxinle.service;

import com.nongxinle.entity.GbDistributerPurchaseBatchEntity;

/**
 * 供货商已读批次并回填采购价（sellUserReadDisBatchGb）。
 */
public interface GbDistributerPurchaseBatchSellUserReadDisBatchService {

    GbDistributerPurchaseBatchEntity sellUserReadDisBatchGb(GbDistributerPurchaseBatchEntity batch);
}
