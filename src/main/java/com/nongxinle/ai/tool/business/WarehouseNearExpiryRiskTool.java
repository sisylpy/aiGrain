package com.nongxinle.ai.tool.business;

import com.nongxinle.ai.inventory.InventoryPresentationTimeSupport;
import com.nongxinle.ai.inventory.WarehouseNearExpiryRiskSupport;
import com.nongxinle.ai.inventory.WarehouseNearExpiryRiskSupport.ExpiryResolution;
import com.nongxinle.ai.scope.AiScopeResolver;
import com.nongxinle.ai.tool.AiTool;
import com.nongxinle.ai.tool.ToolRequest;
import com.nongxinle.ai.tool.ToolResult;
import com.nongxinle.entity.GbDepartmentEntity;
import com.nongxinle.entity.GbDepartmentGoodsStockEntity;
import com.nongxinle.mapper.GbDepartmentMapper;
import com.nongxinle.service.GbDepartmentGoodsStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
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
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_NEAR_EXPIRY_WINDOW_DAYS;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_PARENT_STORE_COUNT;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_PURCHASE_FOCUS_DIS_GOODS_ID;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_QUERY_SCOPE_BANNER;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_RESOLVED_DEPARTMENT_IDS;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_START_DATE;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_STOP_DATE;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_STOCK_AS_OF_DATE;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_VISIBLE_STORES;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_VISIBLE_WAREHOUSES;

