package com.nongxinle.ai.util;

import java.math.BigDecimal;

/**
 * 面向 SSE / 老板可读文案：数字不使用科学计数法。
 */
public final class AiNumericPlainText {

    private AiNumericPlainText() {
    }

    /**
     * @return 可解析为 {@link BigDecimal} 的数值则返回 {@link BigDecimal#toPlainString()} 形式；否则返回去掉首尾空白的原文。
     */
    public static String plainNumber(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof CharSequence cs) {
            String s = cs.toString().trim();
            if (s.isEmpty() || "-".equals(s) || "—".equals(s)) {
                return s;
            }
            try {
                return new BigDecimal(s).stripTrailingZeros().toPlainString();
            } catch (Exception e) {
                return s;
            }
        }
        if (value instanceof BigDecimal bd) {
            return bd.stripTrailingZeros().toPlainString();
        }
        if (value instanceof Float || value instanceof Double) {
            return BigDecimal.valueOf(((Number) value).doubleValue()).stripTrailingZeros().toPlainString();
        }
        if (value instanceof Number n) {
            try {
                return new BigDecimal(n.toString()).stripTrailingZeros().toPlainString();
            } catch (Exception e) {
                return value.toString().trim();
            }
        }
        String s = value.toString().trim();
        try {
            return new BigDecimal(s).stripTrailingZeros().toPlainString();
        } catch (Exception e) {
            return s;
        }
    }
}
