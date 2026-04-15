package com.nongxinle.controller;

import com.nongxinle.entity.GbAiDailyRevenueEntity;
import com.nongxinle.entity.GbAiRestaurantProfileEntity;
import com.nongxinle.entity.GbDepartmentEntity;
import com.nongxinle.service.GbAiDailyRevenueService;
import com.nongxinle.service.GbAiRestaurantProfileService;
import com.nongxinle.service.GbDepartmentService;
import com.nongxinle.service.GbDepartmentGoodsStockReduceService;
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
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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


    /**
     * 获取营业额统计
     *
     * @Description 获取餐厅营业额统计，包含日均营业额、固定开支、成本支出、毛利率、盈亏状态
     * 
     * 业务字段说明：
     * 1. 外卖算法：外卖净收入 = 外卖营业额 - 平台抽成
     * 2. 成本支出包括：生产成本、损耗成本（废气）、损失成本、退货成本
     * 3. 制作成本 = 生产成本 + 损耗成本 + 损失成本
     * 4. 毛利率 = (净收入 - 总成本) / 净收入 × 100%
     * 
     * 返回字段（stats对象中的字段）：
     * - days: 统计天数
     * - avgDailyRevenue: 日均营业额
     * - totalRevenue: 总营业额
     * - avgOrderCount: 日均订单数
     * - avgPerCustomer: 客单价（人均消费）
     * - totalCouponAmount: 总优惠券金额
     * - totalRefundAmount: 总退款金额
     * - maxDailyRevenue: 最高日营业额
     * - minDailyRevenue: 最低日营业额
     * - avgFixedCost: 日均固定成本（工资+租金）
     * - monthlyWage: 月工资总额

     * - monthlyRent: 月租金
     * - avgNetRevenue: 日均净收入（扣除优惠券）

     * - totalTakeoutRevenue: 总外卖营业额
     * - avgTakeoutRevenue: 日均外卖营业额
     * - totalTakeoutNet: 总外卖净收入（扣除平台抽成）

     * - avgTakeoutNet: 日均外卖净收入
     * - produceCost: 生产成本（type=1）

     * - wasteCost: 损耗成本（type=2，废气等）
     * - lossCost: 损失成本（type=3）

     * - returnCost: 退货成本（type=4）
     * - productionCost: 制作成本（生产+损耗+损失）

     * - totalCost: 总成本（制作成本+退货成本）
     * - grossProfitMargin: 毛利率（百分比数值）

     * - grossProfitMarginPercent: 毛利率（字符串格式，带%）
     * - breakEvenPoint: 盈亏平衡点（日均固定成本）

     * - profitAmount: 原有利润（不考虑成本）
     * - profitAfterCost: 考虑成本后的实际利润

     * - actualProfit: 实际利润（同profitAfterCost）

     * - status: 盈亏状态代码（profit/breakeven/loss）
     * - statusDesc: 盈亏状态描述（盈利中/保本/亏损）
     * 
     * 完整数据结构请参考：GbAiDailyRevenueStatsDTO
     * 
     * @param departmentId 部门/餐厅ID

     * @return 包含统计数据和画像信息的JSON对象
     */
    @GetMapping("/stats/{departmentId}")
    @Operation(summary = "获取营业额统计", description = "获取餐厅营业额统计，包含日均营业额、固定开支、成本支出、毛利率、盈亏状态")
    public R getStats(@Parameter(description = "部门/餐厅ID") @PathVariable Long departmentId) {
        // 获取画像数据（包含固定开支）
        GbAiRestaurantProfileEntity profile = profileService.getByDepartmentId(departmentId);
        if (profile == null) {
            return R.error("餐厅画像不存在");
        }

        // 获取营业额统计
        Map<String, Object> stats = dailyRevenueService.getStatsByDepartmentId(departmentId);
        if (stats == null || stats.get("days") == null || ((Number) stats.get("days")).intValue() == 0) {
            return R.error("暂无营业额数据");
        }

        // 构建返回数据
        Map<String, Object> result = new HashMap<>();

        // 基本统计
        int days = ((Number) stats.get("days")).intValue();
        result.put("days", days);

        // 金额统计
        result.put("avgDailyRevenue", toDecimal(stats.get("avg_daily_revenue")));
        result.put("totalRevenue", toDecimal(stats.get("total_revenue")));
        result.put("avgOrderCount", toDecimal(stats.get("avg_order_count")));
        result.put("avgPerCustomer", toDecimal(stats.get("avg_per_customer")));
        result.put("totalCouponAmount", toDecimal(stats.get("total_coupon_amount")));
        result.put("totalRefundAmount", toDecimal(stats.get("total_refund_amount")));
        result.put("maxDailyRevenue", toDecimal(stats.get("max_daily_revenue")));
        result.put("minDailyRevenue", toDecimal(stats.get("min_daily_revenue")));

        // 固定开支（从画像获取，计算日均）
        BigDecimal monthlyWage = profile.getGbAiRestaurantProfileMonthlyWage() != null
                ? profile.getGbAiRestaurantProfileMonthlyWage() : BigDecimal.ZERO;
        BigDecimal monthlyRent = profile.getGbAiRestaurantProfileRentMonthly() != null
                ? profile.getGbAiRestaurantProfileRentMonthly() : BigDecimal.ZERO;
        BigDecimal monthlyFixedCost = monthlyWage.add(monthlyRent);
        BigDecimal dailyFixedCost = monthlyFixedCost.divide(BigDecimal.valueOf(30), 2, RoundingMode.HALF_UP);

        result.put("avgFixedCost", dailyFixedCost);
        result.put("monthlyWage", monthlyWage);
        result.put("monthlyRent", monthlyRent);

        // 净收入（日均营业额 - 优惠券 - 退款）
        BigDecimal totalCoupon = toDecimal(stats.get("total_coupon_amount"));
        BigDecimal totalRefund = toDecimal(stats.get("total_refund_amount"));
        BigDecimal avgNetRevenue = toDecimal(stats.get("avg_daily_revenue")).subtract(totalCoupon.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP));
        result.put("avgNetRevenue", avgNetRevenue);

        // 外卖相关统计
        result.put("totalTakeoutRevenue", toDecimal(stats.get("total_takeout_revenue")));
        result.put("avgTakeoutRevenue", toDecimal(stats.get("avg_takeout_revenue")));
        result.put("totalTakeoutNet", toDecimal(stats.get("total_takeout_net")));
        result.put("avgTakeoutNet", toDecimal(stats.get("avg_takeout_net")));

        // 计算成本支出（从部门商品库存减少表）
        Map<String, Object> costParams = new HashMap<>();
        costParams.put("departmentId", departmentId);
        Map<String, Object> costStats = stockReduceService.queryReduceAllTypesTotal(costParams);
        
        // 成本支出统计
        BigDecimal produceCost = toDecimal(costStats.get("produceTotal"));  // 生产成本
        BigDecimal wasteCost = toDecimal(costStats.get("wasteTotal"));      // 损耗成本
        BigDecimal lossCost = toDecimal(costStats.get("lossTotal"));        // 损失成本
        BigDecimal returnCost = toDecimal(costStats.get("returnTotal"));    // 退货成本
        
        // 制作成本 = 生产 + 损耗 + 损失
        BigDecimal productionCost = produceCost.add(wasteCost).add(lossCost);
        BigDecimal totalCost = productionCost.add(returnCost);  // 总成本
        
        result.put("produceCost", produceCost);
        result.put("wasteCost", wasteCost);
        result.put("lossCost", lossCost);
        result.put("returnCost", returnCost);
        result.put("productionCost", productionCost);  // 制作成本
        result.put("totalCost", totalCost);            // 总成本

        // 计算毛利率（如果净收入大于0）
        BigDecimal grossProfitMargin = BigDecimal.ZERO;
        BigDecimal totalNetRevenue = toDecimal(stats.get("total_revenue")).subtract(totalCoupon);
        if (totalNetRevenue.compareTo(BigDecimal.ZERO) > 0) {
            // 毛利率 = (净收入 - 总成本) / 净收入 × 100%
            grossProfitMargin = totalNetRevenue.subtract(totalCost)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(totalNetRevenue, 2, RoundingMode.HALF_UP);
        }
        result.put("grossProfitMargin", grossProfitMargin);
        result.put("grossProfitMarginPercent", grossProfitMargin + "%");

        // 盈亏平衡点
        result.put("breakEvenPoint", dailyFixedCost);

        // 盈亏状态（考虑成本后的实际利润）
        BigDecimal profitAfterCost = avgNetRevenue.subtract(totalCost.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP))
                .subtract(dailyFixedCost);
        BigDecimal profit = avgNetRevenue.subtract(dailyFixedCost);  // 原有利润计算（不考虑成本）
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
        result.put("status", status);
        result.put("statusDesc", statusDesc);
        result.put("profitAmount", profit);
        result.put("profitAfterCost", profitAfterCost);
        result.put("actualProfit", actualProfit);

        // 返回统计数据和画像
        Map<String, Object> data = new HashMap<>();
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
                    
                    // 使用简单的保存逻辑：先尝试更新，如果失败则插入
                    // 这里我们直接使用save方法，MyBatis Plus的save方法会处理重复键问题
                    // 但我们需要手动处理：先查询是否存在，存在则更新，不存在则插入
                    
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
     * 下载Excel导入模板（基础模板）
     */
