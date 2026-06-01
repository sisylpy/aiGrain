package com.nongxinle.ai.security;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiUserContext;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.mapping.AiRoleMapper;
import com.nongxinle.ai.platform.dto.AiRunCreateRequest;
import com.nongxinle.ai.resolver.AiResolvedQueryContextResolver;
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
        if (AiBusinessToolIds.DISH_PROFIT_ANALYSIS.equals(toolId)
                || AiBusinessToolIds.DISH_INGREDIENT_COST_BREAKDOWN.equals(toolId)) {
            return evaluateDishProfitAnalysisInvocation(state, request);
        }
        if (AiBusinessToolIds.DISH_SALES_ANALYSIS_CARD.equals(toolId)) {
            return evaluateDishSalesAnalysisInvocation(state, request);
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

    private AiToolInvocationDecision evaluateDishSalesAnalysisInvocation(AiRunState state, ToolRequest request) {
        AiUserContext ctx = state.getAiUserContext();
        String rc = ctx.getRoleCode();
        if (AiRoleCodes.DELIVERY_SUPPLIER.equals(rc) || AiRoleCodes.DELIVERY_DRIVER.equals(rc)
                || AiRoleCodes.COUPON_OPERATOR.equals(rc)) {
            return AiToolInvocationDecision.deny(AiAnswerBoundary.forDishProfitUnsupportedRoleDenied());
        }
        if (!hasPermission(ctx, AiPermissions.VIEW_DISH_SALES)) {
            return AiToolInvocationDecision.deny(AiAnswerBoundary.forMissingToolPermission(request.getToolName(),
                    AiPermissions.VIEW_DISH_SALES));
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
        if (ctx == null) {
            return true;
        }
        String role = ctx.getRoleCode();
        if (AiRoleMapper.isGroupWideOrgScope(role)) {
            return true;
        }
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        if (rq == null || rq.getOrgScope() == null) {
            return true;
        }

        Long reqDept = state.getDepartmentId();
        Long reqDis = state.getDistributerId();

        if (isStoreAnchoredOrgScope(role)) {
            if (reqDept != null && !departmentAllowedForStoreAnchoredRole(ctx, reqDept, state)) {
                return false;
            }
        } else {
            Long anchorDept = permissionAnchorDepartmentId(ctx, reqDept);
            if (anchorDept != null && reqDept != null && !Objects.equals(anchorDept, reqDept)) {
                return false;
            }
            List<Long> allowed = ctx.getAllowedStoreIds();
            if (!CollectionUtils.isEmpty(allowed) && reqDept != null && !allowed.contains(reqDept)) {
                return false;
            }
        }

        AiRunCreateRequest syn = new AiRunCreateRequest();
        syn.setDepartmentId(reqDept);
        syn.setDistributerId(reqDis);
        Long mergedDis = AiResolvedQueryContextResolver.mergedDistributerId(syn, ctx);
        if (mergedDis != null && reqDis != null && !Objects.equals(mergedDis, reqDis)) {
            return false;
        }
        return true;
    }

    /**
     * 门店锚点角色：Run 经 {@link com.nongxinle.ai.scope.AiRunScopeIntersectService} /
     * {@link com.nongxinle.ai.resolver.AiResolvedOrgScopeAssembler} 归一化后常以门店根 {@code departmentId}
     * 执行 Tool，不得与登录挂靠子部门 {@link AiUserContext#getDepartmentId()} 做严格相等比较。
     */
    private static boolean isStoreAnchoredOrgScope(String roleCode) {
        if (roleCode == null) {
            return false;
        }
        return switch (roleCode) {
            case AiRoleCodes.STORE_MANAGER,
                 AiRoleCodes.STORE_PURCHASER,
                 AiRoleCodes.STORE_ORDER,
                 AiRoleCodes.WINDOW_ORDER -> true;
            default -> false;
        };
    }

    private static boolean departmentAllowedForStoreAnchoredRole(
            AiUserContext ctx, Long reqDept, AiRunState state) {
        if (reqDept == null) {
            return true;
        }
        if (Objects.equals(reqDept, ctx.getDepartmentId())) {
            return true;
        }
        Long storeRoot = ctx.getStoreId();
        if (storeRoot != null && Objects.equals(reqDept, storeRoot)) {
            return true;
        }
        List<Long> allowedRoots = ctx.getAllowedStoreIds();
        if (!CollectionUtils.isEmpty(allowedRoots) && allowedRoots.contains(reqDept)) {
            return true;
        }
        AiQueryScope qs = state != null ? state.getScope() : null;
        if (qs != null && qs.getResolvedDepartmentIds() != null && !qs.getResolvedDepartmentIds().isEmpty()) {
            if (reqDept >= Integer.MIN_VALUE && reqDept <= Integer.MAX_VALUE) {
                return qs.getResolvedDepartmentIds().contains(reqDept.intValue());
            }
        }
        return false;
    }

    /**
     * Run 级权限锚点部门：集团管理端可用请求部门；门店/区域等角色固定为登录 ctx 部门。
     */
    private static Long permissionAnchorDepartmentId(AiUserContext ctx, Long reqDept) {
        if (ctx == null || ctx.getRoleCode() == null) {
            return null;
        }
        return switch (ctx.getRoleCode()) {
            case AiRoleCodes.GROUP_MANAGER -> reqDept != null ? reqDept : ctx.getDepartmentId();
            case AiRoleCodes.REGION_MANAGER,
                 AiRoleCodes.REGION_PURCHASER,
                 AiRoleCodes.REGION_WAREHOUSE -> ctx.getDepartmentId();
            case AiRoleCodes.STORE_MANAGER,
                 AiRoleCodes.STORE_PURCHASER,
                 AiRoleCodes.STORE_ORDER,
                 AiRoleCodes.WINDOW_ORDER -> ctx.getDepartmentId();
            case AiRoleCodes.GROUP_PURCHASER -> ctx.getDepartmentId();
            case AiRoleCodes.WAREHOUSE_MANAGER,
                 AiRoleCodes.WAREHOUSE_PURCHASER,
                 AiRoleCodes.CENTRAL_KITCHEN_MANAGER,
                 AiRoleCodes.CENTRAL_KITCHEN_PURCHASER -> ctx.getDepartmentId();
            case AiRoleCodes.COUPON_OPERATOR -> ctx.getDepartmentId();
            case AiRoleCodes.DELIVERY_SUPPLIER,
                 AiRoleCodes.DELIVERY_DRIVER -> ctx.getDepartmentId();
            default -> ctx.getDepartmentId();
        };
    }

    public static String requiredPermissionForTool(String toolId) {
        if (toolId == null) {
            return null;
        }
        return switch (toolId) {
            case AiBusinessToolIds.REVENUE_QUERY -> AiPermissions.VIEW_REVENUE;
            case AiBusinessToolIds.PURCHASE_OVERVIEW -> AiPermissions.VIEW_PURCHASE;
            case AiBusinessToolIds.WAREHOUSE_STOCK_OVERVIEW -> AiPermissions.VIEW_STOCK;
            case AiBusinessToolIds.STOCK_REDUCE_QUERY -> AiPermissions.VIEW_STOCK;
            default -> null;
        };
    }

    /** 导出记录下载等 */
    public boolean canExportOrDownload(Long userId, Long exportRecordId, AiQueryScope scope) {
        return true;
    }
}
