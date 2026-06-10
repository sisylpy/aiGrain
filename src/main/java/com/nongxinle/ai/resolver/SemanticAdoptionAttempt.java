package com.nongxinle.ai.resolver;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.SemanticTimeContractCheck;
import com.nongxinle.ai.semantic.contract.DomainContractSelectionResult;
import com.nongxinle.ai.semantic.contract.SemanticContractViolationCode;
import com.nongxinle.ai.semantic.intake.route.SemanticDomainRouteResult;

/**
 * {@link SemanticAdoptionPipeline#tryAdopt} 结果：成功收养、或时间/路径失败待澄清。
 *
 * <p>{@link #semantic()} 为 grounding / completion 后的 effective parse；
 * {@link #effectiveDomainContractSelection()} / {@link #effectiveSemanticDomainRoute()} 为 Adoption 链最终域主权。
 * Intake 初判 route/selection 由 Resolver 保留，不写入本 record。
 */
record SemanticAdoptionAttempt(
        AiQuerySemanticParseResult semantic,
        AiResolvedQueryIntent mergedIntent,
        AiResolvedTimeWindow tentativeTime,
        SemanticTimeContractCheck.Result timeContract,
        String rejectionReason,
        String semanticClarificationQuestion,
        SemanticContractViolationCode contractViolationCode,
        SemanticDomainRouteResult effectiveSemanticDomainRoute,
        DomainContractSelectionResult effectiveDomainContractSelection) {

    SemanticAdoptionAttempt(
            AiQuerySemanticParseResult semantic,
            AiResolvedQueryIntent mergedIntent,
            AiResolvedTimeWindow tentativeTime,
            SemanticTimeContractCheck.Result timeContract,
            String rejectionReason,
            String semanticClarificationQuestion) {
        this(
                semantic,
                mergedIntent,
                tentativeTime,
                timeContract,
                rejectionReason,
                semanticClarificationQuestion,
                null,
                null,
                null);
    }

    SemanticAdoptionAttempt(
            AiQuerySemanticParseResult semantic,
            AiResolvedQueryIntent mergedIntent,
            AiResolvedTimeWindow tentativeTime,
            SemanticTimeContractCheck.Result timeContract,
            String rejectionReason,
            String semanticClarificationQuestion,
            SemanticContractViolationCode contractViolationCode) {
        this(
                semantic,
                mergedIntent,
                tentativeTime,
                timeContract,
                rejectionReason,
                semanticClarificationQuestion,
                contractViolationCode,
                null,
                null);
    }

    SemanticAdoptionAttempt(
            AiQuerySemanticParseResult semantic,
            AiResolvedQueryIntent mergedIntent,
            AiResolvedTimeWindow tentativeTime,
            SemanticTimeContractCheck.Result timeContract,
            String rejectionReason) {
        this(semantic, mergedIntent, tentativeTime, timeContract, rejectionReason, null, null);
    }

    boolean adopted() {
        return semantic != null && mergedIntent != null && timeContract != null && timeContract.valid();
    }

    /** {@link com.nongxinle.ai.semantic.frame.PurchaseCurrentSemanticFrameValidator} 等业务 frame 校验失败时的具体澄清句。 */
    boolean frameClarificationRequired() {
        return semanticClarificationQuestion != null && !semanticClarificationQuestion.isBlank();
    }

    boolean contractSelectionClarificationRequired() {
        return contractViolationCode != null;
    }

    SemanticDomainRouteResult effectiveSemanticDomainRouteOr(SemanticDomainRouteResult intakeRoute) {
        return effectiveSemanticDomainRoute != null ? effectiveSemanticDomainRoute : intakeRoute;
    }

    DomainContractSelectionResult effectiveDomainContractSelectionOr(
            DomainContractSelectionResult intakeSelection) {
        return effectiveDomainContractSelection != null
                ? effectiveDomainContractSelection
                : intakeSelection;
    }
}
