package com.nongxinle.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * AI日营业额统计DTO
 * 用于接口 GET /ai/daily-revenue/stats/{departmentId} 的返回值
 * 
 * 字段说明：
 * 1. 基础统计字段
 * 2. 外卖相关统计字段
 * 3. 成本支出字段
 * 4. 利润与毛利率字段
 * 5. 盈亏状态字段
 */
@Data
public class GbAiDailyRevenueStatsDTO {
    
    // ==================== 基础统计字段 ====================
    
    /**
     * 统计天数
     * 单位：天
     * 说明：统计期间的总天数
     */
    private Integer days;
    
    /**
     * 日均营业额
     * 单位：元
     * 说明：统计期间内每日的平均营业额
     */
    private BigDecimal avgDailyRevenue;
    
    /**
     * 总营业额
     * 单位：元
     * 说明：统计期间内的营业额总和
     */
    private BigDecimal totalRevenue;
    
    /**
     * 日均订单数
     * 单位：单/天
     * 说明：统计期间内每日的平均订单数量
     */
    private BigDecimal avgOrderCount;
    
    /**
     * 客单价（人均消费）
     * 单位：元/人
     * 说明：平均每位顾客的消费金额
     */
    private BigDecimal avgPerCustomer;
    
    /**
     * 总优惠券金额
     * 单位：元
     * 说明：统计期间内使用的优惠券总金额
     */
    private BigDecimal totalCouponAmount;
    
    /**
     * 总退款金额
     * 单位：元
     * 说明：统计期间内的退款总金额
     */
    private BigDecimal totalRefundAmount;
    
    /**
     * 最高日营业额
     * 单位：元
     * 说明：统计期间内最高的单日营业额
     */
    private BigDecimal maxDailyRevenue;
    
    /**
     * 最低日营业额
     * 单位：元
     * 说明：统计期间内最低的单日营业额
     */
    private BigDecimal minDailyRevenue;
    
    // ==================== 固定开支字段 ====================
    
    /**
     * 日均固定成本
     * 单位：元/天
     * 说明：每日的固定成本（工资+租金）
     */
    private BigDecimal avgFixedCost;
    
    /**
     * 月工资总额
     * 单位：元/月
     * 说明：餐厅每月的工资总额
     */
    private BigDecimal monthlyWage;
    
    /**
     * 月租金
     * 单位：元/月
     * 说明：餐厅每月的租金
     */
    private BigDecimal monthlyRent;
    
    /**
     * 日均净收入
     * 单位：元/天
     * 说明：日均营业额扣除优惠券后的净收入
     */
    private BigDecimal avgNetRevenue;
    
    // ==================== 外卖相关统计字段 ====================
    
    /**
     * 总外卖营业额
     * 单位：元
     * 说明：统计期间内外卖的总营业额
     */
    private BigDecimal totalTakeoutRevenue;
    
    /**
     * 日均外卖营业额
     * 单位：元/天
     * 说明：统计期间内每日的平均外卖营业额
     */
    private BigDecimal avgTakeoutRevenue;
    
    /**
     * 总外卖净收入
     * 单位：元
     * 说明：外卖营业额扣除平台抽成后的净收入
     * 计算公式：总外卖营业额 - 总平台抽成
     */
    private BigDecimal totalTakeoutNet;
    
    /**
     * 日均外卖净收入
     * 单位：元/天
     * 说明：每日的外卖营业额扣除平台抽成后的净收入
     */
    private BigDecimal avgTakeoutNet;
    
    // ==================== 成本支出字段 ====================
    
    /**
     * 生产成本
     * 单位：元
     * 说明：原材料采购、加工等生产环节的成本（type=1）
     */
    private BigDecimal produceCost;
    
    /**
     * 损耗成本
     * 单位：元
     * 说明：原材料过期、变质等损耗成本（type=2）
     */
    private BigDecimal wasteCost;
    
    /**
     * 损失成本
     * 单位：元
     * 说明：原材料丢失、损坏等损失成本（type=3）
     */
    private BigDecimal lossCost;
    
    /**
     * 退货成本
     * 单位：元
     * 说明：原材料退货产生的成本（type=4）
     */
    private BigDecimal returnCost;
    
    /**
     * 制作成本
     * 单位：元
     * 说明：制作产品的总成本 = 生产成本 + 损耗成本 + 损失成本
     */
    private BigDecimal productionCost;
    
    /**
     * 总成本
     * 单位：元
     * 说明：全部成本 = 制作成本 + 退货成本
     */
    private BigDecimal totalCost;
    
    // ==================== 利润与毛利率字段 ====================
    
    /**
     * 毛利率
     * 单位：百分比（0-100）
     * 说明：毛利率数值
     * 计算公式：(净收入 - 总成本) / 净收入 × 100%
     */
    private BigDecimal grossProfitMargin;
    
    /**
     * 毛利率（百分比格式）
     * 单位：字符串格式的百分比
     * 示例："25.50%"
     */
    private String grossProfitMarginPercent;
    
    /**
     * 盈亏平衡点
     * 单位：元/天
     * 说明：每日需要达到的最低营业额来覆盖固定成本
     * 等于日均固定成本
     */
    private BigDecimal breakEvenPoint;
    
    // ==================== 利润字段 ====================
    
    /**
     * 原有利润（不考虑成本）
     * 单位：元/天
     * 说明：不考虑成本时的日均利润
     * 计算公式：日均净收入 - 日均固定成本
     */
    private BigDecimal profitAmount;
    
    /**
     * 考虑成本后的利润
     * 单位：元/天
     * 说明：考虑所有成本后的实际日均利润
     * 计算公式：日均净收入 - (总成本/天数) - 日均固定成本
     */
    private BigDecimal profitAfterCost;
    
    /**
     * 实际利润
     * 单位：元/天
     * 说明：实际利润，与 profitAfterCost 相同
     */
    private BigDecimal actualProfit;
    
    // ==================== 盈亏状态字段 ====================
    
    /**
     * 盈亏状态代码
     * 可能值：profit（盈利）、breakeven（保本）、loss（亏损）
     */
    private String status;
    
    /**
     * 盈亏状态描述
     * 可能值：盈利中、保本、亏损
     */
    private String statusDesc;
    
}