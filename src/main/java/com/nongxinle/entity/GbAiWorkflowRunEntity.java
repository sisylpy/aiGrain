package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;

/**
 * 工作流一次运行；预留与 Harness Run、会话、AnswerPlan 对齐的列。
 */
@Data
@TableName("gb_ai_workflow_run")
@EqualsAndHashCode(callSuper = false)
public class GbAiWorkflowRunEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long gbAiWrId;

    private Long gbAiWrWorkflowId;

    private Long gbAiWrAdvisorId;

    private Long gbAiWrUserId;

    private Long gbAiWrConversationId;

    private Long gbAiWrMessageId;

    private Long gbAiWrRunId;

    private String gbAiWrStatus;

    private String gbAiWrInputParamsJson;

    private String gbAiWrResolvedContextJson;

    private String gbAiWrAnswerPlanJson;

    private String gbAiWrResultSummary;

    private String gbAiWrErrorMessage;

    private Long gbAiWrDistributerId;

    private Long gbAiWrDepartmentId;

    private Date gbAiWrStartedAt;

    private Date gbAiWrFinishedAt;

    private Date gbAiWrCreateTime;

    private Date gbAiWrUpdateTime;
}
