package com.nongxinle.ai.trace;

import com.alibaba.fastjson2.JSON;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.entity.GbAiAgentRunEntity;
import com.nongxinle.entity.GbAiAgentStepEntity;
import com.nongxinle.mapper.GbAiAgentRunMapper;
import com.nongxinle.mapper.GbAiAgentStepMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI Run / Step 落库；表未建或未连库时打日志告警，不中断主链路。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAgentTraceService {

    private final GbAiAgentRunMapper runMapper;
    private final GbAiAgentStepMapper stepMapper;

    /** 为 false 时跳过所有 Trace 写库（无库/联调 SSE 用，避免 JDBC 建连长时间阻塞主链路）。默认 true。 */
    @org.springframework.beans.factory.annotation.Value("${ai.trace.persist-enabled:true}")
    private boolean persistEnabled;

    public void insertRunStarting(long runId, AiRunState state) {
        if (!persistEnabled) {
            return;
        }
        Date now = new Date();
        GbAiAgentRunEntity row = new GbAiAgentRunEntity();
        row.setId(runId);
        row.setConversationId(state == null ? null : state.getConversationId());
        row.setUserId(state == null ? null : state.getUserId());
        row.setDepartmentId(state == null ? null : state.getDepartmentId());
        row.setDistributerId(state == null ? null : state.getDistributerId());
        row.setUserInput(state == null ? null : state.getRawUserInput());
        row.setNormalizedInput(state == null ? null : state.getNormalizedUserInput());
        row.setStatus("RUNNING");
        row.setStartTime(now);
        row.setCreatedAt(now);
        try {
            runMapper.insert(row);
        } catch (Exception e) {
            log.warn("[AiAgentTrace] insert gb_ai_agent_run failed id={}, 请执行 sql/gb_ai_agent_run_step.sql 并配置数据源: {}",
                    runId, e.toString());
        }
    }

    public void updateRunFinished(long runId, String statusName, AiRunState state, long durationMs, String workspaceMode) {
        if (!persistEnabled) {
            return;
        }
        GbAiAgentRunEntity row = new GbAiAgentRunEntity();
        row.setId(runId);
        row.setEndTime(new Date());
        row.setTotalDurationMs((int) Math.min(durationMs, Integer.MAX_VALUE));
        row.setStatus(statusName);
        if (workspaceMode != null) {
            row.setWorkspaceMode(workspaceMode);
        }
        if (state != null && state.getRawUserInput() != null) {
            row.setUserInput(state.getRawUserInput());
        }
        try {
            runMapper.updateById(row);
        } catch (Exception e) {
            log.warn("[AiAgentTrace] update gb_ai_agent_run failed id={}: {}", runId, e.toString());
        }
    }

    public void insertStep(long runId, int stepOrder, String stepType, String stepName,
                          Map<String, Object> input, Map<String, Object> output, String status,
                          Integer durationMs, String errorMessage) {
        if (!persistEnabled) {
            return;
        }
        Date now = new Date();
        GbAiAgentStepEntity row = new GbAiAgentStepEntity();
        row.setRunId(runId);
        row.setStepOrder(stepOrder);
        row.setStepType(stepType == null ? "AGENT_NODE" : stepType);
        row.setStepName(stepName);
        row.setInputJson(input == null ? null : JSON.toJSONString(input));
        row.setOutputJson(output == null ? null : JSON.toJSONString(output));
        row.setStatus(status);
        row.setDurationMs(durationMs);
        row.setErrorMessage(errorMessage);
        row.setCreatedAt(now);
        try {
            stepMapper.insert(row);
        } catch (Exception e) {
            log.warn("[AiAgentTrace] insert gb_ai_agent_step failed runId={}, step={}: {}", runId, stepOrder, e.toString());
        }
    }

    /**
     * 用于步骤 input 侧的轻量快照（避免整份 state 入库）。
     */
    public static Map<String, Object> summarizeStateBefore(AiRunState state) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (state == null) {
            return m;
        }
        m.put("cancelledBefore", state.isCancelled());
        m.put("workspaceModeBefore",
                state.getWorkspaceMode() == null ? null : state.getWorkspaceMode().name());
        return m;
    }

    /**
     * 节点执行完成后快照。
     */
    public static Map<String, Object> summarizeStateAfter(AiRunState state, String nodeName) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (state == null) {
            return m;
        }
        m.put("cancelledAfter", state.isCancelled());
        m.put("workspaceModeAfter",
                state.getWorkspaceMode() == null ? null : state.getWorkspaceMode().name());
        m.put("node", nodeName);
        return m;
    }
}
