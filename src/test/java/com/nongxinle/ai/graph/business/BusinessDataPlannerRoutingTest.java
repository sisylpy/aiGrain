package com.nongxinle.ai.graph.business;

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
    void costPhrase_enablesCostPathAndFiveTools() {
        AiRunState st = AiRunState.builder()
                .runId(1L)
                .workspaceMode(AiWorkspaceMode.BUSINESS_CHAT)
                .normalizedUserInput("帮我看本月成本怎么样")
                .build();
        node.run(st);
        assertThat(st.isCostInsightPath()).isTrue();
        assertThat(st.isBusinessOverviewPath()).isFalse();
        assertThat(st.getDataPlanTools()).isEqualTo(AiBusinessToolIds.DEFAULT_COST_INSIGHT_TOOLS);
    }

    @Test
    void overviewPhrase_enablesOverviewPathAndFourTools() {
        AiRunState st = AiRunState.builder()
                .runId(2L)
                .workspaceMode(AiWorkspaceMode.BUSINESS_CHAT)
                .normalizedUserInput("这个月生意怎么样")
                .build();
        node.run(st);
        assertThat(st.isCostInsightPath()).isFalse();
        assertThat(st.isBusinessOverviewPath()).isTrue();
        assertThat(st.getDataPlanTools()).isEqualTo(AiBusinessToolIds.DEFAULT_BUSINESS_OVERVIEW_TOOLS);
    }

    @Test
    void storeManagerMonthlyOperationsPhrase_enablesOverviewPathAndFourTools() {
        AiRunState st = AiRunState.builder()
                .runId(22L)
                .workspaceMode(AiWorkspaceMode.BUSINESS_CHAT)
                .normalizedUserInput("这个月经营怎么样？")
                .build();
        node.run(st);
        assertThat(st.isBusinessOverviewPath()).isTrue();
        assertThat(st.getDataPlanTools()).isEqualTo(AiBusinessToolIds.DEFAULT_BUSINESS_OVERVIEW_TOOLS);
    }

    @Test
    void dishProfitPhrase_prioritizesDishProfitOverGenericCostInsight() {
        AiRunState st = AiRunState.builder()
                .runId(80L)
                .workspaceMode(AiWorkspaceMode.BUSINESS_CHAT)
                .normalizedUserInput("菜品毛利怎么样")
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
                .build();
        node.run(st);
        assertThat(st.isDishProfitPath()).isFalse();
        assertThat(st.isCostInsightPath()).isTrue();
    }

    @Test
    void singleDishMarginPhrase_enablesDishProfitPath() {
        AiRunState st = AiRunState.builder()
                .runId(82L)
                .workspaceMode(AiWorkspaceMode.BUSINESS_CHAT)
                .normalizedUserInput("水煮鱼毛利怎么样")
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
                .build();
        node.run(st);
        assertThat(st.isDishProfitPath()).isTrue();
    }
}
