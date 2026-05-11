package com.nongxinle.ai.security;

import com.nongxinle.ai.tool.business.AiBusinessToolIds;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 可读提示文案与 permissionDenied DTO 的组装。 */
public final class AiAnswerBoundary {

    /** 门店类账号 Scope 收窄时引导语（与 {@link AiRunScopeIntersectService} 对齐）。 */
    public static final String SCOPE_CLAMP_STORE_FRONT =
            "你当前账号只能查看本门店数据，下面按本门店范围为你分析。";

    public static final String SCOPE_CLAMP_REGION_FRONT =
            "你当前账号只能查看负责区域的数据，下面按该区域范围为你分析。";

    public static final String SCOPE_CLAMP_PROCUREMENT_FRONT =
            "你当前账号只能查看采购相关数据，下面按采购视角为你分析。";


    public static final String SUGGEST_OWN_SCOPE =
            "你可以查看自己职责范围内的门店/分销经营数据；若需跨店或集团视图，请联系管理员开通相应权限。";

    private AiAnswerBoundary() {
    }

    /** 拼装自然语言前缀，供 Composer 汇入最终答复。 */
    public static String composeHumanPrefix(List<AiPermissionDenied> denials) {
        if (denials == null || denials.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【权限提示】本次运行存在功能受限：");
        LinkedHashMap<String, Boolean> seen = new LinkedHashMap<>();
        for (AiPermissionDenied d : denials) {
            if (d == null || d.getReason() == null || d.getReason().isBlank()) {
                continue;
            }
            seen.put(d.getReason().trim(), Boolean.TRUE);
        }
        List<String> lines = new ArrayList<>(seen.keySet());
        for (int i = 0; i < Math.min(lines.size(), 6); i++) {
            sb.append("\n• ").append(lines.get(i));
        }
        if (sb.length() > 0) {
            sb.append("\n");
        }
        return sb.toString();
    }

    public static AiPermissionDenied forMissingToolPermission(String toolId, String requiredPermission) {
        return AiPermissionDenied.builder()
                .subject(toolId)
                .requiredPermission(requiredPermission)
                .reason(humanReadableToolDenial(toolId, requiredPermission))
                .suggestedScope(SUGGEST_OWN_SCOPE)
                .build();
    }

    public static AiPermissionDenied forOrgScopeViolation(String subject) {
        return AiPermissionDenied.builder()
                .subject(subject)
                .reason(String.format(
                        "你当前身份的门店/分销范围不匹配本次查询（%s）；请切换到本人负责的组织后再试。", subject))
                .suggestedScope(SUGGEST_OWN_SCOPE)
                .build();
    }

    public static String scopeConvergencePrefix(String note) {
        if (note == null || note.isBlank()) {
            return "";
        }
        String body = note.trim();
        // Planner / 其它节点若已写过「【查询范围】」，此处只保留一条前缀，避免重复。
        while (body.startsWith("【查询范围】")) {
            body = body.substring("【查询范围】".length()).trim();
        }
        return "【查询范围】" + body + "\n\n";
    }

    /**
     * Scope 校准后对用户展示的一句话（按岗位），不出现「子树」「越权」「部门树根」等技术词。
     */
    public static String scopeClampIntroductionForRole(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return SCOPE_CLAMP_STORE_FRONT;
        }
        if (AiRoleCodes.REGION_MANAGER.equals(roleCode) || AiRoleCodes.REGION_WAREHOUSE.equals(roleCode)) {
            return SCOPE_CLAMP_REGION_FRONT;
        }
        if (AiRoleCodes.STORE_PURCHASER.equals(roleCode)
                || AiRoleCodes.GROUP_PURCHASER.equals(roleCode)
                || AiRoleCodes.WAREHOUSE_PURCHASER.equals(roleCode)
                || AiRoleCodes.CENTRAL_KITCHEN_PURCHASER.equals(roleCode)
                || AiRoleCodes.REGION_PURCHASER.equals(roleCode)) {
            return SCOPE_CLAMP_PROCUREMENT_FRONT;
        }
        return SCOPE_CLAMP_STORE_FRONT;
    }

    /**
     * 从最终对用户展示的正文中移除开发态用词（日志/ Trace 不受影响）。逐行剔除含内部字段的行。
     */
    public static String stripDeveloperFacingLeakage(String text) {
        if (text == null || text.isBlank()) {
            return text == null ? "" : text;
        }
        List<String> kept = new ArrayList<>();
        for (String raw : text.replace("\r\n", "\n").split("\n", -1)) {
            if (!isDeveloperFacingNoiseLine(raw)) {
                kept.add(raw);
            }
        }
        return String.join("\n", kept).replaceAll("(\\s*\\n){3,}", "\n\n").trim();
    }

    /** 单行若含内部枚举名、JSON 字段名或开发态话术，不向用户展示。 */
    static boolean isDeveloperFacingNoiseLine(String line) {
        if (line == null || line.isBlank()) {
            return false;
        }
        String t = line.trim();
        String lc = t.toLowerCase(Locale.ROOT);
        return lc.contains("dataplantools")
                || t.contains("toolResults")
                || lc.contains("toolresults")
                || t.contains("tool_results")
                || t.contains("workspaceMode")
                || lc.contains("workspacemode")
                || t.contains("BUSINESS_CHAT")
                || t.contains("系统尚未执行任何数据查询工具")
                || t.contains("建议您补充口径")
                || t.contains("请用户补充口径")
                || t.contains("请用户收窄问题") && t.contains("补充")
                || t.contains("当前时间窗口是")
                || t.contains("子树范围")
                || t.contains("请求的部门超出");
    }

