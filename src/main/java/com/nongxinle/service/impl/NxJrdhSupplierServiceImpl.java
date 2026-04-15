package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nongxinle.entity.NxJrdhSupplierEntity;
import com.nongxinle.mapper.NxJrdhSupplierMapper;
import com.nongxinle.service.NxJrdhSupplierService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 今日达供应商Service实现
 */
@Service
public class NxJrdhSupplierServiceImpl extends ServiceImpl<NxJrdhSupplierMapper, NxJrdhSupplierEntity> implements NxJrdhSupplierService {

    @Override
    public NxJrdhSupplierEntity querySellUserSupplier(Map<String, Object> map) {
        return baseMapper.querySellUserSupplier(map);
    }

    @Override
    public List<NxJrdhSupplierEntity> querySupplierByUserId(Integer userId) {
        LambdaQueryWrapper<NxJrdhSupplierEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(NxJrdhSupplierEntity::getNxJrdhsUserId, userId);
        return list(wrapper);
    }

    @Override
    public List<NxJrdhSupplierEntity> queryJrdhSupplerWithDisByUserId(Map<String, Object> map) {
        return baseMapper.queryJrdhSupplerWithDisByUserId(map);
    }

}
