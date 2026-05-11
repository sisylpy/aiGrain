package com.nongxinle.ai.tool.business;

import com.nongxinle.ai.tool.AiTool;
import com.nongxinle.ai.tool.ToolRequest;
import com.nongxinle.ai.tool.ToolResult;
import com.nongxinle.service.GbDistributerPurchaseGoodsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_DEPARTMENT_FATHER_ID;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_DIS_ID;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_PURCHASE_DEPARTMENT_ID;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_START_DATE;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_STOP_DATE;

/**
 * 采购额汇总：沿用采购商品 Mapper 口径；可按部门再继续收紧过滤（后续迭代）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseQueryTool implements AiTool {

    private final GbDistributerPurchaseGoodsService gbDistributerPurchaseGoodsService;

    @Override
    public String name() {
        return AiBusinessToolIds.PURCHASE_QUERY;
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        Map<String, Object> args = request.getArgs() == null ? Map.of() : request.getArgs();
        Long dept = toLong(args.get(ARG_DEPARTMENT_FATHER_ID));
        Long purDep = toLong(args.get(ARG_PURCHASE_DEPARTMENT_ID));
        Long disId = toLong(args.get(ARG_DIS_ID));
        String start = str(args.get(ARG_START_DATE));
        String stop = str(args.get(ARG_STOP_DATE));

        if (disId == null || start.isEmpty() || stop.isEmpty()) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("purchaseSubTotal", null);
            return ToolResult.builder()
                    .success(false)
                    .message("missing disId/date range")
                    .data(AiBusinessToolResponses.envelope(name(), false, false, start, stop, dept, disId, data,
                            "参数不完整"))
                    .build();
        }

        try {
            Map<String, Object> purMap = new HashMap<>(8);
            purMap.put("disId", disId.intValue());
            purMap.put("startDate", start);
            purMap.put("stopDate", stop);
            purMap.put("useStockFinishDate", Boolean.TRUE);
            if (purDep != null) {
                purMap.put("purDepId", purDep.intValue());
            }
            Integer cnt = gbDistributerPurchaseGoodsService.queryGbPurchaseGoodsCount(purMap);
            double subtotal = 0.0;
            double weightTotal = 0.0;
            boolean hasRows = cnt != null && cnt > 0;
            if (hasRows) {
                Double st = gbDistributerPurchaseGoodsService.queryPurchaseGoodsSubTotal(purMap);
                subtotal = st == null ? 0.0 : st;
                Double wt = gbDistributerPurchaseGoodsService.queryPurchaseGoodsWeightTotal(purMap);
                weightTotal = wt == null ? 0.0 : wt;
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("purchaseRowCount", cnt == null ? 0 : cnt);
            data.put("purchaseSubTotal", subtotal);
            data.put("purchaseWeightTotal", weightTotal);
            return ToolResult.builder()
                    .success(true)
                    .message(hasRows ? "ok" : "empty")
                    .data(AiBusinessToolResponses.envelope(name(), true, false, start, stop, dept, disId, data, null))
                    .build();
        } catch (Exception e) {
            log.warn("[PurchaseQueryTool] runId={} dis={}: {}", request.getRunId(), disId, e.toString());
            Map<String, Object> data = new LinkedHashMap<>();
            data.putAll(AiBusinessToolResponses.mockPayload(e.getMessage()));
            return ToolResult.builder()
                    .success(false)
                    .message(e.getMessage())
                    .data(AiBusinessToolResponses.envelope(name(), false, true, start, stop, dept, disId, data,
                            "查询异常：半真实 mock"))
                    .build();
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
