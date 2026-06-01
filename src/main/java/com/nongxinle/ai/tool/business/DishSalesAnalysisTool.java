package com.nongxinle.ai.tool.business;

import com.nongxinle.ai.capability.dish.DishCostAnalysisCapabilityStatus;
import com.nongxinle.ai.capability.dish.DishSalesAnalysisCapabilityAdapter;
import com.nongxinle.ai.capability.dish.DishSalesAnalysisCapabilityRequest;
import com.nongxinle.ai.capability.dish.DishSalesAnalysisCapabilityResult;
import com.nongxinle.ai.tool.AiTool;
import com.nongxinle.ai.tool.ToolRequest;
import com.nongxinle.ai.tool.ToolResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_DISH_COST_FOOD_ID;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_DISH_NAME_FOCUS_HINT;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_END_DATE;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_SEARCH_DEP_ID;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_SORT_BY;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_SORT_ORDER;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_STOP_DATE;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_SUB_DEP_ID;

/**
 * 单菜销售分析 Tool；委托 {@link DishSalesAnalysisCapabilityAdapter}（与 depGeFoodBusiness 同源 Service）。
 */
@Component
@RequiredArgsConstructor
public class DishSalesAnalysisTool implements AiTool {

    private final DishSalesAnalysisCapabilityAdapter dishSalesAnalysisCapabilityAdapter;

    @Override
    public String name() {
        return AiBusinessToolIds.DISH_SALES_ANALYSIS_CARD;
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        DishProfitToolScopeSupport.BaseArgs base = DishProfitToolScopeSupport.parseBaseArgs(request);
        Map<String, Object> args = base.args();
        Long dept = base.departmentFatherId();
        Long disLong = base.disId();
        String start = base.startDate();
        String stop = firstNonBlank(base.stopDate(), str(args.get(ARG_END_DATE)));

        if (dept == null || disLong == null || !StringUtils.hasText(start) || !StringUtils.hasText(stop)) {
            Map<String, Object> data = Map.of("status", DishCostAnalysisCapabilityStatus.ERROR.name());
            return ToolResult.builder()
                    .success(false)
                    .message("missing_args")
                    .data(AiBusinessToolResponses.envelope(name(), false, false, start, stop, dept, disLong, data,
                            "参数不完整"))
                    .build();
        }

        DishProfitToolScopeSupport.ResolvedScope scope = DishProfitToolScopeSupport.resolveScope(disLong, dept, args);
        DishSalesAnalysisCapabilityRequest capReq = DishSalesAnalysisCapabilityRequest.builder()
                .startDate(start)
                .stopDate(stop)
                .disId(scope.disId())
                .depFatherId(scope.depFatherIdInt())
                .searchDepId(str(args.get(ARG_SEARCH_DEP_ID)))
                .subDepId(toInt(args.get(ARG_SUB_DEP_ID)))
                .sortBy(firstNonBlank(str(args.get(ARG_SORT_BY)), "sales"))
                .sortOrder(firstNonBlank(str(args.get(ARG_SORT_ORDER)), "desc"))
                .dishName(firstNonBlank(str(args.get(ARG_DISH_NAME_FOCUS_HINT)), str(args.get("dishName"))))
                .foodId(toInt(args.get(ARG_DISH_COST_FOOD_ID)))
                .build();

        DishSalesAnalysisCapabilityResult result = dishSalesAnalysisCapabilityAdapter.analyze(capReq);
        Map<String, Object> data = toToolData(result, request, start, stop);

        boolean toolSuccess = result.getStatus() != DishCostAnalysisCapabilityStatus.ERROR;
        String message = result.getStatus().name().toLowerCase(Locale.ROOT);
        return ToolResult.builder()
                .success(toolSuccess)
                .message(message)
                .data(AiBusinessToolResponses.envelope(
                        name(), toolSuccess, false, start, stop, scope.departmentFatherId(), disLong, data,
                        result.getMessage()))
                .build();
    }

