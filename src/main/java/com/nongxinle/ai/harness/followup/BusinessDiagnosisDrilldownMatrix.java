package com.nongxinle.ai.harness.followup;

import com.nongxinle.ai.agent.business.BusinessDiagnosisAgentV1;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.dto.business.DailyRevenueAnswerPlan;
import com.nongxinle.ai.dto.business.DiagnosisPlan;
import com.nongxinle.ai.dto.business.DishProfitAnswerPlan;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.dto.business.StockReduceAnswerPlan;
import lombok.experimental.UtilityClass;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Phase 1：经营诊断内门店下钻 + 子域归因确认（不接 Composite 主链、不扩 Composer）。
 */
@UtilityClass
public final class BusinessDiagnosisDrilldownMatrix {

    public static final String ANCHOR_STRATEGY_NONE = "NONE";
    public static final String ANCHOR_STRATEGY_EMIT_STORE = "EMIT_STORE";
    public static final String ANCHOR_STRATEGY_CONSUME_STORE = "CONSUME_STORE";

    public static final String FACET_SUMMARY = "SUMMARY";
    public static final String FACET_STORE_PRIORITY = "STORE_PRIORITY";
    public static final String FACET_STORE_RISK_REASONS = "STORE_RISK_REASONS";
    public static final String FACET_PURCHASE = "PURCHASE";
    public static final String FACET_STOCK_REDUCE = "STOCK_REDUCE";
    public static final String FACET_DISH_PROFIT = "DISH_PROFIT";
    public static final String FACET_ACTION = "ACTION";

    public static final String CHILD_PURCHASE = "PURCHASE";
    public static final String CHILD_STOCK_REDUCE = "STOCK_REDUCE";
    public static final String CHILD_DISH_PROFIT = "DISH_PROFIT";

    public static final String KNOWN_GAP_CHILD_PLAN_MISSING_PURCHASE =
            "DIAGNOSIS_CHILD_DOMAIN_PLAN_MISSING_PURCHASE";
    public static final String KNOWN_GAP_CHILD_PLAN_MISSING_STOCK_REDUCE =
            "DIAGNOSIS_CHILD_DOMAIN_PLAN_MISSING_STOCK_REDUCE";
    public static final String KNOWN_GAP_CHILD_PLAN_MISSING_DISH_PROFIT =
            "DIAGNOSIS_CHILD_DOMAIN_PLAN_MISSING_DISH_PROFIT";
    public static final String KNOWN_GAP_NO_FINDING_FOR_DOMAIN = "DIAGNOSIS_NO_FINDING_FOR_CHILD_DOMAIN";

    public static final String DEBUG_DOMAIN_ATTRIBUTION_LINES = "diagnosisDomainAttributionLines";

    private static final Pattern EXPLICIT_STORE_BEFORE_WHY =
            Pattern.compile("^([A-Za-z0-9\\u4e00-\\u9fa5]{2,24})(?:店|门店)?(?:为什么|不好)");

    /** 诊断 BD-C：承接上一轮 STORE 锚点的省略「为什么」追问（写入 ResolvedContext follow-up 字段）。 */
    public record DiagnosisStoreRiskFollowUpProbe(
            String followUpAction, String followUpTargetEntityType, String followUpTargetEntityName) {}

    public static final BusinessDiagnosisDrilldownMatrixRow SUMMARY =
            row(
                    "BD-A",
                    "GROUP",
                    "SUMMARY",
                    AiQuerySemanticLexicon.STRUCTURED_BUSINESS_DIAGNOSIS_SUMMARY,
                    FACET_SUMMARY,
                    null,
                    ANCHOR_STRATEGY_NONE,
                    false,
                    false,
                    null);

    public static final BusinessDiagnosisDrilldownMatrixRow STORE_PRIORITY_RANKING =
            row(
                    "BD-B",
                    "STORE",
                    "RANKING",
                    AiQuerySemanticLexicon.STRUCTURED_STORE_PRIORITY_RANKING,
                    FACET_STORE_PRIORITY,
                    BusinessDiagnosisAgentV1.DIAGNOSIS_QUESTION_STORE_PRIORITY_RANKING,
                    ANCHOR_STRATEGY_EMIT_STORE,
                    false,
                    false,
                    null);

