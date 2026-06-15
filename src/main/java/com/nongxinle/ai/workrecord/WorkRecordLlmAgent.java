package com.nongxinle.ai.workrecord;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nongxinle.ai.gateway.LlmGateway;
import com.nongxinle.ai.gateway.LlmGatewayFailureMarker;
import com.nongxinle.ai.prompt.AiPromptIds;
import com.nongxinle.ai.prompt.AiPromptService;
import com.nongxinle.entity.GbWorkRecordCategoryEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkRecordLlmAgent {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final AiPromptService aiPromptService;
    private final LlmGateway llmGateway;

    public AgentResult polishAndClassify(
            String rawContent,
            String storeName,
            List<GbWorkRecordCategoryEntity> activeCategories) {

        if (!StringUtils.hasText(rawContent)) {
            return AgentResult.failure("raw_content_empty", null);
        }
        String systemPrompt;
        try {
            systemPrompt = aiPromptService.require(AiPromptIds.WORK_RECORD_POLISH_CLASSIFY_V1);
        } catch (Exception e) {
            log.warn("[WorkRecordLlmAgent] prompt load failed: {}", e.getMessage());
            return AgentResult.failure("prompt_load_failed", null);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("rawContent", rawContent.trim());
        Map<String, Object> storeContext = new LinkedHashMap<>();
        if (StringUtils.hasText(storeName)) {
            storeContext.put("storeName", storeName);
        }
        payload.put("storeContext", storeContext);
        payload.put(
                "categories",
                activeCategories.stream().map(this::toCategoryPayload).collect(Collectors.toList()));

        String userMessage;
        try {
            userMessage = JSON.writeValueAsString(payload);
        } catch (Exception e) {
            return AgentResult.failure("payload_serialize_failed", null);
        }

        try {
            String raw = llmGateway.chatSimple(systemPrompt, userMessage);
            if (!StringUtils.hasText(raw) || LlmGatewayFailureMarker.isMarked(raw)) {
                return AgentResult.failure(
                        LlmGatewayFailureMarker.isMarked(raw) ? "llm_gateway_failure" : "llm_empty_response",
                        raw);
            }
            WorkRecordLlmJsonParser.ParseResult parsed = WorkRecordLlmJsonParser.parse(raw);
            if (!parsed.ok() || parsed.value() == null) {
                return AgentResult.failure(
                        parsed.errorCode() != null ? parsed.errorCode() : "llm_output_parse_failed", raw);
            }
            if (StringUtils.hasText(parsed.value().getProtocolWarning())) {
                log.warn(
                        "[WorkRecordLlmAgent] protocol warning (transitional): {}",
                        parsed.value().getProtocolWarning());
            }
            if (StringUtils.hasText(parsed.value().getPolishMode())) {
                log.debug("[WorkRecordLlmAgent] polishMode={}", parsed.value().getPolishMode());
            }
            return AgentResult.success(parsed.value(), raw, userMessage, systemPrompt);
        } catch (Exception e) {
            log.warn("[WorkRecordLlmAgent] llm failed: {}", e.getMessage());
            return AgentResult.failure("llm_exception:" + e.getMessage(), null);
        }
    }

    private Map<String, Object> toCategoryPayload(GbWorkRecordCategoryEntity c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("categoryId", c.getGbWrcId());
        m.put("categoryCode", c.getGbWrcCode());
        m.put("categoryName", c.getGbWrcName());
        m.put("description", c.getGbWrcDescription());
        return m;
    }

    public record AgentResult(
            boolean ok,
            WorkRecordLlmResult llmResult,
            String rawLlmResponse,
            String userPayload,
            String systemPrompt,
            String errorCode) {

        public static AgentResult success(
                WorkRecordLlmResult llmResult,
                String rawLlmResponse,
                String userPayload,
                String systemPrompt) {
            return new AgentResult(true, llmResult, rawLlmResponse, userPayload, systemPrompt, null);
        }

        public static AgentResult failure(String errorCode, String rawLlmResponse) {
            return new AgentResult(false, null, rawLlmResponse, null, null, errorCode);
        }
    }
}
