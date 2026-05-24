package com.nongxinle.ai.semantic.matrix;

import com.nongxinle.ai.agent.business.BusinessDiagnosisAgentV1;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.DiagnosisPlan;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.dto.business.DailyRevenueAnswerPlan;
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
 * Phase 1：经营诊断 Matrix — contract-locked capability registry（contractId → row 查表、wire → row 查表、wire → planType 查表）。
 * <p>
 * P1 清理后移除了 non-contract-locked legacy 推断（slots→wire、metric.contains、text contains、
 * message→row 覆盖等）。非 contract-locked 时直接返回 null（known gap / early exit），
 * 不提供 fallback。contract-locked 路径通过 selectedContractId → ACTIVE entry 或
 * canonical wire 查表驱动。
 */
@UtilityClass
public final class BusinessDiagnosisSemanticCapabilityMatrix {

    public static final String MATRIX_WIRE_MISSING = "MATRIX_WIRE_MISSING";

    public static final String ANCHOR_STRATEGY_NONE = "NONE";
    public static final String ANCHOR_STRATEGY_EMIT_STORE = "EMIT_STORE";
    public static final String ANCHOR_STRATEGY_CONSUME_STORE = "CONSUME_STORE";

    public static final String FACET_SUMMARY = "SUMMARY";
    public static final String FACET_PROBLEM = "PROBLEM";
    public static final String FACET_RISK = "RISK";
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

    /** P2H：门店优先排行 / 门店对比等扩展诊断不在 contract-entry 主链。 */
    public static final String KNOWN_GAP_EXTENDED_DIAGNOSIS_NOT_IN_P2H =
            "BUSINESS_DIAGNOSIS_EXTENDED_NOT_IN_P2H";

    public static final String DEBUG_DOMAIN_ATTRIBUTION_LINES = "diagnosisDomainAttributionLines";

    private static final Pattern EXPLICIT_STORE_BEFORE_WHY =
            Pattern.compile("^([A-Za-z0-9\\u4e00-\\u9fa5]{2,24})(?:店|门店)?(?:为什么|不好)");

    public static final BusinessDiagnosisSemanticCapabilityMatrixRow SUMMARY =
            row("BD-A", "GROUP", "SUMMARY", AiQuerySemanticLexicon.STRUCTURED_BUSINESS_DIAGNOSIS_SUMMARY, FACET_SUMMARY, null, null);

    public static final BusinessDiagnosisSemanticCapabilityMatrixRow PROBLEM_SUMMARY =
            row("BD-I", "GROUP", "DIAGNOSIS", AiQuerySemanticLexicon.STRUCTURED_BUSINESS_DIAGNOSIS_SUMMARY, FACET_PROBLEM, null, null);

    public static final BusinessDiagnosisSemanticCapabilityMatrixRow RISK_SUMMARY =
            row("BD-J", "GROUP", "ANOMALY", AiQuerySemanticLexicon.STRUCTURED_BUSINESS_DIAGNOSIS_SUMMARY, FACET_RISK, null, null);

    public static final BusinessDiagnosisSemanticCapabilityMatrixRow STORE_PRIORITY_RANKING =
            row("BD-B", "STORE", "RANKING", AiQuerySemanticLexicon.STRUCTURED_STORE_PRIORITY_RANKING, FACET_STORE_PRIORITY, BusinessDiagnosisAgentV1.DIAGNOSIS_QUESTION_STORE_PRIORITY_RANKING, KNOWN_GAP_EXTENDED_DIAGNOSIS_NOT_IN_P2H);

    public static final BusinessDiagnosisSemanticCapabilityMatrixRow STORE_RISK_REASONS_INHERITED =
            row("BD-C", "STORE", "EXPLAIN", AiQuerySemanticLexicon.STRUCTURED_STORE_RISK_REASON_EXPLANATION, FACET_STORE_RISK_REASONS, BusinessDiagnosisAgentV1.DIAGNOSIS_QUESTION_STORE_RISK_REASONS, null);

    public static final BusinessDiagnosisSemanticCapabilityMatrixRow STORE_RISK_REASONS_NAMED =
            row("BD-D", "STORE", "EXPLAIN", AiQuerySemanticLexicon.STRUCTURED_STORE_RISK_REASON_EXPLANATION, FACET_STORE_RISK_REASONS, BusinessDiagnosisAgentV1.DIAGNOSIS_QUESTION_STORE_RISK_REASONS, null);

