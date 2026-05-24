package com.nongxinle.ai.resolver;

import com.nongxinle.ai.context.AiResolvedDataScope;
import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.context.AiSemanticStoreNarrowingDiagnostics;
import com.nongxinle.ai.context.AiUserContext;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiFollowUpResolution;
import com.nongxinle.ai.semantic.intake.SemanticIntakeNormalizationType;
import com.nongxinle.ai.semantic.intake.SemanticIntakeResult;
import com.nongxinle.ai.semantic.intake.SemanticIntakeStatus;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResultDebugSerializer;
import com.nongxinle.ai.semantic.contract.DomainContractSelectionResult;
import com.nongxinle.ai.semantic.contract.SemanticContractStrictDecision;
import com.nongxinle.ai.semantic.contract.SemanticContractValidationDebug;
import com.nongxinle.ai.semantic.intake.route.SemanticDomainRouteResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 仅负责 {@link AiResolvedQueryContext#builder()} 字段写入；不含 org/编排/收窄策略判断。
 */
public final class AiResolvedQueryContextBuilderSupport {

    private AiResolvedQueryContextBuilderSupport() {}

    public record BuildPayload(
            Long runId,
            Long uid,
            AiUserContext userContext,
            String message,
            AiResolvedOrgScope mergedOrg,
            AiResolvedTimeWindow timeWindow,
            AiResolvedQueryIntent queryIntent,
            AiResolvedDataScope dataScope,
            AiFollowUpResolution followUp,
            String normQuestion,
            String banner,
            String timeLabel,
            String answerBoundaryNote,
            AiConversationTurnMemory previousTurn,
            String effectiveTimeSource,
            String mentionedDishName,
            String dishProfitMetricType,
            AiQuerySemanticParseResult semanticLlm,
            SemanticHarnessScopePolicy.HarnessMultiStoreSnapshot harnessSnapshot,
            boolean clarificationRequired,
            String semanticClarificationQuestion,
            com.nongxinle.ai.semantic.SemanticTimeContractCheck.Result timeContractResult,
            AiSemanticStoreNarrowingDiagnostics storeScopeNarrowDiag,
            String resolvedMatchedSemanticStoreMention,
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
            SemanticOrchestrationDecisionReconciler.OrchestrationAssemblyFields orchestration,
            boolean followUpRewriteApplied,
            SemanticIntakeResult semanticIntake,
            int previousTurnResultAnchorsCount,
            int rewritePromptResultAnchorsCount,
            SemanticDomainRouteResult semanticDomainRoute,
            DomainContractSelectionResult domainContractSelection,
            SemanticContractValidationDebug semanticContractValidation,
            SemanticContractStrictDecision semanticContractStrictDecision,
            AiQuerySemanticParseResult querySemanticV2Raw) {}

    public static AiResolvedQueryContext build(BuildPayload p) {
        SemanticOrchestrationDecisionReconciler.OrchestrationAssemblyFields orch = p.orchestration();
        return AiResolvedQueryContext.builder()
                .runId(p.runId())
                .userId(p.uid())
                .userContext(p.userContext())
                .orgScope(p.mergedOrg())
                .timeWindow(p.timeWindow())
                .queryIntent(p.queryIntent())
                .dataScope(p.dataScope())
                .followUp(p.followUp().isFollowUp())
                .originalQuestion(p.message())
                .normalizedQuestion(p.normQuestion())
                .queryScopeBanner(p.banner())
                .timeWindowLabel(p.timeLabel())
                .answerBoundaryNote(p.answerBoundaryNote())
                .previousTurn(p.previousTurn())
                .followUpResolution(p.followUp())
                .effectiveIntentCode(p.followUp().getEffectiveIntentCode())
                .effectivePathCode(p.followUp().getEffectivePathCode())
                .effectiveTimeWindowSource(p.effectiveTimeSource())
                .effectiveScopeSource(p.followUp().getEffectiveScopeSource())
                .effectiveIntentSource(p.followUp().getEffectiveIntentSource())
                .mentionedDishName(p.mentionedDishName())
                .dishProfitMetricType(p.dishProfitMetricType())
                .querySemanticParse(p.semanticLlm())
                .semanticPromptRegistryId(
                        p.semanticLlm() != null ? p.semanticLlm().getPromptRegistryId() : null)
                .harnessMultiStoreScopeDetected(p.harnessSnapshot().harnessMultiStoreScopeDetected())
                .harnessMultiStoreScopeApplied(p.harnessSnapshot().harnessMultiStoreScopeApplied())
                .harnessMultiStoreScopeSource(
                        AiResolvedQueryContextDebugFactory.blankToNullSemantic(
                                p.harnessSnapshot().harnessMultiStoreScopeSource()))
                .harnessMultiStoreMatchedStores(
                        p.harnessSnapshot().harnessMatchedNames().isEmpty()
                                ? null
                                : new ArrayList<>(p.harnessSnapshot().harnessMatchedNames()))
                .harnessSingleStoreNarrowingBlocked(p.harnessSnapshot().singleStoreNarrowingBlocked())
                .needSemanticClarification(p.clarificationRequired())
                .semanticClarificationQuestion(p.semanticClarificationQuestion())
                .timeContractValid(p.timeContractResult() != null ? p.timeContractResult().valid() : null)
                .timeContractFailureReason(
                        p.timeContractResult() != null && !p.timeContractResult().valid()
                                ? AiResolvedQueryContextDebugFactory.blankToNullSemantic(
                                        p.timeContractResult().failureReason())
                                : null)
                .semanticStoreNarrowingDebug(p.storeScopeNarrowDiag())
                .resolvedMatchedSemanticStoreMention(
                        AiResolvedQueryContextDebugFactory.blankToNullSemantic(
                                p.resolvedMatchedSemanticStoreMention()))
                .semanticPrimaryVersion(
                        AiResolvedQueryContextDebugFactory.blankToNullSemantic(p.semanticPrimaryVersion()))
                .semanticFallbackUsed(p.semanticFallbackUsed())
                .semanticFallbackReason(
                        AiResolvedQueryContextDebugFactory.blankToNullSemantic(p.semanticFallbackReason()))
                .semanticAdoptedFrom(
                        AiResolvedQueryContextDebugFactory.blankToNullSemantic(p.semanticAdoptedFrom()))
                .purchaseSemanticFramePrimaryMerge(
                        p.semanticLlm() != null ? p.semanticLlm().getPurchaseSemanticFramePrimaryMerge() : null)
                .semanticAdoptedFields(
                        p.semanticAdoptedFields() == null || p.semanticAdoptedFields().isEmpty()
                                ? null
                                : new ArrayList<>(p.semanticAdoptedFields()))
                .semanticAdoptionRejectedFields(
                        p.semanticAdoptionRejectedFields() == null
                                        || p.semanticAdoptionRejectedFields().isEmpty()
                                ? null
                                : new ArrayList<>(p.semanticAdoptionRejectedFields()))
                .semanticAdoptionRejectedReason(
                        AiResolvedQueryContextDebugFactory.blankToNullSemantic(
                                p.semanticAdoptionRejectedReason()))
                .semanticMetricNormalizedFrom(
                        AiResolvedQueryContextDebugFactory.blankToNullSemantic(p.semanticMetricNormalizedFrom()))
                .semanticMetricNormalizedTo(
                        AiResolvedQueryContextDebugFactory.blankToNullSemantic(p.semanticMetricNormalizedTo()))
                .semanticV2AbstractIntentNormalizationNotes(
                        p.semanticV2AbstractIntentNormalizationNotes() == null
                                        || p.semanticV2AbstractIntentNormalizationNotes().isEmpty()
                                ? null
                                : new LinkedHashMap<>(p.semanticV2AbstractIntentNormalizationNotes()))
                .querySemanticV2InputPreview(p.querySemanticV2InputPreview())
                .querySemanticV2(
                        p.semanticLlm() == null
                                ? null
                                : AiQuerySemanticParseResultDebugSerializer.toSafeMap(p.semanticLlm()))
                .querySemanticV2ParseMissing(p.semanticLlm() == null ? null : p.semanticLlm().isParseMissing())
                .querySemanticV2Confidence(p.semanticLlm() == null ? null : p.semanticLlm().getConfidence())
                .querySemanticV2TimeAction(
                        p.semanticLlm() == null
                                ? null
                                : AiResolvedQueryContextDebugFactory.blankToNullSemantic(
                                        p.semanticLlm().getTimeAction()))
                .querySemanticV2ScopeAction(
                        p.semanticLlm() == null
                                ? null
                                : AiResolvedQueryContextDebugFactory.blankToNullSemantic(
                                        p.semanticLlm().getScopeAction()))
                .querySemanticV2IntentAction(
                        p.semanticLlm() == null
                                ? null
                                : AiResolvedQueryContextDebugFactory.blankToNullSemantic(
                                        p.semanticLlm().getIntentAction()))
                .querySemanticV2MetricAction(
                        p.semanticLlm() == null
                                ? null
                                : AiResolvedQueryContextDebugFactory.blankToNullSemantic(
                                        p.semanticLlm().getMetricAction()))
                .querySemanticV2MentionedStoreNames(
                        AiResolvedQueryContextDebugFactory.querySemanticV2EffectiveStoreNames(p.semanticLlm()))
                .querySemanticV2MentionedDishName(
                        p.semanticLlm() == null
                                ? null
                                : AiResolvedQueryContextDebugFactory.blankToNullSemantic(
                                        p.semanticLlm().getMentionedDishName()))
                .querySemanticV2RawText(
                        p.semanticLlm() == null
                                ? null
                                : AiResolvedQueryContextDebugFactory.blankToNullSemantic(
                                        p.semanticLlm().getObservationLlmRawText()))
                .querySemanticV2ParseError(
                        p.semanticLlm() == null
                                ? null
                                : AiResolvedQueryContextDebugFactory.blankToNullSemantic(
                                        p.semanticLlm().getObservationJsonParseError()))
                .querySemanticV2RepairAttempted(
                        p.semanticLlm() == null ? null : p.semanticLlm().getQuerySemanticV2RepairAttempted())
                .querySemanticV2RepairSuccess(
                        p.semanticLlm() == null ? null : p.semanticLlm().getQuerySemanticV2RepairSuccess())
                .querySemanticV2RepairReason(
                        p.semanticLlm() == null
                                ? null
                                : AiResolvedQueryContextDebugFactory.blankToNullSemantic(
                                        p.semanticLlm().getQuerySemanticV2RepairReason()))
                .orchestrationTaskMode(orch.taskMode())
                .orchestrationSelectedAgents(orch.selectedAgents())
                .orchestrationSelectedTools(orch.selectedTools())
                .orchestrationPlannerRequired(orch.plannerRequired())
                .orchestrationMultiAgentRequired(orch.multiAgentRequired())
                .orchestrationApprovalRequired(orch.approvalRequired())
                .orchestrationClarificationRequired(orch.clarificationRequiredFlag())
                .orchestrationClarificationQuestion(orch.clarificationQuestionField())
                .orchestrationConfidence(orch.confidence())
                .orchestrationReason(orch.reason())
                .rawUserMessage(p.message())
                .followUpRewriteApplied(p.followUpRewriteApplied())
                .completedUserQuery(resolveCompletedUserQuery(p.followUpRewriteApplied(), p.semanticIntake()))
                .followUpRewriteReason(
                        p.semanticIntake() != null
                                ? AiResolvedQueryContextDebugFactory.blankToNullSemantic(
                                        p.semanticIntake().getReason())
                                : null)
                .followUpRewriteDebug(
                        p.semanticIntake() != null ? p.semanticIntake().toDebugMap() : null)
                .rewriteInheritedTime(null)
                .rewriteInheritedScope(null)
                .rewriteInheritedAnchorType(null)
                .rewriteInheritedAnchorName(null)
                .followUpRewriteClarificationQuestion(resolveIntakeClarificationQuestion(p.semanticIntake()))
                .rewriteUsedAnchors(null)
                .previousTurnResultAnchorsCount(p.previousTurnResultAnchorsCount())
                .rewritePromptResultAnchorsCount(p.rewritePromptResultAnchorsCount())
                .semanticIntake(p.semanticIntake())
                .semanticDomainRoute(p.semanticDomainRoute())
                .domainContractSelection(p.domainContractSelection())
                .semanticContractValidation(p.semanticContractValidation())
                .semanticContractStrictDecision(p.semanticContractStrictDecision())
                .querySemanticV2Domain(
                        p.semanticLlm() != null
                                ? AiResolvedQueryContextDebugFactory.blankToNullSemantic(
                                        p.semanticLlm().getSemanticDomain())
                                : null)
                .routeParserDomainMismatch(
                        AiResolvedQueryContextDebugFactory.observeRouteParserDomainMismatch(
                                p.semanticDomainRoute(), p.querySemanticV2Raw()))
                .routeParserDomainMismatchReason(
                        AiResolvedQueryContextDebugFactory.observeRouteParserDomainMismatchReason(
                                p.semanticDomainRoute(), p.querySemanticV2Raw()))
                .build();
    }

    private static String resolveCompletedUserQuery(
            boolean followUpRewriteApplied, SemanticIntakeResult intake) {
        if (!followUpRewriteApplied || intake == null) {
            return null;
        }
        if (intake.getNormalizationType() != SemanticIntakeNormalizationType.REWRITE) {
            return null;
        }
        return org.springframework.util.StringUtils.hasText(intake.getCanonicalUserQuery())
                ? intake.getCanonicalUserQuery().trim()
                : null;
    }

    private static String resolveIntakeClarificationQuestion(SemanticIntakeResult intake) {
        if (intake == null) {
            return null;
        }
        if (intake.getStatus() != SemanticIntakeStatus.NEED_CLARIFICATION
                && intake.getStatus() != SemanticIntakeStatus.INVALID) {
            return null;
        }
        return org.springframework.util.StringUtils.hasText(intake.getClarificationQuestion())
                ? intake.getClarificationQuestion().trim()
                : null;
    }
}
