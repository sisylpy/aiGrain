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
	 */
	Map<String, Object> getStatsByDepartmentId(Long departmentId);

	/**
	 * 列表接口：查询并组装 chartData + dailyList；无数据时返回 {@code null}。
	 */
	Map<String, Object> buildListPayload(Long departmentId, String startDate, String endDate);

	/** 新增前填充默认日期、星期、节假日、时间戳。 */
	void fillInsertDefaults(GbAiDailyRevenueEntity entity);

	/** 更新前按记录日期重算星期并刷新更新时间。 */
	void fillUpdateWeekday(GbAiDailyRevenueEntity entity);

	/**
	 * 按 部门+记录日 保存：与唯一键 {@code uk_gb_ai_dr_dep_date} 一致，已存在则更新，否则插入。
	 */
	void saveOrUpsertByDepartmentAndDate(GbAiDailyRevenueEntity dailyRevenue);

	/**
	 * 仅写入或更新堂食营业额：已有记录时只覆盖堂食字段与分配者（若传入），其它字段不变。
	 */
	void upsertDineInRevenueOnly(Long departmentId, Long distributerId, Date recordDate, BigDecimal dineInRevenue);

	/**
	 * 日营业额 Excel 批量导入（解析 + 校验 + 按日 upsert）。
	 *
	 * @return 含 total、inserted、updated、errors、errorMessages
	 */
	Map<String, Object> importDailyRevenueFromExcel(MultipartFile file, Long departmentId, Long distributerId)
			throws IOException;

}
