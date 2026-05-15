package com.nongxinle.ai.agent.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Master / 子 Agent 编排请求上下文（阶段 A 骨架；未接入主链路）。
 * <p>
 * 语义已由 v2 + Resolver 落地；本对象仅承载调度与 Trace 所需字段。
 *
 * @see docs/ai/master-business-agent-design.md
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessAgentRequest {

    private Long runId;
    private Long conversationId;
    private Long userId;
    private Long distributerId;

    private AiResolvedQueryContext resolvedQueryContext;
    private AiQuerySemanticParseResult semanticResult;

    /**
     * Graph 执行期挂载：写入 {@link AiRunState#getToolResults()} 等；不向客户端序列化。
     */
    private AiRunState executionContext;

    /**
     * 四域 Harness MultiAgent：在满足 orchestration MULTI_GATE 且走 {@link MasterBusinessAgent#tryOrchestrateBusinessOverviewMultiAgent}
     * 时置 true；放行四个专线子 Agent 的 {@code supports}：<br>
     * 与 {@link #orchestratedSurfacePathCode} / {@link #harnessTargetDomainPathCode} 配合使用，
     * 子 Agent **不得**仅以 {@link #resolvedQueryContext} 的有效 path（可能为 {@code business_diagnosis_path}
     * 与各域 {@code *_overview_path} 混用场景）判别是否接待。
     */
    private boolean orchestratedBusinessOverviewMultiAgent;

    /**
     * Harness 图谱表面（与 {@link AiRunState#isBusinessOverviewPath()} / {@link AiRunState#isBusinessDiagnosisPath()}
     * 一致）：{@link com.nongxinle.ai.context.AiResolvedQueryIntent#PATH_BUSINESS_OVERVIEW} 或
     * {@link com.nongxinle.ai.context.AiResolvedQueryIntent#PATH_BUSINESS_DIAGNOSIS}。
     */
    private String orchestratedSurfaceIntentCode;
    private String orchestratedSurfacePathCode;

    /** 语义/解析侧「原版」effective intent/path，仅观测与对齐排障（可为 null）。 */
    private String orchestratedOriginalIntentCode;
    private String orchestratedOriginalPathCode;

    /** Harness 目的：{@code BUSINESS_OVERVIEW}（四域汇总看板）或 {@code BUSINESS_DIAGNOSIS}（证据型诊断拉数）。 */
    private String orchestratedPurposeIntentCode;

    /** 当前步子 Agent 的专线 intent/path；Master 在每次 {@code supports}/{@code execute} 前写入。 */
    private String harnessTargetDomainIntentCode;
    private String harnessTargetDomainPathCode;

    @Builder.Default
    private Map<String, Object> debugOptions = new LinkedHashMap<>();
}
