package com.nongxinle.ai.graph.business;

import com.nongxinle.utils.GbDepartmentGoodsStockReduceSupport;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * P3-I：菜品毛利实际成本字段语义（type1 生产出库 vs type1+2+3 完整实际成本）。
 * Tool payload 保留 {@code actualCostAmount}=type1；AnswerPlan 对外展示优先 type123。
 */
public final class DishProfitActualCostSemanticsSupport {

    private DishProfitActualCostSemanticsSupport() {}

    /** legacy {@code actualCostAmount}：仅 type1 生产出库成本。 */
    public static BigDecimal productionActualCostType1(Map<String, Object> row) {
        if (row == null) {
            return BigDecimal.ZERO;
        }
        return GbDepartmentGoodsStockReduceSupport.coerceDecimal(row.get("actualCostAmount"));
    }

    /** {@code actualCostTotalAmount123}：type1+2+3 完整实际成本。 */
    public static BigDecimal totalActualCost123(Map<String, Object> row) {
        if (row == null) {
            return BigDecimal.ZERO;
        }
        Object raw = row.get("actualCostTotalAmount123");
        if (raw == null) {
            raw = row.get("totalActualCostAmount123");
        }
        return GbDepartmentGoodsStockReduceSupport.coerceDecimal(raw);
    }

    /** AnswerPlan / 排行 / 对外展示：优先 type123，字段缺失时回退 type1。 */
    public static BigDecimal displayActualCost(Map<String, Object> row) {
        if (row == null) {
            return BigDecimal.ZERO;
        }
        if (hasExplicitAmount123(row)) {
            return totalActualCost123(row);
        }
        return productionActualCostType1(row);
    }

    public static BigDecimal gapDisplayActualMinusTheory(Map<String, Object> row) {
        if (row == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal theory = GbDepartmentGoodsStockReduceSupport.coerceDecimal(row.get("theoryCostAmount"));
        return displayActualCost(row).subtract(theory);
    }

    public static String plainMoney(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    public static void putProductionAndTotalActualFields(Map<String, Object> row, Map<String, Object> target) {
        if (target == null) {
            return;
        }
        BigDecimal type1 = productionActualCostType1(row);
        BigDecimal type123 = totalActualCost123(row);
        target.put("productionActualCostAmount", plainMoney(type1));
        if (hasExplicitAmount123(row)) {
            target.put("actualCostTotalAmount123", plainMoney(type123));
            target.put("totalActualCostAmount123", plainMoney(type123));
        }
    }

    public static boolean hasExplicitAmount123(Map<String, Object> row) {
        if (row == null) {
            return false;
        }
        Object v = row.get("actualCostTotalAmount123");
        if (v == null) {
            v = row.get("totalActualCostAmount123");
        }
        return v != null && StringUtils.hasText(v.toString());
    }
}
