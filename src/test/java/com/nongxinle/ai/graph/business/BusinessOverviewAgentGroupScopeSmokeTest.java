package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiDepartmentUserTestRows;
import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiUserContextResolver;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.core.AiWorkspaceMode;
import com.nongxinle.ai.platform.dto.AiRunCreateRequest;
import com.nongxinle.ai.scope.AiQueryScope;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.nongxinle.ai.trace.AiSseEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GROUP 广角成功返回时 overviewScope / 文案不可回落为门店子树口径。
 */
@ExtendWith(MockitoExtension.class)
class BusinessOverviewAgentGroupScopeSmokeTest {

    @Mock
    AiSseEventPublisher publisher;

    @Test
    void groupManager_rollUp_ok_overview_scope_is_group_and_banner_not_single_store_warning() {
        AiUserContextResolver ur = AiDepartmentUserTestRows.resolverReturning(
                AiDepartmentUserTestRows.groupManager(801, 1, 88));

        AiRunCreateRequest rq = new AiRunCreateRequest();
        rq.setUserId(801L);
        rq.setDepartmentId(1L);
        rq.setDistributerId(88L);

        var uc = ur.resolve(rq);

        var b = AiRunState.builder()
                .runId(9L)
                .userId(801L)
                .conversationId(null)
                .departmentId(1L)
                .distributerId(88L)
                .workspaceMode(AiWorkspaceMode.BUSINESS_CHAT)
                .businessOverviewPath(true)
                .dataPlanTools(new ArrayList<>(AiBusinessToolIds.DEFAULT_BUSINESS_OVERVIEW_TOOLS))
                .statStartDate("2026-05-01")
                .statEndDate("2026-05-10")
                .aiUserContext(uc)
                .resolvedQueryContext(AiResolvedQueryContext.builder()
                        .orgScope(AiResolvedOrgScope.builder()
                                .scopeType(AiResolvedOrgScope.SCOPE_GROUP)
                                .distributerId(88L)
                                .requestDepartmentId(1L)
                                .currentDepartmentId(uc.getDepartmentId())
                                .build())
                        .build())
                .scope(AiQueryScope.builder()
                        .parentStoreCount(2)
                        .resolvedDepartmentIds(List.of(101, 102))
                        .build());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stats", Map.of(
                "统计天数", 2,
                "总营业额", new BigDecimal("854"),
                "日均营业额", new BigDecimal("427"),
                "日均订单数", new BigDecimal("5"),
                "客单价", "85.4",
                "平台费合计", new BigDecimal("30"),
                "外卖营业额合计", new BigDecimal("20"),
                "退款合计", BigDecimal.ZERO,
                "利润率", BigDecimal.ZERO,
                "盈亏状态", "-"
        ));
        data.put("rollupMeta", Map.of(
                "aggregationMode", "GROUP_SQL_ROLLUP",
                "visibleDepartmentNodeCount", 2,
                "dataAvailableRecordingDepartmentCount", 2,
                "dataMissingVisibleNodeApprox", 0,
                "fallbackSingleAnchorOnly", false
        ));

        LinkedHashMap<String, Object> boEnv = new LinkedHashMap<>();
        boEnv.put("success", Boolean.TRUE);
        boEnv.put("data", data);

        Map<String, Object> purchases = Map.of(
                "purchaseSubTotal", new BigDecimal("3303"));

        AiRunState st = b.toolResults(toolResultsMinimal(boEnv, purchases)).build();

        BusinessOverviewAgentNode node = new BusinessOverviewAgentNode(publisher);
        node.run(st);

        var ov = st.getBusinessOverviewResult();
        assertThat(ov).isNotNull();
        assertThat(ov.getOverviewScope()).isNotEmpty();
        assertThat(ov.getOverviewScope()).containsEntry("scopeType", "GROUP");

        Object banner = ov.getOverviewScope().get("primaryBanner");
        assertThat(banner).isInstanceOf(String.class);

        assertThat(((String) banner)).contains("集团范围");
        assertThat(((String) banner)).doesNotContain("【查询范围】");
        assertThat(((String) banner)).doesNotContain("所选组织子树");
        assertThat(((String) banner)).doesNotContain("全集团相加");

        Object coverage = ov.getOverviewScope().get("coverageDetail");
        assertThat(coverage).isInstanceOf(String.class);
        assertThat(((String) coverage)).doesNotContain("登记");
        assertThat(((String) coverage)).doesNotContain("主体");
        assertThat(((String) coverage)).doesNotContain("节点");
    }

    @Test
    void storeRole_rollUpAbsent_overview_banner_is_storeSubtree() {
        AiUserContextResolver ur = AiDepartmentUserTestRows.resolverReturning(
                AiDepartmentUserTestRows.storeManager(AiDepartmentUserTestRows.STORE_MANAGER_TEST_USER_PK, 100, 2));
        AiRunCreateRequest rq = new AiRunCreateRequest();
        rq.setUserId((long) AiDepartmentUserTestRows.STORE_MANAGER_TEST_USER_PK);
        rq.setDepartmentId(100L);
        rq.setDistributerId(2L);
        var uc = ur.resolve(rq);

        var base = AiRunState.builder()
                .runId(10L)
                .userId((long) AiDepartmentUserTestRows.STORE_MANAGER_TEST_USER_PK)
                .departmentId(100L)
                .distributerId(2L)
                .workspaceMode(AiWorkspaceMode.BUSINESS_CHAT)
                .businessOverviewPath(true)
                .dataPlanTools(new ArrayList<>(AiBusinessToolIds.DEFAULT_BUSINESS_OVERVIEW_TOOLS))
                .statStartDate("2026-05-01")
                .statEndDate("2026-05-10")
                .aiUserContext(uc)
                .resolvedQueryContext(AiResolvedQueryContext.builder()
                        .orgScope(AiResolvedOrgScope.builder()
                                .scopeType(AiResolvedOrgScope.SCOPE_STORE)
                                .distributerId(2L)
                                .requestDepartmentId(100L)
                                .currentDepartmentId(100L)
                                .build())
                        .build());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stats", Map.of(
                "统计天数", 2,
                "总营业额", new BigDecimal("500"),
                "日均营业额", new BigDecimal("250"),
                "日均订单数", BigDecimal.ZERO,
                "客单价", "0",
                "平台费合计", BigDecimal.ZERO,
                "外卖营业额合计", BigDecimal.ZERO,
                "退款合计", BigDecimal.ZERO,
                "利润率", BigDecimal.ZERO,
                "盈亏状态", "-"));

        LinkedHashMap<String, Object> boEnv = new LinkedHashMap<>();
        boEnv.put("success", Boolean.TRUE);
        boEnv.put("data", data);

        AiRunState st = base.toolResults(toolResultsMinimal(boEnv, Map.of("purchaseSubTotal", BigDecimal.ZERO))).build();

        BusinessOverviewAgentNode node = new BusinessOverviewAgentNode(publisher);
        node.run(st);

        var ov = st.getBusinessOverviewResult();
        Object banner = ov.getOverviewScope().get("primaryBanner");
        assertThat(ov.getOverviewScope()).containsEntry("scopeType", "STORE");
        assertThat(((String) banner)).contains("当前门店");
    }

    private static Map<String, Object> toolResultsMinimal(Map<String, Object> businessOverviewEnvelope,
            Map<String, Object> purchases) {
        LinkedHashMap<String, Object> tr = new LinkedHashMap<>();

        LinkedHashMap<String, Object> dish = new LinkedHashMap<>();
        dish.put("success", Boolean.TRUE);
        dish.put("data", Map.of("listPriceRevenueTotal", BigDecimal.ZERO));

        LinkedHashMap<String, Object> gm = new LinkedHashMap<>();
        gm.put("success", Boolean.FALSE);
        gm.put("data", Map.of());

        LinkedHashMap<String, Object> purEnv = new LinkedHashMap<>();
        purEnv.put("success", Boolean.TRUE);
        purEnv.put("data", purchases);

        tr.put(AiBusinessToolIds.BUSINESS_OVERVIEW_QUERY, businessOverviewEnvelope);
        tr.put(AiBusinessToolIds.PURCHASE_QUERY, purEnv);
        tr.put(AiBusinessToolIds.DISH_SALES_QUERY, dish);
        tr.put(AiBusinessToolIds.GROSS_MARGIN_CALCULATOR, gm);
        return tr;
    }
}
