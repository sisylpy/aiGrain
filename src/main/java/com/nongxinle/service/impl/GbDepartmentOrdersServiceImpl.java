package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nongxinle.entity.GbDepartmentOrdersEntity;
import com.nongxinle.entity.GbDistributerFatherGoodsEntity;
import com.nongxinle.mapper.GbDepartmentOrdersMapper;
import com.nongxinle.service.GbDepartmentOrdersService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 部门订单Service实现
 */
@Service
public class GbDepartmentOrdersServiceImpl extends ServiceImpl<GbDepartmentOrdersMapper, GbDepartmentOrdersEntity> implements GbDepartmentOrdersService {

    @Autowired
    private GbDepartmentOrdersMapper gbDepartmentOrdersMapper;

    @Override
    public Integer queryGbDepartmentOrderAmount(Map<String, Object> map) {
        return gbDepartmentOrdersMapper.queryGbDepartmentOrderAmount(map);
    }

    @Override
    public List<GbDepartmentOrdersEntity> queryApplyAiFatherOrders(Integer depId) {
        return gbDepartmentOrdersMapper.queryApplyAiFatherOrders(depId);
    }

    @Override
    public List<GbDistributerFatherGoodsEntity> queryGrandGoodsOrder(Map<String, Object> map) {
        return gbDepartmentOrdersMapper.queryGrandGoodsOrder(map);
    }

    @Override
    public List<GbDepartmentOrdersEntity> queryDisOrdersListByParams(Map<String, Object> map) {
        return gbDepartmentOrdersMapper.queryDisOrdersListByParams(map);
    }

