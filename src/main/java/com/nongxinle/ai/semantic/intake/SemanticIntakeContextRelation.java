package com.nongxinle.ai.semantic.intake;

import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

/**
 * Intake 与上一轮的业务上下文关系；Policy / 继承层只读此字段，不读 {@code reason}。
 */
public enum SemanticIntakeContextRelation {
    /** 开启独立新业务 capability，不得标为 follow-up 或继承上一轮业务 frame。 */
    NEW_CAPABILITY,
    /** 同一 capability 下的上下文延续（时间/范围/实体追问等）。 */
    CONTEXT_CONTINUATION;

    private static final Set<String> KNOWN =
            Set.of(NEW_CAPABILITY.name(), CONTEXT_CONTINUATION.name());

    public static String normalize(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String n = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return KNOWN.contains(n) ? n : null;
    }

    public static boolean isKnown(String raw) {
        if (!StringUtils.hasText(raw)) {
            return false;
        }
        return KNOWN.contains(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    }
}
