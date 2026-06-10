package com.nongxinle.ai.tool.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.dto.business.GoodsStockBatchDetailAnswerPlan;
import com.nongxinle.ai.dto.business.GoodsSupportedDishCoverAnswerPlan;
import com.nongxinle.ai.graph.business.DishIngredientCoverSalesBaseline;
import com.nongxinle.ai.graph.business.DishIngredientCoverSalesBaselineSupport;
import com.nongxinle.ai.graph.business.GoodsStockBatchDetailDomainSupport;
import com.nongxinle.ai.graph.business.GoodsSupportedDishCoverDomainService;
import com.nongxinle.ai.inventory.InventoryQueryTimeKind;
import com.nongxinle.ai.tool.AiTool;
import com.nongxinle.ai.tool.ToolRequest;
import com.nongxinle.ai.tool.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_DEPARTMENT_FATHER_ID;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_DIS_ID;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_PURCHASE_FOCUS_DIS_GOODS_ID;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_PURCHASE_FOCUS_GOODS_NAME;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_QUERY_SCOPE_BANNER;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_REQUESTED_PLAN_OUTPUTS;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_SALES_BASELINE_START_DATE;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_SALES_BASELINE_STOP_DATE;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_STOCK_AS_OF_DATE;

/**
 * 原料 → 受影响菜品可支撑分析（库存快照 + 近 7 天销量基线）。
 * 与 {@code goods_amount_ranking_low}、{@code dish.ingredient_cover_days.v1}、库存风险列表严格区分。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WarehouseGoodsSupportedDishCoverTool implements AiTool {

    private final GoodsSupportedDishCoverDomainService domainService;
    private final GoodsStockBatchDetailDomainSupport batchDetailDomainSupport;

    @Override
    public String name() {
        return AiBusinessToolIds.WAREHOUSE_GOODS_SUPPORTED_DISH_COVER;
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        Map<String, Object> args = request.getArgs() == null ? Map.of() : request.getArgs();
        List<String> planOutputs = readRequestedPlanOutputs(args);
        boolean wantCover = planOutputs.contains(GoodsSupportedDishCoverAnswerPlan.TYPE);
        boolean wantBatch = planOutputs.contains(GoodsStockBatchDetailAnswerPlan.TYPE);
        if (!wantCover && !wantBatch) {
            return fail("missing_requested_plan_outputs", args);
        }

        Long disLong = toLong(args.get(ARG_DIS_ID));
        Long deptLong = toLong(args.get(ARG_DEPARTMENT_FATHER_ID));
        if (disLong == null) {
            return fail("missing_dis_id", args);
        }
        int disId = disLong.intValue();
        Integer depFatherId = deptLong == null ? null : deptLong.intValue();
        Integer disGoodsId = toInt(args.get(ARG_PURCHASE_FOCUS_DIS_GOODS_ID));
        String goodsName = str(args.get(ARG_PURCHASE_FOCUS_GOODS_NAME));
        String stockAsOf = str(args.get(ARG_STOCK_AS_OF_DATE));
        String baselineStart = str(args.get(ARG_SALES_BASELINE_START_DATE));
        String baselineStop = str(args.get(ARG_SALES_BASELINE_STOP_DATE));
        String scopeBanner = str(args.get(ARG_QUERY_SCOPE_BANNER));

        DishIngredientCoverSalesBaseline baseline =
                wantCover ? resolveBaseline(request, baselineStart, baselineStop) : null;
        if (baseline != null) {
            baselineStart = baseline.getStartDateIso();
            baselineStop = baseline.getStopDateIso();
        }

        LinkedHashMap<String, Object> debug = new LinkedHashMap<>();
        try {
            Map<String, Object> coverCore = null;
            Map<String, Object> batchCore = null;
            if (wantCover) {
                coverCore =
                        domainService.buildPayload(
                                disId,
                                depFatherId,
                                disGoodsId,
                                goodsName,
                                baseline,
                                stockAsOf,
                                debug);
            }
            if (wantBatch) {
                batchCore =
                        batchDetailDomainSupport.buildPayload(
                                disId, depFatherId, disGoodsId, goodsName, debug);
                if (batchCore != null && scopeBanner != null) {
                    batchCore.put("scopeBanner", scopeBanner);
                }
            }

            LinkedHashMap<String, Object> data = new LinkedHashMap<>();
            if (wantCover && coverCore != null) {
                data.put(GoodsSupportedDishCoverDomainService.PAYLOAD_KEY, coverCore);
            }
            if (wantBatch && batchCore != null) {
                data.put(GoodsStockBatchDetailDomainSupport.PAYLOAD_KEY, batchCore);
            }
            if (scopeBanner != null) {
                data.put("scopeBanner", scopeBanner);
            }
            data.put(
                    "inventoryQueryTimeKind",
                    wantCover
                            ? InventoryQueryTimeKind.HYBRID_SNAPSHOT_WITH_PERIOD_BASELINE.name()
                            : InventoryQueryTimeKind.CURRENT_SNAPSHOT.name());
            data.put("debug", debug);

            boolean needClarification = false;
            boolean ok = true;
            if (wantCover) {
                String coverStatus = coverCore == null ? null : str(coverCore.get("status"));
                if ("NEED_CLARIFICATION".equalsIgnoreCase(coverStatus)) {
                    needClarification = true;
                } else if (!"OK".equalsIgnoreCase(coverStatus)) {
                    ok = false;
                }
            }
            if (wantBatch) {
                String batchStatus = batchCore == null ? null : str(batchCore.get("status"));
                if ("NEED_CLARIFICATION".equalsIgnoreCase(batchStatus)) {
                    needClarification = true;
                } else if (!"OK".equalsIgnoreCase(batchStatus)) {
                    ok = false;
                }
            }

            return ToolResult.builder()
                    .success(ok || needClarification)
                    .message(needClarification ? "need_clarification" : (ok ? "ok" : "failed"))
                    .data(
                            AiBusinessToolResponses.envelope(
                                    name(),
                                    ok,
                                    needClarification,
                                    baselineStart,
                                    baselineStop,
                                    deptLong,
                                    disLong,
                                    data,
                                    needClarification ? "需要澄清具体原料" : null))
                    .build();
        } catch (Exception ex) {
            log.warn("[WarehouseGoodsSupportedDishCoverTool] runId={} {}", request.getRunId(), ex.toString());
            return ToolResult.builder()
                    .success(false)
                    .message(ex.getMessage())
                    .data(
                            AiBusinessToolResponses.envelope(
                                    name(), false, false, baselineStart, baselineStop, deptLong, disLong,
                                    Map.of("error", ex.getMessage()), "查询异常"))
                    .build();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> readRequestedPlanOutputs(Map<String, Object> args) {
        if (args == null || args.isEmpty()) {
            return List.of();
        }
        Object raw = args.get(ARG_REQUESTED_PLAN_OUTPUTS);
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private static ToolResult fail(String msg, Map<String, Object> args) {
        return ToolResult.builder()
                .success(false)
                .message(msg)
                .data(Map.of("args", args))
                .build();
    }

    private static Long toLong(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(o.toString().trim());
        } catch (NumberFormatException ex) {
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
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static DishIngredientCoverSalesBaseline resolveBaseline(
            ToolRequest request, String start, String stop) {
        AiResolvedQueryContext rq = request != null ? request.getResolvedQueryContext() : null;
        if (rq != null) {
            return DishIngredientCoverSalesBaselineSupport.resolve(null, rq);
        }
        if (!StringUtils.hasText(start) || !StringUtils.hasText(stop)) {
            return DishIngredientCoverSalesBaseline.builder()
                    .startDateIso(start)
                    .stopDateIso(stop)
                    .baselineDays(DishIngredientCoverSalesBaseline.DEFAULT_BASELINE_DAYS)
                    .baselineSource(DishIngredientCoverSalesBaseline.SOURCE_DEFAULT_LAST_7_DAYS)
                    .displayLabel("最近7天")
                    .build();
        }
        return baselineFromArgDates(start, stop);
    }

    private static DishIngredientCoverSalesBaseline baselineFromArgDates(String start, String stop) {
        java.time.LocalDate s = java.time.LocalDate.parse(start.trim());
        java.time.LocalDate e = java.time.LocalDate.parse(stop.trim());
        if (e.isBefore(s)) {
            java.time.LocalDate tmp = s;
            s = e;
            e = tmp;
        }
        int days = (int) Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(s, e) + 1);
        return DishIngredientCoverSalesBaseline.builder()
                .startDateIso(s.toString())
                .stopDateIso(e.toString())
                .baselineDays(days)
                .baselineSource(DishIngredientCoverSalesBaseline.SOURCE_USER_EXPLICIT_TIME_WINDOW)
                .displayLabel(start.trim() + "至" + stop.trim())
                .build();
    }

    private static String str(Object o) {
        if (o == null) {
            return null;
        }
        String t = o.toString().trim();
        return t.isEmpty() ? null : t;
    }
}
