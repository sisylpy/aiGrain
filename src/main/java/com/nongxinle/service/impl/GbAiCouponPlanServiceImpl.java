package com.nongxinle.service.impl;

import com.nongxinle.entity.GbAiCouponPlanEntity;
import com.nongxinle.mapper.GbAiCouponPlanMapper;
import com.nongxinle.service.GbAiCouponPlanService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 
 *
 * @author lpy
 * @date 2026-04-11
 */
@Service
public class GbAiCouponPlanServiceImpl extends ServiceImpl<GbAiCouponPlanMapper, GbAiCouponPlanEntity> implements GbAiCouponPlanService {

	@Override
	public List<GbAiCouponPlanEntity> queryCouponPlanListByParams(Map<String, Object> params) {
		return baseMapper.queryCouponPlanListByParams(params);
	}
}
