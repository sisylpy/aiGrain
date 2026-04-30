package com.nongxinle.service;

import com.nongxinle.entity.GbDistributerFoodEntity;

import java.util.List;
import java.util.Map;

public interface GbDistributerFoodService {
	
	GbDistributerFoodEntity queryObject(Integer gbDistributerFoodId);

	/**
	 * 按 id 批量查询（如经营分析中解析「直接父」、避免 N+1）。
	 */
	List<GbDistributerFoodEntity> queryByIds(List<Integer> ids);

	List<GbDistributerFoodEntity> queryList(Map<String, Object> map);
	
	int queryTotal(Map<String, Object> map);
	
	void save(GbDistributerFoodEntity gbDistributerFood);
	
	void update(GbDistributerFoodEntity gbDistributerFood);
	
	void delete(Integer gbDistributerFoodId);
	
	void deleteBatch(Integer[] gbDistributerFoodIds);

    List<GbDistributerFoodEntity> queryFoodByParams(Map<String, Object> map);

    List<GbDistributerFoodEntity> queryDisAllFood(Map<String, Object> map);
}