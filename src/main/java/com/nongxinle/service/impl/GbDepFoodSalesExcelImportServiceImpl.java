package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nongxinle.dto.GbDepFoodDailySalesSubmitRequest;
import com.nongxinle.entity.GbDepartmentEntity;
import com.nongxinle.entity.GbAiDailyRevenueEntity;
import com.nongxinle.entity.GbDepFoodGoodsSalesEntity;
import com.nongxinle.entity.GbDepFoodSalesEntity;
import com.nongxinle.entity.GbDistributerFoodGoodsEntity;
import com.nongxinle.entity.GbDepFoodEntity;
import com.nongxinle.entity.GbDistributerFoodEntity;
import com.nongxinle.service.GbAiDailyRevenueExcelService;
import com.nongxinle.service.GbAiDailyRevenueService;
import com.nongxinle.service.GbDepartmentService;
import com.nongxinle.service.GbDepFoodGoodsSalesService;
import com.nongxinle.service.GbDepFoodSalesExcelImportService;
import com.nongxinle.service.GbDepFoodSalesService;
import com.nongxinle.service.GbDepFoodService;
import com.nongxinle.service.GbDistributerFoodGoodsService;
import com.nongxinle.utils.GbDateTimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
public class GbDepFoodSalesExcelImportServiceImpl implements GbDepFoodSalesExcelImportService {

    private final GbDepFoodSalesService gbDepFoodSalesService;
    private final GbDepFoodGoodsSalesService gbDepFoodGoodsSalesService;
    private final GbDistributerFoodGoodsService gbDistributerFoodGoodsService;
    private final GbDepartmentService departmentService;
    private final GbDepFoodService gbDepFoodService;
    private final GbAiDailyRevenueExcelService dailyRevenueExcelService;
    private final GbAiDailyRevenueService gbAiDailyRevenueService;

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
            String month = GbDateTimeUtils.formatYearMonth(recordDate);
            String year = GbDateTimeUtils.formatYear(recordDate);
            int weekday = GbDateTimeUtils.weekdayForAiDailyRevenue(recordDate);

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

                GbDepFoodSalesEntity sales = findExisting(departmentId, foodId, fullDate);
                boolean isNew = sales == null;
                if (isNew) {
                    sales = new GbDepFoodSalesEntity();
                    sales.setGbDfsDepId(departmentId);
                    sales.setGbDfsDepFatherId(depFatherId);
                    sales.setGbDfsFoodId(foodId);
                    sales.setGbDfsDistributerId(distributerId);
                    sales.setGbDfsFullDate(fullDate);
                    sales.setGbDfsMonth(month);
                    sales.setGbDfsYear(year);
                    sales.setGbDfsRevenueWeekday(weekday);
                    sales.setGbDfsRevenueHoliday("");
                }

                sales.setGbDfsAmount(qty.stripTrailingZeros().toPlainString());
                sales.setGbDfsSubtotal(computeSubtotalPlain(qty, unitPriceByFoodRefId.get(foodId)));

                if (isNew) {
                    gbDepFoodSalesService.save(sales);
                    inserted++;
                } else {
                    gbDepFoodSalesService.updateById(sales);
                    updated++;
                    gbDepFoodGoodsSalesService.remove(
                            new LambdaQueryWrapper<GbDepFoodGoodsSalesEntity>()
                                    .eq(GbDepFoodGoodsSalesEntity::getGbDfgsFoodSalesId, sales.getGbDepFoodSalesId()));
                }

