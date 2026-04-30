package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nongxinle.entity.GbDepartmentEntity;
import com.nongxinle.entity.GbDepFoodEntity;
import com.nongxinle.entity.GbDepFoodSalesEntity;
import com.nongxinle.entity.GbDistributerFoodEntity;
import com.nongxinle.entity.GbDistributerFoodGoodsEntity;
import com.nongxinle.service.GbDepFoodBusinessInsightService;
import com.nongxinle.service.GbDistributerFoodService;
import com.nongxinle.service.GbDepFoodSalesService;
import com.nongxinle.service.GbDepFoodService;
import com.nongxinle.service.GbDepartmentGoodsStockReduceService;
import com.nongxinle.service.GbDepartmentService;
import com.nongxinle.service.GbDishCostAnalysisService;
import com.nongxinle.utils.GbDepartmentGoodsStockReduceSupport;
import com.nongxinle.utils.GrossMarginStandardDisplay;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.nongxinle.utils.GbTypeUtils.getGbDepartmentTypeMendian;

/**
 * 与 {@link GbDishCostAnalysisServiceImpl} 使用相同的门店子部门范围解析，保证销量与成本报表可对齐。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GbDepFoodBusinessInsightServiceImpl implements GbDepFoodBusinessInsightService {

    private static final String REPORT_SALES_DISH = "salesDish";

    private final GbDepFoodService gbDepFoodService;
    private final GbDepFoodSalesService gbDepFoodSalesService;
    private final GbDepartmentService gbDepartmentService;
    private final GbDishCostAnalysisService gbDishCostAnalysisService;
    private final GbDepartmentGoodsStockReduceService gbDepartmentGoodsStockReduceService;
    private final GbDistributerFoodService gbDistributerFoodService;

    @Override
    public Map<String, Object> buildInsight(Integer disId, Integer depFatherId, String startDate, String stopDate) {
        if (disId == null || depFatherId == null) {
            throw new IllegalArgumentException("disId、depFatherId 不能为空");
        }
        if (startDate == null || startDate.trim().isEmpty() || stopDate == null || stopDate.trim().isEmpty()) {
            throw new IllegalArgumentException("startDate、stopDate 不能为空");
        }
        String sd = startDate.trim();
        String ed = stopDate.trim();

        List<Integer> scopeDepIds = resolveScopeDepIds(disId, null, depFatherId);

        Map<String, Object> dishReport = gbDishCostAnalysisService.buildReport(sd, ed, disId, null, depFatherId, REPORT_SALES_DISH);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> salesDishRows = (List<Map<String, Object>>) dishReport.get("salesDishRows");
        Map<Integer, Map<String, Object>> costRowByFoodId = new HashMap<>();
        if (salesDishRows != null) {
            for (Map<String, Object> row : salesDishRows) {
                Object fid = row.get("foodId");
                if (fid instanceof Integer) {
                    costRowByFoodId.put((Integer) fid, row);
                }
            }
        }

        WeekdaySalesAgg salesAgg = loadQtyByFoodAndWeekday(disId, scopeDepIds, sd, ed);

        Map<String, Object> depMap = new HashMap<>();
        depMap.put("disId", disId);
        depMap.put("depFatherId", depFatherId);
        List<GbDepFoodEntity> foods = gbDepFoodService.queryDepAllFood(depMap);
        if (foods == null) {
            foods = Collections.emptyList();
        }

        Map<Integer, GbDistributerFoodEntity> disFoodById = new HashMap<>();
        Map<Integer, GbDistributerFoodEntity> parentById = new HashMap<>();
        if (!foods.isEmpty()) {
            List<Integer> leafIds = new ArrayList<>();
            for (GbDepFoodEntity fe : foods) {
                if (fe.getGbDfFoodId() != null) {
                    leafIds.add(fe.getGbDfFoodId());
                }
            }
            if (!leafIds.isEmpty()) {
                for (GbDistributerFoodEntity dr : gbDistributerFoodService.queryByIds(leafIds)) {
                    if (dr.getGbDistributerFoodId() != null) {
                        disFoodById.put(dr.getGbDistributerFoodId(), dr);
                    }
                }
                Set<Integer> fatherIds = new HashSet<>();
                for (GbDistributerFoodEntity drow : disFoodById.values()) {
                    Integer p = drow.getGbDfFoodFatherId();
                    if (p != null && p != 0) {
                        fatherIds.add(p);
                    }
                }
                if (!fatherIds.isEmpty()) {
                    for (GbDistributerFoodEntity pr : gbDistributerFoodService.queryByIds(new ArrayList<>(fatherIds))) {
                        if (pr.getGbDistributerFoodId() != null) {
                            parentById.put(pr.getGbDistributerFoodId(), pr);
                        }
                    }
                }
            }
        }

        Object scopeOutboundForInterval = dishReport.get("scopeOutboundSubtotals");

        Set<Integer> insightFoodIds = new HashSet<>();
        for (GbDepFoodEntity f : foods) {
            if (f.getGbDfFoodId() != null) {
                insightFoodIds.add(f.getGbDfFoodId());
            }
        }
        Map<Integer, BigDecimal> actPp123ByFoodId = Collections.emptyMap();
        if (!insightFoodIds.isEmpty()) {
            actPp123ByFoodId = gbDishCostAnalysisService.getDishActualCostPerPortion123ByFoodIds(sd, ed, disId, depFatherId,
                    insightFoodIds);
        }

        List<Map<String, Object>> dishes = new ArrayList<>();
        for (GbDepFoodEntity f : foods) {
            Integer foodId = f.getGbDfFoodId();
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("gbDepFoodId", f.getGbDepFoodId());
            line.put("gbDfDepId", f.getGbDfDepId());
            line.put("foodId", foodId);
            String name = f.getGbDfFoodName();
            line.put("foodName", name != null ? name : "");
            line.put("listPrice", f.getGbDfFoodPrice() != null ? f.getGbDfFoodPrice() : "");

            Map<Integer, BigDecimal> byWd = foodId == null ? Collections.emptyMap()
                    : salesAgg.byFoodWeekday.getOrDefault(foodId, Collections.emptyMap());
            Map<String, Object> weekdayQty = new LinkedHashMap<>();
            BigDecimal totalQty = BigDecimal.ZERO;
            for (int wd = 0; wd <= 6; wd++) {
                BigDecimal q = byWd.getOrDefault(wd, BigDecimal.ZERO);
                weekdayQty.put(String.valueOf(wd), q.stripTrailingZeros().toPlainString());
                totalQty = totalQty.add(q);
            }
            BigDecimal unassigned = foodId == null ? BigDecimal.ZERO
                    : salesAgg.unassignedWeekdayQty.getOrDefault(foodId, BigDecimal.ZERO);
            if (foodId != null) {
                totalQty = totalQty.add(unassigned);
            }
            line.put("weekdayQty", weekdayQty);
            line.put("soldPortionsUnassignedWeekday", unassigned.stripTrailingZeros().toPlainString());
            line.put("soldPortionsTotal", totalQty.stripTrailingZeros().toPlainString());

            BigDecimal unitPrice = parseAmountSafe(f.getGbDfFoodPrice());
            BigDecimal listPriceRevenue = totalQty.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
            line.put("listPriceRevenue", listPriceRevenue.stripTrailingZeros().toPlainString());

            Map<String, Object> costRow = foodId == null ? null : costRowByFoodId.get(foodId);
            BigDecimal actualCost = BigDecimal.ZERO;
            BigDecimal theoryCost = BigDecimal.ZERO;
            if (costRow != null) {
                actualCost = GbDepartmentGoodsStockReduceSupport.coerceDecimal(costRow.get("actualCostAmount"));
                theoryCost = GbDepartmentGoodsStockReduceSupport.coerceDecimal(costRow.get("theoryCostAmount"));
            }
            line.put("actualCostAmount", actualCost.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
            line.put("theoryCostAmount", theoryCost.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());

            if (listPriceRevenue.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal marginActual = listPriceRevenue.subtract(actualCost)
                        .divide(listPriceRevenue, 8, RoundingMode.HALF_UP);
                line.put("grossMarginRateOnListPrice",
                        GbDepartmentGoodsStockReduceSupport.formatRatioAsPercentTwoDecimals(marginActual));
                BigDecimal marginTheory = listPriceRevenue.subtract(theoryCost)
                        .divide(listPriceRevenue, 8, RoundingMode.HALF_UP);
                line.put("grossMarginRateTheoryOnListPrice",
                        GbDepartmentGoodsStockReduceSupport.formatRatioAsPercentTwoDecimals(marginTheory));
            } else {
                boolean noRevNoCost = listPriceRevenue.signum() == 0 && actualCost.signum() == 0
                        && theoryCost.signum() == 0;
                line.put("grossMarginRateOnListPrice", noRevNoCost ? "0.00" : null);
                line.put("grossMarginRateTheoryOnListPrice", noRevNoCost ? "0.00" : null);
            }

            BigDecimal actPp123 = foodId == null ? BigDecimal.ZERO : actPp123ByFoodId.getOrDefault(foodId, BigDecimal.ZERO);
            line.put("actualCostPerPortion123", insightCostPerPortionTwoDecimals(actPp123));
            // 与 ingredientAnalysis / dishIngredientDashboard 整菜「单份实际」同口径的区间总金额（type1+2+3）
            BigDecimal actualTotal123 = actPp123.multiply(totalQty).setScale(2, RoundingMode.HALF_UP);
            line.put("actualCostTotalAmount123", actualTotal123.stripTrailingZeros().toPlainString());
            BigDecimal blendedRatio123 = null;
            if (unitPrice.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal margin123 = unitPrice.subtract(actPp123).divide(unitPrice, 8, RoundingMode.HALF_UP);
                blendedRatio123 = margin123;
                line.put("blendedGrossMarginRateOnListPrice",
                        GbDepartmentGoodsStockReduceSupport.formatRatioAsPercentTwoDecimals(margin123));
            } else {
                boolean noPriceNoCost123 = unitPrice.signum() == 0 && actPp123.signum() == 0;
                line.put("blendedGrossMarginRateOnListPrice", noPriceNoCost123 ? "0.00" : null);
                if (noPriceNoCost123) {
                    blendedRatio123 = BigDecimal.ZERO;
                }
            }

            // 与 scopeOutbound 一致：区间 (2+3)/(1+2+3) 损耗率，与「单菜 grossMarginRateOnListPrice（仅 type1 成本）」并列展示
            line.put("wasteLossRatioInOutbound123", wasteLossRatioStringFromScope(scopeOutboundForInterval));

            GbDistributerFoodEntity disRow = foodId == null ? null : disFoodById.get(foodId);
            GbDistributerFoodEntity directParent = null;
            if (disRow != null) {
                Integer pid = disRow.getGbDfFoodFatherId();
                if (pid != null && pid != 0) {
                    directParent = parentById.get(pid);
                }
            }
            GrossMarginStandardDisplay.putOnMap(line, blendedRatio123, directParent);

            dishes.add(line);
        }

        dishes.sort(Comparator.comparing((Map<String, Object> m) -> parseAmountSafe(String.valueOf(m.get("soldPortionsTotal"))))
                .reversed());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("startDate", sd);
        out.put("stopDate", ed);
        out.put("disId", disId);
        out.put("depFatherId", depFatherId);
        out.put("scopeDepIds", scopeDepIds);
        out.put("weekdayLegend", weekdayLegendZh());
        out.put("scopeOutboundSubtotals", dishReport.get("scopeOutboundSubtotals"));
        out.put("bossColumnHintsZh", dishReport.get("bossColumnHintsZh"));
        out.put("dishes", dishes);
        return out;
    }

    @Override
    public Map<String, Object> attachToFoodRows(List<GbDepFoodEntity> foods, Integer disId, Integer depFatherId,
            String startDate, String stopDate) {
        Map<String, Object> insight = buildInsight(disId, depFatherId, startDate, stopDate);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dishes = (List<Map<String, Object>>) insight.get("dishes");
        Map<Integer, Map<String, Object>> byFoodId = new HashMap<>();
        if (dishes != null) {
            for (Map<String, Object> d : dishes) {
                Object fid = d.get("foodId");
                if (fid instanceof Integer) {
                    byFoodId.put((Integer) fid, d);
                }
            }
        }
        Map<String, Object> emptyInsight = defaultEmptyInsightRow();

        if (foods != null) {
            for (GbDepFoodEntity f : foods) {
                Integer foodId = f.getGbDfFoodId();
                Map<String, Object> row = foodId == null ? null : byFoodId.get(foodId);
                if (row != null) {
                    Map<String, Object> copy = new LinkedHashMap<>(row);
                    String nm = f.getGbDfFoodName();
                    if (nm == null || nm.trim().isEmpty()) {
                        if (f.getGbDistributerFoodEntity() != null
                                && f.getGbDistributerFoodEntity().getGbDfFoodName() != null
                                && !f.getGbDistributerFoodEntity().getGbDfFoodName().trim().isEmpty()) {
                            nm = f.getGbDistributerFoodEntity().getGbDfFoodName().trim();
                        }
                    }
                    if (nm != null && !nm.trim().isEmpty()) {
                        copy.put("foodName", nm.trim());
                    }
                    f.setGbDfBusinessInsight(copy);
                    Object tot = row.get("soldPortionsTotal");
                    f.setGbDfSalesAmount(tot != null ? String.valueOf(tot) : "0");
                } else {
                    Map<String, Object> z = new LinkedHashMap<>(emptyInsight);
                    z.put("listPrice", f.getGbDfFoodPrice() != null ? f.getGbDfFoodPrice() : "");
                    z.put("wasteLossRatioInOutbound123", wasteLossRatioStringFromScope(
                            insight == null ? null : insight.get("scopeOutboundSubtotals")));
                    if (f.getGbDistributerFoodEntity() != null
                            && f.getGbDistributerFoodEntity().getGbDfFoodName() != null
                            && !f.getGbDistributerFoodEntity().getGbDfFoodName().trim().isEmpty()) {
                        z.put("foodName", f.getGbDistributerFoodEntity().getGbDfFoodName().trim());
                    }
                    f.setGbDfBusinessInsight(z);
                    f.setGbDfSalesAmount("0");
                }
            }
        }

        if (log.isDebugEnabled() && foods != null) {
            for (GbDepFoodEntity f : foods) {
                log.debug("attachToFoodRows after merge depFoodId={} gbDfFoodId={} distributerFoodPresent={} insightKeys={}",
                        f.getGbDepFoodId(), f.getGbDfFoodId(), f.getGbDistributerFoodEntity() != null,
                        f.getGbDfBusinessInsight() == null ? 0 : f.getGbDfBusinessInsight().size());
            }
        }

        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("businessInsightSummary", summarizeBusinessInsightFromFoodRows(foods, insight));
        extras.put("scopeOutboundSubtotals", insight.get("scopeOutboundSubtotals"));
        extras.put("weekdayLegend", insight.get("weekdayLegend"));
        extras.put("scopeDepIds", insight.get("scopeDepIds"));
        extras.put("bossColumnHintsZh", insight.get("bossColumnHintsZh"));
        extras.put("insightStartDate", insight.get("startDate"));
        extras.put("insightStopDate", insight.get("stopDate"));
        return extras;
    }

    /**
     * 顶部汇总：对当前列表每条 {@code gbDfBusinessInsight} 的标价收入、实际/理论成本求和，再算仅 type1 的 blended 毛利率；
     * 另汇总 {@code actualCostTotalAmount123}（type1+2+3 整菜区间实际成本）及「标价收入 vs 本区间 1+2+3 出库总成本」的综合毛利率。出库维度损耗率与 {@code scopeOutboundSubtotals} 一致。
     */
    private static Map<String, Object> summarizeBusinessInsightFromFoodRows(List<GbDepFoodEntity> foods,
            Map<String, Object> insight) {
        BigDecimal totalRev = BigDecimal.ZERO;
        BigDecimal totalActual = BigDecimal.ZERO;
        BigDecimal totalTheory = BigDecimal.ZERO;
        BigDecimal totalActual123 = BigDecimal.ZERO;
        int rowCount = 0;
        if (foods != null) {
            for (GbDepFoodEntity f : foods) {
                Map<String, Object> ins = f.getGbDfBusinessInsight();
                if (ins == null) {
                    continue;
                }
                rowCount++;
                totalRev = totalRev.add(GbDepartmentGoodsStockReduceSupport.coerceDecimal(ins.get("listPriceRevenue")));
                totalActual = totalActual.add(GbDepartmentGoodsStockReduceSupport.coerceDecimal(ins.get("actualCostAmount")));
                totalTheory = totalTheory.add(GbDepartmentGoodsStockReduceSupport.coerceDecimal(ins.get("theoryCostAmount")));
                totalActual123 = totalActual123.add(
                        GbDepartmentGoodsStockReduceSupport.coerceDecimal(ins.get("actualCostTotalAmount123")));
            }
        }
        BigDecimal subtotalOutbound123 = BigDecimal.ZERO;
        if (insight != null) {
            Object sc = insight.get("scopeOutboundSubtotals");
            if (sc instanceof Map) {
                Object s = ((Map<?, ?>) sc).get("subtotalOutbound123");
                subtotalOutbound123 = GbDepartmentGoodsStockReduceSupport.coerceDecimal(s);
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("dishRowCount", rowCount);
        m.put("totalListPriceRevenue", plainMoneySummary(totalRev));
        m.put("totalActualCostAmount", plainMoneySummary(totalActual));
        m.put("totalActualCostTotalAmount123", plainMoneySummary(totalActual123));
        m.put("totalTheoryCostAmount", plainMoneySummary(totalTheory));
        m.put("subtotalOutbound123FromScope", plainMoneySummary(subtotalOutbound123));
        if (totalRev.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal blendActual = totalRev.subtract(totalActual).divide(totalRev, 8, RoundingMode.HALF_UP);
            BigDecimal blendTheory = totalRev.subtract(totalTheory).divide(totalRev, 8, RoundingMode.HALF_UP);
            m.put("blendedGrossMarginRateOnListPrice",
                    GbDepartmentGoodsStockReduceSupport.formatRatioAsPercentTwoDecimals(blendActual));
            m.put("blendedGrossMarginRateTheoryOnListPrice",
                    GbDepartmentGoodsStockReduceSupport.formatRatioAsPercentTwoDecimals(blendTheory));
            BigDecimal comp = totalRev.subtract(subtotalOutbound123).divide(totalRev, 8, RoundingMode.HALF_UP);
            m.put("comprehensiveGrossMarginRateOnListPrice",
                    GbDepartmentGoodsStockReduceSupport.formatRatioAsPercentTwoDecimals(comp));
        } else {
            boolean allZero = totalRev.signum() == 0 && totalActual.signum() == 0 && totalTheory.signum() == 0;
            m.put("blendedGrossMarginRateOnListPrice", allZero ? "0.00" : null);
            m.put("blendedGrossMarginRateTheoryOnListPrice", allZero ? "0.00" : null);
            m.put("comprehensiveGrossMarginRateOnListPrice", allZero ? "0.00" : null);
        }
        Object scope = insight == null ? null : insight.get("scopeOutboundSubtotals");
        if (scope instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> sc = (Map<String, Object>) scope;
            m.put("wasteLossRatioInOutbound123", sc.get("wasteLossRatioInOutbound123"));
            m.put("subtotalProduceType1", sc.get("subtotalProduceType1"));
            m.put("subtotalWasteType2", sc.get("subtotalWasteType2"));
            m.put("subtotalLossType3", sc.get("subtotalLossType3"));
            m.put("subtotalOutbound123", sc.get("subtotalOutbound123"));
            m.put("wasteLossAmountType23", sc.get("wasteLossAmountType23"));
        }
        return m;
    }

    private static String plainMoneySummary(BigDecimal v) {
        if (v == null) {
            return "0";
        }
        return v.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static Map<String, Object> defaultEmptyInsightRow() {
        Map<String, Object> z = new LinkedHashMap<>();
        Map<String, Object> wd = new LinkedHashMap<>();
        for (int i = 0; i <= 6; i++) {
            wd.put(String.valueOf(i), "0");
        }
        z.put("weekdayQty", wd);
        z.put("soldPortionsUnassignedWeekday", "0");
        z.put("soldPortionsTotal", "0");
        z.put("listPrice", "");
        z.put("listPriceRevenue", "0");
        z.put("actualCostAmount", "0");
        z.put("theoryCostAmount", "0");
        z.put("grossMarginRateOnListPrice", "0.00");
        z.put("grossMarginRateTheoryOnListPrice", "0.00");
        z.put("actualCostPerPortion123", "0.00");
        z.put("actualCostTotalAmount123", "0");
        z.put("blendedGrossMarginRateOnListPrice", "0.00");
        z.put("wasteLossRatioInOutbound123", "0.00");
        z.put("grossMarginStandardTarget", null);
        z.put("grossMarginStandardFloatAbs", null);
        z.put("grossMarginStandardBandLower", null);
        z.put("grossMarginStandardBandUpper", null);
        z.put("grossMarginLevel", GrossMarginStandardDisplay.LEVEL_UNKNOWN);
        return z;
    }

    /** 与配料分析「单份成本」展示一致：固定两位小数、去尾零。 */
    private static String insightCostPerPortionTwoDecimals(BigDecimal v) {
        return nz(v).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static BigDecimal nz(BigDecimal b) {
        return b == null ? BigDecimal.ZERO : b;
    }

    private static Map<String, String> weekdayLegendZh() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("0", "周日");
        m.put("1", "周一");
        m.put("2", "周二");
        m.put("3", "周三");
        m.put("4", "周四");
        m.put("5", "周五");
        m.put("6", "周六");
        return Collections.unmodifiableMap(m);
    }

    private static final class WeekdaySalesAgg {
        final Map<Integer, Map<Integer, BigDecimal>> byFoodWeekday = new HashMap<>();
        final Map<Integer, BigDecimal> unassignedWeekdayQty = new HashMap<>();
    }

    private WeekdaySalesAgg loadQtyByFoodAndWeekday(Integer disId, List<Integer> scopeDepIds,
            String startDate, String stopDate) {
        WeekdaySalesAgg agg = new WeekdaySalesAgg();
        if (scopeDepIds == null || scopeDepIds.isEmpty()) {
            return agg;
        }
        LambdaQueryWrapper<GbDepFoodSalesEntity> w = new LambdaQueryWrapper<>();
        w.eq(GbDepFoodSalesEntity::getGbDfsDistributerId, disId)
                .ge(GbDepFoodSalesEntity::getGbDfsFullDate, startDate)
                .le(GbDepFoodSalesEntity::getGbDfsFullDate, stopDate);
        if (scopeDepIds.size() == 1) {
            w.eq(GbDepFoodSalesEntity::getGbDfsDepId, scopeDepIds.get(0));
        } else {
            w.in(GbDepFoodSalesEntity::getGbDfsDepId, scopeDepIds);
        }
        List<GbDepFoodSalesEntity> rows = gbDepFoodSalesService.list(w);
        if (rows == null) {
            return agg;
        }
        for (GbDepFoodSalesEntity row : rows) {
            Integer foodId = row.getGbDfsFoodId();
            if (foodId == null) {
                continue;
            }
            BigDecimal amt = GbDepartmentGoodsStockReduceSupport.coerceDecimal(row.getGbDfsAmount());
            if (amt.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            Integer wd = row.getGbDfsRevenueWeekday();
            if (wd == null || wd < 0 || wd > 6) {
                agg.unassignedWeekdayQty.merge(foodId, amt, BigDecimal::add);
                continue;
            }
            agg.byFoodWeekday.computeIfAbsent(foodId, k -> new HashMap<>()).merge(wd, amt, BigDecimal::add);
        }
        return agg;
    }

    private List<Integer> resolveScopeDepIds(Integer disId, String searchDepId, Integer depFatherId) {
        if (searchDepId != null && !"-1".equals(searchDepId)) {
            return Collections.singletonList(Integer.valueOf(searchDepId));
        }
        Map<String, Object> q = new HashMap<>();
        q.put("disId", disId);
        q.put("depType", getGbDepartmentTypeMendian());
        List<GbDepartmentEntity> groupDeps = gbDepartmentService.queryGroupDepsByDisId(q);
        if (groupDeps == null || groupDeps.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashMap<Integer, Boolean> uniq = new LinkedHashMap<>();
        for (GbDepartmentEntity group : groupDeps) {
            List<GbDepartmentEntity> subs = group.getGbDepartmentEntityList();
            if (subs == null || subs.isEmpty()) {
                continue;
            }
            for (GbDepartmentEntity sub : subs) {
                if (sub.getGbDepartmentId() == null) {
                    continue;
                }
                if (depFatherId != null && !depFatherId.equals(sub.getGbDepartmentFatherId())) {
                    continue;
                }
                uniq.put(sub.getGbDepartmentId(), Boolean.TRUE);
            }
        }
        return new ArrayList<>(uniq.keySet());
    }

    private static BigDecimal parseAmountSafe(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private static String wasteLossRatioStringFromScope(Object scopeOutbound) {
        if (!(scopeOutbound instanceof Map)) {
            return "0.00";
        }
        Object w = ((Map<?, ?>) scopeOutbound).get("wasteLossRatioInOutbound123");
        return w == null ? "0.00" : w.toString();
    }

    private static Map<String, Object> buildReduceParamsForInsight(Integer disId, Integer depFatherId,
            String startDate, String stopDate) {
        Map<String, Object> map = GbDepartmentGoodsStockReduceSupport.buildReduceCostQueryMap(
                startDate, stopDate, disId, null, null);
        if (depFatherId != null) {
            map.put("depFatherId", depFatherId);
        }
        return map;
    }

    @Override
    public void enrichFoodGoodsOutboundStats(List<GbDistributerFoodGoodsEntity> recipeLines, Integer disId,
            Integer depFatherId, String startDate, String stopDate) {
        if (recipeLines == null || recipeLines.isEmpty() || disId == null) {
            return;
        }
        Map<String, Object> reduceParams = buildReduceParamsForInsight(disId, depFatherId, startDate, stopDate);
        List<Map<String, Object>> prod = gbDepartmentGoodsStockReduceService.queryProductionReduceAggByDisGoods(reduceParams);
        List<Map<String, Object>> all = gbDepartmentGoodsStockReduceService.queryProduceLossWasteReduceAggByDisGoods(reduceParams);
        Map<Integer, BigDecimal> w1 = new HashMap<>();
        Map<Integer, BigDecimal> s1 = new HashMap<>();
        putAggByDisGoodsId(prod, w1, s1);
        Map<Integer, BigDecimal> w123 = new HashMap<>();
        Map<Integer, BigDecimal> s123 = new HashMap<>();
        putAggByDisGoodsId(all, w123, s123);
        for (GbDistributerFoodGoodsEntity line : recipeLines) {
            Integer gid = line.getGbDfgDisGoodsId();
            if (gid == null) {
                clearFoodGoodsOutboundStats(line);
                continue;
            }
            BigDecimal pW = w1.getOrDefault(gid, BigDecimal.ZERO);
            BigDecimal pS = s1.getOrDefault(gid, BigDecimal.ZERO);
            BigDecimal tW = w123.getOrDefault(gid, BigDecimal.ZERO);
            BigDecimal tS = s123.getOrDefault(gid, BigDecimal.ZERO);
            BigDecimal wlW = tW.subtract(pW);
            BigDecimal wlS = tS.subtract(pS);
            if (wlW.compareTo(BigDecimal.ZERO) < 0) {
                wlW = BigDecimal.ZERO;
            }
            if (wlS.compareTo(BigDecimal.ZERO) < 0) {
                wlS = BigDecimal.ZERO;
            }
            BigDecimal unit = pW.compareTo(BigDecimal.ZERO) > 0
                    ? pS.divide(pW, 8, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            line.setGbDfgOutboundUnitPrice(plainScale(unit, 4));
            line.setGbDfgProduceReduceWeight(plainScale(pW, 6));
            line.setGbDfgProduceReduceCost(plainScale(pS, 2));
            line.setGbDfgWasteLossReduceWeight(plainScale(wlW, 6));
            line.setGbDfgWasteLossReduceCost(plainScale(wlS, 2));
            line.setGbDfgOutbound123Weight(plainScale(tW, 6));
            line.setGbDfgOutbound123Cost(plainScale(tS, 2));
        }
    }

    private static void clearFoodGoodsOutboundStats(GbDistributerFoodGoodsEntity line) {
        line.setGbDfgOutboundUnitPrice("0");
        line.setGbDfgProduceReduceWeight("0");
        line.setGbDfgProduceReduceCost("0");
        line.setGbDfgWasteLossReduceWeight("0");
        line.setGbDfgWasteLossReduceCost("0");
        line.setGbDfgOutbound123Weight("0");
        line.setGbDfgOutbound123Cost("0");
    }

    private static void putAggByDisGoodsId(List<Map<String, Object>> rows,
            Map<Integer, BigDecimal> weightByG, Map<Integer, BigDecimal> subByG) {
        if (rows == null) {
            return;
        }
        for (Map<String, Object> row : rows) {
            Integer gid = toIntObject(row.get("disGoodsId"));
            if (gid == null) {
                continue;
            }
            weightByG.put(gid, GbDepartmentGoodsStockReduceSupport.coerceDecimal(row.get("weightSum")));
            subByG.put(gid, GbDepartmentGoodsStockReduceSupport.coerceDecimal(row.get("subtotalSum")));
        }
    }

    private static Integer toIntObject(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Integer) {
            return (Integer) o;
        }
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        try {
            return Integer.parseInt(o.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String plainScale(BigDecimal v, int scale) {
        if (v == null) {
            return "0";
        }
        return v.setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
