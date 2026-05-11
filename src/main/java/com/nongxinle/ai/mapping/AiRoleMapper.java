package com.nongxinle.ai.mapping;

import com.nongxinle.ai.security.AiPermissions;
import com.nongxinle.ai.security.AiRoleCodes;
import com.nongxinle.utils.GbConstants;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static java.util.Map.entry;
/**
 * {@code gb_department_user.gb_du_admin} → AI 可读 {@code roleCode}/{@code roleName}，
 * 以及默认权限集（代码配置第一版）。
 */
public final class AiRoleMapper {

    /** 单行描述：映射与中文名（权限见 {@link #permissionsForAiRole(String)}）。 */
    public record AiRoleDefinition(Integer sourceAdminValue, String roleCode, String roleNameChinese) {
    }

    private static final Map<Integer, AiRoleDefinition> BY_ADMIN;

    static {
        Map<Integer, AiRoleDefinition> m = new LinkedHashMap<>();
        m.put(GbConstants.DepartmentUserRole.GROUP_MANAGER_APP,
                new AiRoleDefinition(GbConstants.DepartmentUserRole.GROUP_MANAGER_APP,
                        AiRoleCodes.GROUP_MANAGER, "集团管理端"));
        m.put(GbConstants.DepartmentUserRole.STORE_PURCHASER_APP,
                new AiRoleDefinition(GbConstants.DepartmentUserRole.STORE_PURCHASER_APP,
                        AiRoleCodes.STORE_PURCHASER, "门店采购端"));
        m.put(GbConstants.DepartmentUserRole.GROUP_PURCHASER_APP,
                new AiRoleDefinition(GbConstants.DepartmentUserRole.GROUP_PURCHASER_APP,
                        AiRoleCodes.GROUP_PURCHASER, "集团集采"));
        m.put(GbConstants.DepartmentUserRole.WAREHOUSE_APP,
                new AiRoleDefinition(GbConstants.DepartmentUserRole.WAREHOUSE_APP,
                        AiRoleCodes.WAREHOUSE_MANAGER, "库房端"));
        m.put(GbConstants.DepartmentUserRole.CENTRAL_KITCHEN_APP,
                new AiRoleDefinition(GbConstants.DepartmentUserRole.CENTRAL_KITCHEN_APP,
                        AiRoleCodes.CENTRAL_KITCHEN_MANAGER, "中央厨房端"));
        m.put(GbConstants.DepartmentUserRole.DELIVERY_SUPPLIER_APP,
                new AiRoleDefinition(GbConstants.DepartmentUserRole.DELIVERY_SUPPLIER_APP,
                        AiRoleCodes.DELIVERY_SUPPLIER, "配送商端"));
        m.put(GbConstants.DepartmentUserRole.DELIVERY_DRIVER_APP,
                new AiRoleDefinition(GbConstants.DepartmentUserRole.DELIVERY_DRIVER_APP,
                        AiRoleCodes.DELIVERY_DRIVER, "配送员端"));
        m.put(GbConstants.DepartmentUserRole.COUPON_APP,
                new AiRoleDefinition(GbConstants.DepartmentUserRole.COUPON_APP,
                        AiRoleCodes.COUPON_OPERATOR, "优惠券端"));
        m.put(GbConstants.DepartmentUserRole.STORE_MANAGER_APP,
                new AiRoleDefinition(GbConstants.DepartmentUserRole.STORE_MANAGER_APP,
                        AiRoleCodes.STORE_MANAGER, "门店管理端"));
        m.put(GbConstants.DepartmentUserRole.STORE_ORDER_APP,
                new AiRoleDefinition(GbConstants.DepartmentUserRole.STORE_ORDER_APP,
                        AiRoleCodes.STORE_ORDER, "门店订货端"));
        m.put(GbConstants.DepartmentUserRole.WINDOW_ORDER_APP,
                new AiRoleDefinition(GbConstants.DepartmentUserRole.WINDOW_ORDER_APP,
                        AiRoleCodes.WINDOW_ORDER, "窗口订货端"));
        m.put(GbConstants.DepartmentUserRole.WAREHOUSE_PURCHASER,
                new AiRoleDefinition(GbConstants.DepartmentUserRole.WAREHOUSE_PURCHASER,
                        AiRoleCodes.WAREHOUSE_PURCHASER, "库房采购员"));
        m.put(GbConstants.DepartmentUserRole.CENTRAL_KITCHEN_PURCHASER,
                new AiRoleDefinition(GbConstants.DepartmentUserRole.CENTRAL_KITCHEN_PURCHASER,
                        AiRoleCodes.CENTRAL_KITCHEN_PURCHASER, "中央厨房采购员"));
        m.put(GbConstants.DepartmentUserRole.REGION_MANAGER_APP,
                new AiRoleDefinition(GbConstants.DepartmentUserRole.REGION_MANAGER_APP,
                        AiRoleCodes.REGION_MANAGER, "区域经理"));
        m.put(GbConstants.DepartmentUserRole.REGION_PURCHASER_APP,
                new AiRoleDefinition(GbConstants.DepartmentUserRole.REGION_PURCHASER_APP,
                        AiRoleCodes.REGION_PURCHASER, "区域采购"));
        m.put(GbConstants.DepartmentUserRole.REGION_WAREHOUSE_APP,
                new AiRoleDefinition(GbConstants.DepartmentUserRole.REGION_WAREHOUSE_APP,
                        AiRoleCodes.REGION_WAREHOUSE, "区域库房"));
        BY_ADMIN = Collections.unmodifiableMap(m);
    }

