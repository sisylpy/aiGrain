package com.nongxinle.ai.semantic.contract;

import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.GoodsSupportedDishCoverAnswerPlan;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.SemanticParserAllowedOutputContract;
import com.nongxinle.ai.semantic.matrix.WarehouseSemanticCapabilityMatrix;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticContractCompletionEngineGoodsAnchorTest {

    @Test
    void complete_goodsSupportedDishCover_preservesMentionedGoodsNameInSlots() {
        AiQuerySemanticParseResult raw =
                AiQuerySemanticParseResult.builder()
                        .parseMissing(false)
                        .semanticDomain("WAREHOUSE")
                        .mentionedGoodsName("三黄鸡")
                        .semanticSlots(
                                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                        .selectedContractId(
                                                WarehouseSemanticCapabilityMatrix
                                                        .CONTRACT_GOODS_SUPPORTED_DISH_COVER)
                                        .queryObject("GOODS")
                                        .operation("DETAIL")
                                        .metric("SUPPORTED_DISH_COVER")
                                        .build())
                        .build();

        DomainContractSelectionResult selection =
                DomainContractSelectionResult.builder()
                        .selectedDomain("WAREHOUSE")
                        .parserAllowedOutputContract(
                                SemanticParserAllowedOutputContract.builder()
                                        .selectedDomain("WAREHOUSE")
                                        .allowedContracts(
                                                List.of(
                                                        SemanticParserAllowedOutputContract
                                                                .AllowedContractEntry.builder()
                                                                .contractId(
                                                                        WarehouseSemanticCapabilityMatrix
                                                                                .CONTRACT_GOODS_SUPPORTED_DISH_COVER)
                                                                .capabilityStatus("ACTIVE")
                                                                .build()))
                                        .build())
                        .build();

        SemanticContractCompletionEngine.Result result =
                SemanticContractCompletionEngine.complete(
                        SemanticContractCompletionEngine.Request.builder()
                                .rawParse(raw)
                                .selectedDomain("WAREHOUSE")
                                .contractSelection(selection)
                                .build());

        assertThat(result.isViolation()).isFalse();
        assertThat(SemanticContractCompletionEngine.isContractEntryValidated(
                        result.getCompletedParse()))
                .isTrue();
        assertThat(result.getCompletedParse().getMentionedGoodsName()).isEqualTo("三黄鸡");
        assertThat(result.getCompletedParse().getSemanticSlots().getMentionedGoodsName())
                .isEqualTo("三黄鸡");
        assertThat(result.getCompletedParse().getSemanticSlots().getSelectedContractId())
                .isEqualTo(WarehouseSemanticCapabilityMatrix.CONTRACT_GOODS_SUPPORTED_DISH_COVER);
        assertThat(result.getCompletedParse().getSemanticSlots().getStructuredIntentDetailWire())
                .isEqualTo(AiQuerySemanticLexicon.STRUCTURED_GOODS_SUPPORTED_DISH_COVER);
        assertThat(result.getCompletedParse().getSemanticSlots().getAnswerPlanType())
                .isEqualTo(GoodsSupportedDishCoverAnswerPlan.TYPE);
    }

    @Test
    void complete_topLevelGoodsWinsOverStaleSlotsMentionedGoodsName() {
        AiQuerySemanticParseResult raw =
                AiQuerySemanticParseResult.builder()
                        .parseMissing(false)
                        .mentionedGoodsName("三黄鸡")
                        .semanticSlots(
                                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                        .selectedContractId(
                                                WarehouseSemanticCapabilityMatrix
                                                        .CONTRACT_GOODS_SUPPORTED_DISH_COVER)
                                        .mentionedGoodsName("旧原料")
                                        .build())
                        .build();

        DomainContractSelectionResult selection =
                DomainContractSelectionResult.builder()
                        .selectedDomain("WAREHOUSE")
                        .parserAllowedOutputContract(
                                SemanticParserAllowedOutputContract.builder()
                                        .selectedDomain("WAREHOUSE")
                                        .allowedContracts(
                                                List.of(
                                                        SemanticParserAllowedOutputContract
                                                                .AllowedContractEntry.builder()
                                                                .contractId(
                                                                        WarehouseSemanticCapabilityMatrix
                                                                                .CONTRACT_GOODS_SUPPORTED_DISH_COVER)
                                                                .capabilityStatus("ACTIVE")
                                                                .build()))
                                        .build())
                        .build();

        SemanticContractCompletionEngine.Result result =
                SemanticContractCompletionEngine.complete(
                        SemanticContractCompletionEngine.Request.builder()
                                .rawParse(raw)
                                .selectedDomain("WAREHOUSE")
                                .contractSelection(selection)
                                .build());

        assertThat(result.isViolation()).isFalse();
        assertThat(result.getCompletedParse().effectiveMentionedGoodsName()).isEqualTo("三黄鸡");
        assertThat(result.getCompletedParse().getSemanticSlots().getMentionedGoodsName())
                .isEqualTo("三黄鸡");
    }
}
