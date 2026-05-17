package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;

/**
 * 顾问与可执行工作流的多对多绑定。
 */
@Data
@TableName("gb_ai_advisor_workflow")
@EqualsAndHashCode(callSuper = false)
public class GbAiAdvisorWorkflowEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long gbAiAwId;

    private Long gbAiAwAdvisorId;

    private Long gbAiAwWorkflowId;

    private String gbAiAwRelationType;

    private Integer gbAiAwSortOrder;

    private Integer gbAiAwPinned;

    private Integer gbAiAwIsDefault;

    private Date gbAiAwCreateTime;

    private Date gbAiAwUpdateTime;
}
