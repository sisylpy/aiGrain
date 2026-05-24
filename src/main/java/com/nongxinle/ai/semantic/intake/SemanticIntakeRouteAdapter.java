package com.nongxinle.ai.semantic.intake;

import com.nongxinle.ai.semantic.intake.route.SemanticDomainRouteResult;
import com.nongxinle.ai.semantic.intake.route.SemanticDomainRouteType;
import org.springframework.util.StringUtils;

/**
 * 将 SemanticIntake LLM 输出适配为 {@link SemanticDomainRouteResult}，供 {@link
 * com.nongxinle.ai.semantic.contract.DomainContractSelector} 使用。不做 domain 修正。
 */
public final class SemanticIntakeRouteAdapter {

    private SemanticIntakeRouteAdapter() {}

    public static SemanticDomainRouteResult toRouteResult(SemanticIntakeResult intake) {
        if (intake == null || intake.getStatus() != SemanticIntakeStatus.READY) {
            return null;
        }
        if (intake.getQuestionMode() == SemanticIntakeQuestionMode.MULTI_QUESTION) {
            return null;
        }
        String primary = SemanticIntakePrimaryDomain.normalize(intake.getPrimaryDomain());
        if (!SemanticIntakePrimaryDomain.isExecutable(primary)) {
            return null;
        }
        SemanticDomainRouteType routeType = mapRouteType(intake.getRouteType());
        boolean needsClarification =
                Boolean.TRUE.equals(intake.getNeedClarification())
                        || routeType == SemanticDomainRouteType.AMBIGUOUS
                        || routeType == SemanticDomainRouteType.UNKNOWN
                        || routeType == SemanticDomainRouteType.MULTI_DOMAIN;
        SemanticDomainRouteResult.SemanticDomainRouteResultBuilder b =
                SemanticDomainRouteResult.builder()
                        .routeType(routeType)
                        .primaryDomain(primary)
                        .confidence(intake.getConfidence())
                        .usedPreviousContext(Boolean.TRUE.equals(intake.getUsedPreviousContext()))
                        .needsClarification(needsClarification);
        if (intake.getCandidateDomains() != null) {
            for (String c : intake.getCandidateDomains()) {
                String n = SemanticIntakePrimaryDomain.normalize(c);
                if (SemanticIntakePrimaryDomain.isKnown(n)) {
                    b.candidateDomain(n);
                }
            }
        }
        if (StringUtils.hasText(intake.getReason())) {
            b.reasonCode(intake.getReason().trim());
        }
        return b.build();
    }

    static SemanticDomainRouteType mapRouteType(String routeType) {
        if (!StringUtils.hasText(routeType)) {
            return SemanticDomainRouteType.UNKNOWN;
        }
        try {
            return SemanticDomainRouteType.valueOf(routeType.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return SemanticDomainRouteType.UNKNOWN;
        }
    }
}
