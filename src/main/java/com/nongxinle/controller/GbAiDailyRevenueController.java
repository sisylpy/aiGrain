package com.nongxinle.controller;

import com.nongxinle.entity.GbAiDailyRevenueEntity;
import com.nongxinle.entity.GbAiRestaurantProfileEntity;
import com.nongxinle.entity.GbDepartmentEntity;
import com.nongxinle.entity.GbDepFoodEntity;
import com.nongxinle.entity.GbDistributerFoodEntity;
import com.nongxinle.service.GbAiDailyRevenueService;
import com.nongxinle.service.GbAiRestaurantProfileService;
import com.nongxinle.service.GbDepartmentService;
import com.nongxinle.service.GbDepartmentGoodsStockReduceService;
import com.nongxinle.service.GbDepFoodSalesExcelImportService;
import com.nongxinle.service.GbDepFoodService;
import com.nongxinle.service.GbDistributerFoodService;
import com.nongxinle.utils.R;
import org.apache.poi.ss.usermodel.Sheet;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 日营业额 Controller
 * 餐厅经营分析看板接口
 */
@RestController
@RequestMapping("ai/daily-revenue")
@Tag(name = "日营业额接口")
@RequiredArgsConstructor
public class GbAiDailyRevenueController {

    private final GbAiDailyRevenueService dailyRevenueService;
    private final GbAiRestaurantProfileService profileService;
    private final GbDepartmentGoodsStockReduceService stockReduceService;
    private final GbDepartmentService departmentService;
    private final GbDepFoodService gbDepFoodService;
    private final GbDistributerFoodService gbDistributerFoodService;
    private final GbDepFoodSalesExcelImportService gbDepFoodSalesExcelImportService;

    private static final Pattern FOOD_HEADER_ID_ZH = Pattern.compile("（id:(\\d+)）");
    private static final Pattern FOOD_HEADER_ID_EN = Pattern.compile("\\(id:(\\d+)\\)");


    /**
     * 获取营业额统计
     *
     * @Description 按经营看板页面分区返回：天平（收入/支出）、底座（健康度与月度预测）、核心指标、经营分析、成本明细。扁平 stats 的键名为中文；小程序绑定请用 dashboard.bindings（英文键）。
     */
    @GetMapping("/stats/{departmentId}")
    @Operation(summary = "获取营业额统计", description = "分区结构化返回（dashboard）+ 扁平 stats；含收入端/支出端、健康度、月度预测、核心指标、经营分析、成本明细")
    public R getStats(@Parameter(description = "部门/餐厅ID") @PathVariable Long departmentId) {
        GbAiRestaurantProfileEntity profile = profileService.getByDepartmentId(departmentId);
        if (profile == null) {
            return R.error("餐厅画像不存在");
        }

        Map<String, Object> stats = dailyRevenueService.getStatsByDepartmentId(departmentId);
        if (stats == null || stats.get("days") == null || ((Number) stats.get("days")).intValue() == 0) {
            return R.error("暂无营业额数据");
        }

        int days = ((Number) stats.get("days")).intValue();
        Map<String, Object> result = new HashMap<>();

        result.put("统计天数", days);
        BigDecimal avgDailyRevenue = toDecimal(stats.get("avg_daily_revenue"));
        result.put("日均营业额", avgDailyRevenue);
        result.put("总营业额", toDecimal(stats.get("total_revenue")));
        result.put("日均订单数", toDecimal(stats.get("avg_order_count")));
        result.put("客单价", toDecimal(stats.get("avg_per_customer")));
        result.put("平台费合计", toDecimal(stats.get("total_coupon_amount")));
        result.put("退款合计", toDecimal(stats.get("total_refund_amount")));
        result.put("最高日营业额", toDecimal(stats.get("max_daily_revenue")));
        result.put("最低日营业额", toDecimal(stats.get("min_daily_revenue")));

        BigDecimal monthlyWage = profile.getGbAiRestaurantProfileMonthlyWage() != null
                ? profile.getGbAiRestaurantProfileMonthlyWage() : BigDecimal.ZERO;
        BigDecimal monthlyRent = profile.getGbAiRestaurantProfileRentMonthly() != null
                ? profile.getGbAiRestaurantProfileRentMonthly() : BigDecimal.ZERO;
        BigDecimal monthlyFixedCost = monthlyWage.add(monthlyRent);
        BigDecimal dailyFixedCost = monthlyFixedCost.divide(BigDecimal.valueOf(30), 2, RoundingMode.HALF_UP);
        BigDecimal dailyWage = monthlyWage.divide(BigDecimal.valueOf(30), 2, RoundingMode.HALF_UP);
        BigDecimal dailyRent = monthlyRent.divide(BigDecimal.valueOf(30), 2, RoundingMode.HALF_UP);

        result.put("日均固定开支", dailyFixedCost);
        result.put("月工资", monthlyWage);
        result.put("月租金", monthlyRent);

        BigDecimal totalCoupon = toDecimal(stats.get("total_coupon_amount"));
        BigDecimal totalRefund = toDecimal(stats.get("total_refund_amount"));
        BigDecimal avgNetRevenue = avgDailyRevenue.subtract(totalCoupon.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP));
        result.put("日均净收入", avgNetRevenue);

        result.put("外卖营业额合计", toDecimal(stats.get("total_takeout_revenue")));
        BigDecimal avgTakeoutRevenue = toDecimal(stats.get("avg_takeout_revenue"));
        result.put("日均外卖营业额", avgTakeoutRevenue);
        result.put("外卖净收合计", toDecimal(stats.get("total_takeout_net")));
        result.put("日均外卖净收", toDecimal(stats.get("avg_takeout_net")));

        Map<String, Object> costParams = new HashMap<>();
        costParams.put("departmentFatherId", departmentId);
        Map<String, Object> costStats = stockReduceService.queryReduceAllTypesTotal(costParams);

