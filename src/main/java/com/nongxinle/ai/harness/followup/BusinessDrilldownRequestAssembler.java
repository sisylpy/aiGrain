package com.nongxinle.ai.harness.followup;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiFollowUpResolution;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.frame.CurrentSemanticFrame;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将 Frame + Slot 与 Registry 组合为 {@link BusinessDrilldownRequest}；Phase 1 仅采购试点。
 */
public final class BusinessDrilldownRequestAssembler {

    private static final BusinessCapabilityRegistry REGISTRY = BusinessCapabilityRegistry.phase1PurchaseWithDish();

    private BusinessDrilldownRequestAssembler() {
    }

    public static Phase1PurchaseApplyResult applyPhase1PurchaseCapabilities(
            String normalizedUserMessage,
            AiFollowUpResolution followUp,
            AiConversationTurnMemory previousTurn,
            AiResolvedQueryIntent queryIntent,
            AiQuerySemanticParseResult semanticLlm) {

        BusinessContextFrame frame = BusinessContextFrameBuilder.fromPreviousTurn(previousTurn);
        boolean followUpFlag = followUp != null && followUp.isFollowUp();
        boolean structuralPurchaseFollowThrough =
                PurchaseFollowUpSlotSignals.isEffectiveStructuralPurchaseFollowUp(
                        semanticLlm, previousTurn, normalizedUserMessage);
        boolean structuralDishFollowThrough =
                PurchaseFollowUpSlotSignals.isEffectiveStructuralDishFollowUp(semanticLlm, previousTurn);
        boolean effectiveFollowUpForPhase1Slots =
                followUpFlag || structuralPurchaseFollowThrough || structuralDishFollowThrough;
        String wireCanon =
                queryIntent != null
                        ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                                queryIntent.getStructuredIntentDetail())
                        : null;
        String slotDetail =
                PurchaseFollowUpSlotSignals.resolveSlotDetailWanted(
                        effectiveFollowUpForPhase1Slots, normalizedUserMessage, semanticLlm, frame, wireCanon);
        CurrentSemanticFrame slotFrame =
                semanticLlm != null
                        ? CurrentSemanticFrame.fromParseResult(semanticLlm, previousTurn)
                        : null;
        BusinessFollowUpSlot.BusinessFollowUpSlotBuilder slotB =
                BusinessFollowUpSlot.builder()
                        .followUp(effectiveFollowUpForPhase1Slots)
                        .normalizedUserMessage(normalizedUserMessage)
                        .slotDetailWanted(slotDetail);
        if (slotFrame != null) {
            slotB.semanticQueryObject(slotFrame.getQueryObject())
                    .semanticOperation(slotFrame.getOperation())
                    .semanticMetric(slotFrame.getMetric())
                    .semanticSourceFacet(slotFrame.getSourceFacet())
                    .semanticAnchorPolicy(slotFrame.getAnchorPolicy())
                    .semanticStructuredIntentDetailWire(slotFrame.getStructuredIntentDetailWire());
        }
        BusinessFollowUpSlot slot =
                DishProfitDrilldownMatrix.alignRegistryFollowUpSlot(
                        frame, slotB.build(), wireCanon, semanticLlm);

        boolean skipForTime =
                PurchaseFollowUpSlotSignals.shouldSkipObjectDrilldownForTimeOnly(
                        semanticLlm, previousTurn, slotDetail);

        BusinessCapabilityMatch match = skipForTime ? null : REGISTRY.match(frame, slot);
        if (match == null
                && !skipForTime
                && StringUtils.hasText(
                        DishProfitDrilldownMatrix.resolveFollowUpDetailWanted(
                                frame, semanticLlm, wireCanon))) {
            match =
                    DishProfitDrilldownMatrix.synthesizeDishAnchorIngredientBreakdownMatch(
                            frame, slot.getSlotDetailWanted());
        }

