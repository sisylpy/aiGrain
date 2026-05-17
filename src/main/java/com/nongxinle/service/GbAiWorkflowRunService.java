package com.nongxinle.service;

import com.nongxinle.ai.advisor.workflow.dto.WorkflowRunDetailDTO;
import com.nongxinle.ai.platform.AiRunStatus;

public interface GbAiWorkflowRunService {

    WorkflowRunDetailDTO getRun(Long workflowRunId);

    /**
     * 将 {@code gb_ai_workflow_run} 从 RUNNING 更新为 Harness 终态（按 {@code gb_ai_wr_run_id}，幂等）。
     * 无匹配行时不报错（非 Workflow 发起的 Run）。
     */
    void markTerminalByHarnessRunId(
            long harnessRunId, AiRunStatus harnessStatus, String finalAnswerText, String terminalErrorMessage);
}
