package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.context.AiUserContext;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.core.AiWorkspaceMode;
import com.nongxinle.ai.mapping.AiRoleMapper;
import com.nongxinle.ai.security.AiRoleCodes;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.nongxinle.ai.trace.AiSseEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class BusinessDataPlannerStorePurchaserBusinessOverviewTest {

    @Mock
    private AiSseEventPublisher publisher;

    private BusinessDataPlannerNode node;

    @BeforeEach
    void setUp() {
        node = new BusinessDataPlannerNode(publisher);
    }

    @Test
    void storePurchaser_monthBusinessQuestion_convergesToPurchaseOverview() {
        AiUserContext ctx = AiUserContext.builder()
                .roleCode(AiRoleCodes.STORE_PURCHASER)
                .permissions(new ArrayList<>(AiRoleMapper.permissionsForAiRole(AiRoleCodes.STORE_PURCHASER)))
                .build();
        AiRunState st = AiRunState.builder()
                .runId(20L)
                .workspaceMode(AiWorkspaceMode.BUSINESS_CHAT)
                .normalizedUserInput("这个月经营怎么样？")
                .aiUserContext(ctx)
                .build();

        node.run(st);

        assertThat(st.isBusinessOverviewPath()).isFalse();
        assertThat(st.isPurchaseCostInsightPath()).isTrue();
        assertThat(st.isCostInsightPath()).isFalse();
        assertThat(st.getDataPlanTools()).isEqualTo(AiBusinessToolIds.DEFAULT_PURCHASE_COST_INSIGHT_TOOLS);
        assertThat(st.getIntentConvergence()).containsEntry("from", "BUSINESS_OVERVIEW");
        assertThat(st.getIntentConvergence()).containsEntry("to", "PURCHASE_OVERVIEW");
        assertThat(st.getCostIntentConvergenceNote()).contains("门店采购角色");
    }

    @Test
    void storeManager_sameQuestion_keepsBusinessOverviewPathWithoutClassicPlan() {
        AiUserContext ctx = AiUserContext.builder()
                .roleCode(AiRoleCodes.STORE_MANAGER)
                .permissions(new ArrayList<>(AiRoleMapper.permissionsForAiRole(AiRoleCodes.STORE_MANAGER)))
                .build();
        AiRunState st = AiRunState.builder()
                .runId(21L)
                .workspaceMode(AiWorkspaceMode.BUSINESS_CHAT)
                .normalizedUserInput("这个月经营怎么样？")
                .aiUserContext(ctx)
                .resolvedQueryContext(AiResolvedQueryContext.builder()
                        .effectiveIntentCode(AiResolvedQueryIntent.BUSINESS_OVERVIEW)
                        .effectivePathCode(AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW)
                        .build())
                .build();

        node.run(st);

        assertThat(st.isBusinessOverviewPath()).isTrue();
        assertThat(st.isPurchaseCostInsightPath()).isFalse();
        assertThat(st.getDataPlanTools()).isEmpty();
        assertThat(st.getIntentConvergence()).isNull();
    }

    @Test
    void storePurchaser_noPurchasePermission_stillRecordsConvergence_andDenial() {
        AiUserContext ctx = AiUserContext.builder()
                .roleCode(AiRoleCodes.STORE_PURCHASER)
                .permissions(new ArrayList<>())
                .build();
        AiRunState st = AiRunState.builder()
                .runId(22L)
                .workspaceMode(AiWorkspaceMode.BUSINESS_CHAT)
                .normalizedUserInput("本月经营怎么样")
                .aiUserContext(ctx)
                .build();

        node.run(st);

        assertThat(st.isPurchaseCostInsightPath()).isFalse();
        assertThat(st.getDataPlanTools()).isEmpty();
        assertThat(st.getIntentConvergence()).containsEntry("to", "PURCHASE_OVERVIEW");
        assertThat(st.getPermissionDenials()).isNotEmpty();
    }
}
