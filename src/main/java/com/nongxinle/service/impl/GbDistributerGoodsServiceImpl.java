package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nongxinle.mapper.GbDistributerGoodsMapper;
import com.nongxinle.service.GbDistributerGoodsService;
import org.springframework.stereotype.Service;
import com.nongxinle.entity.GbDistributerGoodsEntity;

import java.util.List;
import java.util.Map;

/**
 * 批发商商品Service实现
 */
@Service
public class GbDistributerGoodsServiceImpl extends ServiceImpl<GbDistributerGoodsMapper, GbDistributerGoodsEntity> implements GbDistributerGoodsService {

    @Override
    public List<GbDistributerGoodsEntity> queryDisGoodsByParams(Map<String, Object> map) {
        return baseMapper.queryDisGoodsByParams(map);
    }

    @Override
    public List<GbDistributerGoodsEntity> queryGbDisGoodsQuickSearchStr(Map<String, Object> map) {
        return baseMapper.queryGbDisGoodsQuickSearchStr(map);
    }
}
