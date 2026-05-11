package com.nongxinle.ai.security;

import com.nongxinle.ai.context.AiOrgScope;
import com.nongxinle.ai.context.AiUserContext;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.mapping.AiRoleMapper;
import com.nongxinle.ai.scope.AiQueryScope;
import com.nongxinle.ai.graph.business.CostInsightIntentConvergence;
import com.nongxinle.ai.tool.ToolRequest;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Objects;

/**
 * Tool / 导出前鉴权；无上下文时放行（兼容仅构造 {@link AiRunState} 的单测）。
 */
@Component
public class AiPermissionGuard {

    private static final String AGENT_COST_DIAGNOSIS = "CostDiagnosisAgent";

    public boolean canInvokeTool(AiRunState state, ToolRequest request) {
        return evaluateToolInvocation(state, request).isAllowed();
    }

    public AiToolInvocationDecision evaluateToolInvocation(AiRunState state, ToolRequest request) {
        if (state == null || request == null) {
            return AiToolInvocationDecision.allow();
        }
        if (state.getAiUserContext() == null) {
            return AiToolInvocationDecision.allow();
        }
        String toolId = request.getToolName();
        if (AiBusinessToolIds.DISH_PROFIT_ANALYSIS.equals(toolId)) {
            return evaluateDishProfitAnalysisInvocation(state, request);
        }
        if (AiBusinessToolIds.PURCHASE_OVERVIEW.equals(toolId)) {
            AiUserContext ctx = state.getAiUserContext();
            boolean okPur = hasPermission(ctx, AiPermissions.VIEW_PURCHASE);
            boolean okWarehouseReceipt = (AiRoleCodes.WAREHOUSE_MANAGER.equals(ctx.getRoleCode())
                    || AiRoleCodes.REGION_WAREHOUSE.equals(ctx.getRoleCode()))
                    && hasPermission(ctx, AiPermissions.VIEW_STOCK);
            if (!okPur && !okWarehouseReceipt) {
                return AiToolInvocationDecision.deny(
                        AiAnswerBoundary.forMissingToolPermission(toolId, AiPermissions.VIEW_PURCHASE));
            }
            if (!requestWithinOrgScope(state)) {
                return AiToolInvocationDecision.deny(AiAnswerBoundary.forOrgScopeViolation(toolId));
            }
            return AiToolInvocationDecision.allow();
        }
        String required = requiredPermissionForTool(toolId);
        if (required == null) {
            if (!requestWithinOrgScope(state)) {
                return AiToolInvocationDecision.deny(
                        AiAnswerBoundary.forOrgScopeViolation(toolId == null ? "tool" : toolId));
            }
            return AiToolInvocationDecision.allow();
        }
        AiUserContext ctx = state.getAiUserContext();
        if (!hasPermission(ctx, required)) {
            return AiToolInvocationDecision.deny(AiAnswerBoundary.forMissingToolPermission(toolId, required));
        }
        if (!requestWithinOrgScope(state)) {
            return AiToolInvocationDecision.deny(AiAnswerBoundary.forOrgScopeViolation(toolId));
        }
        return AiToolInvocationDecision.allow();
    }

    public AiToolInvocationDecision evaluateCostDiagnosisAgent(AiRunState state) {
        if (state == null || state.getAiUserContext() == null) {
            return AiToolInvocationDecision.allow();
        }
        String required = AiPermissions.VIEW_COST;
        if (!hasPermission(state.getAiUserContext(), required)) {
            return AiToolInvocationDecision.deny(
                    AiAnswerBoundary.forCostDiagnosisAgent(
                            required,
                            "你当前账号没有查看成本/毛利结构化分析的权限，已跳过成本诊断。"));
        }
        if (!requestWithinOrgScope(state)) {
            return AiToolInvocationDecision.deny(
                    AiAnswerBoundary.forOrgScopeViolation(AGENT_COST_DIAGNOSIS));
        }
        return AiToolInvocationDecision.allow();
    }

