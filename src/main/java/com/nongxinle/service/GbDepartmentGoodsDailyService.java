package com.nongxinle.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nongxinle.entity.GbDepartmentGoodsDailyEntity;
import com.nongxinle.entity.GbDepartmentEntity;
import com.nongxinle.entity.GbDistributerFatherGoodsEntity;
import com.nongxinle.entity.GbDistributerGoodsEntity;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 部门商品日报Service接口
 */
public interface GbDepartmentGoodsDailyService extends IService<GbDepartmentGoodsDailyEntity> {

    GbDepartmentGoodsDailyEntity queryDepGoodsDailyItem(Map<String, Object> map);

    Integer queryDepGoodsDailyCount(Map<String, Object> map);

    List<GbDepartmentGoodsDailyEntity> queryDepGoodsDailyListByParams(Map<String, Object> map);

    Double queryDepGoodsDailyProfitSubtotal(Map<String, Object> map);

    Double queryDepGoodsDailySalesSubtotal(Map<String, Object> map);

    Double queryDepGoodsDailySalesProfitSubtotal(Map<String, Object> map);

    List<GbDistributerFatherGoodsEntity> queryDepDailyGoodsFatherTypeByParams(Map<String, Object> map);

    TreeSet<GbDistributerGoodsEntity> queryDisGoodsTreesetByParams(Map<String, Object> map);

    Double queryDepGoodsDailyLossSubtotal(Map<String, Object> map);

    Double queryDepGoodsDailyWasteSubtotal(Map<String, Object> map);

    TreeSet<GbDepartmentEntity> queryWhichDepsHasProduceDepGoodsDaily(Map<String, Object> map);

    Double queryDepGoodsDailyProduceWeight(Map<String, Object> map);

    Double queryDepGoodsDailyLossWeight(Map<String, Object> map);

    Double queryDepGoodsDailyWasteWeight(Map<String, Object> map);

    TreeSet<GbDistributerFatherGoodsEntity> queryFreshFatherGoods(Map<String, Object> map);

    Double queryDepGoodsDailyRestWeight(Map<String, Object> map);

    TreeSet<GbDepartmentEntity> queryDepDisGoodsTreeByParams(Map<String, Object> map);

    Double queryDepGoodsDailyWeight(Map<String, Object> map);

    Double queryDepGoodsDailyLastWeight(Map<String, Object> map);

    TreeSet<GbDistributerFatherGoodsEntity> queryClearFatherGoods(Map<String, Object> map);

    Double queryDepGoodsDailyProduceSubtotal(Map<String, Object> map);

    Double queryDepGoodsDailySubtotal(Map<String, Object> map);

    double queryDepGoodsDailyReturnSubtotal(Map<String, Object> map);

    Double queryDepGoodsDailyReturnWeight(Map<String, Object> map);

    TreeSet<GbDepartmentEntity> queryWhichDepsHasProduceDepGoodsDailyNew(Map<String, Object> map);

    List<GbDepartmentGoodsDailyEntity> queryDepGoodsDailyListWithGoodsByParams(Map<String, Object> map);

    List<GbDepartmentGoodsDailyEntity> queryDepGoodsDailyListWithReduceByParams(Map<String, Object> map);

    TreeSet<GbDistributerGoodsEntity> queryDisGoodsWithBusinessDep(Map<String, Object> map);

    TreeSet<GbDistributerGoodsEntity> queryDisGoodsTreeByParams(Map<String, Object> map);

    TreeSet<GbDistributerFatherGoodsEntity> queryDepDailyGoodsFatherTypeByParamsTree(Map<String, Object> map);

    Double queryDepGoodsDailyRestSubtotal(Map<String, Object> map);

}
