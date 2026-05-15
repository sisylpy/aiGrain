package com.nongxinle.ai.planner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 营收只读桥接入参（C-8）。字段须来自已解析上下文及编排注入；<strong>不得</strong>包含用户聊天原文。
 *
 * @see RevenuePlannerReadBridge
 * @see RevenuePlannerAgentAdapter
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class RevenuePlannerReadRequest {

    /**
     * 与 {@code AiResolvedQueryContext} 快照 id / hash 对齐（必填，由 adapter 校验）。
     */
    private String resolvedQueryContextRef;

    /** 区间起（与 {@link #timeEnd} 成对使用）。 */
    private LocalDate timeStart;

    /** 区间止（与 {@link #timeStart} 成对使用）。 */
    private LocalDate timeEnd;

    /**
     * 可选人类可读时间标签（与 Resolver 口径一致）；若与起止二选一满足，
     * adapter 接受「成对起止」或「非空 {@code timeLabel}」作为时间充分条件。
     */
    private String timeLabel;

    /**
     * 范围类型（如 {@code STORE} / {@code GROUP}），与现有 scope 枚举字符串对齐即可，本包不依赖 Resolver 类型。
     */
    private String scopeType;

    @Builder.Default
    private List<RevenuePlannerVisibleStore> visibleStores = new ArrayList<>();

    @Builder.Default
    private List<Long> queryDepartmentIds = new ArrayList<>();

    private Long targetStoreDepartmentId;

    /** 可选：前序 AnswerPlan / 子计划句柄。 */
    private String answerPlanRef;
}
