package com.nongxinle.ai.graph.business.execution;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.semantic.contract.SemanticContractValidationDebug;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import com.nongxinle.ai.semantic.frame.CurrentSemanticFrame;
import com.nongxinle.ai.semantic.matrix.PurchaseSemanticCapabilityMatrix;
import com.nongxinle.ai.semantic.matrix.PurchaseSemanticCapabilityMatrixRow;
import org.springframework.util.StringUtils;

/**
 * 从 {@link AiResolvedQueryContext} 解析采购 contract-driven execution intent（P4-B / P2-J）。
 * <p>Tool Request 主链仅允许：
 * <ul>
 *   <li>{@code contractEntryValidated=true} + {@code selectedContractId} → contract entry 映射（source=contract_entry）</li>
 *   <li>否则 → {@link PurchaseSemanticExecutionIntent#none()}（source=unresolved）；structuredIntentDetail wire 由
 *   {@link com.nongxinle.ai.graph.business.PurchaseOverviewToolExecutor} pass-through，不在此推导 execution intent。</li>
 * </ul>
 * 禁止 Matrix {@code findByDetailWanted} / {@code slotsInferRowShape} 及非 contract 的 anchor/ranking 推导影响 Tool args。
 */
public final class PurchaseSemanticExecutionIntentResolver {

    public static final String RESOLUTION_SOURCE_CONTRACT_ENTRY = "contract_entry";
    public static final String RESOLUTION_SOURCE_UNRESOLVED = "unresolved";

    private PurchaseSemanticExecutionIntentResolver() {}

    public static PurchaseSemanticExecutionIntent resolve(AiResolvedQueryContext rq) {
        if (rq == null) {
            return PurchaseSemanticExecutionIntent.none();
        }
        if (!SemanticContractCompletionEngine.isContractLockedParse(rq.getQuerySemanticParse())) {
            return PurchaseSemanticExecutionIntent.none();
        }
        CurrentSemanticFrame frame =
                CurrentSemanticFrame.fromParseResult(rq.getQuerySemanticParse(), rq.getPreviousTurn());
        String matchedContractId = matchedContractId(rq);
        PurchaseSemanticExecutionIntent fromContract = fromMatchedContract(matchedContractId, frame, rq);
        if (fromContract != null && fromContract.isActive()) {
            return fromContract;
        }
        return PurchaseSemanticExecutionIntent.none();
    }

    private static String matchedContractId(AiResolvedQueryContext rq) {
        String fromSlots = selectedContractIdFromSemanticSlots(rq);
        if (StringUtils.hasText(fromSlots)) {
            return fromSlots.trim();
        }
        SemanticContractValidationDebug v = rq.getSemanticContractValidation();
        if (v == null || !StringUtils.hasText(v.getMatchedContractId())) {
            return null;
        }
        return v.getMatchedContractId().trim();
    }

    private static String selectedContractIdFromSemanticSlots(AiResolvedQueryContext rq) {
        if (rq == null || rq.getQuerySemanticParse() == null || rq.getQuerySemanticParse().getSemanticSlots() == null) {
            return null;
        }
        return SemanticContractCompletionEngine.extractSelectedContractId(rq.getQuerySemanticParse());
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
        return withResolutionSource(
                buildGoodsAnchorIntent(row, frame, rq, contractId.trim()), RESOLUTION_SOURCE_CONTRACT_ENTRY);
    }

    private static PurchaseSemanticExecutionIntent withResolutionSource(
            PurchaseSemanticExecutionIntent intent, String source) {
        if (intent == null) {
            return null;
        }
        return PurchaseSemanticExecutionIntent.builder()
                .matchedContractId(intent.getMatchedContractId())
                .wire(intent.getWire())
                .queryObject(intent.getQueryObject())
                .operation(intent.getOperation())
                .detailWanted(intent.getDetailWanted())
                .sourceFacet(intent.getSourceFacet())
                .answerPlanType(intent.getAnswerPlanType())
                .focusGoodsId(intent.getFocusGoodsId())
                .focusGoodsName(intent.getFocusGoodsName())
                .focusSupplierId(intent.getFocusSupplierId())
                .anchorType(intent.getAnchorType())
                .anchorResolved(intent.isAnchorResolved())
                .executionIntentType(intent.getExecutionIntentType())
                .toolDetailWantedKey(intent.getToolDetailWantedKey())
                .resolutionSource(source)
                .build();
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
                .wire(frame != null ? frame.getStructuredIntentDetailWire() : null)
                .queryObject(frame != null ? frame.getQueryObject() : null)
                .operation(frame != null ? frame.getOperation() : null)
                .detailWanted(frame != null ? frame.getDetailWanted() : null)
                .sourceFacet(frame != null ? frame.getSourceFacet() : null)
                .answerPlanType(row.getTargetPurchasePlanType())
                .focusGoodsId(anchor.entityId())
                .focusGoodsName(anchor.entityName())
                .anchorType(AiResultAnchor.ENTITY_TYPE_GOODS)
                .anchorResolved(anchor.resolved())
                .executionIntentType(execType)
                .toolDetailWantedKey(toolKey)
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

    private static String blankToNull(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    record GoodsAnchor(String entityId, String entityName, boolean resolved) {
        static GoodsAnchor unresolved() {
            return new GoodsAnchor(null, null, false);
        }
    }
}
