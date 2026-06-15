package com.nongxinle.ai.workspace.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
public class WorkNoteMineListItemDTO {

    private Long noteId;
    private Long conversationId;
    private String conversationTitle;
    private String title;
    private String noteType;
    private Long messageId;
    private Long runId;
    private String preview;
    private Boolean hasCards;
    private Date updatedAt;
}
