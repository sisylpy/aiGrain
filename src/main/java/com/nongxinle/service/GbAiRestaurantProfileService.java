package com.nongxinle.service;

import com.nongxinle.entity.GbAiRestaurantProfileEntity;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

/**
 * 
 *
 * @author lpy
 * @date 2026-04-11
 */
public interface GbAiRestaurantProfileService extends IService<GbAiRestaurantProfileEntity> {

	List<GbAiRestaurantProfileEntity> queryRestaurantProfileListByParams(Map<String, Object> params);

	GbAiRestaurantProfileEntity getByDepartmentId(Long departmentId);

	void saveOrUpdateProfile(GbAiRestaurantProfileEntity profile);
	
}
