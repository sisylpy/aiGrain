package com.nongxinle.ai.semantic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Harness 入口「用户语义 LLM 解析」结果：仅限自然语言语义，不含任何需权限/组织树展开的数据库 ID。
 * <p>
 * SQL 范围 ID（如 queryStoreIds、expandedSqlDepartmentIds 等）必须由
 * {@link com.nongxinle.ai.resolver.AiResolvedQueryContextResolver} /
 * {@link com.nongxinle.ai.context.AiResolvedOrgScope} /
 * {@link com.nongxinle.ai.context.AiResolvedDataScope} 生成。
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AiQuerySemanticParseResult {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimePart {
        private String timeType;
        private String startDate;
        private String endDate;
        private String timeSource;
        private Boolean needInheritFromPrevious;
        /** LLM 对时间窗如何得出的简短说明（Harness 观测）。 */
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequestedScopePart {
        private String requestedScopeType;
        private String mentionedStoreName;
        /**
         * 用户一次点到多家门店（多店对比/并列问营收），口述名列表；不包含任何数据库 ID。
         */
        private List<String> mentionedStoreNames;
        private String mentionedDepartmentName;
        private String mentionedWarehouseName;
        private String scopeSource;
        private Boolean needInheritFromPrevious;
    }

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricPart {
        private String primaryMetric;
        private String rankingType;
        private String purchaseSourceType;
        private String stockReduceType;
    }

    /**
     * LLM 输出的编排候选（仅解析 JSON；Java 不据此猜测用户原文）。
     */
    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrchestrationDecisionCandidatePart {
        private String taskMode;
        /** 模型建议的子 Agent / 能力名（字符串列表，观测用） */
        private List<String> selectedAgents;
        private List<String> selectedTools;
        private Boolean plannerRequired;
        private Boolean multiAgentRequired;
        private Boolean approvalRequired;
        private Boolean clarificationRequired;
        private String clarificationQuestion;
        private Double confidence;
        private String reason;
    }

    /**
     * D-13 Phase1：与 {@code metric} 对象并列的抽象查询槽位（采购排行 / 来源 facet / 锚点策略）；不包含 ID。
     * LLM JSON 键名 {@code semanticSlots}。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SemanticSlotsPart {
        /** GOODS / SUPPLIER / STORE / DISH / ORDER / UNKNOWN */
        private String queryObject;
        /** SUMMARY / RANKING / DETAIL / TREND / COMPARE / DIAGNOSIS */
        private String operation;
        /** PURCHASE_AMOUNT / PURCHASE_COUNT / UNIT_PRICE / … */
        private String metric;
        /** ALL / SELF_PURCHASE / SUPPLIER_PURCHASE；可与 {@link MetricPart#getPurchaseSourceType()} 对齐 */
        private String sourceFacet;
        /** USE_PREVIOUS_ANCHOR / IGNORE_PREVIOUS_ANCHOR / REQUIRE_CLARIFICATION */
        private String anchorPolicy;
        /** 采购追问明细槽：GOODS_DETAIL / GOODS_UNIT_PRICE / SUPPLIER_UNIT_PRICE / SOURCE_BREAKDOWN 等；由 LLM 输出 */
        private String detailWanted;
        /**
         * 与 {@link com.nongxinle.ai.conversation.AiQuerySemanticLexicon#canonicalStructuredIntentDetailWire} 对齐的采购子口径
         * wire；业务意图源，禁止由 {@link MetricPart#getRankingType()} 反推。
         */
        private String structuredIntentDetailWire;
        /**
         * AnswerPlan 类型提示（采购/出库等域）；与 wire 对齐，观测用；
         * 服务端以 {@code structuredIntentDetailWire} 为准，Matrix/Builder 推导。
         */
        private String answerPlanType;
        /**
         * P4-J2：Parser 从 {@code allowedOutputContract.allowedContracts[]} 精确选择的 ACTIVE 合同 id；
         * 与 {@code structuredIntentDetailWire} 等同一条 entry。
         */
        private String selectedContractId;
        /**
         * 单菜 / DISH anchor 合同：用户在原文中点到的一道菜名称（口述，非 ID）。
         * 与顶层 {@link AiQuerySemanticParseResult#mentionedDishName} 二选一或并存；服务端合并读取。
         */
        private String mentionedDishName;
        /**
         * 原料 / GOODS anchor 合同：用户在原文中点到的原料名称（口述，非 ID）。
         * 与顶层 {@link AiQuerySemanticParseResult#mentionedGoodsName} 二选一或并存；服务端合并读取。
         */
        private String mentionedGoodsName;
        /**
         * 用户显式给出的目标毛利率（百分数，如 "55"）；仅 LLM 输出，Java 不得从原文 parse。
         */
        private String requestedTargetGrossMarginRate;

        /** 拷贝全部槽位字段（含 {@code mentionedDishName} / {@code mentionedGoodsName}）；normalizer / contract completion 复用。 */
        public static SemanticSlotsPart copyOf(SemanticSlotsPart from) {
            if (from == null) {
                return null;
            }
            return SemanticSlotsPart.builder()
                    .selectedContractId(from.getSelectedContractId())
                    .queryObject(from.getQueryObject())
                    .operation(from.getOperation())
                    .metric(from.getMetric())
                    .sourceFacet(from.getSourceFacet())
                    .anchorPolicy(from.getAnchorPolicy())
                    .detailWanted(from.getDetailWanted())
                    .structuredIntentDetailWire(from.getStructuredIntentDetailWire())
                    .answerPlanType(from.getAnswerPlanType())
                    .mentionedDishName(from.getMentionedDishName())
                    .mentionedGoodsName(from.getMentionedGoodsName())
                    .requestedTargetGrossMarginRate(from.getRequestedTargetGrossMarginRate())
                    .build();
        }
    }

    private String intent;
    /**
     * LLM 顶层 {@code domain}（如 PURCHASE）；仅当模型显式输出时使用，服务端不用 rankingType / 上一帧路径推断业务域。
     */
    private String semanticDomain;
    /** DISH_PROFIT/BUSINESS_DIAGNOSIS 等：用户在原文中点到的一道菜名称（口述，非 ID）。 */
    private String mentionedDishName;
    /** WAREHOUSE 原料锚：用户在原文中点到的原料名称（口述，非 ID）。 */
    private String mentionedGoodsName;
    private Double confidence;
    /** 是否与上一轮同一对话主线（语义判断，不设 ID） */
    private Boolean followUp;
    /** NEW / INHERIT_PREVIOUS / OVERRIDE；缺省则由 Java 仅按既有 intent/time 字段合并 */
    private String intentAction;
    private String timeAction;
    private String scopeAction;
    private String metricAction;

    private TimePart time;
    private RequestedScopePart requestedScope;
    private MetricPart metric;

    /** 可选；与 {@link #metric} 并存时以槽位与 metric 互校准 {@code purchaseSourceType}。 */
    private SemanticSlotsPart semanticSlots;

    /**
     * D-1X-D1：{@link AiQuerySemanticSlotMerge#reconcileSemanticSlotsViaCapabilityMatrices} 前快照的本轮 LLM
     * {@code semanticSlots.structuredIntentDetailWire}；null 表示本轮 JSON 未显式给出 wire（inherit 回填不算）。
     */
    private String currentTurnStructuredIntentDetailWire;

    /**
     * Resolver：已通过 CurrentSemanticFrame 校验且走采购帧收养路径；{@link AiQuerySemanticLlmMergeHelper#mergeIntent}
     * 不得再用 metric.rankingType / SurfaceSignals / 供货商 summary 类启发覆盖本轮 structuredIntentDetail / purchaseSourceType。
     */
    private Boolean purchaseSemanticFramePrimaryMerge;

    private OrchestrationDecisionCandidatePart orchestrationDecisionCandidate;

    private Boolean needClarification;
    private String clarificationQuestion;
    private String reason;

    /** 模型原始 JSON 摘录（截断），仅用于排障；不落业务逻辑。 */
    private String rawJsonDigest;

    /**
     * 观测：语义解析 LLM 原始输出截断（当前仅 v2 {@link AiQuerySemanticLlmParser#parse} 填充）；不参与 merge / 路由。
     */
    private String observationLlmRawText;
    /**
     * 观测：JSON 未产出、无法抽取对象或解析失败时的简短原因码；不参与 merge。
     */
    private String observationJsonParseError;

    /**
     * Java-only：采购 GOODS 锚矩阵 canonical 原因码（见 {@link com.nongxinle.ai.semantic.matrix.PurchaseSemanticCapabilityMatrix}）；
     * 非 LLM 字段，供 debug / Harness 审计。
     */
    private List<String> purchaseMatrixCanonicalReasons;

    /**
     * Phase1-G：多轮槽位继承观测（仅 debug / Harness；不参与路由）。
     * 键示例：{@code inheritedFromPreviousSlots}、{@code inheritedEntityAnchor}、
     * {@code ignoredPreviousAnchorReason}、{@code clearedPreviousWireReason}、
     * {@code clearedPreviousAnswerPlanTypeReason}、{@code currentTurnWinsReason}、{@code crossDomainTurn}。
     */
    private Map<String, Object> multiTurnInheritanceTrace;

    /**
     * P4-J2：{@link com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine} 审计（raw vs completed）。
     */
    private Map<String, Object> contractCompletionTrace;

    /** v2 协议纠错重试观测（debug / Harness；repair 成功后的 parse 与主链 {@link #querySemanticParse} 同源）。 */
    private Boolean querySemanticV2RepairAttempted;
    private Boolean querySemanticV2RepairSuccess;
    private String querySemanticV2RepairReason;

    /**
     * Harness：本轮若实际走了语义解析 LLM，则为对应 {@link com.nongxinle.ai.prompt.AiPromptRegistry} promptId；
     * 开关关闭或未调用时为 null。
     */
    private String promptRegistryId;

    /** 未调用 LLM / 关闭开关 / 占位网关 / 解析失败。 */
    @Builder.Default
    private boolean parseMissing = true;

    /**
     * 解析未失败且置信度达阈（缺省置信度视为 0）；用于决定是否允许短路 Java keyword follow-up、以及语义合并门禁。
     */
    public boolean isStructuralConfidenceOk(double minConfidence) {
        if (parseMissing) {
            return false;
        }
        double c = confidence != null ? confidence : 0d;
        return c >= minConfidence;
    }



    /** 参与语义 scope/门店收窄等：置信度过关且有意向或口述范围/多轮动作信号。 */
    public boolean isUsableForMerge(double minConfidence) {
        if (!isStructuralConfidenceOk(minConfidence)) {
            return false;
        }
        if (StringUtils.hasText(intent)) {
            return true;
        }
        RequestedScopePart rs = requestedScope;
        if (rs != null && StringUtils.hasText(rs.getRequestedScopeType())) {
            return true;
        }
        if (!effectiveMentionedStoreNames().isEmpty()) {
            return true;
        }
        if (Boolean.TRUE.equals(followUp)) {
            return true;
        }
        if (StringUtils.hasText(intentAction)
                || StringUtils.hasText(timeAction)
                || StringUtils.hasText(scopeAction)
                || StringUtils.hasText(metricAction)) {
            return true;
        }
        SemanticSlotsPart ss = semanticSlots;
        if (ss != null
                && (StringUtils.hasText(ss.getQueryObject())
                        || StringUtils.hasText(ss.getOperation())
                        || StringUtils.hasText(ss.getMetric())
                        || StringUtils.hasText(ss.getSourceFacet())
                        || StringUtils.hasText(ss.getAnchorPolicy()))) {
            return true;
        }
        return false;
    }

    /**
     * 合并 {@code requestedScope.mentionedStoreNames}（优先）与单字段 {@code mentionedStoreName}，去空白、去重、保序。
     */
    public List<String> effectiveMentionedStoreNames() {
        LinkedHashSet<String> dedup = new LinkedHashSet<>();
        RequestedScopePart rs = requestedScope;
        if (rs != null) {
            if (rs.getMentionedStoreNames() != null) {
                for (String n : rs.getMentionedStoreNames()) {
                    String t = sanitizeMentionedStoreNameToken(n);
                    if (t != null) {
                        dedup.add(t);
                    }
                }
            }
            String one = sanitizeMentionedStoreNameToken(rs.getMentionedStoreName());
            if (one != null) {
                dedup.add(one);
            }
        }
        return new ArrayList<>(dedup);
    }

    /**
     * 合并顶层 {@link #mentionedDishName} 与 {@code semanticSlots.mentionedDishName}（顶层优先）。
     * 不读 rawUserMessage；仅结构化 LLM 字段。
     */
    public String effectiveMentionedDishName() {
        if (StringUtils.hasText(mentionedDishName)) {
            return mentionedDishName.trim();
        }
        SemanticSlotsPart ss = semanticSlots;
        if (ss != null && StringUtils.hasText(ss.getMentionedDishName())) {
            return ss.getMentionedDishName().trim();
        }
        return null;
    }

    /**
     * 合并顶层 {@link #mentionedGoodsName} 与 {@code semanticSlots.mentionedGoodsName}（顶层优先）。
     * 不读 rawUserMessage；仅结构化 LLM 字段。
     */
    public String effectiveMentionedGoodsName() {
        if (StringUtils.hasText(mentionedGoodsName)) {
            return mentionedGoodsName.trim();
        }
        SemanticSlotsPart ss = semanticSlots;
        if (ss != null && StringUtils.hasText(ss.getMentionedGoodsName())) {
            return ss.getMentionedGoodsName().trim();
        }
        return null;
    }

    /** 仅读 semanticSlots.requestedTargetGrossMarginRate；Java 不得从原文 parse。 */
    public String effectiveRequestedTargetGrossMarginRate() {
        SemanticSlotsPart ss = semanticSlots;
        if (ss != null && StringUtils.hasText(ss.getRequestedTargetGrossMarginRate())) {
            return ss.getRequestedTargetGrossMarginRate().trim();
        }
        return null;
    }

    /**
     * v2 协议 repair 仅修正 schema 时，从 repair 前 parse 回填 DISH anchor（不读 rawMessage）。
     */
    public static AiQuerySemanticParseResult mergeDishAnchorIfAbsent(
            AiQuerySemanticParseResult beforeRepair, AiQuerySemanticParseResult afterRepair) {
        if (beforeRepair == null || afterRepair == null || afterRepair.isParseMissing()) {
            return afterRepair;
        }
        if (StringUtils.hasText(afterRepair.effectiveMentionedDishName())) {
            return afterRepair;
        }
        String dish = beforeRepair.effectiveMentionedDishName();
        if (!StringUtils.hasText(dish)) {
            return afterRepair;
        }
        var b = afterRepair.toBuilder();
        SemanticSlotsPart slots = afterRepair.getSemanticSlots();
        if (slots != null) {
            b.semanticSlots(
                    SemanticSlotsPart.builder()
                            .selectedContractId(slots.getSelectedContractId())
                            .queryObject(slots.getQueryObject())
                            .operation(slots.getOperation())
                            .metric(slots.getMetric())
                            .sourceFacet(slots.getSourceFacet())
                            .anchorPolicy(slots.getAnchorPolicy())
                            .detailWanted(slots.getDetailWanted())
                            .structuredIntentDetailWire(slots.getStructuredIntentDetailWire())
                            .answerPlanType(slots.getAnswerPlanType())
                            .mentionedDishName(dish)
                            .build());
        } else if (!StringUtils.hasText(afterRepair.getMentionedDishName())) {
            b.mentionedDishName(dish);
        }
        return b.build();
    }

    /** 口述店名：排除 null、空白、字面量 {@code "null"}（JSON/LLM 噪点）；不解析用户原文。 */
    public static String sanitizeMentionedStoreNameToken(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (!StringUtils.hasText(t)) {
            return null;
        }
        if ("null".equalsIgnoreCase(t)) {
            return null;
        }
        return t;
    }
}
