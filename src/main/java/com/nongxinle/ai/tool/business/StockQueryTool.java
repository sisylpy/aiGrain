package com.nongxinle.ai.tool.business;

import com.nongxinle.ai.tool.AiTool;
import com.nongxinle.ai.tool.ToolRequest;
import com.nongxinle.ai.tool.ToolResult;
import com.nongxinle.service.GbDepartmentGoodsStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_DEPARTMENT_FATHER_ID;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_DIS_ID;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_START_DATE;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_STOP_DATE;

/**
 * 库房库存快照：当前剩余汇总 + 查询区间内入库批次金额/重量（与 {@link GbDepartmentGoodsStockMapper} 口径一致）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockQueryTool implements AiTool {

    private final GbDepartmentGoodsStockService gbDepartmentGoodsStockService;

    @Override
    public String name() {
        return AiBusinessToolIds.STOCK_QUERY;
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        Map<String, Object> args = request.getArgs() == null ? Map.of() : request.getArgs();
        Long dept = toLong(args.get(ARG_DEPARTMENT_FATHER_ID));
        Long disId = toLong(args.get(ARG_DIS_ID));
        String start = str(args.get(ARG_START_DATE));
        String stop = str(args.get(ARG_STOP_DATE));

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
            Map<String, Object> snap = new HashMap<>(6);
            snap.put("depFatherId", dept.intValue());
            snap.put("disId", disId.intValue());

            Integer stockRowCount = gbDepartmentGoodsStockService.queryGoodsStockCount(snap);
            double restSubtotal = nzD(gbDepartmentGoodsStockService.queryDepGoodsRestTotal(snap));
            double restWeight = nzD(gbDepartmentGoodsStockService.queryDepGoodsRestWeightTotal(snap));

            Map<String, Object> period = new HashMap<>(snap);
            period.put("startDate", start);
            period.put("stopDate", stop);
            double periodInboundSubtotal = nzD(gbDepartmentGoodsStockService.queryDepGoodsSubtotal(period));
            double periodInboundWeight = nzD(gbDepartmentGoodsStockService.queryDepStockWeightTotal(period));

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("stockBatchRowCount", stockRowCount == null ? 0 : stockRowCount);
            data.put("stockRestSubtotal", restSubtotal);
            data.put("stockRestWeightTotal", restWeight);
            data.put("periodInboundSubtotal", periodInboundSubtotal);
            data.put("periodInboundWeightTotal", periodInboundWeight);

            boolean hasAny = (stockRowCount != null && stockRowCount > 0)
                    || restSubtotal > 0
                    || restWeight > 0
                    || periodInboundSubtotal > 0
                    || periodInboundWeight > 0;

            return ToolResult.builder()
                    .success(true)
                    .message(hasAny ? "ok" : "empty")
                    .data(AiBusinessToolResponses.envelope(name(), true, false, start, stop, dept, disId, data, null))
                    .build();
        } catch (Exception e) {
            log.warn("[StockQueryTool] runId={} dep={}: {}", request.getRunId(), dept, e.toString());
            Map<String, Object> data = new LinkedHashMap<>();
            data.putAll(AiBusinessToolResponses.mockPayload(e.getMessage()));
            return ToolResult.builder()
                    .success(false)
                    .message(e.getMessage())
                    .data(AiBusinessToolResponses.envelope(name(), false, true, start, stop, dept, disId, data,
                            "查询异常"))
                    .build();
        }
    }

    private static double nzD(Double v) {
        return v == null ? 0.0 : v;
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
