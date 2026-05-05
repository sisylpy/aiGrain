package com.nongxinle.service;

import com.nongxinle.entity.GbAiConversationEntity;
import com.nongxinle.entity.GbAiMessageEntity;
import com.nongxinle.ai.scope.AiConversationScopeMode;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * AI对话服务 - 核心引擎
 */
public interface GbAiChatService {

    /**
     * 创建或恢复对话（单店默认）
     */
    default GbAiConversationEntity getOrCreateConversation(Long departmentId, Long userId, Integer type) {
        return getOrCreateConversation(departmentId, null, AiConversationScopeMode.STORE, userId, type);
    }

    /**
     * 创建或恢复对话
     * @param departmentId 单店必填：门店父部门 ID；集团模式可 null
     * @param distributerId 集团必填：批发商/集团 disId；单店可选（未传则从部门反推）
     * @param scopeMode {@link AiConversationScopeMode#STORE} 或 {@link AiConversationScopeMode#GROUP}
     */
    GbAiConversationEntity getOrCreateConversation(Long departmentId, Long distributerId, AiConversationScopeMode scopeMode,
                                                   Long userId, Integer type);

    /**
     * 发送消息（非流式，用于测试）
     * @param conversationId 对话ID
     * @param userId 部门用户ID
     * @param userMessage 用户消息
     * @return AI回复消息
     */
    GbAiMessageEntity chat(Long conversationId, Long userId, String userMessage);

    /**
     * 发送消息（SSE流式）
     * @param conversationId 对话ID
     * @param userId 部门用户ID
     * @param userMessage 用户消息
     * @return SseEmitter
     */
    SseEmitter streamChat(Long conversationId, Long userId, String userMessage);

    /**
     * 获取对话历史
     * @param conversationId 对话ID
     * @return 消息列表
     */
    List<GbAiMessageEntity> getConversationMessages(Long conversationId);

    /**
     * 按用户获取历史聊天主题（会话）列表
     * @param userId 用户ID
     * @return 会话主题列表（按更新时间倒序）
     */
    List<GbAiConversationEntity> getUserConversationTopics(Long userId);

    /**
     * 按主题ID获取详细聊天内容（主题ID即 conversationId）
     * @param topicId 主题ID
     * @return 消息列表
     */
    List<GbAiMessageEntity> getTopicMessages(Long topicId);

    /**
     * 结束对话（有实质消息时触发记忆与总结；空对话不保存、不调模型）
     * @param conversationId 对话ID
     * @return 0=会话不存在；1=已结束但无内容未保存；2=已结束并已记忆/总结
     */
    int endConversation(Long conversationId);

    /**
     * 使用DeepSeek总结对话并提取记忆
     * @param conversationId 对话ID
     * @return 总结结果字符串（JSON格式）
     */
    String summarizeConversation(Long conversationId);
}
