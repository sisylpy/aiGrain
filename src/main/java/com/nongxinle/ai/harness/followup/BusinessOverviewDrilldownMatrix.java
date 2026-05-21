package com.nongxinle.ai.harness.followup;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.BusinessOverviewAnswerPlan;
import com.nongxinle.ai.semantic.AiQuerySemanticLlmMergeHelper;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.AiQuerySemanticSlotMerge;
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
public final class BusinessOverviewDrilldownMatrix {

    public static final String MATRIX_WIRE_MISSING = "MATRIX_WIRE_MISSING";

    public static final BusinessOverviewDrilldownMatrixRow SUMMARY =
            row(
                    "BO-A",
                    "BUSINESS",
                    "SUMMARY",
                    "BUSINESS_STATUS",
                    AiQuerySemanticLexicon.STRUCTURED_BUSINESS_OVERVIEW_SUMMARY);

    public static final BusinessOverviewDrilldownMatrixRow STATUS =
            row(
                    "BO-B",
                    "BUSINESS",
                    "SUMMARY",
                    "BUSINESS_STATUS",
                    AiQuerySemanticLexicon.STRUCTURED_BUSINESS_OVERVIEW_STATUS);

    public static final BusinessOverviewDrilldownMatrixRow STORE_STATUS_COMPARE =
            row(
                    "BO-C",
                    "STORE",
                    "COMPARE",
                    "BUSINESS_STATUS",
                    AiQuerySemanticLexicon.STRUCTURED_BUSINESS_STORE_STATUS_COMPARE);

    private static BusinessOverviewDrilldownMatrixRow row(
            String rowId,
            String queryObject,
            String operation,
            String metric,
            String wire) {
        return BusinessOverviewDrilldownMatrixRow.builder()
                .rowId(rowId)
                .queryObject(queryObject)
                .operation(operation)
                .metric(metric)
                .structuredIntentDetailWire(wire)
                .targetOverviewPlanType(BusinessOverviewAnswerPlan.PLAN_TYPE_BUSINESS_OVERVIEW_MULTI_AGENT_V1)
                .build();
    }

    public static BusinessOverviewDrilldownMatrixRow resolveMatrixRow(String pathCode, String wire) {
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
        BusinessOverviewDrilldownMatrixRow row =
                resolveMatrixRow(AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW, wire);
        return row == null ? null : row.getTargetOverviewPlanType();
    }

    /**
     * business_overview_path：semanticSlots → Matrix canonical wire；无矩阵行时 {@link #MATRIX_WIRE_MISSING} 或保留原 canonical。
     */
    public static String resolveStructuredIntentDetailWire(
            AiQuerySemanticParseResult sem, String pathCode, String mergedStructuredDetail) {
        if (!AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW.equals(pathCode)) {
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
        if (!AiQuerySemanticSlotMerge.hasCanonicalStructuredIntentWireFromSlots(sem)) {
            String fromCompat = inferWireFromMetricCompat(sem);
            if (StringUtils.hasText(fromCompat)) {
                return adoptWireViaMatrix(pathCode, fromCompat);
            }
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
        BusinessOverviewDrilldownMatrixRow row = resolveMatrixRow(pathCode, canonWire);
        return row != null ? row.getStructuredIntentDetailWire() : canonWire;
    }

    public static String inferMatrixWireFromSemanticSlots(AiQuerySemanticParseResult sem) {
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

    /** compat：仅当 slots 无 canonical wire 时，用 primaryMetric / rankingType 兜底。 */
    private static String inferWireFromMetricCompat(AiQuerySemanticParseResult sem) {
        if (sem == null || sem.getMetric() == null) {
            return null;
        }
        if (AiQuerySemanticLlmMergeHelper.hasExplicitStockReduceRouteSignal(sem)) {
            return null;
        }
        String primary = sem.getMetric().getPrimaryMetric();
        if (StringUtils.hasText(primary)) {
            String u = primary.trim().toUpperCase(Locale.ROOT).replace('-', '_');
            if (u.contains("REVENUE") || u.contains("SALES") || u.contains("TURNOVER")) {
                return null;
            }
            if (u.contains("BUSINESS") || u.contains("OPERATION") || "BUSINESS_STATUS".equals(u)) {
                return SUMMARY.getStructuredIntentDetailWire();
            }
        }
        String rt = sem.getMetric().getRankingType();
        if (!StringUtils.hasText(rt)) {
            return null;
        }
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(rt.trim());
        if (AiQuerySemanticLexicon.isStructuredBusinessOverviewFourDomainOrchestrationSurface(canon)) {
            return canon;
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
