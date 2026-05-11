package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nongxinle.entity.GbDistributerPayListEntity;
import com.nongxinle.mapper.GbDistributerPayListMapper;
import com.nongxinle.service.GbDistributerPayListService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 批发商结算记录Service实现
 */
@Service
public class GbDistributerPayListServiceImpl extends ServiceImpl<GbDistributerPayListMapper, GbDistributerPayListEntity> implements GbDistributerPayListService {

    @Override
    public int queryDisPayListCount(Map<String, Object> params) {
        return baseMapper.queryDisPayListCount(params);
    }

    @Override
    public int queryDisRecordSecondsTotal(Map<String, Object> params) {
        return baseMapper.queryDisRecordSecondsTotal(params);
    }

    @Override
    public List<GbDistributerPayListEntity> queryPayListListByParams(Map<String, Object> params) {
        return baseMapper.queryPayListListByParams(params);
    }
}
