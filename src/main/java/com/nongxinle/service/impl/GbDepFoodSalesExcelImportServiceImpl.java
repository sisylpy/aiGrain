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
                sales.setGbDfsSubtotal(sales.getGbDfsAmount());

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
        List<Map.Entry<Date, Map<Integer, BigDecimal>>> rows = dailyRevenueExcelService.parseFoodSalesExcel(file);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Excel文件中没有有效的菜品销售数据");
        }
        Map<String, Object> stats = importFoodSales(departmentId, distributerId, department, allowedFoodIds, rows);
        Map<String, Object> out = new HashMap<>(stats);
        out.put("rows", rows.size());
        return out;
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
}
