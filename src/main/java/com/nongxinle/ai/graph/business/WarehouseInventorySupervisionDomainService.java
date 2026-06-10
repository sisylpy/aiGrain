package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.inventory.InventoryPresentationTimeSupport;
import com.nongxinle.ai.inventory.WarehouseInventorySupervisionSupport;
import com.nongxinle.ai.inventory.WarehouseNearExpiryRiskSupport;
import com.nongxinle.ai.inventory.WarehouseNearExpiryRiskSupport.ExpiryResolution;
import com.nongxinle.entity.GbDepartmentGoodsStockEntity;
import com.nongxinle.entity.GbDistributerFoodEntity;
import com.nongxinle.entity.GbDistributerFoodGoodsEntity;
import com.nongxinle.service.GbDepartmentGoodsStockReduceService;
import com.nongxinle.service.GbDepartmentGoodsStockService;
import com.nongxinle.service.GbDistributerFoodGoodsService;
import com.nongxinle.service.GbDistributerFoodService;
import com.nongxinle.utils.GbDepartmentGoodsStockReduceSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_NEAR_EXPIRY_WINDOW_DAYS;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_QUERY_SCOPE_BANNER;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_SALES_BASELINE_START_DATE;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_SALES_BASELINE_STOP_DATE;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_VISIBLE_STORES;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_VISIBLE_WAREHOUSES;

/**
 * 库存监督/诊断：现量 + 耗用基线分桶 + 批次临期 + 配方关联（确定性；不读用户原文）。
 */
@Service
@RequiredArgsConstructor
public class WarehouseInventorySupervisionDomainService {

    private static final String STOCK_WEIGHT_UNIT_CN = "斤";
    private static final double SLOW_MOVING_REST_WEIGHT_MIN = 5.0;
    private static final int EXPIRY_ROW_CAP = 30;
    private static final int DISH_NAME_CAP = 3;

    private final GbDepartmentGoodsStockService gbDepartmentGoodsStockService;
    private final GbDepartmentGoodsStockReduceService gbDepartmentGoodsStockReduceService;
    private final GbDistributerFoodGoodsService gbDistributerFoodGoodsService;
    private final GbDistributerFoodService gbDistributerFoodService;

