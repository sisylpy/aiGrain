package com.nongxinle.entity;

/**
 * 
 * @author lpy
 * @date 08-19 19:02
 */

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;


@Setter@Getter@ToString
@TableName("gb_department_goods_stock")
public class GbDepartmentGoodsStockEntity implements Serializable,Comparable {
	private static final long serialVersionUID = 1L;
	
	/**
	 *  
	 */
	@TableId(type = IdType.AUTO)
	private Integer gbDepartmentGoodsStockId;
	/**
	 *  
	 */
	private Integer gbDgsGbDistributerId;
	/**
	 *  
	 */
	private Integer gbDgsGbDepartmentId;
	/**
	 *  
	 */
	private Integer gbDgsGbDepartmentFatherId;
	/**
	 *  
	 */
	private Integer gbDgsGbDisGoodsId;
	private Integer gbDgsGbDisGoodsFatherId;
	private Integer gbDgsGbDisGoodsGrandId;
	private Integer gbDgsGbDisGoodsGreatId;
	/**
	 *  
	 */
	private Integer gbDgsGbDepDisGoodsId;
	/**
	 *  
	 */
	private Integer gbDgsGbDepartmentOrderId;
	/**
	 *  批次数量
	 */
	private String gbDgsWeight;
	/**
	 *  剩余数量
	 */
	private String gbDgsRestWeight;
	/**
	 *  批次日期
	 */
	private String gbDgsDate;
	private String gbDgsTimeStamp;
	private String gbDgsWeek;
	private String gbDgsMonth;
	private String gbDgsYear;
	/**
	 *  接收用户
	 */
	private Integer gbDgsReceiveUserId;
	/**
	 * 入库批次供货来源（农鲜供货商维度）。与采购商品行 {@code gb_DPG_purchase_nx_supplier_id}、出库流水 {@code gb_dgsr_stock_nx_supplier_id} 同语义：
	 * {@code -1} 表示自采；正整数为 nx 供货商 ID（供货商配送）。回答「这批库存是自采还是供货商送的」应以此字段为准。
	 */
	private Integer gbDgsNxSupplierId;
	/**
	 *  批次状态
	 */
	private Integer gbDgsStatus;
	/**
	 *  批次采购商品id
	 */
	private Integer gbDgsGbPurGoodsId;
	/**
	 *  批次单价
	 */
	private String gbDgsPrice;
	/**
	 *  批次成本
	 */
	private String gbDgsSubtotal;
	/**
	 *  批次剩余成本
	 */
	private String gbDgsRestSubtotal;

	@TableField(exist = false)
	private Boolean isSelected = false;

//	private String gbDgsInventoryWeight = "-1";
	@TableField(exist = false)
	private String  gbDgsMyProduceWeight = "-1";
	@TableField(exist = false)
	private String  gbDgsMyWasteWeight = "-1";
	@TableField(exist = false)
	private String  gbDgsMyLossWeight = "-1";
	@TableField(exist = false)
	private String  gbDgsMyReturnWeight = "-1";
	/** 本次员工餐实际使用数量（基础单位重量，与 {@code gbDgsMyLossWeight} 同语义） */
	@TableField(exist = false)
	private String gbDgsMyEmployeeMealWeight = "-1";
	@TableField(exist = false)
	private Integer gbDgsReturnUserId ;


	private Integer gbDgsGbGoodsStockId;
	@TableField(exist = false)
	private GbDepartmentGoodsStockEntity fromDepStockEntity;
	@TableField(exist = false)
	private GbDepartmentGoodsStockEntity toDepStockEntity;

	@TableField(exist = false)
	private GbDistributerGoodsEntity gbDistributerGoodsEntity;
	private Integer gbDgsGbFromDepartmentId;
	@TableField(exist = false)
	@JSONField(name = "gbDepartmentEntity")
	@JsonProperty("gbDepartmentEntity")
	private GbDepartmentEntity gbDepartmentEntity;
	private String gbDgsInventoryDate;
	private String gbDgsInventoryWeek;
	private String gbDgsInventoryMonth;
	private String gbDgsInventoryYear;
	private String gbDgsFullTime;
	private String gbDgsWarnFullTime;
	private String gbDgsWasteFullTime;