    private AiRoleMapper() {
    }

    public static Optional<AiRoleDefinition> resolveAdmin(Integer admin) {
        if (admin == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_ADMIN.get(admin));
    }

    public static AiRoleDefinition requireAdmin(Integer admin) {
        return resolveAdmin(admin).orElseThrow(() ->
                new IllegalArgumentException("unsupported gb_department_user.admin=" + admin
                        + " for AI role mapping; extend AiRoleMapper.BY_ADMIN"));
    }

    public static Set<String> permissionsForAiRole(String roleCode) {
        Objects.requireNonNull(roleCode);
        Set<String> p = ROLE_PERMISSION_VIEW.get(roleCode);
        if (p == null) {
            throw new IllegalArgumentException("unknown AI roleCode=" + roleCode + " for permissions");
        }
        return p;
    }

    /** 是否具有集团级敞开组织边界（不按门店锚点校验 Tool 请求的 departmentId）。 */
    public static boolean isGroupWideOrgScope(String roleCode) {
        return AiRoleCodes.GROUP_MANAGER.equals(roleCode);
    }

    private static final Map<String, Set<String>> ROLE_PERMISSION_VIEW = Map.ofEntries(
            entry(AiRoleCodes.GROUP_MANAGER, Set.copyOf(buildGroupManager())),
            entry(AiRoleCodes.STORE_MANAGER,
                    Set.of(AiPermissions.VIEW_REVENUE, AiPermissions.VIEW_COST,
                            AiPermissions.VIEW_PURCHASE, AiPermissions.VIEW_STOCK,
                            AiPermissions.VIEW_DISH_SALES,
                            AiPermissions.ACCESS_BUSINESS_WORKSPACE)),
            entry(AiRoleCodes.STORE_PURCHASER,
                    Set.of(AiPermissions.VIEW_PURCHASE, AiPermissions.VIEW_STOCK,
                            AiPermissions.ACCESS_BUSINESS_WORKSPACE)),
            entry(AiRoleCodes.GROUP_PURCHASER,
                    Set.of(AiPermissions.VIEW_PURCHASE, AiPermissions.VIEW_STOCK,
                            AiPermissions.VIEW_SUPPLIER, AiPermissions.EXPORT_REPORT,
                            AiPermissions.ACCESS_BUSINESS_WORKSPACE, AiPermissions.ACCESS_REPORT_WORKSPACE)),
            entry(AiRoleCodes.WAREHOUSE_MANAGER,
                    Set.of(AiPermissions.VIEW_STOCK, AiPermissions.VIEW_WAREHOUSE,
                            AiPermissions.ACCESS_BUSINESS_WORKSPACE)),
            entry(AiRoleCodes.WAREHOUSE_PURCHASER,
                    Set.of(AiPermissions.VIEW_PURCHASE, AiPermissions.VIEW_STOCK,
                            AiPermissions.ACCESS_BUSINESS_WORKSPACE)),
            entry(AiRoleCodes.CENTRAL_KITCHEN_MANAGER,
                    Set.of(AiPermissions.VIEW_STOCK, AiPermissions.VIEW_COST,
                            AiPermissions.VIEW_DISH_SALES,
                            AiPermissions.ACCESS_BUSINESS_WORKSPACE)),
            entry(AiRoleCodes.CENTRAL_KITCHEN_PURCHASER,
                    Set.of(AiPermissions.VIEW_PURCHASE, AiPermissions.VIEW_STOCK,
                            AiPermissions.VIEW_COST,
                            AiPermissions.ACCESS_BUSINESS_WORKSPACE)),
            entry(AiRoleCodes.COUPON_OPERATOR,
                    Set.of(AiPermissions.VIEW_DISH_SALES, AiPermissions.MANAGE_MARKETING,
                            AiPermissions.ACCESS_MARKETING_WORKSPACE)),
            entry(AiRoleCodes.STORE_ORDER,
                    Set.of(AiPermissions.VIEW_PURCHASE, AiPermissions.VIEW_STOCK,
                            AiPermissions.ACCESS_BUSINESS_WORKSPACE)),
            entry(AiRoleCodes.WINDOW_ORDER,
                    Set.of(AiPermissions.VIEW_PURCHASE, AiPermissions.VIEW_STOCK,
                            AiPermissions.ACCESS_BUSINESS_WORKSPACE)),
            entry(AiRoleCodes.DELIVERY_SUPPLIER,
                    Set.of(AiPermissions.VIEW_PURCHASE, AiPermissions.VIEW_STOCK,
                            AiPermissions.ACCESS_BUSINESS_WORKSPACE)),
            entry(AiRoleCodes.DELIVERY_DRIVER,
                    Set.of(AiPermissions.VIEW_STOCK, AiPermissions.ACCESS_BUSINESS_WORKSPACE)),
            entry(AiRoleCodes.REGION_MANAGER,
                    Set.of(AiPermissions.VIEW_REVENUE, AiPermissions.VIEW_COST,
                            AiPermissions.VIEW_PURCHASE, AiPermissions.VIEW_STOCK,
                            AiPermissions.VIEW_DISH_SALES,
                            AiPermissions.EXPORT_REPORT,
                            AiPermissions.ACCESS_BUSINESS_WORKSPACE, AiPermissions.ACCESS_REPORT_WORKSPACE)),
            entry(AiRoleCodes.REGION_PURCHASER,
                    Set.of(AiPermissions.VIEW_PURCHASE, AiPermissions.VIEW_STOCK,
                            AiPermissions.VIEW_SUPPLIER,
                            AiPermissions.ACCESS_BUSINESS_WORKSPACE,
                            AiPermissions.ACCESS_REPORT_WORKSPACE)),
            entry(AiRoleCodes.REGION_WAREHOUSE,
                    Set.of(AiPermissions.VIEW_STOCK, AiPermissions.VIEW_PURCHASE,
                            AiPermissions.ACCESS_BUSINESS_WORKSPACE,
                            AiPermissions.ACCESS_REPORT_WORKSPACE)),
            /** 仅供单测/过渡期 */
            entry(AiRoleCodes.FINANCE_MANAGER, buildFinanceManagerSynthetic()),
            entry(AiRoleCodes.MARKETING_MANAGER,
                    Set.of(AiPermissions.ACCESS_MARKETING_WORKSPACE)));

    private static Set<String> buildGroupManager() {
        return Set.of(
                AiPermissions.VIEW_REVENUE,
                AiPermissions.VIEW_COST,
                AiPermissions.VIEW_PURCHASE,
                AiPermissions.VIEW_STOCK,
                AiPermissions.VIEW_DISH_SALES,
                AiPermissions.EXPORT_REPORT,
                AiPermissions.ACCESS_BUSINESS_WORKSPACE,
                AiPermissions.ACCESS_REPORT_WORKSPACE,
                AiPermissions.ACCESS_MARKETING_WORKSPACE);
    }

    private static Set<String> buildFinanceManagerSynthetic() {
        return Set.of(AiPermissions.VIEW_REVENUE,
                AiPermissions.VIEW_PURCHASE,
                AiPermissions.VIEW_STOCK,
                AiPermissions.VIEW_DISH_SALES,
                AiPermissions.ACCESS_MARKETING_WORKSPACE);
    }
}
