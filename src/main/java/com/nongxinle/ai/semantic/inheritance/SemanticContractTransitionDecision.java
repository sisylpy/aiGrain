package com.nongxinle.ai.semantic.inheritance;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Value
@Builder(toBuilder = true)
public class SemanticContractTransitionDecision {

    SemanticContractTransitionType transitionType;
    String effectiveContractId;
    List<String> preservedFields;
    List<String> overriddenFields;
    String reasonCode;
    SemanticSlotInheritanceMode inheritanceMode;
    String followUpPath;
    boolean suppressPreviousDishAnchor;
    String targetContractId;

    Map<String, Object> trace;

    public boolean isRegisteredLegalTransition() {
        return transitionType != null
                && transitionType != SemanticContractTransitionType.NONE
                && transitionType != SemanticContractTransitionType.SOVEREIGN_NEW_CAPABILITY
                && transitionType != SemanticContractTransitionType.CROSS_FAMILY_CONTEXT_ONLY
                && transitionType != SemanticContractTransitionType.EXPLICIT_ENTITY_NEW_CAPABILITY;
    }

    /** 为 true 时 V2 弱选合同不得夺取 Business Frame 主权。 */
    public boolean suppressesV2BusinessFrameSovereignty() {
        return isRegisteredLegalTransition();
    }

    public boolean currentTurnHasSovereignBusinessFrame() {
        return !suppressesV2BusinessFrameSovereignty()
                && (transitionType == SemanticContractTransitionType.SOVEREIGN_NEW_CAPABILITY
                        || transitionType == SemanticContractTransitionType.CROSS_FAMILY_CONTEXT_ONLY
                        || transitionType == SemanticContractTransitionType.EXPLICIT_ENTITY_NEW_CAPABILITY);
    }
}
