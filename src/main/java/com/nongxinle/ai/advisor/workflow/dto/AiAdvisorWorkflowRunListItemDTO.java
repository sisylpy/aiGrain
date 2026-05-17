package com.nongxinle.ai.advisor.workflow.dto;

import lombok.Data;

import java.util.Date;

/**
 * 某顾问下用户最近工作流运行（列表项，联表工作流名称）。
 */
@Data
public class AiAdvisorWorkflowRunListItemDTO {
    private Long workflowRunId;
    private Long workflowId;
    private String workflowCode;
    private String workflowName;
    private Long advisorId;
    private Long userId;
    private String status;
    private String resultSummary;
    private String errorMessage;
    private Date createTime;
    private Date updateTime;
}
