package com.nongxinle.utils;

import com.nongxinle.entity.GbDepFoodSalesEntity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * {@code gb_dep_food_sales} 统一统计口径；禁止各 Service 散落 type 数字判断。
 */
public final class GbDepFoodSalesMetricsSupport {

    private GbDepFoodSalesMetricsSupport() {
    }

    public static Integer resolveType(GbDepFoodSalesEntity row) {
        if (row == null || row.getGbDfsType() == null) {
            return GbConstants.FoodSalesType.NORMAL_SALE;
        }
        return row.getGbDfsType();
    }

    public static boolean countsAsOperationalSales(GbDepFoodSalesEntity row) {
        return GbConstants.FoodSalesType.isOperationalSales(resolveType(row));
    }

    public static boolean countsAsIngredientConsumption(GbDepFoodSalesEntity row) {
        return GbConstants.FoodSalesType.isIngredientConsumption(resolveType(row));
    }

    public static boolean countsAsOperationalRevenue(GbDepFoodSalesEntity row) {
        return GbConstants.FoodSalesType.isOperationalRevenue(resolveType(row));
    }

    public static BigDecimal rowQty(GbDepFoodSalesEntity row) {
        return GbDepartmentGoodsStockReduceSupport.coerceDecimal(row == null ? null : row.getGbDfsAmount());
    }

    public static BigDecimal rowSubtotal(GbDepFoodSalesEntity row) {
        return GbDepartmentGoodsStockReduceSupport.coerceDecimal(row == null ? null : row.getGbDfsSubtotal());
    }

    public static BigDecimal rowOriginalUnitPrice(GbDepFoodSalesEntity row) {
        if (row == null || row.getGbDfsOriginalUnitPrice() == null) {
            return BigDecimal.ZERO;
        }
        return row.getGbDfsOriginalUnitPrice();
    }

    public static BigDecimal rowActualUnitPrice(GbDepFoodSalesEntity row) {
        if (row == null || row.getGbDfsActualUnitPrice() == null) {
            return BigDecimal.ZERO;
        }
        return row.getGbDfsActualUnitPrice();
    }

    /** 经营销售份数（type 1/2/3）。 */
    public static BigDecimal operationalSalesQty(GbDepFoodSalesEntity row) {
        if (!countsAsOperationalSales(row)) {
            return BigDecimal.ZERO;
        }
        return rowQty(row);
    }

    /** 菜品总制作/消费份数（type 1～5）。 */
    public static BigDecimal totalConsumptionQty(GbDepFoodSalesEntity row) {
        if (!countsAsIngredientConsumption(row)) {
            return BigDecimal.ZERO;
        }
        return rowQty(row);
    }

    public static BigDecimal employeeMealQty(GbDepFoodSalesEntity row) {
        if (!GbConstants.FoodSalesType.isEmployeeMeal(resolveType(row))) {
            return BigDecimal.ZERO;
        }
        return rowQty(row);
    }

    public static BigDecimal complimentaryQty(GbDepFoodSalesEntity row) {
        if (!GbConstants.FoodSalesType.isComplimentary(resolveType(row))) {
            return BigDecimal.ZERO;
        }
        return rowQty(row);
    }

    public static BigDecimal normalSaleQty(GbDepFoodSalesEntity row) {
        return GbConstants.FoodSalesType.NORMAL_SALE.equals(resolveType(row)) ? rowQty(row) : BigDecimal.ZERO;
    }

    public static BigDecimal discountSaleQty(GbDepFoodSalesEntity row) {
        return GbConstants.FoodSalesType.DISCOUNT_SALE.equals(resolveType(row)) ? rowQty(row) : BigDecimal.ZERO;
    }

    public static BigDecimal memberSaleQty(GbDepFoodSalesEntity row) {
        return GbConstants.FoodSalesType.MEMBER_SALE.equals(resolveType(row)) ? rowQty(row) : BigDecimal.ZERO;
    }

