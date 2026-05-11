package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("gb_ai_agent_step")
public class GbAiAgentStepEntity implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long runId;
    private Integer stepOrder;
    private String stepType;
    private String stepName;
    /** MySQL JSON，MP 写入字符串 */
    private String inputJson;
    private String outputJson;
    private String status;
    private Integer durationMs;
    private String errorMessage;
    private Date createdAt;
}
