package com.nongxinle.ai.history.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.nongxinle.ai.workspace.json.FlexibleLongDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "会话打标签：tagId 与 tagName 二选一；tagName 会在当前用户下按名称唯一查找或创建")
public class AiConversationTagAttachRequest {

    @JsonDeserialize(using = FlexibleLongDeserializer.class)
    private Long userId;

    @JsonDeserialize(using = FlexibleLongDeserializer.class)
    private Long tagId;

    private String tagName;

    private String tagColor;
}
