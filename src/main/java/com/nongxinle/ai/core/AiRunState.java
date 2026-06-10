package com.nongxinle.ai.core;

import com.nongxinle.ai.composer.menu.MenuExpertPresentationPlan;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiUserContext;
import com.nongxinle.ai.security.AiPermissionDenied;
import com.nongxinle.ai.dto.business.AiDishProfitOverviewResult;
import com.nongxinle.ai.dto.business.BusinessOverviewAnswerPlan;
import com.nongxinle.ai.dto.business.DishProfitAnswerPlan;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.dto.business.PurchaseGoodsBusinessAnalysisAnswerPlan;
import com.nongxinle.ai.dto.business.StockReduceAnswerPlan;
import com.nongxinle.ai.dto.business.DailyRevenueAnswerPlan;
import com.nongxinle.ai.dto.business.DiagnosisPlan;
import com.nongxinle.ai.dto.business.DishSalesAnswerPlan;
import com.nongxinle.ai.dto.business.DishProfitPrescriptionAnswerPlan;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan;
import com.nongxinle.ai.dto.cost.AiCostDiagnosisResult;
import com.nongxinle.ai.planner.BusinessDiagnosisCompositeExecutionResult;
import com.nongxinle.ai.planner.BusinessDiagnosisCompositeGateResult;
import com.nongxinle.ai.scope.AiQueryScope;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单次 AI Run 的可变状态载体；执行过程中由各 {@link AgentNode} 读写。
 * 字段随阶段扩展，初版仅打通链路与 SSE。
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AiRunState {

    private Long runId;
    /**
     * 可选。与 {@link com.nongxinle.ai.platform.dto.AiRunCreateRequest#getAdvisorId()} 对齐；Run 归因，不参与语义与权限。
     */
    private Long advisorId;
    /** 与 {@link com.nongxinle.ai.platform.dto.AiRunCreateRequest#getConversationId()} 一致（会话表主键） */
    private Long conversationId;
    private Long userId;

    private Long departmentId;
    private Long distributerId;

    /** 与 {@link com.nongxinle.ai.platform.dto.AiRunCreateRequest#getScopeMode()} 对齐；异步 resolve 时回灌请求。 */
    private String scopeMode;

    private String rawUserInput;
    private String normalizedUserInput;

    /** 默认 BUSINESS_CHAT：Harness 主链路以 LLM {@link AiResolvedQueryContext} 路由，非 null，避免 DataPlanner 误截断。 */
    @Builder.Default
    private AiWorkspaceMode workspaceMode = AiWorkspaceMode.BUSINESS_CHAT;

    private String userRole;

    private AiQueryScope scope;

    private AiUserContext aiUserContext;

    /**
     * 统一查询上下文（组织/时间/意图/数据范围）；在 {@link com.nongxinle.ai.platform.AiRunService#startRun} 早期生成。
     * 后续 Node / Tool 应只读此对象。
     */
    private AiResolvedQueryContext resolvedQueryContext;

    /** 门店/ subtree 收窄说明（可读，非硬性拒绝）；如「请求的部门超出范围，已切换为本人负责维度」。 */
    private String scopeConvergenceNote;


    @Builder.Default
    private List<AiPermissionDenied> permissionDenials = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> toolResults = new HashMap<>();

    @Builder.Default
    private List<String> selectedAgents = new ArrayList<>();

    private String finalAnswerText;

    /**
     * 前端 context bar 结构化字段（store/time/scope 短标签）；不含 Composer 长段边界说明。
     */
    @Builder.Default
    private Map<String, Object> answerContextSummary = new LinkedHashMap<>();

    /**
     * Harness：Composer 曾拼接的完整上下文前言（boundary / scope / intent / permission）。
     */
    private String answerContextPreambleDebug;

    /** Harness：AnswerComposer LLM system prompt 所使用的 promptId；未调用 Composer LLM 时为 null。 */
    private String composerPromptRegistryId;

    private boolean needClarification;
    private String clarificationQuestion;

    /** yyyy-MM-dd，含首末日，与 Tool 入参一致 */
    private String statStartDate;
    private String statEndDate;
    private String timeWindowResolutionNote;

    /**
     * BUSINESS_CHAT：成本主线（毛利/核销/损耗等）。
     */
    @Builder.Default
    private boolean costInsightPath = false;

    /**
     * 采购视角成本问句（原意图 {@code COST_ANALYSIS} → {@code PURCHASE_COST_ANALYSIS}）：
     * 仅跑采购/核销工具，不跑 {@link com.nongxinle.ai.graph.business.CostDiagnosisAgentNode}。
     */
    @Builder.Default
    private boolean purchaseCostInsightPath = false;

    /**
     * 库房端「经营怎么样」收敛为库存视角：跑 {@link com.nongxinle.ai.tool.business.AiBusinessToolIds#WAREHOUSE_STOCK_OVERVIEW}
     *（聚合库存、入库、核销分型与预警列表），不跑经营看板/营收/菜品。
     */
    @Builder.Default
    private boolean warehouseStockOverviewPath = false;

    /**
     * 集团管理端等问题触发库存概览时：{@link com.nongxinle.ai.tool.business.WarehouseStockOverviewTool}
     * 按 {@code distributerId} 下多门店根聚合，不得使用登录 {@code departmentId} 当单一门店锚点。
     */
    @Builder.Default
    private boolean groupWarehouseStockOverview = false;

    /**
     * 采购概览链路（与 {@link #purchaseCostInsightPath} 同跑工具阶段时并存；结构化见 {@link #purchaseOverview}）。
     */
    @Builder.Default
    private boolean purchaseOverviewPath = false;

    /** 集团端采购问句：多门店采购部门聚合，不得用登录部门当单店锚点。 */
    @Builder.Default
    private boolean groupPurchaseOverview = false;

    /**
     * 出库/核销基础查询链路（与日营收口径脱钩阶段一：类型1–4 subtotal 全自然日）。
     */
    @Builder.Default
    private boolean stockReduceQueryPath = false;

    /** 集团端：按多门店父部门 in 聚合，不得仅用登录门店锚点。 */
    @Builder.Default
    private boolean groupStockReduceQuery = false;

    /**
     * 日营业额 / 营收专线（{@link com.nongxinle.ai.tool.business.AiBusinessToolIds#REVENUE_QUERY}）。
     */
    @Builder.Default
    private boolean revenueOverviewPath = false;

    /**
     * 优惠券/营销端用户问成本：不拉数，仅输出权限说明（由 {@link com.nongxinle.ai.graph.business.StubAnswerComposerNode} 处理）。
     */
    @Builder.Default
    private boolean couponCostInsightBlocked = false;

    /** 采购收敛等意图说明，供 Composer 正文前展示（与 {@link #scopeConvergenceNote} 分离）。 */
    private String costIntentConvergenceNote;

    /**
     * 权限/意图收敛（如 BUSINESS_OVERVIEW → PURCHASE_OVERVIEW），供 {@code answer_delta.data} 结构化带回。
     */
    private Map<String, String> intentConvergence;

    /**
     * BUSINESS_CHAT：经营概览主线（营业额/菜品/毛利概览）；与 {@link #costInsightPath} 互斥，
     * 成本类关键词命中时优先走成本。
     */
    @Builder.Default
    private boolean businessOverviewPath = false;

    /**
     * 菜品毛利/经营透视专用链（与 {@link #businessOverviewPath}、{@link #costInsightPath} 互斥）。
     */
    @Builder.Default
    private boolean dishProfitPath = false;

    /** 单菜菜品成本+销售分析（{@code dish_cost_analysis_path}；不启用 {@link #dishProfitPath}）。 */
    @Builder.Default
    private boolean dishCostAnalysisPath = false;

    /**
     * 菜单经营顾问专线（{@code menu_operation_path}；独立于 {@link #dishProfitPath}）。
     */
    @Builder.Default
    private boolean menuOperationPath = false;

    /**
     * 经营诊断编排链（采购概览 + 出库/核销 + 菜品毛利透视；独立 path，不启用 {@link #dishProfitPath}）。
     */
    @Builder.Default
    private boolean businessDiagnosisPath = false;

    @Builder.Default
    private List<String> dataPlanTools = new ArrayList<>();

    private AiCostDiagnosisResult costDiagnosisResult;

    /** 经营概览 MultiAgent：四域 AnswerPlan 聚合（Composer / Debug 只读）。 */
    private BusinessOverviewAnswerPlan businessOverviewAnswerPlan;

    private AiDishProfitOverviewResult dishProfitOverviewResult;

    /**
     * 菜品毛利：本轮 AnswerPlan（选行+排序在服务端完成，Composer 只读）。
     */
    private DishProfitAnswerPlan dishProfitAnswerPlan;

    /**
     * 菜品销量/销售额排行：本轮 AnswerPlan（Harness / Debug；Phase 1 数据来自 {@link com.nongxinle.ai.tool.business.AiBusinessToolIds#DISH_PROFIT_ANALYSIS} 快照）。
     */
    private DishSalesAnswerPlan dishSalesAnswerPlan;

    /**
     * 菜单经营顾问：本轮 AnswerPlan（Harness / Composer 只读；独立于 {@link DishProfitAnswerPlan}）。
     */
    private MenuOperationAnswerPlan menuOperationAnswerPlan;

    /**
     * 单菜利润处方卡：contract {@code dish.profit.prescription.v1}；Harness / Composer / cards[] 只读。
     */
    private DishProfitPrescriptionAnswerPlan dishProfitPrescriptionAnswerPlan;

    /**
     * 单菜配料可支撑天数：contract {@code dish.ingredient_cover_days.v1}；Harness / Composer / cards[] 只读。
     */
    private com.nongxinle.ai.dto.business.DishIngredientCoverAnswerPlan dishIngredientCoverAnswerPlan;

    /**
     * 原料 → 受影响菜品可支撑：contract {@code warehouse.goods_supported_dish_cover.v1}；Harness / Composer / cards[] 只读。
     */
    private com.nongxinle.ai.dto.business.GoodsSupportedDishCoverAnswerPlan goodsSupportedDishCoverAnswerPlan;

    /**
     * 指定商品当前仍有剩余的库存批次明细：contract {@code warehouse.goods_stock_batch_detail.v1}。
     */
    private com.nongxinle.ai.dto.business.GoodsStockBatchDetailAnswerPlan goodsStockBatchDetailAnswerPlan;

    /**
     * 采购概览：本轮 AnswerPlan（{@link com.nongxinle.ai.graph.business.PurchaseOverviewTool} 结果衍生；Composer 后续只读）。
     */
    private PurchaseAnswerPlan purchaseAnswerPlan;

    /**
     * GOODS 锚点原料采购经营分析（{@code purchase.goods_business_analysis.v1}）。
     */
    private PurchaseGoodsBusinessAnalysisAnswerPlan purchaseGoodsBusinessAnalysisAnswerPlan;

    /**
     * 出库/核销专线：本轮 AnswerPlan（{@link com.nongxinle.ai.tool.business.StockReduceQueryTool} 结果衍生）。
     */
    private StockReduceAnswerPlan stockReduceAnswerPlan;

    /**
     * 日营业额 / 营收专线：本轮 AnswerPlan（{@link com.nongxinle.ai.tool.business.RevenueQueryTool} 结果衍生）。
     */
    private DailyRevenueAnswerPlan revenueAnswerPlan;

    /**
     * 库房库存现量专线：本轮 AnswerPlan（{@link com.nongxinle.ai.tool.business.WarehouseStockOverviewTool} 结果衍生）。
     */
    private com.nongxinle.ai.dto.business.WarehouseAnswerPlan warehouseAnswerPlan;

    /** 经营诊断：只读子域 AnswerPlan 聚合（{@link DiagnosisPlanBuilder}）。 */
    private DiagnosisPlan diagnosisPlan;

    /** 库房库存概览结构化摘要（供 {@code answer_delta.data.warehouseOverview}）。 */
    private Map<String, Object> warehouseOverview;

    /** 采购概览结构化摘要（供 {@code answer_delta.data.purchaseOverview}）。 */
    private Map<String, Object> purchaseOverview;

    /**
     * Tool 产出的结构化卡片（如 {@code DISH_COST_ANALYSIS_CARD}）；供 SSE / 历史消息 hydrated。
     */
    private Map<String, Object> cardPayload;

    /** 兼容小程序 {@code cards[0]} 读取路径；通常与 {@link #cardPayload} 同源。 */
    @Builder.Default
    private List<Map<String, Object>> cards = new ArrayList<>();

    /** Run 终态是否已有可下发卡片（Harness / SSE / 历史消息 hydrated）。 */
    public boolean isCardPayloadPresent() {
        return cardPayload != null && !cardPayload.isEmpty();
    }

    /** Run 终态是否已有 {@code cards[0]} 兼容列表。 */
    public boolean isCardsPresent() {
        return cards != null && !cards.isEmpty();
    }

    /** 审核节点占位输出（如 passed/score）；供 Composer 汇入最终提示。 */
    private Map<String, Object> outcomeReviewStub;

    /** Harness：菜单专家 LLM 本轮实际 prompt/input 快照（仅 MENU_ACTION_RECOMMENDATION）。 */
    private Map<String, Object> menuExpertPromptPreview;

    /** Harness：菜单专家 LLM 原始/归一化输出快照。 */
    private Map<String, Object> menuExpertLlmOutputPreview;

    /** Harness：菜单专家 Composer 采用/拒绝决策。 */
    private Map<String, Object> menuExpertComposerDecision;

    /** Harness：营业额卡菜品销量原因 Agent 输入/输出/终稿观测（仅 debug 下发）。 */
    private Map<String, Object> dishSalesReasonAgentHarnessDebug;

    /**
     * 菜单专家 LLM 展示计划（仅 {@code MENU_ACTION_RECOMMENDATION} 且 Guard 通过时写入）。
     */
    private MenuExpertPresentationPlan menuExpertPresentationPlan;

    /**
     * MasterBusinessAgent 编排调试摘要（扁平字段见 {@link com.nongxinle.ai.harness.AiHarnessResolvedContextSummarizer}）。
     */
    private Map<String, Object> masterBusinessAgentDebug;

    /**
     * C-55：经营诊断 Composite 生产入口 Gate 观测结果（只记录，不改变主链路路由 / Tool / 答复）；默认 feature 关闭时为
     * {@link com.nongxinle.ai.planner.BusinessDiagnosisCompositeGateReasonCode#FEATURE_FLAG_DISABLED}。
     */
    private BusinessDiagnosisCompositeGateResult businessDiagnosisCompositeGateResult;

    /** C-58：仅 Harness {@code GRAPH_RUN} + {@code compositeBusinessDiagnosisExecutionMode=HARNESS_ONLY} 时写入；不影响主链路终稿 */
    private BusinessDiagnosisCompositeExecutionResult businessDiagnosisCompositeExecutionResult;

    private volatile boolean cancelled;

    /**
     * Harness {@link com.nongxinle.ai.harness.replay.AiHarnessReplayDryRunStage#TOOL_REQUEST_ONLY}：
     * Graph 在 ToolExecution 节点仅捕获 planned args，不调用 {@code Tool.execute}。
     */
    @Builder.Default
    private boolean harnessToolRequestOnly = false;

    /** Harness 阶段 2：{@code Tool.execute} 是否被刻意跳过。 */
    @Builder.Default
    private boolean toolExecuteSkipped = false;

    /** Harness 阶段 2：是否至少写入一条 planned tool args 快照。 */
    @Builder.Default
    private boolean toolRequestCaptured = false;

    /**
     * Harness 阶段 2：toolId → 快照（含 args、RequestContext 字段、resolutionDebug）。
     */
    @Builder.Default
    private Map<String, Map<String, Object>> plannedToolArgsByToolId = new HashMap<>();
}
