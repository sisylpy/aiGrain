package com.nongxinle.service;

import com.nongxinle.entity.GbAiDailyRevenueEntity;
import com.nongxinle.entity.GbDepFoodEntity;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 日营业额与菜品日销售相关的 Excel 解析与模板输出。
 */
public interface GbAiDailyRevenueExcelService {

    /**
     * 校验上传为 Excel（非空、扩展名 .xls/.xlsx）。
     *
     * @throws IllegalArgumentException 不符合时抛出，消息与原先 Controller 一致
     */
    void assertSpreadsheetUpload(MultipartFile file);

    /** 日营业额上传：解析 Excel 行为实体列表（含表头识别、去重）。 */
    List<GbAiDailyRevenueEntity> parseDailyRevenueExcel(MultipartFile file, Long departmentId, Long distributerId)
            throws IOException;

    /** 日营业额智能模板下载，直接写入 {@link HttpServletResponse}。 */
    void writeDailyRevenueSmartTemplate(HttpServletResponse response, String startDate, String endDate, Integer departmentId)
            throws IOException;

    /** 菜品日销售智能模板下载。 */
    void writeFoodSalesSmartTemplate(HttpServletResponse response, String startDate, String endDate, Integer departmentId)
            throws IOException;

    /** 菜品日销售 Excel 解析（新/旧表头）。 */
    List<Map.Entry<Date, Map<Integer, BigDecimal>>> parseFoodSalesExcel(MultipartFile file) throws IOException;

    /** 为门店菜品列表填充批发商菜品实体（上传/模板共用）。 */
    void attachDistributerFood(List<GbDepFoodEntity> depFoods);

    /** 模板行排序：优先批发商菜品名称。 */
    Comparator<GbDepFoodEntity> depFoodTemplateRowComparator();
}
