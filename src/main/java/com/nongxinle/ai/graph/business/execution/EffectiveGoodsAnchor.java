package com.nongxinle.ai.graph.business.execution;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class EffectiveGoodsAnchor {
    String goodsName;
    Integer disGoodsId;
    String source;

    public static EffectiveGoodsAnchor empty() {
        return EffectiveGoodsAnchor.builder().build();
    }

    public boolean hasGoodsName() {
        return goodsName != null && !goodsName.isBlank();
    }

    public boolean hasDisGoodsId() {
        return disGoodsId != null && disGoodsId > 0;
    }

    public boolean isResolved() {
        return hasGoodsName() || hasDisGoodsId();
    }
}
