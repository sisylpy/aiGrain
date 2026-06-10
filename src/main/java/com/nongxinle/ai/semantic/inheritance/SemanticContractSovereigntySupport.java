package com.nongxinle.ai.semantic.inheritance;

import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.contract.DomainContractSelectionResult;
import com.nongxinle.ai.semantic.contract.SemanticCapabilityContract;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 当前轮合同主权：须先经 {@link SemanticContractTransitionPolicy} 判定合法 transition；
 * 已注册 transition 时 V2 弱选不得夺取 Business Frame 主权。
 */
public final class SemanticContractSovereigntySupport {

    private SemanticContractSovereigntySupport() {}

    public static boolean isCatalogValidActiveContract(
            String contractId, String domainHint, DomainContractSelectionResult selection) {
        if (!StringUtils.hasText(contractId)) {
            return false;
        }
        SemanticCapabilityContract contract =
                SemanticContractFamilySupport.lookupActiveContract(contractId, domainHint);
        if (contract == null) {
            return false;
        }
        return allowedContractsContain(selection, contractId.trim());
    }

    /**
     * @deprecated 请使用 {@link #hasSovereignActiveContract(SemanticSlotInheritanceRequest)} 或传入
     *     {@link SemanticContractTransitionDecision}。
     */
    @Deprecated
    public static boolean hasSovereignActiveContract(
            AiQuerySemanticParseResult current,
            String currentContractId,
            String previousContractId,
            String currentFamily,
            String previousFamily,
            String domainHint,
            DomainContractSelectionResult selection,
            boolean structuredTimeFollowUp,
            boolean explicitEntityFollowUp) {
        return hasSovereignActiveContract(
                current,
                currentContractId,
                previousContractId,
                currentFamily,
                previousFamily,
                domainHint,
                selection,
                structuredTimeFollowUp,
                explicitEntityFollowUp,
                false,
                false,
                false);
    }

    /**
     * @deprecated 请使用 {@link #hasSovereignActiveContract(SemanticContractTransitionDecision)}。
     */
    @Deprecated
    public static boolean hasSovereignActiveContract(
            AiQuerySemanticParseResult current,
            String currentContractId,
            String previousContractId,
            String currentFamily,
            String previousFamily,
            String domainHint,
            DomainContractSelectionResult selection,
            boolean structuredTimeFollowUp,
            boolean explicitEntityFollowUp,
            boolean sameCapabilityNamedEntityFollowUp,
            boolean goodsAnchorSameEntityFollowUp,
            boolean coverDaysSalesBaselineFollowUp) {
        if (goodsAnchorSameEntityFollowUp || coverDaysSalesBaselineFollowUp) {
            return false;
        }
        if (sameCapabilityNamedEntityFollowUp) {
            return false;
        }
        SemanticContractTransitionDecision synthetic =
                coverDaysSalesBaselineFollowUp
                        ? SemanticContractTransitionDecision.builder()
                                .transitionType(
                                        SemanticContractTransitionType.SAME_CAPABILITY_TIME_OVERRIDE)
                                .build()
                        : null;
        if (synthetic != null && synthetic.suppressesV2BusinessFrameSovereignty()) {
            return false;
        }
        return legacySovereignWithoutTransition(
                current,
                currentContractId,
                previousContractId,
                currentFamily,
                previousFamily,
                domainHint,
                selection,
                structuredTimeFollowUp,
                explicitEntityFollowUp);
    }

    public static boolean hasSovereignActiveContract(SemanticSlotInheritanceRequest request) {
        SemanticContractTransitionDecision transition =
                SemanticSlotInheritancePolicy.resolveTransition(request);
        return hasSovereignActiveContract(transition);
    }

    public static boolean hasSovereignActiveContract(SemanticContractTransitionDecision transition) {
        if (transition == null) {
            return false;
        }
        if (transition.suppressesV2BusinessFrameSovereignty()) {
            return false;
        }
        return transition.currentTurnHasSovereignBusinessFrame();
    }

    private static boolean legacySovereignWithoutTransition(
            AiQuerySemanticParseResult current,
            String currentContractId,
            String previousContractId,
            String currentFamily,
            String previousFamily,
            String domainHint,
            DomainContractSelectionResult selection,
            boolean structuredTimeFollowUp,
            boolean explicitEntityFollowUp) {
        if (!isCatalogValidActiveContract(currentContractId, domainHint, selection)) {
            return false;
        }
        if (explicitEntityFollowUp) {
            return true;
        }
        if (SemanticContractFamilySupport.crossFamily(currentFamily, previousFamily)) {
            return true;
        }
        if (!structuredTimeFollowUp) {
            return true;
        }
        if (!SemanticContractFamilySupport.sameFamily(currentFamily, previousFamily)) {
            return true;
        }
        SemanticCapabilityContract currentContract =
                SemanticContractFamilySupport.lookupActiveContract(currentContractId, domainHint);
        if (currentContract != null
                && currentContract.isRequiresAnchor()
                && !StringUtils.hasText(current.effectiveMentionedDishName())) {
            return false;
        }
        return false;
    }

    private static boolean allowedContractsContain(
            DomainContractSelectionResult selection, String contractId) {
        if (selection == null || selection.getParserAllowedOutputContract() == null) {
            return true;
        }
        List<com.nongxinle.ai.semantic.SemanticParserAllowedOutputContract.AllowedContractEntry>
                entries = selection.getParserAllowedOutputContract().getAllowedContracts();
        if (entries == null || entries.isEmpty()) {
            return true;
        }
        String normalized = contractId.trim();
        for (var entry : entries) {
            if (entry != null
                    && StringUtils.hasText(entry.getContractId())
                    && normalized.equals(entry.getContractId().trim())) {
                return "ACTIVE".equalsIgnoreCase(blank(entry.getCapabilityStatus()))
                        || !StringUtils.hasText(entry.getCapabilityStatus());
            }
        }
        return false;
    }

    private static String blank(String s) {
        return s == null ? "" : s.trim();
    }
}
