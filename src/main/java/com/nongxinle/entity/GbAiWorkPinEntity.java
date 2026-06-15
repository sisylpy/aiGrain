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

    /** 创建时从 gb_ai_message_cards_json 复制的完整 cards[] JSON 快照 */
    private String gbAiWpCardsSnapshotJson;

    /** 首张业务卡的 cardType */
    private String gbAiWpPrimaryCardType;

    /** 业务卡片数量（0 表示无业务卡） */
    private Integer gbAiWpCardCount;

    private Date gbAiWpCreatedAt;

    private Date gbAiWpUpdatedAt;

    /** 0 正常，1 软删 */
    private Integer gbAiWpDeleted;
}
