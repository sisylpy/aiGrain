package com.nongxinle.ai.graph.business.execution;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.identity.BusinessEntityIdentityBridge;
import com.nongxinle.ai.identity.BusinessEntityIdentityGoodsProjection;
import com.nongxinle.ai.identity.EntityIdentityResolutionStatus;
import com.nongxinle.ai.identity.ResolvedEntityIdentity;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import com.nongxinle.ai.semantic.frame.ContractLockedSemanticFrame;
import com.nongxinle.ai.semantic.frame.PurchaseLockedSemanticFrameSupport;
import com.nongxinle.ai.semantic.matrix.PurchaseSemanticCapabilityMatrix;
import org.springframework.util.StringUtils;

/**
 * 从 {@link AiResolvedQueryContext#getContractLockedFrame()} 与 {@link ResolvedEntityIdentity}
 * 投影采购 contract-driven execution intent。
 */
public final class PurchaseSemanticExecutionIntentResolver {

    public static final String RESOLUTION_SOURCE_CONTRACT_ENTRY = "contract_entry";
    public static final String RESOLUTION_SOURCE_UNRESOLVED = "unresolved";

    private PurchaseSemanticExecutionIntentResolver() {}

    /** 可执行 intent：requiresGoodsFocus 时 identity 必须 OK。 */
    public static PurchaseSemanticExecutionIntent resolve(AiResolvedQueryContext rq) {
        return resolve(rq, null);
    }

    public static PurchaseSemanticExecutionIntent resolve(
            AiResolvedQueryContext rq, Integer distributerIdHint) {
        PurchaseSemanticExecutionIntent projected = project(rq, distributerIdHint);
        if (!projected.isActive()) {
            return PurchaseSemanticExecutionIntent.none();
        }
        if (projected.requiresGoodsFocus() && !projected.isAnchorResolved()) {
            return PurchaseSemanticExecutionIntent.none();
        }
        return projected;
    }

    /**
     * LockedFrame 投影（含 identity 未 OK 时的 executionIntentType / detailWanted）；Harness debug 与 Tool args 主链 SSOT。
     */
    public static PurchaseSemanticExecutionIntent project(AiResolvedQueryContext rq) {
        return project(rq, null);
    }

    public static PurchaseSemanticExecutionIntent project(
            AiResolvedQueryContext rq, Integer distributerIdHint) {
        if (rq == null
                || !SemanticContractCompletionEngine.isContractLockedParse(rq.getQuerySemanticParse())) {
            return PurchaseSemanticExecutionIntent.none();
        }
        ContractLockedSemanticFrame frame = PurchaseLockedSemanticFrameSupport.lockedFrame(rq);
        if (frame == null || frame.getContractFields() == null) {
            return PurchaseSemanticExecutionIntent.none();
        }
        String contractId = PurchaseLockedSemanticFrameSupport.selectedContractId(frame);
        if (!StringUtils.hasText(contractId)) {
            return PurchaseSemanticExecutionIntent.none();
        }

        String execType = PurchaseSemanticExecutionProjection.executionIntentType(frame);
        if (!StringUtils.hasText(execType)
                || PurchaseSemanticExecutionIntent.EXEC_NONE.equals(execType)) {
            return PurchaseSemanticExecutionIntent.none();
        }

        GoodsAnchor anchor = resolveGoodsAnchor(rq, distributerIdHint);
        String anchorType =
                PurchaseSemanticExecutionIntent.requiresGoodsFocusExecType(execType)
                        ? AiResultAnchor.ENTITY_TYPE_GOODS
                        : null;

        return PurchaseSemanticExecutionIntent.builder()
                .matchedContractId(contractId)
                .wire(PurchaseLockedSemanticFrameSupport.canonicalWire(frame))
                .queryObject(PurchaseLockedSemanticFrameSupport.queryObject(frame))
                .operation(PurchaseLockedSemanticFrameSupport.operation(frame))
                .detailWanted(PurchaseLockedSemanticFrameSupport.detailWanted(frame))
                .sourceFacet(PurchaseLockedSemanticFrameSupport.sourceFacet(frame))
                .answerPlanType(PurchaseLockedSemanticFrameSupport.answerPlanType(frame))
                .focusGoodsId(anchor.entityId())
                .focusGoodsName(anchor.entityName())
                .anchorType(anchorType)
                .anchorResolved(anchor.resolved())
                .executionIntentType(execType)
                .toolDetailWantedKey(PurchaseSemanticExecutionProjection.toolDetailWantedKey(frame))
                .resolutionSource(
                        anchor.resolved()
                                ? RESOLUTION_SOURCE_CONTRACT_ENTRY
                                : RESOLUTION_SOURCE_UNRESOLVED)
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

    public static boolean isGoodsAnchorSourceBreakdownContractId(String contractId) {
        if (!StringUtils.hasText(contractId)) {
            return false;
        }
        return PurchaseSemanticCapabilityMatrix.SOURCE_BREAKDOWN.getCapabilityId().equals(contractId.trim());
    }

    static GoodsAnchor resolveGoodsAnchor(AiResolvedQueryContext rq) {
        return resolveGoodsAnchor(rq, null);
    }

    static GoodsAnchor resolveGoodsAnchor(AiResolvedQueryContext rq, Integer distributerIdHint) {
        if (rq == null) {
            return GoodsAnchor.unresolved();
        }
        ResolvedEntityIdentity identity = rq.getResolvedGoodsIdentity();
        if (identity == null) {
            identity = BusinessEntityIdentityBridge.resolveGoods(rq, distributerIdHint);
        }
        if (identity.getResolutionStatus() != EntityIdentityResolutionStatus.OK) {
            String requestedName =
                    StringUtils.hasText(identity.getUserMentionedName())
                            ? identity.getUserMentionedName().trim()
                            : PurchaseLockedSemanticFrameSupport.mentionedGoodsName(
                                    PurchaseLockedSemanticFrameSupport.lockedFrame(rq));
            return new GoodsAnchor(null, requestedName, false);
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
