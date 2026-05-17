package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.composer.payload.AnswerComposerPayloadFactory;
import com.nongxinle.ai.composer.renderer.DeterministicAnswerRenderer;
import com.nongxinle.ai.context.AiDepartmentUserTestRows;
import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiUserContext;
import com.nongxinle.ai.context.AiUserContextResolver;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.core.AiWorkspaceMode;
import com.nongxinle.ai.dto.business.AiGroupOverviewStoreBrief;
import com.nongxinle.ai.dto.business.AiBusinessOverviewResult;
import com.nongxinle.ai.platform.dto.AiRunCreateRequest;
import com.nongxinle.ai.scope.AiQueryScope;
import com.nongxinle.ai.prompt.AiPromptRegistry;
import com.nongxinle.ai.prompt.AiPromptService;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.nongxinle.ai.trace.AiSseEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.DefaultResourceLoader;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GROUP_MANAGER_APP +「这个月经营怎么样」：stub LLM 确定性 fallback，锁住集团话术与金额平面格式。
 */
@ExtendWith(MockitoExtension.class)
class GroupManagerBusinessOverviewAnswerFormatRegressionTest {

    @Mock
    AiSseEventPublisher publisher;

    private static AiPromptService testPromptService() {
        return new AiPromptService(new DefaultResourceLoader(), new AiPromptRegistry());
    }

    @Test
    void groupManager_monthOverview_blankLlm_answerUsesGroupScopePlainNumbersNoSciNotation() {
        AiUserContextResolver ur = AiDepartmentUserTestRows.resolverReturning(
                AiDepartmentUserTestRows.groupManager(91001, 1, 88));
        AiRunCreateRequest rq = new AiRunCreateRequest();
        rq.setUserId(91001L);
        rq.setDepartmentId(1L);
        rq.setDistributerId(88L);
        var uc = ur.resolve(rq);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("统计天数", 2);
        stats.put("总营业额", new BigDecimal("854"));
        stats.put("日均营业额", new BigDecimal("427"));
        stats.put("日均订单数", Double.valueOf(5.0));
        stats.put("客单价", "85.4");
        stats.put("平台费合计", Double.valueOf(30.0));
        stats.put("外卖营业额合计", Double.valueOf(20.0));
        stats.put("退款合计", BigDecimal.ZERO);
        stats.put("利润率", BigDecimal.ZERO);
        stats.put("盈亏状态", "不适用");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stats", stats);
        data.put("rollupMeta", Map.of(
                "aggregationMode", "GROUP_SQL_ROLLUP",
                "visibleDepartmentNodeCount", 1,
                "dataAvailableRecordingDepartmentCount", 1,
                "dataMissingVisibleNodeApprox", 0,
                "fallbackSingleAnchorOnly", false,
                "visibleStoreCount", 1,
                "storeWithRevenueCount", 1,
                "storeMissingRevenueCount", 0
        ));
        data.put("coveredStores", List.of(sampleCoveredStoreRow("验收门店")));

        LinkedHashMap<String, Object> boEnv = new LinkedHashMap<>();
        boEnv.put("success", Boolean.TRUE);
        boEnv.put("data", data);

        AiRunState st = AiRunState.builder()
                .runId(777L)
                .userId(91001L)
                .departmentId(1L)
                .distributerId(88L)
                .normalizedUserInput("这个月经营怎么样？")
                .workspaceMode(AiWorkspaceMode.BUSINESS_CHAT)
                .businessOverviewPath(true)
                .dataPlanTools(new ArrayList<>(AiBusinessToolIds.DEFAULT_BUSINESS_OVERVIEW_TOOLS))
                .statStartDate("2026-05-01")
                .statEndDate("2026-05-10")
                .aiUserContext(uc)
                .resolvedQueryContext(groupResolved(uc, rq))
                .scope(AiQueryScope.builder()
                        .parentStoreCount(1)
                        .resolvedDepartmentIds(List.of(101))
                        .build())
                .toolResults(toolResultsMinimal(boEnv, Map.of("purchaseSubTotal", new BigDecimal("3303"))))
                .build();

        new BusinessOverviewAgentNode(publisher).aggregateIfApplicable(st);

        AiBusinessOverviewResult ov = st.getBusinessOverviewResult();
        assertThat(ov).isNotNull();
        assertThat(ov.getOverviewScope()).containsEntry("scopeType", "GROUP");
        assertThat(ov.getOverviewScope()).containsKey("coveredStores");
        assertThat(ov.getCoveredStores()).hasSize(1);
        assertThat(new BigDecimal(ov.getDashboardStatsCn().get("总营业额").toString())).isEqualByComparingTo("854");
        assertThat(ov.getKeyMetrics().stream().anyMatch(m ->
                "总营业额".equals(m.get("name"))
                        && new BigDecimal(m.get("value").toString()).compareTo(new BigDecimal("854")) == 0))
                .isTrue();

        StubAnswerComposerNode composer = new StubAnswerComposerNode((s, u) -> "", publisher, testPromptService(), new AnswerComposerPayloadFactory(),
                DeterministicAnswerRenderer.createStandalone());
        composer.run(st);

        String answer = st.getFinalAnswerText();
        assertThat(answer).contains("集团范围");
        assertThat(answer).doesNotContain("本月目前统计");
        assertThat(answer).contains("所选区间");
        assertThat(answer).contains("2026-05-01");
        assertThat(answer).doesNotContain("子树口径");
        assertThat(answer).doesNotContain("登记口径");
        assertThat(answer).doesNotContain("父级网点");
        assertThat(answer).doesNotContain("主体");
        assertThat(answer).doesNotContain("节点");
        assertThat(answer).doesNotContain("3E+");
        assertThat(answer).doesNotContain("2E+");
        assertThat(answer.toUpperCase()).doesNotContain("E+1");
        assertThat(answer).contains("854");
        assertThat(answer).contains("本次参与统计的门店：");
        assertThat(answer).contains("验收门店");
        assertThat(answer).contains(AiGroupOverviewStoreBrief.noIssuesLine());
        assertThat(answer).doesNotContain("当前未识别到需要单独点名处理的门店");
    }

