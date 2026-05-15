package com.nongxinle.ai.planner;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.core.AiRunState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Planner 菜品毛利真实读运行时上下文（C-26 骨架；后续 RealBridge Hydrated 使用）。可持有内嵌 {@link #plannerReadRequest}；
 * {@link DishProfitPlannerReadRequest} <strong>不得</strong>反向引用本对象。
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class DishProfitPlannerExecutionContext {

    private AiRunState runState;
    private String runStateRef;

    private AiResolvedQueryContext resolvedQueryContext;
    private String resolvedQueryContextRef;

    private Long userId;
    private Long departmentId;
    private Long distributerId;

    private String conversationId;
    private String runId;

    private DishProfitPlannerReadRequest plannerReadRequest;
}