    public static final BusinessDiagnosisDrilldownMatrixRow STORE_RISK_REASONS_INHERITED =
            row(
                    "BD-C",
                    "STORE",
                    "EXPLAIN",
                    AiQuerySemanticLexicon.STRUCTURED_STORE_RISK_REASONS_DRILLDOWN,
                    FACET_STORE_RISK_REASONS,
                    BusinessDiagnosisAgentV1.DIAGNOSIS_QUESTION_STORE_RISK_REASONS,
                    ANCHOR_STRATEGY_CONSUME_STORE,
                    false,
                    true,
                    null);

    public static final BusinessDiagnosisDrilldownMatrixRow STORE_RISK_REASONS_NAMED =
            row(
                    "BD-D",
                    "STORE",
                    "EXPLAIN",
                    AiQuerySemanticLexicon.STRUCTURED_STORE_RISK_REASONS_DRILLDOWN,
                    FACET_STORE_RISK_REASONS,
                    BusinessDiagnosisAgentV1.DIAGNOSIS_QUESTION_STORE_RISK_REASONS,
                    ANCHOR_STRATEGY_CONSUME_STORE,
                    true,
                    false,
                    null);

    public static final BusinessDiagnosisDrilldownMatrixRow STORE_DOMAIN_PURCHASE =
            domainRow(
                    "BD-E",
                    AiQuerySemanticLexicon.STRUCTURED_STORE_DOMAIN_ATTRIBUTION_PURCHASE,
                    CHILD_PURCHASE,
                    KNOWN_GAP_CHILD_PLAN_MISSING_PURCHASE);

    public static final BusinessDiagnosisDrilldownMatrixRow STORE_DOMAIN_STOCK_REDUCE =
            domainRow(
                    "BD-F",
                    AiQuerySemanticLexicon.STRUCTURED_STORE_DOMAIN_ATTRIBUTION_STOCK_REDUCE,
                    CHILD_STOCK_REDUCE,
                    KNOWN_GAP_CHILD_PLAN_MISSING_STOCK_REDUCE);

    public static final BusinessDiagnosisDrilldownMatrixRow STORE_DOMAIN_DISH_PROFIT =
            domainRow(
                    "BD-G",
                    AiQuerySemanticLexicon.STRUCTURED_STORE_DOMAIN_ATTRIBUTION_DISH_PROFIT,
                    CHILD_DISH_PROFIT,
                    KNOWN_GAP_CHILD_PLAN_MISSING_DISH_PROFIT);

    public static final BusinessDiagnosisDrilldownMatrixRow ACTION_FOLLOWUP =
            row(
                    "BD-K",
                    "STORE",
                    "ADVISE",
                    AiQuerySemanticLexicon.STRUCTURED_DIAGNOSIS_ACTION_FOLLOWUP,
                    FACET_ACTION,
                    "ACTION_FOLLOWUP",
                    ANCHOR_STRATEGY_CONSUME_STORE,
                    false,
                    true,
                    null);

    private static BusinessDiagnosisDrilldownMatrixRow row(
            String rowId,
            String queryObject,
            String operation,
            String wire,
            String facet,
            String questionType,
            String anchorStrategy,
            boolean requiresExplicitStore,
            boolean consumesPriorStore,
            String knownGap) {
        return BusinessDiagnosisDrilldownMatrixRow.builder()
                .rowId(rowId)
                .queryObject(queryObject)
                .operation(operation)
                .structuredIntentDetailWire(wire)
                .diagnosisFacet(facet)
                .diagnosisQuestionType(
                        StringUtils.hasText(questionType) ? questionType : facet)
                .childDomain(null)
                .resultAnchorStrategy(anchorStrategy)
                .requiresExplicitStoreNameInTurn(requiresExplicitStore)
                .consumesPriorStoreAnchor(consumesPriorStore)
                .knownGapCode(knownGap)
                .build();
    }

    private static BusinessDiagnosisDrilldownMatrixRow domainRow(
            String rowId, String wire, String childDomain, String planMissingGap) {
        return BusinessDiagnosisDrilldownMatrixRow.builder()
                .rowId(rowId)
                .queryObject("STORE")
                .operation("EXPLAIN")
                .structuredIntentDetailWire(wire)
                .diagnosisFacet(childDomain)
                .diagnosisQuestionType(childDomain)
                .childDomain(childDomain)
                .resultAnchorStrategy(ANCHOR_STRATEGY_CONSUME_STORE)
                .requiresExplicitStoreNameInTurn(false)
                .consumesPriorStoreAnchor(true)
                .knownGapCode(planMissingGap)
                .build();
    }

