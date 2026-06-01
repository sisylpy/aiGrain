package com.nongxinle.ai.semantic.dimension;

import lombok.Builder;
import lombok.Value;

/**
 * 裸排行维度切换 follow-up 的统一语义计划（与 time-only follow-up 平级）。
 * active 时：仅继承 time/scope；Business Frame 从 {@link #targetContractId} 整体派生；
 * 禁止继承上一轮 business frame 与 Top1 resultAnchor 主语义。
 */
@Value
@Builder
public class BareRankingDimensionSwitchPlan {
    boolean active;
    String intakeReason;
    String canonicalUserQuery;
    String previousRankingDomain;
    String previousContractId;
    String targetDomain;
    RankingMetricFacet targetFacet;
    String targetFacetResolveSource;
    boolean targetFacetFallbackUsed;
    RankingPolarity targetPolarity;
    String targetContractId;

    public boolean suppressPreviousDishAnchor() {
        return active;
    }
}
