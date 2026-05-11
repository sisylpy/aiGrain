package com.nongxinle.ai.graph.business;

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
class BusinessDataPlannerGroupStockOverviewTest {

    @Mock
    private AiSseEventPublisher publisher;

    private BusinessDataPlannerNode node;

    @BeforeEach
    void setUp() {
        node = new BusinessDataPlannerNode(publisher);
    }

    @Test
    void groupManager_inventoryQuestion_routesToGroupWarehouseStockAggregationPath() {
        AiUserContext ctx = AiUserContext.builder()
                .roleCode(AiRoleCodes.GROUP_MANAGER)
                .permissions(new ArrayList<>(AiRoleMapper.permissionsForAiRole(AiRoleCodes.GROUP_MANAGER)))
                .build();
        AiRunState st = AiRunState.builder()
                .runId(501L)
                .workspaceMode(AiWorkspaceMode.BUSINESS_CHAT)
                .normalizedUserInput("现在库存怎么样？")
                .aiUserContext(ctx)
                .build();

        node.run(st);

        assertThat(st.isWarehouseStockOverviewPath()).isTrue();
        assertThat(st.isGroupWarehouseStockOverview()).isTrue();
        assertThat(st.isBusinessOverviewPath()).isFalse();
        assertThat(st.isCostInsightPath()).isFalse();
        assertThat(st.getDataPlanTools()).containsExactly(AiBusinessToolIds.WAREHOUSE_STOCK_OVERVIEW);
        assertThat(st.getIntentConvergence()).containsEntry("from", "STOCK_INQUIRY");
        assertThat(st.getIntentConvergence()).containsEntry("to", "GROUP_WAREHOUSE_STOCK_OVERVIEW");
        assertThat(st.getScopeConvergenceNote()).contains("集团");
    }

    @Test
    void storeManager_inventoryQuestion_routesToStoreScopedWarehouseOverview() {
        AiUserContext ctx = AiUserContext.builder()
                .roleCode(AiRoleCodes.STORE_MANAGER)
                .permissions(new ArrayList<>(AiRoleMapper.permissionsForAiRole(AiRoleCodes.STORE_MANAGER)))
                .build();
        AiRunState st = AiRunState.builder()
                .runId(502L)
                .workspaceMode(AiWorkspaceMode.BUSINESS_CHAT)
                .normalizedUserInput("库存情况怎么样")
                .aiUserContext(ctx)
                .build();

        node.run(st);

        assertThat(st.isWarehouseStockOverviewPath()).isTrue();
        assertThat(st.isGroupWarehouseStockOverview()).isFalse();
        assertThat(st.getIntentConvergence()).containsEntry("to", "STORE_WAREHOUSE_STOCK_OVERVIEW");
        assertThat(st.getDataPlanTools()).containsExactly(AiBusinessToolIds.WAREHOUSE_STOCK_OVERVIEW);
    }
}
