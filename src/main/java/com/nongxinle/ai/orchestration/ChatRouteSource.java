package com.nongxinle.ai.orchestration;

/**
 * Skill 路由结果来源：模型结构化 JSON 为主，解析失败或空结果时由规则兜底。
 */
public enum ChatRouteSource {
    LLM,
    RULE_FALLBACK
}
