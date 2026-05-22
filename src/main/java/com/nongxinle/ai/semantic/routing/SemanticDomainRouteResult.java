package com.nongxinle.ai.semantic.routing;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

/** Step 1 {@link SemanticDomainRouter} 输出；禁止 wire / answerPlanType / selectedTools。 */
@Value
@Builder
public class SemanticDomainRouteResult {

    SemanticDomainRouteType routeType;
    String primaryDomain;
    @Singular("candidateDomain")
    List<String> candidateDomains;
    Double confidence;
    @Singular("reasonCode")
    List<String> reasonCodes;
    @Singular("matchedBusinessObject")
    List<String> matchedBusinessObjects;
    boolean usedPreviousContext;
    boolean needsClarification;
}
