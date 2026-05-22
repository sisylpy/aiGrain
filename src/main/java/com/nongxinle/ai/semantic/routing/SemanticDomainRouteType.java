package com.nongxinle.ai.semantic.routing;

/**
 * Step 1 选域结果类型（仅路由观测；不含 wire / planType）。
 */
public enum SemanticDomainRouteType {
    /** 当前问句 businessObjects 明确命中单域。 */
    EXPLICIT,
    /** 当前问句不明确，沿用 previousTurn.pathCode 推断域。 */
    INHERITED,
    /** 多域同分或接近，无单一 primaryDomain。 */
    AMBIGUOUS,
    /** 无法从问句或上文推断域。 */
    UNKNOWN
}
