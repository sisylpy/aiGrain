package com.nongxinle.controller;

import com.nongxinle.entity.GbAiDailyRevenueEntity;
import com.nongxinle.entity.GbAiRestaurantProfileEntity;
import com.nongxinle.service.GbAiDailyRevenueDashboardService;
import com.nongxinle.service.GbAiDailyRevenueExcelService;
import com.nongxinle.service.GbAiDailyRevenueService;
import com.nongxinle.service.GbAiRestaurantProfileService;
import com.nongxinle.service.GbDepFoodSalesExcelImportService;
import com.nongxinle.utils.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

/**
 * 日营业额 Controller
 * 餐厅经营分析看板接口
 */
@Slf4j
@RestController
@RequestMapping("ai/daily-revenue")
@Tag(name = "日营业额接口")
@RequiredArgsConstructor
public class GbAiDailyRevenueController {

    private final GbAiDailyRevenueService dailyRevenueService;
    private final GbAiDailyRevenueDashboardService dailyRevenueDashboardService;
    private final GbAiDailyRevenueExcelService dailyRevenueExcelService;
    private final GbAiRestaurantProfileService profileService;
    private final GbDepFoodSalesExcelImportService gbDepFoodSalesExcelImportService;

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

        Map<String, Object> data = dailyRevenueDashboardService.buildStatsDashboard(departmentId, profile, stats);
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
        Map<String, Object> result = dailyRevenueService.buildListPayload(departmentId, startDate, endDate);
        if (result == null) {
            return R.error("暂无营业额数据");
        }
        return R.ok(result);
    }

    /**
     * 保存单条日营业额
     */
    @PostMapping("/save")
    @Operation(summary = "保存日营业额", description = "按部门+记录日保存；该日已存在则覆盖更新（与唯一键一致）")
    public R save(@RequestBody GbAiDailyRevenueEntity dailyRevenue) {
        try {
            dailyRevenueService.saveOrUpsertByDepartmentAndDate(dailyRevenue);
            return R.ok();
        } catch (IllegalArgumentException e) {
            return R.error(e.getMessage());
        }
    }

    /**
     * 更新日营业额
     */
    @PostMapping("/update")
    @Operation(summary = "更新日营业额", description = "更新日营业额记录")
    public R update(@RequestBody GbAiDailyRevenueEntity dailyRevenue) {
        dailyRevenueService.fillUpdateWeekday(dailyRevenue);
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
     * Excel 上传日营业额；同一部门（参数 departmentId）+ 同一记录日已存在则覆盖更新
     */
    @PostMapping("/upload-excel")
    @Operation(summary = "Excel上传日营业额",
            description = "批量导入日营业额；与参数 departmentId + Excel 中各日期为同一业务键时已存在则更新，否则插入；返回 total/inserted/updated 等")
    public R uploadExcel(
            @Parameter(description = "Excel文件") @RequestParam("file") MultipartFile file,
            @Parameter(description = "部门ID") @RequestParam("departmentId") Long departmentId,
            @Parameter(description = "分配者ID") @RequestParam("distributerId") Long distributerId) {
        try {
            Map<String, Object> result = dailyRevenueService.importDailyRevenueFromExcel(file, departmentId, distributerId);
            R ret = R.ok();
            ret.putAll(result);
            return ret;
        } catch (IllegalArgumentException e) {
            return R.error(e.getMessage());
        } catch (IOException e) {
            log.warn("upload-excel read failed", e);
            return R.error("文件读取失败：" + e.getMessage());
        } catch (Exception e) {
            log.warn("upload-excel failed", e);
            return R.error("Excel解析失败：" + e.getMessage());
        }
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
        dailyRevenueExcelService.writeDailyRevenueSmartTemplate(response, startDate, endDate, departmentId);
    }


    /**
     * 部门菜品日销售 — 智能 Excel 模板（行=菜品：第1列序号、第2列部门名称（含部门id）、第3列菜品名称，第4列起为日期销量）
     */
    @GetMapping("/download-food-sales-smart-template")
    @Operation(summary = "菜品日销售智能模板", description = "第1列序号、第2列部门名称（含部门id）、第3列菜品名称，第4列起为日期列；上传按部门id+菜品id匹配部门菜品并展开原料消耗")
    public void downloadFoodSalesSmartTemplate(HttpServletResponse response,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate,
            @RequestParam("departmentId") Integer departmentId) throws IOException {
        dailyRevenueExcelService.writeFoodSalesSmartTemplate(response, startDate, endDate, departmentId);
    }


    /**
     * Excel 上传部门菜品日销售；同一子部门 + 同一菜品 + 同一自然日已存在则覆盖并重建原料行（gb_dep_food_goods_sales）
     */
    @PostMapping("/upload-food-sales-excel")
    @Operation(summary = "Excel上传菜品日销售",
            description = "支持「序号|部门名称|菜品名称|各日期列」模板（兼容旧版）；按部门列子部门id+菜品id匹配 gb_dep_food；子部门+菜品+日期 已存在则更新销量并替换原料展开，否则插入。导入完成后按父部门+自然日汇总菜品销售小计，写入 gb_ai_daily_revenue 的堂食营业额（已有记录则仅覆盖堂食字段）。返回 inserted/updated、dailyRevenueDaysSynced 等")
    public R uploadFoodSalesExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam("departmentId") Integer departmentId,
            @RequestParam("distributerId") Integer distributerId) {
        try {
            Map<String, Object> result = gbDepFoodSalesExcelImportService.importFoodSalesFromExcelMultipart(
                    file, departmentId, distributerId);
            R ret = R.ok();
            ret.putAll(result);
            return ret;
        } catch (IllegalArgumentException e) {
            return R.error(e.getMessage());
        } catch (IOException e) {
            log.warn("upload-food-sales-excel read failed", e);
            return R.error("文件读取失败：" + e.getMessage());
        } catch (Exception e) {
            log.warn("upload-food-sales-excel failed", e);
            return R.error("Excel解析或保存失败：" + e.getMessage());
        }
    }
}
