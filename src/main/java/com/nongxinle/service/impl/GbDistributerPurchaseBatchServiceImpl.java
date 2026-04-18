package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nongxinle.mapper.GbDistributerPurchaseBatchMapper;
import com.nongxinle.service.GbDistributerPurchaseBatchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.nongxinle.entity.GbDistributerPurchaseBatchEntity;

import java.util.List;
import java.util.Map;

/**
 * 批发商采购批次Service实现
 */
@Service
public class GbDistributerPurchaseBatchServiceImpl extends ServiceImpl<GbDistributerPurchaseBatchMapper, GbDistributerPurchaseBatchEntity> implements GbDistributerPurchaseBatchService {

    @Autowired
    private GbDistributerPurchaseBatchMapper gbDistributerPurchaseBatchMapper;

    @Override
    public GbDistributerPurchaseBatchEntity queryBatchWithOrders(Integer batchId) {
        return gbDistributerPurchaseBatchMapper.queryBatchWithOrders(batchId);
    }

    @Override
    public List<GbDistributerPurchaseBatchEntity> queryDisPurchaseBatchInfo(Map<String, Object> map) {
        return gbDistributerPurchaseBatchMapper.queryDisPurchaseBatchInfo(map);
    }

    @Override
    public Integer queryDisPurchaseBatchCount(Map<String, Object> map) {
        return gbDistributerPurchaseBatchMapper.queryDisPurchaseBatchCount(map);
    }

    @Override
    public Double querySupplierUnSettleSubtotal(Map<String, Object> map) {
        return gbDistributerPurchaseBatchMapper.querySupplierUnSettleSubtotal(map);
    }

    @Override
    public List<GbDistributerPurchaseBatchEntity> queryDisPurchaseBatchListWithOrders(Map<String, Object> map) {

        return gbDistributerPurchaseBatchMapper.queryDisPurchaseBatchListWithOrders(map);
    }


}
