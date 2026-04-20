package com.nongxinle.service;

import java.util.Map;

/**
 * 批发商未结账批次核对（disCheckUnPayBillsGb）。
 */
public interface GbDistributerPurchaseBatchDisCheckUnPayBillsService {

    Map<String, Object> buildUnPayBillsSummary(Integer disId, Integer supplierId);
}
