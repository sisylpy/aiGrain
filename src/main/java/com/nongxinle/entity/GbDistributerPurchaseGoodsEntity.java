package com.nongxinle.entity;

import java.io.Serializable;
import java.util.List;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

/**
 * 批发商采购商品实体
 */
@TableName("gb_distributer_purchase_goods")
@Data
public class GbDistributerPurchaseGoodsEntity implements Serializable {
	private static final long serialVersionUID = 1L;
	
	/**
	 *  批发商采购商品id
	 */
	@TableId(type = IdType.AUTO)
	private Integer gbDistributerPurchaseGoodsId;
	/**
	 *  采购商品id
	 */
	private Integer gbDpgDisGoodsId;
	/**
	 *  采购父级商品id
	 */
	private Integer gbDpgDisGoodsFatherId;
	private Integer gbDpgDisGoodsGrandId;
	private Integer gbDpgDisGoodsGreatId;
	/**
	 *  采购数量
	 */
	private String gbDpgQuantity;
	/**
	 *  采购规格
	 */
	private String gbDpgStandard;
	/**
	 *  采购状态
	 */
	private Integer gbDpgStatus;
	/**
	 *  采购批发商id
	 */
	private Integer gbDpgDistributerId;
	/**
	 *  采购方式
	 */
	private Integer gbDpgPurchaseType;
	private Integer gbDpgPurchaseNxSupplierId;
	/**
	 *  采购时间
	 */
	private String gbDpgTime;

	private String gbDpgApplyDate;

	private Integer gbDpgBatchId;

	private Integer gbDpgPurUserId;

	private String gbDpgBuyPrice;

	private String gbDpgBuyQuantity;

	@TableField(exist = false)
	private Boolean isSelected;

	private Integer gbDpgDisGoodsPriceId;
	private Integer gbDpgIsCheck;


	private Integer gbDpgOrdersAmount;
    private Integer gbDpgTypeAddUserId;

    private Integer gbDpgInputType;
    private Integer gbDpgPayType;
    private String gbDpgPurchaseDate;
    private String gbDpgBuySubtotal;
    private Integer gbDpgPurchaseDepartmentId;
    private String gbDpgPurchaseMonth;
    private String gbDpgPurchaseYear;
    private String gbDpgPurchaseFullTime;
    private String gbDpgPurchaseWeek;
    private String gbDpgPurchaseWeekYear;
    private String gbDpgBuyScaleQuantity;
    private String gbDpgBuyScalePrice;
    private String gbDpgBuyScale;
    private String gbDpgBuyPriceReason;
    private String gbDpgWarnFullTime;
    private String gbDpgWasteFullTime;
    private Integer gbDpgWeightId;
    private Integer gbDpgOrdersFinishAmount;
    private Integer gbDpgOrdersBillAmount;
    private Integer gbDpgOrdersWeightAmount;

	@TableField(exist = false)
    private String  gbDpgStockRestWeight;
	@TableField(exist = false)
    private String  gbDpgStockProduceWeight;
	@TableField(exist = false)
    private String  gbDpgStockLossWeight;
	@TableField(exist = false)
    private String  gbDpgStockWasteWeight;
	@TableField(exist = false)
    private String  gbDpgStockRestWeightTotal;
	@TableField(exist = false)
    private String  gbDpgStockReturnWeightTotal;
	@TableField(exist = false)
    private String  gbDpgSupplierFinishDate;
	@TableField(exist = false)
    private String  gbDpgStockFinishDate;

	@TableField(exist = false)
	private String gbDistributerGoodsName;

	@TableField(exist = false)
	private String gbDgGoodsPy;

	@TableField(exist = false)
	private String gbDgGoodsStandardname;

	@TableField(exist = false)
	private String gbDgGoodsStandardWeight;

	@TableField(exist = false)
	private String gbDgGoodsBrand;

	@TableField(exist = false)
	@JsonIgnore
    private GbDistributerGoodsPriceEntity gbDistributerGoodsPriceEntity;

	@TableField(exist = false)
	@JsonIgnore
	@JSONField(name = "nxJrdhSupplierEntity")
    private NxJrdhSupplierEntity nxJrdhSupplierEntity;

	@TableField(exist = false)
	private List<GbDepartmentOrdersEntity> gbDepartmentOrdersEntities;

	@TableField(exist = false)
	@JsonIgnore
	private GbDepartmentOrdersEntity gbDepartmentOrdersEntity;

	@TableField(exist = false)
//	@JsonIgnore
	private GbDistributerGoodsEntity gbDistributerGoodsEntity;

	@TableField(exist = false)
	@JsonIgnore
	private GbDepartmentEntity purchaseDepartmentEntity;

	@TableField(exist = false)
	@JsonIgnore
	@JSONField(name = "purchaseDepartmentUser")
	private GbDepartmentUserEntity purchaseDepartmentUser;

	@TableField(exist = false)
	@JsonIgnore
	private List<GbDepartmentEntity> wasteDepartmentEntities;

	@TableField(exist = false)
	@JsonIgnore
	private List<GbDepartmentGoodsStockEntity> gbDepartmentGoodsStockEntities;

}
