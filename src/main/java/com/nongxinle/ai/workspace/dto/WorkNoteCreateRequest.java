package com.nongxinle.ai.workspace.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.nongxinle.ai.workspace.json.FlexibleLongDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "创建工作笔记；支持顶层字段或与 primarySource 嵌套互补（空缺项由嵌套补齐，并可推断 noteType）")
public class WorkNoteCreateRequest {

    @JsonDeserialize(using = FlexibleLongDeserializer.class)
    private Long userId;

    /** MANUAL 时可 null */
    @JsonDeserialize(using = FlexibleLongDeserializer.class)
    private Long conversationId;

    private String title;

    /** 允许 ""，不得 null（服务端 null→""） */
    private String content;

    /** MANUAL / FROM_RUN / FROM_PIN / FROM_SELECTION；缺省时可根据 primarySource.sourceType（如 RUN）推断 */
    private String noteType;

    /** 可选；为空时由服务端按 noteType 推导；可与嵌套 RUN/MESSAGE 等对齐 */
    private String primarySourceType;

    @JsonDeserialize(using = FlexibleLongDeserializer.class)
    private Long primaryConversationId;

    @JsonDeserialize(using = FlexibleLongDeserializer.class)
    private Long primaryRunId;

    @JsonDeserialize(using = FlexibleLongDeserializer.class)
    private Long primaryMessageId;

    private String sourceTextSnapshot;

    private String sourceAnswerPreview;

    /** 前端保存为笔记时的嵌套锚点与快照 */
    private WorkNotePrimarySourcePayload primarySource;
}
