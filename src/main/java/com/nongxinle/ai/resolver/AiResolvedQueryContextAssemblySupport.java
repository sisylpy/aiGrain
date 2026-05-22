package com.nongxinle.ai.resolver;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.context.AiUserContext;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiFollowUpResolution;
import com.nongxinle.ai.followup.rewrite.FollowUpRewriteResult;
import com.nongxinle.ai.platform.dto.AiRunCreateRequest;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.contract.DomainContractSelectionResult;
import com.nongxinle.ai.semantic.contract.SemanticContractStrictDecision;
import com.nongxinle.ai.semantic.contract.SemanticContractValidationDebug;
import com.nongxinle.ai.semantic.routing.SemanticDomainRouteResult;
import com.nongxinle.ai.context.AiResolvedOrgScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Resolve 阶段最终 {@link AiResolvedQueryContext} 装配入口：串联 scope / orchestration 准备与 builder，
 * 不含具体策略判断（见 {@link ResolvedQueryContextScopePreparation}、
 * {@link SemanticOrchestrationDecisionReconciler}、{@link AiResolvedQueryContextBuilderSupport}）。
 */
@Component
@RequiredArgsConstructor
public class AiResolvedQueryContextAssemblySupport {

    private final ResolvedQueryContextScopePreparation scopePreparation;
    private final AiResolvedQueryContextDiagnostics diagnostics;

    public record AssembleRequest(
            Long runId,
            Long convId,
            Long uid,
            AiUserContext userContext,
            AiRunCreateRequest request,
            String message,
            String normalized,
            String effectiveUserMessage,
            boolean followUpRewriteApplied,
            AiConversationTurnMemory previousTurn,
            AiResolvedOrgScope orgScope,
            FollowUpRewriteResult rewriteResult,
            AiQuerySemanticParseResult querySemanticV2Raw,
            AiResolvedTimeWindow explicitTentative,
            double querySemanticMinConfidence,
            String semanticPrimaryVersion,
            Boolean semanticFallbackUsed,
            String semanticFallbackReason,
            String semanticAdoptedFrom,
            List<String> semanticAdoptedFields,
            List<String> semanticAdoptionRejectedFields,
            String semanticAdoptionRejectedReason,
            String semanticMetricNormalizedFrom,
            String semanticMetricNormalizedTo,
            Map<String, Object> semanticV2AbstractIntentNormalizationNotes,
            Map<String, Object> querySemanticV2InputPreview,
            SemanticDomainRouteResult semanticDomainRoute,
            DomainContractSelectionResult domainContractSelection,
            SemanticContractValidationDebug semanticContractValidation,
            SemanticContractStrictDecision semanticContractStrictDecision,
            int previousTurnResultAnchorsCount,
            int rewritePromptResultAnchorsCount,
            SemanticAdoptionAttempt adoption,
            com.nongxinle.ai.semantic.SemanticTimeContractCheck.Result timeContractResult,
            boolean clarificationRequired,
            String semanticClarificationQuestion,
            AiFollowUpResolution followUp,
            AiResolvedQueryIntent queryIntent,
            AiResolvedTimeWindow timeWindow,
            String effectiveTimeSource,
            AiResolvedQueryIntent mergedIntentStem,
            AiQuerySemanticParseResult semanticLlm,
            boolean applyStructuralLlm) {}