                List<GbDistributerFoodGoodsEntity> recipe = gbDistributerFoodGoodsService.queryFoodGoodsByFoodId(foodId);
                for (GbDistributerFoodGoodsEntity line : recipe) {
                    if (line.getGbDfgStatus() != null && line.getGbDfgStatus() == 0) {
                        continue;
                    }
                    BigDecimal per = parseAmount(line.getGbDfgGoodsAmount());
                    BigDecimal consumed = per.multiply(qty).setScale(6, RoundingMode.HALF_UP);

                    GbDepFoodGoodsSalesEntity ggs = new GbDepFoodGoodsSalesEntity();
                    ggs.setGbDfgsDepId(departmentId);
                    ggs.setGbDfgsDepFatherId(depFatherId);
                    ggs.setGbDfgsFoodSalesId(sales.getGbDepFoodSalesId());
                    ggs.setGbDfgsFoodGoodsId(line.getGbDistributerFoodGoodsId());
                    ggs.setGbDfgsDisGoodsId(line.getGbDfgDisGoodsId());
                    ggs.setGbDfgsGoodsAmount(consumed.stripTrailingZeros().toPlainString());
                    ggs.setGbDfgsMonth(month);
                    ggs.setGbDfgsFullDate(fullDate);
                    ggs.setGbDfgsRevenueWeekday(weekday);
                    ggs.setGbDfgsRevenueHoliday("");

                    gbDepFoodGoodsSalesService.save(ggs);
                    goodsRows++;
                }
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
        Map<String, GbDepFoodEntity> depFoodByDepAndFood = new HashMap<>();
        Map<String, GbDepFoodEntity> depFoodByFatherAndFood = new HashMap<>();
        for (GbDepFoodEntity f : depFoods) {
            if (f.getGbDfFoodId() == null) {
                continue;
            }
            GbDistributerFoodEntity disFood = f.getGbDistributerFoodEntity();
            if (disFood != null && disFood.getGbDfDistributerId() != null
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
                depFoodByFatherAndFood.putIfAbsent(depFoodLookupKey(depFatherId, f.getGbDfFoodId()), f);
                if (f.getGbDfFoodFatherId() != null) {
                    depFoodByFatherAndFood.putIfAbsent(depFoodLookupKey(depFatherId, f.getGbDfFoodFatherId()), f);
                }
            }
        }
        List<GbAiDailyRevenueExcelService.FoodSalesExcelCell> cells = dailyRevenueExcelService.parseFoodSalesExcel(bytes, sheetIndex);
        if (cells.isEmpty()) {
            if (!allowEmptyFoodSheet) {
                throw new IllegalArgumentException("Excel文件中没有有效的菜品销售数据");
            }
            Map<String, Object> empty = new HashMap<>();
            empty.put("inserted", 0);
            empty.put("updated", 0);
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

        Map<String, BigDecimal> qtyByResolvedKey = new HashMap<>();
        Map<String, Date> dateByResolvedKey = new HashMap<>();
        Map<String, Integer> depIdByResolvedKey = new HashMap<>();
        Map<String, Integer> depFatherIdByResolvedKey = new HashMap<>();
        Map<String, Integer> foodIdByResolvedKey = new HashMap<>();

        for (GbAiDailyRevenueExcelService.FoodSalesExcelCell cell : cells) {
            if (cell == null || cell.getRecordDate() == null || cell.getFoodRefId() == null
                    || cell.getAmount() == null || cell.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            Integer depIdFromCell = cell.getDepId();
            GbDepFoodEntity depFood = null;
            if (depIdFromCell != null) {
                depFood = depFoodByDepAndFood.get(depFoodLookupKey(depIdFromCell, cell.getFoodRefId()));
            } else {
                // 兼容旧模板（无部门列）：回退到接口传入的父级部门ID + 菜品ID 匹配
                depFood = depFoodByFatherAndFood.get(depFoodLookupKey(departmentId, cell.getFoodRefId()));
            }
            if (depFood == null || depFood.getGbDfDepId() == null || depFood.getGbDfFoodId() == null) {
                skippedUnknownFood++;
                warnings.add("跳过未匹配部门菜品：部门ID=" + depIdFromCell + " 菜品ID=" + cell.getFoodRefId()
                        + " 日期 " + GbDateTimeUtils.formatDay(cell.getRecordDate()));
                continue;
            }
            String dayKey = GbDateTimeUtils.formatDay(cell.getRecordDate());
            String resolvedKey = dayKey + "|" + depFood.getGbDfDepId() + "|" + depFood.getGbDfFoodId();
            qtyByResolvedKey.merge(resolvedKey, cell.getAmount(), BigDecimal::add);
            dateByResolvedKey.putIfAbsent(resolvedKey, cell.getRecordDate());
            depIdByResolvedKey.putIfAbsent(resolvedKey, depFood.getGbDfDepId());
            depFatherIdByResolvedKey.putIfAbsent(resolvedKey, departmentId);
            foodIdByResolvedKey.putIfAbsent(resolvedKey, depFood.getGbDfFoodId());
        }

        FoodSalesAggResult agg = applyAggregatedFoodSalesUpserts(
                distributerId,
                qtyByResolvedKey,
                dateByResolvedKey,
                depIdByResolvedKey,
                depFatherIdByResolvedKey,
                foodIdByResolvedKey,
                depFoodByDepAndFood);

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
        out.put("goodsRows", agg.goodsRows);
        out.put("skippedUnknownFood", skippedUnknownFood);
        out.put("warnings", warnings);
        out.put("rows", cells.size());
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
        /**
         * 父部门或当前子部门的 {@link GbDepartmentEntity#getGbDepartmentDisId} 已与请求 {@code distributerId} 对齐时，
         * 不再按 {@link GbDistributerFoodEntity#getGbDfDistributerId} 剔除菜品（避免出现：门店/部门归属批发商 2、主档仍为 1 导致无法录入）。
         */
        boolean relaxDistributerFoodMasterFilter =
                Objects.equals(department.getGbDepartmentDisId(), req.getDistributerId())
                        || (subDepValidated != null
                                && Objects.equals(subDepValidated.getGbDepartmentDisId(), req.getDistributerId()));

        Map<String, Object> depMap = new HashMap<>();
        if (req.getSubDepId() != null) {
            // 已校验子部门隶属于 depFatherId；不按 gb_df_dep_father_id 收紧，以免历史数据字段为空/与 gb_department 不一致时加载不到菜品
            depMap.put("depId", req.getSubDepId());
        } else {
            depMap.put("depFatherId", req.getDepFatherId());
        }
        List<GbDepFoodEntity> depFoods = gbDepFoodService.queryDepAllFood(depMap);
        dailyRevenueExcelService.attachDistributerFood(depFoods);
        Map<String, GbDepFoodEntity> depFoodByDepAndFood = new HashMap<>();
        Map<String, GbDepFoodEntity> depFoodByFatherAndFood = new HashMap<>();
        for (GbDepFoodEntity f : depFoods) {
            if (f.getGbDfFoodId() == null) {
                continue;
            }
            GbDistributerFoodEntity disFood = f.getGbDistributerFoodEntity();
            if (!relaxDistributerFoodMasterFilter && disFood != null && disFood.getGbDfDistributerId() != null
                    && !disFood.getGbDfDistributerId().equals(req.getDistributerId())) {
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
                depFoodByFatherAndFood.putIfAbsent(depFoodLookupKey(depFatherId, f.getGbDfFoodId()), f);
                if (f.getGbDfFoodFatherId() != null) {
                    depFoodByFatherAndFood.putIfAbsent(depFoodLookupKey(depFatherId, f.getGbDfFoodFatherId()), f);
                }
            }
        }

        Map<String, BigDecimal> qtyByResolvedKey = new HashMap<>();
        Map<String, Date> dateByResolvedKey = new HashMap<>();
        Map<String, Integer> depIdByResolvedKey = new HashMap<>();
        Map<String, Integer> depFatherIdByResolvedKey = new HashMap<>();
        Map<String, Integer> foodIdByResolvedKey = new HashMap<>();
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
                String resolvedKey = fullDate + "|" + depFood.getGbDfDepId() + "|" + depFood.getGbDfFoodId();
                qtyByResolvedKey.merge(resolvedKey, line.getQuantity(), BigDecimal::add);
                dateByResolvedKey.putIfAbsent(resolvedKey, recordDate);
                depIdByResolvedKey.putIfAbsent(resolvedKey, depFood.getGbDfDepId());
                Integer fatherFromFood = parseIntSafe(depFood.getGbDfDepFatherId());
                depFatherIdByResolvedKey.putIfAbsent(resolvedKey,
                        fatherFromFood != null ? fatherFromFood : req.getDepFatherId());
                foodIdByResolvedKey.putIfAbsent(resolvedKey, depFood.getGbDfFoodId());
            }
        }

