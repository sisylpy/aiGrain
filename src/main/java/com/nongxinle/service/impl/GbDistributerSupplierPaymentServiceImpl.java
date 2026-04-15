package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nongxinle.entity.GbDistributerSupplierPaymentEntity;
import com.nongxinle.mapper.GbDistributerSupplierPaymentMapper;
import com.nongxinle.service.GbDistributerSupplierPaymentService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 批发商供应商支付Service实现
 */
@Service
public class GbDistributerSupplierPaymentServiceImpl extends ServiceImpl<GbDistributerSupplierPaymentMapper, GbDistributerSupplierPaymentEntity> implements GbDistributerSupplierPaymentService {

    @Override
    public List<GbDistributerSupplierPaymentEntity> queryPaymentListByParams(Map<String, Object> map) {
        return baseMapper.queryPaymentListByParams(map);
    }

    @Override
    public GbDistributerSupplierPaymentEntity queryPaymentByWxTradeNo(String ordersSn) {
        return baseMapper.queryPaymentByWxTradeNo(ordersSn);
    }

}
