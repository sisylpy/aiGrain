package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nongxinle.mapper.GbDepartmentDisGoodsMapper;
import com.nongxinle.service.GbDepartmentDisGoodsService;
import org.springframework.stereotype.Service;
import com.nongxinle.entity.GbDepartmentDisGoodsEntity;
import com.nongxinle.entity.GbDistributerFatherGoodsEntity;
import com.nongxinle.entity.GbDistributerGoodsEntity;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 部门商品关联Service实现
 */
@Service
public class GbDepartmentDisGoodsServiceImpl extends ServiceImpl<GbDepartmentDisGoodsMapper, GbDepartmentDisGoodsEntity> implements GbDepartmentDisGoodsService {

    @Override
    public List<GbDistributerFatherGoodsEntity> disGetDepDisGoodsCataGb(Map<String, Object> map) {
        return baseMapper.disGetDepDisGoodsCataGb(map);
    }

    @Override
    public List<Integer> queryOnlyDepGoodsIds(Map<String, Object> map) {
        return baseMapper.queryOnlyDepGoodsIds(map);
    }

    @Override
    public int queryDepGoodsCount(Map<String, Object> mapC) {
        return baseMapper.queryDepGoodsCount(mapC);
    }

    @Override
    public List<GbDepartmentDisGoodsEntity> depQueryDepGoodsWithOrderForAi(Map<String, Object> map) {
        return baseMapper.depQueryDepGoodsWithOrderForAi(map);
    }

    @Override
    public List<GbDepartmentDisGoodsEntity> queryGbDepDisGoodsByParams(Map<String, Object> map) {
        return baseMapper.queryGbDepDisGoodsByParams(map);
    }

    @Override
    public List<Integer> queryOnlyDisGoodsIds(Map<String, Object> map) {
        return baseMapper.queryOnlyDisGoodsIds(map);
    }

    @Override
    public GbDepartmentDisGoodsEntity queryDepartmentGoodsForAi(Map<String, Object> map) {
        return baseMapper.queryDepartmentGoodsForAi(map);
    }

    @Override
    public int queryDisGoodsCount(Map<String, Object> map) {
        return baseMapper.queryDisGoodsCount(map);
    }

    @Override
    public List<GbDepartmentDisGoodsEntity> disQueryDisGoodsWithOrderForAi(Map<String, Object> map) {
        return baseMapper.disQueryDisGoodsWithOrderForAi(map);
    }

    @Override
    public TreeSet<GbDistributerGoodsEntity> disQueryDisGoodsWithOrderForAiTree(Map<String, Object> map) {
        return baseMapper.disQueryDisGoodsWithOrderForAiTree(map);
    }
}
