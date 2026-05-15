package com.nongxinle.ai.planner;

/**
 * C-53：经营诊断 Composite 生产入口 Gate 的稳定拒绝/放行原因码（监控与日志；不含用户原文）。
 */
public enum BusinessDiagnosisCompositeGateReasonCode {

    /** 功能开关关闭；默认生产行为。 */
    FEATURE_FLAG_DISABLED,

    /** 通过 Gate；单店范围。 */
    ALLOWED_STORE,

    /** 通过 Gate；集团/多店范围。 */
    ALLOWED_GROUP,

    /** 语义或编排要求澄清。 */
    CLARIFICATION_REQUIRED,

    /** {@code resolvedQueryContext} 为空。 */
    MISSING_RESOLVED_CONTEXT,

    /** 时间窗缺少 {@code startDate}/{@code endDate}。 */
    MISSING_TIME_WINDOW,

    /** {@code orgScope.scopeType} 非 STORE / GROUP。 */
    UNSUPPORTED_SCOPE,

    /** STORE：缺少门店锚点或无法在 {@code visibleStores} 中定位。 */
    STORE_SCOPE_MISSING_ANCHOR,

    /** GROUP：有效可见门店根少于 2。 */
    GROUP_SCOPE_INSUFFICIENT_VISIBLE_STORES,

    /** intent/path/structured 组合不在 C-52.1 白名单。 */
    INTENT_PATH_NOT_WHITELISTED,

    /** 单域 intent（营收/采购/出库/菜品/成本/库房），非 Composite。 */
    DOMAIN_SINGLE_INTENT_NOT_COMPOSITE,

    /** 点名菜品收窄，非 Composite v1。 */
    NAMED_DISH_DEEP_DIVE_NOT_COMPOSITE,

    /** 结构化子意图为排行/深挖 wire（§3.3.4），非 Composite。 */
    RANKING_OR_DEEP_DIVE_NOT_COMPOSITE
}
