package com.nongxinle.ai.history.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天区某条 assistant 消息「保存为工作笔记」（{@code gb_ai_work_note}，primary_message_id）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiMessageNoteResponseDTO {

    private boolean noted;

    private Long noteId;

    private boolean duplicated;
}
