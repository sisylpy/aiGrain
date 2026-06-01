package com.nongxinle.controller;

import com.nongxinle.ai.capability.dish.DishCostAnalysisCapabilityAdapter;
import com.nongxinle.ai.capability.dish.DishCostAnalysisCapabilityRequest;
import com.nongxinle.ai.capability.dish.DishCostAnalysisCapabilityResult;
import com.nongxinle.utils.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 本地验收入口：直连 {@link DishCostAnalysisCapabilityAdapter}，不走 LLM / AnswerPlan / Composer。
 * <p>默认关闭，需 {@code ai.debug.dish-cost-analysis-enabled=true}。</p>
 */
@RestController
@RequestMapping("ai/debug")
@Tag(name = "AI Debug（开发验收）")
@RequiredArgsConstructor
public class AiDishCostAnalysisDebugController {

    private final DishCostAnalysisCapabilityAdapter dishCostAnalysisCapabilityAdapter;

    @Value("${ai.debug.dish-cost-analysis-enabled:true}")
    private boolean probeEnabled;

    @PostMapping(value = "dish-cost-analysis", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Probe 菜品成本分析 Capability",
            description = "调用 DishCostAnalysisCapabilityAdapter → GbDishCostAnalysisService#buildIngredientAnalysisReport。"
                    + " 需 ai.debug.dish-cost-analysis-enabled=true；仅 local / 内网。")
    public R dishCostAnalysis(@RequestBody DishCostAnalysisCapabilityRequest body) {
        if (!probeEnabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "dish-cost-analysis probe disabled");
        }
        DishCostAnalysisCapabilityResult result = dishCostAnalysisCapabilityAdapter.analyze(body);
        return R.ok().put("data", toProbeJson(result));
    }

    static Map<String, Object> toProbeJson(DishCostAnalysisCapabilityResult result) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (result == null) {
            return m;
        }
        m.put("status", result.getStatus() == null ? null : result.getStatus().name());
        m.put("reasonCode", result.getReasonCode());
        m.put("message", result.getMessage());
        m.put("dishId", result.getDishId());
        m.put("dishName", result.getDishName());
        m.put("salesPortions", result.getSalesPortions());
        m.put("salesAmount", result.getSalesAmount());
        m.put("salesUnitPrice", result.getSalesUnitPrice());
        m.put("theoryCostPerPortion", result.getTheoryCostPerPortion());
        m.put("actualCostPerPortion", result.getActualCostPerPortion());
        m.put("actualCostAmount", result.getActualCostAmount());
        m.put("diffCostPerPortion", result.getDiffCostPerPortion());
        m.put("ingredientRows", result.getIngredientRows());
        m.put("candidates", result.getCandidates());
        m.put("rawReportSummary", result.getRawReportSummary());
        m.put("cardPayload", result.getCardPayload());
        Object cardType = null;
        if (result.getCardPayload() != null) {
            cardType = result.getCardPayload().get("cardType");
        }
        m.put("cardType", cardType);
        return m;
    }
}
