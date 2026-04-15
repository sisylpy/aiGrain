package com.nongxinle.service;

import com.nongxinle.entity.GbAiDailyRevenueEntity;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

/**
 * 日营业额 Service
 *
 * @author lpy
 * @date 2026-04-11
 */
public interface GbAiDailyRevenueService extends IService<GbAiDailyRevenueEntity> {

	/**
	 * 查询日营业额列表
	 */
	List<GbAiDailyRevenueEntity> queryDailyRevenueListByParams(Map<String, Object> params);

	/**
	 * 获取营业额统计数据
	 */
	Map<String, Object> getStatsByDepartmentId(Long departmentId);

}