    @Test
    void groupManager_whenToolListsIssueStores_answerContainsNamedPriorityBlock() {
        AiUserContextResolver ur = AiDepartmentUserTestRows.resolverReturning(
                AiDepartmentUserTestRows.groupManager(91002, 1, 88));
        AiRunCreateRequest rq = new AiRunCreateRequest();
        rq.setUserId(91002L);
        rq.setDepartmentId(1L);
        rq.setDistributerId(88L);
        var uc = ur.resolve(rq);

        Map<String, Object> stats = baseStatsForGroup();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stats", stats);
        LinkedHashMap<String, Object> rm = new LinkedHashMap<>();
        rm.put("aggregationMode", "GROUP_SQL_ROLLUP");
        rm.put("visibleDepartmentNodeCount", 5);
        rm.put("dataAvailableRecordingDepartmentCount", 2);
        rm.put("dataMissingVisibleNodeApprox", 0);
        rm.put("fallbackSingleAnchorOnly", false);
        rm.put("visibleStoreCount", 2);
        rm.put("storeWithRevenueCount", 1);
        rm.put("storeMissingRevenueCount", 1);
        data.put("rollupMeta", rm);
        data.put("visibleStores", List.of(
                Map.of("storeName", "朝阳店"),
                Map.of("storeName", "望京店")));
        data.put("dataMissingStores", List.of(Map.of(
                "storeName", "朝阳店",
                "reason", "暂无日营收记录")));
        data.put("attentionStores", List.of(Map.of(
                "storeName", "望京店",
                "reason", "日均订单数偏低",
                "riskLevel", "warning")));
        data.put("coveredStores", List.of(sampleCoveredStoreRow("望京店")));

        LinkedHashMap<String, Object> boEnv = new LinkedHashMap<>();
        boEnv.put("success", Boolean.TRUE);
        boEnv.put("data", data);

        AiRunState st = baseRunState(uc, rq, boEnv);

        new BusinessOverviewAgentNode(publisher).aggregateIfApplicable(st);

        AiBusinessOverviewResult ov = st.getBusinessOverviewResult();
        assertThat(ov.getPriorityStoresBrief()).startsWith("需要优先关注的门店：");
        assertThat(ov.getPriorityStoresBrief()).contains("朝阳店");
        assertThat(ov.getOverviewScope()).containsKeys("dataMissingStores", "attentionStores", "coveredStores", "visibleStores");

        StubAnswerComposerNode composer = new StubAnswerComposerNode((s, u) -> "", publisher, testPromptService(), new AnswerComposerPayloadFactory(),
                DeterministicAnswerRenderer.createStandalone());
        composer.run(st);

        String answer = st.getFinalAnswerText();
        assertThat(answer).contains("需要优先关注的门店：");
        assertThat(answer).contains("朝阳店");
        assertThat(answer).doesNotContain("当前未识别到需要单独点名处理的门店");
    }

