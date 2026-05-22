package com.nongxinle.controller;

import com.nongxinle.ai.platform.AiRunService;
import com.nongxinle.ai.platform.AiRunStatus;
import com.nongxinle.ai.platform.dto.AiRunCreateRequest;
import com.nongxinle.ai.platform.dto.AiRunStartResult;
import com.nongxinle.ai.platform.dto.StopRunOutcome;
import com.nongxinle.ai.trace.AiRunSession;
import com.nongxinle.ai.trace.AiRunSessionRegistry;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.harness.AiHarnessResolvedContextSummarizer;
import com.nongxinle.utils.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.Map;
@Slf4j
@RestController
@RequestMapping("ai/runs")
@Tag(name = "AI 多智能体 Run（新架构）")
@RequiredArgsConstructor
public class AiRunController {

    private final AiRunService aiRunService;
    private final AiRunSessionRegistry runSessionRegistry;

    /** 默认 false：仅本地/联调开启，勿对正式用户暴露解析摘要。 */
    @Value("${ai.harness.debug-context-enabled:false}")
    private boolean harnessDebugContextEnabled;

    @PostMapping
    @Operation(summary = "创建 Run",
            description = "异步执行 Agent Graph；SSE 订阅 GET /ai/runs/{runId}/events。"
                    + " 首轮可不传 conversationId，服务端写入 gb_ai_conversation 后在响应体返回 conversationId，后续轮须原样回传；"
                    + " 若传 conversationId 则校验归属当前 userId（gb_ai_conversation_user_id）。")
    public R createRun(@RequestBody AiRunCreateRequest body) {
        try {
            AiRunStartResult started = aiRunService.startRun(body);
            return R.ok()
                    .put("runId", started.runId())
                    .put("conversationId", started.conversationId())
                    .put("status", "STARTED");
        } catch (IllegalArgumentException ex) {
            return R.error(400, ex.getMessage());
        }
    }

    @GetMapping("/{runId}")
    @Operation(summary = "查询 Run 状态（内存快照）",
            description = "始终返回 harnessDebug.debugContextEnabled（与配置 ai.harness.debug-context-enabled 一致）。"
                    + " 仅当该开关为 true 且内存态存在 resolvedQueryContext 时，附带 harnessDebug.resolvedQueryContextSummary。"
                    + " （调试专用，非稳定契约；R 继承 Map 且 NON_NULL，故用布尔位区分「未开启 / 无快照 / 已返回摘要」）。")
    public R getRun(@PathVariable long runId) {
        AiRunSession session = runSessionRegistry.get(runId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "run not found"));

        AiRunStatus st = session.getStatus();
        R r = R.ok()
                .put("runId", session.getRunId())
                .put("advisorId", session.getState() != null ? session.getState().getAdvisorId() : null)
                .put("status", st.name())
                .put("workspaceMode", session.getState().getWorkspaceMode() != null ? session.getState().getWorkspaceMode().name() : null)
                .put("cancelled", session.getState().isCancelled())
                .put("answerPreview",
                        session.getState().getFinalAnswerText() == null
                                ? null
                                : session.getState().getFinalAnswerText().substring(
                                0, Math.min(500, session.getState().getFinalAnswerText().length())));

