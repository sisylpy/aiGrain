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

    /** 非空列表：摘要 {@code effectiveIntentCode} 须为其中任一（与单一 {@link #effectiveIntentCode} 可同时不用）。 */
    private List<String> effectiveIntentCodeAnyOf = new ArrayList<>();

    /** 非空列表：摘要 {@code effectivePathCode} 须为其中任一。 */
    private List<String> effectivePathCodeAnyOf = new ArrayList<>();
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

    /**
     * true 时校验采购渠道：优先 {@link #purchaseSourceTypeAnyOf}（非空则 actual 须匹配其中任一）；否则与
     * {@link #purchaseSourceType} 比对。摘要 actual 为 null/空白时按 {@link com.nongxinle.ai.conversation.AiQuerySemanticLexicon#SOURCE_ALL}
     * 归一（显式总览 ALL）。
     */
    private Boolean checkPurchaseSourceType;
    private String purchaseSourceType;

    /** 非空且 {@link #checkPurchaseSourceType} 为 true：actual 归一后须等于列表中任一项（用于 ALL / SELF_PURCHASE 等或关系）。 */
    private List<String> purchaseSourceTypeAnyOf = new ArrayList<>();

    private String mentionedStore;

    /**
     * 非空则要求摘要中 wire 字段 {@code structuredIntentDetailWire}（或兼容旧摘要的 {@code structuredIntentDetail}
     * 若为 wire）与该值完全一致，例如 {@code supplier_amount_ranking}。（展示用枚举见摘要 {@code structuredIntentDetail}。）
     */
    private String structuredIntentDetail;

    /** 非空列表时 actual 必须为其中任一 */
    private List<String> structuredIntentDetailAnyOf = new ArrayList<>();

    /** D-13：与摘要顶层摊平字段 {@code canonicalStructuredIntentDetailWire} 一致（Lexicon canonical）。 */
    private String canonicalStructuredIntentDetailWire;

    /** D-13 semanticSlots：与摘要顶层 {@code queryObject}… 对齐（大小写敏感 trim）。 */
    private String semanticSlotQueryObject;
    private String semanticSlotOperation;
    private String semanticSlotMetric;
    private String semanticSlotSourceFacet;
    private String semanticSlotAnchorPolicy;

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

    /** 非空：与摘要 {@code mentionedGoodsName} 精确匹配（trim 后）。 */
    private String mentionedGoodsName;

    /** 非空：与摘要顶层 {@code selectedContractId} 精确匹配（trim 后）。 */
    private String selectedContractIdExpected;

    /** 非空：与摘要 {@code dishProfitMetricType} 精确匹配。 */
    private String dishProfitMetricType;

    /** 非空：与摘要 {@code semanticAdoptedFrom} 精确匹配（v2 主链路等）。 */
    private String semanticAdoptedFromExpected;

    /** 若为 {@link Boolean#FALSE}：摘要 {@code semanticFallbackUsed} 须为 false。 */
    private Boolean semanticFallbackUsedExpected;

    /** 若非 null：摘要顶层 {@code purchaseSemanticFramePrimaryMerge} 须与该布尔一致。 */
    private Boolean purchaseSemanticFramePrimaryMergeExpected;

    /** 若为 {@link Boolean#FALSE}：摘要 {@code querySemanticV2ParseMissing} 须为 false。 */
    private Boolean querySemanticV2ParseMissingExpected;

    /**
     * 非空：摘要顶层 {@code querySemanticV2TimeAction} 须与该值完全一致（trim 后），
     * 例如要求 v2 原始输出为 {@code INHERIT_PREVIOUS}。
     */
    private String querySemanticV2TimeActionExpected;

    /**
     * 非空列表：摘要 {@code querySemanticV2TimeAction} 归一后须为其中任一（
     * {@link com.nongxinle.ai.harness.replay.AiHarnessExpectationComparator} 优先于单一
     * {@link #querySemanticV2TimeActionExpected}）。
     */
    private List<String> querySemanticV2TimeActionAnyOf = new ArrayList<>();

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

    /** 非空：摘要 JSON 序列化后须包含其中任一字串（如 {@code DISH_INGREDIENT_COVER_DAYS_CARD}）。 */
    private List<String> requiredSubstringsInSummaryJson = new ArrayList<>();

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

    /** 非空：与摘要 {@code stockReduceMatrixRowId} 完全一致。 */
    private String stockReduceMatrixRowIdExpected;

    /** 非空：与摘要 {@code stockReduceKnownGap} 完全一致（如 TYPE2 商品排行未过滤）。 */
    private String stockReduceKnownGapExpected;

    /** true：摘要 {@code stockReduceKnownGap} 必须为 null / 空白。 */
    private Boolean stockReduceKnownGapMustBeAbsent;

    /** 非空：与摘要 {@code revenueMatrixRowId} 完全一致。 */
    private String revenueMatrixRowIdExpected;

    /** 非空：与摘要 {@code revenueKnownGap} 完全一致。 */
    private String revenueKnownGapExpected;

    /** true：摘要 {@code revenueKnownGap} 必须为 null / 空白。 */
    private Boolean revenueKnownGapMustBeAbsent;

    /** 非空：与摘要 {@code warehouseMatrixRowId} 完全一致。 */
    private String warehouseMatrixRowIdExpected;

    /** 非空：与摘要 {@code warehouseKnownGap} 完全一致。 */
    private String warehouseKnownGapExpected;

    /** true：摘要 {@code warehouseKnownGap} 必须为 null / 空白。 */
    private Boolean warehouseKnownGapMustBeAbsent;

    private String harnessReplayWarehouseAnswerPlanType;

    /** 非空：与摘要 {@code dishSalesMatrixObservedRowId} 完全一致（debug-only 观测，非主链 wire）。 */
    private String dishSalesMatrixObservedRowIdExpected;

    /** 非空：与摘要 {@code dishSalesKnownGap} 完全一致。 */
    private String dishSalesKnownGapExpected;

    /** true：摘要 {@code dishSalesKnownGap} 必须为 null / 空白。 */
    private Boolean dishSalesKnownGapMustBeAbsent;

    private String harnessReplayDishSalesAnswerPlanType;

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
    /** 非空：摘要 {@code answerPreview} 须同时包含所列各子串（AND；各子串子串匹配）。 */
    private List<String> answerPreviewMustContainAllSubstrings = new ArrayList<>();
    /** 非空：摘要 {@code answerPreview} 不得包含其中任一字串（子串匹配）。 */
    private List<String> answerPreviewMustNotContainAnyOf = new ArrayList<>();

    /**
     * 非空：嵌套摘要 {@code businessDiagnosisPlan.dataCompleteness.revenue} 与该值完全一致。
     * Historical：嵌套 {@code businessDiagnosisPlan} 已移除；Comparator 对 {@code OK} 走 {@code diagnosisPlan*} 兜底。
     */
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

    /** 非 null：摘要 {@code dishProfitAnswerPlanPresent} 须与该布尔一致。 */
    private Boolean dishProfitAnswerPlanPresentExpected;

    /**
     * 非空：摘要 {@code dishProfitAnswerPlanType}（人读标签，如 {@code 低毛利排行} / {@code 原料成本构成}）须完全一致。
     */
    private String dishProfitAnswerPlanHumanTypeExpected;

    /** 非空：摘要 {@code dishProfitAnswerPlanSortKey} 须完全一致（如 {@code grossProfitAmount}）。 */
    private String dishProfitAnswerPlanSortKeyExpected;

    /** 非 null：摘要 {@code dishProfitAnswerPlanResultAnchorsCount} 须 ≥ 该值。 */
    private Integer dishProfitAnswerPlanResultAnchorsCountMin;

    /** 非空：摘要 {@code dishProfitAnswerPlanResultAnchorTypes} 须逐项包含所列类型（如 DISH）。 */
    private List<String> dishProfitAnswerPlanResultAnchorTypesMustContain = new ArrayList<>();

    /** 非 null：摘要 {@code ingredientBreakdownAvailable} 须与该布尔值一致。 */
    private Boolean ingredientBreakdownAvailableExpected;

    /**
     * 非 null：摘要 {@code dishIngredientCostBreakdownToolSuccess} 须与该值一致。
     * {@link com.nongxinle.ai.dto.business.DishProfitAnswerPlan#TYPE_DISH_INGREDIENT_COST_BREAKDOWN} 时由摘要从
     * {@code ingredientBreakdownAvailable} + 非空 {@code ingredientRows} 推导；其它场景回退工具信封。
     */
    private Boolean dishIngredientCostBreakdownToolSuccessExpected;

    /**
     * 非空：摘要 {@code dishIngredientCoverAnswerPlanType} 须完全一致（如 {@code DISH_INGREDIENT_COVER_DAYS}）。
     */
    private String dishIngredientCoverAnswerPlanTypeExpected;

    /** 非空：摘要 {@code dishIngredientCoverDishName} 须完全一致（如合同槽位 {@code mentionedDishName}）。 */
    private String dishIngredientCoverDishNameExpected;

    /** 非 null：摘要 {@code dishIngredientCoverNoRecipeGap} 须与该布尔一致。 */
    private Boolean dishIngredientCoverNoRecipeGapExpected;

    /**
     * 非空：摘要 {@code goodsSupportedDishCoverAnswerPlanType} 须完全一致（如 {@code GOODS_SUPPORTED_DISH_COVER}）。
     */
    private String goodsSupportedDishCoverAnswerPlanTypeExpected;

    /** 非空：摘要 {@code goodsSupportedDishCoverGoodsName} 须完全一致。 */
    private String goodsSupportedDishCoverGoodsNameExpected;

    /** 非 null：摘要 {@code ingredientRowsCount} 须 ≥ 该值（有原料行时）。 */
    private Integer ingredientRowsCountMin;

    /** 非 null：摘要 {@code ingredientRowCoreMetricPresent} 须与该值一致（行内配方量/单菜成本等）。 */
    private Boolean ingredientRowCoreMetricPresentExpected;

    /**
     * 非空：摘要 {@code ingredientRowFieldsPresent} 须覆盖所列列名（各列在任一行上非空即计为 present；D-13.3B 原料契约）。
     */
    private List<String> ingredientRowFieldsMustContain = new ArrayList<>();

    /** 非空：摘要 {@code ingredientBreakdownUnavailableReason} 须与该值完全一致。 */
    private String ingredientBreakdownUnavailableReasonExpected;

    // --- DiagnosisPlan / 门店对比（business_store_status_compare_diagnosis）Graph 摘要探针 ---

    /** 非空时与摘要 {@code diagnosisPlanExists}（或 {@code diagnosisPlanPresent}）须一致。推荐键。 */
    private Boolean diagnosisPlanExistsExpected;

    /**
     * 非空时与摘要 {@code businessDiagnosisPlanExists} 须一致。
     * Deprecated compat：与 {@link #diagnosisPlanExistsExpected} 同义（均镜像 {@link com.nongxinle.ai.dto.business.DiagnosisPlan} 是否存在）。
     */
    private Boolean businessDiagnosisPlanExistsExpected;

    /** 非空时与摘要 {@code harnessReplayStoreCompareEvidenceRowsLen} 数值相等。 */
    private Integer harnessReplayStoreCompareEvidenceRowsLenExpected;

    /** 非空时与摘要 {@code businessStoreCompareTop1StoreName} 须一致。 */
    private String businessStoreCompareTop1StoreNameExpected;

    /** 非空时与摘要 {@code businessStoreCompareTop2StoreName} 须一致。 */
    private String businessStoreCompareTop2StoreNameExpected;

    /** 非空时与摘要 {@code finalAnswerTextBlank} 须一致。 */
    private Boolean finalAnswerTextBlankExpected;

    // --- 锚 execution 协议（semantic contract + execution intent）---

    /** 非空：摘要顶层 {@code executionIntentType} 须与该值完全一致。 */
    private String executionIntentTypeExpected;

    /** 非空列表：摘要 {@code executionIntentType} 须为其中任一。 */
    private List<String> executionIntentTypeAnyOf = new ArrayList<>();

    /** 非空：摘要 {@code focusEntityType} 须与该值完全一致。 */
    private String focusEntityTypeExpected;

    /**
     * 若为 {@link Boolean#TRUE}：摘要 {@code focusEntityName} 须非空白（数据环境相关，不比具体名称）。
     */
    private Boolean focusEntityNameMustBeNonBlank;

    /**
     * 若为 {@link Boolean#TRUE}：摘要 {@code focusEntityId} 须非空白（Phase2-A GOODS 拆桶等）。
     */
    private Boolean focusEntityIdMustBeNonBlank;

    /** 非空：摘要 {@code executionDetailWanted} 须与该值完全一致。 */
    private String executionDetailWantedExpected;

    /** 非空列表：摘要 {@code executionDetailWanted} 须为其中任一。 */
    private List<String> executionDetailWantedAnyOf = new ArrayList<>();

    /** 非空：摘要 {@code anchorPolicy} 须与该值完全一致。 */
    private String anchorPolicyExpected;

    /** 非空：摘要 {@code previousTurnSummary.lastPathCode} 对应锚来源 planType（观测）。 */
    private String anchorSourcePlanTypeExpected;

    /** 非空：摘要顶层 {@code matchedCapabilityId} 须与该值完全一致。 */
    private String matchedCapabilityIdExpected;

    /** 非空：摘要顶层 {@code contractExecutionQueryMode} 须与该值完全一致。 */
    private String contractExecutionQueryModeExpected;

    /** 非空：摘要顶层 {@code framePlanType} 须与该值完全一致。 */
    private String framePlanTypeExpected;

    /** 非空：摘要顶层 {@code framePurchaseSourceType} 须与该值完全一致。 */
    private String framePurchaseSourceTypeExpected;

    /** 非空：摘要顶层 {@code slotDetailWanted} 须与该值完全一致。 */
    private String slotDetailWantedExpected;

    /** 非 null：摘要 {@code purchaseSupplierGoodsDetailRowsCount} 须 ≥ 该值。 */
    private Integer purchaseSupplierGoodsDetailRowsCountMin;

    /**
     * 若为 {@link Boolean#TRUE}：允许明细行数为 0，但必须给出无数据原因（如 {@code NO_SUPPLIER_PURCHASE_FOR_FOCUSED_GOODS}）
     * 或 {@code purchaseSupplierGoodsDetailAlternativeHasData}=true（自采探针有数）。
     */
    private Boolean purchaseSupplierGoodsDetailRowsOrNoDataOkExpected;

    /**
     * 非空：将 {@code purchaseAnswerPlanFocusRows} 与 {@code purchaseAnswerPlanSecondaryRows} 的 JSON 拼接后，
     * 须逐项包含所列子串（采购明细/ overview 核心行等；与 supplier channel follow-up 无耦合）。
     */
    private List<String> purchaseAnswerPlanFocusOrSecondaryRowsJsonMustContainSubstrings = new ArrayList<>();

    /** 若非 null：摘要 {@code purchaseAnswerPlanResultAnchorsCount} 须 ≥ 该值（D-13.4 等）。 */
    private Integer purchaseAnswerPlanResultAnchorsCountMin;

    /** 非空：摘要 {@code purchaseAnswerPlanResultAnchorTypes} 须逐项包含所列类型（如 GOODS）。 */
    private List<String> purchaseAnswerPlanResultAnchorTypesMustContain = new ArrayList<>();

    // --- D-13.2：STORE anchor → 原因追问（Harness 摘要摊平键）---

    /** 非空：摘要顶层 {@code diagnosisQuestionType}（由 DiagnosisPlan debug 镜像）须与该值一致。 */
    private String diagnosisQuestionTypeExpected;

    /** 非空：摘要 {@code diagnosisReasonExplanationMatrixRowId} 须与该值一致（BD-A…BD-K）。 */
    private String diagnosisReasonExplanationMatrixRowIdExpected;

    /** 非空：摘要 {@code diagnosisFacet} 须与该值一致。 */
    private String diagnosisFacetExpected;

    /** 非空：摘要 {@code diagnosisChildDomain} 须与该值一致（BD-E/F/G）。 */
    private String diagnosisChildDomainExpected;

    /** 非空：摘要 {@code diagnosisKnownGap} 须与该值一致。 */
    private String diagnosisKnownGapExpected;

    /** 非空：摘要 {@code diagnosisTargetStoreName} 须包含该子串（显式门店 BD-D 等）。 */
    private String diagnosisTargetStoreNameMustContain;

    /**
     * 非 null：摘要 {@code diagnosisPlanResultAnchorsCount} 须 ≥ 该值（门店优先级轮通常 ≥1 含 STORE）。
     */
    private Integer diagnosisPlanResultAnchorsCountMin;

    /** 非空：摘要 {@code diagnosisPlanResultAnchorTypes} 须逐项包含所列类型（如 STORE）。 */
    private List<String> diagnosisPlanResultAnchorTypesMustContain = new ArrayList<>();

    /** 非 null：嵌套 {@code previousTurnSummary.resultAnchorsCount} 须 ≥ 该值。 */
    private Integer previousTurnSummaryResultAnchorsCountMin;

    /** 非空：嵌套 {@code previousTurnSummary.resultAnchorTypes} 须逐项包含所列类型。 */
    private List<String> previousTurnSummaryResultAnchorTypesMustContain = new ArrayList<>();

    // --- 阶段 2：Tool Request Only（plannedToolArgsByToolId）---

    /** 非 null 时断言 {@code plannedToolArgsByToolId[toolId]} 最小字段集。 */
    private AiHarnessReplayExpectedPlannedToolArgs expectedPlannedToolArgs;

    /** 非 null 时断言摘要 {@code toolExecuteSkipped}。 */
    private Boolean toolExecuteSkippedExpected;

    /** 非 null 时断言摘要 {@code purchaseAnswerPlanPresent}。 */
    private Boolean purchaseAnswerPlanPresentExpected;

    /** 非 null 时与摘要顶层 {@code needSemanticClarification} 须一致（语义帧/Registry 澄清门禁）。 */
    private Boolean needSemanticClarificationExpected;
}
