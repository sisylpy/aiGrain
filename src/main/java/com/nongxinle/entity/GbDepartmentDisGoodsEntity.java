package com.nongxinle.entity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;

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

    private Integer gbDdgPrepareStatus;
    private Integer gbDdgDepGoodsStatus;
    @TableField(exist = false)
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
    private GbDepartmentGoodsDailyEntity gbDepGoodsDailyEntity;

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


    @Override
    public int compareTo(Object o) {
        if (o instanceof GbDepartmentDisGoodsEntity) {
            GbDepartmentDisGoodsEntity e = (GbDepartmentDisGoodsEntity) o;
            return this.gbDepartmentDisGoodsId.compareTo(e.gbDepartmentDisGoodsId);
        }
        return 0;
    }
}
