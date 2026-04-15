package com.nongxinle.entity;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;


@Setter@Getter@ToString

@TableName("gb_department_goods_stock_reduce_daily")
public class GbDepartmentGoodsStockReduceDailyEntity implements Serializable {
	private static final long serialVersionUID = 1L;
	
	/**
	 *  
	 */
	private Integer gbDepartmentGoodsStockReduceDailyId;
	/**
	 *  
	 */
	private Integer gbDgsrdGbDistributerId;
	/**
	 *  
	 */
	private Integer gbDgsrdGbDepartmentId;
	/**
	 *  
	 */
	private Integer gbDgsrdGbDepartmentFatherId;
	/**
	 *  
	 */
	private Integer gbDgsrdGbDisGoodsId;
	/**
	 *  
	 */
	private Integer gbDgsrdGbDepDisGoodsId;
	/**
	 *  
	 */
	private Integer gbDgsrdGbDisGoodsFatherId;
	/**
	 *  批次采购商品id
	 */
	private Integer gbDgsrdGbGoodsStockId;
	/**
	 *  盘库日期
	 */
	private String gbDgsrdDate;
	/**
	 *  盘库周
	 */
	private String gbDgsrdWeek;
	/**
	 *  盘库月
	 */
	private String gbDgsrdMonth;
	private String gbDgsrdYear;
	/**
	 *  执行废弃时间
	 */
	private String gbDgsrdFullTime;
	/**
	 *  1,cost;2waste;3loass;4return
	 */
	private Integer gbDgsrdType;
	/**
	 *  执行废弃用户
	 */
	private Integer gbDgsrdDoUserId;
	/**
	 *  执行废弃用户
	 */
	private String gbDgsrdCostWeight;
	/**
	 *  执行废弃数量
	 */
	private String gbDgsrdCostSubtotal;
	/**
	 *  执行废弃用户
	 */
	private String gbDgsrdWasteWeight;
	/**
	 *  执行废弃数量
	 */
	private String gbDgsrdWasteSubtotal;
	/**
	 *  执行废弃用户
	 */
	private String gbDgsrdLossWeight;
	/**
	 *  执行废弃数量
	 */
	private String gbDgsrdLossSubtotal;
	/**
	 *  执行废弃用户
	 */
	private String gbDgsrdReturnWeight;
	/**
	 *  执行废弃数量
	 */
	private String gbDgsrdReturnSubtotal;
	/**
	 *  执行废弃用户
	 */
	private String gbDgsrdProduceWeight;
	/**
	 *  执行废弃数量
	 */
	private String gbDgsrdProduceSubtotal;
	private String gbDgsrdProduceProfitSubtotal;
	/**
	 *  批次采购商品id
	 */
	private Integer gbDgsrdGbPurGoodsId;
	/**
	 *  批次采购商品id
	 */
	private Integer gbDgsrdGbGoodsInventoryType;
	/**
	 *  执行废弃数量
	 */
	private String gbDgsrdProfitSubtotal;
	/**
	 *  执行废弃数量
	 */
	private String gbDgsrdSalesSubtotal;
	private String gbDgsrdRestWeight;
	private String gbDgsrdDay;
	private String gbDgsrdWeight;
	private String gbDgsrdSubtotal;

	private GbDistributerGoodsEntity gbDistributerGoodsEntity;
	private GbDepartmentEntity gbDepartmentEntity;


}
