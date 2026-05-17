package com.nongxinle.ai.workspace.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkPinResponse {

    private Long id;

    private Long userId;

    private Long conversationId;

    private Long runId;

    private Long messageId;

    private String title;

    private String sourceType;

    /** 列表与详情皆可有；详情接口另含 {@link #sourceTextSnapshot} */
    private String sourceAnswerPreview;

    private String sourceRole;

    private String sourceAgentName;

    private Date sourceCreatedAt;

    private Date createdAt;

    private Date updatedAt;

    /** 仅详情 true 时序列化由 Controller 统一控制：此处字段可为 null */
    private String sourceTextSnapshot;
}
