package com.nongxinle.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.*;

/**
 * 批发商商品实体
 */
@Data
@TableName("gb_distributer_goods")
@EqualsAndHashCode(callSuper = false)
public class GbDistributerGoodsEntity implements Serializable, Comparable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Integer gbDistributerGoodsId;
    private Integer gbDgDfgGoodsFatherId;
    private Integer gbDgDistributerId;
    private Integer gbDgGoodsStatus;
    private Integer gbDgGoodsIsWeight;
    private String gbDgGoodsName;
    private String gbDgGoodsDetail;
    private String gbDgGoodsStandardname;
    private String gbDgGoodsPinyin;
    private String gbDgGoodsPy;
    private Integer gbDgNxGoodsId;
    private String gbDgNxFatherImg;
    private String gbDgNxFatherImgLarge;
    private Integer gbDgNxFatherId;
    @TableField(exist = false)
    private String gbDgNxGrandName;
    @TableField(exist = false)
    private String gbDgNxGreatGrandName;
    @TableField(exist = false)
    private String gbDgNxFatherName;
    @TableField(exist = false)
    private String gbDgNxGoodsFatherImg;
    private Integer gbDgControlPrice;
    private Integer gbDgControlFresh;
    private String gbDgFreshWarnHour;
    private String gbDgFreshWasteHour;
    private Integer gbDgGoodsInventoryType;
    private Integer gbDgGbSupplierId;
    private Integer gbDgDfgGoodsGrandId;
    private Integer gbDgDfgGoodsGreatId;
    @TableField(exist = false)
    private String goodsGreatName;
    @TableField(exist = false)
    private String goodsGreatImg;
    @TableField(exist = false)
    private Integer goodsGreatSort;
    private Integer gbDgNxGrandId;
    private Integer gbDgQuantityDays;
    private Integer gbDgIsFranchisePrice;
    private Integer gbDgNxGreatGrandId;
    private Integer gbDgPullOff;
    private String gbDgGoodsBrand;
    private String gbDgGoodsPlace;
    private String gbDgNxGoodsFatherColor;
    private String gbDgGoodsStandardWeight;
    private Integer gbDgGoodsType;
    private String gbDgGoodsPrice;
    private String gbDgGoodsLowestPrice;
    private String gbDgGoodsHighestPrice;
    @TableField(exist = false)
    private String gbDgGoodsAveragePrice;
    @TableField(exist = false)
    private String goodsPriceFluctuation;
    @TableField(exist = false)
    private String goodsPriceDiff;
    @TableField(exist = false)
    private String calc_highest_price;
    @TableField(exist = false)
    private String calc_lowest_price;
    private String gbDgSelfPrice;
    private String gbDgSellingPrice;
    private String gbDgNxDistributerGoodsPrice;
    private Integer gbDgNxDistributerId;
    private Integer gbDgNxDistributerGoodsId;
    private Integer gbDgGbDepartmentId;
    private Integer gbDgIsSelfControl;
    private Integer gbDgGoodsSort;
    private Integer gbDgGoodsSonsSort;
    private Integer gbDgGoodsIsHidden;

    @TableField(exist = false)
    private Boolean isSelected = false;

    @TableField(exist = false)
    private List<NxStandardEntity> nxStandardEntities;
    @TableField(exist = false)
    private List<GbDistributerAliasEntity> gbDistributerAliasEntities;
    @TableField(exist = false)
    private List<NxAliasEntity> nxAliasEntities;
    @TableField(exist = false)
    private List<GbDepartmentOrdersEntity> gbDepartmentOrdersEntities;
    @TableField(exist = false)
    @JsonIgnore
    private GbDepartmentOrdersEntity gbDepartmentOrdersEntity;

    @TableField(exist = false)
    private GbDistributerAliasEntity gbAliasEntities;
    @TableField(exist = false)
    private List<GbDistributerStandardEntity> gbDistributerStandardEntities;
    @TableField(exist = false)
    private GbDepartmentEntity gbDepartmentEntity;
    @TableField(exist = false)
    private List<GbDepartmentGoodsStockEntity> gbDepartmentGoodsStockEntities;
    @TableField(exist = false)
    private List<GbDepartmentGoodsStockSimpleEntity> gbDepartmentGoodsStockSimpleEntities;
    @TableField(exist = false)
    @JsonIgnore
    private GbDepartmentGoodsStockEntity gbDepartmentGoodsStockEntity;
    @TableField(exist = false)
    @JsonIgnore
    private GbDistributerGoodsShelfEntity gbDistributerGoodsShelfEntity;
    @TableField(exist = false)
    @JsonIgnore
    private GbDistributerGoodsShelfGoodsEntity gbDistributerGoodsShelfGoodsEntity;
    @TableField(exist = false)
    private List<GbDistributerPurchaseGoodsEntity> wastePurGoodsEntities;
    @TableField(exist = false)
    private List<GbDistributerPurchaseGoodsEntity> returnPurGoodsEntities;
    @TableField(exist = false)
    private List<GbDepartmentEntity> wasteDepartmentEntities;

    /** 成本页：是否展开明细（reduce 按部门树） */
    @TableField(exist = false)
    private Boolean showCostList;
    @TableField(exist = false)
    private TreeSet<GbDepartmentEntity> stockDepartmentEntities;
    @TableField(exist = false)
    private TreeSet<GbDepartmentEntity> produceDepartmentEntities;
    @TableField(exist = false)
    private List<GbDepartmentOrdersEntity> prepareOrderEntities;
    @TableField(exist = false)
    private List<GbDepartmentOrdersEntity> weightedOrderEntities;
    @TableField(exist = false)
    private List<GbDepartmentOrdersEntity> deliveryOrderEntities;
    @TableField(exist = false)
    private List<GbDistributerGoodsPriceEntity> gbDisGoodsPriceEntities;

    @TableField(exist = false)
    private Map<String, Object> purEveryDay;

    @TableField(exist = false)
    private Double goodsStockTotal = 0.0;
    @TableField(exist = false)
    private String goodsStockTotalString;
    @TableField(exist = false)
    private Double goodsStockWeightTotal = 0.0;
    @TableField(exist = false)
    private String goodsStockWeightTotalString = "0";

    @TableField(exist = false)
    private Double outStockTotal = 0.0;
    @TableField(exist = false)
    private String outStockTotalString = "0";

    @TableField(exist = false)
    private Double goodsAverageStockTotal = 0.0;
    @TableField(exist = false)
    private String goodsAverageStockTotalString = "0";

    @TableField(exist = false)
    private Double goodsPriceTotal = 0.0;
    @TableField(exist = false)
    private String goodsPriceTotalString;

    @TableField(exist = false)
    private Double goodsAveragePrice = 0.0;
    @TableField(exist = false)
    private String goodsAveragePriceString = "0";
    @TableField(exist = false)
    private Integer goodsAveragePriceWhat = 0;

    @TableField(exist = false)
    private Double goodsAveragePricePercent = 0.0;
    @TableField(exist = false)
    private String goodsAveragePricePercentString = "0";

    @TableField(exist = false)
    private String goodsAverageOrderTimes = "0";
    @TableField(exist = false)
    private double goodsAverageStars;
    @TableField(exist = false)
    private String goodsAverageStarsString = "0";

    @TableField(exist = false)
    private int goodsStarGreen;
    @TableField(exist = false)
    private int goodsStarGray;
    @TableField(exist = false)
    private int goodsStarHalf;

    @TableField(exist = false)
    private String goodsCostTotalString = "0";

    @TableField(exist = false)
    private Double goodsCostWeightTotal = 0.0;
    @TableField(exist = false)
    private String goodsCostWeightTotalString = "0";
    @TableField(exist = false)
    private Double goodsWeightTotal = 0.0;
    @TableField(exist = false)
    private String goodsWeightTotalString = "0";

    @TableField(exist = false)
    private String goodsWasteTotalString = "0";
    @TableField(exist = false)
    private Double goodsWasteWeightTotal = 0.0;
    @TableField(exist = false)
    private String goodsWasteWeightTotalString = "0";
    @TableField(exist = false)
    private String goodsWastePercent = "0";

    @TableField(exist = false)
    private String goodsLossTotalString = "0";
    @TableField(exist = false)
    private Double goodsLossWeightTotal = 0.0;
    @TableField(exist = false)
    private String goodsLossWeightTotalString = "0";
    @TableField(exist = false)
    private String goodsLossPercent = "0";

    @TableField(exist = false)
    private String goodsProduceTotalString = "0";
    @TableField(exist = false)
    private Double goodsProfitTotal = 0.0;
    @TableField(exist = false)
    private String goodsProfitTotalString = "0";
    @TableField(exist = false)
    private BigDecimal goodsCostTotal = BigDecimal.ZERO;
    @TableField(exist = false)
    private BigDecimal goodsProduceTotal = BigDecimal.ZERO;
    @TableField(exist = false)
    private BigDecimal goodsLossTotal = BigDecimal.ZERO;
    @TableField(exist = false)
    private BigDecimal goodsWasteTotal = BigDecimal.ZERO;

    @TableField(exist = false)
    private Double goodsProduceWeightTotal = 0.0;
    @TableField(exist = false)
    private String goodsProduceWeightTotalString;
    @TableField(exist = false)
    private String goodsProducePercent;

    @TableField(exist = false)
    private Double goodsReturnWeightTotal = 0.0;
    @TableField(exist = false)
    private String goodsReturnWeightTotalString = "0";
    @TableField(exist = false)
    private Double goodsReturnTotal = 0.0;
    @TableField(exist = false)
    private String goodsReturnTotalString = "0";
    @TableField(exist = false)
    private String goodsReturnPercent = "0";

    @TableField(exist = false)
    private Double goodsEveryWasteTotal = 0.0;
    @TableField(exist = false)
    private String goodsEveryWasteTotalString = "0";
    @TableField(exist = false)
    private Double goodsEveryWasteWeightTotal = 0.0;
    @TableField(exist = false)
    private String goodsEveryWasteWeightTotalString = "0";

    @TableField(exist = false)
    private Double goodsEveryLossTotal = 0.0;
    @TableField(exist = false)
    private String goodsEveryLossTotalString = "0";
    @TableField(exist = false)
    private Double goodsEveryLossWeightTotal = 0.0;
    @TableField(exist = false)
    private String goodsEveryLossWeightTotalString = "0";

    @TableField(exist = false)
    private Double goodsEveryProfitTotal = 0.0;
    @TableField(exist = false)
    private String goodsEveryProfitTotalString = "0";
    @TableField(exist = false)
    private Double goodsEveryProduceTotal = 0.0;
    @TableField(exist = false)
    private String goodsEveryProduceTotalString = "0";
    @TableField(exist = false)
    private Double goodsEveryProduceWeightTotal = 0.0;
    @TableField(exist = false)
    private String goodsEveryProduceWeightTotalString = "0";
    @TableField(exist = false)
    private Double everyDayWeight;
    @TableField(exist = false)
    private String everyDayWeightString;
    @TableField(exist = false)
    private Double everyWeekWeight;
    @TableField(exist = false)
    private String everyWeekWeightString;
    @TableField(exist = false)
    private Double everyMonthWeight;
    @TableField(exist = false)
    private String everyMonthWeightString;
    @TableField(exist = false)
    private String averageManyTotal;
    @TableField(exist = false)
    private String goodsStockManyString;
    @TableField(exist = false)
    private Double goodsStockMany;

    @TableField(exist = false)
    private String gbDgFranchisePriceOne;
    @TableField(exist = false)
    private String gbDgFranchisePriceOneUpdate;
    @TableField(exist = false)
    private String gbDgFranchisePriceTwo;
    @TableField(exist = false)
    private String gbDgFranchisePriceTwoUpdate;
    @TableField(exist = false)
    private String gbDgFranchisePriceThree;
    @TableField(exist = false)
    private String gbDgFranchisePriceThreeUpdate;

    @TableField(exist = false)
    private Double goodsFreshRate;
    @TableField(exist = false)
    private String goodsFreshRateString;
    @TableField(exist = false)
    private String goodsClearTimeString;
    @TableField(exist = false)
    private Double goodsClearTime;
    @TableField(exist = false)
    private String goodsCostRateString;
    @TableField(exist = false)
    private Double goodsCostRate;
    @TableField(exist = false)
    private String goodsSalesRateString;
    @TableField(exist = false)
    private Double goodsSalesRate;

    @TableField(exist = false)
    private String goodsLossRateString;
    @TableField(exist = false)
    private Double goodsLossRate;

    @TableField(exist = false)
    private String goodsWasteRateString;
    @TableField(exist = false)
    private Double goodsWasteRate;
    @TableField(exist = false)
    private int goodsPurTotalCount;
    @TableField(exist = false)
    private String goodsPurTotalWeight;
    @TableField(exist = false)
    private String goodsPurTotalSubtotal;

    @TableField(exist = false)
    private Map<String, Object> goodsData;

    @TableField(exist = false)
    private NxJrdhSupplierEntity gbDistributerAppointSupplierEntity;

    @TableField(exist = false)
    private String gbTipText;
    @TableField(exist = false)
    private String aiOrderQuantity;

    @TableField(exist = false)
    private String aiOrderStandard;
    @TableField(exist = false)
    private String aiDailyUsage;
    @TableField(exist = false)
    private String aiRecentAvgUsage;
    @TableField(exist = false)
    private String aiUsageVariation;
    @TableField(exist = false)
    private String aiSafetyStock;
    @TableField(exist = false)
    private String aiReorderPoint;
    @TableField(exist = false)
    private String aiCurrentStock;
    @TableField(exist = false)
    private String aiCurrentStockUnit;
    @TableField(exist = false)
    private String aiLastOrderDate;
    @TableField(exist = false)
    private String aiLastOrderQuantity;
    @TableField(exist = false)
    private String aiLastOrderUnit;
    @TableField(exist = false)
    private String aiDaysSinceLastOrder;
    @TableField(exist = false)
    private GbDepartmentDisGoodsEntity gbDepartmentDisGoodsEntity;

    @TableField(exist = false)
    private String aiTomorrowNeed;
    @TableField(exist = false)
    private String aiAvailableDays;
    @TableField(exist = false)
    private GbDistributerPurchaseGoodsEntity unPurDisGoodsList;

    @Override
    public int compareTo(Object o) {
        if (o instanceof GbDistributerGoodsEntity) {
            GbDistributerGoodsEntity e = (GbDistributerGoodsEntity) o;
            return this.gbDistributerGoodsId.compareTo(e.gbDistributerGoodsId);
        }
        return 0;
    }
}
