package com.nongxinle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nongxinle.entity.GbDistributerSupplierPaymentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 批发商供应商支付Mapper接口
 */
@Mapper
public interface GbDistributerSupplierPaymentMapper extends BaseMapper<GbDistributerSupplierPaymentEntity> {

    /**
     * 根据参数查询支付列表
     */
    List<GbDistributerSupplierPaymentEntity> queryPaymentListByParams(@Param("map") Map<String, Object> map);

    /**
     * 根据微信交易号查询支付
     */
    GbDistributerSupplierPaymentEntity queryPaymentByWxTradeNo(@Param("ordersSn") String ordersSn);

}
