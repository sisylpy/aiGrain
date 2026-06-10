package com.nongxinle.ai.semantic.inheritance;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.contract.DomainContractSelectionResult;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * V2 raw 之后、contract completion 之前的 previousTurn 继承决策。
 * 合同迁移由 {@link SemanticContractTransitionPolicy} 统一判定；本类仅映射为 {@link SemanticSlotInheritanceDecision}。
 */
public final class SemanticSlotInheritancePolicy {

    public static final String REASON_NO_PREVIOUS_TURN = "NO_PREVIOUS_TURN";
    public static final String REASON_EXPLICIT_ENTITY_FOLLOWUP = "EXPLICIT_ENTITY_FOLLOWUP";
    public static final String REASON_CURRENT_TURN_SOVEREIGN = "CURRENT_TURN_SOVEREIGN";
    public static final String REASON_CROSS_FAMILY_SOVEREIGN = "CROSS_FAMILY_SOVEREIGN";
    public static final String REASON_SAME_FAMILY_TIME_ONLY_FOLLOWUP = "SAME_FAMILY_TIME_ONLY_FOLLOWUP";
    public static final String REASON_NOT_TIME_FOLLOWUP = "NOT_TIME_FOLLOWUP";
    public static final String REASON_BARE_RANKING_DIMENSION_SWITCH = "BARE_RANKING_DIMENSION_SWITCH";
    public static final String REASON_SAME_CAPABILITY_NAMED_ENTITY = "SAME_CAPABILITY_NAMED_ENTITY";
    public static final String REASON_SAME_GOODS_ANCHOR_FOLLOWUP = "SAME_GOODS_ANCHOR_FOLLOWUP";
    public static final String REASON_COVER_DAYS_SALES_BASELINE_FOLLOWUP =
            "COVER_DAYS_SALES_BASELINE_FOLLOWUP";
    public static final String REASON_NO_PREVIOUS_FRAME = "NO_PREVIOUS_FRAME";

    private SemanticSlotInheritancePolicy() {}

    public static SemanticSlotInheritanceDecision decide(SemanticSlotInheritanceRequest request) {
        if (request == null || request.getCurrentParse() == null) {
            return decision(SemanticSlotInheritanceMode.INHERIT_NONE, REASON_NO_PREVIOUS_TURN, request);
        }

        SemanticContractTransitionDecision transition =
                SemanticContractTransitionPolicy.decide(request);
        return mapTransitionToInheritanceDecision(request, transition);
    }

    /** 供 {@link SemanticContractSovereigntySupport} 与 Harness 读取 transition 后再映射 sovereignty。 */
    public static SemanticContractTransitionDecision resolveTransition(
            SemanticSlotInheritanceRequest request) {
        if (request == null || request.getCurrentParse() == null) {
            return null;
        }
        return SemanticContractTransitionPolicy.decide(request);
    }

    private static SemanticSlotInheritanceDecision mapTransitionToInheritanceDecision(
            SemanticSlotInheritanceRequest request, SemanticContractTransitionDecision transition) {
        AiQuerySemanticParseResult current = request.getCurrentParse();
        AiConversationTurnMemory previous = request.getPreviousTurn();
        DomainContractSelectionResult selection = request.getContractSelection();

        String domainHint = SemanticContractFamilySupport.resolveSelectedDomain(selection, current);
        String currentContractId = SemanticContractFamilySupport.contractIdFromParse(current);
        String previousContractId = SemanticContractFamilySupport.contractIdFromPreviousTurn(previous);
        String currentFamily = SemanticContractFamilySupport.resolveFamily(currentContractId);
        String previousFamily = SemanticContractFamilySupport.resolveFamily(previousContractId);

        boolean structuredTimeFollowUp =
                StructuredTimeFollowUpSupport.isStructuredTimeOnlyFollowUp(current);
        boolean explicitEntityFollowUp =
                ExplicitEntityFollowUpSupport.isExplicitEntityFollowUp(
                        current, previous, domainHint);
        boolean crossFamily =
                SemanticContractFamilySupport.crossFamily(currentFamily, previousFamily);
        boolean sovereign = transition != null && transition.currentTurnHasSovereignBusinessFrame();

        String effectivePrevious =
                StringUtils.hasText(transition.getEffectiveContractId())
                        ? transition.getEffectiveContractId()
                        : previousContractId;
        String reasonCode =
                transition != null && StringUtils.hasText(transition.getReasonCode())
                        ? transition.getReasonCode()
                        : REASON_NOT_TIME_FOLLOWUP;
        SemanticSlotInheritanceMode mode =
                transition != null && transition.getInheritanceMode() != null
                        ? transition.getInheritanceMode()
                        : SemanticSlotInheritanceMode.INHERIT_NONE;

        Map<String, Object> trace = new LinkedHashMap<>();
        if (transition != null && transition.getTrace() != null) {
            trace.putAll(transition.getTrace());
        }
        if (transition != null) {
            trace.put("transitionType", transition.getTransitionType().name());
            trace.put("effectiveContractId", transition.getEffectiveContractId());
            trace.put("preservedFields", transition.getPreservedFields());
            trace.put("overriddenFields", transition.getOverriddenFields());
        }
        trace.put("inheritanceMode", mode.name());
        trace.put("reasonCode", reasonCode);

        return SemanticSlotInheritanceDecision.builder()
                .mode(mode)
                .reasonCode(reasonCode)
                .reasonDetail(reasonCode)
                .currentContractId(currentContractId)
                .previousContractId(effectivePrevious)
                .currentFamily(currentFamily)
                .previousFamily(previousFamily)
                .currentDomain(domainHint)
                .currentHasSovereignActiveContract(sovereign)
                .structuredTimeFollowUp(structuredTimeFollowUp)
                .crossFamily(crossFamily)
                .explicitEntityFollowUp(explicitEntityFollowUp)
                .suppressPreviousDishAnchor(
                        transition != null && transition.isSuppressPreviousDishAnchor())
                .targetContractId(transition != null ? transition.getTargetContractId() : null)
                .trace(trace)
                .build();
    }

    private static SemanticSlotInheritanceDecision decision(
            SemanticSlotInheritanceMode mode, String reasonCode, SemanticSlotInheritanceRequest request) {
        return SemanticSlotInheritanceDecision.builder()
                .mode(mode)
                .reasonCode(reasonCode)
                .reasonDetail(reasonCode)
                .currentHasSovereignActiveContract(false)
                .trace(Map.of("reasonCode", reasonCode))
                .build();
    }
}
