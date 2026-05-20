package com.nongxinle.ai.semantic.frame;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.harness.followup.BusinessCapabilityRegistry;
import com.nongxinle.ai.harness.followup.BusinessContextFrame;
import com.nongxinle.ai.harness.followup.BusinessContextFrameBuilder;
import com.nongxinle.ai.harness.followup.BusinessFollowUpSlot;
import com.nongxinle.ai.harness.followup.PurchaseDrilldownMatrix;
import com.nongxinle.ai.harness.followup.PurchaseFollowUpSlotSignals;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.AiQuerySemanticSlotMerge;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 采购域 CurrentSemanticFrame 完备性与自洽性校验（V2 主链路门禁）。
 * <p>职责：澄清/放行决策；只读 frame + parse，<strong>不突变</strong> sem；归一由 {@link CurrentSemanticFrame} 完成。
 * Registry 匹配仅作「是否可路由」终检，具体能力表在 {@link BusinessCapabilityRegistry}。
 */
public final class CurrentSemanticFrameValidator {

    private static final BusinessCapabilityRegistry PHASE1_PURCHASE_REGISTRY =
            BusinessCapabilityRegistry.phase1PurchaseOnly();

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
    private static final String Q_ANCHOR_POLICY =
            "当前采购语义不完整：请在解析 JSON 中给出有效 anchorPolicy（USE_PREVIOUS_ANCHOR / IGNORE_PREVIOUS_ANCHOR / REQUIRE_CLARIFICATION）。";
    private static final String Q_WIRE =
            "当前采购语义不完整：请在解析 JSON 中给出有效 structuredIntentDetailWire（合法采购子口径 wire）。";
    private static final String Q_DETAIL =
            "当前为采购追问或来源商品子口径：请在解析 JSON 中补齐 detailWanted。";
    private static final String Q_ANCHOR_PREV =
            "需要沿用上轮锚点，但上一轮结果中缺少唯一明确的商品或供货商锚点，请指明要追问的对象。";
    private static final String Q_REGISTRY =
            "当前追问与已注册采购能力不匹配，请补充更明确的追问意图（detailWanted / 锚点）。";
    private static final String Q_ANCHOR_POLICY_EXPLICIT =
            "当前需要你就锚点策略补充说明：请让用户选择沿用上一轮对象或重新指定范围。";
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
        List<String> codes = new ArrayList<>();
        boolean effFollow =
                PurchaseFollowUpSlotSignals.isEffectiveStructuralPurchaseFollowUp(
                        rawParse, previousTurn, normalizedUserMessage);
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
        // semanticSlots.sourceFacet 为采购主语义；metric.purchaseSourceType 仅为 compat/debug，不得 veto adoption。
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

        String ap = frame.getAnchorPolicy();
        if (!StringUtils.hasText(ap) || AiQuerySemanticSlotMerge.UNKNOWN.equals(ap)) {
            return SemanticFrameValidationResult.clarify(
                    Q_ANCHOR_POLICY, List.of("ANCHOR_POLICY_UNKNOWN"));
        }
        String apNorm = ap.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        boolean apOk =
                AiQuerySemanticSlotMerge.ANCHOR_USE_PREVIOUS.equals(apNorm)
                        || AiQuerySemanticSlotMerge.ANCHOR_IGNORE_PREVIOUS.equals(apNorm)
                        || AiQuerySemanticSlotMerge.ANCHOR_REQUIRE_CLARIFICATION.equals(apNorm);
        if (!apOk) {
            return SemanticFrameValidationResult.clarify(Q_ANCHOR_POLICY, List.of("ANCHOR_POLICY_UNPARSEABLE"));
        }
        if (AiQuerySemanticSlotMerge.ANCHOR_REQUIRE_CLARIFICATION.equals(apNorm)) {
            return SemanticFrameValidationResult.clarify(
                    Q_ANCHOR_POLICY_EXPLICIT, List.of("ANCHOR_POLICY_REQUIRE_CLARIFICATION"));
        }
        if (AiQuerySemanticSlotMerge.ANCHOR_USE_PREVIOUS.equals(apNorm)) {
            boolean breakdownGoods = "BREAKDOWN".equals(frame.getOperation());
            boolean anchorOk =
                    breakdownGoods
                            ? PurchaseSemanticAnchorGate.hasUniqueExplicitGoodsAnchor(previousTurn)
                            : PurchaseSemanticAnchorGate.hasUniqueExplicitPurchaseAnchor(previousTurn);
            if (!anchorOk) {
                return SemanticFrameValidationResult.clarify(Q_ANCHOR_PREV, List.of("USE_PREVIOUS_ANCHOR_NOT_UNIQUE"));
            }
        }

        if (!StringUtils.hasText(frame.getStructuredIntentDetailWire())
                || !PURCHASE_CANONICAL_WIRES.contains(frame.getStructuredIntentDetailWire())) {
            return SemanticFrameValidationResult.clarify(Q_WIRE, List.of("STRUCTURED_WIRE_INVALID"));
        }

