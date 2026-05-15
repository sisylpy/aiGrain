package com.nongxinle.ai.semantic;

import org.springframework.util.StringUtils;

/**
 * 查询语义 v2 时间相关的**归一化用户句**信号（与 {@code query_semantic_parser.v2.md}「明确时间词」对齐）。
 * <p>
 * 集中维护口语片段，避免在合并 / 策略类中散落 {@code String#contains}；后续扩展「本周 / 今天」等统一加在此类。
 */
public final class AiQuerySemanticTimeLexicon {

    private AiQuerySemanticTimeLexicon() {
    }

    /**
     * 本句是否出现当前自然月的明确口语（Resolver 归一后的 {@code normalizedUserMessage}）。
     */
    public static boolean explicitCurrentMonthMentioned(String normalizedUserMessage) {
        if (!StringUtils.hasText(normalizedUserMessage)) {
            return false;
        }
        String m = normalizedUserMessage;
        return m.contains("这个月") || m.contains("本月") || m.contains("当前月");
    }
}
