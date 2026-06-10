package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.inventory.CoverDaysSalesBaselinePresentationSupport;
import com.nongxinle.ai.inventory.InventoryPresentationTimeSupport;

import java.time.LocalDate;
import java.util.Map;

/**
 * Cover-days 日均销量基线：只读 {@link AiResolvedQueryContext#getResolvedSalesBaseline()}（结构化
 * {@code salesBaselineWindow} 投影）；缺投影时默认近 7 天。不读 rawMessage / time.reason /
 * {@code effectiveTimeWindowSource}。
 */
public final class DishIngredientCoverSalesBaselineSupport {
    static final String COST_DATA_BASELINE_KEY = "dishIngredientCoverSalesBaseline";

    private DishIngredientCoverSalesBaselineSupport() {}

    public static DishIngredientCoverSalesBaseline resolve(AiRunState state, AiResolvedQueryContext rq) {
        if (rq != null && rq.getResolvedSalesBaseline() != null) {
            return rq.getResolvedSalesBaseline();
        }
        return defaultLast7Days(InventoryPresentationTimeSupport.resolveSemanticQueryAnchorDate(state, rq));
    }

    public static DishIngredientCoverSalesBaseline fromCostData(Map<String, Object> costData) {
        if (costData == null) {
            return null;
        }
        return DishIngredientCoverSalesBaseline.fromWireMap(costData.get(COST_DATA_BASELINE_KEY));
    }

    /** 结构化销量基线是否为 EXPLICIT（读 resolvedSalesBaseline.source，不读 effectiveTimeWindowSource）。 */
    public static boolean isExplicitSalesBaseline(AiResolvedQueryContext rq) {
        if (rq == null || rq.getResolvedSalesBaseline() == null) {
            return false;
        }
        return DishIngredientCoverSalesBaseline.SOURCE_USER_EXPLICIT_TIME_WINDOW.equals(
                rq.getResolvedSalesBaseline().getBaselineSource());
    }

    /**
     * @deprecated 使用 {@link #isExplicitSalesBaseline(AiResolvedQueryContext)}；禁止用 global time explicit 推断。
     */
    @Deprecated
    public static boolean isUserExplicitSalesBaselineTime(AiResolvedQueryContext rq) {
        return isExplicitSalesBaseline(rq);
    }

    private static DishIngredientCoverSalesBaseline defaultLast7Days(LocalDate anchor) {
        LocalDate end = anchor == null ? LocalDate.now() : anchor;
        LocalDate start = end.minusDays(DishIngredientCoverSalesBaseline.DEFAULT_BASELINE_DAYS - 1L);
        DishIngredientCoverSalesBaseline draft =
                DishIngredientCoverSalesBaseline.builder()
                        .startDateIso(start.toString())
                        .stopDateIso(end.toString())
                        .baselineDays(DishIngredientCoverSalesBaseline.DEFAULT_BASELINE_DAYS)
                        .baselineSource(DishIngredientCoverSalesBaseline.SOURCE_DEFAULT_LAST_7_DAYS)
                        .build();
        String label =
                CoverDaysSalesBaselinePresentationSupport.formatSalesBaselineDisplayLabel(null, draft);
        return draft.toBuilder().displayLabel(label).build();
    }
}
