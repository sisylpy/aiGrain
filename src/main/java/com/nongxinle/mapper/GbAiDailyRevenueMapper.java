package com.nongxinle.mapper;

import com.nongxinle.dto.AiRecordingDeptRevenueAggRow;
import com.nongxinle.dto.AiStoreNetRevenueAggRow;
import com.nongxinle.entity.GbAiDailyRevenueEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
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

	/**
	 * 指定部门（通常为直营+加盟门店 id）在区间内净营业额总和：堂食+外卖−平台抽成。
	 */
	BigDecimal sumNetRevenueForDepartmentIds(@Param("departmentIds") List<Integer> departmentIds,
			@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

	/**
	 * 多部门区间内营业额聚合（一条 SQL），避免拉全表明细导致集团会话卡死/超时。
	 * 返回 Map：totalDineIn/totalTakeout/totalPlatformFee（BigDecimal）、revenueRowCount、distinctRecordDates（Long）、minRecordDate、maxRecordDate。
	 */
	Map<String, Object> selectRevenueWindowAggregateForDepartmentIds(
			@Param("departmentIds") List<Integer> departmentIds,
			@Param("startDate") LocalDate startDate,
			@Param("endDate") LocalDate endDate);

	/**
	 * 集团整户：按 {@code gb_ai_daily_revenue_distributer_id} 在区间内聚合（不依赖部门列表与子树展开）。
	 */
	Map<String, Object> selectRevenueWindowAggregateForDistributerId(
			@Param("distributerId") int distributerId,
			@Param("startDate") LocalDate startDate,
			@Param("endDate") LocalDate endDate);

	/**
	 * 按分销商 ID 汇总区间净营业额。
	 */
	BigDecimal sumNetRevenueForDistributerId(@Param("distributerId") int distributerId,
			@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

	/**
	 * 按「记账部门」汇总净营业额（一行一部门，通常为子部门）。供向上归并到父级门店。
	 */
	List<AiStoreNetRevenueAggRow> listNetRevenueGroupedByRecordingDepartmentIds(
			@Param("departmentIds") List<Integer> departmentIds,
			@Param("startDate") LocalDate startDate,
			@Param("endDate") LocalDate endDate);

	/**
	 * 集团/多部门：在「记账部门 id ∈ departmentIds」上一次性汇总收入侧指标（行级求和，避免父部门树重复展开）。
	 * 返回 totalGrossRevenue、totalOrders、totalPlatformFee、totalDineIn、totalTakeout、totalTakeoutNetApprox、
	 * distinctRecordDates、distinctRecordingDepartments、maxDailyGross、minDailyGrossPositive。
	 */
	Map<String, Object> selectGroupIncomeAggregateForDepartmentIds(
			@Param("departmentIds") List<Integer> departmentIds,
			@Param("startDate") LocalDate startDate,
			@Param("endDate") LocalDate endDate);

	/**
	 * 区间内、记账部门落入可视集合且有行的部门 id。
	 */
	List<Integer> selectDistinctRecordingDepartmentIdsInRange(
			@Param("departmentIds") List<Integer> departmentIds,
			@Param("startDate") LocalDate startDate,
			@Param("endDate") LocalDate endDate);

	/** 区间内按记账部门汇总：毛利额口径（堂食+外卖）、订单合计、记账日天数。 */
	List<AiRecordingDeptRevenueAggRow> listRecordingDeptRevenueAggInRange(
			@Param("departmentIds") List<Integer> departmentIds,
			@Param("startDate") LocalDate startDate,
			@Param("endDate") LocalDate endDate);

}
