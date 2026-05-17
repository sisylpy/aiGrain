package com.nongxinle.ai.advisor.workflow.dto;

import lombok.Data;

import java.util.Date;

@Data
public class WorkflowRunDetailDTO {
    private Long workflowRunId;
    private Long workflowId;
    private Long advisorId;
    private Long userId;
    private Long conversationId;
    private Long messageId;
    private Long runId;
    private String status;
    private String inputParamsJson;
    private String resolvedContextJson;
    private String answerPlanJson;
    private String resultSummary;
    private String errorMessage;
    private Long distributerId;
    private Long departmentId;
    private Date startedAt;
    private Date finishedAt;
    private Date createTime;
    private Date updateTime;
}