    /** 采购视角收敛等意图说明（挂在成本问句Planner之后）。 */
    public static String costIntentConvergencePrefix(String note) {
        if (note == null || note.isBlank()) {
            return "";
        }
        return "【意图说明】" + note.trim() + "\n\n";
    }

    /** 路由命中但未持有进入该工作空间的权限时使用。 */
    public static AiPermissionDenied forMarketingWorkspaceDenied() {
        return AiPermissionDenied.builder()
                .subject("workspace:MARKETING_GROWTH")
                .reason("当前账号暂无「营销增长」工作台权限，如需营销方案请在具备权限的岗位下使用，或使用经营分析话术。")
                .suggestedScope("可尝试「这个月生意怎么样」「帮我看本月成本怎么样」等与采购/营业额相关的问题。")
                .requiredPermission(AiPermissions.ACCESS_MARKETING_WORKSPACE)
                .build();
    }

    public static AiPermissionDenied forCouponOperatorCostInsight() {
        return AiPermissionDenied.builder()
                .subject("cost_insight_intent")
                .requiredPermission(AiPermissions.VIEW_COST)
                .reason("你当前账号没有查看成本分析的权限，可以查看优惠券活动、套餐效果、菜品销售和营销建议。")
                .suggestedScope("可尝试询问营销话术、优惠券活动或与菜品销售相关的问题。")
                .build();
    }

    /** 采购类角色问及「菜品毛利」工具链（话术层仍可能走 dish_profit_path，工具层拒绝）。 */
    public static AiPermissionDenied forDishProfitPurchaserDenied() {
        return AiPermissionDenied.builder()
                .subject(AiBusinessToolIds.DISH_PROFIT_ANALYSIS)
                .reason("你当前账号不能查看菜品毛利，但可以查看相关采购成本和食材价格。")
                .suggestedScope("可询问本月采购入库、核销汇总或供货单价相关问题。")
                .build();
    }

    public static AiPermissionDenied forDishProfitWarehouseDenied() {
        return AiPermissionDenied.builder()
                .subject(AiBusinessToolIds.DISH_PROFIT_ANALYSIS)
                .reason("你当前账号不能查看菜品毛利，但可以查看库存出入库和损耗情况。")
                .suggestedScope("可询问当前库存结余、区间内入库或在库批次与核销分型汇总。")
                .build();
    }

    /** 配送/优惠券等对经营菜品毛利无主链权限的端。 */
    public static AiPermissionDenied forDishProfitUnsupportedRoleDenied() {
        return AiPermissionDenied.builder()
                .subject(AiBusinessToolIds.DISH_PROFIT_ANALYSIS)
                .reason("你当前账号不能查看菜品毛利分析。")
                .suggestedScope(SUGGEST_OWN_SCOPE)
                .build();
    }

    public static AiPermissionDenied forCostDiagnosisAgent(String requiredPermission, String reasonFallback) {
        return AiPermissionDenied.builder()
                .subject("CostDiagnosisAgent")
                .requiredPermission(requiredPermission)
                .reason(reasonFallback != null && !reasonFallback.isBlank()
                        ? reasonFallback
                        : "你当前账号无法进行成本结构化诊断。")
                .suggestedScope(SUGGEST_OWN_SCOPE)
                .build();
    }

    private static String humanReadableToolDenial(String toolId, String requiredPermission) {
        String label = toolLabel(toolId);
        String permCn = permissionLabel(requiredPermission);
        return String.format("你当前账号没有权限使用「%s」（需要权限：%s）。", label, permCn);
    }

    static String toolLabel(String toolId) {
        if (toolId == null) {
            return "该工具";
        }
        return switch (toolId) {
            case AiBusinessToolIds.REVENUE_QUERY -> "营业额查询";
            case AiBusinessToolIds.PURCHASE_QUERY -> "采购查询";
            case AiBusinessToolIds.STOCK_QUERY -> "库房库存快照";
            case AiBusinessToolIds.WAREHOUSE_STOCK_OVERVIEW -> "库房库存概览";
            case AiBusinessToolIds.STOCK_REDUCE_QUERY -> "核销/出库汇总";
            case AiBusinessToolIds.DISH_SALES_QUERY -> "菜品销售";
            case AiBusinessToolIds.DISH_PROFIT_ANALYSIS -> "菜品毛利透视";
            case AiBusinessToolIds.GROSS_MARGIN_CALCULATOR -> "毛利率估算";
            case "CostDiagnosisAgent" -> "成本诊断";
            case "echo_context" -> "演示工具";
            default -> toolId;
        };
    }

    static String permissionLabel(String perm) {
        if (perm == null) {
            return "业务数据";
        }
        return switch (perm) {
            case AiPermissions.VIEW_REVENUE -> "查看营业额";
            case AiPermissions.VIEW_PURCHASE -> "查看采购数据";
            case AiPermissions.VIEW_STOCK -> "查看库存与核销/出库";
            case AiPermissions.VIEW_WAREHOUSE -> "库房数据查看";
            case AiPermissions.VIEW_DISH_SALES -> "查看菜品销售";
            case AiPermissions.VIEW_COST -> "查看成本/毛利结构化分析";
            case AiPermissions.ACCESS_MARKETING_WORKSPACE -> "进入营销工作台";
            case AiPermissions.VIEW_SUPPLIER -> "查看供应商数据";
            case AiPermissions.EXPORT_REPORT -> "导出报表";
            case AiPermissions.MANAGE_MARKETING -> "营销管理";
            case AiPermissions.ACCESS_BUSINESS_WORKSPACE -> "经营分析工作台";
            case AiPermissions.ACCESS_REPORT_WORKSPACE -> "报表工作台";
            default -> perm;
        };
    }
}
