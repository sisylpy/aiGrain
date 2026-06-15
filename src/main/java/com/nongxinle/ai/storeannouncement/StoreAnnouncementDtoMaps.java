package com.nongxinle.ai.storeannouncement;

import com.nongxinle.ai.storeannouncement.dto.StoreAnnouncementResponse;
import com.nongxinle.entity.GbStoreAnnouncementEntity;

public final class StoreAnnouncementDtoMaps {

    private StoreAnnouncementDtoMaps() {
    }

    public static StoreAnnouncementResponse toResponse(GbStoreAnnouncementEntity e) {
        if (e == null) {
            return null;
        }
        return StoreAnnouncementResponse.builder()
                .announcementId(e.getGbSaId())
                .distributerId(e.getGbSaDistributerId())
                .departmentId(e.getGbSaDepartmentId())
                .publisherUserId(e.getGbSaPublisherUserId())
                .announcementType(e.getGbSaAnnouncementType())
                .sourceType(e.getGbSaSourceType())
                .sourceId(e.getGbSaSourceId())
                .title(e.getGbSaTitle())
                .textContent(e.getGbSaTextContent())
                .cardType(e.getGbSaCardType())
                .cardSnapshotJson(e.getGbSaCardSnapshotJson())
                .cardsSnapshotJson(e.getGbSaCardsSnapshotJson())
                .sourceConversationId(e.getGbSaSourceConversationId())
                .sourceMessageId(e.getGbSaSourceMessageId())
                .sourceRunId(e.getGbSaSourceRunId())
                .sourceItemKey(e.getGbSaSourceItemKey())
                .publishStatus(e.getGbSaPublishStatus())
                .publishedAt(e.getGbSaPublishedAt())
                .createdAt(e.getGbSaCreatedAt())
                .build();
    }
}
