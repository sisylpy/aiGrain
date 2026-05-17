package com.nongxinle.ai.history;

import com.nongxinle.ai.history.dto.*;

/**
 * 会话历史列表、消息详情、会话级置顶/标签/笔记本（与工作区 gb_ai_work_pin 区分）。
 *
 * <p><b>多智能体 Run 与 gb_ai_message：</b>{@code /api/ai/runs} 链路当前不向 {@code gb_ai_message}
 * 写入用户消息与助手最终答复；{@link #listMessages(Long, Long)} 仅返回库内已有行。<b>Run 消息落库 gb_ai_message</b>
 * 为后续单独任务（不改 Harness/SSE）。</p>
 *
 * <p><b>标签 / 笔记本权限边界：</b>除 {@code userId} 外，标签与笔记本名称唯一键包含会话上的批发商锚点与部门锚点
 * （空字段按 0 存储），避免同一 department_user ID 在多租户复用时互相串标签。</p>
 */
public interface AiConversationHistoryService {

    AiConversationListResponseDTO listConversations(Long userId,
                                                    Long departmentId,
                                                    Long distributerId,
                                                    String keyword,
                                                    String status,
                                                    boolean includeArchived,
                                                    Long tagId,
                                                    Long notebookId,
                                                    Boolean pinnedOnly,
                                                    int page,
                                                    int pageSize);

    AiConversationMessagesResponseDTO listMessages(Long conversationId, Long userId);

    AiConversationPinMutationDTO pinConversation(Long conversationId, Long userId);

    void unpinConversation(Long conversationId, Long userId);

    AiConversationTagMutationDTO attachTag(Long conversationId, AiConversationTagAttachRequest body);

    void detachTag(Long conversationId, Long userId, Long tagId);

    AiConversationNotebookMutationDTO attachNotebook(Long conversationId, AiConversationNotebookAttachRequest body);

    void detachNotebook(Long conversationId, Long userId, Long notebookId);
}
