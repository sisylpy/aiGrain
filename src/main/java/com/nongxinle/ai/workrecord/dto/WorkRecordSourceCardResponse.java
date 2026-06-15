package com.nongxinle.ai.workrecord.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Date;
import java.util.Map;

@Data
@Builder
public class WorkRecordSourceCardResponse {

    private Long recordId;
    private Long conversationId;
    private Long messageId;
    private Long runId;
    private String cardType;
    private String itemKey;
    private Map<String, Object> payload;
    private String rawFactText;
    private Date timestamp;
    private String scopeLabel;
    private String sourceAnswerPlanType;
    private String cardTitle;
    private String cardSubtitle;
    private String chartType;
}
