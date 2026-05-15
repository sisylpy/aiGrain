package com.nongxinle.ai.harness.replay;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 单轮 Harness 预期；字段为 null / 空列表表示「本项不断言」。
 */
@Data
public class AiHarnessReplayExpectedRound {

    private String effectiveIntentCode;
    private String effectivePathCode;
    /** 非空时 actual 必须落在其中任一 */
    private List<String> effectiveTimeWindowSourceAnyOf = new ArrayList<>();
    /** 与 anyOf 互斥：单一精确匹配 */
    private String effectiveTimeWindowSource;
    private String startDate;
    private String endDate;
    private String scopeType;

    private List<Long> visibleStoreRootIds = new ArrayList<>();
    private List<Long> effectiveSqlDepartmentIds = new ArrayList<>();

    /**
     * 与摘要 {@code queryStoreIds} 对齐（门店根 int 列表）；非空且 {@link com.nongxinle.ai.harness.replay.AiHarnessReplayRequest#strictStoreSqlMatch}
     * 为 true 时做强校验（排序后比较）。
     */
    private List<Integer> queryStoreIds = new ArrayList<>();

    /** 非空时要求 {@code visibleStoreRootIds} 实际数量 ≥ 该值（防多店被误收成单店）。 */
    private Integer visibleStoreRootCountMin;

    /**
     * 与摘要 {@code revenueAnswerPlanType} 对齐；Replay 仅跑解析时多为 null，此时若预期为
     * {@link com.nongxinle.ai.dto.business.DailyRevenueAnswerPlan#TYPE_REVENUE_STORE_AMOUNT_RANKING}
     * 则退化为校验 {@code effectivePathCode} + {@code structuredIntentDetailWire} 已具备门店排行口径。
     */
    private String revenueAnswerPlanPlanType;

    /**
     * 与摘要 {@code querySemanticEffectiveMentionedStoreNames} 对齐（语义 LLM 合并后的店名列表，去空白）；
     * 非空时做强校验（排序后集合相等，忽略顺序）。
     */
    private List<String> querySemanticEffectiveMentionedStoreNames = new ArrayList<>();

    /** true 时校验 purchaseSourceType 与下面字段（可为 null 表示要求空） */
    private Boolean checkPurchaseSourceType;
    private String purchaseSourceType;

    private String mentionedStore;

    /**
     * 非空则要求摘要中 wire 字段 {@code structuredIntentDetailWire}（或兼容旧摘要的 {@code structuredIntentDetail}
     * 若为 wire）与该值完全一致，例如 {@code supplier_amount_ranking}。（展示用枚举见摘要 {@code structuredIntentDetail}。）
     */
    private String structuredIntentDetail;

    /** 非空列表时 actual 必须为其中任一 */
    private List<String> structuredIntentDetailAnyOf = new ArrayList<>();

    private String effectiveIntentSource;
    private String effectiveScopeSource;

    /** 与时间窗类似：任一满足即 Pass */
    private List<String> effectiveIntentSourceAnyOf = new ArrayList<>();
    private List<String> effectiveScopeSourceAnyOf = new ArrayList<>();

    /** 若为 null：不断言；否则与摘要 {@code multiStoreScopeDetected} 必须一致。 */
    private Boolean multiStoreScopeDetectedExpected;
    /** 若为 null：不断言；否则与摘要 {@code multiStoreScopeApplied} 必须一致。 */
    private Boolean multiStoreScopeAppliedExpected;
    /** 若为 null：不断言；否则与摘要 {@code singleStoreNarrowingBlocked} 必须一致。 */
    private Boolean singleStoreNarrowingBlockedExpected;

    /**
     * 若非空：与摘要 {@code multiStoreMatchedStores} 集相等（trim 去空白；忽略顺序）；
     * 若预期非空但实际摘要为空，则失败。
     */
    private List<String> multiStoreMatchedStoresExpected = new ArrayList<>();

    /**
     * 若为 {@link Boolean#TRUE}：摘要 {@code mentionedDishName} 必须为 null / 空白。
     */
    private Boolean mentionedDishNameMustBeAbsent;

    /** 非空：与摘要 {@code mentionedDishName} 精确匹配（trim 后）。 */
    private String mentionedDishName;

    /** 非空：与摘要 {@code dishProfitMetricType} 精确匹配。 */
    private String dishProfitMetricType;

