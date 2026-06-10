package com.nongxinle.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nongxinle.entity.GbDepFoodGoodsSalesEntity;
import com.nongxinle.entity.GbDepFoodSalesEntity;
import com.nongxinle.entity.GbDistributerFoodGoodsEntity;
import com.nongxinle.service.GbDepFoodGoodsSalesService;
import com.nongxinle.service.GbDepFoodSalesService;
import com.nongxinle.service.GbDistributerFoodGoodsService;
import com.nongxinle.utils.GbConstants;
import com.nongxinle.utils.GbDateTimeUtils;
import com.nongxinle.utils.GbDepartmentGoodsStockReduceSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.List;

/**
 * {@code gb_dep_food_sales} 统一写入：type 默认、价格快照、subtotal、父记录 upsert、配料展开与对称删除。
 * <p>菜品型员工餐仅展开 {@code gb_dep_food_goods_sales}，不触发 {@code stock_reduce type=6}。</p>
 */
@Component
@RequiredArgsConstructor
public class GbDepFoodSalesWriteSupport {

    private final GbDepFoodSalesService gbDepFoodSalesService;
    private final GbDepFoodGoodsSalesService gbDepFoodGoodsSalesService;
    private final GbDistributerFoodGoodsService gbDistributerFoodGoodsService;

    public GbDepFoodSalesEntity findExisting(Integer depId, Integer foodId, String fullDate, Integer type) {
        Integer resolvedType = GbConstants.FoodSalesType.normalize(type);
        return gbDepFoodSalesService.getOne(
                new LambdaQueryWrapper<GbDepFoodSalesEntity>()
                        .eq(GbDepFoodSalesEntity::getGbDfsDepId, depId)
                        .eq(GbDepFoodSalesEntity::getGbDfsFoodId, foodId)
                        .eq(GbDepFoodSalesEntity::getGbDfsFullDate, fullDate)
                        .eq(GbDepFoodSalesEntity::getGbDfsType, resolvedType)
                        .last("LIMIT 1"),
                false);
    }

    public UpsertResult upsertFoodSalesLine(FoodSalesWriteCommand cmd) {
        if (cmd == null || cmd.depId == null || cmd.foodId == null || cmd.fullDate == null
                || cmd.qty == null || cmd.qty.compareTo(BigDecimal.ZERO) <= 0) {
            return UpsertResult.skipped();
        }
        Integer type = GbConstants.FoodSalesType.normalize(cmd.type);
        Date recordDate = cmd.recordDate != null ? cmd.recordDate : GbDateTimeUtils.parseDay(cmd.fullDate);
        String month = cmd.month != null ? cmd.month : GbDateTimeUtils.formatYearMonth(recordDate);
        String year = cmd.year != null ? cmd.year : GbDateTimeUtils.formatYear(recordDate);
        int weekday = cmd.weekday >= 0 ? cmd.weekday : GbDateTimeUtils.weekdayForAiDailyRevenue(recordDate);

        GbDepFoodSalesEntity sales = findExisting(cmd.depId, cmd.foodId, cmd.fullDate, type);
        boolean isNew = sales == null;
        if (isNew) {
            sales = new GbDepFoodSalesEntity();
            sales.setGbDfsDepId(cmd.depId);
            sales.setGbDfsDepFatherId(cmd.depFatherId);
            sales.setGbDfsFoodId(cmd.foodId);
            sales.setGbDfsDistributerId(cmd.distributerId);
            sales.setGbDfsFullDate(cmd.fullDate);
            sales.setGbDfsMonth(month);
            sales.setGbDfsYear(year);
            sales.setGbDfsRevenueWeekday(weekday);
            sales.setGbDfsRevenueHoliday(cmd.holiday != null ? cmd.holiday : "");
        }

        applyPricing(sales, type, cmd.qty, cmd.listPriceStr, cmd.actualUnitPrice, cmd.discountRate);
        sales.setGbDfsAmount(cmd.qty.stripTrailingZeros().toPlainString());

        if (isNew) {
            gbDepFoodSalesService.save(sales);
        } else {
            gbDepFoodSalesService.updateById(sales);
            removeGoodsSalesForParent(sales.getGbDepFoodSalesId());
        }

        int goodsRows = expandRecipeLines(sales, cmd.depId, cmd.depFatherId, cmd.foodId, cmd.qty, month, cmd.fullDate,
                weekday, cmd.holiday);
        return new UpsertResult(isNew, goodsRows, sales);
    }

