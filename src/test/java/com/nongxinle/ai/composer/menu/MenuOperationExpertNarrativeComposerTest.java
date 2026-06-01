package com.nongxinle.ai.composer.menu;

import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuOptimizationDishItem;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuOptimizationPlan;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuOptimizationPriorityGroup;
import com.nongxinle.ai.gateway.LlmGateway;
import com.nongxinle.ai.prompt.AiPromptIds;
import com.nongxinle.ai.prompt.AiPromptRegistry;
import com.nongxinle.ai.prompt.AiPromptService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MenuOperationExpertNarrativeComposerTest {

    @Mock
    private AiPromptService aiPromptService;

    @Mock
    private LlmGateway llmGateway;

    @Mock
    private AiPromptRegistry aiPromptRegistry;

    @InjectMocks
    private MenuOperationExpertNarrativeComposer composer;

    @Test
    void emptyLlmResponse_fallsBack() {
        ReflectionTestUtils.setField(composer, "enabled", true);
        MenuOperationAnswerPlan plan = samplePlan();
        when(aiPromptRegistry.resolveClasspathRelativePath(AiPromptIds.COMPOSER_MENU_EXPERT_RUNTIME_V1))
                .thenReturn("ai-prompts/semantic/menu-expert-runtime-prompt.md");
        when(aiPromptService.require(AiPromptIds.COMPOSER_MENU_EXPERT_RUNTIME_V1)).thenReturn("system");
        when(llmGateway.chatSimple(eq("system"), anyString())).thenReturn("");

        AiRunState state = AiRunState.builder().runId(1L).build();
        MenuExpertPresentationComposeResult result = composer.tryComposePresentation(state, plan);

        assertFalse(result.isAccepted());
        assertNotNull(state.getMenuExpertPromptPreview());
        assertNotNull(state.getMenuExpertComposerDecision());
        assertEquals(true, state.getMenuExpertComposerDecision().get("fallbackUsed"));
        assertEquals("empty_llm_response", state.getMenuExpertComposerDecision().get("rejectedReason"));
    }

    @Test
    void acceptedPresentationPlan_setsStateAndShortPreview() {
        ReflectionTestUtils.setField(composer, "enabled", true);
        MenuOperationAnswerPlan plan = samplePlanWithDish();
        String json =
                """
                {
                  "mainSummary": "这次菜单优化的重点，是先处理畅销但毛利偏低的引流菜，同时稳住已有利润菜。",
                  "keyFindings": ["酸奶碗销量高但毛利率偏低，需要先复核成本"],
                  "focusSections": [
                    {
                      "sectionTitle": "优先处理",
                      "sectionSummary": "先复核酸奶碗的成本与定价",
                      "dishes": [
                        {
                          "dishName": "酸奶碗",
                          "blendedGrossMarginRateOnListPrice": "18%",
                          "actualProfitAmount": "1200",
                          "suggestedAction": "复核成本",
                          "reason": "畅销但毛利偏低"
                        }
                      ],
                      "suggestedAction": "先复核成本",
                      "reason": "优先处理引流菜"
                    }
                  ],
                  "nextSteps": ["先复核「酸奶碗」的成本结构、标准用量与损耗"],
                  "capabilityBoundaryZh": "当前版本暂不提供最新采购价。"
                }
                """;
        when(aiPromptRegistry.resolveClasspathRelativePath(AiPromptIds.COMPOSER_MENU_EXPERT_RUNTIME_V1))
                .thenReturn("ai-prompts/semantic/menu-expert-runtime-prompt.md");
        when(aiPromptService.require(AiPromptIds.COMPOSER_MENU_EXPERT_RUNTIME_V1)).thenReturn("system");
        when(llmGateway.chatSimple(eq("system"), anyString())).thenReturn(json);

        AiRunState state =
                AiRunState.builder()
                        .runId(2L)
                        .normalizedUserInput("菜单怎么优化？")
                        .toolResults(MenuExpertNarrativeFactPackTestSupport.stateWithYogurtBowlRow().getToolResults())
                        .build();
        MenuExpertPresentationComposeResult result = composer.tryComposePresentation(state, plan);

        assertTrue(result.isAccepted());
        assertNotNull(result.getPresentationPlan());
        assertNotNull(result.getAnswerPreview());
        assertNotNull(state.getMenuExpertPresentationPlan());
        assertEquals(AiPromptIds.COMPOSER_MENU_EXPERT_RUNTIME_V1, state.getComposerPromptRegistryId());
        assertEquals("accepted", state.getMenuExpertComposerDecision().get("outputGuardResult"));
        assertEquals(false, state.getMenuExpertComposerDecision().get("fallbackUsed"));
        assertEquals(
                "llm_expert_presentation",
                state.getMenuExpertComposerDecision().get("finalAnswerSource"));
        assertNotNull(state.getMenuExpertLlmOutputPreview().get("presentationPlanParsed"));
    }

    @Test
    void nonActionRecommendationPlan_isSkipped() {
        ReflectionTestUtils.setField(composer, "enabled", true);
        MenuOperationAnswerPlan plan =
                MenuOperationAnswerPlan.builder()
                        .planType(MenuOperationAnswerPlan.TYPE_MENU_OPERATION_OVERVIEW)
                        .menuOptimizationPlan(MenuOptimizationPlan.builder().optimizationSummary("x").build())
                        .build();

        MenuExpertPresentationComposeResult result =
                composer.tryComposePresentation(AiRunState.builder().runId(3L).build(), plan);
        assertFalse(result.isAccepted());
    }

    private static MenuOperationAnswerPlan samplePlan() {
        return MenuOperationAnswerPlan.builder()
                .planType(MenuOperationAnswerPlan.TYPE_MENU_ACTION_RECOMMENDATION)
                .scopeLabel("集团")
                .timeLabel("2026年4月")
                .menuOptimizationPlan(
                        MenuOptimizationPlan.builder()
                                .optimizationSummary("先复核引流菜。")
                                .nextSteps(List.of("先复核「酸奶碗」的成本结构、标准用量与损耗"))
                                .build())
                .build();
    }

    private static MenuOperationAnswerPlan samplePlanWithDish() {
        return MenuOperationAnswerPlan.builder()
                .planType(MenuOperationAnswerPlan.TYPE_MENU_ACTION_RECOMMENDATION)
                .scopeLabel("集团")
                .timeLabel("2026年4月")
                .menuOptimizationPlan(
                        MenuOptimizationPlan.builder()
                                .optimizationSummary("先复核引流菜。")
                                .nextSteps(List.of("先复核「酸奶碗」的成本结构、标准用量与损耗"))
                                .priorityGroups(
                                        List.of(
                                                MenuOptimizationPriorityGroup.builder()
                                                        .groupName("优先处理")
                                                        .priority(1)
                                                        .dishes(
                                                                List.of(
                                                                        MenuOptimizationDishItem.builder()
                                                                                .dishName("酸奶碗")
                                                                                .blendedGrossMarginRateOnListPrice("18%")
                                                                                .actualProfitAmount("1200")
                                                                                .suggestedActionLabel("复核成本")
                                                                                .reason("畅销但毛利偏低")
                                                                                .build()))
                                                        .build()))
                                .build())
                .build();
    }
}
