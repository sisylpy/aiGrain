package com.nongxinle.ai.planner;

/**
 * C-8：营收只读桥接。实现类应在此组装 {@code AiResolvedQueryContext} / Run 上下文并调用
 * {@code RevenueQueryToolExecutor} 等；<strong>禁止</strong>解析用户聊天原文、禁止直接拼接 SQL。
 * <p>输入输出边界：{@link RevenuePlannerReadRequest} / {@link RevenuePlannerReadResponse}；本轮默认无生产注入。</p>
 * <p>真实读实现 {@link RevenuePlannerRealReadBridge}：经 {@link PlannerRevenueExecutionContext} 走
 * {@link RevenuePlannerRealReadBridge#readWithExecutionContext}；{@code ReadRequest} 不含执行上下文回指。</p>
 */
@FunctionalInterface
public interface RevenuePlannerReadBridge {

    /**
     * @return 非 null；失败时应返回 {@link RevenuePlannerReadStatus#DEGRADED} 或 {@link RevenuePlannerReadStatus#FAILED}，
     * 不得用 {@link RevenuePlannerReadStatus#OK} 伪装查库成功。
     */
    RevenuePlannerReadResponse readRevenue(RevenuePlannerReadRequest request);
}