    public void removeGoodsSalesForParent(Integer foodSalesId) {
        if (foodSalesId == null) {
            return;
        }
        gbDepFoodGoodsSalesService.remove(
                new LambdaQueryWrapper<GbDepFoodGoodsSalesEntity>()
                        .eq(GbDepFoodGoodsSalesEntity::getGbDfgsFoodSalesId, foodSalesId));
    }

    /** 删除子部门+菜品+日期+type 已有销售行（含配料展开）；不存在则 false。 */
    public boolean deleteFoodSalesLineIfExists(Integer depId, Integer foodId, String fullDate, Integer type) {
        if (depId == null || foodId == null || fullDate == null) {
            return false;
        }
        GbDepFoodSalesEntity existing = findExisting(depId, foodId, fullDate, type);
        if (existing == null || existing.getGbDepFoodSalesId() == null) {
            return false;
        }
        removeGoodsSalesForParent(existing.getGbDepFoodSalesId());
        gbDepFoodSalesService.removeById(existing.getGbDepFoodSalesId());
        return true;
    }

    private void applyPricing(GbDepFoodSalesEntity sales, Integer type, BigDecimal qty, String listPriceStr,
            BigDecimal explicitActualUnitPrice, BigDecimal discountRate) {
        sales.setGbDfsType(type);
        BigDecimal original = parsePrice(listPriceStr);
        sales.setGbDfsOriginalUnitPrice(original);

        BigDecimal actualUnit;
        if (GbConstants.FoodSalesType.isNonOperationalConsumption(type)) {
            actualUnit = BigDecimal.ZERO;
        } else if (explicitActualUnitPrice != null) {
            actualUnit = explicitActualUnitPrice;
        } else if (GbConstants.FoodSalesType.NORMAL_SALE.equals(type)) {
            actualUnit = original;
        } else {
            actualUnit = original;
        }
        sales.setGbDfsActualUnitPrice(actualUnit.setScale(4, RoundingMode.HALF_UP));

        if (discountRate != null) {
            sales.setGbDfsDiscountRate(discountRate.setScale(4, RoundingMode.HALF_UP));
        } else if (original.compareTo(BigDecimal.ZERO) > 0 && actualUnit.compareTo(BigDecimal.ZERO) >= 0) {
            sales.setGbDfsDiscountRate(actualUnit.divide(original, 4, RoundingMode.HALF_UP));
        } else {
            sales.setGbDfsDiscountRate(null);
        }

        BigDecimal subtotal = actualUnit.multiply(qty).setScale(2, RoundingMode.HALF_UP);
        sales.setGbDfsSubtotal(subtotal.stripTrailingZeros().toPlainString());
    }

    private int expandRecipeLines(GbDepFoodSalesEntity sales, Integer depId, Integer depFatherId, Integer foodId,
            BigDecimal qty, String month, String fullDate, int weekday, String holiday) {
        List<GbDistributerFoodGoodsEntity> recipe = gbDistributerFoodGoodsService.queryFoodGoodsByFoodId(foodId);
        if (recipe == null || recipe.isEmpty()) {
            return 0;
        }
        int count = 0;
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
            ggs.setGbDfgsRevenueHoliday(holiday != null ? holiday : "");

            gbDepFoodGoodsSalesService.save(ggs);
            count++;
        }
        return count;
    }

    private static BigDecimal parsePrice(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(raw.trim()).setScale(4, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private static BigDecimal parseAmount(String raw) {
        return GbDepartmentGoodsStockReduceSupport.coerceDecimal(raw);
    }

    public static final class FoodSalesWriteCommand {
        public Integer depId;
        public Integer depFatherId;
        public Integer foodId;
        public Integer distributerId;
        public String fullDate;
        public Date recordDate;
        public String month;
        public String year;
        public int weekday = -1;
        public String holiday;
        public BigDecimal qty;
        public Integer type;
        public String listPriceStr;
        public BigDecimal actualUnitPrice;
        public BigDecimal discountRate;
    }

    public static final class UpsertResult {
        public final boolean inserted;
        public final int goodsRows;
        public final GbDepFoodSalesEntity entity;

        private UpsertResult(boolean inserted, int goodsRows, GbDepFoodSalesEntity entity) {
            this.inserted = inserted;
            this.goodsRows = goodsRows;
            this.entity = entity;
        }

        static UpsertResult skipped() {
            return new UpsertResult(false, 0, null);
        }
    }
}
