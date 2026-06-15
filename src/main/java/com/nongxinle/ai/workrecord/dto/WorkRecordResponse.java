package com.nongxinle.ai.workrecord.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@Builder
public class WorkRecordResponse {

    private Long recordId;
    private Long conversationId;
    private Long sourceMessageId;
    private Long sourceRunId;
    private Long distributerId;
    private Long departmentId;
    private Long recorderUserId;
    private String inputType;
    private String originType;
    private Long bizConversationId;
    private Long bizMessageId;
    private Long bizRunId;
    private String bizAnswerPlanType;
    private String bizCardType;
    private String bizItemKey;
    private String rawContent;
    private String polishedContent;
    private Long categoryId;
    private String categoryCode;
    private String categoryName;
    private String categoryDecision;
    private String suggestedCategoryName;
    private String aiStatus;
    private BigDecimal aiConfidence;
    private String aiReason;
    private String aiErrorCode;
    private Date recordedAt;
    private Date createdAt;
}