//    @GetMapping("/download-template")
//    @Operation(summary = "下载Excel导入模板", description = "下载日营业额Excel导入模板文件")
//    public ResponseEntity<Resource> downloadTemplate() {
//        try {
//            // 创建Excel文件
//            cn.hutool.poi.excel.ExcelWriter writer = cn.hutool.poi.excel.ExcelUtil.getWriter();
//
//            // 设置表头（去掉星期几和节假日，由后台自动计算）
//            String[] headers = {
//                "日期",
//                "堂食营业额",
//                "堂食订单数",
//                "堂食顾客数",
//                "外卖营业额",
//                "外卖订单数",
//                "平台抽成",
//                "备注"
//            };
//
//            // 写入表头
//            writer.writeHeadRow(Arrays.asList(headers));
//
//            // 添加示例数据（3行示例）
//            List<Object> row1 = Arrays.asList(
//                "2024-03-20",
//                12500.50,
//                156,
//                120,
//                8500.00,
//                85,
//                850.00,
//                "天气好"
//            );
//            writer.writeRow(row1);
//
//            List<Object> row2 = Arrays.asList(
//                "2024-03-21",
//                9800.00,
//                120,
//                95,
//                7200.50,
//                72,
//                720.05,
//                ""
//            );
//            writer.writeRow(row2);
//
//            List<Object> row3 = Arrays.asList(
//                "2024-03-22",
//                15000.00,
//                180,
//                150,
//                9200.00,
//                92,
//                920.00,
//                "节日促销"
//            );
//            writer.writeRow(row3);
//
//            // 添加数据验证说明
//            writer.setSheet("使用说明");
//            writer.writeCellValue(0, 0, "日营业额Excel导入模板使用说明");
//            writer.writeCellValue(1, 0, "1. 日期格式：yyyy-MM-dd，如：2024-03-20");
//            writer.writeCellValue(2, 0, "2. 金额字段：支持小数，单位：元");
//            writer.writeCellValue(3, 0, "3. 数量字段：整数");
//            writer.writeCellValue(4, 0, "4. 星期几由系统自动计算，无需填写");
//            writer.writeCellValue(5, 0, "5. 节假日字段已移除，由系统处理");
//            writer.writeCellValue(6, 0, "6. 备注：可选，其他说明信息");
//            writer.writeCellValue(7, 0, "7. 星号(*)列为必填项");
//
//            // 标记必填字段
//            writer.setSheet(0); // 回到数据表
//            for (int i = 0; i < 7; i++) { // 前7列为必填（日期 + 6个数值字段）
//                writer.writeCellValue(0, i, headers[i] + " *");
//            }
//
//            // 调整列宽
//            for (int i = 0; i < headers.length; i++) {
//                writer.autoSizeColumn(i);
//            }
//
//            // 写入到字节数组
//            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
//            writer.flush(outputStream);
//            writer.close();
//
//            byte[] excelBytes = outputStream.toByteArray();
//
//            // 创建资源对象
//            ByteArrayResource resource = new ByteArrayResource(excelBytes);
//
//            // 设置响应头
//            HttpHeaders httpHeaders = new HttpHeaders();
//            httpHeaders.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"日营业额导入模板.xlsx\"");
//            httpHeaders.add(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
//            httpHeaders.add("Cache-Control", "no-cache, no-store, must-revalidate");
//            httpHeaders.add("Pragma", "no-cache");
//            httpHeaders.add("Expires", "0");
//
//            return ResponseEntity.ok()
//                    .headers(httpHeaders)
//                    .contentLength(excelBytes.length)
//                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
//                    .body(resource);
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            throw new RuntimeException("生成Excel模板失败: " + e.getMessage());
//        }
//    }

    /**
     * 下载示例数据模板（CSV格式）
     */
