package com.nongxinle.mapper;

import com.nongxinle.entity.GbAiRestaurantProfileEntity;
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
public interface GbAiRestaurantProfileMapper extends BaseMapper<GbAiRestaurantProfileEntity> {
	
	List<GbAiRestaurantProfileEntity> queryRestaurantProfileListByParams(Map<String, Object> params);

	GbAiRestaurantProfileEntity selectByDepartmentId(Long departmentId);
	
}
