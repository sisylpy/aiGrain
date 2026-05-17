package com.nongxinle.ai.history.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiConversationTagMutationDTO {

    private Long tagId;

    /** true 表示关联已存在，本次未插入（幂等） */
    private boolean duplicated;
}
