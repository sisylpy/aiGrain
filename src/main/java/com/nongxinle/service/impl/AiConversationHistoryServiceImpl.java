package com.nongxinle.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nongxinle.ai.history.AiConversationHistoryService;
import com.nongxinle.ai.history.dto.*;
import com.nongxinle.ai.composer.AiAnswerContextSummarySupport;
import com.nongxinle.ai.platform.AiCardPayloadWireSupport;
import com.nongxinle.ai.trace.AiRunSessionRegistry;
import com.nongxinle.entity.*;
import com.nongxinle.mapper.*;
import com.nongxinle.ai.conversation.AiConversationCoreService;
import com.nongxinle.service.GbAiWorkNoteService;
import com.nongxinle.service.GbAiWorkPinService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLIntegrityConstraintViolationException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiConversationHistoryServiceImpl implements AiConversationHistoryService {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final GbAiConversationHistoryMapper conversationHistoryMapper;
    private final GbAiConversationMapper conversationMapper;
    private final GbAiConversationPinMapper conversationPinMapper;
    private final GbAiConversationTagMapper conversationTagMapper;
    private final GbAiTagMapper tagMapper;
    private final GbAiConversationNotebookMapper conversationNotebookMapper;
    private final GbAiNotebookMapper notebookMapper;
    private final GbAiWorkNoteMapper workNoteMapper;
    private final GbAiAgentRunMapper agentRunMapper;
    private final AiConversationCoreService conversationCoreService;
    private final GbAiWorkPinService gbAiWorkPinService;
    private final GbAiWorkNoteService gbAiWorkNoteService;
    private final AiRunSessionRegistry runSessionRegistry;

    @Override
    public AiConversationListResponseDTO listConversations(Long userId,
                                                           Long departmentId,
                                                           Long distributerId,
                                                           String keyword,
                                                           String status,
                                                           boolean includeArchived,
                                                           Long tagId,
                                                           Long notebookId,
                                                           Boolean pinnedOnly,
                                                           int page,
                                                           int pageSize) {
        if (userId == null) {
            throw new IllegalArgumentException("userId required");
        }
        int p = Math.max(1, page);
        int ps = Math.min(100, Math.max(1, pageSize));
        int offset = (p - 1) * ps;
        String kw = StrUtil.trimToNull(keyword);
        String lastRunStatus = normalizeStatusFilter(status);

        long total = conversationHistoryMapper.countConversationList(
                userId, departmentId, distributerId, kw, lastRunStatus,
                includeArchived, tagId, notebookId, pinnedOnly);
        if (total == 0) {
            return new AiConversationListResponseDTO(0, p, ps, new ArrayList<>());
        }

        List<Long> ids = conversationHistoryMapper.selectConversationIds(
                userId, departmentId, distributerId, kw, lastRunStatus,
                includeArchived, tagId, notebookId, pinnedOnly, offset, ps);
        if (ids.isEmpty()) {
            return new AiConversationListResponseDTO(total, p, ps, new ArrayList<>());
        }

        Collection<GbAiConversationEntity> convRows = conversationMapper.selectBatchIds(ids);
        Map<Long, GbAiConversationEntity> convById = convRows.stream()
                .collect(Collectors.toMap(GbAiConversationEntity::getGbAiConversationId, Function.identity(), (a, b) -> a));
        List<GbAiConversationEntity> ordered = ids.stream().map(convById::get).filter(Objects::nonNull).toList();

        Set<Long> pinnedIds = loadPinnedConversationIds(userId, ids);

        List<GbAiConversationTagEntity> links = conversationTagMapper.selectList(
                Wrappers.<GbAiConversationTagEntity>lambdaQuery()
                        .eq(GbAiConversationTagEntity::getGbAiCtUserId, userId)
                        .in(GbAiConversationTagEntity::getGbAiCtConversationId, ids));
        Set<Long> allTagIds = links.stream().map(GbAiConversationTagEntity::getGbAiCtTagId).collect(Collectors.toSet());
        Map<Long, GbAiTagEntity> tagById = allTagIds.isEmpty() ? Map.of() :
                tagMapper.selectBatchIds(allTagIds).stream()
                        .collect(Collectors.toMap(GbAiTagEntity::getGbAiTagId, Function.identity(), (a, b) -> a));

        Map<Long, List<AiConversationTagBriefDTO>> tagsByConv = new LinkedHashMap<>();
        for (GbAiConversationTagEntity ct : links) {
            GbAiConversationEntity convRow = convById.get(ct.getGbAiCtConversationId());
            if (convRow == null) {
                continue;
            }
            GbAiTagEntity te = tagById.get(ct.getGbAiCtTagId());
            if (te == null) {
                continue;
            }
            if (!tenantAnchorsMatch(convRow, te.getGbAiTagAnchorDistributerId(), te.getGbAiTagAnchorDepartmentId())) {
                continue;
            }
            tagsByConv
                    .computeIfAbsent(ct.getGbAiCtConversationId(), k -> new ArrayList<>())
                    .add(new AiConversationTagBriefDTO(te.getGbAiTagId(), te.getGbAiTagName(), te.getGbAiTagColor()));
        }

        List<GbAiConversationNotebookEntity> nbLinks = conversationNotebookMapper.selectList(
                Wrappers.<GbAiConversationNotebookEntity>lambdaQuery()
                        .eq(GbAiConversationNotebookEntity::getGbAiCnbUserId, userId)
                        .in(GbAiConversationNotebookEntity::getGbAiCnbConversationId, ids));
        Set<Long> allNbIds = nbLinks.stream().map(GbAiConversationNotebookEntity::getGbAiCnbNotebookId).collect(Collectors.toSet());
        Map<Long, GbAiNotebookEntity> nbById = allNbIds.isEmpty() ? Map.of() :
                notebookMapper.selectBatchIds(allNbIds).stream()
                        .collect(Collectors.toMap(GbAiNotebookEntity::getGbAiNbId, Function.identity(), (a, b) -> a));

        Map<Long, List<AiConversationNotebookBriefDTO>> nbByConv = new LinkedHashMap<>();
        for (GbAiConversationNotebookEntity ln : nbLinks) {
            GbAiConversationEntity convRow = convById.get(ln.getGbAiCnbConversationId());
            if (convRow == null) {
                continue;
            }
            GbAiNotebookEntity nb = nbById.get(ln.getGbAiCnbNotebookId());
            if (nb == null) {
                continue;
            }
            if (!tenantAnchorsMatch(convRow, nb.getGbAiNbAnchorDistributerId(), nb.getGbAiNbAnchorDepartmentId())) {
                continue;
            }
            nbByConv
                    .computeIfAbsent(ln.getGbAiCnbConversationId(), k -> new ArrayList<>())
                    .add(new AiConversationNotebookBriefDTO(nb.getGbAiNbId(), nb.getGbAiNbName()));
        }

        List<GbAiWorkNoteEntity> notes = workNoteMapper.selectList(
                Wrappers.<GbAiWorkNoteEntity>lambdaQuery()
                        .eq(GbAiWorkNoteEntity::getGbAiWnUserId, userId)
                        .eq(GbAiWorkNoteEntity::getGbAiWnDeleted, 0)
                        .in(GbAiWorkNoteEntity::getGbAiWnConversationId, ids));
        Map<Long, List<GbAiWorkNoteEntity>> notesByConv = notes.stream()
                .collect(Collectors.groupingBy(GbAiWorkNoteEntity::getGbAiWnConversationId));

        List<GbAiMessageEntity> firstUsers = conversationHistoryMapper.selectFirstUserMessagePerConversation(ids);
        List<GbAiMessageEntity> lastAssistants = conversationHistoryMapper.selectLatestAssistantMessagePerConversation(ids);
        Map<Long, GbAiMessageEntity> firstUserMap = firstUsers.stream()
                .collect(Collectors.toMap(GbAiMessageEntity::getGbAiMessageConversationId, Function.identity(), (a, b) -> a));
        Map<Long, GbAiMessageEntity> lastAssistantMap = lastAssistants.stream()
                .collect(Collectors.toMap(GbAiMessageEntity::getGbAiMessageConversationId, Function.identity(), (a, b) -> a));

        Set<Long> runIds = new HashSet<>();
        for (GbAiConversationEntity c : ordered) {
            if (c.getGbAiConversationLastRunId() != null) {
                runIds.add(c.getGbAiConversationLastRunId());
            }
        }
        Map<Long, GbAiAgentRunEntity> runById = runIds.isEmpty() ? Map.of() :
                agentRunMapper.selectBatchIds(runIds).stream()
                        .collect(Collectors.toMap(GbAiAgentRunEntity::getId, Function.identity(), (a, b) -> a));

        List<AiConversationListItemDTO> items = new ArrayList<>(ordered.size());
        for (GbAiConversationEntity c : ordered) {
            Long cid = c.getGbAiConversationId();
            GbAiMessageEntity fu = firstUserMap.get(cid);
            GbAiMessageEntity la = lastAssistantMap.get(cid);
            AiConversationListItemDTO dto = new AiConversationListItemDTO();
            dto.setConversationId(cid);
            dto.setTitle(resolveTitle(c, fu));
            dto.setConversationStatus(c.getGbAiConversationStatus());
            dto.setArchived(isArchived(c));
            dto.setDepartmentId(c.getGbAiConversationDepartmentId());
            dto.setDistributerId(c.getGbAiConversationDistributerId());
            dto.setScopeMode(c.getGbAiConversationScopeMode());
            dto.setPinned(pinnedIds.contains(cid));
            dto.setUpdatedAt(fmt(c.getGbAiConversationUpdateTime()));
            dto.setLastRunStatus(resolveLastRunStatus(c, la, runById));
            dto.setPreviewText(previewPreferAssistant(la, fu));
            dto.setTags(tagsByConv.getOrDefault(cid, List.of()));
            dto.setNotebooks(nbByConv.getOrDefault(cid, List.of()));
            dto.setNoteSummary(summarizeNotes(notesByConv.get(cid)));
            items.add(dto);
        }

        return new AiConversationListResponseDTO(total, p, ps, items);
    }

    @Override
    public AiConversationMessagesResponseDTO listMessages(Long conversationId, Long userId) {
        conversationCoreService.requireConversationOwnedByUser(conversationId, userId);
        List<GbAiMessageEntity> rows = conversationCoreService.getConversationMessages(conversationId);
        List<Long> messageIds =
                rows.stream().map(GbAiMessageEntity::getGbAiMessageId).filter(Objects::nonNull).toList();
        Map<Long, Long> pinByMessageId =
                gbAiWorkPinService.mapActivePinIdsForMessages(userId, conversationId, messageIds);
        Map<Long, Long> noteByMessageId =
                gbAiWorkNoteService.mapActiveNoteIdsForMessages(userId, conversationId, messageIds);

        List<AiConversationMessageDTO> out = new ArrayList<>(rows.size());
        Map<Long, GbAiMessageEntity> rowById = new HashMap<>(rows.size());
        for (GbAiMessageEntity m : rows) {
            if (m.getGbAiMessageId() != null) {
                rowById.put(m.getGbAiMessageId(), m);
            }
        }
        for (GbAiMessageEntity m : rows) {
            Long mid = m.getGbAiMessageId();
            Long pinId = pinByMessageId.get(mid);
            boolean pinned = pinId != null;
            Long noteId = noteByMessageId.get(mid);
            boolean noted = noteId != null;
            String createdAt = fmt(m.getGbAiMessageCreateTime());
            String updatedAt = fmt(m.getGbAiMessageUpdateTime());
            out.add(
                    new AiConversationMessageDTO(
                            mid,
                            m.getGbAiMessageRole(),
                            m.getGbAiMessageContent(),
                            m.getGbAiMessageStatus(),
                            m.getGbAiMessageRunId(),
                            createdAt,
                            updatedAt,
                            createdAt,
                            updatedAt,
                            pinned,
                            pinId,
                            noted,
                            noteId,
                            null,
                            null,
                            null));
        }
        for (AiConversationMessageDTO dto : out) {
            if ("assistant".equalsIgnoreCase(dto.getRole())) {
                GbAiMessageEntity row = rowById.get(dto.getMessageId());
                if (row != null) {
                    AiAnswerContextSummarySupport.hydrateMessageFromPersistence(
                            dto, row.getGbAiMessageContextSummaryJson());
                    AiCardPayloadWireSupport.hydrateMessageCardsFromPersistence(
                            dto, row.getGbAiMessageCardsJson());
                }
                if (dto.getContextSummary() == null || dto.getContextSummary().isEmpty()) {
                    AiAnswerContextSummarySupport.hydrateMessageFromRunSession(
                            dto, runSessionRegistry, dto.getRunId());
                }
                if (dto.getCards() == null || dto.getCards().isEmpty()) {
                    AiCardPayloadWireSupport.hydrateMessageCardFromRunSession(
                            dto, runSessionRegistry, dto.getRunId());
                }
            }
        }
        return new AiConversationMessagesResponseDTO(conversationId, out);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiConversationPinMutationDTO pinConversation(Long conversationId, Long userId) {
        conversationCoreService.requireConversationOwnedByUser(conversationId, userId);
        Date now = new Date();
        GbAiConversationPinEntity row = new GbAiConversationPinEntity();
        row.setGbAiCpUserId(userId);
        row.setGbAiCpConversationId(conversationId);
        row.setGbAiCpPinnedAt(now);
        row.setGbAiCpCreatedAt(now);
        try {
            conversationPinMapper.insert(row);
            return new AiConversationPinMutationDTO(true, false);
        } catch (Exception ex) {
            // 仅 uk_gb_ai_cp_user_conv 重复视为幂等；其它 DB 错误抛出
            if (isDuplicateKey(ex)) {
                return new AiConversationPinMutationDTO(true, true);
            }
            throw ex;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unpinConversation(Long conversationId, Long userId) {
        conversationCoreService.requireConversationOwnedByUser(conversationId, userId);
        conversationPinMapper.delete(
                Wrappers.<GbAiConversationPinEntity>lambdaQuery()
                        .eq(GbAiConversationPinEntity::getGbAiCpUserId, userId)
                        .eq(GbAiConversationPinEntity::getGbAiCpConversationId, conversationId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiConversationTagMutationDTO attachTag(Long conversationId, AiConversationTagAttachRequest body) {
        if (body == null || body.getUserId() == null) {
            throw new IllegalArgumentException("userId required");
        }
        Long userId = body.getUserId();
        GbAiConversationEntity conv = conversationCoreService.requireConversationOwnedByUser(conversationId, userId);
        long anchorDis = nz(conv.getGbAiConversationDistributerId());
        long anchorDept = nz(conv.getGbAiConversationDepartmentId());

        Long tagId = body.getTagId();
        if (tagId == null && StrUtil.isBlank(body.getTagName())) {
            throw new IllegalArgumentException("tagId or tagName required");
        }
        if (tagId != null) {
            GbAiTagEntity tag = tagMapper.selectById(tagId);
            if (tag == null || !Objects.equals(tag.getGbAiTagUserId(), userId)) {
                throw new IllegalArgumentException("tag not found or not owned by user");
            }
            if (!tenantAnchorsMatch(conv, tag.getGbAiTagAnchorDistributerId(), tag.getGbAiTagAnchorDepartmentId())) {
                throw new IllegalArgumentException("tag tenant scope mismatch");
            }
        } else {
            String name = body.getTagName().trim();
            GbAiTagEntity existing = tagMapper.selectOne(
                    Wrappers.<GbAiTagEntity>lambdaQuery()
                            .eq(GbAiTagEntity::getGbAiTagUserId, userId)
                            .eq(GbAiTagEntity::getGbAiTagAnchorDistributerId, anchorDis)
                            .eq(GbAiTagEntity::getGbAiTagAnchorDepartmentId, anchorDept)
                            .eq(GbAiTagEntity::getGbAiTagName, name));
            if (existing != null) {
                tagId = existing.getGbAiTagId();
            } else {
                GbAiTagEntity nt = new GbAiTagEntity();
                nt.setGbAiTagUserId(userId);
                nt.setGbAiTagAnchorDistributerId(anchorDis);
                nt.setGbAiTagAnchorDepartmentId(anchorDept);
                nt.setGbAiTagName(name);
                nt.setGbAiTagColor(StrUtil.trimToNull(body.getTagColor()));
                nt.setGbAiTagCreatedAt(new Date());
                try {
                    tagMapper.insert(nt);
                    tagId = nt.getGbAiTagId();
                } catch (Exception ex) {
                    // 仅 uk_gb_ai_tag_user_scope_name 并发重复视为幂等；其它 DB 错误抛出
                    if (!isDuplicateKey(ex)) {
                        throw ex;
                    }
                    GbAiTagEntity raced = tagMapper.selectOne(
                            Wrappers.<GbAiTagEntity>lambdaQuery()
                                    .eq(GbAiTagEntity::getGbAiTagUserId, userId)
                                    .eq(GbAiTagEntity::getGbAiTagAnchorDistributerId, anchorDis)
                                    .eq(GbAiTagEntity::getGbAiTagAnchorDepartmentId, anchorDept)
                                    .eq(GbAiTagEntity::getGbAiTagName, name));
                    if (raced == null) {
                        throw ex;
                    }
                    tagId = raced.getGbAiTagId();
                }
            }
        }

        GbAiConversationTagEntity row = new GbAiConversationTagEntity();
        row.setGbAiCtUserId(userId);
        row.setGbAiCtConversationId(conversationId);
        row.setGbAiCtTagId(tagId);
        row.setGbAiCtCreatedAt(new Date());
        try {
            conversationTagMapper.insert(row);
            return new AiConversationTagMutationDTO(tagId, false);
        } catch (Exception ex) {
            // 仅 uk_gb_ai_ct_user_conv_tag 重复视为幂等；其它 DB 错误抛出
            if (isDuplicateKey(ex)) {
                return new AiConversationTagMutationDTO(tagId, true);
            }
            throw ex;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void detachTag(Long conversationId, Long userId, Long tagId) {
        if (tagId == null) {
            throw new IllegalArgumentException("tagId required");
        }
        GbAiConversationEntity conv = conversationCoreService.requireConversationOwnedByUser(conversationId, userId);
        GbAiTagEntity tag = tagMapper.selectById(tagId);
        if (tag != null
                && Objects.equals(tag.getGbAiTagUserId(), userId)
                && !tenantAnchorsMatch(conv, tag.getGbAiTagAnchorDistributerId(), tag.getGbAiTagAnchorDepartmentId())) {
            throw new IllegalArgumentException("tag tenant scope mismatch");
        }
        conversationTagMapper.delete(
                Wrappers.<GbAiConversationTagEntity>lambdaQuery()
                        .eq(GbAiConversationTagEntity::getGbAiCtUserId, userId)
                        .eq(GbAiConversationTagEntity::getGbAiCtConversationId, conversationId)
                        .eq(GbAiConversationTagEntity::getGbAiCtTagId, tagId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiConversationNotebookMutationDTO attachNotebook(Long conversationId, AiConversationNotebookAttachRequest body) {
        if (body == null || body.getUserId() == null) {
            throw new IllegalArgumentException("userId required");
        }
        Long userId = body.getUserId();
        GbAiConversationEntity conv = conversationCoreService.requireConversationOwnedByUser(conversationId, userId);
        long anchorDis = nz(conv.getGbAiConversationDistributerId());
        long anchorDept = nz(conv.getGbAiConversationDepartmentId());

        Long notebookId = body.getNotebookId();
        if (notebookId == null && StrUtil.isBlank(body.getNotebookName())) {
            throw new IllegalArgumentException("notebookId or notebookName required");
        }
        if (notebookId != null) {
            GbAiNotebookEntity nb = notebookMapper.selectById(notebookId);
            if (nb == null || !Objects.equals(nb.getGbAiNbUserId(), userId)) {
                throw new IllegalArgumentException("notebook not found or not owned by user");
            }
            if (!tenantAnchorsMatch(conv, nb.getGbAiNbAnchorDistributerId(), nb.getGbAiNbAnchorDepartmentId())) {
                throw new IllegalArgumentException("notebook tenant scope mismatch");
            }
        } else {
            String name = body.getNotebookName().trim();
            GbAiNotebookEntity existing = notebookMapper.selectOne(
                    Wrappers.<GbAiNotebookEntity>lambdaQuery()
                            .eq(GbAiNotebookEntity::getGbAiNbUserId, userId)
                            .eq(GbAiNotebookEntity::getGbAiNbAnchorDistributerId, anchorDis)
                            .eq(GbAiNotebookEntity::getGbAiNbAnchorDepartmentId, anchorDept)
                            .eq(GbAiNotebookEntity::getGbAiNbName, name));
            if (existing != null) {
                notebookId = existing.getGbAiNbId();
            } else {
                Date now = new Date();
                GbAiNotebookEntity nn = new GbAiNotebookEntity();
                nn.setGbAiNbUserId(userId);
                nn.setGbAiNbAnchorDistributerId(anchorDis);
                nn.setGbAiNbAnchorDepartmentId(anchorDept);
                nn.setGbAiNbName(name);
                nn.setGbAiNbDescription(StrUtil.trimToNull(body.getNotebookDescription()));
                nn.setGbAiNbCreatedAt(now);
                nn.setGbAiNbUpdatedAt(now);
                try {
                    notebookMapper.insert(nn);
                    notebookId = nn.getGbAiNbId();
                } catch (Exception ex) {
                    // 仅 uk_gb_ai_nb_user_scope_name 并发重复视为幂等；其它 DB 错误抛出
                    if (!isDuplicateKey(ex)) {
                        throw ex;
                    }
                    GbAiNotebookEntity raced = notebookMapper.selectOne(
                            Wrappers.<GbAiNotebookEntity>lambdaQuery()
                                    .eq(GbAiNotebookEntity::getGbAiNbUserId, userId)
                                    .eq(GbAiNotebookEntity::getGbAiNbAnchorDistributerId, anchorDis)
                                    .eq(GbAiNotebookEntity::getGbAiNbAnchorDepartmentId, anchorDept)
                                    .eq(GbAiNotebookEntity::getGbAiNbName, name));
                    if (raced == null) {
                        throw ex;
                    }
                    notebookId = raced.getGbAiNbId();
                }
            }
        }

        GbAiConversationNotebookEntity row = new GbAiConversationNotebookEntity();
        row.setGbAiCnbUserId(userId);
        row.setGbAiCnbConversationId(conversationId);
        row.setGbAiCnbNotebookId(notebookId);
        row.setGbAiCnbCreatedAt(new Date());
        try {
            conversationNotebookMapper.insert(row);
            return new AiConversationNotebookMutationDTO(notebookId, false);
        } catch (Exception ex) {
            // 仅 uk_gb_ai_cnb_user_conv_nb 重复视为幂等；其它 DB 错误抛出
            if (isDuplicateKey(ex)) {
                return new AiConversationNotebookMutationDTO(notebookId, true);
            }
            throw ex;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void detachNotebook(Long conversationId, Long userId, Long notebookId) {
        if (notebookId == null) {
            throw new IllegalArgumentException("notebookId required");
        }
        GbAiConversationEntity conv = conversationCoreService.requireConversationOwnedByUser(conversationId, userId);
        GbAiNotebookEntity nb = notebookMapper.selectById(notebookId);
        if (nb != null
                && Objects.equals(nb.getGbAiNbUserId(), userId)
                && !tenantAnchorsMatch(conv, nb.getGbAiNbAnchorDistributerId(), nb.getGbAiNbAnchorDepartmentId())) {
            throw new IllegalArgumentException("notebook tenant scope mismatch");
        }
        conversationNotebookMapper.delete(
                Wrappers.<GbAiConversationNotebookEntity>lambdaQuery()
                        .eq(GbAiConversationNotebookEntity::getGbAiCnbUserId, userId)
                        .eq(GbAiConversationNotebookEntity::getGbAiCnbConversationId, conversationId)
                        .eq(GbAiConversationNotebookEntity::getGbAiCnbNotebookId, notebookId));
    }

    private Set<Long> loadPinnedConversationIds(Long userId, List<Long> conversationIds) {
        List<GbAiConversationPinEntity> pins = conversationPinMapper.selectList(
                Wrappers.<GbAiConversationPinEntity>lambdaQuery()
                        .eq(GbAiConversationPinEntity::getGbAiCpUserId, userId)
                        .in(GbAiConversationPinEntity::getGbAiCpConversationId, conversationIds));
        return pins.stream().map(GbAiConversationPinEntity::getGbAiCpConversationId).collect(Collectors.toSet());
    }

    /** 与会话上的批发商/部门锚点比对；null 当 0，避免跨商户同名标签复用到错误会话。 */
    private static boolean tenantAnchorsMatch(GbAiConversationEntity conv, Long tagDis, Long tagDept) {
        long cd = nz(conv.getGbAiConversationDistributerId());
        long ct = nz(conv.getGbAiConversationDepartmentId());
        long td = nz(tagDis);
        long tp = nz(tagDept);
        return cd == td && ct == tp;
    }

    private static long nz(Long v) {
        return v != null ? v : 0L;
    }

    /**
     * 仅判定「唯一键 / 重复键」冲突，用于幂等返回 duplicated=true。
     * <ul>
     *   <li>{@link DuplicateKeyException}：Spring 对 JDBC 重复键的封装。</li>
     *   <li>{@link SQLIntegrityConstraintViolationException} 且 {@code errorCode == 1062}：MySQL ER_DUP_ENTRY。</li>
     * </ul>
     * 其他数据库异常（含其它 SQLState / errorCode、外键、非空等）一律返回 false，由外层抛出。
     */
    private static boolean isDuplicateKey(Throwable ex) {
        Throwable cur = ex;
        while (cur != null) {
            if (cur instanceof DuplicateKeyException) {
                return true;
            }
            if (cur instanceof SQLIntegrityConstraintViolationException sqlEx) {
                // MySQL 重复键 ER_DUP_ENTRY；避免把所有外键等约束失败误判为幂等重复
                if (sqlEx.getErrorCode() == 1062) {
                    return true;
                }
            }
            cur = cur.getCause();
        }
        return false;
    }

    private static String normalizeStatusFilter(String status) {
        if (StrUtil.isBlank(status)) {
            return null;
        }
        String s = status.trim();
        if ("ALL".equalsIgnoreCase(s)) {
            return null;
        }
        return s;
    }

    private static boolean isArchived(GbAiConversationEntity c) {
        return c.getGbAiConversationArchived() != null && c.getGbAiConversationArchived() != 0;
    }

    private static String resolveTitle(GbAiConversationEntity conv, GbAiMessageEntity firstUser) {
        String t = conv.getGbAiConversationTitle();
        if (isPlaceholderTitle(t) && firstUser != null && StrUtil.isNotBlank(firstUser.getGbAiMessageContent())) {
            String s = firstUser.getGbAiMessageContent().trim();
            return s.length() > 80 ? s.substring(0, 80) + "…" : s;
        }
        return t != null ? t : "";
    }

    private static boolean isPlaceholderTitle(String t) {
        return StrUtil.isBlank(t) || "新对话".equals(t.trim());
    }

    private static String previewPreferAssistant(GbAiMessageEntity lastAssistant, GbAiMessageEntity firstUser) {
        if (lastAssistant != null && StrUtil.isNotBlank(lastAssistant.getGbAiMessageContent())) {
            return truncatePreview(lastAssistant.getGbAiMessageContent(), 160);
        }
        if (firstUser != null && StrUtil.isNotBlank(firstUser.getGbAiMessageContent())) {
            return truncatePreview(firstUser.getGbAiMessageContent(), 160);
        }
        return null;
    }

    private static String truncatePreview(String text, int max) {
        String t = text.trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }

    private static String resolveLastRunStatus(GbAiConversationEntity conv,
                                               GbAiMessageEntity lastAssistant,
                                               Map<Long, GbAiAgentRunEntity> runById) {
        if (lastAssistant != null && StrUtil.isNotBlank(lastAssistant.getGbAiMessageStatus())) {
            return lastAssistant.getGbAiMessageStatus();
        }
        Long rid = conv.getGbAiConversationLastRunId();
        if (rid != null) {
            GbAiAgentRunEntity r = runById.get(rid);
            if (r != null && StrUtil.isNotBlank(r.getStatus())) {
                return r.getStatus();
            }
        }
        return null;
    }

    private static AiConversationNoteSummaryDTO summarizeNotes(List<GbAiWorkNoteEntity> list) {
        AiConversationNoteSummaryDTO dto = new AiConversationNoteSummaryDTO(false, null, null);
        if (list == null || list.isEmpty()) {
            return dto;
        }
        List<GbAiWorkNoteEntity> sorted = new ArrayList<>(list);
        sorted.sort(Comparator.comparing(GbAiWorkNoteEntity::getGbAiWnUpdatedAt,
                Comparator.nullsLast(Date::compareTo)).reversed());
        boolean has = sorted.stream().anyMatch(n ->
                StrUtil.isNotBlank(n.getGbAiWnTitle()) || StrUtil.isNotBlank(n.getGbAiWnContentMd()));
        dto.setHasSummary(has);
        GbAiWorkNoteEntity latest = sorted.get(0);
        dto.setLatestNoteId(latest.getGbAiWnId());
        dto.setLatestTitle(latest.getGbAiWnTitle());
        return dto;
    }

    private static String fmt(Date d) {
        if (d == null) {
            return null;
        }
        return DATE_FMT.format(d.toInstant());
    }
}
