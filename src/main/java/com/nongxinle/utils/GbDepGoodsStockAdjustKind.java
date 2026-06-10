package com.nongxinle.utils;

import java.util.Locale;

/**
 * 部门库存调整 {@code kind} 唯一规范值（API / Ledger 共用，小写 snake_case）。
 * <p>解析规则：trim + {@link Locale#ROOT} 小写后精确匹配；不接受别名或 camelCase。</p>
 */
public final class GbDepGoodsStockAdjustKind {

    public static final String PRODUCE = "produce";
    public static final String LOSS = "loss";
    public static final String RETURN = "return";
    public static final String WASTE = "waste";
    /** 原料型员工餐消耗（对应 {@link GbConstants.StockReduceType#EMPLOYEE_MEAL}） */
    public static final String EMPLOYEE_MEAL = "employee_meal";

    private static final String VALID_KINDS =
            PRODUCE + "、loss、return、waste、" + EMPLOYEE_MEAL;

    private GbDepGoodsStockAdjustKind() {
    }

    /**
     * @return 规范 kind；无法识别时返回 {@code null}
     */
    public static String resolveCanonicalKind(String kind) {
        if (kind == null || kind.trim().isEmpty()) {
            return null;
        }
        String k = kind.trim().toLowerCase(Locale.ROOT);
        if (PRODUCE.equals(k) || LOSS.equals(k) || RETURN.equals(k) || WASTE.equals(k) || EMPLOYEE_MEAL.equals(k)) {
            return k;
        }
        return null;
    }

    public static String invalidKindMessage() {
        return "kind 无效，应为 " + VALID_KINDS;
    }

    public static Integer toStockReduceType(String canonicalKind) {
        if (canonicalKind == null) {
            return null;
        }
        switch (canonicalKind) {
            case PRODUCE:
                return GbConstants.StockReduceType.PRODUCTION;
            case LOSS:
                return GbConstants.StockReduceType.LOSS;
            case RETURN:
                return GbConstants.StockReduceType.RETURN;
            case WASTE:
                return GbConstants.StockReduceType.WASTE;
            case EMPLOYEE_MEAL:
                return GbConstants.StockReduceType.EMPLOYEE_MEAL;
            default:
                return null;
        }
    }
}
