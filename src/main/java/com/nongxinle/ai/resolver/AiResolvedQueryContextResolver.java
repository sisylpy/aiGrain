package com.nongxinle.ai.resolver;

import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.ScopeResolutionTrace;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.context.AiUserContext;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.conversation.AiConversationMemoryService;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiFollowUpResolution;
import com.nongxinle.ai.conversation.AiFollowUpResolver;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.semantic.contract.*;
import com.nongxinle.ai.semantic.intake.*;
import com.nongxinle.ai.semantic.intake.llm.LlmSemanticIntakeParser;
import com.nongxinle.ai.semantic.dimension.BareRankingDimensionSwitchPlan;
import com.nongxinle.ai.semantic.dimension.BareRankingDimensionSwitchSupport;
import com.nongxinle.ai.security.AiPermissionDenied;
import com.nongxinle.ai.platform.dto.AiRunCreateRequest;
import com.nongxinle.ai.scope.AiConversationScopeMode;
import com.nongxinle.ai.semantic.intake.route.SemanticDomainRouteResult;
import com.nongxinle.ai.semantic.*;
import com.nongxinle.ai.util.AiUserMessageSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 统一解析入口：装配 {@link AiResolvedQueryContext}（唯一新业务上下文入口）。
 * <p>主链：SemanticIntake LLM → DomainContractSelector → query_semantic_parser.v2 → Adoption → Validation。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiResolvedQueryContextResolver {

    private final AiConversationMemoryService conversationMemoryService;
    private final AiQuerySemanticLlmParser querySemanticLlmParser;
    private final LlmSemanticIntakeParser semanticIntakeParser;
    private final SemanticContractStrictProperties semanticContractStrictProperties;
    private final AiResolvedOrgScopeAssembler orgScopeAssembler;
    private final SemanticAdoptionPipeline semanticAdoptionPipeline;
    private final AiResolvedQueryContextAssemblySupport assemblySupport;
    private final AiResolvedQueryContextDiagnostics diagnostics;
    private final SemanticPermissionMentionPolicy permissionMentionPolicy;

    @Value("${ai.agent.querySemanticLlm.minConfidence:0.55}")
    private double querySemanticMinConfidence;

    public AiResolvedQueryContext resolve(AiRunCreateRequest request, AiUserContext userContext) {
        return resolve(null, request, userContext, LocalDate.now());
    }

    public AiResolvedQueryContext resolve(Long runId, AiRunCreateRequest request, AiUserContext userContext) {
        return resolve(runId, request, userContext, LocalDate.now());
    }

    /**
     * @param today 语义解析「今天」锚点；Harness Replay 传入固定日以稳定断言，生产链路请使用 {@link #resolve(Long, AiRunCreateRequest, AiUserContext)}。
     */
    public AiResolvedQueryContext resolve(Long runId, AiRunCreateRequest request, AiUserContext userContext, LocalDate today) {
        return resolve(runId, request, userContext, null, today);
    }

    public AiResolvedQueryContext resolve(
            Long runId,
            AiRunCreateRequest request,
            AiUserContext userContext,
            AiConversationScopeMode conversationScopeMode,
            LocalDate today) {
        Objects.requireNonNull(today, "today");
        Objects.requireNonNull(userContext, "userContext");
        AiConversationScopeMode effectiveScopeMode =
                RequestExplicitGroupScopeSupport.resolveEffectiveScopeMode(request, conversationScopeMode);
        ScopeResolutionTrace scopeTrace =
                ScopeResolutionTrace.builder()
                        .requestScopeMode(request != null ? request.getScopeMode() : null)
                        .conversationScopeModeParam(
                                conversationScopeMode != null ? conversationScopeMode.name() : null)
                        .effectiveScopeModeBeforeResolveOrg(
                                effectiveScopeMode != null ? effectiveScopeMode.name() : null)
                        .explicitGroupRequest(
                                String.valueOf(
                                        RequestExplicitGroupScopeSupport.isExplicitGroupScopeRequest(request)))
                        .build();
        String message = request != null ? request.getMessage() : null;
        String normalized = message == null ? "" : AiUserMessageSanitizer.stripLeadingEnumeration(message).trim();
        Long reqDept = request != null ? request.getDepartmentId() : null;
        Long effectiveDept = reqDept != null ? reqDept : userContext.getDepartmentId();

        Long uid = userContext.getUserId() != null ? userContext.getUserId()
                : (request != null ? request.getUserId() : null);
        Long convId = request != null ? request.getConversationId() : null;
        AiConversationTurnMemory previousTurn =
                uid != null ? conversationMemoryService.load(uid, convId) : null;
        diagnostics.logResolveStart(runId, convId, uid, normalized, previousTurn);

        AiResolvedOrgScope orgScope =
                orgScopeAssembler.resolveOrgScope(userContext, effectiveDept, request, effectiveScopeMode);
        if (RequestExplicitGroupScopeSupport.isExplicitGroupScopeRequest(request)) {
            orgScope =
                    RequestExplicitGroupScopeSupport.pinBaselineGroupOrgScope(
                            orgScopeAssembler, userContext, effectiveDept, request, orgScope);
        }
        scopeTrace.snapshotBaseline(orgScope, "resolveOrgScope");

        SemanticIntakeInput intakeInput =
                SemanticIntakeInput.from(message, normalized, today, previousTurn, orgScope);
        int previousTurnResultAnchorsCount = countResultAnchors(previousTurn != null ? previousTurn.getLastResultAnchors() : null);
        int intakePromptResultAnchorsCount = countResultAnchors(intakeInput.getResultAnchors());
        SemanticIntakeResult semanticIntake = semanticIntakeParser.parse(intakeInput);

        boolean intakeClarificationRequired = isIntakeClarificationRequired(semanticIntake);
        SemanticDomainRouteResult semanticDomainRoute = null;
        if (intakeClarificationRequired
                && SemanticIntakeStructuredFollowUpDeferralSupport.shouldDeferIntakeClarificationToV2(
                        semanticIntake, previousTurn)) {
            intakeClarificationRequired = false;
            semanticDomainRoute =
                    SemanticIntakeStructuredFollowUpDeferralSupport.buildDeferredRouteFromPreviousTurn(
                            previousTurn);
        }
        if (!intakeClarificationRequired && semanticDomainRoute == null) {
            semanticDomainRoute = SemanticIntakeRouteAdapter.toRouteResult(semanticIntake);
            if (semanticDomainRoute == null || semanticDomainRoute.isNeedsClarification()) {
                intakeClarificationRequired = true;
            }
        }
        boolean intakeRewriteApplied =
                !intakeClarificationRequired
                        && semanticIntake != null
                        && semanticIntake.getStatus() == SemanticIntakeStatus.READY
                        && semanticIntake.getNormalizationType() == SemanticIntakeNormalizationType.REWRITE;
        String effectiveUserMessage = resolveEffectiveUserMessage(normalized, semanticIntake);
        // v2 实体抽取以本轮原文为准；intake REWRITE 可能误继承上一轮菜名，不得覆盖 currentUserMessage。
        String querySemanticV2UserMessage =
                intakeRewriteApplied ? normalized : effectiveUserMessage;
        AiConversationTurnMemory previousTurnForParser =
                intakeRewriteApplied
                        ? SemanticParserInputBuilder.reducePreviousTurnForFollowUpRewrite(previousTurn)
                        : previousTurn;

        BareRankingDimensionSwitchPlan bareRankingDimensionSwitchPlan =
                BareRankingDimensionSwitchSupport.buildPlan(
                        intakeInput, semanticIntake, previousTurn);
        semanticDomainRoute =
                BareRankingDimensionSwitchSupport.routeForPlan(
                        bareRankingDimensionSwitchPlan, semanticDomainRoute);

        Map<String, Object> querySemanticV2InputPreview = null;
        AiQuerySemanticParseResult querySemanticV2Raw = null;
        DomainContractSelectionResult domainContractSelection = null;
        SemanticContractValidationDebug semanticContractValidation = null;
        SemanticContractStrictDecision semanticContractStrictDecision = null;
        if (!intakeClarificationRequired) {
            domainContractSelection = DomainContractSelector.select(semanticDomainRoute);
            domainContractSelection =
                    WarehouseInventoryShortageSemanticsSupport.filterContractSelection(
                            domainContractSelection, semanticIntake);
            domainContractSelection =
                    BareRankingDimensionSwitchSupport.contractSelectionForPlan(
                            bareRankingDimensionSwitchPlan, domainContractSelection);
            try {
                var v2In =
                        SemanticParserInputBuilder.build(
                                querySemanticV2UserMessage,
                                today,
                                previousTurnForParser,
                                orgScope,
                                semanticDomainRoute,
                                domainContractSelection,
                                previousTurn);
                querySemanticV2InputPreview = SemanticParserInputBuilder.toDebugPreview(v2In);
                querySemanticV2Raw = querySemanticLlmParser.parse(v2In);
            } catch (Exception ex) {
                log.debug(
                        "[AiResolvedQueryContextResolver] querySemanticV2 parse failed: {}",
                        ex.toString());
                querySemanticV2Raw =
                        AiQuerySemanticParseResult.builder()
                                .parseMissing(true)
                                .observationJsonParseError(
                                        "resolver_v2_exception:" + ex.getClass().getSimpleName())
                                .build();
            }
        }

        AiResolvedTimeWindow explicitTentative = null;

        String semanticPrimaryVersion = "v2";
        Boolean semanticFallbackUsed = Boolean.FALSE;
        String semanticFallbackReason = null;
        String semanticAdoptedFrom = null;
        List<String> semanticAdoptedFields = null;
        List<String> semanticAdoptionRejectedFields = null;
        String semanticAdoptionRejectedReason = null;
        String semanticMetricNormalizedFrom = null;
        String semanticMetricNormalizedTo = null;
        Map<String, Object> semanticV2AbstractIntentNormalizationNotes = null;

        SemanticAdoptionAttempt adoption = null;
        boolean timeContractFailed = false;
        boolean frameClarificationRequired = false;
        boolean contractStrictClarificationRequired = false;
        com.nongxinle.ai.semantic.SemanticTimeContractCheck.Result timeContractResult = null;
        String semanticFailureCode = null;
        String semanticFailureStage = null;
        boolean semanticInfrastructureFailure = false;

        if (intakeClarificationRequired) {
            semanticFailureCode = SemanticLlmFailureClassification.classifyIntakeFailure(semanticIntake);
            if (SemanticLlmFailureClassification.isInfrastructureFailure(semanticFailureCode)) {
                semanticInfrastructureFailure = true;
                semanticFailureStage = SemanticLlmFailureClassification.STAGE_SEMANTIC_INTAKE;
                semanticFallbackReason = semanticFailureCode;
            } else {
                semanticFallbackReason = intakeClarificationReason(semanticIntake);
            }
        } else {
            String v2FailureCode =
                    SemanticLlmFailureClassification.classifyV2ParseFailure(querySemanticV2Raw);
            if (v2FailureCode != null
                    && SemanticLlmFailureClassification.isInfrastructureFailure(v2FailureCode)) {
                semanticInfrastructureFailure = true;
                semanticFailureCode = v2FailureCode;
                semanticFailureStage = SemanticLlmFailureClassification.STAGE_SEMANTIC_V2;
                semanticFallbackReason = v2FailureCode;
            } else {
            AiQuerySemanticParseResult v2ForAdoption =
                    querySemanticV2Raw != null && !querySemanticV2Raw.isParseMissing()
                            ? querySemanticV2Raw
                            : null;
            String rewriteInheritedAnchorType = null;
            String rewriteInheritedAnchorName = null;
            boolean bareRankingDimensionSwitch = bareRankingDimensionSwitchPlan.isActive();
            if (intakeRewriteApplied
                    && previousTurnForParser != null
                    && v2ForAdoption != null
                    && !bareRankingDimensionSwitch
                    && !SemanticIntakeMultiDishRankingSupport.suppressRewriteAnchorInjection(
                            semanticIntake)
                    && SemanticContractAnchorInheritanceSupport.isUsePreviousAnchorPolicy(
                            v2ForAdoption)) {
                String structuredDish =
                        SemanticContractAnchorInheritanceSupport.resolveStructuredDishAnchor(
                                previousTurnForParser, null, null);
                if (StringUtils.hasText(structuredDish)) {
                    rewriteInheritedAnchorType = AiResultAnchor.ENTITY_TYPE_DISH;
                    rewriteInheritedAnchorName = structuredDish;
                }
            }
            adoption =
                    semanticAdoptionPipeline.tryAdopt(
                            new SemanticAdoptionPipeline.Request(
                                    v2ForAdoption,
                                    previousTurn,
                                    normalized,
                                    today,
                                    explicitTentative,
                                    intakeRewriteApplied,
                                    domainContractSelection,
                                    semanticIntake,
                                    bareRankingDimensionSwitchPlan,
                                    rewriteInheritedAnchorType,
                                    rewriteInheritedAnchorName,
                                    TimeLayerContextSignals.fromIntake(semanticIntake)));
            SemanticContractValidationPipeline.Result contractPipeline =
                    SemanticContractValidationPipeline.run(
                            new SemanticContractValidationPipeline.Request(
                                    false,
                                    domainContractSelection,
                                    adoption,
                                    querySemanticV2Raw,
                                    previousTurn,
                                    intakeRewriteApplied,
                                    semanticDomainRoute,
                                    semanticContractStrictProperties.isEnabled()));
            semanticContractValidation = contractPipeline.semanticContractValidation();
            semanticContractStrictDecision = contractPipeline.semanticContractStrictDecision();
            timeContractFailed =
                    adoption != null
                            && adoption.timeContract() != null
                            && !adoption.timeContract().valid();
            frameClarificationRequired =
                    adoption != null && adoption.frameClarificationRequired();
            contractStrictClarificationRequired =
                    semanticContractStrictDecision != null
                            && semanticContractStrictDecision.isEnforceClarification();
            timeContractResult = adoption != null ? adoption.timeContract() : null;

            if (adoption != null && StringUtils.hasText(adoption.rejectionReason())) {
                semanticAdoptionRejectedReason = adoption.rejectionReason();
            }

            if (adoption != null && adoption.adopted()) {
                semanticAdoptedFrom = "v2";
                semanticAdoptedFields =
                        AiResolvedQueryContextDebugFactory.describeAdoptedSemanticFields(
                                adoption.semantic());
            } else if (timeContractFailed) {
                semanticFallbackReason =
                        "time_contract:"
                                + AiResolvedQueryContextDebugFactory.blankToNullSemantic(
                                        adoption.timeContract().failureReason());
            } else if (adoption != null && adoption.contractSelectionClarificationRequired()) {
                semanticFallbackReason =
                        "contract_selection:"
                                + adoption.contractViolationCode().name();
            } else if (frameClarificationRequired) {
                semanticFallbackReason =
                        "frame_validation:"
                                + AiResolvedQueryContextDebugFactory.blankToNullSemantic(
                                        adoption.rejectionReason());
            } else if (contractStrictClarificationRequired) {
                semanticFallbackReason =
                        "model_contract_violation:"
                                + (semanticContractStrictDecision.getViolationCode() != null
                                        ? semanticContractStrictDecision.getViolationCode().name()
                                        : "UNKNOWN");
            } else if (adoption != null
                    && StringUtils.hasText(adoption.rejectionReason())) {
                semanticFallbackReason = adoption.rejectionReason();
            } else {
                semanticFallbackReason =
                        diagnostics.explainV2NonAdoption(
                                querySemanticV2Raw, querySemanticMinConfidence);
            }

            if (adoption != null
                    && adoption.semantic() != null
                    && Boolean.TRUE.equals(adoption.semantic().getPurchaseSemanticFramePrimaryMerge())) {
                semanticFallbackUsed = Boolean.FALSE;
            }
            }
        }

        boolean adoptionPathUnresolved =
                adoption != null
                        && (adoption.mergedIntent() == null
                                || !StringUtils.hasText(adoption.mergedIntent().getPathCode()))
                        && !adoption.frameClarificationRequired()
                        && !adoption.contractSelectionClarificationRequired();

        boolean clarificationRequired =
                intakeClarificationRequired
                        || adoption == null
                        || adoptionPathUnresolved
                        || timeContractFailed
                        || frameClarificationRequired
                        || contractStrictClarificationRequired;

        AiQuerySemanticParseResult semanticLlm =
                intakeClarificationRequired
                        ? null
                        : (adoption != null ? adoption.semantic() : querySemanticV2Raw);

        AiResolvedQueryIntent mergedIntentStem =
                adoption != null
                                && !timeContractFailed
                                && adoption.mergedIntent() != null
                                && StringUtils.hasText(adoption.mergedIntent().getPathCode())
                        ? adoption.mergedIntent()
                        : AiResolvedQueryIntent.builder().build();
        AiResolvedTimeWindow tentativeTimeMerged =
                adoption != null && !timeContractFailed ? adoption.tentativeTime() : explicitTentative;

        AiResolvedTimeWindow tentativeTime =
                clarificationRequired ? explicitTentative : tentativeTimeMerged;

        boolean applyStructuralLlm =
                !clarificationRequired
                        && semanticLlm != null
                        && !semanticLlm.isParseMissing()
                        && semanticLlm.isStructuralConfidenceOk(querySemanticMinConfidence)
                        && semanticLlm.isUsableForMerge(querySemanticMinConfidence);

        String semanticClarificationQuestion =
                semanticInfrastructureFailure
                        ? SemanticLlmFailureClassification.userMessageForFailureCode(
                                semanticFailureCode)
                        : intakeClarificationRequired
                        ? resolveIntakeClarificationQuestion(semanticIntake)
                        : contractStrictClarificationRequired
                                ? AiResolvedQueryContextDebugFactory.blankToNullSemantic(
                                        semanticContractStrictDecision.getClarificationQuestion())
                                : timeContractFailed
                                ? adoption.timeContract().clarificationQuestion()
                                : frameClarificationRequired
                                        ? adoption.semanticClarificationQuestion().trim()
                                        : clarificationRequired
                                                ? AiResolvedQueryContextDiagnostics.resolveSemanticClarificationQuestion(semanticLlm)
                                                : null;

        AiFollowUpResolution followUp =
                clarificationRequired
                        ? AiFollowUpResolver.clarificationFailureResolution(
                                orgScope, tentativeTime, normalized)
                        : AiFollowUpResolver.semanticStructuralBypassResolution(
                                previousTurn,
                                mergedIntentStem,
                                tentativeTime,
                                orgScope,
                                message,
                                semanticLlm,
                                intakeRewriteApplied);

        if (!clarificationRequired && RequestExplicitGroupScopeSupport.isExplicitGroupScopeRequest(request)) {
            followUp.setInheritOrgScope(false);
            followUp.setMergedOrgScope(orgScope);
            followUp.setEffectiveScopeSource(RequestExplicitGroupScopeSupport.EFFECTIVE_SCOPE_SOURCE_REQUEST);
        }

        AiResolvedQueryIntent queryIntent = followUp.getMergedQueryIntent() != null
                ? followUp.getMergedQueryIntent()
                : AiResolvedQueryIntent.builder().build();

        AiResolvedTimeWindow timeWindow;
        String effectiveTimeSource;
        if (clarificationRequired) {
            timeWindow = explicitTentative;
            effectiveTimeSource = "UNRESOLVED";
        } else if (timeContractResult != null && timeContractResult.valid()) {
            AiQuerySemanticParseResult.TimePart tp =
                    semanticLlm != null ? semanticLlm.getTime() : null;
            timeWindow =
                    timeContractResult.toTimeWindow(tp != null ? tp.getTimeType() : null);
            effectiveTimeSource = timeContractResult.normalizedTimeSource();
        } else {
            timeWindow = explicitTentative;
            effectiveTimeSource = "UNRESOLVED";
            diagnostics.logTimeContractMissingOnNonClarificationPath(runId, convId);
        }
        if (followUp != null) {
            followUp.setMergedTimeWindow(timeWindow);
            followUp.setEffectiveTimeWindowSource(effectiveTimeSource);
        }
        AiResolvedQueryContext assembled =
                assemblySupport.assemble(
                new AiResolvedQueryContextAssemblySupport.AssembleRequest(
                        runId,
                        convId,
                        uid,
                        userContext,
                        effectiveDept,
                        request,
                        message,
                        normalized,
                        effectiveUserMessage,
                        intakeRewriteApplied,
                        previousTurn,
                        orgScope,
                        semanticIntake,
                        querySemanticV2Raw,
                        explicitTentative,
                        querySemanticMinConfidence,
                        semanticPrimaryVersion,
                        semanticFallbackUsed,
                        semanticFallbackReason,
                        semanticAdoptedFrom,
                        semanticAdoptedFields,
                        semanticAdoptionRejectedFields,
                        semanticAdoptionRejectedReason,
                        semanticMetricNormalizedFrom,
                        semanticMetricNormalizedTo,
                        semanticV2AbstractIntentNormalizationNotes,
                        querySemanticV2InputPreview,
                        semanticDomainRoute,
                        domainContractSelection,
                        semanticContractValidation,
                        semanticContractStrictDecision,
                        previousTurnResultAnchorsCount,
                        intakePromptResultAnchorsCount,
                        adoption,
                        timeContractResult,
                        clarificationRequired,
                        semanticClarificationQuestion,
                        followUp,
                        queryIntent,
                        timeWindow,
                        effectiveTimeSource,
                        mergedIntentStem,
                        semanticLlm,
                        applyStructuralLlm,
                        semanticFailureCode,
                        semanticFailureStage,
                        scopeTrace,
                        bareRankingDimensionSwitchPlan));
        assembled.setConversationScopeMode(effectiveScopeMode);
        if (assembled.getScopeResolutionTrace() != null && semanticLlm != null) {
            assembled.getScopeResolutionTrace().setRawScopeAction(semanticLlm.getScopeAction());
        }
        return assembled;
    }

    private static boolean isIntakeClarificationRequired(SemanticIntakeResult intake) {
        if (intake == null) {
            return true;
        }
        if (intake.getStatus() == SemanticIntakeStatus.INVALID) {
            return true;
        }
        if (intake.getStatus() == SemanticIntakeStatus.NEED_CLARIFICATION) {
            return true;
        }
        if (Boolean.TRUE.equals(intake.getNeedClarification())) {
            return true;
        }
        if (intake.getQuestionMode() == SemanticIntakeQuestionMode.MULTI_QUESTION) {
            return true;
        }
        return intake.getStatus() != SemanticIntakeStatus.READY;
    }

    private static String resolveEffectiveUserMessage(String normalized, SemanticIntakeResult intake) {
        if (intake != null
                && intake.getStatus() == SemanticIntakeStatus.READY
                && StringUtils.hasText(intake.getCanonicalUserQuery())) {
            return intake.getCanonicalUserQuery().trim();
        }
        if (intake != null
                && intake.getStatus() == SemanticIntakeStatus.NEED_CLARIFICATION
                && StringUtils.hasText(intake.getCanonicalUserQuery())) {
            return intake.getCanonicalUserQuery().trim();
        }
        return normalized;
    }

    private static String resolveIntakeClarificationQuestion(SemanticIntakeResult intake) {
        if (intake != null) {
            String warehouseRisk =
                    WarehouseInventoryShortageSemanticsSupport.resolveClarificationQuestion(
                            intake);
            if (StringUtils.hasText(warehouseRisk)) {
                return warehouseRisk;
            }
            if (StringUtils.hasText(intake.getClarificationQuestion())) {
                return intake.getClarificationQuestion().trim();
            }
        }
        if (intake != null && intake.getStatus() == SemanticIntakeStatus.INVALID) {
            String code = SemanticLlmFailureClassification.classifyIntakeFailure(intake);
            String infrastructureMessage =
                    SemanticLlmFailureClassification.userMessageForFailureCode(code);
            if (infrastructureMessage != null) {
                return infrastructureMessage;
            }
        }
        return "能再具体说一下您想问的内容吗？";
    }

    private static String intakeClarificationReason(SemanticIntakeResult intake) {
        if (intake == null) {
            return "semantic_intake_null";
        }
        if (intake.getStatus() == SemanticIntakeStatus.INVALID) {
            return "semantic_intake_invalid:"
                    + AiResolvedQueryContextDebugFactory.blankToNullSemantic(intake.getParseError());
        }
        if (intake.getQuestionMode() == SemanticIntakeQuestionMode.MULTI_QUESTION) {
            return "semantic_intake_multi_question";
        }
        return "semantic_intake_clarification:"
                + AiResolvedQueryContextDebugFactory.blankToNullSemantic(intake.getReason());
    }

    public static Long mergedDistributerId(AiRunCreateRequest request, AiUserContext ctx) {
        return AiResolvedOrgScopeAssembler.mergedDistributerId(request, ctx);
    }

    public void patchResolvedQueryContextAfterRunIntersect(AiRunState state) {
        orgScopeAssembler.patchResolvedQueryContextAfterRunIntersect(state);
    }

    public Optional<AiPermissionDenied> maybeDenialForSemanticMentionsOutsideVisibleStores(AiResolvedQueryContext rq) {
        return permissionMentionPolicy.maybeDenialForSemanticMentionsOutsideVisibleStores(rq);
    }

    private static int countResultAnchors(List<AiResultAnchor> anchors) {
        if (anchors == null || anchors.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (AiResultAnchor a : anchors) {
            if (a != null && StringUtils.hasText(a.getEntityName())) {
                n++;
            }
        }
        return n;
    }
}
