package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nongxinle.ai.advisor.workflow.WorkflowRunStatus;
import com.nongxinle.ai.advisor.workflow.dto.WorkflowRunDetailDTO;
import com.nongxinle.ai.platform.AiRunStatus;
import com.nongxinle.entity.GbAiWorkflowRunEntity;
import com.nongxinle.mapper.GbAiWorkflowRunMapper;
import com.nongxinle.service.GbAiWorkflowRunService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
@RequiredArgsConstructor
public class GbAiWorkflowRunServiceImpl implements GbAiWorkflowRunService {

    private static final int RESULT_SUMMARY_MAX = 512;
    /** {@code gb_ai_wr_error_message} 为 TEXT；限制长度避免异常栈过大 */
    private static final int ERROR_MESSAGE_MAX = 4000;

    private final GbAiWorkflowRunMapper workflowRunMapper;

    @Override
    public WorkflowRunDetailDTO getRun(Long workflowRunId) {
        if (workflowRunId == null) {
            throw new IllegalArgumentException("workflowRunId required");
        }
        GbAiWorkflowRunEntity row = workflowRunMapper.selectById(workflowRunId);
        if (row == null) {
            throw new IllegalArgumentException("workflow run not found: " + workflowRunId);
        }
        return toDetail(row);
    }

    @Override
    public void markTerminalByHarnessRunId(
            long harnessRunId, AiRunStatus harnessStatus, String finalAnswerText, String terminalErrorMessage) {
        if (harnessRunId <= 0) {
            return;
        }
        if (harnessStatus != AiRunStatus.COMPLETED
                && harnessStatus != AiRunStatus.FAILED
                && harnessStatus != AiRunStatus.CANCELLED) {
            return;
        }

        WorkflowRunStatus wfStatus =
                switch (harnessStatus) {
                    case COMPLETED -> WorkflowRunStatus.COMPLETED;
                    case FAILED -> WorkflowRunStatus.FAILED;
                    case CANCELLED -> WorkflowRunStatus.CANCELLED;
                    default -> throw new IllegalStateException("unexpected harnessStatus: " + harnessStatus);
                };

        Date now = new Date();
        LambdaUpdateWrapper<GbAiWorkflowRunEntity> uw = Wrappers.lambdaUpdate();
        uw.eq(GbAiWorkflowRunEntity::getGbAiWrRunId, harnessRunId)
                .eq(GbAiWorkflowRunEntity::getGbAiWrStatus, WorkflowRunStatus.RUNNING.code())
                .set(GbAiWorkflowRunEntity::getGbAiWrStatus, wfStatus.code())
                .set(GbAiWorkflowRunEntity::getGbAiWrFinishedAt, now)
                .set(GbAiWorkflowRunEntity::getGbAiWrUpdateTime, now);

        if (harnessStatus == AiRunStatus.COMPLETED) {
            String summary = truncateNullable(finalAnswerText, RESULT_SUMMARY_MAX);
            uw.set(GbAiWorkflowRunEntity::getGbAiWrResultSummary, summary);
        } else if (harnessStatus == AiRunStatus.FAILED) {
            String err = terminalErrorMessage == null ? "unknown_error" : terminalErrorMessage.trim();
            if (err.isEmpty()) {
                err = "unknown_error";
            }
            uw.set(GbAiWorkflowRunEntity::getGbAiWrErrorMessage, truncateNullable(err, ERROR_MESSAGE_MAX));
        }

        workflowRunMapper.update(null, uw);
    }

    private static String truncateNullable(String raw, int maxLen) {
        if (raw == null) {
            return null;
        }
        if (raw.length() <= maxLen) {
            return raw;
        }
        return raw.substring(0, maxLen);
    }

    private WorkflowRunDetailDTO toDetail(GbAiWorkflowRunEntity e) {
        WorkflowRunDetailDTO d = new WorkflowRunDetailDTO();
        d.setWorkflowRunId(e.getGbAiWrId());
        d.setWorkflowId(e.getGbAiWrWorkflowId());
        d.setAdvisorId(e.getGbAiWrAdvisorId());
        d.setUserId(e.getGbAiWrUserId());
        d.setConversationId(e.getGbAiWrConversationId());
        d.setMessageId(e.getGbAiWrMessageId());
        d.setRunId(e.getGbAiWrRunId());
        d.setStatus(e.getGbAiWrStatus());
        d.setInputParamsJson(e.getGbAiWrInputParamsJson());
        d.setResolvedContextJson(e.getGbAiWrResolvedContextJson());
        d.setAnswerPlanJson(e.getGbAiWrAnswerPlanJson());
        d.setResultSummary(e.getGbAiWrResultSummary());
        d.setErrorMessage(e.getGbAiWrErrorMessage());
        d.setDistributerId(e.getGbAiWrDistributerId());
        d.setDepartmentId(e.getGbAiWrDepartmentId());
        d.setStartedAt(e.getGbAiWrStartedAt());
        d.setFinishedAt(e.getGbAiWrFinishedAt());
        d.setCreateTime(e.getGbAiWrCreateTime());
        d.setUpdateTime(e.getGbAiWrUpdateTime());
        return d;
    }
}
