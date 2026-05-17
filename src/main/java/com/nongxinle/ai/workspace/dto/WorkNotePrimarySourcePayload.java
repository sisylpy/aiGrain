package com.nongxinle.ai.workspace.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 前端「保存为笔记」入参：可与顶层扁平字段互补；服务端仅填补空缺字段，并据此推断 {@code noteType}。
 */
@Data
@Schema(description = "嵌套主来源（可选）；RUN/MESSAGE/SELECTION 等锚点字段")
public class WorkNotePrimarySourcePayload {

    @Schema(description = "RUN / MESSAGE / SELECTION，或已是 MANUAL / FROM_RUN / …")
    private String sourceType;

    private String conversationId;

    private String runId;

    private String messageId;

    /** 顶层 title 为空时可用来补足 */
    private String sourceTitle;

    private String sourceTextSnapshot;

    private String sourceAnswerPreview;

    private String sourceCreatedAt;

    private String sourceRole;
}
