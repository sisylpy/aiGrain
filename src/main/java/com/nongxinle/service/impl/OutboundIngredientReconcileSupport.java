package com.nongxinle.service.impl;

import com.nongxinle.entity.GbDistributerGoodsEntity;
import com.nongxinle.utils.GbDepartmentGoodsStockReduceSupport;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code /outboundIngredientAnalysis} 顶部三张卡片与配料行：核销 / 非核销 / 员工餐拆分。
 * <p><strong>已实现</strong>：核销 = 按菜品分摊的 type1+2+3 出库成本中，归属<strong>区间内菜品消费份数 &gt; 0</strong>
 *（{@link com.nongxinle.utils.GbDepFoodSalesMetricsSupport#totalConsumptionQty}，type 1～5，含菜品型员工餐与赠送）的菜品行；
 * 且出库日不晚于「关联菜品末次消费日 + {@value #VERIFIED_OUTBOUND_GRACE_DAYS_AFTER_LAST_SALES} 天」
 *（宽限期内出库可核销，之后计未核销，如销售在 6/3、6/4 出库可核销、6/6 出库未核销）；
 * 非核销 = 同商品 type1+2+3 出库金额 − 核销；原料型员工餐 = reduce type6 单独列示（与 {@code gb_dep_food_sales type=5} 菜品型员工餐不同）。</p>
 * <p>出库总额 {@code outboundTotalAmount} 仍统计区间内全部 type1+2+3 出库。</p>
 */
final class OutboundIngredientReconcileSupport {

    private OutboundIngredientReconcileSupport() {}

    /** 末次菜品消费日之后仍可核销出库的自然日宽限（含当日）。 */
    static final int VERIFIED_OUTBOUND_GRACE_DAYS_AFTER_LAST_SALES = 1;

    /** {@code consumptionQty}：本菜区间内总消费份数（type 1～5），用于核销判定。 */
    record DishGoodCostLine(int foodId, int goodsId, BigDecimal consumptionQty, BigDecimal actCost, BigDecimal thCost) {}

    static final class GoodsBreakdown {
        final BigDecimal outbound123;
        final BigDecimal employeeMeal;
        final BigDecimal verified;
        final BigDecimal verifiedTheory;
        final BigDecimal nonVerified;

        GoodsBreakdown(BigDecimal outbound123, BigDecimal employeeMeal, BigDecimal verified,
                BigDecimal verifiedTheory, BigDecimal nonVerified) {
            this.outbound123 = outbound123;
            this.employeeMeal = employeeMeal;
            this.verified = verified;
            this.verifiedTheory = verifiedTheory;
            this.nonVerified = nonVerified;
        }
    }

    static final class ScopeBreakdown {
        final Map<Integer, GoodsBreakdown> byGoods;
        final BigDecimal outbound123Total;
        final BigDecimal employeeMealTotal;
        final BigDecimal verifiedTotal;
        final BigDecimal verifiedTheoryTotal;
        final BigDecimal nonVerifiedTotal;
        final int nonVerifiedDishCount;

        ScopeBreakdown(Map<Integer, GoodsBreakdown> byGoods, BigDecimal outbound123Total, BigDecimal employeeMealTotal,
                BigDecimal verifiedTotal, BigDecimal verifiedTheoryTotal, BigDecimal nonVerifiedTotal,
                int nonVerifiedDishCount) {
            this.byGoods = byGoods;
            this.outbound123Total = outbound123Total;
            this.employeeMealTotal = employeeMealTotal;
            this.verifiedTotal = verifiedTotal;
            this.verifiedTheoryTotal = verifiedTheoryTotal;
            this.nonVerifiedTotal = nonVerifiedTotal;
            this.nonVerifiedDishCount = nonVerifiedDishCount;
        }
    }

    static ScopeBreakdown compute(List<DishGoodCostLine> lines, Map<Integer, BigDecimal> outbound123ByGoods,
            Map<Integer, BigDecimal> employeeMealByGoods) {
        return compute(lines, outbound123ByGoods, employeeMealByGoods, null);
    }

    static ScopeBreakdown compute(List<DishGoodCostLine> lines, Map<Integer, BigDecimal> outbound123ByGoods,
            Map<Integer, BigDecimal> employeeMealByGoods,
            Map<Integer, BigDecimal> outbound123AfterSalesGraceByGoods) {
        Map<Integer, BigDecimal> verifiedByGoods = new LinkedHashMap<>();
        Map<Integer, BigDecimal> verifiedTheoryByGoods = new LinkedHashMap<>();
        Set<Integer> nonVerifiedDishIds = new HashSet<>();
        if (lines != null) {
            for (DishGoodCostLine line : lines) {
                if (line == null) {
                    continue;
                }
                BigDecimal act = nz(line.actCost());
                BigDecimal th = nz(line.thCost());
                if (hasVerifiableDishConsumption(line.consumptionQty())) {
                    verifiedByGoods.merge(line.goodsId(), act, BigDecimal::add);
                    verifiedTheoryByGoods.merge(line.goodsId(), th, BigDecimal::add);
                } else if (act.compareTo(BigDecimal.ZERO) > 0) {
                    nonVerifiedDishIds.add(line.foodId());
                }
            }
        }
        Set<Integer> allGoodsIds = new HashSet<>();
        if (outbound123ByGoods != null) {
            allGoodsIds.addAll(outbound123ByGoods.keySet());
        }
        if (employeeMealByGoods != null) {
            allGoodsIds.addAll(employeeMealByGoods.keySet());
        }
        allGoodsIds.addAll(verifiedByGoods.keySet());

        Map<Integer, GoodsBreakdown> byGoods = new LinkedHashMap<>();
        BigDecimal outbound123Total = BigDecimal.ZERO;
        BigDecimal employeeMealTotal = BigDecimal.ZERO;
        BigDecimal verifiedTotal = BigDecimal.ZERO;
        BigDecimal verifiedTheoryTotal = BigDecimal.ZERO;
        BigDecimal nonVerifiedTotal = BigDecimal.ZERO;

        for (Integer gId : allGoodsIds) {
            BigDecimal outbound123 = nz(outbound123ByGoods == null ? null : outbound123ByGoods.get(gId));
            BigDecimal employeeMeal = nz(employeeMealByGoods == null ? null : employeeMealByGoods.get(gId));
            BigDecimal verified = nz(verifiedByGoods.get(gId));
            if (outbound123AfterSalesGraceByGoods != null && verified.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal afterGrace = nz(outbound123AfterSalesGraceByGoods.get(gId));
                BigDecimal verifiablePool = outbound123.subtract(afterGrace);
                if (verifiablePool.compareTo(BigDecimal.ZERO) < 0) {
                    verifiablePool = BigDecimal.ZERO;
                }
                verified = verified.min(verifiablePool);
            }
            BigDecimal verifiedTheory = nz(verifiedTheoryByGoods.get(gId));
            if (verified.compareTo(outbound123) > 0) {
                verified = outbound123;
            }
            BigDecimal nonVerified = outbound123.subtract(verified);
            if (nonVerified.compareTo(BigDecimal.ZERO) < 0) {
                nonVerified = BigDecimal.ZERO;
            }
            byGoods.put(gId, new GoodsBreakdown(outbound123, employeeMeal, verified, verifiedTheory, nonVerified));
            outbound123Total = outbound123Total.add(outbound123);
            employeeMealTotal = employeeMealTotal.add(employeeMeal);
            verifiedTotal = verifiedTotal.add(verified);
            verifiedTheoryTotal = verifiedTheoryTotal.add(verifiedTheory);
            nonVerifiedTotal = nonVerifiedTotal.add(nonVerified);
        }

        return new ScopeBreakdown(byGoods, outbound123Total, employeeMealTotal, verifiedTotal, verifiedTheoryTotal,
                nonVerifiedTotal, nonVerifiedDishIds.size());
    }

    static Map<Integer, BigDecimal> computeOutbound123AfterSalesGraceByGoods(
            List<Map<String, Object>> reduceByDateRows,
            Map<Integer, Set<Integer>> foodIdsByGoods,
            Map<Integer, String> lastOperationalSalesDateByFood) {
        Map<Integer, LocalDate> lastSalesByGoods = resolveLastOperationalSalesDateByGoods(
                foodIdsByGoods, lastOperationalSalesDateByFood);
        Map<Integer, BigDecimal> afterGrace = new LinkedHashMap<>();
        if (reduceByDateRows == null || lastSalesByGoods.isEmpty()) {
            return afterGrace;
        }
        for (Map<String, Object> row : reduceByDateRows) {
            if (row == null) {
                continue;
            }
            Integer gId = toInt(row.get("disGoodsId"));
            LocalDate reduceDate = parseReduceDate(row.get("reduceDate"));
            if (gId == null || reduceDate == null) {
                continue;
            }
            LocalDate lastSales = lastSalesByGoods.get(gId);
            if (lastSales == null) {
                continue;
            }
            LocalDate graceEnd = lastSales.plusDays(VERIFIED_OUTBOUND_GRACE_DAYS_AFTER_LAST_SALES);
            if (reduceDate.isAfter(graceEnd)) {
                afterGrace.merge(gId,
                        nz(GbDepartmentGoodsStockReduceSupport.coerceDecimal(row.get("subtotalSum"))),
                        BigDecimal::add);
            }
        }
        return afterGrace;
    }

    static Map<Integer, Set<Integer>> buildFoodIdsByGoods(
            Map<Integer, ? extends List<? extends com.nongxinle.entity.GbDistributerFoodGoodsEntity>> recipeByFoodId) {
        Map<Integer, Set<Integer>> out = new LinkedHashMap<>();
        if (recipeByFoodId == null) {
            return out;
        }
        for (Map.Entry<Integer, ? extends List<? extends com.nongxinle.entity.GbDistributerFoodGoodsEntity>> en
                : recipeByFoodId.entrySet()) {
            Integer foodId = en.getKey();
            List<? extends com.nongxinle.entity.GbDistributerFoodGoodsEntity> recipe = en.getValue();
            if (foodId == null || recipe == null) {
                continue;
            }
            for (com.nongxinle.entity.GbDistributerFoodGoodsEntity line : recipe) {
                if (!GbDepartmentGoodsStockReduceSupport.isActiveFoodGoodsLine(line)) {
                    continue;
                }
                Integer gId = line.getGbDfgDisGoodsId();
                if (gId == null) {
                    continue;
                }
                out.computeIfAbsent(gId, k -> new LinkedHashSet<>()).add(foodId);
            }
        }
        return out;
    }

    static void trackLastConsumptionDate(Map<Integer, String> target, Integer foodId, String salesDate) {
        if (target == null || foodId == null || salesDate == null) {
            return;
        }
        String d = salesDate.trim();
        if (d.length() >= 10) {
            d = d.substring(0, 10);
        }
        if (d.isEmpty()) {
            return;
        }
        String prev = target.get(foodId);
        if (prev == null || d.compareTo(prev) > 0) {
            target.put(foodId, d);
        }
    }

    private static Map<Integer, LocalDate> resolveLastOperationalSalesDateByGoods(
            Map<Integer, Set<Integer>> foodIdsByGoods,
            Map<Integer, String> lastOperationalSalesDateByFood) {
        Map<Integer, LocalDate> out = new LinkedHashMap<>();
        if (foodIdsByGoods == null || lastOperationalSalesDateByFood == null) {
            return out;
        }
        for (Map.Entry<Integer, Set<Integer>> en : foodIdsByGoods.entrySet()) {
            Integer gId = en.getKey();
            Set<Integer> foodIds = en.getValue();
            if (gId == null || foodIds == null || foodIds.isEmpty()) {
                continue;
            }
            LocalDate max = null;
            for (Integer foodId : foodIds) {
                LocalDate d = parseReduceDate(lastOperationalSalesDateByFood.get(foodId));
                if (d == null) {
                    continue;
                }
                if (max == null || d.isAfter(max)) {
                    max = d;
                }
            }
            if (max != null) {
                out.put(gId, max);
            }
        }
        return out;
    }

    private static LocalDate parseReduceDate(Object raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.toString().trim();
        if (s.length() >= 10) {
            s = s.substring(0, 10);
        }
        if (s.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(s);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    static Map<String, Object> buildSummaryCard(ScopeBreakdown scope, BigDecimal scopeSalesRevenue) {
        Map<String, Object> summ = new LinkedHashMap<>();
        summ.put("totalOutboundAmount", money(nz(scope.outbound123Total).add(nz(scope.employeeMealTotal))));
        summ.put("employeeMealTotalAmount", money(scope.employeeMealTotal));
        summ.put("verifiedTotalAmount", money(scope.verifiedTotal));
        summ.put("nonVerifiedTotalAmount", money(scope.nonVerifiedTotal));
        summ.put("nonVerifiedDishCount", scope.nonVerifiedDishCount);

        BigDecimal sales = nz(scopeSalesRevenue);
        summ.put("verifiedGrossMarginRate", marginRateOnListPriceString(sales, scope.verifiedTotal));
        BigDecimal verifiedDeviation = scope.verifiedTotal.subtract(scope.verifiedTheoryTotal);
        summ.put("verifiedTheoryDeviationAmount", money(verifiedDeviation));
        summ.put("verifiedTheoryDeviationRate", deviationRateString(scope.verifiedTheoryTotal, verifiedDeviation));

        // 兼容旧卡片 / AI 摘要读取
        summ.put("actualOutboundAmount", money(scope.verifiedTotal));
        summ.put("theoryOutboundAmount", money(scope.verifiedTheoryTotal));
        summ.put("actualGrossMarginRate", summ.get("verifiedGrossMarginRate"));
        summ.put("theoryGrossMarginRate",
                marginRateOnListPriceString(sales, scope.verifiedTheoryTotal));
        return summ;
    }

    /** {@code all|verified|unverified}；空为 {@code all}。 */
    static String normalizeVerificationStatus(String verificationStatus) {
        if (verificationStatus == null || verificationStatus.trim().isEmpty()) {
            return "all";
        }
        String s = verificationStatus.trim().toLowerCase().replace(" ", "");
        if ("all".equals(s) || "全部".equals(s) || "全量".equals(s)) {
            return "all";
        }
        if ("verified".equals(s) || "已核销".equals(s)) {
            return "verified";
        }
        if ("unverified".equals(s) || "未核销".equals(s) || "非核销".equals(s)) {
            return "unverified";
        }
        throw new IllegalArgumentException(
                "verificationStatus 仅支持 all(全量)、verified(已核销)、unverified(未核销)，当前: "
                        + verificationStatus);
    }

    static boolean matchesVerificationStatus(GoodsBreakdown bd, String normalizedStatus) {
        if (bd == null || "all".equals(normalizedStatus)) {
            return true;
        }
        if ("verified".equals(normalizedStatus)) {
            return hasPositiveDisplayMoney(bd.verified);
        }
        if ("unverified".equals(normalizedStatus)) {
            return hasPositiveDisplayMoney(bd.nonVerified);
        }
        return true;
    }

    /** 与 {@link #money(BigDecimal)} 展示口径一致：两位小数四舍五入后 &gt; 0 才算有金额。 */
    static boolean hasPositiveDisplayMoney(BigDecimal v) {
        return nz(v).setScale(2, RoundingMode.HALF_UP).compareTo(BigDecimal.ZERO) > 0;
    }

    /** 配料列表商品集合：配方分摊行 + 区间内有 type1+2+3 或员工餐出库、但未进入分摊行的商品。 */
    static LinkedHashSet<Integer> mergeOutboundIngredientListGoodsIds(Set<Integer> recipeGoodsIds,
            Map<Integer, BigDecimal> outbound123ByGoods, Map<Integer, BigDecimal> employeeMealByGoods) {
        LinkedHashSet<Integer> ids = new LinkedHashSet<>();
        if (recipeGoodsIds != null) {
            ids.addAll(recipeGoodsIds);
        }
        appendGoodsIdsWithPositiveAmount(ids, outbound123ByGoods);
        appendGoodsIdsWithPositiveAmount(ids, employeeMealByGoods);
        return ids;
    }

    /** 核销筛选下列表商品：以 {@link ScopeBreakdown#byGoods} 为 SSOT，避免仅出库、无销量菜品被漏掉。 */
    static List<Integer> listGoodsIdsMatchingVerification(ScopeBreakdown scope, String normalizedStatus) {
        List<Integer> ids = new ArrayList<>();
        if (scope == null || scope.byGoods == null || "all".equals(normalizedStatus)) {
            return ids;
        }
        for (Map.Entry<Integer, GoodsBreakdown> en : scope.byGoods.entrySet()) {
            if (en.getKey() != null && matchesVerificationStatus(en.getValue(), normalizedStatus)) {
                ids.add(en.getKey());
            }
        }
        return ids;
    }

    private static void appendGoodsIdsWithPositiveAmount(Set<Integer> ids, Map<Integer, BigDecimal> amountByGoods) {
        if (amountByGoods == null) {
            return;
        }
        for (Map.Entry<Integer, BigDecimal> en : amountByGoods.entrySet()) {
            if (en.getKey() != null && nz(en.getValue()).compareTo(BigDecimal.ZERO) > 0) {
                ids.add(en.getKey());
            }
        }
    }

    static GoodsBreakdown goodsBreakdown(ScopeBreakdown scope, int goodsId) {
        if (scope == null || scope.byGoods == null) {
            return new GoodsBreakdown(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO);
        }
        GoodsBreakdown bd = scope.byGoods.get(goodsId);
        return bd == null
                ? new GoodsBreakdown(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO)
                : bd;
    }

    static Map<String, Object> rowReconcileFields(GoodsBreakdown bd) {
        return rowReconcileFields(bd, null, null);
    }

    /**
     * 配料行核销拆分：金额 + 数量（type1+2+3 重量按金额比例拆分；无金额时未核销重量 = 全部出库重量）。
     */
    static Map<String, Object> rowReconcileFields(GoodsBreakdown bd, BigDecimal outboundWeight123,
            BigDecimal employeeMealWeight) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("outboundTotalAmount", money(bd.outbound123));
        fields.put("verifiedAmount", money(bd.verified));
        fields.put("nonVerifiedAmount", money(bd.nonVerified));
        fields.put("employeeMealAmount", money(bd.employeeMeal));
        BigDecimal w123 = nz(outboundWeight123);
        BigDecimal mealW = nz(employeeMealWeight);
        fields.put("outboundTotalQty", qty(w123));
        fields.put("employeeMealQty", qty(mealW));
        if (w123.compareTo(BigDecimal.ZERO) > 0) {
            if (bd.outbound123.compareTo(BigDecimal.ZERO) > 0) {
                fields.put("verifiedQty", qty(w123.multiply(bd.verified)
                        .divide(bd.outbound123, 8, RoundingMode.HALF_UP)));
                fields.put("nonVerifiedQty", qty(w123.multiply(bd.nonVerified)
                        .divide(bd.outbound123, 8, RoundingMode.HALF_UP)));
            } else {
                fields.put("verifiedQty", qty(BigDecimal.ZERO));
                fields.put("nonVerifiedQty", qty(w123));
            }
        } else {
            fields.put("verifiedQty", qty(BigDecimal.ZERO));
            fields.put("nonVerifiedQty", qty(BigDecimal.ZERO));
        }
        return fields;
    }

    static Map<Integer, BigDecimal> indexSubtotalByGoods(List<Map<String, Object>> aggRows) {
        Map<Integer, BigDecimal> out = new LinkedHashMap<>();
        if (aggRows == null) {
            return out;
        }
        for (Map<String, Object> row : aggRows) {
            Integer gId = toInt(row.get("disGoodsId"));
            if (gId == null) {
                continue;
            }
            out.put(gId, nz(GbDepartmentGoodsStockReduceSupport.coerceDecimal(row.get("subtotalSum"))));
        }
        return out;
    }

    static Map<Integer, BigDecimal> indexWeightByGoods(List<Map<String, Object>> aggRows) {
        Map<Integer, BigDecimal> out = new LinkedHashMap<>();
        if (aggRows == null) {
            return out;
        }
        for (Map<String, Object> row : aggRows) {
            Integer gId = toInt(row.get("disGoodsId"));
            if (gId == null) {
                continue;
            }
            out.put(gId, nz(GbDepartmentGoodsStockReduceSupport.coerceDecimal(row.get("weightSum"))));
        }
        return out;
    }

    static Map<Integer, BigDecimal> mergeOutbound123WeightByGoods(Map<Integer, BigDecimal> produce,
            Map<Integer, BigDecimal> waste, Map<Integer, BigDecimal> loss) {
        Map<Integer, BigDecimal> out = new LinkedHashMap<>();
        mergeAmountMap(out, produce);
        mergeAmountMap(out, waste);
        mergeAmountMap(out, loss);
        return out;
    }

    /**
     * 单类型出库（如员工餐 type6）：{@code subtotalSum} 为空时用重量 × 商品主档价补全。
     */
    static Map<Integer, BigDecimal> enrichAmountWhenWeightMissing(Map<Integer, BigDecimal> amountByGoods,
            Map<Integer, BigDecimal> weightByGoods, Map<Integer, BigDecimal> catalogUnitPriceByGoods) {
        Map<Integer, BigDecimal> out = new LinkedHashMap<>();
        if (amountByGoods != null) {
            out.putAll(amountByGoods);
        }
        if (weightByGoods == null) {
            return out;
        }
        for (Map.Entry<Integer, BigDecimal> en : weightByGoods.entrySet()) {
            Integer gId = en.getKey();
            if (gId == null) {
                continue;
            }
            if (nz(out.get(gId)).compareTo(BigDecimal.ZERO) > 0) {
                continue;
            }
            BigDecimal weight = nz(en.getValue());
            if (weight.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal unitPrice = catalogUnitPriceByGoods == null ? BigDecimal.ZERO : nz(catalogUnitPriceByGoods.get(gId));
            if (unitPrice.compareTo(BigDecimal.ZERO) > 0) {
                out.put(gId, weight.multiply(unitPrice).setScale(8, RoundingMode.HALF_UP));
            }
        }
        return out;
    }

    /**
     * 出库子表 {@code subtotalSum} 为空时，用 (type1+2+3) 重量 × 单价补全金额；单价优先出库 s/w，其次商品主档价。
     */
    static Map<Integer, BigDecimal> enrichOutbound123WhenSubtotalMissing(
            Map<Integer, BigDecimal> outbound123ByGoods,
            Map<Integer, BigDecimal> reduceW, Map<Integer, BigDecimal> wasteW, Map<Integer, BigDecimal> lossW,
            Map<Integer, BigDecimal> reduceS, Map<Integer, BigDecimal> wasteS, Map<Integer, BigDecimal> lossS,
            Map<Integer, BigDecimal> catalogUnitPriceByGoods) {
        Map<Integer, BigDecimal> out = new LinkedHashMap<>();
        if (outbound123ByGoods != null) {
            out.putAll(outbound123ByGoods);
        }
        Set<Integer> gIds = new LinkedHashSet<>();
        collectGoodsIdsFromMap(gIds, reduceW);
        collectGoodsIdsFromMap(gIds, wasteW);
        collectGoodsIdsFromMap(gIds, lossW);
        for (Integer gId : gIds) {
            BigDecimal amount = nz(out.get(gId));
            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                continue;
            }
            BigDecimal totalW = nz(reduceW == null ? null : reduceW.get(gId))
                    .add(nz(wasteW == null ? null : wasteW.get(gId)))
                    .add(nz(lossW == null ? null : lossW.get(gId)));
            if (totalW.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal unitPrice = resolveReduceUnitPrice(gId, reduceW, wasteW, lossW, reduceS, wasteS, lossS);
            if (unitPrice.compareTo(BigDecimal.ZERO) <= 0 && catalogUnitPriceByGoods != null) {
                unitPrice = nz(catalogUnitPriceByGoods.get(gId));
            }
            if (unitPrice.compareTo(BigDecimal.ZERO) > 0) {
                out.put(gId, totalW.multiply(unitPrice).setScale(8, RoundingMode.HALF_UP));
            }
        }
        return out;
    }

    static Map<Integer, BigDecimal> buildCatalogUnitPriceByGoods(Map<Integer, GbDistributerGoodsEntity> byId) {
        Map<Integer, BigDecimal> out = new LinkedHashMap<>();
        if (byId == null) {
            return out;
        }
        for (Map.Entry<Integer, GbDistributerGoodsEntity> en : byId.entrySet()) {
            if (en.getKey() == null || en.getValue() == null) {
                continue;
            }
            BigDecimal price = parseGoodsPriceString(en.getValue().getGbDgGoodsPrice());
            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                price = parseGoodsPriceString(en.getValue().getGbDgGoodsAveragePrice());
            }
            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                price = parseGoodsPriceString(en.getValue().getGbDgSellingPrice());
            }
            if (price.compareTo(BigDecimal.ZERO) > 0) {
                out.put(en.getKey(), price);
            }
        }
        return out;
    }

    static Map<Integer, BigDecimal> mergeOutbound123ByGoods(Map<Integer, BigDecimal> produce,
            Map<Integer, BigDecimal> waste, Map<Integer, BigDecimal> loss) {
        Map<Integer, BigDecimal> out = new LinkedHashMap<>();
        mergeAmountMap(out, produce);
        mergeAmountMap(out, waste);
        mergeAmountMap(out, loss);
        return out;
    }

    private static BigDecimal resolveReduceUnitPrice(int gId,
            Map<Integer, BigDecimal> reduceW, Map<Integer, BigDecimal> wasteW, Map<Integer, BigDecimal> lossW,
            Map<Integer, BigDecimal> reduceS, Map<Integer, BigDecimal> wasteS, Map<Integer, BigDecimal> lossS) {
        BigDecimal totalW = BigDecimal.ZERO;
        BigDecimal totalS = BigDecimal.ZERO;
        totalW = totalW.add(nz(reduceW == null ? null : reduceW.get(gId)));
        totalS = totalS.add(nz(reduceS == null ? null : reduceS.get(gId)));
        totalW = totalW.add(nz(wasteW == null ? null : wasteW.get(gId)));
        totalS = totalS.add(nz(wasteS == null ? null : wasteS.get(gId)));
        totalW = totalW.add(nz(lossW == null ? null : lossW.get(gId)));
        totalS = totalS.add(nz(lossS == null ? null : lossS.get(gId)));
        if (totalW.compareTo(BigDecimal.ZERO) > 0 && totalS.compareTo(BigDecimal.ZERO) > 0) {
            return totalS.divide(totalW, 8, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    private static void collectGoodsIdsFromMap(Set<Integer> target, Map<Integer, BigDecimal> map) {
        if (map == null) {
            return;
        }
        target.addAll(map.keySet());
    }

    private static BigDecimal parseGoodsPriceString(String raw) {
        if (raw == null) {
            return BigDecimal.ZERO;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    private static String qty(BigDecimal v) {
        return nz(v).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static void mergeAmountMap(Map<Integer, BigDecimal> target, Map<Integer, BigDecimal> add) {
        if (add == null) {
            return;
        }
        for (Map.Entry<Integer, BigDecimal> en : add.entrySet()) {
            if (en.getKey() == null) {
                continue;
            }
            target.merge(en.getKey(), nz(en.getValue()), BigDecimal::add);
        }
    }

    private static boolean hasVerifiableDishConsumption(BigDecimal consumptionQty) {
        return consumptionQty != null && consumptionQty.compareTo(BigDecimal.ZERO) > 0;
    }

    private static String money(BigDecimal v) {
        return nz(v).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String marginRateOnListPriceString(BigDecimal revenue, BigDecimal cost) {
        BigDecimal rev = nz(revenue);
        BigDecimal co = nz(cost);
        if (rev.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal ratio = rev.subtract(co).divide(rev, 8, RoundingMode.HALF_UP);
            return GbDepartmentGoodsStockReduceSupport.formatRatioAsPercentTwoDecimals(ratio);
        }
        return rev.signum() == 0 && co.signum() == 0 ? "0.00" : null;
    }

    private static String deviationRateString(BigDecimal theoryBase, BigDecimal deviation) {
        if (theoryBase == null || theoryBase.compareTo(BigDecimal.ZERO) <= 0) {
            return deviation.signum() == 0 ? "0.00" : null;
        }
        BigDecimal ratio = deviation.divide(theoryBase, 8, RoundingMode.HALF_UP);
        return GbDepartmentGoodsStockReduceSupport.formatRatioAsPercentTwoDecimals(ratio);
    }

    private static BigDecimal nz(BigDecimal b) {
        return b == null ? BigDecimal.ZERO : b;
    }

    private static Integer toInt(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Integer) {
            return (Integer) o;
        }
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        String s = o.toString().trim();
        if (s.isEmpty()) {
            return null;
        }
        return Integer.parseInt(s);
    }
}
