package com.nongxinle.ai.planner;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.core.AiRunState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Planner 出库/核销真实读运行时上下文。可持有内嵌 {@link #plannerReadRequest}；
 * {@link StockReducePlannerReadRequest} <strong>不得</strong>反向引用本对象。
 *
 * <p>{@link StockReducePlannerRealReadBridge} 经本对象 {@code readWithExecutionContext} 读取；Spring 注入 Bean 时走
 * {@link com.nongxinle.ai.graph.business.StockReduceQueryToolExecutor}；Harness C-22 {@code new} 桥无依赖时仍骨架降级。</p>
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class StockReducePlannerExecutionContext {

    private AiRunState runState;
    private String runStateRef;

    private AiResolvedQueryContext resolvedQueryContext;
    private String resolvedQueryContextRef;

    private Long userId;
    private Long departmentId;
    private Long distributerId;

    private String conversationId;
    private String runId;

    private StockReducePlannerReadRequest plannerReadRequest;
}
