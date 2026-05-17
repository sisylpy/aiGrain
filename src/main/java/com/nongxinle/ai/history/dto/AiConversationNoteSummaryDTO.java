package com.nongxinle.ai.history.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiConversationNoteSummaryDTO {

    /** 是否存在可作摘要展示的笔记（标题或正文非空） */
    private boolean hasSummary;

    private Long latestNoteId;

    private String latestTitle;
}
