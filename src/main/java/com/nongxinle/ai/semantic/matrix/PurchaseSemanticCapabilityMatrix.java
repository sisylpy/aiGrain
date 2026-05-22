package com.nongxinle.ai.semantic.matrix;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.semantic.capability.SemanticCapabilitySlot;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.AiQuerySemanticSlotMerge;
import com.nongxinle.ai.semantic.contract.SemanticCapabilityContract;
import com.nongxinle.ai.semantic.contract.SemanticCapabilityContractExportSummary;
import com.nongxinle.ai.semantic.contract.PurchaseSemanticCapabilityContractExporter;
import com.nongxinle.ai.semantic.frame.CurrentSemanticFrame;
import lombok.experimental.UtilityClass;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Phase 1：GOODS 锚点下钻矩阵（Harness Engineering 契约表）。
 * <p>职责：矩阵行定义 + 槽位/帧形状匹配 + 合同 canonical（无 NL）。
 * 参见 {@code docs/ai/purchase-answer-plan.md}。
 */
@UtilityClass
public final class PurchaseSemanticCapabilityMatrix {

    public static final String REASON_SOURCE_BREAKDOWN_DETAIL_TO_BREAKDOWN =
            "MATRIX_CANONICAL_SOURCE_BREAKDOWN_DETAIL_TO_BREAKDOWN";
    public static final String REASON_SUPPLIER_AMOUNT_TO_SUPPLIER_UNIT_PRICE =
            "MATRIX_CANONICAL_SUPPLIER_AMOUNT_TO_SUPPLIER_UNIT_PRICE";
    public static final String REASON_GOODS_ANCHOR_SUPPLIER_BREAKDOWN_FRAME =
            "MATRIX_CANONICAL_GOODS_ANCHOR_SUPPLIER_BREAKDOWN_FRAME";
    public static final String REASON_STRUCTURED_WIRE_REWRITTEN_FROM_SLOTS =
            "structured_wire_rewritten_from_slots";
    public static final String REASON_LLM_WIRE_OVERRIDDEN_BY_SLOTS = "llm_wire_overridden_by_slots";
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

    /**
     * P1：只读导出 GOODS 锚下钻 + Catalog 聚合；见 {@link SemanticContractCatalog}。
     */
    public static List<SemanticCapabilityContract> exportContracts() {
        return PurchaseSemanticCapabilityContractExporter.exportContracts();
    }

    public static SemanticCapabilityContractExportSummary exportContractSummary() {
        return PurchaseSemanticCapabilityContractExporter.exportContractSummary();
    }

