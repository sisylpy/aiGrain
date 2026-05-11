package com.nongxinle.ai.platform;

import com.alibaba.fastjson2.JSON;
import com.nongxinle.ai.context.AiOrgScopeResolver;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.context.AiUserContextResolver;
import com.nongxinle.ai.resolver.AiResolvedQueryContextResolver;
import com.nongxinle.ai.core.AiGraphRunner;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.platform.dto.AiRunCreateRequest;
import com.nongxinle.ai.platform.dto.AiRunStartResult;
import com.nongxinle.ai.scope.AiConversationScopeMode;
import com.nongxinle.entity.GbAiConversationEntity;
import com.nongxinle.service.GbAiChatService;
import com.nongxinle.ai.platform.dto.StopRunOutcome;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private final AiOrgScopeResolver orgScopeResolver;
    private final AiFollowUpConversationMemory followUpConversationMemory;
    private final AiResolvedQueryContextResolver resolvedQueryContextResolver;
    private final AiConversationMemoryService conversationMemoryService;
    private final GbAiChatService gbAiChatService;

    @Value("${ai.harness.debug-context-enabled:false}")
    private boolean harnessDebugContextEnabled;

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
        var os = orgScopeResolver.resolve(uc, req);
        AiResolvedQueryContext resolved = resolvedQueryContextResolver.resolve(runId, req, uc);

        String normalizedInput = resolved.getNormalizedQuestion();
        if (!StringUtils.hasText(normalizedInput)) {
            normalizedInput = req.getMessage().trim();
        }

        AiRunState state = AiRunState.builder()
                .runId(runId)
                .conversationId(req.getConversationId())
                .userId(req.getUserId())
                .departmentId(req.getDepartmentId())
                .distributerId(req.getDistributerId())
                .aiUserContext(uc)
                .aiOrgScope(os)
                .resolvedQueryContext(resolved)
                .userRole(uc.getRoleCode())
                .rawUserInput(req.getMessage())
                .normalizedUserInput(normalizedInput)
                .build();

        logResolvedQueryContext(runId, req.getConversationId(), resolved);
        logHarnessTurnMemory(runId, req.getConversationId(), resolved);

        AiRunSession session = new AiRunSession(runId, state);
        sessionRegistry.register(session);

        asyncExecutor.runAsync(() -> executeRun(runId));
        return new AiRunStartResult(runId, req.getConversationId());
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
        eventPublisher.publish(runId, "run_started", runStarted);
        traceService.insertRunStarting(runId, session.getState());
        try {
            endedState = graphRunner.runBusinessGraph(session.getState());

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
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
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
        envelope.put("effectiveSqlDepartmentIds", sum.get("effectiveSqlDepartmentIds"));
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
        envelope.put("buildInsightInputStoreRootIds", sum.get("buildInsightInputStoreRootIds"));
        envelope.put("buildInsightInputDepartmentIdsAllowFilter", sum.get("buildInsightInputDepartmentIdsAllowFilter"));
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
