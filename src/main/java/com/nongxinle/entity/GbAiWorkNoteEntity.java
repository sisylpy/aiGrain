package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("gb_ai_work_note")
public class GbAiWorkNoteEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long gbAiWnId;

    private Long gbAiWnUserId;

    private Long gbAiWnConversationId;

    private String gbAiWnTitle;

    private String gbAiWnContentMd;

    private String gbAiWnNoteType;

    private String gbAiWnPrimarySourceType;

    private Long gbAiWnPrimaryConversationId;

    private Long gbAiWnPrimaryRunId;

    private Long gbAiWnPrimaryMessageId;

    private String gbAiWnSourceTextSnapshot;

    private String gbAiWnSourceAnswerPreview;

    private Date gbAiWnCreatedAt;

    private Date gbAiWnUpdatedAt;

    /** 0 正常，1 软删 */
    private Integer gbAiWnDeleted;
}
