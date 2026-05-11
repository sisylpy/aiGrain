package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nongxinle.entity.GbDistributerPayEntity;
import com.nongxinle.mapper.GbDistributerPayMapper;
import com.nongxinle.service.GbDistributerPayService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 批发商支付Service实现
 */
@Service
public class GbDistributerPayServiceImpl extends ServiceImpl<GbDistributerPayMapper, GbDistributerPayEntity> implements GbDistributerPayService {

    @Override
    public GbDistributerPayEntity queryPayItemByPayId(Integer payId) {
        return getById(payId);
    }

    @Override
    public List<GbDistributerPayEntity> queryDisPayListByParams(Map<String, Object> params) {
        LambdaQueryWrapper<GbDistributerPayEntity> w = new LambdaQueryWrapper<>();
        Object disId = params != null ? params.get("disId") : null;
        if (disId != null) {
            w.eq(GbDistributerPayEntity::getGbGdpGbDisId, disId);
        }
        Object type = params != null ? params.get("type") : null;
        if (type != null) {
            w.eq(GbDistributerPayEntity::getGbGdpType, type);
        }
        Object equalStatus = params != null ? params.get("equalStatus") : null;
        if (equalStatus != null) {
            w.eq(GbDistributerPayEntity::getGbGdpStatus, equalStatus);
        }
        return list(w.orderByDesc(GbDistributerPayEntity::getGbDistributerPayId));
    }

    @Override
    public List<GbDistributerPayEntity> queryListByTradeNo(String tradeNo) {
        return list(new LambdaQueryWrapper<GbDistributerPayEntity>()
                .eq(GbDistributerPayEntity::getGbGdpTradeNo, tradeNo));
    }
}
