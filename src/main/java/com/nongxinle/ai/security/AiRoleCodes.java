package com.nongxinle.ai.security;

/**
 * AI 内部角色编码：由 {@code gb_department_user.gb_du_admin}
 * 经 {@link com.nongxinle.ai.mapping.AiRoleMapper} 映射得到。
 */
public final class AiRoleCodes {

    /** 集团管理端 app — admin 0 */
    public static final String GROUP_MANAGER = "GROUP_MANAGER";

    /** 门店采购 admin 1 */
    public static final String STORE_PURCHASER = "STORE_PURCHASER";
    /** 集采 admin 2 */
    public static final String GROUP_PURCHASER = "GROUP_PURCHASER";
    /** 库房 admin 3 */
    public static final String WAREHOUSE_MANAGER = "WAREHOUSE_MANAGER";
    /** 中央厨房 admin 4 */
    public static final String CENTRAL_KITCHEN_MANAGER = "CENTRAL_KITCHEN_MANAGER";
    /** 配送商 admin 5 */
    public static final String DELIVERY_SUPPLIER = "DELIVERY_SUPPLIER";
    /** 配送员 admin 6 */
    public static final String DELIVERY_DRIVER = "DELIVERY_DRIVER";
    /** 优惠券 / 运营 admin 7 */
    public static final String COUPON_OPERATOR = "COUPON_OPERATOR";
    /** 门店管理端 admin 11 */
    public static final String STORE_MANAGER = "STORE_MANAGER";
    /** 门店订货 admin 12 */
    public static final String STORE_ORDER = "STORE_ORDER";
    /** 窗口订货 admin 13 */
    public static final String WINDOW_ORDER = "WINDOW_ORDER";
    /** 库房采购员 admin 31 */
    public static final String WAREHOUSE_PURCHASER = "WAREHOUSE_PURCHASER";
    /** 中央厨房采购员 admin 41 */
    public static final String CENTRAL_KITCHEN_PURCHASER = "CENTRAL_KITCHEN_PURCHASER";
    /** 区域经理 admin 51 */
    public static final String REGION_MANAGER = "REGION_MANAGER";
    /** 区域采购 admin 52 */
    public static final String REGION_PURCHASER = "REGION_PURCHASER";
    /** 区域库房 admin 53 */
    public static final String REGION_WAREHOUSE = "REGION_WAREHOUSE";

    /**
     * 财务视图角色（{@code gb_du_admin = 91}，见 {@link com.nongxinle.utils.GbConstants.DepartmentUserRole#FINANCE_MANAGER_AI_APP}）。
     */
    public static final String FINANCE_MANAGER = "FINANCE_MANAGER";
    /**
     * 营销工作台视图角色（{@code gb_du_admin = 92}，见 {@link com.nongxinle.utils.GbConstants.DepartmentUserRole#MARKETING_MANAGER_AI_APP}）。
     */
    public static final String MARKETING_MANAGER = "MARKETING_MANAGER";

    /** @deprecated 请使用 {@link #GROUP_MANAGER}；保留别名以免历史 JSON 误判。 */
    @Deprecated(since = "2026-05", forRemoval = false)
    public static final String GROUP_BOSS = GROUP_MANAGER;

    private AiRoleCodes() {
    }
}
