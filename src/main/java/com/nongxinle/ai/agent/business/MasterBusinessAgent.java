package com.nongxinle.ai.agent.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.BusinessOverviewAnswerPlan;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.graph.business.toolrequest.BusinessToolExecutionRequestResolver;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.graph.business.toolrequest.DishProfitToolRequestContext;
import com.nongxinle.ai.graph.business.toolrequest.PurchaseToolRequestContext;
import com.nongxinle.ai.graph.business.toolrequest.StockReduceToolRequestContext;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 经营域 Master：REVENUE_OVERVIEW / PURCHASE_OVERVIEW / STOCK_REDUCE_QUERY / DISH_PROFIT 专线窄口径编排。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MasterBusinessAgent {

    private final BusinessAgentRegistry registry;
    private final BusinessToolExecutionRequestResolver toolExecutionRequestResolver;

    /**
     * 经营概览 + v2 MULTI_AGENT 编排：串联四专线子 Agent，共用 {@link BusinessToolExecutionRequestResolver}；
     * 不进行用户原文理解与 SQL 拼装。
     */
    public MasterBusinessAgentResult tryOrchestrateBusinessOverviewMultiAgent(AiRunState state) {
        LinkedHashMap<String, Object> dbg = new LinkedHashMap<>();
        dbg.put("businessOverviewMultiMasterEnabled", false);
        dbg.put("businessOverviewMultiAgentBatchCompleted", false);
        dbg.put("businessOverviewMultiAgentAnyDomainSuccess", false);
        dbg.put("businessOverviewMultiAgentBatchAttempted", false);
        dbg.put("businessOverviewMultiAgentAllDomainsSkipped", true);
        dbg.put("businessOverviewMultiAgentBatchUsableForDiagnosis", false);
        if (!eligibleForBusinessOverviewMultiAgentOrchestration(state)) {
            return MasterBusinessAgentResult.builder()
                    .masterAgentEnabled(false)
                    .masterAgentUsed(false)
                    .fallbackUsed(false)
                    .fallbackReason("not_eligible_for_business_overview_multi_agent_gate")
                    .agentResults(List.of())
                    .debug(dbg)
                    .businessOverviewMultiAgentBatchCompleted(false)
                    .businessOverviewMultiAgentBatchAttempted(false)
                    .businessOverviewMultiAgentAnyDomainSuccess(false)
                    .build();
        }

        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        dbg.put("businessOverviewMultiMasterEnabled", true);
        Instant startedAt = Instant.now();
        BusinessAgentDispatchPlan dispatchPlan = buildFourDomainHarnessDispatchPlan(state);
        dbg.put("businessOverviewDispatchPlan", summarizeDispatchPlan(dispatchPlan));

        BusinessAgentRequest.BusinessAgentRequestBuilder requestBuilder =
                BusinessAgentRequest.builder()
                        .runId(state.getRunId())
                        .conversationId(state.getConversationId())
                        .userId(state.getUserId())
                        .distributerId(state.getDistributerId())
                        .resolvedQueryContext(rq)
                        .semanticResult(rq != null ? rq.getQuerySemanticParse() : null)
                        .executionContext(state)
                        .orchestratedBusinessOverviewMultiAgent(true)
                        .debugOptions(new LinkedHashMap<>());
        BusinessFourDomainHarnessSupport.populateHarnessContract(requestBuilder, state, rq);
        BusinessAgentRequest request = requestBuilder.build();

        dbg.put("harnessOrchestratedSurfacePath", request.getOrchestratedSurfacePathCode());
        dbg.put("harnessOrchestratedSurfaceIntent", request.getOrchestratedSurfaceIntentCode());
        dbg.put("harnessOrchestratedPurposeIntent", request.getOrchestratedPurposeIntentCode());
        dbg.put("harnessOriginalEffectivePath", request.getOrchestratedOriginalPathCode());
        dbg.put("harnessOriginalEffectiveIntent", request.getOrchestratedOriginalIntentCode());

        List<AgentResultEnvelope> envelopes = new ArrayList<>();
        List<String> warn = new ArrayList<>();
        List<Map<String, Object>> bizOverviewRows = new ArrayList<>();
        List<String> attemptedDomains = new ArrayList<>();
        List<String> successfulDomains = new ArrayList<>();
        List<String> failedDomains = new ArrayList<>();
        AgentTraceEnvelope trace = null;

        record AgentRun(String beanName, String toolIdSkipWhenFailed) {}

        List<AgentRun> sequence = List.of(
                new AgentRun(BusinessAgentNames.REVENUE_OVERVIEW, AiBusinessToolIds.REVENUE_QUERY),
                new AgentRun(BusinessAgentNames.PURCHASE_OVERVIEW, AiBusinessToolIds.PURCHASE_OVERVIEW),
                new AgentRun(BusinessAgentNames.STOCK_REDUCE_QUERY, AiBusinessToolIds.STOCK_REDUCE_QUERY),
                new AgentRun(BusinessAgentNames.DISH_PROFIT_ANALYSIS, AiBusinessToolIds.DISH_PROFIT_ANALYSIS));
        List<String> dataPlanToolIds =
                state.getDataPlanTools() != null ? new ArrayList<>(state.getDataPlanTools()) : List.of();
        dbg.put("businessOverviewDataPlanTools", dataPlanToolIds);

        boolean anyDomainOk = false;
        try {
            int stepOrder = 0;
            for (AgentRun step : sequence) {
                stepOrder++;
                if (!dataPlanToolIds.isEmpty() && !dataPlanToolIds.contains(step.toolIdSkipWhenFailed())) {
                    warn.add("agent_skipped_not_in_data_plan:" + step.beanName());
                    bizOverviewRows.add(
                            businessOverviewTraceRowSkipped(
                                    step.beanName(),
                                    step.toolIdSkipWhenFailed(),
                                    businessOverviewDomainKey(step.toolIdSkipWhenFailed()),
                                    stepOrder,
                                    "SKIPPED_NOT_IN_DATA_PLAN",
                                    "tool not in DataPlanner matrix plan"));
                    stripOverviewToolEnvelope(state, step.toolIdSkipWhenFailed(), null);
                    continue;
                }
                String domainKey = businessOverviewDomainKey(step.toolIdSkipWhenFailed());
                Optional<BusinessSubAgent> agentOpt = registry.getAgent(step.beanName());
                if (agentOpt.isEmpty()) {
                    warn.add("agent_missing:" + step.beanName());
                    failedDomains.add(domainKey);
                    bizOverviewRows.add(businessOverviewTraceRowSkipped(step.beanName(), step.toolIdSkipWhenFailed(),
                            domainKey, stepOrder, "AGENT_REGISTRY_MISSING", "agent bean not registered"));
                    stripOverviewToolEnvelope(state, step.toolIdSkipWhenFailed(), null);
                    continue;
                }
                BusinessSubAgent agent = agentOpt.get();
                BusinessFourDomainHarnessSupport.applyHarnessStepTarget(request, step.beanName());
                if (!agent.supports(request)) {
                    warn.add("agent_supports_false:" + step.beanName());
                    failedDomains.add(domainKey);
                    bizOverviewRows.add(businessOverviewTraceRowSkipped(step.beanName(), step.toolIdSkipWhenFailed(),
                            domainKey, stepOrder, "SKIPPED_SUPPORTS_FALSE", "supports(request)=false"));
                    stripOverviewToolEnvelope(state, step.toolIdSkipWhenFailed(), null);
                    continue;
                }
                attemptedDomains.add(domainKey);
                AgentResultEnvelope env = agent.execute(request);
                envelopes.add(env);
                boolean ok = toolSuccessOkFor(step.toolIdSkipWhenFailed(), env);
                if (ok) {
                    anyDomainOk = true;
                    successfulDomains.add(domainKey);
                } else {
                    warn.add(sectionWarn(step.toolIdSkipWhenFailed(), env));
                    failedDomains.add(domainKey);
                    stripOverviewToolEnvelope(state, step.toolIdSkipWhenFailed(), env);
                }
                bizOverviewRows.add(businessOverviewTraceRowFromEnvelope(
                        step.beanName(), step.toolIdSkipWhenFailed(), domainKey, stepOrder, "EXECUTED", env));
            }

            boolean batchLoopFinishedWithoutFatal = true;
            boolean executedAnyAgent = attemptedDomains.size() > 0;
            boolean allDomainsSkippedNoExecute = attemptedDomains.isEmpty();

            dbg.put("businessOverviewOrchestrationWarnings", new ArrayList<>(warn));
            dbg.put("businessOverviewAgentResults", new ArrayList<>(bizOverviewRows));
            dbg.put("businessOverviewSelectedAgents",
                    sequence.stream().map(AgentRun::beanName).collect(Collectors.toList()));
            dbg.put("businessOverviewExpectedDomainOrder",
                    sequence.stream()
                            .map(s -> businessOverviewDomainKey(s.toolIdSkipWhenFailed()))
                            .collect(Collectors.toList()));
            dbg.put("businessOverviewAgentResultStatus",
                    String.format(Locale.ROOT,
                            "attemptedDomains=%d successful=%d failed=%d expectedSteps=%d",
                            attemptedDomains.size(),
                            successfulDomains.size(),
                            failedDomains.size(),
                            sequence.size()));
            dbg.put("businessOverviewDomainsAttempted", new ArrayList<>(attemptedDomains));
            dbg.put("businessOverviewSuccessfulDomains", new ArrayList<>(successfulDomains));
            dbg.put("businessOverviewFailedDomains", new ArrayList<>(failedDomains));
            dbg.put("businessOverviewMultiAgentBatchAttempted", attemptedDomains.size() > 0);
            dbg.put("businessOverviewMultiAgentBatchCompleted", batchLoopFinishedWithoutFatal);
            dbg.put("businessOverviewMultiAgentBatchLoopFinished", batchLoopFinishedWithoutFatal);
            dbg.put("businessOverviewMultiAgentAllExpectedDomainsExecuted",
                    attemptedDomains.size() == sequence.size());
            dbg.put("businessOverviewMultiAgentAllDomainsSkipped", allDomainsSkippedNoExecute);
            dbg.put("businessOverviewMultiAgentAnyDomainSuccess", anyDomainOk);
            dbg.put("businessOverviewMultiAgentBatchUsableForDiagnosis", anyDomainOk);
            dbg.put("businessOverviewAllExpectedDomainsAttempted", attemptedDomains.size() == sequence.size());
            dbg.put("businessOverviewEnvelopeSummary", summarizeEnvelopes(envelopes));

            Instant finishedAt = Instant.now();
            trace = buildTrace(state, rq, dispatchPlan, envelopes, startedAt, finishedAt,
                    semanticSummary(rq), resolvedSummary(rq));
            assembleBusinessOverviewAnswerPlan(state, warn, dbg);

            return MasterBusinessAgentResult.builder()
                    .trace(trace)
                    .agentResults(envelopes)
                    .masterAgentEnabled(true)
                    .masterAgentUsed(anyDomainOk)
                    .fallbackUsed(!anyDomainOk)
                    .fallbackReason(anyDomainOk ? null : "business_overview_multi_all_domains_failed")
                    .debug(dbg)
                    .businessOverviewMultiAgentBatchCompleted(batchLoopFinishedWithoutFatal)
                    .businessOverviewMultiAgentBatchAttempted(executedAnyAgent)
                    .businessOverviewMultiAgentAnyDomainSuccess(anyDomainOk)
                    .build();
        } catch (Exception ex) {
            log.warn("[MasterBusinessAgent] business overview multi-agent orchestration failed runId={}",
                    state.getRunId(), ex);
            boolean executedAnyAgent = attemptedDomains.size() > 0;
            dbg.put("businessOverviewMultiException", ex.getClass().getSimpleName());
            dbg.put("businessOverviewMultiAgentBatchAttempted", attemptedDomains.size() > 0);
            dbg.put("businessOverviewMultiAgentBatchLoopFinished", false);
            dbg.put("businessOverviewMultiAgentBatchCompleted", false);
            dbg.put("businessOverviewMultiAgentAllExpectedDomainsExecuted", false);
            dbg.put("businessOverviewMultiAgentAllDomainsSkipped", attemptedDomains.isEmpty());
            dbg.put("businessOverviewMultiAgentBatchUsableForDiagnosis", anyDomainOk);
            dbg.put("businessOverviewMultiAgentAnyDomainSuccess", anyDomainOk);
            dbg.put("businessOverviewAgentResults", new ArrayList<>(bizOverviewRows));
            dbg.put("businessOverviewOrchestrationWarnings", new ArrayList<>(warn));
            trace = buildTrace(state, rq, dispatchPlan, envelopes, startedAt, Instant.now(),
                    semanticSummary(rq), resolvedSummary(rq));
            warn.add("orchestration_exception:" + ex.getClass().getSimpleName());
            assembleBusinessOverviewAnswerPlan(state, warn, dbg);
            return MasterBusinessAgentResult.builder()
                    .trace(trace)
                    .agentResults(envelopes)
                    .masterAgentEnabled(true)
                    .masterAgentUsed(false)
                    .fallbackUsed(true)
                    .fallbackReason("business_overview_master_exception:" + ex.getClass().getSimpleName())
                    .debug(dbg)
                    .businessOverviewMultiAgentBatchCompleted(false)
                    .businessOverviewMultiAgentBatchAttempted(executedAnyAgent)
                    .businessOverviewMultiAgentAnyDomainSuccess(anyDomainOk)
                    .build();
        }
    }

    /**
     * MultiAgent 概览的 DishProfitAnswerPlan 在 OutcomeReview 的 {@link com.nongxinle.ai.graph.business.DishProfitAgentNode#aggregateIfApplicable}
     * 之后才可挂载；本方法再合并一次 {@link BusinessOverviewAnswerPlan}。
     */
    public void refreshBusinessOverviewMultiAgentPlanIfApplicable(AiRunState state) {
        if (state == null) {
            return;
        }
        BusinessOverviewAnswerPlan prev = state.getBusinessOverviewAnswerPlan();
        if (prev == null) {
            return;
        }
        if (!BusinessOverviewAnswerPlan.PLAN_TYPE_BUSINESS_OVERVIEW_MULTI_AGENT_V1.equals(prev.getPlanType())
                && !BusinessOverviewAnswerPlan.PLAN_TYPE_BUSINESS_DIAGNOSIS_MULTI_AGENT_V1.equals(
                        prev.getPlanType())) {
            return;
        }
        List<String> w = prev.getWarnings() != null ? new ArrayList<>(prev.getWarnings()) : new ArrayList<>();
        Map<String, Object> dbgPrev = prev.getDebug();
        LinkedHashMap<String, Object> diag =
                dbgPrev != null && !dbgPrev.isEmpty() ? copyStringKeyMap(dbgPrev) : new LinkedHashMap<>();
        diag.put("refreshedAfterDishNode", true);
        assembleBusinessOverviewAnswerPlan(state, w, diag);
    }

    /**
     * 在 Tool 循环前调用：仅 REVENUE_OVERVIEW 专线且计划仅为 revenue_query 时尝试编排。
     */
    public MasterBusinessAgentResult tryOrchestrateRevenueOverview(AiRunState state) {
        LinkedHashMap<String, Object> dbg = new LinkedHashMap<>();
        if (!eligibleForMasterRevenueOverview(state)) {
            dbg.put("masterAgentEnabled", false);
            dbg.put("masterAgentUsed", false);
            dbg.put("selectedAgents", List.of());
            dbg.put("dispatchPlan", null);
            dbg.put("agentResults", List.of());
            dbg.put("agentResultStatus", null);
            dbg.put("degraded", false);
            dbg.put("failurePolicy", null);
            dbg.put("fallbackUsed", false);
            dbg.put("fallbackReason", "not_eligible_for_master_revenue_gate");
            dbg.put("legacyRevenueSkipped", false);
            dbg.put("masterRevenueToolResultKey", null);
            dbg.put("masterRevenueToolResultSuccess", null);
            dbg.put("revenueToolExecutedByMasterPath", false);
            return MasterBusinessAgentResult.builder()
                    .masterAgentEnabled(false)
                    .masterAgentUsed(false)
                    .fallbackUsed(false)
                    .fallbackReason("not_eligible_for_master_revenue_gate")
                    .agentResults(List.of())
                    .debug(dbg)
                    .revenueToolExecutedByMasterPath(false)
                    .build();
        }

        dbg.put("masterAgentEnabled", true);
        Instant startedAt = Instant.now();
        BusinessAgentDispatchPlan dispatchPlan = buildRevenueDispatchPlan();
        dbg.put("dispatchPlan", summarizeDispatchPlan(dispatchPlan));
        dbg.put("failurePolicy", AgentFailurePolicy.FAIL_FAST.name());

        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        BusinessAgentRequest request = BusinessAgentRequest.builder()
                .runId(state.getRunId())
                .conversationId(state.getConversationId())
                .userId(state.getUserId())
                .distributerId(state.getDistributerId())
                .resolvedQueryContext(rq)
                .semanticResult(rq != null ? rq.getQuerySemanticParse() : null)
                .executionContext(state)
                .debugOptions(new LinkedHashMap<>())
                .build();

        List<AgentResultEnvelope> envelopes = new ArrayList<>();
        AgentTraceEnvelope trace = null;
        try {
            Optional<BusinessSubAgent> agentOpt = registry.getAgent(BusinessAgentNames.REVENUE_OVERVIEW);
            if (agentOpt.isEmpty()) {
                return fallback(dbg, envelopes, dispatchPlan, startedAt, rq,
                        "revenue_agent_not_registered", state);
            }
            BusinessSubAgent agent = agentOpt.get();
            if (!agent.supports(request)) {
                return fallback(dbg, envelopes, dispatchPlan, startedAt, rq,
                        "revenue_agent_supports_false", state);
            }

            AgentResultEnvelope env = agent.execute(request);
            envelopes.add(env);

            dbg.put("selectedAgents", List.of(BusinessAgentNames.REVENUE_OVERVIEW));
            dbg.put("agentResults", summarizeEnvelopes(envelopes));
            dbg.put("agentResultStatus", env.getStatus() != null ? env.getStatus().name() : null);
            dbg.put("degraded", env.isDegraded());

            if (!masterRevenuePathAllowsLegacySkip(env)) {
                dbg.put("masterAgentUsed", false);
                dbg.put("fallbackUsed", true);
                dbg.put("fallbackReason", "agent_status_" + env.getStatus()
                        + "_or_tool_success_" + env.getRevenueQueryToolSuccess());
                dbg.put("legacyRevenueSkipped", true);
                dbg.put("revenueToolExecutedByMasterPath", true);
                dbg.put("masterRevenueToolResultKey", AiBusinessToolIds.REVENUE_QUERY);
                dbg.put("masterRevenueToolResultSuccess", env.getRevenueQueryToolSuccess());
                trace = buildTrace(state, rq, dispatchPlan, envelopes, startedAt, Instant.now(),
                        semanticSummary(rq), resolvedSummary(rq));
                return MasterBusinessAgentResult.builder()
                        .trace(trace)
                        .agentResults(envelopes)
                        .masterAgentEnabled(true)
                        .masterAgentUsed(false)
                        .fallbackUsed(true)
                        .fallbackReason("agent_status_" + env.getStatus()
                                + "_or_tool_success_" + env.getRevenueQueryToolSuccess())
                        .debug(dbg)
                        .revenueToolExecutedByMasterPath(true)
                        .build();
            }

            dbg.put("masterAgentUsed", true);
            dbg.put("fallbackUsed", false);
            dbg.put("fallbackReason", null);
            dbg.put("legacyRevenueSkipped", true);
            dbg.put("revenueToolExecutedByMasterPath", true);
            dbg.put("masterRevenueToolResultKey", AiBusinessToolIds.REVENUE_QUERY);
            dbg.put("masterRevenueToolResultSuccess", env.getRevenueQueryToolSuccess());
            Instant finishedAt = Instant.now();
            trace = buildTrace(state, rq, dispatchPlan, envelopes, startedAt, finishedAt,
                    semanticSummary(rq), resolvedSummary(rq));
            return MasterBusinessAgentResult.builder()
                    .trace(trace)
                    .agentResults(envelopes)
                    .masterAgentEnabled(true)
                    .masterAgentUsed(true)
                    .fallbackUsed(false)
                    .fallbackReason(null)
                    .debug(dbg)
                    .revenueToolExecutedByMasterPath(true)
                    .build();
        } catch (Exception ex) {
            log.warn("[MasterBusinessAgent] revenue overview orchestration failed runId={}", state.getRunId(), ex);
            dbg.put("masterAgentUsed", false);
            dbg.put("fallbackUsed", true);
            dbg.put("fallbackReason", "master_exception:" + ex.getClass().getSimpleName());
            dbg.put("legacyRevenueSkipped", true);
            dbg.put("revenueToolExecutedByMasterPath", true);
            dbg.put("masterRevenueToolResultKey", AiBusinessToolIds.REVENUE_QUERY);
            dbg.put("masterRevenueToolResultSuccess", null);
            dbg.put("selectedAgents", List.of(BusinessAgentNames.REVENUE_OVERVIEW));
            dbg.put("agentResults", summarizeEnvelopes(envelopes));
            trace = buildTrace(state, rq, dispatchPlan, envelopes, startedAt, Instant.now(),
                    semanticSummary(rq), resolvedSummary(rq));
            return MasterBusinessAgentResult.builder()
                    .trace(trace)
                    .agentResults(envelopes)
                    .masterAgentEnabled(true)
                    .masterAgentUsed(false)
                    .fallbackUsed(true)
                    .fallbackReason("master_exception:" + ex.getClass().getSimpleName())
                    .debug(dbg)
                    .revenueToolExecutedByMasterPath(true)
                    .build();
        }
    }

    /**
     * 采购总览专线：仅 PURCHASE_OVERVIEW + purchase_overview_path + 计划仅 {@link com.nongxinle.ai.tool.business.AiBusinessToolIds#PURCHASE_OVERVIEW}。
     */
    public MasterBusinessAgentResult tryOrchestratePurchaseOverview(AiRunState state) {
        LinkedHashMap<String, Object> dbg = new LinkedHashMap<>();
        if (!eligibleForMasterPurchaseOverview(state)) {
            dbg.put("purchaseMasterAgentEnabled", false);
            dbg.put("purchaseMasterAgentUsed", false);
            putSupplierAnalysisHarnessContract(dbg, null);
            dbg.put("purchaseSelectedAgents", List.of());
            dbg.put("purchaseDispatchPlan", null);
            dbg.put("purchaseAgentResults", List.of());
            dbg.put("purchaseAgentResultStatus", null);
            dbg.put("purchaseDegraded", false);
            dbg.put("purchaseFailurePolicy", null);
            dbg.put("purchaseFallbackUsed", false);
            dbg.put("purchaseFallbackReason", "not_eligible_for_master_purchase_gate");
            dbg.put("legacyPurchaseSkipped", false);
            dbg.put("masterPurchaseToolResultKey", null);
            dbg.put("masterPurchaseToolResultSuccess", null);
            dbg.put("purchaseToolExecutedByMasterPath", false);
            return MasterBusinessAgentResult.builder()
                    .masterAgentEnabled(false)
                    .masterAgentUsed(false)
                    .fallbackUsed(false)
                    .fallbackReason("not_eligible_for_master_purchase_gate")
                    .agentResults(List.of())
                    .debug(dbg)
                    .revenueToolExecutedByMasterPath(false)
                    .purchaseToolExecutedByMasterPath(false)
                    .build();
        }

        AiResolvedQueryContext rqEarly = state.getResolvedQueryContext();
        PurchaseToolRequestContext purchaseSnap =
                toolExecutionRequestResolver.buildPurchaseRequestContext(state, rqEarly);
        dbg.put("purchaseRequestResolutionDebug", new LinkedHashMap<>(purchaseSnap.getResolutionDebug()));

        dbg.put("purchaseMasterAgentEnabled", true);
        Instant startedAt = Instant.now();
        BusinessAgentDispatchPlan dispatchPlan = buildPurchaseDispatchPlan();
        dbg.put("purchaseDispatchPlan", summarizeDispatchPlan(dispatchPlan));
        dbg.put("purchaseFailurePolicy", AgentFailurePolicy.FAIL_FAST.name());

        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        BusinessAgentRequest request = BusinessAgentRequest.builder()
                .runId(state.getRunId())
                .conversationId(state.getConversationId())
                .userId(state.getUserId())
                .distributerId(state.getDistributerId())
                .resolvedQueryContext(rq)
                .semanticResult(rq != null ? rq.getQuerySemanticParse() : null)
                .executionContext(state)
                .debugOptions(new LinkedHashMap<>())
                .build();

        List<AgentResultEnvelope> envelopes = new ArrayList<>();
        AgentTraceEnvelope trace = null;
        try {
            BusinessSubAgent agent = resolvePurchaseSubAgent(registry, request);
            if (agent == null) {
                return purchaseFallback(dbg, envelopes, dispatchPlan, startedAt, rq, "purchase_family_agent_unavailable", state);
            }

            AgentResultEnvelope env = agent.execute(request);
            envelopes.add(env);

            dbg.put("purchaseSelectedAgents", List.of(agent.agentName()));
            dbg.put("purchaseAgentResults", summarizeEnvelopes(envelopes));
            dbg.put("purchaseAgentResultStatus", env.getStatus() != null ? env.getStatus().name() : null);
            dbg.put("purchaseDegraded", env.isDegraded());
            putSupplierAnalysisHarnessContract(dbg, env);

            if (!masterPurchasePathAllowsLegacySkip(env)) {
                dbg.put("purchaseMasterAgentUsed", false);
                dbg.put("purchaseFallbackUsed", true);
                dbg.put("purchaseFallbackReason", "agent_status_" + env.getStatus()
                        + "_or_tool_success_" + env.getPurchaseOverviewToolSuccess());
                dbg.put("legacyPurchaseSkipped", true);
                dbg.put("purchaseToolExecutedByMasterPath", true);
                dbg.put("masterPurchaseToolResultKey", AiBusinessToolIds.PURCHASE_OVERVIEW);
                dbg.put("masterPurchaseToolResultSuccess", env.getPurchaseOverviewToolSuccess());
                trace = buildTrace(state, rq, dispatchPlan, envelopes, startedAt, Instant.now(),
                        semanticSummary(rq), resolvedSummary(rq));
                return MasterBusinessAgentResult.builder()
                        .trace(trace)
                        .agentResults(envelopes)
                        .masterAgentEnabled(true)
                        .masterAgentUsed(false)
                        .fallbackUsed(true)
                        .fallbackReason("agent_status_" + env.getStatus()
                                + "_or_tool_success_" + env.getPurchaseOverviewToolSuccess())
                        .debug(dbg)
                        .revenueToolExecutedByMasterPath(false)
                        .purchaseToolExecutedByMasterPath(true)
                        .build();
            }

            dbg.put("purchaseMasterAgentUsed", true);
            dbg.put("purchaseFallbackUsed", false);
            dbg.put("purchaseFallbackReason", null);
            dbg.put("legacyPurchaseSkipped", true);
            dbg.put("purchaseToolExecutedByMasterPath", true);
            dbg.put("masterPurchaseToolResultKey", AiBusinessToolIds.PURCHASE_OVERVIEW);
            dbg.put("masterPurchaseToolResultSuccess", env.getPurchaseOverviewToolSuccess());
            Instant finishedAt = Instant.now();
            trace = buildTrace(state, rq, dispatchPlan, envelopes, startedAt, finishedAt,
                    semanticSummary(rq), resolvedSummary(rq));
            return MasterBusinessAgentResult.builder()
                    .trace(trace)
                    .agentResults(envelopes)
                    .masterAgentEnabled(true)
                    .masterAgentUsed(true)
                    .fallbackUsed(false)
                    .fallbackReason(null)
                    .debug(dbg)
                    .revenueToolExecutedByMasterPath(false)
                    .purchaseToolExecutedByMasterPath(true)
                    .build();
        } catch (Exception ex) {
            log.warn("[MasterBusinessAgent] purchase overview orchestration failed runId={}", state.getRunId(), ex);
            dbg.put("purchaseMasterAgentUsed", false);
            dbg.put("purchaseFallbackUsed", true);
            dbg.put("purchaseFallbackReason", "master_exception:" + ex.getClass().getSimpleName());
            dbg.put("legacyPurchaseSkipped", true);
            dbg.put("purchaseToolExecutedByMasterPath", true);
            dbg.put("masterPurchaseToolResultKey", AiBusinessToolIds.PURCHASE_OVERVIEW);
            dbg.put("masterPurchaseToolResultSuccess", null);
            putSupplierAnalysisHarnessContract(dbg, null);
            dbg.put("purchaseSelectedAgents", List.of(BusinessAgentNames.PURCHASE_OVERVIEW));
            dbg.put("purchaseAgentResults", summarizeEnvelopes(envelopes));
            trace = buildTrace(state, rq, dispatchPlan, envelopes, startedAt, Instant.now(),
                    semanticSummary(rq), resolvedSummary(rq));
            return MasterBusinessAgentResult.builder()
                    .trace(trace)
                    .agentResults(envelopes)
                    .masterAgentEnabled(true)
                    .masterAgentUsed(false)
                    .fallbackUsed(true)
                    .fallbackReason("master_exception:" + ex.getClass().getSimpleName())
                    .debug(dbg)
                    .revenueToolExecutedByMasterPath(false)
                    .purchaseToolExecutedByMasterPath(true)
                    .build();
        }
    }

    private static MasterBusinessAgentResult purchaseFallback(LinkedHashMap<String, Object> dbg,
            List<AgentResultEnvelope> envelopes,
            BusinessAgentDispatchPlan dispatchPlan,
            Instant startedAt,
            AiResolvedQueryContext rq,
            String reason,
            AiRunState state) {
        dbg.put("purchaseMasterAgentUsed", false);
        dbg.put("purchaseFallbackUsed", true);
        dbg.put("purchaseFallbackReason", reason);
        putSupplierAnalysisHarnessContract(dbg, null);
        dbg.put("purchaseSelectedAgents", List.of(BusinessAgentNames.PURCHASE_OVERVIEW));
        dbg.put("purchaseAgentResults", summarizeEnvelopes(envelopes));
        dbg.put("purchaseAgentResultStatus", null);
        dbg.put("purchaseDegraded", false);
        dbg.put("legacyPurchaseSkipped", true);
        dbg.put("purchaseToolExecutedByMasterPath", true);
        dbg.put("masterPurchaseToolResultKey", AiBusinessToolIds.PURCHASE_OVERVIEW);
        dbg.put("masterPurchaseToolResultSuccess", null);
        dbg.put("purchaseDispatchPlan", summarizeDispatchPlan(dispatchPlan));
        Instant finishedAt = Instant.now();
        AgentTraceEnvelope trace = buildTrace(state, rq, dispatchPlan, envelopes, startedAt, finishedAt,
                semanticSummary(rq), resolvedSummary(rq));
        return MasterBusinessAgentResult.builder()
                .trace(trace)
                .agentResults(envelopes)
                .masterAgentEnabled(true)
                .masterAgentUsed(false)
                .fallbackUsed(true)
                .fallbackReason(reason)
                .debug(dbg)
                .revenueToolExecutedByMasterPath(false)
                .purchaseToolExecutedByMasterPath(true)
                .stockReduceToolExecutedByMasterPath(false)
                .build();
    }

    /**
     * 出库/核销专线：仅 STOCK_REDUCE_QUERY + stock_reduce_query_path + 计划仅 {@link com.nongxinle.ai.tool.business.AiBusinessToolIds#STOCK_REDUCE_QUERY}。
     */
    public MasterBusinessAgentResult tryOrchestrateStockReduceQuery(AiRunState state) {
        LinkedHashMap<String, Object> dbg = new LinkedHashMap<>();
        if (!eligibleForMasterStockReduceQuery(state)) {
            dbg.put("stockReduceMasterAgentEnabled", false);
            dbg.put("stockReduceMasterAgentUsed", false);
            dbg.put("stockReduceSelectedAgents", List.of());
            dbg.put("stockReduceDispatchPlan", null);
            dbg.put("stockReduceAgentResults", List.of());
            dbg.put("stockReduceAgentResultStatus", null);
            dbg.put("stockReduceDegraded", false);
            dbg.put("stockReduceFailurePolicy", null);
            dbg.put("stockReduceFallbackUsed", false);
            dbg.put("stockReduceFallbackReason", "not_eligible_for_master_stock_reduce_gate");
            dbg.put("legacyStockReduceSkipped", false);
            dbg.put("masterStockReduceToolResultKey", null);
            dbg.put("masterStockReduceToolResultSuccess", null);
            dbg.put("stockReduceToolExecutedByMasterPath", false);
            return MasterBusinessAgentResult.builder()
                    .masterAgentEnabled(false)
                    .masterAgentUsed(false)
                    .fallbackUsed(false)
                    .fallbackReason("not_eligible_for_master_stock_reduce_gate")
                    .agentResults(List.of())
                    .debug(dbg)
                    .revenueToolExecutedByMasterPath(false)
                    .purchaseToolExecutedByMasterPath(false)
                    .stockReduceToolExecutedByMasterPath(false)
                    .build();
        }

        AiResolvedQueryContext rqEarly = state.getResolvedQueryContext();
        StockReduceToolRequestContext stockSnap =
                toolExecutionRequestResolver.buildStockReduceRequestContext(state, rqEarly);
        dbg.put("stockReduceRequestResolutionDebug", new LinkedHashMap<>(stockSnap.getResolutionDebug()));

        dbg.put("stockReduceMasterAgentEnabled", true);
        Instant startedAt = Instant.now();
        BusinessAgentDispatchPlan dispatchPlan = buildStockReduceDispatchPlan();
        dbg.put("stockReduceDispatchPlan", summarizeDispatchPlan(dispatchPlan));
        dbg.put("stockReduceFailurePolicy", AgentFailurePolicy.FAIL_FAST.name());

        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        BusinessAgentRequest request = BusinessAgentRequest.builder()
                .runId(state.getRunId())
                .conversationId(state.getConversationId())
                .userId(state.getUserId())
                .distributerId(state.getDistributerId())
                .resolvedQueryContext(rq)
                .semanticResult(rq != null ? rq.getQuerySemanticParse() : null)
                .executionContext(state)
                .debugOptions(new LinkedHashMap<>())
                .build();

        List<AgentResultEnvelope> envelopes = new ArrayList<>();
        AgentTraceEnvelope trace = null;
        try {
            Optional<BusinessSubAgent> agentOpt = registry.getAgent(BusinessAgentNames.STOCK_REDUCE_QUERY);
            if (agentOpt.isEmpty()) {
                return stockReduceFallback(dbg, envelopes, dispatchPlan, startedAt, rq,
                        "stock_reduce_agent_not_registered", state);
            }
            BusinessSubAgent agent = agentOpt.get();
            if (!agent.supports(request)) {
                return stockReduceFallback(dbg, envelopes, dispatchPlan, startedAt, rq,
                        "stock_reduce_agent_supports_false", state);
            }

            AgentResultEnvelope env = agent.execute(request);
            envelopes.add(env);

            dbg.put("stockReduceSelectedAgents", List.of(BusinessAgentNames.STOCK_REDUCE_QUERY));
            dbg.put("stockReduceAgentResults", summarizeEnvelopes(envelopes));
            dbg.put("stockReduceAgentResultStatus", env.getStatus() != null ? env.getStatus().name() : null);
            dbg.put("stockReduceDegraded", env.isDegraded());

            if (!masterStockReducePathAllowsLegacySkip(env)) {
                dbg.put("stockReduceMasterAgentUsed", false);
                dbg.put("stockReduceFallbackUsed", true);
                dbg.put("stockReduceFallbackReason", "agent_status_" + env.getStatus()
                        + "_or_tool_success_" + env.getStockReduceQueryToolSuccess());
                dbg.put("legacyStockReduceSkipped", true);
                dbg.put("stockReduceToolExecutedByMasterPath", true);
                dbg.put("masterStockReduceToolResultKey", AiBusinessToolIds.STOCK_REDUCE_QUERY);
                dbg.put("masterStockReduceToolResultSuccess", env.getStockReduceQueryToolSuccess());
                trace = buildTrace(state, rq, dispatchPlan, envelopes, startedAt, Instant.now(),
                        semanticSummary(rq), resolvedSummary(rq));
                return MasterBusinessAgentResult.builder()
                        .trace(trace)
                        .agentResults(envelopes)
                        .masterAgentEnabled(true)
                        .masterAgentUsed(false)
                        .fallbackUsed(true)
                        .fallbackReason("agent_status_" + env.getStatus()
                                + "_or_tool_success_" + env.getStockReduceQueryToolSuccess())
                        .debug(dbg)
                        .revenueToolExecutedByMasterPath(false)
                        .purchaseToolExecutedByMasterPath(false)
                        .stockReduceToolExecutedByMasterPath(true)
                        .build();
            }

            dbg.put("stockReduceMasterAgentUsed", true);
            dbg.put("stockReduceFallbackUsed", false);
            dbg.put("stockReduceFallbackReason", null);
            dbg.put("legacyStockReduceSkipped", true);
            dbg.put("stockReduceToolExecutedByMasterPath", true);
            dbg.put("masterStockReduceToolResultKey", AiBusinessToolIds.STOCK_REDUCE_QUERY);
            dbg.put("masterStockReduceToolResultSuccess", env.getStockReduceQueryToolSuccess());
            Instant finishedAt = Instant.now();
            trace = buildTrace(state, rq, dispatchPlan, envelopes, startedAt, finishedAt,
                    semanticSummary(rq), resolvedSummary(rq));
            return MasterBusinessAgentResult.builder()
                    .trace(trace)
                    .agentResults(envelopes)
                    .masterAgentEnabled(true)
                    .masterAgentUsed(true)
                    .fallbackUsed(false)
                    .fallbackReason(null)
                    .debug(dbg)
                    .revenueToolExecutedByMasterPath(false)
                    .purchaseToolExecutedByMasterPath(false)
                    .stockReduceToolExecutedByMasterPath(true)
                    .build();
        } catch (Exception ex) {
            log.warn("[MasterBusinessAgent] stock reduce orchestration failed runId={}", state.getRunId(), ex);
            dbg.put("stockReduceMasterAgentUsed", false);
            dbg.put("stockReduceFallbackUsed", true);
            dbg.put("stockReduceFallbackReason", "master_exception:" + ex.getClass().getSimpleName());
            dbg.put("legacyStockReduceSkipped", true);
            dbg.put("stockReduceToolExecutedByMasterPath", true);
            dbg.put("masterStockReduceToolResultKey", AiBusinessToolIds.STOCK_REDUCE_QUERY);
            dbg.put("masterStockReduceToolResultSuccess", null);
            dbg.put("stockReduceSelectedAgents", List.of(BusinessAgentNames.STOCK_REDUCE_QUERY));
            dbg.put("stockReduceAgentResults", summarizeEnvelopes(envelopes));
            trace = buildTrace(state, rq, dispatchPlan, envelopes, startedAt, Instant.now(),
                    semanticSummary(rq), resolvedSummary(rq));
            return MasterBusinessAgentResult.builder()
                    .trace(trace)
                    .agentResults(envelopes)
                    .masterAgentEnabled(true)
                    .masterAgentUsed(false)
                    .fallbackUsed(true)
                    .fallbackReason("master_exception:" + ex.getClass().getSimpleName())
                    .debug(dbg)
                    .revenueToolExecutedByMasterPath(false)
                    .purchaseToolExecutedByMasterPath(false)
                    .stockReduceToolExecutedByMasterPath(true)
                    .build();
        }
    }

    /**
     * 菜品毛利专线：仅 DISH_PROFIT + dish_profit_path + 计划仅 {@link com.nongxinle.ai.tool.business.AiBusinessToolIds#DISH_PROFIT_ANALYSIS}；
     * 不包含 {@link AiRunState#isBusinessDiagnosisPath()}。
     */
    public MasterBusinessAgentResult tryOrchestrateDishProfitAnalysis(AiRunState state) {
        LinkedHashMap<String, Object> dbg = new LinkedHashMap<>();
        if (!eligibleForMasterDishProfitAnalysis(state)) {
            dbg.put("dishProfitMasterAgentEnabled", false);
            dbg.put("dishProfitMasterAgentUsed", false);
            dbg.put("dishProfitSelectedAgents", List.of());
            dbg.put("dishProfitDispatchPlan", null);
            dbg.put("dishProfitAgentResults", List.of());
            dbg.put("dishProfitAgentResultStatus", null);
            dbg.put("dishProfitFallbackUsed", false);
            dbg.put("dishProfitFallbackReason", "not_eligible_for_master_dish_profit_gate");
            dbg.put("legacyDishProfitSkipped", false);
            dbg.put("masterDishProfitToolResultKey", null);
            dbg.put("masterDishProfitToolResultSuccess", null);
            dbg.put("dishProfitToolExecutedByMasterPath", false);
            return MasterBusinessAgentResult.builder()
                    .masterAgentEnabled(false)
                    .masterAgentUsed(false)
                    .fallbackUsed(false)
                    .fallbackReason("not_eligible_for_master_dish_profit_gate")
                    .agentResults(List.of())
                    .debug(dbg)
                    .revenueToolExecutedByMasterPath(false)
                    .purchaseToolExecutedByMasterPath(false)
                    .stockReduceToolExecutedByMasterPath(false)
                    .dishProfitToolExecutedByMasterPath(false)
                    .build();
        }

        AiResolvedQueryContext rqEarly = state.getResolvedQueryContext();
        DishProfitToolRequestContext dishSnap =
                toolExecutionRequestResolver.buildDishProfitRequestContext(state, rqEarly);
        dbg.put("dishProfitRequestResolutionDebug", new LinkedHashMap<>(dishSnap.getResolutionDebug()));

        dbg.put("dishProfitMasterAgentEnabled", true);
        Instant startedAt = Instant.now();
        BusinessAgentDispatchPlan dispatchPlan = buildDishProfitDispatchPlan();
        dbg.put("dishProfitDispatchPlan", summarizeDispatchPlan(dispatchPlan));

        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        BusinessAgentRequest request = BusinessAgentRequest.builder()
                .runId(state.getRunId())
                .conversationId(state.getConversationId())
                .userId(state.getUserId())
                .distributerId(state.getDistributerId())
                .resolvedQueryContext(rq)
                .semanticResult(rq != null ? rq.getQuerySemanticParse() : null)
                .executionContext(state)
                .debugOptions(new LinkedHashMap<>())
                .build();

        List<AgentResultEnvelope> envelopes = new ArrayList<>();
        AgentTraceEnvelope trace = null;
        try {
            Optional<BusinessSubAgent> agentOpt = registry.getAgent(BusinessAgentNames.DISH_PROFIT_ANALYSIS);
            if (agentOpt.isEmpty()) {
                return dishProfitFallback(dbg, envelopes, dispatchPlan, startedAt, rq,
                        "dish_profit_agent_not_registered", state);
            }
            BusinessSubAgent agent = agentOpt.get();
            if (!agent.supports(request)) {
                return dishProfitFallback(dbg, envelopes, dispatchPlan, startedAt, rq,
                        "dish_profit_agent_supports_false", state);
            }

            AgentResultEnvelope env = agent.execute(request);
            envelopes.add(env);

            dbg.put("dishProfitSelectedAgents", List.of(BusinessAgentNames.DISH_PROFIT_ANALYSIS));
            dbg.put("dishProfitAgentResults", summarizeEnvelopes(envelopes));
            dbg.put("dishProfitAgentResultStatus", env.getStatus() != null ? env.getStatus().name() : null);

            if (!masterDishProfitPathAllowsLegacySkip(env)) {
                dbg.put("dishProfitMasterAgentUsed", false);
                dbg.put("dishProfitFallbackUsed", true);
                dbg.put("dishProfitFallbackReason", "agent_status_" + env.getStatus()
                        + "_or_tool_success_" + env.getDishProfitAnalysisToolSuccess());
                dbg.put("legacyDishProfitSkipped", true);
                dbg.put("dishProfitToolExecutedByMasterPath", true);
                dbg.put("masterDishProfitToolResultKey", AiBusinessToolIds.DISH_PROFIT_ANALYSIS);
                dbg.put("masterDishProfitToolResultSuccess", env.getDishProfitAnalysisToolSuccess());
                trace = buildTrace(state, rq, dispatchPlan, envelopes, startedAt, Instant.now(),
                        semanticSummary(rq), resolvedSummary(rq));
                return MasterBusinessAgentResult.builder()
                        .trace(trace)
                        .agentResults(envelopes)
                        .masterAgentEnabled(true)
                        .masterAgentUsed(false)
                        .fallbackUsed(true)
                        .fallbackReason("agent_status_" + env.getStatus()
                                + "_or_tool_success_" + env.getDishProfitAnalysisToolSuccess())
                        .debug(dbg)
                        .revenueToolExecutedByMasterPath(false)
                        .purchaseToolExecutedByMasterPath(false)
                        .stockReduceToolExecutedByMasterPath(false)
                        .dishProfitToolExecutedByMasterPath(true)
                        .build();
            }

            dbg.put("dishProfitMasterAgentUsed", true);
            dbg.put("dishProfitFallbackUsed", false);
            dbg.put("dishProfitFallbackReason", null);
            dbg.put("legacyDishProfitSkipped", true);
            dbg.put("dishProfitToolExecutedByMasterPath", true);
            dbg.put("masterDishProfitToolResultKey", AiBusinessToolIds.DISH_PROFIT_ANALYSIS);
            dbg.put("masterDishProfitToolResultSuccess", env.getDishProfitAnalysisToolSuccess());
            Instant finishedAt = Instant.now();
            trace = buildTrace(state, rq, dispatchPlan, envelopes, startedAt, finishedAt,
                    semanticSummary(rq), resolvedSummary(rq));
            return MasterBusinessAgentResult.builder()
                    .trace(trace)
                    .agentResults(envelopes)
                    .masterAgentEnabled(true)
                    .masterAgentUsed(true)
                    .fallbackUsed(false)
                    .fallbackReason(null)
                    .debug(dbg)
                    .revenueToolExecutedByMasterPath(false)
                    .purchaseToolExecutedByMasterPath(false)
                    .stockReduceToolExecutedByMasterPath(false)
                    .dishProfitToolExecutedByMasterPath(true)
                    .build();
        } catch (Exception ex) {
            log.warn("[MasterBusinessAgent] dish profit orchestration failed runId={}", state.getRunId(), ex);
            dbg.put("dishProfitMasterAgentUsed", false);
            dbg.put("dishProfitFallbackUsed", true);
            dbg.put("dishProfitFallbackReason", "master_exception:" + ex.getClass().getSimpleName());
            dbg.put("legacyDishProfitSkipped", true);
            dbg.put("dishProfitToolExecutedByMasterPath", true);
            dbg.put("masterDishProfitToolResultKey", AiBusinessToolIds.DISH_PROFIT_ANALYSIS);
            dbg.put("masterDishProfitToolResultSuccess", null);
            dbg.put("dishProfitSelectedAgents", List.of(BusinessAgentNames.DISH_PROFIT_ANALYSIS));
            dbg.put("dishProfitAgentResults", summarizeEnvelopes(envelopes));
            trace = buildTrace(state, rq, dispatchPlan, envelopes, startedAt, Instant.now(),
                    semanticSummary(rq), resolvedSummary(rq));
            return MasterBusinessAgentResult.builder()
                    .trace(trace)
                    .agentResults(envelopes)
                    .masterAgentEnabled(true)
                    .masterAgentUsed(false)
                    .fallbackUsed(true)
                    .fallbackReason("master_exception:" + ex.getClass().getSimpleName())
                    .debug(dbg)
                    .revenueToolExecutedByMasterPath(false)
                    .purchaseToolExecutedByMasterPath(false)
                    .stockReduceToolExecutedByMasterPath(false)
                    .dishProfitToolExecutedByMasterPath(true)
                    .build();
        }
    }

    private static void putSupplierAnalysisHarnessContract(LinkedHashMap<String, Object> dbg, AgentResultEnvelope env) {
        if (dbg == null) {
            return;
        }
        if (env != null && BusinessAgentNames.SUPPLIER_ANALYSIS.equals(env.getAgentName())) {
            dbg.put("supplierAnalysisAgentUsed", Boolean.TRUE);
            AgentResultStatus st = env.getStatus();
            dbg.put("supplierAnalysisAgentStatus", st != null ? st.name() : null);
            String pt = env.getResultType();
            if (pt == null || pt.isBlank()) {
                Object ap = env.getAnswerPlan();
                if (ap instanceof PurchaseAnswerPlan pap) {
                    pt = pap.getPlanType();
                }
            }
            if (pt == null || pt.isBlank()) {
                pt = PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_AMOUNT_RANKING;
            }
            dbg.put("supplierAnalysisPlanType", pt);
            return;
        }
        dbg.put("supplierAnalysisAgentUsed", Boolean.FALSE);
        dbg.put("supplierAnalysisAgentStatus", "SKIPPED");
        dbg.put("supplierAnalysisPlanType", null);
    }

    private static BusinessSubAgent resolvePurchaseSubAgent(BusinessAgentRegistry registry, BusinessAgentRequest request) {
        Optional<BusinessSubAgent> supplierOpt = registry.getAgent(BusinessAgentNames.SUPPLIER_ANALYSIS);
        if (supplierOpt.isPresent() && supplierOpt.get().supports(request)) {
            return supplierOpt.get();
        }
        Optional<BusinessSubAgent> purchaseOpt = registry.getAgent(BusinessAgentNames.PURCHASE_OVERVIEW);
        if (purchaseOpt.isEmpty()) {
            return null;
        }
        BusinessSubAgent p = purchaseOpt.get();
        if (!p.supports(request)) {
            return null;
        }
        return p;
    }

    /**
     * 库房库存概览：WAREHOUSE_STOCK_OVERVIEW + warehouse_stock_overview_path + 计划仅
     * {@link com.nongxinle.ai.tool.business.AiBusinessToolIds#WAREHOUSE_STOCK_OVERVIEW}。
     */
    public MasterBusinessAgentResult tryOrchestrateWarehouseStockOverview(AiRunState state) {
        LinkedHashMap<String, Object> dbg = new LinkedHashMap<>();
        if (!eligibleForMasterWarehouseStockOverview(state)) {
            dbg.put("warehouseMasterAgentEnabled", false);
            dbg.put("warehouseToolExecutedByMasterPath", false);
            putWarehouseStockHarnessSkipped(dbg);
            return MasterBusinessAgentResult.builder()
                    .masterAgentEnabled(false)
                    .masterAgentUsed(false)
                    .fallbackUsed(false)
                    .fallbackReason("not_eligible_for_master_warehouse_gate")
                    .agentResults(List.of())
                    .debug(dbg)
                    .warehouseStockToolExecutedByMasterPath(false)
                    .build();
        }

        dbg.put("warehouseMasterAgentEnabled", true);
        Instant startedAt = Instant.now();
        BusinessAgentDispatchPlan dispatchPlan = buildWarehouseDispatchPlan();
        dbg.put("warehouseDispatchPlan", summarizeDispatchPlan(dispatchPlan));

        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        BusinessAgentRequest request = BusinessAgentRequest.builder()
                .runId(state.getRunId())
                .conversationId(state.getConversationId())
                .userId(state.getUserId())
                .distributerId(state.getDistributerId())
                .resolvedQueryContext(rq)
                .semanticResult(rq != null ? rq.getQuerySemanticParse() : null)
                .executionContext(state)
                .debugOptions(new LinkedHashMap<>())
                .build();

        List<AgentResultEnvelope> envelopes = new ArrayList<>();
        AgentTraceEnvelope trace = null;
        try {
            Optional<BusinessSubAgent> agentOpt = registry.getAgent(BusinessAgentNames.WAREHOUSE_STOCK);
            if (agentOpt.isEmpty()) {
                return warehouseFallback(dbg, envelopes, dispatchPlan, startedAt, rq, "warehouse_agent_not_registered",
                        state);
            }
            BusinessSubAgent agent = agentOpt.get();
            if (!agent.supports(request)) {
                return warehouseFallback(dbg, envelopes, dispatchPlan, startedAt, rq, "warehouse_agent_supports_false",
                        state);
            }

            AgentResultEnvelope env = agent.execute(request);
            envelopes.add(env);

            dbg.put("warehouseSelectedAgents", List.of(BusinessAgentNames.WAREHOUSE_STOCK));
            dbg.put("warehouseAgentResults", summarizeEnvelopes(envelopes));
            dbg.put("warehouseAgentResultStatus", env.getStatus() != null ? env.getStatus().name() : null);

            boolean toolOk = Boolean.TRUE.equals(env.getWarehouseStockOverviewToolSuccess());
            dbg.put("warehouseMasterAgentUsed", toolOk);
            dbg.put("warehouseToolExecutedByMasterPath", true);
            dbg.put("masterWarehouseToolResultSuccess", toolOk);
            putWarehouseStockHarnessFromEnvelope(dbg, env, state);
            trace = buildTrace(state, rq, dispatchPlan, envelopes, startedAt, Instant.now(),
                    semanticSummary(rq), resolvedSummary(rq));
            return MasterBusinessAgentResult.builder()
                    .trace(trace)
                    .agentResults(envelopes)
                    .masterAgentEnabled(true)
                    .masterAgentUsed(toolOk)
                    .fallbackUsed(!toolOk)
                    .fallbackReason(toolOk ? null : "warehouse_tool_failed")
                    .debug(dbg)
                    .warehouseStockToolExecutedByMasterPath(true)
                    .build();
        } catch (Exception ex) {
            log.warn("[MasterBusinessAgent] warehouse stock overview orchestration failed runId={}",
                    state.getRunId(), ex);
            dbg.put("warehouseToolExecutedByMasterPath", true);
            putWarehouseStockHarnessException(dbg, state, envelopes);
            trace = buildTrace(state, rq, dispatchPlan, envelopes, startedAt, Instant.now(),
                    semanticSummary(rq), resolvedSummary(rq));
            return MasterBusinessAgentResult.builder()
                    .trace(trace)
                    .agentResults(envelopes)
                    .masterAgentEnabled(true)
                    .masterAgentUsed(false)
                    .fallbackUsed(true)
                    .fallbackReason("master_exception:" + ex.getClass().getSimpleName())
                    .debug(dbg)
                    .warehouseStockToolExecutedByMasterPath(true)
                    .build();
        }
    }

    private static MasterBusinessAgentResult warehouseFallback(LinkedHashMap<String, Object> dbg,
            List<AgentResultEnvelope> envelopes,
            BusinessAgentDispatchPlan dispatchPlan,
            Instant startedAt,
            AiResolvedQueryContext rq,
            String reason,
            AiRunState state) {
        dbg.put("warehouseToolExecutedByMasterPath", true);
        dbg.put("warehouseFallbackReason", reason);
        putWarehouseStockHarnessFailure(dbg, state, null);
        AgentTraceEnvelope trace = buildTrace(state, rq, dispatchPlan, envelopes, startedAt, Instant.now(),
                semanticSummary(rq), resolvedSummary(rq));
        return MasterBusinessAgentResult.builder()
                .trace(trace)
                .agentResults(envelopes)
                .masterAgentEnabled(true)
                .masterAgentUsed(false)
                .fallbackUsed(true)
                .fallbackReason(reason)
                .debug(dbg)
                .warehouseStockToolExecutedByMasterPath(true)
                .build();
    }

    private static void putWarehouseStockHarnessSkipped(LinkedHashMap<String, Object> dbg) {
        if (dbg == null) {
            return;
        }
        dbg.put("warehouseStockAgentUsed", Boolean.FALSE);
        dbg.put("warehouseStockAgentStatus", "SKIPPED");
        dbg.put("warehouseStockOverviewToolSuccess", null);
        dbg.put("warehouseStockPlanType", null);
        dbg.put("warehouseStockResultCount", null);
    }

    private static void putWarehouseStockHarnessFromEnvelope(
            LinkedHashMap<String, Object> dbg, AgentResultEnvelope env, AiRunState state) {
        if (dbg == null) {
            return;
        }
        if (env == null) {
            putWarehouseStockHarnessFailure(dbg, state, null);
            return;
        }
        dbg.put("warehouseStockAgentUsed", Boolean.TRUE);
        dbg.put("warehouseStockAgentStatus", env.getStatus() != null ? env.getStatus().name() : null);
        dbg.put("warehouseStockOverviewToolSuccess", env.getWarehouseStockOverviewToolSuccess());
        dbg.put("warehouseStockPlanType", AiResolvedQueryIntent.WAREHOUSE_STOCK_OVERVIEW);
        dbg.put("warehouseStockResultCount", resolveWarehouseStockResultCount(state));
    }

    private static void putWarehouseStockHarnessFailure(
            LinkedHashMap<String, Object> dbg, AiRunState state, AgentResultEnvelope envOrNull) {
        if (dbg == null) {
            return;
        }
        dbg.put("warehouseStockAgentUsed", Boolean.TRUE);
        dbg.put("warehouseStockAgentStatus", "FAILED");
        Boolean t = envOrNull != null ? envOrNull.getWarehouseStockOverviewToolSuccess() : Boolean.FALSE;
        dbg.put("warehouseStockOverviewToolSuccess", Boolean.TRUE.equals(t));
        dbg.put("warehouseStockPlanType", AiResolvedQueryIntent.WAREHOUSE_STOCK_OVERVIEW);
        dbg.put("warehouseStockResultCount", resolveWarehouseStockResultCount(state));
    }

    private static void putWarehouseStockHarnessException(
            LinkedHashMap<String, Object> dbg, AiRunState state, List<AgentResultEnvelope> envelopes) {
        if (dbg == null) {
            return;
        }
        AgentResultEnvelope last =
                envelopes == null || envelopes.isEmpty() ? null : envelopes.get(envelopes.size() - 1);
        putWarehouseStockHarnessFailure(dbg, state, last);
        dbg.put("warehouseStockAgentStatus", "FAILED");
    }

    private static Integer resolveWarehouseStockResultCount(AiRunState state) {
        if (state == null || state.getToolResults() == null) {
            return null;
        }
        Object raw = state.getToolResults().get(AiBusinessToolIds.WAREHOUSE_STOCK_OVERVIEW);
        if (!(raw instanceof Map<?, ?> envelope)) {
            return null;
        }
        Object data = envelope.get("data");
        if (!(data instanceof Map<?, ?> dm)) {
            return null;
        }
        Object wo = dm.get("warehouseOverview");
        if (!(wo instanceof Map<?, ?> wom)) {
            return null;
        }
        Object sic = wom.get("stockItemCount");
        if (sic instanceof Number n) {
            return n.intValue();
        }
        int sum = 0;
        String[] listKeys = {
            "lowStockItems",
            "overStockItems",
            "inactiveStockItems",
            "priorityStocktakeItems",
            "storeStockAmountRanking",
            "warehouseStockAmountRanking",
            "storeInventoryAmountRanking"
        };
        for (String k : listKeys) {
            Object v = wom.get(k);
            if (v instanceof List<?> list) {
                sum += list.size();
            }
        }
        return sum;
    }

    private static boolean eligibleForMasterWarehouseStockOverview(AiRunState state) {
        if (state == null || !state.isWarehouseStockOverviewPath()) {
            return false;
        }
        List<String> plan = state.getDataPlanTools();
        if (plan == null || plan.size() != 1 || !AiBusinessToolIds.WAREHOUSE_STOCK_OVERVIEW.equals(plan.get(0))) {
            return false;
        }
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        if (rq == null) {
            return false;
        }
        String ei = rq.getEffectiveIntentCode();
        String ep = rq.getEffectivePathCode();
        return AiResolvedQueryIntent.WAREHOUSE_STOCK_OVERVIEW.equals(ei)
                && AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK.equals(ep);
    }

    private static BusinessAgentDispatchPlan buildWarehouseDispatchPlan() {
        BusinessAgentDispatchStep step = BusinessAgentDispatchStep.builder()
                .agentName(BusinessAgentNames.WAREHOUSE_STOCK)
                .required(true)
                .timeoutMs(120_000L)
                .failurePolicy(AgentFailurePolicy.FAIL_FAST)
                .order(0)
                .build();
        return BusinessAgentDispatchPlan.builder()
                .dispatchId(UUID.randomUUID().toString())
                .intentCode(AiResolvedQueryIntent.WAREHOUSE_STOCK_OVERVIEW)
                .pathCode(AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK)
                .steps(List.of(step))
                .parallelAllowed(false)
                .debug(new LinkedHashMap<>())
                .build();
    }

    private static MasterBusinessAgentResult dishProfitFallback(LinkedHashMap<String, Object> dbg,
            List<AgentResultEnvelope> envelopes,
            BusinessAgentDispatchPlan dispatchPlan,
            Instant startedAt,
            AiResolvedQueryContext rq,
            String reason,
            AiRunState state) {
        dbg.put("dishProfitMasterAgentUsed", false);
        dbg.put("dishProfitFallbackUsed", true);
        dbg.put("dishProfitFallbackReason", reason);
        dbg.put("dishProfitSelectedAgents", List.of(BusinessAgentNames.DISH_PROFIT_ANALYSIS));
        dbg.put("dishProfitAgentResults", summarizeEnvelopes(envelopes));
        dbg.put("dishProfitAgentResultStatus", null);
        dbg.put("legacyDishProfitSkipped", true);
        dbg.put("dishProfitToolExecutedByMasterPath", true);
        dbg.put("masterDishProfitToolResultKey", AiBusinessToolIds.DISH_PROFIT_ANALYSIS);
        dbg.put("masterDishProfitToolResultSuccess", null);
        dbg.put("dishProfitDispatchPlan", summarizeDispatchPlan(dispatchPlan));
        Instant finishedAt = Instant.now();
        AgentTraceEnvelope trace = buildTrace(state, rq, dispatchPlan, envelopes, startedAt, finishedAt,
                semanticSummary(rq), resolvedSummary(rq));
        return MasterBusinessAgentResult.builder()
                .trace(trace)
                .agentResults(envelopes)
                .masterAgentEnabled(true)
                .masterAgentUsed(false)
                .fallbackUsed(true)
                .fallbackReason(reason)
                .debug(dbg)
                .revenueToolExecutedByMasterPath(false)
                .purchaseToolExecutedByMasterPath(false)
                .stockReduceToolExecutedByMasterPath(false)
                .dishProfitToolExecutedByMasterPath(true)
                .build();
    }

    private static MasterBusinessAgentResult stockReduceFallback(LinkedHashMap<String, Object> dbg,
            List<AgentResultEnvelope> envelopes,
            BusinessAgentDispatchPlan dispatchPlan,
            Instant startedAt,
            AiResolvedQueryContext rq,
            String reason,
            AiRunState state) {
        dbg.put("stockReduceMasterAgentUsed", false);
        dbg.put("stockReduceFallbackUsed", true);
        dbg.put("stockReduceFallbackReason", reason);
        dbg.put("stockReduceSelectedAgents", List.of(BusinessAgentNames.STOCK_REDUCE_QUERY));
        dbg.put("stockReduceAgentResults", summarizeEnvelopes(envelopes));
        dbg.put("stockReduceAgentResultStatus", null);
        dbg.put("stockReduceDegraded", false);
        dbg.put("legacyStockReduceSkipped", true);
        dbg.put("stockReduceToolExecutedByMasterPath", true);
        dbg.put("masterStockReduceToolResultKey", AiBusinessToolIds.STOCK_REDUCE_QUERY);
        dbg.put("masterStockReduceToolResultSuccess", null);
        dbg.put("stockReduceDispatchPlan", summarizeDispatchPlan(dispatchPlan));
        Instant finishedAt = Instant.now();
        AgentTraceEnvelope trace = buildTrace(state, rq, dispatchPlan, envelopes, startedAt, finishedAt,
                semanticSummary(rq), resolvedSummary(rq));
        return MasterBusinessAgentResult.builder()
                .trace(trace)
                .agentResults(envelopes)
                .masterAgentEnabled(true)
                .masterAgentUsed(false)
                .fallbackUsed(true)
                .fallbackReason(reason)
                .debug(dbg)
                .revenueToolExecutedByMasterPath(false)
                .purchaseToolExecutedByMasterPath(false)
                .stockReduceToolExecutedByMasterPath(true)
                .build();
    }

    private static MasterBusinessAgentResult fallback(LinkedHashMap<String, Object> dbg,
            List<AgentResultEnvelope> envelopes,
            BusinessAgentDispatchPlan dispatchPlan,
            Instant startedAt,
            AiResolvedQueryContext rq,
            String reason,
            AiRunState state) {
        dbg.put("masterAgentUsed", false);
        dbg.put("fallbackUsed", true);
        dbg.put("fallbackReason", reason);
        dbg.put("selectedAgents", List.of(BusinessAgentNames.REVENUE_OVERVIEW));
        dbg.put("agentResults", summarizeEnvelopes(envelopes));
        dbg.put("agentResultStatus", null);
        dbg.put("degraded", false);
        dbg.put("legacyRevenueSkipped", true);
        dbg.put("revenueToolExecutedByMasterPath", true);
        dbg.put("masterRevenueToolResultKey", AiBusinessToolIds.REVENUE_QUERY);
        dbg.put("masterRevenueToolResultSuccess", null);
        dbg.put("dispatchPlan", summarizeDispatchPlan(dispatchPlan));
        Instant finishedAt = Instant.now();
        AgentTraceEnvelope trace = buildTrace(state, rq, dispatchPlan, envelopes, startedAt, finishedAt,
                semanticSummary(rq), resolvedSummary(rq));
        return MasterBusinessAgentResult.builder()
                .trace(trace)
                .agentResults(envelopes)
                .masterAgentEnabled(true)
                .masterAgentUsed(false)
                .fallbackUsed(true)
                .fallbackReason(reason)
                .debug(dbg)
                .revenueToolExecutedByMasterPath(true)
                .purchaseToolExecutedByMasterPath(false)
                .stockReduceToolExecutedByMasterPath(false)
                .dishProfitToolExecutedByMasterPath(false)
                .build();
    }

    private static boolean eligibleForMasterDishProfitAnalysis(AiRunState state) {
        if (state == null || !state.isDishProfitPath()) {
            return false;
        }
        if (state.isBusinessDiagnosisPath()) {
            return false;
        }
        List<String> plan = state.getDataPlanTools();
        if (plan == null || plan.size() != 1 || !AiBusinessToolIds.DISH_PROFIT_ANALYSIS.equals(plan.get(0))) {
            return false;
        }
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        if (rq == null) {
            return false;
        }
        String ei = rq.getEffectiveIntentCode();
        String ep = rq.getEffectivePathCode();
        return AiResolvedQueryIntent.DISH_PROFIT.equals(ei)
                && AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(ep);
    }

    private static BusinessAgentDispatchPlan buildDishProfitDispatchPlan() {
        BusinessAgentDispatchStep step = BusinessAgentDispatchStep.builder()
                .agentName(BusinessAgentNames.DISH_PROFIT_ANALYSIS)
                .required(true)
                .timeoutMs(120_000L)
                .failurePolicy(AgentFailurePolicy.FAIL_FAST)
                .order(0)
                .build();
        return BusinessAgentDispatchPlan.builder()
                .dispatchId(UUID.randomUUID().toString())
                .intentCode(AiResolvedQueryIntent.DISH_PROFIT)
                .pathCode(AiResolvedQueryIntent.PATH_DISH_PROFIT)
                .steps(List.of(step))
                .parallelAllowed(false)
                .debug(new LinkedHashMap<>())
                .build();
    }

    private static boolean eligibleForMasterStockReduceQuery(AiRunState state) {
        if (state == null || !state.isStockReduceQueryPath()) {
            return false;
        }
        List<String> plan = state.getDataPlanTools();
        if (plan == null || plan.size() != 1 || !AiBusinessToolIds.STOCK_REDUCE_QUERY.equals(plan.get(0))) {
            return false;
        }
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        if (rq == null) {
            return false;
        }
        String ei = rq.getEffectiveIntentCode();
        String ep = rq.getEffectivePathCode();
        return AiResolvedQueryIntent.STOCK_REDUCE_QUERY.equals(ei)
                && AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY.equals(ep);
    }

    private static BusinessAgentDispatchPlan buildStockReduceDispatchPlan() {
        BusinessAgentDispatchStep step = BusinessAgentDispatchStep.builder()
                .agentName(BusinessAgentNames.STOCK_REDUCE_QUERY)
                .required(true)
                .timeoutMs(120_000L)
                .failurePolicy(AgentFailurePolicy.FAIL_FAST)
                .order(0)
                .build();
        return BusinessAgentDispatchPlan.builder()
                .dispatchId(UUID.randomUUID().toString())
                .intentCode(AiResolvedQueryIntent.STOCK_REDUCE_QUERY)
                .pathCode(AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY)
                .steps(List.of(step))
                .parallelAllowed(false)
                .debug(new LinkedHashMap<>())
                .build();
    }

    private static boolean eligibleForMasterRevenueOverview(AiRunState state) {
        if (state == null || !state.isRevenueOverviewPath()) {
            return false;
        }
        List<String> plan = state.getDataPlanTools();
        if (plan == null || plan.size() != 1 || !AiBusinessToolIds.REVENUE_QUERY.equals(plan.get(0))) {
            return false;
        }
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        if (rq == null) {
            return false;
        }
        String ei = rq.getEffectiveIntentCode();
        String ep = rq.getEffectivePathCode();
        return AiResolvedQueryIntent.REVENUE_OVERVIEW.equals(ei)
                && AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW.equals(ep);
    }

    private static BusinessAgentDispatchPlan buildRevenueDispatchPlan() {
        BusinessAgentDispatchStep step = BusinessAgentDispatchStep.builder()
                .agentName(BusinessAgentNames.REVENUE_OVERVIEW)
                .required(true)
                .timeoutMs(120_000L)
                .failurePolicy(AgentFailurePolicy.FAIL_FAST)
                .order(0)
                .build();
        return BusinessAgentDispatchPlan.builder()
                .dispatchId(UUID.randomUUID().toString())
                .intentCode(AiResolvedQueryIntent.REVENUE_OVERVIEW)
                .pathCode(AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW)
                .steps(List.of(step))
                .parallelAllowed(false)
                .debug(new LinkedHashMap<>())
                .build();
    }

    private static boolean eligibleForMasterPurchaseOverview(AiRunState state) {
        if (state == null || !state.isPurchaseOverviewPath()) {
            return false;
        }
        List<String> plan = state.getDataPlanTools();
        if (plan == null || plan.size() != 1 || !AiBusinessToolIds.PURCHASE_OVERVIEW.equals(plan.get(0))) {
            return false;
        }
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        if (rq == null) {
            return false;
        }
        String ei = rq.getEffectiveIntentCode();
        String ep = rq.getEffectivePathCode();
        return AiResolvedQueryIntent.PURCHASE_OVERVIEW.equals(ei)
                && AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW.equals(ep);
    }

    private static BusinessAgentDispatchPlan buildPurchaseDispatchPlan() {
        BusinessAgentDispatchStep step = BusinessAgentDispatchStep.builder()
                .agentName(BusinessAgentNames.PURCHASE_OVERVIEW)
                .required(true)
                .timeoutMs(120_000L)
                .failurePolicy(AgentFailurePolicy.FAIL_FAST)
                .order(0)
                .build();
        return BusinessAgentDispatchPlan.builder()
                .dispatchId(UUID.randomUUID().toString())
                .intentCode(AiResolvedQueryIntent.PURCHASE_OVERVIEW)
                .pathCode(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW)
                .steps(List.of(step))
                .parallelAllowed(false)
                .debug(new LinkedHashMap<>())
                .build();
    }

    private static boolean isAcceptableAgentStatus(AgentResultStatus status) {
        if (status == null) {
            return false;
        }
        return status == AgentResultStatus.SUCCESS
                || status == AgentResultStatus.NO_DATA
                || status == AgentResultStatus.PARTIAL_SUCCESS;
    }

    /** 仅当路由状态可接受且 REVENUE_QUERY 实际 success 时才跳过 legacy 循环。 */
    private static boolean masterRevenuePathAllowsLegacySkip(AgentResultEnvelope env) {
        if (env == null) {
            return false;
        }
        if (!isAcceptableAgentStatus(env.getStatus())) {
            return false;
        }
        return Boolean.TRUE.equals(env.getRevenueQueryToolSuccess());
    }

    /** 仅当路由状态可接受且 PURCHASE_OVERVIEW 实际 success 时才跳过 legacy 循环。 */
    private static boolean masterPurchasePathAllowsLegacySkip(AgentResultEnvelope env) {
        if (env == null) {
            return false;
        }
        if (!isAcceptableAgentStatus(env.getStatus())) {
            return false;
        }
        return Boolean.TRUE.equals(env.getPurchaseOverviewToolSuccess());
    }

    /** 仅当路由状态可接受且 STOCK_REDUCE_QUERY 实际 success 时才跳过 legacy 循环。 */
    private static boolean masterStockReducePathAllowsLegacySkip(AgentResultEnvelope env) {
        if (env == null) {
            return false;
        }
        if (!isAcceptableAgentStatus(env.getStatus())) {
            return false;
        }
        return Boolean.TRUE.equals(env.getStockReduceQueryToolSuccess());
    }

    /** 仅当路由状态可接受且 DISH_PROFIT_ANALYSIS 实际 success 时才跳过 legacy 循环。 */
    private static boolean masterDishProfitPathAllowsLegacySkip(AgentResultEnvelope env) {
        if (env == null) {
            return false;
        }
        if (!isAcceptableAgentStatus(env.getStatus())) {
            return false;
        }
        return Boolean.TRUE.equals(env.getDishProfitAnalysisToolSuccess());
    }

    private static String semanticSummary(AiResolvedQueryContext rq) {
        if (rq == null) {
            return "";
        }
        return "effectiveIntent=" + blank(rq.getEffectiveIntentCode())
                + ";effectivePath=" + blank(rq.getEffectivePathCode());
    }

    private static String resolvedSummary(AiResolvedQueryContext rq) {
        if (rq == null) {
            return "";
        }
        return "scopeSource=" + blank(rq.getEffectiveScopeSource())
                + ";timeSource=" + blank(rq.getEffectiveTimeWindowSource());
    }

    private static String blank(String s) {
        return s == null ? "" : s.trim();
    }

    private static AgentTraceEnvelope buildTrace(AiRunState state,
            AiResolvedQueryContext rq,
            BusinessAgentDispatchPlan plan,
            List<AgentResultEnvelope> results,
            Instant startedAt,
            Instant finishedAt,
            String semanticSummary,
            String resolvedSummary) {
        return AgentTraceEnvelope.builder()
                .runId(state != null ? state.getRunId() : null)
                .conversationId(state != null ? state.getConversationId() : null)
                .semanticSummary(semanticSummary)
                .resolvedContextSummary(resolvedSummary)
                .dispatchPlan(plan)
                .agentResults(results == null ? List.of() : new ArrayList<>(results))
                .startedAt(startedAt)
                .finishedAt(finishedAt)
                .build();
    }

    private static Map<String, Object> summarizeDispatchPlan(BusinessAgentDispatchPlan plan) {
        if (plan == null) {
            return null;
        }
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("dispatchId", plan.getDispatchId());
        m.put("intentCode", plan.getIntentCode());
        m.put("pathCode", plan.getPathCode());
        m.put("parallelAllowed", plan.isParallelAllowed());
        List<Map<String, Object>> steps = new ArrayList<>();
        if (plan.getSteps() != null) {
            for (BusinessAgentDispatchStep s : plan.getSteps()) {
                if (s == null) {
                    continue;
                }
                LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                row.put("agentName", s.getAgentName());
                row.put("required", s.isRequired());
                row.put("timeoutMs", s.getTimeoutMs());
                row.put("failurePolicy", s.getFailurePolicy() != null ? s.getFailurePolicy().name() : null);
                row.put("order", s.getOrder());
                steps.add(row);
            }
        }
        m.put("steps", steps);
        return m;
    }

    private static List<Map<String, Object>> summarizeEnvelopes(List<AgentResultEnvelope> envelopes) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (envelopes == null) {
            return out;
        }
        for (AgentResultEnvelope e : envelopes) {
            if (e == null) {
                continue;
            }
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("agentName", e.getAgentName());
            row.put("status", e.getStatus() != null ? e.getStatus().name() : null);
            row.put("resultType", e.getResultType());
            row.put("degraded", e.isDegraded());
            row.put("durationMs", e.getDurationMs());
            row.put("traceId", e.getTraceId());
            row.put("warnings", e.getWarnings());
            row.put("errors", e.getErrors());
            row.put("revenueQueryToolSuccess", e.getRevenueQueryToolSuccess());
            row.put("purchaseOverviewToolSuccess", e.getPurchaseOverviewToolSuccess());
            row.put("stockReduceQueryToolSuccess", e.getStockReduceQueryToolSuccess());
            row.put("dishProfitAnalysisToolSuccess", e.getDishProfitAnalysisToolSuccess());
            row.put("warehouseStockOverviewToolSuccess", e.getWarehouseStockOverviewToolSuccess());
            out.add(row);
        }
        return out;
    }

    public static boolean eligibleForBusinessOverviewMultiAgentOrchestration(AiRunState state) {
        if (state == null) {
            return false;
        }
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        if (rq == null) {
            return false;
        }

        boolean overviewSurface = state.isBusinessOverviewPath()
                && AiResolvedQueryIntent.BUSINESS_OVERVIEW.equals(rq.getEffectiveIntentCode())
                && AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW.equals(rq.getEffectivePathCode());

        boolean diagnosisSurface = state.isBusinessDiagnosisPath()
                && AiResolvedQueryIntent.BUSINESS_DIAGNOSIS.equals(rq.getEffectiveIntentCode())
                && AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS.equals(rq.getEffectivePathCode());

        if (!overviewSurface && !diagnosisSurface) {
            return false;
        }

        String rtm = rq.getOrchestrationTaskMode();
        if (rtm != null && "MULTI_AGENT".equalsIgnoreCase(rtm.trim())) {
            return true;
        }
        if (Boolean.TRUE.equals(rq.getOrchestrationMultiAgentRequired())) {
            return true;
        }

        AiQuerySemanticParseResult sem = rq.getQuerySemanticParse();
        if (sem == null || sem.isParseMissing()) {
            return false;
        }
        AiQuerySemanticParseResult.OrchestrationDecisionCandidatePart od = sem.getOrchestrationDecisionCandidate();
        if (od == null) {
            return false;
        }
        String tm = od.getTaskMode() == null ? "" : od.getTaskMode().trim();
        if ("MULTI_AGENT".equalsIgnoreCase(tm)) {
            return true;
        }
        return Boolean.TRUE.equals(od.getMultiAgentRequired());
    }

    /** 经营概览与经营诊断复用同一四域编排；intent/path 标注随 {@link AiRunState} 表面切换。 */
    private static BusinessAgentDispatchPlan buildFourDomainHarnessDispatchPlan(AiRunState state) {
        String intentCode = AiResolvedQueryIntent.BUSINESS_OVERVIEW;
        String pathCode = AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW;
        if (state != null && state.isBusinessDiagnosisPath()) {
            intentCode = AiResolvedQueryIntent.BUSINESS_DIAGNOSIS;
            pathCode = AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS;
        }
        List<BusinessAgentDispatchStep> steps = new ArrayList<>();
        steps.add(businessOverviewMultiDispatchStep(BusinessAgentNames.REVENUE_OVERVIEW, 0));
        steps.add(businessOverviewMultiDispatchStep(BusinessAgentNames.PURCHASE_OVERVIEW, 1));
        steps.add(businessOverviewMultiDispatchStep(BusinessAgentNames.STOCK_REDUCE_QUERY, 2));
        steps.add(businessOverviewMultiDispatchStep(BusinessAgentNames.DISH_PROFIT_ANALYSIS, 3));
        return BusinessAgentDispatchPlan.builder()
                .dispatchId(UUID.randomUUID().toString())
                .intentCode(intentCode)
                .pathCode(pathCode)
                .steps(steps)
                .parallelAllowed(false)
                .debug(new LinkedHashMap<>())
                .build();
    }

    private static BusinessAgentDispatchStep businessOverviewMultiDispatchStep(String agentName, int order) {
        return BusinessAgentDispatchStep.builder()
                .agentName(agentName)
                .required(false)
                .timeoutMs(120_000L)
                .failurePolicy(AgentFailurePolicy.DEGRADE)
                .order(order)
                .build();
    }

    private static boolean toolSuccessOkFor(String toolId, AgentResultEnvelope env) {
        if (env == null) {
            return false;
        }
        if (!isAcceptableAgentStatus(env.getStatus())) {
            return false;
        }
        if (AiBusinessToolIds.REVENUE_QUERY.equals(toolId)) {
            return Boolean.TRUE.equals(env.getRevenueQueryToolSuccess());
        }
        if (AiBusinessToolIds.PURCHASE_OVERVIEW.equals(toolId)) {
            return Boolean.TRUE.equals(env.getPurchaseOverviewToolSuccess());
        }
        if (AiBusinessToolIds.STOCK_REDUCE_QUERY.equals(toolId)) {
            return Boolean.TRUE.equals(env.getStockReduceQueryToolSuccess());
        }
        if (AiBusinessToolIds.DISH_PROFIT_ANALYSIS.equals(toolId)) {
            return Boolean.TRUE.equals(env.getDishProfitAnalysisToolSuccess());
        }
        return false;
    }

    private static void stripOverviewToolEnvelope(AiRunState state, String toolId, AgentResultEnvelope envIgnored) {
        if (state == null || toolId == null) {
            return;
        }
        state.getToolResults().remove(toolId);
        if (AiBusinessToolIds.REVENUE_QUERY.equals(toolId)) {
            state.setRevenueAnswerPlan(null);
        } else if (AiBusinessToolIds.PURCHASE_OVERVIEW.equals(toolId)) {
            state.setPurchaseAnswerPlan(null);
        } else if (AiBusinessToolIds.STOCK_REDUCE_QUERY.equals(toolId)) {
            state.setStockReduceAnswerPlan(null);
        } else if (AiBusinessToolIds.DISH_PROFIT_ANALYSIS.equals(toolId)) {
            state.setDishProfitAnswerPlan(null);
        }
    }

    private static String sectionWarn(String toolId, AgentResultEnvelope env) {
        String st = env != null && env.getStatus() != null ? env.getStatus().name() : "null";
        if (env != null && env.getErrors() != null && !env.getErrors().isEmpty()) {
            return "section_failed:" + toolId + ":" + st + ":" + env.getErrors().get(0);
        }
        return "section_failed:" + toolId + ":" + st;
    }

    private static LinkedHashMap<String, Object> copyStringKeyMap(Map<?, ?> raw) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        if (raw == null) {
            return out;
        }
        for (Map.Entry<?, ?> e : raw.entrySet()) {
            if (e.getKey() != null) {
                out.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        return out;
    }

    private static String businessOverviewDomainKey(String toolId) {
        if (AiBusinessToolIds.REVENUE_QUERY.equals(toolId)) {
            return "revenue";
        }
        if (AiBusinessToolIds.PURCHASE_OVERVIEW.equals(toolId)) {
            return "purchase";
        }
        if (AiBusinessToolIds.STOCK_REDUCE_QUERY.equals(toolId)) {
            return "stockReduce";
        }
        if (AiBusinessToolIds.DISH_PROFIT_ANALYSIS.equals(toolId)) {
            return "dishProfit";
        }
        return toolId == null ? "unknown" : toolId;
    }

    private static LinkedHashMap<String, Object> businessOverviewTraceRowSkipped(String beanName,
            String toolId,
            String domainKey,
            int order,
            String phase,
            String skipReason) {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("order", order);
        row.put("phase", phase);
        row.put("domainKey", domainKey);
        row.put("toolId", toolId);
        row.put("agentName", beanName);
        row.put("status", phase);
        row.put("toolSuccess", false);
        row.put("answerPlanPresent", false);
        row.put("warnings", List.of());
        row.put("errors", skipReason == null || skipReason.isBlank()
                ? List.of()
                : List.of(skipReason.trim()));
        row.put("durationMs", null);
        row.put("skipReason", skipReason);
        return row;
    }

    private static LinkedHashMap<String, Object> businessOverviewTraceRowFromEnvelope(String beanName,
            String toolId,
            String domainKey,
            int order,
            String phase,
            AgentResultEnvelope env) {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("order", order);
        row.put("phase", phase);
        row.put("domainKey", domainKey);
        row.put("toolId", toolId);
        row.put("agentName", beanName);
        row.put("skipReason", null);
        if (env != null) {
            row.put("status", env.getStatus() != null ? env.getStatus().name() : null);
            row.put("toolSuccess", toolSuccessOkFor(toolId, env));
            row.put("answerPlanPresent", env.getAnswerPlan() != null);
            row.put("warnings", env.getWarnings() != null ? new ArrayList<>(env.getWarnings()) : List.of());
            row.put("errors", env.getErrors() != null ? new ArrayList<>(env.getErrors()) : List.of());
            row.put("durationMs", env.getDurationMs());
            row.put("traceId", env.getTraceId());
        }
        return row;
    }

    private static void copyBusinessOverviewOrchestrationFieldsIntoPlanDebug(Map<String, Object> orch,
            LinkedHashMap<String, Object> dbgPlan) {
        if (orch == null || orch.isEmpty()) {
            return;
        }
        String[] keys = {
                "businessOverviewAgentResults",
                "businessOverviewSelectedAgents",
                "businessOverviewExpectedDomainOrder",
                "businessOverviewAgentResultStatus",
                "businessOverviewDomainsAttempted",
                "businessOverviewSuccessfulDomains",
                "businessOverviewFailedDomains",
                "businessOverviewAllExpectedDomainsAttempted",
                "businessOverviewEnvelopeSummary",
                "businessOverviewMultiAgentBatchAttempted",
                "businessOverviewMultiAgentBatchLoopFinished",
                "businessOverviewMultiAgentAllExpectedDomainsExecuted",
                "businessOverviewMultiAgentAllDomainsSkipped",
                "businessOverviewMultiAgentBatchUsableForDiagnosis",
                "businessOverviewMultiAgentBatchCompleted",
                "businessOverviewMultiAgentAnyDomainSuccess",
                "businessOverviewOrchestrationWarnings",
                "harnessOrchestratedSurfacePath",
                "harnessOrchestratedSurfaceIntent",
                "harnessOrchestratedPurposeIntent",
                "harnessOriginalEffectivePath",
                "harnessOriginalEffectiveIntent",
        };
        for (String k : keys) {
            if (orch.containsKey(k)) {
                dbgPlan.put(k, orch.get(k));
            }
        }
    }

    private static void assembleBusinessOverviewAnswerPlan(AiRunState state,
            List<String> warnings,
            Map<String, Object> orchestrationDiag) {
        if (state == null) {
            return;
        }
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        LinkedHashMap<String, Object> dbgPlan = new LinkedHashMap<>();
        boolean diagnosisHarness = state.isBusinessDiagnosisPath();
        String harnessPlanType = diagnosisHarness
                ? BusinessOverviewAnswerPlan.PLAN_TYPE_BUSINESS_DIAGNOSIS_MULTI_AGENT_V1
                : BusinessOverviewAnswerPlan.PLAN_TYPE_BUSINESS_OVERVIEW_MULTI_AGENT_V1;
        dbgPlan.put("source", harnessPlanType);
        if (orchestrationDiag != null && Boolean.TRUE.equals(orchestrationDiag.get("refreshedAfterDishNode"))) {
            dbgPlan.put("refreshedAfterDishNode", true);
        }
        copyBusinessOverviewOrchestrationFieldsIntoPlanDebug(orchestrationDiag, dbgPlan);

        List<String> missing = new ArrayList<>();
        List<String> missingPlansDetail = new ArrayList<>();
        List<String> mergeWarn = warnings != null ? new ArrayList<>(warnings) : new ArrayList<>();

        if (state.getRevenueAnswerPlan() == null) {
            missing.add("revenue");
            String rs = revenueMissingExplanation(state);
            missingPlansDetail.add("DailyRevenueAnswerPlan:" + rs);
            mergeWarn.removeIf(w -> w != null && w.startsWith("overview_multi_missing:revenue:"));
            mergeWarn.add("overview_multi_missing:revenue:" + rs);
        }
        if (state.getPurchaseAnswerPlan() == null) {
            missing.add("purchase");
            String rs = purchaseMissingExplanation(state);
            missingPlansDetail.add("PurchaseAnswerPlan:" + rs);
            mergeWarn.removeIf(w -> w != null && w.startsWith("overview_multi_missing:purchase:"));
            mergeWarn.add("overview_multi_missing:purchase:" + rs);
        }
        if (state.getStockReduceAnswerPlan() == null) {
            missing.add("stockReduce");
            String rs = stockReduceMissingExplanation(state);
            missingPlansDetail.add("StockReduceAnswerPlan:" + rs);
            mergeWarn.removeIf(w -> w != null && w.startsWith("overview_multi_missing:stockReduce:"));
            mergeWarn.add("overview_multi_missing:stockReduce:" + rs);
        }
        if (state.getDishProfitAnswerPlan() == null) {
            missing.add("dishProfit");
            String rs = dishProfitMissingExplanation(state);
            missingPlansDetail.add("DishProfitAnswerPlan:" + rs);
            mergeWarn.removeIf(w -> w != null && w.startsWith("overview_multi_missing:dishProfit:"));
            mergeWarn.add("overview_multi_missing:dishProfit:" + rs);
        }

        List<String> domainsWithPlans = new ArrayList<>();
        if (state.getRevenueAnswerPlan() != null) {
            domainsWithPlans.add("revenue");
        }
        if (state.getPurchaseAnswerPlan() != null) {
            domainsWithPlans.add("purchase");
        }
        if (state.getStockReduceAnswerPlan() != null) {
            domainsWithPlans.add("stockReduce");
        }
        if (state.getDishProfitAnswerPlan() != null) {
            domainsWithPlans.add("dishProfit");
        }
        dbgPlan.put("missingAnswerPlans", new ArrayList<>(missingPlansDetail));
        dbgPlan.put("domainsWithAnswerPlans", domainsWithPlans);

        BusinessOverviewAnswerPlan merged = BusinessOverviewAnswerPlan.builder()
                .planType(harnessPlanType)
                .timeLabel(rq != null ? rq.getTimeWindowLabel() : null)
                .scopeLabel(rq != null ? rq.getQueryScopeBanner() : null)
                .revenueSummary(state.getRevenueAnswerPlan())
                .purchaseSummary(state.getPurchaseAnswerPlan())
                .stockReduceSummary(state.getStockReduceAnswerPlan())
                .dishProfitSummary(state.getDishProfitAnswerPlan())
                .warnings(mergeWarn)
                .missingSections(missing)
                .debug(dbgPlan)
                .build();
        state.setBusinessOverviewAnswerPlan(merged);
    }

    private static String revenueMissingExplanation(AiRunState state) {
        List<String> plan = state != null ? state.getDataPlanTools() : null;
        if (plan == null || !plan.contains(AiBusinessToolIds.REVENUE_QUERY)) {
            return "dataPlanTools 未含 revenue_query（权限裁剪或非 Multi 编排）";
        }
        Object env = state.getToolResults() == null ? null
                : state.getToolResults().get(AiBusinessToolIds.REVENUE_QUERY);
        if (env == null) {
            return "工具未写入或已被失败剥离";
        }
        if (env instanceof Map<?, ?> m && Boolean.FALSE.equals(m.get("success"))) {
            return "revenue_query 执行失败或 success=false";
        }
        return "AnswerPlan 未挂载（attach 失败或数据为空）";
    }

    private static String purchaseMissingExplanation(AiRunState state) {
        List<String> plan = state != null ? state.getDataPlanTools() : null;
        if (plan == null || !plan.contains(AiBusinessToolIds.PURCHASE_OVERVIEW)) {
            return "dataPlanTools 未含 purchase_overview（权限裁剪或非 Multi 编排）";
        }
        Object env = state.getToolResults() == null ? null
                : state.getToolResults().get(AiBusinessToolIds.PURCHASE_OVERVIEW);
        if (env == null) {
            return "工具未写入或已被失败剥离";
        }
        if (env instanceof Map<?, ?> m && Boolean.FALSE.equals(m.get("success"))) {
            return "purchase_overview 执行失败或 success=false";
        }
        return "AnswerPlan 未挂载（attach 失败或 overview 数据为空）";
    }

    private static String stockReduceMissingExplanation(AiRunState state) {
        List<String> plan = state != null ? state.getDataPlanTools() : null;
        if (plan == null || !plan.contains(AiBusinessToolIds.STOCK_REDUCE_QUERY)) {
            return "dataPlanTools 未含 stock_reduce_query（权限裁剪）";
        }
        Object env = state.getToolResults() == null ? null
                : state.getToolResults().get(AiBusinessToolIds.STOCK_REDUCE_QUERY);
        if (env == null) {
            return "工具未写入或已被失败剥离";
        }
        if (env instanceof Map<?, ?> m && Boolean.FALSE.equals(m.get("success"))) {
            return "stock_reduce_query 执行失败或 success=false";
        }
        return "AnswerPlan 未挂载（attach 失败或 inner 数据为空）";
    }

    private static String dishProfitMissingExplanation(AiRunState state) {
        List<String> plan = state != null ? state.getDataPlanTools() : null;
        if (plan == null || !plan.contains(AiBusinessToolIds.DISH_PROFIT_ANALYSIS)) {
            return "dataPlanTools 未含 dish_profit_analysis（权限裁剪）";
        }
        Object env = state.getToolResults() == null ? null
                : state.getToolResults().get(AiBusinessToolIds.DISH_PROFIT_ANALYSIS);
        if (env == null) {
            return "工具未写入或已被失败剥离";
        }
        if (env instanceof Map<?, ?> m && Boolean.FALSE.equals(m.get("success"))) {
            return "dish_profit_analysis 执行失败或 success=false";
        }
        return "AnswerPlan 未挂载（菜品毛利聚合未产出或数据不足）";
    }
}