    public static final BusinessDiagnosisSemanticCapabilityMatrixRow STORE_DOMAIN_PURCHASE =
            domainRow(
                    "BD-E",
                    AiQuerySemanticLexicon.STRUCTURED_STORE_DOMAIN_ATTRIBUTION_PURCHASE,
                    CHILD_PURCHASE,
                    KNOWN_GAP_CHILD_PLAN_MISSING_PURCHASE);

    public static final BusinessDiagnosisSemanticCapabilityMatrixRow STORE_DOMAIN_STOCK_REDUCE =
            domainRow(
                    "BD-F",
                    AiQuerySemanticLexicon.STRUCTURED_STORE_DOMAIN_ATTRIBUTION_STOCK_REDUCE,
                    CHILD_STOCK_REDUCE,
                    KNOWN_GAP_CHILD_PLAN_MISSING_STOCK_REDUCE);

    public static final BusinessDiagnosisSemanticCapabilityMatrixRow STORE_DOMAIN_DISH_PROFIT =
            domainRow(
                    "BD-G",
                    AiQuerySemanticLexicon.STRUCTURED_STORE_DOMAIN_ATTRIBUTION_DISH_PROFIT,
                    CHILD_DISH_PROFIT,
                    KNOWN_GAP_CHILD_PLAN_MISSING_DISH_PROFIT);

    public static final BusinessDiagnosisSemanticCapabilityMatrixRow ACTION_SUGGESTION =
            row("BD-K", "STORE", "ADVISE", AiQuerySemanticLexicon.STRUCTURED_DIAGNOSIS_ACTION_SUGGESTION, FACET_ACTION, "ACTION_SUGGESTION", null);

    public static final BusinessDiagnosisSemanticCapabilityMatrixRow STORE_COMPARE_DIAGNOSIS =
            row("BD-H", "STORE", "COMPARE", AiQuerySemanticLexicon.STRUCTURED_BUSINESS_STORE_COMPARE_DIAGNOSIS, FACET_SUMMARY, DiagnosisPlan.TYPE_OVERALL_BUSINESS_DIAGNOSIS, KNOWN_GAP_EXTENDED_DIAGNOSIS_NOT_IN_P2H);

    private static BusinessDiagnosisSemanticCapabilityMatrixRow row(
            String rowId,
            String queryObject,
            String operation,
            String wire,
            String facet,
            String questionType,
            String knownGap) {
        return BusinessDiagnosisSemanticCapabilityMatrixRow.builder()
                .rowId(rowId)
                .queryObject(queryObject)
                .operation(operation)
                .structuredIntentDetailWire(wire)
                .diagnosisFacet(facet)
                .diagnosisQuestionType(
                        StringUtils.hasText(questionType) ? questionType : facet)
                .childDomain(null)
                .knownGapCode(knownGap)
                .build();
    }

    private static BusinessDiagnosisSemanticCapabilityMatrixRow domainRow(
            String rowId, String wire, String childDomain, String planMissingGap) {
        return BusinessDiagnosisSemanticCapabilityMatrixRow.builder()
                .rowId(rowId)
                .queryObject("STORE")
                .operation("EXPLAIN")
                .structuredIntentDetailWire(wire)
                .diagnosisFacet(childDomain)
                .diagnosisQuestionType(childDomain)
                .childDomain(childDomain)
                .knownGapCode(planMissingGap)
                .build();
    }

    // resolveRowFromMessage DELETED — BUSINESS-DIAGNOSIS-OVERVIEW-SEMANTIC-MERGE-CLEAN-P1
    // non-contract-locked text-based row resolution removed; use contractId → row lookup.

    /**
     * Contract-locked only：通过 selectedContractId → ACTIVE entry 或 structured wire canonical 查表。
     * 非 contract-locked 时直接返回 null（known gap / early exit）。
     */
    public static BusinessDiagnosisSemanticCapabilityMatrixRow resolveRow(AiRunState state) {
        if (state == null || !state.isBusinessDiagnosisPath() || state.getResolvedQueryContext() == null) {
            return null;
        }
        if (isContractLockedDiagnosisState(state)) {
            BusinessDiagnosisSemanticCapabilityMatrixRow fromContract =
                    rowFromActiveContractId(selectedContractId(state));
            if (fromContract != null) {
                return fromContract;
            }
            return resolveRowFromStructuredWireContractLocked(state);
        }
        // non-contract-locked: no fallback, return null (early exit / known gap)
        return null;
    }

