package com.nongxinle.ai.resolver;

import com.nongxinle.ai.context.AiResolvedDataScope;
import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.context.AiResolvedTimeWindowDisplaySupport;
import com.nongxinle.ai.context.AiSemanticStoreNarrowingDiagnostics;
import com.nongxinle.ai.context.ScopeResolutionTrace;
import com.nongxinle.ai.context.AiUserContext;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiFollowUpResolution;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.harness.AiMultiStoreHarnessTrace;
import com.nongxinle.ai.platform.dto.AiRunCreateRequest;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.util.AiUserMessageSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Resolve 阶段 org 继承、语义门店收窄、harness 多店快照与展示字段（不含 {@link AiResolvedQueryContext} builder）。
 */
@Component
@RequiredArgsConstructor
public class ResolvedQueryContextScopePreparation {

    private final SemanticScopeNarrowingPolicy scopeNarrowingPolicy;
    private final AiResolvedOrgScopeAssembler orgScopeAssembler;
    private final SemanticPermissionMentionPolicy permissionMentionPolicy;
    private final AiResolvedQueryContextDiagnostics diagnostics;

    public record ScopePrepareRequest(
            Long runId,
            Long convId,
            String normalized,
            AiRunCreateRequest request,
            boolean clarificationRequired,
            boolean applyStructuralLlm,
            double querySemanticMinConfidence,
            AiConversationTurnMemory previousTurn,
            AiUserContext userContext,
            Long effectiveDepartmentId,
            AiResolvedOrgScope orgScope,
            AiFollowUpResolution followUp,
            AiResolvedQueryIntent queryIntent,
            AiResolvedQueryIntent mergedIntentStem,
            AiQuerySemanticParseResult semanticLlm,
            AiResolvedTimeWindow timeWindow,
            String effectiveTimeSource,
            ScopeResolutionTrace scopeResolutionTrace) {}

    public record ScopePrepareResult(
            AiResolvedOrgScope mergedOrg,
            AiResolvedDataScope dataScope,
            AiSemanticStoreNarrowingDiagnostics storeScopeNarrowDiag,
            SemanticHarnessScopePolicy.HarnessMultiStoreSnapshot harnessSnapshot,
            String normQuestion,
            String banner,
            String timeLabel,
            String answerBoundaryNote,
            String mentionedDishName,
            String dishProfitMetricType,
            String resolvedMatchedSemanticStoreMention) {}

