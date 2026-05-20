package com.nongxinle.ai.harness.followup;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 命中能力后的结构化下钻请求（Phase 1：采购；供 Debug / 可选意图补全）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessDrilldownRequest {

    private BusinessCapabilityMatch match;
    private BusinessContextFrame frame;
    private BusinessFollowUpSlot slot;

    /** 若应用意图补全，建议写入 Context 的 followUpAction；否则 null。 */
    private String proposedFollowUpAction;
    /** 若应用意图补全，建议写入 Context 的 followUpDetailWanted。 */
    private String proposedFollowUpDetailWanted;
    private String proposedFollowUpSourcePlanType;
}
