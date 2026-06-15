package com.nongxinle.ai.workspace.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
public class WorkPinMineListItemDTO {

    private Long pinId;
    private Long conversationId;
    private String conversationTitle;
    private Long messageId;
    private Long runId;
    private String sourceType;
    /** 图钉业务标题（卡片 > 正文） */
    private String title;
    private String preview;
    private String primaryCardType;
    private Integer cardCount;
    private Boolean hasCards;
    private Date createdAt;
}
