package com.nongxinle.ai.prompt;

/**
 * 按 promptId 未找到或未登记 classpath 资源时抛出，便于 Harness 立刻定位缺失文件。
 */
public class AiPromptNotFoundException extends IllegalStateException {

    public AiPromptNotFoundException(String message) {
        super(message);
    }
}
