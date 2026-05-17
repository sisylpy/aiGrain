package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("gb_ai_work_pin")
public class GbAiWorkPinEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long gbAiWpId;

    private Long gbAiWpUserId;

    private Long gbAiWpConversationId;

    private Long gbAiWpRunId;

    private Long gbAiWpMessageId;

    private String gbAiWpTitle;

    private String gbAiWpSourceType;

    private String gbAiWpSourceTextSnapshot;

    private String gbAiWpSourceAnswerPreview;

    private String gbAiWpSourceRole;

    private String gbAiWpSourceAgentName;

    private Date gbAiWpSourceCreatedAt;

    private Date gbAiWpCreatedAt;

    private Date gbAiWpUpdatedAt;

    /** 0 正常，1 软删 */
    private Integer gbAiWpDeleted;
}
