package com.nongxinle.ai.conversation;

import com.nongxinle.ai.scope.AiConversationScopeMode;
import com.nongxinle.entity.GbAiConversationEntity;
import com.nongxinle.entity.GbAiMessageEntity;

import java.util.List;

/**
 * 会话与消息的纯仓储能力（创建、归属校验、按会话读消息、顾问线程幂等会话），
 * 不包含单 Agent Skill / DeepSeek / 旧 prompt 拼接。
 */
public interface AiConversationCoreService {

    /**
     * 多智能体 Run 首轮：无条件插入新会话（不复用未结束旧主题），字段规则与同 scope 下历史新建一致。
     */
    GbAiConversationEntity createNewConversationForAgentRun(
            Long departmentId,
            Long distributerId,
            AiConversationScopeMode scopeMode,
            Long userId);

    /**
     * 校验会话存在且 {@code gb_ai_conversation_user_id} 与 {@code userId} 一致。
     */
    GbAiConversationEntity requireConversationOwnedByUser(Long conversationId, Long userId);

    List<GbAiMessageEntity> getConversationMessages(Long conversationId);

    /**
     * 顾问长期会话：按 userId + advisorId + 组织锚点幂等查找或插入。
     */
    GbAiConversationEntity getOrCreateAdvisorConversation(
            Long advisorId,
            String conversationTitle,
            Long departmentId,
            Long distributerId,
            AiConversationScopeMode scopeMode,
            Long userId);

    /**
     * 店长工作记录长期会话：按 userId + threadKind=WORK_RECORD + 组织锚点幂等查找或插入。
     */
    GbAiConversationEntity getOrCreateWorkRecordConversation(
            Long departmentId,
            Long distributerId,
            AiConversationScopeMode scopeMode,
            Long userId);
}
