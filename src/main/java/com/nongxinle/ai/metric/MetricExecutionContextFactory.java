package com.nongxinle.ai.metric;

import com.nongxinle.ai.scope.AiQueryScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 从 {@link AiMetricCatalog} 与 {@link AiQueryScope} 组装 {@link MetricExecutionContext}。
 */
@Component
@RequiredArgsConstructor
public class MetricExecutionContextFactory {

    private final AiMetricCatalog catalog;
    private final MetricDepartmentScopeResolver departmentScopeResolver;

    public MetricExecutionContext build(String metricId, AiQueryScope sessionScope, Integer userAnchorDepartmentId) {
        return build(metricId, sessionScope, userAnchorDepartmentId, null);
    }

    public MetricExecutionContext build(String metricId, AiQueryScope sessionScope, Integer userAnchorDepartmentId,
                                       Map<Integer, List<Integer>> subtreeCache) {
        MetricDefinition metric = catalog.find(metricId).orElse(null);
        if (metric == null) {
            return MetricExecutionContext.builder()
                    .metric(null)
                    .sessionScope(sessionScope)
                    .userAnchorDepartmentId(userAnchorDepartmentId)
                    .effectiveDepartmentIds(sessionScope.getResolvedDepartmentIds())
                    .timeRange(MetricTimeWindows.resolve(sessionScope, null))
                    .build();
        }
        List<Integer> deptIds = departmentScopeResolver.resolveDepartmentIdsForMetric(
                sessionScope,
                metric.getDepartmentScope(),
                userAnchorDepartmentId,
                subtreeCache);
        return MetricExecutionContext.builder()
                .metric(metric)
                .sessionScope(sessionScope)
                .userAnchorDepartmentId(userAnchorDepartmentId)
                .effectiveDepartmentIds(deptIds)
                .timeRange(MetricTimeWindows.resolve(sessionScope, metric))
                .build();
    }
}
