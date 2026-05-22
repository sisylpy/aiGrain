package com.nongxinle.ai.resolver;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.followup.rewrite.FollowUpRewriteResult;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.contract.DomainContractSelectionResult;
import com.nongxinle.ai.semantic.contract.EffectiveSemanticContractFrame;
import com.nongxinle.ai.semantic.contract.SemanticContractClarificationQuestionFactory;
import com.nongxinle.ai.semantic.contract.SemanticContractStrictDecision;
import com.nongxinle.ai.semantic.contract.SemanticContractStrictDecisionEvaluator;
import com.nongxinle.ai.semantic.contract.SemanticContractValidationDebug;
import com.nongxinle.ai.semantic.contract.SemanticContractValidator;
import com.nongxinle.ai.semantic.routing.SemanticDomainRouteResult;

/**
 * Contract validation 组装：EffectiveSemanticContractFrame → Validator.observe → StrictDecision。
 * 逻辑与 Resolver 内联块等价，仅搬移调用顺序。
 */
public final class SemanticContractValidationPipeline {

    private SemanticContractValidationPipeline() {}

    public record Request(
            boolean rewriteClarificationRequired,
            DomainContractSelectionResult domainContractSelection,
            SemanticAdoptionAttempt adoption,
            AiQuerySemanticParseResult querySemanticV2Raw,
            AiConversationTurnMemory previousTurnForParser,
            boolean followUpRewriteApplied,
            FollowUpRewriteResult rewriteResult,
            SemanticDomainRouteResult semanticDomainRoute,
            boolean strictEnabled) {}

    public record Result(
            SemanticContractValidationDebug semanticContractValidation,
            SemanticContractStrictDecision semanticContractStrictDecision) {}

    public static Result run(Request request) {
        if (request == null
                || request.rewriteClarificationRequired()
                || request.domainContractSelection() == null) {
            return new Result(null, null);
        }
        AiQuerySemanticParseResult parseForContractValidation =
                request.adoption() != null
                                && request.adoption().semantic() != null
                                && !request.adoption().semantic().isParseMissing()
                        ? request.adoption().semantic()
                        : request.querySemanticV2Raw();
        if (parseForContractValidation == null || parseForContractValidation.isParseMissing()) {
            return new Result(null, null);
        }
        EffectiveSemanticContractFrame effectiveContractFrame =
                EffectiveSemanticContractFrame.of(
                        parseForContractValidation,
                        request.domainContractSelection().getSelectedDomain(),
                        request.previousTurnForParser(),
                        request.followUpRewriteApplied() && request.rewriteResult() != null
                                ? AiResolvedQueryContextDebugFactory.blankToNullSemantic(
                                        request.rewriteResult().getInheritedAnchorType())
                                : null,
                        request.followUpRewriteApplied() && request.rewriteResult() != null
                                ? AiResolvedQueryContextDebugFactory.blankToNullSemantic(
                                        request.rewriteResult().getInheritedAnchorName())
                                : null);
        SemanticContractValidationDebug validation =
                SemanticContractValidator.observe(
                        effectiveContractFrame, request.domainContractSelection());
        SemanticContractStrictDecision strictDecision =
                SemanticContractStrictDecisionEvaluator.evaluate(
                        request.semanticDomainRoute(),
                        request.domainContractSelection(),
                        effectiveContractFrame,
                        request.strictEnabled());
        if (request.adoption() != null && request.adoption().contractViolationCode() != null) {
            SemanticAdoptionAttempt adoption = request.adoption();
            String question =
                    org.springframework.util.StringUtils.hasText(adoption.semanticClarificationQuestion())
                            ? adoption.semanticClarificationQuestion().trim()
                            : SemanticContractClarificationQuestionFactory.forContractViolation(
                                    adoption.contractViolationCode(),
                                    adoption.rejectionReason() != null
                                            ? adoption.rejectionReason()
                                                    .replaceFirst("^contract_completion:", "")
                                            : null);
            validation =
                    SemanticContractValidationDebug.builder()
                            .modelContractViolation(true)
                            .violationCode(adoption.contractViolationCode())
                            .violationReason(
                                    adoption.rejectionReason() != null
                                            ? adoption.rejectionReason()
                                                    .replaceFirst("^contract_completion:", "")
                                            : null)
                            .selectedDomain(
                                    request.domainContractSelection() != null
                                            ? request.domainContractSelection().getSelectedDomain()
                                            : null)
                            .allowedContractCount(
                                    request.domainContractSelection() != null
                                            ? request.domainContractSelection().getSelectedActiveContractCount()
                                            : 0)
                            .build();
            strictDecision =
                    SemanticContractStrictDecision.builder()
                            .strictEnabled(request.strictEnabled())
                            .modelContractViolation(true)
                            .enforceClarification(true)
                            .violationCode(adoption.contractViolationCode())
                            .violationReason(validation.getViolationReason())
                            .selectedDomain(validation.getSelectedDomain())
                            .allowedContractCount(validation.getAllowedContractCount())
                            .clarificationQuestion(question)
                            .activeStrictBlockers(
                                    strictDecision != null && strictDecision.getActiveStrictBlockers() != null
                                            ? strictDecision.getActiveStrictBlockers()
                                            : java.util.List.of())
                            .build();
        }
        return new Result(validation, strictDecision);
    }
}
