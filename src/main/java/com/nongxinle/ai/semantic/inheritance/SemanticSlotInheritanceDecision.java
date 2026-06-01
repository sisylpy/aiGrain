package com.nongxinle.ai.semantic.inheritance;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Value
@Builder(toBuilder = true)
public class SemanticSlotInheritanceDecision {
    SemanticSlotInheritanceMode mode;
    String reasonCode;
    String reasonDetail;

    String currentContractId;
    String previousContractId;
    String currentFamily;
    String previousFamily;
    String currentDomain;
    boolean currentHasSovereignActiveContract;
    boolean structuredTimeFollowUp;
    boolean crossFamily;
    boolean explicitEntityFollowUp;
    /** Applier：禁止 reconcileExplicitCurrentTurnDishAnchor 从 previous 拉菜名。 */
    boolean suppressPreviousDishAnchor;
    /** 裸维度切换：从 plan 派生的目标 contract（非 previousContractId）。 */
    String targetContractId;

    Map<String, Object> trace;
}
