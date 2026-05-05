package com.nongxinle.ai.orchestration;

/**
 * Skill 路由结果来源：模型结构化 JSON 为主，解析失败或空结果时由规则兜底；
 * 任意轮次上可由 {@link com.nongxinle.ai.routing.SkillRouteCatalog} 合并缺省 skill（见 ENRICHED）。
 */
public enum ChatRouteSource {
    LLM,
    RULE_FALLBACK,
    ENRICHED
}
