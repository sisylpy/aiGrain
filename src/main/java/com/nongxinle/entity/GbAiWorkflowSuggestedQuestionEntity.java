package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;

/**
 * Workflow 推荐问句：一个 Workflow 下多条自然语言入口。
 */
@Data
@TableName("gb_ai_workflow_suggested_question")
@EqualsAndHashCode(callSuper = false)
public class GbAiWorkflowSuggestedQuestionEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long gbAiWsqId;

    private Long gbAiWsqWorkflowId;

    private String gbAiWsqWorkflowCode;

    private String gbAiWsqTopicId;

    private String gbAiWsqTopicTitle;

    private String gbAiWsqTopicDescription;

    private Integer gbAiWsqTopicSort;

    private String gbAiWsqQuestionCode;

    private String gbAiWsqQuestionText;

    private Integer gbAiWsqEnabled;

    private String gbAiWsqStatus;

    private String gbAiWsqScene;

    private Integer gbAiWsqSort;

    private String gbAiWsqIntentHint;

    private String gbAiWsqContractHint;

    private Date gbAiWsqCreateTime;

    private Date gbAiWsqUpdateTime;
}
