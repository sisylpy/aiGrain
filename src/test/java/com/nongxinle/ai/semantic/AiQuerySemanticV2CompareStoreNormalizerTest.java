package com.nongxinle.ai.semantic;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiQuerySemanticV2CompareStoreNormalizerTest {

    @Test
    void twoStores_bareEnglishRevenueRoutesToRevenueOverview() {
        AiQuerySemanticParseResult in =
                baseCompareStore(
                        List.of("AAA", "汀兰餐厅"),
                        AiQuerySemanticParseResult.MetricPart.builder().primaryMetric("revenue").build());

        AiQuerySemanticV2CompareStoreNormalizer.Result r = AiQuerySemanticV2CompareStoreNormalizer.apply(in);

        assertEquals(AiResolvedQueryIntent.REVENUE_OVERVIEW, r.semantic().getIntent());
        assertNotNull(r.notes());
        assertNull(r.notes().get("degradedBusinessCompareByRevenue"));
        assertNull(r.notes().get("ambiguousCompareStorePrimaryMetric"));
    }

    @Test
    void twoStores_explicitTurnoverChineseRoutesToRevenue() {
        AiQuerySemanticParseResult in =
                baseCompareStore(
                        List.of("A", "B"),
                        AiQuerySemanticParseResult.MetricPart.builder().primaryMetric("哪个营业额高").build());

        AiQuerySemanticV2CompareStoreNormalizer.Result r = AiQuerySemanticV2CompareStoreNormalizer.apply(in);

        assertEquals(AiResolvedQueryIntent.REVENUE_OVERVIEW, r.semantic().getIntent());
        assertNull(r.notes().get("degradedBusinessCompareByRevenue"));
    }

    @Test
    void holisticPrimaryRoutesToBusinessWithDegradedFlagWhenMultiStore() {
        AiQuerySemanticParseResult in =
                baseCompareStore(
                        List.of("A", "B"),
                        AiQuerySemanticParseResult.MetricPart.builder().primaryMetric("经营情况").build());

        AiQuerySemanticV2CompareStoreNormalizer.Result r = AiQuerySemanticV2CompareStoreNormalizer.apply(in);

        assertEquals(AiResolvedQueryIntent.BUSINESS_OVERVIEW, r.semantic().getIntent());
        assertTrue((Boolean) r.notes().get("degradedBusinessCompareByRevenue"));
    }

    private static AiQuerySemanticParseResult baseCompareStore(
            List<String> names, AiQuerySemanticParseResult.MetricPart metric) {
        return AiQuerySemanticParseResult.builder()
                .parseMissing(false)
                .confidence(0.95)
                .intent("COMPARE_STORE")
                .metric(metric)
                .requestedScope(
                        AiQuerySemanticParseResult.RequestedScopePart.builder()
                                .mentionedStoreNames(names)
                                .build())
                .build();
    }
}
