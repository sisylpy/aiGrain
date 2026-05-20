package com.nongxinle.ai.planner;

import java.util.Locale;

/**
 * Composite Execution 模式。
 * Spring {@code ai.composite.businessDiagnosis.executionMode}：{@link #OFF}（默认）、{@link #SHADOW}；
 * Harness 请求字段：{@link #HARNESS_ONLY}。
 */
public enum BusinessDiagnosisCompositeExecutionMode {
    OFF,
    HARNESS_ONLY,
    SHADOW,
    /**
     * 预留值：{@link BusinessDiagnosisCompositeExecutionService} 当前<strong>不执行</strong> PRIMARY，
     * 不接生产主回答链，不替换 {@link com.nongxinle.ai.core.AiRunState#getFinalAnswerText()}。
     */
    PRIMARY;

    /**
     * API / Spring 配置取值：{@code OFF} / {@code HARNESS_ONLY} / {@code SHADOW} / {@code PRIMARY}；
     * 空白或无法识别 → {@link #OFF}。
     */
    public static BusinessDiagnosisCompositeExecutionMode fromHarnessApiString(String raw) {
        if (raw == null || raw.isBlank()) {
            return OFF;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return OFF;
        }
    }
}
