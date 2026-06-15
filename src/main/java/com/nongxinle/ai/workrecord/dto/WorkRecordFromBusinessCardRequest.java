package com.nongxinle.ai.workrecord.dto;

import lombok.Data;

@Data
public class WorkRecordFromBusinessCardRequest {

    private Long userId;
    private Long departmentId;
    private Long distributerId;

    private Long sourceConversationId;
    private Long sourceMessageId;
    private Long sourceRunId;
    private String sourceCardType;
    private String sourceItemKey;

    /** 可选；仅辅助校验，不作为事实选择主权。 */
    private String sourceAnswerPlanType;
}