    /** contract-locked：仅 structured wire / selectedContractId，不读用户原文。 */
    private static BusinessDiagnosisSemanticCapabilityMatrixRow resolveRowFromStructuredWireContractLocked(
            AiRunState state) {
        AiResolvedQueryIntent qi = state.getResolvedQueryContext().getQueryIntent();
        String wire = qi != null ? qi.getStructuredIntentDetail() : null;
        if (!StringUtils.hasText(wire)) {
            return null;
        }
        String canonical = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wire);
        BusinessDiagnosisSemanticCapabilityMatrixRow row = rowFromCanonicalStructuredWire(canonical);
        if (row != null && row.getKnownGapCode() == null) {
            return row;
        }
        return null;
    }

    private static boolean isContractLockedDiagnosisState(AiRunState state) {
        if (state == null || state.getResolvedQueryContext() == null) {
            return false;
        }
        AiQuerySemanticParseResult parse = state.getResolvedQueryContext().getQuerySemanticParse();
        return com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine.isContractLockedParse(parse);
    }

    private static String selectedContractId(AiRunState state) {
        if (state == null || state.getResolvedQueryContext() == null) {
            return null;
        }
        AiQuerySemanticParseResult parse = state.getResolvedQueryContext().getQuerySemanticParse();
        return com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine.extractSelectedContractId(parse);
    }

    /** ACTIVE contractId → Matrix 行（contract-entry 注册表，非 NL 推断）。 */
    public static BusinessDiagnosisSemanticCapabilityMatrixRow rowFromActiveContractId(String contractId) {
        if (!StringUtils.hasText(contractId)) {
            return null;
        }
        return switch (contractId.trim()) {
            case "business_diagnosis.summary" -> SUMMARY;
            case "business_diagnosis.problem_summary" -> PROBLEM_SUMMARY;
            case "business_diagnosis.risk_summary" -> RISK_SUMMARY;
            case "business_diagnosis.suggestion_summary" -> ACTION_SUGGESTION;
            case "business_diagnosis.store_risk_reasons_inherited" -> STORE_RISK_REASONS_INHERITED;
            case "business_diagnosis.store_risk_reasons_named" -> STORE_RISK_REASONS_NAMED;
            default -> null;
        };
    }

    // resolveRowFromStructuredWire DELETED — BUSINESS-DIAGNOSIS-OVERVIEW-SEMANTIC-MERGE-CLEAN-P1
    // non-contract-locked wire→row fallback removed; use resolveRowFromStructuredWireContractLocked for contract-locked.

    // shouldPreferMessageRowOverWire DELETED — BUSINESS-DIAGNOSIS-OVERVIEW-SEMANTIC-MERGE-CLEAN-P1
    // non-contract-locked message→row override removed; use contract entry for row resolution.

    private static BusinessDiagnosisSemanticCapabilityMatrixRow rowFromCanonicalStructuredWire(String structuredIntentDetailWire) {
        if (!StringUtils.hasText(structuredIntentDetailWire)) {
            return null;
        }
        String canonical = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(structuredIntentDetailWire.trim());
        if (AiQuerySemanticLexicon.isDiagnosisActionSuggestionStructuredDetail(canonical)) {
            return ACTION_SUGGESTION;
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
        if (AiQuerySemanticLexicon.isStoreRiskReasonExplanationStructuredDetail(canonical)) {
            return STORE_RISK_REASONS_INHERITED;
        }
        if (AiQuerySemanticLexicon.isBusinessDiagnosisSummaryStructuredDetail(canonical)) {
            return SUMMARY;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_BUSINESS_STORE_COMPARE_DIAGNOSIS.equals(canonical)) {
            return STORE_COMPARE_DIAGNOSIS;
        }
        if (isDualDomainPurchaseStockWire(canonical)) {
            return SUMMARY;
        }
        return null;
    }

    // messageRowOverridesWireRow DELETED — BUSINESS-DIAGNOSIS-OVERVIEW-SEMANTIC-MERGE-CLEAN-P1
    // non-contract-locked message→row override removed; use contract entry for row resolution.

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

    // messageLooksLikeStoreRiskReasonsNamed DELETED — BUSINESS-DIAGNOSIS-OVERVIEW-SEMANTIC-MERGE-CLEAN-P1

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

    // hasExplicitStoreNameInUserMessage DELETED — BUSINESS-DIAGNOSIS-OVERVIEW-SEMANTIC-MERGE-CLEAN-P1

    // nullToEmpty DELETED — BUSINESS-DIAGNOSIS-OVERVIEW-SEMANTIC-MERGE-CLEAN-P1

    // messageLooksLikeStorePriorityRanking DELETED — BUSINESS-DIAGNOSIS-OVERVIEW-SEMANTIC-MERGE-CLEAN-P1
    // non-contract-locked text contains inference removed.

    // messageLooksLikeBusinessDiagnosisSummary DELETED — BUSINESS-DIAGNOSIS-OVERVIEW-SEMANTIC-MERGE-CLEAN-P1
    // non-contract-locked text contains inference removed.

    public static BusinessDiagnosisSemanticCapabilityMatrixRow resolveMatrixRow(
            String pathCode, String wire, AiQuerySemanticParseResult sem) {
        if (!AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS.equals(pathCode)) {
            return null;
        }
        if (sem != null
                && com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine.isContractLockedParse(sem)) {
            String contractId =
                    com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine.extractSelectedContractId(sem);
            BusinessDiagnosisSemanticCapabilityMatrixRow fromContract = rowFromActiveContractId(contractId);
            if (fromContract != null) {
                return fromContract;
            }
            String canon =
                    StringUtils.hasText(wire)
                            ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wire.trim())
                            : null;
            if (StringUtils.hasText(canon)) {
                BusinessDiagnosisSemanticCapabilityMatrixRow row = rowFromCanonicalStructuredWire(canon);
                return row != null && row.getKnownGapCode() == null ? row : null;
            }
            return null;
        }
        // non-contract-locked: only canonical wire lookup; no slots→row inference
        String canon =
                StringUtils.hasText(wire)
                        ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wire.trim())
                        : null;
        if (!StringUtils.hasText(canon)) {
            return null;
        }
        BusinessDiagnosisSemanticCapabilityMatrixRow row = rowFromCanonicalStructuredWire(canon);
        if (row != null) {
            return row;
        }
        if (AiQuerySemanticLexicon.isStructuredBusinessDiagnosisDetail(canon)) {
            return SUMMARY;
        }
        return null;
    }

    // resolveStructuredIntentDetailWire DELETED — BUSINESS-DIAGNOSIS-OVERVIEW-SEMANTIC-MERGE-CLEAN-P1
    // non-contract-locked slots→wire resolution removed; contract-locked path uses selectedContractId → ACTIVE entry.

    // adoptWireViaMatrix DELETED — BUSINESS-DIAGNOSIS-OVERVIEW-SEMANTIC-MERGE-CLEAN-P1

    // inferMatrixWireFromSemanticSlots DELETED — BUSINESS-DIAGNOSIS-OVERVIEW-SEMANTIC-MERGE-CLEAN-P1
    // non-contract-locked slots→wire inference removed; use contractId → ACTIVE entry or canonical wire lookup.

    // inferRowFromSemanticSlots DELETED — BUSINESS-DIAGNOSIS-OVERVIEW-SEMANTIC-MERGE-CLEAN-P1
    // non-contract-locked slots→row inference removed; use contractId → row lookup instead.

    public static String targetAnswerPlanTypeForWire(String wire) {
        BusinessDiagnosisSemanticCapabilityMatrixRow row =
                resolveMatrixRow(
                        AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS, wire, null);
        if (row == null) {
            return null;
        }
        if (StringUtils.hasText(row.getDiagnosisQuestionType())) {
            return row.getDiagnosisQuestionType();
        }
        return DiagnosisPlan.TYPE_OVERALL_BUSINESS_DIAGNOSIS;
    }

    public static boolean isDualDomainPurchaseStockWire(String wire) {
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wire);
        if (!StringUtils.hasText(canon)) {
            return false;
        }
        return AiQuerySemanticLexicon.STRUCTURED_PURCHASE_STOCK_REDUCE_MISMATCH.equals(canon)
                || AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SLOW_MOVING_RISK.equals(canon)
                || AiQuerySemanticLexicon.STRUCTURED_PURCHASE_INVENTORY_OVERSTOCK_RISK.equals(canon)
                || AiQuerySemanticLexicon.STRUCTURED_PURCHASE_FRESHNESS_RISK.equals(canon);
    }

    /**
     * 诊断 Planner 工具表：双域风险 wire 仅采购+出库；其余默认四域（权限裁剪前）。
     */
    public static List<String> plannerToolsForWire(String wire) {
        if (isDualDomainPurchaseStockWire(wire)) {
            List<String> dual = new ArrayList<>();
            dual.add(AiBusinessToolIds.PURCHASE_OVERVIEW);
            dual.add(AiBusinessToolIds.STOCK_REDUCE_QUERY);
            return dual;
        }
        return new ArrayList<>(AiBusinessToolIds.BUSINESS_OVERVIEW_MULTI_AGENT_DOMAIN_TOOLS);
    }

    public static boolean isMatrixWireMissing(String wire) {
        return MATRIX_WIRE_MISSING.equals(wire);
    }

    private static String normalizeMatrixToken(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    /**
     * Contract observe：contract-locked 时走 ContractFrameLightNormalizer；非 contract-locked 时原样返回 raw。
     * 不做 non-contract-locked legacy 补全（删除 slots→wire 推断、metric.contains 推断等旁路）。
     */
    public static AiQuerySemanticParseResult canonicalizeBusinessDiagnosisContractFrame(
            AiQuerySemanticParseResult raw) {
        if (raw == null || raw.isParseMissing() || raw.getSemanticSlots() == null) {
            return raw;
        }
        if (com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine.isContractLockedParse(raw)) {
            return com.nongxinle.ai.semantic.contract.canonicalizer.ContractFrameLightNormalizer.normalize(raw);
        }
        // non-contract-locked: return raw as-is; no legacy fallback
        return raw;
    }

    // mergeBusinessDiagnosisContractRow DELETED — BUSINESS-DIAGNOSIS-OVERVIEW-SEMANTIC-MERGE-CLEAN-P1
    // non-contract-locked slots→wire/planType rewrite removed; use contract-locked ContractFrameLightNormalizer instead.

    // normalizeDiagnosisOperationForRow DELETED — BUSINESS-DIAGNOSIS-OVERVIEW-SEMANTIC-MERGE-CLEAN-P1
    // operationAcceptedForRow DELETED — BUSINESS-DIAGNOSIS-OVERVIEW-SEMANTIC-MERGE-CLEAN-P1
    // queryObjectAcceptedForRow DELETED — BUSINESS-DIAGNOSIS-OVERVIEW-SEMANTIC-MERGE-CLEAN-P1

    // diagnosisMetricForRow DELETED — BUSINESS-DIAGNOSIS-OVERVIEW-SEMANTIC-MERGE-CLEAN-P1

    // slotsInferBusinessDiagnosisSummaryShape DELETED — BUSINESS-DIAGNOSIS-OVERVIEW-SEMANTIC-MERGE-CLEAN-P1
    // non-contract-locked slot shape inference removed; use contract entry for capability lookup.

    // metricAcceptedForRow DELETED — BUSINESS-DIAGNOSIS-OVERVIEW-SEMANTIC-MERGE-CLEAN-P1
    // metric.contains("BUSINESS") shape inference removed; use contract entry for capability lookup.

    public static void applyResolvedRow(
            AiRunState state,
            DiagnosisPlan plan,
            BusinessDiagnosisSemanticCapabilityMatrixRow row,
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
        if (ACTION_SUGGESTION.equals(row)) {
            applyActionFollowup(state, plan);
        }
    }

    public static void stampMatrixDebug(
            DiagnosisPlan plan, BusinessDiagnosisSemanticCapabilityMatrixRow row, String knownGapOverride) {
        if (plan == null || row == null) {
            return;
        }
        Map<String, Object> dbg = plan.getDebug();
        dbg.put(BusinessDiagnosisAgentV1.DEBUG_DIAGNOSIS_REASON_EXPLANATION_MATRIX_ROW_ID, row.getRowId());
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

    // isStorePriorityHarnessTextFallback DELETED — BUSINESS-DIAGNOSIS-OVERVIEW-SEMANTIC-MERGE-CLEAN-P1
    // non-contract-locked text fallback removed; use contract entry for store priority semantics.

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
            BusinessDiagnosisSemanticCapabilityMatrixRow row,
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
                String fn = state.getResolvedQueryContext().getRewriteInheritedAnchorName();
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
