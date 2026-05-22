package com.nongxinle.ai.semantic.frame;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.AiQuerySemanticSlotMerge;
import com.nongxinle.ai.semantic.matrix.PurchaseSemanticCapabilityMatrix;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 采购域 CurrentSemanticFrame 完备性与自洽性校验（V2 主链路门禁）。
 * Phase1-J：仅校验<strong>当前轮</strong> semanticSlots + Matrix wire；不再依赖 structural follow-up /
 * previousTurn detailWanted / anchorPolicy 门禁。
 */
public final class CurrentSemanticFrameValidator {

    private static final Set<String> PURCHASE_CANONICAL_WIRES =
            Set.of(
                    AiQuerySemanticLexicon.STRUCTURED_PURCHASE_OVERVIEW_SUMMARY,
                    AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_SUMMARY,
                    AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_AMOUNT_QUERY,
                    AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY,
                    AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_AMOUNT_RANKING,
                    AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_COUNT_RANKING,
                    AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_ANOMALY,
                    AiQuerySemanticLexicon.STRUCTURED_PURCHASE_PRICE_ANOMALY,
                    AiQuerySemanticLexicon.STRUCTURED_PURCHASE_FREQUENCY_ANOMALY,
                    AiQuerySemanticLexicon.STRUCTURED_PURCHASE_QUANTITY_ANOMALY,
                    AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_AMOUNT_SPIKE,
                    AiQuerySemanticLexicon.STRUCTURED_PURCHASE_STOCK_REDUCE_MISMATCH,
                    AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SLOW_MOVING_RISK,
                    AiQuerySemanticLexicon.STRUCTURED_PURCHASE_INVENTORY_OVERSTOCK_RISK,
                    AiQuerySemanticLexicon.STRUCTURED_PURCHASE_FRESHNESS_RISK,
                    AiQuerySemanticLexicon.STRUCTURED_PURCHASE_STORE_AMOUNT_RANKING,
                    AiQuerySemanticLexicon.STRUCTURED_SUPPLIER_AMOUNT_RANKING);

    private static final String Q_SLOT_MISSING =
            "当前采购语义不完整：请在解析 JSON 中补齐 queryObject、operation、metric、sourceFacet。";
    private static final String Q_WIRE =
            "当前采购语义不完整：请在解析 JSON 中给出有效 structuredIntentDetailWire（合法采购子口径 wire）。";
    private static final String Q_BREAKDOWN_DETAIL_OP_MISMATCH =
            "detailWanted=SOURCE_BREAKDOWN 时 operation 必须为 BREAKDOWN；请勿落成排行或渠道总览。";
    private static final String Q_BREAKDOWN_GOODS_OBJECT =
            "operation=BREAKDOWN 且按来源拆桶时 queryObject 必须为 GOODS。";
    private static final String Q_BREAKDOWN_METRIC =
            "operation=BREAKDOWN 时 metric 须为 PURCHASE_AMOUNT、PURCHASE_QUANTITY 或 PURCHASE_COUNT（采购金额或采购数量）。";
    private static final String Q_BREAKDOWN_DETAIL_SLOT =
            "operation=BREAKDOWN 时 detailWanted 须与矩阵行一致（SOURCE_BREAKDOWN / SUPPLIER_BREAKDOWN / SUPPLIER_UNIT_PRICE）。";

    private CurrentSemanticFrameValidator() {}

    public static SemanticFrameValidationResult validate(
            CurrentSemanticFrame frame,
            AiQuerySemanticParseResult rawParse,
            AiConversationTurnMemory previousTurn,
            String normalizedUserMessage) {
        return validate(frame, rawParse, previousTurn, normalizedUserMessage, false);
    }

