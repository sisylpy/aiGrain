package com.nongxinle.ai.harness.followup;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.AiQuerySemanticSlotMerge;
import com.nongxinle.ai.semantic.frame.CurrentSemanticFrame;
import com.nongxinle.ai.semantic.frame.PurchaseSemanticAnchorGate;
import lombok.experimental.UtilityClass;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Phase 1：GOODS 锚点下钻矩阵（Harness Engineering 契约表）。
 * <p>职责：矩阵行定义 + 槽位/帧形状匹配 + 合同 canonical（无 NL）。
 * 参见 {@code docs/ai/purchase-drilldown-matrix-contract.md}。
 */
@UtilityClass
public final class PurchaseDrilldownMatrix {

    public static final String REASON_SOURCE_BREAKDOWN_DETAIL_TO_BREAKDOWN =
            "MATRIX_CANONICAL_SOURCE_BREAKDOWN_DETAIL_TO_BREAKDOWN";
    public static final String REASON_SUPPLIER_AMOUNT_TO_SUPPLIER_UNIT_PRICE =
            "MATRIX_CANONICAL_SUPPLIER_AMOUNT_TO_SUPPLIER_UNIT_PRICE";
    public static final String VALIDATION_SUPPLIER_UNIT_PRICE_INCOMPLETE =
            "MATRIX_VALIDATION_SUPPLIER_UNIT_PRICE_INCOMPLETE";
    public static final String VALIDATION_SOURCE_BREAKDOWN_INCOMPLETE =
            "MATRIX_VALIDATION_SOURCE_BREAKDOWN_INCOMPLETE";

    private static final Set<String> GOODS_ANCHOR_PRIOR_FRAME_PLAN_TYPES =
            Set.of(
                    PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_AMOUNT_RANKING,
                    PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_COUNT_RANKING,
                    PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_SOURCE_BREAKDOWN,
                    PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL);

    public static final PurchaseDrilldownMatrixRow SOURCE_BREAKDOWN =
            PurchaseDrilldownMatrixRow.builder()
                    .capabilityId("purchase.goods_anchor.source_breakdown")
                    .anchorType("GOODS")
                    .allowedPriorFramePlanTypes(GOODS_ANCHOR_PRIOR_FRAME_PLAN_TYPES)
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

    public static final PurchaseDrilldownMatrixRow SUPPLIER_BREAKDOWN =
            PurchaseDrilldownMatrixRow.builder()
                    .capabilityId("purchase.goods_anchor.supplier_breakdown")
                    .anchorType("GOODS")
                    .allowedPriorFramePlanTypes(GOODS_ANCHOR_PRIOR_FRAME_PLAN_TYPES)
                    .allowedQueryObjects(Set.of("GOODS"))
                    .allowedOperations(Set.of("BREAKDOWN", "DETAIL"))
                    .allowedMetricContains(
                            Set.of("PURCHASE_AMOUNT", "PURCHASE_QUANTITY", "PURCHASE_COUNT"))
                    .requiredSourceFacet(AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE)
                    .requiredDetailWanted(AiQuerySemanticLexicon.DETAIL_WANTED_SUPPLIER_BREAKDOWN)
                    .requiredStructuredIntentDetailWire(
                            AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY)
                    .targetPurchasePlanType(PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL)
                    .build();

    public static final PurchaseDrilldownMatrixRow SUPPLIER_UNIT_PRICE =
            PurchaseDrilldownMatrixRow.builder()
                    .capabilityId("purchase.goods_anchor.supplier_unit_price")
                    .anchorType("GOODS")
                    .allowedPriorFramePlanTypes(GOODS_ANCHOR_PRIOR_FRAME_PLAN_TYPES)
                    .allowedQueryObjects(Set.of("SUPPLIER"))
                    .allowedOperations(Set.of("RANKING", "BREAKDOWN", "DETAIL"))
                    .allowedMetricContains(Set.of("UNIT_PRICE"))
                    .requiredSourceFacet(AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE)
                    .requiredDetailWanted(AiQuerySemanticLexicon.DETAIL_WANTED_SUPPLIER_UNIT_PRICE)
                    .requiredStructuredIntentDetailWire(
                            AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY)
                    .targetPurchasePlanType(PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL)
                    .canonicalDebugReason(REASON_SUPPLIER_AMOUNT_TO_SUPPLIER_UNIT_PRICE)
                    .build();

    public static List<PurchaseDrilldownMatrixRow> goodsAnchorRows() {
        return List.of(SOURCE_BREAKDOWN, SUPPLIER_BREAKDOWN, SUPPLIER_UNIT_PRICE);
    }

    public static boolean isGoodsAnchoredDrilldownFramePlanType(String framePlanType) {
        return framePlanType != null && GOODS_ANCHOR_PRIOR_FRAME_PLAN_TYPES.contains(framePlanType);
    }

