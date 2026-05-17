package com.nongxinle.ai.history.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiConversationPinMutationDTO {

    private boolean pinned;

    /** true 表示记录已存在，本次未插入（幂等） */
    private boolean duplicated;
}
