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
    List<GbDistributerPurchaseBatchEntity> queryDisPurchaseBatch(Map<String, Object> map);

    Integer queryDisPurchaseBatchCount(Map<String, Object> map);

    Double querySupplierUnSettleSubtotal(Map<String, Object> map);

    // 查询采购批次详细信息（老项目兼容方法）
    List<GbDistributerPurchaseBatchEntity> queryDisPurchaseBatchInfo(Map<String, Object> map);

    // 老项目的 queryObject 方法，使用 default 委托给 getById
    default GbDistributerPurchaseBatchEntity queryObject(Integer batchId) {
        return getById(batchId);
    }

    // 老项目的 update 方法，使用 default 委托给 updateById
    default boolean update(GbDistributerPurchaseBatchEntity entity) {
        return updateById(entity);
    }

}
