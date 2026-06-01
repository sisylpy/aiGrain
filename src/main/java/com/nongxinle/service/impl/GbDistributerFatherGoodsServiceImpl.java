package com.nongxinle.service.impl;

import com.nongxinle.mapper.GbDistributerFatherGoodsMapper;
import com.nongxinle.entity.GbDistributerFatherGoodsEntity;
import com.nongxinle.service.GbDistributerFatherGoodsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;

@Service("gbDistributerFatherGoodsService")
public class GbDistributerFatherGoodsServiceImpl implements GbDistributerFatherGoodsService {

    @Autowired
    private GbDistributerFatherGoodsMapper gbDistributerFatherGoodsMapper;

    @Override
    public GbDistributerFatherGoodsEntity queryObject(Integer gbDistributerFatherGoodsId) {
        return gbDistributerFatherGoodsMapper.queryObject(gbDistributerFatherGoodsId);
    }

    @Override
    public List<GbDistributerFatherGoodsEntity> queryList(Map<String, Object> map) {
        return gbDistributerFatherGoodsMapper.queryList(map);
    }

    @Override
    public int queryTotal(Map<String, Object> map) {
        return gbDistributerFatherGoodsMapper.queryTotal(map);
    }

    @Override
    public void save(GbDistributerFatherGoodsEntity gbDistributerFatherGoods) {
        gbDistributerFatherGoodsMapper.save(gbDistributerFatherGoods);
    }

    @Override
    public void update(GbDistributerFatherGoodsEntity gbDistributerFatherGoods) {
        gbDistributerFatherGoodsMapper.update(gbDistributerFatherGoods);
    }

    @Override
    public void delete(Integer gbDistributerFatherGoodsId) {
        gbDistributerFatherGoodsMapper.delete(gbDistributerFatherGoodsId);
    }

    @Override
    public void deleteBatch(Integer[] gbDistributerFatherGoodsIds) {
        gbDistributerFatherGoodsMapper.deleteBatch(gbDistributerFatherGoodsIds);
    }

    @Override
    public List<GbDistributerFatherGoodsEntity> querySubFatherGoods(Integer goodsId) {
        return gbDistributerFatherGoodsMapper.querySubFatherGoods(goodsId);
    }

    @Override
    public List<GbDistributerFatherGoodsEntity> queryHasDisFathersFather(Map<String, Object> map3) {
        return gbDistributerFatherGoodsMapper.queryHasDisFathersFather(map3);
    }

    @Override
    public List<GbDistributerFatherGoodsEntity> queryDisGoodsCata(Map<String, Object> map) {
        return gbDistributerFatherGoodsMapper.queryDisGoodsCata(map);
    }

    @Override
    public List<GbDistributerFatherGoodsEntity> queryDisStockOrdersFatherGoods(Map<String, Object> map) {
        return gbDistributerFatherGoodsMapper.queryDisStockOrdersFatherGoods(map);
    }

    @Override
    public List<GbDistributerFatherGoodsEntity> queryDisFathersGoodsByParamsGb(Map<String, Object> mapGrand) {
        return gbDistributerFatherGoodsMapper.queryDisFathersGoodsByParamsGb(mapGrand);
    }

    @Override
    public List<GbDistributerFatherGoodsEntity> queryDisGoodsCataWithGoods(Map<String, Object> map) {
        return gbDistributerFatherGoodsMapper.queryDisGoodsCataWithGoods(map);
    }

    @Override
    public GbDistributerFatherGoodsEntity queryAppFatherGoods(Map<String, Object> map) {
        return gbDistributerFatherGoodsMapper.queryAppFatherGoods(map);
    }

    @Override
    public List<GbDistributerFatherGoodsEntity> queryDisFathersGoodsByNxGoodsId(Integer nxGoodsId) {
        return gbDistributerFatherGoodsMapper.queryDisFathersGoodsByNxGoodsId(nxGoodsId);
    }

    @Override
    public List<GbDistributerFatherGoodsEntity> queryDisGoodsCataLinshi(Integer nxDistributerId) {
        return gbDistributerFatherGoodsMapper.queryDisGoodsCataLinshi(nxDistributerId);
    }

    @Override
    public GbDistributerFatherGoodsEntity queryDisGoodsCataLinshiFatherGoods(Map<String, Object> map) {
        return gbDistributerFatherGoodsMapper.queryDisGoodsCataLinshiFatherGoods(map);
    }

    @Override
    public int queryGbFatherGoodsMaxSort(Map<String, Object> map5) {
        return gbDistributerFatherGoodsMapper.queryGbFatherGoodsMaxSort(map5);
    }

    @Override
    public List<GbDistributerFatherGoodsEntity> queryDisGoodsCataWithFilter(Map<String, Object> mapG) {
        return gbDistributerFatherGoodsMapper.queryDisGoodsCataWithFilter(mapG);
    }

    @Override
    public TreeSet<GbDistributerFatherGoodsEntity> queryPurchaseGoodsFatherTypes(Map<String, Object> map) {
        List<GbDistributerFatherGoodsEntity> list = gbDistributerFatherGoodsMapper.queryPurchaseGoodsFatherTypes(map);
        return list != null ? new TreeSet<>(list) : new TreeSet<>();
    }
}
