package com.nongxinle.ai.graph.business.execution;

import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.PurchaseGoodsBusinessAnalysisAnswerPlan;
import com.nongxinle.ai.semantic.frame.ContractLockedSemanticFrame;
import com.nongxinle.ai.semantic.frame.PurchaseLockedSemanticFrameSupport;
import com.nongxinle.ai.semantic.matrix.PurchaseSemanticCapabilityMatrix;
import org.springframework.util.StringUtils;

/**
 * 从 {@link ContractLockedSemanticFrame} 投影采购 execution intent type / tool detail key；
 * 不做 Matrix 反查或 wire 推导。
 */
public final class PurchaseSemanticExecutionProjection {

    private PurchaseSemanticExecutionProjection() {}

    public static String executionIntentType(ContractLockedSemanticFrame frame) {
        if (frame == null) {
            return PurchaseSemanticExecutionIntent.EXEC_NONE;
        }
        String contractId = PurchaseLockedSemanticFrameSupport.selectedContractId(frame);
        if (!StringUtils.hasText(contractId)) {
            return PurchaseSemanticExecutionIntent.EXEC_NONE;
        }
        if (PurchaseLockedSemanticFrameSupport.isGoodsAnchorSourceBreakdown(frame)) {
            return PurchaseSemanticExecutionIntent.EXEC_GOODS_SOURCE_BREAKDOWN;
        }
        if (PurchaseSemanticCapabilityMatrix.SUPPLIER_BREAKDOWN.getCapabilityId().equals(contractId)) {
            return PurchaseSemanticExecutionIntent.EXEC_GOODS_SUPPLIER_BREAKDOWN;
        }
        if (PurchaseSemanticCapabilityMatrix.SUPPLIER_UNIT_PRICE.getCapabilityId().equals(contractId)) {
            return PurchaseSemanticExecutionIntent.EXEC_GOODS_SUPPLIER_UNIT_PRICE;
        }
        if (PurchaseGoodsBusinessAnalysisAnswerPlan.CONTRACT_ID.equals(contractId)) {
            return PurchaseSemanticExecutionIntent.EXEC_GOODS_BUSINESS_ANALYSIS;
        }
        if (PurchaseLockedSemanticFrameSupport.isPeriodGoodsList(frame)) {
            return PurchaseSemanticExecutionIntent.EXEC_PERIOD_GOODS_LIST;
        }
        String detailWanted = PurchaseLockedSemanticFrameSupport.detailWanted(frame);
        if (AiQuerySemanticLexicon.DETAIL_WANTED_SOURCE_BREAKDOWN.equals(detailWanted)) {
            return PurchaseSemanticExecutionIntent.EXEC_GOODS_SOURCE_BREAKDOWN;
        }
        if (AiQuerySemanticLexicon.DETAIL_WANTED_SUPPLIER_BREAKDOWN.equals(detailWanted)) {
            return PurchaseSemanticExecutionIntent.EXEC_GOODS_SUPPLIER_BREAKDOWN;
        }
        return PurchaseSemanticExecutionIntent.EXEC_NONE;
    }

    public static String toolDetailWantedKey(ContractLockedSemanticFrame frame) {
        if (frame == null) {
            return null;
        }
        if (PurchaseLockedSemanticFrameSupport.isGoodsAnchorSourceBreakdown(frame)) {
            return AiQuerySemanticLexicon.DETAIL_WANTED_SOURCE_BREAKDOWN;
        }
        String contractId = PurchaseLockedSemanticFrameSupport.selectedContractId(frame);
        if (PurchaseSemanticCapabilityMatrix.SUPPLIER_BREAKDOWN.getCapabilityId().equals(contractId)
                || PurchaseSemanticCapabilityMatrix.SUPPLIER_UNIT_PRICE.getCapabilityId().equals(contractId)) {
            return AiQuerySemanticLexicon.DETAIL_WANTED_SUPPLIER_UNIT_PRICE;
        }
        if (PurchaseLockedSemanticFrameSupport.isPeriodGoodsList(frame)) {
            return "PERIOD_GOODS_LIST";
        }
        return PurchaseLockedSemanticFrameSupport.detailWanted(frame);
    }
}
