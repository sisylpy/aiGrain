package com.nongxinle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 会话内一轮已完成 Run 的语义快照（供追问继承）；追加写入，追问时读conversation+user最新一行。
 */
@Data
@TableName("gb_ai_conversation_turn_memory")
public class GbAiConversationTurnMemoryEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long gbAiCtmId;

    private Long gbAiConversationId;

    private Long gbAiCtmUserId;

    private Long gbAiCtmRunId;

    private String gbAiCtmIntentCode;

    private String gbAiCtmPathCode;

    private String gbAiCtmStructuredIntentDetail;

    private String gbAiCtmPurchaseSourceType;

    private String gbAiCtmStartDate;

    private String gbAiCtmEndDate;

    private String gbAiCtmTimeLabel;

    private String gbAiCtmScopeType;

    private String gbAiCtmVisibleStoreIds;

    private Long gbAiCtmFocusedStoreId;

    private String gbAiCtmFocusedStoreName;

    private String gbAiCtmMentionedStore;

    private String gbAiCtmMentionedDishName;

    private String gbAiCtmFocusType;

    private String gbAiCtmFocusName;

    private String gbAiCtmEffectiveScopeSource;

    private String gbAiCtmEffectiveQuestion;

    private String gbAiCtmAnswerSummary;

    private String gbAiCtmToolSummary;

    @TableField("gb_ai_ctm_create_time")
    private Date gbAiCtmCreateTime;
}