	private String gbDgsDoWasteFullTime;
	@TableField(exist = false)
	private Integer gbDgsReduceWeightUserId;
	private String gbDgsLossWeight;
	private String gbDgsLossSubtotal;
	private String gbDgsReturnWeight;
	private String gbDgsReturnSubtotal;
	/** 批次累计员工餐出库（由 reduce type=6 汇总；无独立库存列，展示层回填） */
	@TableField(exist = false)
	private String gbDgsEmployeeMealWeight;
	@TableField(exist = false)
	private String gbDgsEmployeeMealSubtotal;
	private String gbDgsProduceWeight;
	private String gbDgsProduceSubtotal;
	private String gbDgsProduceSellingSubtotal;

	private Integer gbDgsDepSettleId;
	private Integer gbDgsFromDepSettleId;
	private Integer gbDgsStars;
	private Integer gbDgsPurUserId;

	@TableField(exist = false)
	private String gbStockWarnHours;
	@TableField(exist = false)
	private String gbStockWasetHours;

	private String gbDgsOutFullTime;
	private String gbDgsOutDate;
	private  Integer gbDgsOutHour;
	private  String gbDgsInventoryFullTime;
	private  String gbDgsWarnTimeQuantumName;
	private  String gbDgsWasteTimeQuantumName;
	@TableField(exist = false)
	private BigDecimal timeStockRestTotal;
	@TableField(exist = false)
	private String gbDgsInventoryWeight;
	private Integer gbDgsGbPriceGoodsId;
	private Integer gbDgsWeightGoodsId;
	private Integer gbDgsNxDistributerId;
	private String gbDgsGbPriceSubtotal;
	private String gbDgsGbPriceSubtotalScale;
	private String gbDgsRestWeightShowStandard;
	private String gbDgsRestWeightShowStandardName;
	private String gbDgsBetweenPrice;
	private String gbDgsProfitWeight;
	private String gbDgsProfitSubtotal;
	private String gbDgsSellingPrice;
	private String gbDgsSellingSubtotal;
	private String gbDgsWasteWeight;
	private String gbDgsWasteSubtotal;
	private String gbDgsAfterProfitSubtotal;
	private String gbDgsCostRate;

	@TableField(exist = false)
	private String outWeightTotal;

	@TableField(exist = false)
	private GbDepartmentOrdersEntity gbDepartmentOrdersEntity;
	@TableField(exist = false)
	private GbDepartmentUserEntity wasteUserEntity;
	@TableField(exist = false)
	private GbDepartmentUserEntity lossUserEntity;
	@TableField(exist = false)
	private GbDepartmentUserEntity stockUserEntity;
	@TableField(exist = false)
	@JSONField(name = "purchaseGoodsEntity")
	private GbDistributerPurchaseGoodsEntity purchaseGoodsEntity;
	@TableField(exist = false)
	private GbDepartmentGoodsStockReduceEntity returnReduceEntity;
	@TableField(exist = false)
	private List<GbDepartmentGoodsStockReduceEntity> starReduce;
	@TableField(exist = false)
	private GbDepartmentGoodsStockReduceAttachmentEntity reduceAttachmentEntity;
	@TableField(exist = false)
	private List<GbDepartmentGoodsStockReduceEntity> goodsStockReduceEntityList;
	@TableField(exist = false)
	private List<GbDepartmentGoodsStockEntity> outStockList;

	@TableField(exist = false)
	private List<GbDepartmentEntity> wasteDepartmentEntities;
	@TableField(exist = false)
	private Double goodsWasteTotal;

	@TableField(exist = false)
	@JSONField(name = "goodsName")
	private String goodsName;

	@TableField(exist = false)
	@JSONField(name = "goodsStandardName")
	private String goodsStandardName;

	@TableField(exist = false)
	@JSONField(name = "goodsStandard")
	private String goodsStandard;

	@TableField(exist = false)
	@JSONField(name = "goodsPrice")
	private String goodsPrice;

	/** 来自 {@code gb_distributer_goods.gb_dg_quantity_days}（Mapper join 投影）。 */
	@TableField(exist = false)
	private Integer gbDgQuantityDays;

	@TableField(exist = false)
	@JSONField(name = "wasteGoodsCount")
	private Integer wasteGoodsCount;

	@TableField(exist = false)
	private NxJrdhSupplierEntity nxJrdhSupplierEntity;

	@Override
	public int compareTo(Object o) {
		if (o instanceof GbDepartmentGoodsStockEntity) {
			GbDepartmentGoodsStockEntity e = (GbDepartmentGoodsStockEntity) o;
			return this.getGbDepartmentGoodsStockId().compareTo(e.getGbDepartmentGoodsStockId());
		}
		return 0;

	}
}
