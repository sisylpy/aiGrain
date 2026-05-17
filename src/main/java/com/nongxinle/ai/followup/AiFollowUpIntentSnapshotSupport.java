package com.nongxinle.ai.followup;

import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.core.AiWorkspaceMode;
import org.springframework.util.StringUtils;

/**
 * Run 结束后由 {@link AiRunService} 调用，从 {@link AiRunState} 构建可持久化的 {@link AiFollowUpIntentSnapshot}，
 * 供下一轮 {@link com.nongxinle.ai.resolver.AiResolvedQueryContextResolver} 与会话 memory 加载。
 */
public final class AiFollowUpIntentSnapshotSupport {

    private AiFollowUpIntentSnapshotSupport() {
    }

    /** 无经营主线或未识别则返回 null。 */
    public static AiFollowUpIntentSnapshot snapshotFromCompletedState(AiRunState state) {
        if (state == null || state.isCancelled()) {
            return null;
        }
        if (state.getWorkspaceMode() != AiWorkspaceMode.BUSINESS_CHAT) {
            return null;
        }
        String q = state.getNormalizedUserInput();
        if (!StringUtils.hasText(q) && state.getResolvedQueryContext() != null) {
            q = state.getResolvedQueryContext().getNormalizedQuestion();
        }
        if (!StringUtils.hasText(q)) {
            return null;
        }
        FollowUpPathKind kind = null;
        if (state.isDishProfitPath()) {
            kind = FollowUpPathKind.DISH_PROFIT;
        } else if (state.isBusinessOverviewPath()) {
            kind = FollowUpPathKind.BUSINESS_OVERVIEW;
        } else if (state.isWarehouseStockOverviewPath()) {
            kind = FollowUpPathKind.WAREHOUSE_STOCK;
        } else if (state.isStockReduceQueryPath()) {
            kind = FollowUpPathKind.STOCK_REDUCE_QUERY;
        } else if (state.isRevenueOverviewPath()) {
            kind = FollowUpPathKind.REVENUE_OVERVIEW;
        } else if (state.isPurchaseOverviewPath()) {
            kind = FollowUpPathKind.PURCHASE_OVERVIEW;
        } else if (state.isPurchaseCostInsightPath()) {
            kind = FollowUpPathKind.PURCHASE_COST;
        } else if (state.isCostInsightPath()) {
            kind = FollowUpPathKind.COST_INSIGHT;
        }
        if (kind == null) {
            return null;
        }
        return AiFollowUpIntentSnapshot.builder()
                .v(AiFollowUpIntentSnapshot.VERSION)
                .effectiveQuestion(q.trim())
                .pathKind(kind)
                .build();
    }
}
