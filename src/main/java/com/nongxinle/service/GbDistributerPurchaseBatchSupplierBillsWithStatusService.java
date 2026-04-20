package com.nongxinle.service;

import com.nongxinle.entity.GbDistributerPurchaseBatchEntity;

import java.util.List;

/**
 * 按状态、日期区间查询供货商与批发商之间的采购批次（disGetGbSupplierBillsWithStatus）。
 */
public interface GbDistributerPurchaseBatchSupplierBillsWithStatusService {

    List<GbDistributerPurchaseBatchEntity> queryBatches(Integer supplierId, String status, Integer disId,
                                                        String startDate, String stopDate);
}
