package com.nongxinle.ai.semantic.matrix;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.BusinessOverviewAnswerPlan;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import lombok.experimental.UtilityClass;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Phase 1-E：经营概览 contract-locked capability registry（四域 MULTI_AGENT 表面）。
 * <p>
 * P1 清理后移除了 non-contract-locked legacy 推断（slots→wire、metric.contains 等）。
 * 提供 contractId → row 查表和 contract frame light normalize 能力。
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

    // resolveStructuredIntentDetailWire DELETED — BUSINESS-DIAGNOSIS-OVERVIEW-SEMANTIC-MERGE-CLEAN-P1
    // adoptWireViaMatrix DELETED — BUSINESS-DIAGNOSIS-OVERVIEW-SEMANTIC-MERGE-CLEAN-P1
    // inferMatrixWireFromSemanticSlots DELETED — BUSINESS-DIAGNOSIS-OVERVIEW-SEMANTIC-MERGE-CLEAN-P1
    // non-contract-locked slots→wire/row inference + metric.contains inference removed.

    /**
     * Contract observe：contract-locked 时走 ContractFrameLightNormalizer；非 contract-locked 时原样返回 raw。
     * 不做 non-contract-locked legacy 补全。
     */
    public static AiQuerySemanticParseResult canonicalizeBusinessOverviewContractFrame(
            AiQuerySemanticParseResult raw) {
        if (raw == null || raw.isParseMissing() || raw.getSemanticSlots() == null) {
            return raw;
        }
        if (SemanticContractCompletionEngine.isContractLockedParse(raw)) {
            return com.nongxinle.ai.semantic.contract.canonicalizer.ContractFrameLightNormalizer.normalize(raw);
        }
        // non-contract-locked: return raw as-is; no legacy fallback
        return raw;
    }

    /**
     * ACTIVE contractId → Matrix 行（contract-entry 注册表，非 NL 推断）。
     * 只允许 selectedContractId → ACTIVE matrix row；不从 slots shape / wire / rawMessage 推断。
     */
    public static BusinessOverviewSemanticCapabilityMatrixRow rowFromActiveContractId(String contractId) {
        if (!StringUtils.hasText(contractId)) {
            return null;
        }
        return switch (contractId.trim()) {
            case "business_overview.summary" -> SUMMARY;
            case "business_overview.status" -> STATUS;
            case "business_overview.store_status_compare" -> STORE_STATUS_COMPARE;
            default -> null;
        };
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