    public Map<String, Object> buildPayload(
            int depFatherId,
            int disId,
            String baselineStart,
            String baselineStop,
            String storeLabel,
            Map<String, Object> args,
            String stockAsOf) {
        String salesStart = resolveSalesBaselineStart(args, baselineStart);
        String salesStop = resolveSalesBaselineStop(args, baselineStop);
        int windowDays = windowDays(salesStart, salesStop);
        LocalDate anchor = WarehouseNearExpiryRiskSupport.parseAnchorDate(stockAsOf);
        int nearExpiryWindowDays =
                WarehouseNearExpiryRiskSupport.resolveNearExpiryWindowDays(args.get(ARG_NEAR_EXPIRY_WINDOW_DAYS));

        Map<String, Object> listParams = new HashMap<>();
        listParams.put("depFatherId", depFatherId);
        listParams.put("disId", disId);
        listParams.put("restWeight", "0");
        List<GbDepartmentGoodsStockEntity> rows =
                gbDepartmentGoodsStockService.queryGoodsStockListForMendianPeriod(listParams);
        if (rows == null) {
            rows = List.of();
        }

        Map<Integer, StockAgg> byGoods = aggregateStockByGoods(rows);
        Map<Integer, Double> outboundByGoods = loadOutboundByGoods(depFatherId, salesStart, salesStop);

        Map<String, List<Map<String, Object>>> bucketRows = initBucketMap();
        List<Map<String, Object>> expiryRows = new ArrayList<>();

        for (GbDepartmentGoodsStockEntity batch : rows) {
            double rw = parseDoubleLoose(batch.getGbDgsRestWeight());
            if (rw <= 0) {
                continue;
            }
            ExpiryResolution expiry = WarehouseNearExpiryRiskSupport.resolveExpiry(batch);
            if (expiry != null) {
                String tier =
                        WarehouseNearExpiryRiskSupport.classifyRiskTier(
                                expiry.expiryDate(), anchor, nearExpiryWindowDays);
                if (WarehouseNearExpiryRiskSupport.isActionableRiskTier(tier)) {
                    expiryRows.add(buildExpiryRow(batch, rw, expiry, tier, anchor, storeLabel));
                }
            }
        }
        sortExpiryRows(expiryRows);
        if (expiryRows.size() > EXPIRY_ROW_CAP) {
            expiryRows = new ArrayList<>(expiryRows.subList(0, EXPIRY_ROW_CAP));
        }

        for (StockAgg agg : byGoods.values()) {
            if (agg.goodsId <= 0) {
                continue;
            }
            double outbound = outboundByGoods.getOrDefault(agg.goodsId, 0.0);
            double daily = windowDays > 0 ? outbound / windowDays : 0.0;
            Double supportDays = daily > 0 ? agg.restWeight / daily : null;
            String bucket = classifyGoodsBucket(supportDays, agg.restWeight, daily);
            if (bucket == null) {
                continue;
            }
            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            if (storeLabel != null && !storeLabel.isBlank()) {
                item.put("storeName", storeLabel);
            }
            item.put("goodsId", agg.goodsId);
            item.put("goodsName", agg.goodsName);
            item.put("restWeight", round2(agg.restWeight));
            item.put("weightUnit", agg.weightUnit == null ? STOCK_WEIGHT_UNIT_CN : agg.weightUnit);
            item.put("periodOutboundWeight", round2(outbound));
            item.put("dailyOutboundWeight", round2(daily));
            if (supportDays != null) {
                item.put("supportDays", round2(supportDays));
            }
            bucketRows.get(bucket).add(item);
        }

        enrichDishLinksForBuckets(bucketRows, disId);

        for (List<Map<String, Object>> list : bucketRows.values()) {
            list.sort(Comparator.comparingDouble(WarehouseInventorySupervisionDomainService::supportDaysSortKey));
            if (list.size() > WarehouseInventorySupervisionSupport.SECTION_ROW_CAP) {
                list.subList(WarehouseInventorySupervisionSupport.SECTION_ROW_CAP, list.size()).clear();
            }
        }

        List<Map<String, Object>> sections = buildSections(bucketRows, expiryRows);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scopeType", storeLabel == null ? "STORE" : "STORE");
        payload.put("scopeName", storeLabel == null ? "单门店/库房范围" : storeLabel);
        payload.put("startDate", salesStart);
        payload.put("stopDate", salesStop);
        payload.put("windowDays", windowDays);
        payload.put("stockAsOfDate", stockAsOf);
        payload.put("nearExpiryWindowDays", nearExpiryWindowDays);
        payload.put("sections", sections);
        payload.put("sectionCounts", sectionCounts(sections));
        payload.put(
                "summary",
                buildSummaryNarrative(sections));
        payload.put(
                "dataSources",
                List.of(
                        "gb_department_goods_stock.queryGoodsStockListForMendianPeriod",
                        "gb_department_goods_stock_reduce.queryProductionReduceAggByDisGoods",
                        "gb_distributer_food_goods.queryFoodGoodsByDisGoodsId"));
        InventoryPresentationTimeSupport.applySnapshotMetadataToPayload(payload, salesStart, salesStop, stockAsOf);
        applyResolvedScopeFromArgs(payload, args);
        Object banner = args == null ? null : args.get(ARG_QUERY_SCOPE_BANNER);
        if (banner != null && !banner.toString().isBlank()) {
            payload.put("queryScopeBanner", banner.toString().trim());
        }
        return payload;
    }

