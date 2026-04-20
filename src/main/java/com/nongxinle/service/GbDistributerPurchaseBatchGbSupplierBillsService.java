package com.nongxinle.service;

import java.util.List;
import java.util.Map;

/**
 * 供货商近三个月未结账单列表与汇总（disGetGbSupplierBills）。
 */
public interface GbDistributerPurchaseBatchGbSupplierBillsService {

    /**
     * @return 与原先一致：前 3 个元素为各月 {month, arr, total}，第 4 个为 {unSettleSubtotal, listTotal}
     */
    List<Map<String, Object>> buildGbSupplierBills(Integer supplierId, Integer disId);
}
