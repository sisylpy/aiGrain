package com.nongxinle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nongxinle.entity.GbDepartmentUserEntity;
import com.nongxinle.entity.GbDistributerGoodsEntity;
import com.nongxinle.entity.GbDistributerPurchaseGoodsEntity;
import com.nongxinle.entity.NxJrdhSupplierEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 批发商采购商品Mapper接口
 */
@Mapper
public interface GbDistributerPurchaseGoodsMapper extends BaseMapper<GbDistributerPurchaseGoodsEntity> {

    List<GbDistributerPurchaseGoodsEntity> querySimplePurGoods(Map<String, Object> map);

    GbDistributerPurchaseGoodsEntity queryPurchaseGoodsLastItem(Map<String, Object> map);

    List<NxJrdhSupplierEntity> queryDisPurGoodsSupplierList(Map<String, Object> map);

    Integer queryPurchaseGoodsCount(@Param("params") Map<String, Object> map);

    Double queryPurchaseGoodsSubTotal(@Param("params") Map<String, Object> map);

    Integer queryGbPurchaseGoodsCount(Map<String, Object> map);

    Integer queryGbGoodsCount(Map<String, Object> map);

    List<GbDepartmentUserEntity> queryPurUserList(Map<String, Object> map);

    List<GbDistributerGoodsEntity> queryGbPurchaseGoodsTopTimes(Map<String, Object> map);

    List<GbDistributerGoodsEntity> queryGbPurchaseGoodsTopSubtotal(Map<String, Object> map);

    Double queryGbPurchaseSubtotalTopSubtotal(Map<String, Object> map);

    List<GbDistributerGoodsEntity> queryGbPurchaseGoodsTopPriceFluctuation(Map<String, Object> map);

    Integer queryGbDisGoodsTreeCount(Map<String, Object> map);

    List<GbDistributerGoodsEntity> queryDisTreeGoodsWithPurList(Map<String, Object> map);

    List<GbDistributerGoodsEntity> queryPurchaseGoodsFatherTypeByParams(Map<String, Object> map);

    List<GbDistributerPurchaseGoodsEntity> queryPurchaseGoodsWithDetailByParams(Map<String, Object> map);

    List<GbDistributerPurchaseGoodsEntity> queryOnlyPurGoods(Map<String, Object> map);

}
