package com.nongxinle.service.impl;

import com.nongxinle.entity.GbAiDailyRevenueEntity;
import com.nongxinle.entity.GbDepartmentEntity;
import com.nongxinle.entity.GbDepFoodEntity;
import com.nongxinle.entity.GbDistributerFoodEntity;
import com.nongxinle.service.GbAiDailyRevenueExcelService;
import com.nongxinle.service.GbDepartmentService;
import com.nongxinle.service.GbDepFoodService;
import com.nongxinle.service.GbDistributerFoodService;
import com.nongxinle.utils.GbDateTimeUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class GbAiDailyRevenueExcelServiceImpl implements GbAiDailyRevenueExcelService {

    private static final Pattern FOOD_HEADER_ID_ZH = Pattern.compile("（id:(\\d+)）");
    private static final Pattern FOOD_HEADER_ID_EN = Pattern.compile("\\(id:(\\d+)\\)");

    private final GbDepartmentService departmentService;
    private final GbDepFoodService gbDepFoodService;
    private final GbDistributerFoodService gbDistributerFoodService;

    @Override
    public void assertSpreadsheetUpload(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("请上传Excel文件");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null
                || (!originalFilename.toLowerCase().endsWith(".xls")
                && !originalFilename.toLowerCase().endsWith(".xlsx"))) {
            throw new IllegalArgumentException("请上传Excel文件（.xls 或 .xlsx 格式）");
        }
    }

    @Override
    public List<GbAiDailyRevenueEntity> parseDailyRevenueExcel(MultipartFile file, Long departmentId, Long distributerId)
            throws IOException {
        List<GbAiDailyRevenueEntity> revenueList = new ArrayList<>();
        Set<String> dateSet = new HashSet<>(); // 用于检查重复日期
        
        // 使用 Hutool 的 ExcelReader
        cn.hutool.poi.excel.ExcelReader reader = cn.hutool.poi.excel.ExcelUtil.getReader(file.getInputStream());
        
        // 读取所有行数据（跳过表头）
        List<List<Object>> rows = reader.read();
        
        if (log.isDebugEnabled()) {
            for (int idx = 0; idx < rows.size(); idx++) {
                List<Object> row = rows.get(idx);
                StringBuilder sb = new StringBuilder();
                for (int col = 0; col < row.size(); col++) {
                    Object cell = row.get(col);
                    sb.append("col").append(col).append('=')
                            .append(cell != null ? cell.toString() : "null").append(' ');
                }
                log.debug("excel raw row {}: {}", idx, sb);
            }
        }

        // 智能识别表头行数
        int startRow = detectDailyRevenueDataStartRow(rows);
        boolean deptCol = dailyRevenueHeaderHasDepartmentColumn(rows, startRow);

        for (int i = startRow; i < rows.size(); i++) {
            List<Object> row = rows.get(i);
            
            // 跳过空行
            if (row.isEmpty() || row.get(0) == null) {
                continue;
            }
            
            // 解析日期（Excel中的日期可能是 Date 类型或字符串）
            Object dateCell = row.size() > 0 ? row.get(0) : null;
            Date recordDate = GbDateTimeUtils.parseExcelDateLikeCell(dateCell);
            String dateStr = recordDate != null ? GbDateTimeUtils.formatDay(recordDate) : null;
            if (recordDate == null || dateStr == null) {
                continue;
            }

            Long rowDeptId = departmentId;
            if (deptCol && row.size() > 1 && row.get(1) != null) {
                Integer parsedDep = parseDepartmentIdFromHeaderCell(row.get(1));
                if (parsedDep != null) {
                    rowDeptId = parsedDep.longValue();
                }
            }
            assertDepartmentInUploadScope(rowDeptId, departmentId);
            
            // 检查重复：同一部门 + 同一自然日
            String dateKey = rowDeptId + "-" + dateStr;
            if (dateSet.contains(dateKey)) {
                log.warn("skip duplicate excel date deptId={} date={}", rowDeptId, dateStr);
                continue;
            }
            dateSet.add(dateKey);
            
            GbAiDailyRevenueEntity entity = new GbAiDailyRevenueEntity();
            
            // 设置部门ID和分配者ID
            entity.setGbAiDailyRevenueDepartmentId(rowDeptId);
            entity.setGbAiDailyRevenueDistributerId(distributerId);
            // 设置记录日期
            entity.setGbAiDailyRevenueRecordDate(recordDate);
            
            if (log.isDebugEnabled()) {
                log.debug("excel entity deptId={} date={} dayKey={}", rowDeptId, recordDate, dateStr);
            }

            int base = deptCol ? 2 : 1;

            // 解析堂食营业额
            if (row.size() > base && row.get(base) != null) {
                try {
                    BigDecimal dineInRevenue = new BigDecimal(row.get(base).toString());
                    entity.setGbAiDailyRevenueDineInRevenue(dineInRevenue);
                } catch (NumberFormatException e) {
                    entity.setGbAiDailyRevenueDineInRevenue(BigDecimal.ZERO);
                }
            } else {
                entity.setGbAiDailyRevenueDineInRevenue(BigDecimal.ZERO);
            }
            
            // 解析堂食订单数
            if (row.size() > base + 1 && row.get(base + 1) != null) {
                try {
                    Integer dineInOrders = Integer.parseInt(row.get(base + 1).toString());
                    entity.setGbAiDailyRevenueDineInOrders(dineInOrders);
                } catch (NumberFormatException e) {
                    entity.setGbAiDailyRevenueDineInOrders(0);
                }
            } else {
                entity.setGbAiDailyRevenueDineInOrders(0);
            }
            
            // 解析堂食顾客数
            if (row.size() > base + 2 && row.get(base + 2) != null) {
                try {
                    Integer dineInCustomers = Integer.parseInt(row.get(base + 2).toString());
                    entity.setGbAiDailyRevenueDineInCustomers(dineInCustomers);
                } catch (NumberFormatException e) {
                    entity.setGbAiDailyRevenueDineInCustomers(0);
                }
            } else {
                entity.setGbAiDailyRevenueDineInCustomers(0);
            }
            
            // 解析外卖营业额
            if (row.size() > base + 3 && row.get(base + 3) != null) {
                try {
                    BigDecimal takeoutRevenue = new BigDecimal(row.get(base + 3).toString());
                    entity.setGbAiDailyRevenueTakeoutRevenue(takeoutRevenue);
                } catch (NumberFormatException e) {
                    entity.setGbAiDailyRevenueTakeoutRevenue(BigDecimal.ZERO);
                }
            } else {
                entity.setGbAiDailyRevenueTakeoutRevenue(BigDecimal.ZERO);
            }
            
            // 解析外卖订单数
            if (row.size() > base + 4 && row.get(base + 4) != null) {
                try {
                    Integer takeoutOrders = Integer.parseInt(row.get(base + 4).toString());
                    entity.setGbAiDailyRevenueTakeoutOrders(takeoutOrders);
                } catch (NumberFormatException e) {
                    entity.setGbAiDailyRevenueTakeoutOrders(0);
                }
            } else {
                entity.setGbAiDailyRevenueTakeoutOrders(0);
            }
            
            // 解析平台抽成
            if (row.size() > base + 5 && row.get(base + 5) != null) {
                try {
                    BigDecimal platformFee = new BigDecimal(row.get(base + 5).toString());
                    entity.setGbAiDailyRevenuePlatformFee(platformFee);
                } catch (NumberFormatException e) {
                    entity.setGbAiDailyRevenuePlatformFee(BigDecimal.ZERO);
                }
            } else {
                entity.setGbAiDailyRevenuePlatformFee(BigDecimal.ZERO);
            }
            
            // 自动计算星期几（从模板中去掉了，由后台自动计算）
            try {
                entity.setGbAiDailyRevenueWeekday(GbDateTimeUtils.weekdayForAiDailyRevenue(recordDate));
            } catch (Exception e) {
                entity.setGbAiDailyRevenueWeekday(1);
            }
            
            // 节假日设为空字符串（从模板中去掉了，由后台自动计算或后续补充）
            entity.setGbAiDailyRevenueHoliday("");
            
            // 解析备注
            if (row.size() > base + 6 && row.get(base + 6) != null) {
                entity.setGbAiDailyRevenueNotes(row.get(base + 6).toString());
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

    private static int detectDailyRevenueDataStartRow(List<List<Object>> rows) {
        int startRow = 0;
        if (!rows.isEmpty() && rows.get(0).size() > 0) {
            Object firstCell = rows.get(0).get(0);
            if (rows.size() >= 3
                    && firstCell instanceof String
                    && ((String) firstCell).contains("表格")) {
                if (rows.get(1).size() > 0
                        && rows.get(1).get(0) instanceof String
                        && ((String) rows.get(1).get(0)).contains("部门ID")) {
                    if (rows.get(2).size() > 0
                            && rows.get(2).get(0) instanceof String
                            && ((String) rows.get(2).get(0)).contains("日期")) {
                        startRow = 3;
                        log.debug("excel smart template: skip 3 metadata rows");
                    }
                }
            } else if (firstCell instanceof String
                    && ((String) firstCell).contains("日期")) {
                startRow = 1;
                log.debug("excel legacy template: skip header row");
            }
        }
        return startRow;
    }

    @Override
    public void writeDailyRevenueSmartTemplate(HttpServletResponse response,
            String startDate,
            String endDate,
            Integer departmentId) throws IOException {
        
        try {
            log.debug("download-smart-template startDate={} endDate={} departmentId={}", startDate, endDate, departmentId);
            LocalDate start = GbDateTimeUtils.parseLocalDay(startDate);
            LocalDate end = GbDateTimeUtils.parseLocalDay(endDate);
            if (start.isAfter(end)) {
                throw new IllegalArgumentException("开始日期不能晚于结束日期");
            }
            List<LocalDate> dayList = GbDateTimeUtils.inclusiveLocalDates(start, end);
            long days = dayList.size();
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
                "部门名称",
                "堂食营业额",
                "堂食订单数",
                "堂食顾客数",
                "外卖营业额",
                "外卖订单数",
                "平台抽成",
                "备注"
            };
            writer.writeHeadRow(Arrays.asList(dataHeaders));

            // 4. 生成日期序列并填充模板（每个子部门一行/日；无子部门时为父部门一行）
            List<GbDepartmentEntity> revDeps = departmentsForRevenueTemplateRows(departmentId);
            for (LocalDate d : dayList) {
                for (GbDepartmentEntity dep : revDeps) {
                    List<Object> rowData = new ArrayList<>();
                    rowData.add(GbDateTimeUtils.formatDay(d));
                    Integer did = dep.getGbDepartmentId();
                    rowData.add(depDisplayName(did) + "（id:" + (did == null ? "" : did) + "）");
                    rowData.add(""); // 堂食营业额
                    rowData.add(""); // 堂食订单数
                    rowData.add(""); // 堂食顾客数
                    rowData.add(""); // 外卖营业额
                    rowData.add(""); // 外卖订单数
                    rowData.add(""); // 平台抽成
                    rowData.add(""); // 备注
                    writer.writeRow(rowData);
                }
            }
            
            // 5. 添加使用说明
            writer.setSheet("使用说明");
            writer.writeCellValue(0, 0, "智能模板使用说明");
            writer.writeCellValue(1, 0, "模板特性：");
            writer.writeCellValue(2, 0, "1. 自动生成指定日期范围的所有日期");
            writer.writeCellValue(3, 0, "2. 有子部门时每个日期下按子部门分行，无子部门时为父部门一行");
            writer.writeCellValue(4, 0, "3. 数值字段留空，等待用户填写");
            writer.writeCellValue(5, 0, "4. 星期几和节假日由系统自动计算，无需填写");
            writer.writeCellValue(6, 0, "");
            writer.writeCellValue(7, 0, "填写指南：");
            writer.writeCellValue(8, 0, "1. 第2列为部门名称（含子部门 id），与菜品模板一致；同一日期下每个子部门一行");
            writer.writeCellValue(9, 0, "2. 数值字段：堂食营业额、订单数、顾客数、外卖营业额、外卖订单数、平台抽成");
            writer.writeCellValue(10, 0, "3. 金额字段：支持小数，单位：元");
            writer.writeCellValue(11, 0, "4. 数量字段：整数");
            writer.writeCellValue(12, 0, "5. 备注：可选；星期与节假日由系统自动计算");
            writer.writeCellValue(13, 0, "");
            writer.writeCellValue(14, 0, "上传说明：");
            writer.writeCellValue(15, 0, "1. 填写完成后保存文件");
            writer.writeCellValue(16, 0, "2. 使用上传接口：/ai/daily-revenue/upload-excel");
            writer.writeCellValue(17, 0, "3. 上传时需提供父部门 ID（与下载模板一致）");
            writer.writeCellValue(18, 0, "4. 系统按 部门 id + 日期 写入；子部门须隶属于该父部门");
            
            // 6. 设置数据格式
            writer.setSheet(0); // 回到数据表
            
            // 调整列宽
            for (int i = 0; i < dataHeaders.length; i++) {
                writer.autoSizeColumn(i);
            }
            
            // 标记必填字段：第3–8列为数值字段（索引 2–7）
            for (int i = 2; i <= 7; i++) {
                Sheet sheet = writer.getSheet();
                if (sheet != null && sheet.getRow(2) != null) {
                    sheet.getRow(2).getCell(i).setCellValue(dataHeaders[i] + " *");
                }
            }
            
            // 7. 生成简单文件名
            String fileName = String.format("daily_revenue_template_%s_%s.xlsx", 
                startDate, 
                endDate);
            
            log.debug("download-smart-template fileName={}", fileName);

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
                log.debug("download-smart-template flush done");
            } catch (Exception e) {
                log.warn("download-smart-template flush failed", e);
                throw e;
            }

        } catch (DateTimeParseException e) {
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
            log.warn("download-smart-template failed", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("text/plain; charset=utf-8");
            response.getWriter().write("生成智能模板失败: " + e.getMessage());
            return;
        }
    }

    @Override
    public void writeFoodSalesSmartTemplate(HttpServletResponse response,
            String startDate,
            String endDate,
            Integer departmentId) throws IOException {

        try {
            LocalDate start = GbDateTimeUtils.parseLocalDay(startDate);
            LocalDate end = GbDateTimeUtils.parseLocalDay(endDate);
            if (start.isAfter(end)) {
                throw new IllegalArgumentException("开始日期不能晚于结束日期");
            }
            List<LocalDate> dayList = GbDateTimeUtils.inclusiveLocalDates(start, end);
            long days = dayList.size();
            if (days > 365) {
                throw new IllegalArgumentException("日期范围不能超过365天");
            }

            GbDepartmentEntity department = departmentService.getById(departmentId);
            if (department == null) {
                throw new IllegalArgumentException("部门不存在，部门ID: " + departmentId);
            }

            Map<String, Object> depMap = new HashMap<>();
            depMap.put("depFatherId", departmentId);
            if (log.isDebugEnabled()) {
                log.debug("queryDepAllFood depMap={}", depMap);
            }
            List<GbDepFoodEntity> depFoods = gbDepFoodService.queryDepAllFood(depMap);
            attachDistributerFood(depFoods);
            depFoods.sort(depFoodTemplateRowComparator());

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
            dataHeaders.add("部门名称");
            dataHeaders.add("菜品名称");
            for (LocalDate d : dayList) {
                dataHeaders.add(GbDateTimeUtils.formatDay(d));
            }
            writer.writeHeadRow(dataHeaders);

            log.debug("dep food sales template departmentId={} dishRowCount={}", departmentId, dishRowCount);
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
                if (log.isDebugEnabled()) {
                    log.debug("dep food sales template dish serial={} foodId={} name={}", serial, foodId, name);
                }
                Integer depId = f.getGbDfDepId();
                String depName = depDisplayName(depId);
                List<Object> rowData = new ArrayList<>();
                rowData.add(serial++);
                rowData.add(depName + "（id:" + (depId == null ? "" : depId) + "）");
                rowData.add(name + "（id:" + foodId + "）");
                for (int i = 0; i < days; i++) {
                    rowData.add("");
                }
                writer.writeRow(rowData);
            }
            log.debug("dep food sales template rowsWritten={}", serial - 1);

            writer.setSheet("使用说明");
            writer.writeCellValue(0, 0, "菜品日销售模板说明");
            writer.writeCellValue(1, 0, "1. 第1列序号、第2列部门名称（含部门id）、第3列菜品名称（含id）请勿改，第4列起为各日期，在对应格填写该菜当日销量（可小数）");
            writer.writeCellValue(2, 0, "2. 上传接口：POST /ai/daily-revenue/upload-food-sales-excel ，参数 file、departmentId、distributerId");
            writer.writeCellValue(3, 0, "3. 上传按第2列部门id + 第3列菜品id匹配 gb_dep_food，写入 gb_dep_food_sales，并按配方展开到 gb_dep_food_goods_sales");
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
        } catch (DateTimeParseException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("text/plain; charset=utf-8");
            response.getWriter().write("日期格式错误，请使用 yyyy-MM-dd 格式");
        } catch (IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("text/plain; charset=utf-8");
            response.getWriter().write(e.getMessage());
        } catch (Exception e) {
            log.warn("download-food-sales-smart-template failed", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("text/plain; charset=utf-8");
            response.getWriter().write("生成模板失败: " + e.getMessage());
        }
    }

    @Override
    public List<FoodSalesExcelCell> parseFoodSalesExcel(MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();
        int sheet = resolveFoodSalesDataSheetIndex(bytes);
        return parseFoodSalesExcel(bytes, sheet);
    }

    @Override
    public List<FoodSalesExcelCell> parseFoodSalesExcel(byte[] spreadsheetBytes, int sheetIndex) throws IOException {
        cn.hutool.poi.excel.ExcelReader reader =
                cn.hutool.poi.excel.ExcelUtil.getReader(new ByteArrayInputStream(spreadsheetBytes), sheetIndex);
        List<List<Object>> rows = reader.read();

        log.info("parseFoodSalesExcel sheetIndex={} dataRowCount={}", sheetIndex, rows.size());
        int previewRows = Math.min(rows.size(), 8);
        for (int i = 0; i < previewRows; i++) {
            log.info("  foodSheet row[{}]: {}", i, summarizeFoodSheetRowForLog(rows.get(i), 10));
        }

        Integer pivotHeaderRow = findFoodSalesPivotHeaderRow(rows);
        if (pivotHeaderRow != null) {
            log.info("parseFoodSalesExcel using pivot layout, headerRowIndex={}", pivotHeaderRow);
            return readFoodSalesExcelPivotLayout(rows, pivotHeaderRow);
        }
        log.warn("parseFoodSalesExcel pivot header not matched (e.g. need 序号 column + 菜品 + date columns); trying legacy layout");
        return readFoodSalesExcelLegacyDateRows(rows);
    }

    @Override
    public int[] resolveCombinedTemplateFoodAndRevenueSheetIndexes(byte[] spreadsheetBytes) throws IOException {
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(spreadsheetBytes))) {
            int sheetCount = wb.getNumberOfSheets();
            int namedFood = -1;
            int namedRevenue = -1;
            for (int i = 0; i < sheetCount; i++) {
                String n = wb.getSheetName(i);
                if (GbAiDailyRevenueExcelService.COMBINED_SHEET_FOOD_NAME.equals(n)) {
                    namedFood = i;
                } else if (GbAiDailyRevenueExcelService.COMBINED_SHEET_REVENUE_NAME.equals(n)) {
                    namedRevenue = i;
                }
            }
            if (namedFood >= 0 && namedRevenue >= 0) {
                log.info("resolveCombinedTemplate by sheet name food={} revenue={}", namedFood, namedRevenue);
                logWorkbookSheetNames(wb);
                return new int[] { namedFood, namedRevenue };
            }

            int food = namedFood;
            int revenue = namedRevenue;
            for (int i = 0; i < sheetCount; i++) {
                String sheetName = wb.getSheetName(i);
                List<List<Object>> rows = readSheetRowsForDetection(spreadsheetBytes, i);
                if (isNumbersOrExportJunkSheet(sheetName, rows)) {
                    log.info("resolveCombinedTemplate skip junk sheet index={} name=\"{}\"", i, sheetName);
                    continue;
                }
                if (food < 0 && findFoodSalesPivotHeaderRow(rows) != null) {
                    food = i;
                    log.info("resolveCombinedTemplate detected FOOD sheet index={} name=\"{}\"", i, sheetName);
                } else if (food < 0 && hasLegacyFoodSalesDateColumnLayout(rows)) {
                    food = i;
                    log.info("resolveCombinedTemplate detected FOOD (legacy 日期列) sheet index={} name=\"{}\"", i, sheetName);
                } else if (revenue < 0 && looksLikeDailyRevenueSupplementSheet(rows)) {
                    revenue = i;
                    log.info("resolveCombinedTemplate detected REVENUE sheet index={} name=\"{}\"", i, sheetName);
                }
            }

            if (food < 0) {
                throw new IllegalArgumentException(
                        "未识别菜品销售表：Numbers 导出常将「导出摘要」作为首表，真正数据在「工作表 1」等；请保留含「序号、部门、菜品、日期列」的表，或使用系统下载的合并模板。");
            }
            if (revenue < 0) {
                throw new IllegalArgumentException(
                        "未识别日营业额表：请确保另有工作表表头含「日期」及「堂食订单/顾客/外卖/平台抽成」等列（合并模板无堂食营业额列），或使用系统下载的合并模板。");
            }
            if (food == revenue) {
                throw new IllegalArgumentException("菜品销售表与日营业额表不能是同一张工作表，请检查导出是否完整。");
            }
            log.info("resolveCombinedTemplate foodSheetIndex={} revenueSheetIndex={} totalSheets={}",
                    food, revenue, sheetCount);
            logWorkbookSheetNames(wb);
            return new int[] { food, revenue };
        }
    }

    @Override
    public int resolveFoodSalesDataSheetIndex(byte[] spreadsheetBytes) throws IOException {
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(spreadsheetBytes))) {
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                String sheetName = wb.getSheetName(i);
                List<List<Object>> rows = readSheetRowsForDetection(spreadsheetBytes, i);
                if (isNumbersOrExportJunkSheet(sheetName, rows)) {
                    log.info("resolveFoodSalesDataSheetIndex skip junk index={} name=\"{}\"", i, sheetName);
                    continue;
                }
                if (findFoodSalesPivotHeaderRow(rows) != null) {
                    log.info("resolveFoodSalesDataSheetIndex chose index={} name=\"{}\" (pivot)", i, sheetName);
                    return i;
                }
                if (hasLegacyFoodSalesDateColumnLayout(rows)) {
                    log.info("resolveFoodSalesDataSheetIndex chose index={} name=\"{}\" (legacy)", i, sheetName);
                    return i;
                }
            }
        }
        throw new IllegalArgumentException(
                "未找到菜品销售工作表：若使用 Numbers 导出，首表可能为「导出摘要」，请从含「序号、菜品…、日期列」的表上传，或使用「菜品日销售」单表模板。");
    }

    private void logWorkbookSheetNames(Workbook wb) {
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            log.info("  workbook sheet[{}]: \"{}\"", i, wb.getSheetName(i));
        }
    }

    private List<List<Object>> readSheetRowsForDetection(byte[] spreadsheetBytes, int sheetIndex) throws IOException {
        return cn.hutool.poi.excel.ExcelUtil.getReader(new ByteArrayInputStream(spreadsheetBytes), sheetIndex).read();
    }

    private static boolean isNumbersOrExportJunkSheet(String sheetName, List<List<Object>> rows) {
        if (sheetName != null) {
            String sn = sheetName.trim();
            if ("导出摘要".equals(sn) || sn.contains("导出摘要")) {
                return true;
            }
            if ("Export Summary".equalsIgnoreCase(sn)) {
                return true;
            }
        }
        if (rows == null || rows.isEmpty()) {
            return false;
        }
        for (int i = 0; i < Math.min(rows.size(), 5); i++) {
            List<Object> row = rows.get(i);
            if (row == null) {
                continue;
            }
            for (Object cell : row) {
                if (cell == null) {
                    continue;
                }
                String t = cell.toString();
                if (t.contains("此文稿由 Numbers") || t.contains("Numbers 表格导出") || t.contains("Numbers 表格工作表名称")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasLegacyFoodSalesDateColumnLayout(List<List<Object>> rows) {
        if (rows.size() >= 3
                && rows.get(2).size() > 0
                && rows.get(2).get(0) != null
                && rows.get(2).get(0).toString().contains("日期")) {
            return true;
        }
        return rows.size() >= 1
                && rows.get(0).size() > 0
                && rows.get(0).get(0) != null
                && rows.get(0).get(0).toString().contains("日期");
    }

    private static boolean looksLikeDailyRevenueSupplementSheet(List<List<Object>> rows) {
        if (findFoodSalesPivotHeaderRow(rows) != null) {
            return false;
        }
        for (int r = 0; r < Math.min(rows.size(), 40); r++) {
            List<Object> row = rows.get(r);
            if (row == null || row.isEmpty() || row.get(0) == null) {
                continue;
            }
            String c0 = row.get(0).toString().trim();
            if (!"日期".equals(c0) && !"Date".equalsIgnoreCase(c0)) {
                continue;
            }
            if (row.size() < 2) {
                continue;
            }
            String c1 = row.get(1) == null ? "" : row.get(1).toString();
            if (c1.contains("部门")) {
                if (row.size() > 2) {
                    String c2 = row.get(2) == null ? "" : row.get(2).toString();
                    if (c2.contains("堂食订单") || c2.contains("堂食顾客") || c2.contains("外卖")
                            || c2.contains("平台") || c2.contains("抽成") || c2.contains("备注")) {
                        return true;
                    }
                }
                continue;
            }
            if (c1.contains("堂食订单") || c1.contains("堂食顾客") || c1.contains("堂食营业额")
                    || c1.contains("外卖") || c1.contains("平台") || c1.contains("抽成")
                    || c1.contains("备注")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<GbAiDailyRevenueEntity> parseCombinedTemplateRevenueSheet(
            byte[] spreadsheetBytes, int sheetIndex, Long departmentId, Long distributerId) throws IOException {
        List<GbAiDailyRevenueEntity> revenueList = new ArrayList<>();
        Set<String> dateSet = new HashSet<>();

        cn.hutool.poi.excel.ExcelReader reader =
                cn.hutool.poi.excel.ExcelUtil.getReader(new ByteArrayInputStream(spreadsheetBytes), sheetIndex);
        List<List<Object>> rows = reader.read();

        int startRow = detectDailyRevenueDataStartRow(rows);
        boolean deptCol = dailyRevenueHeaderHasDepartmentColumn(rows, startRow);
        if (startRow > 0 && startRow - 1 < rows.size()) {
            List<Object> header = rows.get(startRow - 1);
            int supplementBase = deptCol ? 2 : 1;
            if (header.size() > supplementBase && header.get(supplementBase) != null
                    && header.get(supplementBase).toString().contains("堂食营业额")) {
                throw new IllegalArgumentException("合并模板「日营业额」页不应包含堂食营业额列；堂食金额由「菜品日销售」页汇总");
            }
        }

        for (int i = startRow; i < rows.size(); i++) {
            List<Object> row = rows.get(i);
            if (row.isEmpty() || row.get(0) == null) {
                continue;
            }
            Object dateCell = row.get(0);
            Date recordDate = GbDateTimeUtils.parseExcelDateLikeCell(dateCell);
            String dateStr = recordDate != null ? GbDateTimeUtils.formatDay(recordDate) : null;
            if (recordDate == null || dateStr == null) {
                continue;
            }

            Long rowDeptId = departmentId;
            if (deptCol && row.size() > 1 && row.get(1) != null) {
                Integer parsedDep = parseDepartmentIdFromHeaderCell(row.get(1));
                if (parsedDep != null) {
                    rowDeptId = parsedDep.longValue();
                }
            }
            assertDepartmentInUploadScope(rowDeptId, departmentId);

            String dateKey = rowDeptId + "-" + dateStr;
            if (dateSet.contains(dateKey)) {
                log.warn("skip duplicate excel date deptId={} date={}", rowDeptId, dateStr);
                continue;
            }
            dateSet.add(dateKey);

            GbAiDailyRevenueEntity entity = new GbAiDailyRevenueEntity();
            entity.setGbAiDailyRevenueDepartmentId(rowDeptId);
            entity.setGbAiDailyRevenueDistributerId(distributerId);
            entity.setGbAiDailyRevenueRecordDate(recordDate);
            // 无堂食营业额列：堂食金额由菜品导入逻辑写入

            int base = deptCol ? 2 : 1;

            if (row.size() > base && row.get(base) != null) {
                try {
                    entity.setGbAiDailyRevenueDineInOrders(Integer.parseInt(row.get(base).toString()));
                } catch (NumberFormatException e) {
                    entity.setGbAiDailyRevenueDineInOrders(0);
                }
            } else {
                entity.setGbAiDailyRevenueDineInOrders(0);
            }

            if (row.size() > base + 1 && row.get(base + 1) != null) {
                try {
                    entity.setGbAiDailyRevenueDineInCustomers(Integer.parseInt(row.get(base + 1).toString()));
                } catch (NumberFormatException e) {
                    entity.setGbAiDailyRevenueDineInCustomers(0);
                }
            } else {
                entity.setGbAiDailyRevenueDineInCustomers(0);
            }

            if (row.size() > base + 2 && row.get(base + 2) != null) {
                try {
                    entity.setGbAiDailyRevenueTakeoutRevenue(new BigDecimal(row.get(base + 2).toString()));
                } catch (NumberFormatException e) {
                    entity.setGbAiDailyRevenueTakeoutRevenue(BigDecimal.ZERO);
                }
            } else {
                entity.setGbAiDailyRevenueTakeoutRevenue(BigDecimal.ZERO);
            }

            if (row.size() > base + 3 && row.get(base + 3) != null) {
                try {
                    entity.setGbAiDailyRevenueTakeoutOrders(Integer.parseInt(row.get(base + 3).toString()));
                } catch (NumberFormatException e) {
                    entity.setGbAiDailyRevenueTakeoutOrders(0);
                }
            } else {
                entity.setGbAiDailyRevenueTakeoutOrders(0);
            }

            if (row.size() > base + 4 && row.get(base + 4) != null) {
                try {
                    entity.setGbAiDailyRevenuePlatformFee(new BigDecimal(row.get(base + 4).toString()));
                } catch (NumberFormatException e) {
                    entity.setGbAiDailyRevenuePlatformFee(BigDecimal.ZERO);
                }
            } else {
                entity.setGbAiDailyRevenuePlatformFee(BigDecimal.ZERO);
            }

            try {
                entity.setGbAiDailyRevenueWeekday(GbDateTimeUtils.weekdayForAiDailyRevenue(recordDate));
            } catch (Exception e) {
                entity.setGbAiDailyRevenueWeekday(1);
            }
            entity.setGbAiDailyRevenueHoliday("");

            if (row.size() > base + 5 && row.get(base + 5) != null) {
                entity.setGbAiDailyRevenueNotes(row.get(base + 5).toString());
            } else {
                entity.setGbAiDailyRevenueNotes("");
            }

            Date currentTime = new Date();
            entity.setGbAiDailyRevenueCreateTime(currentTime);
            entity.setGbAiDailyRevenueUpdateTime(currentTime);

            revenueList.add(entity);
        }
        return revenueList;
    }

    @Override
    public void writeDailyRevenueFoodCombinedTemplate(HttpServletResponse response,
            String startDate,
            String endDate,
            Integer departmentId) throws IOException {

        try {
            LocalDate start = GbDateTimeUtils.parseLocalDay(startDate);
            LocalDate end = GbDateTimeUtils.parseLocalDay(endDate);
            if (start.isAfter(end)) {
                throw new IllegalArgumentException("开始日期不能晚于结束日期");
            }
            List<LocalDate> dayList = GbDateTimeUtils.inclusiveLocalDates(start, end);
            long days = dayList.size();
            if (days > 365) {
                throw new IllegalArgumentException("日期范围不能超过365天");
            }

            GbDepartmentEntity department = departmentService.getById(departmentId);
            if (department == null) {
                throw new IllegalArgumentException("部门不存在，部门ID: " + departmentId);
            }

            Map<String, Object> depMap = new HashMap<>();
            depMap.put("depFatherId", departmentId);
            List<GbDepFoodEntity> depFoods = gbDepFoodService.queryDepAllFood(depMap);
            attachDistributerFood(depFoods);
            depFoods.sort(depFoodTemplateRowComparator());

            cn.hutool.poi.excel.ExcelWriter writer = cn.hutool.poi.excel.ExcelUtil.getWriter(true);
            writer.renameSheet(0, GbAiDailyRevenueExcelService.COMBINED_SHEET_FOOD_NAME);

            List<Object> foodMeta = new ArrayList<>();
            foodMeta.add("部门ID: " + departmentId);
            foodMeta.add("部门名称: " + department.getGbDepartmentName());
            foodMeta.add("日期范围: " + startDate + " 至 " + endDate);
            foodMeta.add("总天数: " + days);
            int dishRowCount = 0;
            for (GbDepFoodEntity f : depFoods) {
                if (includeDepFoodInSalesTemplate(f, department)) {
                    dishRowCount++;
                }
            }
            foodMeta.add("菜品行数(含id): " + dishRowCount);
            foodMeta.add("");
            foodMeta.add("");
            foodMeta.add("");
            foodMeta.add("");
            writer.writeRow(foodMeta);
            writer.writeRow(new ArrayList<>());

            List<Object> foodDataHeaders = new ArrayList<>();
            foodDataHeaders.add("序号");
            foodDataHeaders.add("部门名称");
            foodDataHeaders.add("菜品名称");
            for (LocalDate d : dayList) {
                foodDataHeaders.add(GbDateTimeUtils.formatDay(d));
            }
            writer.writeHeadRow(foodDataHeaders);

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
                Integer depId = f.getGbDfDepId();
                String depName = depDisplayName(depId);
                List<Object> rowData = new ArrayList<>();
                rowData.add(serial++);
                rowData.add(depName + "（id:" + (depId == null ? "" : depId) + "）");
                rowData.add(name + "（id:" + foodId + "）");
                for (int i = 0; i < days; i++) {
                    rowData.add("");
                }
                writer.writeRow(rowData);
            }

            writer.setSheet(GbAiDailyRevenueExcelService.COMBINED_SHEET_REVENUE_NAME);
            List<Object> revInfoRow = new ArrayList<>();
            revInfoRow.add("部门ID: " + departmentId);
            revInfoRow.add("部门名称: " + department.getGbDepartmentName());
            revInfoRow.add("日期范围: " + startDate + " 至 " + endDate);
            revInfoRow.add("总天数: " + days);
            revInfoRow.add("说明: 无堂食/当日营业额列，堂食金额由「" + GbAiDailyRevenueExcelService.COMBINED_SHEET_FOOD_NAME + "」汇总");
            revInfoRow.add("");
            revInfoRow.add("");
            revInfoRow.add("");
            revInfoRow.add("");
            writer.writeRow(revInfoRow);
            writer.writeRow(new ArrayList<>());

            List<GbDepartmentEntity> revDeps = departmentsForRevenueTemplateRows(departmentId);
            String[] revHeaders = {
                    "日期",
                    "部门名称",
                    "堂食订单数",
                    "堂食顾客数",
                    "外卖营业额",
                    "外卖订单数",
                    "平台抽成",
                    "备注"
            };
            writer.writeHeadRow(Arrays.asList(revHeaders));

            for (LocalDate d : dayList) {
                for (GbDepartmentEntity dep : revDeps) {
                    List<Object> rowData = new ArrayList<>();
                    rowData.add(GbDateTimeUtils.formatDay(d));
                    Integer did = dep.getGbDepartmentId();
                    rowData.add(depDisplayName(did) + "（id:" + (did == null ? "" : did) + "）");
                    for (int i = 0; i < 6; i++) {
                        rowData.add("");
                    }
                    writer.writeRow(rowData);
                }
            }

            writer.setSheet("使用说明");
            writer.writeCellValue(0, 0, "合并模板说明（菜品 + 日营业额）");
            writer.writeCellValue(1, 0, "一、「" + GbAiDailyRevenueExcelService.COMBINED_SHEET_FOOD_NAME + "」与单独下载的菜品模板相同：第1列序号、第2列部门（含id）、第3列菜品（含id），第4列起为各日销量。");
            writer.writeCellValue(2, 0, "二、「" + GbAiDailyRevenueExcelService.COMBINED_SHEET_REVENUE_NAME
                    + "」第2列为部门名称（含 id），与菜品表一致；每个日期下按子部门分行；不含堂食营业额列，堂食金额由菜品销量×单价汇总。");
            writer.writeCellValue(3, 0, "三、上传接口：POST /ai/daily-revenue/upload-combined-excel ，参数 file、departmentId（父部门）、distributerId");
            writer.writeCellValue(4, 0, "四、先导入菜品销售并汇总堂食，再按子部门合并写入订单数、顾客数、外卖、平台抽成、备注等。");
            if (skipped > 0) {
                writer.writeCellValue(5, 0, "五、当前有 " + skipped + " 条门店菜品未出现在菜品表中（未配置 gb_df_food_id 或与部门批发商不一致）");
            }

            writer.setSheet(GbAiDailyRevenueExcelService.COMBINED_SHEET_FOOD_NAME);
            for (int i = 0; i < foodDataHeaders.size(); i++) {
                writer.autoSizeColumn(i);
            }

            writer.setSheet(GbAiDailyRevenueExcelService.COMBINED_SHEET_REVENUE_NAME);
            for (int i = 0; i < revHeaders.length; i++) {
                writer.autoSizeColumn(i);
            }
            Sheet revSheet = writer.getSheet();
            if (revSheet != null && revSheet.getRow(2) != null) {
                // 第3–7列为需填数值：堂食订单数…平台抽成
                for (int i = 2; i <= 6; i++) {
                    if (revSheet.getRow(2).getCell(i) != null) {
                        revSheet.getRow(2).getCell(i).setCellValue(revHeaders[i] + " *");
                    }
                }
            }

            writer.setSheet(GbAiDailyRevenueExcelService.COMBINED_SHEET_FOOD_NAME);

            String fileName = String.format("daily_revenue_food_combined_template_%s_%s.xlsx", startDate, endDate);
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"");
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            writer.flush(response.getOutputStream(), true);
            writer.close();
        } catch (DateTimeParseException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("text/plain; charset=utf-8");
            response.getWriter().write("日期格式错误，请使用 yyyy-MM-dd 格式");
        } catch (IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("text/plain; charset=utf-8");
            response.getWriter().write(e.getMessage());
        } catch (Exception e) {
            log.warn("download-combined-template failed", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("text/plain; charset=utf-8");
            response.getWriter().write("生成合并模板失败: " + e.getMessage());
        }
    }

    private List<FoodSalesExcelCell> readFoodSalesExcelPivotLayout(
            List<List<Object>> rows, int headerRowIndex) {
        List<FoodSalesExcelCell> out = new ArrayList<>();
        List<Object> header = rows.get(headerRowIndex);
        int dataStartRow = headerRowIndex + 1;

        int firstDateCol = indexOfFirstDateColumnInRow(header, 1);
        if (firstDateCol < 2) {
            throw new IllegalArgumentException(
                    "菜品表未找到日期列表头（需为 yyyy-MM-dd、斜杠日期、Excel 日期序列等；Numbers 导出后表头日期常为数字）");
        }
        int foodCol = firstDateCol - 1;
        Object foodHeaderCell = header.size() > foodCol ? header.get(foodCol) : null;
        if (foodHeaderCell == null || !foodHeaderCell.toString().contains("菜品")) {
            throw new IllegalArgumentException("菜品表头列未识别：第 " + (foodCol + 1) + " 列应含「菜品」");
        }
        int depCol = foodCol - 1;
        boolean hasDepartmentCol = depCol >= 1
                && header.size() > depCol
                && (isDepartmentHeader(header, depCol) || isBlankOrNonDateHeaderCell(header.get(depCol)));

        List<String> dateKeys = new ArrayList<>();
        for (int c = firstDateCol; c < header.size(); c++) {
            String dk = GbDateTimeUtils.normalizeExcelDayKey(header.get(c));
            if (dk != null) {
                dateKeys.add(dk);
            } else {
                dateKeys.add(null);
            }
        }

        for (int i = dataStartRow; i < rows.size(); i++) {
            List<Object> row = rows.get(i);
            if (row.size() <= foodCol) {
                continue;
            }
            if (row.get(foodCol) == null) {
                continue;
            }
            Integer foodId = parseFoodIdFromHeader(row.get(foodCol));
            if (foodId == null) {
                continue;
            }
            Integer depId = null;
            if (hasDepartmentCol && depCol >= 0 && row.size() > depCol) {
                depId = parseDepartmentIdFromHeaderCell(row.get(depCol));
            }

            for (int j = 0; j < dateKeys.size(); j++) {
                String dk = dateKeys.get(j);
                if (dk == null) {
                    continue;
                }
                int col = firstDateCol + j;
                if (col >= row.size()) {
                    continue;
                }
                Object cell = row.get(col);
                BigDecimal q = parseExcelBigDecimalCell(cell);
                if (q == null || q.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                try {
                    Date recordDate = GbDateTimeUtils.parseDay(dk);
                    out.add(new FoodSalesExcelCell(recordDate, depId, foodId, q));
                } catch (Exception ignored) {
                }
            }
        }
        return out;
    }

    private List<FoodSalesExcelCell> readFoodSalesExcelLegacyDateRows(
            List<List<Object>> rows) {
        List<FoodSalesExcelCell> out = new ArrayList<>();
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

        for (int i = dataStartRow; i < rows.size(); i++) {
            List<Object> row = rows.get(i);
            if (row.isEmpty() || row.get(0) == null) {
                continue;
            }
            Object dateCell = row.get(0);
            Date recordDate = GbDateTimeUtils.parseExcelDateLikeCell(dateCell);
            if (recordDate == null) {
                continue;
            }

            for (int c = 1; c < colFoodIds.size() && c < row.size(); c++) {
                Integer fid = colFoodIds.get(c);
                if (fid == null) {
                    continue;
                }
                Object cell = row.get(c);
                BigDecimal q = parseExcelBigDecimalCell(cell);
                if (q == null || q.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                out.add(new FoodSalesExcelCell(recordDate, null, fid, q));
            }
        }
        return out;
    }

    private static Integer findFoodSalesPivotHeaderRow(List<List<Object>> rows) {
        int maxScan = Math.min(rows.size(), 20);
        for (int r = 0; r < maxScan; r++) {
            List<Object> row = rows.get(r);
            if (row == null || row.size() < 3) {
                continue;
            }
            Object c0 = row.get(0);
            if (c0 == null || !c0.toString().trim().contains("序号")) {
                continue;
            }
            int firstDateCol = indexOfFirstDateColumnInRow(row, 1);
            if (firstDateCol < 2) {
                continue;
            }
            int foodCol = firstDateCol - 1;
            Object foodCell = row.get(foodCol);
            if (foodCell == null || !foodCell.toString().contains("菜品")) {
                continue;
            }
            return r;
        }
        return null;
    }

    private static int indexOfFirstDateColumnInRow(List<Object> row, int startCol) {
        if (row == null) {
            return -1;
        }
        for (int c = startCol; c < row.size(); c++) {
            if (GbDateTimeUtils.normalizeExcelDayKey(row.get(c)) != null) {
                return c;
            }
        }
        return -1;
    }

    private static boolean isBlankOrNonDateHeaderCell(Object cell) {
        if (cell == null) {
            return true;
        }
        return cell.toString().trim().isEmpty();
    }

    private static String summarizeFoodSheetRowForLog(List<Object> row, int maxCells) {
        if (row == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        int n = Math.min(row.size(), maxCells);
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append(" | ");
            }
            Object c = row.get(i);
            if (c == null) {
                sb.append("∅");
            } else {
                String s = c.toString();
                if (s.length() > 32) {
                    s = s.substring(0, 29) + "...";
                }
                sb.append(s.replace('\n', ' '));
            }
        }
        if (row.size() > maxCells) {
            sb.append(" | ...(").append(row.size() - maxCells).append(" more)");
        }
        return sb.toString();
    }

    private static BigDecimal parseExcelBigDecimalCell(Object cell) {
        if (cell == null) {
            return null;
        }
        if (cell instanceof BigDecimal) {
            return (BigDecimal) cell;
        }
        if (cell instanceof Number) {
            return BigDecimal.valueOf(((Number) cell).doubleValue());
        }
        String s = cell.toString().trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isDepartmentHeader(List<Object> row, int idx) {
        if (row == null || idx < 0 || idx >= row.size() || row.get(idx) == null) {
            return false;
        }
        String s = row.get(idx).toString().trim();
        return s.contains("部门");
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

    private static Integer parseDepartmentIdFromHeaderCell(Object cell) {
        return parseFoodIdFromHeader(cell);
    }

    @Override
    public void attachDistributerFood(List<GbDepFoodEntity> depFoods) {
        for (GbDepFoodEntity f : depFoods) {
            if (f.getGbDfFoodId() == null) {
                continue;
            }
            GbDistributerFoodEntity disFood = gbDistributerFoodService.queryObject(f.getGbDfFoodId());
            f.setGbDistributerFoodEntity(disFood);
        }
    }

    private static String distributerFoodSortKey(GbDepFoodEntity f) {
        if (f.getGbDistributerFoodEntity() != null) {
            String n = f.getGbDistributerFoodEntity().getGbDfFoodName();
            if (n != null && !n.isEmpty()) {
                return n;
            }
        }
        return f.getGbDfFoodName();
    }

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

    /**
     * 日营业额模板/上传：表头行第2列是否为「部门名称」列（与菜品销售模板一致）。
     */
    private static boolean dailyRevenueHeaderHasDepartmentColumn(List<List<Object>> rows, int dataStartRow) {
        int headerIdx = dataStartRow > 0 ? dataStartRow - 1 : 0;
        if (headerIdx < 0 || headerIdx >= rows.size()) {
            return false;
        }
        return isDepartmentHeader(rows.get(headerIdx), 1);
    }

    private void assertDepartmentInUploadScope(Long rowDepartmentId, Long uploadParentDepartmentId) {
        if (rowDepartmentId == null || uploadParentDepartmentId == null) {
            throw new IllegalArgumentException("部门信息无效（上传父部门或 Excel 行部门为空）");
        }
        List<Long> scope = departmentScopeIdsForUpload(uploadParentDepartmentId);
        if (!scope.contains(rowDepartmentId)) {
            throw new IllegalArgumentException(
                    "Excel 中的部门 id " + rowDepartmentId + " 不在当前上传父部门（id=" + uploadParentDepartmentId + "）范围内");
        }
    }

    private List<Long> departmentScopeIdsForUpload(Long parentId) {
        List<Long> ids = new ArrayList<>();
        if (parentId == null) {
            return ids;
        }
        ids.add(parentId);
        List<GbDepartmentEntity> subs = departmentService.querySubDepartments(parentId.intValue());
        if (subs != null) {
            for (GbDepartmentEntity s : subs) {
                if (s.getGbDepartmentId() != null) {
                    ids.add(s.getGbDepartmentId().longValue());
                }
            }
        }
        return ids;
    }

    /**
     * 智能模板日营业额区：有子部门时每个子部门一行/日；无子部门时为父部门一行/日。
     */
    private List<GbDepartmentEntity> departmentsForRevenueTemplateRows(Integer departmentId) {
        if (departmentId == null) {
            return Collections.emptyList();
        }
        List<GbDepartmentEntity> subs = departmentService.querySubDepartments(departmentId);
        List<GbDepartmentEntity> out = new ArrayList<>();
        if (subs != null && !subs.isEmpty()) {
            subs.sort(Comparator.comparing(GbDepartmentEntity::getGbDepartmentId, Comparator.nullsLast(Integer::compareTo)));
            out.addAll(subs);
        } else {
            GbDepartmentEntity parent = departmentService.getById(departmentId);
            if (parent != null) {
                out.add(parent);
            }
        }
        return out;
    }

    private String depDisplayName(Integer depId) {
        if (depId == null) {
            return "未知部门";
        }
        GbDepartmentEntity dep = departmentService.getById(depId);
        if (dep == null || dep.getGbDepartmentName() == null || dep.getGbDepartmentName().trim().isEmpty()) {
            return "部门" + depId;
        }
        return dep.getGbDepartmentName().trim();
    }

    private static Integer parseIntSafe(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public Comparator<GbDepFoodEntity> depFoodTemplateRowComparator() {
        return Comparator
                .comparing(GbDepFoodEntity::getGbDfDepId, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(GbAiDailyRevenueExcelServiceImpl::distributerFoodSortKey,
                        Comparator.nullsLast(String::compareTo));
    }
}
