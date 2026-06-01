package com.nongxinle.ai.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolve / ScopePreparation / ScopeIntersect 全链路观测：定位 request GROUP 何时落成 STORE。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScopeResolutionTrace {

    private String requestScopeMode;
    private String conversationScopeModeParam;
    private String rawScopeAction;
    private String effectiveScopeModeBeforeResolveOrg;
    private String baselineOrgScopeType;
    @Builder.Default
    private List<String> baselineVisibleStores = new ArrayList<>();
    private String mergedOrgScopeTypeBeforePreparation;
    private String mergedOrgScopeTypeAfterPreparation;
    private Boolean multiTurnInherited;
    private Boolean semanticNarrowingApplied;
    private String dataScopeInputScopeType;
    private Integer queryDistributerId;
    @Builder.Default
    private List<Integer> queryStoreIds = new ArrayList<>();
    @Builder.Default
    private List<Integer> expandedSqlDepartmentIds = new ArrayList<>();
    private String explicitGroupRequest;
    private Boolean groupToStoreNarrowingAllowed;
    private String baselineOrgSource;
    private String postIntersectOrgScopeType;
    private String scopeIntersectPath;

    public static List<String> visibleStoreNames(AiResolvedOrgScope org) {
        List<String> names = new ArrayList<>();
        if (org == null || org.getVisibleStores() == null) {
            return names;
        }
        for (AiStoreScopeDTO s : org.getVisibleStores()) {
            if (s != null && s.getStoreName() != null && !s.getStoreName().isBlank()) {
                names.add(s.getStoreName().trim());
            } else if (s != null && s.getStoreDepartmentId() != null) {
                names.add("dept:" + s.getStoreDepartmentId());
            }
        }
        return names;
    }

    public void snapshotBaseline(AiResolvedOrgScope org, String source) {
        baselineOrgSource = source;
        if (org == null) {
            baselineOrgScopeType = null;
            baselineVisibleStores = new ArrayList<>();
            return;
        }
        baselineOrgScopeType = org.getScopeType();
        baselineVisibleStores = visibleStoreNames(org);
    }

    public void snapshotAfterPreparation(AiResolvedOrgScope mergedOrg, boolean inherited, boolean semanticNarrowing) {
        multiTurnInherited = inherited;
        semanticNarrowingApplied = semanticNarrowing;
        mergedOrgScopeTypeAfterPreparation =
                mergedOrg != null ? mergedOrg.getScopeType() : null;
    }

    public void snapshotDataScope(AiResolvedOrgScope org, AiResolvedDataScope ds) {
        dataScopeInputScopeType = org != null ? org.getScopeType() : null;
        if (ds == null) {
            queryDistributerId = null;
            queryStoreIds = new ArrayList<>();
            expandedSqlDepartmentIds = new ArrayList<>();
            return;
        }
        queryDistributerId = ds.getQueryDistributerId();
        queryStoreIds =
                ds.getQueryStoreIds() == null ? new ArrayList<>() : new ArrayList<>(ds.getQueryStoreIds());
        expandedSqlDepartmentIds =
                ds.getExpandedSqlDepartmentIds() == null
                        ? new ArrayList<>()
                        : new ArrayList<>(ds.getExpandedSqlDepartmentIds());
    }
}
