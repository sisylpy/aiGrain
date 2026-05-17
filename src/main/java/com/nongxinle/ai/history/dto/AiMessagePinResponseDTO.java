package com.nongxinle.ai.history.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天区答案级图钉（{@code gb_ai_work_pin}，与工作区创建接口并存）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiMessagePinResponseDTO {

    private boolean pinned;

    /** POST 成功或幂等命中时返回；DELETE 可为 null */
    private Long pinId;

    /** 仅 POST：重复钉时为 true；DELETE 为 null */
    private Boolean duplicated;
}
