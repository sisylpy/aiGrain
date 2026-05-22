package com.nongxinle.ai.resolver;

import com.nongxinle.ai.context.AiResolvedOrgScope;
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
import com.nongxinle.ai.followup.rewrite.FollowUpRewriteRequest;
import com.nongxinle.ai.followup.rewrite.FollowUpRewriteResult;
import com.nongxinle.ai.followup.rewrite.llm.LlmFollowUpQueryRewriter;
import com.nongxinle.ai.security.AiPermissionDenied;
import com.nongxinle.ai.platform.dto.AiRunCreateRequest;
import com.nongxinle.ai.semantic.routing.SemanticDomainRouter;
import com.nongxinle.ai.semantic.routing.SemanticDomainRouterInput;
import com.nongxinle.ai.semantic.routing.SemanticDomainRouteResult;
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
 * <p>职责分区（编排保留在此，域特殊规则外移）：
 * <ul>
 *   <li>FollowUp Rewrite — {@link LlmFollowUpQueryRewriter} + rewrite debug 写入</li>
 *   <li>Semantic Router / ContractSelector / Parser — {@link SemanticDomainRouter}、{@link DomainContractSelector}、{@link AiQuerySemanticLlmParser}</li>
 *   <li>语义采纳 — {@link SemanticAdoptionPipeline#tryAdopt}</li>
 *   <li>Contract Validation / StrictDecision — {@link SemanticContractValidationPipeline#run}</li>
 *   <li>Org / DataScope 权限装配 — {@link AiResolvedOrgScopeAssembler#resolveOrgScope} / {@link AiResolvedOrgScopeAssembler#buildDataScope}</li>
 *   <li>Time / 语义门店 narrowing — {@link AiFollowUpResolver}、{@link AiMultiTurnOrgScopePolicy}、{@link SemanticScopeNarrowingPolicy}</li>
 *   <li>Harness 多店范围 — {@link SemanticHarnessScopePolicy#buildHarnessMultiStoreSnapshot}</li>
 *   <li>Orchestration 业务修正 — {@link SemanticOrchestrationDecisionReconciler#reconcile}</li>
 *   <li>Context 装配 — {@link AiResolvedQueryContextAssemblySupport#assemble}</li>
 *   <li>诊断日志 — {@link AiResolvedQueryContextDiagnostics}</li>
 *   <li>权限点名 — {@link SemanticPermissionMentionPolicy}</li>
 * </ul>
 * 用户话术不做 Java 关键词语义解析；组织树与门店收窄口径由本类集中处理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiResolvedQueryContextResolver {

    private final AiConversationMemoryService conversationMemoryService;
    private final AiQuerySemanticLlmParser querySemanticLlmParser;
    private final LlmFollowUpQueryRewriter followUpQueryRewriter;
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
        diagnostics.logResolveStart(runId, convId, uid, normalized, previousTurn);

        AiResolvedOrgScope orgScope = orgScopeAssembler.resolveOrgScope(userContext, effectiveDept, request);

        FollowUpRewriteRequest rewriteRequest =
                FollowUpRewriteRequest.from(message, normalized, today, previousTurn, orgScope);
        int previousTurnResultAnchorsCount = countResultAnchors(previousTurn != null ? previousTurn.getLastResultAnchors() : null);
        int rewritePromptResultAnchorsCount = countResultAnchors(rewriteRequest.getResultAnchors());
        FollowUpRewriteResult rewriteResult = followUpQueryRewriter.rewrite(rewriteRequest);
        boolean rewriteClarificationRequired =
                rewriteResult != null
                        && rewriteResult.isNeedClarification()
                        && StringUtils.hasText(rewriteResult.getClarificationQuestion());
        boolean followUpRewriteApplied =
                !rewriteClarificationRequired
                        && rewriteResult != null
                        && rewriteResult.isCanRewrite();
        String effectiveUserMessage =
                followUpRewriteApplied && StringUtils.hasText(rewriteResult.getCompletedUserQuery())
                        ? rewriteResult.getCompletedUserQuery().trim()
                        : normalized;
        AiConversationTurnMemory previousTurnForParser =
                followUpRewriteApplied
                        ? SemanticParserInputBuilder.reducePreviousTurnForFollowUpRewrite(previousTurn)
                        : previousTurn;

        Map<String, Object> querySemanticV2InputPreview = null;
        AiQuerySemanticParseResult querySemanticV2Raw = null;
        SemanticDomainRouteResult semanticDomainRoute = null;
        DomainContractSelectionResult domainContractSelection = null;
        SemanticContractValidationDebug semanticContractValidation = null;
        SemanticContractStrictDecision semanticContractStrictDecision = null;
        if (!rewriteClarificationRequired) {
            semanticDomainRoute =
                    SemanticDomainRouter.INSTANCE.route(
                            SemanticDomainRouterInput.builder()
                                    .rewrittenUserMessage(effectiveUserMessage)
                                    .previousTurn(previousTurnForParser)
                                    .build());
            domainContractSelection = DomainContractSelector.select(semanticDomainRoute);
            try {
                var v2In =
                        SemanticParserInputBuilder.build(
                                effectiveUserMessage,
                                today,
                                previousTurnForParser,
                                orgScope,
                                semanticDomainRoute,
                                domainContractSelection);
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
        adoption =
                semanticAdoptionPipeline.tryAdopt(
                        new SemanticAdoptionPipeline.Request(
                                v2ForAdoption,
                                previousTurnForParser,
                                effectiveUserMessage,
                                today,
                                explicitTentative,
                                followUpRewriteApplied,
                                domainContractSelection,
                                followUpRewriteApplied && rewriteResult != null
                                        ? AiResolvedQueryContextDebugFactory.blankToNullSemantic(
                                                rewriteResult.getInheritedAnchorType())
                                        : null,
                                followUpRewriteApplied && rewriteResult != null
                                        ? AiResolvedQueryContextDebugFactory.blankToNullSemantic(
                                                rewriteResult.getInheritedAnchorName())
                                        : null));
        SemanticContractValidationPipeline.Result contractPipeline =
                SemanticContractValidationPipeline.run(
                        new SemanticContractValidationPipeline.Request(
                                rewriteClarificationRequired,
                                domainContractSelection,
                                adoption,
                                querySemanticV2Raw,
                                previousTurnForParser,
                                followUpRewriteApplied,
                                rewriteResult,
                                semanticDomainRoute,
                                semanticContractStrictProperties.isEnabled()));
        semanticContractValidation = contractPipeline.semanticContractValidation();
        semanticContractStrictDecision = contractPipeline.semanticContractStrictDecision();
        boolean timeContractFailed =
                adoption != null
                        && adoption.timeContract() != null
                        && !adoption.timeContract().valid();
        boolean frameClarificationRequired =
                adoption != null && adoption.frameClarificationRequired();
        boolean contractStrictClarificationRequired =
                semanticContractStrictDecision != null
                        && semanticContractStrictDecision.isEnforceClarification();
        boolean clarificationRequired =
                rewriteClarificationRequired
                        || adoption == null
                        || timeContractFailed
                        || frameClarificationRequired
                        || contractStrictClarificationRequired;
        com.nongxinle.ai.semantic.SemanticTimeContractCheck.Result timeContractResult =
                adoption != null ? adoption.timeContract() : null;

        if (adoption != null && adoption.adopted()) {
            semanticAdoptedFrom = "v2";
            semanticAdoptedFields =
                    AiResolvedQueryContextDebugFactory.describeAdoptedSemanticFields(adoption.semantic());
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
                            + AiResolvedQueryContextDebugFactory.blankToNullSemantic(adoption.rejectionReason());
        } else if (contractStrictClarificationRequired) {
            semanticFallbackReason =
                    "model_contract_violation:"
                            + (semanticContractStrictDecision.getViolationCode() != null
                                    ? semanticContractStrictDecision.getViolationCode().name()
                                    : "UNKNOWN");
        } else {
            semanticFallbackReason = rewriteClarificationRequired
                    ? "follow_up_rewrite_clarification"
                    : diagnostics.explainV2NonAdoption(querySemanticV2Raw, querySemanticMinConfidence);
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
                rewriteClarificationRequired
                        ? rewriteResult.getClarificationQuestion().trim()
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
                                followUpRewriteApplied);

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
            diagnostics.logTimeContractMissingOnNonClarificationPath(runId, convId);
        }
        if (followUp != null) {
            followUp.setMergedTimeWindow(timeWindow);
            followUp.setEffectiveTimeWindowSource(effectiveTimeSource);
        }
        return assemblySupport.assemble(
                new AiResolvedQueryContextAssemblySupport.AssembleRequest(
                        runId,
                        convId,
                        uid,
                        userContext,
                        request,
                        message,
                        normalized,
                        effectiveUserMessage,
                        followUpRewriteApplied,
                        previousTurn,
                        orgScope,
                        rewriteResult,
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
                        rewritePromptResultAnchorsCount,
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
                        applyStructuralLlm));
    }


    /**
     * Run 请求体中的 {@code distributerId} 优先于用户表快照，避免集团账号挂靠部门与主体 ID 不一致时只展开一家门店。
     */
    public static Long mergedDistributerId(AiRunCreateRequest request, AiUserContext ctx) {
        return AiResolvedOrgScopeAssembler.mergedDistributerId(request, ctx);
    }

    /**
     * {@link com.nongxinle.ai.graph.business.BusinessScopeIntersectNode} 收窄 Run 锚点后，同步
     * {@link AiResolvedQueryContext#getOrgScope()} / {@link AiResolvedQueryContext#getDataScope()}。
     */
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
