package com.nongxinle.ai.workspace.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.nongxinle.ai.workspace.json.FlexibleLongDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "创建图钉；支持顶层扁平字段，或与 sourceSnapshot 嵌套组合（空缺项由嵌套补齐）")
public class WorkPinCreateRequest {

    /** V1 与 AiRunCreateRequest 一致可由客户端传入 */
    @JsonDeserialize(using = FlexibleLongDeserializer.class)
    private Long userId;

    @JsonDeserialize(using = FlexibleLongDeserializer.class)
    private Long conversationId;

    @JsonDeserialize(using = FlexibleLongDeserializer.class)
    private Long runId;

    @JsonDeserialize(using = FlexibleLongDeserializer.class)
    private Long messageId;

    private String title;

    /** RUN / MESSAGE / SELECTION */
    private String sourceType;

    /** 必填 */
    private String sourceTextSnapshot;

    private String sourceAnswerPreview;

    private String sourceRole;

    private String sourceAgentName;

    /** ISO-8601 字符串可选；服务端不强校验格式时可尝试解析 */
    private String sourceCreatedAt;

    /** 前端 PinNote 等结构：来源字段可集中在此对象内 */
    private WorkPinSourceSnapshotPayload sourceSnapshot;
}
