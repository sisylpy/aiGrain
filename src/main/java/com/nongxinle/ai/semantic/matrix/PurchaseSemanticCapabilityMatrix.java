package com.nongxinle.ai.semantic.matrix;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.AiQuerySemanticSlotMerge;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import com.nongxinle.ai.semantic.contract.canonicalizer.ContractFrameLightNormalizer;
import com.nongxinle.ai.semantic.frame.CurrentSemanticFrame;
import lombok.experimental.UtilityClass;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Purchase 纯 capability registry（GOODS 锚点下钻合同表）。
 *
 * <p><b>职责：</b></p>
 * <ul>
 *   <li>定义 ACTIVE matrix rows（GOODS 锚三类合同：source_breakdown / supplier_breakdown / supplier_unit_price）</li>
 *   <li>提供 wire → row 查表、frame 形状匹配、operation canonical 归一</li>
 *   <li>提供 contract-locked canonicalize（走 {@code ContractFrameLightNormalizer}）</li>
 * </ul>
 *
 * <p><b>禁止职责：</b></p>
 * <ul>
 *   <li>不做 slots→wire 推断（不读 queryObject + operation + metric 推 canonical wire）</li>
 *   <li>不做 sourceFacet 默认推断</li>
 *   <li>不做 metric.contains 业务语义推断（单价/金额/数量含义不在此派生）</li>
 *   <li>不读取 rawMessage / normalizedUserMessage</li>
 *   <li>非 contract-locked 路径原样返回，不做 canonical frame completion</li>
 * </ul>
 *
 * <p>Purchase 主链语义唯一来源：
 * selectedContractId → ACTIVE contract entry → SemanticContractCompletionEngine。</p>
 */
@UtilityClass
public final class PurchaseSemanticCapabilityMatrix {

    public static final String REASON_SOURCE_BREAKDOWN_DETAIL_TO_BREAKDOWN =
            "MATRIX_CANONICAL_SOURCE_BREAKDOWN_DETAIL_TO_BREAKDOWN";
    public static final String REASON_SUPPLIER_AMOUNT_TO_SUPPLIER_UNIT_PRICE =
            "MATRIX_CANONICAL_SUPPLIER_AMOUNT_TO_SUPPLIER_UNIT_PRICE";
    public static final String VALIDATION_SUPPLIER_UNIT_PRICE_INCOMPLETE =
            "MATRIX_VALIDATION_SUPPLIER_UNIT_PRICE_INCOMPLETE";
    public static final String VALIDATION_SOURCE_BREAKDOWN_INCOMPLETE =
            "MATRIX_VALIDATION_SOURCE_BREAKDOWN_INCOMPLETE";

    public static final PurchaseSemanticCapabilityMatrixRow SOURCE_BREAKDOWN =
            PurchaseSemanticCapabilityMatrixRow.builder()
                    .capabilityId("purchase.goods_anchor.source_breakdown")
                    .anchorType("GOODS")
                    .allowedQueryObjects(Set.of("GOODS"))
                    .allowedOperations(Set.of("BREAKDOWN"))
                    .allowedMetricContains(
                            Set.of("PURCHASE_AMOUNT", "PURCHASE_QUANTITY", "PURCHASE_COUNT"))
                    .requiredSourceFacet(AiQuerySemanticLexicon.SOURCE_ALL)
                    .requiredDetailWanted(AiQuerySemanticLexicon.DETAIL_WANTED_SOURCE_BREAKDOWN)
                    .requiredStructuredIntentDetailWire(
                            AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY)
                    .targetPurchasePlanType(PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_SOURCE_BREAKDOWN)
                    .operationCanonicalFrom("DETAIL")
                    .operationCanonicalTo("BREAKDOWN")
                    .canonicalDebugReason(REASON_SOURCE_BREAKDOWN_DETAIL_TO_BREAKDOWN)
                    .build();

    public static final PurchaseSemanticCapabilityMatrixRow SUPPLIER_BREAKDOWN =
            PurchaseSemanticCapabilityMatrixRow.builder()
                    .capabilityId("purchase.goods_anchor.supplier_breakdown")
                    .anchorType("GOODS")
                    .allowedQueryObjects(Set.of("GOODS"))
                    .allowedOperations(Set.of("BREAKDOWN", "DETAIL"))
                    .allowedMetricContains(
                            Set.of(
                                    "PURCHASE_AMOUNT",
                                    "PURCHASE_QUANTITY",
                                    "PURCHASE_COUNT",
                                    "SUPPLIER_NAME"))
                    .requiredSourceFacet(AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE)
                    .requiredDetailWanted(AiQuerySemanticLexicon.DETAIL_WANTED_SUPPLIER_BREAKDOWN)
                    .requiredStructuredIntentDetailWire(
                            AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY)
                    .targetPurchasePlanType(PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL)
                    .build();

