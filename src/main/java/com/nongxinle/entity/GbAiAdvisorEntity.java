package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;

/**
 * 业务顾问（Advisor）元数据：用户入口角色，不承载 Harness 执行逻辑。
 */
@Data
@TableName("gb_ai_advisor")
@EqualsAndHashCode(callSuper = false)
public class GbAiAdvisorEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long gbAiAdvisorId;

    private String gbAiAdvisorCode;

    private String gbAiAdvisorName;

    private String gbAiAdvisorSubtitle;

    private String gbAiAdvisorDescription;

    private String gbAiAdvisorAvatarUrl;

    private Integer gbAiAdvisorSortOrder;

    private Integer gbAiAdvisorEnabled;

    private Long gbAiAdvisorDistributerId;

    private Long gbAiAdvisorDepartmentId;

    private Date gbAiAdvisorCreateTime;

    private Date gbAiAdvisorUpdateTime;
}
