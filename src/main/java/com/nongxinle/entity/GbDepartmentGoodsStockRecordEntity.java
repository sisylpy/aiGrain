package com.nongxinle.entity;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.util.List;


@Setter@Getter@ToString

@TableName("gb_department_goods_stock_record")
public class GbDepartmentGoodsStockRecordEntity implements Serializable {
	private static final long serialVersionUID = 1L;
	
	/**
	 *  
	 */
	private Integer gbDepartmentGoodsStockRecordId;
	/**
	 *  
	 */
	private Integer gbDgscGbDepartmentId;
	/**
	 *  
	 */
	private Integer gbDgscGbDepartmentFatherId;
	private Integer gbDgscGbDistributerId;
	/**
	 *  
	 */
	private Integer gbDgscGbDisGoodsId;
	/**
	 *  
	 */
	private Integer gbDgscGbDepDisGoodsId;
	/**
	 *  
	 */
	private Integer gbDgscGbDepartmentOrderId;
	/**
	 *  批次数量
	 */
	private String gbDgscWeight;
	/**
	 *  批次单价
	 */
	private String gbDgscPrice;
	/**
	 *  批次成本
	 */
	private String gbDgscSubtotal;
	/**
	 *  剩余数量
	 */
	private String gbDgscRestWeight;
	/**
	 *  批次剩余成本
	 */
	private String gbDgscRestSubtotal;
	/**
	 *  批次日期
	 */
	private String gbDgscDate;
	/**
	 *  接收用户
	 */
	private Integer gbDgscReceiveUserId;
	/**
	 *  批次状态
	 */
	private Integer gbDgscStatus;
	/**
	 *  批次采购商品id
	 */
	private Integer gbDgscGbPurGoodsId;
	/**
	 *  批次采购商品id
	 */
	private Integer gbDgscGbGoodsStockId;
	/**
	 *  出库部门id
	 */
	private Integer gbDgscGbFromDepartmentId;
	/**
	 *  盘库日期
	 */
	private String gbDgscInventoryDate;
	/**
	 *  盘库周
	 */
	private String gbDgscInventoryWeek;
	/**
	 *  盘库月
	 */
	private String gbDgscInventoryMonth;
	/**
	 *  
	 */
	private Integer gbDgscGbDisGoodsFatherId;
	/**
	 *  盘库日期
	 */
	private String gbDgscFullTime;
	/**
	 *  盘库日期
	 */
	private String gbDgscWarnFullTime;
	/**
	 *  盘库日期
	 */
	private String gbDgscWasteFullTime;
	/**
	 *  执行废弃数量
	 */
	private String gbDgscDoWasteWeight;
	/**
	 *  执行废弃金额
	 */
	private String gbDgscDoWasteSubtotal;
	/**
	 *  执行废弃时间
	 */
	private String gbDgscDoWasteFullTime;
	/**
	 *  执行废弃用户
	 */
	private Integer gbDgscDoWasteUserId;
	/**
	 *  执行废弃数量
	 */
	private String gbDgscLossWeight;
	/**
	 *  执行废弃数量
	 */
	private String gbDgscLossSubtotal;
	private String gbDgscReturnWeight;
	private String gbDgscReturnSubtotal;
	private String gbDgscProduceWeight;
	private String gbDgscProduceSubtotal;
	private String gbDgscInventoryMany;
	private String gbDgscSellingPrice;
	private String gbDgscSellingSubtotal;
	private String gbDgscProfitPrice;
	private String gbDgscProfitWeight;
	private String gbDgscProfitSubtotal;
	private String gbDgscWeek;
	private String gbDgscMonth;
	private String gbDgscYear;
	private String gbDgscTimeStamp;
	/**
	 *  
	 */
	private Integer gbDgscDepSettleId;
	private Integer gbDgscFromDepSettleId;
	private Integer gbDgscDepDisGoodsSettleId;
	private Integer gbDgscFromGbGoodsStockId;
	private Integer gbDgscDgInventoryType;

	private List<GbDepartmentGoodsStockReduceEntity> goodsStockReduceEntityList;
	private GbDistributerGoodsEntity gbDistributerGoodsEntity;
	private GbDistributerPurchaseGoodsEntity purchaseGoodsEntity;
	private GbDepartmentOrdersEntity gbDepartmentOrdersEntity;
	private GbDepartmentEntity fromDepStockEntity;
	private GbDepartmentEntity gbDepartmentEntity;

}
