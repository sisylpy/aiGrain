package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("gb_ai_conversation_pin")
public class GbAiConversationPinEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long gbAiCpId;

    private Long gbAiCpUserId;

    private Long gbAiCpConversationId;

    private Date gbAiCpPinnedAt;

    private Date gbAiCpCreatedAt;
}
