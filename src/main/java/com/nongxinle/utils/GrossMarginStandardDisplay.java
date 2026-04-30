package com.nongxinle.utils;

import com.nongxinle.entity.GbDistributerFoodEntity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * 父级「直接」配置的目标毛利率 T、浮动 F；与单菜、列表行的 {@code blendedGrossMarginRateOnListPrice} 同显示口径（百分数 0～100 字符串比较）。
 */
public final class GrossMarginStandardDisplay {

    public static final String LEVEL_UNKNOWN = "UNKNOWN";
    public static final String LEVEL_IN_BAND = "IN_BAND";
    public static final String LEVEL_BELOW = "BELOW";
    public static final String LEVEL_ABOVE = "ABOVE";

    private GrossMarginStandardDisplay() {
    }

    /**
     * 写入：{@code grossMarginStandardTarget/FloatAbs/BandLower/BandUpper} 与 {@code grossMarginLevel}。
     * @param blendedRatio0to1  (标价−type1+2+3 实摊)÷标价 的 0～1 比例，不可比时为 null
     * @param directParent 直接父行（同 {@code gb_distributer_food}），可 null
     */
    public static void putOnMap(Map<String, Object> line, BigDecimal blendedRatio0to1, GbDistributerFoodEntity directParent) {
        line.put("grossMarginStandardTarget", null);
        line.put("grossMarginStandardFloatAbs", null);
        line.put("grossMarginStandardBandLower", null);
        line.put("grossMarginStandardBandUpper", null);
        line.put("grossMarginLevel", LEVEL_UNKNOWN);
        if (directParent == null) {
            return;
        }
        BigDecimal t = directParent.getGbDfTargetGrossMarginRate();
        BigDecimal f = directParent.getGbDfGrossMarginFloatAbs();
        if (t == null || f == null) {
            return;
        }
        BigDecimal lower = t.subtract(f);
        BigDecimal upper = t.add(f);
        line.put("grossMarginStandardTarget", t.setScale(2, RoundingMode.HALF_UP).toPlainString());
        line.put("grossMarginStandardFloatAbs", f.setScale(2, RoundingMode.HALF_UP).toPlainString());
        line.put("grossMarginStandardBandLower", lower.setScale(2, RoundingMode.HALF_UP).toPlainString());
        line.put("grossMarginStandardBandUpper", upper.setScale(2, RoundingMode.HALF_UP).toPlainString());
        if (blendedRatio0to1 == null) {
            return;
        }
        BigDecimal p = blendedRatio0to1.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
        if (p.compareTo(lower) < 0) {
            line.put("grossMarginLevel", LEVEL_BELOW);
        } else if (p.compareTo(upper) > 0) {
            line.put("grossMarginLevel", LEVEL_ABOVE);
        } else {
            line.put("grossMarginLevel", LEVEL_IN_BAND);
        }
    }
}