    private AiToolInvocationDecision evaluateDishProfitAnalysisInvocation(AiRunState state, ToolRequest request) {
        AiUserContext ctx = state.getAiUserContext();
        String rc = ctx.getRoleCode();
        if (CostInsightIntentConvergence.isProcurementCostConvergenceRole(rc)) {
            return AiToolInvocationDecision.deny(AiAnswerBoundary.forDishProfitPurchaserDenied());
        }
        if (AiRoleCodes.WAREHOUSE_MANAGER.equals(rc) || AiRoleCodes.REGION_WAREHOUSE.equals(rc)) {
            return AiToolInvocationDecision.deny(AiAnswerBoundary.forDishProfitWarehouseDenied());
        }
        if (AiRoleCodes.DELIVERY_SUPPLIER.equals(rc) || AiRoleCodes.DELIVERY_DRIVER.equals(rc)
                || AiRoleCodes.COUPON_OPERATOR.equals(rc)) {
            return AiToolInvocationDecision.deny(AiAnswerBoundary.forDishProfitUnsupportedRoleDenied());
        }
        if (!hasPermission(ctx, AiPermissions.VIEW_DISH_SALES)) {
            return AiToolInvocationDecision.deny(AiAnswerBoundary.forMissingToolPermission(request.getToolName(),
                    AiPermissions.VIEW_DISH_SALES));
        }
        if (!hasPermission(ctx, AiPermissions.VIEW_COST)) {
            return AiToolInvocationDecision.deny(AiAnswerBoundary.forMissingToolPermission(request.getToolName(),
                    AiPermissions.VIEW_COST));
        }
        if (!requestWithinOrgScope(state)) {
            return AiToolInvocationDecision.deny(AiAnswerBoundary.forOrgScopeViolation(request.getToolName()));
        }
        return AiToolInvocationDecision.allow();
    }

    private static boolean hasPermission(AiUserContext ctx, String required) {
        if (ctx.getPermissions() == null) {
            return false;
        }
        return ctx.getPermissions().contains(required);
    }

    private static boolean requestWithinOrgScope(AiRunState state) {
        AiUserContext ctx = state.getAiUserContext();
        AiOrgScope scope = state.getAiOrgScope();
        if (ctx == null || scope == null) {
            return true;
        }
        String role = ctx.getRoleCode();
        if (AiRoleMapper.isGroupWideOrgScope(role)) {
            return true;
        }
        Long reqDept = state.getDepartmentId();
        Long reqDis = state.getDistributerId();

        Long anchorDept = scope.getDepartmentId();
        if (anchorDept != null && reqDept != null && !Objects.equals(anchorDept, reqDept)) {
            return false;
        }
        if (scope.getDistributerId() != null && reqDis != null
                && !Objects.equals(scope.getDistributerId(), reqDis)) {
            return false;
        }
        List<Long> allowed = scope.getStoreIds();
        if (!CollectionUtils.isEmpty(allowed) && reqDept != null && !allowed.contains(reqDept)) {
            return false;
        }
        return true;
    }

    static String requiredPermissionForTool(String toolId) {
        if (toolId == null) {
            return null;
        }
        return switch (toolId) {
            case AiBusinessToolIds.REVENUE_QUERY -> AiPermissions.VIEW_REVENUE;
            case AiBusinessToolIds.BUSINESS_OVERVIEW_QUERY -> AiPermissions.VIEW_REVENUE;
            case AiBusinessToolIds.PURCHASE_QUERY -> AiPermissions.VIEW_PURCHASE;
            case AiBusinessToolIds.STOCK_QUERY -> AiPermissions.VIEW_STOCK;
            case AiBusinessToolIds.WAREHOUSE_STOCK_OVERVIEW -> AiPermissions.VIEW_STOCK;
            case AiBusinessToolIds.STOCK_REDUCE_QUERY -> AiPermissions.VIEW_STOCK;
            case AiBusinessToolIds.DISH_SALES_QUERY -> AiPermissions.VIEW_DISH_SALES;
            case AiBusinessToolIds.GROSS_MARGIN_CALCULATOR -> AiPermissions.VIEW_COST;
            case "echo_context" -> null;
            default -> null;
        };
    }

    /** 导出记录下载等 */
    public boolean canExportOrDownload(Long userId, Long exportRecordId, AiQueryScope scope) {
        return true;
    }
}
