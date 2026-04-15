package com.nongxinle.service;

import com.nongxinle.entity.GbAiMemoryEntity;

import java.util.List;

/**
 * 记忆系统服务
 * L1：餐厅画像
 * L2：经验记忆（自动提取 + 按需检索）
 * L3：会话记忆（由 ChatService 管理历史消息）
 * L4：AutoDream（后台记忆整理）
 */
public interface GbAiMemoryService {

    /**
     * 检索相关记忆
     * @param departmentId 部门/餐厅ID
     * @param query 用户当前输入（用于关键词匹配，可为null则返回最重要的记忆）
     * @param limit 返回条数
     */
    List<GbAiMemoryEntity> retrieveRelevantMemories(Long departmentId, String query, int limit);

    /**
     * 从对话中提取记忆（对话结束时调用）
     * @param conversationId 对话ID
     * @param departmentId 部门ID
     * @param userId 部门用户ID
     * @param type 记忆类型 (0=普通记忆, 1=促销活动/销售额, 2=公众号相关)
     */
    void extractMemories(Long conversationId, Long departmentId, Long userId, Integer type);

    /**
     * AutoDream：后台整理记忆（定时任务调用）
     */
    void autoDream();

    /**
     * 保存DeepSeek总结的对话记忆
     * @param conversationId 对话ID
     * @param departmentId 部门ID
     * @param userId 部门用户ID
     * @param summaryResult DeepSeek返回的JSON总结结果
     */
    void saveConversationSummary(Long conversationId, Long departmentId, Long userId, String summaryResult);
}
