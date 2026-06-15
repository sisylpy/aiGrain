package com.nongxinle.entity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.nongxinle.dto.GbDepReorderAuxHint;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * 部门分销商品实体
 */
@Data
@TableName("gb_department_dis_goods")
@EqualsAndHashCode(callSuper = false)
public class GbDepartmentDisGoodsEntity implements Serializable, Comparable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Integer gbDepartmentDisGoodsId;
    private Integer gbDdgDepartmentFatherId;
    private Integer gbDdgDepartmentId;
    private Integer gbDdgDisGoodsId;
    private Integer gbDdgDisGoodsFatherId;
    private Integer gbDdgDisGoodsGrandId;
    private String gbDdgDepGoodsName;
    private String gbDdgDepGoodsPinyin;
    private String gbDdgDepGoodsPy;
    private String gbDdgDepGoodsStandardname;
    private String gbDdgDepGoodsDetail;
    private String gbDdgDepGoodsBrand;
    private String gbDdgDepGoodsPlace;
    @TableField(exist = false)
    private String gbDisGoodsFile;
    @TableField(exist = false)
    private String gbDisGoodsFileLarge;
    private Integer gbDdgGoodsType;
    @TableField(exist = false)
    private Integer gbDgControlFresh;
    private Integer gbDdgNxDistributerId;
    private Integer gbDdgNxDistributerGoodsId;
    private Integer gbDdgGbDepartmentId;
    @TableField(exist = false)
    private String gbDdgGbDepartmentName;
    private Integer gbDdgGbSupplierId;
    private Integer gbDdgGbDisId;
    private Integer gbDdgDisGoodsGreatId;

    private String gbDdgInventoryDate;
    private String gbDdgInventoryFullTime;
    private String gbDdgStockTotalWeight;
    private String gbDdgStockTotalSubtotal;
    private String gbDdgPrepareTotalWeight;
    private String gbDdgShowStandardName;
    private Integer gbDdgShowStandardId;
    private String gbDdgShowStandardWeight;
    private String gbDdgShowStandardScale;
    private String gbDdgLevelPrice;
    private String gbDdgSellingPrice;
    private String gbDdgOrderPrice;
    private String gbDdgOrderDate;
    private String gbDdgOrderRemark;
    private String gbDdgOrderQuantity;
    private String gbDdgOrderStandard;
    private String gbDdgOrderWeight;
    private String gbDdgPrintStandard;
    @TableField(exist = false)
    private String gbDdgOrderGoodsName;
    @TableField(exist = false)
    private String gbDdgOrderPriceLevel;
    @TableField(exist = false)
    private String gbDgFreshWasteHour;
    @TableField(exist = false)
    private String gbDgFreshWarnHour;
    @TableField(exist = false)
    private Integer gbDgGoodsInventoryType;
    @TableField(exist = false)
    private Integer gbDgQuantityDays;
    @TableField(exist = false)
    private String gbDgCartonUnit;
    @TableField(exist = false)
    private String gbDgItemsPerCarton;
    @TableField(exist = false)
    private Integer gbDgIsFranchisePrice;

    private Integer gbDdgPrepareStatus;
    private Integer gbDdgDepGoodsStatus;
    private Integer gbDdgDepGoodsPullOff;
    @TableField(exist = false)
    private String gbTipText;
    @TableField(exist = false)
    private String gbDgGoodsStandardWeight;
    @TableField(exist = false)
    private String gbDgGoodsName;
    @TableField(exist = false)
    private String gbDgGoodsStandardname;

    @TableField(exist = false)
    private Boolean showStock = false;

    @TableField(exist = false)
    private List<GbDepartmentGoodsStockEntity> gbDepartmentGoodsStockEntities;

    @TableField(exist = false)
    private GbDistributerGoodsEntity gbDistributerGoodsEntity;
    @TableField(exist = false)
    private GbDepartmentOrdersEntity gbDepartmentOrdersEntity;
    @TableField(exist = false)
    private List<GbDistributerStandardEntity> gbDistributerStandardEntities;
    @TableField(exist = false)
    private GbDepartmentEntity outStockDepartmentEntity;
    @TableField(exist = false)
    private GbDepartmentEntity gbGoodsDepartmentEntity;

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
    private String goodsStockWeightTotalString;
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
    private String aiTomorrowNeed;
    @TableField(exist = false)
    private String aiAvailableDays;

    /** 订货习惯：平均间隔天数（约整数） */
    @TableField(exist = false)
    private String aiHabitIntervalDays;
    /** 按习惯推算的下次订货日 yyyy-MM-dd */
    @TableField(exist = false)
    private String aiNextHabitOrderDate;
    /** 综合订货逻辑是否建议今日关注 true/false */
    @TableField(exist = false)
    private String aiShouldRemindToday;

    /** reduce 窗口统计：生产出库(type=1)日均重量 */
    @TableField(exist = false)
    private String aiReduceProductionDailyAvg;
    /** reduce 窗口统计：损耗(type=2)+损失(type=3)日均出库重量合计 */
    @TableField(exist = false)
    private String aiReduceLossWasteDailyAvg;
    /** 仅按生产日均×覆盖天数 − 当前库存，建议补货量（≥0，与批次库存同口径） */
    @TableField(exist = false)
    private String aiRecommendGapWeightProductionOnly;
    /** 按「生产+损耗+损失」合计日均×覆盖天数 − 当前库存，建议补货量（补充口径） */
    @TableField(exist = false)
    private String aiRecommendGapWeightWithLossWaste;
    /** 假定按生产日均线性耗尽，预估耗尽日 yyyy-MM-dd */
    @TableField(exist = false)
    private String aiEstimateDepleteDateProductionOnly;
    /** 假定按「生产+损耗+损失」日均线性耗尽，预估耗尽日（更早则优先备货） */
    @TableField(exist = false)
    private String aiEstimateDepleteDateWithLossWaste;
    /** 上面缺口口径使用的覆盖天数（如「3」） */
    @TableField(exist = false)
    private String aiRecommendCoverDaysUsed;

    /** 单次订货场景：按（最近一单 gb_do_weight − 当前库存）/ 距到货天数 估算的日均消耗（重量） */
    @TableField(exist = false)
    private String aiStockEstimateDailyUsage;
    /** 因「库存低于约 2 天估算消耗」而建议提醒时为 true */
    @TableField(exist = false)
    private String aiRemindLowStockBelowTwoDayUsage;
    /** 提醒原因：habit / stock_below_two_day_usage / 逗号分隔 */
    @TableField(exist = false)
    private String aiRemindReason;

    @TableField(exist = false)
    private List<GbDepReorderAuxHint> aiAuxHints;


    @Override
    public int compareTo(Object o) {
        if (o instanceof GbDepartmentDisGoodsEntity) {
            GbDepartmentDisGoodsEntity e = (GbDepartmentDisGoodsEntity) o;
            return this.gbDepartmentDisGoodsId.compareTo(e.gbDepartmentDisGoodsId);
        }
        return 0;
    }
}