    public static SemanticFrameValidationResult validate(
            CurrentSemanticFrame frame,
            AiQuerySemanticParseResult rawParse,
            AiConversationTurnMemory previousTurn,
            String normalizedUserMessage,
            boolean followUpRewriteApplied) {
        List<String> codes = new ArrayList<>();
        if (frame == null) {
            return SemanticFrameValidationResult.clarify(Q_SLOT_MISSING, List.of("FRAME_NULL"));
        }
        if (!StringUtils.hasText(frame.getQueryObject())
                || AiQuerySemanticSlotMerge.UNKNOWN.equals(frame.getQueryObject())) {
            codes.add("QUERY_OBJECT_UNKNOWN");
        }
        if (!StringUtils.hasText(frame.getOperation())
                || AiQuerySemanticSlotMerge.UNKNOWN.equals(frame.getOperation())) {
            codes.add("OPERATION_UNKNOWN");
        }
        if (!StringUtils.hasText(frame.getMetric()) || AiQuerySemanticSlotMerge.UNKNOWN.equals(frame.getMetric())) {
            codes.add("METRIC_UNKNOWN");
        }
        if (!StringUtils.hasText(frame.getSourceFacet())
                || AiQuerySemanticSlotMerge.UNKNOWN.equals(frame.getSourceFacet())) {
            codes.add("SOURCE_FACET_UNKNOWN");
        }
        if (!codes.isEmpty()) {
            return SemanticFrameValidationResult.clarify(Q_SLOT_MISSING, codes);
        }

        List<String> warnings = new ArrayList<>();
        if (rawParse != null
                && rawParse.getMetric() != null
                && StringUtils.hasText(rawParse.getMetric().getPurchaseSourceType())
                && hasExplicitCanonicalSourceFacet(frame.getSourceFacet())) {
            String pst = canonicalPurchaseSourceToken(rawParse.getMetric().getPurchaseSourceType());
            String sf = canonicalPurchaseSourceToken(frame.getSourceFacet());
            if (pst != null && sf != null && !pst.equals(sf)) {
                warnings.add("METRIC_PURCHASE_SOURCE_TYPE_VS_SEMANTIC_SOURCE_FACET");
            }
        }

        String effectiveWire = frame.getStructuredIntentDetailWire();
        if (!StringUtils.hasText(effectiveWire)
                || !PURCHASE_CANONICAL_WIRES.contains(effectiveWire)) {
            String fromSlots =
                    PurchaseSemanticCapabilityMatrix.resolveStructuredIntentDetailWire(
                            rawParse,
                            com.nongxinle.ai.context.AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW,
                            null);
            if (StringUtils.hasText(fromSlots) && PURCHASE_CANONICAL_WIRES.contains(fromSlots)) {
                String frameCanon =
                        StringUtils.hasText(frame.getStructuredIntentDetailWire())
                                ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                                        frame.getStructuredIntentDetailWire().trim())
                                : null;
                if (AiQuerySemanticLexicon.isPurchaseOverviewDomainCanonicalWire(frameCanon)
                        && !fromSlots.equals(frameCanon)) {
                    warnings.add(PurchaseSemanticCapabilityMatrix.REASON_LLM_WIRE_OVERRIDDEN_BY_SLOTS);
                } else {
                    warnings.add(PurchaseSemanticCapabilityMatrix.REASON_STRUCTURED_WIRE_REWRITTEN_FROM_SLOTS);
                }
                effectiveWire = fromSlots;
            } else {
                return SemanticFrameValidationResult.clarify(
                        Q_WIRE,
                        List.of(
                                StringUtils.hasText(fromSlots)
                                        ? "STRUCTURED_WIRE_UNMAPPABLE_FROM_SLOTS"
                                        : "STRUCTURED_WIRE_INVALID"));
            }
        }

