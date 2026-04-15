package com.nongxinle.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nongxinle.entity.GbDepartmentGoodsStockEntity;
import com.nongxinle.entity.GbDepartmentGoodsStockSimpleEntity;
import com.nongxinle.entity.GbDistributerFatherGoodsEntity;
import com.nongxinle.entity.GbDistributerGoodsEntity;

import java.util.List;
import java.util.Map;

/**
 * 部门商品库存Service接口
 */
public interface GbDepartmentGoodsStockService extends IService<GbDepartmentGoodsStockEntity> {

    /**
     * 根据参数查询部门库存
     */
    List<GbDepartmentGoodsStockEntity> queryGoodsStockByParams(Map<String, Object> map);

    /**
     * 查询库存商品数量
     */
    Integer queryGoodsStockCount(Map<String, Object> map);

    /**
     * 查询部门商品剩余总量
     */
    Double queryDepGoodsRestTotal(Map<String, Object> map);

    /**
     * 查询部门库存树形父商品
     */
    List<GbDistributerFatherGoodsEntity> queryDepStockTreeFatherGoodsByParams(Map<String, Object> map);

    /**
     * 根据参数查询分销商商品库存
     */
    List<GbDistributerGoodsEntity> queryDisGoodsStockByParams(Map<String, Object> map);

    /**
     * 查询部门商品损耗总量
     */
    Double queryDepGoodsWasteTotal(Map<String, Object> map);

    /**
     * 查询分销库存商品数量
     */
    Integer queryDisStockGoodsCount(Map<String, Object> map);

    /**
     * 根据参数查询部门库存（简化版）
     */
    List<GbDepartmentGoodsStockSimpleEntity> queryGoodsStockSimpleByParams(Map<String, Object> map);

}
