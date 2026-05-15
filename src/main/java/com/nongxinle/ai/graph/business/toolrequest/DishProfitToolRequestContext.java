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
 * 菜品毛利专线 {@link com.nongxinle.ai.tool.business.AiBusinessToolIds#DISH_PROFIT_ANALYSIS}：
 * 时间窗、部门锚点、展开 SQL / 门店根、结构化口径与观测 debug（只读 {@link com.nongxinle.ai.context.AiResolvedQueryContext}）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DishProfitToolRequestContext {

    private String startDateIso;
    private String endDateIso;
    private String stopDateIso;

    /** 与 {@link com.nongxinle.ai.context.AiResolvedDataScope#getEffectiveSqlDepartmentIds()} 对齐（Tool IN 列表）。 */
    @Builder.Default
    private List<Long> effectiveSqlDepartmentIds = new ArrayList<>();

    /** 菜品域标记（当前与 effective 同源，便于观测）。 */
    @Builder.Default
    private List<Long> dishProfitSqlDepartmentIds = new ArrayList<>();

    @Builder.Default
    private List<Long> visibleStoreRootIds = new ArrayList<>();

    @Builder.Default
    private List<Integer> visibleStoreRootDepartmentIds = new ArrayList<>();

    private Long departmentFatherIdForScopedTools;
    private Long departmentFatherIdForBuildInsight;

    private String mentionedDishName;
    private String structuredIntentDetail;
    private String dishProfitMetricType;

    private String orgScopeType;
    private String queryScopeKind;

    @Builder.Default
    private Map<String, Object> resolutionDebug = new LinkedHashMap<>();
}
