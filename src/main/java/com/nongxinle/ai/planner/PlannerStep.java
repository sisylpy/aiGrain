package com.nongxinle.ai.planner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 执行计划中的一步（Planner 骨架 DTO）。
 * <p>
 * 前缀为 {@code mock*} 的字段<strong>仅用于</strong> {@link PlannerExecutor} <strong>mock / Harness</strong> 路径：生产执行链路不得依赖
 * 这些字段表达真实业务状态；未来生产 Executor 应忽略或使用独立计划模型。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlannerStep {

    private String stepId;
    private String stepName;
    /** 执行顺序；仅骨架约定，Executor 按此排序后遍历 */
    private Integer order;
    private String targetAgent;
    /** 主 Tool id，Replay 对齐用；多 Tool 后续可扩展 */
    private String targetTool;
    private String inputSummary;
    private String expectedOutput;
    private String acceptanceCriteria;
    /** 为空则继承 {@link PlannerExecutionPlan#getFailureStrategy()} */
    private PlannerFailureStrategy failureStrategy;

    /**
     * Mock / Harness：本步在 skeleton 中应呈现的执行结果类别。
     * {@code null} 时 {@link PlannerExecutor} <strong>仅在本 mock 实现中</strong> 视为 {@link PlannerStepMockExecutionStatus#SUCCESS}，<strong>不是</strong>生产上「未执行即成功」的语义。
     */
    private PlannerStepMockExecutionStatus mockExecutionStatus;

    /**
     * Mock / Harness：当 {@code mockExecutionStatus == DEGRADED} 时写入结果 {@link PlannerStepResult#getDegradedReason()}；
     * 当为 {@code FAILED} 且策略为 {@link PlannerFailureStrategy#CONTINUE_WITH_DEGRADED} 时，非空则优先作为降级原因（否则回退 {@link #mockErrorMessage}）。
     */
    private String mockDegradedReason;

    /**
     * Mock / Harness：当 {@code mockExecutionStatus == FAILED} 时作为失败/降级消息来源（见 {@link PlannerExecutor}）；
     * 对 {@code ASK_CLARIFICATION} / {@code FAIL_FAST} 路径写入 {@link PlannerStepResult#getErrorMessage()}。
     */
    private String mockErrorMessage;

    /**
     * 可选：本步只读的 AnswerPlan / 子计划句柄（C-7+ Adapter）；由编排层注入，非用户原文。
     */
    private String answerPlanRef;
}

