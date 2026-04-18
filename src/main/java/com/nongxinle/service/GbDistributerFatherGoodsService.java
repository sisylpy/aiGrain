package com.nongxinle.service;

import com.nongxinle.entity.GbDistributerFatherGoodsEntity;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public interface GbDistributerFatherGoodsService {

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

    /**
     * 按采购筛选条件，列出涉及到的「曾祖父」级批发商商品分类（分类树最高层级，即最大一级类目）。
     * <p>数据来自 {@code gb_DPG_dis_goods_father_id} 与 {@code gb_distributer_father_goods} 的关联。
     *
     * @param map 与采购统计一致，常用：{@code disId}、{@code startDate}、{@code stopDate}、{@code dayuStatus}、{@code typeNotEqual}
     */
    TreeSet<GbDistributerFatherGoodsEntity> queryPurchaseGoodsFatherTypes(Map<String, Object> map);
}
