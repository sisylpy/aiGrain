package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nongxinle.ai.history.dto.AiMessagePinResponseDTO;
import com.nongxinle.ai.workspace.AiWorkspaceConstants;
import com.nongxinle.ai.workspace.AiWorkspaceDtoMaps;
import com.nongxinle.ai.workspace.AiWorkspaceParse;
import com.nongxinle.ai.workspace.AiWorkspacePinRequestMerge;
import com.nongxinle.ai.workspace.AiWorkspaceTextSupport;
import com.nongxinle.ai.workspace.dto.PromotePinToNoteRequest;
import com.nongxinle.ai.workspace.dto.WorkPinCreateRequest;
import com.nongxinle.ai.workspace.dto.WorkPinResponse;
import com.nongxinle.ai.workspace.dto.WorkNoteResponse;
import com.nongxinle.entity.GbAiMessageEntity;
import com.nongxinle.entity.GbAiWorkPinEntity;
import com.nongxinle.mapper.GbAiMessageMapper;
import com.nongxinle.mapper.GbAiWorkPinMapper;
import com.nongxinle.ai.conversation.AiConversationCoreService;
import com.nongxinle.service.GbAiWorkNoteService;
import com.nongxinle.service.GbAiWorkPinService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GbAiWorkPinServiceImpl implements GbAiWorkPinService {

    private final GbAiWorkPinMapper pinMapper;
    private final GbAiMessageMapper messageMapper;
    private final AiConversationCoreService conversationCoreService;
    private final GbAiWorkNoteService gbAiWorkNoteService;

    private static String normalizePinSourceType(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new IllegalArgumentException("sourceType required");
        }
        String u = raw.trim().toUpperCase(Locale.ROOT);
        if (!AiWorkspaceConstants.PIN_SOURCE_RUN.equals(u)
                && !AiWorkspaceConstants.PIN_SOURCE_MESSAGE.equals(u)
                && !AiWorkspaceConstants.PIN_SOURCE_SELECTION.equals(u)) {
            throw new IllegalArgumentException("invalid sourceType: " + raw);
        }
        return u;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkPinResponse createPin(WorkPinCreateRequest request) {
        if (request == null || request.getUserId() == null) {
            throw new IllegalArgumentException("userId required");
        }
        AiWorkspacePinRequestMerge.applyNestedSnapshotIfNeeded(request);
        if (request.getConversationId() == null) {
            throw new IllegalArgumentException("conversationId required");
        }
        conversationCoreService.requireConversationOwnedByUser(request.getConversationId(), request.getUserId());

        String snapshot = AiWorkspaceTextSupport.normalizeWhitespaceSnapshot(request.getSourceTextSnapshot());
        if (!StringUtils.hasText(snapshot)) {
            throw new IllegalArgumentException("sourceTextSnapshot required");
        }

        String sourceType = normalizePinSourceType(request.getSourceType());
        String preview = AiWorkspaceTextSupport.truncatePreview(request.getSourceAnswerPreview());
        if (!StringUtils.hasText(preview)) {
            preview = AiWorkspaceTextSupport.truncatePreview(snapshot);
        }

        String title = AiWorkspaceTextSupport.derivePinTitle(request.getTitle(), preview, snapshot);
        Date now = new Date();
        Date sourceCreatedAt = AiWorkspaceParse.parseOptionalSourceCreatedAt(request.getSourceCreatedAt());

        GbAiWorkPinEntity row = new GbAiWorkPinEntity();
        row.setGbAiWpUserId(request.getUserId());
        row.setGbAiWpConversationId(request.getConversationId());
        row.setGbAiWpRunId(request.getRunId());
        row.setGbAiWpMessageId(request.getMessageId());
        row.setGbAiWpTitle(title);
        row.setGbAiWpSourceType(sourceType);
        row.setGbAiWpSourceTextSnapshot(snapshot);
        row.setGbAiWpSourceAnswerPreview(preview);
        row.setGbAiWpSourceRole(trimOrNull(request.getSourceRole()));
        row.setGbAiWpSourceAgentName(trimOrNull(request.getSourceAgentName()));
        row.setGbAiWpSourceCreatedAt(sourceCreatedAt);
        row.setGbAiWpCreatedAt(now);
        row.setGbAiWpUpdatedAt(now);
        row.setGbAiWpDeleted(0);

        pinMapper.insert(row);
        return AiWorkspaceDtoMaps.toPinResponse(row, true);
    }

    private static String trimOrNull(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        return s.trim();
    }

    @Override
    public List<WorkPinResponse> listPins(Long conversationId, Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId required");
        }
        if (conversationId == null) {
            throw new IllegalArgumentException("conversationId required");
        }
        conversationCoreService.requireConversationOwnedByUser(conversationId, userId);

        LambdaQueryWrapper<GbAiWorkPinEntity> q =
                new LambdaQueryWrapper<GbAiWorkPinEntity>()
                        .eq(GbAiWorkPinEntity::getGbAiWpUserId, userId)
                        .eq(GbAiWorkPinEntity::getGbAiWpConversationId, conversationId)
                        .eq(GbAiWorkPinEntity::getGbAiWpDeleted, 0)
                        .orderByDesc(GbAiWorkPinEntity::getGbAiWpCreatedAt);

        return pinMapper.selectList(q).stream()
                .map(e -> AiWorkspaceDtoMaps.toPinResponse(e, false))
                .collect(Collectors.toList());
    }

    @Override
    public WorkPinResponse getPinDetail(Long pinId, Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId required");
        }
        GbAiWorkPinEntity row = requireOwnedPin(pinId, userId);
        return AiWorkspaceDtoMaps.toPinResponse(row, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void softDeletePin(Long pinId, Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId required");
        }
        GbAiWorkPinEntity existing = requireOwnedPin(pinId, userId);
        GbAiWorkPinEntity patch = new GbAiWorkPinEntity();
        patch.setGbAiWpId(existing.getGbAiWpId());
        patch.setGbAiWpDeleted(1);
        patch.setGbAiWpUpdatedAt(new Date());
        pinMapper.updateById(patch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkNoteResponse promotePinToNote(Long pinId, PromotePinToNoteRequest request) {
        if (request == null || request.getUserId() == null) {
            throw new IllegalArgumentException("userId required");
        }
        GbAiWorkPinEntity pin = requireOwnedPin(pinId, request.getUserId());
        return gbAiWorkNoteService.createFromPromotedPin(pin, request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiMessagePinResponseDTO pinAssistantMessage(Long userId, Long messageId) {
        if (userId == null || messageId == null) {
            throw new IllegalArgumentException("userId and messageId required");
        }
        GbAiMessageEntity msg = messageMapper.selectById(messageId);
        if (msg == null) {
            throw new IllegalArgumentException("message not found");
        }
        Long conversationId = msg.getGbAiMessageConversationId();
        conversationCoreService.requireConversationOwnedByUser(conversationId, userId);

        String role = msg.getGbAiMessageRole();
        if (role == null || !"assistant".equalsIgnoreCase(role.trim())) {
            throw new IllegalArgumentException("only assistant messages can be pinned");
        }

        GbAiWorkPinEntity dup = pinMapper.selectOne(activePinByUserAndMessage(userId, messageId));
        if (dup != null) {
            return new AiMessagePinResponseDTO(true, dup.getGbAiWpId(), true);
        }

        String raw = msg.getGbAiMessageContent() != null ? msg.getGbAiMessageContent() : "";
        String snapshot = AiWorkspaceTextSupport.normalizeWhitespaceSnapshot(raw);
        if (snapshot == null) {
            snapshot = "";
        }
        String preview = AiWorkspaceTextSupport.truncatePreview(snapshot);
        if (!StringUtils.hasText(preview)) {
            preview = AiWorkspaceTextSupport.truncatePreview(raw);
        }
        String title = AiWorkspaceTextSupport.derivePinTitle(null, preview, snapshot);

        Date now = new Date();
        GbAiWorkPinEntity row = new GbAiWorkPinEntity();
        row.setGbAiWpUserId(userId);
        row.setGbAiWpConversationId(conversationId);
        row.setGbAiWpRunId(msg.getGbAiMessageRunId());
        row.setGbAiWpMessageId(messageId);
        row.setGbAiWpTitle(title);
        row.setGbAiWpSourceType(AiWorkspaceConstants.PIN_SOURCE_MESSAGE);
        row.setGbAiWpSourceTextSnapshot(snapshot);
        row.setGbAiWpSourceAnswerPreview(preview);
        row.setGbAiWpSourceRole(trimOrNull("assistant"));
        row.setGbAiWpSourceAgentName(null);
        row.setGbAiWpSourceCreatedAt(msg.getGbAiMessageCreateTime());
        row.setGbAiWpCreatedAt(now);
        row.setGbAiWpUpdatedAt(now);
        row.setGbAiWpDeleted(0);

        pinMapper.insert(row);
        return new AiMessagePinResponseDTO(true, row.getGbAiWpId(), false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiMessagePinResponseDTO unpinAssistantMessage(Long userId, Long messageId) {
        if (userId == null || messageId == null) {
            throw new IllegalArgumentException("userId and messageId required");
        }
        GbAiMessageEntity msg = messageMapper.selectById(messageId);
        if (msg == null) {
            throw new IllegalArgumentException("message not found");
        }
        conversationCoreService.requireConversationOwnedByUser(msg.getGbAiMessageConversationId(), userId);

        GbAiWorkPinEntity existing = pinMapper.selectOne(activePinByUserAndMessage(userId, messageId));
        if (existing != null) {
            GbAiWorkPinEntity patch = new GbAiWorkPinEntity();
            patch.setGbAiWpId(existing.getGbAiWpId());
            patch.setGbAiWpDeleted(1);
            patch.setGbAiWpUpdatedAt(new Date());
            pinMapper.updateById(patch);
        }
        return new AiMessagePinResponseDTO(false, null, null);
    }

    @Override
    public Map<Long, Long> mapActivePinIdsForMessages(Long userId, Long conversationId, Collection<Long> messageIds) {
        if (userId == null || conversationId == null || messageIds == null || messageIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<GbAiWorkPinEntity> list =
                pinMapper.selectList(
                        Wrappers.<GbAiWorkPinEntity>lambdaQuery()
                                .eq(GbAiWorkPinEntity::getGbAiWpUserId, userId)
                                .eq(GbAiWorkPinEntity::getGbAiWpConversationId, conversationId)
                                .eq(GbAiWorkPinEntity::getGbAiWpDeleted, 0)
                                .in(GbAiWorkPinEntity::getGbAiWpMessageId, messageIds));
        Map<Long, Long> out = new HashMap<>();
        for (GbAiWorkPinEntity e : list) {
            Long mid = e.getGbAiWpMessageId();
            if (mid != null && !out.containsKey(mid)) {
                out.put(mid, e.getGbAiWpId());
            }
        }
        return out;
    }

    private static LambdaQueryWrapper<GbAiWorkPinEntity> activePinByUserAndMessage(Long userId, Long messageId) {
        return Wrappers.<GbAiWorkPinEntity>lambdaQuery()
                .eq(GbAiWorkPinEntity::getGbAiWpUserId, userId)
                .eq(GbAiWorkPinEntity::getGbAiWpMessageId, messageId)
                .eq(GbAiWorkPinEntity::getGbAiWpDeleted, 0)
                .last("LIMIT 1");
    }

    private GbAiWorkPinEntity requireOwnedPin(Long pinId, Long userId) {
        GbAiWorkPinEntity row = pinMapper.selectById(pinId);
        if (row == null || isSoftDeleted(row.getGbAiWpDeleted())) {
            throw new IllegalArgumentException("pin not found");
        }
        if (!Objects.equals(row.getGbAiWpUserId(), userId)) {
            throw new IllegalArgumentException("pin not owned by user");
        }
        return row;
    }

    private static boolean isSoftDeleted(Integer deletedFlag) {
        return deletedFlag != null && deletedFlag != 0;
    }
}
