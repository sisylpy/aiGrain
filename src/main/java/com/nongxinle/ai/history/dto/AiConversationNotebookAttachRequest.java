package com.nongxinle.ai.history.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.nongxinle.ai.workspace.json.FlexibleLongDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "会话归入笔记本：notebookId 与 notebookName 二选一；notebookName 按用户唯一查找或创建")
public class AiConversationNotebookAttachRequest {

    @JsonDeserialize(using = FlexibleLongDeserializer.class)
    private Long userId;

    @JsonDeserialize(using = FlexibleLongDeserializer.class)
    private Long notebookId;

    private String notebookName;

    private String notebookDescription;
}
