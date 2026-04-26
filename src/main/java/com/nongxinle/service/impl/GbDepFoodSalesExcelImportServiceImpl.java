package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nongxinle.entity.GbDepartmentEntity;
import com.nongxinle.entity.GbDepFoodGoodsSalesEntity;
import com.nongxinle.entity.GbDepFoodSalesEntity;
import com.nongxinle.entity.GbDistributerFoodGoodsEntity;
import com.nongxinle.entity.GbDepFoodEntity;
import com.nongxinle.entity.GbDistributerFoodEntity;
import com.nongxinle.service.GbAiDailyRevenueExcelService;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GbDepFoodSalesExcelImportServiceImpl implements GbDepFoodSalesExcelImportService {

    private final GbDepFoodSalesService gbDepFoodSalesService;
    private final GbDepFoodGoodsSalesService gbDepFoodGoodsSalesService;
    private final GbDistributerFoodGoodsService gbDistributerFoodGoodsService;
    private final GbDepartmentService departmentService;
    private final GbDepFoodService gbDepFoodService;
    private final GbAiDailyRevenueExcelService dailyRevenueExcelService;

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
        List<GbAiDailyRevenueExcelService.FoodSalesExcelCell> cells = dailyRevenueExcelService.parseFoodSalesExcel(file);
        if (cells.isEmpty()) {
            throw new IllegalArgumentException("Excel文件中没有有效的菜品销售数据");
        }

        int inserted = 0;
        int updated = 0;
        int goodsRows = 0;
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
                goodsRows++;
            }
        }

        Map<String, Object> out = new HashMap<>();
        out.put("inserted", inserted);
        out.put("updated", updated);
        out.put("goodsRows", goodsRows);
        out.put("skippedUnknownFood", skippedUnknownFood);
        out.put("warnings", warnings);
        out.put("rows", cells.size());
        return out;
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
