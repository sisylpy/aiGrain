package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nongxinle.ai.conversation.AiConversationCoreService;
import com.nongxinle.ai.storeannouncement.StoreAnnouncementConstants;
import com.nongxinle.ai.storeannouncement.StoreAnnouncementErrors;
import com.nongxinle.ai.storeannouncement.StoreAnnouncementException;
import com.nongxinle.ai.storeannouncement.StoreAnnouncementResponseProjector;
import com.nongxinle.ai.storeannouncement.StoreAnnouncementScopeGuard;
import com.nongxinle.ai.storeannouncement.StoreAnnouncementSnapshotBuilder;
import com.nongxinle.ai.storeannouncement.dto.StoreAnnouncementDeleteResponse;
import com.nongxinle.ai.storeannouncement.dto.StoreAnnouncementPublishRequest;
import com.nongxinle.ai.storeannouncement.dto.StoreAnnouncementResponse;
import com.nongxinle.ai.workrecord.WorkRecordOwnership;
import com.nongxinle.entity.GbAiConversationEntity;
import com.nongxinle.entity.GbAiWorkNoteEntity;
import com.nongxinle.entity.GbAiWorkPinEntity;
import com.nongxinle.entity.GbStoreAnnouncementEntity;
import com.nongxinle.entity.GbWorkRecordEntity;
import com.nongxinle.mapper.GbAiConversationMapper;
import com.nongxinle.mapper.GbAiWorkNoteMapper;
import com.nongxinle.mapper.GbAiWorkPinMapper;
import com.nongxinle.mapper.GbStoreAnnouncementMapper;
import com.nongxinle.mapper.GbWorkRecordMapper;
import com.nongxinle.service.StoreAnnouncementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class StoreAnnouncementServiceImpl implements StoreAnnouncementService {

    private final StoreAnnouncementScopeGuard scopeGuard;
    private final StoreAnnouncementSnapshotBuilder snapshotBuilder;
    private final StoreAnnouncementResponseProjector responseProjector;
    private final GbWorkRecordMapper workRecordMapper;
    private final GbAiWorkPinMapper pinMapper;
    private final GbAiWorkNoteMapper noteMapper;
    private final GbAiConversationMapper conversationMapper;
    private final GbStoreAnnouncementMapper announcementMapper;
    private final AiConversationCoreService conversationCoreService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StoreAnnouncementResponse publishFromWorkRecord(
            Long recordId, StoreAnnouncementPublishRequest request) {
        requirePublishRequest(request);
        StoreAnnouncementScopeGuard.ResolvedScope scope =
                scopeGuard.resolveStoreMember(
                        request.getUserId(), request.getDepartmentId(), request.getDistributerId());

        GbWorkRecordEntity record = workRecordMapper.selectById(recordId);
        WorkRecordOwnership.assertOwnedRecord(
                record, request.getUserId(), scope.departmentId(), scope.distributerId());

        GbStoreAnnouncementEntity existing =
                findReusableBySource(
                        scope.departmentId(),
                        scope.distributerId(),
                        StoreAnnouncementConstants.SOURCE_WORK_RECORD,
                        record.getGbWrId());

        StoreAnnouncementSnapshotBuilder.WorkRecordSnapshot snapshot =
                snapshotBuilder.buildFromWorkRecord(record, request.getTitle());

        if (existing != null) {
            updateFromWorkRecordSnapshot(existing.getGbSaId(), request.getUserId(), snapshot);
            return responseProjector.project(announcementMapper.selectById(existing.getGbSaId()), scope);
        }

        GbStoreAnnouncementEntity row = buildPublishedRow(scope, request.getUserId(), snapshot, record);
        announcementMapper.insert(row);
        return responseProjector.project(row, scope);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StoreAnnouncementResponse publishFromPin(Long pinId, StoreAnnouncementPublishRequest request) {
        requirePublishRequest(request);
        GbAiWorkPinEntity pin = requireOwnedPin(pinId, request.getUserId());

        StoreAnnouncementScopeGuard.ResolvedScope scope = resolveScopeForPin(request, pin);

        GbStoreAnnouncementEntity existing =
                findReusableBySource(
                        scope.departmentId(),
                        scope.distributerId(),
                        StoreAnnouncementConstants.SOURCE_WORK_PIN,
                        pin.getGbAiWpId());

        StoreAnnouncementSnapshotBuilder.PinSnapshot snapshot =
                snapshotBuilder.buildFromPin(pin, request.getTitle());

        if (existing != null) {
            updateFromPinSnapshot(existing.getGbSaId(), request.getUserId(), snapshot);
            return responseProjector.project(announcementMapper.selectById(existing.getGbSaId()), scope);
        }

        Date now = new Date();
        GbStoreAnnouncementEntity row = new GbStoreAnnouncementEntity();
        row.setGbSaDistributerId(scope.distributerId());
        row.setGbSaDepartmentId(scope.departmentId());
        row.setGbSaPublisherUserId(request.getUserId());
        row.setGbSaAnnouncementType(snapshot.announcementType());
        row.setGbSaSourceType(StoreAnnouncementConstants.SOURCE_WORK_PIN);
        row.setGbSaSourceId(pin.getGbAiWpId());
        row.setGbSaTitle(snapshot.title());
        row.setGbSaTextContent(snapshot.textContent());
        row.setGbSaCardsSnapshotJson(snapshot.cardsSnapshotJson());
        row.setGbSaSourceConversationId(snapshot.sourceConversationId());
        row.setGbSaSourceMessageId(snapshot.sourceMessageId());
        row.setGbSaSourceRunId(snapshot.sourceRunId());
        row.setGbSaPublishStatus(StoreAnnouncementConstants.STATUS_PUBLISHED);
        row.setGbSaPublishedAt(now);
        row.setGbSaCreatedAt(now);
        row.setGbSaUpdatedAt(now);

        announcementMapper.insert(row);
        return responseProjector.project(row, scope);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StoreAnnouncementResponse publishFromNote(Long noteId, StoreAnnouncementPublishRequest request) {
        requirePublishRequest(request);
        GbAiWorkNoteEntity note = requireOwnedNote(noteId, request.getUserId());

        StoreAnnouncementScopeGuard.ResolvedScope scope = resolveScopeForNote(request, note);

        GbStoreAnnouncementEntity existing =
                findReusableBySource(
                        scope.departmentId(),
                        scope.distributerId(),
                        StoreAnnouncementConstants.SOURCE_WORK_NOTE,
                        note.getGbAiWnId());

        StoreAnnouncementSnapshotBuilder.NoteSnapshot snapshot =
                snapshotBuilder.buildFromNote(note, request.getTitle());

        if (existing != null) {
            updateFromNoteSnapshot(existing.getGbSaId(), request.getUserId(), snapshot);
            return responseProjector.project(announcementMapper.selectById(existing.getGbSaId()), scope);
        }

        Date now = new Date();
        GbStoreAnnouncementEntity row = new GbStoreAnnouncementEntity();
        row.setGbSaDistributerId(scope.distributerId());
        row.setGbSaDepartmentId(scope.departmentId());
        row.setGbSaPublisherUserId(request.getUserId());
        row.setGbSaAnnouncementType(snapshot.announcementType());
        row.setGbSaSourceType(StoreAnnouncementConstants.SOURCE_WORK_NOTE);
        row.setGbSaSourceId(note.getGbAiWnId());
        row.setGbSaTitle(snapshot.title());
        row.setGbSaTextContent(snapshot.textContent());
        row.setGbSaCardsSnapshotJson(snapshot.cardsSnapshotJson());
        row.setGbSaSourceConversationId(snapshot.sourceConversationId());
        row.setGbSaSourceMessageId(snapshot.sourceMessageId());
        row.setGbSaSourceRunId(snapshot.sourceRunId());
        row.setGbSaPublishStatus(StoreAnnouncementConstants.STATUS_PUBLISHED);
        row.setGbSaPublishedAt(now);
        row.setGbSaCreatedAt(now);
        row.setGbSaUpdatedAt(now);

        announcementMapper.insert(row);
        return responseProjector.project(row, scope);
    }

    @Override
    public List<StoreAnnouncementResponse> listPublished(
            Long userId, Long departmentId, Long distributerId, Integer page, Integer pageSize) {
        StoreAnnouncementScopeGuard.ResolvedScope scope =
                scopeGuard.resolveStoreMember(userId, departmentId, distributerId);

        int effPage = page == null || page < 1 ? 1 : page;
        int effSize =
                pageSize == null || pageSize < 1
                        ? StoreAnnouncementConstants.DEFAULT_PAGE_SIZE
                        : Math.min(pageSize, StoreAnnouncementConstants.MAX_PAGE_SIZE);
        int offset = (effPage - 1) * effSize;

        LambdaQueryWrapper<GbStoreAnnouncementEntity> q =
                publishedStoreQuery(scope.departmentId(), scope.distributerId())
                        .orderByDesc(GbStoreAnnouncementEntity::getGbSaPublishedAt)
                        .last("LIMIT " + offset + "," + effSize);

        return responseProjector.projectList(announcementMapper.selectList(q), scope);
    }

    @Override
    public StoreAnnouncementResponse getById(
            Long announcementId, Long userId, Long departmentId, Long distributerId) {
        StoreAnnouncementScopeGuard.ResolvedScope scope =
                scopeGuard.resolveStoreMember(userId, departmentId, distributerId);
        GbStoreAnnouncementEntity row = requirePublishedInScope(announcementId, scope);
        return responseProjector.project(row, scope);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StoreAnnouncementDeleteResponse deleteAnnouncement(
            Long announcementId, Long userId, Long departmentId, Long distributerId) {
        StoreAnnouncementScopeGuard.ResolvedScope scope =
                scopeGuard.resolveStoreMember(userId, departmentId, distributerId);
        GbStoreAnnouncementEntity existing = requirePublishedInScope(announcementId, scope);
        if (!Objects.equals(existing.getGbSaPublisherUserId(), userId)) {
            throw new IllegalArgumentException("only publisher can delete announcement");
        }

        GbStoreAnnouncementEntity patch = new GbStoreAnnouncementEntity();
        patch.setGbSaId(existing.getGbSaId());
        patch.setGbSaPublishStatus(StoreAnnouncementConstants.STATUS_DELETED);
        patch.setGbSaUpdatedAt(new Date());
        announcementMapper.updateById(patch);

        return StoreAnnouncementDeleteResponse.builder()
                .announcementId(existing.getGbSaId())
                .publishStatus(StoreAnnouncementConstants.STATUS_DELETED)
                .build();
    }

    private GbStoreAnnouncementEntity buildPublishedRow(
            StoreAnnouncementScopeGuard.ResolvedScope scope,
            Long publisherUserId,
            StoreAnnouncementSnapshotBuilder.WorkRecordSnapshot snapshot,
            GbWorkRecordEntity record) {
        Date now = new Date();
        GbStoreAnnouncementEntity row = new GbStoreAnnouncementEntity();
        row.setGbSaDistributerId(scope.distributerId());
        row.setGbSaDepartmentId(scope.departmentId());
        row.setGbSaPublisherUserId(publisherUserId);
        row.setGbSaAnnouncementType(snapshot.announcementType());
        row.setGbSaSourceType(StoreAnnouncementConstants.SOURCE_WORK_RECORD);
        row.setGbSaSourceId(record.getGbWrId());
        row.setGbSaTitle(snapshot.title());
        row.setGbSaTextContent(snapshot.textContent());
        row.setGbSaCardType(snapshot.cardType());
        row.setGbSaCardSnapshotJson(snapshot.cardSnapshotJson());
        row.setGbSaCardsSnapshotJson(snapshot.cardsSnapshotJson());
        row.setGbSaSourceItemKey(snapshot.sourceItemKey());
        row.setGbSaSourceConversationId(snapshot.sourceConversationId());
        row.setGbSaSourceMessageId(snapshot.sourceMessageId());
        row.setGbSaSourceRunId(snapshot.sourceRunId());
        row.setGbSaPublishStatus(StoreAnnouncementConstants.STATUS_PUBLISHED);
        row.setGbSaPublishedAt(now);
        row.setGbSaCreatedAt(now);
        row.setGbSaUpdatedAt(now);
        return row;
    }

    private GbStoreAnnouncementEntity findReusableBySource(
            Long departmentId, Long distributerId, String sourceType, Long sourceId) {
        if (sourceId == null) {
            return null;
        }
        return announcementMapper.selectOne(
                new LambdaQueryWrapper<GbStoreAnnouncementEntity>()
                        .eq(GbStoreAnnouncementEntity::getGbSaDepartmentId, departmentId)
                        .eq(GbStoreAnnouncementEntity::getGbSaDistributerId, distributerId)
                        .eq(GbStoreAnnouncementEntity::getGbSaSourceType, sourceType)
                        .eq(GbStoreAnnouncementEntity::getGbSaSourceId, sourceId)
                        .in(
                                GbStoreAnnouncementEntity::getGbSaPublishStatus,
                                StoreAnnouncementConstants.STATUS_PUBLISHED,
                                StoreAnnouncementConstants.STATUS_DELETED)
                        .orderByDesc(GbStoreAnnouncementEntity::getGbSaPublishedAt)
                        .last("LIMIT 1"));
    }

    private void updateFromWorkRecordSnapshot(
            Long announcementId,
            Long publisherUserId,
            StoreAnnouncementSnapshotBuilder.WorkRecordSnapshot snapshot) {
        Date now = new Date();
        GbStoreAnnouncementEntity patch = new GbStoreAnnouncementEntity();
        patch.setGbSaId(announcementId);
        patch.setGbSaPublisherUserId(publisherUserId);
        patch.setGbSaAnnouncementType(snapshot.announcementType());
        patch.setGbSaTitle(snapshot.title());
        patch.setGbSaTextContent(snapshot.textContent());
        patch.setGbSaCardType(snapshot.cardType());
        patch.setGbSaCardSnapshotJson(snapshot.cardSnapshotJson());
        patch.setGbSaCardsSnapshotJson(snapshot.cardsSnapshotJson());
        patch.setGbSaSourceItemKey(snapshot.sourceItemKey());
        patch.setGbSaSourceConversationId(snapshot.sourceConversationId());
        patch.setGbSaSourceMessageId(snapshot.sourceMessageId());
        patch.setGbSaSourceRunId(snapshot.sourceRunId());
        patch.setGbSaPublishStatus(StoreAnnouncementConstants.STATUS_PUBLISHED);
        patch.setGbSaPublishedAt(now);
        patch.setGbSaUpdatedAt(now);
        announcementMapper.updateById(patch);
    }

    private void updateFromPinSnapshot(
            Long announcementId,
            Long publisherUserId,
            StoreAnnouncementSnapshotBuilder.PinSnapshot snapshot) {
        Date now = new Date();
        GbStoreAnnouncementEntity patch = new GbStoreAnnouncementEntity();
        patch.setGbSaId(announcementId);
        patch.setGbSaPublisherUserId(publisherUserId);
        patch.setGbSaAnnouncementType(snapshot.announcementType());
        patch.setGbSaTitle(snapshot.title());
        patch.setGbSaTextContent(snapshot.textContent());
        patch.setGbSaCardType(null);
        patch.setGbSaCardSnapshotJson(null);
        patch.setGbSaCardsSnapshotJson(snapshot.cardsSnapshotJson());
        patch.setGbSaSourceItemKey(null);
        patch.setGbSaSourceConversationId(snapshot.sourceConversationId());
        patch.setGbSaSourceMessageId(snapshot.sourceMessageId());
        patch.setGbSaSourceRunId(snapshot.sourceRunId());
        patch.setGbSaPublishStatus(StoreAnnouncementConstants.STATUS_PUBLISHED);
        patch.setGbSaPublishedAt(now);
        patch.setGbSaUpdatedAt(now);
        announcementMapper.updateById(patch);
    }

    private void updateFromNoteSnapshot(
            Long announcementId,
            Long publisherUserId,
            StoreAnnouncementSnapshotBuilder.NoteSnapshot snapshot) {
        Date now = new Date();
        GbStoreAnnouncementEntity patch = new GbStoreAnnouncementEntity();
        patch.setGbSaId(announcementId);
        patch.setGbSaPublisherUserId(publisherUserId);
        patch.setGbSaAnnouncementType(snapshot.announcementType());
        patch.setGbSaTitle(snapshot.title());
        patch.setGbSaTextContent(snapshot.textContent());
        if (StoreAnnouncementConstants.TYPE_TEXT.equals(snapshot.announcementType())) {
            patch.setGbSaCardType(null);
            patch.setGbSaCardSnapshotJson(null);
            patch.setGbSaCardsSnapshotJson(null);
            patch.setGbSaSourceItemKey(null);
        } else {
            patch.setGbSaCardType(null);
            patch.setGbSaCardSnapshotJson(null);
            patch.setGbSaCardsSnapshotJson(snapshot.cardsSnapshotJson());
            patch.setGbSaSourceItemKey(null);
        }
        patch.setGbSaSourceConversationId(snapshot.sourceConversationId());
        patch.setGbSaSourceMessageId(snapshot.sourceMessageId());
        patch.setGbSaSourceRunId(snapshot.sourceRunId());
        patch.setGbSaPublishStatus(StoreAnnouncementConstants.STATUS_PUBLISHED);
        patch.setGbSaPublishedAt(now);
        patch.setGbSaUpdatedAt(now);
        announcementMapper.updateById(patch);
    }

    private static LambdaQueryWrapper<GbStoreAnnouncementEntity> publishedStoreQuery(
            Long departmentId, Long distributerId) {
        return new LambdaQueryWrapper<GbStoreAnnouncementEntity>()
                .eq(GbStoreAnnouncementEntity::getGbSaDepartmentId, departmentId)
                .eq(GbStoreAnnouncementEntity::getGbSaDistributerId, distributerId)
                .eq(
                        GbStoreAnnouncementEntity::getGbSaPublishStatus,
                        StoreAnnouncementConstants.STATUS_PUBLISHED);
    }

    private StoreAnnouncementScopeGuard.ResolvedScope resolveScopeForPin(
            StoreAnnouncementPublishRequest request, GbAiWorkPinEntity pin) {
        Long departmentId = request.getDepartmentId();
        Long distributerId = request.getDistributerId();
        if (departmentId == null || distributerId == null) {
            GbAiConversationEntity conv =
                    conversationMapper.selectById(pin.getGbAiWpConversationId());
            if (conv != null) {
                if (departmentId == null) {
                    departmentId = conv.getGbAiConversationDepartmentId();
                }
                if (distributerId == null) {
                    distributerId = conv.getGbAiConversationDistributerId();
                }
            }
        }
        return scopeGuard.resolveStoreMember(request.getUserId(), departmentId, distributerId);
    }

    private StoreAnnouncementScopeGuard.ResolvedScope resolveScopeForNote(
            StoreAnnouncementPublishRequest request, GbAiWorkNoteEntity note) {
        Long departmentId = request.getDepartmentId();
        Long distributerId = request.getDistributerId();
        Long conversationId = note.getGbAiWnConversationId();
        if (conversationId == null) {
            conversationId = note.getGbAiWnPrimaryConversationId();
        }
        if ((departmentId == null || distributerId == null) && conversationId != null) {
            GbAiConversationEntity conv = conversationMapper.selectById(conversationId);
            if (conv != null) {
                if (departmentId == null) {
                    departmentId = conv.getGbAiConversationDepartmentId();
                }
                if (distributerId == null) {
                    distributerId = conv.getGbAiConversationDistributerId();
                }
            }
        }
        return scopeGuard.resolveStoreMember(request.getUserId(), departmentId, distributerId);
    }

    private GbStoreAnnouncementEntity requirePublishedInScope(
            Long announcementId, StoreAnnouncementScopeGuard.ResolvedScope scope) {
        if (announcementId == null) {
            throw new IllegalArgumentException("announcementId required");
        }
        GbStoreAnnouncementEntity row = announcementMapper.selectById(announcementId);
        if (row == null
                || !StoreAnnouncementConstants.STATUS_PUBLISHED.equals(row.getGbSaPublishStatus())) {
            throw new StoreAnnouncementException(
                    StoreAnnouncementErrors.ANNOUNCEMENT_NOT_FOUND, "announcement not found");
        }
        if (!Objects.equals(row.getGbSaDepartmentId(), scope.departmentId())
                || !Objects.equals(row.getGbSaDistributerId(), scope.distributerId())) {
            throw new IllegalArgumentException("announcement store scope mismatch");
        }
        return row;
    }

    private GbAiWorkPinEntity requireOwnedPin(Long pinId, Long userId) {
        if (pinId == null) {
            throw new IllegalArgumentException("pinId required");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId required");
        }
        GbAiWorkPinEntity pin = pinMapper.selectById(pinId);
        if (pin == null || pin.getGbAiWpDeleted() != null && pin.getGbAiWpDeleted() == 1) {
            throw new StoreAnnouncementException(
                    StoreAnnouncementErrors.SOURCE_NOT_FOUND, "pin not found");
        }
        if (!Objects.equals(pin.getGbAiWpUserId(), userId)) {
            throw new IllegalArgumentException("pin does not belong to current user");
        }
        conversationCoreService.requireConversationOwnedByUser(
                pin.getGbAiWpConversationId(), userId);
        return pin;
    }

    private GbAiWorkNoteEntity requireOwnedNote(Long noteId, Long userId) {
        if (noteId == null) {
            throw new IllegalArgumentException("noteId required");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId required");
        }
        GbAiWorkNoteEntity note = noteMapper.selectById(noteId);
        if (note == null || note.getGbAiWnDeleted() != null && note.getGbAiWnDeleted() == 1) {
            throw new StoreAnnouncementException(
                    StoreAnnouncementErrors.SOURCE_NOT_FOUND, "note not found");
        }
        if (!Objects.equals(note.getGbAiWnUserId(), userId)) {
            throw new IllegalArgumentException("note does not belong to current user");
        }
        Long conversationId = note.getGbAiWnConversationId();
        if (conversationId == null) {
            conversationId = note.getGbAiWnPrimaryConversationId();
        }
        if (conversationId != null) {
            conversationCoreService.requireConversationOwnedByUser(conversationId, userId);
        }
        return note;
    }

    private static void requirePublishRequest(StoreAnnouncementPublishRequest request) {
        if (request == null || request.getUserId() == null) {
            throw new IllegalArgumentException("userId required");
        }
    }
}
