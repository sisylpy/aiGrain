package com.nongxinle.ai.graph.business;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nongxinle.entity.GbDepFoodSalesEntity;
import com.nongxinle.entity.GbDepartmentEntity;
import com.nongxinle.entity.GbDepartmentGoodsStockEntity;
import com.nongxinle.entity.GbDistributerFoodEntity;
import com.nongxinle.entity.GbDistributerFoodGoodsEntity;
import com.nongxinle.entity.GbDistributerGoodsEntity;
import com.nongxinle.service.GbDepFoodSalesService;
import com.nongxinle.service.GbDepartmentGoodsStockService;
import com.nongxinle.service.GbDepartmentService;
import com.nongxinle.service.GbDistributerFoodGoodsService;
import com.nongxinle.service.GbDistributerFoodService;
import com.nongxinle.service.GbDistributerGoodsService;
import com.nongxinle.constants.AiInsightDishProfitScope;
import com.nongxinle.utils.GbDepartmentGoodsStockReduceSupport;
import com.nongxinle.utils.GbDepFoodSalesMetricsSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** 原料库存快照 + 配方反查 + 销量基线 → 受影响菜品行。 */
@Service
@RequiredArgsConstructor
public class GoodsSupportedDishCoverDomainService {

    public static final String PAYLOAD_KEY = "goodsSupportedDishCover";

    private final GbDistributerGoodsService gbDistributerGoodsService;
    private final GbDistributerFoodGoodsService gbDistributerFoodGoodsService;
    private final GbDistributerFoodService gbDistributerFoodService;
    private final GbDepartmentGoodsStockService gbDepartmentGoodsStockService;
    private final GbDepFoodSalesService gbDepFoodSalesService;
    private final GbDepartmentService gbDepartmentService;

