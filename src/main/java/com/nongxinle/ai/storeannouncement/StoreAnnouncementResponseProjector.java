package com.nongxinle.ai.storeannouncement;

import com.nongxinle.ai.storeannouncement.dto.StoreAnnouncementResponse;
import com.nongxinle.entity.GbDepartmentUserEntity;
import com.nongxinle.entity.GbStoreAnnouncementEntity;
import com.nongxinle.service.GbDepartmentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class StoreAnnouncementResponseProjector {

    private final GbDepartmentUserService departmentUserService;

    public StoreAnnouncementResponse project(
            GbStoreAnnouncementEntity entity, StoreAnnouncementScopeGuard.ResolvedScope scope) {
        StoreAnnouncementResponse base = StoreAnnouncementDtoMaps.toResponse(entity);
        if (base == null) {
            return null;
        }
        base.setStoreName(scope != null ? scope.storeName() : null);
        base.setPublisherName(resolvePublisherName(entity.getGbSaPublisherUserId()));
        return base;
    }

    public List<StoreAnnouncementResponse> projectList(
            List<GbStoreAnnouncementEntity> entities, StoreAnnouncementScopeGuard.ResolvedScope scope) {
        Map<Long, String> publisherNames = batchPublisherNames(entities);
        String storeName = scope != null ? scope.storeName() : null;
        return entities.stream()
                .map(
                        e -> {
                            StoreAnnouncementResponse dto = StoreAnnouncementDtoMaps.toResponse(e);
                            if (dto != null) {
                                dto.setStoreName(storeName);
                                dto.setPublisherName(
                                        publisherNames.getOrDefault(
                                                e.getGbSaPublisherUserId(),
                                                resolvePublisherName(e.getGbSaPublisherUserId())));
                            }
                            return dto;
                        })
                .toList();
    }

    private Map<Long, String> batchPublisherNames(List<GbStoreAnnouncementEntity> entities) {
        Map<Long, String> out = new HashMap<>();
        for (GbStoreAnnouncementEntity e : entities) {
            Long uid = e.getGbSaPublisherUserId();
            if (uid == null || out.containsKey(uid)) {
                continue;
            }
            out.put(uid, resolvePublisherName(uid));
        }
        return out;
    }

    private String resolvePublisherName(Long publisherUserId) {
        if (publisherUserId == null) {
            return null;
        }
        if (publisherUserId > Integer.MAX_VALUE || publisherUserId < Integer.MIN_VALUE) {
            return "用户" + publisherUserId;
        }
        GbDepartmentUserEntity user = departmentUserService.getById(publisherUserId.intValue());
        if (user == null) {
            return "用户" + publisherUserId;
        }
        if (StringUtils.hasText(user.getGbDuWxNickName())) {
            return user.getGbDuWxNickName().trim();
        }
        return "用户" + publisherUserId;
    }
}
