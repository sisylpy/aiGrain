package com.nongxinle.ai.tool.business;

import com.nongxinle.ai.scope.AiScopeResolver;
import com.nongxinle.ai.tool.ToolRequest;
import com.nongxinle.service.GbAiDailyRevenueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** GROUP + {@link AiBusinessToolIds#ARG_GROUP_WIDE_OVERVIEW_HINT}：总营业额必须与门店排行金额之和一致（按集团 SQL rollup）。 */
@ExtendWith(MockitoExtension.class)
class RevenueQueryToolGroupAggregateTest {

    @Mock
    GbAiDailyRevenueService revenueService;

    @Mock
    AiScopeResolver aiScopeResolver;

    RevenueQueryTool tool;

    @BeforeEach
    void setUp() {
        tool = new RevenueQueryTool(revenueService, aiScopeResolver);
    }

    @Test
    void groupWide_totalRevenue_sumOfStoreLines_matches_groupAggregate() {
        List<Integer> expanded = List.of(1, 2, 5, 3, 4);

        Map<String, Object> agg = new LinkedHashMap<>();
        agg.put("distinctRecordDates", 2);
        agg.put("totalGrossRevenue", new BigDecimal("5831"));
        agg.put("totalOrders", new BigDecimal("100"));
        agg.put("distinctRecordingDepartments", 5);
        agg.put("totalPlatformFee", new BigDecimal("10"));
        agg.put("totalDineIn", new BigDecimal("4000"));
        agg.put("totalTakeout", new BigDecimal("1831"));
        agg.put("totalTakeoutNetApprox", new BigDecimal("1821"));
        agg.put("maxDailyGross", new BigDecimal("4000"));
        agg.put("minDailyGrossPositive", new BigDecimal("100"));

        when(aiScopeResolver.listDomainStoreAnchorsInResolved(List.of(1, 3))).thenReturn(List.of(1, 3));
        when(revenueService.expandStoreRootsToDailyRevenueScopeIds(List.of(1, 3))).thenReturn(expanded);
        when(revenueService.getGroupIncomeAggregateForDepartmentIds(eq(expanded), eq("2026-05-01"), eq("2026-05-02")))
                .thenReturn(agg);

        Map<String, Object> statsAaa = Map.of(
                "total_revenue", new BigDecimal("4644"),
                "days", 2);
        Map<String, Object> statsTingLan = Map.of(
                "total_revenue", new BigDecimal("1187"),
                "days", 2);
        when(revenueService.getStatsByDepartmentId(1L, "2026-05-01", "2026-05-02")).thenReturn(statsAaa);
        when(revenueService.getStatsByDepartmentId(3L, "2026-05-01", "2026-05-02")).thenReturn(statsTingLan);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put(AiBusinessToolIds.ARG_DEPARTMENT_FATHER_ID, 1L);
        args.put(AiBusinessToolIds.ARG_START_DATE, "2026-05-01");
        args.put(AiBusinessToolIds.ARG_STOP_DATE, "2026-05-02");
        args.put(AiBusinessToolIds.ARG_GROUP_WIDE_OVERVIEW_HINT, Boolean.TRUE);
        args.put(AiBusinessToolIds.ARG_RESOLVED_DEPARTMENT_IDS, List.of(1, 3));

        var outcome = tool.execute(ToolRequest.builder().runId(1L).userId(1L).toolName(tool.name()).args(args).build());

        assertThat(outcome.isSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> env = outcome.getData() instanceof Map ? (Map<String, Object>) outcome.getData() : Map.of();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = env.get("data") instanceof Map ? (Map<String, Object>) env.get("data") : Map.of();

        assertThat(data.get("days")).isEqualTo(2);
        assertThat(((BigDecimal) data.get("totalRevenue")).intValue()).isEqualTo(5831);
        assertThat(((BigDecimal) data.get("avgDailyRevenue")).doubleValue()).isEqualTo(2915.5);

        double sumRanking = 0d;
        if (data.get("storeRevenueRanking") instanceof List<?> list) {
            for (Object row : list) {
                if (row instanceof Map<?, ?> m && m.get("revenueAmount") instanceof Number n) {
                    sumRanking += n.doubleValue();
                }
            }
        }
        assertThat(sumRanking).isEqualTo(5831d);
    }

    @Test
    void withoutGroupWideHint_stillUsesSingleAnchorStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total_revenue", new BigDecimal("4644"));
        stats.put("days", 2);

        when(revenueService.getStatsByDepartmentId(1L, "2026-05-01", "2026-05-02")).thenReturn(stats);
        when(aiScopeResolver.listDomainStoreAnchorsInResolved(anyList())).thenReturn(List.of(1));

        Map<String, Object> args = new LinkedHashMap<>();
        args.put(AiBusinessToolIds.ARG_DEPARTMENT_FATHER_ID, 1L);
        args.put(AiBusinessToolIds.ARG_START_DATE, "2026-05-01");
        args.put(AiBusinessToolIds.ARG_STOP_DATE, "2026-05-02");
        args.put(AiBusinessToolIds.ARG_GROUP_WIDE_OVERVIEW_HINT, Boolean.FALSE);

        var outcome = tool.execute(ToolRequest.builder().runId(1L).userId(1L).toolName(tool.name()).args(args).build());

        assertThat(outcome.isSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> env = outcome.getData() instanceof Map ? (Map<String, Object>) outcome.getData() : Map.of();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = env.get("data") instanceof Map ? (Map<String, Object>) env.get("data") : Map.of();
        assertThat(((BigDecimal) data.get("totalRevenue")).intValue()).isEqualTo(4644);
    }
}