    public static final PurchaseSemanticCapabilityMatrixRow SUPPLIER_UNIT_PRICE =
            PurchaseSemanticCapabilityMatrixRow.builder()
                    .capabilityId("purchase.goods_anchor.supplier_unit_price")
                    .anchorType("GOODS")
                    .allowedQueryObjects(Set.of("SUPPLIER"))
                    .allowedOperations(Set.of("RANKING", "BREAKDOWN", "DETAIL"))
                    .allowedMetricContains(Set.of("UNIT_PRICE", "SUPPLIER_NAME"))
                    .requiredSourceFacet(AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE)
                    .requiredDetailWanted(AiQuerySemanticLexicon.DETAIL_WANTED_SUPPLIER_UNIT_PRICE)
                    .requiredStructuredIntentDetailWire(
                            AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY)
                    .targetPurchasePlanType(PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL)
                    .canonicalDebugReason(REASON_SUPPLIER_AMOUNT_TO_SUPPLIER_UNIT_PRICE)
                    .build();

    public static List<PurchaseSemanticCapabilityMatrixRow> goodsAnchorRows() {
        return List.of(SOURCE_BREAKDOWN, SUPPLIER_BREAKDOWN, SUPPLIER_UNIT_PRICE);
    }

    public static PurchaseSemanticCapabilityMatrixRow findByDetailWanted(String detailWanted) {
        String dw = normalizeToken(detailWanted);
        if (dw == null) {
            return null;
        }
        for (PurchaseSemanticCapabilityMatrixRow row : goodsAnchorRows()) {
            if (dw.equals(row.getRequiredDetailWanted())) {
                return row;
            }
        }
        return null;
    }

    public static String canonicalOperation(
            String operation,
            String detailWanted,
            String queryObject,
            String anchorPolicy,
            String structuredIntentDetailWire) {
        String op = normalizeToken(operation);
        if (op == null) {
            return null;
        }
        PurchaseSemanticCapabilityMatrixRow row = findByDetailWanted(detailWanted);
        if (row == null || row.getOperationCanonicalFrom() == null || row.getOperationCanonicalTo() == null) {
            return op;
        }
        if (!row.getOperationCanonicalFrom().equals(op)) {
            return op;
        }
        String qo = normalizeToken(queryObject);
        String ap = normalizeToken(anchorPolicy);
        String wire =
                AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(structuredIntentDetailWire);
        if (!row.getAllowedQueryObjects().contains(qo)) {
            return op;
        }
        if (!AiQuerySemanticSlotMerge.ANCHOR_USE_PREVIOUS.equals(ap)) {
            return op;
        }
        if (!row.getRequiredStructuredIntentDetailWire().equals(wire)) {
            return op;
        }
        if (!row.getRequiredDetailWanted().equals(normalizeToken(detailWanted))) {
            return op;
        }
        return row.getOperationCanonicalTo();
    }

    public static boolean operationAccepted(PurchaseSemanticCapabilityMatrixRow row, String operation) {
        if (row == null) {
            return false;
        }
        String op = normalizeToken(operation);
        if (op == null) {
            return false;
        }
        if (row.getAllowedOperations().contains(op)) {
            return true;
        }
        String from = row.getOperationCanonicalFrom();
        String to = row.getOperationCanonicalTo();
        return from != null
                && to != null
                && from.equals(op)
                && row.getAllowedOperations().contains(to);
    }

    public static String canonicalizeOperationForRow(PurchaseSemanticCapabilityMatrixRow row, String operation) {
        String op = normalizeToken(operation);
        if (op == null || row == null) {
            return op;
        }
        String from = row.getOperationCanonicalFrom();
        String to = row.getOperationCanonicalTo();
        if (from != null && to != null && from.equals(op)) {
            return to;
        }
        return op;
    }

