package com.nongxinle.ai.workspace.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkNoteResponse {

    private Long id;

    private Long userId;

    private Long conversationId;

    private String title;

    private String content;

    private String noteType;

    private String primarySourceType;

    private Long primaryConversationId;

    private Long primaryRunId;

    private Long primaryMessageId;

    private String sourceAnswerPreview;

    private Date createdAt;

    private Date updatedAt;

    /** 列表为 null；详情返回 */
    private String sourceTextSnapshot;
}
