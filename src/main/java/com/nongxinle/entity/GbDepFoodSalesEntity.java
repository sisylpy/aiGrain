package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;


@Data
@EqualsAndHashCode(callSuper = false)
@TableName("gb_dep_food_sales")
public class GbDepFoodSalesEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	@TableId(type = IdType.AUTO)
	private Integer gbDepFoodSalesId;
	private Integer gbDfsDepId;
	private Integer gbDfsFoodId;
	private Integer gbDfsDepFatherId;
	private String gbDfsAmount;
	private Integer gbDfsSettleId;
	private String gbDfsMonth;
	private String gbDfsFullDate;
	private Integer gbDfsUserId;
	private String gbDfsYear;
	private String gbDfsSubtotal;
	private Integer gbDfsDistributerId;
	/** 消费类型：1正常 2折扣 3会员 4赠送 5员工餐 */
	private Integer gbDfsType;
	/** 标价快照（元/份） */
	private BigDecimal gbDfsOriginalUnitPrice;
	/** 实际成交价（元/份） */
	private BigDecimal gbDfsActualUnitPrice;
	/** 折扣率（展示用） */
	private BigDecimal gbDfsDiscountRate;

	/**
	 *  星期几
	 */
	private Integer gbDfsRevenueWeekday;
	/**
	 *  节假日
	 */
	private String gbDfsRevenueHoliday;
}