        Map<String, Object> foodStats = new HashMap<>();
        if (!qtyByResolvedKey.isEmpty()) {
            FoodSalesAggResult agg = applyAggregatedFoodSalesUpserts(
                    req.getDistributerId(),
                    qtyByResolvedKey,
                    dateByResolvedKey,
                    depIdByResolvedKey,
                    depFatherIdByResolvedKey,
                    foodIdByResolvedKey,
                    depFoodByDepAndFood);
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
            String recordDate, Integer subDepId) {
        if (depFatherId == null) {
            throw new IllegalArgumentException("depFatherId 不能为空");
        }
        if (distributerId == null) {
            throw new IllegalArgumentException("distributerId 不能为空");
        }
        if (subDepId != null) {
            GbDepartmentEntity subDep = departmentService.getById(subDepId);
            if (subDep == null) {
                throw new IllegalArgumentException("子部门不存在: " + subDepId);
            }
            if (!Objects.equals(subDep.getGbDepartmentFatherId(), depFatherId)) {
                throw new IllegalArgumentException("subDepId 与 depFatherId 不是父子关系");
            }
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
        if (subDepId != null) {
            salesQ.eq(GbDepFoodSalesEntity::getGbDfsDepId, subDepId);
        }
        salesQ.orderByAsc(GbDepFoodSalesEntity::getGbDfsDepId)
                .orderByAsc(GbDepFoodSalesEntity::getGbDfsFoodId);
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
            Long mergeDept = subDepId != null ? subDepId.longValue() : depFatherId.longValue();
            revRow = GbAiDailyRevenueServiceImpl.mergeRevenueRowsForSameDay(drList, mergeDept);
        }

        GbDepFoodDailySalesSubmitRequest dto = new GbDepFoodDailySalesSubmitRequest();
        dto.setRecordDate(fullDate);
        dto.setDepFatherId(depFatherId);
        dto.setSubDepId(subDepId);
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

        BigDecimal dineInFromDishes = subDepId != null
                ? sumFoodSalesSubtotalByDepAndDay(subDepId, fullDate)
                : sumFoodSalesSubtotalByFatherAndDay(depFatherId, fullDate);

        Map<String, Object> out = new HashMap<>();
        out.put("submitShape", dto);
        out.put("lineCount", lines.size());
        Map<String, Object> daily = buildDailyRevenueDayPayload(revRow, fullDate, depFatherId, distributerId, dineInFromDishes);
        if (subDepId != null) {
            daily.put("subDepId", subDepId);
        }
        out.put("dailyRevenue", daily);
        return out;
    }

