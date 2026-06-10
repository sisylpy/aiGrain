package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.graph.business.execution.EffectiveDishAnchor;
import com.nongxinle.ai.graph.business.execution.EffectiveDishAnchorSupport;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.nongxinle.entity.GbDepartmentGoodsStockEntity;
import com.nongxinle.service.GbDepartmentGoodsStockService;
import com.nongxinle.service.GbDishCostAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code dish.ingredient_cover_days.v1} 专用：在 AnswerPlan 构建前补齐完整配方行，并挂载真实库存（{@code gb_dgs_rest_weight} 汇总）。
 * 不修改语义 / 合同；只增强 {@link AiBusinessToolIds#DISH_COST_ANALYSIS} 快照的业务可读性。
 */
@Component
@RequiredArgsConstructor
public class DishIngredientCoverCostDataEnricher {

    private final GbDepartmentGoodsStockService gbDepartmentGoodsStockService;
    private final GbDishCostAnalysisService gbDishCostAnalysisService;

    public void enrichIfApplicable(AiRunState state) {
        if (state == null || !state.isDishCostAnalysisPath()) {
            return;
        }
        Map<String, Object> costData = dishCostToolData(state);
        if (costData == null || costData.isEmpty()) {
            return;
        }
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        DishIngredientCoverSalesBaseline baseline = DishIngredientCoverSalesBaselineSupport.resolve(state, rq);
        costData.put(DishIngredientCoverSalesBaselineSupport.COST_DATA_BASELINE_KEY, baseline.toWireMap());
        refreshDishRowFromSalesBaseline(costData, state, baseline);
        attachInventoryRestWeights(costData, state);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> dishCostToolData(AiRunState state) {
        Object raw =
                state.getToolResults() == null ? null : state.getToolResults().get(AiBusinessToolIds.DISH_COST_ANALYSIS);
        if (!(raw instanceof Map<?, ?> env) || !Boolean.TRUE.equals(env.get("success"))) {
            return null;
        }
        Object data = env.get("data");
        if (data instanceof Map<?, ?> dm) {
            return (Map<String, Object>) dm;
        }
        return null;
    }

    /** 始终按销量基线区间重载单菜配料行（与问句 inherited timeWindow 解耦）。 */
    private void refreshDishRowFromSalesBaseline(
            Map<String, Object> costData, AiRunState state, DishIngredientCoverSalesBaseline baseline) {
        if (baseline == null) {
            return;
        }
        Integer foodId = firstNonNullInt(costData.get("dishId"), resolveFoodIdFromContext(state));
        if (foodId == null) {
            return;
        }
        OrgScope scope = resolveOrgScope(costData, state);
        if (scope.disId() == null || scope.depFatherId() == null) {
            return;
        }
        List<Integer> scopeDepIds =
                BusinessToolExecutionNode.extractSqlQueryDepartmentIdsForTools(
                        state != null ? state.getResolvedQueryContext() : null);
        Collection<Integer> scopeFilter = scopeDepIds.isEmpty() ? null : scopeDepIds;
        try {
            Map<String, Object> fresh =
                    gbDishCostAnalysisService.buildIngredientAnalysisDishRowForFoodId(
                            baseline.getStartDateIso(),
                            baseline.getStopDateIso(),
                            scope.disId(),
                            scope.depFatherId(),
                            scope.searchDepId(),
                            foodId,
                            scopeFilter);
            if (fresh == null || fresh.isEmpty()) {
                return;
            }
            List<Map<String, Object>> freshRows = ingredientRows(fresh);
            if (!freshRows.isEmpty()) {
                costData.put("ingredientRows", freshRows);
            }
            copyIfPresent(costData, fresh, "dishName");
            copyIfPresent(costData, fresh, "dishId");
            copyIfPresent(costData, fresh, "salesPortions");
            copyIfPresent(costData, fresh, "bottle");
        } catch (RuntimeException ignored) {
            // 保留 tool 快照，由 AnswerPlan 按 partial 表达
        }
    }

    private void attachInventoryRestWeights(Map<String, Object> costData, AiRunState state) {
        OrgScope scope = resolveOrgScope(costData, state);
        if (scope.disId() == null || scope.depFatherId() == null) {
            return;
        }
        Map<Integer, BigDecimal> restByGoods = sumRestWeightByDisGoodsId(scope.disId(), scope.depFatherId());
        List<Map<String, Object>> rows = ingredientRows(costData);
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            Integer disGoodsId = toInt(row.get("disGoodsId"));
            BigDecimal rest = disGoodsId == null ? null : restByGoods.get(disGoodsId);
            if (rest != null && rest.compareTo(BigDecimal.ZERO) > 0) {
                row.put("inventoryRestWeightQty", formatQty(rest));
            } else {
                row.put("inventoryRestWeightQty", null);
            }
        }
        LinkedHashMap<String, Object> debugInventory = new LinkedHashMap<>();
        debugInventory.put("source", "gb_department_goods_stock.queryGoodsStockListForMendianPeriod");
        debugInventory.put("disId", scope.disId());
        debugInventory.put("depFatherId", scope.depFatherId());
        debugInventory.put("disGoodsWithRestCount", restByGoods.size());
        costData.put("dishIngredientCoverInventoryDebug", debugInventory);
    }

    private Map<Integer, BigDecimal> sumRestWeightByDisGoodsId(int disId, int depFatherId) {
        Map<String, Object> listParams = new HashMap<>();
        listParams.put("depFatherId", depFatherId);
        listParams.put("disId", disId);
        listParams.put("restWeight", "0");
        List<GbDepartmentGoodsStockEntity> stockRows =
                gbDepartmentGoodsStockService.queryGoodsStockListForMendianPeriod(listParams);
        Map<Integer, BigDecimal> out = new LinkedHashMap<>();
        if (stockRows == null) {
            return out;
        }
        for (GbDepartmentGoodsStockEntity entity : stockRows) {
            if (entity == null || entity.getGbDgsGbDisGoodsId() == null) {
                continue;
            }
            BigDecimal rw = parseDecimal(entity.getGbDgsRestWeight());
            if (rw == null || rw.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            out.merge(entity.getGbDgsGbDisGoodsId(), rw, BigDecimal::add);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> ingredientRows(Map<String, Object> costData) {
        Object raw = costData.get("ingredientRows");
        if (!(raw instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> out = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                out.add((Map<String, Object>) m);
            }
        }
        return out;
    }

    private static OrgScope resolveOrgScope(Map<String, Object> costData, AiRunState state) {
        Integer disId = null;
        Integer depFatherId = null;
        String searchDepId = null;
        Object summaryRaw = costData.get("rawReportSummary");
        if (summaryRaw instanceof Map<?, ?> summary) {
            disId = toInt(summary.get("disId"));
            depFatherId = toInt(summary.get("depFatherId"));
        }
        if (state != null) {
            AiResolvedQueryContext rq = state.getResolvedQueryContext();
            if (rq != null) {
                if (disId == null
                        && rq.getOrgScope() != null
                        && rq.getOrgScope().getDistributerId() != null) {
                    disId = rq.getOrgScope().getDistributerId().intValue();
                }
                if (depFatherId == null
                        && rq.getUserContext() != null
                        && rq.getUserContext().getDepartmentFatherId() != null) {
                    depFatherId = rq.getUserContext().getDepartmentFatherId();
                }
            }
        }
        return new OrgScope(disId, depFatherId, searchDepId);
    }

    private static Integer resolveFoodIdFromContext(AiRunState state) {
        if (state == null) {
            return null;
        }
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        if (rq == null) {
            return null;
        }
        EffectiveDishAnchor anchor = EffectiveDishAnchorSupport.resolve(rq);
        return anchor == null ? null : anchor.getFoodId();
    }

    private static void copyIfPresent(Map<String, Object> target, Map<String, Object> source, String key) {
        if (source.containsKey(key) && source.get(key) != null) {
            target.put(key, source.get(key));
        }
    }

    private static Integer firstNonNullInt(Object a, Integer b) {
        Integer ia = toInt(a);
        return ia != null ? ia : b;
    }

    private static String formatQty(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static BigDecimal parseDecimal(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof BigDecimal bd) {
            return bd;
        }
        if (o instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        String s = o.toString().trim();
        if (!StringUtils.hasText(s)) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer toInt(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(o.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record OrgScope(Integer disId, Integer depFatherId, String searchDepId) {}
}
