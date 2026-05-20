package com.nongxinle.ai.history.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiConversationListItemDTO {

    private Long conversationId;

    /** 会话标题（占位会从首条用户消息推导） */
    private String title;

    /** 与 gb_ai_conversation.gb_ai_conversation_status 一致：0 进行中，1 已结束 */
    private Integer conversationStatus;

    private boolean archived;

    private Long departmentId;

    private Long distributerId;

    private Integer scopeMode;

    private Boolean pinned;

    private String updatedAt;

    /** 助理最新消息 gb_ai_message_status；若无助理消息则回退 gb_ai_agent_run.status（若有 last_run_id） */
    private String lastRunStatus;

    /** 列表摘要：优先最近助理正文节选，否则首条用户消息节选 */
    private String previewText;

    private List<AiConversationTagBriefDTO> tags = new ArrayList<>();

    private List<AiConversationNotebookBriefDTO> notebooks = new ArrayList<>();

    private AiConversationNoteSummaryDTO noteSummary;
}
