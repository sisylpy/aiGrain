package com.nongxinle.ai.resolver;

import com.nongxinle.ai.context.AiDepartmentScopeDTO;
import com.nongxinle.ai.context.AiResolvedDataScope;
import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiSemanticStoreNarrowingDiagnostics;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.context.AiUserContext;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.conversation.AiConversationMemoryService;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiFollowUpResolution;
import com.nongxinle.ai.conversation.AiFollowUpResolver;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.agent.business.BusinessAgentNames;
import com.nongxinle.ai.followup.AiFollowUpHintSupport;
import com.nongxinle.ai.followup.FollowUpPathKind;
import com.nongxinle.ai.harness.AiMultiStoreHarnessTrace;
import com.nongxinle.ai.security.AiAnswerBoundary;
import com.nongxinle.ai.security.AiPermissionDenied;
import com.nongxinle.ai.platform.dto.AiRunCreateRequest;
import com.nongxinle.ai.scope.AiConversationScopeMode;
import com.nongxinle.ai.scope.AiQueryScope;
import com.nongxinle.ai.scope.AiScopeResolver;
import com.nongxinle.ai.semantic.AiQuerySemanticLlmMergeHelper;
import com.nongxinle.ai.semantic.AiQuerySemanticLlmParser;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResultDebugSerializer;
import com.nongxinle.ai.semantic.AiQuerySemanticV2BusinessHolisticIntentNormalizer;
import com.nongxinle.ai.semantic.AiQuerySemanticV2CompareStoreNormalizer;
import com.nongxinle.ai.semantic.AiQuerySemanticV2DishProfitGate;
import com.nongxinle.ai.semantic.AiQuerySemanticV2StockReducePurchaseDeconflictNormalizer;
import com.nongxinle.ai.semantic.SemanticParseFallbackPolicy;
import com.nongxinle.ai.semantic.SemanticParserInputBuilder;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.nongxinle.ai.util.AiUserMessageSanitizer;
import com.nongxinle.entity.GbDepartmentEntity;
import com.nongxinle.mapper.GbDepartmentMapper;
import com.nongxinle.utils.GbConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 统一解析入口：装配 {@link AiResolvedQueryContext}（唯一新业务上下文入口）；规则解析 + 组织树下钻口径由本类集中处理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiResolvedQueryContextResolver {

    private final GbDepartmentMapper gbDepartmentMapper;
    private final AiScopeResolver scopeResolver;
    private final AiConversationMemoryService conversationMemoryService;
    private final AiQuerySemanticLlmParser querySemanticLlmParser;

    @Value("${ai.agent.querySemanticLlm.minConfidence:0.55}")
    private double querySemanticMinConfidence;

    @Value("${ai.agent.querySemanticLlm.enabled:true}")
    private boolean querySemanticLlmEnabled;

    /**
     * v2 已采纳时仍调用 v1 仅用于 Harness 对照（多一次 LLM）；默认关闭。
     */
    @Value("${ai.agent.querySemanticLlm.v1DebugWhenV2Adopted:false}")
    private boolean querySemanticV1DebugWhenV2Adopted;

    private record SemanticAdoption(
            AiQuerySemanticParseResult semantic,
            AiResolvedQueryIntent mergedIntent,
            AiResolvedTimeWindow tentativeTime) {}

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
        Objects.requireNonNull(today, "today");
        Objects.requireNonNull(userContext, "userContext");
        String message = request != null ? request.getMessage() : null;
        String normalized = message == null ? "" : AiUserMessageSanitizer.stripLeadingEnumeration(message).trim();
        Long reqDept = request != null ? request.getDepartmentId() : null;
        Long effectiveDept = reqDept != null ? reqDept : userContext.getDepartmentId();

        Long uid = userContext.getUserId() != null ? userContext.getUserId()
                : (request != null ? request.getUserId() : null);
        Long convId = request != null ? request.getConversationId() : null;
        AiConversationTurnMemory previousTurn =
                uid != null ? conversationMemoryService.load(uid, convId) : null;
        if (log.isInfoEnabled()) {
            log.info(
                    "[AiFollowUpContext] resolve start runId={} conversationId={} userId={} messageSnippet={} "
                            + "previousTurnLoaded={} prevPathCode={}",
                    runId,
                    convId,
                    uid,
                    normalized.length() > 80 ? normalized.substring(0, 80) + "…" : normalized,
                    previousTurn != null,
                    previousTurn != null ? previousTurn.getLastPathCode() : null);
        }

        AiResolvedOrgScope orgScope = resolveOrgScope(userContext, effectiveDept, request);

        Map<String, Object> querySemanticV2InputPreview = null;
        AiQuerySemanticParseResult querySemanticV2Raw = null;
        AiQuerySemanticParseResult querySemanticV1Raw = null;
        if (querySemanticLlmEnabled) {
            try {
                var v2In = SemanticParserInputBuilder.build(normalized, today, previousTurn, orgScope);
                querySemanticV2InputPreview = SemanticParserInputBuilder.toDebugPreview(v2In);
                querySemanticV2Raw = querySemanticLlmParser.parse(v2In);
            } catch (Exception ex) {
                log.debug(
                        "[AiResolvedQueryContextResolver] querySemanticV2 parse failed: {}", ex.toString());
                querySemanticV2Raw = AiQuerySemanticParseResult.builder()
                        .parseMissing(true)
                        .observationJsonParseError(
                                "resolver_v2_exception:" + ex.getClass().getSimpleName())
                        .build();
            }
        }

        /* 显式时间仅来自语义 LLM / 多轮合并（{@link AiResolvedTimeWindow#fromSemanticTimeType}），不再对用户话术做关键词解析。 */
        AiResolvedTimeWindow explicitTentative = null;

        String semanticPrimaryVersion = querySemanticLlmEnabled ? "v2" : null;
        Boolean semanticFallbackUsed = Boolean.FALSE;
        String semanticFallbackReason = null;
        String semanticAdoptedFrom = null;
        List<String> semanticAdoptedFields = null;
        List<String> semanticAdoptionRejectedFields = null;
        String semanticAdoptionRejectedReason = null;
        String semanticMetricNormalizedFrom = null;
        String semanticMetricNormalizedTo = null;
        Map<String, Object> semanticV2AbstractIntentNormalizationNotes = null;
        Map<String, Object> querySemanticV1Map = null;

        SemanticAdoption adoption = null;
        if (querySemanticLlmEnabled) {
            AiQuerySemanticParseResult v2Pipeline = querySemanticV2Raw;
            if (v2Pipeline != null && !v2Pipeline.isParseMissing()) {
                AiQuerySemanticV2StockReducePurchaseDeconflictNormalizer.Result sr =
                        AiQuerySemanticV2StockReducePurchaseDeconflictNormalizer.apply(v2Pipeline);
                v2Pipeline = sr.semantic();
                if (sr.notes() != null) {
                    semanticV2AbstractIntentNormalizationNotes = new LinkedHashMap<>(sr.notes());
                }
            }
            if (v2Pipeline != null && !v2Pipeline.isParseMissing()) {
                AiQuerySemanticV2CompareStoreNormalizer.Result cmp =
                        AiQuerySemanticV2CompareStoreNormalizer.apply(v2Pipeline);
                v2Pipeline = cmp.semantic();
                if (cmp.notes() != null) {
                    if (semanticV2AbstractIntentNormalizationNotes == null) {
                        semanticV2AbstractIntentNormalizationNotes = new LinkedHashMap<>();
                    }
                    semanticV2AbstractIntentNormalizationNotes.putAll(cmp.notes());
                }
            }
            if (v2Pipeline != null && !v2Pipeline.isParseMissing()) {
                AiQuerySemanticV2BusinessHolisticIntentNormalizer.Result bh =
                        AiQuerySemanticV2BusinessHolisticIntentNormalizer.apply(v2Pipeline);
                v2Pipeline = bh.semantic();
                if (bh.notes() != null) {
                    if (semanticV2AbstractIntentNormalizationNotes == null) {
                        semanticV2AbstractIntentNormalizationNotes = new LinkedHashMap<>();
                    }
                    semanticV2AbstractIntentNormalizationNotes.putAll(bh.notes());
                }
            }
            AiQuerySemanticParseResult v2ForAdoption = v2Pipeline;
            AiQuerySemanticV2DishProfitGate.SanitizeResult v2Sanitize = null;
            if (v2Pipeline != null && !v2Pipeline.isParseMissing()) {
                v2Sanitize = AiQuerySemanticV2DishProfitGate.sanitize(v2Pipeline);
                if (!v2Sanitize.adoptable()) {
                    v2ForAdoption = null;
                    semanticAdoptionRejectedFields = v2Sanitize.semanticAdoptionRejectedFields() == null
                            ? null
                            : new ArrayList<>(v2Sanitize.semanticAdoptionRejectedFields());
                    semanticAdoptionRejectedReason = blankToNullSemantic(v2Sanitize.semanticAdoptionRejectedReason());
                } else {
                    v2ForAdoption = v2Sanitize.semantic();
                    if (StringUtils.hasText(v2Sanitize.normalizedMetricFrom())) {
                        semanticMetricNormalizedFrom = v2Sanitize.normalizedMetricFrom();
                        semanticMetricNormalizedTo = v2Sanitize.normalizedMetricTo();
                    }
                }
            }
            if (v2ForAdoption != null && !v2ForAdoption.isParseMissing()) {
                v2ForAdoption = augmentV2SemanticWithInheritedHarnessMultiStores(v2ForAdoption, previousTurn);
            }
            adoption = trySemanticAdoption(v2ForAdoption, previousTurn, normalized, today, explicitTentative);
            if (adoption != null) {
                semanticAdoptedFrom = "v2";
                semanticAdoptedFields = describeAdoptedSemanticFields(adoption.semantic());
                if (querySemanticV1DebugWhenV2Adopted) {
                    querySemanticV1Raw = querySemanticLlmParser.parseUserQuestion(normalized);
                    querySemanticV1Map = safeSemanticMap(querySemanticV1Raw);
                }
            } else {
                if (StringUtils.hasText(semanticAdoptionRejectedReason)) {
                    semanticFallbackReason = semanticAdoptionRejectedReason;
                } else {
                    semanticFallbackReason = explainV2NonAdoption(querySemanticV2Raw);
                }
                querySemanticV1Raw = querySemanticLlmParser.parseUserQuestion(normalized);
                querySemanticV1Map = safeSemanticMap(querySemanticV1Raw);
                adoption = trySemanticAdoption(querySemanticV1Raw, previousTurn, normalized, today, explicitTentative);
                if (adoption != null) {
                    semanticAdoptedFrom = "v1";
                    semanticFallbackUsed = Boolean.TRUE;
                    semanticAdoptedFields = describeAdoptedSemanticFields(adoption.semantic());
                }
            }
        } else {
            querySemanticV1Raw = querySemanticLlmParser.parseUserQuestion(normalized);
            querySemanticV1Map = safeSemanticMap(querySemanticV1Raw);
            adoption = trySemanticAdoption(querySemanticV1Raw, previousTurn, normalized, today, explicitTentative);
            if (adoption != null) {
                semanticAdoptedFrom = "v1";
                semanticPrimaryVersion = "v1";
                semanticAdoptedFields = describeAdoptedSemanticFields(adoption.semantic());
            }
        }

        AiQuerySemanticParseResult semanticLlm =
                adoption != null ? adoption.semantic() : preferReadableSemantic(querySemanticV2Raw, querySemanticV1Raw);

        AiResolvedQueryIntent mergedIntentStem =
                adoption != null
                        ? adoption.mergedIntent()
                        : AiResolvedQueryIntent.builder().build();
        AiResolvedTimeWindow tentativeTimeMerged =
                adoption != null ? adoption.tentativeTime() : explicitTentative;

        boolean clarificationRequired = adoption == null;

        AiResolvedTimeWindow tentativeTime =
                clarificationRequired ? explicitTentative : tentativeTimeMerged;

        boolean applyStructuralLlm =
                !clarificationRequired
                        && semanticLlm != null
                        && !semanticLlm.isParseMissing()
                        && semanticLlm.isStructuralConfidenceOk(querySemanticMinConfidence)
                        && semanticLlm.isUsableForMerge(querySemanticMinConfidence);

        String semanticClarificationQuestion =
                clarificationRequired ? SemanticParseFallbackPolicy.clarificationQuestion() : null;

        AiFollowUpResolution followUp =
                clarificationRequired
                        ? AiFollowUpResolver.clarificationFailureResolution(
                                orgScope, tentativeTime, normalized)
                        : AiFollowUpResolver.semanticStructuralBypassResolution(
                                previousTurn, mergedIntentStem, tentativeTime, orgScope, message, semanticLlm);

        AiResolvedQueryIntent queryIntent = followUp.getMergedQueryIntent() != null
                ? followUp.getMergedQueryIntent()
                : AiResolvedQueryIntent.builder().build();
        AiResolvedTimeWindow rawTw =
                followUp.getMergedTimeWindow() != null ? followUp.getMergedTimeWindow() : tentativeTime;

        AiResolvedTimeWindow timeWindow;
        String effectiveTimeSource;
        if (clarificationRequired) {
            timeWindow = explicitTentative;
            effectiveTimeSource =
                    timeWindow != null
                            ? AiMultiTurnTimeWindowPolicy.resolveEffectiveTimeWindowSource(explicitTentative, timeWindow)
                            : "UNRESOLVED";
        } else {
            timeWindow =
                    AiMultiTurnTimeWindowPolicy.finalizeTimeWindow(rawTw, explicitTentative, previousTurn, today);
            effectiveTimeSource =
                    AiMultiTurnTimeWindowPolicy.resolveEffectiveTimeWindowSource(explicitTentative, timeWindow);
        }
        if (followUp != null) {
            followUp.setMergedTimeWindow(timeWindow);
            followUp.setEffectiveTimeWindowSource(effectiveTimeSource);
        }
        AiResolvedOrgScope mergedOrg = followUp != null && followUp.getMergedOrgScope() != null
                ? followUp.getMergedOrgScope()
                : orgScope;

        if (!clarificationRequired) {
            String dishReasonScopeProbe =
                    applyStructuralLlm && semanticLlm != null
                            && org.springframework.util.StringUtils.hasText(semanticLlm.getMentionedDishName())
                            ? semanticLlm.getMentionedDishName().trim()
                            : null;
            var orgOutcome = AiMultiTurnOrgScopePolicy.applyInheritedEffectiveOrgScope(
                    mergedOrg,
                    previousTurn,
                    normalized,
                    dishReasonScopeProbe,
                    mergedIntentStem != null ? mergedIntentStem.getStructuredIntentDetail() : null,
                    semanticLlm);
            mergedOrg = orgOutcome.org();
            if (orgOutcome.inheritedFromPreviousTurn()) {
                followUp.setEffectiveScopeSource("INHERITED_PREVIOUS");
            } else if (followUp != null && followUp.isInheritOrgScope()
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
        multiStoreHarness.ingestDetectionCandidate(normalized, semanticLlm, mergedOrg);
        AiSemanticStoreNarrowingDiagnostics storeScopeNarrowDiag = AiSemanticStoreNarrowingDiagnostics.empty();
        if (!clarificationRequired) {
            AiResolvedOrgScope beforeSemanticStore = mergedOrg;
            mergedOrg =
                    narrowGroupOrgBySemanticLlmStoreIfNeeded(
                            mergedOrg,
                            semanticLlm,
                            request,
                            applyStructuralLlm,
                            querySemanticMinConfidence,
                            normalized,
                            multiStoreHarness,
                            storeScopeNarrowDiag);
            if (mergedOrg != beforeSemanticStore) {
                followUp.setEffectiveScopeSource("CURRENT_MESSAGE_EXPLICIT_STORE");
            }
        }

        if (!clarificationRequired && mergedOrg != beforeStoreNarrowingPass && log.isInfoEnabled()) {
            log.info(
                    "[AiResolvedQueryContext] explicitStoreMentionNarrowing runId={} conversationId={} "
                            + "beforeScopeType={} afterScopeType={} afterVisibleStoreIds={}",
                    runId,
                    convId,
                    beforeStoreNarrowingPass != null ? beforeStoreNarrowingPass.getScopeType() : null,
                    mergedOrg != null ? mergedOrg.getScopeType() : null,
                    mergedOrg != null && mergedOrg.getVisibleStores() != null
                            ? mergedOrg.getVisibleStores().stream()
                                    .map(AiStoreScopeDTO::getStoreDepartmentId)
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.toList())
                            : null);
        }

        if (!clarificationRequired) {
            normalizeStockReduceStructuredRouting(queryIntent);
            normalizePurchaseStructuredRouting(queryIntent);
            upgradePurchaseSupplierDimensionFromResolverSignals(queryIntent, semanticLlm, normalized);
            normalizeRevenueIntentRouting(queryIntent);
            alignFollowUpEffectiveRoutingWithQueryIntent(followUp, queryIntent);
            repairInheritedIntentPathAfterBroadScopeFollowUpLeak(
                    normalized,
                    previousTurn,
                    followUp,
                    queryIntent,
                    semanticLlm,
                    applyStructuralLlm,
                    querySemanticMinConfidence);
            stabilizeDishProfitFollowUpStructuredIntent(normalized, followUp, previousTurn, queryIntent);
            if (upgradePurchaseStoreRankingAfterRevenueFollowUp(queryIntent, mergedOrg, previousTurn, followUp)) {
                if (semanticV2AbstractIntentNormalizationNotes != null) {
                    semanticV2AbstractIntentNormalizationNotes.remove("degradedToPurchaseOverview");
                    semanticV2AbstractIntentNormalizationNotes.remove("mentionedStoreCount");
                }
                followUp.setPurchaseStructuredIntent(queryIntent.getStructuredIntentDetail());
            }
        }

        AiResolvedDataScope dataScope = buildDataScope(mergedOrg);

        String normQuestion = normalized;
        if (followUp.isNormalizedInputExpandedAtResolvePhase()
                && followUp.getExpandedNormalizedQuestion() != null
                && !followUp.getExpandedNormalizedQuestion().isBlank()) {
            normQuestion =
                    AiUserMessageSanitizer.stripLeadingEnumeration(followUp.getExpandedNormalizedQuestion()).trim();
        }

        String banner = mergedOrg != null ? mergedOrg.getQueryScopeBanner() : null;
        String timeLabel = timeWindow != null ? timeWindow.getDisplayText() : null;
        String answerBoundaryNote = buildCombinedBoundaryNote(
                effectiveTimeSource, followUp != null ? followUp.getEffectiveScopeSource() : null,
                timeWindow, mergedOrg, previousTurn);

        String mentionedDishName =
                clarificationRequired
                        ? null
                        : resolveMentionedDishName(
                                queryIntent,
                                previousTurn,
                                mergedOrg,
                                followUp,
                                semanticLlm);
        String dishProfitMetricType =
                AiQuerySemanticLexicon.dishProfitMetricTypeFromStructuredWire(
                        queryIntent != null ? queryIntent.getStructuredIntentDetail() : null);

        boolean singleStoreNarrowingBlocked =
                multiStoreHarness.resolveSingleStoreNarrowingBlocked(normalized, mergedOrg, semanticLlm);
        boolean subsetApplied = multiStoreHarness.isSubsetApplied();
        boolean inheritedMultiStoreRanking =
                !clarificationRequired
                        && !subsetApplied
                        && followUp != null
                        && "INHERITED_PREVIOUS".equals(followUp.getEffectiveScopeSource())
                        && semanticLlm != null
                        && !semanticLlm.isParseMissing()
                        && "INHERIT_PREVIOUS".equals(normalizeSemanticV2ActionToken(semanticLlm.getScopeAction()))
                        && previousTurn != null
                        && previousTurn.getLastVisibleStoreIds() != null
                        && previousTurn.getLastVisibleStoreIds().size() >= 2
                        && mergedOrg != null
                        && mergedOrg.getVisibleStores() != null
                        && queryIntent != null
                        && isHarnessMultiStoreAmountRankingWire(queryIntent.getStructuredIntentDetail())
                        && mergedOrg.getVisibleStores().stream()
                                        .filter(s -> s != null && s.getStoreDepartmentId() != null)
                                        .count()
                                >= 2;
        boolean harnessMultiStoreScopeDetected =
                multiStoreHarness.isDetected() || inheritedMultiStoreRanking;
        boolean harnessMultiStoreScopeApplied = subsetApplied || inheritedMultiStoreRanking;
        String harnessMultiStoreScopeSource =
                subsetApplied ? "SEMANTIC_SUBSET" : (inheritedMultiStoreRanking ? "INHERITED_PREVIOUS" : null);
        List<String> harnessMatchedNames = multiStoreHarness.copyMatchedStores();
        if (inheritedMultiStoreRanking && harnessMatchedNames.isEmpty() && mergedOrg != null) {
            List<String> fromOrg = visibleStoreNamesForHarness(mergedOrg);
            if (!fromOrg.isEmpty()) {
                harnessMatchedNames = new ArrayList<>(fromOrg);
            }
        }

        AiQuerySemanticParseResult orchSource =
                querySemanticV2Raw != null && !querySemanticV2Raw.isParseMissing()
                        ? querySemanticV2Raw
                        : semanticLlm;
        AiQuerySemanticParseResult.OrchestrationDecisionCandidatePart odPart =
                orchSource != null ? orchSource.getOrchestrationDecisionCandidate() : null;
        boolean businessOverviewEffectiveRouting =
                AiResolvedQueryIntent.BUSINESS_OVERVIEW.equals(followUp.getEffectiveIntentCode())
                        && AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW.equals(followUp.getEffectivePathCode());
        List<String> orchestrationSelectedAgents =
                odPart != null && odPart.getSelectedAgents() != null && !odPart.getSelectedAgents().isEmpty()
                        ? new ArrayList<>(odPart.getSelectedAgents())
                        : null;
        List<String> orchestrationSelectedTools =
                odPart != null && odPart.getSelectedTools() != null && !odPart.getSelectedTools().isEmpty()
                        ? new ArrayList<>(odPart.getSelectedTools())
                        : null;
        String orchestrationTaskMode =
                odPart != null ? blankToNullSemantic(odPart.getTaskMode()) : null;
        Boolean orchestrationPlannerRequired = odPart != null ? odPart.getPlannerRequired() : null;
        Boolean orchestrationMultiAgentRequired =
                odPart != null ? odPart.getMultiAgentRequired() : null;
        Boolean orchestrationApprovalRequired = odPart != null ? odPart.getApprovalRequired() : null;
        Boolean orchestrationClarificationRequiredFlag =
                odPart != null ? odPart.getClarificationRequired() : null;
        String orchestrationClarificationQuestionField =
                odPart != null ? blankToNullSemantic(odPart.getClarificationQuestion()) : null;
        Double orchestrationConfidenceField = odPart != null ? odPart.getConfidence() : null;
        String orchestrationReasonField =
                odPart != null ? blankToNullSemantic(odPart.getReason()) : null;
        if (!clarificationRequired
                && followUp != null
                && AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS.equals(followUp.getEffectivePathCode())) {
            String tmEff = orchestrationTaskMode != null ? orchestrationTaskMode.trim() : "";
            boolean needHarnessMultiGate =
                    !StringUtils.hasText(tmEff) || !"MULTI_AGENT".equalsIgnoreCase(tmEff);
            if (needHarnessMultiGate || Boolean.FALSE.equals(orchestrationMultiAgentRequired)) {
                orchestrationTaskMode = "MULTI_AGENT";
                orchestrationMultiAgentRequired = true;
                orchestrationPlannerRequired = false;
            }
        }
        if (!clarificationRequired
                && followUp != null
                && AiResolvedQueryIntent.BUSINESS_OVERVIEW.equals(followUp.getEffectiveIntentCode())
                && AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW.equals(followUp.getEffectivePathCode())
                && queryIntent != null
                && AiQuerySemanticLexicon.isStructuredBusinessOverviewFourDomainOrchestrationSurface(
                        queryIntent.getStructuredIntentDetail())) {
            String tmOv = orchestrationTaskMode != null ? orchestrationTaskMode.trim() : "";
            boolean needOverviewMultiOrchestration =
                    !StringUtils.hasText(tmOv) || !"MULTI_AGENT".equalsIgnoreCase(tmOv);
            if (needOverviewMultiOrchestration || Boolean.FALSE.equals(orchestrationMultiAgentRequired)) {
                orchestrationTaskMode = "MULTI_AGENT";
                orchestrationMultiAgentRequired = true;
                orchestrationPlannerRequired = false;
            }
        }
        if (!clarificationRequired
                && followUp != null
                && followUp.isFollowUp()
                && previousTurn != null
                && queryIntent != null
                && orchSource != null
                && !orchSource.isParseMissing()
                && AiResolvedQueryIntent.BUSINESS_OVERVIEW.equals(followUp.getEffectiveIntentCode())
                && AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW.equals(followUp.getEffectivePathCode())
                && AiQuerySemanticLexicon.isStructuredBusinessOverviewFourDomainOrchestrationSurface(
                        queryIntent.getStructuredIntentDetail())
                && AiQuerySemanticLexicon.isStructuredBusinessOverviewFourDomainOrchestrationSurface(
                        previousTurn.getLastStructuredIntentDetail())) {
            String tmRest = orchestrationTaskMode != null ? orchestrationTaskMode.trim() : "";
            boolean multiRest = StringUtils.hasText(tmRest) && "MULTI_AGENT".equalsIgnoreCase(tmRest);
            if (multiRest
                    && shouldCanonicalizeOrchestrationForSemanticTimeFollowUpBizOverview(orchSource, previousTurn)) {
                orchestrationSelectedAgents =
                        new ArrayList<>(canonicalBusinessOverviewMultiAgentAgents());
                orchestrationSelectedTools =
                        new ArrayList<>(AiBusinessToolIds.BUSINESS_OVERVIEW_MULTI_AGENT_DOMAIN_TOOLS);
            }
        }
        if (!clarificationRequired && businessOverviewEffectiveRouting && odPart != null) {
            if (Boolean.TRUE.equals(orchestrationClarificationRequiredFlag)) {
                clarificationRequired = true;
                semanticClarificationQuestion =
                        StringUtils.hasText(orchestrationClarificationQuestionField)
                                ? orchestrationClarificationQuestionField
                                : SemanticParseFallbackPolicy.clarificationQuestion();
            } else if (Boolean.TRUE.equals(orchestrationApprovalRequired)) {
                clarificationRequired = true;
                semanticClarificationQuestion =
                        StringUtils.hasText(orchestrationReasonField)
                                ? "该操作需要确认：" + orchestrationReasonField.trim()
                                : "该操作需要确认后才能继续。";
            }
        }

        boolean storeLexicalAmbiguity = storeScopeNarrowDiag.isAmbiguousLexicalMatch();
        if (storeLexicalAmbiguity) {
            clarificationRequired = true;
            semanticClarificationQuestion = buildStoreLexicalAmbiguityQuestion(storeScopeNarrowDiag);
        }
        boolean narrowingFailedExplicitWithoutAmbiguity =
                storeScopeNarrowDiag.isNarrowingAttemptedSemanticExplicitStore()
                        && !storeScopeNarrowDiag.isNarrowedSuccessfully()
                        && !storeLexicalAmbiguity
                        && StringUtils.hasText(storeScopeNarrowDiag.getNarrowingFailureReason());
        if (narrowingFailedExplicitWithoutAmbiguity && followUp != null) {
            followUp.setEffectiveScopeSource("STORE_SCOPE_SEMANTIC_UNRESOLVED");
        }
        String resolvedMatchedSemanticStoreMention =
                storeScopeNarrowDiag.isNarrowedSuccessfully()
                                && StringUtils.hasText(storeScopeNarrowDiag.getMatchedSemanticStoreMention())
                        ? storeScopeNarrowDiag.getMatchedSemanticStoreMention().trim()
                        : null;

        AiResolvedQueryContext built = AiResolvedQueryContext.builder()
                .runId(runId)
                .userId(uid)
                .userContext(userContext)
                .orgScope(mergedOrg)
                .timeWindow(timeWindow)
                .queryIntent(queryIntent)
                .dataScope(dataScope)
                .followUp(followUp.isFollowUp())
                .originalQuestion(message)
                .normalizedQuestion(normQuestion)
                .queryScopeBanner(banner)
                .timeWindowLabel(timeLabel)
                .answerBoundaryNote(answerBoundaryNote)
                .previousTurn(previousTurn)
                .followUpResolution(followUp)
                .effectiveIntentCode(followUp.getEffectiveIntentCode())
                .effectivePathCode(followUp.getEffectivePathCode())
                .effectiveTimeWindowSource(effectiveTimeSource)
                .effectiveScopeSource(followUp.getEffectiveScopeSource())
                .effectiveIntentSource(followUp.getEffectiveIntentSource())
                .mentionedDishName(mentionedDishName)
                .dishProfitMetricType(dishProfitMetricType)
                .querySemanticParse(semanticLlm)
                .semanticPromptRegistryId(
                        semanticLlm != null ? semanticLlm.getPromptRegistryId() : null)
                .harnessMultiStoreScopeDetected(harnessMultiStoreScopeDetected)
                .harnessMultiStoreScopeApplied(harnessMultiStoreScopeApplied)
                .harnessMultiStoreScopeSource(blankToNullSemantic(harnessMultiStoreScopeSource))
                .harnessMultiStoreMatchedStores(
                        harnessMatchedNames.isEmpty() ? null : new ArrayList<>(harnessMatchedNames))
                .harnessSingleStoreNarrowingBlocked(singleStoreNarrowingBlocked)
                .needSemanticClarification(clarificationRequired)
                .semanticClarificationQuestion(semanticClarificationQuestion)
                .semanticStoreNarrowingDebug(storeScopeNarrowDiag)
                .resolvedMatchedSemanticStoreMention(blankToNullSemantic(resolvedMatchedSemanticStoreMention))
                .semanticPrimaryVersion(blankToNullSemantic(semanticPrimaryVersion))
                .semanticFallbackUsed(querySemanticLlmEnabled ? semanticFallbackUsed : null)
                .semanticFallbackReason(blankToNullSemantic(semanticFallbackReason))
                .semanticAdoptedFrom(blankToNullSemantic(semanticAdoptedFrom))
                .semanticAdoptedFields(
                        semanticAdoptedFields == null || semanticAdoptedFields.isEmpty()
                                ? null
                                : new ArrayList<>(semanticAdoptedFields))
                .semanticAdoptionRejectedFields(
                        semanticAdoptionRejectedFields == null || semanticAdoptionRejectedFields.isEmpty()
                                ? null
                                : new ArrayList<>(semanticAdoptionRejectedFields))
                .semanticAdoptionRejectedReason(blankToNullSemantic(semanticAdoptionRejectedReason))
                .semanticMetricNormalizedFrom(blankToNullSemantic(semanticMetricNormalizedFrom))
                .semanticMetricNormalizedTo(blankToNullSemantic(semanticMetricNormalizedTo))
                .semanticV2AbstractIntentNormalizationNotes(
                        semanticV2AbstractIntentNormalizationNotes == null
                                || semanticV2AbstractIntentNormalizationNotes.isEmpty()
                                ? null
                                : new LinkedHashMap<>(semanticV2AbstractIntentNormalizationNotes))
                .querySemanticV1(querySemanticV1Map)
                .querySemanticV2InputPreview(querySemanticV2InputPreview)
                .querySemanticV2(
                        querySemanticV2Raw == null
                                ? null
                                : AiQuerySemanticParseResultDebugSerializer.toSafeMap(querySemanticV2Raw))
                .querySemanticV2ParseMissing(
                        querySemanticV2Raw == null ? null : querySemanticV2Raw.isParseMissing())
                .querySemanticV2Confidence(
                        querySemanticV2Raw == null ? null : querySemanticV2Raw.getConfidence())
                .querySemanticV2TimeAction(
                        querySemanticV2Raw == null
                                ? null
                                : blankToNullSemantic(
                                        AiQuerySemanticLlmMergeHelper.canonicalQuerySemanticV2TimeActionForHarness(
                                                querySemanticV2Raw,
                                                previousTurn,
                                                querySemanticMinConfidence,
                                                normalized)))
                .querySemanticV2ScopeAction(
                        querySemanticV2Raw == null
                                ? null
                                : blankToNullSemantic(querySemanticV2Raw.getScopeAction()))
                .querySemanticV2IntentAction(
                        querySemanticV2Raw == null
                                ? null
                                : blankToNullSemantic(querySemanticV2Raw.getIntentAction()))
                .querySemanticV2MetricAction(
                        querySemanticV2Raw == null
                                ? null
                                : blankToNullSemantic(querySemanticV2Raw.getMetricAction()))
                .querySemanticV2MentionedStoreNames(
                        querySemanticV2EffectiveStoreNames(querySemanticV2Raw))
                .querySemanticV2MentionedDishName(
                        querySemanticV2Raw == null
                                ? null
                                : blankToNullSemantic(querySemanticV2Raw.getMentionedDishName()))
                .querySemanticV2RawText(
                        querySemanticV2Raw == null
                                ? null
                                : blankToNullSemantic(querySemanticV2Raw.getObservationLlmRawText()))
                .querySemanticV2ParseError(
                        querySemanticV2Raw == null
                                ? null
                                : blankToNullSemantic(querySemanticV2Raw.getObservationJsonParseError()))
                .orchestrationTaskMode(orchestrationTaskMode)
                .orchestrationSelectedAgents(orchestrationSelectedAgents)
                .orchestrationSelectedTools(orchestrationSelectedTools)
                .orchestrationPlannerRequired(orchestrationPlannerRequired)
                .orchestrationMultiAgentRequired(orchestrationMultiAgentRequired)
                .orchestrationApprovalRequired(orchestrationApprovalRequired)
                .orchestrationClarificationRequired(orchestrationClarificationRequiredFlag)
                .orchestrationClarificationQuestion(orchestrationClarificationQuestionField)
                .orchestrationConfidence(orchestrationConfidenceField)
                .orchestrationReason(orchestrationReasonField)
                .build();
        logIntentResolutionDiagnostics(runId, convId, message, previousTurn, mergedIntentStem, followUp, built);
        logFollowUpDiagnostics(runId, convId, previousTurn, followUp, built);
        logResolvedContextPipeline(
                runId,
                convId,
                message,
                previousTurn,
                orgScope,
                mergedIntentStem,
                explicitTentative != null,
                followUp,
                built);
        return built;
    }

    private void logIntentResolutionDiagnostics(
            Long runId,
            Long conversationId,
            String rawMessage,
            AiConversationTurnMemory previousTurn,
            AiResolvedQueryIntent currentIntentProbe,
            AiFollowUpResolution followUp,
            AiResolvedQueryContext ctx) {
        if (!log.isInfoEnabled()) {
            return;
        }
        var fur = followUp;
        var probe = currentIntentProbe != null ? currentIntentProbe : AiResolvedQueryIntent.builder().build();
        log.info(
                "[AiFollowUpContext] intentRouting runId={} conversationId={} rawMessageSnippet={} "
                        + "currentIntentCode={} currentPathCode={} previousTurn.intentCode={} previousTurn.pathCode={} "
                        + "followUp={} inheritIntent={} effectiveIntentCode={} effectivePathCode={} effectiveIntentSource={}",
                runId,
                conversationId,
                rawMessage == null ? "" : (rawMessage.length() > 120 ? rawMessage.substring(0, 120) + "…" : rawMessage),
                probe != null ? probe.getIntentCode() : null,
                probe != null ? probe.getPathCode() : null,
                previousTurn != null ? previousTurn.getLastIntentCode() : null,
                previousTurn != null ? previousTurn.getLastPathCode() : null,
                fur != null && fur.isFollowUp(),
                fur != null && fur.isInheritIntent(),
                ctx.getEffectiveIntentCode(),
                ctx.getEffectivePathCode(),
                ctx.getEffectiveIntentSource());
    }

    private void logFollowUpDiagnostics(
            Long runId,
            Long conversationId,
            AiConversationTurnMemory previousTurn,
            AiFollowUpResolution followUp,
            AiResolvedQueryContext ctx) {
        if (!log.isInfoEnabled()) {
            return;
        }
        var tw = ctx.getTimeWindow();
        var fur = followUp;
        var ds = ctx.getDataScope();
        boolean storeFu = fur != null && "STORE_SCOPE_FOLLOW_UP".equals(fur.getFollowUpType());
        log.info(
                "[AiFollowUpContext] runId={} conversationId={} previousTurnPresent={} prevIntentCode={} prevPathCode={} "
                        + "prevTimeWindow={}..{} prevTimeLabel={} "
                        + "followUp={} followUpType={} inheritIntent={} inheritTimeWindow={} inheritOrgScope={} "
                        + "timeLabel={} startDate={} endDate={} "
                        + "effectiveIntentCode={} effectivePathCode={} effectiveTimeWindowSource={} effectiveScopeSource={} "
                        + "effectiveIntentSource={}",
                runId,
                conversationId,
                previousTurn != null,
                previousTurn != null ? previousTurn.getLastIntentCode() : null,
                previousTurn != null ? previousTurn.getLastPathCode() : null,
                previousTurn != null ? previousTurn.getLastStartDate() : null,
                previousTurn != null ? previousTurn.getLastEndDate() : null,
                previousTurn != null ? previousTurn.getLastTimeLabel() : null,
                fur != null && fur.isFollowUp(),
                fur != null ? fur.getFollowUpType() : null,
                fur != null && fur.isInheritIntent(),
                fur != null && fur.isInheritTimeWindow(),
                fur != null && fur.isInheritOrgScope(),
                tw != null ? tw.getTimeLabel() : null,
                tw != null ? tw.getStartDate() : null,
                tw != null ? tw.getEndDate() : null,
                ctx.getEffectiveIntentCode(),
                ctx.getEffectivePathCode(),
                ctx.getEffectiveTimeWindowSource(),
                ctx.getEffectiveScopeSource(),
                ctx.getEffectiveIntentSource());
        if (storeFu && fur != null) {
            log.info(
                    "[AiFollowUpContext] STORE_SCOPE_FOLLOW_UP runId={} conversationId={} "
                            + "currentMentionedStoreName={} matchedStoreDepartmentId={} "
                            + "inheritIntent=true inheritPath=true inheritTimeWindow=true overrideScope=true "
                            + "effectiveScopeSource={} expandedSqlDepartmentIds={}",
                    runId,
                    conversationId,
                    fur.getStoreScopeFollowUpMentionedName(),
                    fur.getStoreScopeFollowUpMatchedStoreRootId(),
                    ctx.getEffectiveScopeSource(),
                    ds != null ? ds.getEffectiveSqlDepartmentIds() : null);
        }
    }

    private static String buildCombinedBoundaryNote(
            String effectiveTimeWindowSource,
            String effectiveScopeSource,
            AiResolvedTimeWindow tw,
            AiResolvedOrgScope org,
            AiConversationTurnMemory previousTurn) {
        boolean timeInh = "INHERITED_PREVIOUS".equals(effectiveTimeWindowSource);
        boolean scopeInh = "INHERITED_PREVIOUS".equals(effectiveScopeSource);
        List<String> hints = new ArrayList<>();
        if (scopeInh) {
            AiMultiTurnOrgScopePolicy.singleVisibleStoreName(org).ifPresent(hints::add);
        }
        if (timeInh && tw != null) {
            hints.add(AiMultiTurnTimeWindowPolicy.humanReadableTimeCarryover(tw));
        }
        if (!hints.isEmpty()) {
            return "按上文「" + String.join(" + ", hints) + "」口径查询；本句未指定新的时间和门店。若需调整请直接说明。";
        }
        return AiMultiTurnTimeWindowPolicy.buildAnswerBoundaryNote(
                effectiveTimeWindowSource, tw, previousTurn);
    }

    private static boolean semanticDeclaresStoreFocusForLogging(
            AiQuerySemanticParseResult sem, AiResolvedOrgScope groupLikeOrg) {
        if (sem == null || sem.isParseMissing() || groupLikeOrg == null
                || !AiResolvedOrgScope.SCOPE_GROUP.equals(groupLikeOrg.getScopeType())) {
            return false;
        }
        if (!sem.effectiveMentionedStoreNames().isEmpty()) {
            return true;
        }
        AiQuerySemanticParseResult.RequestedScopePart rs = sem.getRequestedScope();
        return rs != null && StringUtils.hasText(rs.getMentionedStoreName());
    }

    private void logResolvedContextPipeline(
            Long runId,
            Long conversationId,
            String rawMessage,
            AiConversationTurnMemory previousTurn,
            AiResolvedOrgScope permissionBaselineOrg,
            AiResolvedQueryIntent currentKeywordIntent,
            boolean currentExplicitTimeMentioned,
            AiFollowUpResolution followUp,
            AiResolvedQueryContext ctx) {
        if (!log.isInfoEnabled()) {
            return;
        }
        var cur = currentKeywordIntent;
        var tw = ctx.getTimeWindow();
        var effOrg = ctx.getOrgScope();
        var qi = ctx.getQueryIntent();
        String prevTw = null;
        String prevStores = null;
        if (previousTurn != null) {
            prevTw = (previousTurn.getLastStartDate() != null ? previousTurn.getLastStartDate() : "")
                    + ".."
                    + (previousTurn.getLastEndDate() != null ? previousTurn.getLastEndDate() : "")
                    + "|label="
                    + previousTurn.getLastTimeLabel();
            if (previousTurn.getLastVisibleStoreIds() != null) {
                prevStores = previousTurn.getLastVisibleStoreIds().toString();
            }
        }
        String effStores = effOrg != null && effOrg.getVisibleStores() != null
                ? effOrg.getVisibleStores().stream()
                .filter(Objects::nonNull)
                .map(s -> s.getStoreDepartmentId() + ":" + (s.getStoreName() != null ? s.getStoreName() : ""))
                .collect(Collectors.joining(","))
                : null;
        boolean currentExplicitStore =
                semanticDeclaresStoreFocusForLogging(ctx.getQuerySemanticParse(), permissionBaselineOrg);
        String rm = rawMessage == null ? "" : rawMessage;
        if (rm.length() > 2000) {
            rm = rm.substring(0, 2000) + "…(truncated)";
        }
        log.info(
                "[AiResolvedContext] pipeline runId={} conversationId={} rawMessage={} "
                        + "previousIntentCode={} previousPathCode={} "
                        + "previousStructuredIntentDetail={} previousPurchaseSourceType={} "
                        + "previousScopeType={} previousVisibleStores={} "
                        + "previousTimeWindow={} "
                        + "currentIntentCode={} currentPathCode={} currentStructuredIntentDetail={} currentPurchaseSourceType={} "
                        + "currentExplicitTimeMentioned={} currentExplicitStoreMentioned={} "
                        + "currentDeclaresDomainPath={} "
                        + "effectiveIntentCode={} effectivePathCode={} "
                        + "effectiveTimeWindow={}..{} effectiveTimeLabel={} "
                        + "effectiveScopeType={} effectiveVisibleStores={} "
                        + "effectivePurchaseSourceType={} effectiveStructuredIntentDetail={} "
                        + "effectiveIntentSource={} effectiveTimeWindowSource={} effectiveScopeSource={} "
                        + "mentionedStore={} matchedStoreDepartmentId={}",
                runId,
                conversationId,
                rm,
                previousTurn != null ? previousTurn.getLastIntentCode() : null,
                previousTurn != null ? previousTurn.getLastPathCode() : null,
                previousTurn != null ? previousTurn.getLastStructuredIntentDetail() : null,
                previousTurn != null ? previousTurn.getLastPurchaseSourceType() : null,
                previousTurn != null ? previousTurn.getLastScopeType() : null,
                prevStores,
                prevTw,
                cur != null ? cur.getIntentCode() : null,
                cur != null ? cur.getPathCode() : null,
                cur != null ? cur.getStructuredIntentDetail() : null,
                cur != null ? cur.getPurchaseSourceType() : null,
                currentExplicitTimeMentioned,
                currentExplicitStore,
                AiFollowUpHintSupport.currentMessageDeclaresDomainPath(rawMessage),
                ctx.getEffectiveIntentCode(),
                ctx.getEffectivePathCode(),
                tw != null ? tw.getStartDate() : null,
                tw != null ? tw.getEndDate() : null,
                tw != null ? tw.getTimeLabel() : null,
                effOrg != null ? effOrg.getScopeType() : null,
                effStores,
                qi != null ? qi.getPurchaseSourceType() : null,
                qi != null ? qi.getStructuredIntentDetail() : null,
                ctx.getEffectiveIntentSource(),
                ctx.getEffectiveTimeWindowSource(),
                ctx.getEffectiveScopeSource(),
                followUp != null ? followUp.getStoreScopeFollowUpMentionedName() : null,
                followUp != null ? followUp.getStoreScopeFollowUpMatchedStoreRootId() : null);
    }

    private AiResolvedDataScope buildDataScope(AiResolvedOrgScope org) {
        if (org == null) {
            return AiResolvedDataScope.builder()
                    .queryScopeKind(AiResolvedDataScope.QUERY_SCOPE_KIND_STORE)
                    .queryScopeMode(AiResolvedDataScope.QUERY_SCOPE_EMPTY)
                    .queryStoreIds(new ArrayList<>())
                    .queryRealDepartmentIds(new ArrayList<>())
                    .expandedSqlDepartmentIds(new ArrayList<>())
                    .storeToDepartmentIds(new LinkedHashMap<>())
                    .build();
        }
        if (AiResolvedOrgScope.SCOPE_WAREHOUSE.equals(org.getScopeType())) {
            List<Long> whIds = org.getVisibleWarehouses() == null ? new ArrayList<>() : org.getVisibleWarehouses().stream()
                    .map(AiDepartmentScopeDTO::getDepartmentId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(ArrayList::new));
            boolean allWh = !whIds.isEmpty();
            List<Integer> whInt = new ArrayList<>();
            for (Long id : whIds) {
                if (id != null && id > 0 && id <= Integer.MAX_VALUE) {
                    whInt.add(id.intValue());
                }
            }
            return AiResolvedDataScope.builder()
                    .queryScopeKind(AiResolvedDataScope.QUERY_SCOPE_KIND_DEPARTMENT)
                    .queryRealDepartmentIds(new ArrayList<>(whInt))
                    .queryStoreIds(new ArrayList<>())
                    .queryDistributerId(null)
                    .storeToDepartmentIds(new LinkedHashMap<>())
                    .expandedSqlDepartmentIds(new ArrayList<>(whInt))
                    .visibleStoreIds(new ArrayList<>())
                    .storeRootDepartmentIds(new ArrayList<>())
                    .targetStoreIds(new ArrayList<>())
                    .explicitChildDepartmentIds(new ArrayList<>())
                    .expandedChildDepartmentIds(new ArrayList<>())
                    .visibleWarehouseIds(new ArrayList<>(whIds))
                    .targetWarehouseIds(whIds)
                    .targetDepartmentIds(new ArrayList<>(whIds))
                    .queryScopeMode(AiResolvedDataScope.QUERY_SCOPE_WAREHOUSE_DEPARTMENT)
                    .allVisibleStores(false)
                    .allVisibleWarehouses(allWh)
                    .build();
        }

        List<Long> storeRoots = org.getVisibleStores() == null ? new ArrayList<>() : org.getVisibleStores().stream()
                .map(AiStoreScopeDTO::getStoreDepartmentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));

        LinkedHashMap<Integer, List<Integer>> rootToChildrenInt = new LinkedHashMap<>();
        List<Long> expandedChildren = new ArrayList<>();
        List<Integer> expandedSqlInt = new ArrayList<>();
        List<Integer> storeRootInts = new ArrayList<>();

        for (Long root : storeRoots) {
            if (root == null || root <= 0 || root > Integer.MAX_VALUE) {
                continue;
            }
            int ri = root.intValue();
            storeRootInts.add(ri);
            expandedSqlInt.add(ri);
            List<Integer> childIdsInt = new ArrayList<>();
            List<GbDepartmentEntity> subs = gbDepartmentMapper.querySubDepartments(ri);
            if (subs != null) {
                for (GbDepartmentEntity sub : subs) {
                    if (sub != null && sub.getGbDepartmentId() != null) {
                        long sid = sub.getGbDepartmentId().longValue();
                        if (sid > 0 && sid <= Integer.MAX_VALUE) {
                            int si = (int) sid;
                            expandedSqlInt.add(si);
                            childIdsInt.add(si);
                            expandedChildren.add(sid);
                        }
                    }
                }
            }
            rootToChildrenInt.put(ri, childIdsInt);
        }

        if (storeRoots.isEmpty() && org.getDistributerId() != null) {
            long dis = org.getDistributerId();
            if (dis > 0 && dis <= Integer.MAX_VALUE) {
                return AiResolvedDataScope.builder()
                        .queryScopeKind(AiResolvedDataScope.QUERY_SCOPE_KIND_DISTRIBUTER)
                        .queryStoreIds(new ArrayList<>())
                        .queryRealDepartmentIds(new ArrayList<>())
                        .queryDistributerId((int) dis)
                        .storeToDepartmentIds(new LinkedHashMap<>())
                        .expandedSqlDepartmentIds(new ArrayList<>())
                        .visibleStoreIds(new ArrayList<>())
                        .storeRootDepartmentIds(new ArrayList<>())
                        .targetStoreIds(new ArrayList<>())
                        .explicitChildDepartmentIds(new ArrayList<>())
                        .expandedChildDepartmentIds(new ArrayList<>())
                        .visibleWarehouseIds(new ArrayList<>())
                        .targetWarehouseIds(new ArrayList<>())
                        .targetDepartmentIds(new ArrayList<>())
                        .queryScopeMode(AiResolvedDataScope.QUERY_SCOPE_EMPTY)
                        .allVisibleStores(false)
                        .allVisibleWarehouses(false)
                        .build();
            }
        }

        boolean allStores = AiResolvedOrgScope.SCOPE_GROUP.equals(org.getScopeType());
        List<Long> rootsCopy = new ArrayList<>(storeRoots);
        return AiResolvedDataScope.builder()
                .queryScopeKind(AiResolvedDataScope.QUERY_SCOPE_KIND_STORE)
                .queryStoreIds(new ArrayList<>(storeRootInts))
                .queryRealDepartmentIds(new ArrayList<>())
                .queryDistributerId(null)
                .storeToDepartmentIds(rootToChildrenInt)
                .expandedSqlDepartmentIds(new ArrayList<>(expandedSqlInt))
                .visibleStoreIds(new ArrayList<>(rootsCopy))
                .storeRootDepartmentIds(new ArrayList<>(rootsCopy))
                .targetStoreIds(new ArrayList<>(rootsCopy))
                .explicitChildDepartmentIds(new ArrayList<>())
                .expandedChildDepartmentIds(expandedChildren)
                .visibleWarehouseIds(new ArrayList<>())
                .targetWarehouseIds(new ArrayList<>())
                .targetDepartmentIds(new ArrayList<>())
                .queryScopeMode(AiResolvedDataScope.QUERY_SCOPE_MODE_STORE)
                .allVisibleStores(allStores)
                .allVisibleWarehouses(false)
                .build();
    }

    /**
     * 词典仅写出库结构化子意图、path 仍空时，补全 {@code stock_reduce_query_path}，避免追问仅带「损耗呢」时 effective* 断档。
     */
    private static void normalizeStockReduceStructuredRouting(AiResolvedQueryIntent qi) {
        if (qi == null) {
            return;
        }
        String sid = qi.getStructuredIntentDetail();
        boolean wants = AiQuerySemanticLexicon.isStructuredStockReduceDetail(sid);
        String path = qi.getPathCode();
        if (path != null && !path.isBlank()) {
            if (!AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY.equals(path)) {
                return;
            }
            if (!StringUtils.hasText(qi.getIntentCode())) {
                qi.setIntentCode(AiResolvedQueryIntent.STOCK_REDUCE_QUERY);
            }
            return;
        }
        if (!wants) {
            return;
        }
        qi.setPathCode(AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY);
        qi.setIntentCode(AiResolvedQueryIntent.STOCK_REDUCE_QUERY);
        if (!StringUtils.hasText(qi.getTopic())) {
            qi.setTopic("出库/核销查询");
        }
    }

    private static void normalizePurchaseStructuredRouting(AiResolvedQueryIntent qi) {
        if (qi == null) {
            return;
        }
        String sid = qi.getStructuredIntentDetail();
        boolean ranking = AiQuerySemanticLexicon.isSupplierAmountRankingDetail(sid);
        boolean needsPurchasePath = ranking
                || AiQuerySemanticLexicon.STRUCTURED_PURCHASE_STORE_AMOUNT_RANKING.equals(sid)
                || AiQuerySemanticLexicon.STRUCTURED_PURCHASE_OVERVIEW_SUMMARY.equals(sid)
                || AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_SUMMARY.equals(sid)
                || AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_AMOUNT_QUERY.equals(sid)
                || AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY.equals(sid);
        if (!needsPurchasePath && (qi.getPurchaseSourceType() == null || qi.getPurchaseSourceType().isBlank())) {
            return;
        }
        if (qi.getPathCode() != null && !qi.getPathCode().isBlank()) {
            return;
        }
        qi.setPathCode(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW);
        qi.setIntentCode(AiResolvedQueryIntent.PURCHASE_OVERVIEW);
        if (ranking) {
            qi.setTopic("采购概览（供货商排行）");
        } else if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_STORE_AMOUNT_RANKING.equals(sid)) {
            qi.setTopic("采购概览（并排门店金额）");
        } else if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_AMOUNT_QUERY.equals(sid)) {
            qi.setTopic("采购概览（来源金额）");
        } else if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY.equals(sid)) {
            qi.setTopic("采购概览（来源商品）");
        } else if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_OVERVIEW_SUMMARY.equals(sid)) {
            qi.setTopic("采购概览");
        } else {
            qi.setTopic("采购概览（来源聚焦）");
        }
    }

    /**
     * 采购专线已落在 {@link AiResolvedQueryIntent#PATH_PURCHASE_OVERVIEW}，但结构化子口径仍为泛化 summary、且
     * 语义 JSON / 归一化话术已明确供货商维度时，补齐 wire 与 {@code purchaseSourceType}，以便
     * {@link com.nongxinle.ai.agent.business.MasterBusinessAgent} 路由到 {@link com.nongxinle.ai.agent.business.SupplierAnalysisAgent}。
     * <p>
     * 不放行：自采固化、≥2 店并排采购（由 merge 层写门店对比 wire）、已为供货商/商品子口径者。
     */
    private static void upgradePurchaseSupplierDimensionFromResolverSignals(
            AiResolvedQueryIntent qi,
            AiQuerySemanticParseResult semanticLlm,
            String normalizedUserMessage) {
        if (qi == null) {
            return;
        }
        if (!AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW.equals(qi.getPathCode())) {
            return;
        }
        if (StringUtils.hasText(qi.getIntentCode())
                && !AiResolvedQueryIntent.PURCHASE_OVERVIEW.equals(qi.getIntentCode())) {
            return;
        }
        String selfWire = AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE;
        String qiPst = qi.getPurchaseSourceType();
        if (StringUtils.hasText(qiPst) && selfWire.equals(qiPst.trim().toUpperCase(Locale.ROOT).replace('-', '_'))) {
            return;
        }
        if (semanticLlm != null && semanticLlm.getMetric() != null) {
            String mPst = semanticLlm.getMetric().getPurchaseSourceType();
            if (StringUtils.hasText(mPst) && selfWire.equals(mPst.trim().toUpperCase(Locale.ROOT).replace('-', '_'))) {
                return;
            }
        }
        PurchaseSupplierTextSignals txt = parsePurchaseSupplierTextSignals(normalizedUserMessage);
        boolean forceSupplierWireFromExplicitUserText =
                txt.supplierChannel() && (txt.rankingish() || txt.situational());

        String canonSid =
                StringUtils.hasText(qi.getStructuredIntentDetail())
                        ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(qi.getStructuredIntentDetail().trim())
                        : null;
        if (StringUtils.hasText(canonSid) && AiQuerySemanticLexicon.isSupplierAmountRankingDetail(canonSid)) {
            return;
        }
        if (StringUtils.hasText(canonSid)
                && AiQuerySemanticLexicon.STRUCTURED_PURCHASE_STORE_AMOUNT_RANKING.equals(canonSid)) {
            if (!forceSupplierWireFromExplicitUserText) {
                return;
            }
        }
        if (StringUtils.hasText(canonSid) && AiQuerySemanticLexicon.isStructuredPurchaseGoodsFocusedDetail(canonSid)) {
            if (!forceSupplierWireFromExplicitUserText) {
                return;
            }
        }
        if (StringUtils.hasText(canonSid)
                && AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_AMOUNT_QUERY.equals(canonSid)) {
            if (!forceSupplierWireFromExplicitUserText) {
                return;
            }
        }
        if (StringUtils.hasText(canonSid)
                && AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY.equals(canonSid)) {
            boolean allowOverrideSourceGoods =
                    forceSupplierWireFromExplicitUserText
                            && !purchaseUserMessageMentionsGoodsDrilldown(normalizedUserMessage);
            if (!allowOverrideSourceGoods) {
                return;
            }
        }
        if (!forceSupplierWireFromExplicitUserText
                && !purchaseStructuredSidEligibleForSupplierOverviewUpgrade(canonSid)) {
            return;
        }
        if (semanticLlm != null && semanticLlm.effectiveMentionedStoreNames().size() >= 2) {
            return;
        }

        boolean signalMetricSupplierPst = metricPurchaseSourceSuggestsSupplier(semanticLlm);
        boolean signalMetricSupplierRanking = metricRankingTypeSuggestsSupplierAmountRanking(semanticLlm);
        boolean signalOrch = orchestrationSuggestsSupplierAgent(semanticLlm);

        if (!signalMetricSupplierPst
                && !signalMetricSupplierRanking
                && !signalOrch
                && !txt.supplierChannel()) {
            return;
        }

        boolean strongRanking =
                signalMetricSupplierRanking
                        || signalOrch
                        || txt.rankingish()
                        || (signalMetricSupplierPst && txt.rankingish());

        if (!strongRanking && !signalMetricSupplierPst && !txt.situational()) {
            return;
        }

        if (strongRanking) {
            qi.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_SUPPLIER_AMOUNT_RANKING);
            if (!StringUtils.hasText(qi.getTopic()) || "采购概览".equals(qi.getTopic())) {
                qi.setTopic("采购概览（供货商排行）");
            }
        } else {
            qi.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_AMOUNT_QUERY);
            if (!StringUtils.hasText(qi.getTopic()) || "采购概览".equals(qi.getTopic())) {
                qi.setTopic("采购概览（供货商维度）");
            }
        }
        qi.setPurchaseSourceType(AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE);
        if (!StringUtils.hasText(qi.getIntentCode())) {
            qi.setIntentCode(AiResolvedQueryIntent.PURCHASE_OVERVIEW);
        }
    }

    private record PurchaseSupplierTextSignals(boolean supplierChannel, boolean rankingish, boolean situational) {}

    private static PurchaseSupplierTextSignals parsePurchaseSupplierTextSignals(String normalizedUserMessage) {
        if (!StringUtils.hasText(normalizedUserMessage)) {
            return new PurchaseSupplierTextSignals(false, false, false);
        }
        String n = normalizedUserMessage.replace(" ", "").replace("\u3000", "");
        boolean textSupplierChannel =
                n.contains("供应商") || n.contains("供货商") || n.contains("供货方");
        boolean textRankingish = false;
        boolean textSituational = false;
        if (textSupplierChannel) {
            textRankingish =
                    n.contains("最高")
                            || n.contains("最多")
                            || n.contains("排行")
                            || n.contains("排名")
                            || n.contains("第一")
                            || n.contains("榜首")
                            || n.contains("哪一家")
                            || n.contains("哪家")
                            || n.contains("哪个")
                            || n.contains("谁");
            textSituational = n.contains("情况") || n.contains("分析");
        }
        return new PurchaseSupplierTextSignals(textSupplierChannel, textRankingish, textSituational);
    }

    /**
     * 用户明确要求「供应商供了哪些货 / 商品」时，应保留 {@link AiQuerySemanticLexicon#STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY}，
     * 不因出现「供应商」字样误升为金额排行。
     */
    private static boolean purchaseUserMessageMentionsGoodsDrilldown(String normalizedUserMessage) {
        if (!StringUtils.hasText(normalizedUserMessage)) {
            return false;
        }
        String n = normalizedUserMessage.replace(" ", "").replace("\u3000", "").toLowerCase(Locale.ROOT);
        return n.contains("商品")
                || n.contains("货品")
                || n.contains("单品")
                || n.contains("sku")
                || n.contains("哪些货")
                || n.contains("什么货");
    }

    private static boolean purchaseStructuredSidEligibleForSupplierOverviewUpgrade(String canonSid) {
        if (!StringUtils.hasText(canonSid)) {
            return true;
        }
        return AiQuerySemanticLexicon.STRUCTURED_PURCHASE_OVERVIEW_SUMMARY.equals(canonSid)
                || AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_SUMMARY.equals(canonSid);
    }

    private static boolean metricPurchaseSourceSuggestsSupplier(AiQuerySemanticParseResult sem) {
        if (sem == null || sem.getMetric() == null) {
            return false;
        }
        String pst = sem.getMetric().getPurchaseSourceType();
        if (!StringUtils.hasText(pst)) {
            return false;
        }
        String n = pst.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE.equals(n);
    }

    private static boolean metricRankingTypeSuggestsSupplierAmountRanking(AiQuerySemanticParseResult sem) {
        if (sem == null || sem.getMetric() == null) {
            return false;
        }
        String rt = sem.getMetric().getRankingType();
        if (!StringUtils.hasText(rt)) {
            return false;
        }
        return AiQuerySemanticLexicon.isSupplierAmountRankingDetail(rt);
    }

    private static boolean orchestrationSuggestsSupplierAgent(AiQuerySemanticParseResult sem) {
        if (sem == null || sem.getOrchestrationDecisionCandidate() == null) {
            return false;
        }
        List<String> agents = sem.getOrchestrationDecisionCandidate().getSelectedAgents();
        if (agents == null || agents.isEmpty()) {
            return false;
        }
        for (String raw : agents) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            String t = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
            if (BusinessAgentNames.SUPPLIER_ANALYSIS.equals(t)) {
                return true;
            }
            if (t.contains("supplier") && (t.contains("analysis") || t.contains("rank"))) {
                return true;
            }
        }
        return false;
    }

    /** path 已为营收专线但 intent 缺失时补齐，避免 Harness effectiveIntentCode 断档。 */
    private static void normalizeRevenueIntentRouting(AiResolvedQueryIntent qi) {
        if (qi == null) {
            return;
        }
        if (!AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW.equals(qi.getPathCode())) {
            return;
        }
        if (!StringUtils.hasText(qi.getIntentCode())) {
            qi.setIntentCode(AiResolvedQueryIntent.REVENUE_OVERVIEW);
        }
        if (!StringUtils.hasText(qi.getTopic())) {
            qi.setTopic("营业额/营收");
        }
    }

    /**
     * 承接「多店营业额对比」后的短追问（如只切换采购指标）：合并后 org 已继承上轮 2+ 店可见子集，
     * 但若 v2 仅给出 {@code purchase_overview_summary}（点名店名未再输出），在此处升成门店采购金额排行 wire。
     * 仅依赖 path / 结构化字段 / 上一 path / org 可见门店数，不解析用户原文。
     */
    private static boolean upgradePurchaseStoreRankingAfterRevenueFollowUp(
            AiResolvedQueryIntent qi,
            AiResolvedOrgScope org,
            AiConversationTurnMemory previousTurn,
            AiFollowUpResolution followUp) {
        if (qi == null || org == null || previousTurn == null || followUp == null) {
            return false;
        }
        if (!"SEMANTIC_STRUCTURAL_MERGE".equals(followUp.getFollowUpType())) {
            return false;
        }
        if (!AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW.equals(qi.getPathCode())) {
            return false;
        }
        if (!AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW.equals(previousTurn.getLastPathCode())) {
            return false;
        }
        if (org.getVisibleStores() == null) {
            return false;
        }
        long vis =
                org.getVisibleStores().stream()
                        .filter(s -> s != null && s.getStoreDepartmentId() != null)
                        .count();
        if (vis < 2) {
            return false;
        }
        String sidRaw = qi.getStructuredIntentDetail();
        if (AiQuerySemanticLexicon.isSupplierAmountRankingDetail(sidRaw)) {
            return false;
        }
        String canon =
                StringUtils.hasText(sidRaw)
                        ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(sidRaw.trim())
                        : null;
        String effective = StringUtils.hasText(canon) ? canon : (StringUtils.hasText(sidRaw) ? sidRaw.trim() : null);
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_STORE_AMOUNT_RANKING.equals(effective)) {
            return false;
        }
        if (effective != null
                && !AiQuerySemanticLexicon.STRUCTURED_PURCHASE_OVERVIEW_SUMMARY.equals(effective)) {
            return false;
        }
        qi.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_STORE_AMOUNT_RANKING);
        return true;
    }

    /**
     * {@link #normalizePurchaseStructuredRouting} 在本类后段补全 intent/path（如仅含 {@code purchaseSourceType}），
     * 而 {@link com.nongxinle.ai.conversation.AiFollowUpResolver} 内 {@code fillSources} 已写过一轮 {@code effective*}，
     * 必须把二者对齐，否则 Harness / Replay 会看到 effectiveIntentCode 为空。
     * <p>
     * 「全部门店呢」会先被误认为店名片语且无 DB 命中，此处按「大范围重置用语」兜底补全 intent/path/effective*；
     * 意图叠加上一轮记忆仅在语义 LLM {@code intentAction=INHERIT_PREVIOUS} 且达合并阈值时允许。
     */
    private void repairInheritedIntentPathAfterBroadScopeFollowUpLeak(
            String normalized,
            AiConversationTurnMemory previousTurn,
            AiFollowUpResolution followUp,
            AiResolvedQueryIntent queryIntent,
            AiQuerySemanticParseResult semanticLlm,
            boolean applyStructuralLlm,
            double querySemanticMinConfidence) {
        if (!StringUtils.hasText(normalized) || previousTurn == null || queryIntent == null || followUp == null) {
            return;
        }
        if (!applyStructuralLlm
                || semanticLlm == null
                || semanticLlm.isParseMissing()
                || !semanticLlm.isUsableForMerge(querySemanticMinConfidence)) {
            return;
        }
        if (!intentActionIsInheritPrevious(semanticLlm)) {
            return;
        }
        if (!StringUtils.hasText(previousTurn.getLastPathCode())) {
            return;
        }
        if (!AiMultiTurnOrgScopePolicy.messageDeclaresBroadGroupReset(normalized)) {
            return;
        }
        if (StringUtils.hasText(queryIntent.getPathCode()) && StringUtils.hasText(queryIntent.getIntentCode())) {
            return;
        }
        if (AiFollowUpHintSupport.currentMessageDeclaresDomainPath(normalized)) {
            return;
        }
        FollowUpPathKind lk = followUpPathKindFrom(previousTurn.getLastPathCode());
        if (lk == null || AiFollowUpHintSupport.pathTopicConflict(normalized, lk)) {
            return;
        }
        applyInheritedIntentOverlayFromMemory(previousTurn, queryIntent);
        normalizeStockReduceStructuredRouting(queryIntent);
        normalizePurchaseStructuredRouting(queryIntent);
        normalizeRevenueIntentRouting(queryIntent);
        alignFollowUpEffectiveRoutingWithQueryIntent(followUp, queryIntent);
        if (!followUp.isFollowUp()) {
            followUp.setFollowUp(true);
            followUp.setFollowUpType("GROUP_SCOPE_EXPAND_FOLLOW_UP");
            followUp.setInheritIntent(true);
            followUp.setEffectiveIntentSource("INHERITED_PREVIOUS");
            followUp.setEffectiveScopeSource("CURRENT_MESSAGE_GROUP_EXPAND");
        }
    }

    private static boolean intentActionIsInheritPrevious(AiQuerySemanticParseResult sem) {
        if (sem == null || !StringUtils.hasText(sem.getIntentAction())) {
            return false;
        }
        return "INHERIT_PREVIOUS".equals(
                sem.getIntentAction().trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    }

    private static void applyInheritedIntentOverlayFromMemory(
            AiConversationTurnMemory prev, AiResolvedQueryIntent qi) {
        if (prev == null || qi == null || !StringUtils.hasText(prev.getLastPathCode())) {
            return;
        }
        if (StringUtils.hasText(qi.getPathCode()) && StringUtils.hasText(qi.getIntentCode())) {
            return;
        }
        AiResolvedQueryIntent fromMem = AiFollowUpResolver.inheritIntentFromMemory(prev, "");
        if (!StringUtils.hasText(qi.getPathCode())) {
            qi.setPathCode(fromMem.getPathCode());
        }
        if (!StringUtils.hasText(qi.getIntentCode())) {
            qi.setIntentCode(fromMem.getIntentCode());
        }
        if (!StringUtils.hasText(qi.getStructuredIntentDetail())) {
            qi.setStructuredIntentDetail(fromMem.getStructuredIntentDetail());
        }
        if (!StringUtils.hasText(qi.getPurchaseSourceType())) {
            qi.setPurchaseSourceType(fromMem.getPurchaseSourceType());
        }
        qi.setInheritedFromPreviousTurn(true);
    }

    private static FollowUpPathKind followUpPathKindFrom(String pathCode) {
        if (!StringUtils.hasText(pathCode)) {
            return null;
        }
        return switch (pathCode) {
            case AiResolvedQueryIntent.PATH_DISH_PROFIT -> FollowUpPathKind.DISH_PROFIT;
            case AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW -> FollowUpPathKind.BUSINESS_OVERVIEW;
            case AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK -> FollowUpPathKind.WAREHOUSE_STOCK;
            case AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW -> FollowUpPathKind.PURCHASE_OVERVIEW;
            case AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW -> FollowUpPathKind.REVENUE_OVERVIEW;
            case AiResolvedQueryIntent.PATH_COST_DIAGNOSIS -> FollowUpPathKind.COST_INSIGHT;
            case AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY -> FollowUpPathKind.STOCK_REDUCE_QUERY;
            case AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS -> FollowUpPathKind.BUSINESS_DIAGNOSIS;
            default -> null;
        };
    }

    private static void alignFollowUpEffectiveRoutingWithQueryIntent(
            AiFollowUpResolution followUp, AiResolvedQueryIntent qi) {
        if (followUp == null || qi == null) {
            return;
        }
        if (StringUtils.hasText(qi.getIntentCode())) {
            followUp.setEffectiveIntentCode(qi.getIntentCode());
        }
        if (StringUtils.hasText(qi.getPathCode())) {
            followUp.setEffectivePathCode(qi.getPathCode());
        }
    }

    private AiResolvedOrgScope resolveOrgScope(AiUserContext ctx, Long requestDepartmentId, AiRunCreateRequest request) {
        Integer admin = ctx.getSourceAdminRole();
        if (admin == null) {
            return buildDepartmentLikeScope(ctx, requestDepartmentId, AiResolvedOrgScope.SCOPE_DEPARTMENT, request);
        }
        if (Objects.equals(admin, GbConstants.DepartmentUserRole.GROUP_MANAGER_APP)) {
            return buildGroupScope(ctx, requestDepartmentId, request);
        }
        if (Objects.equals(admin, GbConstants.DepartmentUserRole.STORE_MANAGER_APP)) {
            return buildStoreScope(ctx, requestDepartmentId, AiResolvedOrgScope.SCOPE_STORE, request);
        }
        if (Objects.equals(admin, GbConstants.DepartmentUserRole.STORE_PURCHASER_APP)) {
            return buildStoreScope(ctx, requestDepartmentId, AiResolvedOrgScope.SCOPE_PURCHASER, request);
        }
        if (Objects.equals(admin, GbConstants.DepartmentUserRole.WAREHOUSE_APP)) {
            return buildWarehouseScope(ctx, requestDepartmentId, request);
        }
        return buildDepartmentLikeScope(ctx, requestDepartmentId, AiResolvedOrgScope.SCOPE_DEPARTMENT, request);
    }

    /**
     * Run 请求体中的 {@code distributerId} 优先于用户表快照，避免集团账号挂靠部门与主体 ID 不一致时只展开一家门店。
     */
    public static Long mergedDistributerId(AiRunCreateRequest request, AiUserContext ctx) {
        if (request != null && request.getDistributerId() != null) {
            return request.getDistributerId();
        }
        return ctx != null ? ctx.getDistributerId() : null;
    }

    /**
     * {@link com.nongxinle.ai.graph.business.BusinessScopeIntersectNode} 收窄 Run 锚点后，同步
     * {@link AiResolvedQueryContext#getOrgScope()} / {@link AiResolvedQueryContext#getDataScope()}。
     */
    public void patchResolvedQueryContextAfterRunIntersect(AiRunState state) {
        if (state == null || state.getResolvedQueryContext() == null || state.getAiUserContext() == null) {
            return;
        }
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        AiResolvedOrgScope org = rq.getOrgScope();
        if (org == null) {
            return;
        }
        AiUserContext ctx = state.getAiUserContext();
        AiRunCreateRequest syn = new AiRunCreateRequest();
        syn.setDepartmentId(state.getDepartmentId());
        syn.setDistributerId(state.getDistributerId());

        Long mergedDis = mergedDistributerId(syn, ctx);
        Long prevDis = org.getDistributerId();

        org.setDistributerId(mergedDis);
        org.setRequestDepartmentId(state.getDepartmentId());

        String scopeType = org.getScopeType();

        if (AiResolvedOrgScope.SCOPE_GROUP.equals(scopeType)) {
            org.setCurrentDepartmentId(ctx.getDepartmentId());
            if (mergedDis != null && prevDis != null && !Objects.equals(prevDis, mergedDis)) {
                try {
                    int disPk = Math.toIntExact(mergedDis);
                    List<AiStoreScopeDTO> stores = loadStoreScopeDtosUnderDistributer(disPk);
                    org.setVisibleStores(stores);
                    org.setQueryScopeBanner(
                            "集团范围：共识别 " + stores.size() + " 家门店（gb_department_father_id=0）");
                } catch (ArithmeticException ex) {
                    org.setVisibleStores(new ArrayList<>());
                    org.setQueryScopeBanner("集团范围：distributerId 无法用于部门表查询");
                }
            }
        } else if (AiResolvedOrgScope.SCOPE_STORE.equals(scopeType)
                || AiResolvedOrgScope.SCOPE_PURCHASER.equals(scopeType)) {
            AiStoreScopeDTO anchored = anchorStoreRowForIntersectPatch(org, state);
            if (anchored != null && anchored.getStoreDepartmentId() != null && anchored.getStoreDepartmentId() > 0) {
                AiStoreScopeDTO row = enrichStoreDepartmentNameFromDbIfNeeded(anchored);
                List<AiStoreScopeDTO> concrete = new ArrayList<>();
                concrete.add(row);
                org.setVisibleStores(concrete);
                long sid = row.getStoreDepartmentId();
                org.setCurrentStoreDepartmentId(sid);
                org.setCurrentDepartmentId(sid);
                org.setRequestDepartmentId(sid);
                state.setDepartmentId(sid);
                String labelPrefix =
                        AiResolvedOrgScope.SCOPE_PURCHASER.equals(scopeType) ? "门店采购" : "门店";
                if (storeBannerLooksBrokenForIntersectPatch(org.getQueryScopeBanner())) {
                    if (StringUtils.hasText(row.getStoreName())) {
                        org.setQueryScopeBanner(labelPrefix + "：" + row.getStoreName().trim());
                    } else {
                        org.setQueryScopeBanner(labelPrefix + "：部门 " + sid);
                    }
                }
                tightenRunQueryScopeAroundStoreSubtree(state, sid);
            } else {
                NormalizedDept n = normalizeStoreAnchor(state.getDepartmentId());
                org.setCurrentStoreDepartmentId(n.storeDepartmentId());
                org.setCurrentDepartmentId(state.getDepartmentId());
                List<AiStoreScopeDTO> stores = new ArrayList<>();
                if (n.storeDepartmentId() != null) {
                    stores.add(AiStoreScopeDTO.builder()
                            .storeDepartmentId(n.storeDepartmentId())
                            .storeName(n.storeName())
                            .build());
                }
                org.setVisibleStores(stores);
                String label = AiResolvedOrgScope.SCOPE_PURCHASER.equals(scopeType) ? "门店采购" : "门店";
                org.setQueryScopeBanner(n.storeName() != null
                        ? label + "：" + n.storeName()
                        : label + "：部门 " + state.getDepartmentId());
            }
        } else if (AiResolvedOrgScope.SCOPE_WAREHOUSE.equals(scopeType)) {
            AiResolvedOrgScope rebuilt = buildWarehouseScope(ctx, state.getDepartmentId(), syn);
            rq.setOrgScope(rebuilt);
            rq.setDataScope(buildDataScope(rebuilt));
            rq.setQueryScopeBanner(rebuilt.getQueryScopeBanner());
            return;
        } else {
            AiResolvedOrgScope rebuilt = buildDepartmentLikeScope(ctx, state.getDepartmentId(), scopeType, syn);
            rq.setOrgScope(rebuilt);
            rq.setDataScope(buildDataScope(rebuilt));
            rq.setQueryScopeBanner(rebuilt.getQueryScopeBanner());
            return;
        }

        rq.setDataScope(buildDataScope(org));
        rq.setQueryScopeBanner(org.getQueryScopeBanner());
    }

    /**
     * D-11：语义 LLM 点名的口述店名中，若无法用当前账号可见门店根名称做 lexical 命中，则产出软权限提示载荷。
     * 仅适用于 {@link AiResolvedOrgScope#SCOPE_STORE} / {@link AiResolvedOrgScope#SCOPE_PURCHASER}，
     * 不向 LLM 再判权限。
     */
    public Optional<AiPermissionDenied> maybeDenialForSemanticMentionsOutsideVisibleStores(AiResolvedQueryContext rq) {
        if (rq == null || rq.isNeedSemanticClarification()) {
            return Optional.empty();
        }
        AiResolvedOrgScope org = rq.getOrgScope();
        if (org == null) {
            return Optional.empty();
        }
        String scopeType = org.getScopeType();
        if (!AiResolvedOrgScope.SCOPE_STORE.equals(scopeType)
                && !AiResolvedOrgScope.SCOPE_PURCHASER.equals(scopeType)) {
            return Optional.empty();
        }
        List<AiStoreScopeDTO> visible = org.getVisibleStores();
        if (visible == null || visible.isEmpty()) {
            return Optional.empty();
        }
        AiQuerySemanticParseResult sem = rq.getQuerySemanticParse();
        if (sem == null || sem.isParseMissing() || !sem.isStructuralConfidenceOk(querySemanticMinConfidence)) {
            return Optional.empty();
        }
        List<String> mentions = sem.effectiveMentionedStoreNames();
        if (mentions == null || mentions.isEmpty()) {
            return Optional.empty();
        }
        List<AiStoreScopeDTO> lexicalCandidates =
                visible.stream()
                        .filter(s -> s != null && StringUtils.hasText(s.getStoreName()))
                        .collect(Collectors.toList());
        if (lexicalCandidates.isEmpty()) {
            return Optional.empty();
        }
        LinkedHashSet<String> outside = new LinkedHashSet<>();
        for (String raw : mentions) {
            AiFollowUpResolver.SemanticLexicalSingleStoreHit hit =
                    AiFollowUpResolver.matchSemanticSingleStoreLexically(raw.trim(), lexicalCandidates);
            if (hit.kind() == AiFollowUpResolver.SemanticLexicalSingleStoreKind.NONE) {
                outside.add(raw.trim());
            }
        }
        if (outside.isEmpty()) {
            return Optional.empty();
        }
        LinkedHashSet<String> visNamesOrdered = new LinkedHashSet<>();
        for (AiStoreScopeDTO v : visible) {
            if (v != null && StringUtils.hasText(v.getStoreName())) {
                visNamesOrdered.add(v.getStoreName().trim());
            }
        }
        if (visNamesOrdered.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(
                AiAnswerBoundary.forMentionedStoresOutsideVisibleScope(
                        List.copyOf(outside), List.copyOf(visNamesOrdered)));
    }

    /**
     * 集团广角会话下语义已收窄 {@link AiResolvedOrgScope#SCOPE_STORE} 时，
     * {@link AiRunState#getDepartmentId()} 可能仍为 null；此处优先保留 org.visibleStores 中已落地的门店根行。
     */
    private AiStoreScopeDTO anchorStoreRowForIntersectPatch(AiResolvedOrgScope org, AiRunState state) {
        AiStoreScopeDTO row = extractFirstConcreteStoreDepartmentRow(org != null ? org.getVisibleStores() : null);
        if (row != null) {
            return row;
        }
        Long deptId = state != null ? state.getDepartmentId() : null;
        NormalizedDept n = normalizeStoreAnchor(deptId);
        if (n.storeDepartmentId() == null) {
            return null;
        }
        return AiStoreScopeDTO.builder()
                .storeDepartmentId(n.storeDepartmentId())
                .storeName(n.storeName())
                .build();
    }

    private static AiStoreScopeDTO extractFirstConcreteStoreDepartmentRow(List<AiStoreScopeDTO> visibleStores) {
        if (visibleStores == null) {
            return null;
        }
        for (AiStoreScopeDTO s : visibleStores) {
            if (s != null && s.getStoreDepartmentId() != null && s.getStoreDepartmentId() > 0) {
                return s;
            }
        }
        return null;
    }

    private AiStoreScopeDTO enrichStoreDepartmentNameFromDbIfNeeded(AiStoreScopeDTO row) {
        if (row == null || row.getStoreDepartmentId() == null) {
            return row;
        }
        if (StringUtils.hasText(row.getStoreName())) {
            return row;
        }
        long sid = row.getStoreDepartmentId();
        if (sid <= 0 || sid > Integer.MAX_VALUE) {
            return row;
        }
        GbDepartmentEntity dep = gbDepartmentMapper.selectById((int) sid);
        String nm = dep != null ? dep.getGbDepartmentName() : null;
        return AiStoreScopeDTO.builder()
                .storeDepartmentId(sid)
                .storeName(nm)
                .build();
    }

    private static boolean storeBannerLooksBrokenForIntersectPatch(String banner) {
        if (!StringUtils.hasText(banner)) {
            return true;
        }
        return banner.contains("部门 null");
    }

    /**
     * 单店语义落地后重写 Run scope：对齐 {@link AiRunScopeIntersectService} 的 STORE subtree 快照，
     * 避免 Revenue 等 Tool 拿不到 {@link AiQueryScope#getDepartmentFatherId()}。
     */
    private void tightenRunQueryScopeAroundStoreSubtree(AiRunState state, long storeRootDepartmentId) {
        if (state == null) {
            return;
        }
        int sidInt;
        try {
            sidInt = Math.toIntExact(storeRootDepartmentId);
        } catch (ArithmeticException ex) {
            return;
        }
        List<Integer> subtree = scopeResolver.collectSubtreeDepartmentIds(sidInt, null);
        if (subtree == null || subtree.isEmpty()) {
            subtree = List.of(sidInt);
        }
        List<Integer> sorted =
                subtree.stream().filter(Objects::nonNull).sorted().distinct().collect(Collectors.toList());
        AiUserContext ctx = state.getAiUserContext();
        Long anchorMem = ctx != null ? ctx.getDepartmentId() : null;
        int disInt = state.getDistributerId() != null ? state.getDistributerId().intValue() : 0;
        Map<Integer, Integer> counts =
                sorted.isEmpty() ? Map.of() : scopeResolver.departmentTypeCountsForIds(sorted);
        AiQueryScope qs = AiQueryScope.builder()
                .mode(AiConversationScopeMode.STORE)
                .departmentFatherId(storeRootDepartmentId)
                .distributerId(state.getDistributerId())
                .disIdForPurchaseQueries(disInt)
                .resolvedDepartmentIds(List.copyOf(sorted))
                .departmentTypeCounts(counts != null ? counts : Map.of())
                .parentStoreCount(sorted.isEmpty() ? 0 : 1)
                .userMemoryAnchorDepartmentId(anchorMem)
                .groupRevenueUseDistributerWideQuery(false)
                .build();
        state.setScope(qs);
    }

    private List<AiStoreScopeDTO> loadStoreScopeDtosUnderDistributer(int disPk) {
        List<Integer> storeIds = gbDepartmentMapper.selectStoreDepartmentIdsUnderDistributer(disPk);
        List<AiStoreScopeDTO> stores = new ArrayList<>(storeIds.size());
        for (Integer sid : storeIds) {
            GbDepartmentEntity row = sid != null ? gbDepartmentMapper.selectById(sid) : null;
            stores.add(AiStoreScopeDTO.builder()
                    .storeDepartmentId(sid != null ? sid.longValue() : null)
                    .storeName(row != null ? row.getGbDepartmentName() : null)
                    .build());
        }
        return stores;
    }

    private AiResolvedOrgScope buildGroupScope(AiUserContext ctx, Long requestDepartmentId, AiRunCreateRequest request) {
        Long dis = mergedDistributerId(request, ctx);
        var b = AiResolvedOrgScope.builder()
                .scopeType(AiResolvedOrgScope.SCOPE_GROUP)
                .distributerId(dis)
                .requestDepartmentId(requestDepartmentId)
                .currentDepartmentId(ctx.getDepartmentId())
                .visibleWarehouses(new ArrayList<>())
                .visibleDepartments(new ArrayList<>());

        if (dis == null) {
            b.scopeName("集团（未解析 distributerId）")
                    .visibleStores(new ArrayList<>())
                    .queryScopeBanner("集团范围：缺少 distributerId，暂无法展开下属门店")
                    .coverageDetail("请确保 gb_department_user 挂靠 distributerId、或请求体传入 distributerId。");
            return b.build();
        }
        int disPk;
        try {
            disPk = Math.toIntExact(dis);
        } catch (ArithmeticException ex) {
            b.scopeName("集团（distributerId 超出 int 范围）")
                    .visibleStores(new ArrayList<>())
                    .queryScopeBanner("集团范围：distributerId 无法用于部门表查询")
                    .coverageDetail("distributerId=" + dis + " 超出 MyBatis 映射 int。");
            return b.build();
        }

        List<AiStoreScopeDTO> stores = loadStoreScopeDtosUnderDistributer(disPk);

        String banner = "集团范围：共识别 " + stores.size() + " 家门店（gb_department_father_id=0）";
        b.visibleStores(stores)
                .currentStoreDepartmentId(null)
                .scopeName("集团")
                .queryScopeBanner(banner)
                .coverageDetail("visibleStores 为权限内应可见门店根，非当日有营收门店。");
        return b.build();
    }

    private AiResolvedOrgScope buildStoreScope(AiUserContext ctx, Long requestDepartmentId, String scopeType,
                                               AiRunCreateRequest request) {
        Long dis = mergedDistributerId(request, ctx);
        NormalizedDept n = normalizeStoreAnchor(requestDepartmentId);
        List<AiStoreScopeDTO> stores = new ArrayList<>();
        if (n.storeDepartmentId() != null) {
            stores.add(AiStoreScopeDTO.builder()
                    .storeDepartmentId(n.storeDepartmentId())
                    .storeName(n.storeName())
                    .build());
        }
        String label = AiResolvedOrgScope.SCOPE_PURCHASER.equals(scopeType) ? "门店采购" : "门店";
        String banner = n.storeName() != null
                ? label + "：" + n.storeName()
                : label + "：部门 " + requestDepartmentId;
        return AiResolvedOrgScope.builder()
                .scopeType(scopeType)
                .distributerId(dis)
                .requestDepartmentId(requestDepartmentId)
                .currentStoreDepartmentId(n.storeDepartmentId())
                .currentDepartmentId(requestDepartmentId)
                .visibleStores(stores)
                .visibleWarehouses(new ArrayList<>())
                .visibleDepartments(new ArrayList<>())
                .scopeName(label)
                .queryScopeBanner(banner)
                .coverageDetail("单门店可见范围。")
                .build();
    }

    private AiResolvedOrgScope buildWarehouseScope(AiUserContext ctx, Long requestDepartmentId,
                                                   AiRunCreateRequest request) {
        Long dis = mergedDistributerId(request, ctx);
        Long deptId = requestDepartmentId != null ? requestDepartmentId : ctx.getDepartmentId();
        GbDepartmentEntity dep = departmentRow(deptId);
        List<AiDepartmentScopeDTO> wh = new ArrayList<>();
        Long father = null;
        if (dep != null) {
            Integer f = dep.getGbDepartmentFatherId();
            father = f != null ? f.longValue() : null;
            wh.add(AiDepartmentScopeDTO.builder()
                    .departmentId(dep.getGbDepartmentId() != null ? dep.getGbDepartmentId().longValue() : deptId)
                    .departmentName(dep.getGbDepartmentName())
                    .fatherId(father)
                    .build());
        } else if (deptId != null) {
            wh.add(AiDepartmentScopeDTO.builder()
                    .departmentId(deptId)
                    .departmentName(null)
                    .fatherId(null)
                    .build());
        }

        Long storeAnchor = (father != null && father > 0L) ? father : null;

        return AiResolvedOrgScope.builder()
                .scopeType(AiResolvedOrgScope.SCOPE_WAREHOUSE)
                .distributerId(dis)
                .requestDepartmentId(requestDepartmentId)
                .currentStoreDepartmentId(storeAnchor)
                .currentDepartmentId(deptId)
                .visibleStores(new ArrayList<>())
                .visibleWarehouses(wh)
                .visibleDepartments(new ArrayList<>(wh))
                .scopeName("库房")
                .queryScopeBanner(dep != null && dep.getGbDepartmentName() != null
                        ? "本库房：" + dep.getGbDepartmentName()
                        : "本库房/部门：" + deptId)
                .coverageDetail("库房视角：仅本人所在库房/部门，不展开集团全部门店库存。")
                .build();
    }

    private AiResolvedOrgScope buildDepartmentLikeScope(AiUserContext ctx, Long requestDepartmentId, String scopeType,
                                                        AiRunCreateRequest request) {
        Long dis = mergedDistributerId(request, ctx);
        NormalizedDept n = normalizeStoreAnchor(requestDepartmentId != null ? requestDepartmentId : ctx.getDepartmentId());
        List<AiStoreScopeDTO> stores = new ArrayList<>();
        if (n.storeDepartmentId() != null) {
            stores.add(AiStoreScopeDTO.builder()
                    .storeDepartmentId(n.storeDepartmentId())
                    .storeName(n.storeName())
                    .build());
        }
        return AiResolvedOrgScope.builder()
                .scopeType(scopeType)
                .distributerId(dis)
                .requestDepartmentId(requestDepartmentId)
                .currentStoreDepartmentId(n.storeDepartmentId())
                .currentDepartmentId(requestDepartmentId != null ? requestDepartmentId : ctx.getDepartmentId())
                .visibleStores(stores)
                .visibleWarehouses(new ArrayList<>())
                .visibleDepartments(new ArrayList<>())
                .scopeName("部门")
                .queryScopeBanner(n.storeName() != null ? "可见门店：" + n.storeName() : "组织范围：待解析")
                .coverageDetail("非 0/1/3/11 角色的兜底：按挂靠部门归一化门店锚点。")
                .build();
    }

    private NormalizedDept normalizeStoreAnchor(Long departmentId) {
        GbDepartmentEntity dep = departmentRow(departmentId);
        if (dep == null) {
            return new NormalizedDept(departmentId, null);
        }
        Integer father = dep.getGbDepartmentFatherId();
        if (father == null || father == 0) {
            long sid = dep.getGbDepartmentId() != null ? dep.getGbDepartmentId().longValue() : departmentId;
            return new NormalizedDept(sid, dep.getGbDepartmentName());
        }
        GbDepartmentEntity store = gbDepartmentMapper.selectById(father);
        long sid = father.longValue();
        return new NormalizedDept(sid, store != null ? store.getGbDepartmentName() : null);
    }

    private GbDepartmentEntity departmentRow(Long departmentId) {
        if (departmentId == null) {
            return null;
        }
        if (departmentId > Integer.MAX_VALUE || departmentId < Integer.MIN_VALUE) {
            return null;
        }
        return gbDepartmentMapper.selectById(departmentId.intValue());
    }

    private AiResolvedOrgScope narrowGroupOrgBySemanticLlmStoreIfNeeded(
            AiResolvedOrgScope mergedOrg,
            AiQuerySemanticParseResult semantic,
            AiRunCreateRequest request,
            boolean structuralLlmApplied,
            double minConfidence,
            String normalizedUserMessage,
            AiMultiStoreHarnessTrace harnessTrace,
            AiSemanticStoreNarrowingDiagnostics diag) {
        List<String> mentionListRaw = semantic != null ? semantic.effectiveMentionedStoreNames() : List.of();
        List<String> mentionList = mentionListRaw == null ? List.of() : new ArrayList<>(mentionListRaw);
        if (diag != null) {
            diag.setSemanticMentionedStoreNames(new ArrayList<>(mentionList));
        }
        AiQuerySemanticParseResult.RequestedScopePart rs = semantic != null ? semantic.getRequestedScope() : null;
        boolean explicitSemanticStoreIntent =
                !mentionList.isEmpty()
                        || (rs != null
                                && AiResolvedOrgScope.SCOPE_STORE.equals(rs.getRequestedScopeType())
                                && StringUtils.hasText(rs.getMentionedStoreName()));

        if (!structuralLlmApplied || mergedOrg == null) {
            if (diag != null) {
                diag.setNarrowingFailureReason(AiSemanticStoreNarrowingDiagnostics.REASON_SKIPPED_STRUCTUREAL_GATE);
            }
            return mergedOrg;
        }
        if (semantic == null || semantic.isParseMissing() || !semantic.isUsableForMerge(minConfidence)) {
            if (diag != null) {
                diag.setNarrowingFailureReason(AiSemanticStoreNarrowingDiagnostics.REASON_SKIPPED_SEMANTIC_UNUSABLE);
            }
            return mergedOrg;
        }
        if (!AiResolvedOrgScope.SCOPE_GROUP.equals(mergedOrg.getScopeType())) {
            if (diag != null) {
                diag.setNarrowingFailureReason(AiSemanticStoreNarrowingDiagnostics.REASON_SKIPPED_NOT_GROUP_SCOPE);
            }
            return mergedOrg;
        }

        if (!explicitSemanticStoreIntent) {
            if (diag != null) {
                diag.setNarrowingAttemptedSemanticExplicitStore(false);
                diag.setNarrowingFailureReason(null);
            }
            return mergedOrg;
        }
        if (diag != null) {
            diag.setNarrowingAttemptedSemanticExplicitStore(true);
        }

        List<AiStoreScopeDTO> candidates =
                buildIntersectedStoreRootCandidates(mergedOrg, request, diag);

        if (mentionList.size() >= 2) {
            if (candidates.isEmpty()) {
                if (diag != null) {
                    diag.setNarrowingFailureReason(
                            AiSemanticStoreNarrowingDiagnostics.REASON_SEMANTIC_MENTION_BUT_EMPTY_CANDIDATES);
                }
                return mergedOrg;
            }
            for (String raw : mentionList) {
                if (!StringUtils.hasText(raw)) {
                    continue;
                }
                AiFollowUpResolver.SemanticLexicalSingleStoreHit h =
                        AiFollowUpResolver.matchSemanticSingleStoreLexically(raw.trim(), candidates);
                if (h.kind() == AiFollowUpResolver.SemanticLexicalSingleStoreKind.AMBIGUOUS) {
                    if (diag != null) {
                        diag.setAmbiguousLexicalMatch(true);
                        diag.setLastSingleSemanticStoreMention(raw.trim());
                        diag.setLexicalAmbiguityStoreSummaries(
                                h.ambiguousStores() == null
                                        ? new ArrayList<>()
                                        : h.ambiguousStores().stream()
                                                .map(AiResolvedQueryContextResolver::summarizeStoreCandidateForDiag)
                                                .filter(Objects::nonNull)
                                                .collect(Collectors.toList()));
                        diag.setNarrowingFailureReason(
                                AiSemanticStoreNarrowingDiagnostics.REASON_AMBIGUOUS_LEXICAL_MATCH);
                    }
                    return mergedOrg;
                }
            }
            List<AiStoreScopeDTO> picks =
                    AiFollowUpResolver.resolvedStoresSubsetFromDistinctMentions(mentionList, candidates);
            if (picks.size() >= 2) {
                log.info(
                        "[AiResolvedQueryContext] semanticLlmMultiStoreSubset hitCount={} storeRootIds={}",
                        picks.size(),
                        picks.stream().map(AiStoreScopeDTO::getStoreDepartmentId).toList());
                if (harnessTrace != null) {
                    harnessTrace.noteSubsetKeepingGroupApplied(picks);
                }
                if (diag != null) {
                    diag.setNarrowedSuccessfully(true);
                    diag.setNarrowingFailureReason(null);
                    diag.setMatchedStoreCandidate(null);
                    diag.setMatchedSemanticStoreMention(null);
                }
                return AiFollowUpResolver.copyOrgNarrowedToStoreSubsetKeepingGroup(mergedOrg, picks);
            }
            if (diag != null) {
                diag.setNarrowingFailureReason(AiSemanticStoreNarrowingDiagnostics.REASON_MULTI_STORE_SUBSET_PARTIAL);
            }
            return mergedOrg;
        }

        String singleStoreMention = null;
        if (mentionList.size() == 1) {
            singleStoreMention = mentionList.get(0);
        } else if (rs != null
                && AiResolvedOrgScope.SCOPE_STORE.equals(rs.getRequestedScopeType())
                && StringUtils.hasText(rs.getMentionedStoreName())) {
            singleStoreMention = rs.getMentionedStoreName().trim();
        }
        if (!StringUtils.hasText(singleStoreMention)) {
            if (diag != null) {
                diag.setNarrowingFailureReason(AiSemanticStoreNarrowingDiagnostics.REASON_NO_SINGLE_STORE_MENTION);
            }
            return mergedOrg;
        }
        if (diag != null) {
            diag.setLastSingleSemanticStoreMention(singleStoreMention.trim());
        }
        if (candidates.isEmpty()) {
            if (diag != null) {
                diag.setNarrowingFailureReason(
                        AiSemanticStoreNarrowingDiagnostics.REASON_SEMANTIC_MENTION_BUT_EMPTY_CANDIDATES);
            }
            return mergedOrg;
        }
        AiFollowUpResolver.SemanticLexicalSingleStoreHit hit =
                AiFollowUpResolver.matchSemanticSingleStoreLexically(singleStoreMention.trim(), candidates);
        if (hit.kind() == AiFollowUpResolver.SemanticLexicalSingleStoreKind.AMBIGUOUS) {
            if (diag != null) {
                diag.setAmbiguousLexicalMatch(true);
                diag.setLexicalAmbiguityStoreSummaries(
                        hit.ambiguousStores() == null
                                ? new ArrayList<>()
                                : hit.ambiguousStores().stream()
                                        .map(AiResolvedQueryContextResolver::summarizeStoreCandidateForDiag)
                                        .filter(Objects::nonNull)
                                        .collect(Collectors.toList()));
                diag.setNarrowingFailureReason(AiSemanticStoreNarrowingDiagnostics.REASON_AMBIGUOUS_LEXICAL_MATCH);
            }
            return mergedOrg;
        }
        if (hit.kind() != AiFollowUpResolver.SemanticLexicalSingleStoreKind.UNIQUE || hit.unique() == null) {
            if (diag != null) {
                diag.setNarrowingFailureReason(AiSemanticStoreNarrowingDiagnostics.REASON_NO_LEXICAL_MATCH);
            }
            return mergedOrg;
        }
        AiStoreScopeDTO narrowed = hit.unique();
        log.info(
                "[AiResolvedQueryContext] semanticLlmStoreMentionHit storeRootId={} storeName={}",
                narrowed.getStoreDepartmentId(),
                narrowed.getStoreName());
        if (diag != null) {
            diag.setNarrowedSuccessfully(true);
            diag.setNarrowingFailureReason(null);
            diag.setMatchedStoreCandidate(summarizeStoreCandidateForDiag(narrowed));
            diag.setMatchedSemanticStoreMention(singleStoreMention.trim());
        }
        return AiFollowUpResolver.copyOrgNarrowedToSingleStore(mergedOrg, narrowed);
    }

    /**
     * 经销权限内 gb_department 门店根：先按 visibleStores 中非空 storeDepartmentId 与根 id 相交；
     * 若无 id（仅预览店名），则按店名 ↔ 根部名称 lexical 相容匹配；再无可见行则用权限内全体根。
     */
    private List<AiStoreScopeDTO> buildIntersectedStoreRootCandidates(
            AiResolvedOrgScope mergedOrg,
            AiRunCreateRequest request,
            AiSemanticStoreNarrowingDiagnostics diag) {
        List<AiStoreScopeDTO> vis = mergedOrg != null ? mergedOrg.getVisibleStores() : null;
        boolean visEmptyCollection = vis == null || vis.isEmpty();
        if (diag != null) {
            diag.setVisibleStoreCandidates(formatVisibleStorePreviewLabels(vis));
        }
        if (mergedOrg == null) {
            return List.of();
        }

        Long dis = mergedOrg.getDistributerId() != null
                ? mergedOrg.getDistributerId()
                : (request != null ? request.getDistributerId() : null);
        if (dis == null) {
            if (diag != null) {
                diag.setStoreRootCandidates(new ArrayList<>());
            }
            return List.of();
        }
        int disPk;
        try {
            disPk = Math.toIntExact(dis);
        } catch (ArithmeticException ex) {
            if (diag != null) {
                diag.setStoreRootCandidates(new ArrayList<>());
            }
            return List.of();
        }

        List<AiStoreScopeDTO> allRoots = loadDistributerStoreRootDtos(disPk);
        if (diag != null && allRoots.isEmpty()) {
            diag.setStoreRootCandidates(new ArrayList<>());
        }

        boolean hasNamedVisibleRows =
                vis != null
                        && vis.stream()
                                .anyMatch(s -> s != null && StringUtils.hasText(s.getStoreName()));
        Set<Long> allowedIds =
                vis == null
                        ? Set.of()
                        : vis.stream()
                                .filter(s -> s != null && s.getStoreDepartmentId() != null)
                                .map(AiStoreScopeDTO::getStoreDepartmentId)
                                .collect(Collectors.toSet());

        List<AiStoreScopeDTO> candidates;
        if (!allowedIds.isEmpty()) {
            candidates =
                    allRoots.stream()
                            .filter(r -> r.getStoreDepartmentId() != null && allowedIds.contains(r.getStoreDepartmentId()))
                            .collect(Collectors.toCollection(ArrayList::new));
        } else if (hasNamedVisibleRows && vis != null) {
            candidates =
                    allRoots.stream()
                            .filter(
                                    root -> vis.stream()
                                            .anyMatch(
                                                    row ->
                                                            row != null
                                                                    && AiFollowUpResolver
                                                                            .visibleStoreRowLabelMatchesDepartmentName(
                                                                                    row.getStoreName(),
                                                                                    root.getStoreName())))
                            .collect(Collectors.toCollection(ArrayList::new));
        } else {
            candidates = new ArrayList<>(allRoots);
        }

        if (diag != null) {
            diag.setStoreRootCandidates(
                    candidates.stream()
                            .map(AiResolvedQueryContextResolver::summarizeStoreCandidateForDiag)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toCollection(ArrayList::new)));
        }
        return candidates;
    }

    private List<AiStoreScopeDTO> loadDistributerStoreRootDtos(int distributerPk) {
        List<Integer> ids = gbDepartmentMapper.selectStoreDepartmentIdsUnderDistributer(distributerPk);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<AiStoreScopeDTO> roots = new ArrayList<>();
        for (Integer id : ids) {
            if (id == null || id <= 0) {
                continue;
            }
            GbDepartmentEntity e = gbDepartmentMapper.selectById(id);
            if (e == null || e.getGbDepartmentFatherId() == null || e.getGbDepartmentFatherId() != 0) {
                continue;
            }
            roots.add(
                    AiStoreScopeDTO.builder()
                            .storeDepartmentId(
                                    e.getGbDepartmentId() != null ? e.getGbDepartmentId().longValue() : id.longValue())
                            .storeName(e.getGbDepartmentName())
                            .build());
        }
        return roots;
    }

    private static String summarizeStoreCandidateForDiag(AiStoreScopeDTO s) {
        if (s == null) {
            return null;
        }
        Long id = s.getStoreDepartmentId();
        String name = s.getStoreName();
        boolean hasId = id != null;
        boolean hasName = StringUtils.hasText(name);
        if (hasId && hasName) {
            return id + ":" + name.trim();
        }
        if (hasId) {
            return String.valueOf(id);
        }
        return hasName ? name.trim() : null;
    }

    private static List<String> formatVisibleStorePreviewLabels(List<AiStoreScopeDTO> vis) {
        List<String> out = new ArrayList<>();
        if (vis == null) {
            return out;
        }
        for (AiStoreScopeDTO s : vis) {
            String lbl = summarizeStoreCandidateForDiag(s);
            if (lbl != null) {
                out.add(lbl);
            }
        }
        return out;
    }

    private static String buildStoreLexicalAmbiguityQuestion(AiSemanticStoreNarrowingDiagnostics diag) {
        String mention = diag != null ? diag.getLastSingleSemanticStoreMention() : null;
        List<String> opts = diag != null ? diag.getLexicalAmbiguityStoreSummaries() : null;
        if (!StringUtils.hasText(mention)) {
            return "请说明要查询哪家门店（系统识别出多个同名或相近门店）。";
        }
        String m = mention.trim();
        if (opts != null && !opts.isEmpty()) {
            return String.format(
                    "您提到的「%s」可能对应多家门店：%s。请明确具体是哪一家。", m, String.join("、", opts));
        }
        return String.format("您提到的「%s」可能对应多家门店，请明确具体是哪一家。", m);
    }

    private static String resolveMentionedDishName(
            AiResolvedQueryIntent qi,
            AiConversationTurnMemory previousTurn,
            AiResolvedOrgScope mergedOrg,
            AiFollowUpResolution followUp,
            AiQuerySemanticParseResult semLlm) {
        if (qi == null) {
            return null;
        }
        String path = qi.getPathCode();
        boolean dishProfitPath = AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(path);
        boolean diagnosisSingleDishTail = AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS.equals(path)
                && AiQuerySemanticLexicon.isSingleDishMetricOrReasonStructuredDetail(qi.getStructuredIntentDetail());
        if (!dishProfitPath && !diagnosisSingleDishTail) {
            return null;
        }
        if (semLlm != null && StringUtils.hasText(semLlm.getMentionedDishName())) {
            String dish = discardIfHintIsScopedStoreName(semLlm.getMentionedDishName().trim(), mergedOrg, followUp);
            if (StringUtils.hasText(dish)) {
                return AiQuerySemanticLexicon.finalizeMentionedDishNameForDishProfit(dish);
            }
        }
        if (AiQuerySemanticLexicon.isDishProfitRankingStructuredDetail(qi.getStructuredIntentDetail())) {
            return null;
        }
        if (previousTurn != null && StringUtils.hasText(previousTurn.getLastMentionedDishName())) {
            String inherited = discardIfHintIsScopedStoreName(
                    previousTurn.getLastMentionedDishName().trim(), mergedOrg, followUp);
            if (StringUtils.hasText(inherited)) {
                return AiQuerySemanticLexicon.finalizeMentionedDishNameForDishProfit(inherited);
            }
        }
        return null;
    }

    /**
     * 「某某店呢」会与点名菜的正则重叠，不能把当前可见门店名当作菜名传给毛利工具（否则明细被 filter 光）。
     */
    private static String discardIfHintIsScopedStoreName(
            String dishHint,
            AiResolvedOrgScope org,
            AiFollowUpResolution followUp) {
        if (!StringUtils.hasText(dishHint)) {
            return null;
        }
        if (equalsNormalizedStoreLabel(dishHint, followUp != null ? followUp.getStoreScopeFollowUpMentionedName() : null)) {
            return null;
        }
        if (org != null && org.getVisibleStores() != null) {
            for (AiStoreScopeDTO s : org.getVisibleStores()) {
                if (s != null && equalsNormalizedStoreLabel(dishHint, s.getStoreName())) {
                    return null;
                }
            }
        }
        return dishHint;
    }

    /**
     * 时间/门店/集团范围类短追问：防止 mergeDishProfitCuesInto 等写入 overview 或空白，冲掉上一轮「单菜指标 / 原因」子意图。
     */
    private static void stabilizeDishProfitFollowUpStructuredIntent(
            String normalized,
            AiFollowUpResolution followUp,
            AiConversationTurnMemory previousTurn,
            AiResolvedQueryIntent queryIntent) {
        if (!StringUtils.hasText(normalized) || followUp == null || !followUp.isFollowUp()
                || previousTurn == null || queryIntent == null) {
            return;
        }
        if ("NEED_SEMANTIC_CLARIFICATION".equals(followUp.getFollowUpType())) {
            return;
        }
        if (!AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(queryIntent.getPathCode())) {
            return;
        }
        String furType = followUp.getFollowUpType();
        if (!"TIME_SHIFT".equals(furType)
                && !"STORE_SCOPE_FOLLOW_UP".equals(furType)
                && !"GROUP_SCOPE_EXPAND_FOLLOW_UP".equals(furType)
                && !"SEMANTIC_STRUCTURAL_MERGE".equals(furType)) {
            return;
        }
        String prevSid = previousTurn.getLastStructuredIntentDetail();
        if (!AiQuerySemanticLexicon.isSingleDishMetricOrReasonStructuredDetail(prevSid)) {
            return;
        }
        String cur = queryIntent.getStructuredIntentDetail();
        String curWire =
                StringUtils.hasText(cur) ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(cur.trim()) : "";
        boolean weak = !StringUtils.hasText(curWire)
                || AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_OVERVIEW.equals(curWire)
                || !AiQuerySemanticLexicon.isSingleDishMetricOrReasonStructuredDetail(cur);
        if (StringUtils.hasText(curWire) && AiQuerySemanticLexicon.isDishProfitRankingStructuredDetail(curWire)) {
            weak = false;
        }
        if (!weak) {
            return;
        }
        queryIntent.setStructuredIntentDetail(prevSid);
        log.info(
                "[AiResolvedQueryContext] stabilizeDishProfitFollowUpStructuredIntent followUpType={} restoredStructured={}",
                furType,
                prevSid);
    }

    private static boolean equalsNormalizedStoreLabel(String dishHint, String storeLabel) {
        if (!StringUtils.hasText(dishHint) || !StringUtils.hasText(storeLabel)) {
            return false;
        }
        String a = dishHint.replace(" ", "").trim();
        String b = storeLabel.replace(" ", "").trim();
        return !a.isEmpty() && a.equals(b);
    }

    private SemanticAdoption trySemanticAdoption(
            AiQuerySemanticParseResult sem,
            AiConversationTurnMemory previousTurn,
            String normalized,
            LocalDate today,
            AiResolvedTimeWindow explicitTentative) {
        if (sem == null || SemanticParseFallbackPolicy.needSemanticParseClarification(sem, querySemanticMinConfidence)) {
            return null;
        }
        AiResolvedQueryIntent baseline = AiResolvedQueryIntent.builder().build();
        AiResolvedQueryIntent merged =
                AiQuerySemanticLlmMergeHelper.mergeIntent(
                        baseline, sem, querySemanticMinConfidence, normalized, previousTurn);
        if (!StringUtils.hasText(merged.getPathCode())) {
            return null;
        }
        AiResolvedTimeWindow tentative =
                AiQuerySemanticLlmMergeHelper.mergeTentativeTime(
                        explicitTentative,
                        sem,
                        today,
                        querySemanticMinConfidence,
                        normalized,
                        merged,
                        previousTurn);
        return new SemanticAdoption(sem, merged, tentative);
    }

    private String explainV2NonAdoption(AiQuerySemanticParseResult v2) {
        if (!querySemanticLlmEnabled) {
            return null;
        }
        if (v2 == null) {
            return "v2_null";
        }
        if (SemanticParseFallbackPolicy.needSemanticParseClarification(v2, querySemanticMinConfidence)) {
            if (v2.isParseMissing()) {
                String err = v2.getObservationJsonParseError();
                return StringUtils.hasText(err) ? "v2_parse_missing:" + err : "v2_parse_missing";
            }
            if (!v2.isStructuralConfidenceOk(querySemanticMinConfidence)) {
                return "v2_low_confidence";
            }
            if (Boolean.TRUE.equals(v2.getNeedClarification())) {
                return "v2_need_clarification";
            }
            return "v2_unreliable";
        }
        return "v2_no_routable_path";
    }

    private static List<String> describeAdoptedSemanticFields(AiQuerySemanticParseResult r) {
        if (r == null) {
            return null;
        }
        List<String> keys = new ArrayList<>();
        if (StringUtils.hasText(r.getIntent())) {
            keys.add("intent");
        }
        if (StringUtils.hasText(r.getMentionedDishName())) {
            keys.add("mentionedDishName");
        }
        if (r.getConfidence() != null) {
            keys.add("confidence");
        }
        if (Boolean.TRUE.equals(r.getFollowUp())) {
            keys.add("followUp");
        }
        if (StringUtils.hasText(r.getIntentAction())) {
            keys.add("intentAction");
        }
        if (StringUtils.hasText(r.getTimeAction())) {
            keys.add("timeAction");
        }
        if (StringUtils.hasText(r.getScopeAction())) {
            keys.add("scopeAction");
        }
        if (StringUtils.hasText(r.getMetricAction())) {
            keys.add("metricAction");
        }
        if (r.getTime() != null && StringUtils.hasText(r.getTime().getTimeType())) {
            keys.add("time.timeType");
        }
        if (r.getRequestedScope() != null) {
            keys.add("requestedScope");
        }
        if (r.getMetric() != null && StringUtils.hasText(r.getMetric().getRankingType())) {
            keys.add("metric.rankingType");
        }
        return keys.isEmpty() ? null : keys;
    }

    private static AiQuerySemanticParseResult preferReadableSemantic(
            AiQuerySemanticParseResult v2, AiQuerySemanticParseResult v1) {
        if (v2 != null && !v2.isParseMissing()) {
            return v2;
        }
        if (v1 != null && !v1.isParseMissing()) {
            return v1;
        }
        return v2 != null ? v2 : v1;
    }

    private static Map<String, Object> safeSemanticMap(AiQuerySemanticParseResult r) {
        return r == null ? null : AiQuerySemanticParseResultDebugSerializer.toSafeMap(r);
    }

    private static String blankToNullSemantic(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    private static List<String> querySemanticV2EffectiveStoreNames(AiQuerySemanticParseResult r) {
        if (r == null) {
            return null;
        }
        List<String> e = r.effectiveMentionedStoreNames();
        return e == null || e.isEmpty() ? null : new ArrayList<>(e);
    }

    private static AiQuerySemanticParseResult augmentV2SemanticWithInheritedHarnessMultiStores(
            AiQuerySemanticParseResult sem, AiConversationTurnMemory previousTurn) {
        if (sem == null || sem.isParseMissing() || previousTurn == null) {
            return sem;
        }
        if (!"INHERIT_PREVIOUS".equals(normalizeSemanticV2ActionToken(sem.getScopeAction()))) {
            return sem;
        }
        List<String> prevStores = previousTurn.getLastHarnessMultiStoreMatchedStores();
        if (prevStores == null || prevStores.isEmpty()) {
            prevStores = AiConversationTurnMemory.readHarnessMultiStoreFromToolSummary(previousTurn.getLastToolSummary());
        }
        if (prevStores == null || prevStores.size() < 2) {
            return sem;
        }
        if (sem.effectiveMentionedStoreNames().size() >= 2) {
            return sem;
        }
        AiQuerySemanticParseResult.RequestedScopePart rs = sem.getRequestedScope();
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (rs != null && rs.getMentionedStoreNames() != null) {
            for (String n : rs.getMentionedStoreNames()) {
                String t = AiQuerySemanticParseResult.sanitizeMentionedStoreNameToken(n);
                if (t != null) {
                    merged.add(t);
                }
            }
        }
        for (String n : prevStores) {
            String t = AiQuerySemanticParseResult.sanitizeMentionedStoreNameToken(n);
            if (t != null) {
                merged.add(t);
            }
        }
        if (merged.size() < 2) {
            return sem;
        }
        AiQuerySemanticParseResult.RequestedScopePart rsNew =
                AiQuerySemanticParseResult.RequestedScopePart.builder()
                        .requestedScopeType(rs != null ? rs.getRequestedScopeType() : null)
                        .mentionedStoreName(rs != null ? rs.getMentionedStoreName() : null)
                        .mentionedStoreNames(new ArrayList<>(merged))
                        .mentionedDepartmentName(rs != null ? rs.getMentionedDepartmentName() : null)
                        .mentionedWarehouseName(rs != null ? rs.getMentionedWarehouseName() : null)
                        .scopeSource(rs != null ? rs.getScopeSource() : null)
                        .needInheritFromPrevious(rs != null ? rs.getNeedInheritFromPrevious() : null)
                        .build();
        return sem.toBuilder().requestedScope(rsNew).build();
    }

    /** 语义 v2：四域经营概览追问仅挪时间时应保持四专线编排，补齐候选里可能被 LLM 截断的子 Agent/tool。 */
    private static boolean shouldCanonicalizeOrchestrationForSemanticTimeFollowUpBizOverview(
            AiQuerySemanticParseResult sem, AiConversationTurnMemory previousTurn) {
        if (sem == null) {
            return false;
        }
        String ia = normalizeSemanticV2ActionToken(sem.getIntentAction());
        boolean intentInherited =
                "INHERIT_PREVIOUS".equals(ia)
                        || (!StringUtils.hasText(ia)
                                && previousTurn != null
                                && AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW.equals(
                                        previousTurn.getLastPathCode()));
        if (!intentInherited) {
            return false;
        }
        String ta = normalizeSemanticV2ActionToken(sem.getTimeAction());
        if (!"NEW".equals(ta) && !"OVERRIDE".equals(ta)) {
            return false;
        }
        if ("OVERRIDE".equals(normalizeSemanticV2ActionToken(sem.getScopeAction()))) {
            return false;
        }
        if ("OVERRIDE".equals(normalizeSemanticV2ActionToken(sem.getMetricAction()))) {
            return false;
        }
        return true;
    }

    private static List<String> canonicalBusinessOverviewMultiAgentAgents() {
        return List.of(
                BusinessAgentNames.REVENUE_OVERVIEW,
                BusinessAgentNames.PURCHASE_OVERVIEW,
                BusinessAgentNames.STOCK_REDUCE_QUERY,
                BusinessAgentNames.DISH_PROFIT_ANALYSIS);
    }

    private static String normalizeSemanticV2ActionToken(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        return raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private static boolean isHarnessMultiStoreAmountRankingWire(String structuredDetailRaw) {
        if (!StringUtils.hasText(structuredDetailRaw)) {
            return false;
        }
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(structuredDetailRaw.trim());
        String wire = StringUtils.hasText(canon) ? canon : structuredDetailRaw.trim();
        return AiQuerySemanticLexicon.STRUCTURED_REVENUE_STORE_AMOUNT_RANKING.equals(wire)
                || AiQuerySemanticLexicon.STRUCTURED_PURCHASE_STORE_AMOUNT_RANKING.equals(wire)
                || AiQuerySemanticLexicon.STRUCTURED_BUSINESS_STORE_STATUS_COMPARE.equals(wire)
                || AiQuerySemanticLexicon.STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY.equals(wire)
                || AiQuerySemanticLexicon.STRUCTURED_STORE_OUTBOUND_AMOUNT_RANKING.equals(wire)
                || AiQuerySemanticLexicon.STRUCTURED_GOODS_OUTBOUND_RANKING.equals(wire);
    }

    private static List<String> visibleStoreNamesForHarness(AiResolvedOrgScope org) {
        if (org == null || org.getVisibleStores() == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (AiStoreScopeDTO s : org.getVisibleStores()) {
            if (s == null || s.getStoreDepartmentId() == null) {
                continue;
            }
            String n = AiQuerySemanticParseResult.sanitizeMentionedStoreNameToken(s.getStoreName());
            if (n != null) {
                out.add(n);
            }
        }
        return out;
    }

    private record NormalizedDept(Long storeDepartmentId, String storeName) {
    }
}
