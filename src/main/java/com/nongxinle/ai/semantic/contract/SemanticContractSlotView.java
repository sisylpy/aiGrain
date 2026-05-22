package com.nongxinle.ai.semantic.contract;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import lombok.experimental.UtilityClass;

/**
 * ContractValidator 只读槽位视图：委托 {@link EffectiveSemanticContractFrame}，避免 raw parse 路径差异误报。
 */
@UtilityClass
public final class SemanticContractSlotView {

    public static SemanticCapabilityContractMatcher.SlotSnapshot forDomain(
            AiQuerySemanticParseResult parse, String domain) {
        EffectiveSemanticContractFrame frame =
                EffectiveSemanticContractFrame.of(parse, domain, null, null, null);
        return frame != null ? frame.slotSnapshot() : SemanticCapabilityContractMatcher.SlotSnapshot.empty();
    }

    /** 带 previousTurn / rewrite anchor 的完整 effective 视图。 */
    public static SemanticCapabilityContractMatcher.SlotSnapshot forDomain(
            AiQuerySemanticParseResult parse,
            String domain,
            AiConversationTurnMemory previousTurn,
            String rewriteInheritedAnchorType,
            String rewriteInheritedAnchorName) {
        EffectiveSemanticContractFrame frame =
                EffectiveSemanticContractFrame.of(
                        parse,
                        domain,
                        previousTurn,
                        rewriteInheritedAnchorType,
                        rewriteInheritedAnchorName);
        return frame != null ? frame.slotSnapshot() : SemanticCapabilityContractMatcher.SlotSnapshot.empty();
    }
}
