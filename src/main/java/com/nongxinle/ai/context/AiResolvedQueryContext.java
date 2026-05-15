package com.nongxinle.ai.context;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiFollowUpResolution;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
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
     * 菜品毛利：用户话术中点名的菜名（或多轮继承）；仅用于收窄 Tool/答复，非 SQL 部门列表。
     */
    private String mentionedDishName;
    /** Harness/Debug：由 structuredIntentDetail（wire）推导的指标类别，见 {@link AiQuerySemanticLexicon#dishProfitMetricTypeFromStructuredWire}。 */
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

    // ── QuerySemanticParser：v2 为主入口；v1 为 fallback / 对照观测 ──

    /**
     * Harness：主链路语义解析协议版本；启用语义 LLM 时为 {@code v2}（优先 v2 输入），关闭时为 null。
     */
    private String semanticPrimaryVersion;
    /** 是否因 v2 未采纳而回落到 v1。 */
    private Boolean semanticFallbackUsed;
    /** v2 未采纳原因（如 parse_missing、low_confidence、no_routable_path）；未回落时为 null。 */
    private String semanticFallbackReason;
    /** 与 {@link #querySemanticParse} 一致的来源：{@code v2} / {@code v1} / 澄清失败时 null。 */
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
    /** v1 解析安全摘要（无 ID）；未调用 v1 时为 null。 */
    private Map<String, Object> querySemanticV1;

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
}