    /**
     * 当日 {@code gb_ai_daily_revenue} 一行（含总/净营业额生成列、堂食订单与人数、外卖与平台抽成），与 {@code submitShape} 指标一致并便于小程序直显。
     */
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

    /**
     * 与 {@link #submitDailyFoodSalesAndRevenue} 的编辑范围对齐：删掉当日、{@code gb_dfs_distributer_id} 下已有整菜与配料销量，再由本次 payload 重写（末次写入为准）。
     * <ul>
     *   <li>{@code subDepId != null}：仅该子部门的销量行。</li>
     *   <li>否则：{@code gb_dfs_dep_father_id = depFatherId} 下该日全部子部门/Etc. 的销量行。</li>
     * </ul>
     */
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
            Map<String, BigDecimal> qtyByResolvedKey,
            Map<String, Date> dateByResolvedKey,
            Map<String, Integer> depIdByResolvedKey,
            Map<String, Integer> depFatherIdByResolvedKey,
            Map<String, Integer> foodIdByResolvedKey,
            Map<String, GbDepFoodEntity> depFoodByDepAndFood) {
        FoodSalesAggResult r = new FoodSalesAggResult();
        for (Map.Entry<String, BigDecimal> e : qtyByResolvedKey.entrySet()) {
            String key = e.getKey();
            BigDecimal qty = e.getValue();
            Date recordDate = dateByResolvedKey.get(key);
            Integer depId = depIdByResolvedKey.get(key);
            Integer depFatherId = depFatherIdByResolvedKey.get(key);
            Integer foodId = foodIdByResolvedKey.get(key);
            if (recordDate == null || depId == null || foodId == null || qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            String fullDate = GbDateTimeUtils.formatDay(recordDate);
            r.syncDates.add(fullDate);
            String month = GbDateTimeUtils.formatYearMonth(recordDate);
            String year = GbDateTimeUtils.formatYear(recordDate);
            int weekday = GbDateTimeUtils.weekdayForAiDailyRevenue(recordDate);

            GbDepFoodSalesEntity sales = findExisting(depId, foodId, fullDate);
            boolean isNew = sales == null;
            if (isNew) {
                sales = new GbDepFoodSalesEntity();
                sales.setGbDfsDepId(depId);
                sales.setGbDfsDepFatherId(depFatherId);
                sales.setGbDfsFoodId(foodId);
                sales.setGbDfsDistributerId(distributerId);
                sales.setGbDfsFullDate(fullDate);
                sales.setGbDfsMonth(month);
                sales.setGbDfsYear(year);
                sales.setGbDfsRevenueWeekday(weekday);
                sales.setGbDfsRevenueHoliday("");
            }

            sales.setGbDfsAmount(qty.stripTrailingZeros().toPlainString());
            GbDepFoodEntity depFoodRow = depFoodByDepAndFood.get(depFoodLookupKey(depId, foodId));
            sales.setGbDfsSubtotal(computeSubtotalPlain(qty,
                    depFoodRow == null ? null : depFoodRow.getGbDfFoodPrice()));

            if (isNew) {
                gbDepFoodSalesService.save(sales);
                r.inserted++;
            } else {
                gbDepFoodSalesService.updateById(sales);
                r.updated++;
                gbDepFoodGoodsSalesService.remove(
                        new LambdaQueryWrapper<GbDepFoodGoodsSalesEntity>()
                                .eq(GbDepFoodGoodsSalesEntity::getGbDfgsFoodSalesId, sales.getGbDepFoodSalesId()));
            }

            List<GbDistributerFoodGoodsEntity> recipe = gbDistributerFoodGoodsService.queryFoodGoodsByFoodId(foodId);
            for (GbDistributerFoodGoodsEntity line : recipe) {
                if (line.getGbDfgStatus() != null && line.getGbDfgStatus() == 0) {
                    continue;
                }
                BigDecimal per = parseAmount(line.getGbDfgGoodsAmount());
                BigDecimal consumed = per.multiply(qty).setScale(6, RoundingMode.HALF_UP);

                GbDepFoodGoodsSalesEntity ggs = new GbDepFoodGoodsSalesEntity();
                ggs.setGbDfgsDepId(depId);
                ggs.setGbDfgsDepFatherId(depFatherId);
                ggs.setGbDfgsFoodSalesId(sales.getGbDepFoodSalesId());
                ggs.setGbDfgsFoodGoodsId(line.getGbDistributerFoodGoodsId());
                ggs.setGbDfgsDisGoodsId(line.getGbDfgDisGoodsId());
                ggs.setGbDfgsGoodsAmount(consumed.stripTrailingZeros().toPlainString());
                ggs.setGbDfgsMonth(month);
                ggs.setGbDfgsFullDate(fullDate);
                ggs.setGbDfgsRevenueWeekday(weekday);
                ggs.setGbDfgsRevenueHoliday("");

                gbDepFoodGoodsSalesService.save(ggs);
                r.goodsRows++;
            }
        }
        return r;
    }

    private static final class FoodSalesAggResult {
        private int inserted;
        private int updated;
        private int goodsRows;
        private final Set<String> syncDates = new TreeSet<>();
    }

    private BigDecimal sumFoodSalesSubtotalByFatherAndDay(Integer depFatherId, String fullDate) {
        List<GbDepFoodSalesEntity> rows = gbDepFoodSalesService.list(
                new LambdaQueryWrapper<GbDepFoodSalesEntity>()
                        .eq(GbDepFoodSalesEntity::getGbDfsDepFatherId, depFatherId)
                        .eq(GbDepFoodSalesEntity::getGbDfsFullDate, fullDate));
        if (rows == null || rows.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (GbDepFoodSalesEntity r : rows) {
            sum = sum.add(parseSubtotalBd(r.getGbDfsSubtotal()));
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 堂食营业额（菜品小计）写入的部门：有子部门时只写各子部门，不再写父部门汇总行；无子部门时写父部门本身。
     */
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
        if (rows == null || rows.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (GbDepFoodSalesEntity r : rows) {
            sum = sum.add(parseSubtotalBd(r.getGbDfsSubtotal()));
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal parseSubtotalBd(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private static String depFoodLookupKey(Integer depFatherId, Integer foodRefId) {
        return depFatherId + "#" + foodRefId;
    }

    private GbDepFoodSalesEntity findExisting(Integer depId, Integer foodId, String fullDate) {
        return gbDepFoodSalesService.getOne(
                new LambdaQueryWrapper<GbDepFoodSalesEntity>()
                        .eq(GbDepFoodSalesEntity::getGbDfsDepId, depId)
                        .eq(GbDepFoodSalesEntity::getGbDfsFoodId, foodId)
                        .eq(GbDepFoodSalesEntity::getGbDfsFullDate, fullDate)
                        .last("LIMIT 1"),
                false);
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

    /**
     * 部门菜品标价（{@code gb_dep_food.gb_df_food_price}）按菜品 id 与可选父 id 建索引，与 Excel 行里引用的 id 对齐。
     */
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

    /**
     * 销售小计金额：{@code qty × 单价}；单价为空或无法解析时为 {@code "0"}。
     */
    private static String computeSubtotalPlain(BigDecimal qty, String unitPriceStr) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            return "0";
        }
        if (unitPriceStr == null || unitPriceStr.trim().isEmpty()) {
            return "0";
        }
        try {
            BigDecimal price = new BigDecimal(unitPriceStr.trim());
            return qty.multiply(price).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException e) {
            return "0";
        }
    }
}
