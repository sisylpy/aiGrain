package com.nongxinle.ai.resolver;

import com.nongxinle.ai.context.AiDepartmentScopeDTO;
import com.nongxinle.ai.context.AiResolvedDataScope;
import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiSemanticStoreNarrowingDiagnostics;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.context.AiResolvedTimeWindowDisplaySupport;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.context.AiUserContext;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.conversation.AiConversationMemoryService;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiFollowUpResolution;
import com.nongxinle.ai.conversation.AiFollowUpResolver;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.agent.business.BusinessDiagnosisAgentV1;
import com.nongxinle.ai.agent.business.BusinessAgentNames;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.dto.business.DiagnosisPlan;
import com.nongxinle.ai.harness.followup.BusinessDiagnosisDrilldownMatrix;
import com.nongxinle.ai.harness.followup.BusinessOverviewDrilldownMatrix;
import com.nongxinle.ai.harness.followup.BusinessDrilldownRequestAssembler;
import com.nongxinle.ai.harness.followup.DishSalesDrilldownMatrix;
import com.nongxinle.ai.harness.followup.PurchaseFollowUpSlotSignals;
import com.nongxinle.ai.followup.AiFollowUpHintSupport;
import com.nongxinle.ai.harness.AiMultiStoreHarnessTrace;
import com.nongxinle.ai.security.AiAnswerBoundary;
import com.nongxinle.ai.security.AiPermissionDenied;
import com.nongxinle.ai.platform.dto.AiRunCreateRequest;
import com.nongxinle.ai.scope.AiConversationScopeMode;
import com.nongxinle.ai.scope.AiQueryScope;
import com.nongxinle.ai.scope.AiScopeResolver;
import com.nongxinle.ai.semantic.*;
import com.nongxinle.ai.semantic.frame.CurrentSemanticFrame;
import com.nongxinle.ai.semantic.frame.CurrentSemanticFrameValidator;
import com.nongxinle.ai.semantic.frame.SemanticFrameValidationResult;
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
 * 统一解析入口：装配 {@link AiResolvedQueryContext}（唯一新业务上下文入口）。
 * 主链路：V2 语义 parse → {@link #trySemanticAdoption}（SlotMerge / 采购 frame 校验 / MergeHelper / {@link com.nongxinle.ai.semantic.SemanticTimeContractCheck}）→
 * FollowUp / Time / Org 策略 → Graph。用户话术不做 Java 关键词语义解析；组织树与门店收窄口径由本类集中处理。
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

        /* 显式时间仅来自 V2 LLM time 块 + {@link com.nongxinle.ai.semantic.SemanticTimeContractCheck}，不做 Java 侧 timeType 推算。 */
        AiResolvedTimeWindow explicitTentative = null;

        String semanticPrimaryVersion = "v2";
        /** 历史兼容：写入 {@link AiResolvedQueryContext#getSemanticFallbackUsed()}，当前恒 false，不代表 V1 fallback。 */
        Boolean semanticFallbackUsed = Boolean.FALSE;
        /** V2 拒收原因（time contract / frame validation / parse 失败）；写入 semanticFallbackReason，非 V1 fallback。 */
        String semanticFallbackReason = null;
        String semanticAdoptedFrom = null;
        List<String> semanticAdoptedFields = null;
        List<String> semanticAdoptionRejectedFields = null;
        String semanticAdoptionRejectedReason = null;
        String semanticMetricNormalizedFrom = null;
        String semanticMetricNormalizedTo = null;
        Map<String, Object> semanticV2AbstractIntentNormalizationNotes = null;

        SemanticAdoptionAttempt adoption = null;
        AiQuerySemanticParseResult v2ForAdoption =
                querySemanticV2Raw != null && !querySemanticV2Raw.isParseMissing()
                        ? querySemanticV2Raw
                        : null;
        adoption = trySemanticAdoption(v2ForAdoption, previousTurn, normalized, today, explicitTentative);
        boolean timeContractFailed =
                adoption != null
                        && adoption.timeContract() != null
                        && !adoption.timeContract().valid();
        boolean frameClarificationRequired =
                adoption != null && adoption.frameClarificationRequired();
        boolean clarificationRequired =
                adoption == null || timeContractFailed || frameClarificationRequired;
        com.nongxinle.ai.semantic.SemanticTimeContractCheck.Result timeContractResult =
                adoption != null ? adoption.timeContract() : null;

        if (adoption != null && adoption.adopted()) {
            semanticAdoptedFrom = "v2";
            semanticAdoptedFields = describeAdoptedSemanticFields(adoption.semantic());
        } else if (timeContractFailed) {
            semanticFallbackReason =
                    "time_contract:" + blankToNullSemantic(adoption.timeContract().failureReason());
        } else if (frameClarificationRequired) {
            semanticFallbackReason =
                    "frame_validation:" + blankToNullSemantic(adoption.rejectionReason());
        } else {
            semanticFallbackReason = explainV2NonAdoption(querySemanticV2Raw);
        }

        if (adoption != null
                && adoption.semantic() != null
                && Boolean.TRUE.equals(adoption.semantic().getPurchaseSemanticFramePrimaryMerge())) {
            semanticFallbackUsed = Boolean.FALSE;
        }

        AiQuerySemanticParseResult semanticLlm =
                adoption != null ? adoption.semantic() : querySemanticV2Raw;

        AiResolvedQueryIntent mergedIntentStem =
                adoption != null && !timeContractFailed
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
                timeContractFailed
                        ? adoption.timeContract().clarificationQuestion()
                        : frameClarificationRequired
                                ? adoption.semanticClarificationQuestion().trim()
                                : clarificationRequired
                                        ? resolveSemanticClarificationQuestion(semanticLlm)
                                        : null;

        AiFollowUpResolution followUp =
                clarificationRequired
                        ? AiFollowUpResolver.clarificationFailureResolution(
                                orgScope, tentativeTime, normalized)
                        : AiFollowUpResolver.semanticStructuralBypassResolution(
                                previousTurn, mergedIntentStem, tentativeTime, orgScope, message, semanticLlm);

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
            // 防御：不应在 Production 主链落到这里；不做 Java 时间兜底或 tentative!=null 归因。
            timeWindow = explicitTentative;
            effectiveTimeSource = "UNRESOLVED";
            log.warn(
                    "[AiResolvedQueryContext] time contract missing on non-clarification path runId={} conversationId={}",
                    runId,
                    convId);
        }
        if (followUp != null) {
            followUp.setMergedTimeWindow(timeWindow);
            followUp.setEffectiveTimeWindowSource(effectiveTimeSource);
        }
        AiResolvedOrgScope mergedOrg = followUp != null && followUp.getMergedOrgScope() != null
                ? followUp.getMergedOrgScope()
                : orgScope;

        BusinessDrilldownRequestAssembler.Phase1PurchaseApplyResult phase1Purchase = null;

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
            phase1Purchase =
                    BusinessDrilldownRequestAssembler.applyPhase1PurchaseCapabilities(
                            normalized,
                            followUp,
                            previousTurn,
                            queryIntent,
                            semanticLlm);
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
        String answerBoundaryNote = AiResolvedTimeWindowDisplaySupport.buildCombinedBoundaryNote(
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
                                semanticLlm,
                                phase1Purchase);
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
        OrchestrationToolsMatrixReconcile matrixOrchReconcile =
                reconcileOrchestrationSelectedToolsFromBusinessMatrix(
                        followUp, queryIntent, orchestrationSelectedTools);
        orchestrationSelectedTools = matrixOrchReconcile.tools();
        if (StringUtils.hasText(matrixOrchReconcile.reasonNote())) {
            orchestrationReasonField =
                    StringUtils.hasText(orchestrationReasonField)
                            ? orchestrationReasonField.trim() + "; " + matrixOrchReconcile.reasonNote()
                            : matrixOrchReconcile.reasonNote();
        }
        if (!clarificationRequired && businessOverviewEffectiveRouting && odPart != null) {
            if (Boolean.TRUE.equals(orchestrationClarificationRequiredFlag)) {
                clarificationRequired = true;
                if (StringUtils.hasText(orchestrationClarificationQuestionField)) {
                    semanticClarificationQuestion = orchestrationClarificationQuestionField;
                } else if (!StringUtils.hasText(semanticClarificationQuestion)) {
                    semanticClarificationQuestion = SemanticParseFallbackPolicy.clarificationQuestion();
                }
            } else if (Boolean.TRUE.equals(orchestrationApprovalRequired)) {
                clarificationRequired = true;
                if (StringUtils.hasText(orchestrationReasonField)) {
                    semanticClarificationQuestion = "该操作需要确认：" + orchestrationReasonField.trim();
                } else if (!StringUtils.hasText(semanticClarificationQuestion)) {
                    semanticClarificationQuestion = "该操作需要确认后才能继续。";
                }
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

        LinkedHashMap<String, Object> businessFollowUpCapDebug = new LinkedHashMap<>();
        if (phase1Purchase != null && phase1Purchase.capabilityDebug() != null) {
            businessFollowUpCapDebug.putAll(phase1Purchase.capabilityDebug());
        }
        String effFollowUpAction =
                phase1Purchase != null ? phase1Purchase.proposedFollowUpAction() : null;
        String effFollowUpTargetType =
                phase1Purchase != null
                        ? blankToNullSemantic(phase1Purchase.proposedFollowUpTargetEntityType())
                        : null;
        String effFollowUpTargetName =
                phase1Purchase != null
                        ? blankToNullSemantic(phase1Purchase.proposedFollowUpTargetEntityName())
                        : null;
        String effFollowUpTargetId =
                phase1Purchase != null
                        ? blankToNullSemantic(phase1Purchase.proposedFollowUpTargetEntityId())
                        : null;
        String effFollowUpDetail =
                phase1Purchase != null ? phase1Purchase.proposedFollowUpDetailWanted() : null;
        String effFollowUpSourcePlan =
                phase1Purchase != null ? phase1Purchase.proposedFollowUpSourcePlanType() : null;

        if (!StringUtils.hasText(effFollowUpAction)
                && queryIntent != null
                && AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS.equals(queryIntent.getPathCode())) {
            BusinessDiagnosisDrilldownMatrix.DiagnosisStoreRiskFollowUpProbe diagRiskFollowUp =
                    BusinessDiagnosisDrilldownMatrix.probeStoreRiskReasonsInheritedFollowUp(
                            previousTurn, queryIntent, normalized);
            if (diagRiskFollowUp != null) {
                effFollowUpAction = diagRiskFollowUp.followUpAction();
                effFollowUpTargetType = diagRiskFollowUp.followUpTargetEntityType();
                effFollowUpTargetName = diagRiskFollowUp.followUpTargetEntityName();
                effFollowUpDetail = BusinessDiagnosisAgentV1.DIAGNOSIS_QUESTION_STORE_RISK_REASONS;
                effFollowUpSourcePlan = DiagnosisPlan.TYPE_OVERALL_BUSINESS_DIAGNOSIS;
            }
        }
        if (!StringUtils.hasText(effFollowUpAction)
                && queryIntent != null
                && AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(queryIntent.getPathCode())
                && previousTurn != null
                && AiResolvedQueryIntent.PATH_DISH_SALES_QUERY.equals(
                        blankToNullSemantic(previousTurn.getLastPathCode()))
                && DishSalesDrilldownMatrix.canAdoptDishSalesRankingAnchorProfitDrillDownFollowUp(
                        previousTurn, normalized)) {
            AiResultAnchor salesAnchor =
                    DishSalesDrilldownMatrix.resolveUniqueDishSalesRankingAnchor(
                            previousTurn.getLastResultAnchors());
            if (salesAnchor != null
                    && (StringUtils.hasText(salesAnchor.getEntityName())
                            || StringUtils.hasText(salesAnchor.getEntityId()))) {
                effFollowUpAction = "OBJECT_DRILLDOWN";
                effFollowUpTargetType = AiResultAnchor.ENTITY_TYPE_DISH;
                effFollowUpTargetName =
                        StringUtils.hasText(salesAnchor.getEntityName())
                                ? salesAnchor.getEntityName().trim()
                                : null;
                effFollowUpTargetId =
                        StringUtils.hasText(salesAnchor.getEntityId())
                                ? salesAnchor.getEntityId().trim()
                                : null;
                effFollowUpSourcePlan = salesAnchor.getSourcePlanType();
            }
        }

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
                .timeContractValid(
                        timeContractResult != null ? timeContractResult.valid() : null)
                .timeContractFailureReason(
                        timeContractResult != null && !timeContractResult.valid()
                                ? blankToNullSemantic(timeContractResult.failureReason())
                                : null)
                .semanticStoreNarrowingDebug(storeScopeNarrowDiag)
                .resolvedMatchedSemanticStoreMention(blankToNullSemantic(resolvedMatchedSemanticStoreMention))
                .semanticPrimaryVersion(blankToNullSemantic(semanticPrimaryVersion))
                .semanticFallbackUsed(semanticFallbackUsed)
                .semanticFallbackReason(blankToNullSemantic(semanticFallbackReason))
                .semanticAdoptedFrom(blankToNullSemantic(semanticAdoptedFrom))
                .purchaseSemanticFramePrimaryMerge(
                        semanticLlm != null ? semanticLlm.getPurchaseSemanticFramePrimaryMerge() : null)
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
                .querySemanticV2InputPreview(querySemanticV2InputPreview)
                .querySemanticV2(
                        semanticLlm == null
                                ? null
                                : AiQuerySemanticParseResultDebugSerializer.toSafeMap(semanticLlm))
                .querySemanticV2ParseMissing(semanticLlm == null ? null : semanticLlm.isParseMissing())
                .querySemanticV2Confidence(semanticLlm == null ? null : semanticLlm.getConfidence())
                .querySemanticV2TimeAction(
                        semanticLlm == null ? null : blankToNullSemantic(semanticLlm.getTimeAction()))
                .querySemanticV2ScopeAction(
                        semanticLlm == null ? null : blankToNullSemantic(semanticLlm.getScopeAction()))
                .querySemanticV2IntentAction(
                        semanticLlm == null ? null : blankToNullSemantic(semanticLlm.getIntentAction()))
                .querySemanticV2MetricAction(
                        semanticLlm == null ? null : blankToNullSemantic(semanticLlm.getMetricAction()))
                .querySemanticV2MentionedStoreNames(querySemanticV2EffectiveStoreNames(semanticLlm))
                .querySemanticV2MentionedDishName(
                        semanticLlm == null ? null : blankToNullSemantic(semanticLlm.getMentionedDishName()))
                .querySemanticV2RawText(
                        semanticLlm == null ? null : blankToNullSemantic(semanticLlm.getObservationLlmRawText()))
                .querySemanticV2ParseError(
                        semanticLlm == null ? null : blankToNullSemantic(semanticLlm.getObservationJsonParseError()))
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
                .followUpAction(blankToNullSemantic(effFollowUpAction))
                .followUpTargetEntityType(blankToNullSemantic(effFollowUpTargetType))
                .followUpTargetEntityId(blankToNullSemantic(effFollowUpTargetId))
                .followUpTargetEntityName(blankToNullSemantic(effFollowUpTargetName))
                .followUpDetailWanted(blankToNullSemantic(effFollowUpDetail))
                .followUpSourcePlanType(blankToNullSemantic(effFollowUpSourcePlan))
                .businessFollowUpCapabilityDebug(
                        businessFollowUpCapDebug.isEmpty() ? null : businessFollowUpCapDebug)
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
            AiResolvedQueryIntent mergedIntentStemForLog,
            boolean currentExplicitTimeMentioned,
            AiFollowUpResolution followUp,
            AiResolvedQueryContext ctx) {
        if (!log.isInfoEnabled()) {
            return;
        }
        var cur = mergedIntentStemForLog;
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
                    .explicitChildDepartmentIds(new ArrayList<>())
                    .expandedChildDepartmentIds(new ArrayList<>())
                    .visibleWarehouseIds(new ArrayList<>(whIds))
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
                        .explicitChildDepartmentIds(new ArrayList<>())
                        .expandedChildDepartmentIds(new ArrayList<>())
                        .visibleWarehouseIds(new ArrayList<>())
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
                .explicitChildDepartmentIds(new ArrayList<>())
                .expandedChildDepartmentIds(expandedChildren)
                .visibleWarehouseIds(new ArrayList<>())
                .targetDepartmentIds(new ArrayList<>())
                .queryScopeMode(AiResolvedDataScope.QUERY_SCOPE_MODE_STORE)
                .allVisibleStores(allStores)
                .allVisibleWarehouses(false)
                .build();
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
            AiQuerySemanticParseResult semLlm,
            BusinessDrilldownRequestAssembler.Phase1PurchaseApplyResult phase1Purchase) {
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
        if (phase1Purchase != null
                && AiResultAnchor.ENTITY_TYPE_DISH.equalsIgnoreCase(
                        blankToNullSemantic(phase1Purchase.proposedFollowUpTargetEntityType()))
                && StringUtils.hasText(phase1Purchase.proposedFollowUpTargetEntityName())) {
            return AiQuerySemanticLexicon.finalizeMentionedDishNameForDishProfit(
                    phase1Purchase.proposedFollowUpTargetEntityName().trim());
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
        if (previousTurn != null && previousTurn.getLastResultAnchors() != null) {
            AiResultAnchor salesAnchor =
                    DishSalesDrilldownMatrix.resolveUniqueDishSalesRankingAnchor(
                            previousTurn.getLastResultAnchors());
            if (salesAnchor != null && StringUtils.hasText(salesAnchor.getEntityName())) {
                return AiQuerySemanticLexicon.finalizeMentionedDishNameForDishProfit(
                        salesAnchor.getEntityName().trim());
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


    private static boolean equalsNormalizedStoreLabel(String dishHint, String storeLabel) {
        if (!StringUtils.hasText(dishHint) || !StringUtils.hasText(storeLabel)) {
            return false;
        }
        String a = dishHint.replace(" ", "").trim();
        String b = storeLabel.replace(" ", "").trim();
        return !a.isEmpty() && a.equals(b);
    }

    private static String resolveSemanticClarificationQuestion(AiQuerySemanticParseResult semanticLlm) {
        if (semanticLlm != null
                && Boolean.TRUE.equals(semanticLlm.getNeedClarification())
                && StringUtils.hasText(semanticLlm.getClarificationQuestion())) {
            return semanticLlm.getClarificationQuestion().trim();
        }
        return SemanticParseFallbackPolicy.clarificationQuestion();
    }

    /**
     * V2 解析失败（如 LLM 返回 prose）时，Matrix 钉住门店+单菜销量明细（R6），避免 intent/path=null。
     */
    private SemanticAdoptionAttempt tryDishSalesMatrixStoreSingleDishAdoption(
            AiConversationTurnMemory previousTurn,
            String normalized,
            LocalDate today,
            AiResolvedTimeWindow explicitTentative) {
        AiResolvedQueryIntent merged =
                AiQuerySemanticLlmMergeHelper.buildDishSalesMatrixStoreSingleDishIntent(
                        previousTurn, null, normalized);
        if (merged == null || !StringUtils.hasText(merged.getPathCode())) {
            return null;
        }
        AiQuerySemanticParseResult syntheticSem =
                AiQuerySemanticLlmMergeHelper.buildSyntheticSemanticForDishSalesStoreSingleDish(
                        normalized, previousTurn, today);
        return dishSalesMatrixAdoptionFromSynthetic(syntheticSem, merged, today, explicitTentative);
    }

    /** V2/LLM 不可用时，诊断 Matrix 多轮子域归因 / 改进行动收养（须留在 business_diagnosis_path）。 */
    private SemanticAdoptionAttempt tryBusinessDiagnosisDrilldownMatrixContinuationAdoption(
            AiConversationTurnMemory previousTurn,
            String normalized,
            LocalDate today,
            AiResolvedTimeWindow explicitTentative) {
        AiResolvedQueryIntent merged =
                AiQuerySemanticLlmMergeHelper.buildBusinessDiagnosisDrilldownContinuationIntent(
                        previousTurn, normalized);
        if (merged == null || !StringUtils.hasText(merged.getPathCode())) {
            return null;
        }
        AiQuerySemanticParseResult syntheticSem =
                AiQuerySemanticLlmMergeHelper.buildSyntheticSemanticForBusinessDiagnosisDrilldownContinuation(
                        normalized, previousTurn, today);
        if (syntheticSem == null || syntheticSem.getTime() == null) {
            return null;
        }
        SemanticTimeContractCheck.Result timeContract =
                SemanticTimeContractCheck.check(syntheticSem, null, today);
        if (timeContract == null || !timeContract.valid()) {
            SemanticTimeContractCheck.Result inherited =
                    SemanticTimeContractCheck.inheritFromPreviousTurn(previousTurn);
            if (inherited == null || !inherited.valid()) {
                return null;
            }
            timeContract = inherited;
        }
        AiResolvedTimeWindow tentative = timeContract.toTimeWindow(
                syntheticSem.getTime().getTimeType() != null
                        ? syntheticSem.getTime().getTimeType()
                        : AiResolvedTimeWindow.THIS_MONTH);
        if (tentative == null) {
            tentative = explicitTentative;
        }
        return new SemanticAdoptionAttempt(syntheticSem, merged, tentative, timeContract, null, null);
    }

    /** V2/LLM 不可用时，Matrix 问句形态收养（首轮排行等，建立 dish_sales_query_path）。 */
    private SemanticAdoptionAttempt tryDishSalesMatrixUtterancePinAdoption(
            AiConversationTurnMemory previousTurn,
            String normalized,
            LocalDate today,
            AiResolvedTimeWindow explicitTentative) {
        AiResolvedQueryIntent merged =
                AiQuerySemanticLlmMergeHelper.buildDishSalesMatrixUtterancePinIntent(
                        previousTurn, null, normalized);
        if (merged == null || !StringUtils.hasText(merged.getPathCode())) {
            return null;
        }
        AiQuerySemanticParseResult syntheticSem =
                AiQuerySemanticLlmMergeHelper.buildSyntheticSemanticForDishSalesMatrixUtterancePin(
                        normalized, previousTurn, today);
        return dishSalesMatrixAdoptionFromSynthetic(syntheticSem, merged, today, explicitTentative);
    }

    /** V2 解析失败时，Matrix 钉住集团口径单菜销量明细（R4）。 */
    private SemanticAdoptionAttempt tryDishSalesMatrixGroupSingleDishAdoption(
            AiConversationTurnMemory previousTurn,
            String normalized,
            LocalDate today,
            AiResolvedTimeWindow explicitTentative) {
        AiResolvedQueryIntent merged =
                AiQuerySemanticLlmMergeHelper.buildDishSalesMatrixGroupSingleDishIntent(
                        previousTurn, null, normalized);
        if (merged == null || !StringUtils.hasText(merged.getPathCode())) {
            return null;
        }
        AiQuerySemanticParseResult syntheticSem =
                AiQuerySemanticLlmMergeHelper.buildSyntheticSemanticForDishSalesGroupSingleDish(
                        normalized, previousTurn, today);
        return dishSalesMatrixAdoptionFromSynthetic(syntheticSem, merged, today, explicitTentative);
    }

    /**
     * V2 解析失败（如 LLM 返回 prose）时，Matrix 钉住销量排行追问，避免落入 NEED_SEMANTIC_CLARIFICATION。
     */
    private SemanticAdoptionAttempt tryDishSalesMatrixRankingFollowUpAdoption(
            AiConversationTurnMemory previousTurn,
            String normalized,
            LocalDate today,
            AiResolvedTimeWindow explicitTentative) {
        AiResolvedQueryIntent merged =
                AiQuerySemanticLlmMergeHelper.buildDishSalesMatrixRankingFollowUpIntent(previousTurn, normalized);
        if (merged == null || !StringUtils.hasText(merged.getPathCode())) {
            return null;
        }
        SemanticTimeContractCheck.Result timeContract =
                SemanticTimeContractCheck.inheritFromPreviousTurn(previousTurn);
        if (timeContract == null || !timeContract.valid()) {
            return null;
        }
        AiQuerySemanticParseResult syntheticSem =
                AiQuerySemanticLlmMergeHelper.buildSyntheticSemanticForDishSalesRankingFollowUp(
                        normalized, previousTurn);
        AiResolvedTimeWindow tentative =
                timeContract.toTimeWindow(
                        previousTurn != null && StringUtils.hasText(previousTurn.getLastTimeLabel())
                                ? previousTurn.getLastTimeLabel()
                                : null);
        if (tentative == null) {
            tentative = explicitTentative;
        }
        return new SemanticAdoptionAttempt(syntheticSem, merged, tentative, timeContract, null, null);
    }

    private SemanticAdoptionAttempt tryDishSalesMatrixCrossDomainProfitFollowUpAdoption(
            AiConversationTurnMemory previousTurn,
            String normalized,
            LocalDate today,
            AiResolvedTimeWindow explicitTentative) {
        AiResolvedQueryIntent merged =
                AiQuerySemanticLlmMergeHelper.buildDishSalesMatrixCrossDomainProfitFollowUpIntent(
                        previousTurn, normalized);
        if (merged == null || !StringUtils.hasText(merged.getPathCode())) {
            return null;
        }
        SemanticTimeContractCheck.Result timeContract =
                SemanticTimeContractCheck.inheritFromPreviousTurn(previousTurn);
        if (timeContract == null || !timeContract.valid()) {
            return null;
        }
        String wire = merged.getStructuredIntentDetail();
        AiQuerySemanticParseResult syntheticSem =
                AiQuerySemanticParseResult.builder()
                        .parseMissing(false)
                        .confidence(1.0d)
                        .followUp(true)
                        .intent(AiResolvedQueryIntent.DISH_SALES_QUERY)
                        .intentAction("INHERIT_PREVIOUS")
                        .timeAction("INHERIT_PREVIOUS")
                        .semanticSlots(
                                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                        .queryObject("DISH")
                                        .operation("DETAIL")
                                        .metric("GROSS_MARGIN")
                                        .structuredIntentDetailWire(wire)
                                        .build())
                        .time(
                                AiQuerySemanticParseResult.TimePart.builder()
                                        .timeType(
                                                StringUtils.hasText(previousTurn.getLastTimeLabel())
                                                        ? previousTurn.getLastTimeLabel()
                                                        : AiResolvedTimeWindow.CUSTOM)
                                        .startDate(previousTurn.getLastStartDate())
                                        .endDate(previousTurn.getLastEndDate())
                                        .timeSource(SemanticTimeContractCheck.SOURCE_INHERITED_PREVIOUS)
                                        .needInheritFromPrevious(true)
                                        .build())
                        .build();
        AiResolvedTimeWindow tentative =
                timeContract.toTimeWindow(
                        previousTurn != null && StringUtils.hasText(previousTurn.getLastTimeLabel())
                                ? previousTurn.getLastTimeLabel()
                                : null);
        if (tentative == null) {
            tentative = explicitTentative;
        }
        return new SemanticAdoptionAttempt(syntheticSem, merged, tentative, timeContract, null, null);
    }

    private SemanticAdoptionAttempt tryDishSalesRankingAnchorProfitDrillDownAdoption(
            AiConversationTurnMemory previousTurn,
            String normalized,
            LocalDate today,
            AiResolvedTimeWindow explicitTentative) {
        AiResolvedQueryIntent merged =
                AiQuerySemanticLlmMergeHelper.buildDishSalesRankingAnchorProfitDrillDownIntent(
                        previousTurn, normalized);
        if (merged == null || !StringUtils.hasText(merged.getPathCode())) {
            return null;
        }
        SemanticTimeContractCheck.Result timeContract =
                SemanticTimeContractCheck.inheritFromPreviousTurn(previousTurn);
        if (timeContract == null || !timeContract.valid()) {
            return null;
        }
        AiQuerySemanticParseResult syntheticSem =
                AiQuerySemanticLlmMergeHelper.buildSyntheticSemanticForDishSalesRankingAnchorProfitDrillDown(
                        normalized, previousTurn, today);
        if (syntheticSem == null) {
            return null;
        }
        AiResolvedTimeWindow tentative =
                timeContract.toTimeWindow(
                        previousTurn != null && StringUtils.hasText(previousTurn.getLastTimeLabel())
                                ? previousTurn.getLastTimeLabel()
                                : null);
        if (tentative == null) {
            tentative = explicitTentative;
        }
        return new SemanticAdoptionAttempt(syntheticSem, merged, tentative, timeContract, null, null);
    }

    private SemanticAdoptionAttempt dishSalesMatrixAdoptionFromSynthetic(
            AiQuerySemanticParseResult syntheticSem,
            AiResolvedQueryIntent merged,
            LocalDate today,
            AiResolvedTimeWindow explicitTentative) {
        if (syntheticSem == null || syntheticSem.getTime() == null) {
            return null;
        }
        SemanticTimeContractCheck.Result timeContract =
                SemanticTimeContractCheck.check(syntheticSem, null, today);
        if (timeContract == null || !timeContract.valid()) {
            return null;
        }
        AiResolvedTimeWindow tentative =
                timeContract.toTimeWindow(
                        syntheticSem.getTime().getTimeType() != null
                                ? syntheticSem.getTime().getTimeType()
                                : AiResolvedTimeWindow.THIS_MONTH);
        if (tentative == null) {
            tentative = explicitTentative;
        }
        return new SemanticAdoptionAttempt(syntheticSem, merged, tentative, timeContract, null, null);
    }

    private SemanticAdoptionAttempt trySemanticAdoption(
            AiQuerySemanticParseResult sem,
            AiConversationTurnMemory previousTurn,
            String normalized,
            LocalDate today,
            AiResolvedTimeWindow explicitTentative) {
        if (sem != null
                && Boolean.TRUE.equals(sem.getNeedClarification())
                && PurchaseFollowUpSlotSignals.isPurchaseOverviewSummaryScopeTimePivotFollowUp(
                        sem, previousTurn, normalized)) {
            sem.setNeedClarification(false);
            sem.setClarificationQuestion(null);
        }
        SemanticAdoptionAttempt diagnosisDrilldownContinuation =
                tryBusinessDiagnosisDrilldownMatrixContinuationAdoption(
                        previousTurn, normalized, today, explicitTentative);
        if (diagnosisDrilldownContinuation != null) {
            return diagnosisDrilldownContinuation;
        }
        SemanticAdoptionAttempt matrixStoreSingleDish =
                tryDishSalesMatrixStoreSingleDishAdoption(previousTurn, normalized, today, explicitTentative);
        if (matrixStoreSingleDish != null) {
            return matrixStoreSingleDish;
        }
        SemanticAdoptionAttempt matrixGroupSingleDish =
                tryDishSalesMatrixGroupSingleDishAdoption(previousTurn, normalized, today, explicitTentative);
        if (matrixGroupSingleDish != null) {
            return matrixGroupSingleDish;
        }
        SemanticAdoptionAttempt matrixRankingFollowUp =
                tryDishSalesMatrixRankingFollowUpAdoption(previousTurn, normalized, today, explicitTentative);
        if (matrixRankingFollowUp != null) {
            return matrixRankingFollowUp;
        }
        SemanticAdoptionAttempt matrixSalesAnchorProfit =
                tryDishSalesRankingAnchorProfitDrillDownAdoption(
                        previousTurn, normalized, today, explicitTentative);
        if (matrixSalesAnchorProfit != null) {
            return matrixSalesAnchorProfit;
        }
        SemanticAdoptionAttempt matrixCrossDomainProfit =
                tryDishSalesMatrixCrossDomainProfitFollowUpAdoption(
                        previousTurn, normalized, today, explicitTentative);
        if (matrixCrossDomainProfit != null) {
            return matrixCrossDomainProfit;
        }
        if (sem == null || SemanticParseFallbackPolicy.needSemanticParseClarification(sem, querySemanticMinConfidence)) {
            SemanticAdoptionAttempt matrixUtterancePin =
                    tryDishSalesMatrixUtterancePinAdoption(previousTurn, normalized, today, explicitTentative);
            if (matrixUtterancePin != null) {
                return matrixUtterancePin;
            }
            return null;
        }
        boolean purchaseFrameAdoption =
                !AiQuerySemanticLlmMergeHelper.currentTurnMapsToExplicitNonPurchasePath(sem)
                        && (AiQuerySemanticLlmMergeHelper.shouldUsePurchaseSemanticFrameAdoption(sem)
                                || PurchaseFollowUpSlotSignals.isEffectiveStructuralPurchaseFollowUp(
                                        sem, previousTurn, normalized))
                        && !AiQuerySemanticLlmMergeHelper.hasExplicitStockReduceRouteSignal(sem);
        if (purchaseFrameAdoption) {
            sem =
                    AiQuerySemanticSlotMerge.applyPreviousFrameInheritanceIfTemporalPurchaseFollowUp(
                            sem, previousTurn, normalized);
            // sourceFacet 主语义 → metric.purchaseSourceType，须在 Validator 前 reconcile，避免 compat 字段误伤。
            sem = AiQuerySemanticSlotMerge.reconcileMetricWithSourceFacet(sem);
            sem = AiQuerySemanticSlotMerge.reconcilePurchaseGoodsRankingSemanticSlots(sem);
            sem = AiQuerySemanticSlotMerge.reconcileAnswerPlanTypeFromWire(sem);
            sem = CurrentSemanticFrame.canonicalizePurchaseFollowUp(sem, previousTurn);
            CurrentSemanticFrame frame = CurrentSemanticFrame.buildFrame(sem);
            SemanticFrameValidationResult frameVal =
                    CurrentSemanticFrameValidator.validate(frame, sem, previousTurn, normalized);
            if (frameVal.needSemanticClarification()) {
                sem.setNeedClarification(true);
                String frameQuestion = frameVal.semanticClarificationQuestion();
                if (StringUtils.hasText(frameQuestion)) {
                    sem.setClarificationQuestion(frameQuestion);
                }
                List<String> frameCodes = frameVal.violationCodes();
                String frameRejectReason =
                        frameCodes != null && !frameCodes.isEmpty()
                                ? String.join(",", frameCodes)
                                : "frame_validation";
                return new SemanticAdoptionAttempt(
                        sem, null, null, null, frameRejectReason, frameQuestion);
            }
            sem = AiQuerySemanticSlotMerge.applyPreviousFrameInheritance(sem, previousTurn, normalized, false);
            sem.setPurchaseSemanticFramePrimaryMerge(true);
        } else {
            sem = AiQuerySemanticSlotMerge.applyPreviousFrameInheritance(sem, previousTurn, normalized, true);
        }
        AiResolvedQueryIntent baseline = AiResolvedQueryIntent.builder().build();
        AiResolvedQueryIntent merged =
                AiQuerySemanticLlmMergeHelper.mergeIntent(
                        baseline, sem, querySemanticMinConfidence, normalized, previousTurn);
        if (!StringUtils.hasText(merged.getPathCode())) {
            return null;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart alignedSlots =
                AiQuerySemanticSlotMerge.alignSemanticSlotsForTurnMemoryPersistence(
                        sem.getSemanticSlots(), merged.getStructuredIntentDetail());
        if (alignedSlots != sem.getSemanticSlots()) {
            sem = sem.toBuilder().semanticSlots(alignedSlots).build();
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
        com.nongxinle.ai.semantic.SemanticTimeContractCheck.Result timeContract =
                com.nongxinle.ai.semantic.SemanticTimeContractCheck.check(sem, previousTurn, today);
        return new SemanticAdoptionAttempt(sem, merged, tentative, timeContract, null, null);
    }

    private String explainV2NonAdoption(AiQuerySemanticParseResult v2) {
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
        if (StringUtils.hasText(r.getSemanticDomain())) {
            keys.add("domain");
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
        if (r.getSemanticSlots() != null) {
            keys.add("semanticSlots");
        }
        return keys.isEmpty() ? null : keys;
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

    /** V2 四域经营概览：仅换时间窗且 intent 继承时，补齐 orchestration 候选里可能被 LLM 截断的子 Agent/tool 列表。 */
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

    private record OrchestrationToolsMatrixReconcile(List<String> tools, String reasonNote) {}

    /**
     * 经营概览/诊断：Planner 工具表以 Matrix 为准；LLM {@code selectedTools} 冲突时写 reason 供 Debug。
     */
    private static OrchestrationToolsMatrixReconcile reconcileOrchestrationSelectedToolsFromBusinessMatrix(
            AiFollowUpResolution followUpRes,
            AiResolvedQueryIntent queryIntent,
            List<String> llmSelectedTools) {
        if (followUpRes == null || queryIntent == null) {
            return new OrchestrationToolsMatrixReconcile(llmSelectedTools, null);
        }
        String path = followUpRes.getEffectivePathCode();
        String wireRaw = queryIntent.getStructuredIntentDetail();
        String canon =
                StringUtils.hasText(wireRaw)
                        ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wireRaw.trim())
                        : null;
        List<String> matrixTools = null;
        String source = null;
        if (AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW.equals(path)
                && StringUtils.hasText(canon)
                && AiQuerySemanticLexicon.isStructuredBusinessOverviewFourDomainOrchestrationSurface(canon)) {
            matrixTools = BusinessOverviewDrilldownMatrix.defaultFourDomainPlannerTools();
            source = "business_overview_matrix";
        } else if (AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS.equals(path) && StringUtils.hasText(canon)) {
            matrixTools = BusinessDiagnosisDrilldownMatrix.plannerToolsForWire(canon);
            source =
                    BusinessDiagnosisDrilldownMatrix.isDualDomainPurchaseStockWire(canon)
                            ? "business_diagnosis_matrix_dual_domain"
                            : "business_diagnosis_matrix_four_domain";
        }
        if (matrixTools == null || matrixTools.isEmpty()) {
            return new OrchestrationToolsMatrixReconcile(llmSelectedTools, null);
        }
        String reason = null;
        if (llmSelectedTools != null
                && !llmSelectedTools.isEmpty()
                && !plannerToolListsEqual(llmSelectedTools, matrixTools)) {
            reason = "planner_tools_matrix_override_llm_selectedTools:" + source;
        }
        return new OrchestrationToolsMatrixReconcile(new ArrayList<>(matrixTools), reason);
    }

    private static boolean plannerToolListsEqual(List<String> a, List<String> b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null || a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            String ta = a.get(i) != null ? a.get(i).trim() : "";
            String tb = b.get(i) != null ? b.get(i).trim() : "";
            if (!ta.equals(tb)) {
                return false;
            }
        }
        return true;
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
