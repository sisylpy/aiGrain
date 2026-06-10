package com.nongxinle.ai.tool.business;

import com.nongxinle.ai.graph.business.PurchaseGoodsBusinessAnalysisSupport;
import com.nongxinle.ai.tool.AiTool;
import com.nongxinle.ai.tool.ToolRequest;
import com.nongxinle.ai.tool.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_DEPARTMENT_FATHER_ID;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_DIS_ID;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_START_DATE;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_STOP_DATE;

/**
 * GOODS 锚点原料采购经营分析（独立 Tool，不调用 {@link PurchaseOverviewTool#execute}）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseGoodsBusinessAnalysisTool implements AiTool {

    private final PurchaseGoodsBusinessAnalysisSupport support;

    @Override
    public String name() {
        return AiBusinessToolIds.PURCHASE_GOODS_BUSINESS_ANALYSIS;
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        Map<String, Object> args = request.getArgs() == null ? Map.of() : request.getArgs();
        Long disLong = toLong(args.get(ARG_DIS_ID));
        if (disLong == null) {
            return fail("missing_dis_id", args);
        }
        String start = str(args.get(ARG_START_DATE));
        String stop = str(args.get(ARG_STOP_DATE));
        LinkedHashMap<String, Object> debug = new LinkedHashMap<>();
        try {
            var rq = request.getResolvedQueryContext();
            Map<String, Object> core = support.buildPayload(null, rq, args, debug);
            LinkedHashMap<String, Object> data = new LinkedHashMap<>();
            data.put(PurchaseGoodsBusinessAnalysisSupport.PAYLOAD_KEY, core);
            data.put("debug", debug);
            boolean ok = "OK".equalsIgnoreCase(str(core.get("status")));
            return ToolResult.builder()
                    .success(ok)
                    .message(ok ? "ok" : str(core.get("failureReason")))
                    .data(
                            AiBusinessToolResponses.envelope(
                                    name(),
                                    ok,
                                    false,
                                    start,
                                    stop,
                                    toLong(args.get(ARG_DEPARTMENT_FATHER_ID)),
                                    disLong,
                                    data,
                                    ok ? null : str(core.get("failureReason"))))
                    .build();
        } catch (Exception ex) {
            log.warn("[PurchaseGoodsBusinessAnalysisTool] runId={} {}", request.getRunId(), ex.toString());
            return ToolResult.builder()
                    .success(false)
                    .message(ex.getMessage())
                    .data(
                            AiBusinessToolResponses.envelope(
                                    name(),
                                    false,
                                    false,
                                    start,
                                    stop,
                                    toLong(args.get(ARG_DEPARTMENT_FATHER_ID)),
                                    disLong,
                                    Map.of("error", ex.getMessage()),
                                    "查询异常"))
                    .build();
        }
    }

    private static ToolResult fail(String msg, Map<String, Object> args) {
        return ToolResult.builder().success(false).message(msg).data(Map.of("args", args)).build();
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
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString().trim();
    }
}
