package com.nongxinle.ai.semantic.inheritance;

import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 菜品排行上下文后的 structured time-only Intake reason（与裸排行维度切换互斥）。
 * 仅读 Intake {@code reason} token，不解析用户原文。
 */
public final class StructuredRankingTimeOnlyIntakeSupport {

    public static final String FOLLOW_UP_PATH_TIME_ONLY = "STRUCTURED_RANKING_TIME_ONLY";
    public static final String FOLLOW_UP_PATH_DIMENSION_SWITCH = "BARE_RANKING_DIMENSION_SWITCH";

    private StructuredRankingTimeOnlyIntakeSupport() {}

    public static boolean isStructuredRankingTimeOnlyIntakeReason(String reason) {
        String normalized = normalizeReason(reason);
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        return normalized.contains("time_only")
                || normalized.contains("_time_follow")
                || normalized.endsWith("_time_follow_up")
                || normalized.contains("ranking_time_follow");
    }

    private static String normalizeReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return null;
        }
        return reason.trim().toLowerCase(Locale.ROOT);
    }
}