//    @GetMapping("/download-sample")
//    @Operation(summary = "下载示例数据模板", description = "下载日营业额示例数据模板（CSV格式）")
//    public ResponseEntity<Resource> downloadSampleTemplate() {
//        try {
//            // 创建CSV内容（去掉星期几和节假日）
//            String csvContent = "日期,堂食营业额,堂食订单数,堂食顾客数,外卖营业额,外卖订单数,平台抽成,备注\n" +
//                    "2024-03-20,12500.50,156,120,8500.00,85,850.00,天气好\n" +
//                    "2024-03-21,9800.00,120,95,7200.50,72,720.05,\n" +
//                    "2024-03-22,15000.00,180,150,9200.00,92,920.00,节日促销\n" +
//                    "2024-03-23,13500.00,165,130,7800.00,78,780.00,周末促销\n" +
//                    "2024-03-24,11000.00,140,110,6500.00,65,650.00,\n" +
//                    "# 使用说明：\n" +
//                    "# 1. 日期格式：yyyy-MM-dd\n" +
//                    "# 2. 金额字段：支持小数，单位：元\n" +
//                    "# 3. 数量字段：整数\n" +
//                    "# 4. 备注：可选，其他说明信息\n" +
//                    "# 5. 星号列为必填项：前7列必填\n" +
//                    "# 注意：星期几由系统自动计算，节假日字段已移除，由系统处理";
//
//            byte[] csvBytes = csvContent.getBytes("UTF-8");
//
//            // 创建资源对象
//            ByteArrayResource resource = new ByteArrayResource(csvBytes);
//
//            // 设置响应头
//            HttpHeaders httpHeaders = new HttpHeaders();
//            httpHeaders.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"日营业额示例数据.csv\"");
//            httpHeaders.add(HttpHeaders.CONTENT_TYPE, "text/csv; charset=utf-8");
//            httpHeaders.add("Cache-Control", "no-cache, no-store, must-revalidate");
//            httpHeaders.add("Pragma", "no-cache");
//            httpHeaders.add("Expires", "0");
//
//            return ResponseEntity.ok()
//                    .headers(httpHeaders)
//                    .contentLength(csvBytes.length)
//                    .contentType(MediaType.parseMediaType("text/csv; charset=utf-8"))
//                    .body(resource);
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            throw new RuntimeException("生成CSV模板失败: " + e.getMessage());
//        }
//    }

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
}
