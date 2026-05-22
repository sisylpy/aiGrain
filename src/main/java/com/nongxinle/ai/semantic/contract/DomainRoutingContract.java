package com.nongxinle.ai.semantic.contract;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

/**
 * Step 1 域路由简表：仅供未来 {@code SemanticDomainRouter} 粗选域。
 * <p>禁止包含 wire / answerPlanType / selectedTools / SQL / Java if 规则。
 */
@Value
@Builder
public class DomainRoutingContract {

    String domainCode;
    String domainName;
    @Singular("businessObject")
    List<String> businessObjects;
    @Singular("supportedTaskType")
    List<String> supportedTaskTypes;
    @Singular("anchorType")
    List<String> anchorTypes;
    @Singular("crossDomainHint")
    List<String> crossDomainHints;
    @Singular("routeExample")
    List<String> routeExamples;
    DomainRoutingContractStatus status;
}
