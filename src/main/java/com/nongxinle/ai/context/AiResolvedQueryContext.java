package com.nongxinle.ai.context;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiFollowUpResolution;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.contract.DomainContractSelectionResult;
import com.nongxinle.ai.semantic.contract.SemanticContractStrictDecision;
import com.nongxinle.ai.semantic.contract.SemanticContractValidationDebug;
import com.nongxinle.ai.scope.AiConversationScopeMode;
import com.nongxinle.ai.semantic.intake.route.SemanticDomainRouteResult;
import com.nongxinle.ai.semantic.intake.SemanticIntakeResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 单次 Run 的<b>唯一公共查询上下文</b>（Harness 入口）：用户身份、最终组织范围、时间窗、意图与数据口径。
 * 后续经营 / 采购 / 库存 / 菜品毛利 / 报表等 Agent 与 Tool 应<b>只读</b>本对象，禁止再从请求体各自重复解析范围。
 * <p>
 * 典型只读路径：{@link #getOrgScope()}、{@link #getTimeWindow()}、{@link #getQueryIntent()}、{@link #getDataScope()}、
 * {@link #getEffectiveIntentCode()} / {@link #getEffectivePathCode()}。
 * {@link AiResolvedDataScope}：主查询维度见 {@code queryScopeKind} + {@code queryStoreIds} / {@code queryRealDepartmentIds} / {@code queryDistributerId}；
 * 业务表 {@code department_id IN} 用 {@code expandedSqlDepartmentIds}。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiResolvedQueryContext {

    private Long runId;
    private Long userId;

    private AiUserContext userContext;
    /** 会话统计范围：仅 GROUP 枚举 distributer 下全部门店；STORE 单店。 */
    private AiConversationScopeMode conversationScopeMode;
    /** Resolve / ScopePreparation / ScopeIntersect 全链路观测（Harness debug）。 */
    private ScopeResolutionTrace scopeResolutionTrace;
    private AiResolvedOrgScope orgScope;
    private AiResolvedTimeWindow timeWindow;
    private AiResolvedQueryIntent queryIntent;
    private AiResolvedDataScope dataScope;

    private boolean followUp;
    private String originalQuestion;
    private String normalizedQuestion;

    private String queryScopeBanner;
    private String timeWindowLabel;
    private String answerBoundaryNote;

    /** 加载到的上一轮快照（可能为 null）。 */
    private AiConversationTurnMemory previousTurn;
    /** 本 Run 规则型追问解析结果。 */
    private AiFollowUpResolution followUpResolution;

    /** 合并追问后与 {@link #queryIntent} 一致的有效路由（便于日志与排查）。 */
    private String effectiveIntentCode;
    private String effectivePathCode;
    private String effectiveTimeWindowSource;
    private String effectiveScopeSource;
    /** 与 {@link AiFollowUpResolution#getEffectiveIntentSource()} 对齐 */
    private String effectiveIntentSource;

    /**
     * Harness：镜像 {@link AiQuerySemanticParseResult#getPurchaseSemanticFramePrimaryMerge()}；
     * 为 true 时表示本轮采纳语义走采购 CurrentSemanticFrame 主合并路径（与 {@link #semanticAdoptedFrom} 是否为 v2 独立）。
     */
    private Boolean purchaseSemanticFramePrimaryMerge;

    /**
     * 菜品毛利：用户话术中点名的菜名（或多轮继承）；仅用于收窄 Tool/答复，非 SQL 部门列表。
     */
    private String mentionedDishName;
    /**
     * 生产字段：菜品毛利 Tool/Request 层使用的毛利指标类型。
     * <p>
     * TODO(CLEANUP): 当前字段来源仍可能由 structuredIntentDetail →
     * dishProfitMetricTypeFromStructuredWire() Java 推导得到。后续应改为 selectedContractId →
     * contract entry metadata 派生，禁止 Java 根据旧 wire 继续推导业务指标类型。
     */
    private String dishProfitMetricType;

    /**
     * LLM 「用户语义」解析快照（仅存 intent/口述范围/指标等）；不含 queryStoreIds 等可由权限推导的字段。
     */
    private AiQuerySemanticParseResult querySemanticParse;

    /** Harness：语义解析所用 promptId（{@link AiQuerySemanticParseResult#getPromptRegistryId()} 镜像）；未启用时为 null。 */
    private String semanticPromptRegistryId;

    /**
     * 语义路由不可置信（解析失败 / JSON 无效 / confidence 过低 / LLM needClarification / 无法落到有效 path）时需用户澄清，
     * 禁止再用 Java keyword 猜测意图与范围。
     */
    private boolean needSemanticClarification;
    /** Harness：语义收窄门店的诊断（观测）。 */
    private AiSemanticStoreNarrowingDiagnostics semanticStoreNarrowingDebug;
    /** 语义点名单店已成功收窄时的口述店名（可与 mentionedStore 摘要对齐）。 */
    private String resolvedMatchedSemanticStoreMention;
    /** 与 {@link com.nongxinle.ai.core.AiRunState#setClarificationQuestion(String)} 对齐；可由 LLM或统一兜底话术填写。 */
    private String semanticClarificationQuestion;

    // ── Harness / GET run 摘要：公共多门店并排范围语义（不向业务 Agent 耦合） ──

    /** 话术或语义 LLM 命中「多门店并排对比/排行」语义且当时集团视角下≥2门店可见。 */
    private boolean harnessMultiStoreScopeDetected;
    /** 多店并排 Harness：语义子集收窄成功，或因继承上轮多店 + 门店额排行 wire 被判为并排口径。 */
    private boolean harnessMultiStoreScopeApplied;
    /** {@link #harnessMultiStoreScopeApplied} 时对齐到店名序列；否则 null。 */
    private List<String> harnessMultiStoreMatchedStores;
    /** 并排问法下最终结果仍为 GROUP 且≥2门店，表示未误收成单门店。 */
    private boolean harnessSingleStoreNarrowingBlocked;

    /** Harness：多店并排范围来源（仅观测）：{@code SEMANTIC_SUBSET} / {@code INHERITED_PREVIOUS}。 */
    private String harnessMultiStoreScopeSource;

    // ── QuerySemanticParser：v2 唯一主入口 ──

    /**
     * Harness：主链路语义解析协议版本；启用语义 LLM 时为 {@code v2}，关闭时为 null。
     */
    private String semanticPrimaryVersion;
    /**
     * 历史兼容字段（Harness/debug 契约）；当前 v2 主链路恒为 {@code false}，<b>不代表</b> V1 语义 fallback 或 Java 关键词回退。
     */
    private Boolean semanticFallbackUsed;
    /**
     * V2 语义未采纳时的拒收原因（如 {@code time_contract:…}、{@code frame_validation:…}、{@code parse_missing}）；
     * 采纳成功时为 null。<b>不是</b> V1 fallback 标记。
     */
    private String semanticFallbackReason;
    /** 与 {@link #querySemanticParse} 一致的来源：{@code v2} 或澄清失败时 null。 */
    private String semanticAdoptedFrom;
    /** 采纳解析结果中非空语义字段键（Harness）。 */
    private List<String> semanticAdoptedFields;
    /**
     * v2 菜品毛利闸：未通过一致性校验时的字段路径（如 metric.rankingType）；通过或未触发时为 null。
     */
    private List<String> semanticAdoptionRejectedFields;
    /** v2 闸拒绝原因码；仅拒绝采纳时非空。 */
    private String semanticAdoptionRejectedReason;
    /** v2 闸将 metric 从何种 rankingType / 状态归一；无归一则 null。 */
    private String semanticMetricNormalizedFrom;
    /** v2 闸归一结果描述（目标 rankingType 或 cleared_single_dish_profit_margin）。 */
    private String semanticMetricNormalizedTo;
    /**
     * v2：抽象意图（如 COMPARE_STORE）归一至业务 intent 的 Harness 附注（如 fromIntent、toIntent、degradedToPurchaseOverview）。
     */
    private Map<String, Object> semanticV2AbstractIntentNormalizationNotes;

    /** D-TIME-CONTRACT：V2 时间输出合同是否通过（仅结构校验；false 时 {@link #needSemanticClarification} 为 true）。 */
    private Boolean timeContractValid;
    /** 合同失败原因码：MISSING_TIME_FIELDS、TIME_TYPE_DATE_MISMATCH 等。 */
    private String timeContractFailureReason;

    /** 脱敏 v2 输入预览：currentUserMessage、today、previousTurn、visibleStores（仅 storeName）。 */
    private Map<String, Object> querySemanticV2InputPreview;
    /** v2 解析结果安全摘要（无 ID）；与 {@link #querySemanticParse} 在采用 v2 时同源。 */
    private Map<String, Object> querySemanticV2;
    private Boolean querySemanticV2ParseMissing;
    private Double querySemanticV2Confidence;
    private String querySemanticV2TimeAction;
    private String querySemanticV2ScopeAction;
    private String querySemanticV2IntentAction;
    private String querySemanticV2MetricAction;
    private List<String> querySemanticV2MentionedStoreNames;
    private String querySemanticV2MentionedDishName;
    /** v2 观测：模型原始输出截断（无业务 ID 过滤；stub/空响应时常为空串）。 */
    private String querySemanticV2RawText;
    /** v2 观测：{@code parseMissing} 时的原因码（如 empty_llm_response、json_extract_or_syntax_failed）。 */
    private String querySemanticV2ParseError;
    /** v2 协议纠错重试观测（仅 debug / Harness）。 */
    private Boolean querySemanticV2RepairAttempted;
    private Boolean querySemanticV2RepairSuccess;
    private String querySemanticV2RepairReason;

    /** 自 v2 orchestrationDecisionCandidate 解析的扁平字段（仅观测/编排门禁；不重读用户原文）。 */
    private String orchestrationTaskMode;
    private List<String> orchestrationSelectedAgents;
    private List<String> orchestrationSelectedTools;
    private Boolean orchestrationPlannerRequired;
    private Boolean orchestrationMultiAgentRequired;
    private Boolean orchestrationApprovalRequired;
    private Boolean orchestrationClarificationRequired;
    private String orchestrationClarificationQuestion;
    private Double orchestrationConfidence;
    private String orchestrationReason;

    // ── Phase1-H FollowUp Query Rewrite（仅观测 / Harness） ──

    /** 用户原始问句（与 {@link #originalQuestion} 对齐，便于 Harness 摊平）。 */
    private String rawUserMessage;
    /** 是否在进入 v2 前完成省略追问补全。 */
    private Boolean followUpRewriteApplied;
    /** 补全后的自然语言问句；未 rewrite 时为 null。 */
    private String completedUserQuery;
    /** rewrite 规则 id（debug）；非 path/wire/Tool。 */
    private String followUpRewriteReason;
    private Map<String, Object> followUpRewriteDebug;
    private Boolean rewriteInheritedTime;
    private Boolean rewriteInheritedScope;
    /** rewrite 补全引用的实体 type（debug）；非业务 path。 */
    private String rewriteInheritedAnchorType;
    /** rewrite 补全引用的实体名（debug）；非 wire/Tool。 */
    private String rewriteInheritedAnchorName;
    /** rewrite 层 clarification（未 canRewrite 时观测）。 */
    private String followUpRewriteClarificationQuestion;
    /** LLM rewrite 引用的锚点（观测）。 */
    private List<Map<String, String>> rewriteUsedAnchors;

    /** 上一轮 TurnMemory 中 resultAnchors 条数（Rewrite 前观测）。 */
    private Integer previousTurnResultAnchorsCount;
    /** 传入 Rewrite Prompt 的 resultAnchors 条数（Rewrite 前观测）。 */
    private Integer rewritePromptResultAnchorsCount;

    // ── P2 两段式语义：Router + ContractSelector（主链观测；Validator 暂不强拦截） ──

    private SemanticDomainRouteResult semanticDomainRoute;
    private DomainContractSelectionResult domainContractSelection;
    private SemanticContractValidationDebug semanticContractValidation;
    /** P3：合同 strict 统一决策（observe / enforce 共用；默认 strict=false 不阻断）。 */
    private SemanticContractStrictDecision semanticContractStrictDecision;

    /** v2 LLM 顶层 domain（观测；与 Router primaryDomain 对比）。 */
    private String querySemanticV2Domain;
    /** Router primaryDomain 与 v2 domain 是否不一致（观测；不阻断）。 */
    private Boolean routeParserDomainMismatch;
    /** mismatch 原因摘要（观测）。 */
    private String routeParserDomainMismatchReason;

    /** 语义基础设施失败码：LLM_SERVICE_UNAVAILABLE / SEMANTIC_INTAKE_PARSE_FAILED / SEMANTIC_V2_PARSE_FAILED。 */
    private String semanticFailureCode;
    /** 失败阶段：SEMANTIC_INTAKE / SEMANTIC_V2。 */
    private String semanticFailureStage;

    // ── SemanticIntake LLM（主链 Step 1） ──

    private SemanticIntakeResult semanticIntake;

    /** 裸排行维度切换 plan 观测（Harness debug）。 */
    private Map<String, Object> bareRankingDimensionSwitchDebug;
}
