package com.nongxinle.ai.tool.business;

import com.nongxinle.ai.scope.AiScopeResolver;
import com.nongxinle.ai.tool.AiTool;
import com.nongxinle.ai.tool.ToolRequest;
import com.nongxinle.ai.tool.ToolResult;
import com.nongxinle.entity.GbDepartmentEntity;
import com.nongxinle.entity.GbDepartmentGoodsStockEntity;
import com.nongxinle.mapper.GbDepartmentMapper;
import com.nongxinle.service.GbDepartmentGoodsStockReduceService;
import com.nongxinle.service.GbDepartmentGoodsStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
 * 库房库存概览：聚合库存快照、区间内入库、核销分型汇总，以及简易库存预警列表（低库存 / 高积压 / 早入库仍有剩余）。
 * <p>集团管理端：{@code groupWarehouseStockAggregation=true} 时按分销户下多门店根（{@code father_id=0}）汇总。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WarehouseStockOverviewTool implements AiTool {

    /** 库存重量展示单位（与 {@code gb_dgs_rest_weight} 等业务字段常见口径一致，斤）。 */
    private static final String STOCK_WEIGHT_UNIT_CN = "斤";

    private static final double LOW_STOCK_WEIGHT_MAX = 1.0;
    private static final int OVERSTOCK_TOP_N = 8;
    private static final int LOW_STOCK_TOP_N = 10;
    private static final int INACTIVE_BATCH_MAX = 15;

    /**
     * 集团聚合仅为门店根维度；无独立仓库维汇总时不提供真实仓库级排行（与 semantic wire
     * {@code warehouse_stock_amount_ranking} / {@code warehouse_stock_item_count_ranking} 区分）。
     */
    private static final String WAREHOUSE_STOCK_RANKING_DEGRADED_NOTE =
            "当前为按门店根部门维度的库存汇总；系统未做独立仓库维聚合，不提供真实的仓库级库存排行，"
                    + "请勿将下方门店排行等同于仓库排行。";

    private final GbDepartmentGoodsStockService gbDepartmentGoodsStockService;
    private final GbDepartmentGoodsStockReduceService gbDepartmentGoodsStockReduceService;
    private final AiScopeResolver scopeResolver;
    private final GbDepartmentMapper gbDepartmentMapper;

    @Override
    public String name() {
        return AiBusinessToolIds.WAREHOUSE_STOCK_OVERVIEW;
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
            return executeGroupAggregation(request, args, disId, start, stop);
        }

        if (dept == null || disId == null || start.isEmpty() || stop.isEmpty()) {
            Map<String, Object> data = new LinkedHashMap<>();
            return ToolResult.builder()
                    .success(false)
                    .message("missing departmentFatherId/disId/date range")
                    .data(AiBusinessToolResponses.envelope(name(), false, false, start, stop, dept, disId, data,
                            "参数不完整"))
                    .build();
        }

        try {
            Map<String, Object> wo = buildWarehouseOverviewForFather(dept.intValue(), disId.intValue(), start, stop,
                    null);
            boolean reduceMock = Boolean.TRUE.equals(wo.remove("_reduceMock"));
            wo.remove("_byGoods");
            wo.put("scopeType", "STORE");
            wo.put("scopeName", "单门店/库房范围");
            applyResolvedScopeFromArgs(wo, args);
            wo.put("priorityStocktakeItems",
                    wo.get("inactiveStockItems") != null ? wo.get("inactiveStockItems") : List.of());

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("warehouseOverview", wo);

            boolean hasAny = warehouseOverviewHasSignal(wo);

            return ToolResult.builder()
                    .success(true)
                    .message(hasAny && reduceMock ? "partial_empty" : "ok")
                    .data(AiBusinessToolResponses.envelope(name(), true, reduceMock && !hasAny, start, stop, dept,
                            disId, data, null))
                    .build();
        } catch (Exception e) {
            log.warn("[WarehouseStockOverviewTool] runId={} dep={}: {}", request.getRunId(), dept, e.toString());
            Map<String, Object> wo = new LinkedHashMap<>();
            wo.put("scopeType", "STORE");
            wo.put("scopeName", "单门店/库房范围");
            wo.put("summary",
                    "库存汇总读取遇到异常，请稍后在库存模块核对。请确认入库、出库、盘点数据是否已录入；若仍无法查看请联系技术支持。");
            wo.put("stockItemCount", 0);
            wo.put("stockBatchRowCount", 0);
            wo.put("totalStockAmount", 0.0);
            wo.put("totalStockWeight", 0.0);
            wo.put("inboundAmount", 0.0);
            wo.put("inboundWeight", 0.0);
            wo.put("outboundAmount", 0.0);
            wo.put("stockReduceAmount", 0.0);
            wo.put("lossAmount", 0.0);
            wo.put("wasteAmount", 0.0);
            wo.put("returnAmount", 0.0);
            wo.put("produceAmount", 0.0);
            wo.put("lowStockItems", List.of());
            wo.put("overStockItems", List.of());
            wo.put("inactiveStockItems", List.of());
            wo.put("recommendations", List.of("请确认入库、出库与盘点数据是否已录入。", "若问题持续，请在库存模块核对或联系技术支持。"));
            wo.put("queryErrorCode", "WAREHOUSE_STOCK_QUERY_FAILED");
            applyResolvedScopeFromArgs(wo, args);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("warehouseOverview", wo);
            return ToolResult.builder()
                    .success(true)
                    .message("degraded_after_error")
                    .data(AiBusinessToolResponses.envelope(name(), true, true, start, stop, dept, disId, data,
                            "单店库存查询降级"))
                    .build();
        }
    }

    private ToolResult executeGroupAggregation(ToolRequest request, Map<String, Object> args, Long disId, String start,
            String stop) {
        if (disId == null || start.isEmpty() || stop.isEmpty()) {
            Map<String, Object> data = new LinkedHashMap<>();
            return ToolResult.builder()
                    .success(false)
                    .message("missing disId/date range for group stock aggregation")
                    .data(AiBusinessToolResponses.envelope(name(), false, false, start, stop, null, disId, data,
                            "参数不完整"))
                    .build();
        }
        List<Integer> resolved = parsePositiveIntList(args.get(ARG_RESOLVED_DEPARTMENT_IDS));
        List<Integer> storeIds = scopeResolver.listDomainStoreAnchorsInResolved(resolved);
        if (storeIds.isEmpty()) {
            storeIds = scopeResolver.listStoreDepartmentIdsUnderDistributer(disId.intValue());
        }
        Object roleObj = args.get(ARG_AI_ROLE_CODE);
        String roleCode = roleObj == null ? "" : roleObj.toString().trim();
        int parentHint = -1;
        Object pc = args.get(ARG_PARENT_STORE_COUNT);
        if (pc instanceof Number n) {
            parentHint = n.intValue();
        }
        log.info(
                "[WAREHOUSE-STOCK-OVERVIEW][GROUP] runId={} userId={} roleCode={} disId={} requestResolvedNodes={} "
                        + "storeAnchorsToQuery={} scopeParentStoreCountHint={}",
                request.getRunId(), request.getUserId(), roleCode, disId,
                resolved.size(), storeIds.size(), parentHint);

        Map<Integer, String> namesById = loadDepartmentNames(storeIds);
        if (storeIds.isEmpty()) {
            Map<String, Object> wo = emptyGroupOverview(disId.intValue(), resolved.size());
            applyResolvedScopeFromArgs(wo, args);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("warehouseOverview", wo);
            return ToolResult.builder()
                    .success(true)
                    .message("ok")
                    .data(AiBusinessToolResponses.envelope(name(), true, false, start, stop, null, disId, data, null))
                    .build();
        }

        try {
            int totalBatchRows = 0;
            double restAmtSum = 0;
            double restWtSum = 0;
            double inboundAmtSum = 0;
            double inboundWtSum = 0;
            BigDecimal produceSum = BigDecimal.ZERO;
            BigDecimal wasteSum = BigDecimal.ZERO;
            BigDecimal lossSum = BigDecimal.ZERO;
            BigDecimal returnSum = BigDecimal.ZERO;
            boolean anyReduceMock = false;
            Map<Integer, GoodAgg> mergedGoods = new LinkedHashMap<>();
            List<Map<String, Object>> mergedInactive = new ArrayList<>();

            List<Map<String, Object>> coveredStores = new ArrayList<>();
            List<Map<String, Object>> dataMissingStores = new ArrayList<>();
            List<LinkedHashMap<String, Object>> storeRankingBaseRows = new ArrayList<>();

            for (Integer sid : storeIds) {
                if (sid == null || sid <= 0) {
                    continue;
                }
                String storeLabel = namesById.getOrDefault(sid, "门店 " + sid);
                Map<String, Object> one;
                try {
                    one = buildWarehouseOverviewForFather(sid, disId.intValue(), start, stop, storeLabel);
                } catch (Exception ex) {
                    log.warn("[WAREHOUSE-STOCK-OVERVIEW][GROUP] storeAggFailed runId={} sid={}: {}",
                            request.getRunId(), sid, ex.toString());
                    LinkedHashMap<String, Object> miss = new LinkedHashMap<>();
                    miss.put("departmentId", sid);
                    miss.put("name", storeLabel);
                    miss.put("hasData", false);
                    miss.put("queryError", "STORE_AGG_FAILED");
                    dataMissingStores.add(miss);
                    storeRankingBaseRows.add(buildStoreRankingBaseRow(sid, storeLabel, null, false,
                            "本门店库存聚合失败（STORE_AGG_FAILED），未写入估计金额。"));
                    continue;
                }
                Boolean rm = (Boolean) one.remove("_reduceMock");
                if (Boolean.TRUE.equals(rm)) {
                    anyReduceMock = true;
                }
                boolean storeSignal = warehouseOverviewHasSignal(one);

                LinkedHashMap<String, Object> cov = new LinkedHashMap<>();
                cov.put("departmentId", sid);
                cov.put("name", storeLabel);
                cov.put("hasData", storeSignal);
                if (storeSignal) {
                    coveredStores.add(cov);
                } else {
                    dataMissingStores.add(cov);
                }

                totalBatchRows += intHint(one.get("stockBatchRowCount"));
                restAmtSum += parseDoubleLoose(one.get("totalStockAmount"));
                restWtSum += parseDoubleLoose(one.get("totalStockWeight"));
                inboundAmtSum += parseDoubleLoose(one.get("inboundAmount"));
                inboundWtSum += parseDoubleLoose(one.get("inboundWeight"));
                produceSum = produceSum.add(BigDecimal.valueOf(parseDoubleLoose(one.get("produceAmount"))));
                wasteSum = wasteSum.add(BigDecimal.valueOf(parseDoubleLoose(one.get("wasteAmount"))));
                lossSum = lossSum.add(BigDecimal.valueOf(parseDoubleLoose(one.get("lossAmount"))));
                returnSum = returnSum.add(BigDecimal.valueOf(parseDoubleLoose(one.get("returnAmount"))));

                mergeGoodAggs(mergedGoods, one.get("_byGoods"));
                appendInactiveWithStore(mergedInactive, one.get("inactiveStockItems"), storeLabel);

                String noSignalNote = storeSignal
                        ? null
                        : "本门店在统计口径下暂无库存侧有效信号（金额、种数、批次、入库或核销均为空），未臆造数值。";
                storeRankingBaseRows.add(buildStoreRankingBaseRow(sid, storeLabel, one, storeSignal, noSignalNote));

                one.clear();
            }

            BigDecimal outboundBd = produceSum;
            BigDecimal reduceAllBd = produceSum.add(wasteSum).add(lossSum).add(returnSum);

            int skuCount = mergedGoods.size();
            List<Map<String, Object>> lowStock = buildLowStockItems(mergedGoods, null);
            Set<Integer> lowGoodsIds = goodsIdsFromConcernItems(lowStock);
            removeInactiveForGoodsIds(mergedInactive, lowGoodsIds);
            Set<Integer> inactiveGoodsIds = inactiveDistinctGoodsIds(mergedInactive);
            Set<Integer> overExclude = new HashSet<>(lowGoodsIds);
            overExclude.addAll(inactiveGoodsIds);
            List<Map<String, Object>> overStock = buildOverStockItems(mergedGoods, overExclude);

            trimInactive(mergedInactive);

            String summary;
            int storesTotal = storeIds.size();
            int storesWithSignal = coveredStores.size();
            int storesNoSignal = Math.max(0, storesTotal - storesWithSignal);
            boolean hasResolvedWarehouses = hasNonEmptyVisibleWarehouses(args);
            if (skuCount == 0 && totalBatchRows == 0 && restAmtSum <= 0 && inboundAmtSum <= 0
                    && reduceAllBd.signum() == 0) {
                summary = String.format(Locale.CHINA,
                        "集团范围内共识别到 %d 家门店，其中 %d 家有库存侧信号，%d 家暂无。"
                                + "当前范围内暂未查询到有效库存记录，请确认入库、出库、盘点数据是否已录入。",
                        storesTotal, storesWithSignal, storesNoSignal);
            } else if (hasResolvedWarehouses) {
                summary = String.format(Locale.CHINA,
                        "集团范围内共识别到 %d 个门店/库房，其中 %d 个有库存侧信号，%d 个暂无有效库存记录。"
                                + "合并后约 %d 种商品仍有账面剩余（批次约 %d 行）；剩余金额约 %.2f 元，剩余重量约 %.2f%s；"
                                + "统计区间内入库约 %.2f 元、入库重量约 %.2f%s；核销侧出品约 %.2f 元，损耗 %.2f、报损 %.2f、退货 %.2f。",
                        storesTotal, storesWithSignal, storesNoSignal,
                        skuCount, totalBatchRows, restAmtSum, restWtSum, STOCK_WEIGHT_UNIT_CN,
                        inboundAmtSum, inboundWtSum, STOCK_WEIGHT_UNIT_CN,
                        outboundBd.doubleValue(), wasteSum.doubleValue(),
                        lossSum.doubleValue(), returnSum.doubleValue());
            } else {
                summary = String.format(Locale.CHINA,
                        "集团范围内共识别到 %d 家门店，其中 %d 家有库存侧信号，%d 家暂无有效库存记录。"
                                + "合并后约 %d 种商品仍有账面剩余（批次约 %d 行）；剩余金额约 %.2f 元，剩余重量约 %.2f%s；"
                                + "统计区间内入库约 %.2f 元、入库重量约 %.2f%s；核销侧出品约 %.2f 元，损耗 %.2f、报损 %.2f、退货 %.2f。",
                        storesTotal, storesWithSignal, storesNoSignal,
                        skuCount, totalBatchRows, restAmtSum, restWtSum, STOCK_WEIGHT_UNIT_CN,
                        inboundAmtSum, inboundWtSum, STOCK_WEIGHT_UNIT_CN,
                        outboundBd.doubleValue(), wasteSum.doubleValue(),
                        lossSum.doubleValue(), returnSum.doubleValue());
            }

            List<String> recommendations = buildRecommendations(lowStock, overStock, mergedInactive);

            Map<String, Object> wo = new LinkedHashMap<>();
            wo.put("scopeType", "GROUP");
            wo.put("scopeName", "集团范围");
            wo.put("visibleStoreCount", storeIds.size());
            wo.put("dataAvailableStoreCount", coveredStores.size());
            wo.put("dataMissingStoreCount", Math.max(0, storeIds.size() - coveredStores.size()));
            wo.put("coveredStores", coveredStores);
            wo.put("dataMissingStores", dataMissingStores);
            wo.put("summary", summary);
            wo.put("stockItemCount", skuCount);
            wo.put("stockBatchRowCount", totalBatchRows);
            wo.put("totalStockAmount", round2(restAmtSum));
            wo.put("totalStockWeight", round2(restWtSum));
            wo.put("inboundAmount", round2(inboundAmtSum));
            wo.put("inboundWeight", round2(inboundWtSum));
            wo.put("outboundAmount", round2(outboundBd.doubleValue()));
            wo.put("stockReduceAmount", round2(reduceAllBd.doubleValue()));
            wo.put("lossAmount", round2(lossSum.doubleValue()));
            wo.put("wasteAmount", round2(wasteSum.doubleValue()));
            wo.put("returnAmount", round2(returnSum.doubleValue()));
            wo.put("produceAmount", round2(produceSum.doubleValue()));
            wo.put("lowStockItems", tagItemsWithScope(lowStock, "集团汇总"));
            wo.put("overStockItems", tagItemsWithScope(overStock, "集团汇总"));
            wo.put("inactiveStockItems", mergedInactive);
            wo.put("recommendations", recommendations);
            wo.put("storeStockAmountRanking", sortAndRankStoresByTotalStockAmountDesc(storeRankingBaseRows));
            wo.put("storeStockItemCountRanking", sortAndRankStoresByStockItemCountDesc(storeRankingBaseRows));
            wo.put("warehouseStockRankingDegradedNote", WAREHOUSE_STOCK_RANKING_DEGRADED_NOTE);

            applyResolvedScopeFromArgs(wo, args);
            ensureGroupVisibleStores(wo, storeIds, namesById);
            normalizeStoreRollupKeys(wo.get("coveredStores"));
            normalizeStoreRollupKeys(wo.get("dataMissingStores"));
            wo.put("priorityStocktakeItems",
                    wo.get("inactiveStockItems") != null ? wo.get("inactiveStockItems") : List.of());

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("warehouseOverview", wo);

            boolean hasAny = skuCount > 0 || totalBatchRows > 0 || restAmtSum > 0 || inboundAmtSum > 0
                    || reduceAllBd.signum() != 0;
            boolean mock = anyReduceMock && !hasAny;

            return ToolResult.builder()
                    .success(true)
                    .message(mock ? "partial_empty" : "ok")
                    .data(AiBusinessToolResponses.envelope(name(), true, mock, start, stop, null, disId, data, null))
                    .build();
        } catch (Exception e) {
            log.warn("[WarehouseStockOverviewTool][GROUP] runId={} disId={}: {}", request.getRunId(), disId,
                    e.toString());
            int attempted = storeIds == null ? 0 : storeIds.size();
            Map<String, Object> wo = groupAggregationDegradedOverview(disId.intValue(), attempted, e);
            applyResolvedScopeFromArgs(wo, args);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("warehouseOverview", wo);
            return ToolResult.builder()
                    .success(true)
                    .message("degraded_after_error")
                    .data(AiBusinessToolResponses.envelope(name(), true, true, start, stop, null, disId, data,
                            "集团库存汇总降级：查询异常已吞掉，返回占位结构"))
                    .build();
        }
    }

    /**
     * 集团聚合整体失败时仍返回可读结构与 completed 链路，避免前端只看到泛化「AI 异常」。
     */
    private static Map<String, Object> groupAggregationDegradedOverview(int disId, int visibleStoresAttempted,
            Throwable e) {
        Map<String, Object> wo = new LinkedHashMap<>();
        wo.put("scopeType", "GROUP");
        wo.put("scopeName", "集团范围");
        wo.put("visibleStoreCount", visibleStoresAttempted);
        wo.put("dataAvailableStoreCount", 0);
        wo.put("dataMissingStoreCount", visibleStoresAttempted);
        wo.put("coveredStores", List.of());
        wo.put("dataMissingStores", List.of());
        wo.put("summary",
                String.format(Locale.CHINA,
                        "集团库存汇总未能完成（disId=%d，已尝试门店根数=%d）。"
                                + "请确认入库、出库、盘点数据是否已录入；若反复失败请检查系统或联系技术支持。",
                        disId, visibleStoresAttempted));
        wo.put("stockItemCount", 0);
        wo.put("stockBatchRowCount", 0);
        wo.put("totalStockAmount", 0.0);
        wo.put("totalStockWeight", 0.0);
        wo.put("inboundAmount", 0.0);
        wo.put("inboundWeight", 0.0);
        wo.put("outboundAmount", 0.0);
        wo.put("stockReduceAmount", 0.0);
        wo.put("lossAmount", 0.0);
        wo.put("wasteAmount", 0.0);
        wo.put("returnAmount", 0.0);
        wo.put("produceAmount", 0.0);
        wo.put("lowStockItems", List.of());
        wo.put("overStockItems", List.of());
        wo.put("inactiveStockItems", List.of());
        wo.put("recommendations", List.of(
                "请确认各门店入库、出库与盘点数据是否已录入。",
                "若多次失败请检查数据库连接或联系技术支持。"));
        wo.put("queryErrorCode", "WAREHOUSE_STOCK_QUERY_FAILED");
        wo.put("queryErrorMessage", e == null ? "" : String.valueOf(e.getMessage()));
        wo.put("storeStockAmountRanking", List.of());
        wo.put("storeStockItemCountRanking", List.of());
        wo.put("warehouseStockRankingDegradedNote", WAREHOUSE_STOCK_RANKING_DEGRADED_NOTE);
        return wo;
    }

    private static Map<String, Object> emptyGroupOverview(int disId, int resolvedNodes) {
        Map<String, Object> wo = new LinkedHashMap<>();
        wo.put("scopeType", "GROUP");
        wo.put("scopeName", "集团范围");
        wo.put("visibleStoreCount", 0);
        wo.put("dataAvailableStoreCount", 0);
        wo.put("dataMissingStoreCount", 0);
        wo.put("coveredStores", List.of());
        wo.put("dataMissingStores", List.of());
        wo.put("summary",
                String.format(Locale.CHINA,
                        "集团范围（disId=%d）下未解析到门店根部门（father_id=0）。resolvedDeptNodes=%d。"
                                + "请在后台核对部门树或分销户配置。",
                        disId, resolvedNodes));
        wo.put("stockItemCount", 0);
        wo.put("stockBatchRowCount", 0);
        wo.put("totalStockAmount", 0.0);
        wo.put("totalStockWeight", 0.0);
        wo.put("inboundAmount", 0.0);
        wo.put("inboundWeight", 0.0);
        wo.put("outboundAmount", 0.0);
        wo.put("stockReduceAmount", 0.0);
        wo.put("lossAmount", 0.0);
        wo.put("wasteAmount", 0.0);
        wo.put("returnAmount", 0.0);
        wo.put("produceAmount", 0.0);
        wo.put("lowStockItems", List.of());
        wo.put("overStockItems", List.of());
        wo.put("inactiveStockItems", List.of());
        wo.put("recommendations",
                List.of("请核对分销户下是否维护门店，且 gb_department_father_id=0 的门店锚点是否存在。"));
        wo.put("storeStockAmountRanking", List.of());
        wo.put("storeStockItemCountRanking", List.of());
        wo.put("warehouseStockRankingDegradedNote", WAREHOUSE_STOCK_RANKING_DEGRADED_NOTE);
        return wo;
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
            log.warn("[WarehouseStockOverviewTool] loadDepartmentNames failed ids.size={}: {}", ids.size(),
                    e.toString());
            return Map.of();
        }
    }

    /**
     * 构建单个门店父部门的库存概览；{@code storeLabel} 非空时写入 inactive/low/over 条目的门店前缀。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildWarehouseOverviewForFather(int depFatherId, int disId, String start, String stop,
            String storeLabel) {
        Map<String, Object> snap = new HashMap<>(4);
        snap.put("depFatherId", depFatherId);
        snap.put("disId", disId);

        Integer batchRowsDb = gbDepartmentGoodsStockService.queryGoodsStockCount(snap);
        int batchRows = batchRowsDb == null ? 0 : batchRowsDb;
        double restAmtTotal = nzD(gbDepartmentGoodsStockService.queryDepGoodsRestTotal(snap));
        double restWtTotal = nzD(gbDepartmentGoodsStockService.queryDepGoodsRestWeightTotal(snap));

        Map<String, Object> period = new HashMap<>(snap);
        period.put("startDate", start);
        period.put("stopDate", stop);
        double inboundAmt = nzD(gbDepartmentGoodsStockService.queryDepGoodsSubtotal(period));
        double inboundWt = nzD(gbDepartmentGoodsStockService.queryDepStockWeightTotal(period));

        Map<String, Object> reduceParams = new HashMap<>();
        reduceParams.put("departmentFatherId", (long) depFatherId);
        reduceParams.put("matchDailyRevenueDepartmentId", (long) depFatherId);
        reduceParams.put("startDate", start);
        reduceParams.put("stopDate", stop);
        Map<String, Object> rawReduce =
                gbDepartmentGoodsStockReduceService.queryReduceAllTypesTotalOnDailyRevenueDays(reduceParams);
        boolean reduceMock = rawReduce == null || rawReduce.isEmpty();

        BigDecimal produceBd = nzBd(rawReduce == null ? null : rawReduce.get("produceTotal"));
        BigDecimal wasteBd = nzBd(rawReduce == null ? null : rawReduce.get("wasteTotal"));
        BigDecimal lossBd = nzBd(rawReduce == null ? null : rawReduce.get("lossTotal"));
        BigDecimal returnBd = nzBd(rawReduce == null ? null : rawReduce.get("returnTotal"));
        BigDecimal outboundBd = produceBd;
        BigDecimal reduceAllBd = produceBd.add(wasteBd).add(lossBd).add(returnBd);

        Map<String, Object> listParams = new HashMap<>();
        listParams.put("depFatherId", depFatherId);
        listParams.put("disId", disId);
        listParams.put("restWeight", "0");
        List<GbDepartmentGoodsStockEntity> rows =
                gbDepartmentGoodsStockService.queryGoodsStockListForMendianPeriod(listParams);
        if (rows == null) {
            rows = List.of();
        }

        Map<Integer, GoodAgg> byGoods = new LinkedHashMap<>();
        List<Map<String, Object>> inactiveBatches = new ArrayList<>();
        for (GbDepartmentGoodsStockEntity e : rows) {
            double rw = parseDoubleLoose(e.getGbDgsRestWeight());
            double ra = parseDoubleLoose(e.getGbDgsRestSubtotal());
            if (rw <= 0 && ra <= 0) {
                continue;
            }
            Integer gid = e.getGbDgsGbDisGoodsId();
            int key = gid == null ? -1 : gid;
            String nmRaw = e.getGoodsName();
            final String label;
            if (nmRaw == null || nmRaw.isBlank()) {
                label = key <= 0 ? "未知商品" : ("商品ID " + key);
            } else {
                label = nmRaw.trim();
            }
            GoodAgg g = byGoods.computeIfAbsent(key, k -> new GoodAgg(label, key));
            g.restWeight += rw;
            g.restAmount += ra;
            String batchDate = e.getGbDgsDate();
            if (batchDate != null && start.compareTo(batchDate) > 0 && rw > 0) {
                if (inactiveBatches.size() < INACTIVE_BATCH_MAX) {
                    LinkedHashMap<String, Object> it = new LinkedHashMap<>();
                    if (storeLabel != null && !storeLabel.isBlank()) {
                        it.put("storeName", storeLabel);
                    }
                    it.put("goodsName", label);
                    it.put("batchDate", batchDate);
                    it.put("restWeight", round2(rw));
                    if (key > 0) {
                        it.put("goodsId", key);
                    }
                    if (e.getGbDgsGbGoodsStockId() != null) {
                        it.put("stockBatchId", e.getGbDgsGbGoodsStockId());
                    }
                    String unitName = e.getGbDgsRestWeightShowStandardName();
                    if (unitName != null && !unitName.isBlank()) {
                        it.put("weightDisplayUnit", unitName.trim());
                    }
                    it.put("note", "该批次入库早于本次统计区间起始仍有剩余，建议盘点货架是否与账面一致");
                    inactiveBatches.add(it);
                }
            }
        }

        int skuCount = byGoods.size();

        List<Map<String, Object>> lowStock = buildLowStockItems(byGoods, storeLabel);
        Set<Integer> lowGoodsIds = goodsIdsFromConcernItems(lowStock);
        inactiveBatches.removeIf(it -> shouldRemoveInactiveForLowStock(it, lowGoodsIds));
        Set<Integer> inactiveGoodsIds = inactiveDistinctGoodsIds(inactiveBatches);
        Set<Integer> overExclude = new HashSet<>(lowGoodsIds);
        overExclude.addAll(inactiveGoodsIds);
        List<Map<String, Object>> overStock = buildOverStockItems(byGoods, overExclude);

        List<String> recommendations = buildRecommendations(lowStock, overStock, inactiveBatches);

        String summary;
        if (skuCount == 0 && batchRows == 0 && restAmtTotal <= 0 && inboundAmt <= 0 && reduceAllBd.signum() == 0) {
            summary = "当前范围内暂未查询到有效库存记录，请确认入库、出库、盘点数据是否已录入。";
        } else {
            String metrics = String.format(
                    Locale.CHINA,
                    "共有 %d 种商品、约 %d 个批次仍有账面剩余；剩余金额约 %.2f 元，剩余重量约 %.2f%s；"
                            + "统计区间内入库约 %.2f 元、入库重量约 %.2f%s；核销侧出品约 %.2f 元，损耗 %.2f、报损 %.2f、退货 %.2f。",
                    skuCount, batchRows, restAmtTotal, restWtTotal, STOCK_WEIGHT_UNIT_CN, inboundAmt,
                    inboundWt, STOCK_WEIGHT_UNIT_CN,
                    outboundBd.doubleValue(), wasteBd.doubleValue(),
                    lossBd.doubleValue(), returnBd.doubleValue());
            if (storeLabel == null || storeLabel.isBlank()) {
                summary = "当前库存。" + metrics;
            } else {
                summary = String.format(Locale.CHINA, "当前库存范围：%s。", storeLabel.trim()) + metrics;
            }
        }

        Map<String, Object> wo = new LinkedHashMap<>();
        wo.put("summary", summary);
        wo.put("stockItemCount", skuCount);
        wo.put("stockBatchRowCount", batchRows);
        wo.put("totalStockAmount", round2(restAmtTotal));
        wo.put("totalStockWeight", round2(restWtTotal));
        wo.put("inboundAmount", round2(inboundAmt));
        wo.put("inboundWeight", round2(inboundWt));
        wo.put("outboundAmount", round2(outboundBd.doubleValue()));
        wo.put("stockReduceAmount", round2(reduceAllBd.doubleValue()));
        wo.put("lossAmount", round2(lossBd.doubleValue()));
        wo.put("wasteAmount", round2(wasteBd.doubleValue()));
        wo.put("returnAmount", round2(returnBd.doubleValue()));
        wo.put("produceAmount", round2(produceBd.doubleValue()));
        wo.put("lowStockItems", lowStock);
        wo.put("overStockItems", overStock);
        wo.put("inactiveStockItems", inactiveBatches);
        wo.put("recommendations", recommendations);
        wo.put("_reduceMock", reduceMock);
        wo.put("_byGoods", byGoods);
        return wo;
    }

    private static List<Map<String, Object>> buildLowStockItems(Map<Integer, GoodAgg> byGoods, String storeLabel) {
        List<Map<String, Object>> lowStock = new ArrayList<>();
        byGoods.values().stream()
                .filter(g -> g.restWeight > 0 && g.restWeight < LOW_STOCK_WEIGHT_MAX)
                .sorted(Comparator.comparingDouble(g -> g.restWeight))
                .limit(LOW_STOCK_TOP_N)
                .forEach(g -> {
                    LinkedHashMap<String, Object> it = new LinkedHashMap<>();
                    if (storeLabel != null && !storeLabel.isBlank()) {
                        it.put("storeName", storeLabel);
                    }
                    it.put("goodsName", g.name);
                    if (g.goodsId > 0) {
                        it.put("goodsId", g.goodsId);
                    }
                    it.put("restWeightTotal", round2(g.restWeight));
                    it.put("restAmountTotal", round2(g.restAmount));
                    it.put("note", "剩余重量偏低，请关注是否需要补货");
                    lowStock.add(it);
                });
        return lowStock;
    }

    private static List<Map<String, Object>> buildOverStockItems(Map<Integer, GoodAgg> byGoods,
            Set<Integer> excludeGoodsIds) {
        Set<Integer> ex = excludeGoodsIds == null ? Set.of() : excludeGoodsIds;
        double maxAmt = byGoods.values().stream().mapToDouble(g -> g.restAmount).max().orElse(0);
        double threshold = Math.max(1e-6, maxAmt * 0.12);
        List<Map<String, Object>> overStock = new ArrayList<>();
        byGoods.values().stream()
                .filter(g -> !ex.contains(g.goodsId) && g.restAmount >= threshold && g.restAmount > 0)
                .sorted(Comparator.comparingDouble((GoodAgg g) -> g.restAmount).reversed())
                .limit(OVERSTOCK_TOP_N)
                .forEach(g -> {
                    LinkedHashMap<String, Object> it = new LinkedHashMap<>();
                    it.put("goodsName", g.name);
                    if (g.goodsId > 0) {
                        it.put("goodsId", g.goodsId);
                    }
                    it.put("restAmountTotal", round2(g.restAmount));
                    it.put("restWeightTotal", round2(g.restWeight));
                    it.put("note", "剩余金额相对较高，建议优先消耗避免积压");
                    overStock.add(it);
                });
        return overStock;
    }

    private static Set<Integer> goodsIdsFromConcernItems(List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) {
            return Set.of();
        }
        Set<Integer> out = new HashSet<>();
        for (Map<String, Object> it : items) {
            if (it == null) {
                continue;
            }
            Object g = it.get("goodsId");
            if (g instanceof Number n) {
                int id = n.intValue();
                if (id > 0) {
                    out.add(id);
                }
            }
        }
        return out;
    }

    private static Set<Integer> inactiveDistinctGoodsIds(List<Map<String, Object>> inactive) {
        return goodsIdsFromConcernItems(inactive);
    }

    private static void removeInactiveForGoodsIds(List<Map<String, Object>> inactive, Set<Integer> lowGoodsIds) {
        if (inactive == null || inactive.isEmpty() || lowGoodsIds == null || lowGoodsIds.isEmpty()) {
            return;
        }
        inactive.removeIf(it -> shouldRemoveInactiveForLowStock(it, lowGoodsIds));
    }

    private static boolean shouldRemoveInactiveForLowStock(Map<String, Object> it, Set<Integer> lowGoodsIds) {
        if (it == null || lowGoodsIds == null || lowGoodsIds.isEmpty()) {
            return false;
        }
        Object g = it.get("goodsId");
        if (g instanceof Number n) {
            return lowGoodsIds.contains(n.intValue());
        }
        return false;
    }

    private static List<String> buildRecommendations(List<Map<String, Object>> lowStock,
            List<Map<String, Object>> overStock, List<Map<String, Object>> inactiveBatches) {
        List<String> recommendations = new ArrayList<>();
        if (!lowStock.isEmpty()) {
            recommendations.add("优先核对偏低库存商品是否即将缺货，并与订货周期对齐补货。");
        }
        if (!overStock.isEmpty()) {
            recommendations.add("对积压较高的商品安排出库或促销消耗，避免长期呆滞。");
        }
        if (!inactiveBatches.isEmpty()) {
            recommendations.add("对「早于本次统计区间起始日入库、当前仍有账面剩余」的批次安排抽检盘点。");
        }
        recommendations.add("每日核对入库、核销（出品/损耗/报损/退货）记录是否与实物一致。");
        return recommendations;
    }

    @SuppressWarnings("unchecked")
    private static void mergeGoodAggs(Map<Integer, GoodAgg> merged, Object byGoodsObj) {
        if (!(byGoodsObj instanceof Map<?, ?> raw)) {
            return;
        }
        for (Object v : raw.values()) {
            if (!(v instanceof GoodAgg g)) {
                continue;
            }
            GoodAgg t = merged.computeIfAbsent(g.goodsId, k -> new GoodAgg(g.name, g.goodsId));
            t.restWeight += g.restWeight;
            t.restAmount += g.restAmount;
        }
    }

    @SuppressWarnings("unchecked")
    private static void appendInactiveWithStore(List<Map<String, Object>> mergedInactive, Object itemsObj,
            String storeLabel) {
        if (!(itemsObj instanceof List<?> list)) {
            return;
        }
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> mm)) {
                continue;
            }
            LinkedHashMap<String, Object> it = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : mm.entrySet()) {
                it.put(String.valueOf(e.getKey()), e.getValue());
            }
            it.putIfAbsent("storeName", storeLabel);
            mergedInactive.add(it);
            if (mergedInactive.size() >= INACTIVE_BATCH_MAX * 8) {
                break;
            }
        }
    }

    private static void trimInactive(List<Map<String, Object>> mergedInactive) {
        if (mergedInactive.size() > INACTIVE_BATCH_MAX * 3) {
            mergedInactive.subList(INACTIVE_BATCH_MAX * 3, mergedInactive.size()).clear();
        }
    }

    private static List<Map<String, Object>> tagItemsWithScope(List<Map<String, Object>> items, String scopeNote) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> it : items) {
            LinkedHashMap<String, Object> copy = new LinkedHashMap<>(it);
            copy.putIfAbsent("scopeNote", scopeNote);
            out.add(copy);
        }
        return out;
    }

    /** D-6 Phase 4C：门店级排行基准行（不含 rank；排序后浅拷贝写入 rank）。 */
    private static LinkedHashMap<String, Object> buildStoreRankingBaseRow(int sid, String storeName,
            Map<String, Object> one, boolean dataAvailable, String note) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("storeDepartmentId", sid);
        m.put("storeName", storeName);
        if (one != null) {
            m.put("totalStockAmount", round2(parseDoubleLoose(one.get("totalStockAmount"))));
            m.put("stockItemCount", intHint(one.get("stockItemCount")));
            m.put("stockBatchRowCount", intHint(one.get("stockBatchRowCount")));
            m.put("lowStockItemCount", listSize(one.get("lowStockItems")));
            m.put("overStockItemCount", listSize(one.get("overStockItems")));
        } else {
            m.put("totalStockAmount", 0.0);
            m.put("stockItemCount", 0);
            m.put("stockBatchRowCount", 0);
            m.put("lowStockItemCount", 0);
            m.put("overStockItemCount", 0);
        }
        m.put("dataAvailable", dataAvailable);
        if (note != null && !note.isBlank()) {
            m.put("note", note);
        }
        return m;
    }

    private static int listSize(Object listObj) {
        if (listObj instanceof List<?> l) {
            return l.size();
        }
        return 0;
    }

    private static List<Map<String, Object>> sortAndRankStoresByTotalStockAmountDesc(
            List<LinkedHashMap<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<LinkedHashMap<String, Object>> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator
                .<LinkedHashMap<String, Object>, Double>comparing(m -> ((Number) m.get("totalStockAmount")).doubleValue())
                .reversed()
                .thenComparing(m -> (Integer) m.get("storeDepartmentId")));
        List<Map<String, Object>> out = new ArrayList<>(sorted.size());
        int rank = 1;
        for (LinkedHashMap<String, Object> src : sorted) {
            LinkedHashMap<String, Object> row = new LinkedHashMap<>(src);
            row.put("rank", rank++);
            out.add(row);
        }
        return out;
    }

    private static List<Map<String, Object>> sortAndRankStoresByStockItemCountDesc(
            List<LinkedHashMap<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<LinkedHashMap<String, Object>> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator
                .<LinkedHashMap<String, Object>, Integer>comparing(m -> (Integer) m.get("stockItemCount"))
                .reversed()
                .thenComparing(m -> (Integer) m.get("storeDepartmentId")));
        List<Map<String, Object>> out = new ArrayList<>(sorted.size());
        int rank = 1;
        for (LinkedHashMap<String, Object> src : sorted) {
            LinkedHashMap<String, Object> row = new LinkedHashMap<>(src);
            row.put("rank", rank++);
            out.add(row);
        }
        return out;
    }

    private static boolean warehouseOverviewHasSignal(Map<String, Object> wo) {
        int sku = intHint(wo.get("stockItemCount"));
        int rows = intHint(wo.get("stockBatchRowCount"));
        double amt = parseDoubleLoose(wo.get("totalStockAmount"));
        double wt = parseDoubleLoose(wo.get("totalStockWeight"));
        double inbound = parseDoubleLoose(wo.get("inboundAmount"));
        double reduce = parseDoubleLoose(wo.get("stockReduceAmount"));
        return sku > 0 || rows > 0 || amt > 0 || wt > 0 || inbound > 0 || reduce > 0;
    }

    private static List<Integer> parsePositiveIntList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Integer> out = new ArrayList<>(list.size());
        for (Object o : list) {
            if (o instanceof Number n) {
                int v = n.intValue();
                if (v > 0) {
                    out.add(v);
                }
            } else if (o != null) {
                try {
                    int v = Integer.parseInt(o.toString().trim());
                    if (v > 0) {
                        out.add(v);
                    }
                } catch (NumberFormatException ignored) {
                    // skip
                }
            }
        }
        out.sort(Integer::compareTo);
        return out;
    }

    private static final class GoodAgg {
        final String name;
        final int goodsId;
        double restWeight;
        double restAmount;

        GoodAgg(String name, int goodsId) {
            this.name = name;
            this.goodsId = goodsId;
        }
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static double nzD(Double v) {
        return v == null ? 0.0 : v;
    }

    private static BigDecimal nzBd(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        if (v instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(v.toString().trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private static double parseDoubleLoose(String s) {
        if (s == null || s.isBlank()) {
            return 0;
        }
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static double parseDoubleLoose(Object v) {
        if (v == null) {
            return 0;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        return parseDoubleLoose(v.toString());
    }

    private static Long toLong(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(v.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(Object v) {
        return v == null ? "" : v.toString().trim();
    }

    private static int intHint(Object v) {
        if (v == null) {
            return 0;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(v.toString().trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static boolean hasNonEmptyVisibleWarehouses(Map<String, Object> args) {
        if (args == null) {
            return false;
        }
        Object vw = args.get(ARG_VISIBLE_WAREHOUSES);
        return vw instanceof List<?> list && !list.isEmpty();
    }

    /** 透传 {@link com.nongxinle.ai.graph.business.BusinessToolExecutionNode} 注入的 ResolvedQuery 范围字段。 */
    private static void applyResolvedScopeFromArgs(Map<String, Object> wo, Map<String, Object> args) {
        if (wo == null || args == null) {
            return;
        }
        Object b = args.get(ARG_QUERY_SCOPE_BANNER);
        if (b != null && !b.toString().isBlank()) {
            wo.put("queryScopeBanner", b.toString().trim());
        }
        Object vs = args.get(ARG_VISIBLE_STORES);
        if (vs instanceof List<?> list && !list.isEmpty()) {
            wo.put("visibleStores", vs);
        }
        Object vw = args.get(ARG_VISIBLE_WAREHOUSES);
        if (vw instanceof List<?> list && !list.isEmpty()) {
            wo.put("visibleWarehouses", vw);
        }
    }

    /** 集团合并：若未从入参带入 visibleStores，则由本轮聚合门店根回填。 */
    private static void ensureGroupVisibleStores(Map<String, Object> wo, List<Integer> storeIds,
            Map<Integer, String> namesById) {
        Object cur = wo.get("visibleStores");
        if (cur instanceof List<?> l && !l.isEmpty()) {
            return;
        }
        if (storeIds == null || storeIds.isEmpty()) {
            return;
        }
        List<Map<String, Object>> vs = new ArrayList<>();
        for (Integer sid : storeIds) {
            if (sid == null || sid <= 0) {
                continue;
            }
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("storeDepartmentId", sid.longValue());
            row.put("storeName", namesById.getOrDefault(sid, "门店 " + sid));
            vs.add(row);
        }
        wo.put("visibleStores", vs);
    }

    @SuppressWarnings("unchecked")
    private static void normalizeStoreRollupKeys(Object listObj) {
        if (!(listObj instanceof List<?> list)) {
            return;
        }
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> m = (Map<String, Object>) raw;
            if (!m.containsKey("storeDepartmentId") && m.get("departmentId") != null) {
                Object did = m.get("departmentId");
                if (did instanceof Number n) {
                    m.put("storeDepartmentId", n.longValue());
                }
            }
            if (!m.containsKey("storeName") && m.get("name") != null) {
                m.put("storeName", Objects.toString(m.get("name"), "").trim());
            }
        }
    }
}
