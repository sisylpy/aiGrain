package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nongxinle.entity.GbDepartmentEntity;
import com.nongxinle.entity.GbDepartmentGoodsDailyEntity;
import com.nongxinle.entity.GbDistributerFatherGoodsEntity;
import com.nongxinle.entity.GbDistributerGoodsEntity;
import com.nongxinle.mapper.GbDepartmentGoodsDailyMapper;
import com.nongxinle.service.GbDepartmentGoodsDailyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 部门商品日报Service实现
 */
@Service
public class GbDepartmentGoodsDailyServiceImpl extends ServiceImpl<GbDepartmentGoodsDailyMapper, GbDepartmentGoodsDailyEntity> implements GbDepartmentGoodsDailyService {

    @Autowired
    private GbDepartmentGoodsDailyMapper gbDepartmentGoodsDailyMapper;

    @Override
    public GbDepartmentGoodsDailyEntity queryDepGoodsDailyItem(Map<String, Object> map) {
        return gbDepartmentGoodsDailyMapper.queryDepGoodsDailyItem(map);
    }

    @Override
    public Integer queryDepGoodsDailyCount(Map<String, Object> map) {
        return gbDepartmentGoodsDailyMapper.queryDepGoodsDailyCount(map);
    }

    @Override
    public List<GbDepartmentGoodsDailyEntity> queryDepGoodsDailyListByParams(Map<String, Object> map) {
        return gbDepartmentGoodsDailyMapper.queryDepGoodsDailyListByParams(map);
    }

    @Override
    public Double queryDepGoodsDailyProfitSubtotal(Map<String, Object> map) {
        return gbDepartmentGoodsDailyMapper.queryDepGoodsDailyProfitSubtotal(map);
    }

    @Override
    public Double queryDepGoodsDailySalesSubtotal(Map<String, Object> map) {
        return gbDepartmentGoodsDailyMapper.queryDepGoodsDailySalesSubtotal(map);
    }

    @Override
    public Double queryDepGoodsDailySalesProfitSubtotal(Map<String, Object> map) {
        return gbDepartmentGoodsDailyMapper.queryDepGoodsDailySalesProfitSubtotal(map);
    }

    @Override
    public List<GbDistributerFatherGoodsEntity> queryDepDailyGoodsFatherTypeByParams(Map<String, Object> map) {
        return gbDepartmentGoodsDailyMapper.queryDepDailyGoodsFatherTypeByParams(map);
    }

    @Override
    public TreeSet<GbDistributerGoodsEntity> queryDisGoodsTreesetByParams(Map<String, Object> map) {
        return gbDepartmentGoodsDailyMapper.queryDisGoodsTreesetByParams(map);
    }

    @Override
    public Double queryDepGoodsDailyLossSubtotal(Map<String, Object> map) {
        return gbDepartmentGoodsDailyMapper.queryDepGoodsDailyLossSubtotal(map);
    }

    @Override
    public Double queryDepGoodsDailyWasteSubtotal(Map<String, Object> map) {
        return gbDepartmentGoodsDailyMapper.queryDepGoodsDailyWasteSubtotal(map);
    }

    @Override
    public TreeSet<GbDepartmentEntity> queryWhichDepsHasProduceDepGoodsDaily(Map<String, Object> map) {
        return gbDepartmentGoodsDailyMapper.queryWhichDepsHasProduceDepGoodsDaily(map);
    }

    @Override
    public Double queryDepGoodsDailyProduceWeight(Map<String, Object> map) {
        return gbDepartmentGoodsDailyMapper.queryDepGoodsDailyProduceWeight(map);
    }

    @Override
    public Double queryDepGoodsDailyLossWeight(Map<String, Object> map) {
        return gbDepartmentGoodsDailyMapper.queryDepGoodsDailyLossWeight(map);
    }

    @Override
    public Double queryDepGoodsDailyWasteWeight(Map<String, Object> map) {
        return gbDepartmentGoodsDailyMapper.queryDepGoodsDailyWasteWeight(map);
    }

