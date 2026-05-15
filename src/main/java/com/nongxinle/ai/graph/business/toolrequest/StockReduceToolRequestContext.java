package com.nongxinle.ai.graph.business.toolrequest;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 出库/核销专线 {@link com.nongxinle.ai.tool.business.AiBusinessToolIds#STOCK_REDUCE_QUERY}：
 * 仅来自 {@link com.nongxinle.ai.context.AiResolvedQueryContext} / {@link com.nongxinle.ai.core.AiRunState} 结构化字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockReduceToolRequestContext {

    private String startDateIso;
    /** 与 Tool ARG_STOP_DATE 对齐（语义等同 endDate） */
    private String endDateIso;

    private Long departmentFatherIdForScopedTools;
    private Long departmentFatherIdForBuildInsight;

    /** {@link com.nongxinle.ai.context.AiResolvedDataScope#getSqlDepartmentIdsForDomain(String)} {@code stock_reduce} */
    @Builder.Default
    private List<Long> stockReduceSqlDepartmentIds = new ArrayList<>();

    @Builder.Default
    private List<Long> effectiveSqlDepartmentIds = new ArrayList<>();

    @Builder.Default
    private List<Long> visibleStoreRootIds = new ArrayList<>();

    @Builder.Default
    private List<Integer> visibleStoreRootDepartmentIds = new ArrayList<>();

    private String structuredIntentDetail;

    /** {@link com.nongxinle.ai.semantic.AiQuerySemanticParseResult.MetricPart#getStockReduceType()}（可为 null） */
    private String stockReduceType;

    /** {@link com.nongxinle.ai.context.AiResolvedOrgScope#getScopeType()} */
    private String orgScopeType;

    /** {@link com.nongxinle.ai.context.AiResolvedDataScope#getQueryScopeKind()} */
    private String queryScopeKind;

    @Builder.Default
    private Map<String, Object> resolutionDebug = new LinkedHashMap<>();
}
