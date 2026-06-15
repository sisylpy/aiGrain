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

	/** 更新前按记录日期重算星期并刷新更新时间。 */
	void fillUpdateWeekday(GbAiDailyRevenueEntity entity);

	/**
	 * 按 父部门+记录日 保存：已存在则更新，否则插入。
	 */
	void saveOrUpsertByDepartmentAndDate(GbAiDailyRevenueEntity dailyRevenue);

	/**
	 * 按 父部门+记录日 更新（纯更新，不新增）。找不到当天记录则抛出 IllegalArgumentException。
	 */
	void updateByDepartmentAndDate(GbAiDailyRevenueEntity dailyRevenue);

	/**
	 * 仅写入或更新堂食营业额：已有记录时只覆盖堂食字段与分配者（若传入），其它字段不变。
	 */
	void upsertDineInRevenueOnly(Long parentDepartmentId, Long distributerId, Date recordDate, BigDecimal dineInRevenue);

	/**
	 * 合并非堂食相关指标：堂食订单/顾客数、外卖营业额/订单数、平台抽成、备注。
	 * 不修改堂食营业额；{@code null} 的字段表示不覆盖库中已有值。
	 */
	void mergeNonDineInDailyRevenueMetrics(Long parentDepartmentId, Long distributerId, Date recordDate,
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
	 * 日营业额查询/导入用的部门 ID 列表（当前仅返回 {@code parentId} 自身）。
	 */
	List<Long> departmentScopeIdsForParent(Long parentId);

	/**
	 * 集团默认「门店级」：每个门店根（{@code gb_department_father_id=0}）对应日营收 SQL 用的部门 id 列表。
	 * <p><strong>仅用于查库</strong>。
	 *
	 * @return 有序 Map：key = 门店根部门 id，value = 该门店日营收查账 id 列表
	 */
	Map<Long, List<Integer>> buildStoreRevenueQueryScopeByStoreRoot(List<Integer> storeRootDepartmentIds);

	/**
	 * 将多个门店根的 {@link #buildStoreRevenueQueryScopeByStoreRoot} 值集合并去重保序，供
	 * {@link #getGroupIncomeAggregateForDepartmentIds} 一次汇总集团收入侧指标。
	 */
	List<Integer> expandStoreRootsToDailyRevenueScopeIds(List<Integer> storeRootDepartmentIds);

}
