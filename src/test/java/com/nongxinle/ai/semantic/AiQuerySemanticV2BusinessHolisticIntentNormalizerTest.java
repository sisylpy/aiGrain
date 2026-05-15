package com.nongxinle.ai.semantic;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AiQuerySemanticV2BusinessHolisticIntentNormalizerTest {

    @Test
    void revenueOverviewWithHolisticPrimaryBecomesBusiness() {
        AiQuerySemanticParseResult in =
                AiQuerySemanticParseResult.builder()
                        .parseMissing(false)
                        .confidence(0.9)
                        .intent("REVENUE_OVERVIEW")
                        .metric(
                                AiQuerySemanticParseResult.MetricPart.builder()
                                        .primaryMetric("生意怎么样")
                                        .build())
                        .build();

        AiQuerySemanticV2BusinessHolisticIntentNormalizer.Result r =
                AiQuerySemanticV2BusinessHolisticIntentNormalizer.apply(in);

        assertEquals(AiResolvedQueryIntent.BUSINESS_OVERVIEW, r.semantic().getIntent());
        assertNotNull(r.notes());
    }

    @Test
    void revenueOverviewWithExplicitTurnoverUnchanged() {
        AiQuerySemanticParseResult in =
                AiQuerySemanticParseResult.builder()
                        .parseMissing(false)
                        .confidence(0.9)
                        .intent("REVENUE_OVERVIEW")
                        .metric(
                                AiQuerySemanticParseResult.MetricPart.builder()
                                        .primaryMetric("营业额")
                                        .build())
                        .build();

        AiQuerySemanticV2BusinessHolisticIntentNormalizer.Result r =
                AiQuerySemanticV2BusinessHolisticIntentNormalizer.apply(in);

        assertEquals("REVENUE_OVERVIEW", r.semantic().getIntent());
        assertNull(r.notes());
    }
}
