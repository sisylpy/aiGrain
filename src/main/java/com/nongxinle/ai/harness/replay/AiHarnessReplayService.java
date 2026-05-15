package com.nongxinle.ai.harness.replay;

import com.nongxinle.ai.context.AiUserContextResolver;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiConversationMemoryService;
import com.nongxinle.ai.harness.AiHarnessResolvedContextSummarizer;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.platform.AiRunService;
import com.nongxinle.ai.platform.dto.AiRunCreateRequest;
import com.nongxinle.ai.planner.DishProfitPlannerRealReadBridge;
import com.nongxinle.ai.planner.PurchasePlannerRealReadBridge;
import com.nongxinle.ai.planner.RevenuePlannerRealReadBridge;
import com.nongxinle.ai.planner.StockReducePlannerRealReadBridge;
import com.nongxinle.ai.resolver.AiResolvedQueryContextResolver;
import com.nongxinle.ai.scope.AiConversationScopeMode;
import com.nongxinle.ai.trace.AiRunSessionRegistry;
import com.nongxinle.entity.GbAiConversationEntity;
import com.nongxinle.service.GbAiChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Harness：多轮会话跑 {@link AiResolvedQueryContextResolver}（及可选同步业务图），写入 {@link AiConversationMemoryService}，并对照预期结构化失败类型。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiHarnessReplayService {

    private final AiUserContextResolver userContextResolver;
    private final AiResolvedQueryContextResolver resolvedQueryContextResolver;
    private final AiConversationMemoryService conversationMemoryService;
    private final AiRunSessionRegistry sessionRegistry;
    private final GbAiChatService gbAiChatService;
    private final AiRunService aiRunService;
    private final RevenuePlannerRealReadBridge revenuePlannerRealReadBridge;
    private final PurchasePlannerRealReadBridge purchasePlannerRealReadBridge;
    private final StockReducePlannerRealReadBridge stockReducePlannerRealReadBridge;
    private final DishProfitPlannerRealReadBridge dishProfitPlannerRealReadBridge;

    public AiHarnessReplayResponse replay(AiHarnessReplayRequest req) {
        if (req == null || req.getUserId() == null) {
            throw new IllegalArgumentException("userId required");
        }
        if (req.getMessages() == null || req.getMessages().isEmpty()) {
            throw new IllegalArgumentException("messages required");
        }
        if (StringUtils.hasText(req.getCaseId()) && AiHarnessBuiltinCases.isCompositeGateHarnessCase(req.getCaseId().trim())) {
            return AiHarnessReplayCompositeGate.replay(req);
        }
        if (StringUtils.hasText(req.getCaseId()) && AiHarnessBuiltinCases.isPlannerExecutorMockHarnessCase(req.getCaseId())) {
            return AiHarnessReplayPlannerExecutorMock.replay(
                    req,
                    revenuePlannerRealReadBridge,
                    purchasePlannerRealReadBridge,
                    stockReducePlannerRealReadBridge,
                    dishProfitPlannerRealReadBridge);
        }

        LocalDate today = resolveToday(req.getFrozenClockDate());

        AiConversationScopeMode mode = inferScopeMode(req);

        GbAiConversationEntity conv = gbAiChatService.createNewConversationForAgentRun(
                req.getDepartmentId(), req.getDistributerId(), mode, req.getUserId(), 0);
        long conversationId = conv.getGbAiConversationId();

        List<AiHarnessReplayExpectedRound> expectations = resolveExpectations(req, today);
        boolean exploreProbeReplay = computeExploreProbeReplay(req, expectations);
        boolean strict = req.isStrictStoreSqlMatch();

        List<AiHarnessReplayRoundResult> rounds = new ArrayList<>();
        boolean allPass = true;
        AiHarnessReplayMode replayMode = resolveReplayMode(req);

        for (int i = 0; i < req.getMessages().size(); i++) {
            String msg = req.getMessages().get(i);
            if (!StringUtils.hasText(msg)) {
                AiHarnessReplayRoundResult.AiHarnessReplayRoundResultBuilder skipB =
                        AiHarnessReplayRoundResult.builder()
                                .roundIndex(i + 1)
                                .message("")
                                .runId(-1)
                                .conversationId(conversationId)
                                .pass(true)
                                .resolvedQueryContextSummary(new LinkedHashMap<>())
                                .failedFields(List.of());
                if (exploreProbeReplay) {
                    skipB.probe(AiHarnessReplayProbeView.fromSummary(new LinkedHashMap<>(), ""));
                }
                rounds.add(skipB.build());
                continue;
            }

            AiRunCreateRequest runReq = new AiRunCreateRequest();
            runReq.setUserId(req.getUserId());
            runReq.setDepartmentId(req.getDepartmentId());
            runReq.setDistributerId(req.getDistributerId());
            runReq.setConversationId(conversationId);
            runReq.setMessage(msg.trim());
            if (StringUtils.hasText(req.getScopeMode())) {
                runReq.setScopeMode(req.getScopeMode());
            }

            long runId = sessionRegistry.nextRunId();
            LinkedHashMap<String, Object> summary;
            if (replayMode == AiHarnessReplayMode.GRAPH_RUN) {
                AiRunState ended = aiRunService.executeBusinessGraphSyncForHarness(
                        runReq,
                        today,
                        runId,
                        req.getCompositeProductionGateProductionEnabledOverride(),
                        req.getCompositeBusinessDiagnosisExecutionMode());
                summary = new LinkedHashMap<>(AiHarnessResolvedContextSummarizer.summarize(
                        ended.getResolvedQueryContext(), conversationId, ended));
            } else {
                var uc = userContextResolver.resolve(runReq);
                var resolved = resolvedQueryContextResolver.resolve(runId, runReq, uc, today);
                resolved.setRunId(runId);
                summary = new LinkedHashMap<>(
                        AiHarnessResolvedContextSummarizer.summarize(resolved, conversationId));
                AiConversationTurnMemory turn =
                        AiConversationTurnMemory.fromHarnessReplayStep(resolved, conversationId, runId);
                conversationMemoryService.rememberCompletedTurn(req.getUserId(), conversationId, turn);
            }

            List<AiHarnessMismatch> failed = List.of();
            if (!exploreProbeReplay && expectations != null && i < expectations.size()) {
                failed = AiHarnessExpectationComparator.compare(summary, expectations.get(i), strict);
            }

            boolean pass = failed.isEmpty();
            if (!exploreProbeReplay && !pass) {
                allPass = false;
            }

            AiHarnessReplayRoundResult.AiHarnessReplayRoundResultBuilder rb =
                    AiHarnessReplayRoundResult.builder()
                            .roundIndex(i + 1)
                            .message(msg.trim())
                            .runId(runId)
                            .conversationId(conversationId)
                            .resolvedQueryContextSummary(summary)
                            .pass(pass)
                            .failedFields(new ArrayList<>(failed));
            if (exploreProbeReplay) {
                rb.probe(AiHarnessReplayProbeView.fromSummary(summary, msg.trim()));
            }
            rounds.add(rb.build());
        }

        return AiHarnessReplayResponse.builder()
                .conversationId(conversationId)
                .overallPass(exploreProbeReplay ? null : Boolean.valueOf(allPass))
                .frozenClockDate(today.toString())
                .caseId(req.getCaseId())
                .exploreProbeReplay(exploreProbeReplay ? Boolean.TRUE : null)
                .rounds(rounds)
                .build();
    }

    /** 不写 expectations 断言：仅输出每轮摘要/探针。 */
    private static boolean computeExploreProbeReplay(AiHarnessReplayRequest req,
            List<AiHarnessReplayExpectedRound> resolvedExpectations) {
        return req.isIgnoreExpectations()
                || (AiHarnessReplayRequest.isBuiltinProbeCaseId(
                                StringUtils.hasText(req.getCaseId()) ? req.getCaseId().trim() : "")
                        && (resolvedExpectations == null || resolvedExpectations.isEmpty()));
    }

    private static AiHarnessReplayMode resolveReplayMode(AiHarnessReplayRequest req) {
        AiHarnessReplayMode explicit = AiHarnessReplayMode.fromApiString(req.getReplayMode());
        if (explicit != null) {
            return explicit;
        }
        if (StringUtils.hasText(req.getCaseId()) && AiHarnessBuiltinCases.isCompositeGateHarnessCase(req.getCaseId().trim())) {
            return AiHarnessReplayMode.BUSINESS_DIAGNOSIS_COMPOSITE_GATE;
        }
        if (StringUtils.hasText(req.getCaseId())
                && (AiHarnessBuiltinCases.BUSINESS_DIAGNOSIS_V1_CORE_3.equals(req.getCaseId().trim())
                        || AiHarnessBuiltinCases.BUSINESS_OVERVIEW_MULTI_AGENT_CORE_3.equals(
                                req.getCaseId().trim())
                        || AiHarnessBuiltinCases.REVENUE_AGENT_GRAPH_CORE.equals(req.getCaseId().trim())
                        || AiHarnessBuiltinCases.PURCHASE_AGENT_GRAPH_CORE.equals(req.getCaseId().trim())
                        || AiHarnessBuiltinCases.STOCK_REDUCE_AGENT_GRAPH_CORE.equals(req.getCaseId().trim())
                        || AiHarnessBuiltinCases.DISH_PROFIT_AGENT_GRAPH_CORE.equals(req.getCaseId().trim()))) {
            return AiHarnessReplayMode.GRAPH_RUN;
        }
        if (StringUtils.hasText(req.getCaseId()) && AiHarnessBuiltinCases.isPlannerExecutorMockHarnessCase(req.getCaseId())) {
            if (AiHarnessBuiltinCases.PLANNER_EXECUTOR_PURCHASE_ADAPTER_CORE.equals(req.getCaseId().trim())
                    || AiHarnessBuiltinCases.PLANNER_EXECUTOR_PURCHASE_ADAPTER_FAKE_OK_CORE.equals(
                            req.getCaseId().trim())
                    || AiHarnessBuiltinCases.PLANNER_EXECUTOR_PURCHASE_ADAPTER_REAL_BRIDGE_CORE.equals(
                            req.getCaseId().trim())
                    || AiHarnessBuiltinCases.PLANNER_EXECUTOR_PURCHASE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE.equals(
                            req.getCaseId().trim())
                    || AiHarnessBuiltinCases.PLANNER_EXECUTOR_PURCHASE_ADAPTER_GROUP_HYDRATED_CORE.equals(
                            req.getCaseId().trim())) {
                return AiHarnessReplayMode.PLANNER_EXECUTOR_PURCHASE_ADAPTER;
            }
            if (AiHarnessBuiltinCases.PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_CORE.equals(req.getCaseId().trim())
                    || AiHarnessBuiltinCases.PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_FAKE_OK_CORE.equals(
                            req.getCaseId().trim())
                    || AiHarnessBuiltinCases.PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_REAL_BRIDGE_CORE.equals(
                            req.getCaseId().trim())
                    || AiHarnessBuiltinCases.PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE.equals(
                            req.getCaseId().trim())
                    || AiHarnessBuiltinCases.PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_GROUP_HYDRATED_CORE.equals(
                            req.getCaseId().trim())
                    || AiHarnessBuiltinCases.PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_REVENUE_PURCHASE_STOCK_CORE
                            .equals(req.getCaseId().trim())) {
                return AiHarnessReplayMode.PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER;
            }
            if (AiHarnessBuiltinCases.PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_CORE.equals(req.getCaseId().trim())
                    || AiHarnessBuiltinCases.PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_FAKE_OK_CORE.equals(
                            req.getCaseId().trim())
                    || AiHarnessBuiltinCases.PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_REAL_BRIDGE_CORE.equals(
                            req.getCaseId().trim())
                    || AiHarnessBuiltinCases.PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_REAL_BRIDGE_HYDRATED_CORE.equals(
                            req.getCaseId().trim())
                    || AiHarnessBuiltinCases.PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_GROUP_HYDRATED_CORE.equals(
                            req.getCaseId().trim())
                    || AiHarnessBuiltinCases.PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_ALL_REAL_CORE.equals(
                            req.getCaseId().trim())
                    || AiHarnessBuiltinCases.PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_GROUP_CORE.equals(
                            req.getCaseId().trim())
                    || AiHarnessBuiltinCases.PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_STOCK_DEGRADED_CORE.equals(
                            req.getCaseId().trim())) {
                return AiHarnessReplayMode.PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER;
            }
            if (AiHarnessBuiltinCases.PLANNER_EXECUTOR_REVENUE_ADAPTER_CORE.equals(req.getCaseId().trim())
                    || AiHarnessBuiltinCases.PLANNER_EXECUTOR_REVENUE_ADAPTER_FAKE_OK_CORE.equals(
                            req.getCaseId().trim())
                    || AiHarnessBuiltinCases.PLANNER_EXECUTOR_REVENUE_ADAPTER_REAL_BRIDGE_CORE.equals(
                            req.getCaseId().trim())
                    || AiHarnessBuiltinCases.PLANNER_EXECUTOR_REVENUE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE.equals(
                            req.getCaseId().trim())
                    || AiHarnessBuiltinCases.PLANNER_EXECUTOR_REVENUE_ADAPTER_GROUP_HYDRATED_CORE.equals(
                            req.getCaseId().trim())
                    || AiHarnessBuiltinCases.PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_REVENUE_CORE.equals(
                            req.getCaseId().trim())
                    || AiHarnessBuiltinCases.PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_REVENUE_PURCHASE_CORE.equals(
                            req.getCaseId().trim())) {
                return AiHarnessReplayMode.PLANNER_EXECUTOR_REVENUE_ADAPTER;
            }
            return AiHarnessReplayMode.PLANNER_EXECUTOR_MOCK;
        }
        return AiHarnessReplayMode.RESOLVER_ONLY;
    }

    private static LocalDate resolveToday(String frozenClockDate) {
        if (!StringUtils.hasText(frozenClockDate)) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(frozenClockDate.trim());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("invalid frozenClockDate (yyyy-MM-dd): " + frozenClockDate);
        }
    }

    private static AiConversationScopeMode inferScopeMode(AiHarnessReplayRequest req) {
        if (StringUtils.hasText(req.getScopeMode())) {
            return AiConversationScopeMode.fromApiString(req.getScopeMode());
        }
        if (req.getDepartmentId() != null) {
            return AiConversationScopeMode.STORE;
        }
        if (req.getDistributerId() != null) {
            return AiConversationScopeMode.GROUP;
        }
        throw new IllegalArgumentException("创建会话需要 departmentId（单店）或 distributerId（集团），或 scopeMode");
    }

    private List<AiHarnessReplayExpectedRound> resolveExpectations(AiHarnessReplayRequest req, LocalDate today) {
        if (req.isIgnoreExpectations()) {
            return null;
        }
        if (req.getExpectations() != null && !req.getExpectations().isEmpty()) {
            return req.getExpectations();
        }
        if (!StringUtils.hasText(req.getCaseId())) {
            return null;
        }
        if (AiHarnessReplayRequest.isBuiltinProbeCaseId(req.getCaseId())) {
            return null;
        }
        if (AiHarnessBuiltinCases.isPlannerExecutorMockHarnessCase(req.getCaseId())) {
            return null;
        }
        if (AiHarnessBuiltinCases.PURCHASE_MULTITURN_1.equals(req.getCaseId().trim())) {
            var anchor = AiHarnessBuiltinCases.LocalDateAnchor.frozenClock(today);
            int n = AiHarnessBuiltinCases.expectationsPurchaseMultiturn1(anchor).size();
            if (req.getMessages().size() < n) {
                log.warn(
                        "[AiHarnessReplay] case={} expects {} rounds, got {}",
                        req.getCaseId(),
                        n,
                        req.getMessages().size());
            }
            return AiHarnessBuiltinCases.expectationsPurchaseMultiturn1(anchor);
        }
        if (AiHarnessBuiltinCases.MULTI_STORE_PUBLIC_SCOPE_BLOCK3.equals(req.getCaseId().trim())) {
            var anchor = AiHarnessBuiltinCases.LocalDateAnchor.frozenClock(today);
            int n = AiHarnessBuiltinCases.expectationsMultiStorePublicScopeBlock3(anchor).size();
            if (req.getMessages().size() < n) {
                log.warn(
                        "[AiHarnessReplay] case={} expects {} rounds, got {}",
                        req.getCaseId(),
                        n,
                        req.getMessages().size());
            }
            return AiHarnessBuiltinCases.expectationsMultiStorePublicScopeBlock3(anchor);
        }
        if (AiHarnessBuiltinCases.MULTI_STORE_GLOBAL_LINKS_CONFIRM_5.equals(req.getCaseId().trim())) {
            var anchor = AiHarnessBuiltinCases.LocalDateAnchor.frozenClock(today);
            int n = AiHarnessBuiltinCases.expectationsMultiStoreGlobalLinksConfirm5(anchor).size();
            if (req.getMessages().size() < n) {
                log.warn(
                        "[AiHarnessReplay] case={} expects {} rounds, got {}",
                        req.getCaseId(),
                        n,
                        req.getMessages().size());
            }
            return AiHarnessBuiltinCases.expectationsMultiStoreGlobalLinksConfirm5(anchor);
        }
        if (AiHarnessBuiltinCases.V2_SEMANTIC_MAINLINE_CORE_10.equals(req.getCaseId().trim())) {
            var anchor = AiHarnessBuiltinCases.LocalDateAnchor.frozenClock(today);
            int n = AiHarnessBuiltinCases.expectationsV2SemanticMainlineCore10(anchor).size();
            if (req.getMessages().size() < n) {
                log.warn(
                        "[AiHarnessReplay] case={} expects {} rounds, got {}",
                        req.getCaseId(),
                        n,
                        req.getMessages().size());
            }
            return AiHarnessBuiltinCases.expectationsV2SemanticMainlineCore10(anchor);
        }
        if (AiHarnessBuiltinCases.DISH_PROFIT_RANKING_TO_NAMED_DISH_FOLLOWUP_2.equals(req.getCaseId().trim())) {
            var anchor = AiHarnessBuiltinCases.LocalDateAnchor.frozenClock(today);
            int n = AiHarnessBuiltinCases.expectationsDishProfitRankingToNamedDishFollowup2(anchor).size();
            if (req.getMessages().size() < n) {
                log.warn(
                        "[AiHarnessReplay] case={} expects {} rounds, got {}",
                        req.getCaseId(),
                        n,
                        req.getMessages().size());
            }
            return AiHarnessBuiltinCases.expectationsDishProfitRankingToNamedDishFollowup2(anchor);
        }
        if (AiHarnessBuiltinCases.BUSINESS_DIAGNOSIS_V1_CORE_3.equals(req.getCaseId().trim())) {
            var anchor = AiHarnessBuiltinCases.LocalDateAnchor.frozenClock(today);
            int n = AiHarnessBuiltinCases.expectationsBusinessDiagnosisV1Core3(anchor).size();
            if (req.getMessages().size() < n) {
                log.warn(
                        "[AiHarnessReplay] case={} expects {} rounds, got {}",
                        req.getCaseId(),
                        n,
                        req.getMessages().size());
            }
            return AiHarnessBuiltinCases.expectationsBusinessDiagnosisV1Core3(anchor);
        }
        if (AiHarnessBuiltinCases.BUSINESS_OVERVIEW_MULTI_AGENT_CORE_3.equals(req.getCaseId().trim())) {
            var anchor = AiHarnessBuiltinCases.LocalDateAnchor.frozenClock(today);
            int n = AiHarnessBuiltinCases.expectationsBusinessOverviewMultiAgentCore3(anchor).size();
            if (req.getMessages().size() < n) {
                log.warn(
                        "[AiHarnessReplay] case={} expects {} rounds, got {}",
                        req.getCaseId(),
                        n,
                        req.getMessages().size());
            }
            return AiHarnessBuiltinCases.expectationsBusinessOverviewMultiAgentCore3(anchor);
        }
        if (AiHarnessBuiltinCases.REVENUE_AGENT_GRAPH_CORE.equals(req.getCaseId().trim())) {
            var anchor = AiHarnessBuiltinCases.LocalDateAnchor.frozenClock(today);
            int n = AiHarnessBuiltinCases.expectationsRevenueAgentGraphCore(anchor).size();
            if (req.getMessages().size() < n) {
                log.warn(
                        "[AiHarnessReplay] case={} expects {} rounds, got {}",
                        req.getCaseId(),
                        n,
                        req.getMessages().size());
            }
            return AiHarnessBuiltinCases.expectationsRevenueAgentGraphCore(anchor);
        }
        if (AiHarnessBuiltinCases.PURCHASE_AGENT_GRAPH_CORE.equals(req.getCaseId().trim())) {
            var anchor = AiHarnessBuiltinCases.LocalDateAnchor.frozenClock(today);
            int n = AiHarnessBuiltinCases.expectationsPurchaseAgentGraphCore(anchor).size();
            if (req.getMessages().size() < n) {
                log.warn(
                        "[AiHarnessReplay] case={} expects {} rounds, got {}",
                        req.getCaseId(),
                        n,
                        req.getMessages().size());
            }
            return AiHarnessBuiltinCases.expectationsPurchaseAgentGraphCore(anchor);
        }
        if (AiHarnessBuiltinCases.STOCK_REDUCE_AGENT_GRAPH_CORE.equals(req.getCaseId().trim())) {
            var anchor = AiHarnessBuiltinCases.LocalDateAnchor.frozenClock(today);
            int n = AiHarnessBuiltinCases.expectationsStockReduceAgentGraphCore(anchor).size();
            if (req.getMessages().size() < n) {
                log.warn(
                        "[AiHarnessReplay] case={} expects {} rounds, got {}",
                        req.getCaseId(),
                        n,
                        req.getMessages().size());
            }
            return AiHarnessBuiltinCases.expectationsStockReduceAgentGraphCore(anchor);
        }
        if (AiHarnessBuiltinCases.DISH_PROFIT_AGENT_GRAPH_CORE.equals(req.getCaseId().trim())) {
            var anchor = AiHarnessBuiltinCases.LocalDateAnchor.frozenClock(today);
            int n = AiHarnessBuiltinCases.expectationsDishProfitAgentGraphCore(anchor).size();
            if (req.getMessages().size() < n) {
                log.warn(
                        "[AiHarnessReplay] case={} expects {} rounds, got {}",
                        req.getCaseId(),
                        n,
                        req.getMessages().size());
            }
            return AiHarnessBuiltinCases.expectationsDishProfitAgentGraphCore(anchor);
        }
        throw new IllegalArgumentException("unknown harness caseId: " + req.getCaseId());
    }
}
