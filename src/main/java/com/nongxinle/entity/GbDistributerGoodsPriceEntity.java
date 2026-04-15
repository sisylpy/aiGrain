package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;

import java.io.Serializable;
import java.util.Date;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;


@Setter@Getter@ToString

@TableName("gb_distributer_goods_price")
public class GbDistributerGoodsPriceEntity implements Serializable {
	private static final long serialVersionUID = 1L;
	
	/**
	 *  价格商品id
	 */
	private Integer gbDistributerGoodsPriceId;
	/**
	 *  dg父类商品id
	 */
	private Integer gbDgpDfgGoodsFatherId;
	/**
	 *  dgGoodsId
	 */
	private Integer gbDgpDistributerGoodsId;
	/**
	 *  dg
	 */
	private Integer gbDgpDistributerId;
	/**
	 *  价格
	 */
	private String gbDgpGoodsPrice;
	/**
	 *  最低价格
	 */
	private String gbDgpGoodsLowestPrice;
	/**
	 *  最高价格
	 */
	private String gbDgpGoodsHighestPrice;
	/**
	 *  采购商品id
	 */
	private Integer gbDgpPurGoodsId;
	private Integer gbDgpPurNxDistributerId;
	/**
	 *  采购员
	 */
	private Integer gbDgpPurUserId;
	/**
	 *  采购日期
	 */
	private String gbDgpPurDate;
	/**
	 *  采购价格
	 */
	private String gbDgpPurPrice;
	/**
	 *  采购价高或低
	 */
	private Integer gbDgpPurWhat;
	/**
	 *  高或低比例
	 */
	private String gbDgpPurScale;
	private String gbDgpPurWeight;
	/**
	 *  高或低差额
	 */
	private String gbDgpPurWhatTotal;
	private Integer gbDgpPurDepartmentId;
	private Integer gbDgpStatus;
	private String gbDgpMonth;
	private String gbDgpWeek;
	private String gbDgpYear;
	private String gbDgpPurTotal;
	private String gbDgpGoodsLowestTotal;
	private String gbDgpGoodsHighestTotal;


	@TableField(exist = false)
	private GbDistributerPurchaseGoodsEntity gbDisPurGoodsEntity;

	@TableField(exist = false)
	private GbDepartmentUserEntity gbDepUserEntity;

}
