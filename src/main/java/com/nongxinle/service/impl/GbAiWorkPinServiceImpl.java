package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nongxinle.ai.history.dto.AiMessagePinResponseDTO;
import com.nongxinle.ai.workspace.AiWorkspaceCardSupport;
import com.nongxinle.ai.workspace.AiWorkspaceConstants;
import com.nongxinle.ai.workspace.AiWorkspaceDtoMaps;
import com.nongxinle.ai.workspace.AiWorkspaceParse;
import com.nongxinle.ai.workspace.AiWorkspacePinRequestMerge;
import com.nongxinle.ai.workspace.AiWorkspaceTextSupport;
import com.nongxinle.ai.workspace.dto.PromotePinToNoteRequest;
import com.nongxinle.ai.workspace.dto.WorkPinCreateRequest;
import com.nongxinle.ai.workspace.dto.WorkPinMineListItemDTO;
import com.nongxinle.ai.workspace.dto.WorkPinMineListResponseDTO;
import com.nongxinle.ai.workspace.dto.WorkPinResponse;
import com.nongxinle.ai.workspace.dto.WorkNoteResponse;
import com.nongxinle.entity.GbAiConversationEntity;
import com.nongxinle.entity.GbAiMessageEntity;
import com.nongxinle.entity.GbAiWorkPinEntity;
import com.nongxinle.mapper.GbAiConversationMapper;
import com.nongxinle.mapper.GbAiMessageMapper;
import com.nongxinle.mapper.GbAiWorkPinMapper;
import com.nongxinle.ai.conversation.AiConversationCoreService;
import com.nongxinle.ai.platform.AiCardPayloadWireSupport;
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
    private final GbAiConversationMapper conversationMapper;
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

        // 从关联消息读取 cards[]（如果有 messageId），后端可信源
        List<Map<String, Object>> cards = loadCardsFromMessage(request.getMessageId());

        String sourceType = normalizePinSourceType(request.getSourceType());
        String preview = AiWorkspaceTextSupport.truncatePreview(request.getSourceAnswerPreview());
        if (!StringUtils.hasText(preview)) {
            preview = AiWorkspaceCardSupport.derivePinPreview(cards, snapshot);
            if (!StringUtils.hasText(preview)) {
                preview = AiWorkspaceTextSupport.truncatePreview(snapshot);
            }
        }

        String title = request.getTitle();
        if (!StringUtils.hasText(title)) {
            title = AiWorkspaceCardSupport.derivePinTitleFromCards(cards, null, snapshot);
        }
        if (!StringUtils.hasText(title)) {
            title = AiWorkspaceTextSupport.derivePinTitle(null, preview, snapshot);
        }

        Date now = new Date();
        Date sourceCreatedAt = AiWorkspaceParse.parseOptionalSourceCreatedAt(request.getSourceCreatedAt());

        String cardsSnapshotJson = AiWorkspaceCardSupport.cardsToJson(cards);
        String primaryCardType = AiWorkspaceCardSupport.extractPrimaryCardType(cards);
        int cardCount = AiWorkspaceCardSupport.countCards(cards);

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
        row.setGbAiWpCardsSnapshotJson(cardsSnapshotJson);
        row.setGbAiWpPrimaryCardType(primaryCardType);
        row.setGbAiWpCardCount(cardCount);
        row.setGbAiWpCreatedAt(now);
        row.setGbAiWpUpdatedAt(now);
        row.setGbAiWpDeleted(0);

        pinMapper.insert(row);
        return AiWorkspaceDtoMaps.toPinResponse(row, true);
    }

    private List<Map<String, Object>> loadCardsFromMessage(Long messageId) {
        if (messageId == null) {
            return null;
        }
        GbAiMessageEntity msg = messageMapper.selectById(messageId);
        if (msg == null) {
            return null;
        }
        return AiCardPayloadWireSupport.parseCardsFromPersistence(msg.getGbAiMessageCardsJson());
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
    public WorkPinMineListResponseDTO listMyPins(
            Long userId,
            Long conversationId,
            String sourceType,
            Integer page,
            Integer pageSize) {
        if (userId == null) {
            throw new IllegalArgumentException("userId required");
        }

        int effPage = page == null || page < 1 ? 1 : page;
        int effSize =
                pageSize == null || pageSize < 1
                        ? AiWorkspaceConstants.MINE_LIST_DEFAULT_PAGE_SIZE
                        : Math.min(pageSize, AiWorkspaceConstants.MINE_LIST_MAX_PAGE_SIZE);

        LambdaQueryWrapper<GbAiWorkPinEntity> q =
                Wrappers.<GbAiWorkPinEntity>lambdaQuery()
                        .eq(GbAiWorkPinEntity::getGbAiWpUserId, userId)
                        .eq(GbAiWorkPinEntity::getGbAiWpDeleted, 0);
        if (conversationId != null) {
            q.eq(GbAiWorkPinEntity::getGbAiWpConversationId, conversationId);
        }
        if (StringUtils.hasText(sourceType)) {
            q.eq(GbAiWorkPinEntity::getGbAiWpSourceType, normalizePinSourceType(sourceType));
        }

        long total = pinMapper.selectCount(q);
        int offset = (effPage - 1) * effSize;
        q.orderByDesc(GbAiWorkPinEntity::getGbAiWpCreatedAt).last("LIMIT " + offset + "," + effSize);

        List<GbAiWorkPinEntity> rows = pinMapper.selectList(q);
        Map<Long, String> conversationTitles = loadConversationTitles(rows);

        List<WorkPinMineListItemDTO> items =
                rows.stream()
                        .map(
                                row ->
                                        WorkPinMineListItemDTO.builder()
                                                .pinId(row.getGbAiWpId())
                                                .conversationId(row.getGbAiWpConversationId())
                                                .conversationTitle(
                                                        resolveConversationTitle(
                                                                row.getGbAiWpConversationId(),
                                                                conversationTitles))
                                                .messageId(row.getGbAiWpMessageId())
                                                .runId(row.getGbAiWpRunId())
                                                .sourceType(row.getGbAiWpSourceType())
                                                .title(row.getGbAiWpTitle())
                                                .preview(resolveListPreview(row))
                                                .primaryCardType(row.getGbAiWpPrimaryCardType())
                                                .cardCount(row.getGbAiWpCardCount() != null ? row.getGbAiWpCardCount() : 0)
                                                .hasCards(resolveHasCardsFromEntity(row))
                                                .createdAt(row.getGbAiWpCreatedAt())
                                                .build())
                        .collect(Collectors.toList());

        return new WorkPinMineListResponseDTO(total, effPage, effSize, items);
    }

    private Map<Long, String> loadConversationTitles(List<GbAiWorkPinEntity> rows) {
        Set<Long> conversationIds =
                rows.stream()
                        .map(GbAiWorkPinEntity::getGbAiWpConversationId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
        if (conversationIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<GbAiConversationEntity> conversations =
                conversationMapper.selectBatchIds(conversationIds);
        Map<Long, String> out = new HashMap<>();
        if (conversations != null) {
            for (GbAiConversationEntity conv : conversations) {
                if (conv == null || conv.getGbAiConversationId() == null) {
                    continue;
                }
                out.put(conv.getGbAiConversationId(), resolveConversationTitleText(conv));
            }
        }
        return out;
    }

    private static String resolveConversationTitle(
            Long conversationId, Map<Long, String> conversationTitles) {
        if (conversationId == null) {
            return AiWorkspaceConstants.CONVERSATION_DELETED_TITLE;
        }
        String title = conversationTitles.get(conversationId);
        if (!StringUtils.hasText(title)) {
            return AiWorkspaceConstants.CONVERSATION_DELETED_TITLE;
        }
        return title;
    }

    private static String resolveConversationTitleText(GbAiConversationEntity conv) {
        if (conv == null || !StringUtils.hasText(conv.getGbAiConversationTitle())) {
            return null;
        }
        return conv.getGbAiConversationTitle().trim();
    }

    /** 仅基于 Pin 自身 cardCount / cardsSnapshotJson 判断是否有业务卡，不回查原消息。 */
    private static Boolean resolveHasCardsFromEntity(GbAiWorkPinEntity row) {
        if (row == null) {
            return false;
        }
        if (row.getGbAiWpCardCount() != null && row.getGbAiWpCardCount() > 0) {
            return true;
        }
        return StringUtils.hasText(row.getGbAiWpCardsSnapshotJson());
    }

    private static String resolveListPreview(GbAiWorkPinEntity row) {
        if (StringUtils.hasText(row.getGbAiWpSourceAnswerPreview())) {
            return row.getGbAiWpSourceAnswerPreview().trim();
        }
        return null;
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

        // 从消息读取 cards[] 快照（后端可信源，不接受前端传入）
        List<Map<String, Object>> cards =
                AiCardPayloadWireSupport.parseCardsFromPersistence(msg.getGbAiMessageCardsJson());

        // sourceTextSnapshot = 完整正文快照，不改写、不截断
        String raw = msg.getGbAiMessageContent() != null ? msg.getGbAiMessageContent() : "";
        String snapshot = AiWorkspaceTextSupport.normalizeWhitespaceSnapshot(raw);
        if (snapshot == null) {
            snapshot = "";
        }

        // title：卡片 > 正文短截断
        String title = AiWorkspaceCardSupport.derivePinTitleFromCards(cards, null, snapshot);
        if (title == null) {
            title = AiWorkspaceTextSupport.derivePinTitle(null, snapshot, snapshot);
        }

        // preview：卡片结构化事实摘要 > 正文短截断
        String preview = AiWorkspaceCardSupport.derivePinPreview(cards, snapshot);
        if (!StringUtils.hasText(preview)) {
            preview = AiWorkspaceTextSupport.truncatePreview(snapshot);
        }

        // 卡片快照
        String cardsSnapshotJson = AiWorkspaceCardSupport.cardsToJson(cards);
        String primaryCardType = AiWorkspaceCardSupport.extractPrimaryCardType(cards);
        int cardCount = AiWorkspaceCardSupport.countCards(cards);

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
        row.setGbAiWpCardsSnapshotJson(cardsSnapshotJson);
        row.setGbAiWpPrimaryCardType(primaryCardType);
        row.setGbAiWpCardCount(cardCount);
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
        if (row == null || isSoftDeleted(row.getGbAiWpDeleted()) || !Objects.equals(row.getGbAiWpUserId(), userId)) {
            throw new IllegalArgumentException("pin not found");
        }
        return row;
    }

    private static boolean isSoftDeleted(Integer deletedFlag) {
        return deletedFlag != null && deletedFlag != 0;
    }
}
