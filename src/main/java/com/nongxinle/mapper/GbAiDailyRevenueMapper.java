package com.nongxinle.mapper;

import com.nongxinle.entity.GbAiDailyRevenueEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 日营业额 Mapper
 *
 * @author lpy
 * @date 2026-04-11
 */
@Mapper
public interface GbAiDailyRevenueMapper extends BaseMapper<GbAiDailyRevenueEntity> {

	/**
	 * 查询日营业额列表
	 */
	List<GbAiDailyRevenueEntity> queryDailyRevenueListByParams(Map<String, Object> params);

	/**
	 * 查询营业额统计（按部门 + 可选日期范围；未传日期则全量）
	 */
	Map<String, Object> selectStatsByDepartmentId(@Param("params") Map<String, Object> params);

}
