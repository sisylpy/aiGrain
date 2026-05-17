package com.nongxinle.ai.advisor;

import com.nongxinle.ai.history.AiConversationHistoryService;
import com.nongxinle.ai.history.dto.AiAdvisorConversationBootstrapDTO;
import com.nongxinle.ai.history.dto.AiConversationMessageDTO;
import com.nongxinle.ai.history.dto.AiConversationMessagesResponseDTO;
import com.nongxinle.ai.scope.AiConversationScopeMode;
import com.nongxinle.entity.GbAiAdvisorEntity;
import com.nongxinle.entity.GbAiConversationEntity;
import com.nongxinle.mapper.GbAiAdvisorMapper;
import com.nongxinle.ai.conversation.AiConversationCoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiAdvisorConversationServiceImpl implements AiAdvisorConversationService {

    private final GbAiAdvisorMapper advisorMapper;
    private final AiConversationCoreService conversationCoreService;
    private final AiConversationHistoryService conversationHistoryService;

    @Override
    public AiAdvisorConversationBootstrapDTO getOrBootstrap(
            Long advisorId, Long userId, Long departmentId, Long distributerId, String scopeMode) {

        GbAiAdvisorEntity advisor = requireEnabledAdvisor(advisorId);
        if (userId == null) {
            throw new IllegalArgumentException("userId required");
        }

        AiConversationScopeMode mode = inferScope(scopeMode, departmentId, distributerId);

        String title = buildConversationTitle(advisor);
        GbAiConversationEntity conv =
                conversationCoreService.getOrCreateAdvisorConversation(
                        advisorId, title, departmentId, distributerId, mode, userId);

        AiConversationMessagesResponseDTO msgPkg =
                conversationHistoryService.listMessages(conv.getGbAiConversationId(), userId);

        AiAdvisorConversationBootstrapDTO out =
                new AiAdvisorConversationBootstrapDTO();
        out.setAdvisorId(advisorId);
        out.setConversationId(conv.getGbAiConversationId());
        out.setConversationType(AiAdvisorConversationConstants.THREAD_KIND_ADVISOR);
        out.setThreadKind(AiAdvisorConversationConstants.THREAD_KIND_ADVISOR);
        out.setTitle(conv.getGbAiConversationTitle());
        List<AiConversationMessageDTO> msgs = msgPkg != null ? msgPkg.getMessages() : List.of();
        out.setMessages(msgs != null ? msgs : List.of());
        return out;
    }

    private GbAiAdvisorEntity requireEnabledAdvisor(Long advisorId) {
        if (advisorId == null) {
            throw new IllegalArgumentException("advisorId required");
        }
        GbAiAdvisorEntity row = advisorMapper.selectById(advisorId);
        if (row == null || row.getGbAiAdvisorEnabled() == null || row.getGbAiAdvisorEnabled() != 1) {
            throw new IllegalArgumentException("advisor not found or disabled: " + advisorId);
        }
        return row;
    }

    /**
     * 与 {@link com.nongxinle.ai.platform.AiRunService} 会话创建语义对齐：scopeMode / departmentId /
     * distributerId。
     */
    private static AiConversationScopeMode inferScope(String scopeRaw, Long departmentId, Long distributerId) {
        if (StringUtils.hasText(scopeRaw)) {
            return AiConversationScopeMode.fromApiString(scopeRaw);
        }
        if (departmentId != null) {
            return AiConversationScopeMode.STORE;
        }
        if (distributerId != null) {
            return AiConversationScopeMode.GROUP;
        }
        throw new IllegalArgumentException(
                "创建顾问会话需要提供 scopeMode，或 departmentId（单店），或 distributerId（集团）");
    }

    private static String buildConversationTitle(GbAiAdvisorEntity advisor) {
        String base = advisor.getGbAiAdvisorName();
        String t = base != null && !base.isBlank() ? base + " · 顾问对话" : "顾问对话";
        if (t.length() > 255) {
            return t.substring(0, 255);
        }
        return t;
    }
}
