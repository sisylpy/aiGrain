package com.nongxinle.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nongxinle.entity.GbDistributerPurchaseBatchEntity;

import java.util.List;
import java.util.Map;

/**
 * 批发商采购批次Service接口
 */
public interface GbDistributerPurchaseBatchService extends IService<GbDistributerPurchaseBatchEntity> {

    // 查询批次及其订单信息
    GbDistributerPurchaseBatchEntity queryBatchWithOrders(Integer batchId);

    // 查询采购批次列表
    List<GbDistributerPurchaseBatchEntity> queryDisPurchaseBatchInfo(Map<String, Object> map);

    Integer queryDisPurchaseBatchCount(Map<String, Object> map);

    Double querySupplierUnSettleSubtotal(Map<String, Object> map);


    List<GbDistributerPurchaseBatchEntity> queryDisPurchaseBatchListWithOrders(Map<String, Object> map);
}
