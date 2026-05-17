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
     * 与 {@link com.nongxinle.entity.GbAiConversationEntity#getGbAiConversationThreadKind()} 语义一致，
     * 顾问长期线程固定 {@code ADVISOR}。（与整数 {@code gbAiConversationType} 无关。）
     */
    private String conversationType;

    private String threadKind;

    private String title;

    private List<AiConversationMessageDTO> messages = new ArrayList<>();
}
