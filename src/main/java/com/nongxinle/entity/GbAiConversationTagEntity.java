package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("gb_ai_conversation_tag")
public class GbAiConversationTagEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long gbAiCtId;

    private Long gbAiCtUserId;

    private Long gbAiCtConversationId;

    private Long gbAiCtTagId;

    private Date gbAiCtCreatedAt;
}
