package com.nongxinle.ai.tool.business;

import java.util.List;

/** 固定 Tool 标识：与 SSE / Trace / Planner 对齐。 */
public final class AiBusinessToolIds {

    public static final String REVENUE_QUERY = "revenue_query";
    /** 日营收经营看板：与 {@link com.nongxinle.service.GbAiDailyRevenueDashboardService#buildStatsDashboard} 同源。 */
    public static final String BUSINESS_OVERVIEW_QUERY = "business_overview_query";
    public static final String PURCHASE_QUERY = "purchase_query";
    /** 采购概览：多门店/单门店聚合 + Top 商品与供货商（{@code purchase_overview_path}）。 */
    public static final String PURCHASE_OVERVIEW = "purchase_overview";
    /** 部门库存快照（剩余汇总 + 区间内入库批次汇总）。 */
    public static final String STOCK_QUERY = "stock_query";
    /** 库房库存概览：聚合库存 + 入库 + 核销分型 + 简易预警列表（{@code warehouse_stock_overview_path}）。 */
    public static final String WAREHOUSE_STOCK_OVERVIEW = "warehouse_stock_overview";
    public static final String STOCK_REDUCE_QUERY = "stock_reduce_query";
    public static final String DISH_SALES_QUERY = "dish_sales_query";
    /** 菜品毛利专项：同源 {@link GbDepFoodBusinessInsightService#buildInsight}，输出结构化毛利汇总。 */
    public static final String DISH_PROFIT_ANALYSIS = "dish_profit_analysis";
    public static final String GROSS_MARGIN_CALCULATOR = "gross_margin_calculator";

    /**
     * 成本洞察默认执行顺序（后者可读取前者落库的 toolResults）。
     */
    public static final List<String> DEFAULT_COST_INSIGHT_TOOLS = List.of(
            REVENUE_QUERY,
            PURCHASE_QUERY,
            STOCK_REDUCE_QUERY,
            DISH_SALES_QUERY,
            GROSS_MARGIN_CALCULATOR
    );

    /** 经营概览：先看旧版看板聚合，再补菜品标价、采购、估算毛利率。 */
    public static final List<String> DEFAULT_BUSINESS_OVERVIEW_TOOLS = List.of(
            BUSINESS_OVERVIEW_QUERY,
            DISH_SALES_QUERY,
            PURCHASE_QUERY,
            GROSS_MARGIN_CALCULATOR
    );

    /** 菜品毛利/经营洞察专用链（单列，勿与 {@link #DEFAULT_BUSINESS_OVERVIEW_TOOLS} 混排）。 */
    public static final List<String> DEFAULT_DISH_PROFIT_TOOLS = List.of(
            DISH_PROFIT_ANALYSIS
    );

    /** 用户原话透出给 Tool（如单道菜名匹配），非查询条件锚点。 */
    public static final String ARG_USER_QUESTION_HINT = "userQuestionHint";

    /** 采购视角「成本」问句默认顺序（实际运行按权限子集过滤）。 */
    public static final List<String> DEFAULT_PURCHASE_COST_INSIGHT_TOOLS = List.of(
            PURCHASE_OVERVIEW,
            STOCK_REDUCE_QUERY
    );

    /** 库房端「经营怎么样」收敛：单一聚合 Tool（内含入库与核销分型汇总）。 */
    public static final List<String> DEFAULT_WAREHOUSE_STOCK_OVERVIEW_TOOLS = List.of(
            WAREHOUSE_STOCK_OVERVIEW
    );

