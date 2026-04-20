package com.nongxinle.service.impl;

import com.nongxinle.entity.GbAiRestaurantProfileEntity;
import com.nongxinle.service.GbAiDailyRevenueDashboardService;
import com.nongxinle.service.GbDepartmentGoodsStockReduceService;
import com.nongxinle.utils.GbDateTimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GbAiDailyRevenueDashboardServiceImpl implements GbAiDailyRevenueDashboardService {

    private final GbDepartmentGoodsStockReduceService stockReduceService;

    @Override
    public Map<String, Object> buildStatsDashboard(Long departmentId, GbAiRestaurantProfileEntity profile, Map<String, Object> stats) {
        int days = ((Number) stats.get("days")).intValue();
        Map<String, Object> result = new HashMap<>();

        result.put("统计天数", days);
        BigDecimal avgDailyRevenue = toDecimal(stats.get("avg_daily_revenue"));
        result.put("日均营业额", formatStatNumber(avgDailyRevenue));
        result.put("总营业额", formatStatNumber(toDecimal(stats.get("total_revenue"))));
        result.put("日均订单数", formatStatNumber(toDecimal(stats.get("avg_order_count"))));
        result.put("客单价", formatStatNumber(toDecimal(stats.get("avg_per_customer"))));
        result.put("平台费合计", formatStatNumber(toDecimal(stats.get("total_coupon_amount"))));
        result.put("退款合计", formatStatNumber(toDecimal(stats.get("total_refund_amount"))));
        result.put("最高日营业额", formatStatNumber(toDecimal(stats.get("max_daily_revenue"))));
        result.put("最低日营业额", formatStatNumber(toDecimal(stats.get("min_daily_revenue"))));

        BigDecimal monthlyWage = profile.getGbAiRestaurantProfileMonthlyWage() != null
                ? profile.getGbAiRestaurantProfileMonthlyWage() : BigDecimal.ZERO;
        BigDecimal monthlyRent = profile.getGbAiRestaurantProfileRentMonthly() != null
                ? profile.getGbAiRestaurantProfileRentMonthly() : BigDecimal.ZERO;
        BigDecimal monthlyFixedCost = monthlyWage.add(monthlyRent);
        BigDecimal dailyFixedCost = monthlyFixedCost.divide(BigDecimal.valueOf(30), 2, RoundingMode.HALF_UP);
        BigDecimal dailyWage = monthlyWage.divide(BigDecimal.valueOf(30), 2, RoundingMode.HALF_UP);
        BigDecimal dailyRent = monthlyRent.divide(BigDecimal.valueOf(30), 2, RoundingMode.HALF_UP);

        result.put("日均固定开支", formatStatNumber(dailyFixedCost));
        result.put("月工资", formatStatNumber(monthlyWage));
        result.put("月租金", formatStatNumber(monthlyRent));

        BigDecimal totalCoupon = toDecimal(stats.get("total_coupon_amount"));
        BigDecimal avgNetRevenue = avgDailyRevenue.subtract(totalCoupon.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP));
        result.put("日均净收入", formatStatNumber(avgNetRevenue));

        result.put("外卖营业额合计", formatStatNumber(toDecimal(stats.get("total_takeout_revenue"))));
        BigDecimal avgTakeoutRevenue = toDecimal(stats.get("avg_takeout_revenue"));
        result.put("日均外卖营业额", formatStatNumber(avgTakeoutRevenue));
        result.put("外卖净收合计", formatStatNumber(toDecimal(stats.get("total_takeout_net"))));
        result.put("日均外卖净收", formatStatNumber(toDecimal(stats.get("avg_takeout_net"))));

        Map<String, Object> costParams = new HashMap<>();
        costParams.put("departmentFatherId", departmentId);
        Map<String, Object> costStats = this.stockReduceService.queryReduceAllTypesTotal(costParams);

        BigDecimal produceCost = toDecimal(costStats.get("produceTotal"));
        BigDecimal wasteCost = toDecimal(costStats.get("wasteTotal"));
        BigDecimal lossCost = toDecimal(costStats.get("lossTotal"));
        BigDecimal returnCost = toDecimal(costStats.get("returnTotal"));
        BigDecimal productionCost = produceCost.add(wasteCost).add(lossCost);
        BigDecimal totalCost = productionCost.add(returnCost);
        // 部门库存核销：制作(1)+损耗(2)+废弃/损失(3)，不含退货(4)；按营业额统计天数摊日均
        BigDecimal avgDepartmentReduceDaily = productionCost.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
        BigDecimal expenseDailyTotal = dailyWage.add(dailyRent).add(avgDepartmentReduceDaily);

        result.put("生产成本", formatStatNumber(produceCost));
        result.put("损耗成本", formatStatNumber(wasteCost));
        result.put("损失成本", formatStatNumber(lossCost));
        result.put("退货成本", formatStatNumber(returnCost));
        result.put("制作成本合计", formatStatNumber(productionCost));
        result.put("部门核销制作损耗废弃日均", formatStatNumber(avgDepartmentReduceDaily));
        result.put("日均支出合计", formatStatNumber(expenseDailyTotal));
        result.put("总成本", formatStatNumber(totalCost));

        BigDecimal grossProfitMargin = BigDecimal.ZERO;
        BigDecimal totalNetRevenue = toDecimal(stats.get("total_revenue")).subtract(totalCoupon);
        if (totalNetRevenue.compareTo(BigDecimal.ZERO) > 0) {
            grossProfitMargin = totalNetRevenue.subtract(totalCost)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(totalNetRevenue, 2, RoundingMode.HALF_UP);
        }
        result.put("利润率", formatStatNumber(grossProfitMargin));
        result.put("利润率说明", ((BigDecimal) formatStatNumber(grossProfitMargin)).toPlainString() + "%");

        result.put("参考日均固定开支", formatStatNumber(dailyFixedCost));

        BigDecimal avgDailyStockCost = totalCost.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
        BigDecimal profitAfterCost = avgNetRevenue.subtract(avgDailyStockCost).subtract(dailyFixedCost);
        BigDecimal profit = avgNetRevenue.subtract(dailyFixedCost);
        String status;
        String statusDesc;
        BigDecimal actualProfit;
        if (profitAfterCost.compareTo(BigDecimal.ZERO) > 0) {
            status = "profit";
            statusDesc = "盈利中";
            actualProfit = profitAfterCost;
        } else if (profitAfterCost.compareTo(BigDecimal.ZERO) == 0) {
            status = "breakeven";
            statusDesc = "保本";
            actualProfit = profitAfterCost;
        } else {
            status = "loss";
            statusDesc = "亏损";
            actualProfit = profitAfterCost;
        }
        result.put("盈亏状态码", status);
        result.put("盈亏状态", statusDesc);
        result.put("日均利润未扣库存", formatStatNumber(profit));
        result.put("日均利润含库存成本", formatStatNumber(profitAfterCost));
        result.put("实际日均利润", formatStatNumber(actualProfit));

        BigDecimal totalPlatformFee = stats.get("total_platform_fee") != null
                ? toDecimal(stats.get("total_platform_fee")) : totalCoupon;
        BigDecimal avgPlatformFee = totalPlatformFee.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
        BigDecimal avgDineInRevenue = stats.get("avg_dine_in_revenue") != null
                ? toDecimal(stats.get("avg_dine_in_revenue")) : BigDecimal.ZERO;
        BigDecimal dineTakeSum = avgDineInRevenue.add(avgTakeoutRevenue);
        BigDecimal dineInRatio = BigDecimal.ZERO;
        if (dineTakeSum.compareTo(BigDecimal.ZERO) > 0) {
            dineInRatio = avgDineInRevenue.multiply(BigDecimal.valueOf(100))
                    .divide(dineTakeSum, 2, RoundingMode.HALF_UP);
        }

        boolean isProfit = profitAfterCost.compareTo(BigDecimal.ZERO) > 0;
        String statusClass = "breakeven";
        String statusLabel = "收支相抵";
        if (profitAfterCost.compareTo(BigDecimal.ZERO) > 0) {
            statusClass = "profit";
            statusLabel = "盈余主导";
        } else if (profitAfterCost.compareTo(BigDecimal.ZERO) < 0) {
            statusClass = "loss";
            statusLabel = "支出偏重";
        }

        BigDecimal dailyTotalExpense = dailyFixedCost.add(avgDailyStockCost).add(avgPlatformFee);
        BigDecimal riskMultiple = BigDecimal.ZERO;
        if (dailyTotalExpense.compareTo(BigDecimal.ZERO) > 0) {
            riskMultiple = profitAfterCost.abs().divide(dailyTotalExpense, 2, RoundingMode.HALF_UP);
        }

        BigDecimal healthPercent = grossProfitMargin.max(BigDecimal.ZERO).min(BigDecimal.valueOf(100));
        String healthColor = healthColorForMargin(grossProfitMargin);
        Map<String, String> safety = safetyForMargin(grossProfitMargin);

        LocalDate today = GbDateTimeUtils.todayChina();
        int currentMonth = today.getMonthValue();
        int monthDays = today.lengthOfMonth();
        int daysPassed = today.getDayOfMonth();
        BigDecimal monthProgressBd = BigDecimal.valueOf(daysPassed * 100.0 / monthDays).setScale(1, RoundingMode.HALF_UP);
        BigDecimal estimatedMonthProfit = profitAfterCost.multiply(BigDecimal.valueOf(monthDays));
        BigDecimal absEstimatedProfit = estimatedMonthProfit.abs();

        Map<String, Object> dashboard = new LinkedHashMap<>();

        Map<String, Object> scaleBeam = new LinkedHashMap<>();
        Map<String, Object> incomePanel = new LinkedHashMap<>();
        incomePanel.put("sectionKey", "scale_income");
        incomePanel.put("title", "收入端");
        incomePanel.put("rows", Arrays.asList(
                labeledRow("日均营业额", "日均营业额（毛收）", avgDailyRevenue),
                labeledRow("堂食日均", "堂食（日均）", avgDineInRevenue),
                labeledRow("外卖日均营业额", "外卖（日均营业额）", avgTakeoutRevenue)
        ));
        Map<String, Object> dineRatioBar = new LinkedHashMap<>();
        dineRatioBar.put("label", "堂食占堂食+外卖比例");
        dineRatioBar.put("percent", formatStatNumber(dineInRatio));
        incomePanel.put("ratioBar", dineRatioBar);
        scaleBeam.put("income", incomePanel);

        Map<String, Object> expensePanel = new LinkedHashMap<>();
        expensePanel.put("sectionKey", "scale_expense");
        expensePanel.put("title", "支出端");
        expensePanel.put("summary", labeledRow("日均支出合计", "日均支出合计（工资+租金+部门核销日均）", expenseDailyTotal));
        expensePanel.put("rows", Arrays.asList(
                labeledRow("工资日均", "工资（日均）", dailyWage),
                labeledRow("租金日均", "租金（日均）", dailyRent),
                labeledRow("支出日均", "支出(日均)", avgDepartmentReduceDaily)
        ));
        scaleBeam.put("expense", expensePanel);

        Map<String, Object> pointer = new LinkedHashMap<>();
        pointer.put("statusKey", status);
        pointer.put("statusClass", statusClass);
        pointer.put("statusLabel", statusLabel);
        pointer.put("statusDesc", statusDesc);
        scaleBeam.put("pointer", pointer);
        dashboard.put("scaleBeam", scaleBeam);

        Map<String, Object> scaleBase = new LinkedHashMap<>();
        Map<String, Object> dateHeader = new LinkedHashMap<>();
        dateHeader.put("dateStr", GbDateTimeUtils.formatDay(today));
        dateHeader.put("weekdayStr", GbDateTimeUtils.chineseWeekdayShort(today));
        dateHeader.put("badge", "月度预测");
        dateHeader.put("currentMonth", currentMonth);
        scaleBase.put("dateHeader", dateHeader);

        Map<String, Object> healthCard = new LinkedHashMap<>();
        healthCard.put("sectionKey", "health");
        healthCard.put("title", "经营健康度");
        healthCard.put("rows", Arrays.asList(
                labeledRow("利润率", "利润率（(总营业额-平台费-总库存成本)/总营业额）", grossProfitMargin)
        ));
        Map<String, Object> safetyMap = new LinkedHashMap<>();
        safetyMap.put("level", safety.get("level"));
        safetyMap.put("text", safety.get("text"));
        safetyMap.put("desc", safety.get("desc"));
        healthCard.put("safety", safetyMap);
        Map<String, Object> riskRow = new LinkedHashMap<>();
        riskRow.put("label", "抗风险倍数");
        riskRow.put("hint", isProfit ? "相对日均总支出的盈利倍数" : "相对日均总支出的亏损倍数");
        riskRow.put("value", formatStatNumber(riskMultiple));
        riskRow.put("unit", "x");
        riskRow.put("isProfit", isProfit);
        healthCard.put("riskMultiple", riskRow);
        scaleBase.put("healthCard", healthCard);

        Map<String, Object> forecast = new LinkedHashMap<>();
        forecast.put("sectionKey", "month_forecast");
        forecast.put("title", currentMonth + "月经营预测（按当前日均推算整月）");
        forecast.put("rows", Arrays.asList(
                labeledRow("本月已过进度", "本月已过进度（%）", monthProgressBd),
                labeledRow("已过天数", "已过天数", daysPassed),
                labeledRow("本月天数", "本月天数", monthDays),
                labeledRow("预计本月盈亏", "预计本月盈亏（日均×本月天数）", estimatedMonthProfit),
                labeledRow("预计盈亏绝对值", "预计盈亏绝对值", absEstimatedProfit)
        ));
        forecast.put("isProfit", isProfit);
        forecast.put("hasIndustryCompare", false);
        forecast.put("vsIndustryPercent", null);
        forecast.put("vsIndustryHint", "暂无行业对标数据");
        scaleBase.put("monthForecast", forecast);
        dashboard.put("scaleBase", scaleBase);

        Map<String, Object> coreMetrics = new LinkedHashMap<>();
        coreMetrics.put("sectionKey", "core_metrics");
        coreMetrics.put("title", "核心指标");
        Integer staffCount = profile.getGbAiRestaurantProfileStaffCount();
        Integer seatCount = profile.getGbAiRestaurantProfileSeatCount();
        Integer competitorCount = profile.getGbAiRestaurantProfileNearbyCompetitorCount();
        BigDecimal avgPrice = profile.getGbAiRestaurantProfileAvgPrice() != null
                ? profile.getGbAiRestaurantProfileAvgPrice() : BigDecimal.ZERO;
        coreMetrics.put("rows", Arrays.asList(
                labeledRow("员工人数", "员工人数", staffCount != null ? staffCount : 0),
                labeledRow("座位数", "座位数", seatCount != null ? seatCount : 0),
                labeledRow("附近竞争对手数", "附近竞争对手数", competitorCount != null ? competitorCount : 0),
                labeledRow("画像客单价", "画像客单价", avgPrice)
        ));
        dashboard.put("coreMetrics", coreMetrics);

        Map<String, Object> analysis = new LinkedHashMap<>();
        analysis.put("sectionKey", "operation_analysis");
        analysis.put("title", "经营分析");
        analysis.put("rows", Arrays.asList(
                labeledRow("统计天数", "统计天数", days),
                labeledRow("总营业额", "总营业额", toDecimal(stats.get("total_revenue"))),
                labeledRow("日均净收入", "日均净收入（日均毛收-日均平台费）", avgNetRevenue),
                labeledRow("参考日均固定开支", "参考日均固定开支", dailyFixedCost),
                labeledRow("每日盈亏", "每日盈亏（净收-日均库存成本-固定）", profitAfterCost),
                labeledRow("利润率", "利润率", grossProfitMargin),
                labeledRow("状态", "状态", statusDesc)
        ));
        dashboard.put("operationAnalysis", analysis);

        Map<String, Object> costBreakdown = new LinkedHashMap<>();
        costBreakdown.put("sectionKey", "cost_breakdown");
        costBreakdown.put("title", "成本明细");
        Map<String, Object> makeSection = new LinkedHashMap<>();
        makeSection.put("title", "制作成本");
        makeSection.put("rows", Arrays.asList(
                labeledRow("生产成本", "生产成本", produceCost),
                labeledRow("损耗成本", "损耗成本", wasteCost),
                labeledRow("损失成本", "损失成本", lossCost),
                labeledRow("制作成本合计", "制作成本合计", productionCost)
        ));
        costBreakdown.put("production", makeSection);
        Map<String, Object> otherSection = new LinkedHashMap<>();
        otherSection.put("title", "其他成本");
        otherSection.put("rows", Arrays.asList(labeledRow("退货成本", "退货成本", returnCost)));
        costBreakdown.put("other", otherSection);
        costBreakdown.put("total", labeledRow("总成本", "总成本", totalCost));
        dashboard.put("costBreakdown", costBreakdown);

        Map<String, Object> bindings = new LinkedHashMap<>();
        bindings.put("avgDailyRevenue", formatStatNumber(avgDailyRevenue));
        bindings.put("avgDineInRevenue", formatStatNumber(avgDineInRevenue));
        bindings.put("avgTakeoutRevenue", formatStatNumber(avgTakeoutRevenue));
        bindings.put("dineInRatio", formatStatNumber(dineInRatio));
        bindings.put("avgFixedCost", formatStatNumber(expenseDailyTotal));
        bindings.put("dailyWage", formatStatNumber(dailyWage));
        bindings.put("dailyRent", formatStatNumber(dailyRent));
        bindings.put("avgPlatformFee", formatStatNumber(avgPlatformFee));
        bindings.put("avgDepartmentReduceDaily", formatStatNumber(avgDepartmentReduceDaily));
        bindings.put("dateStr", dateHeader.get("dateStr"));
        bindings.put("weekdayStr", dateHeader.get("weekdayStr"));
        bindings.put("currentMonth", currentMonth);
        bindings.put("monthProgress", formatStatNumber(monthProgressBd));
        bindings.put("daysPassed", daysPassed);
        bindings.put("monthDays", monthDays);
        bindings.put("profitMargin", formatStatNumber(grossProfitMargin));
        bindings.put("healthPercent", formatStatNumber(healthPercent));
        bindings.put("healthColor", healthColor);
        bindings.put("safetyLevel", safety.get("level"));
        bindings.put("safetyText", safety.get("text"));
        bindings.put("safetyDesc", safety.get("desc"));
        bindings.put("isProfit", isProfit);
        bindings.put("riskMultiple", formatStatNumber(riskMultiple));
        bindings.put("absEstimatedProfit", formatStatNumber(absEstimatedProfit));
        bindings.put("vsIndustryPercent", null);
        bindings.put("staffCount", staffCount != null ? staffCount : 0);
        bindings.put("seatCount", seatCount != null ? seatCount : 0);
        bindings.put("competitorCount", competitorCount != null ? competitorCount : 0);
        bindings.put("avgPrice", formatStatNumber(avgPrice));
        bindings.put("days", days);
        bindings.put("totalRevenue", formatStatNumber(toDecimal(stats.get("total_revenue"))));
        bindings.put("dailyNetRevenue", formatStatNumber(avgNetRevenue));
        bindings.put("breakEvenPoint", formatStatNumber(dailyFixedCost));
        bindings.put("dailyProfit", formatStatNumber(profitAfterCost));
        bindings.put("produceCost", formatStatNumber(produceCost));
        bindings.put("wasteCost", formatStatNumber(wasteCost));
        bindings.put("lossCost", formatStatNumber(lossCost));
        bindings.put("productionCost", formatStatNumber(productionCost));
        bindings.put("returnCost", formatStatNumber(returnCost));
        bindings.put("totalCost", formatStatNumber(totalCost));
        bindings.put("statusClass", statusClass);
        bindings.put("statusLabel", statusLabel);
        bindings.put("statusText", statusDesc);
        dashboard.put("bindings", bindings);

        Map<String, Object> data = new HashMap<>();
        data.put("dashboard", dashboard);
        data.put("stats", result);
        data.put("profile", profile);

        return data;
    }

    private static BigDecimal toDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.toString());
    }

    /**
     * 统计接口展示用：有小数则保留 1 位（四舍五入），整数不带小数位。
     */
    private static Object formatStatNumber(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof String) {
            return value;
        }
        if (value instanceof Integer || value instanceof Long) {
            return value;
        }
        if (value instanceof BigDecimal bd) {
            return bd.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros();
        }
        if (value instanceof Number n) {
            return new BigDecimal(n.toString()).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros();
        }
        return value;
    }

    private static Map<String, Object> labeledRow(String key, String label, Object value) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("key", key);
        row.put("label", label);
        row.put("value", formatStatNumber(value));
        return row;
    }

    private static String healthColorForMargin(BigDecimal margin) {
        if (margin.compareTo(BigDecimal.ZERO) < 0) {
            return "#ef4444";
        }
        if (margin.compareTo(new BigDecimal("20")) >= 0) {
            return "#22c55e";
        }
        if (margin.compareTo(new BigDecimal("8")) >= 0) {
            return "#eab308";
        }
        return "#f97316";
    }

    private static Map<String, String> safetyForMargin(BigDecimal margin) {
        Map<String, String> m = new LinkedHashMap<>();
        if (margin.compareTo(new BigDecimal("15")) >= 0) {
            m.put("level", "high");
            m.put("text", "稳健");
            m.put("desc", "利润率处于较好区间");
        } else if (margin.compareTo(new BigDecimal("5")) >= 0) {
            m.put("level", "mid");
            m.put("text", "一般");
            m.put("desc", "有一定压力，关注成本与客流");
        } else if (margin.compareTo(BigDecimal.ZERO) >= 0) {
            m.put("level", "low");
            m.put("text", "偏紧");
            m.put("desc", "利润空间有限");
        } else {
            m.put("level", "danger");
            m.put("text", "预警");
            m.put("desc", "当前统计周期为亏损状态");
        }
        return m;
    }
}