    public static PurchaseDrilldownMatrixRow findByDetailWanted(String detailWanted) {
        String dw = normalizeToken(detailWanted);
        if (dw == null) {
            return null;
        }
        for (PurchaseDrilldownMatrixRow row : goodsAnchorRows()) {
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
        PurchaseDrilldownMatrixRow row = findByDetailWanted(detailWanted);
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

    public static boolean operationAccepted(PurchaseDrilldownMatrixRow row, String operation) {
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

    public static String canonicalizeOperationForRow(PurchaseDrilldownMatrixRow row, String operation) {
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

    public static boolean frameMatchesRow(CurrentSemanticFrame frame, PurchaseDrilldownMatrixRow row) {
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

    public static boolean followUpSlotMatchesRow(BusinessFollowUpSlot slot, PurchaseDrilldownMatrixRow row) {
        if (slot == null || row == null) {
            return false;
        }
        String dw = slot.getSlotDetailWanted();
        if (!StringUtils.hasText(dw) || !row.getRequiredDetailWanted().equalsIgnoreCase(dw.trim())) {
            return false;
        }
        return rowShapeMatchesTokens(
                row,
                slot.getSemanticQueryObject(),
                slot.getSemanticOperation(),
                slot.getSemanticMetric(),
                slot.getSemanticSourceFacet(),
                slot.getSemanticAnchorPolicy(),
                slot.getSemanticStructuredIntentDetailWire(),
                dw,
                true,
                false,
                false,
                false);
    }

    /**
     * LLM 未显式给出 detailWanted 时，按行形状推断是否命中（transitional compat；不读用户原文）。
     */
    public static boolean slotsInferRowShape(
            AiQuerySemanticParseResult sem, PurchaseDrilldownMatrixRow row) {
        if (sem == null || sem.getSemanticSlots() == null || row == null) {
            return false;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = sem.getSemanticSlots();
        String dw = normalizeToken(s.getDetailWanted());
        if (dw != null && !row.getRequiredDetailWanted().equals(dw)) {
            return false;
        }
        if (AiQuerySemanticLexicon.DETAIL_WANTED_SUPPLIER_BREAKDOWN.equals(dw)
                && row == SOURCE_BREAKDOWN) {
            return false;
        }
        if (StringUtils.hasText(s.getDetailWanted())
                && !AiQuerySemanticSlotMerge.UNKNOWN.equalsIgnoreCase(s.getDetailWanted().trim())
                && dw == null) {
            return false;
        }
        return rowShapeMatchesTokens(
                row,
                s.getQueryObject(),
                s.getOperation(),
                s.getMetric(),
                s.getSourceFacet(),
                s.getAnchorPolicy(),
                s.getStructuredIntentDetailWire(),
                row.getRequiredDetailWanted(),
                false,
                true,
                true,
                true);
    }

    public static boolean hasUnitPriceContractSignal(AiQuerySemanticParseResult parse) {
        if (parse == null) {
            return false;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = parse.getSemanticSlots();
        if (s != null) {
            String met = normalizeToken(s.getMetric());
            if (met != null && met.contains("UNIT_PRICE")) {
                return true;
            }
        }
        if (parse.getMetric() != null && StringUtils.hasText(parse.getMetric().getPrimaryMetric())) {
            String pm = normalizeToken(parse.getMetric().getPrimaryMetric());
            return pm != null && pm.contains("UNIT_PRICE");
        }
        return false;
    }

    /**
     * GOODS 锚 + USE + supplier_amount_ranking wire/rankingType 误填，且具备单价合同信号时，允许 canonical 至 SUPPLIER_UNIT_PRICE 行。
     */
    public static boolean shouldCanonicalSupplierAmountToUnitPrice(
            AiQuerySemanticParseResult parse, AiConversationTurnMemory previousTurn) {
        if (parse == null || parse.getSemanticSlots() == null || previousTurn == null) {
            return false;
        }
        if (!PurchaseSemanticAnchorGate.hasUniqueExplicitGoodsAnchor(previousTurn)) {
            return false;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = parse.getSemanticSlots();
        if (!AiQuerySemanticSlotMerge.ANCHOR_USE_PREVIOUS.equals(normalizeToken(s.getAnchorPolicy()))) {
            return false;
        }
        if (!hasUnitPriceContractSignal(parse)) {
            return false;
        }
        String wire =
                AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(s.getStructuredIntentDetailWire());
        if (AiQuerySemanticLexicon.STRUCTURED_SUPPLIER_AMOUNT_RANKING.equals(wire)) {
            return true;
        }
        if (parse.getMetric() != null && StringUtils.hasText(parse.getMetric().getRankingType())) {
            String rt =
                    AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                            parse.getMetric().getRankingType());
            return AiQuerySemanticLexicon.STRUCTURED_SUPPLIER_AMOUNT_RANKING.equals(rt);
        }
        return false;
    }

    /** 采购追问 canonical 入口：突变 sem 并写入 {@link AiQuerySemanticParseResult#getPurchaseMatrixCanonicalReasons()}。 */
    public static AiQuerySemanticParseResult canonicalizePurchaseFollowUp(
            AiQuerySemanticParseResult raw, AiConversationTurnMemory previousTurn) {
        if (raw == null || raw.isParseMissing() || raw.getSemanticSlots() == null) {
            return raw;
        }
        List<String> reasons = new ArrayList<>();
        AiQuerySemanticParseResult adjusted = applySourceBreakdownOperationCanonical(raw, reasons);
        if (shouldCanonicalSupplierAmountToUnitPrice(adjusted, previousTurn)) {
            adjusted = applySupplierUnitPriceCanonical(adjusted);
            reasons.add(REASON_SUPPLIER_AMOUNT_TO_SUPPLIER_UNIT_PRICE);
        }
        if (reasons.isEmpty()) {
            return adjusted;
        }
        List<String> merged = new ArrayList<>();
        if (raw.getPurchaseMatrixCanonicalReasons() != null) {
            merged.addAll(raw.getPurchaseMatrixCanonicalReasons());
        }
        merged.addAll(reasons);
        return adjusted.toBuilder().purchaseMatrixCanonicalReasons(merged).build();
    }

    private static AiQuerySemanticParseResult applySourceBreakdownOperationCanonical(
            AiQuerySemanticParseResult raw, List<String> reasons) {
        AiQuerySemanticParseResult.SemanticSlotsPart s = raw.getSemanticSlots();
        if (s == null) {
            return raw;
        }
        String wireCanon =
                AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(s.getStructuredIntentDetailWire());
        String qo = normalizeToken(s.getQueryObject());
        String ap = normalizeToken(s.getAnchorPolicy());
        String op = normalizeToken(s.getOperation());
        String dw =
                AiQuerySemanticLexicon.canonicalDetailWanted(
                        s.getDetailWanted(), qo, op, wireCanon);
        String canonOp = canonicalOperation(op, dw, qo, ap, wireCanon);
        if (canonOp == null || canonOp.equals(op)) {
            return raw;
        }
        reasons.add(REASON_SOURCE_BREAKDOWN_DETAIL_TO_BREAKDOWN);
        AiQuerySemanticParseResult.SemanticSlotsPart updated =
                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                        .queryObject(s.getQueryObject())
                        .operation(canonOp)
                        .metric(s.getMetric())
                        .sourceFacet(s.getSourceFacet())
                        .anchorPolicy(s.getAnchorPolicy())
                        .detailWanted(s.getDetailWanted())
                        .structuredIntentDetailWire(s.getStructuredIntentDetailWire())
                        .build();
        return raw.toBuilder().semanticSlots(updated).build();
    }

    static AiQuerySemanticParseResult applySupplierUnitPriceCanonical(AiQuerySemanticParseResult raw) {
        AiQuerySemanticParseResult.SemanticSlotsPart s = raw.getSemanticSlots();
        String op = normalizeToken(s != null ? s.getOperation() : null);
        if (!StringUtils.hasText(op)) {
            op = "RANKING";
        }
        AiQuerySemanticParseResult.SemanticSlotsPart updated =
                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                        .queryObject("SUPPLIER")
                        .operation(op)
                        .metric("UNIT_PRICE")
                        .sourceFacet(AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE)
                        .anchorPolicy(AiQuerySemanticSlotMerge.ANCHOR_USE_PREVIOUS)
                        .detailWanted(AiQuerySemanticLexicon.DETAIL_WANTED_SUPPLIER_UNIT_PRICE)
                        .structuredIntentDetailWire(
                                AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY)
                        .build();
        AiQuerySemanticParseResult.MetricPart metric = raw.getMetric();
        AiQuerySemanticParseResult.MetricPart updatedMetric =
                metric != null
                        ? AiQuerySemanticParseResult.MetricPart.builder()
                                .primaryMetric(metric.getPrimaryMetric())
                                .rankingType(null)
                                .purchaseSourceType(AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE)
                                .stockReduceType(metric.getStockReduceType())
                                .build()
                        : AiQuerySemanticParseResult.MetricPart.builder()
                                .purchaseSourceType(AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE)
                                .build();
        return raw.toBuilder().semanticSlots(updated).metric(updatedMetric).build();
    }

    private static boolean rowShapeMatchesTokens(
            PurchaseDrilldownMatrixRow row,
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
