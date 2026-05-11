package com.nongxinle.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AiAsyncConfig {

    public static final String AI_RUN_EXECUTOR = "aiRunExecutor";

    @Bean(name = AI_RUN_EXECUTOR)
    Executor aiRunExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(8);
        ex.setQueueCapacity(500);
        ex.setThreadNamePrefix("ai-run-");
        ex.initialize();
        return ex;
    }
}
