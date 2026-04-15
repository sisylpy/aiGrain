package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nongxinle.entity.GbDistributerStandardEntity;
import com.nongxinle.mapper.GbDistributerStandardMapper;
import com.nongxinle.service.GbDistributerStandardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 批发商商品规格Service实现
 */
@Service("gbDistributerStandardService")
public class GbDistributerStandardServiceImpl extends ServiceImpl<GbDistributerStandardMapper, GbDistributerStandardEntity> implements GbDistributerStandardService {

    @Autowired
    private GbDistributerStandardMapper gbDistributerStandardMapper;

    @Override
    public GbDistributerStandardEntity queryObject(Integer gbDistributerStandardId) {
        return getById(gbDistributerStandardId);
    }

    @Override
    public List<GbDistributerStandardEntity> queryList(Map<String, Object> map) {
        return gbDistributerStandardMapper.queryList(map);
    }

    @Override
    public int queryTotal(Map<String, Object> map) {
        return gbDistributerStandardMapper.queryTotal(map);
    }

    @Override
    public List<GbDistributerStandardEntity> queryDisStandardByDisGoodsIdGb(Integer disGoodsId) {
        return gbDistributerStandardMapper.queryDisStandardByDisGoodsIdGb(disGoodsId);
    }

    @Override
    public List<GbDistributerStandardEntity> queryDisStandardByParams(Map<String, Object> mapNx) {
        return gbDistributerStandardMapper.queryDisStandardByParams(mapNx);
    }
}
