package com.nongxinle.ai.tool.business;

import com.nongxinle.ai.graph.business.DishIngredientCoverSalesBaseline;
import com.nongxinle.ai.graph.business.DishIngredientCoverSalesBaselineSupport;
import com.nongxinle.ai.graph.business.GoodsSupportedDishCoverDomainService;
import com.nongxinle.ai.inventory.InventoryPresentationTimeSupport;
import com.nongxinle.ai.inventory.InventoryQueryTimeKind;
import com.nongxinle.ai.tool.AiTool;
import com.nongxinle.ai.tool.ToolRequest;
import com.nongxinle.ai.tool.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_DEPARTMENT_FATHER_ID;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_DIS_ID;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_INVENTORY_QUERY_TIME_KIND;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_PURCHASE_FOCUS_DIS_GOODS_ID;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_PURCHASE_FOCUS_GOODS_NAME;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_QUERY_SCOPE_BANNER;
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

    @Override
    public String name() {
        return AiBusinessToolIds.WAREHOUSE_GOODS_SUPPORTED_DISH_COVER;
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        Map<String, Object> args = request.getArgs() == null ? Map.of() : request.getArgs();
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

        DishIngredientCoverSalesBaseline baseline = resolveBaseline(baselineStart, baselineStop);

        LinkedHashMap<String, Object> debug = new LinkedHashMap<>();
        try {
            Map<String, Object> core =
                    domainService.buildPayload(
                            disId,
                            depFatherId,
                            disGoodsId,
                            goodsName,
                            baseline,
                            stockAsOf,
                            debug);
            LinkedHashMap<String, Object> data = new LinkedHashMap<>();
            data.put(GoodsSupportedDishCoverDomainService.PAYLOAD_KEY, core);
            data.put("scopeBanner", str(args.get(ARG_QUERY_SCOPE_BANNER)));
            data.put("inventoryQueryTimeKind", InventoryQueryTimeKind.HYBRID_SNAPSHOT_WITH_PERIOD_BASELINE.name());
            data.put("debug", debug);
            String status = str(core.get("status"));
            boolean needClarification = "NEED_CLARIFICATION".equalsIgnoreCase(status);
            boolean ok = "OK".equalsIgnoreCase(status);
            return ToolResult.builder()
                    .success(ok || needClarification)
                    .message(needClarification ? "need_clarification" : (ok ? "ok" : status))
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

    private static DishIngredientCoverSalesBaseline resolveBaseline(String start, String stop) {
        if (!StringUtils.hasText(start) || !StringUtils.hasText(stop)) {
            return DishIngredientCoverSalesBaseline.builder()
                    .startDateIso(start)
                    .stopDateIso(stop)
                    .baselineDays(DishIngredientCoverSalesBaseline.DEFAULT_BASELINE_DAYS)
                    .baselineSource(DishIngredientCoverSalesBaseline.SOURCE_DEFAULT_LAST_7_DAYS)
                    .displayLabel("最近7天")
                    .build();
        }
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
