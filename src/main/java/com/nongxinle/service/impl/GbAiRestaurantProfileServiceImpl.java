package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nongxinle.entity.GbAiRestaurantProfileEntity;
import com.nongxinle.mapper.GbAiRestaurantProfileMapper;
import com.nongxinle.service.GbAiRestaurantProfileService;
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
public class GbAiRestaurantProfileServiceImpl extends ServiceImpl<GbAiRestaurantProfileMapper, GbAiRestaurantProfileEntity> implements GbAiRestaurantProfileService {

	@Override
	public List<GbAiRestaurantProfileEntity> queryRestaurantProfileListByParams(Map<String, Object> params) {
		return baseMapper.queryRestaurantProfileListByParams(params);
	}

	@Override
	public GbAiRestaurantProfileEntity getByDepartmentId(Long departmentId) {
		return baseMapper.selectByDepartmentId(departmentId);
	}

	@Override
	public void saveOrUpdateProfile(GbAiRestaurantProfileEntity profile) {
		// 验证必要字段
		if (profile.getGbAiRestaurantProfileDepartmentId() == null) {
			throw new IllegalArgumentException("部门ID不能为空");
		}
		
		// 先查询是否存在
		GbAiRestaurantProfileEntity existing = baseMapper.selectByDepartmentId(profile.getGbAiRestaurantProfileDepartmentId());
		if (existing != null) {
			// 存在则更新ID
			profile.setGbAiRestaurantProfileId(existing.getGbAiRestaurantProfileId());
		}
		// 创建或更新
		saveOrUpdate(profile);
	}
}
