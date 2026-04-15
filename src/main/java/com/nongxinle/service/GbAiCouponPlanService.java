package com.nongxinle.service;

import com.nongxinle.entity.GbAiCouponPlanEntity;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

/**
 * 
 *
 * @author lpy
 * @date 2026-04-11
 */
public interface GbAiCouponPlanService extends IService<GbAiCouponPlanEntity> {

	List<GbAiCouponPlanEntity> queryCouponPlanListByParams(Map<String, Object> params);
	
}
