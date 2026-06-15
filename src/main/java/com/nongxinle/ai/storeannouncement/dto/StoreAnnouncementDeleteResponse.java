package com.nongxinle.ai.storeannouncement.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StoreAnnouncementDeleteResponse {

    private Long announcementId;
    private String publishStatus;
}
