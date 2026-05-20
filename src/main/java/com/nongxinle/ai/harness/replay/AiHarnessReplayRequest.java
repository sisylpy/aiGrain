package com.nongxinle.ai.harness.replay;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * POST /api/ai/harness/replay 请求体。
 */
@Data
public class AiHarnessReplayRequest {

    /** 探索型 Replay：不写内置/自定义预期断言，仅输出摘要；优先级高于自定义 {@link #expectations} */
    public static final String CASE_ID_PROBE = "PROBE";
    /** 与 {@link #CASE_ID_PROBE} 等价：仅用请求体 {@link #messages} 跑 Replay，不加载内置 expectations */
    public static final String CASE_ID_AD_HOC = "AD_HOC";

    private Long userId;
    private Long departmentId;
    private Long distributerId;
    /** 可选，见 {@link com.nongxinle.ai.platform.dto.AiRunCreateRequest#getScopeMode()} */
    private String scopeMode;

    /**
     * 语义「今天」锚点，yyyy-MM-dd；不传则用当前 JVM 日期（断言不稳定）。
     * 内置用例 CASE1 文档以 2026-05-11 为锚点对齐表内区间。
     */
    private String frozenClockDate;

    /** 不传则仅用 messages replay、不做断言（或依赖 caseId 生成预期） */
    private String caseId;

    /**
     * 为 true：不执行任何预期断言（不采用自定义 {@link #expectations}），仅输出每轮解析与探针摘要；
     * 响应 {@link AiHarnessReplayResponse#getOverallPass()} 为 {@code null}，各轮附带 {@link AiHarnessReplayRoundResult#getProbe()}。
     */
    private boolean ignoreExpectations = false;

    /**
     * 可选：{@code RESOLVER_ONLY}（默认）、{@link AiHarnessReplayMode#GRAPH_RUN}、
     * {@link AiHarnessReplayMode#PLANNER_EXECUTOR_MOCK}（含 Composite strict C-35 / C-48 / C-42）。
     * 未传且 {@link #caseId} 为 {@link AiHarnessBuiltinCases#BUSINESS_DIAGNOSIS_V1_CORE_3}、
     * {@link AiHarnessBuiltinCases#BUSINESS_OVERVIEW_MULTI_AGENT_CORE_3}、或四个单域
     * {@code *_AGENT_GRAPH_CORE} 时，服务端默认 {@code GRAPH_RUN}；
     * {@link AiHarnessBuiltinCases#isPlannerExecutorMockHarnessCase(String)} 为 true 时入口短路为 PlannerExecutor DB-free
     * （PlannerExecutor Harness case 均推断 {@code PLANNER_EXECUTOR_MOCK}；P1-B Final 已摘除单域 Adapter 专用 replayMode）。
     */
    private String replayMode;

    /**
     * 可选 dry-run 阶段：{@link AiHarnessReplayDryRunStage#RESOLVED_CONTEXT_ONLY} 时，即使 {@code replayMode}
     * 为 {@link AiHarnessReplayMode#GRAPH_RUN} 也仅跑 Resolver + 摘要 + TurnMemory、不进同步业务图。
     * {@link AiHarnessBuiltinCases#BUSINESS_SEMANTIC_1B_RESOLVED_CONTEXT}、
     * {@link AiHarnessBuiltinCases#STOCK_REDUCE_SEMANTIC_1C_RESOLVED_CONTEXT} 在 {@code dryRunStage} 未传时，服务端会默认设为
     * {@link AiHarnessReplayDryRunStage#RESOLVED_CONTEXT_ONLY}。
     * {@link AiHarnessReplayDryRunStage#FULL} 与 {@code null}（未传）等价：不强制缩短，行为与改动前一致。
     */
    private AiHarnessReplayDryRunStage dryRunStage;

    /** 自定义预期，长度应与 messages 相同；优先级高于 {@link #caseId} */
    private List<AiHarnessReplayExpectedRound> expectations = new ArrayList<>();

    /**
     * 多轮用户问句。显式非空时直接使用；若为空且 {@link #caseId} 为带固定问句的内置 case，由
     * {@link AiHarnessReplayService} 调用 {@link AiHarnessBuiltinCases#builtinMessagesForCaseIdOrNull(String)} 补全。
     */
    private List<String> messages = new ArrayList<>();

    /**
     * true：对 visibleStoreRootIds / effectiveSqlDepartmentIds 做强校验（与环境部门树相关，易因库数据不同失败）。
     * false：跳过这两项（仍可验 intent/time/purchase）。
     */
    private boolean strictStoreSqlMatch = true;

    /**
     * C-56.2：仅 Harness {@link AiHarnessReplayMode#GRAPH_RUN} — 覆盖传入
     * {@link com.nongxinle.ai.planner.BusinessDiagnosisCompositeProductionGate#evaluate} 第三参数；
     * {@code null} 表示沿用 Spring {@code ai.composite.businessDiagnosis.productionEnabled}。
     * 禁止在普通 {@code /api/ai/runs} 使用。
     */
    private Boolean compositeProductionGateProductionEnabledOverride;

    /**
     * C-58：仅 Harness {@link AiHarnessReplayMode#GRAPH_RUN} — Composite Execution 模式；
     * {@code OFF} / {@code HARNESS_ONLY} / {@code SHADOW} / {@code PRIMARY}（后两者 C-58 不接行为）。
     * {@code null} 或未识别 → {@link com.nongxinle.ai.planner.BusinessDiagnosisCompositeExecutionMode#OFF}。
     * 禁止在普通 {@code /api/ai/runs} 使用。
     */
    private String compositeBusinessDiagnosisExecutionMode;

    /** 服务端用于跳过内置 assertions：{@link #CASE_ID_PROBE} / {@link #CASE_ID_AD_HOC}（大小写不敏感） */
    public static boolean isBuiltinProbeCaseId(String caseIdTrimmed) {
        if (caseIdTrimmed == null || caseIdTrimmed.isEmpty()) {
            return false;
        }
        String u = caseIdTrimmed.trim();
        return CASE_ID_PROBE.equalsIgnoreCase(u) || CASE_ID_AD_HOC.equalsIgnoreCase(u);
    }
}
