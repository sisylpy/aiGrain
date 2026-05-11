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
class BusinessDataPlannerCostIntentBranchTest {

    @Mock
    private AiSseEventPublisher publisher;

    private BusinessDataPlannerNode node;

    @BeforeEach
    void setUp() {
        node = new BusinessDataPlannerNode(publisher);
    }

    @Test
    void groupManager_costQuestion_fullInsightPath_andFiveTools() {
        AiUserContext ctx = AiUserContext.builder()
                .roleCode(AiRoleCodes.GROUP_MANAGER)
                .permissions(new ArrayList<>(AiRoleMapper.permissionsForAiRole(AiRoleCodes.GROUP_MANAGER)))
                .build();
        AiRunState st = AiRunState.builder()
                .runId(1L)
                .workspaceMode(AiWorkspaceMode.BUSINESS_CHAT)
                .normalizedUserInput("本月成本怎么样？")
                .aiUserContext(ctx)
                .build();
        node.run(st);

        assertThat(st.isCostInsightPath()).isTrue();
        assertThat(st.isPurchaseCostInsightPath()).isFalse();
        assertThat(st.isCouponCostInsightBlocked()).isFalse();
        assertThat(st.getDataPlanTools()).isEqualTo(AiBusinessToolIds.DEFAULT_COST_INSIGHT_TOOLS);
        assertThat(st.getScopeConvergenceNote()).isNull();
    }

    @Test
    void storeManager_costQuestion_sameFullPath_butGroupWordingAddsNote() {
        AiUserContext ctx = AiUserContext.builder()
                .roleCode(AiRoleCodes.STORE_MANAGER)
                .permissions(new ArrayList<>(AiRoleMapper.permissionsForAiRole(AiRoleCodes.STORE_MANAGER)))
                .build();
        AiRunState st = AiRunState.builder()
                .runId(2L)
                .workspaceMode(AiWorkspaceMode.BUSINESS_CHAT)
                .normalizedUserInput("集团成本怎么样")
                .aiUserContext(ctx)
                .build();

        node.run(st);

        assertThat(st.isCostInsightPath()).isTrue();
        assertThat(st.getScopeConvergenceNote())
                .contains("本门店");
    }

    @Test
    void storePurchaser_costQuestion_purchasePath_onlyPurchaseTools() {
        AiUserContext ctx = AiUserContext.builder()
                .roleCode(AiRoleCodes.STORE_PURCHASER)
                .permissions(new ArrayList<>(AiRoleMapper.permissionsForAiRole(AiRoleCodes.STORE_PURCHASER)))
                .build();
        AiRunState st = AiRunState.builder()
                .runId(3L)
                .workspaceMode(AiWorkspaceMode.BUSINESS_CHAT)
                .normalizedUserInput("本月采购成本咋样")
                .aiUserContext(ctx)
                .build();

        node.run(st);

        assertThat(st.isPurchaseCostInsightPath()).isTrue();
        assertThat(st.isCostInsightPath()).isFalse();
        assertThat(st.getDataPlanTools()).isEqualTo(AiBusinessToolIds.DEFAULT_PURCHASE_COST_INSIGHT_TOOLS);
        assertThat(st.getCostIntentConvergenceNote()).contains("采购角色");
    }

    @Test
    void couponOperator_costQuestion_blocked() {
        AiUserContext ctx = AiUserContext.builder()
                .roleCode(AiRoleCodes.COUPON_OPERATOR)
                .permissions(new ArrayList<>(AiRoleMapper.permissionsForAiRole(AiRoleCodes.COUPON_OPERATOR)))
                .build();
        AiRunState st = AiRunState.builder()
                .runId(4L)
                .workspaceMode(AiWorkspaceMode.BUSINESS_CHAT)
                .normalizedUserInput("这个月成本还好吗")
                .aiUserContext(ctx)
                .build();

        node.run(st);

        assertThat(st.isCouponCostInsightBlocked()).isTrue();
        assertThat(st.getDataPlanTools()).isEmpty();
        assertThat(st.getPermissionDenials()).hasSize(1);
        assertThat(st.getPermissionDenials().get(0).getReason()).contains("没有查看成本分析");
    }
}
