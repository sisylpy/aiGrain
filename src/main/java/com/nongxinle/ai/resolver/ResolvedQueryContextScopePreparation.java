package com.nongxinle.ai.resolver;

import com.nongxinle.ai.context.AiResolvedDataScope;
import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.context.AiResolvedTimeWindowDisplaySupport;
import com.nongxinle.ai.context.AiSemanticStoreNarrowingDiagnostics;
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
            AiResolvedOrgScope orgScope,
            AiFollowUpResolution followUp,
            AiResolvedQueryIntent queryIntent,
            AiResolvedQueryIntent mergedIntentStem,
            AiQuerySemanticParseResult semanticLlm,
            AiResolvedTimeWindow timeWindow,
            String effectiveTimeSource) {}

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
        AiResolvedOrgScope mergedOrg =
                followUp != null && followUp.getMergedOrgScope() != null
                        ? followUp.getMergedOrgScope()
                        : req.orgScope();

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
                            req.semanticLlm());
            mergedOrg = orgOutcome.org();
            if (orgOutcome.inheritedFromPreviousTurn()) {
                followUp.setEffectiveScopeSource("INHERITED_PREVIOUS");
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
            if (mergedOrg != beforeSemanticStore) {
                followUp.setEffectiveScopeSource("CURRENT_MESSAGE_EXPLICIT_STORE");
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
        String dishProfitMetricType =
                AiQuerySemanticLexicon.dishProfitMetricTypeFromStructuredWire(
                        req.queryIntent() != null ? req.queryIntent().getStructuredIntentDetail() : null);

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
