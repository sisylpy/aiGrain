package com.nongxinle.ai.platform;

import com.alibaba.fastjson2.JSON;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.context.AiUserContext;
import com.nongxinle.ai.context.AiUserContextResolver;
import com.nongxinle.ai.platform.dto.StopRunOutcome;
import com.nongxinle.ai.resolver.AiResolvedQueryContextResolver;
import com.nongxinle.ai.core.AiGraphRunner;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.platform.dto.AiRunCreateRequest;
import com.nongxinle.ai.platform.dto.AiRunStartResult;
import com.nongxinle.ai.scope.AiConversationScopeMode;
import com.nongxinle.entity.GbAiConversationEntity;
import com.nongxinle.service.GbAiChatService;
import com.nongxinle.ai.planner.BusinessDiagnosisCompositeExecutionMode;
import com.nongxinle.ai.planner.BusinessDiagnosisCompositeExecutionResult;
import com.nongxinle.ai.planner.BusinessDiagnosisCompositeExecutionService;
import com.nongxinle.ai.planner.BusinessDiagnosisCompositeGateResult;
import com.nongxinle.ai.planner.BusinessDiagnosisCompositeProductionGate;
import com.nongxinle.ai.planner.ShadowDecision;
import com.nongxinle.ai.planner.ShadowPolicy;
import com.nongxinle.ai.security.AiPermissionDenied;
import com.nongxinle.ai.trace.AiAgentTraceService;
import com.nongxinle.ai.followup.AiFollowUpConversationMemory;
import com.nongxinle.ai.followup.FollowUpIntentResolveService;
import com.nongxinle.ai.followup.AiFollowUpIntentSnapshot;
import com.nongxinle.ai.harness.AiHarnessResolvedContextSummarizer;
import com.nongxinle.ai.trace.AiRunSession;
import com.nongxinle.ai.trace.AiRunSessionRegistry;
import com.nongxinle.ai.conversation.AiConversationMemoryService;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.trace.AiSseEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiRunService {

    private final AiRunSessionRegistry sessionRegistry;
    private final AiSseEventPublisher eventPublisher;
    private final AiGraphRunner graphRunner;
    private final AiRunAsyncExecutor asyncExecutor;
    private final AiAgentTraceService traceService;
    private final AiUserContextResolver userContextResolver;
    private final AiFollowUpConversationMemory followUpConversationMemory;
    private final AiResolvedQueryContextResolver resolvedQueryContextResolver;
    private final AiConversationMemoryService conversationMemoryService;
    private final GbAiChatService gbAiChatService;

    /** C-58：仅 Harness {@code GRAPH_RUN} 在满足条件时跑 Composite PlannerExecutor（不替换主链路终稿）。 */
    private final BusinessDiagnosisCompositeExecutionService businessDiagnosisCompositeExecutionService;

    /** C-63：SHADOW Composite 灰度闸（不参与 Harness_only）。 */
    private final ShadowPolicy shadowPolicy;

    @Value("${ai.harness.debug-context-enabled:false}")
    private boolean harnessDebugContextEnabled;

    /**
     * C-55 / C-56：是否允许 Composite 生产放行（当前仅观测 Gate，不接 PlannerExecutor）；默认 false。
     */
    @Value("${ai.composite.businessDiagnosis.productionEnabled:true}")
    private boolean compositeBusinessDiagnosisProductionEnabled;

    /**
     * C-60：普通 Run Composite 执行模式 spring 配置 {@code OFF|HARNESS_ONLY|SHADOW|PRIMARY}；
     * 仅 {@code SHADOW} 在 {@link #executeRun(long)} 中旁路 PlannerExecutor（{@code PRIMARY} 不接）。
     */
    @Value("${ai.composite.businessDiagnosis.executionMode:SHADOW}")
    private String compositeBusinessDiagnosisExecutionModeSpring;

    public AiRunStartResult startRun(AiRunCreateRequest req) {
        if (req == null || req.getUserId() == null) {
            throw new IllegalArgumentException("userId required");
        }
        if (!StringUtils.hasText(req.getMessage())) {
            throw new IllegalArgumentException("message required");
        }

        if (req.getConversationId() == null) {
            AiConversationScopeMode mode = inferAgentRunScopeMode(req);
            GbAiConversationEntity conv = gbAiChatService.createNewConversationForAgentRun(
                    req.getDepartmentId(), req.getDistributerId(), mode, req.getUserId(), 0);
            req.setConversationId(conv.getGbAiConversationId());
            log.info("[AiRunService] created conversationId={} userId={} mode={}",
                    conv.getGbAiConversationId(), req.getUserId(), mode);
        } else {
            gbAiChatService.requireConversationOwnedByUser(req.getConversationId(), req.getUserId());
        }

        long runId = sessionRegistry.nextRunId();

        var uc = userContextResolver.resolve(req);
        AiResolvedQueryContext resolved = resolvedQueryContextResolver.resolve(runId, req, uc);

        AiRunState state = newRunStateFromResolved(runId, req, uc, resolved);
        maybeAppendOutOfScopeMentionDenial(resolvedQueryContextResolver, resolved, state);
        recordCompositeProductionGateObservation(state, null);

        logResolvedQueryContext(runId, req.getConversationId(), resolved);
        logHarnessTurnMemory(runId, req.getConversationId(), resolved);

        AiRunSession session = new AiRunSession(runId, state);
        sessionRegistry.register(session);

        asyncExecutor.runAsync(() -> executeRun(runId));
        return new AiRunStartResult(runId, req.getConversationId());
    }

    private static void maybeAppendOutOfScopeMentionDenial(
            AiResolvedQueryContextResolver resolver, AiResolvedQueryContext resolved, AiRunState state) {
        if (resolver == null || resolved == null || state == null) {
            return;
        }
        Optional<AiPermissionDenied> d = resolver.maybeDenialForSemanticMentionsOutsideVisibleStores(resolved);
        d.ifPresent(denial -> state.getPermissionDenials().add(denial));
    }

    private static AiRunState newRunStateFromResolved(
            long runId, AiRunCreateRequest req, AiUserContext uc, AiResolvedQueryContext resolved) {
        String normalizedInput = resolved.getNormalizedQuestion();
        if (!StringUtils.hasText(normalizedInput)) {
            normalizedInput = req.getMessage().trim();
        }
        return AiRunState.builder()
                .runId(runId)
                .conversationId(req.getConversationId())
                .userId(req.getUserId())
                .departmentId(req.getDepartmentId())
                .distributerId(req.getDistributerId())
                .aiUserContext(uc)
                .resolvedQueryContext(resolved)
                .userRole(uc.getRoleCode())
                .rawUserInput(req.getMessage())
                .normalizedUserInput(normalizedInput)
                .needClarification(resolved.isNeedSemanticClarification())
                .clarificationQuestion(
                        resolved.isNeedSemanticClarification()
                                        && StringUtils.hasText(resolved.getSemanticClarificationQuestion())
                                ? resolved.getSemanticClarificationQuestion()
                                : null)
                .build();
    }

    /**
     * C-55 / C-56.2：仅观测 Composite 生产 Gate；不改变路由、不执行 Composite {@code PlannerExecutor}。
     *
     * @param harnessProductionEnabledOverride 仅 Harness {@code GRAPH_RUN} 传入；{@code null} 使用 Spring 配置。
     */
    private void recordCompositeProductionGateObservation(AiRunState state, Boolean harnessProductionEnabledOverride) {
        if (state == null) {
            return;
        }
        boolean effectiveEnabled =
                harnessProductionEnabledOverride != null
                        ? harnessProductionEnabledOverride
                        : compositeBusinessDiagnosisProductionEnabled;
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        BusinessDiagnosisCompositeGateResult gateResult =
                BusinessDiagnosisCompositeProductionGate.evaluate(rq, state, effectiveEnabled);
        Map<String, Object> gd = gateResult.getDebug();
        if (gd == null) {
            gd = new LinkedHashMap<>();
            gateResult.setDebug(gd);
        }
        gd.put(
                "productionEnabledSource",
                harnessProductionEnabledOverride != null ? "HARNESS_OVERRIDE" : "CONFIG");
        gd.put("productionEnabledEffective", effectiveEnabled);
        state.setBusinessDiagnosisCompositeGateResult(gateResult);
        if (log.isInfoEnabled() && gateResult != null) {
            log.info(
                    "[AiRunService] compositeProductionGate runId={} conversationId={} allowed={} reasonCode={} "
                            + "recommendedCaseKind={} productionEnabledSource={} productionEnabledEffective={}",
                    state.getRunId(),
                    state.getConversationId(),
                    gateResult.isAllowed(),
                    gateResult.getReasonCode(),
                    gateResult.getRecommendedCaseKind(),
                    gd.get("productionEnabledSource"),
                    effectiveEnabled);
        }
    }

    /**
     * Harness Replay：同步执行与 {@link #executeRun} 相同的 Business Graph（无 Session 注册、无 SSE、无异步），
     * 并成功路径下写入 Turn / Follow-up 记忆；{@code frozenClockDate} 经 {@code today} 传入 Resolver。
     *
     * @param compositeProductionGateProductionEnabledOverride C-56.2：仅 Harness 传入；{@code null} 使用 Spring 配置
     * @param compositeBusinessDiagnosisExecutionMode C-58：{@code OFF}/{@code HARNESS_ONLY}/… API 字符串；{@code null}→OFF；
     *     仅 {@code HARNESS_ONLY} 会尝试 Composite PlannerExecutor
     */
    public AiRunState executeBusinessGraphSyncForHarness(
            AiRunCreateRequest req,
            LocalDate today,
            long runId,
            Boolean compositeProductionGateProductionEnabledOverride,
            String compositeBusinessDiagnosisExecutionMode) {
        if (req == null || req.getUserId() == null) {
            throw new IllegalArgumentException("userId required");
        }
        if (!StringUtils.hasText(req.getMessage())) {
            throw new IllegalArgumentException("message required");
        }
        if (req.getConversationId() == null) {
            throw new IllegalArgumentException("conversationId required for harness graph replay");
        }
        if (today == null) {
            throw new IllegalArgumentException("today required");
        }
        gbAiChatService.requireConversationOwnedByUser(req.getConversationId(), req.getUserId());

        var uc = userContextResolver.resolve(req);
        AiResolvedQueryContext resolved = resolvedQueryContextResolver.resolve(runId, req, uc, today);
        resolved.setRunId(runId);

        AiRunState state = newRunStateFromResolved(runId, req, uc, resolved);
        maybeAppendOutOfScopeMentionDenial(resolvedQueryContextResolver, resolved, state);
        recordCompositeProductionGateObservation(state, compositeProductionGateProductionEnabledOverride);
        logResolvedQueryContext(runId, req.getConversationId(), resolved);
        logHarnessTurnMemory(runId, req.getConversationId(), resolved);

        long t0 = System.currentTimeMillis();
        traceService.insertRunStarting(runId, state);
        AiRunState ended = state;
        String statusName = AiRunStatus.COMPLETED.name();
        try {
            ended = graphRunner.runBusinessGraph(state);
            if (!ended.isCancelled()) {
                maybeExecuteHarnessCompositePlanner(ended, compositeBusinessDiagnosisExecutionMode);
            } else {
                ended.setBusinessDiagnosisCompositeExecutionResult(null);
            }
            if (ended.isCancelled()) {
                statusName = AiRunStatus.CANCELLED.name();
            }
        } catch (RuntimeException e) {
            statusName = AiRunStatus.FAILED.name();
            log.warn("[AiRunService] harness sync runId={} failed: {}", runId, e.toString(), e);
            throw e;
        } finally {
            String workspaceMode = ended.getWorkspaceMode() == null ? null : ended.getWorkspaceMode().name();
            traceService.updateRunFinished(runId, statusName, ended, System.currentTimeMillis() - t0, workspaceMode);
        }
        if (!ended.isCancelled()) {
            AiConversationTurnMemory turnMemory = AiConversationTurnMemory.fromCompletedState(ended);
            if (turnMemory != null && ended.getUserId() != null) {
                conversationMemoryService.rememberCompletedTurn(
                        ended.getUserId(), ended.getConversationId(), turnMemory);
            }
            AiFollowUpIntentSnapshot snap = FollowUpIntentResolveService.snapshotFromCompletedState(ended);
            if (snap != null && ended.getUserId() != null) {
                followUpConversationMemory.remember(ended.getUserId(), ended.getConversationId(), snap);
            }
        }
        return ended;
    }

    /**
     * C-58：仅 Harness 同步图跑完后写入 {@link AiRunState#getBusinessDiagnosisCompositeExecutionResult()}；
     * 不修改 {@link AiRunState#getFinalAnswerText()}。
     */
    private void maybeExecuteHarnessCompositePlanner(AiRunState ended, String compositeBusinessDiagnosisExecutionModeRaw) {
        if (ended == null) {
            return;
        }
        BusinessDiagnosisCompositeExecutionMode mode =
                BusinessDiagnosisCompositeExecutionMode.fromHarnessApiString(compositeBusinessDiagnosisExecutionModeRaw);
        if (mode != BusinessDiagnosisCompositeExecutionMode.HARNESS_ONLY) {
            ended.setBusinessDiagnosisCompositeExecutionResult(null);
            return;
        }
        BusinessDiagnosisCompositeExecutionResult result =
                businessDiagnosisCompositeExecutionService.tryExecute(
                        ended,
                        ended.getResolvedQueryContext(),
                        ended.getBusinessDiagnosisCompositeGateResult(),
                        mode);
        ended.setBusinessDiagnosisCompositeExecutionResult(result);
    }

    /**
     * C-60：普通异步 Run — legacy {@code Graph} 完结且未 cancel 后旁路 Composite；不写 {@link AiRunState#getFinalAnswerText()}。
     * C-61：写入 {@code compositeShadow*} 观测（耗时、两侧是否有正文、未替换契约）。
     * 同步于主线程（后续可异步化）；Composite 异常吞掉。
     */
    private void maybeExecuteShadowCompositePlanner(AiRunState ended) {
        if (ended == null || ended.isCancelled()) {
            return;
        }
        if (!compositeBusinessDiagnosisProductionEnabled) {
            return;
        }
        BusinessDiagnosisCompositeExecutionMode cfg =
                BusinessDiagnosisCompositeExecutionMode.fromHarnessApiString(compositeBusinessDiagnosisExecutionModeSpring);
        if (cfg != BusinessDiagnosisCompositeExecutionMode.SHADOW) {
            return;
        }
        BusinessDiagnosisCompositeGateResult gate = ended.getBusinessDiagnosisCompositeGateResult();
        if (gate == null || !gate.isAllowed()) {
            return;
        }
        ShadowDecision shadowDecision = shadowPolicy.evaluate(ended, ended.getResolvedQueryContext());
        if (!shadowDecision.isAllowed()) {
            ended.setBusinessDiagnosisCompositeExecutionResult(buildShadowSkippedObservation(ended, shadowDecision));
            return;
        }
        long shadowT0 = System.nanoTime();
        try {
            BusinessDiagnosisCompositeExecutionResult result =
                    businessDiagnosisCompositeExecutionService.tryExecute(
                            ended,
                            ended.getResolvedQueryContext(),
                            gate,
                            BusinessDiagnosisCompositeExecutionMode.SHADOW);
            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - shadowT0);
            ended.setBusinessDiagnosisCompositeExecutionResult(
                    enrichShadowExecutionObservation(ended, latencyMs, result));
        } catch (RuntimeException ex) {
            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - shadowT0);
            log.warn(
                    "[AiRunService] shadow composite runId={} failed (legacy answer unaffected): {}",
                    ended.getRunId(),
                    ex.toString(),
                    ex);
            String msg = ex.getMessage() == null ? "" : ex.getMessage().trim();
            boolean legacyPresent = shadowAnswerTextPresent(ended.getFinalAnswerText());
            ended.setBusinessDiagnosisCompositeExecutionResult(
                    BusinessDiagnosisCompositeExecutionResult.builder()
                            .mode(BusinessDiagnosisCompositeExecutionMode.SHADOW)
                            .executed(true)
                            .success(false)
                            .fallbackRequired(true)
                            .fallbackReason("COMPOSITE_SHADOW_EXCEPTION")
                            .errorCode("COMPOSITE_SHADOW_EXCEPTION")
                            .errorMessage(
                                    ex.getClass().getSimpleName()
                                            + (msg.isEmpty() ? "" : ": ".concat(msg)))
                            .businessDiagnosisCompositeAnswerPlan(null)
                            .composeResult(null)
                            .plannerExecutorTrace(null)
                            .plannerOverallStatus(null)
                            .degradedSteps(List.of())
                            .compositeShadowLatencyMs(latencyMs)
                            .compositeShadowLegacyAnswerPresent(legacyPresent)
                            .compositeShadowCompositeAnswerPresent(false)
                            .compositeShadowComparedWithLegacy(false)
                            .compositeShadowFinalAnswerReplaced(false)
                            .compositeShadowSkipped(Boolean.FALSE)
                            .compositeShadowSkipReason(null)
                            .compositeShadowThrottleHit(Boolean.FALSE)
                            .compositeShadowWhitelistMatched(Boolean.TRUE)
                            .build());
        }
    }

    /** C-63：未进入 Composite {@code tryExecute} 时仍写出灰度观测，不影响 legacy。 */
    private static BusinessDiagnosisCompositeExecutionResult buildShadowSkippedObservation(
            AiRunState ended, ShadowDecision shadowDecision) {
        return BusinessDiagnosisCompositeExecutionResult.builder()
                .mode(BusinessDiagnosisCompositeExecutionMode.SHADOW)
                .executed(false)
                .success(false)
                .fallbackRequired(false)
                .fallbackReason(null)
                .errorCode(null)
                .errorMessage(null)
                .businessDiagnosisCompositeAnswerPlan(null)
                .composeResult(null)
                .plannerExecutorTrace(null)
                .plannerOverallStatus(null)
                .compositeShadowSkipped(Boolean.TRUE)
                .compositeShadowSkipReason(shadowDecision.getSkipReason())
                .compositeShadowThrottleHit(Boolean.TRUE.equals(shadowDecision.getThrottleHit()))
                .compositeShadowWhitelistMatched(shadowDecision.getWhitelistMatched())
                .compositeShadowLatencyMs(null)
                .compositeShadowComparedWithLegacy(null)
                .compositeShadowLegacyAnswerPresent(null)
                .compositeShadowCompositeAnswerPresent(null)
                .compositeShadowFinalAnswerReplaced(null)
                .build();
    }

    private static boolean shadowAnswerTextPresent(String text) {
        return text != null && !text.trim().isEmpty();
    }

    private static String shadowCompositeFinalText(BusinessDiagnosisCompositeExecutionResult r) {
        if (r == null || r.getComposeResult() == null) {
            return null;
        }
        return r.getComposeResult().getFinalAnswerText();
    }

    /** C-61：不读用户原文；仅基于 {@code finalAnswerText} / Composer 产出填观测。 */
    private static BusinessDiagnosisCompositeExecutionResult enrichShadowExecutionObservation(
            AiRunState ended, long latencyMs, BusinessDiagnosisCompositeExecutionResult base) {
        if (base == null) {
            return null;
        }
        boolean legacyPresent = shadowAnswerTextPresent(ended != null ? ended.getFinalAnswerText() : null);
        boolean compositePresent = shadowAnswerTextPresent(shadowCompositeFinalText(base));
        return base.toBuilder()
                .compositeShadowSkipped(Boolean.FALSE)
                .compositeShadowSkipReason(null)
                .compositeShadowThrottleHit(Boolean.FALSE)
                .compositeShadowWhitelistMatched(Boolean.TRUE)
                .compositeShadowLatencyMs(latencyMs)
                .compositeShadowLegacyAnswerPresent(legacyPresent)
                .compositeShadowCompositeAnswerPresent(compositePresent)
                .compositeShadowComparedWithLegacy(legacyPresent && compositePresent)
                .compositeShadowFinalAnswerReplaced(false)
                .build();
    }

    /** C-60：`compositeGate*` + `compositeExecution*` 独立于 harness 摘要开关下发 SSE。 */
    private void envelopePutCompositeGateAndExecution(AiRunState state, Map<String, Object> envelope) {
        if (state == null || envelope == null) {
            return;
        }
        try {
            envelope.putAll(AiHarnessResolvedContextSummarizer.summarizeCompositeGateAndExecutionOnly(state));
        } catch (RuntimeException ex) {
            log.debug("[AiRunService] summarizeCompositeGateAndExecutionOnly skipped: {}", ex.toString());
        }
    }

    private static AiConversationScopeMode inferAgentRunScopeMode(AiRunCreateRequest req) {
        if (StringUtils.hasText(req.getScopeMode())) {
            return AiConversationScopeMode.fromApiString(req.getScopeMode());
        }
        if (req.getDepartmentId() != null) {
            return AiConversationScopeMode.STORE;
        }
        if (req.getDistributerId() != null) {
            return AiConversationScopeMode.GROUP;
        }
        throw new IllegalArgumentException(
                "创建会话需要 departmentId（单店）或 distributerId（集团），或显式传入 scopeMode");
    }

    /** 异步入口：在同一阶段仅跑 Business Graph；后续按 workspaceMode 派发多图。 */
    public void executeRun(long runId) {
        AiRunSession session = sessionRegistry.get(runId)
                .orElseThrow(() -> new IllegalArgumentException("run not found: " + runId));

        long t0 = System.currentTimeMillis();
        AiRunState endedState = session.getState();
        session.setStatus(AiRunStatus.RUNNING);
        LinkedHashMap<String, Object> runStarted = new LinkedHashMap<>();
        runStarted.put("displayText", "任务已接收，开始执行…");
        enrichSseEnvelopeWithHarnessDebug(session.getState(), runStarted);
        envelopePutCompositeGateAndExecution(session.getState(), runStarted);
        eventPublisher.publish(runId, "run_started", runStarted);
        traceService.insertRunStarting(runId, session.getState());
        try {
            endedState = graphRunner.runBusinessGraph(session.getState());
            if (!endedState.isCancelled()) {
                maybeExecuteShadowCompositePlanner(endedState);
            }

            String answerText = endedState.getFinalAnswerText() == null ? "" : endedState.getFinalAnswerText();
            Map<String, Object> data = new HashMap<>();
            data.put("text", answerText);
            if (endedState.getCostDiagnosisResult() != null) {
                try {
                    data.put("costDiagnosis", JSON.parseObject(JSON.toJSONString(endedState.getCostDiagnosisResult())));
                } catch (Exception ignore) {
                    data.put("costDiagnosisWarning", "serialize_failed");
                }
            }
            if (endedState.getBusinessOverviewResult() != null) {
                try {
                    data.put("businessOverview", JSON.parseObject(JSON.toJSONString(endedState.getBusinessOverviewResult())));
                } catch (Exception ignore) {
                    data.put("businessOverviewWarning", "serialize_failed");
                }
            }
            if (endedState.getDishProfitOverviewResult() != null) {
                try {
                    data.put("dishProfitOverview", JSON.parseObject(JSON.toJSONString(endedState.getDishProfitOverviewResult())));
                } catch (Exception ignore) {
                    data.put("dishProfitOverviewWarning", "serialize_failed");
                }
            }
            if (endedState.getDishProfitAnswerPlan() != null) {
                try {
                    data.put("dishProfitAnswerPlan", JSON.parseObject(JSON.toJSONString(endedState.getDishProfitAnswerPlan())));
                } catch (Exception ignore) {
                    data.put("dishProfitAnswerPlanWarning", "serialize_failed");
                }
            }
            if (endedState.getPurchaseAnswerPlan() != null) {
                try {
                    data.put("purchaseAnswerPlan", JSON.parseObject(JSON.toJSONString(endedState.getPurchaseAnswerPlan())));
                    data.put("purchaseAnswerPlanPresent", true);
                } catch (Exception ignore) {
                    data.put("purchaseAnswerPlanWarning", "serialize_failed");
                    data.put("purchaseAnswerPlanPresent", false);
                }
            } else {
                data.put("purchaseAnswerPlanPresent", false);
            }
            if (endedState.getIntentConvergence() != null && !endedState.getIntentConvergence().isEmpty()) {
                data.put("intentConvergence", endedState.getIntentConvergence());
            }
            if (endedState.getWarehouseOverview() != null && !endedState.getWarehouseOverview().isEmpty()) {
                try {
                    data.put("warehouseOverview",
                            JSON.parseObject(JSON.toJSONString(endedState.getWarehouseOverview())));
                } catch (Exception ignore) {
                    data.put("warehouseOverviewWarning", "serialize_failed");
                }
            }
            if (endedState.getPurchaseOverview() != null && !endedState.getPurchaseOverview().isEmpty()) {
                try {
                    data.put("purchaseOverview",
                            JSON.parseObject(JSON.toJSONString(endedState.getPurchaseOverview())));
                } catch (Exception ignore) {
                    data.put("purchaseOverviewWarning", "serialize_failed");
                }
            }
            if (endedState.getDiagnosisPlan() != null) {
                try {
                    data.put("diagnosisPlan", JSON.parseObject(JSON.toJSONString(endedState.getDiagnosisPlan())));
                    data.put("diagnosisPlanPresent", true);
                } catch (Exception ignore) {
                    data.put("diagnosisPlanWarning", "serialize_failed");
                    data.put("diagnosisPlanPresent", false);
                }
            } else {
                data.put("diagnosisPlanPresent", false);
            }
            if (endedState.getBusinessDiagnosisPlan() != null) {
                try {
                    data.put("businessDiagnosisPlan",
                            JSON.parseObject(JSON.toJSONString(endedState.getBusinessDiagnosisPlan())));
                } catch (Exception ignore) {
                    data.put("businessDiagnosisPlanWarning", "serialize_failed");
                }
            }
            if (harnessDebugContextEnabled && endedState.getResolvedQueryContext() != null) {
                try {
                    data.put("resolvedQueryContextSummary", AiHarnessResolvedContextSummarizer.summarize(
                            endedState.getResolvedQueryContext(),
                            endedState.getConversationId(),
                            endedState));
                } catch (Exception ex) {
                    data.put("resolvedQueryContextSummaryWarning", "summarize_failed");
                }
            }

            envelopePutCompositeGateAndExecution(endedState, data);

            Map<String, Object> delta = new HashMap<>();
            delta.put("text", answerText);
            delta.put("data", data);
            delta.put("displayText", "回答生成中");
            eventPublisher.publish(runId, "answer_delta", delta);

            session.setStatus(endedState.isCancelled() ? AiRunStatus.CANCELLED : AiRunStatus.COMPLETED);
            if (!endedState.isCancelled()) {
                AiConversationTurnMemory turnMemory = AiConversationTurnMemory.fromCompletedState(endedState);
                if (turnMemory != null && endedState.getUserId() != null) {
                    conversationMemoryService.rememberCompletedTurn(
                            endedState.getUserId(), endedState.getConversationId(), turnMemory);
                }
                AiFollowUpIntentSnapshot snap = FollowUpIntentResolveService.snapshotFromCompletedState(endedState);
                if (snap != null && endedState.getUserId() != null) {
                    followUpConversationMemory.remember(endedState.getUserId(), endedState.getConversationId(), snap);
                }
            }
        } catch (Exception e) {
            log.warn("[AiRunService] runId={} failed: {}", runId, e.toString(), e);
            session.setStatus(AiRunStatus.FAILED);
            String msg = e.getMessage() == null ? "unknown_error" : e.getMessage();
            eventPublisher.publishError(runId, msg, msg, e.getClass().getSimpleName(), e.getClass().getSimpleName(), null);
        } finally {
            String workspaceMode = endedState.getWorkspaceMode() == null ? null : endedState.getWorkspaceMode().name();
            traceService.updateRunFinished(
                    runId,
                    session.getStatus().name(),
                    endedState,
                    System.currentTimeMillis() - t0,
                    workspaceMode
            );
            Map<String, Object> fin = new HashMap<>();
            fin.put("status", sseStatusForFrontend(session.getStatus()));
            fin.put("displayText", runFinishedDisplayText(session.getStatus()));
            fin.put("data", new HashMap<String, Object>());
            enrichSseEnvelopeWithHarnessDebug(endedState, fin);
            envelopePutCompositeGateAndExecution(endedState, fin);
            eventPublisher.publish(runId, "run_finished", fin);
            session.completeEmitters();
        }
    }

    /**
     * Harness 对比轮次：上一轮（TurnMemory） vs 本轮解析态 vs effective*（追问合并后）。
     */
    private void logHarnessTurnMemory(long runId, Long conversationId, AiResolvedQueryContext ctx) {
        if (ctx == null || !log.isInfoEnabled()) {
            return;
        }
        var prev = ctx.getPreviousTurn();
        var tw = ctx.getTimeWindow();
        var org = ctx.getOrgScope();
        var qi = ctx.getQueryIntent();
        int prevStoreCnt = prev != null && prev.getLastVisibleStoreIds() != null
                ? prev.getLastVisibleStoreIds().size() : -1;
        log.info(
                "[AiHarnessTurnMemory] conversationId={} runId={} "
                        + "previousIntent={} previousPath={} currentIntent={} currentPath={} "
                        + "effectiveIntent={} effectivePath={} "
                        + "previousTime={}..{} effectiveTime={}..{} "
                        + "previousScope={} effectiveScope={} previousVisibleStoreCount={} "
                        + "previousMentionedStore={} previousPurchaseSourceType={}",
                conversationId,
                runId,
                prev != null ? prev.getLastIntentCode() : null,
                prev != null ? prev.getLastPathCode() : null,
                qi != null ? qi.getIntentCode() : null,
                qi != null ? qi.getPathCode() : null,
                ctx.getEffectiveIntentCode(),
                ctx.getEffectivePathCode(),
                prev != null ? prev.getLastStartDate() : null,
                prev != null ? prev.getLastEndDate() : null,
                tw != null ? tw.getStartDate() : null,
                tw != null ? tw.getEndDate() : null,
                prev != null ? prev.getLastScopeType() : null,
                org != null ? org.getScopeType() : null,
                prevStoreCnt >= 0 ? prevStoreCnt : null,
                prev != null ? prev.getLastMentionedStore() : null,
                prev != null ? prev.getLastPurchaseSourceType() : null);
    }

    /**
     * 服务端联调用：不下发 SSE，仅打 INFO，便于真机核对统一上下文。
     */
    private void logResolvedQueryContext(long runId, Long conversationId, AiResolvedQueryContext ctx) {
        if (ctx == null || !log.isInfoEnabled()) {
            return;
        }
        var uc = ctx.getUserContext();
        var org = ctx.getOrgScope();
        var tw = ctx.getTimeWindow();
        var qi = ctx.getQueryIntent();
        int nStores = org != null && org.getVisibleStores() != null ? org.getVisibleStores().size() : 0;
        int nWh = org != null && org.getVisibleWarehouses() != null ? org.getVisibleWarehouses().size() : 0;
        var fur = ctx.getFollowUpResolution();
        var ds = ctx.getDataScope();
        int nQueryDept = ds != null ? ds.getEffectiveSqlDepartmentIds().size() : 0;
        log.info(
                "[AiRunService] resolvedQueryContext runId={} conversationId={} userId={} sourceAdminRole={} roleCode={} "
                        + "intentCode={} pathCode={} scopeType={} distributerId={} requestDepartmentId={} "
                        + "visibleStores.size={} visibleWarehouses.size={} timeLabel={} startDate={} endDate={} "
                        + "explicitTimeMentioned={} queryScopeMode={} effectiveQueryDeptCount={} "
                        + "queryScopeBanner={} followUp={} followUpType={} followUpExpandedAtResolve={} "
                        + "effectiveIntentCode={} effectivePathCode={} effectiveTimeWindowSource={} effectiveScopeSource={} "
                        + "effectiveIntentSource={} "
                        + "structuredIntent={} purchaseSourceType={}",
                runId,
                conversationId,
                ctx.getUserId(),
                uc != null ? uc.getSourceAdminRole() : null,
                uc != null ? uc.getRoleCode() : null,
                qi != null ? qi.getIntentCode() : null,
                qi != null ? qi.getPathCode() : null,
                org != null ? org.getScopeType() : null,
                org != null ? org.getDistributerId() : null,
                org != null ? org.getRequestDepartmentId() : null,
                nStores,
                nWh,
                tw != null ? tw.getTimeLabel() : null,
                tw != null ? tw.getStartDate() : null,
                tw != null ? tw.getEndDate() : null,
                tw != null && tw.isExplicitTimeMentioned(),
                ds != null ? ds.getQueryScopeMode() : null,
                nQueryDept,
                ctx.getQueryScopeBanner(),
                fur != null && fur.isFollowUp(),
                fur != null ? fur.getFollowUpType() : null,
                fur != null && fur.isNormalizedInputExpandedAtResolvePhase(),
                ctx.getEffectiveIntentCode(),
                ctx.getEffectivePathCode(),
                ctx.getEffectiveTimeWindowSource(),
                ctx.getEffectiveScopeSource(),
                ctx.getEffectiveIntentSource(),
                qi != null ? qi.getStructuredIntentDetail() : null,
                qi != null ? qi.getPurchaseSourceType() : null
        );
        if (org != null && log.isInfoEnabled()) {
            log.info(
                    "[AiRunService] resolvedQueryContext runId={} visibleStores={} queryScopeKind={} queryStoreIds={} "
                            + "queryRealDepartmentIds={} queryDistributerId={} expandedSqlDepartmentIds={} childDepartmentIds={}",
                    runId,
                    formatVisibleStoresForDebug(org),
                    ds != null ? ds.getQueryScopeKind() : null,
                    ds != null ? ds.getQueryStoreIds() : null,
                    ds != null ? ds.getQueryRealDepartmentIds() : null,
                    ds != null ? ds.getQueryDistributerId() : null,
                    ds != null ? ds.getEffectiveSqlDepartmentIds() : null,
                    ds != null ? ds.getChildDepartmentIds() : null);
        }
        if (fur != null && "STORE_SCOPE_FOLLOW_UP".equals(fur.getFollowUpType()) && log.isInfoEnabled()) {
            var prev = ctx.getPreviousTurn();
            log.info(
                    "[AiRunService] STORE_SCOPE_FOLLOW_UP runId={} previousTurn.intentCode={} previousTurn.pathCode={} "
                            + "previousTurn.timeWindow={}..{} currentMentionedStoreName={} matchedStoreDepartmentId={} "
                            + "followUpType=STORE_SCOPE_FOLLOW_UP inheritIntent=true inheritTimeWindow=true "
                            + "overrideScope=true effectiveScopeSource={} effectiveSqlDepartmentIds={}",
                    runId,
                    prev != null ? prev.getLastIntentCode() : null,
                    prev != null ? prev.getLastPathCode() : null,
                    prev != null ? prev.getLastStartDate() : null,
                    prev != null ? prev.getLastEndDate() : null,
                    fur.getStoreScopeFollowUpMentionedName(),
                    fur.getStoreScopeFollowUpMatchedStoreRootId(),
                    ctx.getEffectiveScopeSource(),
                    ds != null ? ds.getEffectiveSqlDepartmentIds() : null);
        }
    }

    /** 真机核对用：仅日志，含 id+name，不写入 SSE。 */
    private static String formatVisibleStoresForDebug(AiResolvedOrgScope org) {
        List<AiStoreScopeDTO> list = org.getVisibleStores();
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            AiStoreScopeDTO s = list.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{id=").append(s.getStoreDepartmentId());
            sb.append(",name=").append(sanitizeOneLine(s.getStoreName()));
            sb.append('}');
        }
        sb.append(']');
        return sb.toString();
    }

    private static String sanitizeOneLine(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace('\n', ' ').replace('\r', ' ').trim();
    }

    /** 与前端示例契约一致：小写状态字。 */
    private static String sseStatusForFrontend(AiRunStatus s) {
        if (s == null) {
            return "unknown";
        }
        return switch (s) {
            case COMPLETED -> "completed";
            case FAILED -> "failed";
            case CANCELLED -> "cancelled";
            case RUNNING -> "running";
            case PENDING -> "pending";
        };
    }

    /** {@code run_finished} 仅用 displayText/status 收口，不包含正文（正文只在 {@code answer_delta}）。 */
    private static String runFinishedDisplayText(AiRunStatus st) {
        if (st == null) {
            return "运行结束";
        }
        return switch (st) {
            case COMPLETED -> "完成";
            case CANCELLED -> "已取消";
            case FAILED -> "运行失败";
            default -> "运行结束";
        };
    }

    /**
     * 协作式取消；记录 HTTP 上下文便于排查误调 /stop。
     */
    public StopRunOutcome stopRun(long runId) {
        Instant when = Instant.now();
        HttpProbe http = probeHttpClient();

        var sessionOpt = sessionRegistry.get(runId);
        if (sessionOpt.isEmpty()) {
            log.info(
                    "[AiRunService] stopRun runId={} sessionFound=false status=n/a stateCancelled=n/a invokeTime={} remoteAddr={} userAgent={}",
                    runId, when, http.remoteAddr(), http.userAgent());
            return new StopRunOutcome(false, null, false, null);
        }

        AiRunSession session = sessionOpt.get();
        AiRunStatus st = session.getStatus();
        boolean cancelledFlag = session.getState().isCancelled();
        log.info(
                "[AiRunService] stopRun runId={} sessionFound=true status={} stateCancelled={} invokeTime={} remoteAddr={} userAgent={}",
                runId, st, cancelledFlag, when, http.remoteAddr(), http.userAgent());

        if (st == AiRunStatus.COMPLETED || st == AiRunStatus.FAILED) {
            return new StopRunOutcome(true, st, false, "run already finished");
        }
        if (st == AiRunStatus.CANCELLED) {
            return new StopRunOutcome(true, st, false, "run already cancelled");
        }

        session.getState().setCancelled(true);
        return new StopRunOutcome(true, st, true, null);
    }

    /**
     * SSE 信封追加与 GET /ai/runs/{id} 一致的解析摘要（仅 {@code ai.harness.debug-context-enabled=true}），便于前台调试面板订阅事件即可见。
     */
    private void enrichSseEnvelopeWithHarnessDebug(AiRunState state, Map<String, Object> envelope) {
        if (!harnessDebugContextEnabled || state == null || envelope == null) {
            return;
        }
        envelope.put("composerPromptId", state.getComposerPromptRegistryId());
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        envelope.put("semanticPromptId", rq != null ? rq.getSemanticPromptRegistryId() : null);
        if (rq == null) {
            return;
        }
        Map<String, Object> sum = AiHarnessResolvedContextSummarizer.summarize(rq, state.getConversationId(), state);
        envelope.put("resolvedQueryContextSummary", sum);
        envelope.put("structuredIntentDetail", sum.get("structuredIntentDetail"));
        envelope.put("structuredIntentDetailWire", sum.get("structuredIntentDetailWire"));
        envelope.put("structuredIntentDetailCode", sum.get("structuredIntentDetailCode"));
        envelope.put("structuredIntentDetailPresent", sum.get("structuredIntentDetailPresent"));
        envelope.put("purchaseSourceType", sum.get("purchaseSourceType"));
        envelope.put("stockReduceType", sum.get("stockReduceType"));
        envelope.put("dishProfitStructuredDetail", sum.get("dishProfitStructuredDetail"));
        envelope.put("mentionedDishName", sum.get("mentionedDishName"));
        envelope.put("dishProfitMetricType", sum.get("dishProfitMetricType"));
        envelope.put("usedToolId", sum.get("usedToolId"));
        envelope.put("buildInsightUsed", sum.get("buildInsightUsed"));
        envelope.put("usedBuildInsight", sum.get("usedBuildInsight"));
        envelope.put("buildInsightRequest", sum.get("buildInsightRequest"));
        envelope.put("dishesCount", sum.get("dishesCount"));
        envelope.put("dishLineReturned", sum.get("dishLineReturned"));
        envelope.put("effectiveIntentCode", sum.get("effectiveIntentCode"));
        envelope.put("effectivePathCode", sum.get("effectivePathCode"));
        envelope.put("visibleStoreRootIds", sum.get("visibleStoreRootIds"));
        envelope.put("childDepartmentIds", sum.get("childDepartmentIds"));
        envelope.put("expandedSqlDepartmentIds", sum.get("expandedSqlDepartmentIds"));
        envelope.put("dishProfitSqlDepartmentIds", sum.get("dishProfitSqlDepartmentIds"));
        envelope.put("departmentScopeModelNote", sum.get("departmentScopeModelNote"));
        envelope.put("queryScopeKind", sum.get("queryScopeKind"));
        envelope.put("queryStoreIds", sum.get("queryStoreIds"));
        envelope.put("queryRealDepartmentIds", sum.get("queryRealDepartmentIds"));
        envelope.put("queryDistributerId", sum.get("queryDistributerId"));
        envelope.put("storeToDepartmentIds", sum.get("storeToDepartmentIds"));
        envelope.put("salesDishCount", sum.get("salesDishCount"));
        envelope.put("riskLevel", sum.get("riskLevel"));
        envelope.put("dishProfitAnswerPlan", sum.get("dishProfitAnswerPlan"));
        envelope.put("dishProfitAnswerPlanPresent", sum.get("dishProfitAnswerPlanPresent"));
        envelope.put("purchaseAnswerPlan", sum.get("purchaseAnswerPlan"));
        envelope.put("purchaseAnswerPlanPresent", sum.get("purchaseAnswerPlanPresent"));
        envelope.put("purchaseAnswerPlanType", sum.get("purchaseAnswerPlanType"));
        envelope.put("purchaseAnswerPlanSortKey", sum.get("purchaseAnswerPlanSortKey"));
        envelope.put("purchaseAnswerPlanSortDirection", sum.get("purchaseAnswerPlanSortDirection"));
        envelope.put("purchaseAnswerPlanFocusRows", sum.get("purchaseAnswerPlanFocusRows"));
        envelope.put("purchaseAnswerPlanSecondaryRows", sum.get("purchaseAnswerPlanSecondaryRows"));
        envelope.put("purchaseAnswerPlanDebug", sum.get("purchaseAnswerPlanDebug"));
        envelope.put("buildInsightInputStoreRootIds", sum.get("buildInsightInputStoreRootIds"));
        envelope.put("buildInsightInputDepartmentIdsAllowFilter", sum.get("buildInsightInputDepartmentIdsAllowFilter"));
        envelope.put("diagnosisPlan", sum.get("diagnosisPlan"));
        envelope.put("diagnosisPlanPresent", sum.get("diagnosisPlanPresent"));
        envelope.put("diagnosisPlanType", sum.get("diagnosisPlanType"));
        envelope.put("businessDiagnosisPlan", sum.get("businessDiagnosisPlan"));
        envelope.put("diagnosisRiskLevel", sum.get("diagnosisRiskLevel"));
        envelope.put("diagnosisDataCompleteness", sum.get("diagnosisDataCompleteness"));
        envelope.put("planSource", sum.get("planSource"));
        envelope.put("compositeGateAllowed", sum.get("compositeGateAllowed"));
        envelope.put("compositeGateReasonCode", sum.get("compositeGateReasonCode"));
        envelope.put("compositeGateReason", sum.get("compositeGateReason"));
        envelope.put("compositeGateScopeType", sum.get("compositeGateScopeType"));
        envelope.put("compositeGateRecommendedCaseKind", sum.get("compositeGateRecommendedCaseKind"));
        envelope.put("compositeGateFinalAnswerPlanType", sum.get("compositeGateFinalAnswerPlanType"));
        envelope.put("compositeGateDebug", sum.get("compositeGateDebug"));
        envelope.put("compositeGateProductionEnabledSource", sum.get("compositeGateProductionEnabledSource"));
        envelope.put("compositeGateProductionEnabledEffective", sum.get("compositeGateProductionEnabledEffective"));
    }

    private static HttpProbe probeHttpClient() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return HttpProbe.EMPTY;
            }
            HttpServletRequest req = attrs.getRequest();
            if (req == null) {
                return HttpProbe.EMPTY;
            }
            String xff = req.getHeader("X-Forwarded-For");
            String remote = req.getRemoteAddr();
            if (xff != null && !xff.isBlank()) {
                remote = remote + ";xff=" + xff.split("\\s*,\\s*")[0];
            }
            String ua = req.getHeader("User-Agent");
            return new HttpProbe(remote, ua == null ? "" : ua);
        } catch (Exception ex) {
            return HttpProbe.EMPTY;
        }
    }

    /** 联调日志用；字段与 record 访问器区分开，便于空值兜底。 */
    private record HttpProbe(String rawRemote, String rawUserAgent) {
        static final HttpProbe EMPTY = new HttpProbe(null, null);

        String remoteAddr() {
            return rawRemote == null || rawRemote.isEmpty() ? "-" : rawRemote;
        }

        String userAgent() {
            return rawUserAgent == null || rawUserAgent.isEmpty() ? "-" : rawUserAgent;
        }
    }
}