    public Map<String, Object> buildPayload(
            int disId,
            Integer depFatherId,
            Integer disGoodsId,
            String goodsNameHint,
            DishIngredientCoverSalesBaseline salesBaseline,
            String stockAsOfDateIso,
            LinkedHashMap<String, Object> debug) {
        GoodsResolveResult resolved = resolveDisGoodsId(disId, disGoodsId, goodsNameHint, debug);
        if (resolved.status == GoodsResolveStatus.NEED_CLARIFICATION) {
            LinkedHashMap<String, Object> out = new LinkedHashMap<>();
            out.put("status", "NEED_CLARIFICATION");
            out.put("candidates", resolved.candidates);
            out.put("goodsNameHint", goodsNameHint);
            return out;
        }
        if (resolved.status != GoodsResolveStatus.OK || resolved.disGoodsId == null) {
            LinkedHashMap<String, Object> out = new LinkedHashMap<>();
            out.put("status", "NOT_FOUND");
            out.put("goodsNameHint", goodsNameHint);
            return out;
        }

        int targetGoodsId = resolved.disGoodsId;
        GbDistributerGoodsEntity goodsEntity = gbDistributerGoodsService.queryObject(targetGoodsId);
        String goodsName =
                goodsEntity != null && StringUtils.hasText(goodsEntity.getGbDgGoodsName())
                        ? goodsEntity.getGbDgGoodsName().trim()
                        : (StringUtils.hasText(goodsNameHint) ? goodsNameHint.trim() : null);

        BigDecimal stockQty = sumRestWeightByDisGoodsId(disId, depFatherId, targetGoodsId);
        List<GbDistributerFoodGoodsEntity> recipeLines =
                queryRecipeLines(targetGoodsId, disId);
        LinkedHashMap<Integer, BigDecimal> recipeUnitByFoodId = mergeRecipeUnits(recipeLines);
        LinkedHashSet<Integer> foodIds = new LinkedHashSet<>(recipeUnitByFoodId.keySet());

        Map<Integer, BigDecimal> salesByFoodId =
                querySalesPortionsByFoodId(
                        disId,
                        depFatherId,
                        foodIds,
                        salesBaseline.getStartDateIso(),
                        salesBaseline.getStopDateIso());

        List<Map<String, Object>> dishRows = new ArrayList<>();
        for (Integer foodId : foodIds) {
            GbDistributerFoodEntity food = gbDistributerFoodService.queryObject(foodId);
            String dishName =
                    food != null && StringUtils.hasText(food.getGbDfFoodName())
                            ? food.getGbDfFoodName().trim()
                            : "";
            dishRows.add(
                    GoodsSupportedDishCoverDishRowProjection.project(
                            foodId,
                            dishName,
                            recipeUnitByFoodId.get(foodId),
                            salesByFoodId.get(foodId),
                            salesBaseline.getBaselineDays(),
                            stockQty));
        }
        dishRows.sort(
                Comparator.comparing(
                                (Map<String, Object> r) -> (BigDecimal) r.get("_coverDaysSort"),
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(
                                r -> Objects.toString(r.get("dishName"), ""),
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        for (Map<String, Object> row : dishRows) {
            row.remove("_coverDaysSort");
        }

        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        out.put("status", "OK");
        out.put("disGoodsId", targetGoodsId);
        out.put("goodsName", goodsName);
        out.put("currentStockQty", formatQty(stockQty));
        out.put("stockUnit", "斤");
        out.put("stockAsOfDate", stockAsOfDateIso);
        out.put("salesBaseline", salesBaseline.toWireMap());
        out.put("dishRows", dishRows);
        out.put("linkedDishCount", dishRows.size());
        if (!dishRows.isEmpty()) {
            Map<String, Object> first = dishRows.get(0);
            out.put("firstImpactedDishName", first.get("dishName"));
            out.put("firstImpactedCoverDays", first.get("coverDays"));
        }
        if (foodIds.isEmpty()) {
            out.put("knownGap", "no_linked_dish_for_goods");
        }
        if (debug != null) {
            debug.put("resolvedDisGoodsId", targetGoodsId);
            debug.put("recipeLineCount", recipeLines.size());
        }
        return out;
    }

    private GoodsResolveResult resolveDisGoodsId(
            int disId, Integer disGoodsId, String goodsNameHint, LinkedHashMap<String, Object> debug) {
        if (disGoodsId != null && disGoodsId > 0) {
            GbDistributerGoodsEntity g = gbDistributerGoodsService.queryObject(disGoodsId);
            if (g != null) {
                return new GoodsResolveResult(GoodsResolveStatus.OK, disGoodsId, List.of());
            }
            if (debug != null) {
                debug.put("anchorDisGoodsIdRejected", disGoodsId);
            }
        }
        if (!StringUtils.hasText(goodsNameHint)) {
            return new GoodsResolveResult(GoodsResolveStatus.NOT_FOUND, null, List.of());
        }
        Map<String, Object> map = new HashMap<>();
        map.put("disId", disId);
        String hint = goodsNameHint.trim();
        if (containsHan(hint)) {
            map.put("searchStr", hint);
        } else {
            map.put("searchPinyin", hint);
        }
        List<GbDistributerGoodsEntity> hits = gbDistributerGoodsService.queryGbDisGoodsQuickSearchStr(map);
        if (hits == null || hits.isEmpty()) {
            if (debug != null) {
                debug.put("goodsSearchEmpty", hint);
            }
            return new GoodsResolveResult(GoodsResolveStatus.NOT_FOUND, null, List.of());
        }
        LinkedHashSet<Integer> ids = new LinkedHashSet<>();
        for (GbDistributerGoodsEntity e : hits) {
            if (e != null && e.getGbDistributerGoodsId() != null) {
                ids.add(e.getGbDistributerGoodsId());
            }
        }
        if (ids.size() == 1) {
            return new GoodsResolveResult(GoodsResolveStatus.OK, ids.iterator().next(), List.of());
        }
        List<Map<String, Object>> cands = new ArrayList<>();
        for (Integer id : ids) {
            GbDistributerGoodsEntity e =
                    hits.stream()
                            .filter(x -> id.equals(x.getGbDistributerGoodsId()))
                            .findFirst()
                            .orElse(null);
            LinkedHashMap<String, Object> c = new LinkedHashMap<>();
            c.put("disGoodsId", id);
            c.put(
                    "goodsName",
                    e != null && e.getGbDgGoodsName() != null ? e.getGbDgGoodsName().trim() : "");
            cands.add(c);
        }
        if (debug != null) {
            debug.put("goodsSearchAmbiguous", cands);
        }
        return new GoodsResolveResult(GoodsResolveStatus.NEED_CLARIFICATION, null, cands);
    }

    private List<GbDistributerFoodGoodsEntity> queryRecipeLines(int disGoodsId, int disId) {
        List<GbDistributerFoodGoodsEntity> lines =
                gbDistributerFoodGoodsService.queryFoodGoodsByDisGoodsId(disGoodsId, disId);
        if ((lines == null || lines.isEmpty()) && disId > 0) {
            lines = gbDistributerFoodGoodsService.queryFoodGoodsByDisGoodsId(disGoodsId, null);
        }
        return lines == null ? List.of() : lines;
    }

    private static LinkedHashMap<Integer, BigDecimal> mergeRecipeUnits(
            List<GbDistributerFoodGoodsEntity> lines) {
        LinkedHashMap<Integer, BigDecimal> out = new LinkedHashMap<>();
        if (lines == null) {
            return out;
        }
        for (GbDistributerFoodGoodsEntity line : lines) {
            if (!GbDepartmentGoodsStockReduceSupport.isActiveFoodGoodsLine(line)
                    || line.getGbDfgFoodId() == null) {
                continue;
            }
            BigDecimal unit =
                    GbDepartmentGoodsStockReduceSupport.parseGoodsAmountString(
                            line.getGbDfgGoodsAmount());
            if (unit.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            out.merge(line.getGbDfgFoodId(), unit, BigDecimal::add);
        }
        return out;
    }

    private Map<Integer, BigDecimal> querySalesPortionsByFoodId(
            int disId,
            Integer depFatherId,
            LinkedHashSet<Integer> foodIds,
            String startDate,
            String stopDate) {
        Map<Integer, BigDecimal> out = new HashMap<>();
        if (foodIds == null || foodIds.isEmpty() || startDate == null || stopDate == null) {
            return out;
        }
        List<Integer> scopeDepIds = resolveSalesScopeDepIds(depFatherId);
        if (depFatherId != null
                && !AiInsightDishProfitScope.isGroupWideMendianAggregateUnderDis(depFatherId)
                && scopeDepIds.isEmpty()) {
            return out;
        }
        LambdaQueryWrapper<GbDepFoodSalesEntity> sq =
                new LambdaQueryWrapper<GbDepFoodSalesEntity>()
                        .in(GbDepFoodSalesEntity::getGbDfsFoodId, foodIds)
                        .ge(GbDepFoodSalesEntity::getGbDfsFullDate, startDate)
                        .le(GbDepFoodSalesEntity::getGbDfsFullDate, stopDate)
                        .eq(GbDepFoodSalesEntity::getGbDfsDistributerId, disId);
        if (!scopeDepIds.isEmpty()) {
            if (scopeDepIds.size() == 1) {
                sq.eq(GbDepFoodSalesEntity::getGbDfsDepId, scopeDepIds.get(0));
            } else {
                sq.in(GbDepFoodSalesEntity::getGbDfsDepId, scopeDepIds);
            }
        }
        for (GbDepFoodSalesEntity s : gbDepFoodSalesService.list(sq)) {
            if (s.getGbDfsFoodId() == null || !GbDepFoodSalesMetricsSupport.countsAsOperationalSales(s)) {
                continue;
            }
            BigDecimal amt = GbDepFoodSalesMetricsSupport.operationalSalesQty(s);
            out.merge(s.getGbDfsFoodId(), amt, BigDecimal::add);
        }
        return out;
    }

    /**
     * 与 {@code GbDishCostAnalysisServiceImpl#loadFoodSales} 门店口径一致：销售按子部门 depId 汇总，不能只用 disId 全集团混算。
     */
    private List<Integer> resolveSalesScopeDepIds(Integer depFatherId) {
        if (depFatherId == null
                || AiInsightDishProfitScope.isGroupWideMendianAggregateUnderDis(depFatherId)) {
            return List.of();
        }
        LinkedHashMap<Integer, Boolean> uniq = new LinkedHashMap<>();
        List<GbDepartmentEntity> subs = gbDepartmentService.querySubDepartments(depFatherId);
        if (subs != null) {
            for (GbDepartmentEntity sub : subs) {
                if (sub != null && sub.getGbDepartmentId() != null) {
                    uniq.put(sub.getGbDepartmentId(), Boolean.TRUE);
                }
            }
        }
        return new ArrayList<>(uniq.keySet());
    }

    private BigDecimal sumRestWeightByDisGoodsId(int disId, Integer depFatherId, int disGoodsId) {
        Map<String, Object> q = new HashMap<>();
        q.put("disId", disId);
        if (depFatherId != null) {
            q.put("depFatherId", depFatherId);
        }
        List<GbDepartmentGoodsStockEntity> stocks =
                gbDepartmentGoodsStockService.queryGoodsStockListForMendianPeriod(q);
        if (stocks == null || stocks.isEmpty()) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        boolean any = false;
        for (GbDepartmentGoodsStockEntity entity : stocks) {
            if (entity == null || !Objects.equals(disGoodsId, entity.getGbDgsGbDisGoodsId())) {
                continue;
            }
            BigDecimal rw = parseDecimal(entity.getGbDgsRestWeight());
            if (rw == null || rw.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            sum = sum.add(rw);
            any = true;
        }
        return any ? sum.setScale(4, RoundingMode.HALF_UP) : null;
    }

    private static BigDecimal parseDecimal(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof BigDecimal bd) {
            return bd;
        }
        if (raw instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(raw.toString().trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String formatQty(BigDecimal v) {
        if (v == null) {
            return null;
        }
        return v.stripTrailingZeros().toPlainString();
    }

    private static boolean containsHan(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.UnicodeScript.of(s.charAt(i)) == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }

    private enum GoodsResolveStatus {
        OK,
        NOT_FOUND,
        NEED_CLARIFICATION
    }

    private record GoodsResolveResult(
            GoodsResolveStatus status, Integer disGoodsId, List<Map<String, Object>> candidates) {}
}
