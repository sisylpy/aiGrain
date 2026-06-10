package com.nongxinle.ai.semantic.frame;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.dto.business.PurchaseGoodsBusinessAnalysisAnswerPlan;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.matrix.PurchaseSemanticCapabilityMatrix;
import org.springframework.util.StringUtils;

/**
 * 采购 contract-locked 语义帧只读投影；Planner / ToolRequest / Execution 主链 SSOT。
 */
public final class PurchaseLockedSemanticFrameSupport {

    private PurchaseLockedSemanticFrameSupport() {}

    public static ContractLockedSemanticFrame lockedFrame(AiResolvedQueryContext ctx) {
        if (ctx == null) {
            return null;
        }
        if (ctx.getContractLockedFrame() != null) {
            return ctx.getContractLockedFrame();
        }
        if (ctx.getQuerySemanticParse() != null) {
            return ctx.getQuerySemanticParse().getContractLockedFrame();
        }
        return null;
    }

    public static String selectedContractId(ContractLockedSemanticFrame frame) {
        if (frame == null || frame.getContractFields() == null) {
            return null;
        }
        return trim(frame.getContractFields().getSelectedContractId());
    }

    public static String canonicalWire(ContractLockedSemanticFrame frame) {
        if (frame == null || frame.getContractFields() == null) {
            return null;
        }
        return trim(frame.getContractFields().getCanonicalStructuredIntentDetailWire());
    }

    public static String answerPlanType(ContractLockedSemanticFrame frame) {
        if (frame == null || frame.getContractFields() == null) {
            return null;
        }
        return trim(frame.getContractFields().getAnswerPlanType());
    }

    public static AiQuerySemanticParseResult.SemanticSlotsPart businessSlots(ContractLockedSemanticFrame frame) {
        if (frame == null || frame.getBusinessSlots() == null) {
            return null;
        }
        return frame.getBusinessSlots().getSemanticSlots();
    }

    public static String queryObject(ContractLockedSemanticFrame frame) {
        AiQuerySemanticParseResult.SemanticSlotsPart slots = businessSlots(frame);
        return slots != null ? trim(slots.getQueryObject()) : null;
    }

    public static String operation(ContractLockedSemanticFrame frame) {
        AiQuerySemanticParseResult.SemanticSlotsPart slots = businessSlots(frame);
        return slots != null ? trim(slots.getOperation()) : null;
    }

    public static String sourceFacet(ContractLockedSemanticFrame frame) {
        AiQuerySemanticParseResult.SemanticSlotsPart slots = businessSlots(frame);
        return slots != null ? trim(slots.getSourceFacet()) : null;
    }

    public static String detailWanted(ContractLockedSemanticFrame frame) {
        AiQuerySemanticParseResult.SemanticSlotsPart slots = businessSlots(frame);
        return slots != null ? trim(slots.getDetailWanted()) : null;
    }

    public static String anchorPolicy(ContractLockedSemanticFrame frame) {
        AiQuerySemanticParseResult.SemanticSlotsPart slots = businessSlots(frame);
        return slots != null ? trim(slots.getAnchorPolicy()) : null;
    }

    public static String mentionedGoodsName(ContractLockedSemanticFrame frame) {
        if (frame != null
                && frame.getEntitySlots() != null
                && StringUtils.hasText(frame.getEntitySlots().getMentionedGoodsName())) {
            return frame.getEntitySlots().getMentionedGoodsName().trim();
        }
        AiQuerySemanticParseResult.SemanticSlotsPart slots = businessSlots(frame);
        return slots != null ? trim(slots.getMentionedGoodsName()) : null;
    }

    public static boolean isGoodsAnchorSourceBreakdown(ContractLockedSemanticFrame frame) {
        return PurchaseSemanticCapabilityMatrix.SOURCE_BREAKDOWN
                .getCapabilityId()
                .equals(selectedContractId(frame));
    }

    public static boolean isGoodsAnchorContract(ContractLockedSemanticFrame frame) {
        String contractId = selectedContractId(frame);
        if (!StringUtils.hasText(contractId)) {
            return false;
        }
        for (var row : PurchaseSemanticCapabilityMatrix.goodsAnchorRows()) {
            if (contractId.equals(row.getCapabilityId())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isGoodsBusinessAnalysis(ContractLockedSemanticFrame frame) {
        return PurchaseGoodsBusinessAnalysisAnswerPlan.CONTRACT_ID.equals(selectedContractId(frame));
    }

    public static boolean isPeriodGoodsList(ContractLockedSemanticFrame frame) {
        String contractId = selectedContractId(frame);
        if (!StringUtils.hasText(contractId)) {
            return false;
        }
        return switch (contractId) {
            case "purchase.period_goods_list",
                    "purchase.period_goods_list.self",
                    "purchase.period_goods_list.supplier" -> true;
            default -> false;
        };
    }

    private static String trim(String raw) {
        return StringUtils.hasText(raw) ? raw.trim() : null;
    }
}
