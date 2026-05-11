package com.nongxinle.ai.tool.business;

import com.nongxinle.ai.tool.AiTool;
import com.nongxinle.ai.tool.ToolRequest;
import com.nongxinle.ai.tool.ToolResult;
import com.nongxinle.service.GbAiDailyRevenueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_DEPARTMENT_FATHER_ID;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_START_DATE;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_STOP_DATE;

@Slf4j
@Component
@RequiredArgsConstructor
public class RevenueQueryTool implements AiTool {

    private final GbAiDailyRevenueService gbAiDailyRevenueService;

    @Override
    public String name() {
        return AiBusinessToolIds.REVENUE_QUERY;
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        Map<String, Object> args = request.getArgs() == null ? Map.of() : request.getArgs();
        Long dept = toLongObj(args.get(ARG_DEPARTMENT_FATHER_ID));
        String start = str(args.get(ARG_START_DATE));
        String stop = str(args.get(ARG_STOP_DATE));

        if (dept == null || start.isEmpty() || stop.isEmpty()) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("totalRevenue", null);
            return ToolResult.builder()
                    .success(false)
                    .message("missing departmentFatherId/startDate/stopDate")
                    .data(AiBusinessToolResponses.envelope(name(), false, false, start, stop, dept, null, data,
                            "参数不完整"))
                    .build();
        }

        try {
            Map<String, Object> stats = gbAiDailyRevenueService.getStatsByDepartmentId(dept, start, stop);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("rawStats", stats);
            Number daysNum = stats.get("days") instanceof Number ? (Number) stats.get("days") : null;
            int days = daysNum == null ? 0 : Math.max(daysNum.intValue(), 0);
            BigDecimal totalRevenue = nz(stats.get("total_revenue"));
            data.put("days", days);
            data.put("totalRevenue", totalRevenue);
            data.put("avgDailyRevenue",
                    days > 0 ? totalRevenue.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
            boolean mock = stats == null || stats.isEmpty();
            return ToolResult.builder()
                    .success(true)
                    .message(mock ? "no_rows" : "ok")
                    .data(AiBusinessToolResponses.envelope(name(), true, mock, start, stop, dept, null, data, null))
                    .build();
        } catch (Exception e) {
            log.warn("[RevenueQueryTool] runId={} dept={} range={}..{}: {}",
                    request.getRunId(), dept, start, stop, e.toString(), e);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("errorCode", "query_failed");
            data.putAll(AiBusinessToolResponses.mockPayload(e.getMessage()));
            return ToolResult.builder()
                    .success(false)
                    .message(e.getMessage())
                    .data(AiBusinessToolResponses.envelope(name(), false, true, start, stop, dept, null, data,
                            "查询异常：半真实 mock"))
                    .build();
        }
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

    private static Long toLongObj(Object v) {
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