    @Override
    public TreeSet<GbDistributerFatherGoodsEntity> queryFreshFatherGoods(Map<String, Object> map) {
        return gbDepartmentGoodsDailyMapper.queryFreshFatherGoods(map);
    }

    @Override
    public Double queryDepGoodsDailyRestWeight(Map<String, Object> map) {
        return gbDepartmentGoodsDailyMapper.queryDepGoodsDailyRestWeight(map);
    }

    @Override
    public TreeSet<GbDepartmentEntity> queryDepDisGoodsTreeByParams(Map<String, Object> map) {
        return gbDepartmentGoodsDailyMapper.queryDepDisGoodsTreeByParams(map);
    }

    @Override
    public Double queryDepGoodsDailyWeight(Map<String, Object> map) {
        return gbDepartmentGoodsDailyMapper.queryDepGoodsDailyWeight(map);
    }

    @Override
    public Double queryDepGoodsDailyLastWeight(Map<String, Object> map) {
        return gbDepartmentGoodsDailyMapper.queryDepGoodsDailyLastWeight(map);
    }

    @Override
    public TreeSet<GbDistributerFatherGoodsEntity> queryClearFatherGoods(Map<String, Object> map) {
        return gbDepartmentGoodsDailyMapper.queryClearFatherGoods(map);
    }

    @Override
    public Double queryDepGoodsDailyProduceSubtotal(Map<String, Object> map) {
        return gbDepartmentGoodsDailyMapper.queryDepGoodsDailyProduceSubtotal(map);
    }

    @Override
    public Double queryDepGoodsDailySubtotal(Map<String, Object> map) {
        return gbDepartmentGoodsDailyMapper.queryDepGoodsDailySubtotal(map);
    }

    @Override
    public double queryDepGoodsDailyReturnSubtotal(Map<String, Object> map) {
        return gbDepartmentGoodsDailyMapper.queryDepGoodsDailyReturnSubtotal(map);
    }

    @Override
    public Double queryDepGoodsDailyReturnWeight(Map<String, Object> map) {
        return gbDepartmentGoodsDailyMapper.queryDepGoodsDailyReturnWeight(map);
    }

    @Override
    public TreeSet<GbDepartmentEntity> queryWhichDepsHasProduceDepGoodsDailyNew(Map<String, Object> map) {
        return gbDepartmentGoodsDailyMapper.queryWhichDepsHasProduceDepGoodsDailyNew(map);
    }

    @Override
    public List<GbDepartmentGoodsDailyEntity> queryDepGoodsDailyListWithGoodsByParams(Map<String, Object> map) {
        return gbDepartmentGoodsDailyMapper.queryDepGoodsDailyListWithGoodsByParams(map);
    }

    @Override
    public List<GbDepartmentGoodsDailyEntity> queryDepGoodsDailyListWithReduceByParams(Map<String, Object> map) {
        return gbDepartmentGoodsDailyMapper.queryDepGoodsDailyListWithReduceByParams(map);
    }

    @Override
    public TreeSet<GbDistributerGoodsEntity> queryDisGoodsWithBusinessDep(Map<String, Object> map) {
        return gbDepartmentGoodsDailyMapper.queryDisGoodsWithBusinessDep(map);
    }

    @Override
    public TreeSet<GbDistributerGoodsEntity> queryDisGoodsTreeByParams(Map<String, Object> map) {
        return gbDepartmentGoodsDailyMapper.queryDisGoodsTreeByParams(map);
    }

    @Override
    public TreeSet<GbDistributerFatherGoodsEntity> queryDepDailyGoodsFatherTypeByParamsTree(Map<String, Object> map) {
        return gbDepartmentGoodsDailyMapper.queryDepDailyGoodsFatherTypeByParamsTree(map);
    }

    @Override
    public Double queryDepGoodsDailyRestSubtotal(Map<String, Object> map) {
        return gbDepartmentGoodsDailyMapper.queryDepGoodsDailyRestSubtotal(map);
    }

}
