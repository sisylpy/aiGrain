package com.nongxinle.ai.graph.business.execution;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.contract.SemanticContractValidationDebug;
import com.nongxinle.ai.semantic.frame.CurrentSemanticFrame;
import com.nongxinle.ai.semantic.matrix.PurchaseSemanticCapabilityMatrix;
import com.nongxinle.ai.semantic.matrix.PurchaseSemanticCapabilityMatrixRow;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 从 {@link AiResolvedQueryContext} 解析采购 contract-driven execution intent（P4-B）。
 * <p>优先级：{@code semanticContractValidation.matchedContractId} → frame/matrix 形状 → anchorPolicy / resultAnchors。
 */
public final class PurchaseSemanticExecutionIntentResolver {

    private PurchaseSemanticExecutionIntentResolver() {}

    public static PurchaseSemanticExecutionIntent resolve(AiResolvedQueryContext rq) {
        if (rq == null) {
            return PurchaseSemanticExecutionIntent.none();
        }
        CurrentSemanticFrame frame =
                CurrentSemanticFrame.fromParseResult(rq.getQuerySemanticParse(), rq.getPreviousTurn());
        String matchedContractId = matchedContractId(rq);
        PurchaseSemanticExecutionIntent fromContract = fromMatchedContract(matchedContractId, frame, rq);
        if (fromContract != null && fromContract.isActive()) {
            return fromContract;
        }
        PurchaseSemanticExecutionIntent fromFrame = fromFrameAndMatrix(frame, rq);
        if (fromFrame != null && fromFrame.isActive()) {
            return fromFrame;
        }
        PurchaseSemanticExecutionIntent fromSupplierAnchor = fromSupplierAnchorGoodsLines(frame, rq);
        if (fromSupplierAnchor != null && fromSupplierAnchor.isActive()) {
            return fromSupplierAnchor;
        }
        PurchaseSemanticExecutionIntent fromChannel = fromChannelGoodsDetail(frame, rq);
        if (fromChannel != null && fromChannel.isActive()) {
            return fromChannel;
        }
        return PurchaseSemanticExecutionIntent.none();
    }

    private static String matchedContractId(AiResolvedQueryContext rq) {
        SemanticContractValidationDebug v = rq.getSemanticContractValidation();
        if (v == null || !StringUtils.hasText(v.getMatchedContractId())) {
            return null;
        }
        return v.getMatchedContractId().trim();
    }

    private static PurchaseSemanticExecutionIntent fromMatchedContract(
            String contractId, CurrentSemanticFrame frame, AiResolvedQueryContext rq) {
        if (!StringUtils.hasText(contractId)) {
            return null;
        }
        PurchaseSemanticCapabilityMatrixRow row = matrixRowForContractId(contractId.trim());
        if (row == null) {
            return null;
        }
        return buildGoodsAnchorIntent(row, frame, rq, contractId.trim());
    }

