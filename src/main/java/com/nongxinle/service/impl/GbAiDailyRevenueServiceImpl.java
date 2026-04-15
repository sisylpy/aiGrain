package com.nongxinle.service.impl;

import com.nongxinle.entity.GbAiDailyRevenueEntity;
import com.nongxinle.mapper.GbAiDailyRevenueMapper;
import com.nongxinle.service.GbAiDailyRevenueService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 日营业额 Service 实现
 *
 * @author lpy
 * @date 2026-04-11
 */
@Service
public class GbAiDailyRevenueServiceImpl extends ServiceImpl<GbAiDailyRevenueMapper, GbAiDailyRevenueEntity> implements GbAiDailyRevenueService {

	@Override
	public List<GbAiDailyRevenueEntity> queryDailyRevenueListByParams(Map<String, Object> params) {
		return baseMapper.queryDailyRevenueListByParams(params);
	}

	@Override
	public Map<String, Object> getStatsByDepartmentId(Long departmentId) {
		return baseMapper.selectStatsByDepartmentId(departmentId);
	}
}
