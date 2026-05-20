package com.nongxinle.ai.semantic;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import org.springframework.util.StringUtils;

/**
 * 读取 {@link AiConversationTurnMemory} 中上一轮已持久化的统计起止日，供 {@link SemanticTimeContractCheck} 校验
 * {@code INHERITED_PREVIOUS} 时是否存在可继承区间。
 * <p>
 * 不负责推断当前轮时间窗或 {@code effectiveTimeWindowSource}。
 */
public final class TimeContractPreviousTurnSupport {

    private TimeContractPreviousTurnSupport() {
    }

    public static boolean hasTurnMemoryDates(AiConversationTurnMemory previousTurn) {
        return previousTurn != null
                && StringUtils.hasText(previousTurn.getLastStartDate())
                && StringUtils.hasText(previousTurn.getLastEndDate());
    }
}
