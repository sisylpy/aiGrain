package com.nongxinle.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nongxinle.entity.GbDistributerGoodsEntity;

import java.util.List;
import java.util.Map;

/**
 * 批发商商品Service接口
 * 注意：老项目没有继承 IService，是直接定义的接口
 */
public interface GbDistributerGoodsService extends IService<GbDistributerGoodsEntity> {
    
    // 老项目的 queryObject 方法
    default GbDistributerGoodsEntity queryObject(Integer gbDistributerGoodsId) {
        return getById(gbDistributerGoodsId);
    }
    
    List<GbDistributerGoodsEntity> queryDisGoodsByParams(Map<String, Object> map);

    /**
     * 批发商商品快速搜索
     * 支持中文字名称、拼音、拼音首字母、别名搜索
     */
    List<GbDistributerGoodsEntity> queryGbDisGoodsQuickSearchStr(Map<String, Object> map);
    
    // 老项目的 update 方法，使用 default 委托给 updateById
    default boolean update(GbDistributerGoodsEntity entity) {
        return updateById(entity);
    }
    
    // 老项目的 delete 方法
    default boolean delete(Integer gbDistributerGoodsId) {
        return removeById(gbDistributerGoodsId);
    }
}
