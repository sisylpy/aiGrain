package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiUserContext;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.graph.business.execution.ToolRequestContractExecutionParamSupport;
import com.nongxinle.ai.mapping.AiRoleMapper;
import com.nongxinle.ai.semantic.frame.ContractLockedSemanticFrame;
import com.nongxinle.ai.semantic.intake.grounding.CoverDaysSalesBaselineTimeSupport;
import com.nongxinle.ai.semantic.matrix.DishCostAnalysisSemanticCapabilityMatrix;
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
        AiResolvedQueryContext rqCtx = state != null ? state.getResolvedQueryContext() : null;
        boolean lockedCoverDays = isLockedDishIngredientCoverDaysContract(rqCtx);
        if (!lockedCoverDays && ToolRequestContractExecutionParamSupport.isDishIngredientCoverDaysContract(rqCtx)) {
            throw new IllegalStateException("dish.ingredient_cover_days.v1 requires ContractLockedSemanticFrame");
        }
        if (lockedCoverDays) {
            CoverDaysSalesBaselineTimeSupport.DualTimePlan dualTime =
                    CoverDaysSalesBaselineTimeSupport.resolveDualTimePlan(
                            rqCtx != null ? rqCtx.getContractLockedFrame() : null, null);
            if (dualTime == null || dualTime.baseline() == null) {
                throw new IllegalStateException("missing cover-days locked dual time plan");
            }
            if (dualTime != null && dualTime.baseline() != null
                    && StringUtils.hasText(dualTime.baseline().getStartDateIso())
                    && StringUtils.hasText(dualTime.baseline().getStopDateIso())) {
                start = dualTime.baseline().getStartDateIso();
                stop = dualTime.baseline().getStopDateIso();
            }
            if (StringUtils.hasText(dualTime.stockAsOfDate())) {
                m.put(AiBusinessToolIds.ARG_STOCK_AS_OF_DATE, dualTime.stockAsOfDate());
            }
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

        String dishFocus =
                lockedCoverDays
                        ? dishNameFromLockedFrame(rqCtx != null ? rqCtx.getContractLockedFrame() : null)
                        : ToolRequestContractExecutionParamSupport.resolveDishNameFocusHint(rqCtx);
        if (StringUtils.hasText(dishFocus)) {
            m.put(AiBusinessToolIds.ARG_DISH_NAME_FOCUS_HINT, dishFocus.trim());
        }
        if (!lockedCoverDays) {
            Integer foodId = ToolRequestContractExecutionParamSupport.resolveDishFoodIdFromContract(rqCtx);
            if (foodId != null) {
                m.put(AiBusinessToolIds.ARG_DISH_COST_FOOD_ID, foodId);
            }
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

    private static boolean isLockedDishIngredientCoverDaysContract(AiResolvedQueryContext rqCtx) {
        ContractLockedSemanticFrame frame = rqCtx != null ? rqCtx.getContractLockedFrame() : null;
        return frame != null
                && frame.getContractFields() != null
                && DishCostAnalysisSemanticCapabilityMatrix.CONTRACT_DISH_INGREDIENT_COVER_DAYS.equals(
                        frame.getContractFields().getSelectedContractId());
    }

    private static String dishNameFromLockedFrame(ContractLockedSemanticFrame frame) {
        if (frame == null || frame.getEntitySlots() == null) {
            return null;
        }
        return frame.getEntitySlots().getMentionedDishName();
    }
}
