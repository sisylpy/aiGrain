package com.nongxinle.ai.storeannouncement.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
public class StoreAnnouncementResponse {

    private Long announcementId;
    private Long distributerId;
    private Long departmentId;
    private Long publisherUserId;

    /** TEXT / BUSINESS_CARD / AI_MESSAGE */
    private String announcementType;

    /** WORK_RECORD / WORK_PIN / WORK_NOTE / DIRECT */
    private String sourceType;
    private Long sourceId;

    private String title;
    private String textContent;
    private String cardType;
    private String cardSnapshotJson;
    private String cardsSnapshotJson;

    private Long sourceConversationId;
    private Long sourceMessageId;
    private Long sourceRunId;
    private String sourceItemKey;

    private String publisherName;
    private String storeName;
    private String publishStatus;
    private Date publishedAt;
    private Date createdAt;
}
