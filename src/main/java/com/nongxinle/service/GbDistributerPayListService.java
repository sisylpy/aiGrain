package com.nongxinle.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nongxinle.entity.GbDistributerPayListEntity;

/**
 * 批发商结算记录Service接口
 */
public interface GbDistributerPayListService extends IService<GbDistributerPayListEntity> {

    // 老项目的 queryObject 方法，使用 default 委托给 getById
    default GbDistributerPayListEntity queryObject(Integer gbDistributerPayListId) {
        return getById(gbDistributerPayListId);
    }
    
    // 老项目的 update 方法，使用 default 委托给 updateById
    default boolean update(GbDistributerPayListEntity entity) {
        return updateById(entity);
    }
    
    // 老项目的 save 方法，使用 default 委托给 IService 的 save
    default boolean save(GbDistributerPayListEntity entity) {
        return IService.super.save(entity);
    }
}