        BigDecimal produceCost = toDecimal(costStats.get("produceTotal"));
        BigDecimal wasteCost = toDecimal(costStats.get("wasteTotal"));
        BigDecimal lossCost = toDecimal(costStats.get("lossTotal"));
        BigDecimal returnCost = toDecimal(costStats.get("returnTotal"));
        BigDecimal productionCost = produceCost.add(wasteCost).add(lossCost);
        BigDecimal totalCost = productionCost.add(returnCost);
        // 部门库存核销：制作(1)+损耗(2)+废弃/损失(3)，不含退货(4)；按营业额统计天数摊日均
        BigDecimal avgDepartmentReduceDaily = productionCost.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);

        result.put("生产成本", produceCost);
        result.put("损耗成本", wasteCost);
        result.put("损失成本", lossCost);
        result.put("退货成本", returnCost);
        result.put("制作成本合计", productionCost);
        result.put("部门核销制作损耗废弃日均", avgDepartmentReduceDaily);
        result.put("总成本", totalCost);

        BigDecimal grossProfitMargin = BigDecimal.ZERO;
        BigDecimal totalNetRevenue = toDecimal(stats.get("total_revenue")).subtract(totalCoupon);
        if (totalNetRevenue.compareTo(BigDecimal.ZERO) > 0) {
            grossProfitMargin = totalNetRevenue.subtract(totalCost)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(totalNetRevenue, 2, RoundingMode.HALF_UP);
        }
        result.put("利润率", grossProfitMargin);
        result.put("利润率说明", grossProfitMargin + "%");

        result.put("参考日均固定开支", dailyFixedCost);

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
        result.put("日均利润未扣库存", profit);
        result.put("日均利润含库存成本", profitAfterCost);
        result.put("实际日均利润", actualProfit);

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

        Calendar cal = Calendar.getInstance(Locale.CHINA);
        int currentMonth = cal.get(Calendar.MONTH) + 1;
        int monthDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        int daysPassed = cal.get(Calendar.DAY_OF_MONTH);
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
        dineRatioBar.put("percent", dineInRatio);
        incomePanel.put("ratioBar", dineRatioBar);
        scaleBeam.put("income", incomePanel);

        Map<String, Object> expensePanel = new LinkedHashMap<>();
        expensePanel.put("sectionKey", "scale_expense");
        expensePanel.put("title", "支出端");
        expensePanel.put("summary", labeledRow("日均固定开支", "日均固定开支（工资+租金÷30）", dailyFixedCost));
        expensePanel.put("rows", Arrays.asList(
                labeledRow("工资日均", "工资（日均）", dailyWage),
                labeledRow("租金日均", "租金（日均）", dailyRent),
                labeledRow("部门核销支出日均", "部门核销（制作+损耗+废弃）日均", avgDepartmentReduceDaily)
        ));
        scaleBeam.put("expense", expensePanel);

        Map<String, Object> pointer = new LinkedHashMap<>();
        pointer.put("statusKey", status);
        pointer.put("statusClass", statusClass);
        pointer.put("statusLabel", statusLabel);
        pointer.put("statusDesc", statusDesc);
        scaleBeam.put("pointer", pointer);
        dashboard.put("scaleBeam", scaleBeam);

        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
        Map<String, Object> scaleBase = new LinkedHashMap<>();
        Map<String, Object> dateHeader = new LinkedHashMap<>();
        dateHeader.put("dateStr", df.format(cal.getTime()));
        dateHeader.put("weekdayStr", chineseWeekday(cal));
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
        riskRow.put("value", riskMultiple);
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
        bindings.put("avgDailyRevenue", avgDailyRevenue);
        bindings.put("avgDineInRevenue", avgDineInRevenue);
        bindings.put("avgTakeoutRevenue", avgTakeoutRevenue);
        bindings.put("dineInRatio", dineInRatio);
        bindings.put("avgFixedCost", dailyFixedCost);
        bindings.put("dailyWage", dailyWage);
        bindings.put("dailyRent", dailyRent);
        bindings.put("avgPlatformFee", avgPlatformFee);
        bindings.put("avgDepartmentReduceDaily", avgDepartmentReduceDaily);
        bindings.put("dateStr", dateHeader.get("dateStr"));
        bindings.put("weekdayStr", dateHeader.get("weekdayStr"));
        bindings.put("currentMonth", currentMonth);
        bindings.put("monthProgress", monthProgressBd);
        bindings.put("daysPassed", daysPassed);
        bindings.put("monthDays", monthDays);
        bindings.put("profitMargin", grossProfitMargin);
        bindings.put("healthPercent", healthPercent);
        bindings.put("healthColor", healthColor);
        bindings.put("safetyLevel", safety.get("level"));
        bindings.put("safetyText", safety.get("text"));
        bindings.put("safetyDesc", safety.get("desc"));
        bindings.put("isProfit", isProfit);
        bindings.put("riskMultiple", riskMultiple);
        bindings.put("absEstimatedProfit", absEstimatedProfit);
        bindings.put("vsIndustryPercent", null);
        bindings.put("staffCount", staffCount != null ? staffCount : 0);
        bindings.put("seatCount", seatCount != null ? seatCount : 0);
        bindings.put("competitorCount", competitorCount != null ? competitorCount : 0);
        bindings.put("avgPrice", avgPrice);
        bindings.put("days", days);
        bindings.put("totalRevenue", toDecimal(stats.get("total_revenue")));
        bindings.put("dailyNetRevenue", avgNetRevenue);
        bindings.put("breakEvenPoint", dailyFixedCost);
        bindings.put("dailyProfit", profitAfterCost);
        bindings.put("produceCost", produceCost);
        bindings.put("wasteCost", wasteCost);
        bindings.put("lossCost", lossCost);
        bindings.put("productionCost", productionCost);
        bindings.put("returnCost", returnCost);
        bindings.put("totalCost", totalCost);
        bindings.put("statusClass", statusClass);
        bindings.put("statusLabel", statusLabel);
        bindings.put("statusText", statusDesc);
        dashboard.put("bindings", bindings);

        Map<String, Object> data = new HashMap<>();
        data.put("dashboard", dashboard);
        data.put("stats", result);
        data.put("profile", profile);

        return R.ok(data);
    }

    /**
     * 获取日营业额列表（含统计、曲线图、每日详情）
     *
     * @Description 获取指定餐厅的日营业额完整数据，包含统计数据、曲线图数据、每日详情列表
     * @param departmentId 部门/餐厅ID
     * @param startDate 开始日期（可选）
     * @param endDate 结束日期（可选）
     * @return 统计数据、曲线图数据、每日列表
     */
    @GetMapping("/list/{departmentId}")
    @Operation(summary = "获取日营业额完整数据", description = "获取指定餐厅的日营业额完整数据，包含统计数据、曲线图数据、每日详情列表")
    public R getList(
            @Parameter(description = "部门/餐厅ID") @PathVariable Long departmentId,
            @Parameter(description = "开始日期") @RequestParam(required = false) String startDate,
            @Parameter(description = "结束日期") @RequestParam(required = false) String endDate) {

        Map<String, Object> params = new HashMap<>();
        params.put("departmentId", departmentId);
        params.put("startDate", startDate);
        params.put("endDate", endDate);

        // 查询日营业额列表
        List<GbAiDailyRevenueEntity> dailyList = dailyRevenueService.queryDailyRevenueListByParams(params);

        if (dailyList == null || dailyList.isEmpty()) {
            return R.error("暂无营业额数据");
        }

        // 构建返回数据
        Map<String, Object> result = new HashMap<>();

        // 曲线图数据（每日堂食和外卖）
        List<Map<String, Object>> chartData = new ArrayList<>();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        
        for (GbAiDailyRevenueEntity item : dailyList) {
            Map<String, Object> dayData = new HashMap<>();
            dayData.put("date", dateFormat.format(item.getGbAiDailyRevenueRecordDate()));
            
            // 堂食金额（处理null值）
            BigDecimal dineIn = item.getGbAiDailyRevenueDineInRevenue() != null 
                    ? item.getGbAiDailyRevenueDineInRevenue() : BigDecimal.ZERO;
            dayData.put("dineIn", dineIn);
            
            // 外卖金额（处理null值）
            BigDecimal takeout = item.getGbAiDailyRevenueTakeoutRevenue() != null 
                    ? item.getGbAiDailyRevenueTakeoutRevenue() : BigDecimal.ZERO;
            dayData.put("takeout", takeout);
            
            chartData.add(dayData);
        }
        result.put("chartData", chartData);

        // 每日详情列表
        result.put("dailyList", dailyList);

        return R.ok(result);
    }

    /**
     * 获取日营业额柱状图数据
     *
     * @Description 获取指定餐厅的日营业额柱状图数据，每个日期包含堂食和外卖金额
     * @param departmentId 部门/餐厅ID
     * @param startDate 开始日期（可选）
     * @param endDate 结束日期（可选）
     * @return 柱状图数据，每个日期包含堂食和外卖金额
     */
    @GetMapping("/chart/{departmentId}")
    @Operation(summary = "获取日营业额柱状图数据", description = "获取指定餐厅的日营业额柱状图数据，每个日期包含堂食和外卖金额")
    public R getChartData(
            @Parameter(description = "部门/餐厅ID") @PathVariable Long departmentId,
            @Parameter(description = "开始日期") @RequestParam(required = false) String startDate,
            @Parameter(description = "结束日期") @RequestParam(required = false) String endDate) {

        Map<String, Object> params = new HashMap<>();
        params.put("departmentId", departmentId);
        params.put("startDate", startDate);
        params.put("endDate", endDate);

        // 查询日营业额列表
        List<GbAiDailyRevenueEntity> dailyList = dailyRevenueService.queryDailyRevenueListByParams(params);

        if (dailyList == null || dailyList.isEmpty()) {
            return R.error("暂无营业额数据");
        }

        // 构建柱状图数据：每个日期一个对象，包含堂食和外卖金额
        List<Map<String, Object>> barChartData = new ArrayList<>();
        
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        
        for (GbAiDailyRevenueEntity item : dailyList) {
            Map<String, Object> dayData = new HashMap<>();
            
            // 格式化日期
            String dateStr = dateFormat.format(item.getGbAiDailyRevenueRecordDate());
            dayData.put("date", dateStr);
            
            // 堂食金额（处理null值）
            BigDecimal dineIn = item.getGbAiDailyRevenueDineInRevenue() != null 
                    ? item.getGbAiDailyRevenueDineInRevenue() : BigDecimal.ZERO;
            dayData.put("dineIn", dineIn);
            
            // 外卖金额（处理null值）
            BigDecimal takeout = item.getGbAiDailyRevenueTakeoutRevenue() != null 
                    ? item.getGbAiDailyRevenueTakeoutRevenue() : BigDecimal.ZERO;
            dayData.put("takeout", takeout);
            
            barChartData.add(dayData);
        }

        // 构建返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("barChartData", barChartData);    // 柱状图数据
        result.put("totalDays", dailyList.size());

        return R.ok(result);
    }

    private BigDecimal toDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        return new BigDecimal(value.toString());
    }

    private static Map<String, Object> labeledRow(String key, String label, Object value) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("key", key);
        row.put("label", label);
        row.put("value", value);
        return row;
    }

    private static String chineseWeekday(Calendar cal) {
        String[] w = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};
        return w[cal.get(Calendar.DAY_OF_WEEK) - 1];
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

    /**
     * 保存单条日营业额
     */
    @PostMapping("/save")
    @Operation(summary = "保存日营业额", description = "保存单条日营业额记录")
    public R save(@RequestBody GbAiDailyRevenueEntity dailyRevenue) {
        // 设置记录日期，默认当天
        if (dailyRevenue.getGbAiDailyRevenueRecordDate() == null) {
            dailyRevenue.setGbAiDailyRevenueRecordDate(new Date());
        }
        
        // 自动计算星期几
        try {
            Date recordDate = dailyRevenue.getGbAiDailyRevenueRecordDate();
            if (recordDate != null) {
                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.setTime(recordDate);
                int dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK);
                int weekday = dayOfWeek == 1 ? 0 : dayOfWeek - 1; // 0=周日, 1=周一, ..., 6=周六
                dailyRevenue.setGbAiDailyRevenueWeekday(weekday);
            } else {
                // 如果日期为空，设置为默认值
                dailyRevenue.setGbAiDailyRevenueWeekday(1); // 默认周一
            }
        } catch (Exception e) {
            dailyRevenue.setGbAiDailyRevenueWeekday(1); // 默认周一
        }
        
        // 节假日设为空字符串（从模板中去掉了，由后台自动计算或后续补充）
        if (dailyRevenue.getGbAiDailyRevenueHoliday() == null) {
            dailyRevenue.setGbAiDailyRevenueHoliday("");
        }
        
        dailyRevenue.setGbAiDailyRevenueCreateTime(new Date());
        dailyRevenue.setGbAiDailyRevenueUpdateTime(new Date());

        dailyRevenueService.save(dailyRevenue);
        return R.ok();
    }

  
    /**
     * 更新日营业额
     */
    @PostMapping("/update")
    @Operation(summary = "更新日营业额", description = "更新日营业额记录")
    public R update(@RequestBody GbAiDailyRevenueEntity dailyRevenue) {
        // 自动计算星期几（如果日期有变化）
        try {
            Date recordDate = dailyRevenue.getGbAiDailyRevenueRecordDate();
            if (recordDate != null) {
                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.setTime(recordDate);
                int dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK);
                int weekday = dayOfWeek == 1 ? 0 : dayOfWeek - 1; // 0=周日, 1=周一, ..., 6=周六
                dailyRevenue.setGbAiDailyRevenueWeekday(weekday);
            }
        } catch (Exception e) {
            // 如果计算失败，保持原值
        }
        
        dailyRevenue.setGbAiDailyRevenueUpdateTime(new Date());
        dailyRevenueService.updateById(dailyRevenue);
        return R.ok();
    }

    /**
     * 删除日营业额
     */
    @DeleteMapping("/delete/{id}")
    @Operation(summary = "删除日营业额", description = "删除单条日营业额记录")
    public R delete(@PathVariable Long id) {
        dailyRevenueService.removeById(id);
        return R.ok();
    }

    /**
     * Excel上传批量保存日营业额
     */
    @PostMapping("/upload-excel")
    @Operation(summary = "Excel上传日营业额", description = "通过Excel文件上传批量保存日营业额记录")
    public R uploadExcel(
            @Parameter(description = "Excel文件") @RequestParam("file") MultipartFile file,
            @Parameter(description = "部门ID") @RequestParam("departmentId") Long departmentId,
            @Parameter(description = "分配者ID") @RequestParam("distributerId") Long distributerId) {
        try {
            // 检查文件是否为空
            if (file.isEmpty()) {
                return R.error("请上传Excel文件");
            }

            // 检查文件类型
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || 
                (!originalFilename.toLowerCase().endsWith(".xls") && 
                 !originalFilename.toLowerCase().endsWith(".xlsx"))) {
                return R.error("请上传Excel文件（.xls 或 .xlsx 格式）");
            }

            // 使用 Hutool 读取 Excel 文件
            List<GbAiDailyRevenueEntity> revenueList = readExcelData(file, departmentId, distributerId);
            
            if (revenueList.isEmpty()) {
                return R.error("Excel文件中没有有效的日营业额数据");
            }
            
            // 打印所有读取到的数据
            System.out.println("[DEBUG] ============ 读取到的所有数据开始 ============");
            for (int i = 0; i < revenueList.size(); i++) {
                GbAiDailyRevenueEntity revenue = revenueList.get(i);
                System.out.println("[DEBUG] 记录" + i + ": 部门ID=" + revenue.getGbAiDailyRevenueDepartmentId() + 
                                 ", 日期=" + revenue.getGbAiDailyRevenueRecordDate() + 
                                 ", 堂食营业额=" + revenue.getGbAiDailyRevenueDineInRevenue());
            }
            System.out.println("[DEBUG] ============ 读取到的所有数据结束 ============");

            // 检查是否有日期为空的记录
            List<String> emptyDateRecords = new ArrayList<>();
            for (GbAiDailyRevenueEntity revenue : revenueList) {
                if (revenue.getGbAiDailyRevenueRecordDate() == null) {
                    emptyDateRecords.add("部门ID=" + revenue.getGbAiDailyRevenueDepartmentId() + 
                                       ", 堂食营业额=" + revenue.getGbAiDailyRevenueDineInRevenue());
                }
            }
            
            if (!emptyDateRecords.isEmpty()) {
                return R.error("Excel文件中存在日期为空的记录，无法处理。请检查以下数据：" + emptyDateRecords);
            }
            
            // 逐个处理数据，更新或插入
            Date now = new Date();
            int inserted = 0;
            int updated = 0;
            int errors = 0;
            List<String> errorMessages = new ArrayList<>();
            
            for (GbAiDailyRevenueEntity revenue : revenueList) {
                try {
                    // 打印当前处理的数据
                    System.out.println("[DEBUG] 处理实体数据: 部门ID=" + revenue.getGbAiDailyRevenueDepartmentId() + 
                                     ", 日期=" + revenue.getGbAiDailyRevenueRecordDate() + 
                                     ", 堂食营业额=" + revenue.getGbAiDailyRevenueDineInRevenue() +
                                     ", 外卖营业额=" + revenue.getGbAiDailyRevenueTakeoutRevenue());
                    
                    // 设置创建时间和更新时间
                    if (revenue.getGbAiDailyRevenueCreateTime() == null) {
                        revenue.setGbAiDailyRevenueCreateTime(now);
                    }
                    revenue.setGbAiDailyRevenueUpdateTime(now);
                    // 构建查询条件：部门ID和记录日期
                    Map<String, Object> queryMap = new HashMap<>();
                    queryMap.put("gb_ai_daily_revenue_department_id", revenue.getGbAiDailyRevenueDepartmentId());
                    // 使用日期字符串格式进行查询，避免时间部分的影响
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                    String dateStr = dateFormat.format(revenue.getGbAiDailyRevenueRecordDate());
                    queryMap.put("DATE(gb_ai_daily_revenue_record_date)", dateStr);
                    
                    // 查询是否已存在
                    List<GbAiDailyRevenueEntity> existingList = dailyRevenueService.listByMap(queryMap);
                    
                    if (!existingList.isEmpty()) {
                        // 已存在，更新第一条记录
                        GbAiDailyRevenueEntity existing = existingList.get(0);
                        
                        // 更新数据
                        existing.setGbAiDailyRevenueDineInRevenue(revenue.getGbAiDailyRevenueDineInRevenue());
                        existing.setGbAiDailyRevenueDineInOrders(revenue.getGbAiDailyRevenueDineInOrders());
                        existing.setGbAiDailyRevenueDineInCustomers(revenue.getGbAiDailyRevenueDineInCustomers());
                        existing.setGbAiDailyRevenueTakeoutRevenue(revenue.getGbAiDailyRevenueTakeoutRevenue());
                        existing.setGbAiDailyRevenueTakeoutOrders(revenue.getGbAiDailyRevenueTakeoutOrders());
                        existing.setGbAiDailyRevenuePlatformFee(revenue.getGbAiDailyRevenuePlatformFee());
                        existing.setGbAiDailyRevenueWeekday(revenue.getGbAiDailyRevenueWeekday());
                        existing.setGbAiDailyRevenueHoliday(revenue.getGbAiDailyRevenueHoliday());
                        existing.setGbAiDailyRevenueNotes(revenue.getGbAiDailyRevenueNotes());
                        existing.setGbAiDailyRevenueUpdateTime(now);
                        
                        dailyRevenueService.updateById(existing);
                        updated++;
                    } else {
                        // 不存在，插入新记录
                        dailyRevenueService.save(revenue);
                        inserted++;
                    }
                } catch (Exception e) {
                    errors++;
                    errorMessages.add("处理日期 " + revenue.getGbAiDailyRevenueRecordDate() + " 的数据时出错: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            return R.ok()
                    .put("total", revenueList.size())
                    .put("inserted", inserted)
                    .put("updated", updated)
                    .put("errors", errors)
                    .put("errorMessages", errorMessages);
        } catch (IOException e) {
            e.printStackTrace();
            return R.error("文件读取失败：" + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return R.error("Excel解析失败：" + e.getMessage());
        }
    }

    /**
     * 读取Excel数据并转换为实体列表
     */
    private List<GbAiDailyRevenueEntity> readExcelData(MultipartFile file, Long departmentId, Long distributerId) 
            throws IOException, ParseException {
        List<GbAiDailyRevenueEntity> revenueList = new ArrayList<>();
        Set<String> dateSet = new HashSet<>(); // 用于检查重复日期
        
        // 使用 Hutool 的 ExcelReader
        cn.hutool.poi.excel.ExcelReader reader = cn.hutool.poi.excel.ExcelUtil.getReader(file.getInputStream());
        
        // 读取所有行数据（跳过表头）
        List<List<Object>> rows = reader.read();
        
        // 打印所有Excel行数据用于调试
        System.out.println("[DEBUG] ============ Excel原始数据开始 ============");
        for (int idx = 0; idx < rows.size(); idx++) {
            List<Object> row = rows.get(idx);
            System.out.print("[DEBUG] 行" + idx + ": ");
            for (int col = 0; col < row.size(); col++) {
                Object cell = row.get(col);
                System.out.print("列" + col + "=" + (cell != null ? cell.toString() : "null") + " ");
            }
            System.out.println();
        }
        System.out.println("[DEBUG] ============ Excel原始数据结束 ============");
        
        // 智能识别表头行数
        int startRow = 0;
        if (!rows.isEmpty()) {
            // 检查第一行是否是元数据行（包含"表格"、"部门ID"、"日期"等）
            if (rows.size() > 0 && rows.get(0).size() > 0) {
                Object firstCell = rows.get(0).get(0);
                // 如果第一行包含"表格"，第二行包含"部门ID"，第三行包含"日期"
                // 那么需要跳过前3行
                if (rows.size() >= 3 && 
                    firstCell instanceof String && 
                    ((String) firstCell).contains("表格")) {
                    
                    // 检查第二行是否包含"部门ID"
                    if (rows.get(1).size() > 0 && 
                        rows.get(1).get(0) instanceof String &&
                        ((String) rows.get(1).get(0)).contains("部门ID")) {
                        
                        // 检查第三行是否包含"日期"
                        if (rows.get(2).size() > 0 && 
                            rows.get(2).get(0) instanceof String &&
                            ((String) rows.get(2).get(0)).contains("日期")) {
                            
                            startRow = 3; // 跳过前3行元数据
                            System.out.println("[DEBUG] 检测到智能模板格式，跳过前3行元数据");
                        }
                    }
                }
                // 如果第一行直接包含"日期"（旧格式），跳过1行
                else if (firstCell instanceof String && 
                         ((String) firstCell).toString().contains("日期")) {
                    startRow = 1;
                    System.out.println("[DEBUG] 检测到旧模板格式，跳过表头行");
                }
            }
        }
        
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat dateKeyFormat = new SimpleDateFormat("yyyy-MM-dd");
        Date now = new Date();
        
        for (int i = startRow; i < rows.size(); i++) {
            List<Object> row = rows.get(i);
            
            // 跳过空行
            if (row.isEmpty() || row.get(0) == null) {
                continue;
            }
            
            // 解析日期（Excel中的日期可能是Date类型或String类型）
            Object dateCell = row.size() > 0 ? row.get(0) : null;
            Date recordDate = null;
            String dateStr = null;
            
            if (dateCell != null) {
                if (dateCell instanceof Date) {
                    recordDate = (Date) dateCell;
                    dateStr = dateKeyFormat.format(recordDate);
                } else if (dateCell instanceof String) {
                    String dateString = ((String) dateCell).trim();
                    if (!dateString.isEmpty()) {
                        try {
                            recordDate = dateFormat.parse(dateString);
                            dateStr = dateKeyFormat.format(recordDate);
                        } catch (ParseException e) {
                            // 如果解析失败，跳过这行
                            System.out.println("[WARN] 跳过无效日期行：" + dateString);
                            continue;
                        }
                    } else {
                        // 空日期，跳过这行
                        continue;
                    }
                } else {
                    // 不是日期也不是字符串，跳过
                    continue;
                }
            } else {
                // 日期单元格为空，跳过
                continue;
            }
            
            // 检查重复日期
            String dateKey = departmentId + "-" + dateStr;
            if (dateSet.contains(dateKey)) {
                System.out.println("[WARN] 跳过重复日期数据：部门ID=" + departmentId + ", 日期=" + dateStr);
                continue;
            }
            dateSet.add(dateKey);
            
            GbAiDailyRevenueEntity entity = new GbAiDailyRevenueEntity();
            
            // 设置部门ID和分配者ID
            entity.setGbAiDailyRevenueDepartmentId(departmentId);
            entity.setGbAiDailyRevenueDistributerId(distributerId);
            // 设置记录日期
            entity.setGbAiDailyRevenueRecordDate(recordDate);
            
            // 打印设置的信息用于调试
            System.out.println("[DEBUG] 创建实体: 部门ID=" + departmentId + 
                             ", 日期=" + recordDate + 
                             ", 日期字符串=" + dateStr);
            
            // 解析堂食营业额（第2列）
            if (row.size() > 1 && row.get(1) != null) {
                try {
                    BigDecimal dineInRevenue = new BigDecimal(row.get(1).toString());
                    entity.setGbAiDailyRevenueDineInRevenue(dineInRevenue);
                } catch (NumberFormatException e) {
                    // 如果转换失败，设置为0
                    entity.setGbAiDailyRevenueDineInRevenue(BigDecimal.ZERO);
                }
            } else {
                entity.setGbAiDailyRevenueDineInRevenue(BigDecimal.ZERO);
            }
            
            // 解析堂食订单数（第3列）
            if (row.size() > 2 && row.get(2) != null) {
                try {
                    Integer dineInOrders = Integer.parseInt(row.get(2).toString());
                    entity.setGbAiDailyRevenueDineInOrders(dineInOrders);
                } catch (NumberFormatException e) {
                    entity.setGbAiDailyRevenueDineInOrders(0);
                }
            } else {
                entity.setGbAiDailyRevenueDineInOrders(0);
            }
            
            // 解析堂食顾客数（第4列）
            if (row.size() > 3 && row.get(3) != null) {
                try {
                    Integer dineInCustomers = Integer.parseInt(row.get(3).toString());
                    entity.setGbAiDailyRevenueDineInCustomers(dineInCustomers);
                } catch (NumberFormatException e) {
                    entity.setGbAiDailyRevenueDineInCustomers(0);
                }
            } else {
                entity.setGbAiDailyRevenueDineInCustomers(0);
            }
            
            // 解析外卖营业额（第5列）
            if (row.size() > 4 && row.get(4) != null) {
                try {
                    BigDecimal takeoutRevenue = new BigDecimal(row.get(4).toString());
                    entity.setGbAiDailyRevenueTakeoutRevenue(takeoutRevenue);
                } catch (NumberFormatException e) {
                    entity.setGbAiDailyRevenueTakeoutRevenue(BigDecimal.ZERO);
                }
            } else {
                entity.setGbAiDailyRevenueTakeoutRevenue(BigDecimal.ZERO);
            }
            
            // 解析外卖订单数（第6列）
            if (row.size() > 5 && row.get(5) != null) {
                try {
                    Integer takeoutOrders = Integer.parseInt(row.get(5).toString());
                    entity.setGbAiDailyRevenueTakeoutOrders(takeoutOrders);
                } catch (NumberFormatException e) {
                    entity.setGbAiDailyRevenueTakeoutOrders(0);
                }
            } else {
                entity.setGbAiDailyRevenueTakeoutOrders(0);
            }
            
            // 解析平台抽成（第7列）
            if (row.size() > 6 && row.get(6) != null) {
                try {
                    BigDecimal platformFee = new BigDecimal(row.get(6).toString());
                    entity.setGbAiDailyRevenuePlatformFee(platformFee);
                } catch (NumberFormatException e) {
                    entity.setGbAiDailyRevenuePlatformFee(BigDecimal.ZERO);
                }
            } else {
                entity.setGbAiDailyRevenuePlatformFee(BigDecimal.ZERO);
            }
            
            // 自动计算星期几（从模板中去掉了，由后台自动计算）
            try {
                if (recordDate != null) {
                    java.util.Calendar cal = java.util.Calendar.getInstance();
                    cal.setTime(recordDate);
                    int dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK);
                    int weekday = dayOfWeek == 1 ? 0 : dayOfWeek - 1; // 0=周日, 1=周一, ..., 6=周六
                    entity.setGbAiDailyRevenueWeekday(weekday);
                } else {
                    // 如果日期为空，设置为默认值
                    entity.setGbAiDailyRevenueWeekday(1); // 默认周一
                }
            } catch (Exception e) {
                entity.setGbAiDailyRevenueWeekday(1); // 默认周一
            }
            
            // 节假日设为空字符串（从模板中去掉了，由后台自动计算或后续补充）
            entity.setGbAiDailyRevenueHoliday("");
            
            // 解析备注（第8列，因为去掉了星期几和节假日列）
            if (row.size() > 7 && row.get(7) != null) {
                entity.setGbAiDailyRevenueNotes(row.get(7).toString());
            } else {
                entity.setGbAiDailyRevenueNotes("");
            }
            
            // 设置创建时间和更新时间
            Date currentTime = new Date();
            entity.setGbAiDailyRevenueCreateTime(currentTime);
            entity.setGbAiDailyRevenueUpdateTime(currentTime);
            
            revenueList.add(entity);
        }
        
        return revenueList;
    }

    /**
     * 智能模板生成 - 根据日期范围和部门ID生成预填模板
     * 
     * @param startDate 开始日期，格式：yyyy-MM-dd
     * @param endDate 结束日期，格式：yyyy-MM-dd
     * @param departmentId 部门ID
     * @return 包含日期列和部门信息的Excel模板
     */
    @GetMapping("/download-smart-template")
    @Operation(summary = "智能模板生成", description = "根据日期范围和部门ID生成预填模板，包含日期列和部门信息")
    public void downloadSmartTemplate(HttpServletResponse response,
            @Parameter(description = "开始日期，格式：yyyy-MM-dd") @RequestParam("startDate") String startDate,
            @Parameter(description = "结束日期，格式：yyyy-MM-dd") @RequestParam("endDate") String endDate,
            @Parameter(description = "部门ID") @RequestParam("departmentId") Integer departmentId) throws IOException {
        
        try {
            System.out.println("[DEBUG] 开始处理智能模板下载请求，参数：startDate=" + startDate + ", endDate=" + endDate + ", departmentId=" + departmentId);
            // 1. 验证日期格式
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            Date start = dateFormat.parse(startDate);
            Date end = dateFormat.parse(endDate);
            
            // 验证日期范围
            if (start.after(end)) {
                throw new IllegalArgumentException("开始日期不能晚于结束日期");
            }
            
            // 计算日期范围天数
            long diff = end.getTime() - start.getTime();
            long days = diff / (1000 * 60 * 60 * 24) + 1; // 包含首尾
            
            if (days > 365) {
                throw new IllegalArgumentException("日期范围不能超过365天");
            }
            // 2. 获取部门信息
            GbDepartmentEntity department = departmentService.getById(departmentId);
            if (department == null) {
                throw new IllegalArgumentException("部门不存在，部门ID: " + departmentId);
            }
            
            String departmentName = department.getGbDepartmentName();
            
            // 3. 创建Excel文件
            cn.hutool.poi.excel.ExcelWriter writer = cn.hutool.poi.excel.ExcelUtil.getWriter();
            
            // 设置表头（包含部门信息）
            List<Object> headerRow = new ArrayList<>();
            headerRow.add("部门ID: " + departmentId);
            headerRow.add("部门名称: " + departmentName);
            headerRow.add("日期范围: " + startDate + " 至 " + endDate);
            headerRow.add("总天数: " + days);
            headerRow.add("");
            headerRow.add("");
            headerRow.add("");
            headerRow.add("");
            headerRow.add("");
            headerRow.add("");
            writer.writeRow(headerRow);
            
            // 空行
            writer.writeRow(new ArrayList<>());
            
            // 数据表头（去掉星期几和节假日，由后台自动计算）
            String[] dataHeaders = {
                "日期", 
                "堂食营业额", 
                "堂食订单数", 
                "堂食顾客数", 
                "外卖营业额", 
                "外卖订单数", 
                "平台抽成", 
                "备注"
            };
            writer.writeHeadRow(Arrays.asList(dataHeaders));
            
            // 4. 生成日期序列并填充模板
            java.util.Calendar calendar = java.util.Calendar.getInstance();
            calendar.setTime(start);
            
            for (int i = 0; i < days; i++) {
                List<Object> rowData = new ArrayList<>();
                
                // 日期
                Date currentDate = calendar.getTime();
                String dateStr = dateFormat.format(currentDate);
                rowData.add(dateStr);
                
                // 数值字段留空，等待用户填写
                rowData.add(""); // 堂食营业额
                rowData.add(""); // 堂食订单数
                rowData.add(""); // 堂食顾客数
                rowData.add(""); // 外卖营业额
                rowData.add(""); // 外卖订单数
                rowData.add(""); // 平台抽成
                
                // 备注留空（星期几由后台自动计算，节假日字段已移除）
                rowData.add(""); // 备注
                
                writer.writeRow(rowData);
                
                // 下一天
                calendar.add(java.util.Calendar.DATE, 1);
            }
            
            // 5. 添加使用说明
            writer.setSheet("使用说明");
            writer.writeCellValue(0, 0, "智能模板使用说明");
            writer.writeCellValue(1, 0, "模板特性：");
            writer.writeCellValue(2, 0, "1. 自动生成指定日期范围的所有日期");
            writer.writeCellValue(3, 0, "2. 自动填充部门信息");
            writer.writeCellValue(4, 0, "3. 数值字段留空，等待用户填写");
            writer.writeCellValue(5, 0, "4. 星期几和节假日由系统自动计算，无需填写");
            writer.writeCellValue(6, 0, "");
            writer.writeCellValue(7, 0, "填写指南：");
            writer.writeCellValue(8, 0, "1. 只需填写数值字段（堂食营业额、订单数、顾客数、外卖营业额、订单数、平台抽成）");
            writer.writeCellValue(9, 0, "2. 金额字段：支持小数，单位：元");
            writer.writeCellValue(10, 0, "3. 数量字段：整数");
            writer.writeCellValue(11, 0, "4. 备注：可选，其他说明信息");
            writer.writeCellValue(12, 0, "5. 星期几和节假日由系统自动计算，无需填写");
            writer.writeCellValue(13, 0, "");
            writer.writeCellValue(14, 0, "上传说明：");
            writer.writeCellValue(15, 0, "1. 填写完成后保存文件");
            writer.writeCellValue(16, 0, "2. 使用上传接口：/ai/daily-revenue/upload-excel");
            writer.writeCellValue(17, 0, "3. 上传时需提供相同的部门ID");
            writer.writeCellValue(18, 0, "4. 系统会自动匹配日期和部门信息");
            
            // 6. 设置数据格式
            writer.setSheet(0); // 回到数据表
            
            // 调整列宽
            for (int i = 0; i < dataHeaders.length; i++) {
                writer.autoSizeColumn(i);
            }
            
            // 标记必填字段
            for (int i = 1; i <= 6; i++) { // 第2-7列为数值字段，需要填写
                Sheet sheet = writer.getSheet();
                if (sheet != null && sheet.getRow(2) != null) {
                    sheet.getRow(2).getCell(i).setCellValue(dataHeaders[i] + " *");
                }
            }
            
            // 7. 生成简单文件名
            String fileName = String.format("daily_revenue_template_%s_%s.xlsx", 
                startDate, 
                endDate);
            
            System.out.println("[DEBUG] 生成文件名：" + fileName);
            
            // 设置响应头 - 使用简单文件名，避免中文问题
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"");
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Expires", "0");
            
            // 8. 直接写入响应流
            try {
                writer.flush(response.getOutputStream(), true);
                writer.close();
                System.out.println("[DEBUG] Excel文件写入完成");
            } catch (Exception e) {
                System.out.println("[DEBUG] 写入Excel时出错：" + e.getMessage());
                throw e;
            }
            
        } catch (ParseException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("text/plain; charset=utf-8");
            response.getWriter().write("日期格式错误，请使用 yyyy-MM-dd 格式，如：2024-03-20");
            return;
        } catch (IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("text/plain; charset=utf-8");
            response.getWriter().write(e.getMessage());
            return;
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("text/plain; charset=utf-8");
            response.getWriter().write("生成智能模板失败: " + e.getMessage());
            return;
        }
    }

    /**
     * 部门菜品日销售 — 智能 Excel 模板（行=菜品：第1列序号、第2列菜品名称，第3列起为 startDate～endDate 各日销量）
     */
    @GetMapping("/download-food-sales-smart-template")
    @Operation(summary = "菜品日销售智能模板", description = "第1列序号、第2列菜品名称，第3列起为日期列；上传后按配方展开为原料消耗")
    public void downloadFoodSalesSmartTemplate(HttpServletResponse response,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate,
            @RequestParam("departmentId") Integer departmentId) throws IOException {

        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            Date start = dateFormat.parse(startDate);
            Date end = dateFormat.parse(endDate);
            if (start.after(end)) {
                throw new IllegalArgumentException("开始日期不能晚于结束日期");
            }
            long days = (end.getTime() - start.getTime()) / (1000 * 60 * 60 * 24) + 1;
            if (days > 365) {
                throw new IllegalArgumentException("日期范围不能超过365天");
            }

            GbDepartmentEntity department = departmentService.getById(departmentId);
            if (department == null) {
                throw new IllegalArgumentException("部门不存在，部门ID: " + departmentId);
            }

            Map<String, Object> depMap = new HashMap<>();
            depMap.put("depId", departmentId);
            List<GbDepFoodEntity> depFoods = gbDepFoodService.queryDepAllFood(depMap);
            attachDistributerFood(depFoods);
            depFoods.sort(Comparator.comparing(GbAiDailyRevenueController::distributerFoodSortKey,
                    Comparator.nullsLast(String::compareTo)));

            cn.hutool.poi.excel.ExcelWriter writer = cn.hutool.poi.excel.ExcelUtil.getWriter();

            List<Object> meta = new ArrayList<>();
            meta.add("部门ID: " + departmentId);
            meta.add("部门名称: " + department.getGbDepartmentName());
            meta.add("日期范围: " + startDate + " 至 " + endDate);
            meta.add("总天数: " + days);
            int dishRowCount = 0;
            for (GbDepFoodEntity f : depFoods) {
                if (includeDepFoodInSalesTemplate(f, department)) {
                    dishRowCount++;
                }
            }
            meta.add("菜品行数(含id): " + dishRowCount);
            meta.add("");
            meta.add("");
            meta.add("");
            meta.add("");
            writer.writeRow(meta);
            writer.writeRow(new ArrayList<>());

            List<Object> dataHeaders = new ArrayList<>();
            dataHeaders.add("序号");
            dataHeaders.add("菜品名称");
            java.util.Calendar calendar = java.util.Calendar.getInstance();
            calendar.setTime(start);
            for (int i = 0; i < days; i++) {
                dataHeaders.add(dateFormat.format(calendar.getTime()));
                calendar.add(java.util.Calendar.DATE, 1);
            }
            writer.writeHeadRow(dataHeaders);

            System.out.println("[DEBUG] ========== 菜品日销售模板 · 菜品名称 departmentId=" + departmentId
                    + " 预计行数=" + dishRowCount + " ==========");
            int skipped = 0;
            int serial = 1;
            for (GbDepFoodEntity f : depFoods) {
                Integer foodId = f.getGbDfFoodId();
                if (foodId == null) {
                    skipped++;
                    continue;
                }
                if (!includeDepFoodInSalesTemplate(f, department)) {
                    skipped++;
                    continue;
                }
                String name = distributerFoodDisplayName(f, foodId);
                System.out.println("[DEBUG] 模板菜品 序号=" + serial + " gb_df_food_id=" + foodId + " 菜品名称=" + name
                        + " 写入第二列=" + name + "（id:" + foodId + "）");
                List<Object> rowData = new ArrayList<>();
                rowData.add(serial++);
                rowData.add(name + "（id:" + foodId + "）");
                for (int i = 0; i < days; i++) {
                    rowData.add("");
                }
                writer.writeRow(rowData);
            }
            System.out.println("[DEBUG] ========== 菜品日销售模板 · 菜品名称打印结束 已写入行数=" + (serial - 1) + " ==========");

            writer.setSheet("使用说明");
            writer.writeCellValue(0, 0, "菜品日销售模板说明");
            writer.writeCellValue(1, 0, "1. 第1列序号、第2列菜品名称（含「（id:数字）」请勿改），第3列起为各日期，在对应格填写该菜当日销量（可小数）");
            writer.writeCellValue(2, 0, "2. 上传接口：POST /ai/daily-revenue/upload-food-sales-excel ，参数 file、departmentId、distributerId");
            writer.writeCellValue(3, 0, "3. 上传后写入 gb_dep_food_sales，并按 gb_distributer_food_goods 单份用量×销量写入 gb_dep_food_goods_sales");
            if (skipped > 0) {
                writer.writeCellValue(4, 0, "4. 当前有 " + skipped + " 条门店菜品未出现在表格中（未配置 gb_df_food_id，或与部门所属批发商不一致）");
            }

            writer.setSheet(0);
            for (int i = 0; i < dataHeaders.size(); i++) {
                writer.autoSizeColumn(i);
            }

            String fileName = String.format("dep_food_sales_template_%s_%s.xlsx", startDate, endDate);
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"");
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            writer.flush(response.getOutputStream(), true);
            writer.close();
        } catch (ParseException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("text/plain; charset=utf-8");
            response.getWriter().write("日期格式错误，请使用 yyyy-MM-dd 格式");
        } catch (IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("text/plain; charset=utf-8");
            response.getWriter().write(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("text/plain; charset=utf-8");
            response.getWriter().write("生成模板失败: " + e.getMessage());
        }
    }

    /**
     * Excel 上传部门菜品日销售，并计算部门菜品原料消耗（gb_dep_food_goods_sales）
     */
    @PostMapping("/upload-food-sales-excel")
    @Operation(summary = "Excel上传菜品日销售", description = "支持「序号|菜品名称|各日期列」模板（及旧版「日期|各菜品列」）；按销量×配方写入原料消耗")
    public R uploadFoodSalesExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam("departmentId") Integer departmentId,
            @RequestParam("distributerId") Integer distributerId) {
        try {
            if (file.isEmpty()) {
                return R.error("请上传Excel文件");
            }
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null
                    || (!originalFilename.toLowerCase().endsWith(".xls")
                    && !originalFilename.toLowerCase().endsWith(".xlsx"))) {
                return R.error("请上传Excel文件（.xls 或 .xlsx 格式）");
            }

            GbDepartmentEntity department = departmentService.getById(departmentId);
            if (department == null) {
                return R.error("部门不存在");
            }

            Map<String, Object> depMap = new HashMap<>();
            depMap.put("depId", departmentId);
            List<GbDepFoodEntity> depFoods = gbDepFoodService.queryDepAllFood(depMap);
            attachDistributerFood(depFoods);
            Set<Integer> allowedFoodIds = new HashSet<>();
            for (GbDepFoodEntity f : depFoods) {
                if (f.getGbDfFoodId() == null) {
                    continue;
                }
                GbDistributerFoodEntity disFood = f.getGbDistributerFoodEntity();
                if (disFood != null && disFood.getGbDfDistributerId() != null
                        && !disFood.getGbDfDistributerId().equals(distributerId)) {
                    continue;
                }
                allowedFoodIds.add(f.getGbDfFoodId());
            }

            List<Map.Entry<Date, Map<Integer, BigDecimal>>> rows = readFoodSalesExcel(file);
            if (rows.isEmpty()) {
                return R.error("Excel文件中没有有效的菜品销售数据");
            }

            Map<String, Object> stats = gbDepFoodSalesExcelImportService.importFoodSales(
                    departmentId, distributerId, department, allowedFoodIds, rows);
            R ret = R.ok();
            ret.put("rows", rows.size());
            ret.putAll(stats);
            return ret;
        } catch (IOException e) {
            e.printStackTrace();
            return R.error("文件读取失败：" + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return R.error("Excel解析或保存失败：" + e.getMessage());
        }
    }

    private List<Map.Entry<Date, Map<Integer, BigDecimal>>> readFoodSalesExcel(MultipartFile file)
            throws IOException, ParseException {
        cn.hutool.poi.excel.ExcelReader reader = cn.hutool.poi.excel.ExcelUtil.getReader(file.getInputStream());
        List<List<Object>> rows = reader.read();

        Integer pivotHeaderRow = findFoodSalesPivotHeaderRow(rows);
        if (pivotHeaderRow != null) {
            return readFoodSalesExcelPivotLayout(rows, pivotHeaderRow);
        }
        return readFoodSalesExcelLegacyDateRows(rows);
    }

    /**
     * 新模板：表头为 序号 | 菜品名称 | yyyy-MM-dd ...
     */
    private List<Map.Entry<Date, Map<Integer, BigDecimal>>> readFoodSalesExcelPivotLayout(
            List<List<Object>> rows, int headerRowIndex) throws ParseException {
        List<Map.Entry<Date, Map<Integer, BigDecimal>>> out = new ArrayList<>();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        List<Object> header = rows.get(headerRowIndex);
        int dataStartRow = headerRowIndex + 1;

        List<String> dateKeys = new ArrayList<>();
        for (int c = 2; c < header.size(); c++) {
            String dk = parseFlexibleDateCellToDayKey(header.get(c), dateFormat);
            if (dk != null) {
                dateKeys.add(dk);
            } else {
                dateKeys.add(null);
            }
        }

        Map<String, Map<Integer, BigDecimal>> byDateKey = new LinkedHashMap<>();

        for (int i = dataStartRow; i < rows.size(); i++) {
            List<Object> row = rows.get(i);
            if (row.size() < 3) {
                continue;
            }
            if (row.get(1) == null) {
                continue;
            }
            Integer foodId = parseFoodIdFromHeader(row.get(1));
            if (foodId == null) {
                continue;
            }

            for (int j = 0; j < dateKeys.size(); j++) {
                String dk = dateKeys.get(j);
                if (dk == null) {
                    continue;
                }
                int col = 2 + j;
                if (col >= row.size()) {
                    continue;
                }
                Object cell = row.get(col);
                if (cell == null || cell.toString().trim().isEmpty()) {
                    continue;
                }
                try {
                    BigDecimal q = new BigDecimal(cell.toString().trim());
                    if (q.compareTo(BigDecimal.ZERO) <= 0) {
                        continue;
                    }
                    Map<Integer, BigDecimal> qtyByFood = byDateKey.computeIfAbsent(dk, k -> new LinkedHashMap<>());
                    qtyByFood.merge(foodId, q, BigDecimal::add);
                } catch (NumberFormatException ignored) {
                }
            }
        }

        for (Map.Entry<String, Map<Integer, BigDecimal>> e : byDateKey.entrySet()) {
            if (!e.getValue().isEmpty()) {
                out.add(new AbstractMap.SimpleEntry<>(dateFormat.parse(e.getKey()), e.getValue()));
            }
        }
        return out;
    }

    /**
     * 旧模板：首列为日期，后续列为「菜名（id:n）」
     */
    private List<Map.Entry<Date, Map<Integer, BigDecimal>>> readFoodSalesExcelLegacyDateRows(
            List<List<Object>> rows) throws ParseException {
        List<Map.Entry<Date, Map<Integer, BigDecimal>>> out = new ArrayList<>();
        int headerRowIndex;
        int dataStartRow;
        if (rows.size() >= 3
                && rows.get(2).size() > 0
                && rows.get(2).get(0) != null
                && rows.get(2).get(0).toString().contains("日期")) {
            headerRowIndex = 2;
            dataStartRow = 3;
        } else if (rows.size() >= 1
                && rows.get(0).size() > 0
                && rows.get(0).get(0) != null
                && rows.get(0).get(0).toString().contains("日期")) {
            headerRowIndex = 0;
            dataStartRow = 1;
        } else {
            throw new IllegalArgumentException("未识别表头：请使用「序号|菜品名称|日期列…」或旧版「日期|各菜品列」");
        }

        List<Object> header = rows.get(headerRowIndex);
        List<Integer> colFoodIds = new ArrayList<>();
        colFoodIds.add(null);
        for (int c = 1; c < header.size(); c++) {
            colFoodIds.add(parseFoodIdFromHeader(header.get(c)));
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Map<String, Map<Integer, BigDecimal>> byDateKey = new LinkedHashMap<>();

        for (int i = dataStartRow; i < rows.size(); i++) {
            List<Object> row = rows.get(i);
            if (row.isEmpty() || row.get(0) == null) {
                continue;
            }
            Object dateCell = row.get(0);
            Date recordDate;
            if (dateCell instanceof Date) {
                recordDate = (Date) dateCell;
            } else {
                String ds = dateCell.toString().trim();
                if (ds.isEmpty()) {
                    continue;
                }
                recordDate = dateFormat.parse(ds);
            }
            String dk = dateFormat.format(recordDate);
            Map<Integer, BigDecimal> qtyByFood = byDateKey.computeIfAbsent(dk, k -> new LinkedHashMap<>());

            for (int c = 1; c < colFoodIds.size() && c < row.size(); c++) {
                Integer fid = colFoodIds.get(c);
                if (fid == null) {
                    continue;
                }
                Object cell = row.get(c);
                if (cell == null || cell.toString().trim().isEmpty()) {
                    continue;
                }
                try {
                    BigDecimal q = new BigDecimal(cell.toString().trim());
                    if (q.compareTo(BigDecimal.ZERO) <= 0) {
                        continue;
                    }
                    qtyByFood.merge(fid, q, BigDecimal::add);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        for (Map.Entry<String, Map<Integer, BigDecimal>> e : byDateKey.entrySet()) {
            if (!e.getValue().isEmpty()) {
                out.add(new AbstractMap.SimpleEntry<>(dateFormat.parse(e.getKey()), e.getValue()));
            }
        }
        return out;
    }

    private static Integer findFoodSalesPivotHeaderRow(List<List<Object>> rows) {
        int maxScan = Math.min(rows.size(), 15);
        for (int r = 0; r < maxScan; r++) {
            List<Object> row = rows.get(r);
            if (row == null || row.size() < 3) {
                continue;
            }
            Object c0 = row.get(0);
            Object c1 = row.get(1);
            Object c2 = row.get(2);
            if (c0 == null || c1 == null || c2 == null) {
                continue;
            }
            String s0 = c0.toString().trim();
            String s1 = c1.toString().trim();
            if (!s0.contains("序号")) {
                continue;
            }
            if (!s1.contains("菜品")) {
                continue;
            }
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
            if (parseFlexibleDateCellToDayKey(c2, df) != null) {
                return r;
            }
        }
        return null;
    }

    private static String parseFlexibleDateCellToDayKey(Object cell, SimpleDateFormat dayFmt) {
        if (cell == null) {
            return null;
        }
        if (cell instanceof Date) {
            return dayFmt.format((Date) cell);
        }
        String s = cell.toString().trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return dayFmt.format(dayFmt.parse(s));
        } catch (ParseException ignored) {
        }
        return null;
    }

    private static Integer parseFoodIdFromHeader(Object cell) {
        if (cell == null) {
            return null;
        }
        String s = cell.toString();
        Matcher m = FOOD_HEADER_ID_ZH.matcher(s);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        m = FOOD_HEADER_ID_EN.matcher(s);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return null;
    }

    /**
     * 按 gb_df_food_id 查询 gb_distributer_food，写入 depFood.gbDistributerFoodEntity
     */
    private void attachDistributerFood(List<GbDepFoodEntity> depFoods) {
        for (GbDepFoodEntity f : depFoods) {
            if (f.getGbDfFoodId() == null) {
                continue;
            }
            GbDistributerFoodEntity disFood = gbDistributerFoodService.queryObject(f.getGbDfFoodId());
            f.setGbDistributerFoodEntity(disFood);
        }
    }

    /** 排序键：优先批发商菜品名称 gb_df_food_name */
    private static String distributerFoodSortKey(GbDepFoodEntity f) {
        if (f.getGbDistributerFoodEntity() != null) {
            String n = f.getGbDistributerFoodEntity().getGbDfFoodName();
            if (n != null && !n.isEmpty()) {
                return n;
            }
        }
        return f.getGbDfFoodName();
    }

    /**
     * 模板中展示：已配置批发商菜品 id，且（若部门有 disId）该菜品属于同一批发商
     */
    private static boolean includeDepFoodInSalesTemplate(GbDepFoodEntity f, GbDepartmentEntity department) {
        if (f.getGbDfFoodId() == null) {
            return false;
        }
        Integer depDisId = department.getGbDepartmentDisId();
        GbDistributerFoodEntity d = f.getGbDistributerFoodEntity();
        if (depDisId != null && d != null && d.getGbDfDistributerId() != null
                && !d.getGbDfDistributerId().equals(depDisId)) {
            return false;
        }
        return true;
    }

    /** 展示名：优先 GbDistributerFoodEntity.gbDfFoodName，否则门店侧名称 */
    private static String distributerFoodDisplayName(GbDepFoodEntity f, int distributerFoodId) {
        if (f.getGbDistributerFoodEntity() != null) {
            String n = f.getGbDistributerFoodEntity().getGbDfFoodName();
            if (n != null && !n.trim().isEmpty()) {
                return n.trim();
            }
        }
        if (f.getGbDfFoodName() != null && !f.getGbDfFoodName().trim().isEmpty()) {
            return f.getGbDfFoodName().trim();
        }
        return "菜品" + distributerFoodId;
    }
}
