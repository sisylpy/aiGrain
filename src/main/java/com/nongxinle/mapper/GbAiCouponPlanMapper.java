package com.nongxinle.mapper;

import com.nongxinle.entity.GbAiCouponPlanEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

/**
 * 
 *
 * @author lpy
 * @date 2026-04-11
 */
@Mapper
public interface GbAiCouponPlanMapper extends BaseMapper<GbAiCouponPlanEntity> {
	
	List<GbAiCouponPlanEntity> queryCouponPlanListByParams(Map<String, Object> params);
	
}
