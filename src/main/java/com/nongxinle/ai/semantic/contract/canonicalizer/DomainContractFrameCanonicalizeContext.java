package com.nongxinle.ai.semantic.contract.canonicalizer;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import lombok.Builder;
import lombok.Value;

/** Contract validation 前 domain canonicalize 入参（parse + 追问锚点上下文）。 */
@Value
@Builder
public class DomainContractFrameCanonicalizeContext {

    String selectedDomain;
    AiQuerySemanticParseResult parse;
    AiConversationTurnMemory previousTurn;
    String rewriteInheritedAnchorType;
    String rewriteInheritedAnchorName;
}
