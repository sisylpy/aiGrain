package com.nongxinle.ai.semantic;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiQuerySemanticV2StockReducePurchaseDeconflictNormalizerTest {

    @Test
    void purchaseOverviewWithOutboundPrimaryMetricRewritesToStockReduce() {
        AiQuerySemanticParseResult in =
                AiQuerySemanticParseResult.builder()
                        .parseMissing(false)
                        .confidence(0.95)
                        .intent("PURCHASE_OVERVIEW")
                        .metric(
                                AiQuerySemanticParseResult.MetricPart.builder()
                                        .primaryMetric("outbound / procurement")
                                        .purchaseSourceType("OUTBOUND")
                                        .build())
                        .build();

        AiQuerySemanticV2StockReducePurchaseDeconflictNormalizer.Result r =
                AiQuerySemanticV2StockReducePurchaseDeconflictNormalizer.apply(in);

        assertNotNull(r.notes());
        assertTrue((Boolean) r.notes().get("stockReducePurchaseDeconflict"));
        assertEquals(AiResolvedQueryIntent.STOCK_REDUCE_QUERY, r.semantic().getIntent());
        assertEquals("OVERRIDE", r.semantic().getIntentAction());
        assertEquals("stock_reduce", r.semantic().getMetric().getPrimaryMetric());
        assertNull(r.semantic().getMetric().getPurchaseSourceType());
        assertNull(r.semantic().getMetric().getRankingType());
    }

    @Test
    void purchaseOverviewWithOutboundPurchaseSourceOnlyRewrites() {
        AiQuerySemanticParseResult in =
                AiQuerySemanticParseResult.builder()
                        .parseMissing(false)
                        .confidence(0.9)
                        .intent("PURCHASE_OVERVIEW")
                        .metric(
                                AiQuerySemanticParseResult.MetricPart.builder()
                                        .primaryMetric("purchase")
                                        .purchaseSourceType("OUTBOUND")
                                        .build())
                        .build();

        AiQuerySemanticV2StockReducePurchaseDeconflictNormalizer.Result r =
                AiQuerySemanticV2StockReducePurchaseDeconflictNormalizer.apply(in);

        assertEquals(AiResolvedQueryIntent.STOCK_REDUCE_QUERY, r.semantic().getIntent());
        assertNull(r.semantic().getMetric().getPurchaseSourceType());
    }

    @Test
    void leavesPurePurchaseUntouched() {
        AiQuerySemanticParseResult in =
                AiQuerySemanticParseResult.builder()
                        .parseMissing(false)
                        .confidence(0.9)
                        .intent("PURCHASE_OVERVIEW")
                        .metric(
                                AiQuerySemanticParseResult.MetricPart.builder()
                                        .primaryMetric("procurement")
                                        .purchaseSourceType("SUPPLIER_PURCHASE")
                                        .build())
                        .build();

        AiQuerySemanticV2StockReducePurchaseDeconflictNormalizer.Result r =
                AiQuerySemanticV2StockReducePurchaseDeconflictNormalizer.apply(in);

        assertNull(r.notes());
        assertEquals("PURCHASE_OVERVIEW", r.semantic().getIntent());
    }

    @Test
    void alreadyStockReduceClearsOutboundPurchaseSourceOnly() {
        AiQuerySemanticParseResult in =
                AiQuerySemanticParseResult.builder()
                        .parseMissing(false)
                        .confidence(0.9)
                        .intent("STOCK_REDUCE_QUERY")
                        .metric(
                                AiQuerySemanticParseResult.MetricPart.builder()
                                        .primaryMetric("stock_reduce")
                                        .purchaseSourceType("OUTBOUND")
                                        .rankingType("goods_outbound_ranking")
                                        .build())
                        .build();

        AiQuerySemanticV2StockReducePurchaseDeconflictNormalizer.Result r =
                AiQuerySemanticV2StockReducePurchaseDeconflictNormalizer.apply(in);

        Map<String, Object> notes = r.notes();
        assertNotNull(notes);
        assertTrue((Boolean) notes.get("clearedPurchaseSourceTypeOutbound"));
        assertEquals("STOCK_REDUCE_QUERY", r.semantic().getIntent());
        assertNull(r.semantic().getMetric().getPurchaseSourceType());
        assertEquals("goods_outbound_ranking", r.semantic().getMetric().getRankingType());
    }
}
