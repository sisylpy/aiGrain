package com.nongxinle.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nongxinle.entity.GbDistributerPayEntity;

import java.util.List;
import java.util.Map;

/**
 * 批发商支付Service接口
 */
public interface GbDistributerPayService extends IService<GbDistributerPayEntity> {

    GbDistributerPayEntity queryPayItemByPayId(Integer payId);

    List<GbDistributerPayEntity> queryDisPayListByParams(Map<String, Object> params);

    List<GbDistributerPayEntity> queryListByTradeNo(String tradeNo);
}
