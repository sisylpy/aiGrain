package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nongxinle.dto.GbDepFoodDailySalesSubmitRequest;
import com.nongxinle.dto.GbDepFoodDishDailySalesBatchSaveRequest;
import com.nongxinle.dto.GbDepFoodDishDailySalesRangeQueryRequest;
import com.nongxinle.dto.GbDepFoodDishSalesLineRequest;
import com.nongxinle.entity.GbDepartmentEntity;
import com.nongxinle.entity.GbAiDailyRevenueEntity;
import com.nongxinle.entity.GbDepFoodGoodsSalesEntity;
import com.nongxinle.entity.GbDepFoodSalesEntity;
import com.nongxinle.entity.GbDistributerFoodEntity;
import com.nongxinle.entity.GbDepFoodEntity;
import com.nongxinle.service.GbAiDailyRevenueExcelService;
import com.nongxinle.service.GbAiDailyRevenueService;
import com.nongxinle.service.GbDepartmentService;
import com.nongxinle.service.GbDepFoodGoodsSalesService;
import com.nongxinle.service.GbDepFoodSalesExcelImportService;
import com.nongxinle.service.GbDepFoodSalesService;
import com.nongxinle.service.GbDepFoodService;
import com.nongxinle.service.GbDistributerFoodService;
import com.nongxinle.service.support.GbDepFoodSalesWriteSupport;
import com.nongxinle.utils.GbConstants;
import com.nongxinle.utils.GbDateTimeUtils;
import com.nongxinle.utils.GbDepFoodSalesMetricsSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
public class GbDepFoodSalesExcelImportServiceImpl implements GbDepFoodSalesExcelImportService {

    private final GbDepFoodSalesService gbDepFoodSalesService;
    private final GbDepFoodGoodsSalesService gbDepFoodGoodsSalesService;
    private final GbDepartmentService departmentService;
    private final GbDepFoodService gbDepFoodService;
    private final GbDistributerFoodService gbDistributerFoodService;
    private final GbAiDailyRevenueExcelService dailyRevenueExcelService;
    private final GbAiDailyRevenueService gbAiDailyRevenueService;
    private final GbDepFoodSalesWriteSupport gbDepFoodSalesWriteSupport;

