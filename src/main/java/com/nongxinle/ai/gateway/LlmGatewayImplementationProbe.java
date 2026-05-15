package com.nongxinle.ai.gateway;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时确认实际注入的 {@link LlmGateway} 实现（DeepSeek / Placeholder），便于与语义解析 raw 观测对齐。
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class LlmGatewayImplementationProbe implements ApplicationRunner {

    private final LlmGateway llmGateway;

    @Override
    public void run(ApplicationArguments args) {
        log.info("LlmGateway active implementation = {}", llmGateway.getClass().getName());
    }
}
