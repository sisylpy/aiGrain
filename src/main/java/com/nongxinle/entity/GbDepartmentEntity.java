package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 订货部门实体
 */
@Data
@TableName("gb_department")
@EqualsAndHashCode(callSuper = false)
public class GbDepartmentEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 订货部门id
     */
    @TableId(type = IdType.AUTO)
    private Integer gbDepartmentId;

    /**
     * 订货部门名称
     */
    private String gbDepartmentName;

    /**
     * 订货部门上级id
     */
    private Integer gbDepartmentFatherId;

    /**
     * 订货部门类型
     */
    private Integer gbDepartmentType;

    /**
     * 订货部门子部门数量
     */
    private Integer gbDepartmentSubAmount;

    /**
     * 订货部门批发商id
     */
    private Integer gbDepartmentDisId;

    /**
     * 文件路径
     */
    private String gbDepartmentFilePath;

    /**
     * 1:是父级门店 0:是子级门店
     */
    private Integer gbDepartmentIsGroupDep;

    /**
     * 打印名称
     */
    private String gbDepartmentPrintName;

    /**
     * 显示周数
     */
    private Integer gbDepartmentShowWeeks;

    /**
     * 结算类型
     */
    private Integer gbDepartmentSettleType;

    /**
     * 客户简称
     */
    private String gbDepartmentAttrName;

    /**
     * 路线id
     */
    private Integer gbDepartmentRouteId;

    /**
     * 结算时间
     */
    private String gbDepartmentSettleFullTime;

    /**
     * 结算日期
     */
    private String gbDepartmentSettleDate;

    /**
     * 结算周
     */
    private String gbDepartmentSettleWeek;

    /**
     * 结算月
     */
    private String gbDepartmentSettleMonth;

    /**
     * 结算年
     */
    private String gbDepartmentSettleYear;

    /**
     * 结算次数
     */
    private String gbDepartmentSettleTimes;

    /**
     * 结算id
     */
    private Integer gbDepartmentDepSettleId;

    /**
     * 加盟级别
     */
    private Integer gbDepartmentLevel;

    /**
     * 排序
     */
    private Integer gbDepartmentSort;

    /**
     * 打印设置
     */
    private Integer gbDepartmentPrintSet;

    /**
     * 名称拼音
     */
    private String gbDepartmentNamePy;

    /**
     * 纬度
     */
    private String gbDepartmentLatitude;

    /**
     * 经度
     */
    private String gbDepartmentLongitude;


    // ========== 非数据库字段 ==========

    /**
     * 关联对象
     */
    @TableField(exist = false)
    private GbDistributerUserEntity gbDistributerUserEntity;

    @TableField(exist = false)
    private Integer cankaoDepId;

    @TableField(exist = false)
    private GbDepartmentEntity fatherGbDepartmentEntity;

    @TableField(exist = false)
    private GbDepartmentDisGoodsEntity gbDepartmentDisGoodsEntity;

    @TableField(exist = false)
    private GbDepartmentGoodsDailyEntity departmentGoodsDailyEntity;

    @TableField(exist = false)
    private Boolean isSelected = false;

    @TableField(exist = false)
    private List<GbDepartmentEntity> gbDepartmentEntityList;

    @TableField(exist = false)
    private List<GbDepartmentUserEntity> gbDepartmentUserEntities;

    /**
     * 成本页：该部门下 reduce 明细列表（与小程序 wasteReduceList 对应）
     */
    @TableField(exist = false)
    private List<GbDepartmentGoodsStockReduceEntity> wasteReduceList;


    @TableField(exist = false)
    private String depProduceGoodsTotalString;

    @TableField(exist = false)
    private String depLossGoodsTotalString;

    @TableField(exist = false)
    private String depWasteGoodsTotalString;

    @TableField(exist = false)
    private String depEmployeeMealGoodsTotalString;

    @TableField(exist = false)
    private String depCostGoodsTotalString;


    @TableField(exist = false)
    private String depStockSubtotalString;

    @TableField(exist = false)
    private String depStockWeightTotalString;

    /**
     * 按日商品曲线：该部门当日本料的出库 1+2+3 重量合计（两位小数）、金额合计（两位小数，与 reduce subtotal 一致）。
     */
    @TableField(exist = false)
    private String dayOutbound123Weight;

    @TableField(exist = false)
    private String dayOutbound123Subtotal;

    /**
     * 按日：该部门当日本料配方理论用量、毛利贡献金额（与 {@code GbDishCostAnalysisService#summarizeDisGoodsDayForReduceCurve} 同口径）。
     */
    @TableField(exist = false)
    private String dayTheoryOutboundQty;

    @TableField(exist = false)
    private String dayGrossProfitContributionTotal;

    /**
     * 按日：本配料当日在各菜品上的销售拆解（与 {@code GbDishCostAnalysisService#summarizeDisGoodsDayForReduceCurve} 返回的
     * {@code dishIngredientDayBreakdown} 一致）：实销份数、实收、每份配方用量、理论总用量、标价分摊到本料、本料 1+2+3 出库成本、毛利贡献。
     */
    @TableField(exist = false)
    private List<Map<String, Object>> dayDishIngredientSales;

}