    private static Map<String, Object> toToolData(
            DishSalesAnalysisCapabilityResult result, ToolRequest request, String start, String stop) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", result.getStatus() == null ? null : result.getStatus().name());
        data.put("reasonCode", result.getReasonCode());
        data.put("message", result.getMessage());
        data.put("dishId", result.getDishId());
        data.put("dishName", result.getDishName());
        data.put("salesPortions", result.getSalesPortions());
        data.put("salesAmount", result.getSalesAmount());
        data.put("salesUnitPrice", result.getSalesUnitPrice());
        data.put("ranking", result.getRanking());
        data.put("rawSalesRows", result.getRawSalesRows());
        data.put("candidates", result.getCandidates());
        data.put("rawReportSummary", result.getRawReportSummary());
        data.put("cardType", DishSalesAnalysisCapabilityResult.CARD_TYPE);
        Map<String, Object> cardPayload = ensureCardPayload(result, request, start, stop);
        data.put("cardPayload", cardPayload);
        data.put("needClarification", result.getStatus() == DishCostAnalysisCapabilityStatus.NEED_CLARIFICATION);
        return data;
    }

    private static Map<String, Object> ensureCardPayload(
            DishSalesAnalysisCapabilityResult result, ToolRequest request, String start, String stop) {
        if (result.getStatus() != DishCostAnalysisCapabilityStatus.SUCCESS
                || !StringUtils.hasText(result.getDishName())) {
            return result.getCardPayload();
        }
        String timeLabel = resolveTimeLabel(request, start, stop, result.getRawReportSummary());
        String scopeLabel = resolveScopeLabel(request);
        Map<String, Object> cardPayload = result.getCardPayload();
        if (cardPayload == null || cardPayload.isEmpty()) {
            cardPayload =
                    DishSalesAnalysisCapabilityResult.buildCardPayload(
                            result.getDishName(),
                            result.getDishId(),
                            result.getSalesPortions(),
                            result.getSalesAmount(),
                            result.getSalesUnitPrice(),
                            result.getRanking(),
                            timeLabel,
                            scopeLabel);
            return cardPayload;
        }
        enrichCardPayloadLabels(cardPayload, timeLabel, scopeLabel);
        return cardPayload;
    }

    @SuppressWarnings("unchecked")
    private static void enrichCardPayloadLabels(
            Map<String, Object> cardPayload, String timeLabel, String scopeLabel) {
        if (cardPayload == null) {
            return;
        }
        Object dataObj = cardPayload.get("data");
        if (!(dataObj instanceof Map<?, ?> dataMap)) {
            return;
        }
        Map<String, Object> data = (Map<String, Object>) dataMap;
        if (StringUtils.hasText(timeLabel) && !data.containsKey("timeLabel")) {
            data.put("timeLabel", timeLabel.trim());
        }
        if (StringUtils.hasText(scopeLabel) && !data.containsKey("scopeLabel")) {
            data.put("scopeLabel", scopeLabel.trim());
        }
        if (!cardPayload.containsKey("cardType") || cardPayload.get("cardType") == null) {
            cardPayload.put("cardType", DishSalesAnalysisCapabilityResult.CARD_TYPE);
        }
    }

    private static String resolveTimeLabel(
            ToolRequest request, String start, String stop, Map<String, Object> rawSummary) {
        if (rawSummary != null) {
            String fromSummary = formatDateRange(
                    str(rawSummary.get("startDate")), str(rawSummary.get("stopDate")));
            if (StringUtils.hasText(fromSummary)) {
                return fromSummary;
            }
        }
        return formatDateRange(start, stop);
    }

    private static String resolveScopeLabel(ToolRequest request) {
        if (request != null && request.getResolvedQueryContext() != null) {
            var rq = request.getResolvedQueryContext();
            if (StringUtils.hasText(rq.getQueryScopeBanner())) {
                return rq.getQueryScopeBanner().trim();
            }
        }
        return "当前查询范围";
    }

    private static String formatDateRange(String start, String stop) {
        if (!StringUtils.hasText(start) && !StringUtils.hasText(stop)) {
            return "";
        }
        if (StringUtils.hasText(start) && StringUtils.hasText(stop)) {
            if (start.trim().equals(stop.trim())) {
                return start.trim();
            }
            return start.trim() + " 至 " + stop.trim();
        }
        return StringUtils.hasText(start) ? start.trim() : stop.trim();
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString().trim();
    }

    private static String firstNonBlank(String a, String b) {
        if (StringUtils.hasText(a)) {
            return a.trim();
        }
        if (StringUtils.hasText(b)) {
            return b.trim();
        }
        return "";
    }

    private static Integer toInt(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.intValue();
        }
        String s = o.toString().trim();
        if (!StringUtils.hasText(s)) {
            return null;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
