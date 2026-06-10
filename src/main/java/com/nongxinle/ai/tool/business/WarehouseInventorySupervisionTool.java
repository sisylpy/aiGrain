package com.nongxinle.ai.tool.business;

import com.nongxinle.ai.graph.business.WarehouseInventorySupervisionDomainService;
import com.nongxinle.ai.inventory.InventoryPresentationTimeSupport;
import com.nongxinle.ai.inventory.WarehouseInventorySupervisionSupport;
import com.nongxinle.ai.inventory.WarehouseNearExpiryRiskSupport;
import com.nongxinle.ai.scope.AiScopeResolver;
import com.nongxinle.ai.tool.AiTool;
import com.nongxinle.ai.tool.ToolRequest;
import com.nongxinle.ai.tool.ToolResult;
import com.nongxinle.entity.GbDepartmentEntity;
import com.nongxinle.mapper.GbDepartmentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_DEPARTMENT_FATHER_ID;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_DIS_ID;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_GROUP_WAREHOUSE_STOCK_AGGREGATION;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_NEAR_EXPIRY_WINDOW_DAYS;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_RESOLVED_DEPARTMENT_IDS;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_SALES_BASELINE_START_DATE;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_SALES_BASELINE_STOP_DATE;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_START_DATE;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_STOP_DATE;