    public ScopePrepareResult prepare(ScopePrepareRequest req) {
        AiFollowUpResolution followUp = req.followUp();
        ScopeResolutionTrace trace = req.scopeResolutionTrace();
        boolean explicitGroupScopeRequest = RequestExplicitGroupScopeSupport.isExplicitGroupScopeRequest(req.request());
        if (trace != null) {
            trace.setGroupToStoreNarrowingAllowed(
                    !explicitGroupScopeRequest
                            || RequestExplicitGroupScopeSupport.shouldAllowGroupToStoreNarrowing(
                                    req.normalized(), req.semanticLlm()));
        }
        AiResolvedOrgScope groupBaselineOrg =
                explicitGroupScopeRequest
                        ? RequestExplicitGroupScopeSupport.pinBaselineGroupOrgScope(
                                orgScopeAssembler,
                                req.userContext(),
                                req.effectiveDepartmentId(),
                                req.request(),
                                req.orgScope())
                        : req.orgScope();
        AiResolvedOrgScope mergedOrg =
                explicitGroupScopeRequest ? groupBaselineOrg : (followUp != null && followUp.getMergedOrgScope() != null
                                ? followUp.getMergedOrgScope()
                                : req.orgScope());
        if (trace != null) {
            trace.setMergedOrgScopeTypeBeforePreparation(
                    mergedOrg != null ? mergedOrg.getScopeType() : null);
        }

        if (explicitGroupScopeRequest && followUp != null) {
            followUp.setInheritOrgScope(false);
            followUp.setMergedOrgScope(groupBaselineOrg);
            followUp.setEffectiveScopeSource(RequestExplicitGroupScopeSupport.EFFECTIVE_SCOPE_SOURCE_REQUEST);
        }

        if (!req.clarificationRequired()) {
            String dishReasonScopeProbe =
                    req.applyStructuralLlm()
                                    && req.semanticLlm() != null
                                    && StringUtils.hasText(req.semanticLlm().getMentionedDishName())
                            ? req.semanticLlm().getMentionedDishName().trim()
                            : null;
            var orgOutcome =
                    AiMultiTurnOrgScopePolicy.applyInheritedEffectiveOrgScope(
                            mergedOrg,
                            req.previousTurn(),
                            req.normalized(),
                            dishReasonScopeProbe,
                            req.mergedIntentStem() != null
                                    ? req.mergedIntentStem().getStructuredIntentDetail()
                                    : null,
                            req.semanticLlm(),
                            explicitGroupScopeRequest);
            mergedOrg = orgOutcome.org();
            if (trace != null) {
                trace.setMultiTurnInherited(orgOutcome.inheritedFromPreviousTurn());
            }
            if (orgOutcome.inheritedFromPreviousTurn()) {
                followUp.setEffectiveScopeSource("INHERITED_PREVIOUS");
            } else if (explicitGroupScopeRequest) {
                followUp.setEffectiveScopeSource(RequestExplicitGroupScopeSupport.EFFECTIVE_SCOPE_SOURCE_REQUEST);
            } else if (followUp != null
                    && followUp.isInheritOrgScope()
                    && !"STORE_SCOPE_FOLLOW_UP".equals(followUp.getFollowUpType())
                    && !"GROUP_SCOPE_EXPAND_FOLLOW_UP".equals(followUp.getFollowUpType())
                    && !"STORE_PRIORITY_RANKING_FOLLOW_UP".equals(followUp.getFollowUpType())
                    && !"SEMANTIC_STRUCTURAL_MERGE".equals(followUp.getFollowUpType())
                    && "INHERITED_PREVIOUS".equals(followUp.getEffectiveScopeSource())) {
                followUp.setEffectiveScopeSource("CURRENT_MESSAGE");
            }
        }

        AiResolvedOrgScope beforeStoreNarrowingPass = mergedOrg;
        AiMultiStoreHarnessTrace multiStoreHarness = AiMultiStoreHarnessTrace.create();
        multiStoreHarness.ingestDetectionCandidate(req.normalized(), req.semanticLlm(), mergedOrg);
        AiSemanticStoreNarrowingDiagnostics storeScopeNarrowDiag =
                AiSemanticStoreNarrowingDiagnostics.empty();
        boolean semanticNarrowingApplied = false;
        if (!req.clarificationRequired()) {
            AiResolvedOrgScope beforeSemanticStore = mergedOrg;
            mergedOrg =
                    scopeNarrowingPolicy.narrowGroupOrgBySemanticLlmStoreIfNeeded(
                            mergedOrg,
                            req.semanticLlm(),
                            req.request(),
                            req.applyStructuralLlm(),
                            req.querySemanticMinConfidence(),
                            req.normalized(),
                            null,
                            multiStoreHarness,
                            storeScopeNarrowDiag);
            semanticNarrowingApplied = mergedOrg != beforeSemanticStore;
            if (semanticNarrowingApplied) {
                followUp.setEffectiveScopeSource("CURRENT_MESSAGE_EXPLICIT_STORE");
                followUp.setMergedOrgScope(mergedOrg);
            }
        }

        if (explicitGroupScopeRequest
                && !RequestExplicitGroupScopeSupport.shouldAllowGroupToStoreNarrowing(
                        req.normalized(), req.semanticLlm())) {
            mergedOrg =
                    RequestExplicitGroupScopeSupport.pinBaselineGroupOrgScope(
                            orgScopeAssembler,
                            req.userContext(),
                            req.effectiveDepartmentId(),
                            req.request(),
                            groupBaselineOrg);
            if (followUp != null) {
                followUp.setInheritOrgScope(false);
                followUp.setMergedOrgScope(mergedOrg);
                followUp.setEffectiveScopeSource(
                        RequestExplicitGroupScopeSupport.EFFECTIVE_SCOPE_SOURCE_REQUEST);
            }
            if (storeScopeNarrowDiag != null
                    && storeScopeNarrowDiag.getNarrowingFailureReason() == null) {
                storeScopeNarrowDiag.setNarrowingFailureReason(
                        AiSemanticStoreNarrowingDiagnostics.REASON_SKIPPED_EXPLICIT_GROUP_REQUEST);
            }
        }

        if (!req.clarificationRequired() && mergedOrg != beforeStoreNarrowingPass) {
            diagnostics.logExplicitStoreMentionNarrowing(
                    req.runId(), req.convId(), beforeStoreNarrowingPass, mergedOrg);
        }

        SemanticHarnessScopePolicy.HarnessMultiStoreSnapshot harnessSnapshot =
                SemanticHarnessScopePolicy.buildHarnessMultiStoreSnapshot(
                        req.clarificationRequired(),
                        req.normalized(),
                        followUp,
                        req.semanticLlm(),
                        req.previousTurn(),
                        mergedOrg,
                        req.queryIntent(),
                        multiStoreHarness);

        AiResolvedDataScope dataScope = orgScopeAssembler.buildDataScope(mergedOrg);
        if (trace != null) {
            trace.snapshotAfterPreparation(mergedOrg, Boolean.TRUE.equals(trace.getMultiTurnInherited()), semanticNarrowingApplied);
            trace.snapshotDataScope(mergedOrg, dataScope);
        }

        String normQuestion = req.normalized();
        if (followUp.isNormalizedInputExpandedAtResolvePhase()
                && followUp.getExpandedNormalizedQuestion() != null
                && !followUp.getExpandedNormalizedQuestion().isBlank()) {
            normQuestion =
                    AiUserMessageSanitizer.stripLeadingEnumeration(
                                    followUp.getExpandedNormalizedQuestion())
                            .trim();
        }

        String banner = mergedOrg != null ? mergedOrg.getQueryScopeBanner() : null;
        String timeLabel = req.timeWindow() != null ? req.timeWindow().getDisplayText() : null;
        String answerBoundaryNote =
                AiResolvedTimeWindowDisplaySupport.buildCombinedBoundaryNote(
                        req.effectiveTimeSource(),
                        followUp != null ? followUp.getEffectiveScopeSource() : null,
                        req.timeWindow(),
                        mergedOrg,
                        req.previousTurn());

        String mentionedDishName =
                req.clarificationRequired()
                        ? null
                        : permissionMentionPolicy.resolveMentionedDishName(
                                req.queryIntent(),
                                req.previousTurn(),
                                mergedOrg,
                                followUp,
                                req.semanticLlm());
        String dishProfitMetricType = null;
        // dishProfitMetricType 仅在 contract-locked ToolRequest 参数构造阶段由
        // ToolRequestContractExecutionParamSupport.resolveDishProfitMetricType(...) 计算。
        // ScopePreparation 不读取 raw queryIntent wire 推导业务 metricType。

        SemanticScopeNarrowingPolicy.StoreNarrowingSideEffects narrowingSideEffects =
                SemanticScopeNarrowingPolicy.resolveStoreNarrowingSideEffects(
                        storeScopeNarrowDiag, followUp);

        return new ScopePrepareResult(
                mergedOrg,
                dataScope,
                storeScopeNarrowDiag,
                harnessSnapshot,
                normQuestion,
                banner,
                timeLabel,
                answerBoundaryNote,
                mentionedDishName,
                dishProfitMetricType,
                narrowingSideEffects.resolvedMatchedSemanticStoreMention());
    }
}
