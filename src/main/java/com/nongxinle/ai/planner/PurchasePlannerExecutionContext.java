package com.nongxinle.ai.planner;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.core.AiRunState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Planner 采购真实读运行时上下文（C-16/C-17）。可持有内嵌 {@link #plannerReadRequest}（「查什么」切片）；
 * {@link PurchasePlannerReadRequest} <strong>不得</strong>反向引用本对象。
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class PurchasePlannerExecutionContext {

    private AiRunState runState;
    private String runStateRef;

    private AiResolvedQueryContext resolvedQueryContext;
    private String resolvedQueryContextRef;

    private Long userId;
    private Long departmentId;
    private Long distributerId;

    private String conversationId;
    private String runId;

    private PurchasePlannerReadRequest plannerReadRequest;
}
