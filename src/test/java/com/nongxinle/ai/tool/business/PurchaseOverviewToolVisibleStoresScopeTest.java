package com.nongxinle.ai.tool.business;

import com.nongxinle.ai.tool.ToolRequest;
import com.nongxinle.service.GbAiDailyRevenueService;
import com.nongxinle.service.GbDistributerPurchaseGoodsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 集团采购：门店根必须以 visibleStores 为准，避免单列 resolvedDepartmentIds 收窄 Top 频次与汇总范围。 */
@ExtendWith(MockitoExtension.class)
class PurchaseOverviewToolVisibleStoresScopeTest {

    @Mock
    GbDistributerPurchaseGoodsService purchaseGoodsService;

    @Mock
    GbAiDailyRevenueService revenueService;

    @Captor
    ArgumentCaptor<List<Integer>> expandRootsCaptor;

    PurchaseOverviewTool tool;

    @BeforeEach
    void setUp() {
        tool = new PurchaseOverviewTool(purchaseGoodsService, revenueService);
    }

    @Test
    void groupAggregation_prefersVisibleStoreRoots_overNarrowResolvedDepartmentIds() {
        when(revenueService.expandStoreRootsToDailyRevenueScopeIds(anyList()))
                .thenAnswer(invocation -> new ArrayList<>((List<Integer>) invocation.getArgument(0)));
        when(revenueService.buildStoreRevenueQueryScopeByStoreRoot(anyList())).thenReturn(Map.of());

        when(purchaseGoodsService.queryGbPurchaseGoodsCount(any())).thenReturn(0);
        when(purchaseGoodsService.sumPurchaseSubtotalGroupedByPurDepartmentId(any())).thenReturn(List.of());

        Map<String, Object> vs1 = new LinkedHashMap<>();
        vs1.put("storeDepartmentId", 101);
        vs1.put("storeName", "AAA");
        Map<String, Object> vs2 = new LinkedHashMap<>();
        vs2.put("storeDepartmentId", 102);
        vs2.put("storeName", "汀兰餐厅");

        Map<String, Object> args = new LinkedHashMap<>();
        args.put(AiBusinessToolIds.ARG_DIS_ID, 800L);
        args.put(AiBusinessToolIds.ARG_START_DATE, "2026-05-01");
        args.put(AiBusinessToolIds.ARG_STOP_DATE, "2026-05-31");
        args.put(AiBusinessToolIds.ARG_GROUP_PURCHASE_AGGREGATION, Boolean.TRUE);
        args.put(AiBusinessToolIds.ARG_RESOLVED_DEPARTMENT_IDS, List.of(999));
        args.put(AiBusinessToolIds.ARG_VISIBLE_STORES, List.of(vs1, vs2));

        var req = ToolRequest.builder().runId(1L).userId(1L).toolName(tool.name()).args(args).build();
        var outcome = tool.execute(req);
        assertThat(outcome.isSuccess()).isTrue();

        verify(revenueService).expandStoreRootsToDailyRevenueScopeIds(expandRootsCaptor.capture());
        assertThat(expandRootsCaptor.getValue()).containsExactly(101, 102);
    }
}