    private static Map<String, List<Map<String, Object>>> initBucketMap() {
        Map<String, List<Map<String, Object>>> map = new LinkedHashMap<>();
        map.put(WarehouseInventorySupervisionSupport.SECTION_URGENT_TODAY, new ArrayList<>());
        map.put(WarehouseInventorySupervisionSupport.SECTION_URGENT_TOMORROW, new ArrayList<>());
        map.put(WarehouseInventorySupervisionSupport.SECTION_SHORTAGE_2_3, new ArrayList<>());
        map.put(WarehouseInventorySupervisionSupport.SECTION_SHORTAGE_WEEK, new ArrayList<>());
        map.put(WarehouseInventorySupervisionSupport.SECTION_HEALTHY, new ArrayList<>());
        map.put(WarehouseInventorySupervisionSupport.SECTION_OVERSTOCK, new ArrayList<>());
        return map;
    }

    private static String classifyGoodsBucket(Double supportDays, double restWeight, double daily) {
        if (supportDays != null) {
            return WarehouseInventorySupervisionSupport.classifySupportDaysBucket(supportDays);
        }
        if (daily <= 0 && restWeight >= SLOW_MOVING_REST_WEIGHT_MIN) {
            return WarehouseInventorySupervisionSupport.SECTION_OVERSTOCK;
        }
        return WarehouseInventorySupervisionSupport.SECTION_HEALTHY;
    }

    private Map<Integer, Double> loadOutboundByGoods(int depFatherId, String start, String stop) {
        Map<Integer, Double> outboundByGoods = new HashMap<>();
        if (!InventoryPresentationTimeSupport.hasPeriodFlowDates(start, stop)) {
            return outboundByGoods;
        }
        Map<String, Object> reduceParams = new HashMap<>();
        reduceParams.put("departmentFatherId", (long) depFatherId);
        reduceParams.put("matchDailyRevenueDepartmentId", (long) depFatherId);
        reduceParams.put("startDate", start);
        reduceParams.put("stopDate", stop);
        List<Map<String, Object>> prod =
                gbDepartmentGoodsStockReduceService.queryProductionReduceAggByDisGoods(reduceParams);
        if (prod == null) {
            return outboundByGoods;
        }
        for (Map<String, Object> row : prod) {
            Object idObj = row.get("gbDgsrGbDisGoodsId");
            if (idObj == null) {
                idObj = row.get("disGoodsId");
            }
            Integer gid = toInt(idObj);
            if (gid == null) {
                continue;
            }
            double w = parseDoubleLoose(row.get("weightSum"));
            outboundByGoods.merge(gid, w, Double::sum);
        }
        return outboundByGoods;
    }

    private static Map<Integer, StockAgg> aggregateStockByGoods(List<GbDepartmentGoodsStockEntity> rows) {
        Map<Integer, StockAgg> byGoods = new LinkedHashMap<>();
        for (GbDepartmentGoodsStockEntity e : rows) {
            double rw = parseDoubleLoose(e.getGbDgsRestWeight());
            if (rw <= 0) {
                continue;
            }
            Integer gid = e.getGbDgsGbDisGoodsId();
            int key = gid == null ? -1 : gid;
            String nm = e.getGoodsName();
            String label = nm == null || nm.isBlank() ? (key <= 0 ? "未知商品" : ("商品ID " + key)) : nm.trim();
            StockAgg g = byGoods.computeIfAbsent(key, k -> new StockAgg(label, key));
            g.addWeight(rw);
            String unit = e.getGbDgsRestWeightShowStandardName();
            if (unit != null && !unit.isBlank()) {
                g.weightUnit = unit.trim();
            }
        }
        return byGoods;
    }

    private void enrichDishLinksForBuckets(Map<String, List<Map<String, Object>>> bucketRows, int disId) {
        int enriched = 0;
        for (String bucketId :
                List.of(
                        WarehouseInventorySupervisionSupport.SECTION_URGENT_TODAY,
                        WarehouseInventorySupervisionSupport.SECTION_URGENT_TOMORROW,
                        WarehouseInventorySupervisionSupport.SECTION_SHORTAGE_2_3)) {
            List<Map<String, Object>> rows = bucketRows.get(bucketId);
            if (rows == null) {
                continue;
            }
            for (Map<String, Object> row : rows) {
                if (enriched >= WarehouseInventorySupervisionSupport.DISH_LINK_ENRICH_CAP) {
                    return;
                }
                Integer goodsId = toInt(row.get("goodsId"));
                if (goodsId == null || goodsId <= 0) {
                    continue;
                }
                List<String> dishNames = linkedDishNames(goodsId, disId);
                if (!dishNames.isEmpty()) {
                    row.put("linkedDishNames", dishNames);
                    enriched++;
                }
            }
        }
    }

