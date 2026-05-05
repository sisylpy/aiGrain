package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nongxinle.dto.GbDepGoodsStockAdjustRequest;
import com.nongxinle.dto.GbDepGoodsStockAdjustResult;
import com.nongxinle.entity.GbDepartmentGoodsStockEntity;
import com.nongxinle.entity.GbDepartmentGoodsStockSimpleEntity;
import com.nongxinle.entity.GbDistributerFatherGoodsEntity;
import com.nongxinle.entity.GbDistributerGoodsEntity;
import com.nongxinle.mapper.GbDepartmentGoodsStockMapper;
import com.nongxinle.service.GbDepartmentGoodsStockLedgerService;
import com.nongxinle.service.GbDepartmentGoodsStockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 部门商品库存：查询与持久化委托给 Mapper；与 reduce / 日报 / 部门商品的编排见 {@link GbDepartmentGoodsStockLedgerService}。
 */
@Service
public class GbDepartmentGoodsStockServiceImpl extends ServiceImpl<GbDepartmentGoodsStockMapper, GbDepartmentGoodsStockEntity> implements GbDepartmentGoodsStockService {

    @Autowired
    private GbDepartmentGoodsStockMapper gbDepartmentGoodsStockMapper;
    @Autowired
    @Lazy
    private GbDepartmentGoodsStockLedgerService gbDepartmentGoodsStockLedgerService;

    @Override
    public List<GbDepartmentGoodsStockEntity> queryGoodsStockByParams(Map<String, Object> map) {
        return gbDepartmentGoodsStockMapper.queryGoodsStockByParams(map);
    }

    @Override
    public List<GbDepartmentGoodsStockEntity> queryGoodsStockListForMendianPeriod(Map<String, Object> map) {
        return gbDepartmentGoodsStockMapper.queryGoodsStockListForMendianPeriod(map);
    }

    @Override
    public Integer queryGoodsStockCount(Map<String, Object> map) {
        return gbDepartmentGoodsStockMapper.queryGoodsStockCount(map);
    }

    @Override
    public Double queryDepGoodsRestTotal(Map<String, Object> map) {
        return gbDepartmentGoodsStockMapper.queryDepGoodsRestTotal(map);
    }

    @Override
    public Double queryDepGoodsRestWeightTotal(Map<String, Object> map) {
        return gbDepartmentGoodsStockMapper.queryDepGoodsRestWeightTotal(map);
    }

    @Override
    public Double queryDepGoodsSubtotal(Map<String, Object> map) {
        return gbDepartmentGoodsStockMapper.queryDepGoodsSubtotal(map);
    }

    @Override
    public Double queryDepStockWeightTotal(Map<String, Object> map) {
        return gbDepartmentGoodsStockMapper.queryDepStockWeightTotal(map);
    }

    @Override
    public List<GbDistributerFatherGoodsEntity> queryDepStockTreeFatherGoodsByParams(Map<String, Object> map) {
        return gbDepartmentGoodsStockMapper.queryDepStockTreeFatherGoodsByParams(map);
    }

    @Override
    public List<GbDistributerGoodsEntity> queryDisGoodsStockByParams(Map<String, Object> map) {
        return gbDepartmentGoodsStockMapper.queryDisGoodsStockByParams(map);
    }

    @Override
    public Double queryDepGoodsWasteTotal(Map<String, Object> map) {
        return gbDepartmentGoodsStockMapper.queryDepGoodsWasteTotal(map);
    }

    @Override
    public Integer queryDisStockGoodsCount(Map<String, Object> map) {
        return gbDepartmentGoodsStockMapper.queryDisStockGoodsCount(map);
    }

    @Override
    public List<GbDepartmentGoodsStockSimpleEntity> queryGoodsStockSimpleByParams(Map<String, Object> map) {
        return gbDepartmentGoodsStockMapper.queryGoodsStockSimpleByParams(map);
    }

    @Override
    public GbDepartmentGoodsStockEntity queryReturnStockItemByOrderId(Integer orderId) {
        if (orderId == null) {
            return null;
        }
        return getOne(new LambdaQueryWrapper<GbDepartmentGoodsStockEntity>()
                .eq(GbDepartmentGoodsStockEntity::getGbDgsGbDepartmentOrderId, orderId)
                .last("LIMIT 1"));
    }

    @Override
    public Double queryDepStockRestSubtotal(Map<String, Object> queryMap) {

        return gbDepartmentGoodsStockMapper.queryDepGoodsRestTotal(queryMap);
    }

    @Override
    public GbDepGoodsStockAdjustResult adjustDepGoodsStock(GbDepGoodsStockAdjustRequest request) {
        return gbDepartmentGoodsStockLedgerService.adjustDepGoodsStock(request);
    }
}
