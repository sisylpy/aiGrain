package com.nongxinle.service;

import com.nongxinle.entity.GbAiDailyRevenueEntity;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Date;
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
	 *
	 * @param departmentFatherId 父部门/餐厅 ID（与核销 gb_dgsr_gb_department_father_id 一致）
	 * @param startDate          可选，yyyy-MM-dd
	 * @param endDate            可选，yyyy-MM-dd
	 */
	Map<String, Object> getStatsByDepartmentId(Long departmentFatherId, String startDate, String endDate);

	/**
	 * 集团/多部门：按「日营收记账部门 id」列表在区间内一次性汇总收入侧指标（见 {@code selectGroupIncomeAggregateForDepartmentIds}）。
	 * {@code departmentIds} 为空时返回空 Map。
	 */
	Map<String, Object> getGroupIncomeAggregateForDepartmentIds(List<Integer> departmentIds, String startDate, String endDate);

	/**
	 * 列表接口：主账 + 菜品五类销售；无数据时返回 {@code null}。
	 */
	Map<String, Object> buildListPayload(Long departmentId, String startDate, String endDate);

	/**
	 * 列表接口（可选子部门、分配者范围）；summary 由 dailyRows 同口径累加。
	 */
	Map<String, Object> buildListPayload(Long departmentId, String startDate, String endDate,
			Long subDepId, Long distributerId);

	/** 新增前填充默认日期、星期、节假日、时间戳。 */
	void fillInsertDefaults(GbAiDailyRevenueEntity entity);

	/**
	 * 若父部门 ID 为空，则按 {@code gb_department.gb_department_father_id} 填充。
	 * 与历史回填脚本一致：父级为 {@code 0}、{@code -1} 或空时写入 {@code null}。
	 */
	void backfillParentDepartmentIdIfMissing(GbAiDailyRevenueEntity entity);

	/** 更新前按记录日期重算星期并刷新更新时间。 */
	void fillUpdateWeekday(GbAiDailyRevenueEntity entity);

	/**
	 * 按 部门+记录日 保存：与唯一键 {@code uk_gb_ai_dr_dep_date} 一致，已存在则更新，否则插入。
	 */
	void saveOrUpsertByDepartmentAndDate(GbAiDailyRevenueEntity dailyRevenue);

	/**
	 * 按 父部门+记录日 保存（子部门 ID 为 null）：与 {@code uk_gb_ai_dr_dep_date} 一致，已存在则更新，否则插入。
	 * 适用于仅提供父部门 ID、无需指定子部门的场景。
	 */
	void saveOrUpsertByParentDepartmentAndDate(GbAiDailyRevenueEntity dailyRevenue);

	/**
	 * 按 部门+记录日 更新（纯更新，不新增）。找不到当天记录则抛出 IllegalArgumentException。
	 */
	void updateByDepartmentAndDate(GbAiDailyRevenueEntity dailyRevenue);

	/**
	 * 按 父部门+记录日 更新（纯更新，不新增）。找不到当天记录则抛出 IllegalArgumentException。
	 */
	void updateByParentDepartmentAndDate(GbAiDailyRevenueEntity dailyRevenue);

	/**
	 * 仅写入或更新堂食营业额：已有记录时只覆盖堂食字段与分配者（若传入），其它字段不变。
	 */
	void upsertDineInRevenueOnly(Long departmentId, Long distributerId, Date recordDate, BigDecimal dineInRevenue);

	/**
	 * 合并非堂食相关指标：堂食订单/顾客数、外卖营业额/订单数、平台抽成、备注。
	 * 不修改堂食营业额；{@code null} 的字段表示不覆盖库中已有值。
	 */
	void mergeNonDineInDailyRevenueMetrics(Long departmentId, Long distributerId, Date recordDate,
			Integer dineInOrders, Integer dineInCustomers, BigDecimal takeoutRevenue,
			Integer takeoutOrders, BigDecimal platformFee, String notes);

	/**
	 * 日营业额 Excel 批量导入（解析 + 校验 + 按日 upsert）。
	 *
	 * @return 含 total、inserted、updated、errors、errorMessages
	 */
	Map<String, Object> importDailyRevenueFromExcel(MultipartFile file, Long departmentId, Long distributerId)
			throws IOException;

	/**
	 * 合并 Excel：先导入「菜品日销售」Sheet 并汇总堂食，再合并「日营业额」Sheet（无堂食/当日营业额列）的外卖等指标。
	 *
	 * @return 含 foodSales、dailyRevenueSupplement 等
	 */
	Map<String, Object> importCombinedDailyRevenueAndFoodFromExcel(MultipartFile file, Long departmentId, Long distributerId)
			throws IOException;

	/**
	 * 父部门 + 其直接子部门 id列表（日营业额查询/导入范围与 Excel 部门列一致）。
	 */
	List<Long> departmentScopeIdsForParent(Long parentId);

	/**
	 * 集团默认「门店级」：每个门店根（{@code gb_department_father_id=0}）对应日营收 SQL 用的部门 id 列表，
	 * 即该门店自身 + 其<strong>直属</strong>子部门，与 {@link #getStatsByDepartmentId} 一致。
	 * <p><strong>仅用于查库</strong>，不把子部门当作集团下的独立门店；展示与 covered/missing 仍以门店根为准。
	 *
	 * @return 有序 Map：key = 门店根部门 id，value = 该门店日营收查账 id 列表
	 */
	Map<Long, List<Integer>> buildStoreRevenueQueryScopeByStoreRoot(List<Integer> storeRootDepartmentIds);

	/**
	 * 将多个门店根的 {@link #buildStoreRevenueQueryScopeByStoreRoot} 值集合并去重保序，供
	 * {@link #getGroupIncomeAggregateForDepartmentIds} 一次汇总集团收入侧指标（金额仍为多店合计，不按子部门拆分展示）。
	 */
	List<Integer> expandStoreRootsToDailyRevenueScopeIds(List<Integer> storeRootDepartmentIds);

}
