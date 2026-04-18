package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;


@Data
@EqualsAndHashCode(callSuper = false)
@TableName("gb_dep_food_goods_sales")
public class GbDepFoodGoodsSalesEntity implements Serializable {
	private static final long serialVersionUID = 1L;
	
	/**
	 *  供货商id
	 */
	@TableId(type = IdType.AUTO)
	private Integer gbDepFoodGoodsSalesId;
	/**
	 *  供货商名称
	 */
	private Integer gbDfgsDepId;
	/**
	 *  供货商名称
	 */
	private Integer gbDfgsDepFatherId;
	/**
	 *  gbDisid
	 */
	private Integer gbDfgsFoodSalesId;
	/**
	 *  gbDisid
	 */
	private Integer gbDfgsFoodGoodsId;
	private Integer gbDfgsDisGoodsId;
	/**
	 *  gbDisid
	 */
	private String gbDfgsGoodsAmount;
	/**
	 *  
	 */
	private Integer gbDfgsSettleId;
	/**
	 *  gbDisid
	 */
	private String gbDfgsMonth;
	/**
	 *  gbDisid
	 */
	private String gbDfgsFullDate;

	/**
	 *  星期几
	 */
	private Integer gbDfgsRevenueWeekday;
	/**
	 *  节假日
	 */
	private String gbDfgsRevenueHoliday;

	@TableField(exist = false)
	private GbDistributerFoodGoodsEntity gbDistributerFoodGoodsEntity;
}