    private List<String> linkedDishNames(int disGoodsId, int disId) {
        List<GbDistributerFoodGoodsEntity> lines =
                gbDistributerFoodGoodsService.queryFoodGoodsByDisGoodsId(disGoodsId, disId);
        if ((lines == null || lines.isEmpty()) && disId > 0) {
            lines = gbDistributerFoodGoodsService.queryFoodGoodsByDisGoodsId(disGoodsId, null);
        }
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (GbDistributerFoodGoodsEntity line : lines) {
            if (!GbDepartmentGoodsStockReduceSupport.isActiveFoodGoodsLine(line)
                    || line.getGbDfgFoodId() == null) {
                continue;
            }
            GbDistributerFoodEntity food = gbDistributerFoodService.queryObject(line.getGbDfgFoodId());
            if (food != null && food.getGbDfFoodName() != null && !food.getGbDfFoodName().isBlank()) {
                names.add(food.getGbDfFoodName().trim());
            }
            if (names.size() >= DISH_NAME_CAP) {
                break;
            }
        }
        return names;
    }

    private static List<Map<String, Object>> buildSections(
            Map<String, List<Map<String, Object>>> bucketRows, List<Map<String, Object>> expiryRows) {
        List<Map<String, Object>> sections = new ArrayList<>();
        for (String sectionId :
                List.of(
                        WarehouseInventorySupervisionSupport.SECTION_URGENT_TODAY,
                        WarehouseInventorySupervisionSupport.SECTION_URGENT_TOMORROW,
                        WarehouseInventorySupervisionSupport.SECTION_SHORTAGE_2_3,
                        WarehouseInventorySupervisionSupport.SECTION_SHORTAGE_WEEK,
                        WarehouseInventorySupervisionSupport.SECTION_OVERSTOCK,
                        WarehouseInventorySupervisionSupport.SECTION_HEALTHY)) {
            List<Map<String, Object>> rows = bucketRows.get(sectionId);
            if (rows == null || rows.isEmpty()) {
                continue;
            }
            sections.add(buildSection(sectionId, rows, null));
        }
        if (!expiryRows.isEmpty()) {
            sections.add(buildExpirySection(expiryRows));
        }
        sections.sort(
                Comparator.comparingInt(
                        s -> WarehouseInventorySupervisionSupport.bucketSortKey(str(s.get("sectionId")))));
        return sections;
    }

    private static Map<String, Object> buildSection(
            String sectionId, List<Map<String, Object>> rows, List<Map<String, Object>> subSections) {
        LinkedHashMap<String, Object> section = new LinkedHashMap<>();
        section.put("sectionId", sectionId);
        section.put("title", WarehouseInventorySupervisionSupport.sectionTitle(sectionId));
        section.put("rowCount", rows.size());
        section.put("rows", rows);
        if (subSections != null) {
            section.put("subSections", subSections);
        }
        return section;
    }

