package com.nongxinle.ai.workrecord;

import com.alibaba.fastjson2.JSON;
import com.nongxinle.entity.GbAiAgentRunEntity;
import com.nongxinle.entity.GbAiAgentStepEntity;
import com.nongxinle.mapper.GbAiAgentRunMapper;
import com.nongxinle.mapper.GbAiAgentStepMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkRecordTraceHelper {

    private final GbAiAgentRunMapper runMapper;
    private final GbAiAgentStepMapper stepMapper;

    @Value("${ai.trace.persist-enabled:true}")
    private boolean persistEnabled;

    public void insertRunStarting(
            long runId,
            Long conversationId,
            Long userId,
            Long departmentId,
            Long distributerId,
            String userInput) {
        if (!persistEnabled) {
            return;
        }
        Date now = new Date();
        GbAiAgentRunEntity row = new GbAiAgentRunEntity();
        row.setId(runId);
        row.setConversationId(conversationId);
        row.setUserId(userId);
        row.setDepartmentId(departmentId);
        row.setDistributerId(distributerId);
        row.setUserInput(userInput);
        row.setNormalizedInput(userInput);
        row.setStatus("RUNNING");
        row.setStartTime(now);
        row.setCreatedAt(now);
        try {
            runMapper.insert(row);
        } catch (Exception e) {
            log.warn("[WorkRecordTrace] insert run failed id={}: {}", runId, e.toString());
        }
    }

    public void finishRun(long runId, String status, long durationMs, String errorMessage) {
        if (!persistEnabled) {
            return;
        }
        GbAiAgentRunEntity row = new GbAiAgentRunEntity();
        row.setId(runId);
        row.setEndTime(new Date());
        row.setTotalDurationMs((int) Math.min(durationMs, Integer.MAX_VALUE));
        row.setStatus(status);
        row.setWorkspaceMode(WorkRecordConstants.WORKSPACE_MODE);
        try {
            runMapper.updateById(row);
        } catch (Exception e) {
            log.warn("[WorkRecordTrace] update run failed id={}: {}", runId, e.toString());
        }
        if (errorMessage != null) {
            insertStep(runId, 99, "WORK_RECORD_AI", "work_record_polish_classify", null, null, "FAILED", 0, errorMessage);
        }
    }

    public void insertLlmStep(
            long runId,
            String userPayload,
            String rawLlmResponse,
            String status,
            int durationMs) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("userPayload", userPayload);
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("rawLlmResponse", rawLlmResponse);
        insertStep(runId, 1, "WORK_RECORD_AI", "work_record_polish_classify", input, output, status, durationMs, null);
    }

    private void insertStep(
            long runId,
            int stepOrder,
            String stepType,
            String stepName,
            Map<String, Object> input,
            Map<String, Object> output,
            String status,
            Integer durationMs,
            String errorMessage) {
        if (!persistEnabled) {
            return;
        }
        GbAiAgentStepEntity row = new GbAiAgentStepEntity();
        row.setRunId(runId);
        row.setStepOrder(stepOrder);
        row.setStepType(stepType);
        row.setStepName(stepName);
        row.setInputJson(input == null ? null : JSON.toJSONString(input));
        row.setOutputJson(output == null ? null : JSON.toJSONString(output));
        row.setStatus(status);
        row.setDurationMs(durationMs);
        row.setErrorMessage(errorMessage);
        row.setCreatedAt(new Date());
        try {
            stepMapper.insert(row);
        } catch (Exception e) {
            log.warn("[WorkRecordTrace] insert step failed runId={}: {}", runId, e.toString());
        }
    }
}
