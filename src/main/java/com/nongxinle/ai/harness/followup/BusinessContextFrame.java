package com.nongxinle.ai.harness.followup;

import com.nongxinle.ai.dto.business.AiResultAnchor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 上一轮可追问语境帧（derived-only，不替代 TurnMemory）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessContextFrame {

    private String lastPathCode;
    private String lastStructuredIntentDetailWire;
    private String lastPurchaseSourceType;
    /**
     * 与 {@link com.nongxinle.ai.graph.business.PurchaseAnswerPlanBuilder#resolvePlanType} 对齐的上一轮等价计划类型。
     */
    private String framePlanType;
    private boolean purchasePath;

    @Builder.Default
    private List<AiResultAnchor> previousResultAnchors = new ArrayList<>();
}