    /**
     * 诊断 path 多轮：上一轮已在 {@link AiResolvedQueryIntent#PATH_BUSINESS_DIAGNOSIS}，
     * 本句为 Matrix 子域归因 / 改进行动追问时，不得被销量 Matrix utterance pin（含「毛利」默认 wire）抢走。
     */
    public static boolean shouldBlockDishSalesMatrixUtterancePin(
            AiConversationTurnMemory previousTurn, String normalizedUserMessage) {
        if (!canAdoptDiagnosisDrilldownContinuation(previousTurn, normalizedUserMessage)) {
            return false;
        }
        BusinessDiagnosisDrilldownMatrixRow row = resolveRowFromMessage(normalizedUserMessage);
        return row != null && isDiagnosisContinuationPinRow(row);
    }

    public static boolean canAdoptDiagnosisDrilldownContinuation(
            AiConversationTurnMemory previousTurn, String normalizedUserMessage) {
        if (previousTurn == null || !StringUtils.hasText(previousTurn.getLastPathCode())) {
            return false;
        }
        return AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS.equals(previousTurn.getLastPathCode().trim());
    }

    /**
     * 仅凭本句问法解析 Matrix 行（Harness P1 契约问句）；不用于全局「经营得怎么样」→ overview 路由。
     */
    public static BusinessDiagnosisDrilldownMatrixRow resolveRowFromMessage(String normalizedUserMessage) {
        String msg = compactMessage(normalizedUserMessage);
        if (!StringUtils.hasText(msg)) {
            return null;
        }
        if (messageLooksLikeActionFollowup(msg)) {
            return ACTION_FOLLOWUP;
        }
        if (messageLooksLikeStoreDomainAttributionPurchase(msg)) {
            return STORE_DOMAIN_PURCHASE;
        }
        if (messageLooksLikeStoreDomainAttributionStockReduce(msg)) {
            return STORE_DOMAIN_STOCK_REDUCE;
        }
        if (messageLooksLikeStoreDomainAttributionDishProfit(msg)) {
            return STORE_DOMAIN_DISH_PROFIT;
        }
        if (messageLooksLikeStoreRiskReasonsNamed(msg)) {
            return STORE_RISK_REASONS_NAMED;
        }
        if (messageLooksLikeStoreRiskReasonsInherited(msg)) {
            return STORE_RISK_REASONS_INHERITED;
        }
        if (messageLooksLikeStorePriorityRanking(msg)) {
            return STORE_PRIORITY_RANKING;
        }
        if (messageLooksLikeBusinessDiagnosisSummary(msg)) {
            return SUMMARY;
        }
        return null;
    }

    public static BusinessDiagnosisDrilldownMatrixRow resolveRow(AiRunState state) {
        if (state == null || !state.isBusinessDiagnosisPath() || state.getResolvedQueryContext() == null) {
            return null;
        }
        BusinessDiagnosisDrilldownMatrixRow fromWire = resolveRowFromStructuredWire(state);
        String msg = normalizedUserMessage(state);
        BusinessDiagnosisDrilldownMatrixRow fromMsg = resolveRowFromMessage(msg);
        if (messageRowOverridesWireRow(fromWire, fromMsg)) {
            return fromMsg;
        }
        return fromWire;
    }

    private static BusinessDiagnosisDrilldownMatrixRow resolveRowFromStructuredWire(AiRunState state) {
        AiResolvedQueryIntent qi = state.getResolvedQueryContext().getQueryIntent();
        String wire = qi != null ? qi.getStructuredIntentDetail() : null;
        if (!StringUtils.hasText(wire)) {
            return null;
        }
        String canonical = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wire);

