package com.nongxinle.service;

import com.nongxinle.entity.GbDepartmentOrdersEntity;

import java.util.List;

/**
 * 供应商按批次查询可打印订单列表（supplierGetPrintBatchGb）。
 */
public interface GbDistributerPurchaseBatchSupplierGetPrintBatchService {

    List<GbDepartmentOrdersEntity> listOrdersForSupplierPrint(Integer batchId);
}
