package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiUserContext;
import com.nongxinle.ai.security.AiPermissions;
import com.nongxinle.ai.mapping.AiRoleMapper;
import com.nongxinle.ai.security.AiRoleCodes;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 成本类问句的意图收敛：同一话术在不同 {@code roleCode} 下走全量成本诊断、采购视角或权限拒绝。
 */
public final class CostInsightIntentConvergence {

    private CostInsightIntentConvergence() {
    }

    /** admin 对应采购类角色：走 {@code PURCHASE_COST_ANALYSIS}，不调营收/毛利/完整 CostDiagnosis。 */
    public static boolean isProcurementCostConvergenceRole(String roleCode) {
        if (roleCode == null) {
            return false;
        }
        return AiRoleCodes.STORE_PURCHASER.equals(roleCode)
                || AiRoleCodes.GROUP_PURCHASER.equals(roleCode)
                || AiRoleCodes.WAREHOUSE_PURCHASER.equals(roleCode)
                || AiRoleCodes.CENTRAL_KITCHEN_PURCHASER.equals(roleCode)
                || AiRoleCodes.REGION_PURCHASER.equals(roleCode);
    }

    /**
     * 门店/部门账号在问句中显式提到「集团」且仍属成本意图时，答复需提示已收窄到本店（数据范围由 org scope 保证）。
     * <p>
     * TODO(CLEANUP): 当前逻辑仅用于展示 disclaimer，但仍属于 Java 文本关键词判断。
     * 后续应改为由 SemanticIntake / semanticSlots / scopeAction 明确表达集团口径，
     * 禁止通过中文 contains 推断用户范围或业务语义。
     */
    public static boolean asksGroupWideCostWording(String normalizedQuestion) {
        if (normalizedQuestion == null || normalizedQuestion.isBlank()) {
            return false;
        }
        String q = normalizedQuestion.trim();
        if (!q.contains("集团")) {
            return false;
        }
        return q.contains("成本") || q.contains("毛利") || q.contains("损耗")
                || q.contains("采购") || q.contains("食材") || q.contains("核销")
                || q.contains("出库成本") || q.contains("出库");
    }

    /** 仅门店管理端等「非集团敞开」账号需要集团口径提示时使用。 */
    public static boolean shouldAddStoreScopedGroupCostDisclaimer(AiUserContext ctx, String normalizedQuestion) {
        if (ctx == null || ctx.getRoleCode() == null || normalizedQuestion == null) {
            return false;
        }
        if (AiRoleMapper.isGroupWideOrgScope(ctx.getRoleCode())) {
            return false;
        }
        return asksGroupWideCostWording(normalizedQuestion);
    }

    /** 采购视角工具链（按权限过滤）：{@link AiBusinessToolIds#PURCHASE_OVERVIEW} 为主，可选核销/出库。 */
    public static List<String> buildPurchaseCostInsightTools(Set<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return List.of();
        }
        List<String> plan = new ArrayList<>(2);
        if (permissions.contains(AiPermissions.VIEW_PURCHASE)) {
            plan.add(AiBusinessToolIds.PURCHASE_OVERVIEW);
        }
        if (permissions.contains(AiPermissions.VIEW_STOCK)) {
            plan.add(AiBusinessToolIds.STOCK_REDUCE_QUERY);
        }
        return List.copyOf(plan);
    }
}
