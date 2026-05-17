package com.nongxinle.service;

import com.nongxinle.ai.workspace.dto.PromotePinToNoteRequest;
import com.nongxinle.ai.history.dto.AiMessagePinResponseDTO;
import com.nongxinle.ai.workspace.dto.WorkPinCreateRequest;
import com.nongxinle.ai.workspace.dto.WorkPinResponse;
import com.nongxinle.ai.workspace.dto.WorkNoteResponse;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface GbAiWorkPinService {

    WorkPinResponse createPin(WorkPinCreateRequest request);

    List<WorkPinResponse> listPins(Long conversationId, Long userId);

    WorkPinResponse getPinDetail(Long pinId, Long userId);

    void softDeletePin(Long pinId, Long userId);

    WorkNoteResponse promotePinToNote(Long pinId, PromotePinToNoteRequest request);

    /**
     * 聊天区：钉住一条 assistant 消息（{@code sourceType=MESSAGE}）；幂等 userId+messageId。
     */
    AiMessagePinResponseDTO pinAssistantMessage(Long userId, Long messageId);

    /**
     * 聊天区：按消息取消图钉（软删）；无记录时幂等 {@code pinned=false}。
     */
    AiMessagePinResponseDTO unpinAssistantMessage(Long userId, Long messageId);

    /**
     * 批量解析会话内活跃 MESSAGE 图钉（单查询）；返回 messageId → pinId。
     */
    Map<Long, Long> mapActivePinIdsForMessages(Long userId, Long conversationId, Collection<Long> messageIds);
}
