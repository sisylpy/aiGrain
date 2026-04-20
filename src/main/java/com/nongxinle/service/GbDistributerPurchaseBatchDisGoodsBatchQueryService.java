package com.nongxinle.service;

import com.nongxinle.entity.GbDistributerGoodsEntity;
import com.nongxinle.entity.GbDistributerPurchaseBatchEntity;

import java.util.List;

/**
 * 批发商进货批次查询（getDisPurchaseGoodsBatchGb / getDisPurchaseGoodsBatchDetail）。
 */
public interface GbDistributerPurchaseBatchDisGoodsBatchQueryService {

    GbDistributerPurchaseBatchEntity getBatchWithOrders(Integer batchId);

    List<GbDistributerGoodsEntity> listBatchDetailGoodsTree(Integer batchId);
}
