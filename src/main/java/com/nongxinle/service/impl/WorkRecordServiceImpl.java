package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nongxinle.ai.conversation.AiConversationCoreService;
import com.nongxinle.ai.platform.AiRunMessagePersistenceService;
import com.nongxinle.ai.scope.AiConversationScopeMode;
import com.nongxinle.ai.trace.AiRunSessionRegistry;
import com.nongxinle.ai.workrecord.*;
import com.nongxinle.ai.workrecord.business.WorkRecordBusinessCardFactResolver;
import com.nongxinle.ai.workrecord.business.WorkRecordBusinessCardFactResult;
import com.nongxinle.ai.workrecord.business.WorkRecordBusinessCardSourceLoader;
import com.nongxinle.ai.workrecord.business.WorkRecordBusinessCardSourceReader;
import com.nongxinle.ai.workrecord.business.WorkRecordItemKey;
import com.nongxinle.ai.workrecord.dto.WorkRecordCategoryDTO;
import com.nongxinle.ai.workrecord.dto.WorkRecordConversationBootstrapDTO;
import com.nongxinle.ai.workrecord.dto.WorkRecordCreateRequest;
import com.nongxinle.ai.workrecord.dto.WorkRecordFromBusinessCardRequest;
import com.nongxinle.ai.workrecord.dto.WorkRecordDeleteResponse;
import com.nongxinle.ai.workrecord.dto.WorkRecordResponse;
import com.nongxinle.ai.workrecord.dto.WorkRecordSourceCardResponse;
import com.nongxinle.ai.workrecord.dto.WorkRecordUpdateRequest;
import com.nongxinle.entity.GbAiConversationEntity;
import com.nongxinle.entity.GbWorkRecordCategoryEntity;
import com.nongxinle.entity.GbWorkRecordEntity;
import com.nongxinle.mapper.GbAiMessageMapper;
import com.nongxinle.entity.GbDepartmentUserEntity;
import com.nongxinle.mapper.GbWorkRecordMapper;
import com.nongxinle.service.GbDepartmentUserService;
import com.nongxinle.service.WorkRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkRecordServiceImpl implements WorkRecordService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String ROLE_USER = "user";

    private final WorkRecordScopeGuard scopeGuard;
    private final AiConversationCoreService conversationCoreService;
    private final WorkRecordCategoryService categoryService;
    private final WorkRecordLlmAgent llmAgent;
    private final WorkRecordTraceHelper traceHelper;
    private final AiRunSessionRegistry runSessionRegistry;
    private final AiRunMessagePersistenceService messagePersistenceService;
    private final GbAiMessageMapper messageMapper;
    private final GbWorkRecordMapper workRecordMapper;
    private final GbDepartmentUserService departmentUserService;
    private final WorkRecordBusinessCardSourceLoader businessCardSourceLoader;
    private final WorkRecordBusinessCardFactResolver businessCardFactResolver;
    private final WorkRecordBusinessCardSourceReader businessCardSourceReader;

    @Override
    public WorkRecordConversationBootstrapDTO bootstrapConversation(
            Long userId, Long departmentId, Long distributerId) {
        WorkRecordScopeGuard.ResolvedScope scope =
                scopeGuard.resolveAndValidate(userId, departmentId, distributerId);
        GbAiConversationEntity conv =
                conversationCoreService.getOrCreateWorkRecordConversation(
                        scope.departmentId(),
                        scope.distributerId(),
                        AiConversationScopeMode.STORE,
                        scope.userId());
        conversationCoreService.requireConversationOwnedByUser(conv.getGbAiConversationId(), userId);
        WorkRecordConversationBootstrapDTO dto = new WorkRecordConversationBootstrapDTO();
        dto.setConversationId(conv.getGbAiConversationId());
        dto.setConversationType(WorkRecordConstants.CONVERSATION_TYPE_WORK_RECORD);
        dto.setThreadKind(WorkRecordConstants.THREAD_KIND_WORK_RECORD);
        dto.setTitle(conv.getGbAiConversationTitle());
        return dto;
    }

    @Override
    public WorkRecordResponse createRecord(WorkRecordCreateRequest request) {
        validateCreateRequest(request);
        WorkRecordScopeGuard.ResolvedScope scope =
                scopeGuard.resolveAndValidate(
                        request.getUserId(), request.getDepartmentId(), request.getDistributerId());

        GbAiConversationEntity conv =
                conversationCoreService.getOrCreateWorkRecordConversation(
                        scope.departmentId(),
                        scope.distributerId(),
                        AiConversationScopeMode.STORE,
                        scope.userId());
        conversationCoreService.requireConversationOwnedByUser(conv.getGbAiConversationId(), scope.userId());

        Date recordedAt = parseRecordedAt(request.getRecordedAt());
        long runId = runSessionRegistry.nextRunId();
        String rawContent = request.getContent().trim();

        GbWorkRecordEntity record = insertPendingRecord(request, scope, conv, recordedAt, runId);
        traceHelper.insertRunStarting(
                runId,
                conv.getGbAiConversationId(),
                scope.userId(),
                scope.departmentId(),
                scope.distributerId(),
                rawContent);

        messagePersistenceService.persistUserMessageForRun(
                conv.getGbAiConversationId(), scope.userId(), runId, rawContent);
        Long userMessageId =
                messageMapper.selectMessageIdByConversationRunAndRole(
                        conv.getGbAiConversationId(), runId, ROLE_USER);
        if (userMessageId != null) {
            record.setGbWrSourceMessageId(userMessageId);
            workRecordMapper.updateById(record);
        }

        return processAiAndFinalize(record, conv, scope, runId, rawContent, true);
    }

    @Override
    public WorkRecordResponse createFromBusinessCard(WorkRecordFromBusinessCardRequest request) {
        WorkRecordBusinessCardSourceLoader.LoadedBusinessSource loaded =
                businessCardSourceLoader.load(request);

        String cardType = request.getSourceCardType().trim();
        String itemKeyRaw = request.getSourceItemKey().trim();
        WorkRecordItemKey itemKey = WorkRecordItemKey.parse(itemKeyRaw);

        GbWorkRecordEntity existing =
                findByBusinessOrigin(
                        loaded.scope().userId(),
                        request.getSourceMessageId(),
                        cardType,
                        itemKeyRaw);
        if (existing != null) {
            return toResponse(existing);
        }

        WorkRecordBusinessCardFactResult fact =
                businessCardFactResolver.resolve(
                        loaded.cards(),
                        cardType,
                        itemKey,
                        request.getSourceAnswerPlanType());

        GbAiConversationEntity wrConv =
                conversationCoreService.getOrCreateWorkRecordConversation(
                        loaded.scope().departmentId(),
                        loaded.scope().distributerId(),
                        AiConversationScopeMode.STORE,
                        loaded.scope().userId());
        conversationCoreService.requireConversationOwnedByUser(
                wrConv.getGbAiConversationId(), loaded.scope().userId());

        long runId = runSessionRegistry.nextRunId();
        String rawContent = fact.getSourceFactText().trim();
        GbWorkRecordEntity record =
                buildPendingBusinessCardRecord(
                        request, loaded.scope(), wrConv, runId, rawContent, fact, cardType, itemKeyRaw);
        try {
            workRecordMapper.insert(record);
        } catch (DuplicateKeyException ex) {
            GbWorkRecordEntity raced =
                    findByBusinessOrigin(
                            loaded.scope().userId(),
                            request.getSourceMessageId(),
                            cardType,
                            itemKeyRaw);
            if (raced != null) {
                return toResponse(raced);
            }
            throw new WorkRecordBusinessCardException(
                    WorkRecordBusinessCardErrors.ALREADY_RECORDED,
                    "business card already recorded");
        }

        traceHelper.insertRunStarting(
                runId,
                wrConv.getGbAiConversationId(),
                loaded.scope().userId(),
                loaded.scope().departmentId(),
                loaded.scope().distributerId(),
                rawContent);

        messagePersistenceService.persistUserMessageForRun(
                wrConv.getGbAiConversationId(), loaded.scope().userId(), runId, rawContent);
        Long userMessageId =
                messageMapper.selectMessageIdByConversationRunAndRole(
                        wrConv.getGbAiConversationId(), runId, ROLE_USER);
        if (userMessageId != null) {
            record.setGbWrSourceMessageId(userMessageId);
            workRecordMapper.updateById(record);
        }

        return processAiAndFinalize(record, wrConv, loaded.scope(), runId, rawContent, true);
    }

    @Override
    public WorkRecordResponse retryAiProcessing(Long recordId, Long userId) {
        if (recordId == null || userId == null) {
            throw new IllegalArgumentException("recordId and userId required");
        }
        GbWorkRecordEntity record = workRecordMapper.selectById(recordId);
        if (record == null) {
            throw new IllegalArgumentException("record not found: " + recordId);
        }

        WorkRecordScopeGuard.ResolvedScope scope =
                scopeGuard.resolveAndValidate(
                        userId, record.getGbWrDepartmentId(), record.getGbWrDistributerId());
        WorkRecordOwnership.assertOwnedRecord(record, userId, scope);
        GbAiConversationEntity conv =
                conversationCoreService.requireConversationOwnedByUser(
                        record.getGbWrConversationId(), userId);

        long runId = runSessionRegistry.nextRunId();
        String rawContent = record.getGbWrRawContent();
        Date now = new Date();
        int migrated =
                workRecordMapper.update(
                        null,
                        new LambdaUpdateWrapper<GbWorkRecordEntity>()
                                .eq(GbWorkRecordEntity::getGbWrId, recordId)
                                .eq(GbWorkRecordEntity::getGbWrAiStatus, WorkRecordConstants.AI_FAILED)
                                .eq(GbWorkRecordEntity::getGbWrRecorderUserId, userId)
                                .eq(GbWorkRecordEntity::getGbWrDepartmentId, scope.departmentId())
                                .eq(GbWorkRecordEntity::getGbWrDistributerId, scope.distributerId())
                                .set(GbWorkRecordEntity::getGbWrAiStatus, WorkRecordConstants.AI_PROCESSING)
                                .set(GbWorkRecordEntity::getGbWrSourceRunId, runId)
                                .set(GbWorkRecordEntity::getGbWrAiErrorCode, null)
                                .set(GbWorkRecordEntity::getGbWrUpdatedAt, now));
        if (migrated != 1) {
            throw new IllegalArgumentException(WorkRecordOwnership.RETRY_NOT_ALLOWED);
        }

        record = reload(recordId);
        traceHelper.insertRunStarting(
                runId,
                conv.getGbAiConversationId(),
                scope.userId(),
                scope.departmentId(),
                scope.distributerId(),
                rawContent);

        return processAiAndFinalize(record, conv, scope, runId, rawContent, false);
    }

    @Override
    public WorkRecordSourceCardResponse getSourceCard(Long recordId, Long userId) {
        return businessCardSourceReader.read(recordId, userId);
    }

    @Override
    public WorkRecordResponse updatePolishedContent(
            Long recordId, Long userId, WorkRecordUpdateRequest request) {
        if (request == null || !StringUtils.hasText(request.getContent())) {
            throw new WorkRecordMutationException(
                    WorkRecordMutationErrors.WORK_RECORD_CONTENT_EMPTY, "content required");
        }
        String polished = request.getContent().trim();
        if (!StringUtils.hasText(polished)) {
            throw new WorkRecordMutationException(
                    WorkRecordMutationErrors.WORK_RECORD_CONTENT_EMPTY, "content cannot be empty");
        }

        GbWorkRecordEntity record = requireOwnedRecord(recordId, userId);
        Date now = new Date();
        int updated =
                workRecordMapper.update(
                        null,
                        new LambdaUpdateWrapper<GbWorkRecordEntity>()
                                .eq(GbWorkRecordEntity::getGbWrId, record.getGbWrId())
                                .eq(GbWorkRecordEntity::getGbWrRecorderUserId, userId)
                                .eq(GbWorkRecordEntity::getGbWrDepartmentId, record.getGbWrDepartmentId())
                                .eq(GbWorkRecordEntity::getGbWrDistributerId, record.getGbWrDistributerId())
                                .eq(GbWorkRecordEntity::getGbWrStatus, 0)
                                .set(GbWorkRecordEntity::getGbWrPolishedContent, polished)
                                .set(GbWorkRecordEntity::getGbWrUpdatedAt, now));
        if (updated != 1) {
            throw new WorkRecordMutationException(
                    WorkRecordMutationErrors.WORK_RECORD_UPDATE_FAILED,
                    "work record update failed: " + recordId);
        }
        return toResponse(reload(record.getGbWrId()));
    }

    @Override
    public WorkRecordDeleteResponse deleteRecord(Long recordId, Long userId) {
        GbWorkRecordEntity record = requireOwnedRecord(recordId, userId);
        int deleted =
                workRecordMapper.delete(
                        new LambdaQueryWrapper<GbWorkRecordEntity>()
                                .eq(GbWorkRecordEntity::getGbWrId, record.getGbWrId())
                                .eq(GbWorkRecordEntity::getGbWrRecorderUserId, userId)
                                .eq(GbWorkRecordEntity::getGbWrDepartmentId, record.getGbWrDepartmentId())
                                .eq(GbWorkRecordEntity::getGbWrDistributerId, record.getGbWrDistributerId())
                                .eq(GbWorkRecordEntity::getGbWrStatus, 0));
        if (deleted != 1) {
            throw new WorkRecordMutationException(
                    WorkRecordMutationErrors.WORK_RECORD_DELETE_FAILED,
                    "work record delete failed: " + recordId);
        }
        return WorkRecordDeleteResponse.builder().recordId(recordId).deleted(true).build();
    }

    @Override
    public List<WorkRecordResponse> listRecords(
            Long userId,
            Long departmentId,
            Long distributerId,
            Long categoryId,
            Date startDate,
            Date endDate,
            int page,
            int pageSize) {

        WorkRecordScopeGuard.ResolvedScope scope =
                scopeGuard.resolveAndValidate(userId, departmentId, distributerId);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        int offset = (safePage - 1) * safeSize;

        LambdaQueryWrapper<GbWorkRecordEntity> w = new LambdaQueryWrapper<>();
        w.eq(GbWorkRecordEntity::getGbWrRecorderUserId, scope.userId())
                .eq(GbWorkRecordEntity::getGbWrDepartmentId, scope.departmentId())
                .eq(GbWorkRecordEntity::getGbWrDistributerId, scope.distributerId())
                .eq(GbWorkRecordEntity::getGbWrStatus, 0);
        if (categoryId != null) {
            w.eq(GbWorkRecordEntity::getGbWrCategoryId, categoryId);
        }
        if (startDate != null) {
            w.ge(GbWorkRecordEntity::getGbWrRecordedAt, startDate);
        }
        if (endDate != null) {
            w.le(GbWorkRecordEntity::getGbWrRecordedAt, endOfDay(endDate));
        }
        w.orderByDesc(GbWorkRecordEntity::getGbWrRecordedAt)
                .last("LIMIT " + offset + "," + safeSize);

        return workRecordMapper.selectList(w).stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public List<WorkRecordCategoryDTO> listCategories(Long userId, Long distributerId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId required");
        }
        GbDepartmentUserEntity user = requireUser(userId);
        Long dis = distributerId;
        if (dis == null && user.getGbDuDistributerId() != null) {
            dis = user.getGbDuDistributerId().longValue();
        }
        return categoryService.listActiveCategories(dis);
    }

    private WorkRecordResponse processAiAndFinalize(
            GbWorkRecordEntity record,
            GbAiConversationEntity conv,
            WorkRecordScopeGuard.ResolvedScope scope,
            long runId,
            String rawContent,
            boolean markProcessingBeforeLlm) {

        if (markProcessingBeforeLlm) {
            record.setGbWrAiStatus(WorkRecordConstants.AI_PROCESSING);
            record.setGbWrUpdatedAt(new Date());
            workRecordMapper.updateById(record);
        }

        long t0 = System.currentTimeMillis();
        List<GbWorkRecordCategoryEntity> categories =
                categoryService.listActiveCategoryEntities(scope.distributerId());
        Map<Long, GbWorkRecordCategoryEntity> activeById =
                categoryService.activeCategoryMap(scope.distributerId());
        GbWorkRecordCategoryEntity otherCategory = categoryService.requireOtherCategory(scope.distributerId());

        WorkRecordLlmAgent.AgentResult agentResult =
                llmAgent.polishAndClassify(rawContent, scope.storeName(), categories);
        long duration = System.currentTimeMillis() - t0;

        if (!agentResult.ok()) {
            markAiFailed(record, agentResult.errorCode());
            traceHelper.insertLlmStep(
                    runId,
                    agentResult.userPayload(),
                    agentResult.rawLlmResponse(),
                    "FAILED",
                    (int) duration);
            traceHelper.finishRun(runId, "FAILED", duration, agentResult.errorCode());
            persistAssistantFailure(conv, scope.userId(), runId);
            return toResponse(reload(record.getGbWrId()));
        }

        WorkRecordLlmOutputGuard.GuardResult guard =
                WorkRecordLlmOutputGuard.validate(agentResult.llmResult(), activeById, otherCategory);
        if (!guard.ok()) {
            markAiFailed(record, guard.errorCode());
            traceHelper.insertLlmStep(
                    runId,
                    agentResult.userPayload(),
                    agentResult.rawLlmResponse(),
                    "FAILED",
                    (int) duration);
            traceHelper.finishRun(runId, "FAILED", duration, guard.errorCode());
            persistAssistantFailure(conv, scope.userId(), runId);
            return toResponse(reload(record.getGbWrId()));
        }

        WorkRecordLlmOutputGuard.GuardedCategory cat = guard.category();
        WorkRecordLlmResult llm = agentResult.llmResult();

        record.setGbWrPolishedContent(llm.getPolishedContent());
        record.setGbWrCategoryId(cat.categoryId());
        record.setGbWrCategoryCode(cat.categoryCode());
        record.setGbWrCategoryNameSnapshot(cat.categoryName());
        record.setGbWrCategoryDecision(cat.categoryDecision());
        record.setGbWrSuggestedCategoryName(cat.suggestedCategoryName());
        record.setGbWrAiStatus(WorkRecordConstants.AI_SUCCESS);
        record.setGbWrAiConfidence(llm.getConfidence());
        record.setGbWrAiReason(llm.getShortReason());
        record.setGbWrAiErrorCode(null);
        record.setGbWrSourceRunId(runId);
        record.setGbWrUpdatedAt(new Date());
        workRecordMapper.updateById(record);

        traceHelper.insertLlmStep(
                runId, agentResult.userPayload(), agentResult.rawLlmResponse(), "COMPLETED", (int) duration);
        traceHelper.finishRun(runId, "COMPLETED", duration, null);

        String assistantReceipt =
                WorkRecordAssistantReceipt.success(cat.categoryName(), llm.getPolishedContent());
        Long assistantMessageId =
                messagePersistenceService.persistAssistantMessageForRun(
                        conv.getGbAiConversationId(),
                        scope.userId(),
                        runId,
                        assistantReceipt,
                        "COMPLETED",
                        null,
                        null);
        messagePersistenceService.updateConversationAfterRunMessage(
                conv.getGbAiConversationId(), runId, assistantMessageId, "WORK_RECORD", "WORK_RECORD");

        return toResponse(reload(record.getGbWrId()));
    }

    private void persistAssistantFailure(GbAiConversationEntity conv, Long userId, long runId) {
        Long assistantMessageId =
                messagePersistenceService.persistAssistantMessageForRun(
                        conv.getGbAiConversationId(),
                        userId,
                        runId,
                        WorkRecordAssistantReceipt.failure(),
                        "FAILED",
                        null,
                        null);
        messagePersistenceService.updateConversationAfterRunMessage(
                conv.getGbAiConversationId(), runId, assistantMessageId, "WORK_RECORD", "WORK_RECORD");
    }

    private void markAiFailed(GbWorkRecordEntity record, String errorCode) {
        record.setGbWrAiStatus(WorkRecordConstants.AI_FAILED);
        record.setGbWrAiErrorCode(errorCode);
        record.setGbWrUpdatedAt(new Date());
        workRecordMapper.updateById(record);
    }

    private GbWorkRecordEntity insertPendingRecord(
            WorkRecordCreateRequest request,
            WorkRecordScopeGuard.ResolvedScope scope,
            GbAiConversationEntity conv,
            Date recordedAt,
            long runId) {

        Date now = new Date();
        GbWorkRecordEntity record = new GbWorkRecordEntity();
        record.setGbWrConversationId(conv.getGbAiConversationId());
        record.setGbWrSourceRunId(runId);
        record.setGbWrDistributerId(scope.distributerId());
        record.setGbWrDepartmentId(scope.departmentId());
        record.setGbWrRecorderUserId(scope.userId());
        record.setGbWrInputType(normalizeInputType(request.getInputType()));
        record.setGbWrOriginType(WorkRecordConstants.ORIGIN_MANUAL);
        record.setGbWrRawContent(request.getContent().trim());
        record.setGbWrAiStatus(WorkRecordConstants.AI_PENDING);
        record.setGbWrRecordedAt(recordedAt);
        record.setGbWrCreatedAt(now);
        record.setGbWrUpdatedAt(now);
        record.setGbWrStatus(0);
        workRecordMapper.insert(record);
        return record;
    }

    private GbWorkRecordEntity buildPendingBusinessCardRecord(
            WorkRecordFromBusinessCardRequest request,
            WorkRecordScopeGuard.ResolvedScope scope,
            GbAiConversationEntity conv,
            long runId,
            String rawContent,
            WorkRecordBusinessCardFactResult fact,
            String cardType,
            String itemKeyRaw) {

        Date now = new Date();
        GbWorkRecordEntity record = new GbWorkRecordEntity();
        record.setGbWrConversationId(conv.getGbAiConversationId());
        record.setGbWrSourceRunId(runId);
        record.setGbWrDistributerId(scope.distributerId());
        record.setGbWrDepartmentId(scope.departmentId());
        record.setGbWrRecorderUserId(scope.userId());
        record.setGbWrInputType(WorkRecordConstants.INPUT_BUSINESS_CARD);
        record.setGbWrOriginType(WorkRecordConstants.ORIGIN_BUSINESS_CARD);
        record.setGbWrBizConversationId(request.getSourceConversationId());
        record.setGbWrBizMessageId(request.getSourceMessageId());
        record.setGbWrBizRunId(request.getSourceRunId());
        record.setGbWrBizAnswerPlanType(fact.getResolvedAnswerPlanType());
        record.setGbWrBizCardType(cardType);
        record.setGbWrBizItemKey(itemKeyRaw);
        record.setGbWrBizFactSnapshot(fact.getSourceFactSnapshot());
        record.setGbWrRawContent(rawContent);
        record.setGbWrAiStatus(WorkRecordConstants.AI_PENDING);
        record.setGbWrRecordedAt(now);
        record.setGbWrCreatedAt(now);
        record.setGbWrUpdatedAt(now);
        record.setGbWrStatus(0);
        return record;
    }

    private GbWorkRecordEntity findByBusinessOrigin(
            Long recorderUserId, Long bizMessageId, String cardType, String itemKey) {
        if (recorderUserId == null || bizMessageId == null || !StringUtils.hasText(cardType)) {
            return null;
        }
        LambdaQueryWrapper<GbWorkRecordEntity> w = new LambdaQueryWrapper<>();
        w.eq(GbWorkRecordEntity::getGbWrRecorderUserId, recorderUserId)
                .eq(GbWorkRecordEntity::getGbWrBizMessageId, bizMessageId)
                .eq(GbWorkRecordEntity::getGbWrBizCardType, cardType.trim())
                .eq(GbWorkRecordEntity::getGbWrBizItemKey, itemKey)
                .eq(GbWorkRecordEntity::getGbWrStatus, 0)
                .last("LIMIT 1");
        return workRecordMapper.selectOne(w);
    }

    private GbWorkRecordEntity reload(Long id) {
        return workRecordMapper.selectById(id);
    }

    private GbWorkRecordEntity requireOwnedRecord(Long recordId, Long userId) {
        if (recordId == null || userId == null) {
            throw new WorkRecordMutationException(
                    WorkRecordMutationErrors.WORK_RECORD_NOT_FOUND, "recordId and userId required");
        }
        GbWorkRecordEntity record = workRecordMapper.selectById(recordId);
        if (record == null || (record.getGbWrStatus() != null && record.getGbWrStatus() == 1)) {
            throw new WorkRecordMutationException(
                    WorkRecordMutationErrors.WORK_RECORD_NOT_FOUND,
                    "work record not found: " + recordId);
        }
        WorkRecordScopeGuard.ResolvedScope scope =
                scopeGuard.resolveAndValidate(
                        userId, record.getGbWrDepartmentId(), record.getGbWrDistributerId());
        try {
            WorkRecordOwnership.assertOwnedRecord(record, userId, scope);
        } catch (IllegalArgumentException ex) {
            throw new WorkRecordMutationException(
                    WorkRecordMutationErrors.WORK_RECORD_NOT_FOUND, ex.getMessage());
        }
        return record;
    }

    private static void validateCreateRequest(WorkRecordCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request required");
        }
        if (request.getUserId() == null) {
            throw new IllegalArgumentException("userId required");
        }
        if (!StringUtils.hasText(request.getContent())) {
            throw new IllegalArgumentException("content required");
        }
    }

    private static String normalizeInputType(String inputType) {
        if (!StringUtils.hasText(inputType)) {
            return WorkRecordConstants.INPUT_TEXT;
        }
        String t = inputType.trim().toUpperCase();
        if (WorkRecordConstants.INPUT_VOICE_TRANSCRIPT.equals(t)) {
            return WorkRecordConstants.INPUT_VOICE_TRANSCRIPT;
        }
        return WorkRecordConstants.INPUT_TEXT;
    }

    private static Date parseRecordedAt(String raw) {
        if (!StringUtils.hasText(raw)) {
            return new Date();
        }
        String[] patterns = {"yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd"};
        for (String p : patterns) {
            try {
                return new SimpleDateFormat(p).parse(raw.trim());
            } catch (ParseException ignored) {
                // try next
            }
        }
        throw new IllegalArgumentException("invalid recordedAt format: " + raw);
    }

    private GbDepartmentUserEntity requireUser(Long userId) {
        if (userId > Integer.MAX_VALUE || userId < Integer.MIN_VALUE) {
            throw new IllegalArgumentException("userId out of range: " + userId);
        }
        GbDepartmentUserEntity user = departmentUserService.getById(userId.intValue());
        if (user == null) {
            throw new IllegalArgumentException("user not found: " + userId);
        }
        return user;
    }

    private static Date endOfDay(Date date) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTime(date);
        cal.set(java.util.Calendar.HOUR_OF_DAY, 23);
        cal.set(java.util.Calendar.MINUTE, 59);
        cal.set(java.util.Calendar.SECOND, 59);
        cal.set(java.util.Calendar.MILLISECOND, 999);
        return cal.getTime();
    }

    private WorkRecordResponse toResponse(GbWorkRecordEntity e) {
        if (e == null) {
            return null;
        }
        return WorkRecordResponse.builder()
                .recordId(e.getGbWrId())
                .conversationId(e.getGbWrConversationId())
                .sourceMessageId(e.getGbWrSourceMessageId())
                .sourceRunId(e.getGbWrSourceRunId())
                .distributerId(e.getGbWrDistributerId())
                .departmentId(e.getGbWrDepartmentId())
                .recorderUserId(e.getGbWrRecorderUserId())
                .inputType(e.getGbWrInputType())
                .originType(e.getGbWrOriginType())
                .bizConversationId(e.getGbWrBizConversationId())
                .bizMessageId(e.getGbWrBizMessageId())
                .bizRunId(e.getGbWrBizRunId())
                .bizAnswerPlanType(e.getGbWrBizAnswerPlanType())
                .bizCardType(e.getGbWrBizCardType())
                .bizItemKey(e.getGbWrBizItemKey())
                .rawContent(e.getGbWrRawContent())
                .polishedContent(e.getGbWrPolishedContent())
                .categoryId(e.getGbWrCategoryId())
                .categoryCode(e.getGbWrCategoryCode())
                .categoryName(e.getGbWrCategoryNameSnapshot())
                .categoryDecision(e.getGbWrCategoryDecision())
                .suggestedCategoryName(e.getGbWrSuggestedCategoryName())
                .aiStatus(e.getGbWrAiStatus())
                .aiConfidence(e.getGbWrAiConfidence())
                .aiReason(e.getGbWrAiReason())
                .aiErrorCode(e.getGbWrAiErrorCode())
                .recordedAt(e.getGbWrRecordedAt())
                .createdAt(e.getGbWrCreatedAt())
                .build();
    }
}
