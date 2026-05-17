package com.nongxinle.ai.history.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiConversationMessageDTO {

    private Long messageId;

    private String role;

    private Integer messageType;

    private String content;

    /** PENDING / RUNNING / COMPLETED / FAILED；可为 null */
    private String status;

    private Long runId;

    private String createdAt;

    private String updatedAt;

    /** 与 {@link #createdAt} 同源，兼容顾问会话等接口契约 */
    private String createTime;

    /** 与 {@link #updatedAt} 同源 */
    private String updateTime;

    /** 是否已钉（仅 assistant；{@code gb_ai_work_pin}，与会话置顶无关） */
    private boolean pinned;

    /** 活跃图钉主键；未钉时为 null */
    private Long pinId;

    /** 是否已由本条消息保存过工作笔记（{@code gb_ai_work_note}，与会话 noteSummary 无关） */
    private boolean noted;

    /** 活跃笔记主键；未保存时为 null */
    private Long noteId;
}
