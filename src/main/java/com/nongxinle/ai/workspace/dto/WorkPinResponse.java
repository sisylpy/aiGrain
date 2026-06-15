package com.nongxinle.ai.workspace.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkPinResponse {

    /** 详情返回：pinId；列表返回沿用 id 兼容 */
    private Long id;

    /** 详情接口偏好字段名 pinId */
    private Long pinId;

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

    /** 仅详情包含完整 cards[]（已解析为标准结构，列表不返回） */
    private List<Map<String, Object>> cards;

    /** 仅详情包含完整 cards JSON 原始字符串快照（列表不返回） */
    private String cardsSnapshotJson;

    /** 首张业务卡的 cardType */
    private String primaryCardType;

    /** 业务卡片数量 */
    private Integer cardCount;
}