    /** ToolRequest.args / payload 共用键（第一版契约）。 */
    public static final String ARG_DEPARTMENT_FATHER_ID = "departmentFatherId";
    /** 采购商品 Mapper 可选过滤：{@code gb_DPG_purchase_department_id}（通常为门店根部门）。 */
    public static final String ARG_PURCHASE_DEPARTMENT_ID = "purDepId";
    public static final String ARG_DIS_ID = "disId";
    public static final String ARG_START_DATE = "startDate";
    public static final String ARG_STOP_DATE = "stopDate";
    public static final String ARG_INPUT_SNAPSHOT = "inputs";
    /**
     * 若为 true：`AiRunScopeIntersectService` 对集团广角角色不写回单体门店部门；
     * 经营看板仍按「单一父部门/餐厅」旧版口径，需在 Tool 内向用户解释「集团多维汇总暂未接入」，避免误判为「单店画像未配」。
     */
    public static final String ARG_GROUP_WIDE_OVERVIEW_HINT = "groupWideOverviewHint";
    /** 集团经营概览：{@link com.nongxinle.ai.scope.AiQueryScope#getResolvedDepartmentIds()} 快照。 */
    public static final String ARG_RESOLVED_DEPARTMENT_IDS = "resolvedDepartmentIds";
    /** 可读角色码，仅用于日志。 */
    public static final String ARG_AI_ROLE_CODE = "aiRoleCode";
    /** 集团范围内父级门店计数（与日营收记账部门数口径不同）；用于文案。 */
    public static final String ARG_PARENT_STORE_COUNT = "parentStoreCount";
    /** 集团库存概览：按分销户下多门店根汇总 {@link com.nongxinle.ai.tool.business.WarehouseStockOverviewTool}。 */
    public static final String ARG_GROUP_WAREHOUSE_STOCK_AGGREGATION = "groupWarehouseStockAggregation";
    /** 集团采购概览：{@link PurchaseOverviewTool} 按 {@code resolvedDepartmentIds} 多门店采购部门聚合。 */
    public static final String ARG_GROUP_PURCHASE_AGGREGATION = "groupPurchaseAggregation";
    /** 库存概览：客户端可读范围抬头（来自 {@link com.nongxinle.ai.context.AiResolvedQueryContext}）。 */
    public static final String ARG_QUERY_SCOPE_BANNER = "queryScopeBanner";
    /** 库存概览：{@link com.nongxinle.ai.context.AiResolvedOrgScope#getVisibleStores()} 序列化。 */
    public static final String ARG_VISIBLE_STORES = "resolvedVisibleStores";
    /** 库存概览：{@link com.nongxinle.ai.context.AiResolvedOrgScope#getVisibleWarehouses()} 序列化。 */
    public static final String ARG_VISIBLE_WAREHOUSES = "resolvedVisibleWarehouses";

    /** 采购概览：结构化来源聚焦（{@link com.nongxinle.ai.conversation.AiQuerySemanticLexicon}）。 */
    public static final String ARG_PURCHASE_SOURCE_FOCUS = "purchaseSourceFocus";
    /** 采购概览：回答模板（与 queryIntent.structuredIntentDetail 一致，供 Tool 日志与 Composer）。 */
    public static final String ARG_PURCHASE_NARRATIVE_MODE = "purchaseNarrativeMode";

    /** 集团出库/核销查询：多门店父部门 in 聚合（与 {@link PurchaseOverviewTool} 集团旗标对称）。 */
    public static final String ARG_GROUP_STOCK_REDUCE_AGGREGATION = "groupStockReduceAggregation";
    /** 独立 {@code stock_reduce_query_path}：自然日历日四类 subtotal（与成本主链单日营收过滤区分）。 */
    public static final String ARG_STOCK_REDUCE_HARNESS_PATH = "stockReduceHarnessPath";
    /** 出库/核销：与 queryIntent.structuredIntentDetail 一致（wire）。 */
    public static final String ARG_STOCK_REDUCE_NARRATIVE_MODE = "stockReduceNarrativeMode";
    /** 菜品毛利：与 queryIntent.structuredIntentDetail 一致（wire）。 */
    public static final String ARG_DISH_PROFIT_STRUCTURED_DETAIL = "dishProfitStructuredDetail";
    /** 点名菜名收窄（与 resolvedQueryContext.mentionedDishName 一致）。 */
    public static final String ARG_DISH_NAME_FOCUS_HINT = "dishNameFocusHint";

    /** 与 {@link com.nongxinle.ai.context.AiResolvedDataScope#getQueryScopeKind()} 一致：STORE / DEPARTMENT / DISTRIBUTER。 */
    public static final String ARG_QUERY_SCOPE_KIND = "queryScopeKind";
    /** 门店主查询：门店 rootId 列表（不含子部门）。 */
    public static final String ARG_QUERY_STORE_IDS = "queryStoreIds";
    /** 部门主查询：真实部门 id 列表（不含门店 root）。 */
    public static final String ARG_QUERY_REAL_DEPARTMENT_IDS = "queryRealDepartmentIds";
    /** 组织机构主查询：单一 distributer id。 */
    public static final String ARG_QUERY_DISTRIBUTER_ID = "queryDistributerId";
    /** 门店 root → 直属子部门 id（辅助映射，不替代 SQL 展开列表）。 */
    public static final String ARG_STORE_TO_DEPARTMENT_IDS = "storeToDepartmentIds";

    private AiBusinessToolIds() {
    }
}
