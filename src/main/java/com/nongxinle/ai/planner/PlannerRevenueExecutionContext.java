package com.nongxinle.ai.planner;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.core.AiRunState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Planner 营收真实读链路的<strong>执行上下文边界</strong>（C-11）。
 * <p>
 * 生产侧执行 {@link com.nongxinle.ai.tool.business.AiBusinessToolIds#REVENUE_QUERY} 时，必须依托
 * {@link AiRunState} 与 {@link AiResolvedQueryContext}（已由解析层产出），<strong>不得</strong>在本包内
 * 解析用户原文、<strong>不得</strong>拼凑 SQL。{@link RevenuePlannerReadRequest} 仅承载计划级切片
 * （时间标签、scope 摘要等），<strong>不能替代</strong>已解析的运行态与 ResolvedQueryContext。
 * </p>
 * <p>
 * {@code runStateRef} / {@code resolvedQueryContextRef} 供会话外或持久化恢复使用；C-11 骨架仅接受
 * <strong>已物化</strong>的 {@link AiRunState} / {@link AiResolvedQueryContext}，仅有 ref 而未 Hydrate
 * 时由 {@link RevenuePlannerRealReadBridge} 视为缺失并降级。
 * </p>
 *
 * @see RevenuePlannerRealReadBridge
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class PlannerRevenueExecutionContext {

    /** 当前运行态；真实 Tool 写入 target。与 {@link #runStateRef} 二选一以物化对象为准。 */
    private AiRunState runState;

    /**
     * 运行态外部引用（例如会话存储 key）；C-11 未 Hydrate 时等同「无 runState」，
     * {@link RevenuePlannerRealReadBridge} 返回 {@code ADAPTER_NO_RUN_STATE}。
     */
    private String runStateRef;

    /** 已解析查询上下文（时间窗、scope、可见门店等）；不得为 null 才允许进入 C-12+ 执行。 */
    private AiResolvedQueryContext resolvedQueryContext;

    /**
     * ResolvedQueryContext 外部引用；仅有 ref 而无 {@link #resolvedQueryContext} 时降级
     * {@code ADAPTER_NO_RESOLVED_CONTEXT}。
     */
    private String resolvedQueryContextRef;

    /** 登录或作用域用户；可与 {@link AiRunState#getUserId()} 冗余，便于审计与 Hydrate 前校验。 */
    private Long userId;

    /** 部门锚点；可与 state 冗余。 */
    private Long departmentId;

    private Long distributerId;

    private String conversationId;

    /** 与 {@link com.nongxinle.ai.graph.business.RevenueQueryToolExecutor#executeRevenueQuery} 的 runId 对齐。 */
    private String runId;

    /**
     * 计划级营收读请求（Adapter / Harness 契约）；与 state、resolvedContext 并存，
     * 不单独作为 Tool 入参来源。
     */
    private RevenuePlannerReadRequest plannerReadRequest;
}
