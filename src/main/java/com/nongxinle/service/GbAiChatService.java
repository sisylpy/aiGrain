package com.nongxinle.service;

import com.nongxinle.entity.GbAiConversationEntity;
import com.nongxinle.entity.GbAiMessageEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * AI对话服务 - 核心引擎
 */
public interface GbAiChatService {

    /**
     * 创建或恢复对话
     * @param departmentId 部门/餐厅ID
     * @param userId 部门用户ID
     * @param type 对话类型 (0=普通聊天, 1=促销活动/销售额, 2=公众号相关)
     * @return 对话实体
     */
    GbAiConversationEntity getOrCreateConversation(Long departmentId, Long userId, Integer type);

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
     * 结束对话（触发记忆提取）
     * @param conversationId 对话ID
     */
    void endConversation(Long conversationId);

    /**
     * 使用DeepSeek总结对话并提取记忆
     * @param conversationId 对话ID
     * @return 总结结果字符串（JSON格式）
     */
    String summarizeConversation(Long conversationId);
}
