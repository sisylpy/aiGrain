package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("gb_ai_agent_run")
public class GbAiAgentRunEntity implements Serializable {

    /** 与应用层 {@code AiRunState#runId} 一致（非自增） */
    @TableId(type = IdType.INPUT)
    private Long id;

    private Long conversationId;
    private Long userId;
    private Long departmentId;
    private Long distributerId;

    private String workspaceMode;
    private String userInput;
    private String normalizedInput;
    private String intent;
    private String status;

    private Date startTime;
    private Date endTime;
    private Integer totalDurationMs;
    private String modelProvider;
    private String modelName;
    private Date createdAt;
}