        if (AiQuerySemanticLexicon.isDiagnosisActionFollowupStructuredDetail(canonical)) {
            return ACTION_FOLLOWUP;
        }
        if (AiQuerySemanticLexicon.isStoreDomainAttributionPurchaseStructuredDetail(canonical)) {
            return STORE_DOMAIN_PURCHASE;
        }
        if (AiQuerySemanticLexicon.isStoreDomainAttributionStockReduceStructuredDetail(canonical)) {
            return STORE_DOMAIN_STOCK_REDUCE;
        }
        if (AiQuerySemanticLexicon.isStoreDomainAttributionDishProfitStructuredDetail(canonical)) {
            return STORE_DOMAIN_DISH_PROFIT;
        }
        if (AiQuerySemanticLexicon.isStorePriorityRankingStructuredDetail(canonical)) {
            return STORE_PRIORITY_RANKING;
        }
        if (AiQuerySemanticLexicon.isStoreRiskReasonsDrilldownStructuredDetail(canonical)) {
            BusinessDiagnosisDrilldownMatrixRow msgRow = resolveRowFromMessage(normalizedUserMessage(state));
            if (msgRow != null) {
                return msgRow;
            }
            return hasExplicitStoreNameInUserMessage(state)
                    ? STORE_RISK_REASONS_NAMED
                    : STORE_RISK_REASONS_INHERITED;
        }
        if (AiQuerySemanticLexicon.isBusinessDiagnosisSummaryStructuredDetail(canonical)) {
            return SUMMARY;
        }
        return null;
    }

    private static boolean isDiagnosisContinuationPinRow(BusinessDiagnosisDrilldownMatrixRow row) {
        if (row == null) {
            return false;
        }
        return row.getChildDomain() != null || ACTION_FOLLOWUP.equals(row);
    }

    /** 本句 Matrix 行是否应覆盖 resolved intent 上的 structured wire（如 SUMMARY inherit → 门店优先）。 */
    public static boolean shouldPreferMessageRowOverWire(
            String structuredIntentDetailWire, BusinessDiagnosisDrilldownMatrixRow msgRow) {
        if (msgRow == null) {
            return false;
        }
        BusinessDiagnosisDrilldownMatrixRow wireRow = rowFromCanonicalStructuredWire(structuredIntentDetailWire);
        return messageRowOverridesWireRow(wireRow, msgRow);
    }

    private static BusinessDiagnosisDrilldownMatrixRow rowFromCanonicalStructuredWire(String structuredIntentDetailWire) {
        if (!StringUtils.hasText(structuredIntentDetailWire)) {
            return null;
        }
        String canonical = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(structuredIntentDetailWire.trim());
        if (AiQuerySemanticLexicon.isDiagnosisActionFollowupStructuredDetail(canonical)) {
            return ACTION_FOLLOWUP;
        }
        if (AiQuerySemanticLexicon.isStoreDomainAttributionPurchaseStructuredDetail(canonical)) {
            return STORE_DOMAIN_PURCHASE;
        }
        if (AiQuerySemanticLexicon.isStoreDomainAttributionStockReduceStructuredDetail(canonical)) {
            return STORE_DOMAIN_STOCK_REDUCE;
        }
        if (AiQuerySemanticLexicon.isStoreDomainAttributionDishProfitStructuredDetail(canonical)) {
            return STORE_DOMAIN_DISH_PROFIT;
        }
        if (AiQuerySemanticLexicon.isStorePriorityRankingStructuredDetail(canonical)) {
            return STORE_PRIORITY_RANKING;
        }
        if (AiQuerySemanticLexicon.isStoreRiskReasonsDrilldownStructuredDetail(canonical)) {
            return STORE_RISK_REASONS_INHERITED;
        }
        if (AiQuerySemanticLexicon.isBusinessDiagnosisSummaryStructuredDetail(canonical)) {
            return SUMMARY;
        }
        return null;
    }

    private static boolean messageRowOverridesWireRow(
            BusinessDiagnosisDrilldownMatrixRow wireRow, BusinessDiagnosisDrilldownMatrixRow msgRow) {
        if (msgRow == null) {
            return false;
        }
        if (wireRow == null) {
            return true;
        }
        if (SUMMARY.equals(wireRow) && !SUMMARY.equals(msgRow)) {
            return true;
        }
        if (STORE_PRIORITY_RANKING.equals(msgRow)) {
            return true;
        }
        if (msgRow.getChildDomain() != null) {
            return true;
        }
        if (ACTION_FOLLOWUP.equals(msgRow)) {
            return true;
        }
        if (STORE_RISK_REASONS_INHERITED.equals(msgRow) && STORE_RISK_REASONS_NAMED.equals(wireRow)) {
            return true;
        }
        if (STORE_RISK_REASONS_INHERITED.equals(msgRow) || STORE_RISK_REASONS_NAMED.equals(msgRow)) {
            return SUMMARY.equals(wireRow);
        }
        return false;
    }

    /**
     * BD-C：本句无显式店名、wire 为门店风险原因下钻时，从上一轮 {@link AiConversationTurnMemory#getLastResultAnchors()}
     * 消费 STORE 锚点并填充 follow-up 协议字段。
     */
    public static DiagnosisStoreRiskFollowUpProbe probeStoreRiskReasonsInheritedFollowUp(
            AiConversationTurnMemory previousTurn,
            AiResolvedQueryIntent queryIntent,
            String normalizedUserMessage) {
        if (previousTurn == null || queryIntent == null) {
            return null;
        }
        if (!AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS.equals(
                nullToEmpty(queryIntent.getPathCode()).trim())) {
            return null;
        }
        String wire =
                AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                        queryIntent.getStructuredIntentDetail());
        if (!AiQuerySemanticLexicon.isStoreRiskReasonsDrilldownStructuredDetail(wire)) {
            return null;
        }
        if (!STORE_RISK_REASONS_INHERITED.equals(resolveRowFromMessage(normalizedUserMessage))) {
            return null;
        }
        AiResultAnchor storeAnchor = firstStoreAnchorFromPreviousTurn(previousTurn);
        if (storeAnchor == null || !StringUtils.hasText(storeAnchor.getEntityName())) {
            return null;
        }
        return new DiagnosisStoreRiskFollowUpProbe(
                "DETAIL_DRILLDOWN",
                AiResultAnchor.ENTITY_TYPE_STORE,
                storeAnchor.getEntityName().trim());
    }

    public static AiResultAnchor firstStoreAnchorFromPreviousTurn(AiConversationTurnMemory previousTurn) {
        if (previousTurn == null || previousTurn.getLastResultAnchors() == null) {
            return null;
        }
        for (AiResultAnchor anchor : previousTurn.getLastResultAnchors()) {
            if (anchor == null || !StringUtils.hasText(anchor.getEntityType())) {
                continue;
            }
            if (AiResultAnchor.ENTITY_TYPE_STORE.equalsIgnoreCase(anchor.getEntityType().trim())
                    && StringUtils.hasText(anchor.getEntityName())) {
                return anchor;
            }
        }
        return null;
    }

    private static String normalizedUserMessage(AiRunState state) {
        String q = state.getNormalizedUserInput();
        if (!StringUtils.hasText(q)) {
            q = state.getRawUserInput();
        }
        return q != null ? q.trim() : "";
    }

    private static String compactMessage(String normalizedUserMessage) {
        if (!StringUtils.hasText(normalizedUserMessage)) {
            return "";
        }
        return normalizedUserMessage.trim().replaceAll("\\s+", "");
    }

    private static boolean messageLooksLikeActionFollowup(String msg) {
        return msg.contains("怎么改")
                || msg.contains("怎么改善")
                || msg.contains("怎么调整")
                || msg.contains("如何改")
                || msg.contains("如何改善");
    }

    private static boolean messageLooksLikeStoreDomainAttributionPurchase(String msg) {
        return msg.contains("采购") && (msg.contains("问题") || msg.contains("吗") || msg.endsWith("么"));
    }

    private static boolean messageLooksLikeStoreDomainAttributionStockReduce(String msg) {
        return (msg.contains("出库") || msg.contains("核销"))
                && (msg.contains("问题") || msg.contains("吗") || msg.endsWith("么"));
    }

    private static boolean messageLooksLikeStoreDomainAttributionDishProfit(String msg) {
        if (msg.contains("毛利") && (msg.contains("问题") || msg.contains("吗") || msg.endsWith("么"))) {
            return true;
        }
        return (msg.contains("利润") || msg.contains("毛利率"))
                && (msg.contains("问题") || msg.contains("吗") || msg.endsWith("么"));
    }

    private static boolean messageLooksLikeStoreRiskReasonsInherited(String msg) {
        if (StringUtils.hasText(extractExplicitStoreLabelFromMessage(msg))) {
            return false;
        }
        return msg.contains("为什么") || msg.contains("怎么回事");
    }

    private static boolean messageLooksLikeStoreRiskReasonsNamed(String msg) {
        return StringUtils.hasText(extractExplicitStoreLabelFromMessage(msg));
    }

    /** BD-D：仅当用户原文显式带出店名（如「AAA 为什么不好」），不读 semantic inherit 的 mentionedStore。 */
    static String extractExplicitStoreLabelFromMessage(String normalizedUserMessage) {
        String compact = compactMessage(normalizedUserMessage);
        if (!StringUtils.hasText(compact)) {
            return null;
        }
        Matcher m = EXPLICIT_STORE_BEFORE_WHY.matcher(compact);
        if (!m.find()) {
            return null;
        }
        String label = m.group(1);
        if (!StringUtils.hasText(label)) {
            return null;
        }
        String trimmed = label.trim();
        if (trimmed.contains("哪个") || trimmed.contains("哪家") || trimmed.contains("哪间")) {
            return null;
        }
        return trimmed;
    }

    private static boolean hasExplicitStoreNameInUserMessage(AiRunState state) {
        return StringUtils.hasText(extractExplicitStoreLabelFromMessage(normalizedUserMessage(state)));
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static boolean messageLooksLikeStorePriorityRanking(String msg) {
        return msg.contains("哪个门店问题最大")
                || msg.contains("哪家门店问题最大")
                || msg.contains("哪个店问题最大")
                || msg.contains("哪家店问题最大")
                || msg.contains("哪个店问题最多")
                || msg.contains("哪家店问题最多")
                || msg.contains("哪个门店问题最多")
                || msg.contains("哪个店风险最高")
                || msg.contains("哪家店风险最高")
                || msg.contains("哪个门店风险最高");
    }

    private static boolean messageLooksLikeBusinessDiagnosisSummary(String msg) {
        return msg.contains("经营诊断")
                || msg.contains("做一下诊断")
                || msg.contains("做经营诊断");
    }

    public static void applyResolvedRow(
            AiRunState state,
            DiagnosisPlan plan,
            BusinessDiagnosisDrilldownMatrixRow row,
            PurchaseAnswerPlan pPurchase,
            StockReduceAnswerPlan pStock,
            DishProfitAnswerPlan pDish,
            DailyRevenueAnswerPlan pRevenue) {
        if (state == null || plan == null || row == null) {
            return;
        }
        stampMatrixDebug(plan, row, null);

        if (STORE_PRIORITY_RANKING.equals(row)) {
            applyStorePriorityRanking(state, plan, pRevenue, pPurchase, pStock);
            return;
        }
        if (STORE_RISK_REASONS_INHERITED.equals(row) || STORE_RISK_REASONS_NAMED.equals(row)) {
            applyStoreRiskReasons(state, plan, pRevenue, pPurchase, pStock);
            return;
        }
        if (row.getChildDomain() != null) {
            applyChildDomainAttribution(state, plan, row, pPurchase, pStock, pDish);
            return;
        }
        if (ACTION_FOLLOWUP.equals(row)) {
            applyActionFollowup(state, plan);
        }
    }

    public static void stampMatrixDebug(
            DiagnosisPlan plan, BusinessDiagnosisDrilldownMatrixRow row, String knownGapOverride) {
        if (plan == null || row == null) {
            return;
        }
        Map<String, Object> dbg = plan.getDebug();
        dbg.put(BusinessDiagnosisAgentV1.DEBUG_DIAGNOSIS_DRILLDOWN_MATRIX_ROW_ID, row.getRowId());
        dbg.put(BusinessDiagnosisAgentV1.DEBUG_DIAGNOSIS_FACET, row.getDiagnosisFacet());
        dbg.put(BusinessDiagnosisAgentV1.DEBUG_DIAGNOSIS_QUESTION_TYPE, row.getDiagnosisQuestionType());
        if (row.getChildDomain() != null) {
            dbg.put(BusinessDiagnosisAgentV1.DEBUG_DIAGNOSIS_CHILD_DOMAIN, row.getChildDomain());
        } else {
            dbg.remove(BusinessDiagnosisAgentV1.DEBUG_DIAGNOSIS_CHILD_DOMAIN);
        }
        String gap = knownGapOverride != null ? knownGapOverride : row.getKnownGapCode();
        if (StringUtils.hasText(gap)) {
            dbg.put(BusinessDiagnosisAgentV1.DEBUG_DIAGNOSIS_KNOWN_GAP, gap);
        } else {
            dbg.remove(BusinessDiagnosisAgentV1.DEBUG_DIAGNOSIS_KNOWN_GAP);
        }
    }

    /**
     * Harness 兜底：无 structured wire 时，仅当用户原文形似「哪个门店问题最大」类问法。
     */
    public static boolean isStorePriorityHarnessTextFallback(AiRunState state) {
        if (state == null || !state.isBusinessDiagnosisPath()) {
            return false;
        }
        AiResolvedQueryIntent qi =
                state.getResolvedQueryContext() != null
                        ? state.getResolvedQueryContext().getQueryIntent()
                        : null;
        String wire = qi != null ? qi.getStructuredIntentDetail() : null;
        if (StringUtils.hasText(wire)) {
            return false;
        }
        return messageLooksLikeStorePriorityRanking(compactMessage(normalizedUserMessage(state)));
    }

    private static void applyStorePriorityRanking(
            AiRunState state,
            DiagnosisPlan plan,
            DailyRevenueAnswerPlan pRevenue,
            PurchaseAnswerPlan pPurchase,
            StockReduceAnswerPlan pStock) {
        Map<String, Object> dbg = plan.getDebug();
        List<String> reasonTitles = collectReasonTitles(plan);
        dbg.put(
                BusinessDiagnosisAgentV1.DEBUG_DIAGNOSIS_TOP_STORE_REASONS,
                reasonTitles.isEmpty() ? List.of() : reasonTitles);
        dbg.put(BusinessDiagnosisAgentV1.DEBUG_DIAGNOSIS_RANKING_ROWS_COUNT, plan.getRiskRows().size());

        String topStore =
                BusinessDiagnosisAgentV1.extractStoreNameForStorePriorityRanking(
                        plan, pRevenue, pPurchase, pStock, dbg);
        stampTargetStore(plan, topStore);

        List<AiResultAnchor> ra = new ArrayList<>();
        if (StringUtils.hasText(topStore)) {
            ra.add(
                    AiResultAnchor.builder()
                            .entityType(AiResultAnchor.ENTITY_TYPE_STORE)
                            .entityName(topStore.trim())
                            .rank(1)
                            .sourcePlanType(DiagnosisPlan.ANCHOR_SOURCE_STORE_PRIORITY_RANKING)
                            .metric("priority")
                            .build());
        }
        plan.setResultAnchors(ra);
    }

    private static void applyStoreRiskReasons(
            AiRunState state,
            DiagnosisPlan plan,
            DailyRevenueAnswerPlan pRevenue,
            PurchaseAnswerPlan pPurchase,
            StockReduceAnswerPlan pStock) {
        Map<String, Object> dbg = plan.getDebug();
        List<String> reasonTitles = collectReasonTitles(plan);
        dbg.put(
                BusinessDiagnosisAgentV1.DEBUG_DIAGNOSIS_TOP_STORE_REASONS,
                reasonTitles.isEmpty() ? List.of() : reasonTitles);
        dbg.put(BusinessDiagnosisAgentV1.DEBUG_DIAGNOSIS_RANKING_ROWS_COUNT, plan.getRiskRows().size());

        String anchorStore = resolveTargetStoreName(state, plan, pRevenue, pPurchase, pStock);
        stampTargetStore(plan, anchorStore);
        plan.setResultAnchors(new ArrayList<>());
    }

    private static void applyChildDomainAttribution(
            AiRunState state,
            DiagnosisPlan plan,
            BusinessDiagnosisDrilldownMatrixRow row,
            PurchaseAnswerPlan pPurchase,
            StockReduceAnswerPlan pStock,
            DishProfitAnswerPlan pDish) {
        String child = row.getChildDomain();
        String gap = null;
        if (CHILD_PURCHASE.equals(child) && pPurchase == null) {
            gap = KNOWN_GAP_CHILD_PLAN_MISSING_PURCHASE;
        } else if (CHILD_STOCK_REDUCE.equals(child) && pStock == null) {
            gap = KNOWN_GAP_CHILD_PLAN_MISSING_STOCK_REDUCE;
        } else if (CHILD_DISH_PROFIT.equals(child) && pDish == null) {
            gap = KNOWN_GAP_CHILD_PLAN_MISSING_DISH_PROFIT;
        }
        stampMatrixDebug(plan, row, gap);

        String anchorStore = resolveTargetStoreName(state, plan, null, null, null);
        stampTargetStore(plan, anchorStore);

        List<String> lines = filterDomainAttributionLines(plan, child);
        if (lines.isEmpty() && gap == null) {
            gap = KNOWN_GAP_NO_FINDING_FOR_DOMAIN;
            stampMatrixDebug(plan, row, gap);
        }
        plan.getDebug().put(DEBUG_DOMAIN_ATTRIBUTION_LINES, lines);
    }

    private static void applyActionFollowup(AiRunState state, DiagnosisPlan plan) {
        String anchorStore = resolveTargetStoreName(state, plan, null, null, null);
        stampTargetStore(plan, anchorStore);
        plan.getDebug().put(DEBUG_DOMAIN_ATTRIBUTION_LINES, List.of());
    }

    private static List<String> filterDomainAttributionLines(DiagnosisPlan plan, String childDomain) {
        List<String> lines = new ArrayList<>();
        if (plan.getFocusFindings() == null) {
            return lines;
        }
        for (Map<String, Object> f : plan.getFocusFindings()) {
            if (f == null || "NO_MAJOR_FINDING".equals(f.get("findingType"))) {
                continue;
            }
            if (!findingMatchesChildDomain(f, childDomain)) {
                continue;
            }
            String ttl = Objects.toString(f.get("title"), "").trim();
            String det = Objects.toString(f.get("detail"), "").trim();
            if (!ttl.isEmpty()) {
                lines.add(det.isEmpty() ? ttl : ttl + "：" + det);
            }
        }
        return lines;
    }

    private static boolean findingMatchesChildDomain(Map<String, Object> f, String childDomain) {
        String ev = Objects.toString(f.get("evidenceSource"), "").trim().toUpperCase(Locale.ROOT);
        String ft = Objects.toString(f.get("findingType"), "").trim();
        if (CHILD_PURCHASE.equals(childDomain)) {
            return "PURCHASE".equals(ev)
                    || "PURCHASE_PRESSURE".equals(ft)
                    || ("MULTI_DOMAIN".equals(ev) && ft.contains("PURCHASE"));
        }
        if (CHILD_STOCK_REDUCE.equals(childDomain)) {
            return "STOCK_REDUCE".equals(ev)
                    || "COST_PRESSURE".equals(ft)
                    || "STOCK_REDUCE_ABNORMAL".equals(ft)
                    || ("MULTI_DOMAIN".equals(ev) && (ft.contains("COST") || ft.contains("STOCK")));
        }
        if (CHILD_DISH_PROFIT.equals(childDomain)) {
            return "DISH_PROFIT".equals(ev)
                    || "LOW_DISH_MARGIN".equals(ft)
                    || "PROFIT_QUALITY_RISK".equals(ft);
        }
        return false;
    }

    private static String resolveTargetStoreName(
            AiRunState state,
            DiagnosisPlan plan,
            DailyRevenueAnswerPlan pRevenue,
            PurchaseAnswerPlan pPurchase,
            StockReduceAnswerPlan pStock) {
        if (state != null) {
            String fromUserMessage = extractExplicitStoreLabelFromMessage(normalizedUserMessage(state));
            if (StringUtils.hasText(fromUserMessage)) {
                return fromUserMessage.trim();
            }
            if (state.getResolvedQueryContext() != null) {
                String fn = state.getResolvedQueryContext().getFollowUpTargetEntityName();
                if (StringUtils.hasText(fn)) {
                    return fn.trim();
                }
            }
        }
        return BusinessDiagnosisAgentV1.extractStoreNameForStorePriorityRanking(
                plan, pRevenue, pPurchase, pStock);
    }

    private static void stampTargetStore(DiagnosisPlan plan, String storeName) {
        Map<String, Object> dbg = plan.getDebug();
        if (StringUtils.hasText(storeName)) {
            String s = storeName.trim();
            dbg.put(BusinessDiagnosisAgentV1.DEBUG_DIAGNOSIS_TOP_STORE_NAME, s);
            dbg.put(BusinessDiagnosisAgentV1.DEBUG_DIAGNOSIS_TARGET_STORE_NAME, s);
        } else {
            dbg.put(BusinessDiagnosisAgentV1.DEBUG_DIAGNOSIS_TOP_STORE_NAME, null);
            dbg.put(BusinessDiagnosisAgentV1.DEBUG_DIAGNOSIS_TARGET_STORE_NAME, null);
        }
    }

    private static List<String> collectReasonTitles(DiagnosisPlan plan) {
        List<String> reasonTitles = new ArrayList<>();
        for (Map<String, Object> f : plan.getFocusFindings()) {
            if (f == null || "NO_MAJOR_FINDING".equals(f.get("findingType"))) {
                continue;
            }
            String ttl = Objects.toString(f.get("title"), "").trim();
            if (!ttl.isEmpty()) {
                reasonTitles.add(ttl);
            }
        }
        return reasonTitles;
    }
}
