package com.nongxinle.ai.composer.menu;

import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan;
import com.nongxinle.ai.gateway.LlmGateway;
import com.nongxinle.ai.gateway.LlmGatewayFailureMarker;
import com.nongxinle.ai.prompt.AiPromptIds;
import com.nongxinle.ai.prompt.AiPromptRegistry;
import com.nongxinle.ai.prompt.AiPromptService;
import com.nongxinle.ai.security.AiAnswerBoundary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 菜单优化方案（{@link MenuOperationAnswerPlan#TYPE_MENU_ACTION_RECOMMENDATION}）的 LLM 展示计划层。
 * 基于 {@code menuFactPack} 统计事实生成 {@link MenuExpertPresentationPlan}，校验通过后写入 cards[]。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MenuOperationExpertNarrativeComposer {

    private final AiPromptService aiPromptService;
    private final LlmGateway llmGateway;
    private final AiPromptRegistry aiPromptRegistry;

    @Value("${ai.composer.menu_expert_narrative.enabled:true}")
    private boolean enabled;

    /**
     * @return 校验通过的展示计划；失败时 {@code accepted=false}，由调用方回退 deterministic card。
     */
    public MenuExpertPresentationComposeResult tryComposePresentation(
            AiRunState state, MenuOperationAnswerPlan plan) {
        if (plan == null
                || !MenuOperationAnswerPlan.TYPE_MENU_ACTION_RECOMMENDATION.equals(plan.getPlanType())
                || plan.getMenuOptimizationPlan() == null) {
            return MenuExpertPresentationComposeResult.builder().accepted(false).build();
        }

        String promptId = AiPromptIds.COMPOSER_MENU_EXPERT_RUNTIME_V1;
        String promptPath = MenuOperationExpertNarrativePromptPreviewSupport.defaultPromptPath(aiPromptRegistry);
        Map<String, Object> inputEnvelope = MenuOperationExpertNarrativeInputBuilder.buildInputEnvelope(state, plan);
        String userMessage = MenuOperationExpertNarrativeInputBuilder.buildUserMessage(state, plan);

        String systemPrompt;
        try {
            systemPrompt = aiPromptService.require(promptId);
        } catch (Exception e) {
            recordDebug(
                    state,
                    inputEnvelope,
                    promptId,
                    promptPath,
                    null,
                    userMessage,
                    null,
                    null,
                    null,
                    null,
                    enabled,
                    false,
                    true,
                    "skipped",
                    "prompt_load_failed: " + e.getMessage(),
                    MenuOperationExpertNarrativePromptPreviewSupport.FINAL_ANSWER_SOURCE_DETERMINISTIC);
            return MenuExpertPresentationComposeResult.builder().accepted(false).build();
        }

        recordInputPreview(state, promptId, promptPath, systemPrompt, userMessage, inputEnvelope);

        if (!enabled) {
            recordDecision(
                    state,
                    false,
                    false,
                    true,
                    "skipped",
                    "composer_disabled",
                    MenuOperationExpertNarrativePromptPreviewSupport.FINAL_ANSWER_SOURCE_DETERMINISTIC);
            return MenuExpertPresentationComposeResult.builder().accepted(false).build();
        }

        try {
            String raw = llmGateway.chatSimple(systemPrompt, userMessage);
            String rawObs = raw == null ? "" : raw;
            if (LlmGatewayFailureMarker.isMarked(rawObs)) {
                recordDebug(
                        state,
                        inputEnvelope,
                        promptId,
                        promptPath,
                        systemPrompt,
                        userMessage,
                        rawObs,
                        null,
                        null,
                        null,
                        true,
                        true,
                        true,
                        "rejected",
                        "llm_gateway_unavailable",
                        MenuOperationExpertNarrativePromptPreviewSupport.FINAL_ANSWER_SOURCE_DETERMINISTIC);
                return MenuExpertPresentationComposeResult.builder().accepted(false).build();
            }
            if (!StringUtils.hasText(rawObs)) {
                recordDebug(
                        state,
                        inputEnvelope,
                        promptId,
                        promptPath,
                        systemPrompt,
                        userMessage,
                        rawObs,
                        null,
                        null,
                        null,
                        true,
                        true,
                        true,
                        "rejected",
                        "empty_llm_response",
                        MenuOperationExpertNarrativePromptPreviewSupport.FINAL_ANSWER_SOURCE_DETERMINISTIC);
                return MenuExpertPresentationComposeResult.builder().accepted(false).build();
            }

            MenuExpertPresentationPlanJsonParser.ParseResult parsed =
                    MenuExpertPresentationPlanJsonParser.parse(rawObs);
            if (!parsed.success() || parsed.plan() == null) {
                recordDebug(
                        state,
                        inputEnvelope,
                        promptId,
                        promptPath,
                        systemPrompt,
                        userMessage,
                        rawObs,
                        null,
                        null,
                        null,
                        true,
                        true,
                        true,
                        "rejected",
                        parsed.errorCode(),
                        MenuOperationExpertNarrativePromptPreviewSupport.FINAL_ANSWER_SOURCE_DETERMINISTIC);
                return MenuExpertPresentationComposeResult.builder().accepted(false).build();
            }

            String guardReason = MenuExpertPresentationPlanGuard.validate(parsed.plan(), state, plan);
            if (guardReason != null) {
                log.info(
                        "[MenuExpertPresentation] guard rejected (runId={}, reason={})",
                        state != null ? state.getRunId() : null,
                        guardReason);
                recordDebug(
                        state,
                        inputEnvelope,
                        promptId,
                        promptPath,
                        systemPrompt,
                        userMessage,
                        rawObs,
                        parsed.normalizedJson(),
                        null,
                        guardReason,
                        true,
                        true,
                        true,
                        "rejected",
                        guardReason,
                        MenuOperationExpertNarrativePromptPreviewSupport.FINAL_ANSWER_SOURCE_DETERMINISTIC);
                return MenuExpertPresentationComposeResult.builder().accepted(false).build();
            }

            MenuExpertPresentationPlan presentation = parsed.plan();
            String answerPreview =
                    MenuOperationCardCompanionAnswerPreviewSupport.composeActionRecommendationHint();
            recordDebug(
                    state,
                    inputEnvelope,
                    promptId,
                    promptPath,
                    systemPrompt,
                    userMessage,
                    rawObs,
                    parsed.normalizedJson(),
                    presentation,
                    null,
                    true,
                    true,
                    false,
                    "accepted",
                    null,
                    MenuOperationExpertNarrativePromptPreviewSupport.FINAL_ANSWER_SOURCE_LLM_PRESENTATION);
            if (state != null) {
                state.setComposerPromptRegistryId(promptId);
                state.setMenuExpertPresentationPlan(presentation);
            }
            return MenuExpertPresentationComposeResult.builder()
                    .accepted(true)
                    .presentationPlan(presentation)
                    .answerPreview(AiAnswerBoundary.stripDeveloperFacingLeakage(answerPreview))
                    .build();
        } catch (Exception e) {
            log.warn(
                    "[MenuExpertPresentation] compose failed, fallback to deterministic card (runId={}): {}",
                    state != null ? state.getRunId() : null,
                    e.toString());
            recordDebug(
                    state,
                    inputEnvelope,
                    promptId,
                    promptPath,
                    systemPrompt,
                    userMessage,
                    null,
                    null,
                    null,
                    null,
                    true,
                    false,
                    true,
                    "rejected",
                    "compose_exception: " + e.getMessage(),
                    MenuOperationExpertNarrativePromptPreviewSupport.FINAL_ANSWER_SOURCE_DETERMINISTIC);
            return MenuExpertPresentationComposeResult.builder().accepted(false).build();
        }
    }

    private static void recordDebug(
            AiRunState state,
            Map<String, Object> inputEnvelope,
            String promptId,
            String promptPath,
            String systemPrompt,
            String userMessage,
            String rawResponse,
            String normalizedJson,
            MenuExpertPresentationPlan parsedPlan,
            String guardRejectedReason,
            boolean enabledFlag,
            boolean llmUsed,
            boolean fallbackUsed,
            String outputGuardResult,
            String rejectedReason,
            String finalAnswerSource) {
        recordInputPreview(state, promptId, promptPath, systemPrompt, userMessage, inputEnvelope);
        recordOutput(state, rawResponse, normalizedJson, parsedPlan, guardRejectedReason);
        recordDecision(state, enabledFlag, llmUsed, fallbackUsed, outputGuardResult, rejectedReason, finalAnswerSource);
    }

    private static void recordInputPreview(
            AiRunState state,
            String promptId,
            String promptPath,
            String systemPrompt,
            String userMessage,
            Map<String, Object> inputEnvelope) {
        if (state == null) {
            return;
        }
        state.setMenuExpertPromptPreview(
                MenuOperationExpertNarrativePromptPreviewSupport.buildInputPreview(
                        promptId, promptPath, systemPrompt, userMessage, inputEnvelope));
    }

    private static void recordOutput(
            AiRunState state,
            String rawResponse,
            String normalizedJson,
            MenuExpertPresentationPlan parsedPlan,
            String guardRejectedReason) {
        if (state == null) {
            return;
        }
        Map<String, Object> preview =
                MenuOperationExpertNarrativePromptPreviewSupport.buildOutputPreview(rawResponse, normalizedJson);
        if (parsedPlan != null) {
            preview.put("presentationPlanParsed", parsedPlan.toCardPayloadMap());
        }
        if (StringUtils.hasText(guardRejectedReason)) {
            preview.put("guardRejectedReason", guardRejectedReason.trim());
        }
        state.setMenuExpertLlmOutputPreview(preview);
    }

    private static void recordDecision(
            AiRunState state,
            boolean enabledFlag,
            boolean llmUsed,
            boolean fallbackUsed,
            String outputGuardResult,
            String rejectedReason,
            String finalAnswerSource) {
        if (state == null) {
            return;
        }
        state.setMenuExpertComposerDecision(
                MenuOperationExpertNarrativePromptPreviewSupport.buildDecisionPreview(
                        enabledFlag,
                        llmUsed,
                        fallbackUsed,
                        outputGuardResult,
                        rejectedReason,
                        finalAnswerSource));
    }
}
