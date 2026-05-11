package com.nongxinle.ai.tool.business;

import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.tool.AiTool;
import com.nongxinle.ai.tool.ToolRequest;
import com.nongxinle.ai.tool.ToolResult;
import com.nongxinle.entity.GbDistributerGoodsEntity;
import com.nongxinle.service.GbDepartmentGoodsStockReduceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_DEPARTMENT_FATHER_ID;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_DIS_ID;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_GROUP_STOCK_REDUCE_AGGREGATION;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_START_DATE;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_STOCK_REDUCE_HARNESS_PATH;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_STOCK_REDUCE_NARRATIVE_MODE;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_STOP_DATE;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockReduceQueryTool implements AiTool {

    private final GbDepartmentGoodsStockReduceService stockReduceService;

    @Override
    public String name() {
        return AiBusinessToolIds.STOCK_REDUCE_QUERY;
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        Map<String, Object> args = request.getArgs() == null ? Map.of() : request.getArgs();
        if (Boolean.TRUE.equals(args.get(ARG_STOCK_REDUCE_HARNESS_PATH))) {
            return executeHarnessCalendarPath(request, args);
        }
        return executeLegacyEmbeddedCostPath(request, args);
    }

    /**
     * 独立 {@code stock_reduce_query_path}：自然日历日 {@code queryReduceAllTypesTotal*}
     * （与嵌入成本链路的「仅日营业额日」口径区分）。
     */
    private ToolResult executeHarnessCalendarPath(ToolRequest request, Map<String, Object> args) {
        String start = str(args.get(ARG_START_DATE));
        String stop = str(args.get(ARG_STOP_DATE));
        AiResolvedQueryContext rctx = request.getResolvedQueryContext();
        if ((start.isEmpty() || stop.isEmpty()) && rctx != null && rctx.getTimeWindow() != null) {
            var tw = rctx.getTimeWindow();
            if (tw.getStartDate() != null) {
                start = tw.getStartDate().toString();
            }
            if (tw.getEndDate() != null) {
                stop = tw.getEndDate().toString();
            }
        }
        Long dis = toLong(args.get(ARG_DIS_ID));
        boolean group = Boolean.TRUE.equals(args.get(ARG_GROUP_STOCK_REDUCE_AGGREGATION));
        String narrative = str(args.get(ARG_STOCK_REDUCE_NARRATIVE_MODE));

        if (start.isEmpty() || stop.isEmpty()) {
            return harnessError(request, start, stop, null, dis,
                    AiBusinessToolResponses.envelope(name(), false, false, start, stop, null, dis,
                            Map.of("reason", "missing_dates"), "时间窗未解析"));
        }

        Map<String, Object> params = new HashMap<>(8);
        if (dis != null) {
            params.put("disId", dis);
        }
        params.put("startDate", start);
        params.put("stopDate", stop);

        Map<String, Object> raw;
        Long anchorFather = null;
        try {
            if (group) {
                // 仅按 orgScope.visibleStores 的门店根 ID 汇总；不得用 dataScope 展开 ID（会与单店追问不一致）。
                List<Long> fatherIds = fatherIdsFromVisibleStoresOnly(rctx);
                if (fatherIds.isEmpty()) {
                    fatherIds = longListArg(args.get(AiBusinessToolIds.ARG_RESOLVED_DEPARTMENT_IDS));
                }
                if (fatherIds.isEmpty()) {
                    return harnessError(request, start, stop, null, dis,
                            AiBusinessToolResponses.envelope(name(), false, false, start, stop, null, dis,
                                    Map.of("reason", "missing_departmentFatherIds"),
                                    "集团汇总缺少门店根 ID（visibleStores）"));
                }
                params.put("departmentFatherIds", fatherIds);
                log.info("[StockReduceQueryTool] harness group runId={} departmentFatherIds={} (fromVisibleStoresPreferred)",
                        request.getRunId(), fatherIds);
                raw = stockReduceService.queryReduceAllTypesTotalForRetailDepartmentFathers(params);
            } else {
                Long dept = resolveHarnessSingleStoreFatherId(rctx, args);
                if (dept == null) {
                    return harnessError(request, start, stop, null, dis,
                            AiBusinessToolResponses.envelope(name(), false, false, start, stop, null, dis,
                                    Map.of("reason", "missing_departmentFatherId"),
                                    "缺少门店根 departmentFatherId"));
                }
                anchorFather = dept;
                // 与集团同源：零售父部门 type in (1,11) + 四类 subtotal，避免「集团 IN 与单店 =」口径分叉
                params.put("departmentFatherIds", List.of(dept));
                log.info("[StockReduceQueryTool] harness store runId={} departmentFatherId={} (singletonVisibleStoreWinsOverArgs)",
                        request.getRunId(), dept);
                raw = stockReduceService.queryReduceAllTypesTotalForRetailDepartmentFathers(params);
            }
        } catch (Exception e) {
            log.warn("[StockReduceQueryTool] harness runId={}: {}", request.getRunId(), e.toString());
            Map<String, Object> dataErr = new LinkedHashMap<>(AiBusinessToolResponses.mockPayload(e.getMessage()));
            dataErr.put("calendarNaturalDayTotals", false);
            return ToolResult.builder()
                    .success(false)
                    .message(e.getMessage())
                    .data(AiBusinessToolResponses.envelope(name(), false, true, start, stop, anchorFather, dis, dataErr,
                            "出库/核销查询异常"))
                    .build();
        }

        boolean mock = raw == null || raw.isEmpty();
        BigDecimal produce = nz(raw == null ? null : raw.get("produceTotal"));
        BigDecimal waste = nz(raw == null ? null : raw.get("wasteTotal"));
        BigDecimal loss = nz(raw == null ? null : raw.get("lossTotal"));
        BigDecimal ret = nz(raw == null ? null : raw.get("returnTotal"));
        BigDecimal grand = produce.add(waste).add(loss).add(ret);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("rawReduceTotals", raw);
        data.put("produceTotal", produce);
        data.put("wasteTotal", waste);
        data.put("lossTotal", loss);
        data.put("returnTotal", ret);
        data.put("grandTotalFourTypes", grand);
        data.put("totalsBasis", "CALENDAR_NATURAL_DAY");
        data.put("groupStockReduceAggregation", group);

        boolean ranking =
                AiQuerySemanticLexicon.STRUCTURED_GOODS_OUTBOUND_RANKING.equals(narrative);
        if (ranking && !mock) {
            try {
                Map<String, Object> topParams = new HashMap<>(params);
                List<Map<String, Object>> rows = topGoodsPayload(
                        stockReduceService.queryStockSubtotalTopTimes(topParams));
                data.put("topGoodsOutboundBySubtotal", rows);
            } catch (Exception e) {
                log.debug("[StockReduceQueryTool] top goods runId={}: {}", request.getRunId(), e.toString());
            }
        }

        return ToolResult.builder()
                .success(true)
                .message(mock ? "empty" : "ok")
                .data(AiBusinessToolResponses.envelope(name(), true, mock, start, stop, anchorFather, dis, data, null))
                .build();
    }

    private static ToolResult harnessError(ToolRequest request, String start, String stop, Long dept, Long dis,
            Map<String, Object> envelope) {
        log.info("[StockReduceQueryTool] harness validation runId={} envelope={}",
                request.getRunId(), envelope);
        return ToolResult.builder().success(false).message("validation").data(envelope).build();
    }

    /**
     * 集团出库汇总必须与 Run Debug 中 {@code visibleStores} 一致，只收集门店根 ID；
     * 不得使用 dataScope 展开后的 query 部门列表。
     */
    private static List<Long> fatherIdsFromVisibleStoresOnly(AiResolvedQueryContext rctx) {
        if (rctx == null || rctx.getOrgScope() == null) {
            return List.of();
        }
        List<AiStoreScopeDTO> stores = rctx.getOrgScope().getVisibleStores();
        if (stores == null || stores.isEmpty()) {
            return List.of();
        }
        List<Long> out = new ArrayList<>(stores.size());
        for (AiStoreScopeDTO s : stores) {
            if (s == null || s.getStoreDepartmentId() == null) {
                continue;
            }
            long id = s.getStoreDepartmentId();
            if (id > 0L) {
                out.add(id);
            }
        }
        return out;
    }

    /**
     * 单店出库必须与 Run Debug 中 {@code visibleStores}（仅 1 家）一致。
     * 不能优先使用 Tool args 里的 {@link AiBusinessToolIds#ARG_DEPARTMENT_FATHER_ID}：它来自
     * {@code state.getDepartmentId}，集团账号下往往是固定登录锚点，会导致「AAA / 汀兰」追问得到同一 father、金额完全相同。
     */
    private static Long resolveHarnessSingleStoreFatherId(AiResolvedQueryContext rctx, Map<String, Object> args) {
        if (rctx != null && rctx.getOrgScope() != null
                && rctx.getOrgScope().getVisibleStores() != null) {
            List<AiStoreScopeDTO> vs = rctx.getOrgScope().getVisibleStores();
            if (vs.size() == 1) {
                AiStoreScopeDTO s0 = vs.get(0);
                if (s0 != null && s0.getStoreDepartmentId() != null && s0.getStoreDepartmentId() > 0L) {
                    return s0.getStoreDepartmentId();
                }
            }
        }
        return toLong(args.get(ARG_DEPARTMENT_FATHER_ID));
    }

    /** 嵌入成本链路：与原经营看板一致，仅统计有日营业额的自然日。 */
    private ToolResult executeLegacyEmbeddedCostPath(ToolRequest request, Map<String, Object> args) {
        Long dept = toLong(args.get(ARG_DEPARTMENT_FATHER_ID));
        String start = str(args.get(ARG_START_DATE));
        String stop = str(args.get(ARG_STOP_DATE));

        if (dept == null || start.isEmpty() || stop.isEmpty()) {
            Map<String, Object> data = new LinkedHashMap<>();
            return ToolResult.builder()
                    .success(false)
                    .message("missing departmentFatherId/dates")
                    .data(AiBusinessToolResponses.envelope(name(), false, false, start, stop, dept, null, data,
                            "参数不完整"))
                    .build();
        }

        Long dis = toLong(args.get(ARG_DIS_ID));
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("departmentFatherId", dept);
            params.put("matchDailyRevenueDepartmentId", dept);
            params.put("startDate", start);
            params.put("stopDate", stop);
            if (dis != null) {
                params.put("disId", dis);
            }
            Map<String, Object> raw = stockReduceService.queryReduceAllTypesTotalOnDailyRevenueDays(params);
            boolean mock = raw == null || raw.isEmpty();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("rawReduceTotals", raw);
            BigDecimal produce = nz(raw == null ? null : raw.get("produceTotal"));
            BigDecimal waste = nz(raw == null ? null : raw.get("wasteTotal"));
            BigDecimal loss = nz(raw == null ? null : raw.get("lossTotal"));
            BigDecimal ret = nz(raw == null ? null : raw.get("returnTotal"));
            data.put("produceTotal", produce);
            data.put("wasteTotal", waste);
            data.put("lossTotal", loss);
            data.put("returnTotal", ret);
            data.put("grandTotalFourTypes", produce.add(waste).add(loss).add(ret));
            data.put("totalsBasis", "DAILY_REVENUE_DAYS_ONLY");
            data.put("productionTotalExcludeReturn", produce.add(waste).add(loss));
            return ToolResult.builder()
                    .success(true)
                    .message(mock ? "empty" : "ok")
                    .data(AiBusinessToolResponses.envelope(name(), true, mock, start, stop, dept, dis, data, null))
                    .build();
        } catch (Exception e) {
            log.warn("[StockReduceQueryTool] legacy runId={}: {}", request.getRunId(), e.toString());
            Map<String, Object> data = new LinkedHashMap<>();
            data.putAll(AiBusinessToolResponses.mockPayload(e.getMessage()));
            return ToolResult.builder()
                    .success(false)
                    .message(e.getMessage())
                    .data(AiBusinessToolResponses.envelope(name(), false, true, start, stop, dept, dis, data,
                            "查询异常：半真实 mock"))
                    .build();
        }
    }

    private static List<Map<String, Object>> topGoodsPayload(List<GbDistributerGoodsEntity> tops) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (tops == null) {
            return rows;
        }
        int lim = Math.min(tops.size(), 5);
        for (int i = 0; i < lim; i++) {
            GbDistributerGoodsEntity g = tops.get(i);
            if (g == null) {
                continue;
            }
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("name", g.getGbDgGoodsName());
            BigDecimal amt = g.getGoodsSubtotalTotal();
            if (amt == null) {
                amt = nz(null);
            }
            row.put("amount", amt);
            rows.add(row);
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    private static List<Long> longListArg(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Long> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Number n) {
                out.add(n.longValue());
            }
        }
        return out;
    }

    private static BigDecimal nz(Object v) {
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
}
