package com.nongxinle.controller;

import com.nongxinle.ai.composer.AiAnswerContextSummarySupport;
import com.nongxinle.ai.platform.AiRunService;
import com.nongxinle.ai.platform.AiRunStatus;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.platform.dto.AiRunCreateRequest;
import com.nongxinle.ai.platform.dto.AiRunStartResult;
import com.nongxinle.ai.platform.dto.StopRunOutcome;
import com.nongxinle.ai.trace.AiRunSession;
import com.nongxinle.ai.trace.AiRunSessionRegistry;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.harness.AiHarnessResolvedContextSummarizer;
import com.nongxinle.ai.harness.BusinessOverviewDishSalesReasonAgentHarnessSupport;
import com.nongxinle.utils.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
        log.info(
                "[AiRunController#createRun] received userId={} conversationId={} messageLen={}",
                body != null ? body.getUserId() : null,
                body != null ? body.getConversationId() : null,
                body != null && body.getMessage() != null ? body.getMessage().length() : 0);
        try {
            AiRunStartResult started = aiRunService.startRun(body);
            log.info(
                    "[AiRunController#createRun] return response runId={} conversationId={}",
                    started.runId(),
                    started.conversationId());
            return R.ok()
                    .put("runId", started.runId())
                    .put("conversationId", started.conversationId())
                    .put("status", "STARTED");
        } catch (IllegalArgumentException ex) {
            log.warn("[AiRunController#createRun] bad request: {}", ex.getMessage());
            return R.error(400, ex.getMessage());
        } catch (Exception ex) {
            log.error("[AiRunController#createRun] failed", ex);
            return R.error(500, ex.getMessage() == null ? "create_run_failed" : ex.getMessage());
        }
    }

    @GetMapping("/{runId}")
    @Operation(summary = "查询 Run 状态（内存快照）",
            description = "始终返回 harnessDebug.debugContextEnabled（与配置 ai.harness.debug-context-enabled 一致）。"
                    + " 默认返回完整 resolvedQueryContextSummary（debug=light 时仅返回元数据，不含摘要）。"
                    + " SSE answer_delta/run_finished 在 debug-context-enabled=true 时下发 resolvedQueryContextSummary（全量摘要）与 debugSummary（Run 元数据）。")
    public R getRun(@PathVariable long runId,
                    @Parameter(description = "debug=full 返回完整 resolvedQueryContextSummary；默认 light 仅返回轻量字段")
                    @RequestParam(required = false, defaultValue = "full") String debug) {
        AiRunSession session = runSessionRegistry.get(runId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "run not found"));

        boolean debugFull = "full".equalsIgnoreCase(debug) || harnessDebugContextEnabled;

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
        harnessDebug.put("debugMode", debugFull ? "full" : "light");

        if (debugFull) {
            AiResolvedQueryContext rq = session.getState().getResolvedQueryContext();
            boolean present = rq != null;
            harnessDebug.put("resolvedQueryContextPresent", present);
            if (present) {
                Map<String, Object> summary = AiHarnessResolvedContextSummarizer.summarize(
                        rq, session.getState().getConversationId(), session.getState());
                harnessDebug.put("resolvedQueryContextSummary", summary);
            }
        }
        boolean stateNonNull = session.getState() != null;
        boolean rqNonNull = stateNonNull && session.getState().getResolvedQueryContext() != null;
        log.info(
                "[AiRunController#getRun] runId={} debugMode={} harnessDebugContextEnabled={} stateNonNull={} resolvedQueryContextNonNull={}",
                runId,
                debugFull ? "full" : "light",
                harnessDebugContextEnabled,
                stateNonNull,
                rqNonNull);
        AiRunState runState = session.getState();
        if (debugFull && runState != null) {
            aiRunService.enrichHarnessDebugWithRunCardFields(harnessDebug, runState);
            if (runState.getMenuExpertPromptPreview() != null) {
                harnessDebug.put("menuExpertPromptPreview", runState.getMenuExpertPromptPreview());
            }
            if (runState.getMenuExpertLlmOutputPreview() != null) {
                harnessDebug.put("menuExpertLlmOutputPreview", runState.getMenuExpertLlmOutputPreview());
            }
            if (runState.getMenuExpertComposerDecision() != null) {
                harnessDebug.put("menuExpertComposerDecision", runState.getMenuExpertComposerDecision());
            }
            BusinessOverviewDishSalesReasonAgentHarnessSupport.copyFlatHarnessFieldsToMap(harnessDebug, runState);
        }
        aiRunService.appendGetRunTopLevelCardFields(r, runState, harnessDebug);
        aiRunService.appendMenuOperationAnswerPlanFields(r, runState, harnessDebug);
        AiAnswerContextSummarySupport.appendToEnvelope(r, runState);
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
