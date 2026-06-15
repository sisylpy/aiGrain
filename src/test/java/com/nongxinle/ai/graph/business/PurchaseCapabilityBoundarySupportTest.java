package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.frame.CurrentSemanticFrame;
import com.nongxinle.ai.semantic.frame.SemanticFrameValidationResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PurchaseCapabilityBoundarySupportTest {

    @Test
    void priceAnomalyWithoutExplicitSpecificityPassesWhenContractWireAndMetricAlign() {
        AiQuerySemanticParseResult parse =
                AiQuerySemanticParseResult.builder()
                        .semanticSlots(
                                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                        .selectedContractId("purchase.anomaly.price")
                                        .structuredIntentDetailWire(
                                                AiQuerySemanticLexicon.STRUCTURED_PURCHASE_PRICE_ANOMALY)
                                        .queryObject("GOODS")
                                        .operation("ANOMALY")
                                        .metric("UNIT_PRICE")
                                        .sourceFacet(AiQuerySemanticLexicon.SOURCE_ALL)
                                        .build())
                        .build();
        CurrentSemanticFrame frame =
                CurrentSemanticFrame.builder()
                        .queryObject("GOODS")
                        .operation("ANOMALY")
                        .metric("UNIT_PRICE")
                        .sourceFacet(AiQuerySemanticLexicon.SOURCE_ALL)
                        .structuredIntentDetailWire(
                                AiQuerySemanticLexicon.STRUCTURED_PURCHASE_PRICE_ANOMALY)
                        .build();

        SemanticFrameValidationResult result =
                PurchaseCapabilityBoundarySupport.validateCapabilityBoundary(
                        frame, parse, null, null);

        assertNull(result);
    }

    @Test
    void unspecifiedSpecificityStillPassesWhenAnomalyContractIsStructurallyExplicit() {
        AiQuerySemanticParseResult parse =
                AiQuerySemanticParseResult.builder()
                        .semanticSlots(
                                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                        .selectedContractId("purchase.anomaly.price")
                                        .capabilitySpecificity("UNSPECIFIED")
                                        .structuredIntentDetailWire(
                                                AiQuerySemanticLexicon.STRUCTURED_PURCHASE_PRICE_ANOMALY)
                                        .queryObject("GOODS")
                                        .operation("ANOMALY")
                                        .metric("UNIT_PRICE")
                                        .sourceFacet(AiQuerySemanticLexicon.SOURCE_ALL)
                                        .build())
                        .build();
        CurrentSemanticFrame frame =
                CurrentSemanticFrame.builder()
                        .queryObject("GOODS")
                        .operation("ANOMALY")
                        .metric("UNIT_PRICE")
                        .sourceFacet(AiQuerySemanticLexicon.SOURCE_ALL)
                        .structuredIntentDetailWire(
                                AiQuerySemanticLexicon.STRUCTURED_PURCHASE_PRICE_ANOMALY)
                        .build();

        SemanticFrameValidationResult result =
                PurchaseCapabilityBoundarySupport.validateCapabilityBoundary(
                        frame, parse, null, null);

        assertNull(result);
    }

    @Test
    void genericAnomalyWireStillClarifiesEvenWithoutSpecificityGate() {
        AiQuerySemanticParseResult parse =
                AiQuerySemanticParseResult.builder()
                        .semanticSlots(
                                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                        .structuredIntentDetailWire(
                                                AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_ANOMALY)
                                        .queryObject("GOODS")
                                        .operation("ANOMALY")
                                        .metric("PURCHASE_AMOUNT")
                                        .sourceFacet(AiQuerySemanticLexicon.SOURCE_ALL)
                                        .build())
                        .build();
        CurrentSemanticFrame frame =
                CurrentSemanticFrame.builder()
                        .queryObject("GOODS")
                        .operation("ANOMALY")
                        .metric("PURCHASE_AMOUNT")
                        .sourceFacet(AiQuerySemanticLexicon.SOURCE_ALL)
                        .structuredIntentDetailWire(
                                AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_ANOMALY)
                        .build();

        SemanticFrameValidationResult result =
                PurchaseCapabilityBoundarySupport.validateCapabilityBoundary(
                        frame, parse, null, null);

        assertTrue(result != null && result.needSemanticClarification());
        assertTrue(result.violationCodes().contains("PURCHASE_GENERIC_ANOMALY_WIRE_UNSUPPORTED"));
    }

    @Test
    void amountSpikeContractWithoutExplicitSpecificityPassesViaDetectionWire() {
        AiQuerySemanticParseResult parse =
                AiQuerySemanticParseResult.builder()
                        .semanticSlots(
                                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                        .selectedContractId("purchase.anomaly.amount_spike")
                                        .structuredIntentDetailWire(
                                                AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_AMOUNT_SPIKE)
                                        .queryObject("GOODS")
                                        .operation("ANOMALY")
                                        .metric("PURCHASE_AMOUNT")
                                        .sourceFacet(AiQuerySemanticLexicon.SOURCE_ALL)
                                        .build())
                        .build();
        CurrentSemanticFrame frame =
                CurrentSemanticFrame.builder()
                        .queryObject("GOODS")
                        .operation("ANOMALY")
                        .metric("PURCHASE_AMOUNT")
                        .sourceFacet(AiQuerySemanticLexicon.SOURCE_ALL)
                        .structuredIntentDetailWire(
                                AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_AMOUNT_SPIKE)
                        .build();

        SemanticFrameValidationResult result =
                PurchaseCapabilityBoundarySupport.validateCapabilityBoundary(
                        frame, parse, null, null);

        assertNull(result);
    }

    @Test
    void anomalyContractIdAloneIsStructurallyExplicitEvenWithAmbiguousMetric() {
        AiQuerySemanticParseResult parse =
                AiQuerySemanticParseResult.builder()
                        .semanticSlots(
                                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                        .selectedContractId("purchase.anomaly.price")
                                        .queryObject("GOODS")
                                        .operation("ANOMALY")
                                        .metric("PURCHASE_AMOUNT")
                                        .sourceFacet(AiQuerySemanticLexicon.SOURCE_ALL)
                                        .build())
                        .build();
        CurrentSemanticFrame frame =
                CurrentSemanticFrame.builder()
                        .queryObject("GOODS")
                        .operation("ANOMALY")
                        .metric("PURCHASE_AMOUNT")
                        .sourceFacet(AiQuerySemanticLexicon.SOURCE_ALL)
                        .build();

        SemanticFrameValidationResult result =
                PurchaseCapabilityBoundarySupport.validateCapabilityBoundary(
                        frame, parse, null, null);

        assertNull(result);
    }
}
