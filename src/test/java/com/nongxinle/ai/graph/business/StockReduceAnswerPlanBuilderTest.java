package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.StockReduceAnswerPlan;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StockReduceAnswerPlanBuilderTest {

    @Test
    void resolvePlanType_overview_produceOutput_waste_loss_ranking() {
        assertEquals(StockReduceAnswerPlan.TYPE_STOCK_REDUCE_OVERVIEW,
                StockReduceAnswerPlanBuilder.resolvePlanType(
                        AiQuerySemanticLexicon.STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY));
        assertEquals(StockReduceAnswerPlan.TYPE_STOCK_REDUCE_PRODUCTION_OVERVIEW,
                StockReduceAnswerPlanBuilder.resolvePlanType(
                        AiQuerySemanticLexicon.STRUCTURED_PRODUCE_CONSUME));
        assertEquals(StockReduceAnswerPlan.TYPE_STOCK_REDUCE_OUTPUT_OVERVIEW,
                StockReduceAnswerPlanBuilder.resolvePlanType(
                        AiQuerySemanticLexicon.STRUCTURED_PRODUCE_OUTPUT));
        assertEquals(StockReduceAnswerPlan.TYPE_STOCK_REDUCE_WASTE_OVERVIEW,
                StockReduceAnswerPlanBuilder.resolvePlanType(AiQuerySemanticLexicon.STRUCTURED_WASTE));
        assertEquals(StockReduceAnswerPlan.TYPE_STOCK_REDUCE_LOSS_OVERVIEW,
                StockReduceAnswerPlanBuilder.resolvePlanType(AiQuerySemanticLexicon.STRUCTURED_LOSS));
        assertEquals(StockReduceAnswerPlan.TYPE_STOCK_REDUCE_RETURN_OVERVIEW,
                StockReduceAnswerPlanBuilder.resolvePlanType(AiQuerySemanticLexicon.STRUCTURED_RETURN));
        assertEquals(StockReduceAnswerPlan.TYPE_STOCK_REDUCE_GOODS_AMOUNT_RANKING,
                StockReduceAnswerPlanBuilder.resolvePlanType(
                        AiQuerySemanticLexicon.STRUCTURED_GOODS_OUTBOUND_RANKING));
        assertEquals(StockReduceAnswerPlan.TYPE_STOCK_REDUCE_GOODS_COUNT_RANKING,
                StockReduceAnswerPlanBuilder.resolvePlanType(
                        AiQuerySemanticLexicon.STRUCTURED_GOODS_OUTBOUND_COUNT_RANKING));
    }

    @Test
    void resolvePlanType_storeOutboundParallelWire() {
        assertEquals(StockReduceAnswerPlan.TYPE_STOCK_REDUCE_STORE_AMOUNT_RANKING,
                StockReduceAnswerPlanBuilder.resolvePlanType(
                        AiQuerySemanticLexicon.STRUCTURED_STORE_OUTBOUND_AMOUNT_RANKING));
    }

    @Test
    void resolveReduceType_mapsPlanToTypeDimension() {
        assertEquals(StockReduceAnswerPlan.REDUCE_TYPE_ALL,
                StockReduceAnswerPlanBuilder.resolveReduceType(StockReduceAnswerPlan.TYPE_STOCK_REDUCE_OVERVIEW));
        assertEquals(StockReduceAnswerPlan.REDUCE_TYPE_TYPE1,
                StockReduceAnswerPlanBuilder.resolveReduceType(
                        StockReduceAnswerPlan.TYPE_STOCK_REDUCE_PRODUCTION_OVERVIEW));
        assertEquals(StockReduceAnswerPlan.REDUCE_TYPE_TYPE2,
                StockReduceAnswerPlanBuilder.resolveReduceType(StockReduceAnswerPlan.TYPE_STOCK_REDUCE_WASTE_OVERVIEW));
    }

    @Test
    void build_storeOutboundRanking_populatesFocusFromPerStoreList() {
        AiResolvedQueryIntent qi = AiResolvedQueryIntent.builder()
                .structuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_STORE_OUTBOUND_AMOUNT_RANKING)
                .build();
        AiResolvedQueryContext rq = AiResolvedQueryContext.builder()
                .queryIntent(qi)
                .build();
        AiRunState state = AiRunState.builder()
                .runId(21L)
                .rawUserInput("AAA和汀兰餐厅哪个出库金额高")
                .build();

        LinkedHashMap<String, Object> inner = new LinkedHashMap<>();
        inner.put("produceTotal", BigDecimal.ZERO);
        inner.put("wasteTotal", BigDecimal.ZERO);
        inner.put("lossTotal", BigDecimal.ZERO);
        inner.put("returnTotal", BigDecimal.ZERO);
        inner.put("grandTotalFourTypes", BigDecimal.ZERO);
        LinkedHashMap<String, Object> s1 = new LinkedHashMap<>();
        s1.put("storeDepartmentId", 2L);
        s1.put("storeName", "BBB");
        s1.put("grandTotalFourTypes", new BigDecimal("120"));
        s1.put("amount", 120.0);
        LinkedHashMap<String, Object> s2 = new LinkedHashMap<>();
        s2.put("storeDepartmentId", 3L);
        s2.put("storeName", "汀兰餐厅");
        s2.put("grandTotalFourTypes", new BigDecimal("80"));
        s2.put("amount", 80.0);
        inner.put("topStoresOutboundByGrandTotal", List.of(s1, s2));

        LinkedHashMap<String, Object> dbg = new LinkedHashMap<>();
        StockReduceAnswerPlan plan = StockReduceAnswerPlanBuilder.build(state, inner, rq, dbg);
        assertEquals(StockReduceAnswerPlan.TYPE_STOCK_REDUCE_STORE_AMOUNT_RANKING, plan.getPlanType());
        assertEquals(1, plan.getFocusRows().size());
        assertEquals("BBB", plan.getFocusRows().get(0).get("storeName"));
        assertEquals(1, plan.getSecondaryRows().size());
        assertEquals("grandTotalFourTypes", plan.getDebug().get("sortKey"));
    }

    @Test
    void extractInnerData_flattensToolData() {
        Map<String, Object> inner = Map.of(
                "produceTotal", new BigDecimal("10"),
                "wasteTotal", new BigDecimal("2"),
                "lossTotal", new BigDecimal("3"),
                "returnTotal", new BigDecimal("1"),
                "grandTotalFourTypes", new BigDecimal("16"));
        Map<String, Object> diag = new LinkedHashMap<>();
        Map<String, Object> got = StockReduceAnswerPlanBuilder.extractStockReduceInnerData(inner, diag);
        assertFalse(got.isEmpty());
        assertTrue(diag.containsKey("foundDataPath"));
    }

    @Test
    void attachIfApplicable_setsPlanFromToolEnvelope() {
        AiRunState state = AiRunState.builder()
                .runId(9L)
                .stockReduceQueryPath(true)
                .dataPlanTools(List.of(AiBusinessToolIds.STOCK_REDUCE_QUERY))
                .resolvedQueryContext(null)
                .rawUserInput("这个月出库多少钱")
                .build();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("produceTotal", new BigDecimal("100"));
        data.put("wasteTotal", BigDecimal.ZERO);
        data.put("lossTotal", BigDecimal.ZERO);
        data.put("returnTotal", BigDecimal.ZERO);
        data.put("grandTotalFourTypes", new BigDecimal("100"));
        data.put("totalsBasis", "CALENDAR_NATURAL_DAY");

        Map<String, Object> env = new LinkedHashMap<>();
        env.put("success", true);
        env.put("data", data);
        state.getToolResults().put(AiBusinessToolIds.STOCK_REDUCE_QUERY, env);

        StockReduceAnswerPlanBuilder.attachIfApplicable(state);
        assertTrue(state.getStockReduceAnswerPlan() != null);
        assertEquals(StockReduceAnswerPlan.TYPE_STOCK_REDUCE_OVERVIEW, state.getStockReduceAnswerPlan().getPlanType());
        assertEquals(1, state.getStockReduceAnswerPlan().getFocusRows().size());
        assertEquals(4, state.getStockReduceAnswerPlan().getSecondaryRows().size());
    }

    @Test
    void build_countRanking_populatesFocusFromOutboundTimesList() {
        AiResolvedQueryIntent qi = AiResolvedQueryIntent.builder()
                .structuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_GOODS_OUTBOUND_COUNT_RANKING)
                .build();
        AiResolvedQueryContext rq = AiResolvedQueryContext.builder()
                .queryIntent(qi)
                .build();
        AiRunState state = AiRunState.builder()
                .runId(11L)
                .rawUserInput("哪个商品出库次数最多")
                .build();

        LinkedHashMap<String, Object> inner = new LinkedHashMap<>();
        inner.put("produceTotal", BigDecimal.ZERO);
        inner.put("wasteTotal", BigDecimal.ZERO);
        inner.put("lossTotal", BigDecimal.ZERO);
        inner.put("returnTotal", BigDecimal.ZERO);
        inner.put("grandTotalFourTypes", BigDecimal.ZERO);
        LinkedHashMap<String, Object> r1 = new LinkedHashMap<>();
        r1.put("name", "G1");
        r1.put("outboundTimes", 10L);
        r1.put("amount", new BigDecimal("1.5"));
        LinkedHashMap<String, Object> r2 = new LinkedHashMap<>();
        r2.put("name", "G2");
        r2.put("outboundTimes", 3L);
        r2.put("amount", BigDecimal.ZERO);
        inner.put("topGoodsOutboundByOutboundTimes", List.of(r1, r2));

        LinkedHashMap<String, Object> dbg = new LinkedHashMap<>();
        StockReduceAnswerPlan plan = StockReduceAnswerPlanBuilder.build(state, inner, rq, dbg);
        assertEquals(StockReduceAnswerPlan.TYPE_STOCK_REDUCE_GOODS_COUNT_RANKING, plan.getPlanType());
        assertEquals(1, plan.getFocusRows().size());
        assertEquals("G1", plan.getFocusRows().get(0).get("name"));
        assertEquals(10L, ((Number) plan.getFocusRows().get(0).get("outboundTimes")).longValue());
        assertEquals(1, plan.getSecondaryRows().size());
        assertEquals("outboundTimes", plan.getDebug().get("sortKey"));
        assertEquals("DESC", plan.getDebug().get("sortDirection"));
        assertFalse(plan.getDebug().containsKey("failureReason"));
    }
}