        Map<String, Object> debug = new LinkedHashMap<>();
        debug.put("framePlanType", frame.getFramePlanType());
        debug.put("framePurchaseSourceType", frame.getLastPurchaseSourceType());
        debug.put("slotDetailWanted", slotDetail);
        debug.put("followUpRegistrySkippedForTimeOnly", skipForTime);
        if (match != null) {
            debug.put("matchedCapabilityId", match.getCapabilityId());
            debug.put("followUpRegistryQueryMode", match.getQueryMode());
        } else {
            debug.put("matchedCapabilityId", null);
            debug.put("followUpRegistryQueryMode", null);
            if (!followUpFlag && !structuralPurchaseFollowThrough && !structuralDishFollowThrough) {
                debug.put("unsupportedReason", BusinessFollowUpUnsupportedReason.NOT_FOLLOW_UP.name());
            } else if (!frame.isPurchasePath()
                    && !AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(nullToEmpty(frame.getLastPathCode()))) {
                debug.put("unsupportedReason", BusinessFollowUpUnsupportedReason.NOT_PURCHASE_PATH_FRAME.name());
            } else if (!StringUtils.hasText(slotDetail)) {
                debug.put("unsupportedReason", BusinessFollowUpUnsupportedReason.NO_DETAIL_SLOT.name());
            } else {
                debug.put("unsupportedReason", BusinessFollowUpUnsupportedReason.NO_MATCHING_CAPABILITY.name());
            }
        }

        // Phase 1 CurrentSemanticFrame：结构化 intent / PST 由 LLM slots + merge 负责；Registry 只做匹配与追问前置探测，不在此处改写意图。
        boolean intentNeedsMutation = false;

        String proposedAction = null;
        String proposedDetail = null;
        String proposedSourcePlan = null;
        PurchasePhase1FollowUpTargets.Probe phase1Target = PurchasePhase1FollowUpTargets.Probe.EMPTY;
        if (match != null && !skipForTime) {
            proposedAction = "OBJECT_DRILLDOWN";
            proposedDetail = inferContextFollowUpDetail(match, slotDetail);
            proposedSourcePlan = frame.getFramePlanType();
            phase1Target = PurchasePhase1FollowUpTargets.resolve(match, frame);
        }
        if (match != null
                && "purchase.goods_anchor.source_breakdown".equals(match.getCapabilityId())
                && AiResultAnchor.ENTITY_TYPE_GOODS.equals(phase1Target.entityType())
                && !StringUtils.hasText(phase1Target.entityId())) {
            debug.put("goodsAnchorIdMissing", Boolean.TRUE);
        }
        return new Phase1PurchaseApplyResult(
                debug,
                intentNeedsMutation,
                proposedAction,
                proposedDetail,
                proposedSourcePlan,
                match,
                phase1Target.entityType(),
                phase1Target.entityName(),
                phase1Target.entityId());
    }

    public static BusinessDrilldownRequest toRequest(
            BusinessCapabilityMatch match, BusinessContextFrame frame, BusinessFollowUpSlot slot) {
        if (match == null) {
            return null;
        }
        return BusinessDrilldownRequest.builder()
                .match(match)
                .frame(frame)
                .slot(slot)
                .proposedFollowUpAction("OBJECT_DRILLDOWN")
                .proposedFollowUpDetailWanted(
                        inferContextFollowUpDetail(match, slot != null ? slot.getSlotDetailWanted() : null))
                .proposedFollowUpSourcePlanType(frame != null ? frame.getFramePlanType() : null)
                .build();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    static String inferContextFollowUpDetail(BusinessCapabilityMatch match, String slotDetailWanted) {
        if (match == null) {
            return null;
        }
        return switch (match.getCapabilityId()) {
            case "purchase.supplier_channel.goods_detail", "purchase.self_channel.goods_detail" -> "GOODS_DETAIL";
            case "purchase.goods_anchor.supplier_unit_price" -> "SUPPLIER_UNIT_PRICE";
            case "purchase.goods_anchor.supplier_breakdown" -> "SUPPLIER_BREAKDOWN";
            case "purchase.goods_anchor.source_breakdown" -> "SOURCE_BREAKDOWN";
            case "purchase.supplier_anchor.goods_detail" ->
                    "GOODS_DETAIL".equals(slotDetailWanted) ? "GOODS_DETAIL" : "GOODS_UNIT_PRICE";
            case DishProfitDrilldownMatrix.CAPABILITY_DISH_ANCHOR_INGREDIENT_BREAKDOWN ->
                    DishProfitDrilldownMatrix.DETAIL_WANTED_INGREDIENT_COST_BREAKDOWN;
            default -> null;
        };
    }

    /**
     * Phase 1 应用副作用摘要（解析层使用）。
     */
    public record Phase1PurchaseApplyResult(
            Map<String, Object> capabilityDebug,
            boolean appliedIntentMutation,
            String proposedFollowUpAction,
            String proposedFollowUpDetailWanted,
            String proposedFollowUpSourcePlanType,
            BusinessCapabilityMatch capabilityMatch,
            String proposedFollowUpTargetEntityType,
            String proposedFollowUpTargetEntityName,
            String proposedFollowUpTargetEntityId) {}
}
