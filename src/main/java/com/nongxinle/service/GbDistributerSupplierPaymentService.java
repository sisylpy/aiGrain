package com.nongxinle.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nongxinle.entity.GbDistributerSupplierPaymentEntity;

import java.util.List;
import java.util.Map;

/**
 * 批发商供应商支付Service接口
 */
public interface GbDistributerSupplierPaymentService extends IService<GbDistributerSupplierPaymentEntity> {

    /**
     * 根据参数查询支付列表
     */
    List<GbDistributerSupplierPaymentEntity> queryPaymentListByParams(Map<String, Object> map);

    /**
     * 根据微信交易号查询支付
     */
    GbDistributerSupplierPaymentEntity queryPaymentByWxTradeNo(String ordersSn);

}
