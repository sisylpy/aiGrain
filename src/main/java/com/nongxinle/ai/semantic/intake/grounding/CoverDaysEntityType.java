package com.nongxinle.ai.semantic.intake.grounding;

import org.springframework.util.StringUtils;

/** Intake / V2 结构化实体类型（cover days 落地）。 */
public final class CoverDaysEntityType {

    public static final String DISH = "DISH";
    public static final String GOODS = "GOODS";
    /** LLM 未给出或无法识别实体类型时，由存在性探测决定合同（先菜品后原料）。 */
    public static final String UNKNOWN = "UNKNOWN";

    private CoverDaysEntityType() {}

    public static String normalize(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String n = raw.trim().toUpperCase();
        if (DISH.equals(n) || "DISH_NAME".equals(n)) {
            return DISH;
        }
        if (GOODS.equals(n) || "INGREDIENT".equals(n) || "INVENTORY_GOODS".equals(n)) {
            return GOODS;
        }
        if (UNKNOWN.equals(n)) {
            return UNKNOWN;
        }
        return null;
    }

    /** Grounding 用：有明确类型用明确类型，否则 UNKNOWN（不阻断落地）。 */
    public static String resolveForGrounding(String raw) {
        String normalized = normalize(raw);
        return normalized != null ? normalized : UNKNOWN;
    }
}
