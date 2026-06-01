package com.nongxinle.ai.graph.business;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.gateway.LlmGateway;
import com.nongxinle.ai.gateway.LlmGatewayFailureMarker;
import com.nongxinle.ai.harness.BusinessOverviewDishSalesReasonAgentHarnessSupport;
import com.nongxinle.ai.prompt.AiPromptIds;
import com.nongxinle.ai.prompt.AiPromptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/** 营业额卡：菜品销量原因 Agent（只读 fact pack → 结构化 summary + items）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessOverviewDishSalesReasonAgent {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final AiPromptService aiPromptService;
    private final LlmGateway llmGateway;

    @Value("${ai.composer.business_overview_dish_sales_reason.enabled:true}")
    private boolean enabled;

    public String tryComposeSummary(Map<String, Object> factPack) {
        BusinessOverviewDishSalesReasonOutputGuard.ComposeResult result = tryCompose(factPack, null);
        return result != null ? result.summary() : null;
    }

    public String tryComposeSummary(Map<String, Object> factPack, AiRunState state) {
        BusinessOverviewDishSalesReasonOutputGuard.ComposeResult result = tryCompose(factPack, state);
        return result != null ? result.summary() : null;
    }

    public BusinessOverviewDishSalesReasonOutputGuard.ComposeResult tryCompose(
            Map<String, Object> factPack, AiRunState state) {
        LinkedHashMap<String, Object> harness =
                BusinessOverviewDishSalesReasonAgentHarnessSupport.newHarnessMap(enabled);
        if (!enabled) {
            BusinessOverviewDishSalesReasonAgentHarnessSupport.recordFailure(harness, "agent_disabled");
            BusinessOverviewDishSalesReasonAgentHarnessSupport.publish(state, harness);
            return null;
        }
        if (factPack == null || factPack.isEmpty()) {
            BusinessOverviewDishSalesReasonAgentHarnessSupport.recordFailure(harness, "fact_pack_empty");
            BusinessOverviewDishSalesReasonAgentHarnessSupport.publish(state, harness);
            return null;
        }
        BusinessOverviewDishSalesReasonAgentHarnessSupport.recordFactPackDiagnostics(harness, factPack);
        String systemPrompt;
        try {
            systemPrompt = aiPromptService.require(AiPromptIds.BUSINESS_OVERVIEW_DISH_SALES_REASON_AGENT_V2);
        } catch (Exception e) {
            log.warn("[BusinessOverviewDishSalesReasonAgent] prompt load failed: {}", e.getMessage());
            BusinessOverviewDishSalesReasonAgentHarnessSupport.recordFailure(
                    harness, "prompt_load_failed:" + e.getMessage());
            BusinessOverviewDishSalesReasonAgentHarnessSupport.publish(state, harness);
            return null;
        }
        String userMessage;
        try {
            userMessage = JSON.writeValueAsString(factPack);
        } catch (Exception e) {
            BusinessOverviewDishSalesReasonAgentHarnessSupport.recordFailure(
                    harness, "fact_pack_serialize_failed:" + e.getMessage());
            BusinessOverviewDishSalesReasonAgentHarnessSupport.publish(state, harness);
            return null;
        }
        BusinessOverviewDishSalesReasonAgentHarnessSupport.recordInputPreview(harness, systemPrompt, userMessage);
        try {
            String raw = llmGateway.chatSimple(systemPrompt, userMessage);
            BusinessOverviewDishSalesReasonAgentHarnessSupport.recordLlmOutputPreview(harness, raw);
            if (!StringUtils.hasText(raw) || LlmGatewayFailureMarker.isMarked(raw)) {
                BusinessOverviewDishSalesReasonAgentHarnessSupport.recordFailure(
                        harness,
                        LlmGatewayFailureMarker.isMarked(raw) ? "llm_gateway_failure" : "llm_empty_response");
                BusinessOverviewDishSalesReasonAgentHarnessSupport.publish(state, harness);
                return null;
            }
            BusinessOverviewDishSalesReasonOutputGuard.ComposeResult parsed =
                    BusinessOverviewDishSalesReasonOutputGuard.parseAndSanitize(raw, factPack);
            if (parsed == null || !StringUtils.hasText(parsed.summary())) {
                BusinessOverviewDishSalesReasonAgentHarnessSupport.recordFailure(harness, "llm_output_parse_failed");
                BusinessOverviewDishSalesReasonAgentHarnessSupport.publish(state, harness);
                return null;
            }
            BusinessOverviewDishSalesReasonAgentHarnessSupport.recordFinalSummary(harness, parsed.summary());
            BusinessOverviewDishSalesReasonAgentHarnessSupport.publish(state, harness);
            return parsed;
        } catch (Exception e) {
            log.warn("[BusinessOverviewDishSalesReasonAgent] llm failed: {}", e.getMessage());
            BusinessOverviewDishSalesReasonAgentHarnessSupport.recordFailure(
                    harness, "llm_exception:" + e.getMessage());
            BusinessOverviewDishSalesReasonAgentHarnessSupport.publish(state, harness);
            return null;
        }
    }
}
