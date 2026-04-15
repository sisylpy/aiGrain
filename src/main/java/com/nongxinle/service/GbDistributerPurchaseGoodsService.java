package com.nongxinle.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nongxinle.entity.GbDepartmentUserEntity;
import com.nongxinle.entity.GbDistributerFatherGoodsEntity;
import com.nongxinle.entity.GbDistributerGoodsEntity;
import com.nongxinle.entity.GbDistributerPurchaseGoodsEntity;
import com.nongxinle.entity.NxJrdhSupplierEntity;

import java.util.List;
import java.util.Map;

/**
 * 批发商采购商品Service接口
 */
public interface GbDistributerPurchaseGoodsService extends IService<GbDistributerPurchaseGoodsEntity> {

    // 老项目的 queryObject 方法，使用 default 委托给 getById
    default GbDistributerPurchaseGoodsEntity queryObject(Integer gbDistributerPurchaseGoodsId) {
        return getById(gbDistributerPurchaseGoodsId);
    }
    
    // 老项目的 update 方法，使用 default 委托给 updateById
    default boolean update(GbDistributerPurchaseGoodsEntity entity) {
        return updateById(entity);
    }
    
    // 老项目的 save 方法，使用 default 委托给 IService 的 save
    default boolean save(GbDistributerPurchaseGoodsEntity entity) {
        // 调用 IService 的 save 方法
        return IService.super.save(entity);
    }

    List<GbDistributerPurchaseGoodsEntity> querySimplePurGoods(Map<String, Object> map);

    GbDistributerPurchaseGoodsEntity queryPurchaseGoodsLastItem(Map<String, Object> map);

    List<NxJrdhSupplierEntity> queryDisPurGoodsSupplierList(Map<String, Object> map);

    Integer queryPurchaseGoodsCount(Map<String, Object> map);

    Double queryPurchaseGoodsSubTotal(Map<String, Object> map);

    Integer queryGbPurchaseGoodsCount(Map<String, Object> map);

    Integer queryGbGoodsCount(Map<String, Object> map);

    List<GbDepartmentUserEntity> queryPurUserList(Map<String, Object> map);

    List<GbDistributerGoodsEntity> queryGbPurchaseGoodsTopTimes(Map<String, Object> map);

    List<GbDistributerGoodsEntity> queryGbPurchaseGoodsTopSubtotal(Map<String, Object> map);

    Double queryGbPurchaseSubtotalTopSubtotal(Map<String, Object> map);

    List<GbDistributerGoodsEntity> queryGbPurchaseGoodsTopPriceFluctuation(Map<String, Object> map);

    Integer queryGbDisGoodsTreeCount(Map<String, Object> queryMap);

    List<GbDistributerGoodsEntity> queryDisTreeGoodsWithPurList(Map<String, Object> queryMap);

    List<GbDistributerGoodsEntity> queryPurchaseGoodsFatherTypeByParams(Map<String, Object> queryMap);

    List<GbDistributerPurchaseGoodsEntity> queryPurchaseGoodsWithDetailByParams(Map<String, Object> map);

    List<GbDistributerPurchaseGoodsEntity> queryOnlyPurGoods(Map<String, Object> map);

}
