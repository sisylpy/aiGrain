package com.nongxinle.service;

import com.nongxinle.ai.history.dto.AiMessageNoteResponseDTO;
import com.nongxinle.ai.workspace.dto.WorkNoteCreateRequest;
import com.nongxinle.ai.workspace.dto.WorkNoteResponse;
import com.nongxinle.ai.workspace.dto.WorkNoteUpdateRequest;
import com.nongxinle.ai.workspace.dto.PromotePinToNoteRequest;
import com.nongxinle.entity.GbAiWorkPinEntity;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface GbAiWorkNoteService {

    WorkNoteResponse createNote(WorkNoteCreateRequest request);

    List<WorkNoteResponse> listNotes(Long conversationId, Long userId);

    WorkNoteResponse getNoteDetail(Long noteId, Long userId);

    WorkNoteResponse updateNote(Long noteId, WorkNoteUpdateRequest request);

    void softDeleteNote(Long noteId, Long userId);

    WorkNoteResponse createFromPromotedPin(GbAiWorkPinEntity pin, PromotePinToNoteRequest request);

    /**
     * 将一条 assistant 消息保存为工作笔记（幂等：同一 user + primary_message_id + deleted=0 视为重复）。
     */
    AiMessageNoteResponseDTO saveNoteFromAssistantMessage(Long userId, Long messageId);

    /**
     * 本会话下多条消息的活跃笔记 id（user + conversation + deleted=0 + primary_message_id IN）。
     */
    Map<Long, Long> mapActiveNoteIdsForMessages(Long userId, Long conversationId, Collection<Long> messageIds);
}
