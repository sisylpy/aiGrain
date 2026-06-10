package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nongxinle.constants.AiInsightDishProfitScope;
import com.nongxinle.entity.GbDepartmentEntity;
import com.nongxinle.entity.GbDepFoodEntity;
import com.nongxinle.entity.GbDepFoodSalesEntity;
import com.nongxinle.entity.GbDistributerFoodEntity;
import com.nongxinle.entity.GbDistributerFoodGoodsEntity;
import com.nongxinle.mapper.GbDepartmentMapper;
import com.nongxinle.service.GbDepFoodBusinessInsightService;
import com.nongxinle.service.GbDistributerFoodService;
import com.nongxinle.service.GbDepFoodSalesService;
import com.nongxinle.service.GbDepFoodService;
import com.nongxinle.service.GbDepartmentGoodsStockReduceService;
import com.nongxinle.service.GbDepartmentService;
import com.nongxinle.service.GbDishCostAnalysisService;
import com.nongxinle.utils.GbDateTimeUtils;
import com.nongxinle.utils.GbDepartmentGoodsStockReduceSupport;
import com.nongxinle.utils.GbDepFoodSalesMetricsSupport;
import com.nongxinle.utils.GbConstants;
import com.nongxinle.utils.GrossMarginStandardDisplay;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    /** 与 {@code businessInsightSummary} JSON 键一一对应的老板可读说明。 */
    private static final Map<String, String> BUSINESS_INSIGHT_SUMMARY_HINTS_ZH;
    static {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        m.put("dishRowCount", "参与汇总的菜品行数（本列表或 insight 内 dishes 条数）");
        m.put("totalListPriceRevenue", "总营业额（元，全部 type 1-5 subtotal 合计）");
        m.put("totalActualCostAmount", "type=1（生产）实际成本合计（元）：与按菜 grossMarginRateOnListPrice 分子分母同源");
        m.put("totalActualCostTotalAmount123", "type1+2+3 单份实际成本×实销份数后的整菜金额合计（元）");
        m.put("totalTheoryCostAmount", "type=1 理论成本合计（元）");
        m.put("subtotalOutbound123FromScope", "区间 type1+2+3 出库金额合计（元），与 scopeOutboundSubtotals.subtotalOutbound123 一致");
        m.put("blendedGrossMarginRateOnListPrice", "综合毛利率（仅 type1 实际成本）：(标价收入合计−type1 实际成本合计)÷标价收入合计，百分数两位小数");
        m.put("blendedGrossMarginRateTheoryOnListPrice", "理论综合毛利率（仅 type1 理论成本），计算方式同上");
        m.put("comprehensiveGrossMarginRateOnListPrice", "列表标价综合毛利率：(标价收入合计−区间 1+2+3 出库总成本)÷标价收入合计，百分数两位小数");
        m.put("wasteLossRatioInOutbound123", "区间损耗率：(type2+3 出库金额)÷(type1+2+3 出库金额)，百分数两位小数");
        m.put("subtotalProduceType1", "区间 type=1 生产出库金额合计（元）");
        m.put("subtotalWasteType2", "区间 type=2 损耗出库金额合计（元）");
        m.put("subtotalLossType3", "区间 type=3 损失出库金额合计（元）");
        m.put("subtotalOutbound123", "区间 type1+2+3 出库金额合计（元）");
        m.put("wasteLossAmountType23", "区间 type2+3 出库金额合计（元）");
        m.put("subtotalEmployeeMealType6", "区间 type=6（原料型员工餐）出库金额合计（元）；与 sales type=5 菜品型员工餐分摊的 employeeMealCostAmount 不同层");
        m.put("totalVerifiedAmount", "已核销总额（元）：totalConsumptionQty > 0 的菜品 actualCostAmount 合计 + wasteLossAmountType23（损耗报废出库即计已核销）");
        m.put("totalVerifiedAmountPercent", "已核销毛利率（%）：(标价收入合计−已核销总额)÷标价收入合计，百分数两位小数，与 blendedGrossMarginRate 算法统一");
        m.put("totalNonVerifiedAmount", "未核销总额（元）：(totalActualCostAmount + wasteLossAmountType23) − totalVerifiedAmount");
        m.put("totalNonVerifiedAmountPercent", "未核销毛利率（%）：(标价收入合计−未核销总额)÷标价收入合计，百分数两位小数");
        m.put("totalEmployeeMealQty", "员工餐总份数（type=5，FoodSalesType.isEmployeeMeal 判定）");
        m.put("totalEmployeeMealAmount", "员工餐总金额（元，type=5 subtotal 合计）");
        m.put("totalDiscountGiftQty", "打折餐总份数（折扣+会员+赠送 = type 2+3+4）");
        m.put("totalDiscountGiftAmount", "打折餐总金额（元，折扣+会员+赠送 = type 2+3+4 subtotal 合计）");
        m.put("totalNormalSaleQty", "正常销售总份数（type=1）");
        m.put("totalNormalSaleAmount", "正常销售总金额（元，type=1 subtotal 合计）");
        BUSINESS_INSIGHT_SUMMARY_HINTS_ZH = Collections.unmodifiableMap(m);
    }

    private final GbDepFoodService gbDepFoodService;
    private final GbDepFoodSalesService gbDepFoodSalesService;
    private final GbDepartmentService gbDepartmentService;
    private final GbDepartmentMapper gbDepartmentMapper;
    private final GbDishCostAnalysisService gbDishCostAnalysisService;
    private final GbDepartmentGoodsStockReduceService gbDepartmentGoodsStockReduceService;
    private final GbDistributerFoodService gbDistributerFoodService;

    @Override
    public Map<String, Object> buildInsight(Integer disId, Integer depFatherId, String startDate, String stopDate, Integer subDepId,
            Collection<Integer> scopeDepartmentIdsAllowFilter) {
        if (disId == null || depFatherId == null) {
            throw new IllegalArgumentException("disId、depFatherId 不能为空");
        }
        boolean groupWideAgg = AiInsightDishProfitScope.isGroupWideMendianAggregateUnderDis(depFatherId);
        if (subDepId != null) {
            if (groupWideAgg) {
                throw new IllegalArgumentException("集团多维菜品聚合模式下不可指定 subDepId");
            }
            GbDepartmentEntity sub = gbDepartmentService.getById(subDepId);
            if (sub == null) {
                throw new IllegalArgumentException("子部门不存在: " + subDepId);
            }
            if (!Objects.equals(sub.getGbDepartmentFatherId(), depFatherId)) {
                throw new IllegalArgumentException("subDepId 与 depFatherId 不是父子关系");
            }
        }
        if (startDate == null || startDate.trim().isEmpty() || stopDate == null || stopDate.trim().isEmpty()) {
            throw new IllegalArgumentException("startDate、stopDate 不能为空");
        }
        String sd = startDate.trim();
        String ed = stopDate.trim();

        String searchDepStr = subDepId == null ? null : String.valueOf(subDepId);
        List<Integer> scopeDepIds = resolveScopeDepIds(disId, searchDepStr, depFatherId, scopeDepartmentIdsAllowFilter);

        Map<String, Object> dishReport = gbDishCostAnalysisService.buildReport(sd, ed, disId, searchDepStr, depFatherId,
                REPORT_SALES_DISH, scopeDepartmentIdsAllowFilter);
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
        List<GbDepFoodEntity> foods;
        if (subDepId != null) {
            depMap.put("depId", subDepId);
            foods = gbDepFoodService.queryDepAllFood(depMap);
        } else if (groupWideAgg) {
            List<Integer> menuRoots = resolveStoreRootsForGroupWideMenu(disId, scopeDepartmentIdsAllowFilter);
            foods = mergeDepFoodAcrossMendianParents(disId, menuRoots);
        } else {
            depMap.put("depFatherId", depFatherId);
            foods = gbDepFoodService.queryDepAllFood(depMap);
        }
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
            actPp123ByFoodId = gbDishCostAnalysisService.getDishActualCostPerPortion123ByFoodIds(
                    sd, ed, disId, depFatherId, searchDepStr, insightFoodIds, scopeDepartmentIdsAllowFilter);
        }

        List<Map<String, Object>> dishes = new ArrayList<>();
        for (GbDepFoodEntity f : foods) {
            Integer foodId = f.getGbDfFoodId();
            GbDistributerFoodEntity disRowForName = foodId == null ? null : disFoodById.get(foodId);
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("gbDepFoodId", f.getGbDepFoodId());
            line.put("gbDfDepId", f.getGbDfDepId());
            line.put("foodId", foodId);
            String name = f.getGbDfFoodName();
            if (name == null || name.trim().isEmpty()) {
                if (disRowForName != null && disRowForName.getGbDfFoodName() != null
                        && !disRowForName.getGbDfFoodName().trim().isEmpty()) {
                    name = disRowForName.getGbDfFoodName().trim();
                } else {
                    name = "";
                }
            }
            line.put("foodName", name);
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

            BigDecimal listPriceRevenue = foodId == null ? BigDecimal.ZERO
                    : salesAgg.listPriceRevenueByFood.getOrDefault(foodId, BigDecimal.ZERO);
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

            BigDecimal consumptionQty = foodId == null ? BigDecimal.ZERO
                    : salesAgg.consumptionQtyByFood.getOrDefault(foodId, totalQty);
            // 核销判定用：type 1-5 总消费份数（与 OutboundIngredientReconcileSupport 同口径）
            BigDecimal totalConsumptionQty = foodId == null ? BigDecimal.ZERO
                    : salesAgg.totalConsumptionQtyByFood.getOrDefault(foodId,
                            consumptionQty);
            line.put("totalConsumptionQty", totalConsumptionQty.stripTrailingZeros().toPlainString());
            BigDecimal operationalActualCost = GbDepFoodSalesMetricsSupport.operationalShareOfCost(
                    actualCost, totalQty, consumptionQty);
            BigDecimal employeeMealCost = GbDepFoodSalesMetricsSupport.employeeMealShareOfCost(
                    actualCost, totalQty, consumptionQty);
            line.put("employeeMealCostAmount",
                    employeeMealCost.stripTrailingZeros().toPlainString());

            // 员工餐（type 5）：份数 + subtotal
            BigDecimal employeeMealQty = foodId == null ? BigDecimal.ZERO
                    : salesAgg.employeeMealQtyByFood.getOrDefault(foodId, BigDecimal.ZERO);
            BigDecimal employeeMealAmount = foodId == null ? BigDecimal.ZERO
                    : salesAgg.employeeMealAmountByFood.getOrDefault(foodId, BigDecimal.ZERO);
            line.put("employeeMealQty", employeeMealQty.stripTrailingZeros().toPlainString());
            line.put("employeeMealAmount", employeeMealAmount.stripTrailingZeros().toPlainString());

            // 打折餐（折扣+会员+赠送 = type 2+3+4）：份数 + subtotal
            BigDecimal discountGiftQty = foodId == null ? BigDecimal.ZERO
                    : salesAgg.discountGiftQtyByFood.getOrDefault(foodId, BigDecimal.ZERO);
            BigDecimal discountGiftAmount = foodId == null ? BigDecimal.ZERO
                    : salesAgg.discountGiftAmountByFood.getOrDefault(foodId, BigDecimal.ZERO);
            line.put("discountGiftQty", discountGiftQty.stripTrailingZeros().toPlainString());
            line.put("discountGiftAmount", discountGiftAmount.stripTrailingZeros().toPlainString());

            // 正常销售（type 1）：份数 + subtotal
            BigDecimal normalSaleQty = foodId == null ? BigDecimal.ZERO
                    : salesAgg.normalSaleQtyByFood.getOrDefault(foodId, BigDecimal.ZERO);
            BigDecimal normalSaleAmount = foodId == null ? BigDecimal.ZERO
                    : salesAgg.normalSaleAmountByFood.getOrDefault(foodId, BigDecimal.ZERO);
            line.put("normalSaleQty", normalSaleQty.stripTrailingZeros().toPlainString());
            line.put("normalSaleAmount", normalSaleAmount.stripTrailingZeros().toPlainString());

            if (listPriceRevenue.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal marginActual = listPriceRevenue.subtract(operationalActualCost)
                        .divide(listPriceRevenue, 8, RoundingMode.HALF_UP);
                line.put("grossMarginRateOnListPrice",
                        GbDepartmentGoodsStockReduceSupport.formatRatioAsPercentTwoDecimals(marginActual));
                BigDecimal marginTheory = listPriceRevenue.subtract(
                        GbDepFoodSalesMetricsSupport.operationalShareOfCost(theoryCost, totalQty, consumptionQty))
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
            BigDecimal actualTotal123 = actPp123.multiply(totalQty).setScale(2, RoundingMode.HALF_UP);
            line.put("actualCostTotalAmount123", actualTotal123.stripTrailingZeros().toPlainString());
            // 损耗报废成本（type 2+3 分摊）= total123 − type1 制作成本
            BigDecimal wasteLossCost = actualTotal123.subtract(actualCost).max(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);
            line.put("wasteLossCostAmount", wasteLossCost.stripTrailingZeros().toPlainString());
            BigDecimal blendedRatio123 = null;
            BigDecimal unitPrice = parseAmountSafe(f.getGbDfFoodPrice());
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

            GbDistributerFoodEntity disRow = disRowForName;
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
        if (subDepId != null) {
            out.put("subDepId", subDepId);
        }
        out.put("scopeDepIds", scopeDepIds);
        out.put("weekdayLegend", weekdayLegendZh());
        out.put("scopeOutboundSubtotals", dishReport.get("scopeOutboundSubtotals"));
        out.put("bossColumnHintsZh", dishReport.get("bossColumnHintsZh"));
        out.put("dishes", dishes);
        out.put("dishProfitStoreCoverage", computeDishProfitStoreCoverage(disId, sd, ed, scopeDepIds));
        out.put("businessInsightSummary", summarizeBusinessInsightFromDishInsightRows(dishes, out));
        out.put("businessInsightSummaryChinese", BUSINESS_INSIGHT_SUMMARY_HINTS_ZH);
        return out;
    }

    @Override
    public Map<String, Object> attachToFoodRows(List<GbDepFoodEntity> foods, Integer disId, Integer depFatherId,
            String startDate, String stopDate, Integer subDepId) {
        Map<String, Object> insight = buildInsight(disId, depFatherId, startDate, stopDate, subDepId);
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
        extras.put("businessInsightSummaryChinese", BUSINESS_INSIGHT_SUMMARY_HINTS_ZH);
        extras.put("scopeOutboundSubtotals", insight.get("scopeOutboundSubtotals"));
        extras.put("weekdayLegend", insight.get("weekdayLegend"));
        extras.put("scopeDepIds", insight.get("scopeDepIds"));
        extras.put("bossColumnHintsZh", insight.get("bossColumnHintsZh"));
        extras.put("insightStartDate", insight.get("startDate"));
        extras.put("insightStopDate", insight.get("stopDate"));
        return extras;
    }

    /**
     * 顶部汇总：对菜品 insight 行的标价收入、实际/理论成本求和，再算 blended / 理论 / 综合毛利率（与 attachToFoodRows 同源口径）。
     */
    private static Map<String, Object> summarizeBusinessInsightFromDishInsightRows(
            List<Map<String, Object>> dishRows, Map<String, Object> insight) {
        BigDecimal totalRev = BigDecimal.ZERO;
        BigDecimal totalActual = BigDecimal.ZERO;
        BigDecimal totalTheory = BigDecimal.ZERO;
        BigDecimal totalActual123 = BigDecimal.ZERO;
        // 已核销总额 / 未核销总额
        BigDecimal totalVerifiedAmount = BigDecimal.ZERO;
        // 员工餐份数 / 金额
        BigDecimal totalEmployeeMealQty = BigDecimal.ZERO;
        BigDecimal totalEmployeeMealAmount = BigDecimal.ZERO;
        // 打折餐份数 / 金额
        BigDecimal totalDiscountGiftQty = BigDecimal.ZERO;
        BigDecimal totalDiscountGiftAmount = BigDecimal.ZERO;
        // 正常销售份数 / 金额
        BigDecimal totalNormalSaleQty = BigDecimal.ZERO;
        BigDecimal totalNormalSaleAmount = BigDecimal.ZERO;
        int rowCount = 0;
        if (dishRows != null) {
            for (Map<String, Object> ins : dishRows) {
                if (ins == null) {
                    continue;
                }
                rowCount++;
                totalRev = totalRev.add(GbDepartmentGoodsStockReduceSupport.coerceDecimal(ins.get("listPriceRevenue")));
                totalActual = totalActual.add(GbDepartmentGoodsStockReduceSupport.coerceDecimal(ins.get("actualCostAmount")));
                totalTheory = totalTheory.add(GbDepartmentGoodsStockReduceSupport.coerceDecimal(ins.get("theoryCostAmount")));
                totalActual123 = totalActual123.add(
                        GbDepartmentGoodsStockReduceSupport.coerceDecimal(ins.get("actualCostTotalAmount123")));
                // 已核销：totalConsumptionQty > 0 的菜品 actualCostAmount 合计
                BigDecimal tcq = GbDepartmentGoodsStockReduceSupport.coerceDecimal(ins.get("totalConsumptionQty"));
                if (tcq.compareTo(BigDecimal.ZERO) > 0) {
                    totalVerifiedAmount = totalVerifiedAmount.add(
                            GbDepartmentGoodsStockReduceSupport.coerceDecimal(ins.get("actualCostAmount")));
                }
                totalEmployeeMealQty = totalEmployeeMealQty.add(
                        GbDepartmentGoodsStockReduceSupport.coerceDecimal(ins.get("employeeMealQty")));
                totalEmployeeMealAmount = totalEmployeeMealAmount.add(
                        GbDepartmentGoodsStockReduceSupport.coerceDecimal(ins.get("employeeMealAmount")));
                totalDiscountGiftQty = totalDiscountGiftQty.add(
                        GbDepartmentGoodsStockReduceSupport.coerceDecimal(ins.get("discountGiftQty")));
                totalDiscountGiftAmount = totalDiscountGiftAmount.add(
                        GbDepartmentGoodsStockReduceSupport.coerceDecimal(ins.get("discountGiftAmount")));
                totalNormalSaleQty = totalNormalSaleQty.add(
                        GbDepartmentGoodsStockReduceSupport.coerceDecimal(ins.get("normalSaleQty")));
                totalNormalSaleAmount = totalNormalSaleAmount.add(
                        GbDepartmentGoodsStockReduceSupport.coerceDecimal(ins.get("normalSaleAmount")));
            }
        }
        // 损耗报废成本（type 2+3）：出库即视为已消耗，计入已核销
        BigDecimal wasteLossAmount = BigDecimal.ZERO;
        if (insight != null) {
            Object sc = insight.get("scopeOutboundSubtotals");
            if (sc instanceof Map) {
                wasteLossAmount = GbDepartmentGoodsStockReduceSupport.coerceDecimal(
                        ((Map<?, ?>) sc).get("wasteLossAmountType23"));
            }
        }
        totalVerifiedAmount = totalVerifiedAmount.add(wasteLossAmount);
        // 非核销总额 = (总实际成本 + 损耗报废) - 已核销总额
        BigDecimal totalNonVerifiedAmount = totalActual.add(wasteLossAmount)
                .subtract(totalVerifiedAmount).max(BigDecimal.ZERO);
        // 已核销/未核销毛利率（与 blendedGrossMarginRate 统一算法：(收入-成本)÷收入）
        BigDecimal verifiedRatio = BigDecimal.ZERO;
        BigDecimal nonVerifiedRatio = BigDecimal.ZERO;
        if (totalRev.compareTo(BigDecimal.ZERO) > 0) {
            verifiedRatio = totalRev.subtract(totalVerifiedAmount).divide(totalRev, 8, RoundingMode.HALF_UP);
            nonVerifiedRatio = totalRev.subtract(totalNonVerifiedAmount).divide(totalRev, 8, RoundingMode.HALF_UP);
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
        // 已核销总额 / 未核销总额 / 占比
        m.put("totalVerifiedAmount", plainMoneySummary(totalVerifiedAmount));
        m.put("totalNonVerifiedAmount", plainMoneySummary(totalNonVerifiedAmount));
        m.put("totalVerifiedAmountPercent",
                GbDepartmentGoodsStockReduceSupport.formatRatioAsPercentTwoDecimals(verifiedRatio));
        m.put("totalNonVerifiedAmountPercent",
                GbDepartmentGoodsStockReduceSupport.formatRatioAsPercentTwoDecimals(nonVerifiedRatio));
        // 员工餐份数 / 金额
        m.put("totalEmployeeMealQty", totalEmployeeMealQty.stripTrailingZeros().toPlainString());
        m.put("totalEmployeeMealAmount", plainMoneySummary(totalEmployeeMealAmount));
        // 打折餐份数 / 金额
        m.put("totalDiscountGiftQty", totalDiscountGiftQty.stripTrailingZeros().toPlainString());
        m.put("totalDiscountGiftAmount", plainMoneySummary(totalDiscountGiftAmount));
        // 正常销售份数 / 金额
        m.put("totalNormalSaleQty", totalNormalSaleQty.stripTrailingZeros().toPlainString());
        m.put("totalNormalSaleAmount", plainMoneySummary(totalNormalSaleAmount));
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
            m.put("subtotalEmployeeMealType6", sc.get("subtotalEmployeeMealType6"));
        }
        return m;
    }

    /**
     * 顶部汇总：对当前列表每条 {@code gbDfBusinessInsight} 的标价收入、实际/理论成本求和（与 {@link #summarizeBusinessInsightFromDishInsightRows} 一致）。
     */
    private static Map<String, Object> summarizeBusinessInsightFromFoodRows(List<GbDepFoodEntity> foods,
            Map<String, Object> insight) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (foods != null) {
            for (GbDepFoodEntity f : foods) {
                if (f != null && f.getGbDfBusinessInsight() != null) {
                    rows.add(f.getGbDfBusinessInsight());
                }
            }
        }
        return summarizeBusinessInsightFromDishInsightRows(rows, insight);
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
        z.put("employeeMealCostAmount", "0");
        z.put("totalConsumptionQty", "0");
        z.put("employeeMealQty", "0");
        z.put("employeeMealAmount", "0");
        z.put("discountGiftQty", "0");
        z.put("discountGiftAmount", "0");
        z.put("normalSaleQty", "0");
        z.put("normalSaleAmount", "0");
        z.put("grossMarginRateOnListPrice", "0.00");
        z.put("grossMarginRateTheoryOnListPrice", "0.00");
        z.put("actualCostPerPortion123", "0.00");
        z.put("actualCostTotalAmount123", "0");
        z.put("wasteLossCostAmount", "0");
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
        final Map<Integer, BigDecimal> listPriceRevenueByFood = new HashMap<>();
        /** 核销判定用：type 1-5 总消费份数（与 {@link OutboundIngredientReconcileSupport#hasVerifiableDishConsumption} 同口径） */
        final Map<Integer, BigDecimal> totalConsumptionQtyByFood = new HashMap<>();
        /** 原有：type 1-3 消费份数（用于 {@link com.nongxinle.utils.GbDepFoodSalesMetricsSupport#operationalShareOfCost}） */
        final Map<Integer, BigDecimal> consumptionQtyByFood = new HashMap<>();
        /** 员工餐份数（type 5） */
        final Map<Integer, BigDecimal> employeeMealQtyByFood = new HashMap<>();
        /** 员工餐金额（type 5 subtotal） */
        final Map<Integer, BigDecimal> employeeMealAmountByFood = new HashMap<>();
        /** 打折餐份数（折扣+会员+赠送 = type 2+3+4） */
        final Map<Integer, BigDecimal> discountGiftQtyByFood = new HashMap<>();
        /** 打折餐金额（折扣+会员+赠送 = type 2+3+4 subtotal） */
        final Map<Integer, BigDecimal> discountGiftAmountByFood = new HashMap<>();
        /** 正常销售份数（type 1） */
        final Map<Integer, BigDecimal> normalSaleQtyByFood = new HashMap<>();
        /** 正常销售金额（type 1 subtotal） */
        final Map<Integer, BigDecimal> normalSaleAmountByFood = new HashMap<>();
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
            BigDecimal qty = GbDepFoodSalesMetricsSupport.rowQty(row);
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            // 核销判定用：type 1-5 总消费份数（与 OutboundIngredientReconcileSupport.hasVerifiableDishConsumption 同口径）
            if (GbDepFoodSalesMetricsSupport.countsAsIngredientConsumption(row)) {
                agg.totalConsumptionQtyByFood.merge(foodId,
                        GbDepFoodSalesMetricsSupport.totalConsumptionQty(row), BigDecimal::add);
            }
            // 员工餐 type 5、打折餐 type 2+3+4（用现有 isEmployeeMeal/isComplimentary 等方法）
            if (GbConstants.FoodSalesType.isEmployeeMeal(GbDepFoodSalesMetricsSupport.resolveType(row))) {
                agg.employeeMealQtyByFood.merge(foodId, qty, BigDecimal::add);
                agg.employeeMealAmountByFood.merge(foodId,
                        GbDepFoodSalesMetricsSupport.rowSubtotal(row), BigDecimal::add);
            }
            Integer type = GbDepFoodSalesMetricsSupport.resolveType(row);
            if (type >= 2 && type <= 4) { // 打折餐 折扣+会员+赠送 type 2+3+4
                agg.discountGiftQtyByFood.merge(foodId, qty, BigDecimal::add);
                agg.discountGiftAmountByFood.merge(foodId,
                        GbDepFoodSalesMetricsSupport.rowSubtotal(row), BigDecimal::add);
            }
            // 正常销售（type 1）
            if (type == 1) {
                agg.normalSaleQtyByFood.merge(foodId, qty, BigDecimal::add);
                agg.normalSaleAmountByFood.merge(foodId,
                        GbDepFoodSalesMetricsSupport.rowSubtotal(row), BigDecimal::add);
            }
            // 总营业额（全部 type 1-5，subtotal 合计）
            agg.listPriceRevenueByFood.merge(foodId,
                    GbDepFoodSalesMetricsSupport.rowSubtotal(row), BigDecimal::add);
            // 菜品销售数量：不限制 type，全部 type 1-5 都计入 soldPortionsTotal / weekdayQty
            BigDecimal amt = qty;
            Integer wd = row.getGbDfsRevenueWeekday();
            if (wd == null || wd < 0 || wd > 6) {
                agg.unassignedWeekdayQty.merge(foodId, amt, BigDecimal::add);
            } else {
                agg.byFoodWeekday.computeIfAbsent(foodId, k -> new HashMap<>()).merge(wd, amt, BigDecimal::add);
            }
            // 经营销售（type 1+2+3）的 consumptionQty（用于 operationalShareOfCost 分母）
            if (GbDepFoodSalesMetricsSupport.countsAsOperationalSales(row)) {
                if (GbDepFoodSalesMetricsSupport.countsAsIngredientConsumption(row)) {
                    agg.consumptionQtyByFood.merge(foodId,
                            GbDepFoodSalesMetricsSupport.totalConsumptionQty(row), BigDecimal::add);
                }
            }
        }
        return agg;
    }

    private Map<Integer, Integer> loadDepFatherById(int disId) {
        LambdaQueryWrapper<GbDepartmentEntity> w = new LambdaQueryWrapper<GbDepartmentEntity>()
                .eq(GbDepartmentEntity::getGbDepartmentDisId, disId)
                .select(GbDepartmentEntity::getGbDepartmentId, GbDepartmentEntity::getGbDepartmentFatherId);
        List<GbDepartmentEntity> rows = gbDepartmentService.list(w);
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        Map<Integer, Integer> m = new HashMap<>(Math.max(16, rows.size() * 2));
        for (GbDepartmentEntity e : rows) {
            if (e.getGbDepartmentId() != null) {
                m.put(e.getGbDepartmentId(), e.getGbDepartmentFatherId());
            }
        }
        return m;
    }

    /**
     * 与 {@link com.nongxinle.service.impl.GbDishCostAnalysisServiceImpl} 同源，
     * sentinel {@link AiInsightDishProfitScope} 表示不按单父门店过滤子公司部门。
     */
    private List<Integer> resolveScopeDepIds(Integer disId, String searchDepId, Integer depFatherId,
            Collection<Integer> scopeDepartmentIdsAllowFilter) {
        if (searchDepId != null && !"-1".equals(searchDepId)) {
            return applyScopeDepartmentAllowFilter(Collections.singletonList(Integer.valueOf(searchDepId)),
                    scopeDepartmentIdsAllowFilter);
        }
        if (!AiInsightDishProfitScope.isGroupWideMendianAggregateUnderDis(depFatherId) && depFatherId != null) {
            LinkedHashMap<Integer, Boolean> singleStoreUniq = new LinkedHashMap<>();
            List<GbDepartmentEntity> subs = gbDepartmentService.querySubDepartments(depFatherId);
            if (subs != null) {
                for (GbDepartmentEntity sub : subs) {
                    if (sub.getGbDepartmentId() != null) {
                        singleStoreUniq.put(sub.getGbDepartmentId(), Boolean.TRUE);
                    }
                }
            }
            if (scopeDepartmentIdsAllowFilter != null) {
                for (Integer allowed : scopeDepartmentIdsAllowFilter) {
                    if (allowed != null && allowed.equals(depFatherId)) {
                        singleStoreUniq.put(depFatherId, Boolean.TRUE);
                        break;
                    }
                }
            }
            return applyScopeDepartmentAllowFilter(new ArrayList<>(singleStoreUniq.keySet()),
                    scopeDepartmentIdsAllowFilter);
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
                if (!AiInsightDishProfitScope.isGroupWideMendianAggregateUnderDis(depFatherId)
                        && depFatherId != null
                        && !depFatherId.equals(sub.getGbDepartmentFatherId())) {
                    continue;
                }
                uniq.put(sub.getGbDepartmentId(), Boolean.TRUE);
            }
        }
        // 单店口径：门店根部门的 father 通常不是本店 id（多为 0/分销商），不会出现在「子部门 father==depFatherId」集合内；
        // 若 allow 已含门店根且销量/流水挂在根部门上，需显式纳入 scopeDepIds，否则集团广角有数、单店无行。
        if (!AiInsightDishProfitScope.isGroupWideMendianAggregateUnderDis(depFatherId)
                && depFatherId != null
                && scopeDepartmentIdsAllowFilter != null) {
            for (Integer allowed : scopeDepartmentIdsAllowFilter) {
                if (allowed != null && allowed.equals(depFatherId)) {
                    uniq.put(depFatherId, Boolean.TRUE);
                    break;
                }
            }
        }
        return applyScopeDepartmentAllowFilter(new ArrayList<>(uniq.keySet()), scopeDepartmentIdsAllowFilter);
    }

    private static List<Integer> applyScopeDepartmentAllowFilter(List<Integer> scopeDeptIds,
            Collection<Integer> allowFilter) {
        if (scopeDeptIds == null || scopeDeptIds.isEmpty()) {
            return scopeDeptIds == null ? Collections.emptyList() : scopeDeptIds;
        }
        if (allowFilter == null || allowFilter.isEmpty()) {
            return scopeDeptIds;
        }
        Set<Integer> allow = new HashSet<>();
        for (Integer id : allowFilter) {
            if (id != null) {
                allow.add(id);
            }
        }
        if (allow.isEmpty()) {
            return scopeDeptIds;
        }
        List<Integer> out = new ArrayList<>();
        for (Integer id : scopeDeptIds) {
            if (id != null && allow.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    /**
     * 集团菜品透视：菜谱合并仅包含「允许范围」内的门店根；允许列表可为门店根或含子部门的展开列表（向上归到
     * {@code gb_department_father_id=0} 的门店）。未传或无法解析时退回分销户下全部门店根。
     */
    private List<Integer> resolveStoreRootsForGroupWideMenu(Integer disId,
            Collection<Integer> scopeDepartmentIdsAllowFilter) {
        if (disId == null) {
            return Collections.emptyList();
        }
        if (scopeDepartmentIdsAllowFilter == null || scopeDepartmentIdsAllowFilter.isEmpty()) {
            List<Integer> all = gbDepartmentMapper.selectStoreDepartmentIdsUnderDistributer(disId);
            return all == null ? Collections.emptyList() : all;
        }
        Map<Integer, Integer> fatherById = loadDepFatherById(disId);
        LinkedHashSet<Integer> roots = new LinkedHashSet<>();
        for (Integer id : scopeDepartmentIdsAllowFilter) {
            if (id == null || id <= 0) {
                continue;
            }
            Integer root = walkToMendianRootDeptId(id, fatherById);
            if (root != null) {
                roots.add(root);
            }
        }
        if (roots.isEmpty()) {
            List<Integer> all = gbDepartmentMapper.selectStoreDepartmentIdsUnderDistributer(disId);
            return all == null ? Collections.emptyList() : all;
        }
        return new ArrayList<>(roots);
    }

    /**
     * 按门店根列表合并 {@code gb_dep_food}，按 {@code gb_df_food_id} 去重；同名优先保留菜名非空的一行。
     */
    private List<GbDepFoodEntity> mergeDepFoodAcrossMendianParents(int disId, List<Integer> storeRootIds) {
        List<Integer> roots = storeRootIds;
        if (roots == null || roots.isEmpty()) {
            roots = gbDepartmentMapper.selectStoreDepartmentIdsUnderDistributer(disId);
        }
        if (roots == null || roots.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashMap<Integer, GbDepFoodEntity> byFoodId = new LinkedHashMap<>();
        for (Integer storeRootId : roots) {
            if (storeRootId == null) {
                continue;
            }
            Map<String, Object> depMap = new HashMap<>(2);
            depMap.put("depFatherId", storeRootId);
            List<GbDepFoodEntity> part = gbDepFoodService.queryDepAllFood(depMap);
            if (part == null) {
                continue;
            }
            for (GbDepFoodEntity row : part) {
                Integer fid = row == null ? null : row.getGbDfFoodId();
                if (fid == null) {
                    continue;
                }
                GbDepFoodEntity existing = byFoodId.get(fid);
                if (existing == null) {
                    byFoodId.put(fid, row);
                } else if (isGbDepFoodNameBlank(existing) && !isGbDepFoodNameBlank(row)) {
                    byFoodId.put(fid, row);
                }
            }
        }
        return new ArrayList<>(byFoodId.values());
    }

    private static boolean isGbDepFoodNameBlank(GbDepFoodEntity e) {
        if (e == null) {
            return true;
        }
        String n = e.getGbDfFoodName();
        return n == null || n.trim().isEmpty();
    }

    private static Integer walkToMendianRootDeptId(Integer depId, Map<Integer, Integer> fatherById) {
        if (depId == null) {
            return null;
        }
        Integer cur = depId;
        for (int guard = 0; guard < 48 && cur != null; guard++) {
            Integer p = fatherById.get(cur);
            if (p == null || p == 0) {
                return cur;
            }
            cur = p;
        }
        return cur;
    }

    private Map<Integer, String> loadDepartmentNames(Collection<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        LinkedHashSet<Integer> uniq = new LinkedHashSet<>();
        for (Integer id : ids) {
            if (id != null) {
                uniq.add(id);
            }
        }
        if (uniq.isEmpty()) {
            return Map.of();
        }
        Map<Integer, String> out = new HashMap<>();
        List<GbDepartmentEntity> rows = gbDepartmentService.listByIds(uniq);
        if (rows == null) {
            return out;
        }
        for (GbDepartmentEntity e : rows) {
            if (e == null || e.getGbDepartmentId() == null) {
                continue;
            }
            out.put(e.getGbDepartmentId(), e.getGbDepartmentName() != null ? e.getGbDepartmentName().trim() : "");
        }
        return out;
    }

    /** 口径：{@code visibleStoreCount} / 菜品销售归属门店 vs 区间内无销量的可见门店（仅名称，不脱敏 dept id）。 */
    private Map<String, Object> computeDishProfitStoreCoverage(Integer disId, String sd, String ed,
            List<Integer> scopeSubDepIds) {
        LinkedHashMap<String, Object> cov = new LinkedHashMap<>();
        if (disId == null || sd == null || ed == null || scopeSubDepIds == null || scopeSubDepIds.isEmpty()) {
            cov.put("visibleStoreCount", 0);
            cov.put("dataAvailableStoreCount", 0);
            cov.put("dataMissingStoreCount", 0);
            cov.put("coveredStores", Collections.emptyList());
            cov.put("dataMissingStores", Collections.emptyList());
            cov.put("dishSalesRowCount", 0);
            cov.put("dishSalesDepartmentIds", Collections.emptyList());
            return cov;
        }
        Map<Integer, Integer> fatherById = loadDepFatherById(disId);
        Set<Integer> visibleRoots = new LinkedHashSet<>();
        for (Integer sub : scopeSubDepIds) {
            Integer r = walkToMendianRootDeptId(sub, fatherById);
            if (r != null) {
                visibleRoots.add(r);
            }
        }

        LambdaQueryWrapper<GbDepFoodSalesEntity> w = new LambdaQueryWrapper<GbDepFoodSalesEntity>()
                .eq(GbDepFoodSalesEntity::getGbDfsDistributerId, disId)
                .ge(GbDepFoodSalesEntity::getGbDfsFullDate, sd)
                .le(GbDepFoodSalesEntity::getGbDfsFullDate, ed)
                .in(GbDepFoodSalesEntity::getGbDfsDepId, scopeSubDepIds);
        List<GbDepFoodSalesEntity> salesRows = gbDepFoodSalesService.list(w);
        int dishSalesRowCount = salesRows == null ? 0 : salesRows.size();

        Set<Integer> deptIdsSeenInSales = new LinkedHashSet<>();
        if (salesRows != null) {
            for (GbDepFoodSalesEntity row : salesRows) {
                if (row != null && row.getGbDfsDepId() != null) {
                    deptIdsSeenInSales.add(row.getGbDfsDepId());
                }
            }
        }
        Set<Integer> rootsWithSales = new LinkedHashSet<>();
        for (Integer d : deptIdsSeenInSales) {
            Integer root = walkToMendianRootDeptId(d, fatherById);
            if (root != null) {
                rootsWithSales.add(root);
            }
        }

        Set<Integer> namesToLoad = new HashSet<>(visibleRoots);
        namesToLoad.addAll(rootsWithSales);
        Map<Integer, String> namesById = loadDepartmentNames(namesToLoad);

        List<Map<String, Object>> covered = new ArrayList<>();
        List<Integer> coveredSorted = new ArrayList<>(rootsWithSales);
        Collections.sort(coveredSorted);
        for (Integer id : coveredSorted) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("storeDepartmentId", id.longValue());
            row.put("storeName", namesById.getOrDefault(id, ""));
            covered.add(row);
        }
        List<Map<String, Object>> missing = new ArrayList<>();
        List<Integer> missSorted = new ArrayList<>();
        for (Integer id : visibleRoots) {
            if (!rootsWithSales.contains(id)) {
                missSorted.add(id);
            }
        }
        Collections.sort(missSorted);
        for (Integer id : missSorted) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("storeDepartmentId", id.longValue());
            row.put("storeName", namesById.getOrDefault(id, ""));
            row.put("reason", "区间内暂无菜品销售明细记入");
            missing.add(row);
        }

        List<Integer> salesDeptSorted = new ArrayList<>(deptIdsSeenInSales);
        Collections.sort(salesDeptSorted);

        cov.put("visibleStoreCount", visibleRoots.size());
        cov.put("dataAvailableStoreCount", rootsWithSales.size());
        cov.put("dataMissingStoreCount", Math.max(0, visibleRoots.size() - rootsWithSales.size()));
        cov.put("coveredStores", covered);
        cov.put("dataMissingStores", missing);
        cov.put("dishSalesRowCount", dishSalesRowCount);
        cov.put("dishSalesDepartmentIds", salesDeptSorted);
        return cov;
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

    private static Map<String, Object> buildReduceParamsForInsight(Integer disId, Integer depFatherId, Integer subDepId,
            String startDate, String stopDate) {
        String searchDepStr = subDepId == null ? null : String.valueOf(subDepId);
        Map<String, Object> map = GbDepartmentGoodsStockReduceSupport.buildReduceCostQueryMap(
                startDate, stopDate, disId, null, searchDepStr);
        if (depFatherId != null) {
            map.put("depFatherId", depFatherId);
        }
        return map;
    }

    @Override
    public void enrichFoodGoodsOutboundStats(List<GbDistributerFoodGoodsEntity> recipeLines, Integer disId,
            Integer depFatherId, Integer subDepId, String startDate, String stopDate) {
        if (recipeLines == null || recipeLines.isEmpty() || disId == null) {
            return;
        }
        Map<String, Object> reduceParams = buildReduceParamsForInsight(disId, depFatherId, subDepId, startDate, stopDate);
        List<Map<String, Object>> prod = gbDepartmentGoodsStockReduceService.queryProductionReduceAggByDisGoods(reduceParams);
        List<Map<String, Object>> all = gbDepartmentGoodsStockReduceService.queryProduceLossWasteReduceAggByDisGoods(reduceParams);
        List<Map<String, Object>> emp = gbDepartmentGoodsStockReduceService.queryEmployeeMealReduceAggByDisGoods(reduceParams);
        Map<Integer, BigDecimal> w1 = new HashMap<>();
        Map<Integer, BigDecimal> s1 = new HashMap<>();
        putAggByDisGoodsId(prod, w1, s1);
        Map<Integer, BigDecimal> w123 = new HashMap<>();
        Map<Integer, BigDecimal> s123 = new HashMap<>();
        putAggByDisGoodsId(all, w123, s123);
        Map<Integer, BigDecimal> w6 = new HashMap<>();
        Map<Integer, BigDecimal> s6 = new HashMap<>();
        putAggByDisGoodsId(emp, w6, s6);
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
            BigDecimal eW = w6.getOrDefault(gid, BigDecimal.ZERO);
            BigDecimal eS = s6.getOrDefault(gid, BigDecimal.ZERO);
            line.setGbDfgEmployeeMealReduceWeight(plainScale(eW, 6));
            line.setGbDfgEmployeeMealReduceCost(plainScale(eS, 2));
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
        line.setGbDfgEmployeeMealReduceWeight("0");
        line.setGbDfgEmployeeMealReduceCost("0");
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

    @Override
    public Map<String, Object> buildWeekdaySalesDistributionForFood(
            Integer disId,
            Integer depFatherId,
            Integer foodId,
            String startDate,
            String stopDate,
            Map<String, Object> dishCostContext) {
        if (disId == null || depFatherId == null || foodId == null) {
            throw new IllegalArgumentException("disId、depFatherId、foodId 不能为空");
        }
        if (startDate == null || startDate.trim().isEmpty() || stopDate == null || stopDate.trim().isEmpty()) {
            throw new IllegalArgumentException("startDate、stopDate 不能为空");
        }
        String sd = startDate.trim();
        String ed = stopDate.trim();
        List<Integer> scopeDepIds = resolveScopeDepIds(disId, null, depFatherId, null);
        BigDecimal costPerPortion = resolveActualCostPerPortionForWeekdayProfit(dishCostContext);
        BigDecimal listPrice = resolveListPriceForWeekdayRevenue(dishCostContext);

        WeekdayBucketAgg[] buckets = new WeekdayBucketAgg[7];
        for (int i = 0; i < 7; i++) {
            buckets[i] = new WeekdayBucketAgg();
        }
        BigDecimal unassignedQty = BigDecimal.ZERO;
        BigDecimal unassignedRevenue = BigDecimal.ZERO;
        int unassignedOrders = 0;

        if (!scopeDepIds.isEmpty()) {
            LambdaQueryWrapper<GbDepFoodSalesEntity> w = new LambdaQueryWrapper<>();
            w.eq(GbDepFoodSalesEntity::getGbDfsDistributerId, disId)
                    .eq(GbDepFoodSalesEntity::getGbDfsFoodId, foodId)
                    .ge(GbDepFoodSalesEntity::getGbDfsFullDate, sd)
                    .le(GbDepFoodSalesEntity::getGbDfsFullDate, ed);
            if (scopeDepIds.size() == 1) {
                w.eq(GbDepFoodSalesEntity::getGbDfsDepId, scopeDepIds.get(0));
            } else {
                w.in(GbDepFoodSalesEntity::getGbDfsDepId, scopeDepIds);
            }
            List<GbDepFoodSalesEntity> rows = gbDepFoodSalesService.list(w);
            if (rows != null) {
                for (GbDepFoodSalesEntity row : rows) {
                    BigDecimal qty = GbDepartmentGoodsStockReduceSupport.coerceDecimal(row.getGbDfsAmount());
                    if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                        continue;
                    }
                    BigDecimal revenue = resolveSalesRowRevenue(row, qty, listPrice);
                    int storageWd = resolveStorageWeekday(row);
                    if (storageWd < 0 || storageWd > 6) {
                        unassignedQty = unassignedQty.add(qty);
                        unassignedRevenue = unassignedRevenue.add(revenue);
                        unassignedOrders++;
                        continue;
                    }
                    WeekdayBucketAgg bucket = buckets[storageWd];
                    bucket.qty = bucket.qty.add(qty);
                    bucket.revenue = bucket.revenue.add(revenue);
                    bucket.orderCount++;
                }
            }
        }

        List<Map<String, Object>> distribution = new ArrayList<>();
        for (int displayCode = 1; displayCode <= 7; displayCode++) {
            int storageWd = displayWeekdayCodeToStorage(displayCode);
            WeekdayBucketAgg bucket = buckets[storageWd];
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("weekdayCode", displayCode);
            item.put("weekdayName", weekdayNameForDisplayCode(displayCode));
            item.put("salesCount", MenuCategoryBusinessOverviewSupport.moneyDisplayPublic(bucket.qty));
            item.put("soldPortionsTotal", item.get("salesCount"));
            item.put("salesAmount", MenuCategoryBusinessOverviewSupport.moneyDisplayPublic(bucket.revenue));
            item.put("listPriceRevenue", item.get("salesAmount"));
            BigDecimal profit = bucket.revenue.subtract(bucket.qty.multiply(costPerPortion));
            item.put("actualProfitAmount", MenuCategoryBusinessOverviewSupport.moneyDisplayPublic(profit));
            item.put("orderCount", bucket.orderCount);
            distribution.add(item);
        }

        assignWeekdayRanksAndPeakFlags(distribution);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("weekdaySalesDistribution", distribution);
        Map<String, Object> peak = findPeakWeekday(distribution);
        out.putAll(peak);
        if (unassignedQty.compareTo(BigDecimal.ZERO) > 0) {
            out.put("unassignedSalesCount", MenuCategoryBusinessOverviewSupport.moneyDisplayPublic(unassignedQty));
            out.put("unassignedSalesAmount", MenuCategoryBusinessOverviewSupport.moneyDisplayPublic(unassignedRevenue));
            out.put("unassignedOrderCount", unassignedOrders);
        }
        return out;
    }

    private static BigDecimal resolveActualCostPerPortionForWeekdayProfit(Map<String, Object> dishCostContext) {
        if (dishCostContext == null) {
            return BigDecimal.ZERO;
        }
        Object v = dishCostContext.get("actualCostPerPortion");
        if (v == null) {
            v = dishCostContext.get("actualCostPerPortion123");
        }
        return GbDepartmentGoodsStockReduceSupport.coerceDecimal(v);
    }

    private static BigDecimal resolveListPriceForWeekdayRevenue(Map<String, Object> dishCostContext) {
        if (dishCostContext == null) {
            return BigDecimal.ZERO;
        }
        Object lp = dishCostContext.get("listPrice");
        if (lp == null) {
            return BigDecimal.ZERO;
        }
        return GbDepartmentGoodsStockReduceSupport.coerceDecimal(String.valueOf(lp));
    }

    private static BigDecimal resolveSalesRowRevenue(GbDepFoodSalesEntity row, BigDecimal qty, BigDecimal listPrice) {
        BigDecimal subtotal = GbDepartmentGoodsStockReduceSupport.coerceDecimal(row.getGbDfsSubtotal());
        if (subtotal.compareTo(BigDecimal.ZERO) > 0) {
            return subtotal;
        }
        if (listPrice.compareTo(BigDecimal.ZERO) > 0) {
            return qty.multiply(listPrice).setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    private static int resolveStorageWeekday(GbDepFoodSalesEntity row) {
        Integer wd = row.getGbDfsRevenueWeekday();
        if (wd != null && wd >= 0 && wd <= 6) {
            return wd;
        }
        String fullDate = row.getGbDfsFullDate();
        if (fullDate != null && !fullDate.trim().isEmpty()) {
            Date d = GbDateTimeUtils.parseDay(fullDate.trim());
            if (d != null) {
                return GbDateTimeUtils.weekdayForAiDailyRevenue(d);
            }
        }
        return -1;
    }

    /** 展示码 1=周一 … 7=周日；存储码 0=周日，1=周一，…，6=周六。 */
    private static int displayWeekdayCodeToStorage(int displayCode) {
        if (displayCode == 7) {
            return 0;
        }
        return displayCode;
    }

    private static String weekdayNameForDisplayCode(int displayCode) {
        int storage = displayWeekdayCodeToStorage(displayCode);
        return weekdayLegendZh().get(String.valueOf(storage));
    }

    private static void assignWeekdayRanksAndPeakFlags(List<Map<String, Object>> distribution) {
        List<Map<String, Object>> byQty = new ArrayList<>(distribution);
        byQty.sort(
                (a, b) ->
                        GbDepartmentGoodsStockReduceSupport.coerceDecimal(b.get("salesCount"))
                                .compareTo(
                                        GbDepartmentGoodsStockReduceSupport.coerceDecimal(a.get("salesCount"))));
        int rank = 1;
        BigDecimal topQty = BigDecimal.ZERO;
        for (int i = 0; i < byQty.size(); i++) {
            Map<String, Object> row = byQty.get(i);
            BigDecimal qty = GbDepartmentGoodsStockReduceSupport.coerceDecimal(row.get("salesCount"));
            if (i == 0) {
                topQty = qty;
            }
            int assignedRank = qty.compareTo(BigDecimal.ZERO) > 0 ? rank++ : 0;
            row.put("rank", assignedRank);
            row.put("isPeakDay", assignedRank == 1 && topQty.compareTo(BigDecimal.ZERO) > 0);
        }
        for (Map<String, Object> row : distribution) {
            if (GbDepartmentGoodsStockReduceSupport.coerceDecimal(row.get("salesCount")).compareTo(BigDecimal.ZERO) <= 0) {
                row.put("rank", 0);
                row.put("isPeakDay", false);
            }
        }
    }

    private static Map<String, Object> findPeakWeekday(List<Map<String, Object>> distribution) {
        Map<String, Object> peak = new LinkedHashMap<>();
        Map<String, Object> best = null;
        BigDecimal bestQty = BigDecimal.ZERO;
        for (Map<String, Object> row : distribution) {
            BigDecimal qty = GbDepartmentGoodsStockReduceSupport.coerceDecimal(row.get("salesCount"));
            if (qty.compareTo(bestQty) > 0) {
                bestQty = qty;
                best = row;
            }
        }
        if (best == null || bestQty.compareTo(BigDecimal.ZERO) <= 0) {
            peak.put("peakWeekdayName", null);
            peak.put("peakWeekdaySalesCount", MenuCategoryBusinessOverviewSupport.moneyDisplayPublic(BigDecimal.ZERO));
            peak.put("peakWeekdaySalesAmount", MenuCategoryBusinessOverviewSupport.moneyDisplayPublic(BigDecimal.ZERO));
            return peak;
        }
        peak.put("peakWeekdayName", best.get("weekdayName"));
        peak.put("peakWeekdaySalesCount", best.get("salesCount"));
        peak.put("peakWeekdaySalesAmount", best.get("salesAmount"));
        peak.put("peakWeekdayCode", best.get("weekdayCode"));
        return peak;
    }

    private static final class WeekdayBucketAgg {
        BigDecimal qty = BigDecimal.ZERO;
        BigDecimal revenue = BigDecimal.ZERO;
        int orderCount;
    }
}
