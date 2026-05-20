package com.nongxinle.ai.history.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 顾问会话恢复：单次返回会话锚点与同构消息列表。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiAdvisorConversationBootstrapDTO {

    private Long advisorId;

    private Long conversationId;

    /**
     * 与 {@link com.nongxinle.entity.GbAiConversationEntity#getGbAiConversationThreadKind()} 对齐的展示字段；
     * 顾问恢复场景固定 {@code ADVISOR}。
     */
    private String conversationType;

    private String threadKind;

    private String title;

    private List<AiConversationMessageDTO> messages = new ArrayList<>();
}
