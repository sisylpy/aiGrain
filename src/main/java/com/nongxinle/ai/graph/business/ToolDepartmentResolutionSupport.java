package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.security.AiRoleCodes;
import com.nongxinle.ai.scope.AiConversationScopeMode;
import com.nongxinle.ai.scope.AiQueryScope;
import com.nongxinle.ai.scope.AiScopeResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Tool 入参部门锚点解析（与 {@link BusinessToolExecutionNode} 历史逻辑一致）。
 */
@Component
@RequiredArgsConstructor
public class ToolDepartmentResolutionSupport {

    private final AiScopeResolver scopeResolver;

    /**
     * 门店采购端：子部门入参归一到门店根（{@code gb_department_father_id = 0}），再传给采购/核销工具。
     */
    public Long resolveToolDepartmentFatherId(AiRunState state, Long dept) {
        if (dept == null || state.getAiUserContext() == null) {
            return dept;
        }
        if (!AiRoleCodes.STORE_PURCHASER.equals(state.getAiUserContext().getRoleCode())
                && !AiRoleCodes.WAREHOUSE_MANAGER.equals(state.getAiUserContext().getRoleCode())
                && !AiRoleCodes.REGION_WAREHOUSE.equals(state.getAiUserContext().getRoleCode())
                && !AiRoleCodes.STORE_MANAGER.equals(state.getAiUserContext().getRoleCode())) {
            return dept;
        }
        int normalized = scopeResolver.resolveDomainStoreDepartmentId(dept.intValue());
        return (long) normalized;
    }

    /**
     * 菜品 insight / buildInsight 的 depFatherId 必须与 SQL 子部门展开属于同一门店根。
     */
    public Long resolveBuildInsightDepartmentFatherId(AiRunState state, Long deptFallback) {
        if (state == null) {
            return deptFallback;
        }
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        if (rq != null && rq.getOrgScope() != null) {
            AiResolvedOrgScope org = rq.getOrgScope();
            if (AiResolvedOrgScope.SCOPE_STORE.equals(org.getScopeType())) {
                if (org.getCurrentStoreDepartmentId() != null) {
                    return org.getCurrentStoreDepartmentId();
                }
                if (org.getVisibleStores() != null && !org.getVisibleStores().isEmpty()) {
                    AiStoreScopeDTO s0 = org.getVisibleStores().get(0);
                    if (s0 != null && s0.getStoreDepartmentId() != null) {
                        return s0.getStoreDepartmentId();
                    }
                }
            }
            if (org.getVisibleStores() != null && org.getVisibleStores().size() == 1) {
                AiStoreScopeDTO s0 = org.getVisibleStores().get(0);
                if (s0 != null && s0.getStoreDepartmentId() != null) {
                    return s0.getStoreDepartmentId();
                }
            }
        }
        AiQueryScope sc = state.getScope();
        if (sc != null && sc.getMode() == AiConversationScopeMode.STORE && sc.getDepartmentFatherId() != null) {
            return sc.getDepartmentFatherId();
        }
        return deptFallback;
    }
}
