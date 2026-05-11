package com.nongxinle.ai.core;

import com.nongxinle.ai.context.AiOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiUserContext;
import com.nongxinle.ai.security.AiPermissionDenied;
import com.nongxinle.ai.dto.business.AiBusinessOverviewResult;
import com.nongxinle.ai.dto.business.AiDishProfitOverviewResult;
import com.nongxinle.ai.dto.cost.AiCostDiagnosisResult;
import com.nongxinle.ai.scope.AiQueryScope;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
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
    /** 与 {@link com.nongxinle.ai.platform.dto.AiRunCreateRequest#getConversationId()} 一致（会话表主键） */
    private Long conversationId;
    private Long userId;

    private Long departmentId;
    private Long distributerId;

    private String rawUserInput;
    private String normalizedUserInput;

    private AiWorkspaceMode workspaceMode;

    private String userRole;

    private AiQueryScope scope;

    private AiUserContext aiUserContext;
    private AiOrgScope aiOrgScope;

    /**
     * 统一查询上下文（组织/时间/意图/数据范围）；在 {@link com.nongxinle.ai.platform.AiRunService#startRun} 早期生成。
     * 后续 Node / Tool 应优先只读此对象；与 {@link #aiUserContext}/{@link #aiOrgScope} 并存，逐步收敛。
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

    @Builder.Default
    private List<String> dataPlanTools = new ArrayList<>();

    private AiCostDiagnosisResult costDiagnosisResult;

    private AiBusinessOverviewResult businessOverviewResult;

    private AiDishProfitOverviewResult dishProfitOverviewResult;

    /** 库房库存概览结构化摘要（供 {@code answer_delta.data.warehouseOverview}）。 */
    private Map<String, Object> warehouseOverview;

    /** 采购概览结构化摘要（供 {@code answer_delta.data.purchaseOverview}）。 */
    private Map<String, Object> purchaseOverview;

    /** 审核节点占位输出（如 passed/score）；供 Composer 汇入最终提示。 */
    private Map<String, Object> outcomeReviewStub;

    private volatile boolean cancelled;
}
