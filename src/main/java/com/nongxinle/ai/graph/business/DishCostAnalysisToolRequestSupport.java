package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiUserContext;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.graph.business.execution.ToolRequestContractExecutionParamSupport;
import com.nongxinle.ai.mapping.AiRoleMapper;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.nongxinle.constants.AiInsightDishProfitScope;
import com.nongxinle.service.GbAiDailyRevenueService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link AiBusinessToolIds#DISH_COST_ANALYSIS} ToolRequest 参数：时间/范围来自 {@link AiResolvedQueryContext}，
 * 菜名/foodId 来自 contract-locked semantic slots / anchor。
 */
@Component
public class DishCostAnalysisToolRequestSupport {

    private final GbAiDailyRevenueService gbAiDailyRevenueService;

    public DishCostAnalysisToolRequestSupport(GbAiDailyRevenueService gbAiDailyRevenueService) {
        this.gbAiDailyRevenueService = gbAiDailyRevenueService;
    }

    public Map<String, Object> buildDishCostAnalysisToolArgs(
            Long deptForScopedTools,
            Long deptForBuildInsight,
            Long dis,
            String start,
            String stop,
            AiRunState state) {
        String toolId = AiBusinessToolIds.DISH_COST_ANALYSIS;
        Map<String, Object> m = new LinkedHashMap<>(16);
        Long dept =
                BusinessToolExecutionNode.departmentIdArgumentForToolPublic(
                        toolId, deptForScopedTools, deptForBuildInsight);

        if (dis != null) {
            m.put(AiBusinessToolIds.ARG_DIS_ID, dis);
        }
        if (BusinessToolExecutionNode.shouldRouteGroupWideDishInsight(state)) {
            m.put(
                    AiBusinessToolIds.ARG_DEPARTMENT_FATHER_ID,
                    (long) AiInsightDishProfitScope.DEP_FATHER_ID_GROUP_WIDE_Mendian_AGGREGATE_UNDER_DIS_ID);
        } else if (dept != null) {
            m.put(AiBusinessToolIds.ARG_DEPARTMENT_FATHER_ID, dept);
        }
        if (start != null) {
            m.put(AiBusinessToolIds.ARG_START_DATE, start);
        }
        if (stop != null) {
            m.put(AiBusinessToolIds.ARG_STOP_DATE, stop);
            m.put(AiBusinessToolIds.ARG_END_DATE, stop);
        }

        m.put(AiBusinessToolIds.ARG_SORT_BY, "sales");
        m.put(AiBusinessToolIds.ARG_SORT_ORDER, "desc");
        m.put(AiBusinessToolIds.ARG_SEARCH_DEP_ID, "-1");

        boolean dishGroup = BusinessToolExecutionNode.shouldRouteGroupWideDishInsight(state);
        List<Integer> visibleStoreRoots =
                dishGroup
                        ? BusinessToolExecutionNode.extractVisibleStoreDepartmentIds(
                                state != null ? state.getResolvedQueryContext() : null)
                        : List.of();
        if (dishGroup && !visibleStoreRoots.isEmpty()) {
            List<Integer> expandedForSql =
                    gbAiDailyRevenueService.expandStoreRootsToDailyRevenueScopeIds(visibleStoreRoots);
            m.put(AiBusinessToolIds.ARG_RESOLVED_DEPARTMENT_IDS, new ArrayList<>(expandedForSql));
            m.put(AiBusinessToolIds.ARG_PARENT_STORE_COUNT, visibleStoreRoots.size());
        } else {
            List<Integer> fromResolvedCtx =
                    BusinessToolExecutionNode.extractSqlQueryDepartmentIdsForTools(
                            state != null ? state.getResolvedQueryContext() : null);
            if (!fromResolvedCtx.isEmpty()) {
                m.put(AiBusinessToolIds.ARG_RESOLVED_DEPARTMENT_IDS, new ArrayList<>(fromResolvedCtx));
            }
        }

        AiResolvedQueryContext rqCtx = state != null ? state.getResolvedQueryContext() : null;
        String dishFocus = ToolRequestContractExecutionParamSupport.resolveDishNameFocusHint(rqCtx);
        if (StringUtils.hasText(dishFocus)) {
            m.put(AiBusinessToolIds.ARG_DISH_NAME_FOCUS_HINT, dishFocus.trim());
        }
        Integer foodId = ToolRequestContractExecutionParamSupport.resolveDishFoodIdFromContract(rqCtx);
        if (foodId != null) {
            m.put(AiBusinessToolIds.ARG_DISH_COST_FOOD_ID, foodId);
        }

        AiUserContext ctxSnap = state != null ? state.getAiUserContext() : null;
        if (ctxSnap != null) {
            String roleTag = ctxSnap.getRoleCode();
            if ((roleTag == null || roleTag.isBlank()) && ctxSnap.getSourceAdminRole() != null) {
                roleTag = AiRoleMapper.resolveAdmin(ctxSnap.getSourceAdminRole())
                        .map(AiRoleMapper.AiRoleDefinition::roleCode)
                        .orElse(null);
            }
            if (roleTag != null && !roleTag.isBlank()) {
                m.put(AiBusinessToolIds.ARG_AI_ROLE_CODE, roleTag);
            }
        }
        return m;
    }
}
