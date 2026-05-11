package com.nongxinle.ai.tool.business;

import com.nongxinle.ai.tool.AiTool;
import com.nongxinle.ai.tool.ToolRequest;
import com.nongxinle.ai.tool.ToolResult;
import com.nongxinle.service.GbDepFoodBusinessInsightService;
import com.nongxinle.utils.GbDepartmentGoodsStockReduceSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_DEPARTMENT_FATHER_ID;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_DIS_ID;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_START_DATE;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_STOP_DATE;

@Slf4j
@Component
@RequiredArgsConstructor
public class DishSalesQueryTool implements AiTool {

    private final GbDepFoodBusinessInsightService depFoodBusinessInsightService;

    @Override
    public String name() {
        return AiBusinessToolIds.DISH_SALES_QUERY;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ToolResult execute(ToolRequest request) {
        Map<String, Object> args = request.getArgs() == null ? Map.of() : request.getArgs();
        Long dept = toLong(args.get(ARG_DEPARTMENT_FATHER_ID));
        Long disLong = toLong(args.get(ARG_DIS_ID));
        String start = str(args.get(ARG_START_DATE));
        String stop = str(args.get(ARG_STOP_DATE));

        if (dept == null || disLong == null || start.isEmpty() || stop.isEmpty()) {
            Map<String, Object> data = new LinkedHashMap<>();
            return ToolResult.builder()
                    .success(false)
                    .message("missing disId/departmentFatherId/dates")
                    .data(AiBusinessToolResponses.envelope(name(), false, false, start, stop, dept, disLong, data,
                            "参数不完整"))
                    .build();
        }

        int disId = disLong.intValue();
        int depFatherId = dept.intValue();

        try {
            Map<String, Object> insight = depFoodBusinessInsightService.buildInsight(disId, depFatherId, start, stop, null);
            List<Map<String, Object>> dishes = (List<Map<String, Object>>) insight.get("dishes");
            int dishLines = dishes == null ? 0 : dishes.size();
            BigDecimal listRev = BigDecimal.ZERO;
            BigDecimal qty = BigDecimal.ZERO;
            if (dishes != null) {
                for (Map<String, Object> row : dishes) {
                    listRev = listRev.add(GbDepartmentGoodsStockReduceSupport.coerceDecimal(row.get("listPriceRevenue")));
                    qty = qty.add(GbDepartmentGoodsStockReduceSupport.coerceDecimal(row.get("soldPortionsTotal")));
                }
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("dishLineCount", dishLines);
            data.put("listPriceRevenueTotal", listRev.stripTrailingZeros().toPlainString());
            data.put("soldPortionsTotal", qty.stripTrailingZeros().toPlainString());
            data.put("scopeOutboundSubtotals", insight.get("scopeOutboundSubtotals"));
            boolean mock = dishLines == 0;
            return ToolResult.builder()
                    .success(true)
                    .message(mock ? "empty_dishes" : "ok")
                    .data(AiBusinessToolResponses.envelope(name(), true, mock, start, stop, dept, disLong, data,
                            mock ? "无菜品洞察行，可能未维护菜品或未发生销量" : null))
                    .build();
        } catch (Exception e) {
            log.warn("[DishSalesQueryTool] runId={}: {}", request.getRunId(), e.toString());
            Map<String, Object> data = new LinkedHashMap<>();
            data.putAll(AiBusinessToolResponses.mockPayload(e.getMessage()));
            return ToolResult.builder()
                    .success(false)
                    .message(e.getMessage())
                    .data(AiBusinessToolResponses.envelope(name(), false, true, start, stop, dept, disLong, data,
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