/**
 * 库存批次临期/过期风险（warehouse.near_expiry）；与销量基线、可支撑天数无关。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WarehouseNearExpiryRiskTool implements AiTool {

    public static final String PAYLOAD_KEY = "warehouseNearExpiryRisk";

    private static final String STOCK_WEIGHT_UNIT_CN = "斤";
    private static final int RISK_LIST_TOP_N = 50;

    private final GbDepartmentGoodsStockService gbDepartmentGoodsStockService;
    private final AiScopeResolver scopeResolver;
    private final GbDepartmentMapper gbDepartmentMapper;

    @Override
    public String name() {
        return AiBusinessToolIds.WAREHOUSE_NEAR_EXPIRY_RISK;
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

        String stockAsOf = resolveStockAsOf(args, start, stop);
        try {
            Map<String, Object> risk =
                    buildRiskPayload(
                            dept.intValue(),
                            disId.intValue(),
                            null,
                            args,
                            stockAsOf);
            Map<String, Object> data = Map.of(PAYLOAD_KEY, risk);
            boolean hasRows = risk.get("riskItems") instanceof List<?> list && !list.isEmpty();
            return ToolResult.builder()
                    .success(true)
                    .message(hasRows ? "ok" : "no_risk_rows")
                    .data(AiBusinessToolResponses.envelope(
                            name(), true, !hasRows, start, stop, dept, disId, data, null))
                    .build();
        } catch (Exception e) {
            log.warn("[WarehouseNearExpiryRiskTool] runId={} dep={}: {}", request.getRunId(), dept, e.toString());
            Map<String, Object> risk = degradedPayload(start, stop, args, stockAsOf);
            return ToolResult.builder()
                    .success(true)
                    .message("degraded_after_error")
                    .data(AiBusinessToolResponses.envelope(
                            name(), true, true, start, stop, dept, disId, Map.of(PAYLOAD_KEY, risk),
                            "临期风险列表查询降级"))
                    .build();
        }
    }

    private ToolResult executeGroup(
            ToolRequest request, Map<String, Object> args, Long disId, String start, String stop) {
        if (disId == null) {
            return ToolResult.builder()
                    .success(false)
                    .message("missing disId for group near-expiry aggregation")
                    .data(AiBusinessToolResponses.envelope(
                            name(), false, false, start, stop, null, disId, Map.of(), "参数不完整"))
                    .build();
        }
        String stockAsOf = resolveStockAsOf(args, start, stop);
        List<Integer> resolved = parsePositiveIntList(args.get(ARG_RESOLVED_DEPARTMENT_IDS));
        List<Integer> storeIds = scopeResolver.listDomainStoreAnchorsInResolved(resolved);
        if (storeIds.isEmpty()) {
            storeIds = scopeResolver.listStoreDepartmentIdsUnderDistributer(disId.intValue());
        }
        Map<Integer, String> namesById = loadDepartmentNames(storeIds);
        List<Map<String, Object>> merged = new ArrayList<>();
        int normalCount = 0;
        int unjudgableCount = 0;
        for (Integer storeId : storeIds) {
            String label = namesById.getOrDefault(storeId, "门店" + storeId);
            try {
                Map<String, Object> one =
                        buildRiskPayload(storeId, disId.intValue(), label, args, stockAsOf);
                Object items = one.get("riskItems");
                if (items instanceof List<?> list) {
                    for (Object o : list) {
                        if (o instanceof Map<?, ?> m) {
                            merged.add(new LinkedHashMap<>((Map<String, Object>) m));
                        }
                    }
                }
                normalCount += intStat(one.get("normalBatchCount"));
                unjudgableCount += intStat(one.get("unjudgableBatchCount"));
            } catch (Exception ex) {
                log.warn("[WarehouseNearExpiryRiskTool] group storeId={} failed: {}", storeId, ex.toString());
            }
        }
        sortRiskItems(merged);
        if (merged.size() > RISK_LIST_TOP_N) {
            merged = new ArrayList<>(merged.subList(0, RISK_LIST_TOP_N));
        }
        Map<String, Object> risk = new LinkedHashMap<>();
        risk.put("scopeType", "GROUP");
        risk.put("scopeName", "集团下属门店汇总");
        risk.put("stockAsOfDate", stockAsOf);
        risk.put("nearExpiryWindowDays", WarehouseNearExpiryRiskSupport.resolveNearExpiryWindowDays(
                args.get(ARG_NEAR_EXPIRY_WINDOW_DAYS)));
        risk.put("riskItems", merged);
        risk.put("normalBatchCount", normalCount);
        risk.put("unjudgableBatchCount", unjudgableCount);
        risk.put(
                "summary",
                merged.isEmpty()
                        ? "当前库存口径下，未识别到已过期、今日到期或临期的库存批次。"
                        : "共 " + merged.size() + " 个库存批次存在过期或临期风险。");
        risk.put("dataSources", List.of("gb_department_goods_stock.queryGoodsStockListForMendianPeriod"));
        applyResolvedScopeFromArgs(risk, args);
        InventoryPresentationTimeSupport.applySnapshotMetadataToPayload(risk, null, null, stockAsOf);
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
            int depFatherId,
            int disId,
            String storeLabel,
            Map<String, Object> args,
            String stockAsOf) {
        int windowDays = WarehouseNearExpiryRiskSupport.resolveNearExpiryWindowDays(
                args.get(ARG_NEAR_EXPIRY_WINDOW_DAYS));
        LocalDate anchor = WarehouseNearExpiryRiskSupport.parseAnchorDate(stockAsOf);
        Integer focusGoodsId = toInt(args.get(ARG_PURCHASE_FOCUS_DIS_GOODS_ID));

        Map<String, Object> listParams = new HashMap<>();
        listParams.put("depFatherId", depFatherId);
        listParams.put("disId", disId);
        listParams.put("restWeight", "0");
        if (focusGoodsId != null) {
            listParams.put("disGoodsId", focusGoodsId);
        }
        List<GbDepartmentGoodsStockEntity> rows =
                gbDepartmentGoodsStockService.queryGoodsStockListForMendianPeriod(listParams);
        if (rows == null) {
            rows = List.of();
        }

        List<Map<String, Object>> riskItems = new ArrayList<>();
        int normalCount = 0;
        int unjudgableCount = 0;
        for (GbDepartmentGoodsStockEntity batch : rows) {
            double rw = parseDoubleLoose(batch.getGbDgsRestWeight());
            if (rw <= 0) {
                continue;
            }
            ExpiryResolution expiry = WarehouseNearExpiryRiskSupport.resolveExpiry(batch);
            if (expiry == null) {
                unjudgableCount++;
                continue;
            }
            String tier =
                    WarehouseNearExpiryRiskSupport.classifyRiskTier(
                            expiry.expiryDate(), anchor, windowDays);
            if (WarehouseNearExpiryRiskSupport.RISK_TIER_NORMAL.equals(tier)) {
                normalCount++;
                continue;
            }
            if (!WarehouseNearExpiryRiskSupport.isActionableRiskTier(tier)) {
                unjudgableCount++;
                continue;
            }
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
            long daysUntil = WarehouseNearExpiryRiskSupport.daysUntilExpiry(expiry.expiryDate(), anchor);
            item.put("daysUntilExpiry", daysUntil);
            riskItems.add(item);
        }

        sortRiskItems(riskItems);
        if (riskItems.size() > RISK_LIST_TOP_N) {
            riskItems = new ArrayList<>(riskItems.subList(0, RISK_LIST_TOP_N));
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scopeType", storeLabel == null ? "STORE" : "STORE");
        payload.put("scopeName", storeLabel == null ? "单门店/库房范围" : storeLabel);
        payload.put("stockAsOfDate", stockAsOf);
        payload.put("nearExpiryWindowDays", windowDays);
        payload.put("riskItems", riskItems);
        payload.put("normalBatchCount", normalCount);
        payload.put("unjudgableBatchCount", unjudgableCount);
        payload.put(
                "summary",
                riskItems.isEmpty()
                        ? "当前库存口径下，未识别到已过期、今日到期或临期的库存批次。"
                        : "共 " + riskItems.size() + " 个库存批次存在过期或临期风险。");
        payload.put("dataSources", List.of("gb_department_goods_stock.queryGoodsStockListForMendianPeriod"));
        applyResolvedScopeFromArgs(payload, args);
        Object banner = args.get(ARG_QUERY_SCOPE_BANNER);
        if (banner != null && !banner.toString().isBlank()) {
            payload.put("queryScopeBanner", banner.toString().trim());
        }
        InventoryPresentationTimeSupport.applySnapshotMetadataToPayload(payload, null, null, stockAsOf);
        return payload;
    }

    private static void sortRiskItems(List<Map<String, Object>> items) {
        items.sort(
                Comparator.comparingInt((Map<String, Object> row) ->
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

    private static Map<String, Object> degradedPayload(
            String start, String stop, Map<String, Object> args, String stockAsOf) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scopeType", "STORE");
        payload.put("scopeName", "单门店/库房范围");
        payload.put("stockAsOfDate", stockAsOf);
        payload.put("riskItems", List.of());
        payload.put("summary", "临期风险列表读取遇到异常，请稍后在库存模块核对。");
        payload.put("queryErrorCode", "WAREHOUSE_NEAR_EXPIRY_QUERY_FAILED");
        applyResolvedScopeFromArgs(payload, args);
        InventoryPresentationTimeSupport.applySnapshotMetadataToPayload(payload, start, stop, stockAsOf);
        return payload;
    }

    private static String resolveStockAsOf(Map<String, Object> args, String start, String stop) {
        Object explicit = args.get(ARG_STOCK_AS_OF_DATE);
        if (explicit != null && !explicit.toString().isBlank()) {
            return explicit.toString().trim();
        }
        return InventoryPresentationTimeSupport.resolveStockAsOfFromToolArgs(args, start, stop);
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
            log.warn("[WarehouseNearExpiryRiskTool] loadDepartmentNames failed: {}", e.toString());
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

    private static int intStat(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return o == null ? 0 : Integer.parseInt(o.toString());
        } catch (NumberFormatException e) {
            return 0;
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

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
