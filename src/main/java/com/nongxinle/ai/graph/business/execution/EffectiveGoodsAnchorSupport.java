package com.nongxinle.ai.graph.business.execution;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.identity.BusinessEntityIdentityGoodsProjection;

/**
 * GOODS 锚：委托 {@link com.nongxinle.ai.identity.BusinessEntityIdentityResolver}（PR1 执行 SSOT）。
 * 展示层 {@link com.nongxinle.ai.graph.business.PurchaseGoodsBusinessAnalysisDisplayNameSupport} 仍可读本类，
 * 但仅接受 {@code source=currentTurn.mentionedGoodsName} 的 anchor 名。
 */
public final class EffectiveGoodsAnchorSupport {

    private EffectiveGoodsAnchorSupport() {}

    public static EffectiveGoodsAnchor resolve(AiResolvedQueryContext ctx) {
        return BusinessEntityIdentityGoodsProjection.toEffectiveGoodsAnchor(ctx);
    }
}