    /** 经营实际收入：仅 type 1/2/3 的 gb_dfs_subtotal。 */
    public static BigDecimal operationalActualRevenue(GbDepFoodSalesEntity row) {
        if (!countsAsOperationalRevenue(row)) {
            return BigDecimal.ZERO;
        }
        return rowSubtotal(row);
    }

    /** 经营标价收入：originalUnitPrice × amount（仅经营销售）。 */
    public static BigDecimal operationalListPriceRevenue(GbDepFoodSalesEntity row) {
        if (!countsAsOperationalSales(row)) {
            return BigDecimal.ZERO;
        }
        BigDecimal q = rowQty(row);
        if (q.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return rowOriginalUnitPrice(row).multiply(q).setScale(2, RoundingMode.HALF_UP);
    }

    /** 折扣额：标价收入 − 实际收入（仅经营销售且标价&gt;实际）。 */
    public static BigDecimal discountAmount(GbDepFoodSalesEntity row) {
        if (!countsAsOperationalSales(row)) {
            return BigDecimal.ZERO;
        }
        BigDecimal list = operationalListPriceRevenue(row);
        BigDecimal actual = operationalActualRevenue(row);
        BigDecimal diff = list.subtract(actual);
        return diff.compareTo(BigDecimal.ZERO) > 0 ? diff.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    public static BigDecimal normalSaleRevenue(GbDepFoodSalesEntity row) {
        return GbConstants.FoodSalesType.NORMAL_SALE.equals(resolveType(row)) ? rowSubtotal(row) : BigDecimal.ZERO;
    }

    public static BigDecimal discountSaleRevenue(GbDepFoodSalesEntity row) {
        return GbConstants.FoodSalesType.DISCOUNT_SALE.equals(resolveType(row)) ? rowSubtotal(row) : BigDecimal.ZERO;
    }

    public static BigDecimal memberSaleRevenue(GbDepFoodSalesEntity row) {
        return GbConstants.FoodSalesType.MEMBER_SALE.equals(resolveType(row)) ? rowSubtotal(row) : BigDecimal.ZERO;
    }

    /** 单类型让利：原价×份数 − 实际小计（仅指定 type 行）。 */
    public static BigDecimal concessionForType(GbDepFoodSalesEntity row, Integer type) {
        if (row == null || type == null || !type.equals(resolveType(row))) {
            return BigDecimal.ZERO;
        }
        BigDecimal q = rowQty(row);
        if (q.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal list = rowOriginalUnitPrice(row).multiply(q).setScale(2, RoundingMode.HALF_UP);
        BigDecimal actual = rowSubtotal(row);
        BigDecimal diff = list.subtract(actual);
        return diff.compareTo(BigDecimal.ZERO) > 0 ? diff.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    public static BigDecimal discountConcession(GbDepFoodSalesEntity row) {
        return concessionForType(row, GbConstants.FoodSalesType.DISCOUNT_SALE);
    }

    public static BigDecimal memberConcession(GbDepFoodSalesEntity row) {
        return concessionForType(row, GbConstants.FoodSalesType.MEMBER_SALE);
    }

    /** 赠送原价价值（非营业额）。 */
    public static BigDecimal complimentaryOriginalValue(GbDepFoodSalesEntity row) {
        if (!GbConstants.FoodSalesType.isComplimentary(resolveType(row))) {
            return BigDecimal.ZERO;
        }
        BigDecimal q = rowQty(row);
        if (q.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return rowOriginalUnitPrice(row).multiply(q).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal sumOperationalSalesQty(Collection<GbDepFoodSalesEntity> rows) {
        return sum(rows, GbDepFoodSalesMetricsSupport::operationalSalesQty);
    }

    public static BigDecimal sumTotalConsumptionQty(Collection<GbDepFoodSalesEntity> rows) {
        return sum(rows, GbDepFoodSalesMetricsSupport::totalConsumptionQty);
    }

    public static BigDecimal sumEmployeeMealQty(Collection<GbDepFoodSalesEntity> rows) {
        return sum(rows, GbDepFoodSalesMetricsSupport::employeeMealQty);
    }

    public static BigDecimal sumComplimentaryQty(Collection<GbDepFoodSalesEntity> rows) {
        return sum(rows, GbDepFoodSalesMetricsSupport::complimentaryQty);
    }

    public static BigDecimal sumOperationalActualRevenue(Collection<GbDepFoodSalesEntity> rows) {
        return sum(rows, GbDepFoodSalesMetricsSupport::operationalActualRevenue);
    }

    public static BigDecimal sumOperationalListPriceRevenue(Collection<GbDepFoodSalesEntity> rows) {
        return sum(rows, GbDepFoodSalesMetricsSupport::operationalListPriceRevenue);
    }

    public static BigDecimal sumDiscountAmount(Collection<GbDepFoodSalesEntity> rows) {
        return sum(rows, GbDepFoodSalesMetricsSupport::discountAmount);
    }

    /** 按菜品汇总经营销量。 */
    public static Map<Integer, BigDecimal> operationalSalesQtyByFoodId(Collection<GbDepFoodSalesEntity> rows) {
        return qtyByFoodId(rows, GbDepFoodSalesMetricsSupport::operationalSalesQty);
    }

    /** 按菜品汇总总消费量。 */
    public static Map<Integer, BigDecimal> totalConsumptionQtyByFoodId(Collection<GbDepFoodSalesEntity> rows) {
        return qtyByFoodId(rows, GbDepFoodSalesMetricsSupport::totalConsumptionQty);
    }

    /** 按菜品汇总经营实际收入。 */
    public static Map<Integer, BigDecimal> operationalActualRevenueByFoodId(Collection<GbDepFoodSalesEntity> rows) {
        return qtyByFoodId(rows, GbDepFoodSalesMetricsSupport::operationalActualRevenue);
    }

    /** 按菜品汇总经营标价收入。 */
    public static Map<Integer, BigDecimal> operationalListPriceRevenueByFoodId(Collection<GbDepFoodSalesEntity> rows) {
        return qtyByFoodId(rows, GbDepFoodSalesMetricsSupport::operationalListPriceRevenue);
    }

    /**
     * 将总成本按经营销量/总消费量比值拆出「经营对应成本」；无消费量时返回 ZERO。
     */
    public static BigDecimal operationalShareOfCost(BigDecimal totalCost, BigDecimal operationalQty, BigDecimal consumptionQty) {
        if (totalCost == null || totalCost.signum() == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal op = operationalQty == null ? BigDecimal.ZERO : operationalQty;
        BigDecimal all = consumptionQty == null ? BigDecimal.ZERO : consumptionQty;
        if (op.signum() <= 0 || all.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return totalCost.multiply(op).divide(all, 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal employeeMealShareOfCost(BigDecimal totalCost, BigDecimal operationalQty, BigDecimal consumptionQty) {
        if (totalCost == null || totalCost.signum() == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal op = operationalShareOfCost(totalCost, operationalQty, consumptionQty);
        return totalCost.subtract(op).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private interface RowMetric {
        BigDecimal apply(GbDepFoodSalesEntity row);
    }

    private static BigDecimal sum(Collection<GbDepFoodSalesEntity> rows, RowMetric metric) {
        if (rows == null || rows.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (GbDepFoodSalesEntity row : rows) {
            if (row == null) {
                continue;
            }
            sum = sum.add(metric.apply(row));
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    private static Map<Integer, BigDecimal> qtyByFoodId(Collection<GbDepFoodSalesEntity> rows, RowMetric metric) {
        Map<Integer, BigDecimal> out = new HashMap<>();
        if (rows == null) {
            return out;
        }
        for (GbDepFoodSalesEntity row : rows) {
            if (row == null || row.getGbDfsFoodId() == null) {
                continue;
            }
            out.merge(row.getGbDfsFoodId(), metric.apply(row), BigDecimal::add);
        }
        return out;
    }
}
