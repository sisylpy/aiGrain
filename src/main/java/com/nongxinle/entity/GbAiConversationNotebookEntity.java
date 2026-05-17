package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("gb_ai_conversation_notebook")
public class GbAiConversationNotebookEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long gbAiCnbId;

    private Long gbAiCnbUserId;

    private Long gbAiCnbConversationId;

    private Long gbAiCnbNotebookId;

    private Date gbAiCnbCreatedAt;
}
