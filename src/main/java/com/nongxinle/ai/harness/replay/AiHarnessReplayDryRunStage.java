package com.nongxinle.ai.harness.replay;

import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * Harness Replay dry-run：控制是否在 {@link AiHarnessReplayMode#GRAPH_RUN} 时仍走同步业务图。
 * <p>
 * {@code null}：不强制，沿用 {@link AiHarnessReplayService} 既有 {@code replayMode} 行为。
 */
public enum AiHarnessReplayDryRunStage {

    /**
     * 强制仅 Resolver + 摘要 + TurnMemory，不进入 {@link com.nongxinle.ai.platform.AiRunService#executeBusinessGraphSyncForHarness}。
     */
    RESOLVED_CONTEXT_ONLY,

    /**
     * 跑 Resolver → Scope/Time → DataPlanner → ToolExecution（仅 build*ToolArgs + RequestContext 快照），
     * 在 {@code Tool.execute} 之前截断；不跑 OutcomeReview / Composer，不 attach AnswerPlan。
     */
    TOOL_REQUEST_ONLY,

    /**
     * 不缩短链路；是否与生产一致跑图完全由 {@code replayMode}（及 caseId 推断）决定。
     */
    FULL;

    public static AiHarnessReplayDryRunStage fromApiString(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