    private static Map<String, Object> buildExpirySection(List<Map<String, Object>> expiryRows) {
        Map<String, List<Map<String, Object>>> byTier = new LinkedHashMap<>();
        byTier.put(WarehouseInventorySupervisionSupport.EXPIRY_SUB_EXPIRED, new ArrayList<>());
        byTier.put(WarehouseInventorySupervisionSupport.EXPIRY_SUB_DUE_TODAY, new ArrayList<>());
        byTier.put(WarehouseInventorySupervisionSupport.EXPIRY_SUB_NEAR_EXPIRY, new ArrayList<>());
        for (Map<String, Object> row : expiryRows) {
            String tier = str(row.get("riskTier"));
            List<Map<String, Object>> target = byTier.get(tier);
            if (target != null) {
                target.add(row);
            }
        }
        List<Map<String, Object>> subSections = new ArrayList<>();
        for (String tier :
                List.of(
                        WarehouseInventorySupervisionSupport.EXPIRY_SUB_EXPIRED,
                        WarehouseInventorySupervisionSupport.EXPIRY_SUB_DUE_TODAY,
                        WarehouseInventorySupervisionSupport.EXPIRY_SUB_NEAR_EXPIRY)) {
            List<Map<String, Object>> tierRows = byTier.get(tier);
            if (tierRows == null || tierRows.isEmpty()) {
                continue;
            }
            LinkedHashMap<String, Object> sub = new LinkedHashMap<>();
            sub.put("subSectionId", tier);
            sub.put("title", WarehouseInventorySupervisionSupport.expirySubTitle(tier));
            sub.put("rowCount", tierRows.size());
            sub.put("rows", tierRows);
            subSections.add(sub);
        }
        return buildSection(
                WarehouseInventorySupervisionSupport.SECTION_EXPIRY,
                expiryRows,
                subSections);
    }

    private static LinkedHashMap<String, Object> buildExpiryRow(
            GbDepartmentGoodsStockEntity batch,
            double rw,
            ExpiryResolution expiry,
            String tier,
            LocalDate anchor,
            String storeLabel) {
        LinkedHashMap<String, Object> item = new LinkedHashMap<>();
        if (storeLabel != null && !storeLabel.isBlank()) {
            item.put("storeName", storeLabel);
        }
        item.put("batchId", batch.getGbDepartmentGoodsStockId());
        Integer gid = batch.getGbDgsGbDisGoodsId();
        item.put("goodsId", gid);
        String nm = batch.getGoodsName();
        item.put(
                "goodsName",
                nm == null || nm.isBlank()
                        ? (gid == null ? "未知商品" : ("商品ID " + gid))
                        : nm.trim());
        item.put("restWeight", round2(rw));
        String unit = batch.getGbDgsRestWeightShowStandardName();
        item.put("weightUnit", unit == null || unit.isBlank() ? STOCK_WEIGHT_UNIT_CN : unit.trim());
        item.put("stockInDate", blankToNull(batch.getGbDgsDate()));
        item.put("explicitExpiryTime", blankToNull(batch.getGbDgsWasteFullTime()));
        if (expiry.quantityDaysUsed() != null) {
            item.put("quantityDays", expiry.quantityDaysUsed());
        }
        item.put("expiryDate", expiry.expiryDate().toString());
        item.put("expirySource", expiry.expirySource());
        item.put("riskTier", tier);
        item.put(
                "daysUntilExpiry",
                WarehouseNearExpiryRiskSupport.daysUntilExpiry(expiry.expiryDate(), anchor));
        return item;
    }

    private static void sortExpiryRows(List<Map<String, Object>> items) {
        items.sort(
                Comparator.comparingInt(
                                (Map<String, Object> row) ->
                                        WarehouseNearExpiryRiskSupport.tierSortKey(str(row.get("riskTier"))))
                        .thenComparingLong(row -> {
                            Object v = row.get("daysUntilExpiry");
                            if (v instanceof Number n) {
                                return n.longValue();
                            }
                            try {
                                return Long.parseLong(String.valueOf(v));
                            } catch (Exception e) {
                                return Long.MAX_VALUE;
                            }
                        }));
    }

    private static Map<String, Object> sectionCounts(List<Map<String, Object>> sections) {
        LinkedHashMap<String, Object> counts = new LinkedHashMap<>();
        for (Map<String, Object> section : sections) {
            counts.put(str(section.get("sectionId")), section.get("rowCount"));
        }
        return counts;
    }