        Map<String, Object> harnessDebug = new LinkedHashMap<>();
        harnessDebug.put("debugContextEnabled", harnessDebugContextEnabled);
        if (harnessDebugContextEnabled) {
            AiResolvedQueryContext rq = session.getState().getResolvedQueryContext();
            boolean present = rq != null;
            harnessDebug.put("resolvedQueryContextPresent", present);
            if (present) {
                Map<String, Object> summary = AiHarnessResolvedContextSummarizer.summarize(
                        rq, session.getState().getConversationId(), session.getState());
                harnessDebug.put("resolvedQueryContextSummary", summary);
                harnessDebug.put("structuredIntentDetail", summary.get("structuredIntentDetail"));
                harnessDebug.put("structuredIntentDetailWire", summary.get("structuredIntentDetailWire"));
                harnessDebug.put("structuredIntentDetailCode", summary.get("structuredIntentDetailCode"));
                harnessDebug.put("structuredIntentDetailPresent", summary.get("structuredIntentDetailPresent"));
                harnessDebug.put("purchaseSourceType", summary.get("purchaseSourceType"));
                harnessDebug.put("supplierAnalysisAgentUsed", summary.get("supplierAnalysisAgentUsed"));
                harnessDebug.put("supplierAnalysisAgentStatus", summary.get("supplierAnalysisAgentStatus"));
                harnessDebug.put("supplierAnalysisPlanType", summary.get("supplierAnalysisPlanType"));
                harnessDebug.put("purchaseSelectedAgents", summary.get("purchaseSelectedAgents"));
                harnessDebug.put("masterBusinessAgentDebug", summary.get("masterBusinessAgentDebug"));
                harnessDebug.put("stockReduceType", summary.get("stockReduceType"));
                harnessDebug.put("dishProfitStructuredDetail", summary.get("dishProfitStructuredDetail"));
                harnessDebug.put("mentionedDishName", summary.get("mentionedDishName"));
                harnessDebug.put("dishProfitMetricType", summary.get("dishProfitMetricType"));
                harnessDebug.put("usedToolId", summary.get("usedToolId"));
                harnessDebug.put("buildInsightUsed", summary.get("buildInsightUsed"));
                harnessDebug.put("usedBuildInsight", summary.get("usedBuildInsight"));
                harnessDebug.put("buildInsightRequest", summary.get("buildInsightRequest"));
                harnessDebug.put("buildInsightInputStoreRootIds", summary.get("buildInsightInputStoreRootIds"));
                harnessDebug.put("buildInsightInputDepartmentIdsAllowFilter",
                        summary.get("buildInsightInputDepartmentIdsAllowFilter"));
                harnessDebug.put("dishesCount", summary.get("dishesCount"));
                harnessDebug.put("dishLineReturned", summary.get("dishLineReturned"));
                harnessDebug.put("effectiveIntentCode", summary.get("effectiveIntentCode"));
                harnessDebug.put("effectivePathCode", summary.get("effectivePathCode"));
                harnessDebug.put("queryScopeKind", summary.get("queryScopeKind"));
                harnessDebug.put("queryStoreIds", summary.get("queryStoreIds"));
                harnessDebug.put("queryRealDepartmentIds", summary.get("queryRealDepartmentIds"));
                harnessDebug.put("queryDistributerId", summary.get("queryDistributerId"));
                harnessDebug.put("storeToDepartmentIds", summary.get("storeToDepartmentIds"));
                harnessDebug.put("salesDishCount", summary.get("salesDishCount"));
                harnessDebug.put("riskLevel", summary.get("riskLevel"));
                harnessDebug.put("expandedSqlDepartmentIds", summary.get("expandedSqlDepartmentIds"));
                harnessDebug.put("visibleStoreRootIds", summary.get("visibleStoreRootIds"));
                harnessDebug.put("childDepartmentIds", summary.get("childDepartmentIds"));
                harnessDebug.put("dishProfitSqlDepartmentIds", summary.get("dishProfitSqlDepartmentIds"));
                harnessDebug.put("departmentScopeModelNote", summary.get("departmentScopeModelNote"));
                harnessDebug.put("dishProfitAnswerPlan", summary.get("dishProfitAnswerPlan"));
                harnessDebug.put("dishProfitAnswerPlanPresent", summary.get("dishProfitAnswerPlanPresent"));
                harnessDebug.put("purchaseAnswerPlan", summary.get("purchaseAnswerPlan"));
                harnessDebug.put("purchaseAnswerPlanPresent", summary.get("purchaseAnswerPlanPresent"));
                harnessDebug.put("purchaseAnswerPlanType", summary.get("purchaseAnswerPlanType"));
                harnessDebug.put("purchaseAnswerPlanSortKey", summary.get("purchaseAnswerPlanSortKey"));
                harnessDebug.put("purchaseAnswerPlanSortDirection", summary.get("purchaseAnswerPlanSortDirection"));
                harnessDebug.put("purchaseAnswerPlanFocusRows", summary.get("purchaseAnswerPlanFocusRows"));
                harnessDebug.put("purchaseAnswerPlanSecondaryRows", summary.get("purchaseAnswerPlanSecondaryRows"));
                harnessDebug.put("purchaseAnswerPlanDebug", summary.get("purchaseAnswerPlanDebug"));
                harnessDebug.put("usedTools", summary.get("usedTools"));
                harnessDebug.put("planSource", summary.get("planSource"));
                harnessDebug.put("warehouseStockAgentUsed", summary.get("warehouseStockAgentUsed"));
                harnessDebug.put("warehouseStockAgentStatus", summary.get("warehouseStockAgentStatus"));
                harnessDebug.put("warehouseStockOverviewToolSuccess", summary.get("warehouseStockOverviewToolSuccess"));
                harnessDebug.put("warehouseStockPlanType", summary.get("warehouseStockPlanType"));
                harnessDebug.put("warehouseStockResultCount", summary.get("warehouseStockResultCount"));
                harnessDebug.put("warehouseStockOverviewPath", summary.get("warehouseStockOverviewPath"));
                harnessDebug.put("groupWarehouseStockOverview", summary.get("groupWarehouseStockOverview"));
                harnessDebug.put("consumedAnswerPlans", summary.get("consumedAnswerPlans"));
                harnessDebug.put("missingAnswerPlans", summary.get("missingAnswerPlans"));
                harnessDebug.put("diagnosisPlan", summary.get("diagnosisPlan"));
                harnessDebug.put("diagnosisPlanPresent", summary.get("diagnosisPlanPresent"));
                harnessDebug.put("diagnosisPlanType", summary.get("diagnosisPlanType"));
                harnessDebug.put("diagnosisRiskLevel", summary.get("diagnosisRiskLevel"));
                harnessDebug.put("diagnosisDataCompleteness", summary.get("diagnosisDataCompleteness"));
                harnessDebug.put("effectiveTimeWindowSource", summary.get("effectiveTimeWindowSource"));
                harnessDebug.put("multiStoreScopeDetected", summary.get("multiStoreScopeDetected"));
                harnessDebug.put("multiStoreScopeApplied", summary.get("multiStoreScopeApplied"));
                harnessDebug.put("multiStoreScopeSource", summary.get("multiStoreScopeSource"));
                harnessDebug.put("multiStoreMatchedStores", summary.get("multiStoreMatchedStores"));
                harnessDebug.put("singleStoreNarrowingBlocked", summary.get("singleStoreNarrowingBlocked"));
                harnessDebug.put("querySemanticEffectiveMentionedStoreNames",
                        summary.get("querySemanticEffectiveMentionedStoreNames"));
                Object rawQSem = summary.get("querySemanticLlm");
                if (rawQSem instanceof Map<?, ?> mq) {
                    harnessDebug.put("querySemanticLlm_mentionedStoreNames", mq.get("mentionedStoreNames"));
                }
            }
            harnessDebug.put("advisorId", session.getState() != null ? session.getState().getAdvisorId() : null);
            harnessDebug.put("composerPromptId", session.getState() != null
                    ? session.getState().getComposerPromptRegistryId() : null);
            AiResolvedQueryContext rqPm = session.getState() != null
                    ? session.getState().getResolvedQueryContext() : null;
            harnessDebug.put("semanticPromptId", rqPm != null ? rqPm.getSemanticPromptRegistryId() : null);
        }
        boolean stateNonNull = session.getState() != null;
        boolean rqNonNull = stateNonNull && session.getState().getResolvedQueryContext() != null;
        log.info(
                "[AiRunController#getRun] runId={} harnessDebugContextEnabled={} stateNonNull={} resolvedQueryContextNonNull={} harnessDebug={}",
                runId,
                harnessDebugContextEnabled,
                stateNonNull,
                rqNonNull,
                harnessDebug);
        r.put("harnessDebug", harnessDebug);
        return r;
    }

    @GetMapping(path = "/{runId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    @Operation(summary = "SSE：Run 过程事件", description = "事件名参见 docs/ARCHITECTURE_DECISIONS.md、docs/SSE_BACKEND_EVENT_CONTRACT.md、docs/API_INTEGRATION.md")
    public SseEmitter streamEvents(@PathVariable long runId) {
        AiRunSession session = runSessionRegistry.get(runId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "run not found"));
        long timeoutMs = 30L * 60_000L;
        SseEmitter emitter = new SseEmitter(timeoutMs);
        session.subscribe(emitter);
        return emitter;
    }

    @PostMapping("/{runId}/stop")
    @Operation(summary = "请求取消 Run（协作式：节点间检测 cancelled）")
    public R stopRun(@PathVariable long runId) {
        StopRunOutcome outcome = aiRunService.stopRun(runId);
        if (!outcome.sessionFound()) {
            return R.error(404, "run not found").put("runId", runId);
        }
        R r = R.ok().put("runId", runId);
        if (outcome.cancelApplied()) {
            return r.put("status", "CANCEL_REQUESTED");
        }
        return r.put("status", outcome.statusAtInvocation().name())
                .put("message", outcome.message());
    }
}