    /** 非空：与摘要 {@code semanticAdoptedFrom} 精确匹配（v2 主链路等）。 */
    private String semanticAdoptedFromExpected;

    /** 若为 {@link Boolean#FALSE}：摘要 {@code semanticFallbackUsed} 须为 false。 */
    private Boolean semanticFallbackUsedExpected;

    /** 若为 {@link Boolean#FALSE}：摘要 {@code querySemanticV2ParseMissing} 须为 false。 */
    private Boolean querySemanticV2ParseMissingExpected;

    /**
     * 非空：摘要顶层 {@code querySemanticV2TimeAction} 须与该值完全一致（trim 后），
     * 例如要求 v2 原始输出为 {@code INHERIT_PREVIOUS}。
     */
    private String querySemanticV2TimeActionExpected;

    /**
     * 非空：摘要顶层 {@code querySemanticV2MetricAction} 须与该值完全一致（trim 后），
     * 例如排行→点菜名追问须为 {@code OVERRIDE}。
     */
    private String querySemanticV2MetricActionExpected;

    /**
     * 非空：摘要顶层 {@code querySemanticV2TimeAction} 不得为列表中任一项（大小写不敏感），
     * 用于禁止 v2 原始输出误将承接窗标成 OVERRIDE 等。
     */
    private List<String> querySemanticV2TimeActionNoneOf = new ArrayList<>();

    /**
     * 非空：摘要 {@code querySemanticV2.time.timeType}（嵌套）归一化后不得为列表中任一项，
     * 例如禁止误默认 {@code CURRENT_MONTH}。
     */
    private List<String> querySemanticV2TimeTypeNoneOf = new ArrayList<>();

    /**
     * 非空：将摘要整体 JSON 序列化后不得包含其中任一字串（防 v2_no_routable_path / Placeholder 等）。
     */
    private List<String> forbiddenSubstringsInSummaryJson = new ArrayList<>();

    /**
     * 若为 {@link Boolean#TRUE}：在 {@code querySemanticV2} 与 {@code querySemanticV2InputPreview} 树内不得出现键
     * {@code queryStoreIds}、{@code departmentIds}、{@code expandedSqlDepartmentIds}。
     */
    private Boolean enforceQuerySemanticV2ScopeKeyAbsence;

    /**
     * 非空：{@code effectiveTimeWindowSource} 不得为列表中任一项（用于禁止 DEFAULT_MONTH_TO_DATE 等）。
     */
    private List<String> effectiveTimeWindowSourceNoneOf = new ArrayList<>();

    /** 非空：{@code effectiveIntentCode} 不得为列表中任一项。 */
    private List<String> effectiveIntentCodeNoneOf = new ArrayList<>();

    /** 非空：{@code purchaseSourceType} 不得为列表中任一项。 */
    private List<String> purchaseSourceTypeNoneOf = new ArrayList<>();

    /// --- Replay 无 RunState 时由 {@link AiHarnessReplayContextProbes} 写入的探针 ---

    private String harnessReplayPlanSource;
    private String harnessReplayDishProfitAnswerPlanType;
    private String harnessReplayDishProfitAnswerPlanSortDirection;
    private Boolean harnessReplayPurchaseAnswerPlanProbePresent;
    private String harnessReplayPurchaseAnswerPlanType;
    private Boolean harnessReplayRevenueAnswerPlanProbePresent;
    private String harnessReplayRevenueAnswerPlanType;
    private String harnessReplayStockReduceAnswerPlanType;
    private String harnessReplayStockReduceAnswerPlanSortDirection;
    private String harnessReplayStockReduceReduceType;

    /** 非空时与摘要 {@code orchestrationTaskMode} 完全一致。 */
    private String orchestrationTaskModeExpected;
    /** 非空时与摘要 {@code queryScopeMode} 完全一致（数据口径 STORE / …）。 */
    private String queryScopeModeExpected;
    /** 非空时与摘要 {@code queryScopeKind} 完全一致。 */
    private String queryScopeKindExpected;

