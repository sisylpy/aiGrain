package com.nongxinle.ai.semantic.inheritance;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.AiQuerySemanticSlotMerge;
import com.nongxinle.ai.semantic.contract.SemanticCapabilityContract;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按 {@link SemanticSlotInheritanceDecision} 受控写入 parse；禁止整包 {@code semanticSlots} 替换。
 *
 * <p><b>硬边界（见 {@code docs/ai/semantic-inheritance-architecture.md}）：</b>
 * <ul>
 *   <li>本类 <b>不得</b>成为业务 if/else 补丁中心；禁止 per-domain reconcile 分支。</li>
 *   <li>当前轮 sovereign ACTIVE contract 时，<b>不得</b>用 previousTurn 覆盖任何业务语义字段。</li>
 *   <li>{@link SemanticSlotInheritanceMode#INHERIT_SAME_FAMILY_TIME_FOLLOWUP}：previousTurn 只提供
 *       {@code previousContractId}；完整 frame 经 {@link CanonicalContractFrameSupport} 从 ACTIVE catalog entry 派生，
 *       <b>禁止</b>从 {@code lastSemanticSlots} 或 current raw 做字段级 copy / coalesce。</li>
 *   <li>保留当前轮 {@code time} / {@code requestedScope}；time-only follow-up 清空 {@code mentionedDishName}
 *       （resultAnchor 不得自动升级为主语）。</li>
 *   <li>禁止 {@code contains} / alias 猜业务语义。</li>
 * </ul>
 */
public final class SemanticSlotInheritanceApplier {

    private SemanticSlotInheritanceApplier() {}

    public static AiQuerySemanticParseResult apply(
            AiQuerySemanticParseResult current,
            AiConversationTurnMemory previousTurn,
            SemanticSlotInheritanceDecision decision) {
        if (current == null || current.isParseMissing() || decision == null) {
            return current;
        }
        return switch (decision.getMode()) {
            case INHERIT_NONE -> attachTrace(current, decision);
            case INHERIT_CONTEXT_ONLY -> applyContextOnly(current, previousTurn, decision);
            case INHERIT_SAME_FAMILY_TIME_FOLLOWUP ->
                    applySameFamilyTimeFollowUp(current, previousTurn, decision);
            case INHERIT_BARE_RANKING_DIMENSION_SWITCH ->
                    applyBareRankingDimensionSwitch(current, previousTurn, decision);
            case INHERIT_SAME_CAPABILITY_NAMED_ENTITY ->
                    applySameCapabilityNamedEntity(current, previousTurn, decision);
            case INHERIT_SAME_GOODS_ANCHOR_FOLLOWUP ->
                    applySameGoodsAnchorFollowUp(current, previousTurn, decision);
            case INHERIT_SAME_CAPABILITY_TIME_FOLLOWUP, INHERIT_COVER_DAYS_SALES_BASELINE_FOLLOWUP ->
                    applySameCapabilityTimeFollowUp(current, previousTurn, decision);
        };
    }

    private static AiQuerySemanticParseResult applySameGoodsAnchorFollowUp(
            AiQuerySemanticParseResult current,
            AiConversationTurnMemory previousTurn,
            SemanticSlotInheritanceDecision decision) {
        String previousContractId = resolvePreviousContractId(decision, previousTurn);
        if (!StringUtils.hasText(previousContractId)) {
            return attachTrace(current, decision);
        }
        String domainHint = resolveDomainHint(decision, previousContractId);
        SemanticCapabilityContract contract =
                SemanticContractFamilySupport.lookupActiveContract(previousContractId, domainHint);
        if (contract == null) {
            return attachTrace(current, decision);
        }
        CanonicalContractFrameSupport.CanonicalBusinessFrame frame =
                CanonicalContractFrameSupport.fromActiveContract(
                        contract, AiQuerySemanticSlotMerge.ANCHOR_USE_PREVIOUS);
        if (frame == null) {
            return attachTrace(current, decision);
        }
        AiQuerySemanticParseResult merged =
                CanonicalContractFrameSupport.applyBusinessFrameWhitelist(current, frame);
        String previousGoods = GoodsAnchorSameEntityFollowUpSupport.previousGoodsName(previousTurn);
        if (StringUtils.hasText(previousGoods)) {
            AiQuerySemanticParseResult.SemanticSlotsPart slots = merged.getSemanticSlots();
            AiQuerySemanticParseResult.SemanticSlotsPart withGoods =
                    slots != null
                            ? copySlotsWithGoods(slots, previousGoods)
                            : AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                    .mentionedGoodsName(previousGoods)
                                    .anchorPolicy(AiQuerySemanticSlotMerge.ANCHOR_USE_PREVIOUS)
                                    .build();
            merged =
                    merged.toBuilder()
                            .mentionedGoodsName(previousGoods)
                            .semanticSlots(withGoods)
                            .build();
        }
        return attachTrace(merged, decision);
    }

    private static AiQuerySemanticParseResult.SemanticSlotsPart copySlotsWithGoods(
            AiQuerySemanticParseResult.SemanticSlotsPart slots, String goodsName) {
        return AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                .selectedContractId(slots.getSelectedContractId())
                .queryObject(slots.getQueryObject())
                .operation(slots.getOperation())
                .metric(slots.getMetric())
                .sourceFacet(slots.getSourceFacet())
                .anchorPolicy(AiQuerySemanticSlotMerge.ANCHOR_USE_PREVIOUS)
                .detailWanted(slots.getDetailWanted())
                .structuredIntentDetailWire(slots.getStructuredIntentDetailWire())
                .answerPlanType(slots.getAnswerPlanType())
                .mentionedGoodsName(goodsName)
                .mentionedDishName(slots.getMentionedDishName())
                .requestedTargetGrossMarginRate(slots.getRequestedTargetGrossMarginRate())
                .expiryRiskFilter(slots.getExpiryRiskFilter())
                .build();
    }

    private static AiQuerySemanticParseResult applySameCapabilityTimeFollowUp(
            AiQuerySemanticParseResult current,
            AiConversationTurnMemory previousTurn,
            SemanticSlotInheritanceDecision decision) {
        String previousContractId = resolvePreviousContractId(decision, previousTurn);
        if (!StringUtils.hasText(previousContractId)) {
            return attachTrace(current, decision);
        }
        String domainHint = resolveDomainHint(decision, previousContractId);
        SemanticCapabilityContract contract =
                SemanticContractFamilySupport.lookupActiveContract(previousContractId, domainHint);
        if (contract == null) {
            return attachTrace(current, decision);
        }
        CanonicalContractFrameSupport.CanonicalBusinessFrame frame =
                CanonicalContractFrameSupport.fromActiveContract(
                        contract, AiQuerySemanticSlotMerge.ANCHOR_USE_PREVIOUS);
        if (frame == null) {
            return attachTrace(current, decision);
        }
        AiQuerySemanticParseResult merged =
                CanonicalContractFrameSupport.applyBusinessFrameWhitelist(current, frame);
        if (CoverDaysSalesBaselineFollowUpSupport.previousTurnWasGoodsCover(previousTurn)) {
            String previousGoods = CoverDaysSalesBaselineFollowUpSupport.previousGoodsName(previousTurn);
            if (StringUtils.hasText(previousGoods)) {
                AiQuerySemanticParseResult.SemanticSlotsPart slots = merged.getSemanticSlots();
                AiQuerySemanticParseResult.SemanticSlotsPart withGoods =
                        slots != null
                                ? copySlotsWithGoods(slots, previousGoods)
                                : AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                        .mentionedGoodsName(previousGoods)
                                        .anchorPolicy(AiQuerySemanticSlotMerge.ANCHOR_USE_PREVIOUS)
                                        .build();
                merged =
                        merged.toBuilder()
                                .mentionedGoodsName(previousGoods)
                                .semanticSlots(withGoods)
                                .build();
            }
        } else {
            String previousDish = CoverDaysSalesBaselineFollowUpSupport.previousDishName(previousTurn);
            if (StringUtils.hasText(previousDish)) {
                AiQuerySemanticParseResult.SemanticSlotsPart slots = merged.getSemanticSlots();
                AiQuerySemanticParseResult.SemanticSlotsPart withDish =
                        slots != null
                                ? copySlotsWithCoverDaysDish(slots, previousDish)
                                : AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                        .mentionedDishName(previousDish)
                                        .anchorPolicy(AiQuerySemanticSlotMerge.ANCHOR_USE_PREVIOUS)
                                        .build();
                merged =
                        merged.toBuilder()
                                .mentionedDishName(previousDish)
                                .semanticSlots(withDish)
                                .build();
            }
        }
        return attachTrace(merged, decision);
    }

    private static AiQuerySemanticParseResult.SemanticSlotsPart copySlotsWithCoverDaysDish(
            AiQuerySemanticParseResult.SemanticSlotsPart slots, String dishName) {
        return AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                .selectedContractId(slots.getSelectedContractId())
                .queryObject(slots.getQueryObject())
                .operation(slots.getOperation())
                .metric(slots.getMetric())
                .sourceFacet(slots.getSourceFacet())
                .anchorPolicy(AiQuerySemanticSlotMerge.ANCHOR_USE_PREVIOUS)
                .detailWanted(slots.getDetailWanted())
                .structuredIntentDetailWire(slots.getStructuredIntentDetailWire())
                .answerPlanType(slots.getAnswerPlanType())
                .mentionedDishName(dishName)
                .mentionedGoodsName(slots.getMentionedGoodsName())
                .requestedTargetGrossMarginRate(slots.getRequestedTargetGrossMarginRate())
                .expiryRiskFilter(slots.getExpiryRiskFilter())
                .build();
    }

    private static AiQuerySemanticParseResult applySameCapabilityNamedEntity(
            AiQuerySemanticParseResult current,
            AiConversationTurnMemory previousTurn,
            SemanticSlotInheritanceDecision decision) {
        String previousContractId = resolvePreviousContractId(decision, previousTurn);
        if (!StringUtils.hasText(previousContractId)) {
            return attachTrace(current, decision);
        }
        String domainHint = resolveDomainHint(decision, previousContractId);
        SemanticCapabilityContract contract =
                SemanticContractFamilySupport.lookupActiveContract(previousContractId, domainHint);
        if (contract == null) {
            return attachTrace(current, decision);
        }
        String currentDish = current.effectiveMentionedDishName();
        CanonicalContractFrameSupport.CanonicalBusinessFrame frame =
                CanonicalContractFrameSupport.fromActiveContract(
                        contract, AiQuerySemanticSlotMerge.ANCHOR_IGNORE_PREVIOUS);
        if (frame == null) {
            return attachTrace(current, decision);
        }
        AiQuerySemanticParseResult merged =
                CanonicalContractFrameSupport.applyBusinessFrameWhitelist(current, frame);
        if (StringUtils.hasText(currentDish)) {
            AiQuerySemanticParseResult.SemanticSlotsPart slots = merged.getSemanticSlots();
            AiQuerySemanticParseResult.SemanticSlotsPart withDish =
                    slots != null
                            ? copySlotsWithDish(slots, currentDish)
                            : AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                    .mentionedDishName(currentDish)
                                    .anchorPolicy(AiQuerySemanticSlotMerge.ANCHOR_IGNORE_PREVIOUS)
                                    .build();
            merged =
                    merged.toBuilder()
                            .mentionedDishName(currentDish)
                            .semanticSlots(withDish)
                            .build();
        }
        return attachTrace(merged, decision);
    }

    private static AiQuerySemanticParseResult.SemanticSlotsPart copySlotsWithDish(
            AiQuerySemanticParseResult.SemanticSlotsPart slots, String dishName) {
        return AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                .selectedContractId(slots.getSelectedContractId())
                .queryObject(slots.getQueryObject())
                .operation(slots.getOperation())
                .metric(slots.getMetric())
                .sourceFacet(slots.getSourceFacet())
                .anchorPolicy(AiQuerySemanticSlotMerge.ANCHOR_IGNORE_PREVIOUS)
                .detailWanted(slots.getDetailWanted())
                .structuredIntentDetailWire(slots.getStructuredIntentDetailWire())
                .answerPlanType(slots.getAnswerPlanType())
                .mentionedDishName(dishName)
                .requestedTargetGrossMarginRate(slots.getRequestedTargetGrossMarginRate())
                .expiryRiskFilter(slots.getExpiryRiskFilter())
                .build();
    }

    private static AiQuerySemanticParseResult applyBareRankingDimensionSwitch(
            AiQuerySemanticParseResult current,
            AiConversationTurnMemory previousTurn,
            SemanticSlotInheritanceDecision decision) {
        AiQuerySemanticParseResult scoped = applyContextOnly(current, previousTurn, decision);
        String targetContractId = decision.getTargetContractId();
        if (!StringUtils.hasText(targetContractId)) {
            return attachTrace(scoped, decision);
        }

        String domainHint = resolveDomainHint(decision, targetContractId);
        SemanticCapabilityContract contract =
                SemanticContractFamilySupport.lookupActiveContract(targetContractId, domainHint);
        if (contract == null) {
            return attachTrace(scoped, decision);
        }

        CanonicalContractFrameSupport.CanonicalBusinessFrame frame =
                CanonicalContractFrameSupport.fromActiveContract(
                        contract, AiQuerySemanticSlotMerge.ANCHOR_IGNORE_PREVIOUS);
        if (frame == null) {
            return attachTrace(scoped, decision);
        }

        AiQuerySemanticParseResult merged =
                CanonicalContractFrameSupport.applyBusinessFrameWhitelist(scoped, frame);
        merged = merged.toBuilder().mentionedDishName(null).build();
        return attachTrace(merged, decision);
    }

    private static AiQuerySemanticParseResult applyContextOnly(
            AiQuerySemanticParseResult current,
            AiConversationTurnMemory previousTurn,
            SemanticSlotInheritanceDecision decision) {
        AiQuerySemanticParseResult working = current;
        if (decision.isSuppressPreviousDishAnchor()) {
            working = clearDishAnchor(working);
        }
        if (previousTurn == null) {
            return attachTrace(working, decision);
        }
        AiQuerySemanticParseResult.RequestedScopePart mergedScope =
                mergeScopeContext(working.getRequestedScope(), previousTurn);
        if (mergedScope == working.getRequestedScope()) {
            return attachTrace(working, decision);
        }
        return attachTrace(working.toBuilder().requestedScope(mergedScope).build(), decision);
    }

    private static AiQuerySemanticParseResult clearDishAnchor(AiQuerySemanticParseResult current) {
        if (current == null) {
            return null;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart slots = current.getSemanticSlots();
        AiQuerySemanticParseResult.SemanticSlotsPart cleared =
                slots != null
                        ? AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                .selectedContractId(slots.getSelectedContractId())
                                .queryObject(slots.getQueryObject())
                                .operation(slots.getOperation())
                                .metric(slots.getMetric())
                                .sourceFacet(slots.getSourceFacet())
                                .anchorPolicy(AiQuerySemanticSlotMerge.ANCHOR_IGNORE_PREVIOUS)
                                .detailWanted(slots.getDetailWanted())
                                .structuredIntentDetailWire(slots.getStructuredIntentDetailWire())
                                .answerPlanType(slots.getAnswerPlanType())
                                .requestedTargetGrossMarginRate(slots.getRequestedTargetGrossMarginRate())
                                .expiryRiskFilter(slots.getExpiryRiskFilter())
                                .build()
                        : null;
        return current.toBuilder().mentionedDishName(null).semanticSlots(cleared).build();
    }

    private static AiQuerySemanticParseResult applySameFamilyTimeFollowUp(
            AiQuerySemanticParseResult current,
            AiConversationTurnMemory previousTurn,
            SemanticSlotInheritanceDecision decision) {
        if (decision.isCurrentHasSovereignActiveContract()) {
            return attachTrace(current, decision);
        }
        String previousContractId = resolvePreviousContractId(decision, previousTurn);
        if (!StringUtils.hasText(previousContractId)) {
            return attachTrace(current, decision);
        }

        String domainHint = resolveDomainHint(decision, previousContractId);
        SemanticCapabilityContract contract =
                SemanticContractFamilySupport.lookupActiveContract(previousContractId, domainHint);
        if (contract == null) {
            return attachTrace(current, decision);
        }

        String anchorPolicy =
                decision.isSuppressPreviousDishAnchor()
                        ? AiQuerySemanticSlotMerge.ANCHOR_IGNORE_PREVIOUS
                        : null;
        CanonicalContractFrameSupport.CanonicalBusinessFrame frame =
                CanonicalContractFrameSupport.fromActiveContract(contract, anchorPolicy);
        if (frame == null) {
            return attachTrace(current, decision);
        }

        AiQuerySemanticParseResult merged =
                CanonicalContractFrameSupport.applyBusinessFrameWhitelist(current, frame);
        return attachTrace(merged, decision);
    }

    private static String resolvePreviousContractId(
            SemanticSlotInheritanceDecision decision, AiConversationTurnMemory previousTurn) {
        if (decision != null && StringUtils.hasText(decision.getPreviousContractId())) {
            return decision.getPreviousContractId().trim();
        }
        return SemanticContractFamilySupport.contractIdFromPreviousTurn(previousTurn);
    }

    private static String resolveDomainHint(
            SemanticSlotInheritanceDecision decision, String previousContractId) {
        if (decision != null && StringUtils.hasText(decision.getCurrentDomain())) {
            return decision.getCurrentDomain().trim();
        }
        SemanticCapabilityContract contract =
                SemanticContractFamilySupport.lookupActiveContract(previousContractId, null);
        if (contract != null && StringUtils.hasText(contract.getDomain())) {
            return contract.getDomain().trim();
        }
        return null;
    }

    private static AiQuerySemanticParseResult.RequestedScopePart mergeScopeContext(
            AiQuerySemanticParseResult.RequestedScopePart current,
            AiConversationTurnMemory previousTurn) {
        if (previousTurn == null) {
            return current;
        }
        var b = AiQuerySemanticParseResult.RequestedScopePart.builder();
        if (current != null) {
            b.requestedScopeType(current.getRequestedScopeType())
                    .mentionedStoreName(current.getMentionedStoreName())
                    .mentionedStoreNames(current.getMentionedStoreNames())
                    .mentionedDepartmentName(current.getMentionedDepartmentName())
                    .mentionedWarehouseName(current.getMentionedWarehouseName())
                    .scopeSource(current.getScopeSource())
                    .needInheritFromPrevious(current.getNeedInheritFromPrevious());
        }
        boolean changed = false;
        if (current == null || !StringUtils.hasText(current.getRequestedScopeType())) {
            if (StringUtils.hasText(previousTurn.getLastScopeType())) {
                b.requestedScopeType(previousTurn.getLastScopeType());
                changed = true;
            }
        }
        if (current == null || !StringUtils.hasText(current.getMentionedStoreName())) {
            if (StringUtils.hasText(previousTurn.getLastMentionedStore())) {
                b.mentionedStoreName(previousTurn.getLastMentionedStore());
                changed = true;
            }
        }
        return changed ? b.build() : current;
    }

    private static AiQuerySemanticParseResult attachTrace(
            AiQuerySemanticParseResult current, SemanticSlotInheritanceDecision decision) {
        Map<String, Object> trace = new LinkedHashMap<>();
        if (current.getMultiTurnInheritanceTrace() != null) {
            trace.putAll(current.getMultiTurnInheritanceTrace());
        }
        if (decision.getTrace() != null) {
            trace.putAll(decision.getTrace());
        }
        trace.put("currentHasSovereignActiveContract", decision.isCurrentHasSovereignActiveContract());
        trace.put("suppressPreviousDishAnchor", decision.isSuppressPreviousDishAnchor());
        return current.toBuilder().multiTurnInheritanceTrace(trace).build();
    }

    /** 供 anchor reconcile 读取：是否禁止从 previous 拉菜名。 */
    public static boolean suppressPreviousDishAnchor(AiQuerySemanticParseResult parse) {
        if (parse == null || parse.getMultiTurnInheritanceTrace() == null) {
            return false;
        }
        Object flag = parse.getMultiTurnInheritanceTrace().get("suppressPreviousDishAnchor");
        return Boolean.TRUE.equals(flag);
    }
}