    @Override
    public Map<String, Object> importFoodSales(
            Integer departmentId,
            Integer distributerId,
            GbDepartmentEntity department,
            Set<Integer> allowedFoodIds,
            List<Map.Entry<Date, Map<Integer, BigDecimal>>> cellQuantities) {

        int inserted = 0;
        int updated = 0;
        int goodsRows = 0;
        int skippedUnknownFood = 0;
        List<String> warnings = new ArrayList<>();

        Integer depFatherId = department.getGbDepartmentFatherId();
        Map<Integer, String> unitPriceByFoodRefId = loadUnitPriceByFoodRefIdForDep(departmentId);

        for (Map.Entry<Date, Map<Integer, BigDecimal>> entry : cellQuantities) {
            Date recordDate = entry.getKey();
            if (recordDate == null) {
                continue;
            }
            String fullDate = GbDateTimeUtils.formatDay(recordDate);
            Map<Integer, BigDecimal> qtyByFood = entry.getValue();
            if (qtyByFood == null || qtyByFood.isEmpty()) {
                continue;
            }

            for (Map.Entry<Integer, BigDecimal> fe : qtyByFood.entrySet()) {
                Integer foodId = fe.getKey();
                BigDecimal qty = fe.getValue();
                if (foodId == null || qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                if (!allowedFoodIds.contains(foodId)) {
                    skippedUnknownFood++;
                    warnings.add("跳过未归属当前部门的菜品ID: " + foodId + " 日期 " + fullDate);
                    continue;
                }

                GbDepFoodSalesWriteSupport.UpsertResult r = upsertViaWriteSupport(
                        departmentId, depFatherId, foodId, distributerId, recordDate, fullDate,
                        qty, GbConstants.FoodSalesType.NORMAL_SALE, unitPriceByFoodRefId.get(foodId), null);
                if (r.inserted) {
                    inserted++;
                } else {
                    updated++;
                }
                goodsRows += r.goodsRows;
            }
        }

        Map<String, Object> out = new HashMap<>();
        out.put("inserted", inserted);
        out.put("updated", updated);
        out.put("goodsRows", goodsRows);
        out.put("skippedUnknownFood", skippedUnknownFood);
        out.put("warnings", warnings);
        return out;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importFoodSalesFromExcelMultipart(MultipartFile file, Integer departmentId, Integer distributerId)
            throws IOException {
        dailyRevenueExcelService.assertSpreadsheetUpload(file);
        byte[] bytes = file.getBytes();
        int sheet = dailyRevenueExcelService.resolveFoodSalesDataSheetIndex(bytes);
        return importFoodSalesFromExcelMultipart(file, departmentId, distributerId, sheet, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importFoodSalesFromExcelMultipart(MultipartFile file, Integer departmentId, Integer distributerId,
            int sheetIndex, boolean allowEmptyFoodSheet)
            throws IOException {
        dailyRevenueExcelService.assertSpreadsheetUpload(file);
        byte[] bytes = file.getBytes();
        GbDepartmentEntity department = departmentService.getById(departmentId);
        if (department == null) {
            throw new IllegalArgumentException("部门不存在");
        }
        Map<String, Object> depMap = new HashMap<>();
        depMap.put("depFatherId", departmentId);
        List<GbDepFoodEntity> depFoods = gbDepFoodService.queryDepAllFood(depMap);
        dailyRevenueExcelService.attachDistributerFood(depFoods);
        Map<String, GbDepFoodEntity> depFoodByDepAndFood = buildDepFoodLookupMaps(depFoods, distributerId, depMap);

        List<GbAiDailyRevenueExcelService.FoodSalesExcelCell> cells = dailyRevenueExcelService.parseFoodSalesExcel(bytes, sheetIndex);
        if (cells.isEmpty()) {
            if (!allowEmptyFoodSheet) {
                throw new IllegalArgumentException("Excel文件中没有有效的菜品销售数据");
            }
            Map<String, Object> empty = new HashMap<>();
            empty.put("inserted", 0);
            empty.put("updated", 0);
            empty.put("deleted", 0);
            empty.put("goodsRows", 0);
            empty.put("skippedUnknownFood", 0);
            empty.put("warnings", new ArrayList<String>());
            empty.put("rows", 0);
            empty.put("dailyRevenueDaysSynced", 0);
            empty.put("skippedEmptyFoodSheet", true);
            return empty;
        }

        int skippedUnknownFood = 0;
        List<String> warnings = new ArrayList<>();
        AggregatedFoodSalesBatch batch = new AggregatedFoodSalesBatch();

        for (GbAiDailyRevenueExcelService.FoodSalesExcelCell cell : cells) {
            if (cell == null || cell.getRecordDate() == null || cell.getFoodRefId() == null
                    || cell.getAmount() == null) {
                continue;
            }
            Integer depIdFromCell = cell.getDepId();
            GbDepFoodEntity depFood = null;
            if (depIdFromCell != null) {
                depFood = depFoodByDepAndFood.get(depFoodLookupKey(depIdFromCell, cell.getFoodRefId()));
            } else {
                depFood = depFoodByDepAndFood.get(depFoodLookupKey(departmentId, cell.getFoodRefId()));
            }
            if (depFood == null || depFood.getGbDfDepId() == null || depFood.getGbDfFoodId() == null) {
                skippedUnknownFood++;
                warnings.add("跳过未匹配部门菜品：部门ID=" + depIdFromCell + " 菜品ID=" + cell.getFoodRefId()
                        + " 日期 " + GbDateTimeUtils.formatDay(cell.getRecordDate()));
                continue;
            }
            batch.merge(
                    cell.getRecordDate(),
                    depFood.getGbDfDepId(),
                    departmentId,
                    depFood.getGbDfFoodId(),
                    GbConstants.FoodSalesType.NORMAL_SALE,
                    cell.getAmount(),
                    depFood.getGbDfFoodPrice(),
                    null);
        }

        FoodSalesAggResult agg = applyAggregatedFoodSalesUpserts(distributerId, batch, depFoodByDepAndFood);

        int dailyRevenueDaysSynced = 0;
        Long disLong = distributerId.longValue();
        List<Integer> dineInOutlets = resolveOutletIdsForDineInDailyRevenue(departmentId);
        for (String fullDate : agg.syncDates) {
            for (Integer depId : dineInOutlets) {
                BigDecimal dineIn = sumFoodSalesSubtotalByDepAndDay(depId, fullDate);
                gbAiDailyRevenueService.upsertDineInRevenueOnly(
                        depId.longValue(), disLong, GbDateTimeUtils.parseDay(fullDate), dineIn);
                dailyRevenueDaysSynced++;
            }
        }

        Map<String, Object> out = new HashMap<>();
        out.put("inserted", agg.inserted);
        out.put("updated", agg.updated);
        out.put("deleted", agg.deleted);
        out.put("goodsRows", agg.goodsRows);
        out.put("skippedUnknownFood", skippedUnknownFood);
        out.put("warnings", warnings);
        out.put("rows", cells.size());
        out.put("dailyRevenueDaysSynced", dailyRevenueDaysSynced);
        return out;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importDiscountFoodSalesFromCombinedSheet(MultipartFile file, Integer departmentId,
            Integer distributerId, int sheetIndex, boolean allowEmptyDiscountSheet)
            throws IOException {
        if (sheetIndex < 0) {
            Map<String, Object> skip = new HashMap<>();
            skip.put("inserted", 0);
            skip.put("updated", 0);
            skip.put("deleted", 0);
            skip.put("goodsRows", 0);
            skip.put("skippedUnknownFood", 0);
            skip.put("warnings", new ArrayList<String>());
            skip.put("rows", 0);
            skip.put("dailyRevenueDaysSynced", 0);
            skip.put("skippedMissingSheet", true);
            return skip;
        }
        dailyRevenueExcelService.assertSpreadsheetUpload(file);
        byte[] bytes = file.getBytes();
        GbDepartmentEntity department = departmentService.getById(departmentId);
        if (department == null) {
            throw new IllegalArgumentException("部门不存在");
        }
        Map<String, Object> depMap = new HashMap<>();
        depMap.put("depFatherId", departmentId);
        List<GbDepFoodEntity> depFoods = gbDepFoodService.queryDepAllFood(depMap);
        dailyRevenueExcelService.attachDistributerFood(depFoods);
        Map<String, GbDepFoodEntity> depFoodByDepAndFood = buildDepFoodLookupMaps(depFoods, distributerId, depMap);

        List<GbAiDailyRevenueExcelService.DiscountFoodSalesExcelRow> rows =
                dailyRevenueExcelService.parseCombinedTemplateDiscountFoodSalesSheet(
                        bytes, sheetIndex, departmentId.longValue());
        if (rows.isEmpty()) {
            if (!allowEmptyDiscountSheet) {
                throw new IllegalArgumentException("Excel文件中没有有效的打折/员工餐销售数据");
            }
            Map<String, Object> empty = new HashMap<>();
            empty.put("inserted", 0);
            empty.put("updated", 0);
            empty.put("deleted", 0);
            empty.put("goodsRows", 0);
            empty.put("skippedUnknownFood", 0);
            empty.put("warnings", new ArrayList<String>());
            empty.put("rows", 0);
            empty.put("dailyRevenueDaysSynced", 0);
            empty.put("skippedEmptySheet", true);
            return empty;
        }

        int skippedUnknownFood = 0;
        List<String> warnings = new ArrayList<>();
        AggregatedFoodSalesBatch batch = new AggregatedFoodSalesBatch();

        for (GbAiDailyRevenueExcelService.DiscountFoodSalesExcelRow row : rows) {
            if (row == null || row.getRecordDate() == null || row.getFoodRefId() == null
                    || row.getQuantity() == null) {
                continue;
            }
            Integer depIdFromCell = row.getDepId();
            GbDepFoodEntity depFood = depFoodByDepAndFood.get(depFoodLookupKey(depIdFromCell, row.getFoodRefId()));
            if (depFood == null || depFood.getGbDfDepId() == null || depFood.getGbDfFoodId() == null) {
                skippedUnknownFood++;
                warnings.add("跳过未匹配部门菜品：部门ID=" + depIdFromCell + " 菜品ID=" + row.getFoodRefId()
                        + " 日期 " + GbDateTimeUtils.formatDay(row.getRecordDate())
                        + " 类型 " + row.getType());
                continue;
            }
            batch.merge(
                    row.getRecordDate(),
                    depFood.getGbDfDepId(),
                    departmentId,
                    depFood.getGbDfFoodId(),
                    row.getType(),
                    row.getQuantity(),
                    depFood.getGbDfFoodPrice(),
                    row.getActualUnitPrice());
        }

        if (batch.isEmpty()) {
            Map<String, Object> none = new HashMap<>();
            none.put("inserted", 0);
            none.put("updated", 0);
            none.put("deleted", 0);
            none.put("goodsRows", 0);
            none.put("skippedUnknownFood", skippedUnknownFood);
            none.put("warnings", warnings);
            none.put("rows", rows.size());
            none.put("dailyRevenueDaysSynced", 0);
            return none;
        }

        FoodSalesAggResult agg = applyAggregatedFoodSalesUpserts(distributerId, batch, depFoodByDepAndFood);

        int dailyRevenueDaysSynced = 0;
        Long disLong = distributerId.longValue();
        List<Integer> dineInOutlets = resolveOutletIdsForDineInDailyRevenue(departmentId);
        for (String fullDate : agg.syncDates) {
            for (Integer depId : dineInOutlets) {
                BigDecimal dineIn = sumFoodSalesSubtotalByDepAndDay(depId, fullDate);
                gbAiDailyRevenueService.upsertDineInRevenueOnly(
                        depId.longValue(), disLong, GbDateTimeUtils.parseDay(fullDate), dineIn);
                dailyRevenueDaysSynced++;
            }
        }

        Map<String, Object> out = new HashMap<>();
        out.put("inserted", agg.inserted);
        out.put("updated", agg.updated);
        out.put("deleted", agg.deleted);
        out.put("goodsRows", agg.goodsRows);
        out.put("skippedUnknownFood", skippedUnknownFood);
        out.put("warnings", warnings);
        out.put("rows", rows.size());
        out.put("dailyRevenueDaysSynced", dailyRevenueDaysSynced);
        return out;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submitDailyFoodSalesAndRevenue(GbDepFoodDailySalesSubmitRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (req.getDepFatherId() == null) {
            throw new IllegalArgumentException("depFatherId 不能为空");
        }
        if (req.getDistributerId() == null) {
            throw new IllegalArgumentException("distributerId 不能为空");
        }
        if (req.getRecordDate() == null || req.getRecordDate().trim().isEmpty()) {
            throw new IllegalArgumentException("recordDate 不能为空（yyyy-MM-dd）");
        }
        String fullDate = req.getRecordDate().trim();
        Date recordDate = GbDateTimeUtils.parseDay(fullDate);
        if (recordDate == null) {
            throw new IllegalArgumentException("recordDate 格式须为 yyyy-MM-dd");
        }
        GbDepartmentEntity department = departmentService.getById(req.getDepFatherId());
        if (department == null) {
            throw new IllegalArgumentException("部门不存在");
        }
        GbDepartmentEntity subDepValidated = null;
        if (req.getSubDepId() != null) {
            subDepValidated = departmentService.getById(req.getSubDepId());
            if (subDepValidated == null) {
                throw new IllegalArgumentException("子部门不存在: " + req.getSubDepId());
            }
            if (!Objects.equals(subDepValidated.getGbDepartmentFatherId(), req.getDepFatherId())) {
                throw new IllegalArgumentException("subDepId 与 depFatherId 不是父子关系");
            }
        }
        PriorFoodSalesDeletion priorDeletion = replaceScopeFoodSalesRecordsForSubmit(
                req.getDepFatherId(), req.getSubDepId(), req.getDistributerId(), fullDate);
        boolean relaxDistributerFoodMasterFilter =
                Objects.equals(department.getGbDepartmentDisId(), req.getDistributerId())
                        || (subDepValidated != null
                                && Objects.equals(subDepValidated.getGbDepartmentDisId(), req.getDistributerId()));

        Map<String, Object> depMap = new HashMap<>();
        if (req.getSubDepId() != null) {
            depMap.put("depId", req.getSubDepId());
        } else {
            depMap.put("depFatherId", req.getDepFatherId());
        }
        List<GbDepFoodEntity> depFoods = gbDepFoodService.queryDepAllFood(depMap);
        dailyRevenueExcelService.attachDistributerFood(depFoods);
        Map<String, GbDepFoodEntity> depFoodByDepAndFood = buildDepFoodLookupMaps(
                depFoods, req.getDistributerId(), depMap, relaxDistributerFoodMasterFilter);

        AggregatedFoodSalesBatch batch = new AggregatedFoodSalesBatch();
        List<String> warnings = new ArrayList<>();
        int skippedUnknownFood = 0;

        if (req.getLines() != null) {
            for (GbDepFoodDailySalesSubmitRequest.Line line : req.getLines()) {
                if (line == null || line.getQuantity() == null
                        || line.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                Integer lineDepId = line.getDepId() != null ? line.getDepId() : req.getSubDepId();
                if (lineDepId == null || line.getFoodId() == null) {
                    skippedUnknownFood++;
                    warnings.add("跳过：未指定子部门（请在行内传 depId 或请求体传 subDepId），或 foodId 为空");
                    continue;
                }
                GbDepFoodEntity depFood = depFoodByDepAndFood.get(depFoodLookupKey(lineDepId, line.getFoodId()));
                if (depFood == null || depFood.getGbDfDepId() == null || depFood.getGbDfFoodId() == null) {
                    skippedUnknownFood++;
                    warnings.add("未匹配部门菜品：depId=" + lineDepId + " foodId=" + line.getFoodId()
                            + "（请确认 gb_dep_food 存在该组合；若部门归属批发商与主档菜品不一致，请保证请求 distributerId 与部门 gb_department_dis_id 一致）");
                    continue;
                }
                Integer fatherFromFood = parseIntSafe(depFood.getGbDfDepFatherId());
                batch.merge(
                        recordDate,
                        depFood.getGbDfDepId(),
                        fatherFromFood != null ? fatherFromFood : req.getDepFatherId(),
                        depFood.getGbDfFoodId(),
                        line.getType(),
                        line.getQuantity(),
                        depFood.getGbDfFoodPrice(),
                        line.getActualUnitPrice());
            }
        }

        Map<String, Object> foodStats = new HashMap<>();
        if (!batch.isEmpty()) {
            FoodSalesAggResult agg = applyAggregatedFoodSalesUpserts(req.getDistributerId(), batch, depFoodByDepAndFood);
            foodStats.put("inserted", agg.inserted);
            foodStats.put("updated", agg.updated);
            foodStats.put("goodsRows", agg.goodsRows);
        } else {
            foodStats.put("inserted", 0);
            foodStats.put("updated", 0);
            foodStats.put("goodsRows", 0);
        }
        foodStats.put("priorFoodSalesRowsRemoved", priorDeletion.foodSalesRows);
        foodStats.put("priorFoodGoodsSalesRowsRemoved", priorDeletion.goodsSalesRows);

        List<Integer> dineInOutlets = resolveOutletIdsForDineInDailyRevenue(req.getDepFatherId());
        boolean revenueRowIsParentOnly = dineInOutlets.size() == 1
                && dineInOutlets.get(0).equals(req.getDepFatherId());

        for (Integer depId : dineInOutlets) {
            BigDecimal dineIn = sumFoodSalesSubtotalByDepAndDay(depId, fullDate);
            gbAiDailyRevenueService.upsertDineInRevenueOnly(
                    depId.longValue(),
                    req.getDistributerId().longValue(),
                    recordDate,
                    dineIn);
        }

        if (revenueRowIsParentOnly) {
            gbAiDailyRevenueService.mergeNonDineInDailyRevenueMetrics(
                    req.getDepFatherId().longValue(),
                    req.getDistributerId().longValue(),
                    recordDate,
                    req.getDineInOrders(),
                    req.getDineInCustomers(),
                    req.getTakeoutRevenue(),
                    req.getTakeoutOrders(),
                    req.getPlatformFee(),
                    req.getNotes());
        }

        Map<String, Object> out = new HashMap<>();
        out.put("foodSales", foodStats);
        out.put("skippedUnknownFood", skippedUnknownFood);
        out.put("warnings", warnings);
        Map<String, Object> sync = new HashMap<>();
        sync.put("fullDate", fullDate);
        sync.put("dineInRevenueFromDishes", sumFoodSalesSubtotalByFatherAndDay(req.getDepFatherId(), fullDate));
        out.put("dailyRevenueSync", sync);
        return out;
    }

    @Override
    public Map<String, Object> getDailyFoodSalesAndRevenue(Integer depFatherId, Integer distributerId,
            String recordDate) {
        if (depFatherId == null) {
            throw new IllegalArgumentException("depFatherId 不能为空");
        }
        if (distributerId == null) {
            throw new IllegalArgumentException("distributerId 不能为空");
        }

        String fullDate = recordDate != null && !recordDate.trim().isEmpty()
                ? recordDate.trim()
                : GbDateTimeUtils.formatDay(GbDateTimeUtils.todayChina());
        Date recordDay;
        try {
            recordDay = GbDateTimeUtils.parseDay(fullDate);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("recordDate 格式须为 yyyy-MM-dd");
        }

        LambdaQueryWrapper<GbDepFoodSalesEntity> salesQ = new LambdaQueryWrapper<GbDepFoodSalesEntity>()
                .eq(GbDepFoodSalesEntity::getGbDfsDepFatherId, depFatherId)
                .eq(GbDepFoodSalesEntity::getGbDfsDistributerId, distributerId)
                .eq(GbDepFoodSalesEntity::getGbDfsFullDate, fullDate);

        salesQ.orderByAsc(GbDepFoodSalesEntity::getGbDfsDepId)
                .orderByAsc(GbDepFoodSalesEntity::getGbDfsFoodId)
                .orderByAsc(GbDepFoodSalesEntity::getGbDfsType);
        List<GbDepFoodSalesEntity> salesRows = gbDepFoodSalesService.list(salesQ);

        List<GbDepFoodDailySalesSubmitRequest.Line> lines = new ArrayList<>();
        if (salesRows != null) {
            for (GbDepFoodSalesEntity s : salesRows) {
                if (s == null || s.getGbDfsDepId() == null || s.getGbDfsFoodId() == null) {
                    continue;
                }
                BigDecimal qty = parseAmount(s.getGbDfsAmount());
                if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                GbDepFoodDailySalesSubmitRequest.Line line = new GbDepFoodDailySalesSubmitRequest.Line();
                line.setDepId(s.getGbDfsDepId());
                line.setFoodId(s.getGbDfsFoodId());
                line.setQuantity(qty);
                line.setType(GbDepFoodSalesMetricsSupport.resolveType(s));
                line.setActualUnitPrice(s.getGbDfsActualUnitPrice());
                lines.add(line);
            }
        }

        Date dayStart = GbDateTimeUtils.startOfDay(recordDay);
        Date dayEnd = GbDateTimeUtils.endOfDay(recordDay);
        Map<String, Object> drParams = new HashMap<>();
        drParams.put("departmentScopeIds", gbAiDailyRevenueService.departmentScopeIdsForParent(depFatherId.longValue()));
        drParams.put("startDate", dayStart);
        drParams.put("endDate", dayEnd);
        List<GbAiDailyRevenueEntity> drList = gbAiDailyRevenueService.queryDailyRevenueListByParams(drParams);
        GbAiDailyRevenueEntity revRow = null;
        if (drList != null && !drList.isEmpty()) {
            revRow = GbAiDailyRevenueServiceImpl.mergeRevenueRowsForSameDay(drList, depFatherId.longValue());
        }

        GbDepFoodDailySalesSubmitRequest dto = new GbDepFoodDailySalesSubmitRequest();
        dto.setRecordDate(fullDate);
        dto.setDepFatherId(depFatherId);
        dto.setDistributerId(distributerId);
        dto.setLines(lines);
        if (revRow != null) {
            dto.setDineInOrders(revRow.getGbAiDailyRevenueDineInOrders());
            dto.setDineInCustomers(revRow.getGbAiDailyRevenueDineInCustomers());
            dto.setTakeoutRevenue(revRow.getGbAiDailyRevenueTakeoutRevenue());
            dto.setTakeoutOrders(revRow.getGbAiDailyRevenueTakeoutOrders());
            dto.setPlatformFee(revRow.getGbAiDailyRevenuePlatformFee());
            dto.setNotes(revRow.getGbAiDailyRevenueNotes());
        }

        BigDecimal dineInFromDishes = sumFoodSalesSubtotalByFatherAndDay(depFatherId, fullDate);

        Map<String, Object> out = new HashMap<>();
        out.put("submitShape", dto);
        out.put("lineCount", lines.size());
        Map<String, Object> daily = buildDailyRevenueDayPayload(revRow, fullDate, depFatherId, distributerId, dineInFromDishes);
        out.put("dailyRevenue", daily);
        out.put("recordDate", fullDate);
        out.put("depFatherId", depFatherId);
        out.put("subDepartments", buildSubDepartmentDishSalesPayload(
                depFatherId, distributerId, fullDate, salesRows));
        return out;
    }

    /**
     * 按子部门返回当日菜品列表，每道菜附带五类销售汇总（经营/赠送/员工餐/销售额等）。
     */
    private List<Map<String, Object>> buildSubDepartmentDishSalesPayload(
            Integer depFatherId,
            Integer distributerId,
            String fullDate,
            List<GbDepFoodSalesEntity> salesRows) {
        List<GbDepartmentEntity> outlets = resolveOutletsForView(depFatherId);
        if (outlets.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Object> depMap = new HashMap<>();
        depMap.put("depFatherId", depFatherId);
        List<GbDepFoodEntity> depFoods = gbDepFoodService.queryDepAllFood(depMap);
        if (depFoods == null) {
            depFoods = Collections.emptyList();
        }

        Map<Integer, GbDistributerFoodEntity> disFoodById = loadDisFoodByIds(depFoods, salesRows);
        Map<Integer, GbDistributerFoodEntity> parentFoodById = loadParentFoodByIds(disFoodById);

        Map<Integer, List<GbDepFoodEntity>> foodsByDepId = new LinkedHashMap<>();
        for (GbDepFoodEntity f : depFoods) {
            if (f == null || f.getGbDfDepId() == null || f.getGbDfFoodId() == null) {
                continue;
            }
            foodsByDepId.computeIfAbsent(f.getGbDfDepId(), k -> new ArrayList<>()).add(f);
        }

        Map<String, List<GbDepFoodSalesEntity>> salesByDepFood = groupSalesByDepAndFood(salesRows);

        List<Map<String, Object>> subDepartments = new ArrayList<>();
        for (GbDepartmentEntity outlet : outlets) {
            if (outlet == null || outlet.getGbDepartmentId() == null) {
                continue;
            }
            Integer depId = outlet.getGbDepartmentId();
            Map<String, Object> deptBlock = new LinkedHashMap<>();
            deptBlock.put("depId", depId);
            deptBlock.put("depName", outlet.getGbDepartmentName() != null ? outlet.getGbDepartmentName().trim() : "");

            List<GbDepFoodEntity> deptFoods = foodsByDepId.getOrDefault(depId, new ArrayList<>());
            deptFoods.sort((a, b) -> compareDepFoodForDisplay(a, b, disFoodById));

            List<Map<String, Object>> dishes = new ArrayList<>();
            Set<Integer> seenFoodIds = new TreeSet<>();
            for (GbDepFoodEntity df : deptFoods) {
                seenFoodIds.add(df.getGbDfFoodId());
                List<GbDepFoodSalesEntity> daySales = salesByDepFood.get(depFoodSalesKey(depId, df.getGbDfFoodId()));
                dishes.add(buildDishSalesItem(df, disFoodById, daySales));
            }

            for (Map.Entry<String, List<GbDepFoodSalesEntity>> e : salesByDepFood.entrySet()) {
                DepFoodSalesKey parsed = DepFoodSalesKey.parse(e.getKey());
                if (parsed == null || !depId.equals(parsed.depId) || seenFoodIds.contains(parsed.foodId)) {
                    continue;
                }
                seenFoodIds.add(parsed.foodId);
                dishes.add(buildDishSalesItemFromSalesOnly(parsed.foodId, disFoodById, e.getValue()));
            }

            List<Map<String, Object>> foodCategories = groupDishItemsByFoodCategory(
                    dishes, disFoodById, parentFoodById);
            deptBlock.put("foodCategories", foodCategories);
            deptBlock.put("dishCount", dishes.size());
            subDepartments.add(deptBlock);
        }
        return subDepartments;
    }

    private Map<Integer, GbDistributerFoodEntity> loadParentFoodByIds(
            Map<Integer, GbDistributerFoodEntity> disFoodById) {
        Set<Integer> parentIds = new TreeSet<>();
        if (disFoodById != null) {
            for (GbDistributerFoodEntity f : disFoodById.values()) {
                if (f != null && f.getGbDfFoodFatherId() != null && f.getGbDfFoodFatherId() > 0) {
                    parentIds.add(f.getGbDfFoodFatherId());
                }
            }
        }
        Map<Integer, GbDistributerFoodEntity> out = new HashMap<>();
        if (parentIds.isEmpty()) {
            return out;
        }
        for (GbDistributerFoodEntity e : gbDistributerFoodService.queryByIds(new ArrayList<>(parentIds))) {
            if (e != null && e.getGbDistributerFoodId() != null) {
                out.put(e.getGbDistributerFoodId(), e);
            }
        }
        return out;
    }

    /**
     * 与 {@code depGetDepFoodList} 一致：按批发商菜品父级分类分组，分类下为带销售汇总的菜品行。
     */
    private static List<Map<String, Object>> groupDishItemsByFoodCategory(
            List<Map<String, Object>> dishes,
            Map<Integer, GbDistributerFoodEntity> disFoodById,
            Map<Integer, GbDistributerFoodEntity> parentFoodById) {
        Map<Integer, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        List<Map<String, Object>> noParent = new ArrayList<>();
        if (dishes != null) {
            for (Map<String, Object> dish : dishes) {
                if (dish == null) {
                    continue;
                }
                Object rawFoodId = dish.get("foodId");
                Integer foodId = rawFoodId instanceof Number ? ((Number) rawFoodId).intValue() : null;
                Integer parentId = resolveParentFoodCategoryId(foodId, disFoodById);
                if (parentId == null) {
                    noParent.add(dish);
                } else {
                    grouped.computeIfAbsent(parentId, k -> new ArrayList<>()).add(dish);
                }
            }
        }

        List<Map<String, Object>> categories = new ArrayList<>();
        for (Map.Entry<Integer, List<Map<String, Object>>> entry : grouped.entrySet()) {
            categories.add(buildFoodCategoryBlock(entry.getKey(), entry.getValue(), parentFoodById));
        }
        if (!noParent.isEmpty()) {
            categories.add(buildFoodCategoryBlock(null, noParent, parentFoodById));
        }
        return categories;
    }

    private static Integer resolveParentFoodCategoryId(
            Integer foodId, Map<Integer, GbDistributerFoodEntity> disFoodById) {
        if (foodId == null || disFoodById == null) {
            return null;
        }
        GbDistributerFoodEntity disFood = disFoodById.get(foodId);
        if (disFood == null || disFood.getGbDfFoodFatherId() == null || disFood.getGbDfFoodFatherId() <= 0) {
            return null;
        }
        return disFood.getGbDfFoodFatherId();
    }

    private static Map<String, Object> buildFoodCategoryBlock(
            Integer parentFoodId,
            List<Map<String, Object>> dishes,
            Map<Integer, GbDistributerFoodEntity> parentFoodById) {
        Map<String, Object> block = new LinkedHashMap<>();
        GbDistributerFoodEntity parentFood = parentFoodId != null && parentFoodById != null
                ? parentFoodById.get(parentFoodId) : null;
        block.put("parentFoodId", parentFoodId);
        block.put("parentFood", parentFood);
        if (parentFood != null && parentFood.getGbDfFoodName() != null
                && !parentFood.getGbDfFoodName().trim().isEmpty()) {
            block.put("categoryName", parentFood.getGbDfFoodName().trim());
        } else {
            block.put("categoryName", parentFoodId == null ? "未分类" : "");
        }
        block.put("dishes", dishes != null ? dishes : Collections.emptyList());
        block.put("dishCount", dishes != null ? dishes.size() : 0);
        return block;
    }

    private List<GbDepartmentEntity> resolveOutletsForView(Integer depFatherId) {
        List<GbDepartmentEntity> subs = departmentService.querySubDepartments(depFatherId);
        if (subs != null && !subs.isEmpty()) {
            return subs;
        }
        GbDepartmentEntity parent = departmentService.getById(depFatherId);
        return parent != null ? Collections.singletonList(parent) : Collections.emptyList();
    }

    private static Map<String, List<GbDepFoodSalesEntity>> groupSalesByDepAndFood(List<GbDepFoodSalesEntity> salesRows) {
        Map<String, List<GbDepFoodSalesEntity>> out = new LinkedHashMap<>();
        if (salesRows == null) {
            return out;
        }
        for (GbDepFoodSalesEntity row : salesRows) {
            if (row == null || row.getGbDfsDepId() == null || row.getGbDfsFoodId() == null) {
                continue;
            }
            out.computeIfAbsent(depFoodSalesKey(row.getGbDfsDepId(), row.getGbDfsFoodId()), k -> new ArrayList<>()).add(row);
        }
        return out;
    }

    private Map<Integer, GbDistributerFoodEntity> loadDisFoodByIds(
            List<GbDepFoodEntity> depFoods, List<GbDepFoodSalesEntity> salesRows) {
        Set<Integer> foodIds = new TreeSet<>();
        if (depFoods != null) {
            for (GbDepFoodEntity f : depFoods) {
                if (f != null && f.getGbDfFoodId() != null) {
                    foodIds.add(f.getGbDfFoodId());
                }
            }
        }
        if (salesRows != null) {
            for (GbDepFoodSalesEntity s : salesRows) {
                if (s != null && s.getGbDfsFoodId() != null) {
                    foodIds.add(s.getGbDfsFoodId());
                }
            }
        }
        Map<Integer, GbDistributerFoodEntity> out = new HashMap<>();
        if (foodIds.isEmpty()) {
            return out;
        }
        for (GbDistributerFoodEntity e : gbDistributerFoodService.queryByIds(new ArrayList<>(foodIds))) {
            if (e != null && e.getGbDistributerFoodId() != null) {
                out.put(e.getGbDistributerFoodId(), e);
            }
        }
        return out;
    }

    private Map<String, Object> buildDishSalesItem(
            GbDepFoodEntity df,
            Map<Integer, GbDistributerFoodEntity> disFoodById,
            List<GbDepFoodSalesEntity> daySales) {
        Map<String, Object> m = DishDayRollup.fromRows(daySales).toDishItemMap();
        m.put("gbDepFoodId", df.getGbDepFoodId());
        m.put("foodId", df.getGbDfFoodId());
        m.put("dishId", df.getGbDfFoodId());
        m.put("dishName", resolveDishDisplayName(df, disFoodById));
        m.put("listPrice", moneyPlain(df.getGbDfFoodPrice()));
        return m;
    }

    private Map<String, Object> buildDishSalesItemFromSalesOnly(
            Integer foodId,
            Map<Integer, GbDistributerFoodEntity> disFoodById,
            List<GbDepFoodSalesEntity> daySales) {
        Map<String, Object> m = DishDayRollup.fromRows(daySales).toDishItemMap();
        m.put("gbDepFoodId", null);
        m.put("foodId", foodId);
        m.put("dishId", foodId);
        GbDistributerFoodEntity disFood = disFoodById.get(foodId);
        String name = disFood != null && disFood.getGbDfFoodName() != null
                ? disFood.getGbDfFoodName().trim() : "";
        m.put("dishName", name);
        m.put("listPrice", moneyPlain(disFood == null ? null : disFood.getGbDfFoodPrice()));
        return m;
    }

    private static String resolveDishDisplayName(
            GbDepFoodEntity df, Map<Integer, GbDistributerFoodEntity> disFoodById) {
        if (df.getGbDfFoodName() != null && !df.getGbDfFoodName().trim().isEmpty()) {
            return df.getGbDfFoodName().trim();
        }
        GbDistributerFoodEntity disFood = disFoodById.get(df.getGbDfFoodId());
        if (disFood != null && disFood.getGbDfFoodName() != null && !disFood.getGbDfFoodName().trim().isEmpty()) {
            return disFood.getGbDfFoodName().trim();
        }
        return "";
    }

    private static int compareDepFoodForDisplay(
            GbDepFoodEntity a, GbDepFoodEntity b, Map<Integer, GbDistributerFoodEntity> disFoodById) {
        Integer sa = a.getGbDfGoodsSort();
        Integer sb = b.getGbDfGoodsSort();
        if (sa != null && sb != null && !sa.equals(sb)) {
            return sa.compareTo(sb);
        }
        if (sa != null && sb == null) {
            return -1;
        }
        if (sa == null && sb != null) {
            return 1;
        }
        return resolveDishDisplayName(a, disFoodById).compareTo(resolveDishDisplayName(b, disFoodById));
    }

    private static String depFoodSalesKey(Integer depId, Integer foodId) {
        return depId + "#" + foodId;
    }

    private static final class DepFoodSalesKey {
        final Integer depId;
        final Integer foodId;

        private DepFoodSalesKey(Integer depId, Integer foodId) {
            this.depId = depId;
            this.foodId = foodId;
        }

        static DepFoodSalesKey parse(String key) {
            if (key == null || !key.contains("#")) {
                return null;
            }
            int idx = key.indexOf('#');
            try {
                return new DepFoodSalesKey(Integer.valueOf(key.substring(0, idx)), Integer.valueOf(key.substring(idx + 1)));
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    private static Map<String, Object> buildDailyRevenueDayPayload(
            GbAiDailyRevenueEntity revRow,
            String recordDate,
            Integer depFatherId,
            Integer distributerId,
            BigDecimal dineInRevenueFromDishes) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("recordDate", recordDate);
        m.put("depFatherId", depFatherId);
        m.put("distributerId", distributerId);
        m.put("hasRecord", Boolean.valueOf(revRow != null));
        m.put("dineInRevenueFromDishes", dineInRevenueFromDishes);
        if (revRow == null) {
            m.put("gbAiDailyRevenueId", null);
            m.put("storedDistributerId", null);
            m.put("dineInRevenueStored", null);
            m.put("grossRevenue", null);
            m.put("netRevenue", null);
            m.put("dineInOrders", null);
            m.put("dineInCustomers", null);
            m.put("takeoutRevenue", null);
            m.put("takeoutOrders", null);
            m.put("platformFee", null);
            m.put("notes", null);
            m.put("weekday", null);
            m.put("holiday", null);
            return m;
        }
        m.put("gbAiDailyRevenueId", revRow.getGbAiDailyRevenueId());
        m.put("storedDistributerId", revRow.getGbAiDailyRevenueDistributerId());
        m.put("dineInRevenueStored", revRow.getGbAiDailyRevenueDineInRevenue());
        m.put("grossRevenue", revRow.getGbAiDailyRevenueGrossRevenue());
        m.put("netRevenue", revRow.getGbAiDailyRevenueNetRevenue());
        m.put("dineInOrders", revRow.getGbAiDailyRevenueDineInOrders());
        m.put("dineInCustomers", revRow.getGbAiDailyRevenueDineInCustomers());
        m.put("takeoutRevenue", revRow.getGbAiDailyRevenueTakeoutRevenue());
        m.put("takeoutOrders", revRow.getGbAiDailyRevenueTakeoutOrders());
        m.put("platformFee", revRow.getGbAiDailyRevenuePlatformFee());
        m.put("notes", revRow.getGbAiDailyRevenueNotes());
        m.put("weekday", revRow.getGbAiDailyRevenueWeekday());
        m.put("holiday", revRow.getGbAiDailyRevenueHoliday());
        return m;
    }

    @Override
    public Map<String, Object> updateDailyFoodSalesAndRevenue(GbDepFoodDailySalesSubmitRequest request) {
        return submitDailyFoodSalesAndRevenue(request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> upsertDishSalesLine(GbDepFoodDishSalesLineRequest req) {
        SingleLineContext ctx = resolveSingleLineContext(req, true);
        Integer type = ctx.type;
        validateFoodSalesType(type);

        GbDepFoodSalesEntity existing = gbDepFoodSalesWriteSupport.findExisting(
                ctx.lineDepId, req.getFoodId(), ctx.fullDate, type);
        BigDecimal targetQty = req.getQuantity();
        String quantityMode = resolveQuantityMode(req.getQuantityMode());
        if (req.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
            targetQty = BigDecimal.ZERO;
        } else if ("ADD".equals(quantityMode)) {
            if (existing != null) {
                targetQty = parseAmount(existing.getGbDfsAmount()).add(req.getQuantity());
            }
        }

        if (targetQty.compareTo(BigDecimal.ZERO) <= 0) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("quantityMode", quantityMode);
            out.put("addedQuantity", "ADD".equals(quantityMode) ? req.getQuantity() : null);
            if (existing == null || existing.getGbDepFoodSalesId() == null) {
                out.put("inserted", false);
                out.put("updated", false);
                out.put("deleted", false);
                out.put("goodsRows", 0);
                out.put("goodsRowsRemoved", 0);
                out.put("foodSales", null);
                out.put("dailyRevenueSync", buildDailyRevenueSyncPayload(
                        GbConstants.FoodSalesType.isOperationalSales(type), ctx.depFatherId, ctx.fullDate));
                return out;
            }
            int goodsRemoved = (int) gbDepFoodGoodsSalesService.count(
                    new LambdaQueryWrapper<GbDepFoodGoodsSalesEntity>()
                            .eq(GbDepFoodGoodsSalesEntity::getGbDfgsFoodSalesId, existing.getGbDepFoodSalesId()));
            gbDepFoodSalesWriteSupport.removeGoodsSalesForParent(existing.getGbDepFoodSalesId());
            gbDepFoodSalesService.removeById(existing.getGbDepFoodSalesId());
            out.put("inserted", false);
            out.put("updated", false);
            out.put("deleted", true);
            out.put("goodsRows", 0);
            out.put("goodsRowsRemoved", goodsRemoved);
            out.put("foodSales", buildSingleLineFoodSalesPayload(existing));
            if (GbConstants.FoodSalesType.isOperationalSales(type)) {
                syncDineInRevenueFromDishes(ctx.depFatherId, req.getDistributerId(), ctx.fullDate, ctx.recordDate);
            }
            out.put("dailyRevenueSync", buildDailyRevenueSyncPayload(
                    GbConstants.FoodSalesType.isOperationalSales(type), ctx.depFatherId, ctx.fullDate));
            return out;
        }

        GbDepFoodSalesWriteSupport.UpsertResult ur = upsertViaWriteSupport(
                ctx.lineDepId,
                ctx.depFatherIdFromFood,
                req.getFoodId(),
                req.getDistributerId(),
                ctx.recordDate,
                ctx.fullDate,
                targetQty,
                type,
                ctx.depFood.getGbDfFoodPrice(),
                req.getActualUnitPrice());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("inserted", ur.inserted);
        out.put("updated", !ur.inserted);
        out.put("deleted", false);
        out.put("goodsRows", ur.goodsRows);
        out.put("quantityMode", quantityMode);
        out.put("addedQuantity", "ADD".equals(quantityMode) ? req.getQuantity() : null);
        out.put("foodSales", buildSingleLineFoodSalesPayload(ur.entity));

        if (GbConstants.FoodSalesType.isOperationalSales(type)) {
            syncDineInRevenueFromDishes(ctx.depFatherId, req.getDistributerId(), ctx.fullDate, ctx.recordDate);
            Map<String, Object> sync = new LinkedHashMap<>();
            sync.put("synced", true);
            sync.put("dineInRevenueFromDishes",
                    sumFoodSalesSubtotalByFatherAndDay(ctx.depFatherId, ctx.fullDate));
            out.put("dailyRevenueSync", sync);
        } else {
            Map<String, Object> sync = new LinkedHashMap<>();
            sync.put("synced", false);
            out.put("dailyRevenueSync", sync);
        }
        return out;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> deleteDishSalesLine(GbDepFoodDishSalesLineRequest req) {
        SingleLineContext ctx = resolveSingleLineContext(req, false);
        Integer type = ctx.type;
        validateFoodSalesType(type);

        GbDepFoodSalesEntity existing = gbDepFoodSalesWriteSupport.findExisting(
                ctx.lineDepId, req.getFoodId(), ctx.fullDate, type);
        Map<String, Object> out = new LinkedHashMap<>();
        if (existing == null || existing.getGbDepFoodSalesId() == null) {
            out.put("deleted", false);
            out.put("goodsRowsRemoved", 0);
            out.put("foodSales", null);
            out.put("dailyRevenueSync", buildDailyRevenueSyncPayload(false, ctx.depFatherId, ctx.fullDate));
            return out;
        }

        int goodsRemoved = (int) gbDepFoodGoodsSalesService.count(
                new LambdaQueryWrapper<GbDepFoodGoodsSalesEntity>()
                        .eq(GbDepFoodGoodsSalesEntity::getGbDfgsFoodSalesId, existing.getGbDepFoodSalesId()));
        gbDepFoodSalesWriteSupport.removeGoodsSalesForParent(existing.getGbDepFoodSalesId());
        gbDepFoodSalesService.removeById(existing.getGbDepFoodSalesId());

        out.put("deleted", true);
        out.put("goodsRowsRemoved", goodsRemoved);
        out.put("foodSales", buildSingleLineFoodSalesPayload(existing));

        if (GbConstants.FoodSalesType.isOperationalSales(type)) {
            syncDineInRevenueFromDishes(ctx.depFatherId, req.getDistributerId(), ctx.fullDate, ctx.recordDate);
        }
        out.put("dailyRevenueSync", buildDailyRevenueSyncPayload(
                GbConstants.FoodSalesType.isOperationalSales(type), ctx.depFatherId, ctx.fullDate));
        return out;
    }

    @Override
    public Map<String, Object> getDishDailySalesRange(GbDepFoodDishDailySalesRangeQueryRequest req) {
        DishDailySalesScope scope = resolveDishDailySalesScope(req);
        List<GbDepFoodSalesEntity> salesRows = listFoodSalesInRange(scope);

        Map<String, List<GbDepFoodSalesEntity>> byDate = new LinkedHashMap<>();
        for (GbDepFoodSalesEntity row : salesRows) {
            if (row == null || row.getGbDfsFullDate() == null) {
                continue;
            }
            byDate.computeIfAbsent(row.getGbDfsFullDate().trim(), k -> new ArrayList<>()).add(row);
        }

        List<Map<String, Object>> dailyRows = new ArrayList<>();
        DishDayRollup rangeRollup = new DishDayRollup();
        LocalDate cursor = scope.start;
        while (!cursor.isAfter(scope.end)) {
            String day = cursor.toString();
            List<GbDepFoodSalesEntity> dayRows = byDate.getOrDefault(day, Collections.emptyList());
            DishDayRollup dayRollup = DishDayRollup.fromRows(dayRows);
            rangeRollup.merge(dayRollup);
            Map<String, Object> daily = dayRollup.toDailyRowMap(day);
            daily.put("records", buildDailySalesRecordPayloads(dayRows));
            dailyRows.add(daily);
            cursor = cursor.plusDays(1);
        }

        GbDistributerFoodEntity food = gbDistributerFoodService.queryObject(scope.foodId);
        Map<String, Object> dish = new LinkedHashMap<>();
        dish.put("dishId", scope.foodId);
        dish.put("foodId", scope.foodId);
        dish.put("dishName", food != null && food.getGbDfFoodName() != null ? food.getGbDfFoodName().trim() : "");
        String img = food != null && food.getGbDfFoodImg() != null && !food.getGbDfFoodImg().trim().isEmpty()
                ? food.getGbDfFoodImg().trim()
                : (food != null ? food.getGbDfFoodImgLarge() : null);
        dish.put("imageUrl", img != null ? img : "");
        dish.put("listPrice", moneyPlain(food == null ? null : food.getGbDfFoodPrice()));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("startDate", scope.startDate);
        out.put("endDate", scope.endDate);
        out.put("stopDate", scope.endDate);
        out.put("dish", dish);
        out.put("summary", rangeRollup.toSummaryMap());
        out.put("dailyRows", dailyRows);
        return out;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> saveDishDailySalesBatch(GbDepFoodDishDailySalesBatchSaveRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        Integer foodId = req.getDishId() != null ? req.getDishId() : req.getFoodId();
        if (foodId == null) {
            throw new IllegalArgumentException("dishId/foodId 不能为空");
        }
        Integer disId = req.getDisId() != null ? req.getDisId() : req.getDistributerId();
        if (disId == null) {
            throw new IllegalArgumentException("disId/distributerId 不能为空");
        }
        if (req.getDepFatherId() == null) {
            throw new IllegalArgumentException("depFatherId 不能为空");
        }
        Integer lineDepId = req.getDepartmentId() != null ? req.getDepartmentId()
                : (req.getDepId() != null ? req.getDepId() : req.getSubDepId());
        if (lineDepId == null) {
            throw new IllegalArgumentException("departmentId/depId/subDepId 不能为空");
        }
        String fullDate = req.getSalesDate();
        if (fullDate == null || fullDate.trim().isEmpty()) {
            fullDate = req.getRecordDate();
        }
        if (fullDate == null || fullDate.trim().isEmpty()) {
            throw new IllegalArgumentException("salesDate/recordDate 不能为空（yyyy-MM-dd）");
        }
        fullDate = fullDate.trim();
        Date recordDate = GbDateTimeUtils.parseDay(fullDate);
        if (recordDate == null) {
            throw new IllegalArgumentException("salesDate 格式须为 yyyy-MM-dd");
        }
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new IllegalArgumentException("items 不能为空");
        }

        GbDepFoodDishSalesLineRequest lineCtx = new GbDepFoodDishSalesLineRequest();
        lineCtx.setDepFatherId(req.getDepFatherId());
        lineCtx.setDistributerId(disId);
        lineCtx.setFoodId(foodId);
        lineCtx.setDepId(lineDepId);
        lineCtx.setRecordDate(fullDate);
        SingleLineContext ctx = resolveSingleLineContext(lineCtx, false);

        int inserted = 0;
        int updated = 0;
        int deleted = 0;
        int goodsRows = 0;
        boolean operationalTouched = false;

        for (GbDepFoodDishDailySalesBatchSaveRequest.Item item : req.getItems()) {
            if (item == null) {
                continue;
            }
            Integer type = GbConstants.FoodSalesType.normalize(item.getType());
            validateFoodSalesType(type);
            BigDecimal portions = item.getPortions() != null ? item.getPortions() : item.getQuantity();
            if (portions == null) {
                portions = BigDecimal.ZERO;
            }

            GbDepFoodSalesEntity existing = gbDepFoodSalesWriteSupport.findExisting(
                    ctx.lineDepId, foodId, fullDate, type);
            if (portions.compareTo(BigDecimal.ZERO) <= 0) {
                if (existing != null && existing.getGbDepFoodSalesId() != null) {
                    gbDepFoodSalesWriteSupport.removeGoodsSalesForParent(existing.getGbDepFoodSalesId());
                    gbDepFoodSalesService.removeById(existing.getGbDepFoodSalesId());
                    deleted++;
                    if (GbConstants.FoodSalesType.isOperationalSales(type)) {
                        operationalTouched = true;
                    }
                }
                continue;
            }

            BigDecimal actualUnitPrice = item.getActualUnitPrice();
            if (GbConstants.FoodSalesType.isNonOperationalConsumption(type)) {
                actualUnitPrice = BigDecimal.ZERO;
            }

            GbDepFoodSalesWriteSupport.UpsertResult ur = upsertViaWriteSupport(
                    ctx.lineDepId,
                    ctx.depFatherIdFromFood,
                    foodId,
                    disId,
                    recordDate,
                    fullDate,
                    portions,
                    type,
                    ctx.depFood.getGbDfFoodPrice(),
                    actualUnitPrice);
            if (ur.inserted) {
                inserted++;
            } else {
                updated++;
            }
            goodsRows += ur.goodsRows;
            if (GbConstants.FoodSalesType.isOperationalSales(type)) {
                operationalTouched = true;
            }
        }

        if (operationalTouched) {
            syncDineInRevenueFromDishes(ctx.depFatherId, disId, fullDate, recordDate);
        }

        List<GbDepFoodSalesEntity> dayRows = gbDepFoodSalesService.list(
                new LambdaQueryWrapper<GbDepFoodSalesEntity>()
                        .eq(GbDepFoodSalesEntity::getGbDfsDepId, ctx.lineDepId)
                        .eq(GbDepFoodSalesEntity::getGbDfsFoodId, foodId)
                        .eq(GbDepFoodSalesEntity::getGbDfsFullDate, fullDate)
                        .orderByAsc(GbDepFoodSalesEntity::getGbDfsType));
        DishDayRollup dayRollup = DishDayRollup.fromRows(dayRows);
        Map<String, Object> dailyRow = dayRollup.toDailyRowMap(fullDate);
        dailyRow.put("records", buildDailySalesRecordPayloads(dayRows));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("dishId", foodId);
        out.put("foodId", foodId);
        out.put("departmentId", ctx.lineDepId);
        out.put("salesDate", fullDate);
        out.put("inserted", inserted);
        out.put("updated", updated);
        out.put("deleted", deleted);
        out.put("goodsRows", goodsRows);
        out.put("dailyRow", dailyRow);
        out.put("dailyRevenueSync", buildDailyRevenueSyncPayload(operationalTouched, ctx.depFatherId, fullDate));
        return out;
    }

    private List<GbDepFoodSalesEntity> listFoodSalesInRange(DishDailySalesScope scope) {
        LambdaQueryWrapper<GbDepFoodSalesEntity> q = new LambdaQueryWrapper<GbDepFoodSalesEntity>()
                .eq(GbDepFoodSalesEntity::getGbDfsDistributerId, scope.disId)
                .eq(GbDepFoodSalesEntity::getGbDfsFoodId, scope.foodId)
                .ge(GbDepFoodSalesEntity::getGbDfsFullDate, scope.startDate)
                .le(GbDepFoodSalesEntity::getGbDfsFullDate, scope.endDate);
        if (scope.subDepId != null) {
            q.eq(GbDepFoodSalesEntity::getGbDfsDepId, scope.subDepId);
        } else {
            q.eq(GbDepFoodSalesEntity::getGbDfsDepFatherId, scope.depFatherId);
        }
        q.orderByAsc(GbDepFoodSalesEntity::getGbDfsFullDate)
                .orderByAsc(GbDepFoodSalesEntity::getGbDfsType);
        List<GbDepFoodSalesEntity> rows = gbDepFoodSalesService.list(q);
        return rows != null ? rows : Collections.emptyList();
    }

    private DishDailySalesScope resolveDishDailySalesScope(GbDepFoodDishDailySalesRangeQueryRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        Integer foodId = req.getDishId() != null ? req.getDishId() : req.getFoodId();
        if (foodId == null) {
            throw new IllegalArgumentException("dishId/foodId 不能为空");
        }
        Integer disId = req.getDisId() != null ? req.getDisId() : req.getDistributerId();
        if (disId == null) {
            throw new IllegalArgumentException("disId/distributerId 不能为空");
        }
        if (req.getDepFatherId() == null) {
            throw new IllegalArgumentException("depFatherId 不能为空");
        }
        String startDate = req.getStartDate();
        if (startDate == null || startDate.trim().isEmpty()) {
            throw new IllegalArgumentException("startDate 不能为空");
        }
        startDate = startDate.trim();
        String endDate = req.getEndDate();
        if (endDate == null || endDate.trim().isEmpty()) {
            endDate = req.getStopDate();
        }
        if (endDate == null || endDate.trim().isEmpty()) {
            throw new IllegalArgumentException("endDate/stopDate 不能为空");
        }
        endDate = endDate.trim();
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("startDate 不能晚于 endDate");
        }
        if (req.getSubDepId() != null) {
            GbDepartmentEntity subDep = departmentService.getById(req.getSubDepId());
            if (subDep == null) {
                throw new IllegalArgumentException("子部门不存在: " + req.getSubDepId());
            }
            if (!Objects.equals(subDep.getGbDepartmentFatherId(), req.getDepFatherId())) {
                throw new IllegalArgumentException("subDepId 与 depFatherId 不是父子关系");
            }
        }
        DishDailySalesScope scope = new DishDailySalesScope();
        scope.foodId = foodId;
        scope.disId = disId;
        scope.depFatherId = req.getDepFatherId();
        scope.subDepId = req.getSubDepId();
        scope.startDate = startDate;
        scope.endDate = endDate;
        scope.start = start;
        scope.end = end;
        return scope;
    }

    private static List<Map<String, Object>> buildDailySalesRecordPayloads(List<GbDepFoodSalesEntity> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (GbDepFoodSalesEntity s : rows) {
            if (s == null) {
                continue;
            }
            Integer type = GbDepFoodSalesMetricsSupport.resolveType(s);
            Map<String, Object> rec = new LinkedHashMap<>();
            rec.put("salesId", s.getGbDepFoodSalesId());
            rec.put("type", type);
            rec.put("typeName", GbConstants.FoodSalesType.displayName(type));
            rec.put("portions", qtyPlain(GbDepFoodSalesMetricsSupport.rowQty(s)));
            rec.put("originalUnitPrice", moneyPlain(s.getGbDfsOriginalUnitPrice()));
            rec.put("actualUnitPrice", moneyPlain(s.getGbDfsActualUnitPrice()));
            rec.put("amount", moneyPlain(GbDepFoodSalesMetricsSupport.rowSubtotal(s)));
            out.add(rec);
        }
        return out;
    }

    private static final class DishDailySalesScope {
        Integer foodId;
        Integer disId;
        Integer depFatherId;
        Integer subDepId;
        String startDate;
        String endDate;
        LocalDate start;
        LocalDate end;
    }

    private static final class DishDayRollup {
        BigDecimal normalPortions = BigDecimal.ZERO;
        BigDecimal discountPortions = BigDecimal.ZERO;
        BigDecimal memberPortions = BigDecimal.ZERO;
        BigDecimal complimentaryPortions = BigDecimal.ZERO;
        BigDecimal employeeMealPortions = BigDecimal.ZERO;
        BigDecimal salesAmount = BigDecimal.ZERO;

        static DishDayRollup fromRows(List<GbDepFoodSalesEntity> rows) {
            DishDayRollup r = new DishDayRollup();
            if (rows == null) {
                return r;
            }
            for (GbDepFoodSalesEntity row : rows) {
                if (row == null) {
                    continue;
                }
                r.normalPortions = r.normalPortions.add(GbDepFoodSalesMetricsSupport.normalSaleQty(row));
                r.discountPortions = r.discountPortions.add(GbDepFoodSalesMetricsSupport.discountSaleQty(row));
                r.memberPortions = r.memberPortions.add(GbDepFoodSalesMetricsSupport.memberSaleQty(row));
                r.complimentaryPortions = r.complimentaryPortions.add(GbDepFoodSalesMetricsSupport.complimentaryQty(row));
                r.employeeMealPortions = r.employeeMealPortions.add(GbDepFoodSalesMetricsSupport.employeeMealQty(row));
                r.salesAmount = r.salesAmount.add(GbDepFoodSalesMetricsSupport.operationalActualRevenue(row));
            }
            return r;
        }

        void merge(DishDayRollup other) {
            if (other == null) {
                return;
            }
            normalPortions = normalPortions.add(other.normalPortions);
            discountPortions = discountPortions.add(other.discountPortions);
            memberPortions = memberPortions.add(other.memberPortions);
            complimentaryPortions = complimentaryPortions.add(other.complimentaryPortions);
            employeeMealPortions = employeeMealPortions.add(other.employeeMealPortions);
            salesAmount = salesAmount.add(other.salesAmount);
        }

        BigDecimal operatingSalesPortions() {
            return normalPortions.add(discountPortions).add(memberPortions);
        }

        BigDecimal totalConsumptionPortions() {
            return operatingSalesPortions().add(complimentaryPortions).add(employeeMealPortions);
        }

        Map<String, Object> toDailyRowMap(String date) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", date);
            m.put("normalPortions", qtyPlain(normalPortions));
            m.put("discountPortions", qtyPlain(discountPortions));
            m.put("memberPortions", qtyPlain(memberPortions));
            m.put("complimentaryPortions", qtyPlain(complimentaryPortions));
            m.put("employeeMealPortions", qtyPlain(employeeMealPortions));
            m.put("operatingSalesPortions", qtyPlain(operatingSalesPortions()));
            m.put("totalConsumptionPortions", qtyPlain(totalConsumptionPortions()));
            m.put("salesAmount", moneyPlain(salesAmount));
            return m;
        }

        Map<String, Object> toSummaryMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("operatingSalesPortions", qtyPlain(operatingSalesPortions()));
            m.put("complimentaryPortions", qtyPlain(complimentaryPortions));
            m.put("employeeMealPortions", qtyPlain(employeeMealPortions));
            m.put("totalConsumptionPortions", qtyPlain(totalConsumptionPortions()));
            m.put("salesAmount", moneyPlain(salesAmount));
            m.put("normalPortions", qtyPlain(normalPortions));
            m.put("discountPortions", qtyPlain(discountPortions));
            m.put("memberPortions", qtyPlain(memberPortions));
            return m;
        }

        /** 子部门菜品行：经营=type1/2/3 份数，赠送=type4，员工餐=type5，销售额=经营实际成交金额。 */
        Map<String, Object> toDishItemMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("normalPortions", qtyPlain(normalPortions));
            m.put("discountPortions", qtyPlain(discountPortions));
            m.put("memberPortions", qtyPlain(memberPortions));
            m.put("operatingSalesPortions", qtyPlain(operatingSalesPortions()));
            m.put("complimentaryPortions", qtyPlain(complimentaryPortions));
            m.put("employeeMealPortions", qtyPlain(employeeMealPortions));
            m.put("totalConsumptionPortions", qtyPlain(totalConsumptionPortions()));
            m.put("salesAmount", moneyPlain(salesAmount));
            m.put("hasSales", totalConsumptionPortions().compareTo(BigDecimal.ZERO) > 0);
            return m;
        }
    }

    private static String qtyPlain(BigDecimal v) {
        if (v == null) {
            return "0";
        }
        return v.stripTrailingZeros().toPlainString();
    }

    private static String moneyPlain(BigDecimal v) {
        if (v == null) {
            return "0.00";
        }
        return v.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static String moneyPlain(Object raw) {
        if (raw == null) {
            return "0.00";
        }
        if (raw instanceof BigDecimal) {
            return moneyPlain((BigDecimal) raw);
        }
        try {
            return moneyPlain(new BigDecimal(raw.toString().trim()));
        } catch (NumberFormatException e) {
            return "0.00";
        }
    }

    private Map<String, Object> buildDailyRevenueSyncPayload(
            boolean synced, Integer depFatherId, String fullDate) {
        Map<String, Object> sync = new LinkedHashMap<>();
        sync.put("synced", synced);
        if (synced) {
            sync.put("dineInRevenueFromDishes", sumFoodSalesSubtotalByFatherAndDay(depFatherId, fullDate));
        }
        return sync;
    }

    private void syncDineInRevenueFromDishes(
            Integer depFatherId, Integer distributerId, String fullDate, Date recordDate) {
        for (Integer depId : resolveOutletIdsForDineInDailyRevenue(depFatherId)) {
            BigDecimal dineIn = sumFoodSalesSubtotalByDepAndDay(depId, fullDate);
            gbAiDailyRevenueService.upsertDineInRevenueOnly(
                    depId.longValue(), distributerId.longValue(), recordDate, dineIn);
        }
    }

    private static Map<String, Object> buildSingleLineFoodSalesPayload(GbDepFoodSalesEntity s) {
        if (s == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("gbDepFoodSalesId", s.getGbDepFoodSalesId());
        m.put("depId", s.getGbDfsDepId());
        m.put("depFatherId", s.getGbDfsDepFatherId());
        m.put("foodId", s.getGbDfsFoodId());
        m.put("recordDate", s.getGbDfsFullDate());
        m.put("type", GbDepFoodSalesMetricsSupport.resolveType(s));
        m.put("quantity", parseAmount(s.getGbDfsAmount()));
        m.put("originalUnitPrice", s.getGbDfsOriginalUnitPrice());
        m.put("actualUnitPrice", s.getGbDfsActualUnitPrice());
        m.put("discountRate", s.getGbDfsDiscountRate());
        m.put("subtotal", s.getGbDfsSubtotal());
        return m;
    }

    private static void validateFoodSalesType(Integer type) {
        Integer t = GbConstants.FoodSalesType.normalize(type);
        if (t < GbConstants.FoodSalesType.NORMAL_SALE || t > GbConstants.FoodSalesType.EMPLOYEE_MEAL) {
            throw new IllegalArgumentException("type 须为 1～5（正常/折扣/会员/赠送/员工餐）");
        }
    }

    private static String resolveQuantityMode(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "ADD";
        }
        String mode = raw.trim().toUpperCase(Locale.ROOT);
        if ("ADD".equals(mode) || "SET".equals(mode)) {
            return mode;
        }
        throw new IllegalArgumentException("quantityMode 仅支持 ADD 或 SET");
    }

    private SingleLineContext resolveSingleLineContext(GbDepFoodDishSalesLineRequest req, boolean requireQuantity) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (req.getDepFatherId() == null) {
            throw new IllegalArgumentException("depFatherId 不能为空");
        }
        if (req.getDistributerId() == null) {
            throw new IllegalArgumentException("distributerId 不能为空");
        }
        if (req.getFoodId() == null) {
            throw new IllegalArgumentException("foodId 不能为空");
        }
        if (req.getRecordDate() == null || req.getRecordDate().trim().isEmpty()) {
            throw new IllegalArgumentException("recordDate 不能为空（yyyy-MM-dd）");
        }
        if (requireQuantity) {
            if (req.getQuantity() == null) {
                throw new IllegalArgumentException("quantity 不能为空");
            }
            if (req.getQuantity().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("quantity 不能为负");
            }
        }
        String fullDate = req.getRecordDate().trim();
        Date recordDate = GbDateTimeUtils.parseDay(fullDate);
        if (recordDate == null) {
            throw new IllegalArgumentException("recordDate 格式须为 yyyy-MM-dd");
        }

        GbDepartmentEntity department = departmentService.getById(req.getDepFatherId());
        if (department == null) {
            throw new IllegalArgumentException("部门不存在");
        }

        Integer lineDepId = req.getDepId() != null ? req.getDepId() : req.getSubDepId();
        if (lineDepId == null) {
            throw new IllegalArgumentException("depId 或 subDepId 不能为空");
        }
        GbDepartmentEntity lineDep = departmentService.getById(lineDepId);
        if (lineDep == null) {
            throw new IllegalArgumentException("子部门不存在: " + lineDepId);
        }
        if (!Objects.equals(lineDep.getGbDepartmentFatherId(), req.getDepFatherId())
                && !Objects.equals(lineDepId, req.getDepFatherId())) {
            throw new IllegalArgumentException("depId 与 depFatherId 不是父子关系");
        }

        boolean relaxDistributerFoodMasterFilter =
                Objects.equals(department.getGbDepartmentDisId(), req.getDistributerId())
                        || Objects.equals(lineDep.getGbDepartmentDisId(), req.getDistributerId());

        Map<String, Object> depMap = new HashMap<>();
        depMap.put("depId", lineDepId);
        List<GbDepFoodEntity> depFoods = gbDepFoodService.queryDepAllFood(depMap);
        dailyRevenueExcelService.attachDistributerFood(depFoods);
        Map<String, GbDepFoodEntity> depFoodByDepAndFood = buildDepFoodLookupMaps(
                depFoods, req.getDistributerId(), depMap, relaxDistributerFoodMasterFilter);
        GbDepFoodEntity depFood = depFoodByDepAndFood.get(depFoodLookupKey(lineDepId, req.getFoodId()));
        if (depFood == null || depFood.getGbDfDepId() == null) {
            throw new IllegalArgumentException("未匹配部门菜品：depId=" + lineDepId + " foodId=" + req.getFoodId()
                    + "（请确认 gb_dep_food 存在该组合）");
        }

        Integer depFatherIdFromFood = parseIntSafe(depFood.getGbDfDepFatherId());
        if (depFatherIdFromFood == null) {
            depFatherIdFromFood = req.getDepFatherId();
        }
        Integer type = GbConstants.FoodSalesType.normalize(req.getType());

        SingleLineContext ctx = new SingleLineContext();
        ctx.fullDate = fullDate;
        ctx.recordDate = recordDate;
        ctx.lineDepId = lineDepId;
        ctx.depFatherId = req.getDepFatherId();
        ctx.depFatherIdFromFood = depFatherIdFromFood;
        ctx.depFood = depFood;
        ctx.type = type;
        return ctx;
    }

    private static final class SingleLineContext {
        String fullDate;
        Date recordDate;
        Integer lineDepId;
        Integer depFatherId;
        Integer depFatherIdFromFood;
        GbDepFoodEntity depFood;
        Integer type;
    }

    private PriorFoodSalesDeletion replaceScopeFoodSalesRecordsForSubmit(
            Integer depFatherId, Integer subDepId, Integer distributerId, String fullDate) {
        PriorFoodSalesDeletion d = new PriorFoodSalesDeletion();
        LambdaQueryWrapper<GbDepFoodSalesEntity> q = new LambdaQueryWrapper<GbDepFoodSalesEntity>()
                .eq(GbDepFoodSalesEntity::getGbDfsFullDate, fullDate)
                .eq(GbDepFoodSalesEntity::getGbDfsDistributerId, distributerId);
        if (subDepId != null) {
            q.eq(GbDepFoodSalesEntity::getGbDfsDepId, subDepId);
        } else {
            q.eq(GbDepFoodSalesEntity::getGbDfsDepFatherId, depFatherId);
        }
        List<GbDepFoodSalesEntity> salesRows = gbDepFoodSalesService.list(q);
        if (salesRows == null || salesRows.isEmpty()) {
            return d;
        }
        List<Integer> salesIds = new ArrayList<>(salesRows.size());
        for (GbDepFoodSalesEntity s : salesRows) {
            if (s != null && s.getGbDepFoodSalesId() != null) {
                salesIds.add(s.getGbDepFoodSalesId());
            }
        }
        if (salesIds.isEmpty()) {
            return d;
        }
        d.foodSalesRows = salesIds.size();
        d.goodsSalesRows = (int) gbDepFoodGoodsSalesService.count(
                new LambdaQueryWrapper<GbDepFoodGoodsSalesEntity>()
                        .in(GbDepFoodGoodsSalesEntity::getGbDfgsFoodSalesId, salesIds));
        gbDepFoodGoodsSalesService.remove(
                new LambdaQueryWrapper<GbDepFoodGoodsSalesEntity>()
                        .in(GbDepFoodGoodsSalesEntity::getGbDfgsFoodSalesId, salesIds));
        gbDepFoodSalesService.removeBatchByIds(salesIds);
        return d;
    }

    private static final class PriorFoodSalesDeletion {
        int foodSalesRows;
        int goodsSalesRows;
    }

    private FoodSalesAggResult applyAggregatedFoodSalesUpserts(
            Integer distributerId,
            AggregatedFoodSalesBatch batch,
            Map<String, GbDepFoodEntity> depFoodByDepAndFood) {
        FoodSalesAggResult r = new FoodSalesAggResult();
        for (AggregatedFoodSalesBatch.Entry e : batch.entries()) {
            String fullDate = GbDateTimeUtils.formatDay(e.recordDate);
            if (e.qty == null || e.qty.compareTo(BigDecimal.ZERO) <= 0) {
                if (gbDepFoodSalesWriteSupport.deleteFoodSalesLineIfExists(e.depId, e.foodId, fullDate, e.type)) {
                    r.deleted++;
                    if (GbConstants.FoodSalesType.isOperationalSales(e.type)) {
                        r.syncDates.add(fullDate);
                    }
                }
                continue;
            }
            r.syncDates.add(fullDate);
            GbDepFoodEntity depFoodRow = depFoodByDepAndFood.get(depFoodLookupKey(e.depId, e.foodId));
            String listPrice = e.listPriceStr != null ? e.listPriceStr
                    : (depFoodRow == null ? null : depFoodRow.getGbDfFoodPrice());
            GbDepFoodSalesWriteSupport.UpsertResult ur = upsertViaWriteSupport(
                    e.depId, e.depFatherId, e.foodId, distributerId, e.recordDate, fullDate,
                    e.qty, e.type, listPrice, e.actualUnitPrice);
            if (ur.inserted) {
                r.inserted++;
            } else {
                r.updated++;
            }
            r.goodsRows += ur.goodsRows;
        }
        return r;
    }

    private GbDepFoodSalesWriteSupport.UpsertResult upsertViaWriteSupport(
            Integer depId, Integer depFatherId, Integer foodId, Integer distributerId,
            Date recordDate, String fullDate, BigDecimal qty, Integer type, String listPriceStr,
            BigDecimal actualUnitPrice) {
        GbDepFoodSalesWriteSupport.FoodSalesWriteCommand cmd = new GbDepFoodSalesWriteSupport.FoodSalesWriteCommand();
        cmd.depId = depId;
        cmd.depFatherId = depFatherId;
        cmd.foodId = foodId;
        cmd.distributerId = distributerId;
        cmd.fullDate = fullDate;
        cmd.recordDate = recordDate;
        cmd.qty = qty;
        cmd.type = type;
        cmd.listPriceStr = listPriceStr;
        cmd.actualUnitPrice = actualUnitPrice;
        return gbDepFoodSalesWriteSupport.upsertFoodSalesLine(cmd);
    }

    private static final class FoodSalesAggResult {
        private int inserted;
        private int updated;
        private int deleted;
        private int goodsRows;
        private final Set<String> syncDates = new TreeSet<>();
    }

    private static final class AggregatedFoodSalesBatch {
        private final Map<String, Entry> byKey = new LinkedHashMap<>();

        void merge(Date recordDate, Integer depId, Integer depFatherId, Integer foodId, Integer type,
                BigDecimal qty, String listPriceStr, BigDecimal actualUnitPrice) {
            Integer resolvedType = GbConstants.FoodSalesType.normalize(type);
            String key = GbDateTimeUtils.formatDay(recordDate) + "|" + depId + "|" + foodId + "|" + resolvedType;
            Entry e = byKey.computeIfAbsent(key, k -> new Entry());
            e.recordDate = recordDate;
            e.depId = depId;
            e.depFatherId = depFatherId;
            e.foodId = foodId;
            e.type = resolvedType;
            e.qty = (e.qty == null ? BigDecimal.ZERO : e.qty).add(qty);
            if (listPriceStr != null) {
                e.listPriceStr = listPriceStr;
            }
            if (actualUnitPrice != null) {
                e.actualUnitPrice = actualUnitPrice;
            }
        }

        boolean isEmpty() {
            return byKey.isEmpty();
        }

        Iterable<Entry> entries() {
            return byKey.values();
        }

        static final class Entry {
            Date recordDate;
            Integer depId;
            Integer depFatherId;
            Integer foodId;
            Integer type;
            BigDecimal qty;
            String listPriceStr;
            BigDecimal actualUnitPrice;
        }
    }

    private BigDecimal sumFoodSalesSubtotalByFatherAndDay(Integer depFatherId, String fullDate) {
        List<GbDepFoodSalesEntity> rows = gbDepFoodSalesService.list(
                new LambdaQueryWrapper<GbDepFoodSalesEntity>()
                        .eq(GbDepFoodSalesEntity::getGbDfsDepFatherId, depFatherId)
                        .eq(GbDepFoodSalesEntity::getGbDfsFullDate, fullDate));
        return GbDepFoodSalesMetricsSupport.sumOperationalActualRevenue(rows);
    }

    private List<Integer> resolveOutletIdsForDineInDailyRevenue(Integer depFatherId) {
        if (depFatherId == null) {
            return Collections.emptyList();
        }
        List<GbDepartmentEntity> subs = departmentService.querySubDepartments(depFatherId);
        if (subs != null && !subs.isEmpty()) {
            List<Integer> ids = new ArrayList<>();
            for (GbDepartmentEntity s : subs) {
                if (s.getGbDepartmentId() != null) {
                    ids.add(s.getGbDepartmentId());
                }
            }
            Collections.sort(ids);
            return ids;
        }
        return Collections.singletonList(depFatherId);
    }

    private BigDecimal sumFoodSalesSubtotalByDepAndDay(Integer depId, String fullDate) {
        List<GbDepFoodSalesEntity> rows = gbDepFoodSalesService.list(
                new LambdaQueryWrapper<GbDepFoodSalesEntity>()
                        .eq(GbDepFoodSalesEntity::getGbDfsDepId, depId)
                        .eq(GbDepFoodSalesEntity::getGbDfsFullDate, fullDate));
        return GbDepFoodSalesMetricsSupport.sumOperationalActualRevenue(rows);
    }

    private Map<String, GbDepFoodEntity> buildDepFoodLookupMaps(List<GbDepFoodEntity> depFoods, Integer distributerId,
            Map<String, Object> depMap) {
        return buildDepFoodLookupMaps(depFoods, distributerId, depMap, false);
    }

    private Map<String, GbDepFoodEntity> buildDepFoodLookupMaps(List<GbDepFoodEntity> depFoods, Integer distributerId,
            Map<String, Object> depMap, boolean relaxDistributerFoodMasterFilter) {
        Map<String, GbDepFoodEntity> depFoodByDepAndFood = new HashMap<>();
        Integer apiDepFatherId = null;
        if (depMap != null && depMap.get("depFatherId") instanceof Integer) {
            apiDepFatherId = (Integer) depMap.get("depFatherId");
        }
        for (GbDepFoodEntity f : depFoods) {
            if (f.getGbDfFoodId() == null) {
                continue;
            }
            GbDistributerFoodEntity disFood = f.getGbDistributerFoodEntity();
            if (!relaxDistributerFoodMasterFilter && disFood != null && disFood.getGbDfDistributerId() != null
                    && !disFood.getGbDfDistributerId().equals(distributerId)) {
                continue;
            }
            Integer depId = f.getGbDfDepId();
            Integer depFatherId = parseIntSafe(f.getGbDfDepFatherId());
            if (depId == null) {
                continue;
            }
            depFoodByDepAndFood.putIfAbsent(depFoodLookupKey(depId, f.getGbDfFoodId()), f);
            if (f.getGbDfFoodFatherId() != null) {
                depFoodByDepAndFood.putIfAbsent(depFoodLookupKey(depId, f.getGbDfFoodFatherId()), f);
            }
            if (depFatherId != null) {
                depFoodByDepAndFood.putIfAbsent(depFoodLookupKey(depFatherId, f.getGbDfFoodId()), f);
                if (f.getGbDfFoodFatherId() != null) {
                    depFoodByDepAndFood.putIfAbsent(depFoodLookupKey(depFatherId, f.getGbDfFoodFatherId()), f);
                }
            }
            if (apiDepFatherId != null) {
                depFoodByDepAndFood.putIfAbsent(depFoodLookupKey(apiDepFatherId, f.getGbDfFoodId()), f);
                if (f.getGbDfFoodFatherId() != null) {
                    depFoodByDepAndFood.putIfAbsent(depFoodLookupKey(apiDepFatherId, f.getGbDfFoodFatherId()), f);
                }
            }
        }
        return depFoodByDepAndFood;
    }

    private static String depFoodLookupKey(Integer depFatherId, Integer foodRefId) {
        return depFatherId + "#" + foodRefId;
    }

    private static BigDecimal parseAmount(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
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

    private Map<Integer, String> loadUnitPriceByFoodRefIdForDep(Integer depId) {
        if (depId == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> m = new HashMap<>();
        m.put("depId", depId);
        List<GbDepFoodEntity> list = gbDepFoodService.queryDepAllFood(m);
        if (list == null || list.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Integer, String> out = new HashMap<>();
        for (GbDepFoodEntity f : list) {
            if (f.getGbDfFoodId() == null) {
                continue;
            }
            String p = f.getGbDfFoodPrice();
            out.putIfAbsent(f.getGbDfFoodId(), p);
            if (f.getGbDfFoodFatherId() != null) {
                out.putIfAbsent(f.getGbDfFoodFatherId(), p);
            }
        }
        return out;
    }
}
