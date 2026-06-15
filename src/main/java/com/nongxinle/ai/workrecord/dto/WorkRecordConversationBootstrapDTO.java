package com.nongxinle.ai.workrecord.dto;

import lombok.Data;

@Data
public class WorkRecordConversationBootstrapDTO {

    private Long conversationId;
    private String conversationType;
    private String threadKind;
    private String title;
}
