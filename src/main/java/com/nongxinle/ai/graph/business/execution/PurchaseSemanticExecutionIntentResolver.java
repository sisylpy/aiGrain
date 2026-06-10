package com.nongxinle.ai.graph.business.execution;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.identity.BusinessEntityIdentityBridge;
import com.nongxinle.ai.identity.BusinessEntityIdentityGoodsProjection;
import com.nongxinle.ai.identity.EntityIdentityResolutionStatus;
import com.nongxinle.ai.identity.ResolvedEntityIdentity;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.dto.business.PurchaseGoodsBusinessAnalysisAnswerPlan;
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
        return resolve(rq, null);
    }

    public static PurchaseSemanticExecutionIntent resolve(
            AiResolvedQueryContext rq, Integer distributerIdHint) {
        if (rq == null) {
            return PurchaseSemanticExecutionIntent.none();
        }
        if (!SemanticContractCompletionEngine.isContractLockedParse(rq.getQuerySemanticParse())) {
            return PurchaseSemanticExecutionIntent.none();
        }
        CurrentSemanticFrame frame =
                CurrentSemanticFrame.fromParseResult(rq.getQuerySemanticParse(), rq.getPreviousTurn());
        String matchedContractId = matchedContractId(rq);
        PurchaseSemanticExecutionIntent fromContract =
                fromMatchedContract(matchedContractId, frame, rq, distributerIdHint);
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
            String contractId, CurrentSemanticFrame frame, AiResolvedQueryContext rq, Integer distributerIdHint) {
        if (!StringUtils.hasText(contractId)) {
            return null;
        }
        String trimmed = contractId.trim();
        PurchaseSemanticCapabilityMatrixRow row = matrixRowForContractId(trimmed);
        if (row != null) {
            return withResolutionSource(
                    buildGoodsAnchorIntent(row, frame, rq, trimmed, distributerIdHint),
                    RESOLUTION_SOURCE_CONTRACT_ENTRY);
        }
        if (PurchaseGoodsBusinessAnalysisAnswerPlan.CONTRACT_ID.equals(trimmed)) {
            return withResolutionSource(
                    buildGoodsBusinessAnalysisIntent(frame, rq, trimmed, distributerIdHint),
                    RESOLUTION_SOURCE_CONTRACT_ENTRY);
        }
        PurchaseSemanticExecutionIntent catalog = fromCatalogContract(trimmed, frame, rq, distributerIdHint);
        if (catalog != null) {
            return withResolutionSource(catalog, RESOLUTION_SOURCE_CONTRACT_ENTRY);
        }
        return null;
    }

    private static PurchaseSemanticExecutionIntent fromCatalogContract(
            String contractId,
            CurrentSemanticFrame frame,
            AiResolvedQueryContext rq,
            Integer distributerIdHint) {
        if (!isPeriodGoodsListContractId(contractId)) {
            return null;
        }
        if (rq != null && resolveGoodsAnchor(rq, distributerIdHint).resolved()) {
            return null;
        }
        String defaultFacet = defaultSourceFacetForPeriodGoodsListContract(contractId);
        String sourceFacet =
                frame != null && StringUtils.hasText(frame.getSourceFacet())
                        ? frame.getSourceFacet().trim()
                        : defaultFacet;
        return PurchaseSemanticExecutionIntent.builder()
                .matchedContractId(contractId)
                .wire(frame != null ? frame.getStructuredIntentDetailWire() : null)
                .queryObject(frame != null ? frame.getQueryObject() : "GOODS")
                .operation(frame != null ? frame.getOperation() : "DETAIL")
                .detailWanted(frame != null ? frame.getDetailWanted() : null)
                .sourceFacet(sourceFacet)
                .answerPlanType(PurchaseAnswerPlan.TYPE_PURCHASE_PERIOD_GOODS_DETAIL)
                .anchorResolved(false)
                .executionIntentType(PurchaseSemanticExecutionIntent.EXEC_PERIOD_GOODS_LIST)
                .toolDetailWantedKey("PERIOD_GOODS_LIST")
                .build();
    }

    public static boolean isPeriodGoodsListContractId(String contractId) {
        if (!StringUtils.hasText(contractId)) {
            return false;
        }
        return switch (contractId.trim()) {
            case "purchase.period_goods_list",
                    "purchase.period_goods_list.self",
                    "purchase.period_goods_list.supplier" -> true;
            default -> false;
        };
    }

    /** contract {@code purchase.goods_anchor.source_breakdown} → 单商品逐笔采购明细卡主链。 */
    public static boolean isGoodsAnchorSourceBreakdownContractId(String contractId) {
        if (!StringUtils.hasText(contractId)) {
            return false;
        }
        return PurchaseSemanticCapabilityMatrix.SOURCE_BREAKDOWN.getCapabilityId().equals(contractId.trim());
    }

    private static String defaultSourceFacetForPeriodGoodsListContract(String contractId) {
        return switch (contractId.trim()) {
            case "purchase.period_goods_list.self" -> AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE;
            case "purchase.period_goods_list.supplier" -> AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE;
            default -> AiQuerySemanticLexicon.SOURCE_ALL;
        };
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
            String matchedContractId,
            Integer distributerIdHint) {
        GoodsAnchor anchor = resolveGoodsAnchor(rq, distributerIdHint);
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

    private static PurchaseSemanticExecutionIntent buildGoodsBusinessAnalysisIntent(
            CurrentSemanticFrame frame, AiResolvedQueryContext rq, String matchedContractId, Integer distributerIdHint) {
        GoodsAnchor anchor = resolveGoodsAnchor(rq, distributerIdHint);
        return PurchaseSemanticExecutionIntent.builder()
                .matchedContractId(matchedContractId)
                .wire(frame != null ? frame.getStructuredIntentDetailWire() : null)
                .queryObject(frame != null ? frame.getQueryObject() : "GOODS")
                .operation(frame != null ? frame.getOperation() : "ANALYSIS")
                .detailWanted(frame != null ? frame.getDetailWanted() : null)
                .sourceFacet(frame != null ? frame.getSourceFacet() : null)
                .answerPlanType(PurchaseGoodsBusinessAnalysisAnswerPlan.TYPE)
                .focusGoodsId(anchor.entityId())
                .focusGoodsName(anchor.entityName())
                .anchorType(AiResultAnchor.ENTITY_TYPE_GOODS)
                .anchorResolved(anchor.resolved())
                .executionIntentType(PurchaseSemanticExecutionIntent.EXEC_GOODS_BUSINESS_ANALYSIS)
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
        return resolveGoodsAnchor(rq, null);
    }

    static GoodsAnchor resolveGoodsAnchor(AiResolvedQueryContext rq, Integer distributerIdHint) {
        if (rq == null) {
            return GoodsAnchor.unresolved();
        }
        ResolvedEntityIdentity identity = BusinessEntityIdentityBridge.resolveGoods(rq, distributerIdHint);
        if (identity.getResolutionStatus() != EntityIdentityResolutionStatus.OK) {
            return GoodsAnchor.unresolved();
        }
        Integer disGoodsId = BusinessEntityIdentityGoodsProjection.executionDisGoodsId(identity);
        String name = BusinessEntityIdentityGoodsProjection.executionGoodsNameHint(identity);
        if (disGoodsId == null && !StringUtils.hasText(name)) {
            return GoodsAnchor.unresolved();
        }
        String id = disGoodsId != null ? String.valueOf(disGoodsId) : null;
        return new GoodsAnchor(id, name, true);
    }

    record GoodsAnchor(String entityId, String entityName, boolean resolved) {
        static GoodsAnchor unresolved() {
            return new GoodsAnchor(null, null, false);
        }
    }
}
