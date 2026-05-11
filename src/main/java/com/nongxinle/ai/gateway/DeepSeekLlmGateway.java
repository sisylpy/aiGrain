package com.nongxinle.ai.gateway;

import com.nongxinle.ai.DeepSeekCompletionClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Agent Graph 主链路 LLM：非流式走 {@link DeepSeekCompletionClient}，与站内其它 DeepSeek 配置同源。
 */
@Component
@Primary
@ConditionalOnProperty(name = "ai.agent.llm.stub", havingValue = "false", matchIfMissing = true)
@RequiredArgsConstructor
public class DeepSeekLlmGateway implements LlmGateway {

    private final DeepSeekCompletionClient deepSeekCompletionClient;

    @Override
    public String chatSimple(String systemPrompt, String userMessage) {
        String user = userMessage == null ? "" : userMessage;
        String sys = systemPrompt == null ? "You are a helpful assistant." : systemPrompt;
        List<Map<String, String>> msgs = List.of(
                Map.of("role", "system", "content", sys),
                Map.of("role", "user", "content", user));
        return deepSeekCompletionClient.complete(msgs, "agent-graph-chat-simple", null);
    }
}
