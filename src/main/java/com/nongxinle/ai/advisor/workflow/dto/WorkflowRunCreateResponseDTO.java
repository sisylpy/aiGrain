package com.nongxinle.ai.advisor.workflow.dto;

import lombok.Data;

@Data
public class WorkflowRunCreateResponseDTO {
    private Long workflowRunId;
    private String status;
    /** Harness {@code gb_ai_agent_run.id} / {@link com.nongxinle.ai.trace.AiRunSessionRegistry}；仅已调度时非空 */
    private Long runId;
    /** 本轮 Run 所属会话；服务端可能新建 */
    private Long conversationId;
}
