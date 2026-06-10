package com.nongxinle.ai.semantic.intake;

import org.springframework.util.StringUtils;

import java.util.Locale;

/** Intake 结构化追问种类；Policy / Sovereignty 只读此枚举，不读 {@code reason} 字符串。 */
public enum SemanticIntakeFollowUpKind {
    NONE,
    /** 同能力仅改时间 / 销量基线窗口；Business Frame 继承，仅覆盖 time。 */
    SAME_CAPABILITY_TIME_OVERRIDE,
    /** 同 DISH_COST 子能力换菜名。 */
    NAMED_ENTITY_SWAP,
    /** 同 GOODS 锚点库存裸追问。 */
    GOODS_ANCHOR_STOCK,
    /** 裸排行维度切换。 */
    RANKING_DIMENSION_SWITCH,
    /** 排行等同 contract family 的 structured time-only；不得用于跨子合同能力（如 cover-days vs cost）。 */
    RANKING_TIME_OVERRIDE;

    public static SemanticIntakeFollowUpKind normalize(String raw) {
        if (!StringUtils.hasText(raw)) {
            return NONE;
        }
        String n = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if ("TIME_ONLY".equals(n) || "COVER_DAYS_SALES_BASELINE".equals(n)) {
            return SAME_CAPABILITY_TIME_OVERRIDE;
        }
        if ("RANKING_TIME_ONLY".equals(n) || "RANKING_TIME_OVERRIDE".equals(n)) {
            return RANKING_TIME_OVERRIDE;
        }
        for (SemanticIntakeFollowUpKind k : values()) {
            if (k.name().equals(n)) {
                return k;
            }
        }
        return NONE;
    }
}
