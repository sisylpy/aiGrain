package com.nongxinle.ai.semantic.routing;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import lombok.Builder;
import lombok.Value;

/** {@link SemanticDomainRouter} 输入。 */
@Value
@Builder
public class SemanticDomainRouterInput {

    /** Rewrite 后的有效问句；Router 只看此文本 + previousTurn。 */
    String rewrittenUserMessage;

    /** 上一轮 TurnMemory（可为 null）；用于 pathCode 继承。 */
    AiConversationTurnMemory previousTurn;
}
