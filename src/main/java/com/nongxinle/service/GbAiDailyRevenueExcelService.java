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

    /** 菜品日销售模板单元：某天、某父级部门、某菜品的销量。 */
    class FoodSalesExcelCell {
        private final Date recordDate;
        private final Integer depId;
        private final Integer foodRefId;
        private final BigDecimal amount;

        public FoodSalesExcelCell(Date recordDate, Integer depId, Integer foodRefId, BigDecimal amount) {
            this.recordDate = recordDate;
            this.depId = depId;
            this.foodRefId = foodRefId;
            this.amount = amount;
        }

        public Date getRecordDate() {
            return recordDate;
        }

        public Integer getDepId() {
            return depId;
        }

        public Integer getFoodRefId() {
            return foodRefId;
        }

        public BigDecimal getAmount() {
            return amount;
        }
    }

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

    /**
     * 合并模板：Sheet「菜品日销售」+「日营业额」（后者不含堂食/当日营业额列，堂食由菜品汇总写入）。
     * 另含「使用说明」Sheet。
     */
    void writeDailyRevenueFoodCombinedTemplate(HttpServletResponse response, String startDate, String endDate, Integer departmentId)
            throws IOException;

    /** 合并模板：菜品 Sheet 名称（与 {@link #writeDailyRevenueFoodCombinedTemplate} 一致）。 */
    String COMBINED_SHEET_FOOD_NAME = "菜品日销售";
    /** 合并模板：日营业额 Sheet 名称（无堂食/当日营业额列）。 */
    String COMBINED_SHEET_REVENUE_NAME = "日营业额";

    /** 按工作簿定位合并模板中菜品、日营业额 Sheet 的下标（优先按名称；兼容 Numbers 导出「导出摘要」与工作表重命名）。 */
    int[] resolveCombinedTemplateFoodAndRevenueSheetIndexes(byte[] spreadsheetBytes) throws IOException;

    /**
     * 解析「菜品日销售」所在 Sheet 下标：跳过 Numbers 导出摘要等无关表，找首表含 序号+菜品+日期 透视或旧版「日期|各菜品」。
     */
    int resolveFoodSalesDataSheetIndex(byte[] spreadsheetBytes) throws IOException;

    /** 菜品日销售 Excel 解析（新/旧表头），从第 {@code sheetIndex} 个 Sheet 读取。 */
    List<FoodSalesExcelCell> parseFoodSalesExcel(byte[] spreadsheetBytes, int sheetIndex) throws IOException;

    /** 菜品日销售 Excel 解析（新/旧表头），默认读取第一个 Sheet。 */
    List<FoodSalesExcelCell> parseFoodSalesExcel(MultipartFile file) throws IOException;

    /**
     * 解析合并模板中的「日营业额」Sheet：列为 日期、堂食订单数、堂食顾客数、外卖营业额、外卖订单数、平台抽成、备注（无堂食营业额）。
     */
    List<GbAiDailyRevenueEntity> parseCombinedTemplateRevenueSheet(
            byte[] spreadsheetBytes, int sheetIndex, Long departmentId, Long distributerId) throws IOException;

    /** 为门店菜品列表填充批发商菜品实体（上传/模板共用）。 */
    void attachDistributerFood(List<GbDepFoodEntity> depFoods);

    /** 模板行排序：优先批发商菜品名称。 */
    Comparator<GbDepFoodEntity> depFoodTemplateRowComparator();
}
