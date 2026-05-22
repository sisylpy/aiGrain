package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.contract.SemanticContractValidationDebug;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PurchaseAnswerPlanBuilderTest {

    @Test
    void resolvePlanType_purchaseParallelStoresWire() {
        assertEquals(PurchaseAnswerPlan.TYPE_PURCHASE_STORE_AMOUNT_RANKING,
                PurchaseAnswerPlanBuilder.resolvePlanType(
                        AiQuerySemanticLexicon.STRUCTURED_PURCHASE_STORE_AMOUNT_RANKING,
                        AiQuerySemanticLexicon.SOURCE_ALL));
    }

    @Test
    void resolvePlanType_supplierRanking() {
        assertEquals(PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_AMOUNT_RANKING,
                PurchaseAnswerPlanBuilder.resolvePlanType(
                        AiQuerySemanticLexicon.STRUCTURED_SUPPLIER_AMOUNT_RANKING,
                        AiQuerySemanticLexicon.SOURCE_ALL));
    }

    @Test
    void resolvePlanType_selfAmountQuery() {
        assertEquals(PurchaseAnswerPlan.TYPE_PURCHASE_SELF_OVERVIEW,
                PurchaseAnswerPlanBuilder.resolvePlanType(
                        AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_AMOUNT_QUERY,
                        AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE));
    }

    @Test
    void resolvePlanType_goodsAmountVsCount_wiresOnly() {
        assertEquals(PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_AMOUNT_RANKING,
                PurchaseAnswerPlanBuilder.resolvePlanType(
                        AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_AMOUNT_RANKING,
                        AiQuerySemanticLexicon.SOURCE_ALL));
        assertEquals(PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_COUNT_RANKING,
                PurchaseAnswerPlanBuilder.resolvePlanType(
                        AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_COUNT_RANKING,
                        AiQuerySemanticLexicon.SOURCE_ALL));
    }

    @Test
    void build_goodsAmountRanking_emitsGoodsResultAnchorFromFocusRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("goodsName", "TestGoods");
        row.put("disGoodsId", 101);
        row.put("totalPurchaseAmount", 12.5);
        row.put("rank", 1);
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("totalPurchaseAmount", 100);
        overview.put("purchaseOrderCount", 3);
        overview.put("goodsPurchaseAmountTop", List.of(row));
        AiResolvedQueryIntent qi =
                AiResolvedQueryIntent.builder()
                        .structuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_AMOUNT_RANKING)
                        .purchaseSourceType(AiQuerySemanticLexicon.SOURCE_ALL)
                        .build();
        AiResolvedQueryContext rq = AiResolvedQueryContext.builder().queryIntent(qi).build();
        AiRunState state = AiRunState.builder().resolvedQueryContext(rq).build();
        PurchaseAnswerPlan plan = PurchaseAnswerPlanBuilder.build(state, overview, rq);
        assertNotNull(plan.getResultAnchors());
        assertEquals(1, plan.getResultAnchors().size());
        AiResultAnchor a = plan.getResultAnchors().get(0);
        assertEquals(AiResultAnchor.ENTITY_TYPE_GOODS, a.getEntityType());
        assertEquals("TestGoods", a.getEntityName());
        assertEquals("101", a.getEntityId());
        assertEquals(PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_AMOUNT_RANKING, a.getSourcePlanType());
        assertEquals(Integer.valueOf(1), a.getRank());
    }

    @Test
    void resolvePlanType_overviewSummaryWire_staysOverviewWithoutRankingWire() {
        assertEquals(PurchaseAnswerPlan.TYPE_PURCHASE_OVERVIEW,
                PurchaseAnswerPlanBuilder.resolvePlanType(
                        AiQuerySemanticLexicon.STRUCTURED_PURCHASE_OVERVIEW_SUMMARY,
                        AiQuerySemanticLexicon.SOURCE_ALL));
    }

    @Test
    void resolvePlanType_sourceGoodsQuery_ambiguous_isOverviewNotRanking() {
        assertEquals(PurchaseAnswerPlan.TYPE_PURCHASE_OVERVIEW,
                PurchaseAnswerPlanBuilder.resolvePlanType(
                        AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY,
                        AiQuerySemanticLexicon.SOURCE_ALL));
    }

    @Test
    void resolvePlanType_sourceGoodsQuery_selfChannel_isSelfGoodsDetailPlan() {
        assertEquals(PurchaseAnswerPlan.TYPE_PURCHASE_SELF_GOODS_DETAIL,
                PurchaseAnswerPlanBuilder.resolvePlanType(
                        AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY,
                        AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE));
    }

    @Test
    void resolvePlanType_sourceGoodsQuery_supplierChannel_isSupplierGoodsDetailPlan() {
        assertEquals(PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL,
                PurchaseAnswerPlanBuilder.resolvePlanType(
                        AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY,
                        AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE));
    }

    @Test
    void build_supplierGoodsDetail_usesPurchaseSupplierGoodsDetailRows_notGoodsTop() {
        Map<String, Object> d1 = new LinkedHashMap<>();
        d1.put("supplierName", "供货商甲");
        d1.put("purchaseAmount", 12.3);
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("totalPurchaseAmount", 100);
        overview.put("purchaseOrderCount", 3);
        overview.put("goodsPurchaseAmountTop", List.of(Map.of("goodsName", "别的商品", "purchaseSubtotal", "999")));
        overview.put("purchaseSupplierGoodsDetailRows", List.of(d1));
        overview.put(AiBusinessToolIds.PAYLOAD_PURCHASE_GOODS_ANCHOR_SUPPLIER_EXECUTION_ACTIVE, true);
        overview.put(AiBusinessToolIds.PAYLOAD_PURCHASE_GOODS_ANCHOR_EXECUTION_TARGET_GOODS_NAME, "白醋");
        AiResolvedQueryIntent qi =
                AiResolvedQueryIntent.builder()
                        .structuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY)
                        .purchaseSourceType(AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE)
                        .build();
        AiResolvedQueryContext rq = AiResolvedQueryContext.builder().queryIntent(qi).build();
        AiRunState state = AiRunState.builder().resolvedQueryContext(rq).build();
        PurchaseAnswerPlan plan = PurchaseAnswerPlanBuilder.build(state, overview, rq);
        assertEquals(PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL, plan.getPlanType());
        assertEquals(1, plan.getFocusRows().size());
        assertEquals("供货商甲", plan.getFocusRows().get(0).get("supplierName"));
        assertNotNull(plan.getDebug());
        assertEquals(1, plan.getDebug().get("purchaseSupplierGoodsDetailRowsCount"));
        assertNull(plan.getDebug().get("purchaseSupplierGoodsDetailNoDataReason"));
    }

    @Test
    void build_supplierGoodsDetail_twoRows_setsDebugRowCount() {
        Map<String, Object> d1 = new LinkedHashMap<>();
        d1.put("supplierName", "供货商甲");
        d1.put("purchaseAmount", 12.3);
        Map<String, Object> d2 = new LinkedHashMap<>();
        d2.put("supplierName", "供货商乙");
        d2.put("purchaseAmount", 5.0);
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("totalPurchaseAmount", 100);
        overview.put("purchaseOrderCount", 3);
        overview.put("purchaseSupplierGoodsDetailRows", List.of(d1, d2));
        overview.put(AiBusinessToolIds.PAYLOAD_PURCHASE_GOODS_ANCHOR_SUPPLIER_EXECUTION_ACTIVE, true);
        overview.put(AiBusinessToolIds.PAYLOAD_PURCHASE_GOODS_ANCHOR_EXECUTION_TARGET_GOODS_NAME, "白醋");
        AiResolvedQueryIntent qi =
                AiResolvedQueryIntent.builder()
                        .structuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY)
                        .purchaseSourceType(AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE)
                        .build();
        AiResolvedQueryContext rq = AiResolvedQueryContext.builder().queryIntent(qi).build();
        AiRunState state = AiRunState.builder().resolvedQueryContext(rq).build();
        PurchaseAnswerPlan plan = PurchaseAnswerPlanBuilder.build(state, overview, rq);
        assertEquals(2, plan.getFocusRows().size() + plan.getSecondaryRows().size());
        assertEquals(2, plan.getDebug().get("purchaseSupplierGoodsDetailRowsCount"));
        assertNull(plan.getDebug().get("purchaseSupplierGoodsDetailNoDataReason"));
    }

    @Test
    void build_supplierGoodsDetail_emptyRows_normalizesNoDataReason() {
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("totalPurchaseAmount", 100);
        overview.put("purchaseOrderCount", 3);
        overview.put("purchaseSupplierGoodsDetailRows", List.of());
        overview.put(AiBusinessToolIds.PAYLOAD_PURCHASE_GOODS_ANCHOR_SUPPLIER_EXECUTION_ACTIVE, true);
        overview.put(AiBusinessToolIds.PAYLOAD_PURCHASE_GOODS_ANCHOR_EXECUTION_TARGET_GOODS_NAME, "白醋");
        overview.put("purchaseSupplierGoodsDetailNoDataReason", "NO_SUPPLIER_PURCHASE_FOR_GOODS");
        overview.put("purchaseSupplierGoodsDetailAlternativeHasData", true);
        AiResolvedQueryIntent qi =
                AiResolvedQueryIntent.builder()
                        .structuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY)
                        .purchaseSourceType(AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE)
                        .build();
        AiResolvedQueryContext rq = AiResolvedQueryContext.builder().queryIntent(qi).build();
        AiRunState state = AiRunState.builder().resolvedQueryContext(rq).build();
        PurchaseAnswerPlan plan = PurchaseAnswerPlanBuilder.build(state, overview, rq);
        assertEquals(0, plan.getDebug().get("purchaseSupplierGoodsDetailRowsCount"));
        assertEquals(
                "NO_SUPPLIER_PURCHASE_FOR_FOCUSED_GOODS",
                plan.getDebug().get("purchaseSupplierGoodsDetailNoDataReason"));
        assertEquals(Boolean.TRUE, plan.getDebug().get("purchaseSupplierGoodsDetailAlternativeHasData"));
    }

    @Test
    void normalizePurchaseSupplierGoodsDetailNoDataReason_toolAliases() {
        assertEquals(
                "NO_SUPPLIER_PURCHASE_FOR_FOCUSED_GOODS",
                PurchaseAnswerPlanBuilder.normalizePurchaseSupplierGoodsDetailNoDataReason(
                        "NO_SUPPLIER_PURCHASE_FOR_GOODS"));
        assertEquals(
                "GOODS_NOT_FOUND_FOR_PURCHASE_DETAIL",
                PurchaseAnswerPlanBuilder.normalizePurchaseSupplierGoodsDetailNoDataReason(
                        "GOODS_NOT_FOUND_FOR_PURCHASE_DETAIL"));
        assertEquals(
                "FOCUSED_GOODS_NOT_FOUND",
                PurchaseAnswerPlanBuilder.normalizePurchaseSupplierGoodsDetailNoDataReason(
                        "GOODS_ID_MISSING_FOR_ANCHOR_EXECUTION"));
    }

    @Test
    void build_goodsSourceBreakdown_emitsGoodsResultAnchorFromFocusRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("goodsName", "海天5度白醋");
        row.put("disGoodsId", 54);
        row.put("totalPurchaseAmount", "2970");
        row.put("selfPurchaseAmount", "2970");
        row.put("supplierPurchaseAmount", "0");
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("purchaseGoodsSourceBreakdownActive", true);
        overview.put("purchaseGoodsSourceBreakdownRow", row);
        overview.put("purchaseGoodsSourceBreakdownFocusDisGoodsId", 54);
        AiResolvedQueryIntent qi =
                AiResolvedQueryIntent.builder()
                        .structuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY)
                        .purchaseSourceType(AiQuerySemanticLexicon.SOURCE_ALL)
                        .build();
        AiResolvedQueryContext rq =
                purchaseGoodsAnchorRq(
                        qi,
                        "SOURCE_BREAKDOWN",
                        "purchase.goods_anchor.source_breakdown",
                        "ALL",
                        "54",
                        "海天5度白醋");
        AiRunState state = AiRunState.builder().resolvedQueryContext(rq).build();
        PurchaseAnswerPlan plan = PurchaseAnswerPlanBuilder.build(state, overview, rq);
        assertEquals(PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_SOURCE_BREAKDOWN, plan.getPlanType());
        assertNotNull(plan.getResultAnchors());
        assertEquals(1, plan.getResultAnchors().size());
        AiResultAnchor a = plan.getResultAnchors().get(0);
        assertEquals(AiResultAnchor.ENTITY_TYPE_GOODS, a.getEntityType());
        assertEquals("54", a.getEntityId());
        assertEquals("海天5度白醋", a.getEntityName());
        assertEquals(PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_SOURCE_BREAKDOWN, a.getSourcePlanType());
        assertEquals(
                PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_SOURCE_BREAKDOWN,
                plan.getDebug().get("resultAnchorSourcePlanType"));
    }

    @Test
    void build_goodsSourceBreakdown_executionIntent_withoutToolFlag_isSourceBreakdownNotOverview() {
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("totalPurchaseAmount", 100);
        overview.put("purchaseOrderCount", 3);
        AiResolvedQueryIntent qi =
                AiResolvedQueryIntent.builder()
                        .structuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY)
                        .purchaseSourceType(AiQuerySemanticLexicon.SOURCE_ALL)
                        .build();
        AiResolvedQueryContext rq =
                purchaseGoodsAnchorRq(
                        qi,
                        "SOURCE_BREAKDOWN",
                        "purchase.goods_anchor.source_breakdown",
                        "ALL",
                        "54",
                        "海天5度白醋");
        AiRunState state = AiRunState.builder().resolvedQueryContext(rq).build();
        PurchaseAnswerPlan plan = PurchaseAnswerPlanBuilder.build(state, overview, rq);
        assertEquals(PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_SOURCE_BREAKDOWN, plan.getPlanType());
        assertEquals(
                PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_SOURCE_BREAKDOWN,
                plan.getDebug().get("resolvedPlanType"));
        assertEquals("TOOL_PAYLOAD_EMPTY", plan.getDebug().get("noDataReason"));
        assertNotNull(plan.getResultAnchors());
        assertEquals(1, plan.getResultAnchors().size());
        assertEquals("54", plan.getResultAnchors().get(0).getEntityId());
    }

    @Test
    void isGoodsSourceBreakdownIntent_trueWhenGoodsFollowUpAndSourceBreakdown() {
        AiResolvedQueryContext rq =
                purchaseGoodsAnchorRq(
                        AiResolvedQueryIntent.builder()
                                .structuredIntentDetail(
                                        AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY)
                                .purchaseSourceType(AiQuerySemanticLexicon.SOURCE_ALL)
                                .build(),
                        "SOURCE_BREAKDOWN",
                        "purchase.goods_anchor.source_breakdown",
                        "ALL",
                        "54",
                        "海天5度白醋");
        assertTrue(
                PurchaseAnswerPlanBuilder.isGoodsSourceBreakdownIntent(
                        rq,
                        AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY,
                        AiQuerySemanticLexicon.SOURCE_ALL));
    }

    @Test
    void canonicalDetailWanted_supplierBreakdown_staysDistinctFromSourceBreakdown() {
        assertEquals(
                AiQuerySemanticLexicon.DETAIL_WANTED_SUPPLIER_BREAKDOWN,
                AiQuerySemanticLexicon.canonicalDetailWanted(
                        AiQuerySemanticLexicon.DETAIL_WANTED_SUPPLIER_BREAKDOWN,
                        "GOODS",
                        "BREAKDOWN",
                        AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY));
        assertEquals(
                AiQuerySemanticLexicon.DETAIL_WANTED_SOURCE_BREAKDOWN,
                AiQuerySemanticLexicon.canonicalDetailWanted(
                        AiQuerySemanticLexicon.DETAIL_WANTED_SOURCE_BREAKDOWN,
                        "GOODS",
                        "BREAKDOWN",
                        AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY));
    }

    @Test
    void build_goodsSupplierBreakdown_executionIntent_withoutToolPayload_isSupplierGoodsDetail() {
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("totalPurchaseAmount", 100);
        overview.put("purchaseOrderCount", 3);
        AiResolvedQueryIntent qi =
                AiResolvedQueryIntent.builder()
                        .structuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY)
                        .purchaseSourceType(AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE)
                        .build();
        AiResolvedQueryContext rq =
                purchaseGoodsAnchorRq(
                        qi,
                        AiQuerySemanticLexicon.DETAIL_WANTED_SUPPLIER_BREAKDOWN,
                        "purchase.goods_anchor.supplier_breakdown",
                        "SUPPLIER_PURCHASE",
                        "54",
                        "海天5度白醋");
        AiRunState state = AiRunState.builder().resolvedQueryContext(rq).build();
        PurchaseAnswerPlan plan = PurchaseAnswerPlanBuilder.build(state, overview, rq);
        assertEquals(PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL, plan.getPlanType());
        assertEquals("GOODS_SUPPLIER_BREAKDOWN_NO_DATA", plan.getDebug().get("noDataReason"));
        assertNotNull(plan.getResultAnchors());
        assertEquals(1, plan.getResultAnchors().size());
        AiResultAnchor a = plan.getResultAnchors().get(0);
        assertEquals(AiResultAnchor.ENTITY_TYPE_GOODS, a.getEntityType());
        assertEquals("54", a.getEntityId());
        assertEquals("海天5度白醋", a.getEntityName());
        assertEquals(PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL, a.getSourcePlanType());
    }

    @Test
    void isGoodsSupplierBreakdownExecutionIntent_trueForGoodsAnchorFollowUp() {
        AiResolvedQueryContext rq =
                purchaseGoodsAnchorRq(
                        AiResolvedQueryIntent.builder()
                                .structuredIntentDetail(
                                        AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY)
                                .purchaseSourceType(AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE)
                                .build(),
                        AiQuerySemanticLexicon.DETAIL_WANTED_SUPPLIER_BREAKDOWN,
                        "purchase.goods_anchor.supplier_breakdown",
                        "SUPPLIER_PURCHASE",
                        "54",
                        "海天5度白醋");
        assertTrue(
                PurchaseAnswerPlanBuilder.isGoodsSupplierBreakdownExecutionIntent(
                        rq, AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY));
    }

    @Test
    void build_goodsSupplierUnitPrice_executionIntent_withoutToolPayload_isSupplierGoodsDetail() {
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("totalPurchaseAmount", 100);
        overview.put("purchaseOrderCount", 3);
        AiResolvedQueryIntent qi =
                AiResolvedQueryIntent.builder()
                        .structuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY)
                        .purchaseSourceType(AiQuerySemanticLexicon.SOURCE_ALL)
                        .build();
        AiResolvedQueryContext rq =
                purchaseGoodsAnchorRq(
                        qi,
                        AiQuerySemanticLexicon.DETAIL_WANTED_SUPPLIER_UNIT_PRICE,
                        "purchase.goods_anchor.supplier_unit_price",
                        "SUPPLIER_PURCHASE",
                        "54",
                        "海天5度白醋");
        AiRunState state = AiRunState.builder().resolvedQueryContext(rq).build();
        PurchaseAnswerPlan plan = PurchaseAnswerPlanBuilder.build(state, overview, rq);
        assertEquals(PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL, plan.getPlanType());
        assertEquals("GOODS_SUPPLIER_UNIT_PRICE_NO_DATA", plan.getDebug().get("noDataReason"));
        assertNotNull(plan.getResultAnchors());
        assertEquals(1, plan.getResultAnchors().size());
        assertEquals("54", plan.getResultAnchors().get(0).getEntityId());
        assertEquals(PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL,
                plan.getResultAnchors().get(0).getSourcePlanType());
    }

    @Test
    void build_supplierGoodsDetail_noData_inheritsGoodsAnchorFromPreviousTurn() {
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("totalPurchaseAmount", 100);
        AiResolvedQueryIntent qi =
                AiResolvedQueryIntent.builder()
                        .structuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY)
                        .purchaseSourceType(AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE)
                        .build();
        AiConversationTurnMemory prev =
                AiConversationTurnMemory.builder()
                        .lastResultAnchors(
                                List.of(
                                        AiResultAnchor.builder()
                                                .entityType(AiResultAnchor.ENTITY_TYPE_GOODS)
                                                .entityId("54")
                                                .entityName("海天5度白醋")
                                                .sourcePlanType(
                                                        PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_SOURCE_BREAKDOWN)
                                                .build()))
                        .build();
        AiResolvedQueryContext rq =
                purchaseGoodsAnchorRq(
                        qi,
                        AiQuerySemanticLexicon.DETAIL_WANTED_SUPPLIER_UNIT_PRICE,
                        "purchase.goods_anchor.supplier_unit_price",
                        AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE,
                        "54",
                        "海天5度白醋");
        rq =
                AiResolvedQueryContext.builder()
                        .queryIntent(rq.getQueryIntent())
                        .querySemanticParse(rq.getQuerySemanticParse())
                        .semanticContractValidation(rq.getSemanticContractValidation())
                        .previousTurn(prev)
                        .build();
        AiRunState state = AiRunState.builder().resolvedQueryContext(rq).build();
        PurchaseAnswerPlan plan = PurchaseAnswerPlanBuilder.build(state, overview, rq);
        assertEquals(PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL, plan.getPlanType());
        assertNotNull(plan.getResultAnchors());
        assertEquals(1, plan.getResultAnchors().size());
        assertEquals("54", plan.getResultAnchors().get(0).getEntityId());
        assertEquals("海天5度白醋", plan.getResultAnchors().get(0).getEntityName());
        assertEquals(
                PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL,
                plan.getResultAnchors().get(0).getSourcePlanType());
    }

    @Test
    void isGoodsSupplierUnitPriceExecutionIntent_trueForGoodsAnchorFollowUp() {
        AiResolvedQueryContext rq =
                purchaseGoodsAnchorRq(
                        AiResolvedQueryIntent.builder()
                                .structuredIntentDetail(
                                        AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY)
                                .purchaseSourceType(AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE)
                                .build(),
                        AiQuerySemanticLexicon.DETAIL_WANTED_SUPPLIER_UNIT_PRICE,
                        "purchase.goods_anchor.supplier_unit_price",
                        "SUPPLIER_PURCHASE",
                        "54",
                        "海天5度白醋");
        assertTrue(
                PurchaseAnswerPlanBuilder.isGoodsSupplierUnitPriceExecutionIntent(
                        rq, AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY));
    }

    @Test
    void structured_stock_reduce_goods_outbound_wire_is_catalogued() {
        assertTrue(AiQuerySemanticLexicon.isStructuredStockReduceDetail(
                AiQuerySemanticLexicon.STRUCTURED_GOODS_OUTBOUND_RANKING));
        assertTrue(AiQuerySemanticLexicon.isNonOverviewStockReduceStructuredDetail(
                AiQuerySemanticLexicon.STRUCTURED_GOODS_OUTBOUND_RANKING));
        assertFalse(AiQuerySemanticLexicon.isNonOverviewStockReduceStructuredDetail(
                AiQuerySemanticLexicon.STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY));
    }

    private static AiResolvedQueryContext purchaseGoodsAnchorRq(
            AiResolvedQueryIntent qi,
            String detailWanted,
            String matchedContractId,
            String sourceFacet,
            String goodsId,
            String goodsName) {
        AiResultAnchor anchor =
                AiResultAnchor.builder()
                        .entityType(AiResultAnchor.ENTITY_TYPE_GOODS)
                        .entityId(goodsId)
                        .entityName(goodsName)
                        .sourcePlanType(PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_AMOUNT_RANKING)
                        .rank(1)
                        .build();
        AiQuerySemanticParseResult parse =
                AiQuerySemanticParseResult.builder()
                        .semanticSlots(
                                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                        .queryObject("GOODS")
                                        .operation("DETAIL")
                                        .detailWanted(detailWanted)
                                        .sourceFacet(sourceFacet)
                                        .anchorPolicy("USE_PREVIOUS_ANCHOR")
                                        .structuredIntentDetailWire(
                                                AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY)
                                        .build())
                        .build();
        return AiResolvedQueryContext.builder()
                .queryIntent(qi)
                .querySemanticParse(parse)
                .semanticContractValidation(
                        SemanticContractValidationDebug.builder()
                                .matchedContractId(matchedContractId)
                                .build())
                .previousTurn(
                        AiConversationTurnMemory.builder()
                                .lastResultAnchors(List.of(anchor))
                                .build())
                .build();
    }
}

