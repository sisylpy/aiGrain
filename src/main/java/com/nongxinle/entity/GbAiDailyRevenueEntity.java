package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;


@Data
@TableName("gb_ai_daily_revenue")
@EqualsAndHashCode(callSuper = false)
public class GbAiDailyRevenueEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 *  日营收ID
	 */
	@TableId(type = IdType.AUTO)
	private Long gbAiDailyRevenueId;
	/**
	 *  父级部门 ID（日营业额的记账部门，对应 {@code gb_ai_daily_revenue_parent_department_id}）
	 */
	private Long gbAiDailyRevenueParentDepartmentId;
	/**
	 *  分配者ID
	 */
	private Long gbAiDailyRevenueDistributerId;
	/**
	 *  记录日期
	 */
	@JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
	private Date gbAiDailyRevenueRecordDate;
	/**
	 *  堂食营业额
	 */
	private BigDecimal gbAiDailyRevenueDineInRevenue;
	/**
	 *  堂食订单数
	 */
	private Integer gbAiDailyRevenueDineInOrders;
	/**
	 *  堂食顾客数
	 */
	private Integer gbAiDailyRevenueDineInCustomers;
	/**
	 *  外卖营业额
	 */
	private BigDecimal gbAiDailyRevenueTakeoutRevenue;
	/**
	 *  外卖订单数
	 */
	private Integer gbAiDailyRevenueTakeoutOrders;
	/**
	 *  平台抽成
	 */
	private BigDecimal gbAiDailyRevenuePlatformFee;
	/**
	 *  星期几
	 */
	private Integer gbAiDailyRevenueWeekday;
	/**
	 *  节假日
	 */
	private String gbAiDailyRevenueHoliday;
	/**
	 *  总营业额（数据库生成列 GENERATED ALWAYS AS (dine_in + takeout)，不可手动插入/更新）
	 */
	@TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
	private BigDecimal gbAiDailyRevenueGrossRevenue;
	/**
	 *  净营业额（数据库生成列，无需手动插入）
	 */
	@TableField(exist = false)
	private BigDecimal gbAiDailyRevenueNetRevenue;
	/**
	 *  备注
	 */
	private String gbAiDailyRevenueNotes;
	/**
	 *  创建时间
	 */
	private Date gbAiDailyRevenueCreateTime;
	/**
	 *  更新时间
	 */
	private Date gbAiDailyRevenueUpdateTime;
}
