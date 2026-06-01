package com.nongxinle.ai.tool.business;

import com.nongxinle.ai.capability.dish.DishCostAnalysisCapabilityAdapter;
import com.nongxinle.ai.capability.dish.DishCostAnalysisCapabilityRequest;
import com.nongxinle.ai.capability.dish.DishCostAnalysisCapabilityResult;
import com.nongxinle.ai.capability.dish.DishCostAnalysisCapabilityStatus;
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
 * P1：AI 菜品成本分析 Tool；仅委托 {@link DishCostAnalysisCapabilityAdapter}，不直连 Mapper / SQL。
 */
@Component
@RequiredArgsConstructor
public class DishCostAnalysisTool implements AiTool {

    private final DishCostAnalysisCapabilityAdapter dishCostAnalysisCapabilityAdapter;

    @Override
    public String name() {
        return AiBusinessToolIds.DISH_COST_ANALYSIS;
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
        DishCostAnalysisCapabilityRequest capReq = DishCostAnalysisCapabilityRequest.builder()
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

        DishCostAnalysisCapabilityResult result = dishCostAnalysisCapabilityAdapter.analyze(capReq);
        Map<String, Object> data = toToolData(result);

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

    private static Map<String, Object> toToolData(DishCostAnalysisCapabilityResult result) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", result.getStatus() == null ? null : result.getStatus().name());
        data.put("reasonCode", result.getReasonCode());
        data.put("message", result.getMessage());
        data.put("dishId", result.getDishId());
        data.put("dishName", result.getDishName());
        data.put("salesPortions", result.getSalesPortions());
        data.put("salesAmount", result.getSalesAmount());
        data.put("salesUnitPrice", result.getSalesUnitPrice());
        data.put("theoryCostPerPortion", result.getTheoryCostPerPortion());
        data.put("actualCostPerPortion", result.getActualCostPerPortion());
        data.put("actualCostAmount", result.getActualCostAmount());
        data.put("diffCostPerPortion", result.getDiffCostPerPortion());
        data.put("ingredientRows", result.getIngredientRows());
        data.put("bottle", result.getBottle());
        data.put("candidates", result.getCandidates());
        data.put("rawReportSummary", result.getRawReportSummary());
        data.put("cardPayload", result.getCardPayload());
        data.put("needClarification", result.getStatus() == DishCostAnalysisCapabilityStatus.NEED_CLARIFICATION);
        return data;
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
