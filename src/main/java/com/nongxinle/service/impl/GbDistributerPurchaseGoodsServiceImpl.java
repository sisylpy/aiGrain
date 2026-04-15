package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nongxinle.entity.GbDepartmentUserEntity;
import com.nongxinle.entity.GbDistributerFatherGoodsEntity;
import com.nongxinle.entity.GbDistributerGoodsEntity;
import com.nongxinle.entity.GbDistributerPurchaseGoodsEntity;
import com.nongxinle.entity.NxJrdhSupplierEntity;
import com.nongxinle.mapper.GbDistributerPurchaseGoodsMapper;
import com.nongxinle.service.GbDistributerPurchaseGoodsService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 批发商采购商品Service实现
 */
@Service
public class GbDistributerPurchaseGoodsServiceImpl extends ServiceImpl<GbDistributerPurchaseGoodsMapper, GbDistributerPurchaseGoodsEntity> implements GbDistributerPurchaseGoodsService {

    @Override
    public List<GbDistributerPurchaseGoodsEntity> querySimplePurGoods(Map<String, Object> map) {
        return baseMapper.querySimplePurGoods(map);
    }

    @Override
    public GbDistributerPurchaseGoodsEntity queryPurchaseGoodsLastItem(Map<String, Object> map) {
        return baseMapper.queryPurchaseGoodsLastItem(map);
    }

    @Override
    public List<NxJrdhSupplierEntity> queryDisPurGoodsSupplierList(Map<String, Object> map) {
        return baseMapper.queryDisPurGoodsSupplierList(map);
    }

    @Override
    public Integer queryPurchaseGoodsCount(Map<String, Object> map) {
        return baseMapper.queryPurchaseGoodsCount(map);
    }

    @Override
    public Double queryPurchaseGoodsSubTotal(Map<String, Object> map) {
        return baseMapper.queryPurchaseGoodsSubTotal(map);
    }

    @Override
    public Integer queryGbPurchaseGoodsCount(Map<String, Object> map) {
        return baseMapper.queryGbPurchaseGoodsCount(map);
    }

    @Override
    public Integer queryGbGoodsCount(Map<String, Object> map) {
        return baseMapper.queryGbGoodsCount(map);
    }

    @Override
    public List<GbDepartmentUserEntity> queryPurUserList(Map<String, Object> map) {
        return baseMapper.queryPurUserList(map);
    }

    @Override
    public Integer queryGbDisGoodsTreeCount(Map<String, Object> queryMap) {
        return baseMapper.queryGbDisGoodsTreeCount(queryMap);
    }

    @Override
    public List<GbDistributerGoodsEntity> queryDisTreeGoodsWithPurList(Map<String, Object> queryMap) {
        return baseMapper.queryDisTreeGoodsWithPurList(queryMap);
    }

    @Override
    public List<GbDistributerGoodsEntity> queryPurchaseGoodsFatherTypeByParams(Map<String, Object> queryMap) {
        return baseMapper.queryPurchaseGoodsFatherTypeByParams(queryMap);
    }

    @Override
    public List<GbDistributerGoodsEntity> queryGbPurchaseGoodsTopTimes(Map<String, Object> map) {
        return baseMapper.queryGbPurchaseGoodsTopTimes(map);
    }

    @Override
    public List<GbDistributerGoodsEntity> queryGbPurchaseGoodsTopSubtotal(Map<String, Object> map) {
        return baseMapper.queryGbPurchaseGoodsTopSubtotal(map);
    }

    @Override
    public Double queryGbPurchaseSubtotalTopSubtotal(Map<String, Object> map) {
        return baseMapper.queryGbPurchaseSubtotalTopSubtotal(map);
    }

    @Override
    public List<GbDistributerGoodsEntity> queryGbPurchaseGoodsTopPriceFluctuation(Map<String, Object> map) {
        return baseMapper.queryGbPurchaseGoodsTopPriceFluctuation(map);
    }

    @Override
    public List<GbDistributerPurchaseGoodsEntity> queryPurchaseGoodsWithDetailByParams(Map<String, Object> map) {
        return baseMapper.queryPurchaseGoodsWithDetailByParams(map);
    }

    @Override
    public List<GbDistributerPurchaseGoodsEntity> queryOnlyPurGoods(Map<String, Object> map) {
        return baseMapper.queryOnlyPurGoods(map);
    }

}