    public static boolean isGoodsAnchoredExecutionFramePlanType(String framePlanType) {
        return framePlanType != null && GOODS_ANCHOR_PRIOR_FRAME_PLAN_TYPES.contains(framePlanType);
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

    public static boolean followUpSlotMatchesRow(SemanticCapabilitySlot slot, PurchaseSemanticCapabilityMatrixRow row) {
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
     * LLM 未显式给出 detailWanted 时，按 Matrix 合同行形状推断是否命中（contract-aligned inference；不读用户原文）。
     */
    public static boolean slotsInferRowShape(
            AiQuerySemanticParseResult sem, PurchaseSemanticCapabilityMatrixRow row) {
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
     * GOODS 维度追问：上一轮 {@code lastResultAnchors} 中是否存在唯一明确 GOODS 锚（口述 ID 或名称）。
     */
    private static boolean hasUniqueExplicitGoodsAnchor(AiConversationTurnMemory previousTurn) {
        if (previousTurn == null || previousTurn.getLastResultAnchors() == null) {
            return false;
        }
        List<AiResultAnchor> goods = new ArrayList<>();
        for (AiResultAnchor a : previousTurn.getLastResultAnchors()) {
            if (a == null || !StringUtils.hasText(a.getEntityType())) {
                continue;
            }
            if (!AiResultAnchor.ENTITY_TYPE_GOODS.equalsIgnoreCase(a.getEntityType().trim())) {
                continue;
            }
            if (!StringUtils.hasText(a.getEntityId()) && !StringUtils.hasText(a.getEntityName())) {
                continue;
            }
            goods.add(a);
        }
        if (goods.isEmpty()) {
            return false;
        }
        if (goods.size() == 1) {
            return true;
        }
        long rankOne =
                goods.stream()
                        .filter(a -> a.getRank() != null && a.getRank() == 1)
                        .count();
        return rankOne == 1;
    }

    /**
     * GOODS 锚 + USE + supplier_amount_ranking wire/rankingType 误填，且具备单价合同信号时，允许 canonical 至 SUPPLIER_UNIT_PRICE 行。
     */
    public static boolean shouldCanonicalSupplierAmountToUnitPrice(
            AiQuerySemanticParseResult parse, AiConversationTurnMemory previousTurn) {
        if (parse == null || parse.getSemanticSlots() == null || previousTurn == null) {
            return false;
        }
        if (!hasUniqueExplicitGoodsAnchor(previousTurn)) {
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

    /**
     * purchase_overview_path 下 structured wire 最终口径：semanticSlots 四槽完整时 slots 形状优先；
     * 仅当 slots 不完整或无法映射时才兼容合法 LLM wire，最后才读 merge 后 structuredDetail。
     */
    public static String resolveStructuredIntentDetailWire(
            AiQuerySemanticParseResult sem, String pathCode, String mergedStructuredDetail) {
        if (!AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW.equals(pathCode)) {
            return null;
        }
        if (hasCompletePurchaseCoreSlots(sem)) {
            String fromShape = inferMatrixWireFromSemanticSlots(sem);
            if (StringUtils.hasText(fromShape)) {
                return fromShape;
            }
        }
        if (sem != null && sem.getSemanticSlots() != null) {
            String slotRaw = sem.getSemanticSlots().getStructuredIntentDetailWire();
            if (StringUtils.hasText(slotRaw)) {
                String slotCanon =
                        AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(slotRaw.trim());
                if (AiQuerySemanticLexicon.isPurchaseOverviewDomainCanonicalWire(slotCanon)) {
                    return slotCanon;
                }
            }
        }
        if (StringUtils.hasText(mergedStructuredDetail)) {
            String mergedCanon =
                    AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                            mergedStructuredDetail.trim());
            if (AiQuerySemanticLexicon.isPurchaseOverviewDomainCanonicalWire(mergedCanon)) {
                return mergedCanon;
            }
        }
        return null;
    }

    /**
     * LLM 未填 {@code sourceFacet} 时，按矩阵行形状、异常 wire 族、或 {@code purchase_source_goods_query} wire 推断合法默认值。
     * 完整问句（含 LLM Rewrite 补全后）不要求 v2 必填 sourceFacet。
     */
    public static String inferDefaultSourceFacetWhenMissing(AiQuerySemanticParseResult sem) {
        if (sem == null || sem.getSemanticSlots() == null) {
            return null;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = sem.getSemanticSlots();
        if (normalizeSourceFacet(s.getSourceFacet()) != null) {
            return null;
        }
        for (PurchaseSemanticCapabilityMatrixRow row : goodsAnchorRows()) {
            if (slotsInferRowShape(sem, row)) {
                return row.getRequiredSourceFacet();
            }
        }
        String wireCanon =
                StringUtils.hasText(s.getStructuredIntentDetailWire())
                        ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                                s.getStructuredIntentDetailWire().trim())
                        : null;
        if (AiQuerySemanticLexicon.isPurchaseAnomalyDetectionWire(wireCanon)
                || isPurchaseAnomalyDetectionOperation(normalizeToken(s.getOperation()))) {
            return AiQuerySemanticLexicon.SOURCE_ALL;
        }
        if (!AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY.equals(wireCanon)) {
            return null;
        }
        String qo = normalizeToken(s.getQueryObject());
        String op = normalizeToken(s.getOperation());
        String dw =
                AiQuerySemanticLexicon.canonicalDetailWanted(
                        s.getDetailWanted(), qo, op, wireCanon);
        if (AiQuerySemanticLexicon.DETAIL_WANTED_SOURCE_BREAKDOWN.equals(dw)) {
            return AiQuerySemanticLexicon.SOURCE_ALL;
        }
        if ("SUPPLIER".equals(qo)) {
            return AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE;
        }
        if ("GOODS".equals(qo)) {
            if (AiQuerySemanticLexicon.DETAIL_WANTED_SUPPLIER_BREAKDOWN.equals(dw)
                    || AiQuerySemanticLexicon.DETAIL_WANTED_SUPPLIER_UNIT_PRICE.equals(dw)) {
                return AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE;
            }
            if ("BREAKDOWN".equals(op)) {
                return AiQuerySemanticLexicon.SOURCE_ALL;
            }
            return AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE;
        }
        return AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE;
    }

    /** 四槽 queryObject / operation / metric / sourceFacet 均已完整（非 UNKNOWN）。 */
    static boolean hasCompletePurchaseCoreSlots(AiQuerySemanticParseResult sem) {
        if (sem == null || sem.getSemanticSlots() == null) {
            return false;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = sem.getSemanticSlots();
        return isCompletePurchaseSlotToken(s.getQueryObject())
                && isCompletePurchaseSlotToken(s.getOperation())
                && isCompletePurchaseSlotToken(s.getMetric())
                && normalizeSourceFacet(s.getSourceFacet()) != null;
    }

    private static boolean isCompletePurchaseSlotToken(String raw) {
        String t = normalizeToken(raw);
        return StringUtils.hasText(t) && !AiQuerySemanticSlotMerge.UNKNOWN.equals(t);
    }

    /**
     * 仅依据 semanticSlots 形状推断 wire（不读用户原话、不用 metric.rankingType）。
     */
    public static String inferMatrixWireFromSemanticSlots(AiQuerySemanticParseResult sem) {
        if (sem == null || sem.getSemanticSlots() == null) {
            return null;
        }
        for (PurchaseSemanticCapabilityMatrixRow row : goodsAnchorRows()) {
            if (slotsInferRowShape(sem, row)) {
                return row.getRequiredStructuredIntentDetailWire();
            }
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = sem.getSemanticSlots();
        String op = normalizeToken(s.getOperation());
        String qo = normalizeToken(s.getQueryObject());
        String metric = normalizeToken(s.getMetric());
        String sf = normalizeSourceFacet(s.getSourceFacet());
        if ("RANKING".equals(op)) {
            if ("GOODS".equals(qo)) {
                if (metric != null && metric.contains("COUNT")) {
                    return AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_COUNT_RANKING;
                }
                return AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_AMOUNT_RANKING;
            }
            if ("SUPPLIER".equals(qo)) {
                return AiQuerySemanticLexicon.STRUCTURED_SUPPLIER_AMOUNT_RANKING;
            }
            if ("STORE".equals(qo) || "BUSINESS".equals(qo)) {
                return AiQuerySemanticLexicon.STRUCTURED_PURCHASE_STORE_AMOUNT_RANKING;
            }
        }
        if ("SUMMARY".equals(op) || "OVERVIEW".equals(op)) {
            if ("SUPPLIER".equals(qo)) {
                return AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_AMOUNT_QUERY;
            }
            if (AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE.equals(sf)) {
                return AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_SUMMARY;
            }
            if (AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE.equals(sf)) {
                return AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_AMOUNT_QUERY;
            }
            if (metric != null && (metric.contains("PURCHASE") || metric.contains("AMOUNT"))) {
                return AiQuerySemanticLexicon.STRUCTURED_PURCHASE_OVERVIEW_SUMMARY;
            }
        }
        return null;
    }

    /**
     * GOODS 锚 + {@link #SUPPLIER_BREAKDOWN} 合同帧形状是否成立（contract shape；非 alias）。
     * <p>仅当 wire 已为 registered {@code purchase_source_goods_query}，且槽位为
     * {@code GOODS}/{@code DETAIL|BREAKDOWN} + {@code USE_PREVIOUS_ANCHOR} 等合同形状时成立。
     * <p>合同外 wire 字面量不再 silent 归一；strict 下由 Validator 报 {@code UNSUPPORTED_WIRE}。
     */
    public static boolean matchesGoodsAnchorSupplierBreakdownFrame(AiQuerySemanticParseResult parse) {
        if (parse == null || parse.getSemanticSlots() == null) {
            return false;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = parse.getSemanticSlots();
        if (slotsAlreadyGoodsAnchorSupplierBreakdownFrame(s)) {
            return false;
        }
        String wireRaw = s.getStructuredIntentDetailWire();
        if (!StringUtils.hasText(wireRaw)) {
            return false;
        }
        String wireCanon =
                AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wireRaw.trim());
        if (!AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY.equals(wireCanon)) {
            return false;
        }
        if (hasUnitPriceContractSignal(parse)) {
            return false;
        }
        String dw = normalizeToken(s.getDetailWanted());
        if (AiQuerySemanticLexicon.DETAIL_WANTED_SUPPLIER_UNIT_PRICE.equals(dw)
                || AiQuerySemanticLexicon.DETAIL_WANTED_SOURCE_BREAKDOWN.equals(dw)) {
            return false;
        }
        String qo = normalizeToken(s.getQueryObject());
        String op = normalizeToken(s.getOperation());
        if ("SUPPLIER".equals(qo) && ("DETAIL".equals(op) || "BREAKDOWN".equals(op))) {
            return dw == null || AiQuerySemanticLexicon.DETAIL_WANTED_SUPPLIER_BREAKDOWN.equals(dw);
        }
        if ("GOODS".equals(qo) && ("DETAIL".equals(op) || "BREAKDOWN".equals(op))) {
            if (AiQuerySemanticLexicon.DETAIL_WANTED_SUPPLIER_UNIT_PRICE.equals(dw)
                    || AiQuerySemanticLexicon.DETAIL_WANTED_SOURCE_BREAKDOWN.equals(dw)) {
                return false;
            }
            if (hasUnitPriceContractSignal(parse)) {
                return false;
            }
            return dw == null || AiQuerySemanticLexicon.DETAIL_WANTED_SUPPLIER_BREAKDOWN.equals(dw);
        }
        return false;
    }

    /** 补全 {@link #SUPPLIER_BREAKDOWN} 合同帧必填槽（detailWanted / sourceFacet / registered wire）；非 alias 归一。 */
    public static AiQuerySemanticParseResult completeGoodsAnchorSupplierBreakdownFrame(
            AiQuerySemanticParseResult raw) {
        if (raw == null || raw.getSemanticSlots() == null) {
            return raw;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = raw.getSemanticSlots();
        String metric = normalizeToken(s.getMetric());
        if (!StringUtils.hasText(metric)) {
            metric = "PURCHASE_AMOUNT";
        } else if (!metricContainsAny(metric, SUPPLIER_BREAKDOWN.getAllowedMetricContains())) {
            metric = "PURCHASE_AMOUNT";
        }
        String anchor = normalizeToken(s.getAnchorPolicy());
        if (!AiQuerySemanticSlotMerge.ANCHOR_USE_PREVIOUS.equals(anchor)) {
            anchor = AiQuerySemanticSlotMerge.ANCHOR_USE_PREVIOUS;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart updated =
                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                        .queryObject("GOODS")
                        .operation(canonicalizeOperationForRow(SUPPLIER_BREAKDOWN, s.getOperation()))
                        .metric(metric)
                        .sourceFacet(AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE)
                        .anchorPolicy(anchor)
                        .detailWanted(AiQuerySemanticLexicon.DETAIL_WANTED_SUPPLIER_BREAKDOWN)
                        .structuredIntentDetailWire(
                                AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY)
                        .answerPlanType(PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL)
                        .build();
        AiQuerySemanticParseResult.MetricPart metricPart = raw.getMetric();
        AiQuerySemanticParseResult.MetricPart updatedMetric =
                metricPart != null
                        ? metricPart.toBuilder()
                                .primaryMetric(metric)
                                .purchaseSourceType(AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE)
                                .rankingType(null)
                                .build()
                        : AiQuerySemanticParseResult.MetricPart.builder()
                                .primaryMetric(metric)
                                .purchaseSourceType(AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE)
                                .build();
        List<String> reasons = new ArrayList<>();
        if (raw.getPurchaseMatrixCanonicalReasons() != null) {
            reasons.addAll(raw.getPurchaseMatrixCanonicalReasons());
        }
        reasons.add(REASON_GOODS_ANCHOR_SUPPLIER_BREAKDOWN_FRAME);
        return raw.toBuilder()
                .semanticSlots(updated)
                .metric(updatedMetric)
                .purchaseMatrixCanonicalReasons(reasons)
                .build();
    }

    /**
     * GOODS 锚 + {@code purchase_source_goods_query}：按 Matrix 行形状推断 {@code detailWanted}（不读用户原文）。
     */
    public static String inferGoodsAnchorDetailWanted(AiQuerySemanticParseResult parse) {
        if (parse == null || parse.getSemanticSlots() == null) {
            return null;
        }
        String wire =
                AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                        parse.getSemanticSlots().getStructuredIntentDetailWire());
        if (!AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY.equals(wire)) {
            return null;
        }
        for (PurchaseSemanticCapabilityMatrixRow row : goodsAnchorRows()) {
            if (slotsInferRowShape(parse, row)) {
                return row.getRequiredDetailWanted();
            }
        }
        return null;
    }

    /**
     * Contract observe 采购域 canonical：首轮 Matrix 槽位补全 + GOODS 锚追问（{@link #canonicalizePurchaseFollowUp}）。
     */
    public static AiQuerySemanticParseResult canonicalizePurchaseContractFrame(
            AiQuerySemanticParseResult raw, AiConversationTurnMemory previousTurn) {
        if (raw == null || raw.isParseMissing() || raw.getSemanticSlots() == null) {
            return raw;
        }
        AiQuerySemanticParseResult adjusted = applyPurchaseFirstTurnMatrixRowContractCompletion(raw);
        return canonicalizePurchaseFollowUp(adjusted, previousTurn);
    }

    /** 采购追问 canonical 入口：突变 sem 并写入 {@link AiQuerySemanticParseResult#getPurchaseMatrixCanonicalReasons()}。 */
    public static AiQuerySemanticParseResult canonicalizePurchaseFollowUp(
            AiQuerySemanticParseResult raw, AiConversationTurnMemory previousTurn) {
        if (raw == null || raw.isParseMissing() || raw.getSemanticSlots() == null) {
            return raw;
        }
        List<String> reasons = new ArrayList<>();
        AiQuerySemanticParseResult adjusted = raw;
        if (matchesGoodsAnchorSupplierBreakdownFrame(adjusted)) {
            adjusted = completeGoodsAnchorSupplierBreakdownFrame(adjusted);
        }
        adjusted = applyInferredGoodsAnchorDetailWanted(adjusted, reasons);
        adjusted = applyGoodsAnchorMatrixRowContractCompletion(adjusted, reasons);
        adjusted = applySourceBreakdownOperationCanonical(adjusted, reasons);
        if (shouldCanonicalSupplierAmountToUnitPrice(adjusted, previousTurn)) {
            adjusted = applySupplierUnitPriceCanonical(adjusted);
            reasons.add(REASON_SUPPLIER_AMOUNT_TO_SUPPLIER_UNIT_PRICE);
        }
        if (reasons.isEmpty()
                && (adjusted.getPurchaseMatrixCanonicalReasons() == null
                        || adjusted.getPurchaseMatrixCanonicalReasons().isEmpty())) {
            return adjusted;
        }
        List<String> merged = new ArrayList<>();
        if (raw.getPurchaseMatrixCanonicalReasons() != null) {
            merged.addAll(raw.getPurchaseMatrixCanonicalReasons());
        }
        if (adjusted.getPurchaseMatrixCanonicalReasons() != null) {
            merged.addAll(adjusted.getPurchaseMatrixCanonicalReasons());
        }
        merged.addAll(reasons);
        return adjusted.toBuilder().purchaseMatrixCanonicalReasons(merged).build();
    }

    private static AiQuerySemanticParseResult applyInferredGoodsAnchorDetailWanted(
            AiQuerySemanticParseResult raw, List<String> reasons) {
        if (raw == null || raw.getSemanticSlots() == null) {
            return raw;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = raw.getSemanticSlots();
        if (StringUtils.hasText(s.getDetailWanted())
                && !AiQuerySemanticSlotMerge.UNKNOWN.equalsIgnoreCase(s.getDetailWanted().trim())) {
            return raw;
        }
        String inferred = inferGoodsAnchorDetailWanted(raw);
        if (!StringUtils.hasText(inferred)) {
            return raw;
        }
        reasons.add("infer_goods_anchor_detail_wanted:" + inferred);
        AiQuerySemanticParseResult.SemanticSlotsPart updated =
                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                        .queryObject(s.getQueryObject())
                        .operation(s.getOperation())
                        .metric(s.getMetric())
                        .sourceFacet(
                                StringUtils.hasText(s.getSourceFacet())
                                        ? s.getSourceFacet()
                                        : AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE)
                        .anchorPolicy(s.getAnchorPolicy())
                        .detailWanted(inferred)
                        .structuredIntentDetailWire(s.getStructuredIntentDetailWire())
                        .answerPlanType(s.getAnswerPlanType())
                        .build();
        return raw.toBuilder().semanticSlots(updated).build();
    }

    /**
     * GOODS 锚 Matrix 行合同帧补全：{@code sourceFacet} / {@code detailWanted} / {@code wire} / {@code answerPlanType}
     * 与 {@link SemanticCapabilityContractMatcher} 对齐（Contract observe 路径，非 execution-only）。
     */
    private static AiQuerySemanticParseResult applyGoodsAnchorMatrixRowContractCompletion(
            AiQuerySemanticParseResult raw, List<String> reasons) {
        if (raw == null || raw.getSemanticSlots() == null) {
            return raw;
        }
        for (PurchaseSemanticCapabilityMatrixRow row : goodsAnchorRows()) {
            if (!slotsInferRowShape(raw, row)) {
                continue;
            }
            AiQuerySemanticParseResult.SemanticSlotsPart s = raw.getSemanticSlots();
            String metric = normalizeToken(s.getMetric());
            if (!StringUtils.hasText(metric)) {
                metric = "PURCHASE_AMOUNT";
            } else if (!metricContainsAny(metric, row.getAllowedMetricContains())) {
                metric = "PURCHASE_AMOUNT";
            }
            String anchor = normalizeToken(s.getAnchorPolicy());
            if (!AiQuerySemanticSlotMerge.ANCHOR_USE_PREVIOUS.equals(anchor)) {
                anchor = AiQuerySemanticSlotMerge.ANCHOR_USE_PREVIOUS;
            }
            String op = canonicalizeOperationForRow(row, s.getOperation());
            if (!StringUtils.hasText(op)) {
                op = row.getAllowedOperations().iterator().next();
            }
            String facet = row.getRequiredSourceFacet();
            String dw = row.getRequiredDetailWanted();
            String wire = row.getRequiredStructuredIntentDetailWire();
            String planType = row.getTargetPurchasePlanType();
            AiQuerySemanticParseResult.SemanticSlotsPart updated =
                    AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                            .queryObject("GOODS")
                            .operation(op)
                            .metric(metric)
                            .sourceFacet(facet)
                            .anchorPolicy(anchor)
                            .detailWanted(dw)
                            .structuredIntentDetailWire(wire)
                            .answerPlanType(planType)
                            .build();
            AiQuerySemanticParseResult.MetricPart metricPart = raw.getMetric();
            AiQuerySemanticParseResult.MetricPart updatedMetric =
                    metricPart != null
                            ? metricPart.toBuilder()
                                    .primaryMetric(metric)
                                    .purchaseSourceType(facet)
                                    .rankingType(null)
                                    .build()
                            : AiQuerySemanticParseResult.MetricPart.builder()
                                    .primaryMetric(metric)
                                    .purchaseSourceType(facet)
                                    .build();
            reasons.add("complete_goods_anchor_matrix_row:" + row.getCapabilityId());
            return raw.toBuilder().semanticSlots(updated).metric(updatedMetric).build();
        }
        return raw;
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

    private static boolean slotsAlreadyGoodsAnchorSupplierBreakdownFrame(
            AiQuerySemanticParseResult.SemanticSlotsPart s) {
        if (s == null) {
            return false;
        }
        String wire =
                AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(s.getStructuredIntentDetailWire());
        if (!AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY.equals(wire)) {
            return false;
        }
        if (!"GOODS".equals(normalizeToken(s.getQueryObject()))) {
            return false;
        }
        if (!AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE.equals(normalizeSourceFacet(s.getSourceFacet()))) {
            return false;
        }
        String dw =
                AiQuerySemanticLexicon.canonicalDetailWanted(
                        s.getDetailWanted(),
                        s.getQueryObject(),
                        s.getOperation(),
                        wire);
        return AiQuerySemanticLexicon.DETAIL_WANTED_SUPPLIER_BREAKDOWN.equals(dw)
                && PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL.equals(s.getAnswerPlanType());
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

    private static boolean isPurchaseAnomalyDetectionOperation(String operation) {
        if (!StringUtils.hasText(operation)) {
            return false;
        }
        String op = operation.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return "ANOMALY_DETECTION".equals(op) || "ANOMALY".equals(op);
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

    /**
     * 首轮采购合同帧：仅依据 semanticSlots 形状补全 wire / 四槽（不读用户原文、不做合同外 wire 别名）。
     */
    private static AiQuerySemanticParseResult applyPurchaseFirstTurnMatrixRowContractCompletion(
            AiQuerySemanticParseResult raw) {
        if (slotsInferPurchaseOverviewSummaryShape(raw)) {
            return mergePurchaseFirstTurnContractSlots(
                    raw, AiQuerySemanticLexicon.STRUCTURED_PURCHASE_OVERVIEW_SUMMARY);
        }
        if (slotsInferPurchaseSourceSummaryShape(raw)) {
            String sf = normalizeSourceFacet(raw.getSemanticSlots().getSourceFacet());
            if (AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE.equals(sf)) {
                return mergePurchaseFirstTurnContractSlots(
                        raw, AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_SUMMARY);
            }
            if (AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE.equals(sf)) {
                return mergePurchaseFirstTurnContractSlots(
                        raw, AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_SUMMARY);
            }
        }
        String inferred = inferMatrixWireFromSemanticSlots(raw);
        if (!StringUtils.hasText(inferred)) {
            return raw;
        }
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(inferred);
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_AMOUNT_QUERY.equals(canon)
                && slotsInferPurchaseOverviewSummaryShape(raw)) {
            canon = AiQuerySemanticLexicon.STRUCTURED_PURCHASE_OVERVIEW_SUMMARY;
        }
        return mergePurchaseFirstTurnContractSlots(raw, canon);
    }

    private static AiQuerySemanticParseResult mergePurchaseFirstTurnContractSlots(
            AiQuerySemanticParseResult raw, String canonWire) {
        if (raw == null || raw.getSemanticSlots() == null || !StringUtils.hasText(canonWire)) {
            return raw;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = raw.getSemanticSlots();
        String sf = normalizeSourceFacet(s.getSourceFacet());
        String op = normalizeSummaryOrOverviewOp(s.getOperation());
        String metric = normalizePurchaseMetricToken(s.getMetric());
        String qo;
        String sourceFacet;
        String planType;
        switch (canonWire) {
            case AiQuerySemanticLexicon.STRUCTURED_PURCHASE_OVERVIEW_SUMMARY -> {
                qo = "PURCHASE_ORDER";
                sourceFacet = AiQuerySemanticLexicon.SOURCE_ALL;
                planType = PurchaseAnswerPlan.TYPE_PURCHASE_OVERVIEW;
            }
            case AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_SUMMARY -> {
                if (AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE.equals(sf)) {
                    qo = "PURCHASE_ORDER";
                    sourceFacet = AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE;
                    planType = PurchaseAnswerPlan.TYPE_PURCHASE_SELF_OVERVIEW;
                } else if (AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE.equals(sf)) {
                    qo = "SUPPLIER";
                    sourceFacet = AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE;
                    planType = PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_OVERVIEW;
                } else {
                    qo = "PURCHASE_ORDER";
                    return mergePurchaseFirstTurnContractSlots(
                            raw, AiQuerySemanticLexicon.STRUCTURED_PURCHASE_OVERVIEW_SUMMARY);
                }
            }
            case AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_AMOUNT_RANKING -> {
                qo = "GOODS";
                op = "RANKING";
                sourceFacet =
                        StringUtils.hasText(sf) ? sf : AiQuerySemanticLexicon.SOURCE_ALL;
                planType = PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_AMOUNT_RANKING;
                metric = metric != null && metric.contains("COUNT")
                        ? "PURCHASE_COUNT"
                        : "PURCHASE_AMOUNT";
            }
            case AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_COUNT_RANKING -> {
                qo = "GOODS";
                op = "RANKING";
                sourceFacet =
                        StringUtils.hasText(sf) ? sf : AiQuerySemanticLexicon.SOURCE_ALL;
                planType = PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_COUNT_RANKING;
                metric = "PURCHASE_COUNT";
            }
            case AiQuerySemanticLexicon.STRUCTURED_SUPPLIER_AMOUNT_RANKING -> {
                qo = "SUPPLIER";
                op = "RANKING";
                sourceFacet = AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE;
                planType = PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_AMOUNT_RANKING;
                metric = "PURCHASE_AMOUNT";
            }
            case AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_AMOUNT_QUERY -> {
                if (!slotsInferPurchaseSourceAmountQueryShape(raw)) {
                    return raw;
                }
                qo = "SUPPLIER";
                op = "SUMMARY";
                sourceFacet = AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE;
                planType = PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_OVERVIEW;
                metric = "PURCHASE_AMOUNT";
            }
            default -> {
                return raw;
            }
        }
        AiQuerySemanticParseResult.SemanticSlotsPart updated =
                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                        .queryObject(qo)
                        .operation(op)
                        .metric(metric)
                        .sourceFacet(sourceFacet)
                        .anchorPolicy(s.getAnchorPolicy())
                        .detailWanted(s.getDetailWanted())
                        .structuredIntentDetailWire(canonWire)
                        .answerPlanType(planType)
                        .build();
        return raw.toBuilder().semanticSlots(updated).build();
    }

    private static boolean slotsInferPurchaseOverviewSummaryShape(AiQuerySemanticParseResult raw) {
        if (raw == null || raw.getSemanticSlots() == null) {
            return false;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = raw.getSemanticSlots();
        String op = normalizeToken(s.getOperation());
        if (!"SUMMARY".equals(op) && !"OVERVIEW".equals(op)) {
            return false;
        }
        if ("SUPPLIER".equals(normalizeToken(s.getQueryObject()))) {
            return false;
        }
        String metric = normalizeToken(s.getMetric());
        if (metric != null && !metricContainsAny(metric, Set.of("PURCHASE_AMOUNT", "PURCHASE_COUNT"))) {
            return false;
        }
        String sf = normalizeSourceFacet(s.getSourceFacet());
        return sf == null || AiQuerySemanticLexicon.SOURCE_ALL.equals(sf);
    }

    /** 用户明确自采/供货商渠道 + 概览 operation → {@code purchase_source_summary} 合同形状。 */
    private static boolean slotsInferPurchaseSourceSummaryShape(AiQuerySemanticParseResult raw) {
        if (raw == null || raw.getSemanticSlots() == null) {
            return false;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = raw.getSemanticSlots();
        String op = normalizeToken(s.getOperation());
        if (!"SUMMARY".equals(op) && !"OVERVIEW".equals(op)) {
            return false;
        }
        if ("SUPPLIER".equals(normalizeToken(s.getQueryObject()))) {
            return false;
        }
        String sf = normalizeSourceFacet(s.getSourceFacet());
        return AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE.equals(sf)
                || AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE.equals(sf);
    }

    private static boolean slotsInferPurchaseSourceAmountQueryShape(AiQuerySemanticParseResult raw) {
        if (raw == null || raw.getSemanticSlots() == null) {
            return false;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = raw.getSemanticSlots();
        return "SUPPLIER".equals(normalizeToken(s.getQueryObject()))
                && ("SUMMARY".equals(normalizeToken(s.getOperation()))
                        || "OVERVIEW".equals(normalizeToken(s.getOperation())));
    }

    private static String normalizeSummaryOrOverviewOp(String raw) {
        String op = normalizeToken(raw);
        if ("OVERVIEW".equals(op)) {
            return "SUMMARY";
        }
        return StringUtils.hasText(op) ? op : "SUMMARY";
    }

    private static String normalizePurchaseMetricToken(String raw) {
        String metric = normalizeToken(raw);
        if (!StringUtils.hasText(metric)) {
            return "PURCHASE_AMOUNT";
        }
        if (metricContainsAny(metric, Set.of("PURCHASE_AMOUNT", "PURCHASE_COUNT", "PURCHASE_QUANTITY"))) {
            return metric.contains("COUNT") ? "PURCHASE_COUNT" : "PURCHASE_AMOUNT";
        }
        return "PURCHASE_AMOUNT";
    }
}