    @Override
    public void update(GbDepartmentOrdersEntity gbDepartmentOrders) {
        // 使用 LambdaUpdateWrapper 根据 ID 更新非空字段
        LambdaUpdateWrapper<GbDepartmentOrdersEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(GbDepartmentOrdersEntity::getGbDepartmentOrdersId, gbDepartmentOrders.getGbDepartmentOrdersId());
        
        // 更新所有非空字段
        if (gbDepartmentOrders.getGbDoNxGoodsId() != null) {
            wrapper.set(GbDepartmentOrdersEntity::getGbDoNxGoodsId, gbDepartmentOrders.getGbDoNxGoodsId());
        }
        if (gbDepartmentOrders.getGbDoNxGoodsFatherId() != null) {
            wrapper.set(GbDepartmentOrdersEntity::getGbDoNxGoodsFatherId, gbDepartmentOrders.getGbDoNxGoodsFatherId());
        }
        if (gbDepartmentOrders.getGbDoDisGoodsId() != null) {
            wrapper.set(GbDepartmentOrdersEntity::getGbDoDisGoodsId, gbDepartmentOrders.getGbDoDisGoodsId());
        }
        if (gbDepartmentOrders.getGbDoDisGoodsFatherId() != null) {
            wrapper.set(GbDepartmentOrdersEntity::getGbDoDisGoodsFatherId, gbDepartmentOrders.getGbDoDisGoodsFatherId());
        }
        if (gbDepartmentOrders.getGbDoDisGoodsGrandId() != null) {
            wrapper.set(GbDepartmentOrdersEntity::getGbDoDisGoodsGrandId, gbDepartmentOrders.getGbDoDisGoodsGrandId());
        }
        if (gbDepartmentOrders.getGbDoDisGoodsGreatId() != null) {
            wrapper.set(GbDepartmentOrdersEntity::getGbDoDisGoodsGreatId, gbDepartmentOrders.getGbDoDisGoodsGreatId());
        }
        if (gbDepartmentOrders.getGbDoDepDisGoodsId() != null) {
            wrapper.set(GbDepartmentOrdersEntity::getGbDoDepDisGoodsId, gbDepartmentOrders.getGbDoDepDisGoodsId());
        }
        if (gbDepartmentOrders.getGbDoQuantity() != null) {
            wrapper.set(GbDepartmentOrdersEntity::getGbDoQuantity, gbDepartmentOrders.getGbDoQuantity());
        }
        if (gbDepartmentOrders.getGbDoStandard() != null) {
            wrapper.set(GbDepartmentOrdersEntity::getGbDoStandard, gbDepartmentOrders.getGbDoStandard());
        }
        if (gbDepartmentOrders.getGbDoRemark() != null) {
            wrapper.set(GbDepartmentOrdersEntity::getGbDoRemark, gbDepartmentOrders.getGbDoRemark());
        }
        if (gbDepartmentOrders.getGbDoWeight() != null) {
            wrapper.set(GbDepartmentOrdersEntity::getGbDoWeight, gbDepartmentOrders.getGbDoWeight());
        }
        if (gbDepartmentOrders.getGbDoPrice() != null) {
            wrapper.set(GbDepartmentOrdersEntity::getGbDoPrice, gbDepartmentOrders.getGbDoPrice());
        }
        if (gbDepartmentOrders.getGbDoSubtotal() != null) {
            wrapper.set(GbDepartmentOrdersEntity::getGbDoSubtotal, gbDepartmentOrders.getGbDoSubtotal());
        }
        if (gbDepartmentOrders.getGbDoDepartmentId() != null) {
            wrapper.set(GbDepartmentOrdersEntity::getGbDoDepartmentId, gbDepartmentOrders.getGbDoDepartmentId());
        }
        if (gbDepartmentOrders.getGbDoDepartmentFatherId() != null) {
            wrapper.set(GbDepartmentOrdersEntity::getGbDoDepartmentFatherId, gbDepartmentOrders.getGbDoDepartmentFatherId());
        }
        if (gbDepartmentOrders.getGbDoDistributerId() != null) {
            wrapper.set(GbDepartmentOrdersEntity::getGbDoDistributerId, gbDepartmentOrders.getGbDoDistributerId());
        }
        if (gbDepartmentOrders.getGbDoBillId() != null) {
            wrapper.set(GbDepartmentOrdersEntity::getGbDoBillId, gbDepartmentOrders.getGbDoBillId());
        }
        if (gbDepartmentOrders.getGbDoStatus() != null) {
            wrapper.set(GbDepartmentOrdersEntity::getGbDoStatus, gbDepartmentOrders.getGbDoStatus());
        }
        if (gbDepartmentOrders.getGbDoOrderUserId() != null) {
            wrapper.set(GbDepartmentOrdersEntity::getGbDoOrderUserId, gbDepartmentOrders.getGbDoOrderUserId());
        }
        if (gbDepartmentOrders.getGbDoPickUserId() != null) {
            wrapper.set(GbDepartmentOrdersEntity::getGbDoPickUserId, gbDepartmentOrders.getGbDoPickUserId());
        }
        if (gbDepartmentOrders.getGbDoReceiveUserId() != null) {
            wrapper.set(GbDepartmentOrdersEntity::getGbDoReceiveUserId, gbDepartmentOrders.getGbDoReceiveUserId());
        }
        if (gbDepartmentOrders.getGbDoBuyStatus() != null) {
            wrapper.set(GbDepartmentOrdersEntity::getGbDoBuyStatus, gbDepartmentOrders.getGbDoBuyStatus());
        }
        if (gbDepartmentOrders.getGbDoApplyDate() != null) {
            wrapper.set(GbDepartmentOrdersEntity::getGbDoApplyDate, gbDepartmentOrders.getGbDoApplyDate());
        }
        if (gbDepartmentOrders.getGbDoApplyWhatDay() != null) {
            wrapper.set(GbDepartmentOrdersEntity::getGbDoApplyWhatDay, gbDepartmentOrders.getGbDoApplyWhatDay());
        }
        if (gbDepartmentOrders.getGbDoApplyArriveDate() != null) {
            wrapper.set(GbDepartmentOrdersEntity::getGbDoApplyArriveDate, gbDepartmentOrders.getGbDoApplyArriveDate());
        }
        if (gbDepartmentOrders.getGbDoArriveOnlyDate() != null) {
            wrapper.set(GbDepartmentOrdersEntity::getGbDoArriveOnlyDate, gbDepartmentOrders.getGbDoArriveOnlyDate());
        }
        if (gbDepartmentOrders.getGbDoApplyFullTime() != null) {
            wrapper.set(GbDepartmentOrdersEntity::getGbDoApplyFullTime, gbDepartmentOrders.getGbDoApplyFullTime());
        }
        if (gbDepartmentOrders.getGbDoGoodsType() != null) {
            wrapper.set(GbDepartmentOrdersEntity::getGbDoGoodsType, gbDepartmentOrders.getGbDoGoodsType());
        }
        if (gbDepartmentOrders.getGbDoPurchaseGoodsId() != null) {
            wrapper.set(GbDepartmentOrdersEntity::getGbDoPurchaseGoodsId, gbDepartmentOrders.getGbDoPurchaseGoodsId());
        }
        if (gbDepartmentOrders.getGbDoArriveWeeksYear() != null) {
            wrapper.set(GbDepartmentOrdersEntity::getGbDoArriveWeeksYear, gbDepartmentOrders.getGbDoArriveWeeksYear());
        }
        if (gbDepartmentOrders.getGbDoArriveWhatDay() != null) {
            wrapper.set(GbDepartmentOrdersEntity::getGbDoArriveWhatDay, gbDepartmentOrders.getGbDoArriveWhatDay());
        }
        if (gbDepartmentOrders.getGbDoIsAgent() != null) {
            wrapper.set(GbDepartmentOrdersEntity::getGbDoIsAgent, gbDepartmentOrders.getGbDoIsAgent());
        }
        if (gbDepartmentOrders.getGbDoNxGoodsGrandId() != null) {
            wrapper.set(GbDepartmentOrdersEntity::getGbDoNxGoodsGrandId, gbDepartmentOrders.getGbDoNxGoodsGrandId());
        }
        if (gbDepartmentOrders.getGbDoNxGoodsGreatId() != null) {
            wrapper.set(GbDepartmentOrdersEntity::getGbDoNxGoodsGreatId, gbDepartmentOrders.getGbDoNxGoodsGreatId());
        }
        if (gbDepartmentOrders.getGbDoApplyOnlyTime() != null) {
            wrapper.set(GbDepartmentOrdersEntity::getGbDoApplyOnlyTime, gbDepartmentOrders.getGbDoApplyOnlyTime());
        }
        if (gbDepartmentOrders.getGbDoNxDistributerId() != null) {
            wrapper.set(GbDepartmentOrdersEntity::getGbDoNxDistributerId, gbDepartmentOrders.getGbDoNxDistributerId());
        }
        if (gbDepartmentOrders.getGbDoNxDistributerGoodsId() != null) {
            wrapper.set(GbDepartmentOrdersEntity::getGbDoNxDistributerGoodsId, gbDepartmentOrders.getGbDoNxDistributerGoodsId());
        }
        if (gbDepartmentOrders.getGbDoToDepartmentId() != null) {
            wrapper.set(GbDepartmentOrdersEntity::getGbDoToDepartmentId, gbDepartmentOrders.getGbDoToDepartmentId());
        }
        if (gbDepartmentOrders.getGbDoOrderType() != null) {
            wrapper.set(GbDepartmentOrdersEntity::getGbDoOrderType, gbDepartmentOrders.getGbDoOrderType());
        }
        if (gbDepartmentOrders.getGbDoDsStandardScale() != null) {
            wrapper.set(GbDepartmentOrdersEntity::getGbDoDsStandardScale, gbDepartmentOrders.getGbDoDsStandardScale());
        }
        if (gbDepartmentOrders.getGbDoGoodsName() != null) {
            wrapper.set(GbDepartmentOrdersEntity::getGbDoGoodsName, gbDepartmentOrders.getGbDoGoodsName());
        }
        
        update(wrapper);
    }

    @Override
    public List<GbDepartmentOrdersEntity> queryDisOrdersByParams(Map<String, Object> map) {
        return gbDepartmentOrdersMapper.queryDisOrdersByParams(map);
    }

    @Override
    public Double queryOrderWeightTotalByPurGoodsId(Integer purGoodsId) {
        return gbDepartmentOrdersMapper.queryOrderWeightTotalByPurGoodsId(purGoodsId);
    }

    @Override
    public Double queryGbOrdersSubtotal(Map<String, Object> map) {
        return gbDepartmentOrdersMapper.queryGbOrdersSubtotal(map);
    }

    @Override
    public GbDepartmentOrdersEntity queryReturnOrderByReduceId(Integer reduceId) {
        if (reduceId == null) {
            return null;
        }
        return getOne(new LambdaQueryWrapper<GbDepartmentOrdersEntity>()
                .eq(GbDepartmentOrdersEntity::getGbDoDgsrReturnId, reduceId)
                .last("LIMIT 1"));
    }

}
