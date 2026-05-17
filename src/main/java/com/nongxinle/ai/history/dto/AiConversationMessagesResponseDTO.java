package com.nongxinle.ai.history.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiConversationMessagesResponseDTO {

    private Long conversationId;

    private List<AiConversationMessageDTO> messages = new ArrayList<>();
}
