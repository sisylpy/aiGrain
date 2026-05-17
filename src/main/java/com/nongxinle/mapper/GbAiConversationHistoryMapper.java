package com.nongxinle.mapper;

import com.nongxinle.entity.GbAiMessageEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 会话历史列表分页与批量消息摘要（避免 N+1）。
 */
@Mapper
public interface GbAiConversationHistoryMapper {

    long countConversationList(@Param("userId") Long userId,
                               @Param("departmentId") Long departmentId,
                               @Param("distributerId") Long distributerId,
                               @Param("keyword") String keyword,
                               @Param("lastRunStatus") String lastRunStatus,
                               @Param("includeArchived") boolean includeArchived,
                               @Param("tagId") Long tagId,
                               @Param("notebookId") Long notebookId,
                               @Param("pinned") Boolean pinned);

    List<Long> selectConversationIds(@Param("userId") Long userId,
                                     @Param("departmentId") Long departmentId,
                                     @Param("distributerId") Long distributerId,
                                     @Param("keyword") String keyword,
                                     @Param("lastRunStatus") String lastRunStatus,
                                     @Param("includeArchived") boolean includeArchived,
                                     @Param("tagId") Long tagId,
                                     @Param("notebookId") Long notebookId,
                                     @Param("pinned") Boolean pinned,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);

    List<GbAiMessageEntity> selectFirstUserMessagePerConversation(@Param("ids") List<Long> conversationIds);

    List<GbAiMessageEntity> selectLatestAssistantMessagePerConversation(@Param("ids") List<Long> conversationIds);
}
