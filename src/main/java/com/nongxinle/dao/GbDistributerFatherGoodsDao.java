package com.nongxinle.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nongxinle.entity.GbDistributerFatherGoodsEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface GbDistributerFatherGoodsDao extends BaseMapper<GbDistributerFatherGoodsEntity> {

    GbDistributerFatherGoodsEntity queryObject(Integer gbDistributerFatherGoodsId);

    List<GbDistributerFatherGoodsEntity> queryList(Map<String, Object> map);

    int queryTotal(Map<String, Object> map);

    void save(GbDistributerFatherGoodsEntity gbDistributerFatherGoods);

    void update(GbDistributerFatherGoodsEntity gbDistributerFatherGoods);

    void delete(Integer gbDistributerFatherGoodsId);

    void deleteBatch(Integer[] gbDistributerFatherGoodsIds);

    List<GbDistributerFatherGoodsEntity> querySubFatherGoods(Integer goodsId);

    List<GbDistributerFatherGoodsEntity> queryHasDisFathersFather(Map<String, Object> map3);

    List<GbDistributerFatherGoodsEntity> queryDisGoodsCata(Map<String, Object> map);

    List<GbDistributerFatherGoodsEntity> queryDisStockOrdersFatherGoods(Map<String, Object> map);

    List<GbDistributerFatherGoodsEntity> queryDisFathersGoodsByParamsGb(Map<String, Object> mapGrand);

    List<GbDistributerFatherGoodsEntity> queryDisGoodsCataWithGoods(Map<String, Object> map);

    GbDistributerFatherGoodsEntity queryAppFatherGoods(Map<String, Object> map);

    List<GbDistributerFatherGoodsEntity> queryDisFathersGoodsByNxGoodsId(Integer nxGoodsId);

    List<GbDistributerFatherGoodsEntity> queryDisGoodsCataLinshi(Integer nxDistributerId);

    GbDistributerFatherGoodsEntity queryDisGoodsCataLinshiFatherGoods(Map<String, Object> map);

    int queryGbFatherGoodsMaxSort(Map<String, Object> map5);

    List<GbDistributerFatherGoodsEntity> queryDisGoodsCataWithFilter(Map<String, Object> mapG);

    List<GbDistributerFatherGoodsEntity> queryPurchaseGoodsFatherTypes(Map<String, Object> map);
}
