package com.nongxinle.ai.tool.business;

import com.nongxinle.ai.security.AiRoleCodes;
import com.nongxinle.ai.scope.AiScopeResolver;
import com.nongxinle.ai.tool.ToolRequest;
import com.nongxinle.service.GbAiDailyRevenueDashboardService;
import com.nongxinle.service.GbAiDailyRevenueService;
import com.nongxinle.service.GbAiGroupOverviewStoreIssuesService;
import com.nongxinle.service.GbAiRestaurantProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/** 广角汇总成功后，门店问题快照异常不得拖垮整张集团看板。 */
@ExtendWith(MockitoExtension.class)
class BusinessOverviewQueryToolGroupSnapshotIsolationTest {

    @Mock
    GbAiDailyRevenueService revenueService;

    @Mock
    GbAiDailyRevenueDashboardService dashboardService;

    @Mock
    GbAiRestaurantProfileService profileService;

    @Mock
    GbAiGroupOverviewStoreIssuesService issuesService;

    @Mock
    AiScopeResolver aiScopeResolver;

    BusinessOverviewQueryTool tool;

    @BeforeEach
    void setUp() {
        tool = new BusinessOverviewQueryTool(revenueService, dashboardService, profileService, issuesService,
                aiScopeResolver);
    }

    @Test
    void groupAggregate_stillSuccessful_when_storeIssues_snapshot_throws() {
        Map<String, Object> agg = new LinkedHashMap<>();
        agg.put("distinctRecordDates", 2);
        agg.put("totalGrossRevenue", new BigDecimal("854"));
        agg.put("distinctRecordingDepartments", 1);

        Map<String, Object> statsCn = new LinkedHashMap<>();
        statsCn.put("总营业额", new BigDecimal("854"));
        statsCn.put("统计天数", 2);
        statsCn.put("日均营业额", new BigDecimal("427"));
        statsCn.put("利润率", BigDecimal.ZERO);
        statsCn.put("盈亏状态", "-");

        when(aiScopeResolver.listDomainStoreAnchorsInResolved(anyList())).thenReturn(List.of(50));

        when(revenueService.expandStoreRootsToDailyRevenueScopeIds(anyList()))
                .thenAnswer(invocation -> new ArrayList<>((List<Integer>) invocation.getArgument(0)));

        when(revenueService.getGroupIncomeAggregateForDepartmentIds(
                ArgumentMatchers.<Integer>anyList(), ArgumentMatchers.eq("2026-05-01"), ArgumentMatchers.eq("2026-05-02")))
                .thenReturn(agg);

        when(dashboardService.buildGroupWideIncomeFlattened(
                        ArgumentMatchers.eq(agg), anyInt(), ArgumentMatchers.any(), ArgumentMatchers.anyString(),
                        ArgumentMatchers.anyString()))
                .thenReturn(statsCn);

        doThrow(new RuntimeException("simulate snapshot SQL failure")).when(issuesService)
                .buildGroupStoreIssuesSnapshot(ArgumentMatchers.eq(AiRoleCodes.GROUP_MANAGER), ArgumentMatchers.anyList(),
                        ArgumentMatchers.eq("2026-05-01"), ArgumentMatchers.eq("2026-05-02"), ArgumentMatchers.eq(800L));

        Map<String, Object> args = new LinkedHashMap<>();
        args.put(AiBusinessToolIds.ARG_DEPARTMENT_FATHER_ID, 800L);
        args.put(AiBusinessToolIds.ARG_START_DATE, "2026-05-01");
        args.put(AiBusinessToolIds.ARG_STOP_DATE, "2026-05-02");
        args.put(AiBusinessToolIds.ARG_GROUP_WIDE_OVERVIEW_HINT, Boolean.TRUE);
        args.put(AiBusinessToolIds.ARG_AI_ROLE_CODE, AiRoleCodes.GROUP_MANAGER);
        args.put(AiBusinessToolIds.ARG_RESOLVED_DEPARTMENT_IDS, List.of(50, 60));

        var res = ToolRequest.builder().runId(1L).userId(1L).toolName(tool.name()).args(args).build();
        var outcome = tool.execute(res);

        assertThat(outcome.isSuccess()).isTrue();

        Map<String, Object> env =
                outcome.getData() instanceof Map<?, ?> om ? new LinkedHashMap<>((Map<String, Object>) om) : Map.of();
        Map<String, Object> data = env.get("data") instanceof Map<?, ?> dm
                ? (Map<String, Object>) dm : Map.of();
        Map<String, Object> rm =
                data.get("rollupMeta") instanceof Map<?, ?> tm ? (Map<String, Object>) tm : Map.of();

        assertThat(rm.get("aggregationMode")).isEqualTo("GROUP_SQL_ROLLUP");
        assertThat(data.get("totalRevenue")).isNotNull();

        BigDecimal tr = BigDecimal.ZERO;
        if (data.get("totalRevenue") instanceof BigDecimal b) {
            tr = b;
        }
        assertThat(tr.compareTo(BigDecimal.ZERO)).isGreaterThan(0);
    }
}
