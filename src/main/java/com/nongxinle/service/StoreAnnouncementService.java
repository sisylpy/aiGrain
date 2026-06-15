package com.nongxinle.service;

import com.nongxinle.ai.storeannouncement.dto.StoreAnnouncementDeleteResponse;
import com.nongxinle.ai.storeannouncement.dto.StoreAnnouncementPublishRequest;
import com.nongxinle.ai.storeannouncement.dto.StoreAnnouncementResponse;

import java.util.List;

public interface StoreAnnouncementService {

    StoreAnnouncementResponse publishFromWorkRecord(Long recordId, StoreAnnouncementPublishRequest request);

    StoreAnnouncementResponse publishFromPin(Long pinId, StoreAnnouncementPublishRequest request);

    StoreAnnouncementResponse publishFromNote(Long noteId, StoreAnnouncementPublishRequest request);

    List<StoreAnnouncementResponse> listPublished(
            Long userId, Long departmentId, Long distributerId, Integer page, Integer pageSize);

    StoreAnnouncementResponse getById(Long announcementId, Long userId, Long departmentId, Long distributerId);

    StoreAnnouncementDeleteResponse deleteAnnouncement(
            Long announcementId, Long userId, Long departmentId, Long distributerId);
}
