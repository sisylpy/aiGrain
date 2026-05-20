package com.nongxinle.ai.harness.followup;

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
public class BusinessFollowUpSlot {

    private boolean followUp;
    private String normalizedUserMessage;
    /**
     * 如 GOODS_DETAIL、GOODS_UNIT_PRICE、SUPPLIER_UNIT_PRICE；与 {@link com.nongxinle.ai.context.AiResolvedQueryContext#getFollowUpDetailWanted()} 可对齐。
     */
    private String slotDetailWanted;

    /** 与 {@link com.nongxinle.ai.semantic.frame.CurrentSemanticFrame} 对齐的镜像字段（LLM semanticSlots）；Registry 只做匹配，不写回意图。 */
    private String semanticQueryObject;
    private String semanticOperation;
    private String semanticMetric;
    private String semanticSourceFacet;
    private String semanticAnchorPolicy;
    private String semanticStructuredIntentDetailWire;
}
