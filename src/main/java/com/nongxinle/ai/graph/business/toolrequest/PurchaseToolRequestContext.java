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
 * 采购 {@link com.nongxinle.ai.tool.business.AiBusinessToolIds#PURCHASE_OVERVIEW} 专线上下文快照：
 * 仅来自 {@link com.nongxinle.ai.context.AiResolvedQueryContext} / {@link com.nongxinle.ai.core.AiRunState} 结构化字段。
 * <p>
 * {@link #visibleStoreRootDepartmentIds}：门店锚点候选（orgScope.visibleStores）；<br>
 * {@link #purchaseSqlDepartmentIds} / {@link #effectiveSqlDepartmentIds}：SQL IN 展开视图（dataScope）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseToolRequestContext {

    private String startDateIso;
    /** 与 Tool ARG_STOP_DATE 对齐（语义等同 endDate） */
    private String endDateIso;

    private Long departmentFatherIdForScopedTools;
    private Long departmentFatherIdForBuildInsight;

    /** dataScope 采购域 SQL IN 列表视图（通常与 effectiveSqlDepartmentIds 同源） */
    @Builder.Default
    private List<Long> purchaseSqlDepartmentIds = new ArrayList<>();

    /** {@link com.nongxinle.ai.context.AiResolvedDataScope#getEffectiveSqlDepartmentIds()} */
    @Builder.Default
    private List<Long> effectiveSqlDepartmentIds = new ArrayList<>();

    /** {@link com.nongxinle.ai.context.AiResolvedDataScope#getVisibleStoreRootIds()} */
    @Builder.Default
    private List<Long> visibleStoreRootIds = new ArrayList<>();

    /** orgScope.visibleStores 的门店 department id（整型）；锚点/展示口径 */
    @Builder.Default
    private List<Integer> visibleStoreRootDepartmentIds = new ArrayList<>();

    private String purchaseSourceType;
    private String structuredIntentDetail;

    /** {@link com.nongxinle.ai.context.AiResolvedOrgScope#getScopeType()} */
    private String orgScopeType;
    /** {@link com.nongxinle.ai.context.AiResolvedDataScope#getQueryScopeKind()} */
    private String queryScopeKind;

    @Builder.Default
    private Map<String, Object> resolutionDebug = new LinkedHashMap<>();
}
