package com.nongxinle.ai.gateway;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 占位：仅当 {@code ai.agent.llm.stub=true} 时使用；返回空串，正文由 Composer 侧的确定性摘要承担，避免向前端透出「占位」话术。 */
@Component
@ConditionalOnProperty(name = "ai.agent.llm.stub", havingValue = "true")
public class PlaceholderLlmGateway implements LlmGateway {

    @Override
    public String chatSimple(String systemPrompt, String userMessage) {
        return "";
    }
}
