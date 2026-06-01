package com.nongxinle.ai.tool.business;

import com.nongxinle.ai.scope.AiScopeResolver;
import com.nongxinle.ai.tool.AiTool;
import com.nongxinle.ai.tool.ToolRequest;
import com.nongxinle.ai.tool.ToolResult;
import com.nongxinle.ai.inventory.InventoryPresentationTimeSupport;
import com.nongxinle.entity.GbDepartmentEntity;
import com.nongxinle.entity.GbDepartmentGoodsStockEntity;
import com.nongxinle.mapper.GbDepartmentMapper;
import com.nongxinle.service.GbDepartmentGoodsStockReduceService;
import com.nongxinle.service.GbDepartmentGoodsStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_AI_ROLE_CODE;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_DEPARTMENT_FATHER_ID;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_DIS_ID;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_GROUP_WAREHOUSE_STOCK_AGGREGATION;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_PARENT_STORE_COUNT;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_QUERY_SCOPE_BANNER;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_RESOLVED_DEPARTMENT_IDS;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_START_DATE;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_STOP_DATE;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_VISIBLE_STORES;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_VISIBLE_WAREHOUSES;

/**
 * 库存风险列表（偏少/快缺货/需关注）：结合现量库存与区间内生产耗用推算可支撑天数。
 * <p>与 {@link WarehouseStockOverviewTool} 的 {@code lowStockItems}（账面重量启发式）及
 * {@code goods_amount_ranking_low}（金额排行）严格区分。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WarehouseInventoryRiskListTool implements AiTool {

    public static final String PAYLOAD_KEY = "warehouseInventoryRisk";

    private static final String STOCK_WEIGHT_UNIT_CN = "斤";
    private static final double RISK_COVER_DAYS_MAX = 7.0;
    private static final double RISK_REST_WEIGHT_MAX = 1.0;
    private static final int RISK_LIST_TOP_N = 30;

    private final GbDepartmentGoodsStockService gbDepartmentGoodsStockService;
    private final GbDepartmentGoodsStockReduceService gbDepartmentGoodsStockReduceService;
    private final AiScopeResolver scopeResolver;
    private final GbDepartmentMapper gbDepartmentMapper;

    @Override
    public String name() {
        return AiBusinessToolIds.WAREHOUSE_INVENTORY_RISK_LIST;
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        Map<String, Object> args = request.getArgs() == null ? Map.of() : request.getArgs();
        Long dept = toLong(args.get(ARG_DEPARTMENT_FATHER_ID));
        Long disId = toLong(args.get(ARG_DIS_ID));
        String start = str(args.get(ARG_START_DATE));
        String stop = str(args.get(ARG_STOP_DATE));
        boolean groupAgg = Boolean.TRUE.equals(args.get(ARG_GROUP_WAREHOUSE_STOCK_AGGREGATION));

        if (groupAgg) {
            return executeGroup(request, args, disId, start, stop);
        }

        if (dept == null || disId == null) {
            return ToolResult.builder()
                    .success(false)
                    .message("missing departmentFatherId/disId")
                    .data(AiBusinessToolResponses.envelope(
                            name(), false, false, start, stop, dept, disId, Map.of(), "参数不完整"))
                    .build();
        }

        String stockAsOf = InventoryPresentationTimeSupport.resolveStockAsOfFromToolArgs(args, start, stop);

        try {
            Map<String, Object> risk =
                    buildRiskPayload(dept.intValue(), disId.intValue(), start, stop, null, args, stockAsOf);
            Map<String, Object> data = Map.of(PAYLOAD_KEY, risk);
            boolean hasRows = risk.get("riskItems") instanceof List<?> list && !list.isEmpty();
            return ToolResult.builder()
                    .success(true)
                    .message(hasRows ? "ok" : "no_risk_rows")
                    .data(AiBusinessToolResponses.envelope(
                            name(), true, !hasRows, start, stop, dept, disId, data, null))
                    .build();
        } catch (Exception e) {
            log.warn("[WarehouseInventoryRiskListTool] runId={} dep={}: {}", request.getRunId(), dept, e.toString());
            Map<String, Object> risk = degradedPayload(start, stop, dept, disId, args, stockAsOf);
            return ToolResult.builder()
                    .success(true)
                    .message("degraded_after_error")
                    .data(AiBusinessToolResponses.envelope(
                            name(), true, true, start, stop, dept, disId, Map.of(PAYLOAD_KEY, risk),
                            "库存风险列表查询降级"))
                    .build();
        }
    }

    private ToolResult executeGroup(ToolRequest request, Map<String, Object> args, Long disId, String start,
            String stop) {
        if (disId == null) {
            return ToolResult.builder()
                    .success(false)
                    .message("missing disId for group risk aggregation")
                    .data(AiBusinessToolResponses.envelope(
                            name(), false, false, start, stop, null, disId, Map.of(), "参数不完整"))
                    .build();
        }
        String stockAsOf = InventoryPresentationTimeSupport.resolveStockAsOfFromToolArgs(args, start, stop);
        List<Integer> resolved = parsePositiveIntList(args.get(ARG_RESOLVED_DEPARTMENT_IDS));
        List<Integer> storeIds = scopeResolver.listDomainStoreAnchorsInResolved(resolved);
        if (storeIds.isEmpty()) {
            storeIds = scopeResolver.listStoreDepartmentIdsUnderDistributer(disId.intValue());
        }
        Map<Integer, String> namesById = loadDepartmentNames(storeIds);
        List<Map<String, Object>> merged = new ArrayList<>();
        for (Integer storeId : storeIds) {
            String label = namesById.getOrDefault(storeId, "门店" + storeId);
            try {
                Map<String, Object> one = buildRiskPayload(storeId, disId.intValue(), start, stop, label, args, stockAsOf);
                Object items = one.get("riskItems");
                if (items instanceof List<?> list) {
                    for (Object o : list) {
                        if (o instanceof Map<?, ?> m) {
                            merged.add(new LinkedHashMap<>((Map<String, Object>) m));
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn("[WarehouseInventoryRiskListTool] group storeId={} failed: {}", storeId, ex.toString());
            }
        }
        merged.sort(Comparator.comparingDouble(WarehouseInventoryRiskListTool::coverDaysSortKey));
        if (merged.size() > RISK_LIST_TOP_N) {
            merged = new ArrayList<>(merged.subList(0, RISK_LIST_TOP_N));
        }
        Map<String, Object> risk = new LinkedHashMap<>();
        risk.put("scopeType", "GROUP");
        risk.put("scopeName", "集团下属门店汇总");
        risk.put("startDate", start);
        risk.put("stopDate", stop);
        risk.put("windowDays", windowDays(start, stop));
        risk.put("riskItems", merged);
        risk.put("summary", merged.isEmpty()
                ? "当前库存口径下，未识别到需重点关注的库存偏少/快缺货原料（按现量与可支撑天数推算）。"
                : "共 " + merged.size() + " 项原料/商品建议关注库存风险（按可支撑天数升序）。");
        InventoryPresentationTimeSupport.applySnapshotMetadataToPayload(risk, start, stop, stockAsOf);
        risk.put("dataSources", List.of(
                "gb_department_goods_stock.queryGoodsStockListForMendianPeriod",
                "gb_department_goods_stock_reduce.queryProductionReduceAggByDisGoods"));
        risk.put("knownGaps", List.of(
                "near_expiry_batch_shelf_life_not_in_this_tool",
                "strict_out_of_stock_zero_stock_only_not_supported"));
        applyResolvedScopeFromArgs(risk, args);
        Map<String, Object> data = Map.of(PAYLOAD_KEY, risk);
        return ToolResult.builder()
                .success(true)
                .message(merged.isEmpty() ? "no_risk_rows" : "ok")
                .data(AiBusinessToolResponses.envelope(name(), true, merged.isEmpty(), start, stop, null, disId, data,
                        null))
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildRiskPayload(
            int depFatherId, int disId, String start, String stop, String storeLabel, Map<String, Object> args,
            String stockAsOf) {
        boolean periodFlow = InventoryPresentationTimeSupport.hasPeriodFlowDates(start, stop);
        int windowDays = periodFlow ? windowDays(start, stop) : 0;

        Map<String, Object> listParams = new HashMap<>();
        listParams.put("depFatherId", depFatherId);
        listParams.put("disId", disId);
        listParams.put("restWeight", "0");
        List<GbDepartmentGoodsStockEntity> rows =
                gbDepartmentGoodsStockService.queryGoodsStockListForMendianPeriod(listParams);
        if (rows == null) {
            rows = List.of();
        }

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

        Map<Integer, Double> outboundByGoods = new HashMap<>();
        if (periodFlow) {
            Map<String, Object> reduceParams = new HashMap<>();
            reduceParams.put("departmentFatherId", (long) depFatherId);
            reduceParams.put("matchDailyRevenueDepartmentId", (long) depFatherId);
            reduceParams.put("startDate", start);
            reduceParams.put("stopDate", stop);
            List<Map<String, Object>> prod =
                    gbDepartmentGoodsStockReduceService.queryProductionReduceAggByDisGoods(reduceParams);
            if (prod != null) {
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
            }
        }

        List<Map<String, Object>> riskItems = new ArrayList<>();
        for (StockAgg agg : byGoods.values()) {
            if (agg.goodsId <= 0) {
                continue;
            }
            double outbound = outboundByGoods.getOrDefault(agg.goodsId, 0.0);
            double daily = windowDays > 0 ? outbound / windowDays : 0.0;
            Double coverDays = null;
            if (daily > 0) {
                coverDays = agg.restWeight / daily;
            }
            boolean lowWeight = agg.restWeight <= RISK_REST_WEIGHT_MAX;
            boolean lowCover = coverDays != null && coverDays < RISK_COVER_DAYS_MAX;
            if (!lowWeight && !lowCover) {
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
            if (coverDays != null) {
                item.put("coverDays", round2(coverDays));
            }
            item.put("needsAttention", true);
            if (lowCover && lowWeight) {
                item.put("riskLevel", "HIGH");
                item.put("riskReason", "现量偏低且按近期耗用推算可支撑不足 " + (int) RISK_COVER_DAYS_MAX + " 天");
            } else if (lowCover) {
                item.put("riskLevel", "MEDIUM");
                item.put("riskReason", "按近期生产耗用推算可支撑不足 " + (int) RISK_COVER_DAYS_MAX + " 天");
            } else {
                item.put("riskLevel", "MEDIUM");
                item.put("riskReason", "账面剩余重量偏低（≤" + RISK_REST_WEIGHT_MAX + " " + STOCK_WEIGHT_UNIT_CN + "）");
            }
            if (daily <= 0 && periodFlow) {
                item.put("riskReason", item.get("riskReason") + "；区间内无生产耗用记录，可支撑天数无法推算");
            } else if (daily <= 0 && !periodFlow) {
                item.put("riskReason", item.get("riskReason") + "；未提供耗用统计区间，可支撑天数无法推算");
            }
            riskItems.add(item);
        }

        riskItems.sort(Comparator.comparingDouble(WarehouseInventoryRiskListTool::coverDaysSortKey));
        if (riskItems.size() > RISK_LIST_TOP_N) {
            riskItems = new ArrayList<>(riskItems.subList(0, RISK_LIST_TOP_N));
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scopeType", storeLabel == null ? "STORE" : "STORE");
        payload.put("scopeName", storeLabel == null ? "单门店/库房范围" : storeLabel);
        payload.put("startDate", start);
        payload.put("stopDate", stop);
        payload.put("windowDays", windowDays);
        payload.put("riskItems", riskItems);
        payload.put(
                "summary",
                riskItems.isEmpty()
                        ? "当前库存口径下，未识别到需重点关注的库存偏少/快缺货原料（按现量与可支撑天数推算）。"
                        : "共 " + riskItems.size() + " 项原料/商品建议关注库存风险（按可支撑天数升序）。");
        payload.put(
                "dataSources",
                List.of(
                        "gb_department_goods_stock.queryGoodsStockListForMendianPeriod",
                        "gb_department_goods_stock_reduce.queryProductionReduceAggByDisGoods"));
        payload.put(
                "knownGaps",
                List.of(
                        "near_expiry_batch_shelf_life_not_in_this_tool",
                        "strict_out_of_stock_zero_stock_only_not_supported",
                        "fresh_warn_hour_from_dis_goods_profile_not_applied_in_p1"));
        applyResolvedScopeFromArgs(payload, args);
        Object banner = args.get(ARG_QUERY_SCOPE_BANNER);
        if (banner != null && !banner.toString().isBlank()) {
            payload.put("queryScopeBanner", banner.toString().trim());
        }
        InventoryPresentationTimeSupport.applySnapshotMetadataToPayload(payload, start, stop, stockAsOf);
        return payload;
    }

    private static Map<String, Object> degradedPayload(
            String start, String stop, Long dept, Long disId, Map<String, Object> args, String stockAsOf) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scopeType", "STORE");
        payload.put("scopeName", "单门店/库房范围");
        payload.put("startDate", start);
        payload.put("stopDate", stop);
        payload.put("riskItems", List.of());
        payload.put("summary", "库存风险列表读取遇到异常，请稍后在库存模块核对。");
        payload.put("queryErrorCode", "WAREHOUSE_INVENTORY_RISK_QUERY_FAILED");
        applyResolvedScopeFromArgs(payload, args);
        InventoryPresentationTimeSupport.applySnapshotMetadataToPayload(payload, start, stop, stockAsOf);
        return payload;
    }

    private static double coverDaysSortKey(Map<String, Object> row) {
        Object v = row.get("coverDays");
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

    private Map<Integer, String> loadDepartmentNames(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        try {
            List<GbDepartmentEntity> rows = gbDepartmentMapper.selectBatchIds(ids);
            if (rows == null || rows.isEmpty()) {
                return Map.of();
            }
            Map<Integer, String> out = new LinkedHashMap<>();
            for (GbDepartmentEntity r : rows) {
                if (r.getGbDepartmentId() != null && r.getGbDepartmentName() != null) {
                    out.put(r.getGbDepartmentId(), r.getGbDepartmentName().trim());
                }
            }
            return out;
        } catch (Exception e) {
            log.warn("[WarehouseInventoryRiskListTool] loadDepartmentNames failed: {}", e.toString());
            return Map.of();
        }
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

    private static List<Integer> parsePositiveIntList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Integer> out = new ArrayList<>();
        for (Object o : list) {
            Integer v = toInt(o);
            if (v != null && v > 0) {
                out.add(v);
            }
        }
        return out;
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

    private static Long toLong(Object o) {
        Integer i = toInt(o);
        return i == null ? null : i.longValue();
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
