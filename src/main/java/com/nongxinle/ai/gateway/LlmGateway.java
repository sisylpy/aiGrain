package com.nongxinle.ai.gateway;

/**
 * 统一 LLM 调用入口。生产默认实现为 {@link DeepSeekLlmGateway}；
 * {@code ai.agent.llm.stub=true} 时使用 {@link PlaceholderLlmGateway}。
 */
public interface LlmGateway {

    /** 非流式对话占位 */
    String chatSimple(String systemPrompt, String userMessage);

    /** 后续：流式、结构化输出等 */
}
