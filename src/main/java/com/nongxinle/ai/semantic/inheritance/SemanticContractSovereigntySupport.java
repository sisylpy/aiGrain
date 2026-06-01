package com.nongxinle.ai.semantic.inheritance;

import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.SemanticParserAllowedOutputContract;
import com.nongxinle.ai.semantic.contract.DomainContractSelectionResult;
import com.nongxinle.ai.semantic.contract.SemanticCapabilityContract;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * 当前轮合同主权：ACTIVE + allowed 仅为必要条件；time-only 同 family 弱选不算 sovereign。
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
     * 当前轮是否对业务 semanticSlots 拥有主权（previousTurn 不得覆盖业务 frame）。
     */
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
                false);
    }

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
            boolean sameCapabilityNamedEntityFollowUp) {
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
                sameCapabilityNamedEntityFollowUp,
                false);
    }

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
            boolean goodsAnchorSameEntityFollowUp) {
        if (!isCatalogValidActiveContract(currentContractId, domainHint, selection)) {
            return false;
        }
        if (goodsAnchorSameEntityFollowUp) {
            return false;
        }
        if (explicitEntityFollowUp && !sameCapabilityNamedEntityFollowUp) {
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
        if (StringUtils.hasText(previousContractId)
                && StringUtils.hasText(currentContractId)
                && !currentContractId.equals(previousContractId)) {
            return false;
        }
        return true;
    }

    private static boolean allowedContractsContain(
            DomainContractSelectionResult selection, String contractId) {
        if (selection == null || selection.getParserAllowedOutputContract() == null) {
            return true;
        }
        List<SemanticParserAllowedOutputContract.AllowedContractEntry> entries =
                selection.getParserAllowedOutputContract().getAllowedContracts();
        if (entries == null || entries.isEmpty()) {
            return true;
        }
        String normalized = contractId.trim();
        for (SemanticParserAllowedOutputContract.AllowedContractEntry entry : entries) {
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
