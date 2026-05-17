package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;

/**
 * 工作流（Workflow）任务定义；真实执行走 Harness，不在此表写结论。
 */
@Data
@TableName("gb_ai_workflow")
@EqualsAndHashCode(callSuper = false)
public class GbAiWorkflowEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long gbAiWorkflowId;

    private String gbAiWorkflowCode;

    private String gbAiWorkflowName;

    private String gbAiWorkflowDescription;

    private String gbAiWorkflowCategory;

    private String gbAiWorkflowIntentCode;

    private String gbAiWorkflowPathCode;

    private String gbAiWorkflowHarnessEntryType;

    private String gbAiWorkflowHarnessPathKey;

    private String gbAiWorkflowInputSchemaJson;

    private String gbAiWorkflowDefaultParamsJson;

    private Integer gbAiWorkflowSortOrder;

    private Integer gbAiWorkflowEnabled;

    private Long gbAiWorkflowDistributerId;

    private Long gbAiWorkflowDepartmentId;

    private Date gbAiWorkflowCreateTime;

    private Date gbAiWorkflowUpdateTime;
}
