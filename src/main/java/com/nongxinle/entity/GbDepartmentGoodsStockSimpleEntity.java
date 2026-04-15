package com.nongxinle.entity;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.io.Serializable;

/**
 * 部门库存简化版对象 - 用于页面展示
 */
@Data
public class GbDepartmentGoodsStockSimpleEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    // ========== 原有库存字段 ==========
    private Integer gbDepartmentGoodsStockId;
    private Integer gbDgsGbDistributerId;
    private Integer gbDgsGbDepartmentId;
    private Integer gbDgsGbDepartmentFatherId;
    private Integer gbDgsGbDisGoodsId;
    private Integer gbDgsGbDisGoodsFatherId;
    private Integer gbDgsGbDisGoodsGrandId;
    private Integer gbDgsGbDisGoodsGreatId;
    private Integer gbDgsGbDepDisGoodsId;
    private Integer gbDgsGbDepartmentOrderId;
    private String gbDgsWeight;
    private String gbDgsRestWeight;
    private String gbDgsDate;
    private String gbDgsTimeStamp;
    private String gbDgsWeek;
    private String gbDgsMonth;
    private String gbDgsYear;
    private Integer gbDgsReceiveUserId;
    private Integer gbDgsNxSupplierId;
    private Integer gbDgsStatus;
    private Integer gbDgsGbPurGoodsId;
    private String gbDgsPrice;
    private String gbDgsSubtotal;
    private String gbDgsRestSubtotal;
    private String gbDgsFullTime;
    private String gbDgsWarnFullTime;
    private String gbDgsWasteFullTime;
    private String gbDgsDoWasteFullTime;
    private String gbDgsLossWeight;
    private String gbDgsLossSubtotal;
    private String gbDgsReturnWeight;
    private String gbDgsReturnSubtotal;
    private String gbDgsProduceWeight;
    private String gbDgsProduceSubtotal;
    private String gbDgsProduceSellingSubtotal;
    private Integer gbDgsDepSettleId;
    private Integer gbDgsFromDepSettleId;
    private Integer gbDgsStars;
    private Integer gbDgsPurUserId;
    private String gbDgsOutFullTime;
    private String gbDgsOutDate;
    private Integer gbDgsOutHour;
    private String gbDgsInventoryFullTime;
    private String gbDgsWarnTimeQuantumName;
    private String gbDgsWasteTimeQuantumName;
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
    private String gbDgsInventoryDate;
    private String gbDgsInventoryWeek;
    private String gbDgsInventoryMonth;
    private String gbDgsInventoryYear;

    // ========== 部门信息（直接查询出来）==========
    @JSONField(name = "departmentName")
    private String gbDepartmentName;

    @JSONField(name = "departmentImg")
    private String gbDepartmentImg;

    // ========== 商品信息（直接查询出来）==========
    @JSONField(name = "goodsName")
    private String goodsName;

    @JSONField(name = "goodsStandardName")
    private String goodsStandardName;

    @JSONField(name = "goodsStandard")
    private String goodsStandard;

    @JSONField(name = "goodsPrice")
    private String goodsPrice;

    @JSONField(name = "goodsImg")
    private String goodsImg;

    // ========== 统计字段 ==========
    @JSONField(name = "wasteGoodsCount")
    private Integer wasteGoodsCount;

    @JSONField(name = "wasteTotal")
    private Double wasteTotal;
}
