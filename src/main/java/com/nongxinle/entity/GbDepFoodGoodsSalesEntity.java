package com.nongxinle.entity;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;


@Setter@Getter@ToString

@TableName("gb_dep_food_goods_sales")
public class GbDepFoodGoodsSalesEntity implements Serializable {
	private static final long serialVersionUID = 1L;
	
	/**
	 *  供货商id
	 */
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

   private GbDistributerFoodGoodsEntity gbDistributerFoodGoodsEntity;
}
