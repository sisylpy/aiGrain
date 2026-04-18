package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nongxinle.entity.GbDepartmentEntity;
import com.nongxinle.entity.GbDepartmentGoodsDailyEntity;
import com.nongxinle.entity.GbDistributerFatherGoodsEntity;
import com.nongxinle.entity.GbDistributerGoodsEntity;
import com.nongxinle.mapper.GbDepartmentGoodsDailyMapper;
import com.nongxinle.service.GbDepartmentGoodsDailyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 部门商品日报Service实现
 */
@Service
public class GbDepartmentGoodsDailyServiceImpl extends ServiceImpl<GbDepartmentGoodsDailyMapper, GbDepartmentGoodsDailyEntity> implements GbDepartmentGoodsDailyService {

    @Autowired
    private GbDepartmentGoodsDailyMapper gbDepartmentGoodsDailyMapper;

    @Override
    public GbDepartmentGoodsDailyEntity queryDepGoodsDailyItem(Map<String, Object> map) {
        return gbDepartmentGoodsDailyMapper.queryDepGoodsDailyItem(map);
    }

    @Override
    public Integer queryDepGoodsDailyCount(Map<String, Object> map) {
        return gbDepartmentGoodsDailyMapper.queryDepGoodsDailyCount(map);
    }


    @Override
    public Double queryDepGoodsDailyLossSubtotal(Map<String, Object> map) {
        return gbDepartmentGoodsDailyMapper.queryDepGoodsDailyLossSubtotal(map);
    }

    @Override
    public Double queryDepGoodsDailyWasteSubtotal(Map<String, Object> map) {
        return gbDepartmentGoodsDailyMapper.queryDepGoodsDailyWasteSubtotal(map);
    }

    @Override
    public Double queryDepGoodsDailyProduceSubtotal(Map<String, Object> map) {
        return gbDepartmentGoodsDailyMapper.queryDepGoodsDailyProduceSubtotal(map);
    }


}