    public AiResolvedQueryContext assemble(AssembleRequest req) {
        ResolvedQueryContextScopePreparation.ScopePrepareResult scope =
                scopePreparation.prepare(
                        new ResolvedQueryContextScopePreparation.ScopePrepareRequest(
                                req.runId(),
                                req.convId(),
                                req.normalized(),
                                req.request(),
                                req.clarificationRequired(),
                                req.applyStructuralLlm(),
                                req.querySemanticMinConfidence(),
                                req.previousTurn(),
                                req.orgScope(),
                                req.followUp(),
                                req.queryIntent(),
                                req.mergedIntentStem(),
                                req.semanticLlm(),
                                req.timeWindow(),
                                req.effectiveTimeSource()));

        SemanticOrchestrationDecisionReconciler.OrchestrationPipelineResult orchestration =
                SemanticOrchestrationDecisionReconciler.extractReconcileAndApplyClarificationGate(
                        req.clarificationRequired(),
                        req.semanticClarificationQuestion(),
                        req.querySemanticV2Raw(),
                        req.semanticLlm(),
                        req.followUp(),
                        req.previousTurn(),
                        req.queryIntent());

        SemanticOrchestrationDecisionReconciler.ClarificationState clarifyAfterOrch =
                orchestration.clarificationState();
        boolean clarificationRequired = clarifyAfterOrch.clarificationRequired();
        String semanticClarificationQuestion = clarifyAfterOrch.semanticClarificationQuestion();

        SemanticScopeNarrowingPolicy.ClarificationState clarifyAfterStore =
                SemanticScopeNarrowingPolicy.applyStoreLexicalAmbiguityClarification(
                        clarificationRequired,
                        semanticClarificationQuestion,
                        scope.storeScopeNarrowDiag());
        clarificationRequired = clarifyAfterStore.clarificationRequired();
        semanticClarificationQuestion = clarifyAfterStore.semanticClarificationQuestion();

        AiResolvedQueryContext built =
                AiResolvedQueryContextBuilderSupport.build(
                        new AiResolvedQueryContextBuilderSupport.BuildPayload(
                                req.runId(),
                                req.uid(),
                                req.userContext(),
                                req.message(),
                                scope.mergedOrg(),
                                req.timeWindow(),
                                req.queryIntent(),
                                scope.dataScope(),
                                req.followUp(),
                                scope.normQuestion(),
                                scope.banner(),
                                scope.timeLabel(),
                                scope.answerBoundaryNote(),
                                req.previousTurn(),
                                req.effectiveTimeSource(),
                                scope.mentionedDishName(),
                                scope.dishProfitMetricType(),
                                req.semanticLlm(),
                                scope.harnessSnapshot(),
                                clarificationRequired,
                                semanticClarificationQuestion,
                                req.timeContractResult(),
                                scope.storeScopeNarrowDiag(),
                                scope.resolvedMatchedSemanticStoreMention(),
                                req.semanticPrimaryVersion(),
                                req.semanticFallbackUsed(),
                                req.semanticFallbackReason(),
                                req.semanticAdoptedFrom(),
                                req.semanticAdoptedFields(),
                                req.semanticAdoptionRejectedFields(),
                                req.semanticAdoptionRejectedReason(),
                                req.semanticMetricNormalizedFrom(),
                                req.semanticMetricNormalizedTo(),
                                req.semanticV2AbstractIntentNormalizationNotes(),
                                req.querySemanticV2InputPreview(),
                                orchestration.fields(),
                                req.followUpRewriteApplied(),
                                req.rewriteResult(),
                                req.previousTurnResultAnchorsCount(),
                                req.rewritePromptResultAnchorsCount(),
                                req.semanticDomainRoute(),
                                req.domainContractSelection(),
                                req.semanticContractValidation(),
                                req.semanticContractStrictDecision(),
                                req.querySemanticV2Raw()));

        diagnostics.logIntentResolutionDiagnostics(
                req.runId(),
                req.convId(),
                req.message(),
                req.previousTurn(),
                req.mergedIntentStem(),
                req.followUp(),
                built);
        diagnostics.logFollowUpDiagnostics(
                req.runId(), req.convId(), req.previousTurn(), req.followUp(), built);
        diagnostics.logResolvedContextPipeline(
                req.runId(),
                req.convId(),
                req.message(),
                req.previousTurn(),
                req.orgScope(),
                req.mergedIntentStem(),
                req.explicitTentative() != null,
                req.followUp(),
                built);
        return built;
    }
}
