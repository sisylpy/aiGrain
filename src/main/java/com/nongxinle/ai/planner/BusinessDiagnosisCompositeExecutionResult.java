package com.nongxinle.ai.planner;

import com.nongxinle.ai.dto.business.BusinessDiagnosisCompositeAnswerPlan;
import com.nongxinle.ai.dto.business.BusinessDiagnosisCompositeComposeResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * C-58：Composite Execution 结果（Harness / SHADOW）；不改变普通 {@code /api/ai/runs} legacy 终稿。
 * C-61：旁路耗时/对比观测（{@code compositeShadowLatencyMs} …）仅 SHADOW 且 {@link #executed} 时为 {@link AiRunService} 写入。
 * C-63：灰度跳过观测（{@code compositeShadowSkipped} …）在同一载体上写入，不要求 {@link #executed}。
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class BusinessDiagnosisCompositeExecutionResult {

    private boolean executed;
    private BusinessDiagnosisCompositeExecutionMode mode;
    private boolean success;
    private boolean fallbackRequired;
    private String fallbackReason;
    private String errorCode;
    private String errorMessage;
    private BusinessDiagnosisCompositeAnswerPlan businessDiagnosisCompositeAnswerPlan;
    private BusinessDiagnosisCompositeComposeResult composeResult;
    private PlannerExecutorTrace plannerExecutorTrace;
    private PlannerStepStatus plannerOverallStatus;

    @Builder.Default
    private List<String> degradedSteps = new ArrayList<>();

    /** C-61：旁路 wall-clock（ms），仅 SHADOW。 */
    private Long compositeShadowLatencyMs;

    /** C-61：legacy 与 composite 是否均有非空正文（便于并行对比）。 */
    private Boolean compositeShadowComparedWithLegacy;

    /** C-61：{@code AiRunState#finalAnswerText} 非空。 */
    private Boolean compositeShadowLegacyAnswerPresent;

    /** C-61：Composite Composer {@code finalAnswerText} 非空。 */
    private Boolean compositeShadowCompositeAnswerPresent;

    /** C-61：SHADOW 契约 — 恒 {@code false}（未替换用户正文）。 */
    private Boolean compositeShadowFinalAnswerReplaced;

    /** C-63：灰度闸门未进入 {@code tryExecute}（如 {@code shadow.enabled=false}、名单不符、节流）。 */
    private Boolean compositeShadowSkipped;

    private String compositeShadowSkipReason;

    private Boolean compositeShadowThrottleHit;

    private Boolean compositeShadowWhitelistMatched;
}
