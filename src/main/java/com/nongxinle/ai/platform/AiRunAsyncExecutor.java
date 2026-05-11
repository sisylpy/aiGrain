package com.nongxinle.ai.platform;

import com.nongxinle.ai.config.AiAsyncConfig;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class AiRunAsyncExecutor {

    @Async(AiAsyncConfig.AI_RUN_EXECUTOR)
    public void runAsync(Runnable task) {
        task.run();
    }
}
