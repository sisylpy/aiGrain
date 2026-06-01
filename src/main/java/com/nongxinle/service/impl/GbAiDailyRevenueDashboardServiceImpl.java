package com.nongxinle.service.impl;

import com.nongxinle.entity.GbAiRestaurantProfileEntity;
import com.nongxinle.service.GbAiDailyRevenueDashboardService;
import com.nongxinle.service.GbDepartmentGoodsStockReduceService;
import com.nongxinle.utils.GbDateTimeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GbAiDailyRevenueDashboardServiceImpl implements GbAiDailyRevenueDashboardService {

    private final GbDepartmentGoodsStockReduceService stockReduceService;


    public Map<String, Object> buildScaleDashboard(Long departmentFatherId, GbAiRestaurantProfileEntity profile,
                                                   Map<String, Object> stats, String startDate, String endDate) {
        String qStart = (startDate != null && !startDate.isBlank()) ? startDate.trim() : null;
        String qEnd = (endDate != null && !endDate.isBlank()) ? endDate.trim() : null;

        int days = ((Number) stats.get("days")).intValue();
        Map<String, Object> result = new HashMap<>();

        result.put("统计天数", days);
        if (qStart != null) {
            result.put("统计开始日期", qStart);
        }
        if (qEnd != null) {
            result.put("统计结束日期", qEnd);
        }
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
        costParams.put("departmentFatherId", departmentFatherId);
        costParams.put("matchDailyRevenueDepartmentId", departmentFatherId);
        if (qStart != null) {
            costParams.put("startDate", qStart);
        }
        if (qEnd != null) {
            costParams.put("stopDate", qEnd);
        }
        Map<String, Object> costStats = this.stockReduceService.queryReduceAllTypesTotalOnDailyRevenueDays(costParams);

        BigDecimal produceCost = toDecimal(costStats.get("produceTotal"));
        BigDecimal wasteCost = toDecimal(costStats.get("wasteTotal"));
        BigDecimal lossCost = toDecimal(costStats.get("lossTotal"));
        BigDecimal returnCost = toDecimal(costStats.get("returnTotal"));
        BigDecimal productionCost = produceCost.add(wasteCost).add(lossCost);
        BigDecimal totalCost = productionCost.add(returnCost);
        BigDecimal wasteLossCost = wasteCost.add(lossCost);

        BigDecimal produceShareOfReduce = BigDecimal.ZERO;
        BigDecimal wasteLossShareOfReduce = BigDecimal.ZERO;
        if (productionCost.compareTo(BigDecimal.ZERO) > 0) {
            produceShareOfReduce = produceCost.multiply(BigDecimal.valueOf(100))
                    .divide(productionCost, 2, RoundingMode.HALF_UP);
            wasteLossShareOfReduce = wasteLossCost.multiply(BigDecimal.valueOf(100))
                    .divide(productionCost, 2, RoundingMode.HALF_UP);
        }

        BigDecimal avgProduceDaily = produceCost.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
        BigDecimal avgWasteLossDaily = wasteLossCost.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
        BigDecimal avgDepartmentReduceDaily = productionCost.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
        // 天平支出端合计：仅工资+租金+生产核销(type=1)；损耗/废弃(2+3)另行列示不计入本合计
        BigDecimal expenseDailyTotal = dailyWage.add(dailyRent).add(avgProduceDaily);

        result.put("生产成本", formatStatNumber(produceCost));
        result.put("损耗成本", formatStatNumber(wasteCost));
        result.put("损失成本", formatStatNumber(lossCost));
        result.put("损耗废弃合计", formatStatNumber(wasteLossCost));
        result.put("退货成本", formatStatNumber(returnCost));
        result.put("制作成本合计", formatStatNumber(productionCost));
        result.put("制作占制作损耗废弃合计比例", formatStatNumber(produceShareOfReduce));
        result.put("损耗废弃占制作损耗废弃合计比例", formatStatNumber(wasteLossShareOfReduce));
        result.put("生产核销日均", formatStatNumber(avgProduceDaily));
        result.put("损耗废弃核销日均", formatStatNumber(avgWasteLossDaily));
        result.put("部门核销制作损耗废弃日均", formatStatNumber(avgDepartmentReduceDaily));
        result.put("日均支出合计", formatStatNumber(expenseDailyTotal));
        result.put("总成本", formatStatNumber(totalCost));

        BigDecimal grossProfitMargin = BigDecimal.ZERO;
        BigDecimal totalNetRevenue = toDecimal(stats.get("total_revenue")).subtract(totalCoupon);
        if (totalNetRevenue.compareTo(BigDecimal.ZERO) > 0) {
            grossProfitMargin = totalNetRevenue.subtract(produceCost)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(totalNetRevenue, 2, RoundingMode.HALF_UP);
        }
        result.put("利润率", formatStatNumber(grossProfitMargin));
        result.put("利润率说明",
                grossProfitMargin.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "%");

        result.put("参考日均固定开支", formatStatNumber(dailyFixedCost));

        BigDecimal avgDailyStockCost = produceCost.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
        BigDecimal profitAfterCost = avgNetRevenue.subtract(avgDailyStockCost).subtract(dailyFixedCost);
        BigDecimal profit = avgNetRevenue.subtract(dailyFixedCost);

        BigDecimal operatingNetMarginPct = BigDecimal.ZERO;
        if (avgNetRevenue.compareTo(BigDecimal.ZERO) > 0) {
            operatingNetMarginPct = profitAfterCost.multiply(BigDecimal.valueOf(100))
                    .divide(avgNetRevenue, 2, RoundingMode.HALF_UP);
        }
        result.put("经营净利率", formatStatNumber(operatingNetMarginPct));
        result.put("经营净利率说明", "（日均净收−生产核销日均−日工资−日租金）/日均净收，画像月工资/月租按÷30摊到日");

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


        Map<String, String> safety = safetyForOperatingNetMargin(operatingNetMarginPct);

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
        expensePanel.put("summary", labeledRow("日均支出合计", "日均支出合计（工资+租金+生产核销日均 type=1）", expenseDailyTotal));
        expensePanel.put("rows", Arrays.asList(
                labeledRow("工资日均", "工资（日均）", dailyWage),
                labeledRow("租金日均", "租金（日均）", dailyRent),
                labeledRow("生产核销日均", "生产核销日均（type=1，本期合计/统计天数）", avgProduceDaily),
                labeledRow("损耗废弃日均", "损耗废弃日均（type=2+3，另计未计入上方合计）", avgWasteLossDaily)
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


        dashboard.put("scaleBase", scaleBase);

        return dashboard;
    }



    @Override
    public Map<String, Object> buildStatsDashboard(Long departmentFatherId, GbAiRestaurantProfileEntity profile,
                                                   Map<String, Object> stats, String startDate, String endDate) {
        String qStart = (startDate != null && !startDate.isBlank()) ? startDate.trim() : null;
        String qEnd = (endDate != null && !endDate.isBlank()) ? endDate.trim() : null;

        int days = ((Number) stats.get("days")).intValue();
        Map<String, Object> result = new HashMap<>();

        result.put("统计天数", days);
        if (qStart != null) {
            result.put("统计开始日期", qStart);
        }
        if (qEnd != null) {
            result.put("统计结束日期", qEnd);
        }
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
        costParams.put("departmentFatherId", departmentFatherId);
        costParams.put("matchDailyRevenueDepartmentId", departmentFatherId);
        if (qStart != null) {
            costParams.put("startDate", qStart);
        }
        if (qEnd != null) {
            costParams.put("stopDate", qEnd);
        }
        Map<String, Object> costStats = this.stockReduceService.queryReduceAllTypesTotalOnDailyRevenueDays(costParams);

        BigDecimal produceCost = toDecimal(costStats.get("produceTotal"));
        BigDecimal wasteCost = toDecimal(costStats.get("wasteTotal"));
        BigDecimal lossCost = toDecimal(costStats.get("lossTotal"));
        BigDecimal returnCost = toDecimal(costStats.get("returnTotal"));
        BigDecimal productionCost = produceCost.add(wasteCost).add(lossCost);
        BigDecimal totalCost = productionCost.add(returnCost);
        BigDecimal wasteLossCost = wasteCost.add(lossCost);

        BigDecimal produceShareOfReduce = BigDecimal.ZERO;
        BigDecimal wasteLossShareOfReduce = BigDecimal.ZERO;
        if (productionCost.compareTo(BigDecimal.ZERO) > 0) {
            produceShareOfReduce = produceCost.multiply(BigDecimal.valueOf(100))
                    .divide(productionCost, 2, RoundingMode.HALF_UP);
            wasteLossShareOfReduce = wasteLossCost.multiply(BigDecimal.valueOf(100))
                    .divide(productionCost, 2, RoundingMode.HALF_UP);
        }

        BigDecimal avgProduceDaily = produceCost.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
        BigDecimal avgWasteLossDaily = wasteLossCost.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
        BigDecimal avgDepartmentReduceDaily = productionCost.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
        // 天平支出端合计：仅工资+租金+生产核销(type=1)；损耗/废弃(2+3)另行列示不计入本合计
        BigDecimal expenseDailyTotal = dailyWage.add(dailyRent).add(avgProduceDaily);

        result.put("生产成本", formatStatNumber(produceCost));
        result.put("损耗成本", formatStatNumber(wasteCost));
        result.put("损失成本", formatStatNumber(lossCost));
        result.put("损耗废弃合计", formatStatNumber(wasteLossCost));
        result.put("退货成本", formatStatNumber(returnCost));
        result.put("制作成本合计", formatStatNumber(productionCost));
        result.put("制作占制作损耗废弃合计比例", formatStatNumber(produceShareOfReduce));
        result.put("损耗废弃占制作损耗废弃合计比例", formatStatNumber(wasteLossShareOfReduce));
        result.put("生产核销日均", formatStatNumber(avgProduceDaily));
        result.put("损耗废弃核销日均", formatStatNumber(avgWasteLossDaily));
        result.put("部门核销制作损耗废弃日均", formatStatNumber(avgDepartmentReduceDaily));
        result.put("日均支出合计", formatStatNumber(expenseDailyTotal));
        result.put("总成本", formatStatNumber(totalCost));

        BigDecimal grossProfitMargin = BigDecimal.ZERO;
        BigDecimal totalNetRevenue = toDecimal(stats.get("total_revenue")).subtract(totalCoupon);
        if (totalNetRevenue.compareTo(BigDecimal.ZERO) > 0) {
            grossProfitMargin = totalNetRevenue.subtract(produceCost)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(totalNetRevenue, 2, RoundingMode.HALF_UP);
        }
        result.put("利润率", formatStatNumber(grossProfitMargin));
        result.put("利润率说明",
                grossProfitMargin.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "%");

        result.put("参考日均固定开支", formatStatNumber(dailyFixedCost));

        BigDecimal avgDailyStockCost = produceCost.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
        BigDecimal profitAfterCost = avgNetRevenue.subtract(avgDailyStockCost).subtract(dailyFixedCost);
        BigDecimal profit = avgNetRevenue.subtract(dailyFixedCost);

        BigDecimal operatingNetMarginPct = BigDecimal.ZERO;
        if (avgNetRevenue.compareTo(BigDecimal.ZERO) > 0) {
            operatingNetMarginPct = profitAfterCost.multiply(BigDecimal.valueOf(100))
                    .divide(avgNetRevenue, 2, RoundingMode.HALF_UP);
        }
        result.put("经营净利率", formatStatNumber(operatingNetMarginPct));
        result.put("经营净利率说明", "（日均净收−生产核销日均−日工资−日租金）/日均净收，画像月工资/月租按÷30摊到日");

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

        if (log.isInfoEnabled()) {
            BigDecimal totalGross = toDecimal(stats.get("total_revenue"));
            BigDecimal shareSum = produceShareOfReduce.add(wasteLossShareOfReduce);
            log.info("[daily-revenue-dashboard] departmentFatherId={} queryRange={}..{} statsDays={} (mapper COUNT rows)",
                    departmentFatherId,
                    qStart != null ? qStart : "—",
                    qEnd != null ? qEnd : "—",
                    days);
            log.info("[daily-revenue-dashboard] income: totalGross={} totalPlatformFee={} totalNet(totalGross-fee)={} "
                            + "avgGrossPerDay={} avgNetPerDay(avgGross-fee/days)={}",
                    totalGross.toPlainString(),
                    totalCoupon.toPlainString(),
                    totalNetRevenue.toPlainString(),
                    avgDailyRevenue.toPlainString(),
                    avgNetRevenue.toPlainString());
            log.info("[daily-revenue-dashboard] stockReduce(only days with uploaded daily revenue, same dept+date as gb_ai_daily_revenue): "
                            + "t1_produce={} t2_waste={} t3_loss={} "
                            + "t4_return={} t123_sum={} | dailyAvg: produce/days={} (waste+loss)/days={}",
                    produceCost.toPlainString(),
                    wasteCost.toPlainString(),
                    lossCost.toPlainString(),
                    returnCost.toPlainString(),
                    productionCost.toPlainString(),
                    avgProduceDaily.toPlainString(),
                    avgWasteLossDaily.toPlainString());
            log.info("[daily-revenue-dashboard] profileFixed: monthlyWage+rent={} => dailyFixed(/30)={} dailyWage={} dailyRent={}",
                    monthlyFixedCost.toPlainString(),
                    dailyFixedCost.toPlainString(),
                    dailyWage.toPlainString(),
                    dailyRent.toPlainString());
            log.info("[daily-revenue-dashboard] KPI: grossMargin%%=(totalNet-produce)/totalNet={}% "
                            + "operatingNetMargin%%=profitAfterCost/avgNet={}% "
                            + "profitAfterCost=avgNet-produce/days-dailyFixed={} "
                            + "dailyTotalExpense(fixed+produce/days+platform/days)={} riskMultiple=|profit|/expense={} "
                            + "shareOf(t123): produce%%={} wasteLoss%%={} (t1%%+t2+3%% should~100, got={})",
                    grossProfitMargin.toPlainString(),
                    operatingNetMarginPct.toPlainString(),
                    profitAfterCost.toPlainString(),
                    dailyTotalExpense.toPlainString(),
                    riskMultiple.toPlainString(),
                    produceShareOfReduce.toPlainString(),
                    wasteLossShareOfReduce.toPlainString(),
                    shareSum.toPlainString());
        }

        BigDecimal healthPercent = operatingNetMarginPct.max(BigDecimal.ZERO).min(BigDecimal.valueOf(100));
        String healthColor = healthColorForOperatingNetMargin(operatingNetMarginPct);
        Map<String, String> safety = safetyForOperatingNetMargin(operatingNetMarginPct);

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
        expensePanel.put("summary", labeledRow("日均支出合计", "日均支出合计（工资+租金+生产核销日均 type=1）", expenseDailyTotal));
        expensePanel.put("rows", Arrays.asList(
                labeledRow("工资日均", "工资（日均）", dailyWage),
                labeledRow("租金日均", "租金（日均）", dailyRent),
                labeledRow("生产核销日均", "生产核销日均（type=1，本期合计/统计天数）", avgProduceDaily),
                labeledRow("损耗废弃日均", "损耗废弃日均（type=2+3，另计未计入上方合计）", avgWasteLossDaily)
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
                labeledRow("食材毛利率", "（净营业额−生产核销）/净营业额；生产=type1", grossProfitMargin),
                labeledRow("经营净利率", "（日均净收−生产核销日均−画像日工资−画像日租金）/日均净收", operatingNetMarginPct),
                labeledRow("制作占比", "制作金额占 type1+2+3 合计%", produceShareOfReduce),
                labeledRow("损耗废弃占比", "损耗+废弃金额占 type1+2+3 合计%", wasteLossShareOfReduce)
        ));
        Map<String, Object> safetyMap = new LinkedHashMap<>();
        safetyMap.put("level", safety.get("level"));
        safetyMap.put("text", safety.get("text"));
        safetyMap.put("desc", safety.get("desc"));
        healthCard.put("safety", safetyMap);
        Map<String, Object> riskRow = new LinkedHashMap<>();
        riskRow.put("label", "抗风险倍数");
        riskRow.put("hint", isProfit
                ? "老板白话：每天净赚的钱，是你店里「日人工/房租摊下来 + 生产原料 + 平台费」这一包的几倍——数字越大，相对越扛得住。"
                : "老板白话：每天亏的这点钱，相当于「日人工/房租摊下来 + 生产原料 + 平台费」这一包的几成——数字越小，相对越好扛。");
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
                labeledRow("每日盈亏", "每日盈亏（净收−日均生产核销−画像日工资−画像日租金）", profitAfterCost),
                labeledRow("食材毛利率", "（净营业额−生产核销）/净营业额", grossProfitMargin),
                labeledRow("经营净利率", "（日均净收−生产−日工资−日租金）/日均净收", operatingNetMarginPct),
                labeledRow("状态", "状态", statusDesc)
        ));
        dashboard.put("operationAnalysis", analysis);

        Map<String, Object> costBreakdown = new LinkedHashMap<>();
        costBreakdown.put("sectionKey", "cost_breakdown");
        costBreakdown.put("title", "成本明细");
        Map<String, Object> makeSection = new LinkedHashMap<>();
        makeSection.put("title", "制作成本");
        makeSection.put("rows", Arrays.asList(
                labeledRow("生产成本", "生产成本（type=1）", produceCost),
                labeledRow("损耗成本", "损耗成本（type=2）", wasteCost),
                labeledRow("损失成本", "损失成本（type=3）", lossCost),
                labeledRow("制作成本合计", "制作+损耗+废弃合计（不含退货）", productionCost),
                labeledRow("制作占比", "制作金额占上述合计%", produceShareOfReduce),
                labeledRow("损耗废弃占比", "损耗+废弃金额占上述合计%", wasteLossShareOfReduce)
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
        bindings.put("avgProduceReduceDaily", formatStatNumber(avgProduceDaily));
        bindings.put("avgWasteLossReduceDaily", formatStatNumber(avgWasteLossDaily));
        bindings.put("produceShareOfReduce", formatStatNumber(produceShareOfReduce));
        bindings.put("wasteLossShareOfReduce", formatStatNumber(wasteLossShareOfReduce));
        if (qStart != null) {
            bindings.put("queryStartDate", qStart);
        }
        if (qEnd != null) {
            bindings.put("queryEndDate", qEnd);
        }
        bindings.put("dateStr", dateHeader.get("dateStr"));
        bindings.put("weekdayStr", dateHeader.get("weekdayStr"));
        bindings.put("currentMonth", currentMonth);
        bindings.put("monthProgress", formatStatNumber(monthProgressBd));
        bindings.put("daysPassed", daysPassed);
        bindings.put("monthDays", monthDays);
        bindings.put("profitMargin", formatStatNumber(grossProfitMargin));
        bindings.put("operatingNetMargin", formatStatNumber(operatingNetMarginPct));
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

    @Override
    public Map<String, Object> buildGroupWideIncomeFlattened(Map<String, Object> groupAggRow,
            int visibleStoreRootCount,
            Integer parentStoreCountHint,
            String startDate,
            String endDate,
            Integer storeRootsWithRecordedRevenue) {
        String qStart = (startDate != null && !startDate.isBlank()) ? startDate.trim() : null;
        String qEnd = (endDate != null && !endDate.isBlank()) ? endDate.trim() : null;
        Map<String, Object> result = new LinkedHashMap<>();
        if (groupAggRow == null || groupAggRow.isEmpty()) {
            return result;
        }

        int days = toPositiveInt(groupAggRow.get("distinctRecordDates"));
        BigDecimal totalGross = toDecimal(groupAggRow.get("totalGrossRevenue"));
        BigDecimal totalOrders = toDecimal(groupAggRow.get("totalOrders"));
        BigDecimal totalPlatform = toDecimal(groupAggRow.get("totalPlatformFee"));
        BigDecimal totalTakeout = toDecimal(groupAggRow.get("totalTakeout"));
        BigDecimal totalTakeoutNetApprox = toDecimal(groupAggRow.get("totalTakeoutNetApprox"));
        BigDecimal totalDineIn = toDecimal(groupAggRow.get("totalDineIn"));

        BigDecimal avgDailyRevenue = days > 0
                ? totalGross.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal avgOrderCount = days > 0
                ? totalOrders.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal avgPerCustomer = totalOrders.compareTo(BigDecimal.ZERO) > 0
                ? totalGross.divide(totalOrders, 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal avgTakeoutRevenue = days > 0
                ? totalTakeout.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal avgTakeoutNet = days > 0
                ? totalTakeoutNetApprox.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal avgDineInRevenue = days > 0
                ? totalDineIn.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        BigDecimal avgNetRevenue = avgDailyRevenue;
        if (days > 0) {
            avgNetRevenue = avgDailyRevenue.subtract(
                    totalPlatform.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP));
        }

        result.put("统计天数", days);
        if (qStart != null) {
            result.put("统计开始日期", qStart);
        }
        if (qEnd != null) {
            result.put("统计结束日期", qEnd);
        }
        result.put("数据口径说明",
                groupScopeNote(visibleStoreRootCount, parentStoreCountHint, groupAggRow, storeRootsWithRecordedRevenue));
        result.put("日均营业额", formatStatNumber(avgDailyRevenue));
        result.put("总营业额", formatStatNumber(totalGross));
        result.put("日均订单数", formatStatNumber(avgOrderCount));
        result.put("客单价", formatStatNumber(avgPerCustomer));
        result.put("平台费合计", formatStatNumber(totalPlatform));
        result.put("退款合计", formatStatNumber(BigDecimal.ZERO));
        result.put("最高日营业额", formatStatNumber(toDecimal(groupAggRow.get("maxDailyGross"))));
        result.put("最低日营业额", formatStatNumber(toDecimal(groupAggRow.get("minDailyGrossPositive"))));

        result.put("日均固定开支", "—");
        result.put("月工资", "—");
        result.put("月租金", "—");

        result.put("日均净收入", formatStatNumber(avgNetRevenue));
        result.put("外卖营业额合计", formatStatNumber(totalTakeout));
        result.put("日均外卖营业额", formatStatNumber(avgTakeoutRevenue));
        result.put("外卖净收合计", formatStatNumber(totalTakeoutNetApprox));
        result.put("日均外卖净收", formatStatNumber(avgTakeoutNet));
        result.put("堂食营业额合计", formatStatNumber(totalDineIn));
        result.put("日均堂食营业额", formatStatNumber(avgDineInRevenue));

        result.put("利润率", "—");
        result.put("利润率说明", "集团多门店汇总：未合并单店核销与食材成本");

        result.put("盈亏状态码", "n_a");
        result.put("盈亏状态", "不适用");
        result.put("日均利润含库存成本", "—");
        return result;
    }

    /**
     * 入账「家」数须按<strong>门店根</strong>计；{@code agg.distinctRecordingDepartments} 为展开后的记账部门 id 数，
     * 不得直接当「家」写入正文（会与 visibleStores / coverage 冲突）。
     */
    private static String groupScopeNote(int visibleStoreRootCount, Integer parentStoreHint,
            Map<String, Object> agg, Integer storeRootsWithRecordedRevenue) {
        boolean useStoreAnchors =
                storeRootsWithRecordedRevenue != null && storeRootsWithRecordedRevenue >= 0 && visibleStoreRootCount > 0;
        String storePart;
        if (visibleStoreRootCount > 0) {
            storePart = "可见范围内 " + visibleStoreRootCount + " 家门店";
        } else if (parentStoreHint != null && parentStoreHint > 0) {
            storePart = "可见范围内约 " + parentStoreHint + " 家门店（门店根暂未解析时的范围提示）";
        } else {
            storePart = "可见范围内的组织单元";
        }

        String mid;
        if (useStoreAnchors) {
            int capped = Math.min(visibleStoreRootCount, Math.max(0, storeRootsWithRecordedRevenue));
            int missingStores = Math.max(0, visibleStoreRootCount - capped);
            if (missingStores <= 0) {
                // 与上文「X 家均有日营收」等 coverage 对齐，省略重复入账句
                mid = "";
            } else {
                mid = "；本期 " + capped + " 家门店根部有日营收入账，"
                        + missingStores + " 家暂无日营收或未纳入本条汇总（按门店根口径）";
            }
        } else {
            int withRowsDept = toPositiveInt(agg.get("distinctRecordingDepartments"));
            int missing = Math.max(0, visibleStoreRootCount - withRowsDept);
            String tail = missing > 0 ? ("；约 " + missing + " 家本期暂无日营收或未纳入本条汇总") : "";
            mid = "；本期记账部门在行内约 " + withRowsDept + " 个有日营业额记录（含直属子部门，非门店家数）" + tail;
        }

        return "集团汇总：" + storePart + mid + "。统计天数为有营业额的自然日数（非简单日历跨度）。";
    }

    private static int toPositiveInt(Object v) {
        if (v == null) {
            return 0;
        }
        if (v instanceof Number n) {
            return Math.max(0, n.intValue());
        }
        try {
            return Math.max(0, new BigDecimal(v.toString().trim()).intValue());
        } catch (Exception e) {
            return 0;
        }
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
            return bd.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        }
        if (value instanceof Number n) {
            return new BigDecimal(n.toString()).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
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

    /**
     * 经营健康度色带：与安全等级同一套分界（相对「经营净利率」%）。
     */
    private static String healthColorForOperatingNetMargin(BigDecimal operatingNetMarginPct) {
        if (operatingNetMarginPct.compareTo(BigDecimal.ZERO) < 0) {
            return "#ef4444";
        }
        if (operatingNetMarginPct.compareTo(new BigDecimal("15")) >= 0) {
            return "#22c55e";
        }
        if (operatingNetMarginPct.compareTo(new BigDecimal("5")) >= 0) {
            return "#eab308";
        }
        return "#f97316";
    }

    /**
     * 安全等级：按「经营净利率」分档（已扣生产核销 type1 + 画像月工资/30 + 画像月租/30），非食材毛利率。
     */
    private static Map<String, String> safetyForOperatingNetMargin(BigDecimal operatingNetMarginPct) {
        Map<String, String> m = new LinkedHashMap<>();
        if (operatingNetMarginPct.compareTo(new BigDecimal("15")) >= 0) {
            m.put("level", "high");
            m.put("text", "稳健");
            m.put("desc", "经营净利率处于较好区间（已含日人工与房租）");
        } else if (operatingNetMarginPct.compareTo(new BigDecimal("5")) >= 0) {
            m.put("level", "mid");
            m.put("text", "一般");
            m.put("desc", "有一定压力，关注客流、人效与租金（已含日人工与房租）");
        } else if (operatingNetMarginPct.compareTo(BigDecimal.ZERO) >= 0) {
            m.put("level", "low");
            m.put("text", "偏紧");
            m.put("desc", "经营净利空间有限（已含日人工与房租）");
        } else {
            m.put("level", "danger");
            m.put("text", "预警");
            m.put("desc", "按日均净收扣除生产主料与房租人工后为亏损");
        }
        return m;
    }
}
