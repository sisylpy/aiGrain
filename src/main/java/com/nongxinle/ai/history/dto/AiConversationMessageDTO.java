package com.nongxinle.ai.history.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiConversationMessageDTO {

    private Long messageId;

    private String role;

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

    /**
     * 轻量上下文（contextBar / store / time / scope）；assistant 消息持久化，
     * 与 Run/SSE {@code result.contextSummary} 字段一致。
     */
    private Map<String, Object> contextSummary;

    /** 结构化卡片（如 {@code DISH_COST_ANALYSIS_CARD}）；Run Session 仍驻内存时可回填 */
    private Map<String, Object> cardPayload;

    /** 兼容 {@code cards[0]} 读取路径 */
    private List<Map<String, Object>> cards;
}
