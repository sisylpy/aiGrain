package com.nongxinle.service;

import java.util.Map;

/**
 * 供货商端批发商采购批次汇总（近三个月，sellerDistributerPurchaseBatchsGb）。
 */
public interface GbDistributerPurchaseBatchSellerPurchaseBatchsGbService {

    Map<String, Object> buildSellerDistributerPurchaseBatchsGb(Integer disId, Integer supplierId);
}
