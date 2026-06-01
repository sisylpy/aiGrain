package com.nongxinle.ai.harness.replay;

import com.nongxinle.ai.context.AiUserContextResolver;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiConversationMemoryService;
import com.nongxinle.ai.harness.AiHarnessResolvedContextSummarizer;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.platform.AiCardPayloadWireSupport;
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
import com.nongxinle.ai.conversation.AiConversationCoreService;
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
    private final AiConversationCoreService conversationCoreService;
    private final AiRunService aiRunService;
    private final RevenuePlannerRealReadBridge revenuePlannerRealReadBridge;
    private final PurchasePlannerRealReadBridge purchasePlannerRealReadBridge;
    private final StockReducePlannerRealReadBridge stockReducePlannerRealReadBridge;
    private final DishProfitPlannerRealReadBridge dishProfitPlannerRealReadBridge;

    public AiHarnessReplayResponse replay(AiHarnessReplayRequest req) {
        if (req == null || req.getUserId() == null) {
            throw new IllegalArgumentException("userId required");
        }
        applyBuiltinMessagesIfMissing(req);
        if (req.getMessages() == null || req.getMessages().isEmpty()) {
            throw new IllegalArgumentException("messages required");
        }
        if (StringUtils.hasText(req.getCaseId())) {
            String cid = req.getCaseId().trim();
            if ((AiHarnessBuiltinCases.BUSINESS_SEMANTIC_1B_RESOLVED_CONTEXT.equals(cid)
                            || AiHarnessBuiltinCases.STOCK_REDUCE_SEMANTIC_1C_RESOLVED_CONTEXT.equals(cid))
                    && req.getDryRunStage() == null) {
                req.setDryRunStage(AiHarnessReplayDryRunStage.RESOLVED_CONTEXT_ONLY);
            }
            if (AiHarnessBuiltinCases.PURCHASE_TOOL_REQUEST_2A_MIN.equals(cid) && req.getDryRunStage() == null) {
                req.setDryRunStage(AiHarnessReplayDryRunStage.TOOL_REQUEST_ONLY);
            }
            if (AiHarnessBuiltinCases.PURCHASE_TOOL_REQUEST_2A_CORE.equals(cid) && req.getDryRunStage() == null) {
                req.setDryRunStage(AiHarnessReplayDryRunStage.TOOL_REQUEST_ONLY);
            }
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

        GbAiConversationEntity conv = conversationCoreService.createNewConversationForAgentRun(
                req.getDepartmentId(), req.getDistributerId(), mode, req.getUserId());
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
            boolean runBusinessGraphSync =
                    replayMode == AiHarnessReplayMode.GRAPH_RUN
                            && req.getDryRunStage() != AiHarnessReplayDryRunStage.RESOLVED_CONTEXT_ONLY;
            if (runBusinessGraphSync) {
                boolean toolRequestOnly =
                        req.getDryRunStage() == AiHarnessReplayDryRunStage.TOOL_REQUEST_ONLY;
                AiRunState ended = aiRunService.executeBusinessGraphSyncForHarness(
                        runReq,
                        today,
                        runId,
                        req.getCompositeProductionGateProductionEnabledOverride(),
                        req.getCompositeBusinessDiagnosisExecutionMode(),
                        toolRequestOnly);
                summary = new LinkedHashMap<>(AiHarnessResolvedContextSummarizer.summarize(
                        ended.getResolvedQueryContext(), conversationId, ended));
                AiCardPayloadWireSupport.refreshAllCardPayloads(ended);
                AiCardPayloadWireSupport.enrichHarnessSummaryWithCardFields(summary, ended);
                summary.put("harnessReplayTurnMemorySource", "fromCompletedState");
            } else {
                var uc = userContextResolver.resolve(runReq);
                var resolved = resolvedQueryContextResolver.resolve(runId, runReq, uc, today);
                resolved.setRunId(runId);
                summary = new LinkedHashMap<>(
                        AiHarnessResolvedContextSummarizer.summarize(resolved, conversationId));
                AiConversationTurnMemory turn =
                        AiConversationTurnMemory.fromHarnessReplayStep(resolved, conversationId, runId);
                conversationMemoryService.rememberCompletedTurn(req.getUserId(), conversationId, turn);
                summary.put("harnessReplayTurnMemorySource", "fromHarnessReplayStep");
                summary.put("harnessReplayTurnMemoryHasResultAnchors", Boolean.FALSE);
            }

            List<AiHarnessMismatch> failed = List.of();
            if (!exploreProbeReplay && expectations != null && i < expectations.size()) {
                failed = AiHarnessExpectationComparator.compare(summary, expectations.get(i), strict);
                if (!failed.isEmpty()) {
                    int ri = i + 1;
                    for (AiHarnessMismatch m : failed) {
                        m.setRoundIndex(ri);
                    }
                }
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

        List<AiHarnessMismatch> flattenedFailures = new ArrayList<>();
        for (AiHarnessReplayRoundResult r : rounds) {
            if (r.getFailedFields() != null) {
                flattenedFailures.addAll(r.getFailedFields());
            }
        }

        return AiHarnessReplayResponse.builder()
                .conversationId(conversationId)
                .overallPass(exploreProbeReplay ? null : Boolean.valueOf(allPass))
                .frozenClockDate(today.toString())
                .caseId(req.getCaseId())
                .exploreProbeReplay(exploreProbeReplay ? Boolean.TRUE : null)
                .rounds(rounds)
                .expectationFailures(flattenedFailures)
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
        if (req.getDryRunStage() == AiHarnessReplayDryRunStage.TOOL_REQUEST_ONLY) {
            AiHarnessReplayMode explicit = AiHarnessReplayMode.fromApiString(req.getReplayMode());
            if (explicit == null || explicit == AiHarnessReplayMode.RESOLVER_ONLY) {
                return AiHarnessReplayMode.GRAPH_RUN;
            }
            if (explicit != AiHarnessReplayMode.GRAPH_RUN) {
                throw new IllegalArgumentException(
                        "dryRunStage=TOOL_REQUEST_ONLY requires replayMode=GRAPH_RUN for harness tool-request capture");
            }
            return explicit;
        }
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
                        || AiHarnessBuiltinCases.PURCHASE_TOOL_REQUEST_2A_MIN.equals(req.getCaseId().trim())
                        || AiHarnessBuiltinCases.PURCHASE_TOOL_REQUEST_2A_CORE.equals(req.getCaseId().trim())
                        || AiHarnessBuiltinCases.PURCHASE_SUPPLIER_RANKING_ANCHOR_EXECUTION_GOODS_UNIT_PRICE_3.equals(
                                req.getCaseId().trim())
                        || AiHarnessBuiltinCases.PURCHASE_GOODS_RANKING_ANCHOR_EXECUTION_SUPPLIER_UNIT_PRICE_2.equals(
                                req.getCaseId().trim())
                        || AiHarnessBuiltinCases.PURCHASE_GOODS_RANKING_SOURCE_BREAKDOWN_2.equals(
                                req.getCaseId().trim())
                        || AiHarnessBuiltinCases.PURCHASE_ANCHOR_EXECUTION_MATRIX_P1.equals(req.getCaseId().trim())
                        || AiHarnessBuiltinCases.DISH_PROFIT_MATRIX_P1.equals(req.getCaseId().trim())
                        || AiHarnessBuiltinCases.PURCHASE_SUPPLIER_FACET_GOODS_RANKING_SOURCE_BREAKDOWN_2.equals(
                                req.getCaseId().trim())
                        || AiHarnessBuiltinCases.PURCHASE_SUPPLIER_FACET_GOODS_AMOUNT_RANKING_IGNORE_ANCHOR_2.equals(
                                req.getCaseId().trim())
                        || AiHarnessBuiltinCases.PURCHASE_SUPPLIER_CHANNEL_OVERVIEW_GOODS_DETAIL_2.equals(
                                req.getCaseId().trim())
                        || AiHarnessBuiltinCases.PURCHASE_SUPPLIER_ANCHOR_THEN_SOURCE_AMOUNT_SUMMARY_2.equals(
                                req.getCaseId().trim())
                        || AiHarnessBuiltinCases.PURCHASE_PERIOD_GOODS_LIST_1.equals(req.getCaseId().trim())
                        || AiHarnessBuiltinCases.PURCHASE_PERIOD_GOODS_LIST_SELF_1.equals(req.getCaseId().trim())
                        || AiHarnessBuiltinCases.PURCHASE_PERIOD_GOODS_LIST_SUPPLIER_1.equals(
                                req.getCaseId().trim())
                        || AiHarnessBuiltinCases.BUSINESS_STORE_PRIORITY_REASON_EXPLANATION_3.equals(
                                req.getCaseId().trim())
                        || AiHarnessBuiltinCases.BUSINESS_DIAGNOSIS_ANCHOR_EXECUTION_MATRIX_P1.equals(
                                req.getCaseId().trim())
                        || AiHarnessBuiltinCases.STOCK_REDUCE_AGENT_GRAPH_CORE.equals(req.getCaseId().trim())
                        || AiHarnessBuiltinCases.STOCK_REDUCE_MATRIX_P1.equals(req.getCaseId().trim())
                        || AiHarnessBuiltinCases.REVENUE_MATRIX_P1.equals(req.getCaseId().trim())
                        || AiHarnessBuiltinCases.WAREHOUSE_MATRIX_P1.equals(req.getCaseId().trim())
                        || AiHarnessBuiltinCases.DISH_SALES_MATRIX_P1.equals(req.getCaseId().trim())
                        || AiHarnessBuiltinCases.DISH_PROFIT_AGENT_GRAPH_CORE.equals(req.getCaseId().trim())
                        || AiHarnessBuiltinCases.DISH_PROFIT_ACTUAL_COST_RANKING_1.equals(req.getCaseId().trim())
                        || AiHarnessBuiltinCases.DISH_PROFIT_HIGH_PROFIT_AMOUNT_RANKING_1.equals(
                                req.getCaseId().trim())
                        || AiHarnessBuiltinCases.DISH_SALES_TO_COST_DIMENSION_SWITCH_2.equals(req.getCaseId().trim())
                        || AiHarnessBuiltinCases.DISH_SALES_TO_MARGIN_DIMENSION_SWITCH_2.equals(req.getCaseId().trim())
                        || AiHarnessBuiltinCases.DISH_PROFIT_COST_TO_SALES_DIMENSION_SWITCH_2.equals(
                                req.getCaseId().trim())
                        || AiHarnessBuiltinCases.DISH_PROFIT_MARGIN_TO_SALES_DIMENSION_SWITCH_2.equals(
                                req.getCaseId().trim())
                        || AiHarnessBuiltinCases.DISH_SALES_TO_AMOUNT_DIMENSION_SWITCH_2.equals(
                                req.getCaseId().trim())
                        || AiHarnessBuiltinCases.DISH_NAMED_DISH_COST_SINGLE_1.equals(req.getCaseId().trim())
                        || AiHarnessBuiltinCases.DISH_INGREDIENT_COVER_SINGLE_1.equals(req.getCaseId().trim())
                        || AiHarnessBuiltinCases.GOODS_SUPPORTED_DISH_COVER_SINGLE_1.equals(req.getCaseId().trim())
                        || AiHarnessBuiltinCases.GOODS_SUPPORTED_DISH_COVER_DAYS_PROBE_1.equals(
                                req.getCaseId().trim())
                        || AiHarnessBuiltinCases.WAREHOUSE_INVENTORY_RISK_TO_DISH_INGREDIENT_COVER_2.equals(
                                req.getCaseId().trim())
                        || AiHarnessBuiltinCases.DISH_LOW_MARGIN_ANCHOR_EXECUTION_INGREDIENT_COST_2.equals(
                                req.getCaseId().trim()))) {
            return AiHarnessReplayMode.GRAPH_RUN;
        }
        if (StringUtils.hasText(req.getCaseId()) && AiHarnessBuiltinCases.isPlannerExecutorMockHarnessCase(req.getCaseId())) {
            return AiHarnessReplayMode.PLANNER_EXECUTOR_MOCK;
        }
        if (req.isIgnoreExpectations()
                && req.getMessages() != null
                && req.getMessages().size() >= 2) {
            return AiHarnessReplayMode.GRAPH_RUN;
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
        if (AiHarnessBuiltinCases.PURCHASE_TOOL_REQUEST_2A_MIN.equals(req.getCaseId().trim())) {
            var anchor = AiHarnessBuiltinCases.LocalDateAnchor.frozenClock(today);
            int n = AiHarnessBuiltinCases.expectationsPurchaseToolRequest2aMin(anchor).size();
            if (req.getMessages().size() < n) {
                log.warn(
                        "[AiHarnessReplay] case={} expects {} rounds, got {}",
                        req.getCaseId(),
                        n,
                        req.getMessages().size());
            }
            return AiHarnessBuiltinCases.expectationsPurchaseToolRequest2aMin(anchor);
        }
        if (AiHarnessBuiltinCases.PURCHASE_TOOL_REQUEST_2A_CORE.equals(req.getCaseId().trim())) {
            var anchor = AiHarnessBuiltinCases.LocalDateAnchor.frozenClock(today);
            int n = AiHarnessBuiltinCases.expectationsPurchaseToolRequest2aCore(anchor).size();
            if (req.getMessages().size() < n) {
                log.warn(
                        "[AiHarnessReplay] case={} expects {} rounds, got {}",
                        req.getCaseId(),
                        n,
                        req.getMessages().size());
            }
            return AiHarnessBuiltinCases.expectationsPurchaseToolRequest2aCore(anchor);
        }
        if (AiHarnessBuiltinCases.PURCHASE_SUPPLIER_RANKING_ANCHOR_EXECUTION_GOODS_UNIT_PRICE_3.equals(req.getCaseId().trim())) {
            var anchor = AiHarnessBuiltinCases.LocalDateAnchor.frozenClock(today);
            int n = AiHarnessBuiltinCases.expectationsPurchaseSupplierRankingAnchorExecutionGoodsUnitPrice3(anchor).size();
            if (req.getMessages().size() < n) {
                log.warn(
                        "[AiHarnessReplay] case={} expects {} rounds, got {}",
                        req.getCaseId(),
                        n,
                        req.getMessages().size());
            }
            return AiHarnessBuiltinCases.expectationsPurchaseSupplierRankingAnchorExecutionGoodsUnitPrice3(anchor);
        }
        if (AiHarnessBuiltinCases.PURCHASE_GOODS_RANKING_ANCHOR_EXECUTION_SUPPLIER_UNIT_PRICE_2.equals(
                req.getCaseId().trim())) {
            var anchor = AiHarnessBuiltinCases.LocalDateAnchor.frozenClock(today);
            int n = AiHarnessBuiltinCases.expectationsPurchaseGoodsRankingAnchorExecutionSupplierUnitPrice2(anchor).size();
            if (req.getMessages().size() < n) {
                log.warn(
                        "[AiHarnessReplay] case={} expects {} rounds, got {}",
                        req.getCaseId(),
                        n,
                        req.getMessages().size());
            }
            return AiHarnessBuiltinCases.expectationsPurchaseGoodsRankingAnchorExecutionSupplierUnitPrice2(anchor);
        }
        if (AiHarnessBuiltinCases.PURCHASE_GOODS_RANKING_SOURCE_BREAKDOWN_2.equals(req.getCaseId().trim())) {
            var anchor = AiHarnessBuiltinCases.LocalDateAnchor.frozenClock(today);
            int n = AiHarnessBuiltinCases.expectationsPurchaseGoodsRankingSourceBreakdown2(anchor).size();
            if (req.getMessages().size() < n) {
                log.warn(
                        "[AiHarnessReplay] case={} expects {} rounds, got {}",
                        req.getCaseId(),
                        n,
                        req.getMessages().size());
            }
            return AiHarnessBuiltinCases.expectationsPurchaseGoodsRankingSourceBreakdown2(anchor);
        }
        if (AiHarnessBuiltinCases.PURCHASE_ANCHOR_EXECUTION_MATRIX_P1.equals(req.getCaseId().trim())) {
            var anchor = AiHarnessBuiltinCases.LocalDateAnchor.frozenClock(today);
            int n = AiHarnessBuiltinCases.expectationsPurchaseAnchorExecutionMatrixP1(anchor).size();
            if (req.getMessages().size() < n) {
                log.warn(
                        "[AiHarnessReplay] case={} expects {} rounds, got {}",
                        req.getCaseId(),
                        n,
                        req.getMessages().size());
            }
            return AiHarnessBuiltinCases.expectationsPurchaseAnchorExecutionMatrixP1(anchor);
        }
        if (AiHarnessBuiltinCases.DISH_PROFIT_MATRIX_P1.equals(req.getCaseId().trim())) {
            var anchor = AiHarnessBuiltinCases.LocalDateAnchor.frozenClock(today);
            int n = AiHarnessBuiltinCases.expectationsDishProfitMatrixP1(anchor).size();
            if (req.getMessages().size() < n) {
                log.warn(
                        "[AiHarnessReplay] case={} expects {} rounds, got {}",
                        req.getCaseId(),
                        n,
                        req.getMessages().size());
            }
            return AiHarnessBuiltinCases.expectationsDishProfitMatrixP1(anchor);
        }
        if (AiHarnessBuiltinCases.STOCK_REDUCE_MATRIX_P1.equals(req.getCaseId().trim())) {
            var anchor = AiHarnessBuiltinCases.LocalDateAnchor.frozenClock(today);
            int n = AiHarnessBuiltinCases.expectationsStockReduceMatrixP1(anchor).size();
            if (req.getMessages().size() < n) {
                log.warn(
                        "[AiHarnessReplay] case={} expects {} rounds, got {}",
                        req.getCaseId(),
                        n,
                        req.getMessages().size());
            }
            return AiHarnessBuiltinCases.expectationsStockReduceMatrixP1(anchor);
        }
        if (AiHarnessBuiltinCases.REVENUE_MATRIX_P1.equals(req.getCaseId().trim())) {
            var anchor = AiHarnessBuiltinCases.LocalDateAnchor.frozenClock(today);
            int n = AiHarnessBuiltinCases.expectationsRevenueMatrixP1(anchor).size();
            if (req.getMessages().size() < n) {
                log.warn(
                        "[AiHarnessReplay] case={} expects {} rounds, got {}",
                        req.getCaseId(),
                        n,
                        req.getMessages().size());
            }
            return AiHarnessBuiltinCases.expectationsRevenueMatrixP1(anchor);
        }
        if (AiHarnessBuiltinCases.WAREHOUSE_MATRIX_P1.equals(req.getCaseId().trim())) {
            var anchor = AiHarnessBuiltinCases.LocalDateAnchor.frozenClock(today);
            int n = AiHarnessBuiltinCases.expectationsWarehouseMatrixP1(anchor).size();
            if (req.getMessages().size() < n) {
                log.warn(
                        "[AiHarnessReplay] case={} expects {} rounds, got {}",
                        req.getCaseId(),
                        n,
                        req.getMessages().size());
            }
            return AiHarnessBuiltinCases.expectationsWarehouseMatrixP1(anchor);
        }
        if (AiHarnessBuiltinCases.DISH_SALES_MATRIX_P1.equals(req.getCaseId().trim())) {
            var anchor = AiHarnessBuiltinCases.LocalDateAnchor.frozenClock(today);
            int n = AiHarnessBuiltinCases.expectationsDishSalesMatrixP1(anchor).size();
            if (req.getMessages().size() < n) {
                log.warn(
                        "[AiHarnessReplay] case={} expects {} rounds, got {}",
                        req.getCaseId(),
                        n,
                        req.getMessages().size());
            }
            return AiHarnessBuiltinCases.expectationsDishSalesMatrixP1(anchor);
        }
        if (AiHarnessBuiltinCases.PURCHASE_SUPPLIER_FACET_GOODS_RANKING_SOURCE_BREAKDOWN_2.equals(
                req.getCaseId().trim())) {
            var anchor = AiHarnessBuiltinCases.LocalDateAnchor.frozenClock(today);
            int n = AiHarnessBuiltinCases.expectationsPurchaseSupplierFacetGoodsRankingSourceBreakdown2(anchor).size();
            if (req.getMessages().size() < n) {
                log.warn(
                        "[AiHarnessReplay] case={} expects {} rounds, got {}",
                        req.getCaseId(),
                        n,
                        req.getMessages().size());
            }
            return AiHarnessBuiltinCases.expectationsPurchaseSupplierFacetGoodsRankingSourceBreakdown2(anchor);
        }
        if (AiHarnessBuiltinCases.PURCHASE_SUPPLIER_FACET_GOODS_AMOUNT_RANKING_IGNORE_ANCHOR_2.equals(
                req.getCaseId().trim())) {
            var anchor = AiHarnessBuiltinCases.LocalDateAnchor.frozenClock(today);
            int n = AiHarnessBuiltinCases.expectationsPurchaseSupplierFacetGoodsAmountRankingIgnoreAnchor2(anchor)
                    .size();
            if (req.getMessages().size() < n) {
                log.warn(
                        "[AiHarnessReplay] case={} expects {} rounds, got {}",
                        req.getCaseId(),
                        n,
                        req.getMessages().size());
            }
            return AiHarnessBuiltinCases.expectationsPurchaseSupplierFacetGoodsAmountRankingIgnoreAnchor2(anchor);
        }
        if (AiHarnessBuiltinCases.PURCHASE_SUPPLIER_CHANNEL_OVERVIEW_GOODS_DETAIL_2.equals(req.getCaseId().trim())) {
            var anchor = AiHarnessBuiltinCases.LocalDateAnchor.frozenClock(today);
            int n = AiHarnessBuiltinCases.expectationsPurchaseSupplierChannelOverviewGoodsDetail2(anchor).size();
            if (req.getMessages().size() < n) {
                log.warn(
                        "[AiHarnessReplay] case={} expects {} rounds, got {}",
                        req.getCaseId(),
                        n,
                        req.getMessages().size());
            }
            return AiHarnessBuiltinCases.expectationsPurchaseSupplierChannelOverviewGoodsDetail2(anchor);
        }
        if (AiHarnessBuiltinCases.PURCHASE_SUPPLIER_ANCHOR_THEN_SOURCE_AMOUNT_SUMMARY_2.equals(
                req.getCaseId().trim())) {
            var anchor = AiHarnessBuiltinCases.LocalDateAnchor.frozenClock(today);
            int n = AiHarnessBuiltinCases.expectationsPurchaseSupplierAnchorThenSourceAmountSummary2(anchor).size();
            if (req.getMessages().size() < n) {
                log.warn(
                        "[AiHarnessReplay] case={} expects {} rounds, got {}",
                        req.getCaseId(),
                        n,
                        req.getMessages().size());
            }
            return AiHarnessBuiltinCases.expectationsPurchaseSupplierAnchorThenSourceAmountSummary2(anchor);
        }
        if (AiHarnessBuiltinCases.PURCHASE_PERIOD_GOODS_LIST_1.equals(req.getCaseId().trim())) {
            var anchor = AiHarnessBuiltinCases.LocalDateAnchor.frozenClock(today);
            int n = AiHarnessBuiltinCases.expectationsPurchasePeriodGoodsList1(anchor).size();
            if (req.getMessages().size() < n) {
                log.warn(
                        "[AiHarnessReplay] case={} expects {} rounds, got {}",
                        req.getCaseId(),
                        n,
                        req.getMessages().size());
            }
            return AiHarnessBuiltinCases.expectationsPurchasePeriodGoodsList1(anchor);
        }
        if (AiHarnessBuiltinCases.PURCHASE_PERIOD_GOODS_LIST_SELF_1.equals(req.getCaseId().trim())) {
            var anchor = AiHarnessBuiltinCases.LocalDateAnchor.frozenClock(today);
            int n = AiHarnessBuiltinCases.expectationsPurchasePeriodGoodsListSelf1(anchor).size();
            if (req.getMessages().size() < n) {
                log.warn(
                        "[AiHarnessReplay] case={} expects {} rounds, got {}",
                        req.getCaseId(),
                        n,
                        req.getMessages().size());
            }
            return AiHarnessBuiltinCases.expectationsPurchasePeriodGoodsListSelf1(anchor);
        }
        if (AiHarnessBuiltinCases.PURCHASE_PERIOD_GOODS_LIST_SUPPLIER_1.equals(req.getCaseId().trim())) {
            var anchor = AiHarnessBuiltinCases.LocalDateAnchor.frozenClock(today);
            int n = AiHarnessBuiltinCases.expectationsPurchasePeriodGoodsListSupplier1(anchor).size();
            if (req.getMessages().size() < n) {
                log.warn(
                        "[AiHarnessReplay] case={} expects {} rounds, got {}",
                        req.getCaseId(),
                        n,
                        req.getMessages().size());
            }
            return AiHarnessBuiltinCases.expectationsPurchasePeriodGoodsListSupplier1(anchor);
        }
        if (AiHarnessBuiltinCases.BUSINESS_STORE_PRIORITY_REASON_EXPLANATION_3.equals(req.getCaseId().trim())) {
            var anchor = AiHarnessBuiltinCases.LocalDateAnchor.frozenClock(today);
            int n = AiHarnessBuiltinCases.expectationsBusinessStorePriorityReasonExplanation3(anchor).size();
            if (req.getMessages().size() < n) {
                log.warn(
                        "[AiHarnessReplay] case={} expects {} rounds, got {}",
                        req.getCaseId(),
                        n,
                        req.getMessages().size());
            }
            return AiHarnessBuiltinCases.expectationsBusinessStorePriorityReasonExplanation3(anchor);
        }
        if (AiHarnessBuiltinCases.BUSINESS_DIAGNOSIS_ANCHOR_EXECUTION_MATRIX_P1.equals(req.getCaseId().trim())) {
            var anchor = AiHarnessBuiltinCases.LocalDateAnchor.frozenClock(today);
            int n = AiHarnessBuiltinCases.expectationsBusinessDiagnosisSemanticCapabilityMatrixP1(anchor).size();
            if (req.getMessages().size() < n) {
                log.warn(
                        "[AiHarnessReplay] case={} expects {} rounds, got {}",
                        req.getCaseId(),
                        n,
                        req.getMessages().size());
            }
            return AiHarnessBuiltinCases.expectationsBusinessDiagnosisSemanticCapabilityMatrixP1(anchor);
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
        if (AiHarnessBuiltinCases.DISH_PROFIT_ACTUAL_COST_RANKING_1.equals(req.getCaseId().trim())) {
            var anchor = AiHarnessBuiltinCases.LocalDateAnchor.frozenClock(today);
            int n = AiHarnessBuiltinCases.expectationsDishProfitActualCostRanking1(anchor).size();
            if (req.getMessages().size() < n) {
                log.warn(
                        "[AiHarnessReplay] case={} expects {} rounds, got {}",
                        req.getCaseId(),
                        n,
                        req.getMessages().size());
            }
            return AiHarnessBuiltinCases.expectationsDishProfitActualCostRanking1(anchor);
        }
        if (AiHarnessBuiltinCases.DISH_PROFIT_HIGH_PROFIT_AMOUNT_RANKING_1.equals(req.getCaseId().trim())) {
            var anchor = AiHarnessBuiltinCases.LocalDateAnchor.frozenClock(today);
            return AiHarnessBuiltinCases.expectationsDishProfitHighProfitAmountRanking1(anchor);
        }
        if (AiHarnessBuiltinCases.DISH_SALES_TO_COST_DIMENSION_SWITCH_2.equals(req.getCaseId().trim())) {
            var anchor = AiHarnessBuiltinCases.LocalDateAnchor.frozenClock(today);
            return AiHarnessBuiltinCases.expectationsDishSalesToCostDimensionSwitch2(anchor);
        }
        if (AiHarnessBuiltinCases.DISH_SALES_TO_MARGIN_DIMENSION_SWITCH_2.equals(req.getCaseId().trim())) {
            var anchor = AiHarnessBuiltinCases.LocalDateAnchor.frozenClock(today);
            return AiHarnessBuiltinCases.expectationsDishSalesToMarginDimensionSwitch2(anchor);
        }
        if (AiHarnessBuiltinCases.DISH_PROFIT_COST_TO_SALES_DIMENSION_SWITCH_2.equals(req.getCaseId().trim())) {
            var anchor = AiHarnessBuiltinCases.LocalDateAnchor.frozenClock(today);
            return AiHarnessBuiltinCases.expectationsDishProfitCostToSalesDimensionSwitch2(anchor);
        }
        if (AiHarnessBuiltinCases.DISH_PROFIT_MARGIN_TO_SALES_DIMENSION_SWITCH_2.equals(req.getCaseId().trim())) {
            var anchor = AiHarnessBuiltinCases.LocalDateAnchor.frozenClock(today);
            return AiHarnessBuiltinCases.expectationsDishProfitMarginToSalesDimensionSwitch2(anchor);
        }
        if (AiHarnessBuiltinCases.DISH_SALES_TO_AMOUNT_DIMENSION_SWITCH_2.equals(req.getCaseId().trim())) {
            var anchor = AiHarnessBuiltinCases.LocalDateAnchor.frozenClock(today);
            return AiHarnessBuiltinCases.expectationsDishSalesToAmountDimensionSwitch2(anchor);
        }
        if (AiHarnessBuiltinCases.DISH_NAMED_DISH_COST_SINGLE_1.equals(req.getCaseId().trim())) {
            var anchor = AiHarnessBuiltinCases.LocalDateAnchor.frozenClock(today);
            return AiHarnessBuiltinCases.expectationsDishNamedDishCostSingle1(anchor);
        }
        if (AiHarnessBuiltinCases.DISH_INGREDIENT_COVER_SINGLE_1.equals(req.getCaseId().trim())) {
            var anchor = AiHarnessBuiltinCases.LocalDateAnchor.frozenClock(today);
            return AiHarnessBuiltinCases.expectationsDishIngredientCoverSingle1(anchor);
        }
        if (AiHarnessBuiltinCases.GOODS_SUPPORTED_DISH_COVER_SINGLE_1.equals(req.getCaseId().trim())) {
            var anchor = AiHarnessBuiltinCases.LocalDateAnchor.frozenClock(today);
            return AiHarnessBuiltinCases.expectationsGoodsSupportedDishCoverSingle1(anchor);
        }
        if (AiHarnessBuiltinCases.GOODS_SUPPORTED_DISH_COVER_DAYS_PROBE_1.equals(req.getCaseId().trim())) {
            var anchor = AiHarnessBuiltinCases.LocalDateAnchor.frozenClock(today);
            return AiHarnessBuiltinCases.expectationsGoodsSupportedDishCoverDaysProbe1(anchor);
        }
        if (AiHarnessBuiltinCases.WAREHOUSE_INVENTORY_RISK_TO_DISH_INGREDIENT_COVER_2.equals(
                req.getCaseId().trim())) {
            var anchor = AiHarnessBuiltinCases.LocalDateAnchor.frozenClock(today);
            int n =
                    AiHarnessBuiltinCases.expectationsWarehouseInventoryRiskToDishIngredientCover2(
                                    anchor)
                            .size();
            if (req.getMessages().size() < n) {
                log.warn(
                        "[AiHarnessReplay] case={} expects {} rounds, got {}",
                        req.getCaseId(),
                        n,
                        req.getMessages().size());
            }
            return AiHarnessBuiltinCases.expectationsWarehouseInventoryRiskToDishIngredientCover2(
                    anchor);
        }
        if (AiHarnessBuiltinCases.DISH_LOW_MARGIN_ANCHOR_EXECUTION_INGREDIENT_COST_2.equals(req.getCaseId().trim())) {
            var anchor = AiHarnessBuiltinCases.LocalDateAnchor.frozenClock(today);
            int n = AiHarnessBuiltinCases.expectationsDishLowMarginAnchorExecutionIngredientCost2(anchor).size();
            if (req.getMessages().size() < n) {
                log.warn(
                        "[AiHarnessReplay] case={} expects {} rounds, got {}",
                        req.getCaseId(),
                        n,
                        req.getMessages().size());
            }
            return AiHarnessBuiltinCases.expectationsDishLowMarginAnchorExecutionIngredientCost2(anchor);
        }
        if (AiHarnessBuiltinCases.BUSINESS_SEMANTIC_1B_RESOLVED_CONTEXT.equals(req.getCaseId().trim())) {
            var anchor = AiHarnessBuiltinCases.LocalDateAnchor.frozenClock(today);
            int n = AiHarnessBuiltinCases.expectationsBusinessSemantic1bResolvedContext(anchor).size();
            if (req.getMessages().size() < n) {
                log.warn(
                        "[AiHarnessReplay] case={} expects {} rounds, got {}",
                        req.getCaseId(),
                        n,
                        req.getMessages().size());
            }
            return AiHarnessBuiltinCases.expectationsBusinessSemantic1bResolvedContext(anchor);
        }
        if (AiHarnessBuiltinCases.STOCK_REDUCE_SEMANTIC_1C_RESOLVED_CONTEXT.equals(req.getCaseId().trim())) {
            var anchor = AiHarnessBuiltinCases.LocalDateAnchor.frozenClock(today);
            int n = AiHarnessBuiltinCases.expectationsStockReduceSemantic1cResolvedContext(anchor).size();
            if (req.getMessages().size() < n) {
                log.warn(
                        "[AiHarnessReplay] case={} expects {} rounds, got {}",
                        req.getCaseId(),
                        n,
                        req.getMessages().size());
            }
            return AiHarnessBuiltinCases.expectationsStockReduceSemantic1cResolvedContext(anchor);
        }
        throw new IllegalArgumentException("unknown harness caseId: " + req.getCaseId());
    }

    /**
     * 请求体已带非空 {@code messages} 时不改动；否则对已知内置 {@code caseId} 写入
     * {@link AiHarnessBuiltinCases#builtinMessagesForCaseIdOrNull(String)}，再交后续校验。
     */
    private static void applyBuiltinMessagesIfMissing(AiHarnessReplayRequest req) {
        if (req.getMessages() != null && !req.getMessages().isEmpty()) {
            return;
        }
        List<String> fromBuiltin = AiHarnessBuiltinCases.builtinMessagesForCaseIdOrNull(req.getCaseId());
        if (fromBuiltin != null && !fromBuiltin.isEmpty()) {
            req.setMessages(fromBuiltin);
        }
    }
}
