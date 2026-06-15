package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nongxinle.ai.history.dto.AiMessageNoteResponseDTO;
import com.nongxinle.ai.workspace.AiWorkspaceConstants;
import com.nongxinle.ai.workspace.AiWorkspaceDtoMaps;
import com.nongxinle.ai.workspace.AiWorkspaceNoteRequestMerge;
import com.nongxinle.ai.workspace.AiWorkspaceTextSupport;
import com.nongxinle.ai.workspace.dto.PromotePinToNoteRequest;
import com.nongxinle.ai.workspace.dto.WorkNoteCreateRequest;
import com.nongxinle.ai.workspace.dto.WorkNoteMineListItemDTO;
import com.nongxinle.ai.workspace.dto.WorkNoteMineListResponseDTO;
import com.nongxinle.ai.workspace.dto.WorkNoteResponse;
import com.nongxinle.ai.workspace.dto.WorkNoteUpdateRequest;
import com.nongxinle.entity.GbAiConversationEntity;
import com.nongxinle.entity.GbAiWorkNoteEntity;
import com.nongxinle.entity.GbAiWorkPinEntity;
import com.nongxinle.entity.GbAiMessageEntity;
import com.nongxinle.mapper.GbAiConversationMapper;
import com.nongxinle.mapper.GbAiWorkNoteMapper;
import com.nongxinle.mapper.GbAiMessageMapper;
import com.nongxinle.ai.conversation.AiConversationCoreService;
import com.nongxinle.service.GbAiWorkNoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GbAiWorkNoteServiceImpl implements GbAiWorkNoteService {

    private final GbAiWorkNoteMapper noteMapper;
    private final GbAiMessageMapper messageMapper;
    private final GbAiConversationMapper conversationMapper;
    private final AiConversationCoreService conversationCoreService;

    private static boolean noteTypeRequiresSnapshot(String noteTypeUpper) {
        return AiWorkspaceConstants.NOTE_FROM_RUN.equals(noteTypeUpper)
                || AiWorkspaceConstants.NOTE_FROM_PIN.equals(noteTypeUpper)
                || AiWorkspaceConstants.NOTE_FROM_SELECTION.equals(noteTypeUpper)
                || AiWorkspaceConstants.NOTE_FROM_MESSAGE.equals(noteTypeUpper);
    }

    private static String normalizeNoteType(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new IllegalArgumentException("noteType required");
        }
        String u = raw.trim().toUpperCase(Locale.ROOT);
        if (!AiWorkspaceConstants.NOTE_MANUAL.equals(u)
                && !AiWorkspaceConstants.NOTE_FROM_RUN.equals(u)
                && !AiWorkspaceConstants.NOTE_FROM_PIN.equals(u)
                && !AiWorkspaceConstants.NOTE_FROM_SELECTION.equals(u)
                && !AiWorkspaceConstants.NOTE_FROM_MESSAGE.equals(u)) {
            throw new IllegalArgumentException("invalid noteType: " + raw);
        }
        return u;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkNoteResponse createNote(WorkNoteCreateRequest request) {
        if (request == null || request.getUserId() == null) {
            throw new IllegalArgumentException("userId required");
        }
        AiWorkspaceNoteRequestMerge.applyPrimarySourceIfNeeded(request);
        if (!StringUtils.hasText(request.getTitle())) {
            throw new IllegalArgumentException("title required");
        }

        String noteType = normalizeNoteType(request.getNoteType());
        String content = request.getContent() == null ? "" : request.getContent();

        Long conversationId = request.getConversationId();
        if (conversationId != null) {
            conversationCoreService.requireConversationOwnedByUser(conversationId, request.getUserId());
        } else if (!AiWorkspaceConstants.NOTE_MANUAL.equals(noteType)) {
            throw new IllegalArgumentException("conversationId required for sourced note");
        }

        String snapshot = AiWorkspaceTextSupport.normalizeWhitespaceSnapshot(request.getSourceTextSnapshot());
        if (noteTypeRequiresSnapshot(noteType)) {
            if (!StringUtils.hasText(snapshot)) {
                throw new IllegalArgumentException("sourceTextSnapshot required for noteType=" + noteType);
            }
        }

        String preview = AiWorkspaceTextSupport.truncatePreview(request.getSourceAnswerPreview());
        if (!StringUtils.hasText(preview) && StringUtils.hasText(snapshot)) {
            preview = AiWorkspaceTextSupport.truncatePreview(snapshot);
        }

        Long pc = request.getPrimaryConversationId() != null ? request.getPrimaryConversationId() : conversationId;
        Long pr = request.getPrimaryRunId();
        Long pm = request.getPrimaryMessageId();

        String pst = null;
        if (StringUtils.hasText(request.getPrimarySourceType())) {
            pst = request.getPrimarySourceType().trim().toUpperCase(Locale.ROOT);
        } else if (noteTypeRequiresSnapshot(noteType)) {
            pst = noteType;
        }

        Date now = new Date();
        GbAiWorkNoteEntity row = new GbAiWorkNoteEntity();
        row.setGbAiWnUserId(request.getUserId());
        row.setGbAiWnConversationId(conversationId);
        row.setGbAiWnTitle(request.getTitle().trim());
        row.setGbAiWnContentMd(content);
        row.setGbAiWnNoteType(noteType);
        row.setGbAiWnPrimarySourceType(pst);
        row.setGbAiWnPrimaryConversationId(pc);
        row.setGbAiWnPrimaryRunId(pr);
        row.setGbAiWnPrimaryMessageId(pm);
        row.setGbAiWnSourceTextSnapshot(StringUtils.hasText(snapshot) ? snapshot : null);
        row.setGbAiWnSourceAnswerPreview(preview);
        row.setGbAiWnCreatedAt(now);
        row.setGbAiWnUpdatedAt(now);
        row.setGbAiWnDeleted(0);

        noteMapper.insert(row);
        return AiWorkspaceDtoMaps.toNoteResponse(row, true);
    }

    @Override
    public List<WorkNoteResponse> listNotes(Long conversationId, Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId required");
        }
        if (conversationId == null) {
            throw new IllegalArgumentException("conversationId required");
        }
        conversationCoreService.requireConversationOwnedByUser(conversationId, userId);

        LambdaQueryWrapper<GbAiWorkNoteEntity> q =
                new LambdaQueryWrapper<GbAiWorkNoteEntity>()
                        .eq(GbAiWorkNoteEntity::getGbAiWnUserId, userId)
                        .eq(GbAiWorkNoteEntity::getGbAiWnConversationId, conversationId)
                        .eq(GbAiWorkNoteEntity::getGbAiWnDeleted, 0)
                        .orderByDesc(GbAiWorkNoteEntity::getGbAiWnUpdatedAt);

        return noteMapper.selectList(q).stream()
                .map(e -> AiWorkspaceDtoMaps.toNoteResponse(e, false))
                .collect(Collectors.toList());
    }

    @Override
    public WorkNoteMineListResponseDTO listMyNotes(
            Long userId,
            Long conversationId,
            String noteType,
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

        LambdaQueryWrapper<GbAiWorkNoteEntity> q =
                Wrappers.<GbAiWorkNoteEntity>lambdaQuery()
                        .eq(GbAiWorkNoteEntity::getGbAiWnUserId, userId)
                        .eq(GbAiWorkNoteEntity::getGbAiWnDeleted, 0);
        if (conversationId != null) {
            q.eq(GbAiWorkNoteEntity::getGbAiWnConversationId, conversationId);
        }
        if (StringUtils.hasText(noteType)) {
            q.eq(GbAiWorkNoteEntity::getGbAiWnNoteType, normalizeNoteType(noteType));
        }

        long total = noteMapper.selectCount(q);
        int offset = (effPage - 1) * effSize;
        q.orderByDesc(GbAiWorkNoteEntity::getGbAiWnUpdatedAt).last("LIMIT " + offset + "," + effSize);

        List<GbAiWorkNoteEntity> rows = noteMapper.selectList(q);
        Map<Long, String> conversationTitles = loadConversationTitles(rows);
        Map<Long, Boolean> hasCardsByMessageId = loadHasCardsByMessageId(rows);

        List<WorkNoteMineListItemDTO> items =
                rows.stream()
                        .map(
                                row ->
                                        WorkNoteMineListItemDTO.builder()
                                                .noteId(row.getGbAiWnId())
                                                .conversationId(row.getGbAiWnConversationId())
                                                .conversationTitle(
                                                        resolveConversationTitle(
                                                                row.getGbAiWnConversationId(),
                                                                conversationTitles))
                                                .title(row.getGbAiWnTitle())
                                                .noteType(row.getGbAiWnNoteType())
                                                .messageId(row.getGbAiWnPrimaryMessageId())
                                                .runId(row.getGbAiWnPrimaryRunId())
                                                .preview(resolveListPreview(row))
                                                .hasCards(
                                                        resolveHasCards(
                                                                row.getGbAiWnPrimaryMessageId(),
                                                                hasCardsByMessageId))
                                                .updatedAt(row.getGbAiWnUpdatedAt())
                                                .build())
                        .collect(Collectors.toList());

        return new WorkNoteMineListResponseDTO(total, effPage, effSize, items);
    }

    private Map<Long, String> loadConversationTitles(List<GbAiWorkNoteEntity> rows) {
        Set<Long> conversationIds = new HashSet<>();
        for (GbAiWorkNoteEntity row : rows) {
            if (row.getGbAiWnConversationId() != null) {
                conversationIds.add(row.getGbAiWnConversationId());
            }
        }
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
                if (StringUtils.hasText(conv.getGbAiConversationTitle())) {
                    out.put(conv.getGbAiConversationId(), conv.getGbAiConversationTitle().trim());
                }
            }
        }
        return out;
    }

    private static String resolveConversationTitle(
            Long conversationId, Map<Long, String> conversationTitles) {
        if (conversationId == null) {
            return null;
        }
        String title = conversationTitles.get(conversationId);
        if (!StringUtils.hasText(title)) {
            return AiWorkspaceConstants.CONVERSATION_DELETED_TITLE;
        }
        return title;
    }

    private Map<Long, Boolean> loadHasCardsByMessageId(List<GbAiWorkNoteEntity> rows) {
        Set<Long> messageIds = new HashSet<>();
        for (GbAiWorkNoteEntity row : rows) {
            if (row.getGbAiWnPrimaryMessageId() != null) {
                messageIds.add(row.getGbAiWnPrimaryMessageId());
            }
        }
        if (messageIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<GbAiMessageEntity> messages = messageMapper.selectBatchIds(messageIds);
        Map<Long, Boolean> out = new HashMap<>();
        if (messages == null) {
            return out;
        }
        for (GbAiMessageEntity message : messages) {
            if (message == null || message.getGbAiMessageId() == null) {
                continue;
            }
            out.put(message.getGbAiMessageId(), hasNonEmptyCardsJson(message.getGbAiMessageCardsJson()));
        }
        return out;
    }

    private static Boolean resolveHasCards(Long messageId, Map<Long, Boolean> hasCardsByMessageId) {
        if (messageId == null) {
            return false;
        }
        return Boolean.TRUE.equals(hasCardsByMessageId.get(messageId));
    }

    private static boolean hasNonEmptyCardsJson(String cardsJson) {
        if (!StringUtils.hasText(cardsJson)) {
            return false;
        }
        String trimmed = cardsJson.trim();
        return !"[]".equals(trimmed) && !"null".equalsIgnoreCase(trimmed);
    }

    private static String resolveListPreview(GbAiWorkNoteEntity row) {
        if (StringUtils.hasText(row.getGbAiWnSourceAnswerPreview())) {
            return row.getGbAiWnSourceAnswerPreview().trim();
        }
        if (StringUtils.hasText(row.getGbAiWnContentMd())) {
            return AiWorkspaceTextSupport.truncatePreview(row.getGbAiWnContentMd());
        }
        return AiWorkspaceTextSupport.truncatePreview(row.getGbAiWnSourceTextSnapshot());
    }

    @Override
    public WorkNoteResponse getNoteDetail(Long noteId, Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId required");
        }
        GbAiWorkNoteEntity row = requireOwnedNote(noteId, userId);
        return AiWorkspaceDtoMaps.toNoteResponse(row, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkNoteResponse updateNote(Long noteId, WorkNoteUpdateRequest request) {
        if (request == null || request.getUserId() == null) {
            throw new IllegalArgumentException("userId required");
        }
        GbAiWorkNoteEntity row = requireOwnedNote(noteId, request.getUserId());

        boolean touched = false;
        if (request.getTitle() != null) {
            if (!StringUtils.hasText(request.getTitle())) {
                throw new IllegalArgumentException("title cannot be blank");
            }
            row.setGbAiWnTitle(request.getTitle().trim());
            touched = true;
        }
        if (request.getContent() != null) {
            row.setGbAiWnContentMd(request.getContent());
            touched = true;
        }
        if (!touched) {
            throw new IllegalArgumentException("title or content required for update");
        }
        row.setGbAiWnUpdatedAt(new Date());
        noteMapper.updateById(row);
        return AiWorkspaceDtoMaps.toNoteResponse(row, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void softDeleteNote(Long noteId, Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId required");
        }
        GbAiWorkNoteEntity existing = requireOwnedNote(noteId, userId);
        GbAiWorkNoteEntity patch = new GbAiWorkNoteEntity();
        patch.setGbAiWnId(existing.getGbAiWnId());
        patch.setGbAiWnDeleted(1);
        patch.setGbAiWnUpdatedAt(new Date());
        noteMapper.updateById(patch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkNoteResponse createFromPromotedPin(GbAiWorkPinEntity pin, PromotePinToNoteRequest request) {
        if (pin == null) {
            throw new IllegalArgumentException("pin required");
        }
        if (request == null || request.getUserId() == null) {
            throw new IllegalArgumentException("userId required");
        }
        if (!Objects.equals(pin.getGbAiWpUserId(), request.getUserId())) {
            throw new IllegalArgumentException("pin not owned by user");
        }
        if (isSoftDeleted(pin.getGbAiWpDeleted())) {
            throw new IllegalArgumentException("pin deleted");
        }

        conversationCoreService.requireConversationOwnedByUser(pin.getGbAiWpConversationId(), request.getUserId());

        String snapshot = pin.getGbAiWpSourceTextSnapshot();
        if (!StringUtils.hasText(snapshot)) {
            throw new IllegalArgumentException("pin snapshot missing");
        }

        String preview = AiWorkspaceTextSupport.truncatePreview(pin.getGbAiWpSourceAnswerPreview());
        if (!StringUtils.hasText(preview)) {
            preview = AiWorkspaceTextSupport.truncatePreview(snapshot);
        }

        String title;
        if (StringUtils.hasText(request.getTitle())) {
            title = request.getTitle().trim();
        } else if (StringUtils.hasText(pin.getGbAiWpTitle())) {
            title = pin.getGbAiWpTitle();
        } else {
            title = AiWorkspaceTextSupport.derivePinTitle(null, pin.getGbAiWpSourceAnswerPreview(), snapshot);
        }

        String content = request.getContent() != null ? request.getContent() : snapshot;

        Date now = new Date();
        GbAiWorkNoteEntity row = new GbAiWorkNoteEntity();
        row.setGbAiWnUserId(pin.getGbAiWpUserId());
        row.setGbAiWnConversationId(pin.getGbAiWpConversationId());
        row.setGbAiWnTitle(title);
        row.setGbAiWnContentMd(content);
        row.setGbAiWnNoteType(AiWorkspaceConstants.NOTE_FROM_PIN);
        row.setGbAiWnPrimarySourceType(AiWorkspaceConstants.NOTE_FROM_PIN);
        row.setGbAiWnPrimaryConversationId(pin.getGbAiWpConversationId());
        row.setGbAiWnPrimaryRunId(pin.getGbAiWpRunId());
        row.setGbAiWnPrimaryMessageId(pin.getGbAiWpMessageId());
        row.setGbAiWnSourceTextSnapshot(snapshot);
        row.setGbAiWnSourceAnswerPreview(preview);
        row.setGbAiWnCreatedAt(now);
        row.setGbAiWnUpdatedAt(now);
        row.setGbAiWnDeleted(0);

        noteMapper.insert(row);
        return AiWorkspaceDtoMaps.toNoteResponse(row, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiMessageNoteResponseDTO saveNoteFromAssistantMessage(Long userId, Long messageId) {
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
            throw new IllegalArgumentException("only assistant messages can be saved as notes");
        }

        GbAiWorkNoteEntity dup =
                noteMapper.selectOne(activeNoteByUserAndPrimaryMessage(userId, messageId));
        if (dup != null) {
            return new AiMessageNoteResponseDTO(true, dup.getGbAiWnId(), true);
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
        String title = AiWorkspaceTextSupport.deriveMessageNoteTitle(snapshot);

        Date now = new Date();
        GbAiWorkNoteEntity row = new GbAiWorkNoteEntity();
        row.setGbAiWnUserId(userId);
        row.setGbAiWnConversationId(conversationId);
        row.setGbAiWnTitle(title);
        row.setGbAiWnContentMd(snapshot);
        row.setGbAiWnNoteType(AiWorkspaceConstants.NOTE_FROM_MESSAGE);
        row.setGbAiWnPrimarySourceType(AiWorkspaceConstants.PIN_SOURCE_MESSAGE);
        row.setGbAiWnPrimaryConversationId(conversationId);
        row.setGbAiWnPrimaryRunId(msg.getGbAiMessageRunId());
        row.setGbAiWnPrimaryMessageId(messageId);
        row.setGbAiWnSourceTextSnapshot(snapshot);
        row.setGbAiWnSourceAnswerPreview(preview);
        row.setGbAiWnCreatedAt(now);
        row.setGbAiWnUpdatedAt(now);
        row.setGbAiWnDeleted(0);

        noteMapper.insert(row);
        return new AiMessageNoteResponseDTO(true, row.getGbAiWnId(), false);
    }

    @Override
    public Map<Long, Long> mapActiveNoteIdsForMessages(Long userId, Long conversationId, Collection<Long> messageIds) {
        if (userId == null || conversationId == null || messageIds == null || messageIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<GbAiWorkNoteEntity> list =
                noteMapper.selectList(
                        Wrappers.<GbAiWorkNoteEntity>lambdaQuery()
                                .eq(GbAiWorkNoteEntity::getGbAiWnUserId, userId)
                                .eq(GbAiWorkNoteEntity::getGbAiWnConversationId, conversationId)
                                .eq(GbAiWorkNoteEntity::getGbAiWnDeleted, 0)
                                .in(GbAiWorkNoteEntity::getGbAiWnPrimaryMessageId, messageIds));
        Map<Long, Long> out = new HashMap<>();
        for (GbAiWorkNoteEntity e : list) {
            Long mid = e.getGbAiWnPrimaryMessageId();
            if (mid != null && !out.containsKey(mid)) {
                out.put(mid, e.getGbAiWnId());
            }
        }
        return out;
    }

    private static LambdaQueryWrapper<GbAiWorkNoteEntity> activeNoteByUserAndPrimaryMessage(Long userId, Long messageId) {
        return Wrappers.<GbAiWorkNoteEntity>lambdaQuery()
                .eq(GbAiWorkNoteEntity::getGbAiWnUserId, userId)
                .eq(GbAiWorkNoteEntity::getGbAiWnPrimaryMessageId, messageId)
                .eq(GbAiWorkNoteEntity::getGbAiWnDeleted, 0)
                .last("LIMIT 1");
    }

    private GbAiWorkNoteEntity requireOwnedNote(Long noteId, Long userId) {
        GbAiWorkNoteEntity row = noteMapper.selectById(noteId);
        if (row == null || isSoftDeleted(row.getGbAiWnDeleted())) {
            throw new IllegalArgumentException("note not found");
        }
        if (!Objects.equals(row.getGbAiWnUserId(), userId)) {
            throw new IllegalArgumentException("note not owned by user");
        }
        return row;
    }

    private static boolean isSoftDeleted(Integer deletedFlag) {
        return deletedFlag != null && deletedFlag != 0;
    }
}
