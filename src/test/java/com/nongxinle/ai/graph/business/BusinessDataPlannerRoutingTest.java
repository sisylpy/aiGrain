package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.core.AiWorkspaceMode;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.nongxinle.ai.trace.AiSseEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class BusinessDataPlannerRoutingTest {

    @Mock
    private AiSseEventPublisher publisher;

    private BusinessDataPlannerNode node;

    @BeforeEach
    void setUp() {
        node = new BusinessDataPlannerNode(publisher);
    }

    @Test
    void costPhrase_enablesCostPathAndFourTools() {
        AiRunState st = AiRunState.builder()
                .runId(1L)
                .workspaceMode(AiWorkspaceMode.BUSINESS_CHAT)
                .normalizedUserInput("帮我看本月成本怎么样")
                .resolvedQueryContext(costDiagnosisContext())
                .build();
        node.run(st);
        assertThat(st.isCostInsightPath()).isTrue();
        assertThat(st.isBusinessOverviewPath()).isFalse();
        assertThat(st.getDataPlanTools()).isEqualTo(AiBusinessToolIds.DEFAULT_COST_INSIGHT_TOOLS);
        assertThat(st.getDataPlanTools()).hasSize(4);
    }

    @Test
    void overviewPhrase_enablesOverviewPathWithoutClassicPlan() {
        AiRunState st = AiRunState.builder()
                .runId(2L)
                .workspaceMode(AiWorkspaceMode.BUSINESS_CHAT)
                .normalizedUserInput("这个月生意怎么样")
                .resolvedQueryContext(AiResolvedQueryContext.builder()
                        .effectiveIntentCode(AiResolvedQueryIntent.BUSINESS_OVERVIEW)
                        .effectivePathCode(AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW)
                        .build())
                .build();
        node.run(st);
        assertThat(st.isCostInsightPath()).isFalse();
        assertThat(st.isBusinessOverviewPath()).isTrue();
        assertThat(st.getDataPlanTools()).isEmpty();
    }

    @Test
    void storeManagerMonthlyOperationsPhrase_enablesOverviewPathWithoutClassicPlan() {
        AiRunState st = AiRunState.builder()
                .runId(22L)
                .workspaceMode(AiWorkspaceMode.BUSINESS_CHAT)
                .normalizedUserInput("这个月经营怎么样？")
                .resolvedQueryContext(AiResolvedQueryContext.builder()
                        .effectiveIntentCode(AiResolvedQueryIntent.BUSINESS_OVERVIEW)
                        .effectivePathCode(AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW)
                        .build())
                .build();
        node.run(st);
        assertThat(st.isBusinessOverviewPath()).isTrue();
        assertThat(st.getDataPlanTools()).isEmpty();
    }

    @Test
    void dishProfitPhrase_prioritizesDishProfitOverGenericCostInsight() {
        AiRunState st = AiRunState.builder()
                .runId(80L)
                .workspaceMode(AiWorkspaceMode.BUSINESS_CHAT)
                .normalizedUserInput("菜品毛利怎么样")
                .resolvedQueryContext(dishProfitContext())
                .build();
        node.run(st);
        assertThat(st.isDishProfitPath()).isTrue();
        assertThat(st.isCostInsightPath()).isFalse();
        assertThat(st.isBusinessOverviewPath()).isFalse();
        assertThat(st.getDataPlanTools()).containsExactly(AiBusinessToolIds.DISH_PROFIT_ANALYSIS);
    }

    @Test
    void bareMarginPhrase_stillUsesCostInsightPath() {
        AiRunState st = AiRunState.builder()
                .runId(81L)
                .workspaceMode(AiWorkspaceMode.BUSINESS_CHAT)
                .normalizedUserInput("本月毛利怎么样")
                .resolvedQueryContext(costDiagnosisContext())
                .build();
        node.run(st);
        assertThat(st.isDishProfitPath()).isFalse();
        assertThat(st.isCostInsightPath()).isTrue();
        assertThat(st.getDataPlanTools()).isEqualTo(AiBusinessToolIds.DEFAULT_COST_INSIGHT_TOOLS);
    }

    @Test
    void singleDishMarginPhrase_enablesDishProfitPath() {
        AiRunState st = AiRunState.builder()
                .runId(82L)
                .workspaceMode(AiWorkspaceMode.BUSINESS_CHAT)
                .normalizedUserInput("水煮鱼毛利怎么样")
                .resolvedQueryContext(dishProfitContext())
                .build();
        node.run(st);
        assertThat(st.isDishProfitPath()).isTrue();
        assertThat(st.getDataPlanTools()).containsExactly(AiBusinessToolIds.DISH_PROFIT_ANALYSIS);
    }

    @Test
    void profitOnDishesPhrase_enablesDishProfitPath() {
        AiRunState st = AiRunState.builder()
                .runId(83L)
                .workspaceMode(AiWorkspaceMode.BUSINESS_CHAT)
                .normalizedUserInput("哪些菜不赚钱")
                .resolvedQueryContext(dishProfitContext())
                .build();
        node.run(st);
        assertThat(st.isDishProfitPath()).isTrue();
    }

    private static AiResolvedQueryContext costDiagnosisContext() {
        return AiResolvedQueryContext.builder()
                .effectiveIntentCode(AiResolvedQueryIntent.COST_DIAGNOSIS)
                .effectivePathCode(AiResolvedQueryIntent.PATH_COST_DIAGNOSIS)
                .build();
    }

    private static AiResolvedQueryContext dishProfitContext() {
        return AiResolvedQueryContext.builder()
                .effectiveIntentCode(AiResolvedQueryIntent.DISH_PROFIT)
                .effectivePathCode(AiResolvedQueryIntent.PATH_DISH_PROFIT)
                .build();
    }
}
