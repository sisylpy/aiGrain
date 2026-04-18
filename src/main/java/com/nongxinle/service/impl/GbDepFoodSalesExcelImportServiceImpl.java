package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nongxinle.entity.GbDepartmentEntity;
import com.nongxinle.entity.GbDepFoodGoodsSalesEntity;
import com.nongxinle.entity.GbDepFoodSalesEntity;
import com.nongxinle.entity.GbDistributerFoodGoodsEntity;
import com.nongxinle.service.GbDepFoodGoodsSalesService;
import com.nongxinle.service.GbDepFoodSalesExcelImportService;
import com.nongxinle.service.GbDepFoodSalesService;
import com.nongxinle.service.GbDistributerFoodGoodsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GbDepFoodSalesExcelImportServiceImpl implements GbDepFoodSalesExcelImportService {

    private final GbDepFoodSalesService gbDepFoodSalesService;
    private final GbDepFoodGoodsSalesService gbDepFoodGoodsSalesService;
    private final GbDistributerFoodGoodsService gbDistributerFoodGoodsService;

    @Override
    @Transactional(rollbackFor = Exception.class)
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

        SimpleDateFormat dayFmt = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat monthFmt = new SimpleDateFormat("yyyy-MM");
        SimpleDateFormat yearFmt = new SimpleDateFormat("yyyy");
        Date now = new Date();

        Integer depFatherId = department.getGbDepartmentFatherId();

        for (Map.Entry<Date, Map<Integer, BigDecimal>> entry : cellQuantities) {
            Date recordDate = entry.getKey();
            if (recordDate == null) {
                continue;
            }
            String fullDate = dayFmt.format(recordDate);
            String month = monthFmt.format(recordDate);
            String year = yearFmt.format(recordDate);
            int weekday = resolveWeekday(recordDate);

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

    private static int resolveWeekday(Date recordDate) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(recordDate);
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        return dayOfWeek == Calendar.SUNDAY ? 0 : dayOfWeek - 1;
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
