package com.nongxinle.ai.semantic.inheritance;

/** 合同状态迁移类型；优先于一般 cross-family sovereign 判定。 */
public enum SemanticContractTransitionType {
    NONE,
    /** 同能力 structured time-only：继承 stable contract，仅覆盖 time / salesBaselineWindow。 */
    SAME_CAPABILITY_TIME_OVERRIDE,
    /** 同 contract family 排行 structured time-only。 */
    SAME_FAMILY_TIME_OVERRIDE,
    SAME_CAPABILITY_NAMED_ENTITY,
    SAME_GOODS_ANCHOR_ENTITY,
    BARE_RANKING_DIMENSION_SWITCH,
    /** 当前轮 V2 主权新能力；previous 不得覆盖 business frame。 */
    SOVEREIGN_NEW_CAPABILITY,
    /** 跨 family 主权：仅继承 context（scope）。 */
    CROSS_FAMILY_CONTEXT_ONLY,
    EXPLICIT_ENTITY_NEW_CAPABILITY
}