    @Test
    void groupManager_whenNoIssueStores_priorityBriefIsNoIssuesCopy_notLegacyPhrase() {
        AiUserContextResolver ur = AiDepartmentUserTestRows.resolverReturning(
                AiDepartmentUserTestRows.groupManager(91003, 1, 88));
        AiRunCreateRequest rq = new AiRunCreateRequest();
        rq.setUserId(91003L);
        rq.setDepartmentId(1L);
        rq.setDistributerId(88L);
        var uc = ur.resolve(rq);

        Map<String, Object> stats = baseStatsForGroup();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stats", stats);
        data.put("rollupMeta", Map.of(
                "aggregationMode", "GROUP_SQL_ROLLUP",
                "visibleDepartmentNodeCount", 1,
                "dataAvailableRecordingDepartmentCount", 1,
                "dataMissingVisibleNodeApprox", 0,
                "fallbackSingleAnchorOnly", false,
                "visibleStoreCount", 1,
                "storeWithRevenueCount", 1,
                "storeMissingRevenueCount", 0
        ));
        data.put("dataMissingStores", List.of());
        data.put("attentionStores", List.of());
        data.put("coveredStores", List.of(sampleCoveredStoreRow("稳定门店")));

        LinkedHashMap<String, Object> boEnv = new LinkedHashMap<>();
        boEnv.put("success", Boolean.TRUE);
        boEnv.put("data", data);

        AiRunState st = baseRunState(uc, rq, boEnv);

        new BusinessOverviewAgentNode(publisher).aggregateIfApplicable(st);

        assertThat(st.getBusinessOverviewResult().getPriorityStoresBrief())
                .isEqualTo(AiGroupOverviewStoreBrief.noIssuesLine());

        StubAnswerComposerNode composer = new StubAnswerComposerNode((s, u) -> "", publisher, testPromptService(), new AnswerComposerPayloadFactory(),
                DeterministicAnswerRenderer.createStandalone());
        composer.run(st);

        String answer = st.getFinalAnswerText();
        assertThat(answer).contains(AiGroupOverviewStoreBrief.noIssuesLine());
        assertThat(answer).contains("本次参与统计的门店：");
        assertThat(answer).contains("稳定门店");
        assertThat(answer).doesNotContain("当前未识别到需要单独点名处理的门店");
    }

    private static Map<String, Object> sampleCoveredStoreRow(String storeName) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("storeName", storeName);
        m.put("hasRevenueData", true);
        m.put("totalRevenue", new BigDecimal("854"));
        m.put("days", 2);
        m.put("orderCount", new BigDecimal("10"));
        m.put("avgOrderCount", new BigDecimal("5"));
        m.put("avgPerCustomer", new BigDecimal("85.4"));
        return m;
    }

    private static Map<String, Object> baseStatsForGroup() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("统计天数", 2);
        stats.put("总营业额", new BigDecimal("854"));
        stats.put("日均营业额", new BigDecimal("427"));
        stats.put("日均订单数", Double.valueOf(5.0));
        stats.put("客单价", "85.4");
        stats.put("平台费合计", Double.valueOf(30.0));
        stats.put("外卖营业额合计", Double.valueOf(20.0));
        stats.put("退款合计", BigDecimal.ZERO);
        stats.put("利润率", BigDecimal.ZERO);
        stats.put("盈亏状态", "不适用");
        return stats;
    }

    private static AiResolvedQueryContext groupResolved(AiUserContext uc, AiRunCreateRequest rq) {
        return AiResolvedQueryContext.builder()
                .orgScope(AiResolvedOrgScope.builder()
                        .scopeType(AiResolvedOrgScope.SCOPE_GROUP)
                        .distributerId(rq.getDistributerId())
                        .requestDepartmentId(rq.getDepartmentId())
                        .currentDepartmentId(uc.getDepartmentId())
                        .build())
                .build();
    }

    private static AiRunState baseRunState(AiUserContext uc,
            AiRunCreateRequest rq,
            LinkedHashMap<String, Object> boEnv) {
        return AiRunState.builder()
                .runId(778L)
                .userId(uc.getUserId())
                .departmentId(1L)
                .distributerId(88L)
                .normalizedUserInput("这个月经营怎么样？")
                .workspaceMode(AiWorkspaceMode.BUSINESS_CHAT)
                .businessOverviewPath(true)
                .dataPlanTools(new ArrayList<>(AiBusinessToolIds.DEFAULT_BUSINESS_OVERVIEW_TOOLS))
                .statStartDate("2026-05-01")
                .statEndDate("2026-05-10")
                .aiUserContext(uc)
                .resolvedQueryContext(groupResolved(uc, rq))
                .scope(AiQueryScope.builder()
                        .parentStoreCount(2)
                        .resolvedDepartmentIds(List.of(101, 102))
                        .build())
                .toolResults(toolResultsMinimal(boEnv, Map.of("purchaseSubTotal", new BigDecimal("3303"))))
                .build();
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
