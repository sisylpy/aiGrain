package com.nongxinle.ai.resolver;

import com.nongxinle.ai.semantic.contract.DomainContractSelectionResult;
import com.nongxinle.ai.semantic.intake.SemanticIntakeResult;
import com.nongxinle.ai.semantic.intake.route.SemanticDomainRouteResult;
import com.nongxinle.ai.semantic.intake.route.SemanticDomainRouteType;
import org.springframework.util.StringUtils;

/**
 * Adoption 后 effective route / selection 与 Intake 初判 route 对齐；Grounding 或 plan 切域时 derive explicit route。
 */
public final class SemanticAdoptionEffectiveSupport {

    /** Harness / debug：effective route 由实体落地或 plan 切域派生。 */
    public static final String REASON_EFFECTIVE_DOMAIN_SWITCH = "effective_domain_switch";

    private SemanticAdoptionEffectiveSupport() {}

    public static SemanticDomainRouteResult routeForDomainSwitch(
            SemanticDomainRouteResult intakeRoute,
            DomainContractSelectionResult effectiveSelection,
            SemanticIntakeResult intake) {
        if (intakeRoute == null || effectiveSelection == null) {
            return intakeRoute;
        }
        String effectiveDomain = blank(effectiveSelection.getSelectedDomain());
        String intakeDomain = blank(intakeRoute.getPrimaryDomain());
        if (!StringUtils.hasText(effectiveDomain) || effectiveDomain.equalsIgnoreCase(intakeDomain)) {
            return intakeRoute;
        }
        return SemanticDomainRouteResult.builder()
                .routeType(SemanticDomainRouteType.EXPLICIT)
                .primaryDomain(effectiveDomain)
                .candidateDomain(effectiveDomain)
                .reasonCode(REASON_EFFECTIVE_DOMAIN_SWITCH)
                .usedPreviousContext(
                        intake != null && Boolean.TRUE.equals(intake.getUsedPreviousContext()))
                .needsClarification(false)
                .build();
    }

    private static String blank(String s) {
        return s == null ? "" : s.trim();
    }
}
