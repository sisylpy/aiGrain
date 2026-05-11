package com.nongxinle.ai.harness.replay;

import com.nongxinle.ai.context.AiUserContextResolver;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiConversationMemoryService;
import com.nongxinle.ai.harness.AiHarnessResolvedContextSummarizer;
import com.nongxinle.ai.platform.dto.AiRunCreateRequest;
import com.nongxinle.ai.resolver.AiResolvedQueryContextResolver;
import com.nongxinle.ai.scope.AiConversationScopeMode;
import com.nongxinle.ai.trace.AiRunSessionRegistry;
import com.nongxinle.entity.GbAiConversationEntity;
import com.nongxinle.service.GbAiChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Harness：多轮会话仅跑 {@link AiResolvedQueryContextResolver}，写入 {@link AiConversationMemoryService}，并对照预期结构化失败类型。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiHarnessReplayService {

    private final AiUserContextResolver userContextResolver;
    private final AiResolvedQueryContextResolver resolvedQueryContextResolver;
    private final AiConversationMemoryService conversationMemoryService;
    private final AiRunSessionRegistry sessionRegistry;
    private final GbAiChatService gbAiChatService;

    public AiHarnessReplayResponse replay(AiHarnessReplayRequest req) {
        if (req == null || req.getUserId() == null) {
            throw new IllegalArgumentException("userId required");
        }
        if (req.getMessages() == null || req.getMessages().isEmpty()) {
            throw new IllegalArgumentException("messages required");
        }

        LocalDate today = resolveToday(req.getFrozenClockDate());

        AiConversationScopeMode mode = inferScopeMode(req);

        GbAiConversationEntity conv = gbAiChatService.createNewConversationForAgentRun(
                req.getDepartmentId(), req.getDistributerId(), mode, req.getUserId(), 0);
        long conversationId = conv.getGbAiConversationId();

        List<AiHarnessReplayExpectedRound> expectations = resolveExpectations(req, today);
        boolean strict = req.isStrictStoreSqlMatch();

        List<AiHarnessReplayRoundResult> rounds = new ArrayList<>();
        boolean allPass = true;

        for (int i = 0; i < req.getMessages().size(); i++) {
            String msg = req.getMessages().get(i);
            if (!StringUtils.hasText(msg)) {
                AiHarnessReplayRoundResult skip = AiHarnessReplayRoundResult.builder()
                        .roundIndex(i + 1)
                        .message("")
                        .runId(-1)
                        .conversationId(conversationId)
                        .pass(true)
                        .resolvedQueryContextSummary(new LinkedHashMap<>())
                        .failedFields(List.of())
                        .build();
                rounds.add(skip);
                continue;
            }

            AiRunCreateRequest runReq = new AiRunCreateRequest();
            runReq.setUserId(req.getUserId());
            runReq.setDepartmentId(req.getDepartmentId());
            runReq.setDistributerId(req.getDistributerId());
            runReq.setConversationId(conversationId);
            runReq.setMessage(msg.trim());
            if (StringUtils.hasText(req.getScopeMode())) {
                runReq.setScopeMode(req.getScopeMode());
            }

            long runId = sessionRegistry.nextRunId();
            var uc = userContextResolver.resolve(runReq);

            var resolved = resolvedQueryContextResolver.resolve(runId, runReq, uc, today);
            resolved.setRunId(runId);

            LinkedHashMap<String, Object> summary = new LinkedHashMap<>(
                    AiHarnessResolvedContextSummarizer.summarize(resolved, conversationId));

            List<AiHarnessMismatch> failed = List.of();
            if (expectations != null && i < expectations.size()) {
                failed = AiHarnessExpectationComparator.compare(summary, expectations.get(i), strict);
            }

            AiConversationTurnMemory turn = AiConversationTurnMemory.fromHarnessReplayStep(resolved, conversationId, runId);
            conversationMemoryService.rememberCompletedTurn(req.getUserId(), conversationId, turn);

            boolean pass = failed.isEmpty();
            if (!pass) {
                allPass = false;
            }

            rounds.add(AiHarnessReplayRoundResult.builder()
                    .roundIndex(i + 1)
                    .message(msg.trim())
                    .runId(runId)
                    .conversationId(conversationId)
                    .resolvedQueryContextSummary(summary)
                    .pass(pass)
                    .failedFields(new ArrayList<>(failed))
                    .build());
        }

        return AiHarnessReplayResponse.builder()
                .conversationId(conversationId)
                .overallPass(allPass)
                .frozenClockDate(today.toString())
                .caseId(req.getCaseId())
                .rounds(rounds)
                .build();
    }

    private static LocalDate resolveToday(String frozenClockDate) {
        if (!StringUtils.hasText(frozenClockDate)) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(frozenClockDate.trim());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("invalid frozenClockDate (yyyy-MM-dd): " + frozenClockDate);
        }
    }

    private static AiConversationScopeMode inferScopeMode(AiHarnessReplayRequest req) {
        if (StringUtils.hasText(req.getScopeMode())) {
            return AiConversationScopeMode.fromApiString(req.getScopeMode());
        }
        if (req.getDepartmentId() != null) {
            return AiConversationScopeMode.STORE;
        }
        if (req.getDistributerId() != null) {
            return AiConversationScopeMode.GROUP;
        }
        throw new IllegalArgumentException("创建会话需要 departmentId（单店）或 distributerId（集团），或 scopeMode");
    }

    private List<AiHarnessReplayExpectedRound> resolveExpectations(AiHarnessReplayRequest req, LocalDate today) {
        if (req.getExpectations() != null && !req.getExpectations().isEmpty()) {
            return req.getExpectations();
        }
        if (!StringUtils.hasText(req.getCaseId())) {
            return null;
        }
        if (AiHarnessBuiltinCases.PURCHASE_MULTITURN_1.equals(req.getCaseId().trim())) {
            var anchor = AiHarnessBuiltinCases.LocalDateAnchor.frozenClock(today);
            int n = AiHarnessBuiltinCases.expectationsPurchaseMultiturn1(anchor).size();
            if (req.getMessages().size() < n) {
                log.warn(
                        "[AiHarnessReplay] case={} expects {} rounds, got {}",
                        req.getCaseId(),
                        n,
                        req.getMessages().size());
            }
            return AiHarnessBuiltinCases.expectationsPurchaseMultiturn1(anchor);
        }
        throw new IllegalArgumentException("unknown harness caseId: " + req.getCaseId());
    }
}
