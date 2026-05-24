package com.nongxinle.ai.semantic.capability;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 本轮追问槽：从用户消息与语义解析抽象出的「明细诉求」。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticCapabilitySlot {

    private boolean followUp;
    private String normalizedUserMessage;
    /**
     * 如 GOODS_DETAIL、GOODS_UNIT_PRICE、SUPPLIER_UNIT_PRICE；与 semanticSlots.detailWanted / executionDetailWanted 对齐。
     */
    private String slotDetailWanted;

    /** 与 {@link com.nongxinle.ai.semantic.frame.CurrentSemanticFrame} 对齐的镜像字段（LLM semanticSlots）。 */
    private String semanticQueryObject;
    private String semanticOperation;
    private String semanticMetric;
    private String semanticSourceFacet;
    private String semanticAnchorPolicy;
    private String semanticStructuredIntentDetailWire;
}
