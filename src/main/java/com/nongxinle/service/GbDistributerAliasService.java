package com.nongxinle.service;

import com.nongxinle.entity.GbDistributerAliasEntity;

import java.util.List;
import java.util.Map;

/**
 * 批发商商品别名Service接口
 */
public interface GbDistributerAliasService {
	
	GbDistributerAliasEntity queryObject(Integer gbDistributerAliasId);
	
	List<GbDistributerAliasEntity> queryList(Map<String, Object> map);
	
	int queryTotal(Map<String, Object> map);
	
	void save(GbDistributerAliasEntity gbDistributerAlias);
	
	void update(GbDistributerAliasEntity gbDistributerAlias);
	
	void delete(Integer gbDistributerAliasId);
	
	void deleteBatch(Integer[] gbDistributerAliasIds);
}