    private static PurchaseSemanticExecutionIntent fromFrameAndMatrix(
            CurrentSemanticFrame frame, AiResolvedQueryContext rq) {
        if (frame == null || !StringUtils.hasText(frame.getDetailWanted())) {
            return null;
        }
        PurchaseSemanticCapabilityMatrixRow row =
                PurchaseSemanticCapabilityMatrix.findByDetailWanted(frame.getDetailWanted());
        if (row == null) {
            return null;
        }
        if (!matchesGoodsAnchorFrame(row, frame)) {
            return null;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart slots = semanticSlots(rq);
        if (slots != null
                && rq.getQuerySemanticParse() != null
                && !PurchaseSemanticCapabilityMatrix.slotsInferRowShape(
                        rq.getQuerySemanticParse(), row)) {
            return null;
        }
        return buildGoodsAnchorIntent(row, frame, rq, row.getCapabilityId());
    }

    private static boolean matchesGoodsAnchorFrame(
            PurchaseSemanticCapabilityMatrixRow row, CurrentSemanticFrame frame) {
        String wire = frame.getStructuredIntentDetailWire();
        if (!StringUtils.hasText(wire)
                || !AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY.equals(wire)) {
            return false;
        }
        String sf = frame.getSourceFacet();
        if (row.getRequiredSourceFacet() != null
                && sf != null
                && !row.getRequiredSourceFacet().equalsIgnoreCase(sf.trim())) {
            return false;
        }
        return row.getRequiredDetailWanted().equalsIgnoreCase(frame.getDetailWanted().trim());
    }

    private static PurchaseSemanticExecutionIntent buildGoodsAnchorIntent(
            PurchaseSemanticCapabilityMatrixRow row,
            CurrentSemanticFrame frame,
            AiResolvedQueryContext rq,
            String matchedContractId) {
        GoodsAnchor anchor = resolveGoodsAnchor(rq);
        String execType = executionTypeForRow(row);
        String toolKey = toolDetailWantedKeyForRow(row);
        return PurchaseSemanticExecutionIntent.builder()
                .matchedContractId(matchedContractId)
                .wire(frame.getStructuredIntentDetailWire())
                .queryObject(frame.getQueryObject())
                .operation(frame.getOperation())
                .detailWanted(frame.getDetailWanted())
                .sourceFacet(frame.getSourceFacet())
                .answerPlanType(row.getTargetPurchasePlanType())
                .focusGoodsId(anchor.entityId())
                .focusGoodsName(anchor.entityName())
                .anchorType(AiResultAnchor.ENTITY_TYPE_GOODS)
                .anchorResolved(anchor.resolved())
                .executionIntentType(execType)
                .toolDetailWantedKey(toolKey)
                .build();
    }

    private static PurchaseSemanticExecutionIntent fromSupplierAnchorGoodsLines(
            CurrentSemanticFrame frame, AiResolvedQueryContext rq) {
        if (frame == null
                || !"GOODS_UNIT_PRICE".equalsIgnoreCase(nullToEmpty(frame.getDetailWanted()))) {
            return null;
        }
        String wire = frame.getStructuredIntentDetailWire();
        if (!AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY.equals(wire)) {
            return null;
        }
        if (!AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE.equalsIgnoreCase(
                nullToEmpty(frame.getSourceFacet()))) {
            return null;
        }
        Integer supplierId = resolveSupplierRankingTop1Id(rq);
        if (supplierId == null) {
            return null;
        }
        return PurchaseSemanticExecutionIntent.builder()
                .wire(wire)
                .queryObject(frame.getQueryObject())
                .operation(frame.getOperation())
                .detailWanted(frame.getDetailWanted())
                .sourceFacet(frame.getSourceFacet())
                .answerPlanType(PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL)
                .focusSupplierId(supplierId)
                .anchorType(AiResultAnchor.ENTITY_TYPE_SUPPLIER)
                .anchorResolved(true)
                .executionIntentType(PurchaseSemanticExecutionIntent.EXEC_SUPPLIER_ANCHOR_GOODS_LINES)
                .toolDetailWantedKey("GOODS_UNIT_PRICE")
                .build();
    }

    private static PurchaseSemanticExecutionIntent fromChannelGoodsDetail(
            CurrentSemanticFrame frame, AiResolvedQueryContext rq) {
        if (frame == null || !"GOODS_DETAIL".equalsIgnoreCase(nullToEmpty(frame.getDetailWanted()))) {
            return null;
        }
        AiResolvedQueryIntent qi = rq.getQueryIntent();
        if (qi == null) {
            return null;
        }
        String pst = qi.getPurchaseSourceType();
        if (!StringUtils.hasText(pst)) {
            return null;
        }
        String pstTrim = pst.trim();
        boolean supplier =
                AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE.equalsIgnoreCase(pstTrim);
        boolean self = AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE.equalsIgnoreCase(pstTrim);
        if (!supplier && !self) {
            return null;
        }
        String planType =
                supplier
                        ? PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL
                        : PurchaseAnswerPlan.TYPE_PURCHASE_SELF_GOODS_DETAIL;
        return PurchaseSemanticExecutionIntent.builder()
                .wire(frame.getStructuredIntentDetailWire())
                .detailWanted(frame.getDetailWanted())
                .sourceFacet(pstTrim)
                .answerPlanType(planType)
                .anchorResolved(false)
                .executionIntentType(PurchaseSemanticExecutionIntent.EXEC_CHANNEL_GOODS_DETAIL)
                .toolDetailWantedKey("GOODS_DETAIL")
                .build();
    }

    private static PurchaseSemanticCapabilityMatrixRow matrixRowForContractId(String contractId) {
        for (PurchaseSemanticCapabilityMatrixRow row : PurchaseSemanticCapabilityMatrix.goodsAnchorRows()) {
            if (contractId.equals(row.getCapabilityId())) {
                return row;
            }
        }
        return null;
    }

    private static String executionTypeForRow(PurchaseSemanticCapabilityMatrixRow row) {
        if (row == PurchaseSemanticCapabilityMatrix.SOURCE_BREAKDOWN) {
            return PurchaseSemanticExecutionIntent.EXEC_GOODS_SOURCE_BREAKDOWN;
        }
        if (row == PurchaseSemanticCapabilityMatrix.SUPPLIER_BREAKDOWN) {
            return PurchaseSemanticExecutionIntent.EXEC_GOODS_SUPPLIER_BREAKDOWN;
        }
        if (row == PurchaseSemanticCapabilityMatrix.SUPPLIER_UNIT_PRICE) {
            return PurchaseSemanticExecutionIntent.EXEC_GOODS_SUPPLIER_UNIT_PRICE;
        }
        String dw = row.getRequiredDetailWanted();
        if (AiQuerySemanticLexicon.DETAIL_WANTED_SOURCE_BREAKDOWN.equals(dw)) {
            return PurchaseSemanticExecutionIntent.EXEC_GOODS_SOURCE_BREAKDOWN;
        }
        if (AiQuerySemanticLexicon.DETAIL_WANTED_SUPPLIER_BREAKDOWN.equals(dw)) {
            return PurchaseSemanticExecutionIntent.EXEC_GOODS_SUPPLIER_BREAKDOWN;
        }
        return PurchaseSemanticExecutionIntent.EXEC_GOODS_SUPPLIER_UNIT_PRICE;
    }

    private static String toolDetailWantedKeyForRow(PurchaseSemanticCapabilityMatrixRow row) {
        if (row == PurchaseSemanticCapabilityMatrix.SOURCE_BREAKDOWN) {
            return AiQuerySemanticLexicon.DETAIL_WANTED_SOURCE_BREAKDOWN;
        }
        if (row == PurchaseSemanticCapabilityMatrix.SUPPLIER_BREAKDOWN
                || row == PurchaseSemanticCapabilityMatrix.SUPPLIER_UNIT_PRICE) {
            return AiQuerySemanticLexicon.DETAIL_WANTED_SUPPLIER_UNIT_PRICE;
        }
        return row.getRequiredDetailWanted();
    }

    static GoodsAnchor resolveGoodsAnchor(AiResolvedQueryContext rq) {
        if (rq == null) {
            return GoodsAnchor.unresolved();
        }
        AiConversationTurnMemory prev = rq.getPreviousTurn();
        if (prev != null && prev.getLastResultAnchors() != null) {
            for (AiResultAnchor a : prev.getLastResultAnchors()) {
                if (a == null || !StringUtils.hasText(a.getEntityType())) {
                    continue;
                }
                if (!AiResultAnchor.ENTITY_TYPE_GOODS.equalsIgnoreCase(a.getEntityType().trim())) {
                    continue;
                }
                String id = blankToNull(a.getEntityId());
                String name = blankToNull(a.getEntityName());
                if (id != null || name != null) {
                    return new GoodsAnchor(id, name, true);
                }
            }
        }
        String rewriteName = blankToNull(rq.getRewriteInheritedAnchorName());
        if (rewriteName != null) {
            return new GoodsAnchor(null, rewriteName, true);
        }
        return GoodsAnchor.unresolved();
    }

    private static Integer resolveSupplierRankingTop1Id(AiResolvedQueryContext rq) {
        AiConversationTurnMemory prev = rq == null ? null : rq.getPreviousTurn();
        List<AiResultAnchor> anchors = prev == null ? null : prev.getLastResultAnchors();
        if (anchors == null || anchors.isEmpty()) {
            return null;
        }
        for (AiResultAnchor a : anchors) {
            if (a == null || !StringUtils.hasText(a.getEntityType())) {
                continue;
            }
            if (!AiResultAnchor.ENTITY_TYPE_SUPPLIER.equalsIgnoreCase(a.getEntityType().trim())) {
                continue;
            }
            if (!PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_AMOUNT_RANKING.equals(
                    nullToEmpty(a.getSourcePlanType()))) {
                continue;
            }
            Integer rk = a.getRank();
            boolean rankOne = rk != null && rk == 1;
            boolean singleUnranked = rk == null && anchors.size() == 1;
            if (!(rankOne || singleUnranked)) {
                continue;
            }
            Integer id = parsePositiveInt(a.getEntityId());
            if (id != null) {
                return id;
            }
        }
        return null;
    }

    private static AiQuerySemanticParseResult.SemanticSlotsPart semanticSlots(AiResolvedQueryContext rq) {
        if (rq == null || rq.getQuerySemanticParse() == null) {
            return null;
        }
        return rq.getQuerySemanticParse().getSemanticSlots();
    }

    private static String blankToNull(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static Integer parsePositiveInt(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            int v = Integer.parseInt(raw.trim());
            return v > 0 ? v : null;
        } catch (Exception e) {
            return null;
        }
    }

    record GoodsAnchor(String entityId, String entityName, boolean resolved) {
        static GoodsAnchor unresolved() {
            return new GoodsAnchor(null, null, false);
        }
    }
}
