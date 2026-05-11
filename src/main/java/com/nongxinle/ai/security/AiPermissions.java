package com.nongxinle.ai.security;

import java.util.Set;

/** AI Run 链路能力码（与 {@code gb_department_user.admin} 解耦的语义常量）。 */
public final class AiPermissions {

    public static final String VIEW_REVENUE = "VIEW_REVENUE";
    public static final String VIEW_PURCHASE = "VIEW_PURCHASE";
    public static final String VIEW_STOCK = "VIEW_STOCK";

    /** 库房端岗位标识（与 VIEW_STOCK 配合；Tool 侧仍以 VIEW_STOCK 为主）。 */
    public static final String VIEW_WAREHOUSE = "VIEW_WAREHOUSE";
    public static final String VIEW_DISH_SALES = "VIEW_DISH_SALES";
    public static final String VIEW_COST = "VIEW_COST";

    /** 供应商/供货商侧数据（占位，Tool 映射可后续接上）。 */
    public static final String VIEW_SUPPLIER = "VIEW_SUPPLIER";

    /** 结构化报表导出/下载等业务（占位）。 */
    public static final String EXPORT_REPORT = "EXPORT_REPORT";

    /** 营销活动类 Agent（占位）。 */
    public static final String MANAGE_MARKETING = "MANAGE_MARKETING";

    /** 经营分析 / BUSINESS_CHAT / 默认成本与经营工具链工作台。 */
    public static final String ACCESS_BUSINESS_WORKSPACE = "ACCESS_BUSINESS_WORKSPACE";

    /** 报表中心类工作台入口（占位，Router 接上后校验）。 */
    public static final String ACCESS_REPORT_WORKSPACE = "ACCESS_REPORT_WORKSPACE";

    /**
     * 命中「营销增长」路由话术后的工作台入口；
     * 明细数据仍由各 VIEW_* 控制。
     */
    public static final String ACCESS_MARKETING_WORKSPACE = "ACCESS_MARKETING_WORKSPACE";

    public static final Set<String> ALL_DATA_VIEW_PERMISSIONS = Set.of(
            VIEW_REVENUE,
            VIEW_PURCHASE,
            VIEW_STOCK,
            VIEW_WAREHOUSE,
            VIEW_DISH_SALES,
            VIEW_COST
    );

    private AiPermissions() {
    }
}
