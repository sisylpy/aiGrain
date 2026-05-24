package com.nongxinle.ai.semantic.matrix;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.BusinessOverviewAnswerPlan;
import com.nongxinle.ai.semantic.AiQuerySemanticLlmMergeHelper;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.AiQuerySemanticSlotMerge;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import lombok.experimental.UtilityClass;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Phase 1-E：经营概览 slots-first 矩阵（四域 MULTI_AGENT 表面）。
 */
@UtilityClass
public final class BusinessOverviewSemanticCapabilityMatrix {

    public static final String MATRIX_WIRE_MISSING = "MATRIX_WIRE_MISSING";

    public static final BusinessOverviewSemanticCapabilityMatrixRow SUMMARY =
            row(
                    "BO-A",
                    "BUSINESS",
                    "SUMMARY",
                    "BUSINESS_STATUS",
                    AiQuerySemanticLexicon.STRUCTURED_BUSINESS_OVERVIEW_SUMMARY);

    public static final BusinessOverviewSemanticCapabilityMatrixRow STATUS =
            row(
                    "BO-B",
                    "BUSINESS",
                    "SUMMARY",
                    "BUSINESS_STATUS",
                    AiQuerySemanticLexicon.STRUCTURED_BUSINESS_OVERVIEW_STATUS);

    public static final BusinessOverviewSemanticCapabilityMatrixRow STORE_STATUS_COMPARE =
            row(
                    "BO-C",
                    "STORE",
                    "COMPARE",
                    "BUSINESS_STATUS",
                    AiQuerySemanticLexicon.STRUCTURED_BUSINESS_STORE_STATUS_COMPARE);

    private static BusinessOverviewSemanticCapabilityMatrixRow row(
            String rowId,
            String queryObject,
            String operation,
            String metric,
            String wire) {
        return BusinessOverviewSemanticCapabilityMatrixRow.builder()
                .rowId(rowId)
                .queryObject(queryObject)
                .operation(operation)
                .metric(metric)
                .structuredIntentDetailWire(wire)
                .targetOverviewPlanType(BusinessOverviewAnswerPlan.PLAN_TYPE_BUSINESS_OVERVIEW_MULTI_AGENT_V1)
                .build();
    }

    /** 四域经营概览 Matrix 首轮稳定行（合同导出与 legacy reconcile 共用）。 */
    public static List<BusinessOverviewSemanticCapabilityMatrixRow> firstTurnRows() {
        return List.of(SUMMARY, STATUS, STORE_STATUS_COMPARE);
    }

    public static BusinessOverviewSemanticCapabilityMatrixRow resolveMatrixRow(String pathCode, String wire) {
        if (!AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW.equals(pathCode) || !StringUtils.hasText(wire)) {
            return null;
        }
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wire.trim());
        if (!StringUtils.hasText(canon)) {
            return null;
        }
        if (SUMMARY.getStructuredIntentDetailWire().equals(canon)) {
            return SUMMARY;
        }
        if (STATUS.getStructuredIntentDetailWire().equals(canon)) {
            return STATUS;
        }
        if (STORE_STATUS_COMPARE.getStructuredIntentDetailWire().equals(canon)) {
            return STORE_STATUS_COMPARE;
        }
        return null;
    }

    public static String targetPlanTypeForWire(String wire) {
        BusinessOverviewSemanticCapabilityMatrixRow row =
                resolveMatrixRow(AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW, wire);
        return row == null ? null : row.getTargetOverviewPlanType();
    }

    /**
     * business_overview_path：semanticSlots → Matrix canonical wire；无矩阵行时 {@link #MATRIX_WIRE_MISSING} 或保留原 canonical。
     * <p>LEGACY_ONLY — contract-locked 时 abstain；主链 wire 仅来自 selectedContractId → ACTIVE entry。
     */
    public static String resolveStructuredIntentDetailWire(
            AiQuerySemanticParseResult sem, String pathCode, String mergedStructuredDetail) {
        if (!AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW.equals(pathCode)) {
            return null;
        }
        if (SemanticContractCompletionEngine.isContractLockedParse(sem)) {
            return null;
        }
        if (AiQuerySemanticLlmMergeHelper.hasExplicitStockReduceRouteSignal(sem)) {
            return null;
        }
        if (AiQuerySemanticSlotMerge.hasCanonicalStructuredIntentWireFromSlots(sem)) {
            String slotCanon =
                    AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                            sem.getSemanticSlots().getStructuredIntentDetailWire().trim());
            return adoptWireViaMatrix(pathCode, slotCanon);
        }
        String fromShape = inferMatrixWireFromSemanticSlots(sem);
        if (StringUtils.hasText(fromShape)) {
            return adoptWireViaMatrix(pathCode, fromShape);
        }
        String mergedCanon =
                StringUtils.hasText(mergedStructuredDetail)
                        ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                                mergedStructuredDetail.trim())
                        : null;
        if (StringUtils.hasText(mergedCanon)
                && AiQuerySemanticLexicon.isStructuredBusinessOverviewFourDomainOrchestrationSurface(
                        mergedCanon)) {
            return adoptWireViaMatrix(pathCode, mergedCanon);
        }
        if (StringUtils.hasText(mergedCanon)) {
            return mergedCanon;
        }
        return MATRIX_WIRE_MISSING;
    }

    private static String adoptWireViaMatrix(String pathCode, String canonWire) {
        if (!StringUtils.hasText(canonWire)) {
            return MATRIX_WIRE_MISSING;
        }
        BusinessOverviewSemanticCapabilityMatrixRow row = resolveMatrixRow(pathCode, canonWire);
        return row != null ? row.getStructuredIntentDetailWire() : canonWire;
    }

    /** LEGACY_ONLY — contract-locked 主链 abstain；仅非 locked 时由 slots 形状推断 wire。 */
    public static String inferMatrixWireFromSemanticSlots(AiQuerySemanticParseResult sem) {
        if (SemanticContractCompletionEngine.isContractLockedParse(sem)) {
            return null;
        }
        if (sem == null || sem.getSemanticSlots() == null) {
            return null;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = sem.getSemanticSlots();
        String op = normalizeToken(s.getOperation());
        String qo = normalizeToken(s.getQueryObject());
        String metric = normalizeToken(s.getMetric());
        boolean businessMetric =
                metric != null
                        && (metric.contains("BUSINESS")
                                || "BUSINESS_STATUS".equals(metric)
                                || "OPERATION_STATUS".equals(metric));
        if ("COMPARE".equals(op) && ("STORE".equals(qo) || "BUSINESS".equals(qo)) && businessMetric) {
            return STORE_STATUS_COMPARE.getStructuredIntentDetailWire();
        }
        if (("SUMMARY".equals(op) || "OVERVIEW".equals(op))
                && ("BUSINESS".equals(qo) || "STORE".equals(qo) || "GROUP".equals(qo))
                && businessMetric) {
            return SUMMARY.getStructuredIntentDetailWire();
        }
        if (businessMetric && StringUtils.hasText(qo)) {
            return SUMMARY.getStructuredIntentDetailWire();
        }
        return null;
    }

    /** 四域经营概览默认 tool 表（权限裁剪前）。 */
    public static List<String> defaultFourDomainPlannerTools() {
        return new ArrayList<>(AiBusinessToolIds.BUSINESS_OVERVIEW_MULTI_AGENT_DOMAIN_TOOLS);
    }

    public static boolean isMatrixWireMissing(String wire) {
        return MATRIX_WIRE_MISSING.equals(wire);
    }

    private static String normalizeToken(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }
}
