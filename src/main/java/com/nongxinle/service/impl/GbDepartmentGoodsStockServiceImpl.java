package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nongxinle.entity.GbDepartmentGoodsStockEntity;
import com.nongxinle.entity.GbDepartmentGoodsStockSimpleEntity;
import com.nongxinle.entity.GbDistributerFatherGoodsEntity;
import com.nongxinle.entity.GbDistributerGoodsEntity;
import com.nongxinle.mapper.GbDepartmentGoodsStockMapper;
import com.nongxinle.service.GbDepartmentGoodsStockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 部门商品库存Service实现
 */
@Service
public class GbDepartmentGoodsStockServiceImpl extends ServiceImpl<GbDepartmentGoodsStockMapper, GbDepartmentGoodsStockEntity> implements GbDepartmentGoodsStockService {

    @Autowired
    private GbDepartmentGoodsStockMapper gbDepartmentGoodsStockMapper;

    @Override
    public List<GbDepartmentGoodsStockEntity> queryGoodsStockByParams(Map<String, Object> map) {
        return gbDepartmentGoodsStockMapper.queryGoodsStockByParams(map);
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

}