    private static String buildSummaryNarrative(List<Map<String, Object>> sections) {
        int urgentToday = countSection(sections, WarehouseInventorySupervisionSupport.SECTION_URGENT_TODAY);
        int urgentTomorrow = countSection(sections, WarehouseInventorySupervisionSupport.SECTION_URGENT_TOMORROW);
        int expiry = countSection(sections, WarehouseInventorySupervisionSupport.SECTION_EXPIRY);
        int overstock = countSection(sections, WarehouseInventorySupervisionSupport.SECTION_OVERSTOCK);
        if (urgentToday == 0 && urgentTomorrow == 0 && expiry == 0 && overstock == 0) {
            return "当前库存整体平稳，暂无急需采购、临期或明显积压提醒。";
        }
        StringBuilder sb = new StringBuilder("库存监督摘要：");
        if (urgentToday > 0) {
            sb.append("今天急需采购 ").append(urgentToday).append(" 项；");
        }
        if (urgentTomorrow > 0) {
            sb.append("明天急需 ").append(urgentTomorrow).append(" 项；");
        }
        if (expiry > 0) {
            sb.append("临期/过期 ").append(expiry).append(" 个批次；");
        }
        if (overstock > 0) {
            sb.append("积压/慢动销 ").append(overstock).append(" 项。");
        }
        return sb.toString();
    }

    private static int countSection(List<Map<String, Object>> sections, String sectionId) {
        for (Map<String, Object> section : sections) {
            if (sectionId.equals(str(section.get("sectionId")))) {
                Object c = section.get("rowCount");
                if (c instanceof Number n) {
                    return n.intValue();
                }
                Object rows = section.get("rows");
                return rows instanceof List<?> list ? list.size() : 0;
            }
        }
        return 0;
    }

    private static String resolveSalesBaselineStart(Map<String, Object> args, String fallback) {
        if (args != null) {
            Object v = args.get(ARG_SALES_BASELINE_START_DATE);
            if (v != null && !v.toString().isBlank()) {
                return v.toString().trim();
            }
        }
        return fallback;
    }

    private static String resolveSalesBaselineStop(Map<String, Object> args, String fallback) {
        if (args != null) {
            Object v = args.get(ARG_SALES_BASELINE_STOP_DATE);
            if (v != null && !v.toString().isBlank()) {
                return v.toString().trim();
            }
        }
        return fallback;
    }

    @SuppressWarnings("unchecked")
    private static void applyResolvedScopeFromArgs(Map<String, Object> wo, Map<String, Object> args) {
        if (args == null) {
            return;
        }
        Object vs = args.get(ARG_VISIBLE_STORES);
        if (vs instanceof List<?> list && !list.isEmpty()) {
            wo.put("visibleStores", list);
        }
        Object vw = args.get(ARG_VISIBLE_WAREHOUSES);
        if (vw instanceof List<?> list && !list.isEmpty()) {
            wo.put("visibleWarehouses", list);
        }
    }

    private static double supportDaysSortKey(Map<String, Object> row) {
        Object v = row.get("supportDays");
        if (v == null) {
            return Double.MAX_VALUE;
        }
        try {
            return Double.parseDouble(v.toString());
        } catch (NumberFormatException e) {
            return Double.MAX_VALUE;
        }
    }

    private static int windowDays(String start, String stop) {
        try {
            LocalDate s = LocalDate.parse(start.trim());
            LocalDate e = LocalDate.parse(stop.trim());
            long days = ChronoUnit.DAYS.between(s, e) + 1;
            return (int) Math.max(1, days);
        } catch (Exception ex) {
            return 1;
        }
    }

    private static double parseDoubleLoose(Object o) {
        if (o == null) {
            return 0.0;
        }
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        String s = o.toString().trim();
        if (s.isEmpty()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static String round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static Integer toInt(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.intValue();
        }
        String s = o.toString().trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString().trim();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static final class StockAgg {
        final String goodsName;
        final int goodsId;
        double restWeight;
        String weightUnit;

        StockAgg(String goodsName, int goodsId) {
            this.goodsName = goodsName;
            this.goodsId = goodsId;
        }

        void addWeight(double w) {
            restWeight += w;
        }
    }
}
