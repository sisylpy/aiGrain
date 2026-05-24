package com.nongxinle.ai.semantic.intake.route;

/**
 * Step 1 选域结果类型（仅路由观测；不含 wire / planType）。
 */
public enum SemanticDomainRouteType {
    /** 当前问句 businessObjects 明确命中单域。 */
    EXPLICIT,
    /** 当前问句为省略追问且无明确域词，沿用 previousTurn.pathCode 推断域。 */
    INHERITED,
    /** 多域同分或接近，无单一 primaryDomain。 */
    AMBIGUOUS,
    /** 无法从问句或上文推断域。 */
    UNKNOWN,
    /** 一句话同时涉及多个一级业务域（由 SemanticIntake LLM 输出）。 */
    MULTI_DOMAIN
}
