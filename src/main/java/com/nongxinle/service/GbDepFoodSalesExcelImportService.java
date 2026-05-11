package com.nongxinle.service;

import com.nongxinle.dto.GbDepFoodDailySalesSubmitRequest;
import com.nongxinle.entity.GbDepartmentEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 部门菜品日销售 Excel 导入：写入菜品销售并按配方展开为原料消耗。
 */
public interface GbDepFoodSalesExcelImportService {

    Map<String, Object> importFoodSales(
            Integer departmentId,
            Integer distributerId,
            GbDepartmentEntity department,
            Set<Integer> allowedFoodIds,
            List<Map.Entry<Date, Map<Integer, BigDecimal>>> cellQuantities);

    /**
     * API 提交某日各菜品销量 + 日营业额指标（堂食金额仍由菜品小计汇总写入）。
     * <p>提交前会删除本请求范围内、当日的整菜与配料销量再以本次 {@code lines} 重建；无有效行则清空该范围内当日菜品销量（末次写入为准）。</p>
     */
    Map<String, Object> submitDailyFoodSalesAndRevenue(GbDepFoodDailySalesSubmitRequest request);

    /**
     * 查询某日（默认中国时区当天）菜品销售行 + 同日 {@code gb_ai_daily_revenue} 非堂食等字段，
     * 返回体可直接作为 {@link #submitDailyFoodSalesAndRevenue} / {@link #updateDailyFoodSalesAndRevenue} 编辑回传的参考结构。
     */
    Map<String, Object> getDailyFoodSalesAndRevenue(Integer depFatherId, Integer distributerId, String recordDate,
            Integer subDepId);

    /** 覆盖/更新某日销售与营业额指标：与 {@link #submitDailyFoodSalesAndRevenue} 同一套写入逻辑。 */
    Map<String, Object> updateDailyFoodSalesAndRevenue(GbDepFoodDailySalesSubmitRequest request);

    /**
     * 从上传的 Excel 完整处理：校验文件、部门、解析表格并调用 {@link #importFoodSales}。
     * 返回 Map 含 {@code rows} 及 importFoodSales 的统计字段。
     */
    Map<String, Object> importFoodSalesFromExcelMultipart(MultipartFile file, Integer departmentId, Integer distributerId)
            throws IOException;

    /**
     * 同上，从指定 Sheet 读取菜品销售（合并模板用）；{@code allowEmptyFoodSheet} 为 true 且该 Sheet 无有效销量时不抛错。
     */
    Map<String, Object> importFoodSalesFromExcelMultipart(MultipartFile file, Integer departmentId, Integer distributerId,
            int sheetIndex, boolean allowEmptyFoodSheet)
            throws IOException;
}
