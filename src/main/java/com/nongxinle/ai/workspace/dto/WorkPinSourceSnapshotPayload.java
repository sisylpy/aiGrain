package com.nongxinle.ai.workspace.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 前端 PinNote 入参：与顶层扁平字段二选一或互补；服务端会将空缺字段从本对象合并到顶层后再校验。
 */
@Data
@Schema(description = "嵌套来源快照（可选）；可与顶层 sourceType/runId/sourceTextSnapshot 等互补")
public class WorkPinSourceSnapshotPayload {

    @Schema(description = "RUN / MESSAGE / SELECTION")
    private String sourceType;

    /** 字符串兼容前端 JSON 数字串 */
    private String conversationId;

    private String runId;

    private String messageId;

    /** 可用于补足顶层 title（仅当顶层 title 为空时写入） */
    private String sourceTitle;

    private String sourceTextSnapshot;

    private String sourceAnswerPreview;

    private String sourceCreatedAt;

    private String sourceRole;

    private String sourceAgentName;
}
