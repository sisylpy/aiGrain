package com.nongxinle.ai.semantic.inheritance;

/** previousTurn → currentTurn 业务槽位继承模式。 */
public enum SemanticSlotInheritanceMode {
    /** 不从 previousTurn 继承业务 semanticSlots。 */
    INHERIT_NONE,
    /** 仅继承 scope / store / department 等安全上下文。 */
    INHERIT_CONTEXT_ONLY,
    /** 同 contract family 的 structured time-only follow-up：可恢复上一轮业务 frame。 */
    INHERIT_SAME_FAMILY_TIME_FOLLOWUP,
    /**
     * 裸排行维度切换：仅继承 scope；Business Frame 从 {@link BareRankingDimensionSwitchPlan}
     * 目标 ACTIVE contract 派生，禁止继承上一轮 frame 与 Top1 anchor。
     */
    INHERIT_BARE_RANKING_DIMENSION_SWITCH,
    /**
     * 同能力换菜名：上一轮 DISH_COST 子合同 frame + 当前轮结构化新菜名（Intake reason 与上一轮合同对齐）。
     */
    INHERIT_SAME_CAPABILITY_NAMED_ENTITY,
    /**
     * 同 GOODS 锚点追问：上一轮 {@code warehouse.goods_supported_dish_cover.v1} + 裸库存追问，
     * 恢复上一轮合同与 {@code USE_PREVIOUS_ANCHOR}，禁止 {@code warehouse.overview} 抢主权。
     */
    INHERIT_SAME_GOODS_ANCHOR_FOLLOWUP
}