        if (AiQuerySemanticLexicon.DETAIL_WANTED_SOURCE_BREAKDOWN.equals(frame.getDetailWanted())
                && !PurchaseSemanticCapabilityMatrix.operationAccepted(
                        PurchaseSemanticCapabilityMatrix.SOURCE_BREAKDOWN, frame.getOperation())) {
            return SemanticFrameValidationResult.clarify(
                    Q_BREAKDOWN_DETAIL_OP_MISMATCH, List.of("SOURCE_BREAKDOWN_REQUIRES_BREAKDOWN_OPERATION"));
        }

        if (AiQuerySemanticLexicon.DETAIL_WANTED_SUPPLIER_UNIT_PRICE.equals(frame.getDetailWanted())
                && !PurchaseSemanticCapabilityMatrix.frameMatchesRow(
                        frame, PurchaseSemanticCapabilityMatrix.SUPPLIER_UNIT_PRICE)) {
            return SemanticFrameValidationResult.clarify(
                    Q_BREAKDOWN_METRIC,
                    List.of(PurchaseSemanticCapabilityMatrix.VALIDATION_SUPPLIER_UNIT_PRICE_INCOMPLETE));
        }
        if (AiQuerySemanticLexicon.DETAIL_WANTED_SUPPLIER_BREAKDOWN.equals(frame.getDetailWanted())
                && !PurchaseSemanticCapabilityMatrix.frameMatchesRow(
                        frame, PurchaseSemanticCapabilityMatrix.SUPPLIER_BREAKDOWN)) {
            return SemanticFrameValidationResult.clarify(
                    Q_BREAKDOWN_DETAIL_SLOT, List.of("SUPPLIER_BREAKDOWN_FRAME_INCOMPLETE"));
        }
        if (AiQuerySemanticLexicon.DETAIL_WANTED_SOURCE_BREAKDOWN.equals(frame.getDetailWanted())
                && !PurchaseSemanticCapabilityMatrix.frameMatchesRow(
                        frame, PurchaseSemanticCapabilityMatrix.SOURCE_BREAKDOWN)) {
            return SemanticFrameValidationResult.clarify(
                    Q_BREAKDOWN_DETAIL_SLOT,
                    List.of(PurchaseSemanticCapabilityMatrix.VALIDATION_SOURCE_BREAKDOWN_INCOMPLETE));
        }

        if ("BREAKDOWN".equals(frame.getOperation())) {
            if (!"GOODS".equals(frame.getQueryObject())) {
                return SemanticFrameValidationResult.clarify(
                        Q_BREAKDOWN_GOODS_OBJECT, List.of("BREAKDOWN_REQUIRES_GOODS_QUERY_OBJECT"));
            }
            String detailWanted = frame.getDetailWanted();
            if (!AiQuerySemanticLexicon.DETAIL_WANTED_SOURCE_BREAKDOWN.equals(detailWanted)
                    && !AiQuerySemanticLexicon.DETAIL_WANTED_SUPPLIER_BREAKDOWN.equals(detailWanted)
                    && !AiQuerySemanticLexicon.DETAIL_WANTED_SUPPLIER_UNIT_PRICE.equals(detailWanted)) {
                return SemanticFrameValidationResult.clarify(
                        Q_BREAKDOWN_DETAIL_SLOT, List.of("BREAKDOWN_REQUIRES_DETAIL_SOURCE_OR_SUPPLIER_BREAKDOWN"));
            }
        }

        if (!warnings.isEmpty()) {
            return SemanticFrameValidationResult.successWithWarnings(warnings);
        }
        return SemanticFrameValidationResult.success();
    }

    private static boolean hasExplicitCanonicalSourceFacet(String sourceFacet) {
        return canonicalPurchaseSourceToken(sourceFacet) != null;
    }

    private static String canonicalPurchaseSourceToken(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String u = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (AiQuerySemanticLexicon.SOURCE_ALL.equals(u) || "ALL".equals(u)) {
            return AiQuerySemanticLexicon.SOURCE_ALL;
        }
        if (AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE.equals(u)) {
            return AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE;
        }
        if (AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE.equals(u)) {
            return AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE;
        }
        return null;
    }
}