    public static boolean frameMatchesRow(CurrentSemanticFrame frame, PurchaseSemanticCapabilityMatrixRow row) {
        if (frame == null || row == null) {
            return false;
        }
        return rowShapeMatchesTokens(
                row,
                frame.getQueryObject(),
                frame.getOperation(),
                frame.getMetric(),
                frame.getSourceFacet(),
                frame.getAnchorPolicy(),
                frame.getStructuredIntentDetailWire(),
                frame.getDetailWanted(),
                true,
                false,
                false,
                false);
    }

    /**
     * Contract observe 采购域 canonical：contract-locked 走 light normalize；非 contract-locked 原样返回。
     * 不再通过 Matrix 从 slots 反推 wire / sourceFacet / planType。
     */
    public static AiQuerySemanticParseResult canonicalizePurchaseContractFrame(
            AiQuerySemanticParseResult raw, AiConversationTurnMemory previousTurn) {
        if (raw == null || raw.isParseMissing() || raw.getSemanticSlots() == null) {
            return raw;
        }
        if (SemanticContractCompletionEngine.isContractLockedParse(raw)) {
            return ContractFrameLightNormalizer.normalize(raw);
        }
        return raw;
    }

    /** 采购追问 canonical：contract-locked 走 light normalize；非 contract-locked 原样返回。 */
    public static AiQuerySemanticParseResult canonicalizePurchaseFollowUp(
            AiQuerySemanticParseResult raw, AiConversationTurnMemory previousTurn) {
        if (raw == null || raw.isParseMissing() || raw.getSemanticSlots() == null) {
            return raw;
        }
        if (SemanticContractCompletionEngine.isContractLockedParse(raw)) {
            return ContractFrameLightNormalizer.normalize(raw);
        }
        return raw;
    }

    private static boolean rowShapeMatchesTokens(
            PurchaseSemanticCapabilityMatrixRow row,
            String queryObject,
            String operation,
            String metric,
            String sourceFacet,
            String anchorPolicy,
            String structuredIntentDetailWire,
            String detailWanted,
            boolean requireDetailMatch,
            boolean relaxMissingSourceFacet,
            boolean relaxMissingAnchorPolicy,
            boolean relaxMissingWire) {
        if (requireDetailMatch) {
            String dw = normalizeToken(detailWanted);
            if (!row.getRequiredDetailWanted().equals(dw)) {
                return false;
            }
        }
        String qo = normalizeToken(queryObject);
        if (qo == null || !row.getAllowedQueryObjects().contains(qo)) {
            return false;
        }
        String op = normalizeToken(operation);
        if (!operationAccepted(row, op)) {
            return false;
        }
        String met = normalizeToken(metric);
        if (!StringUtils.hasText(met)) {
            return false;
        }
        if (!metricContainsAny(met, row.getAllowedMetricContains())) {
            return false;
        }
        String sf = normalizeSourceFacet(sourceFacet);
        if (!row.getRequiredSourceFacet().equals(sf)) {
            if (!relaxMissingSourceFacet || sf != null) {
                return false;
            }
        }
        if (!AiQuerySemanticSlotMerge.ANCHOR_USE_PREVIOUS.equals(normalizeToken(anchorPolicy))) {
            if (!relaxMissingAnchorPolicy || normalizeToken(anchorPolicy) != null) {
                return false;
            }
        }
        String wire =
                AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(structuredIntentDetailWire);
        if (!row.getRequiredStructuredIntentDetailWire().equals(wire)) {
            if (!relaxMissingWire || wire != null) {
                return false;
            }
        }
        return true;
    }

    private static boolean metricContainsAny(String metric, Set<String> allowedContains) {
        for (String token : allowedContains) {
            if (metric.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeToken(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String t = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (AiQuerySemanticSlotMerge.UNKNOWN.equalsIgnoreCase(t)) {
            return AiQuerySemanticSlotMerge.UNKNOWN;
        }
        return t.isEmpty() ? null : t;
    }

    private static String normalizeSourceFacet(String raw) {
        String t = normalizeToken(raw);
        if (!StringUtils.hasText(t)) {
            return null;
        }
        if (AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE.equals(t)) {
            return AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE;
        }
        if (AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE.equals(t)) {
            return AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE;
        }
        if (AiQuerySemanticLexicon.SOURCE_ALL.equals(t) || "ALL".equals(t)) {
            return AiQuerySemanticLexicon.SOURCE_ALL;
        }
        return t;
    }
}
