package com.nongxinle.ai.semantic.contract;

import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

/**
 * 合同 {@code sourceFacet} 校验：单值默认 + {@code allowedSourceFacets} 多值覆盖（如 goods_business_analysis）。
 */
public final class ContractSourceFacetSupport {

    private ContractSourceFacetSupport() {}

    public static boolean frameSourceFacetAllowed(SemanticCapabilityContract contract, String frameFacet) {
        if (contract == null || !StringUtils.hasText(frameFacet)) {
            return true;
        }
        Set<String> allowed = contract.getAllowedSourceFacets();
        if (allowed != null && !allowed.isEmpty()) {
            return tokenInSet(normalizeToken(frameFacet), allowed);
        }
        if (!StringUtils.hasText(contract.getSourceFacet())) {
            return true;
        }
        return normalizeToken(contract.getSourceFacet()).equals(normalizeToken(frameFacet));
    }

    public static boolean slotSourceFacetMatches(SemanticCapabilityContract contract, String slotFacet) {
        if (contract == null) {
            return true;
        }
        Set<String> allowed = contract.getAllowedSourceFacets();
        if (allowed != null && !allowed.isEmpty()) {
            if (!StringUtils.hasText(slotFacet)) {
                return false;
            }
            return tokenInSet(normalizeToken(slotFacet), allowed);
        }
        if (!StringUtils.hasText(contract.getSourceFacet())) {
            return true;
        }
        if (!StringUtils.hasText(slotFacet)) {
            return false;
        }
        return normalizeToken(contract.getSourceFacet()).equals(normalizeToken(slotFacet));
    }

    public static boolean requiresExplicitSourceFacet(SemanticCapabilityContract contract) {
        if (contract == null) {
            return false;
        }
        Set<String> allowed = contract.getAllowedSourceFacets();
        if (allowed != null && !allowed.isEmpty()) {
            return false;
        }
        return StringUtils.hasText(contract.getSourceFacet());
    }

    private static boolean tokenInSet(String token, Set<String> allowed) {
        if (!StringUtils.hasText(token) || allowed == null || allowed.isEmpty()) {
            return false;
        }
        for (String allowedToken : allowed) {
            if (token.equals(normalizeToken(allowedToken))) {
                return true;
            }
        }
        return false;
    }

    static String normalizeToken(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }
}