    /** 非空：摘要 {@code queryStoreIds} 须逐项包含所列 id（子集断言；独立于 strict）。 */
    private List<Integer> queryStoreIdsMustContain = new ArrayList<>();
    /** 非空：摘要 {@code resolvedVisibleStoreRootIds} 须逐项包含（Replay 回填后）。 */
    private List<Long> resolvedVisibleStoreRootIdsMustContain = new ArrayList<>();
    /** true：{@code resolvedEffectiveSqlDepartmentIds} 非 null 且列表非空。 */
    private Boolean resolvedEffectiveSqlDepartmentIdsNonEmpty;
    /** 非空：{@code resolvedEffectiveSqlDepartmentIds} 须逐项包含。 */
    private List<Long> resolvedEffectiveSqlDepartmentIdsMustContain = new ArrayList<>();

    /** 非空：摘要 {@code consumedAnswerPlans} 须逐项包含所列 plan 简易名（子集断言）。 */
    private List<String> consumedAnswerPlansMustContain = new ArrayList<>();
    /** true：{@code missingAnswerPlans} 须为 null 或空列表。 */
    private Boolean missingAnswerPlansMustBeEmpty;

    /** 非空：摘要 {@code answerPreview} trim 后须包含其中任一字串（子串匹配）。 */
    private List<String> answerPreviewContainsAnyOf = new ArrayList<>();
    /** 非空：摘要 {@code answerPreview} 不得包含其中任一字串（子串匹配）。 */
    private List<String> answerPreviewMustNotContainAnyOf = new ArrayList<>();

    /** 非空：嵌套摘要 {@code businessDiagnosisPlan.dataCompleteness.revenue} 与该值完全一致。 */
    private String businessDiagnosisDataCompletenessRevenueExpected;

    private Boolean businessOverviewMultiAgentBatchCompletedExpected;
    private Boolean businessOverviewAllExpectedDomainsAttemptedExpected;
    private Boolean businessOverviewMultiAgentAnyDomainSuccessExpected;

    /** 非空：摘要顶层 {@code businessOverviewSuccessfulDomains} 须逐项包含所列域键（如 revenue / purchase）；与 MultiAgent debug 对齐。 */
    private List<String> businessOverviewSuccessfulDomainsMustContain = new ArrayList<>();

    /** 非空：{@code scopeLabel} 逐项包含所列子串（子串匹配）。 */
    private List<String> scopeLabelMustContainSubstrings = new ArrayList<>();

    /**
     * 非空：将 {@code actionItems} 序列化为 JSON 拼接后的文本不得包含任一字串（Replay 契约；对象为 null 视为「空」）。
     */
    private List<String> summaryActionItemsForbiddenSubstrings = new ArrayList<>();

    /** 非空：摘要 {@code usedTools} 须逐项包含所列 tool id（trim 后 equals）。 */
    private List<String> usedToolsMustContain = new ArrayList<>();

    /** 非空时与摘要 {@code masterRevenueToolResultSuccess} 须一致（GRAPH_RUN Master 营收专线）。 */
    private Boolean masterRevenueToolResultSuccessExpected;

    /** 非空时与摘要 {@code masterPurchaseToolResultSuccess} 须一致。 */
    private Boolean masterPurchaseToolResultSuccessExpected;

    /** 非空时与摘要 {@code masterStockReduceToolResultSuccess} 须一致。 */
    private Boolean masterStockReduceToolResultSuccessExpected;

    /** 非空时与摘要 {@code masterDishProfitToolResultSuccess} 须一致。 */
    private Boolean masterDishProfitToolResultSuccessExpected;

    // --- DiagnosisPlan / 门店对比（business_store_status_compare_diagnosis）Graph 摘要探针 ---

    /** 非空时与摘要 {@code diagnosisPlanExists} 须一致。 */
    private Boolean diagnosisPlanExistsExpected;

    /** 非空时与摘要 {@code businessDiagnosisPlanExists} 须一致。 */
    private Boolean businessDiagnosisPlanExistsExpected;

    /** 非空时与摘要 {@code harnessReplayStoreCompareEvidenceRowsLen} 数值相等。 */
    private Integer harnessReplayStoreCompareEvidenceRowsLenExpected;

    /** 非空时与摘要 {@code businessStoreCompareTop1StoreName} 须一致。 */
    private String businessStoreCompareTop1StoreNameExpected;

    /** 非空时与摘要 {@code businessStoreCompareTop2StoreName} 须一致。 */
    private String businessStoreCompareTop2StoreNameExpected;

    /** 非空时与摘要 {@code finalAnswerTextBlank} 须一致。 */
    private Boolean finalAnswerTextBlankExpected;
}