/**
 * 库存监督/诊断（{@code warehouse.inventory_supervision.v1}）：采购优先级分桶 + 临期 + 积压 + 配方关联。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WarehouseInventorySupervisionTool implements AiTool {

    private final WarehouseInventorySupervisionDomainService domainService;
    private final AiScopeResolver scopeResolver;
    private final GbDepartmentMapper gbDepartmentMapper;

    @Override
    public String name() {
        return AiBusinessToolIds.WAREHOUSE_INVENTORY_SUPERVISION;
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        Map<String, Object> args = request.getArgs() == null ? Map.of() : request.getArgs();
        Long dept = toLong(args.get(ARG_DEPARTMENT_FATHER_ID));
        Long disId = toLong(args.get(ARG_DIS_ID));
        String start = str(args.get(ARG_START_DATE));
        String stop = str(args.get(ARG_STOP_DATE));
        String baselineStart = str(args.get(ARG_SALES_BASELINE_START_DATE));
        String baselineStop = str(args.get(ARG_SALES_BASELINE_STOP_DATE));
        if (baselineStart.isEmpty()) {
            baselineStart = start;
        }
        if (baselineStop.isEmpty()) {
            baselineStop = stop;
        }
        boolean groupAgg = Boolean.TRUE.equals(args.get(ARG_GROUP_WAREHOUSE_STOCK_AGGREGATION));

        if (groupAgg) {
            return executeGroup(request, args, disId, baselineStart, baselineStop);
        }

        if (dept == null || disId == null) {
            return ToolResult.builder()
                    .success(false)
                    .message("missing departmentFatherId/disId")
                    .data(AiBusinessToolResponses.envelope(
                            name(), false, false, baselineStart, baselineStop, dept, disId, Map.of(), "参数不完整"))
                    .build();
        }

        String stockAsOf = InventoryPresentationTimeSupport.resolveStockAsOfFromToolArgs(args, start, stop);
        try {
            Map<String, Object> payload =
                    domainService.buildPayload(
                            dept.intValue(),
                            disId.intValue(),
                            baselineStart,
                            baselineStop,
                            null,
                            args,
                            stockAsOf);
            Map<String, Object> data = Map.of(WarehouseInventorySupervisionSupport.PAYLOAD_KEY, payload);
            boolean empty = isEmptyPayload(payload);
            return ToolResult.builder()
                    .success(true)
                    .message(empty ? "no_supervision_rows" : "ok")
                    .data(AiBusinessToolResponses.envelope(
                            name(), true, empty, baselineStart, baselineStop, dept, disId, data, null))
                    .build();
        } catch (Exception e) {
            log.warn(
                    "[WarehouseInventorySupervisionTool] runId={} dep={}: {}",
                    request.getRunId(),
                    dept,
                    e.toString());
            Map<String, Object> payload = degradedPayload(baselineStart, baselineStop, args, stockAsOf);
            return ToolResult.builder()
                    .success(true)
                    .message("degraded_after_error")
                    .data(AiBusinessToolResponses.envelope(
                            name(),
                            true,
                            true,
                            baselineStart,
                            baselineStop,
                            dept,
                            disId,
                            Map.of(WarehouseInventorySupervisionSupport.PAYLOAD_KEY, payload),
                            "库存监督查询降级"))
                    .build();
        }
    }

    private ToolResult executeGroup(
            ToolRequest request, Map<String, Object> args, Long disId, String baselineStart, String baselineStop) {
        if (disId == null) {
            return ToolResult.builder()
                    .success(false)
                    .message("missing disId for group supervision aggregation")
                    .data(AiBusinessToolResponses.envelope(
                            name(), false, false, baselineStart, baselineStop, null, disId, Map.of(), "参数不完整"))
                    .build();
        }
        String start = str(args.get(ARG_START_DATE));
        String stop = str(args.get(ARG_STOP_DATE));
        String stockAsOf = InventoryPresentationTimeSupport.resolveStockAsOfFromToolArgs(args, start, stop);
        List<Integer> resolved = parsePositiveIntList(args.get(ARG_RESOLVED_DEPARTMENT_IDS));
        List<Integer> storeIds = scopeResolver.listDomainStoreAnchorsInResolved(resolved);
        if (storeIds.isEmpty()) {
            storeIds = scopeResolver.listStoreDepartmentIdsUnderDistributer(disId.intValue());
        }
        Map<Integer, String> namesById = loadDepartmentNames(storeIds);
        List<Map<String, Object>> mergedSections = new ArrayList<>();
        for (Integer storeId : storeIds) {
            String label = namesById.getOrDefault(storeId, "门店" + storeId);
            try {
                Map<String, Object> one =
                        domainService.buildPayload(
                                storeId,
                                disId.intValue(),
                                baselineStart,
                                baselineStop,
                                label,
                                args,
                                stockAsOf);
                mergeSections(mergedSections, one.get("sections"));
            } catch (Exception ex) {
                log.warn(
                        "[WarehouseInventorySupervisionTool] group storeId={} failed: {}",
                        storeId,
                        ex.toString());
            }
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scopeType", "GROUP");
        payload.put("scopeName", "集团下属门店汇总");
        payload.put("startDate", baselineStart);
        payload.put("stopDate", baselineStop);
        payload.put("stockAsOfDate", stockAsOf);
        payload.put("nearExpiryWindowDays", WarehouseNearExpiryRiskSupport.resolveNearExpiryWindowDays(
                args.get(ARG_NEAR_EXPIRY_WINDOW_DAYS)));
        payload.put("sections", mergedSections);
        payload.put("sectionCounts", sectionCounts(mergedSections));
        payload.put("summary", buildGroupSummary(mergedSections));
        payload.put(
                "dataSources",
                List.of(
                        "gb_department_goods_stock.queryGoodsStockListForMendianPeriod",
                        "gb_department_goods_stock_reduce.queryProductionReduceAggByDisGoods",
                        "gb_distributer_food_goods.queryFoodGoodsByDisGoodsId"));
        InventoryPresentationTimeSupport.applySnapshotMetadataToPayload(payload, baselineStart, baselineStop, stockAsOf);
        Map<String, Object> data = Map.of(WarehouseInventorySupervisionSupport.PAYLOAD_KEY, payload);
        boolean empty = mergedSections.isEmpty();
        return ToolResult.builder()
                .success(true)
                .message(empty ? "no_supervision_rows" : "ok")
                .data(AiBusinessToolResponses.envelope(
                        name(), true, empty, baselineStart, baselineStop, null, disId, data, null))
                .build();
    }

    @SuppressWarnings("unchecked")
    private static void mergeSections(List<Map<String, Object>> target, Object sectionsObj) {
        if (!(sectionsObj instanceof List<?> list)) {
            return;
        }
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) {
                target.add(new LinkedHashMap<>((Map<String, Object>) m));
            }
        }
    }

    private static Map<String, Object> sectionCounts(List<Map<String, Object>> sections) {
        LinkedHashMap<String, Object> counts = new LinkedHashMap<>();
        for (Map<String, Object> section : sections) {
            counts.put(str(section.get("sectionId")), section.get("rowCount"));
        }
        return counts;
    }

    private static String buildGroupSummary(List<Map<String, Object>> sections) {
        if (sections == null || sections.isEmpty()) {
            return "当前库存整体平稳，暂无急需采购、临期或明显积压提醒。";
        }
        return "集团范围库存监督：共 " + sections.size() + " 个分组有数据，详情见下方卡片。";
    }

    private static boolean isEmptyPayload(Map<String, Object> payload) {
        Object sections = payload.get("sections");
        return !(sections instanceof List<?> list) || list.isEmpty();
    }

    private static Map<String, Object> degradedPayload(
            String start, String stop, Map<String, Object> args, String stockAsOf) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scopeType", "STORE");
        payload.put("scopeName", "单门店/库房范围");
        payload.put("startDate", start);
        payload.put("stopDate", stop);
        payload.put("sections", List.of());
        payload.put("summary", "库存监督读取遇到异常，请稍后在库存模块核对。");
        payload.put("queryErrorCode", "WAREHOUSE_INVENTORY_SUPERVISION_QUERY_FAILED");
        InventoryPresentationTimeSupport.applySnapshotMetadataToPayload(payload, start, stop, stockAsOf);
        return payload;
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
            log.warn("[WarehouseInventorySupervisionTool] loadDepartmentNames failed: {}", e.toString());
            return Map.of();
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
}
