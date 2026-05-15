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
 * 营收 {@link com.nongxinle.ai.tool.business.AiBusinessToolIds#REVENUE_QUERY} 入参解析结果：
 * 仅消费 {@link com.nongxinle.ai.context.AiResolvedQueryContext} / {@link com.nongxinle.ai.core.AiRunState}
 * 已落地的结构化字段，不重读用户原文。
 * <p>
 * 命名约定：Wire 层沿用 Tool 常量 {@code ARG_START_DATE} / {@code ARG_STOP_DATE}；
 * {@link #stopDateIso} 与解析上下文 {@code timeWindow.endDate} 语义一致（即业务上的 endDate）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueToolRequestResolution {

    /** yyyy-MM-dd → {@code ARG_START_DATE} */
    private String startDateIso;

    /** yyyy-MM-dd → {@code ARG_STOP_DATE}（等同 endDate） */
    private String stopDateIso;

    /**
     * 经 {@link com.nongxinle.ai.graph.business.ToolDepartmentResolutionSupport#resolveToolDepartmentFatherId}
     * 后的门店根 / 部门锚点，供 REVENUE scoped 工具链路与 {@link com.nongxinle.ai.graph.business.RevenueQueryToolExecutor}。
     */
    private Long departmentFatherIdForScopedTools;

    /**
     * 经 {@link com.nongxinle.ai.graph.business.ToolDepartmentResolutionSupport#resolveBuildInsightDepartmentFatherId}
     * 后的 insight/buildInsight 对齐锚点。
     */
    private Long departmentFatherIdForBuildInsight;

    /** 与 {@link com.nongxinle.ai.context.AiResolvedOrgScope#getVisibleStores()} 一致的门店根 department id（整型列表）。 */
    @Builder.Default
    private List<Integer> visibleStoreRootDepartmentIds = new ArrayList<>();

    /** {@link com.nongxinle.ai.context.AiResolvedDataScope#getEffectiveSqlDepartmentIds()} 快照（营收 Tool 不直接拼 SQL，仅供观测与后续扩展）。 */
    @Builder.Default
    private List<Long> expandedSqlDepartmentIds = new ArrayList<>();

    /** {@link com.nongxinle.ai.context.AiResolvedDataScope#getVisibleStoreRootIds()} 快照。 */
    @Builder.Default
    private List<Long> visibleStoreRootIds = new ArrayList<>();

    /**
     * 可并入 Agent/Master debug：时间来源、部门锚点回落原因等（无用户原文、无 regex）。
     */
    @Builder.Default
    private Map<String, Object> resolutionDebug = new LinkedHashMap<>();
}
