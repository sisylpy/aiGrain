package com.nongxinle.ai.harness.replay;

import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * Harness Replay 执行形态：仅 Resolver，或与生产一致的同步业务图。
 */
public enum AiHarnessReplayMode {
    RESOLVER_ONLY,
    GRAPH_RUN,
    /**
     * PlannerExecutor Harness：{@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_MOCK_CORE}、
     * {@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_MOCK_DEGRADED_CORE}、Composite strict（C-35 / C-48 / C-42）。
     * P1-B Final 已摘除单域 Adapter 专用 replayMode。
     */
    PLANNER_EXECUTOR_MOCK,

    /**
     * C-54：{@link AiHarnessReplayCompositeGate} — 仅
     * {@link com.nongxinle.ai.planner.BusinessDiagnosisCompositeProductionGate}，不执行 {@code PlannerExecutor} / Tool。
     */
    BUSINESS_DIAGNOSIS_COMPOSITE_GATE;

    public static AiHarnessReplayMode fromApiString(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