        if (AiQuerySemanticLexicon.DETAIL_WANTED_SOURCE_BREAKDOWN.equals(frame.getDetailWanted())
                && !PurchaseDrilldownMatrix.operationAccepted(
                        PurchaseDrilldownMatrix.SOURCE_BREAKDOWN, frame.getOperation())) {
            return SemanticFrameValidationResult.clarify(
                    Q_BREAKDOWN_DETAIL_OP_MISMATCH, List.of("SOURCE_BREAKDOWN_REQUIRES_BREAKDOWN_OPERATION"));
        }

        if (AiQuerySemanticLexicon.DETAIL_WANTED_SUPPLIER_UNIT_PRICE.equals(frame.getDetailWanted())
                && !PurchaseDrilldownMatrix.frameMatchesRow(
                        frame, PurchaseDrilldownMatrix.SUPPLIER_UNIT_PRICE)) {
            return SemanticFrameValidationResult.clarify(
                    Q_BREAKDOWN_METRIC,
                    List.of(PurchaseDrilldownMatrix.VALIDATION_SUPPLIER_UNIT_PRICE_INCOMPLETE));
        }
        if (AiQuerySemanticLexicon.DETAIL_WANTED_SUPPLIER_BREAKDOWN.equals(frame.getDetailWanted())
                && !PurchaseDrilldownMatrix.frameMatchesRow(
                        frame, PurchaseDrilldownMatrix.SUPPLIER_BREAKDOWN)) {
            return SemanticFrameValidationResult.clarify(
                    Q_BREAKDOWN_DETAIL_SLOT, List.of("SUPPLIER_BREAKDOWN_FRAME_INCOMPLETE"));
        }
        if (AiQuerySemanticLexicon.DETAIL_WANTED_SOURCE_BREAKDOWN.equals(frame.getDetailWanted())
                && !PurchaseDrilldownMatrix.frameMatchesRow(
                        frame, PurchaseDrilldownMatrix.SOURCE_BREAKDOWN)) {
            return SemanticFrameValidationResult.clarify(
                    Q_BREAKDOWN_DETAIL_SLOT,
                    List.of(PurchaseDrilldownMatrix.VALIDATION_SOURCE_BREAKDOWN_INCOMPLETE));
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

        boolean overviewPivotExempt =
                PurchaseFollowUpSlotSignals.isPurchaseOverviewSummaryScopeTimePivotFollowUp(
                        rawParse, previousTurn, normalizedUserMessage);
        boolean completeRankingExempt =
                PurchaseFollowUpSlotSignals.isCompletePurchaseRankingFrame(frame)
                        || PurchaseDrilldownMatrix.frameMatchesRow(
                                frame, PurchaseDrilldownMatrix.SUPPLIER_UNIT_PRICE);
        boolean goodsQueryWire =
                AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY.equals(
                        frame.getStructuredIntentDetailWire());
        boolean detailRequired = goodsQueryWire;
        if (!detailRequired
                && !completeRankingExempt
                && !overviewPivotExempt
                && effFollow) {
            BusinessContextFrame bFrame = BusinessContextFrameBuilder.fromPreviousTurn(previousTurn);
            String prevCanon =
                    previousTurn != null
                            ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                                    previousTurn.getLastStructuredIntentDetail())
                            : null;
            String slotDetail =
                    PurchaseFollowUpSlotSignals.resolveSlotDetailWanted(
                            true,
                            normalizedUserMessage == null ? "" : normalizedUserMessage,
                            rawParse,
                            bFrame,
                            prevCanon);
            detailRequired = StringUtils.hasText(slotDetail);
        }
        if (detailRequired
                && (!StringUtils.hasText(frame.getDetailWanted())
                        || AiQuerySemanticSlotMerge.UNKNOWN.equals(frame.getDetailWanted()))) {
            return SemanticFrameValidationResult.clarify(Q_DETAIL, List.of("DETAIL_WANTED_REQUIRED"));
        }

        if (effFollow
                && StringUtils.hasText(frame.getDetailWanted())
                && !AiQuerySemanticSlotMerge.UNKNOWN.equals(frame.getDetailWanted())) {
            String norm = normalizedUserMessage == null ? "" : normalizedUserMessage;
            boolean skipRegistry =
                    PurchaseFollowUpSlotSignals.shouldSkipObjectDrilldownForTimeOnly(
                            rawParse, previousTurn, frame.getDetailWanted());
            if (!skipRegistry) {
                BusinessContextFrame bFrame = BusinessContextFrameBuilder.fromPreviousTurn(previousTurn);
                BusinessFollowUpSlot slot =
                        BusinessFollowUpSlot.builder()
                                .followUp(true)
                                .normalizedUserMessage(norm)
                                .slotDetailWanted(frame.getDetailWanted())
                                .semanticQueryObject(frame.getQueryObject())
                                .semanticOperation(frame.getOperation())
                                .semanticMetric(frame.getMetric())
                                .semanticSourceFacet(frame.getSourceFacet())
                                .semanticAnchorPolicy(frame.getAnchorPolicy())
                                .semanticStructuredIntentDetailWire(frame.getStructuredIntentDetailWire())
                                .build();
                if (PHASE1_PURCHASE_REGISTRY.match(bFrame, slot) == null) {
                    return SemanticFrameValidationResult.clarify(Q_REGISTRY, List.of("REGISTRY_NO_MATCH"));
                }
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

    /** 将 metric.purchaseSourceType / semanticSlots.sourceFacet 归一到 Lexicon 常量便于比对。 */
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
