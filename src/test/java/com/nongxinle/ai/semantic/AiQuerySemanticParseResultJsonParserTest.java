package com.nongxinle.ai.semantic;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiQuerySemanticParseResultJsonParserTest {

    @Test
    void stripsForbiddenFieldsAndParsesNested() {
        String raw = """
                {
                  "intent": "BUSINESS_OVERVIEW",
                  "confidence": 0.92,
                  "queryStoreIds": [1,2],
                  "time": {"timeType": "CURRENT_MONTH", "needInheritFromPrevious": false},
                  "requestedScope": {
                    "requestedScopeType": "GROUP",
                    "expandedSqlDepartmentIds": [9]
                  },
                  "metric": {"primaryMetric": "BUSINESS_STATUS"},
                  "needClarification": false,
                  "reason": "ok"
                }
                """;

        AiQuerySemanticParseResult p = AiQuerySemanticParseResultJsonParser.parseRaw(raw);
        assertThat(p.isParseMissing()).isFalse();
        assertThat(p.getIntent()).isEqualTo("BUSINESS_OVERVIEW");
        assertThat(p.getConfidence()).isEqualTo(0.92);
        assertThat(p.getTime()).isNotNull();
        assertThat(p.getTime().getTimeType()).isEqualTo("CURRENT_MONTH");
        assertThat(p.getRequestedScope()).isNotNull();
        assertThat(p.getRequestedScope().getRequestedScopeType()).isEqualTo("GROUP");
        assertThat(p.getRequestedScope().getMentionedStoreName()).isNull();
        assertThat(p.getMetric().getPrimaryMetric()).isEqualTo("BUSINESS_STATUS");
    }

    @Test
    void storeMentionSample() {
        String raw = """
                {"intent":"BUSINESS_OVERVIEW","confidence":0.9,
                  "time":{"timeType":"CURRENT_MONTH"},
                  "requestedScope":{"requestedScopeType":"STORE","mentionedStoreName":"AAA"},
                  "metric":{"primaryMetric":"BUSINESS_STATUS"},
                  "needClarification":false,"reason":""}
                """;
        AiQuerySemanticParseResult p = AiQuerySemanticParseResultJsonParser.parseRaw(raw);
        assertThat(p.getRequestedScope().getRequestedScopeType()).isEqualTo("STORE");
        assertThat(p.getRequestedScope().getMentionedStoreName()).isEqualTo("AAA");
    }

    @Test
    void effectiveMentionedStoreNamesIgnoresNullLiteralsAndBlank() {
        AiQuerySemanticParseResult p = AiQuerySemanticParseResult.builder()
                .requestedScope(AiQuerySemanticParseResult.RequestedScopePart.builder()
                        .mentionedStoreNames(List.of("AAA", null, "", "  ", "null", "NULL", "汀兰餐厅"))
                        .mentionedStoreName(" null ")
                        .build())
                .build();
        assertThat(p.effectiveMentionedStoreNames()).containsExactly("AAA", "汀兰餐厅");
    }

    @Test
    void promoteTopLevelContractFieldsIntoSemanticSlots() {
        String raw =
                """
                {
                  "confidence": 0.91,
                  "intentAction": "INHERIT_PREVIOUS",
                  "timeAction": "INHERIT_PREVIOUS",
                  "scopeAction": "INHERIT_PREVIOUS",
                  "metricAction": "INHERIT_PREVIOUS",
                  "selectedContractId": "dish_sales.single_dish",
                  "queryObject": "DISH",
                  "operation": "DETAIL",
                  "metric": "SOLD_PORTIONS",
                  "anchorPolicy": "IGNORE_PREVIOUS_ANCHOR",
                  "mentionedDishName": "烩菜",
                  "time": {
                    "timeType": "THIS_MONTH",
                    "startDate": "2026-05-01",
                    "endDate": "2026-05-25",
                    "timeSource": "DEFAULT_MONTH_TO_DATE"
                  },
                  "needClarification": false
                }
                """;

        AiQuerySemanticParseResultJsonParser.ProtocolNormalizeResult norm =
                AiQuerySemanticParseResultJsonParser.parseAndNormalizeProtocol(raw);
        AiQuerySemanticParseResult p = norm.parsed();

        assertThat(p.isParseMissing()).isFalse();
        assertThat(norm.relocate().changed()).isTrue();
        assertThat(norm.relocate().moves())
                .anyMatch(m -> m.contains("semanticSlots.selectedContractId: promoted from top-level"));
        assertThat(p.getSemanticSlots()).isNotNull();
        assertThat(p.getSemanticSlots().getSelectedContractId()).isEqualTo("dish_sales.single_dish");
        assertThat(p.getSemanticSlots().getQueryObject()).isEqualTo("DISH");
        assertThat(p.getSemanticSlots().getOperation()).isEqualTo("DETAIL");
        assertThat(p.getSemanticSlots().getMetric()).isEqualTo("SOLD_PORTIONS");
        assertThat(p.getSemanticSlots().getAnchorPolicy()).isEqualTo("IGNORE_PREVIOUS_ANCHOR");
        assertThat(p.getSemanticSlots().getMentionedDishName()).isEqualTo("烩菜");
        assertThat(p.getMentionedDishName()).isEqualTo("烩菜");
        assertThat(p.getMetric()).isNull();
        assertThat(Boolean.TRUE.equals(p.getQuerySemanticV2RepairAttempted())).isTrue();
    }

    @Test
    void parseAndPromote_topLevelMentionedGoodsNameIntoSemanticSlots() {
        String raw =
                """
                {
                  "domain": "WAREHOUSE",
                  "confidence": 0.9,
                  "selectedContractId": "warehouse.goods_supported_dish_cover.v1",
                  "queryObject": "GOODS",
                  "operation": "DETAIL",
                  "metric": "SUPPORTED_DISH_COVER",
                  "structuredIntentDetailWire": "goods_supported_dish_cover",
                  "mentionedGoodsName": "三黄鸡",
                  "needClarification": false
                }
                """;

        AiQuerySemanticParseResultJsonParser.ProtocolNormalizeResult norm =
                AiQuerySemanticParseResultJsonParser.parseAndNormalizeProtocol(raw);
        AiQuerySemanticParseResult p = norm.parsed();

        assertThat(p.isParseMissing()).isFalse();
        assertThat(p.getMentionedGoodsName()).isEqualTo("三黄鸡");
        assertThat(p.getSemanticSlots()).isNotNull();
        assertThat(p.getSemanticSlots().getMentionedGoodsName()).isEqualTo("三黄鸡");
        assertThat(p.getSemanticSlots().getSelectedContractId())
                .isEqualTo("warehouse.goods_supported_dish_cover.v1");
    }

    @Test
    void promoteTopLevelContractFields_semanticSlotsWinsWhenBothPresent() {
        String raw =
                """
                {
                  "confidence": 0.9,
                  "intentAction": "NEW",
                  "timeAction": "NEW",
                  "scopeAction": "NEW",
                  "metricAction": "NEW",
                  "selectedContractId": "wrong.top.level",
                  "semanticSlots": {
                    "selectedContractId": "dish_sales.single_dish",
                    "queryObject": "DISH",
                    "operation": "DETAIL",
                    "metric": "SOLD_PORTIONS"
                  },
                  "needClarification": false
                }
                """;

        AiQuerySemanticParseResult p = AiQuerySemanticParseResultJsonParser.parseRaw(raw);

        assertThat(p.getSemanticSlots().getSelectedContractId()).isEqualTo("dish_sales.single_dish");
    }

    @Test
    void rejectsEchoedAllowedOutputContractCatalog() {
        String raw =
                """
                {
                  "allowedOutputContract": {
                    "selectedDomain": "DISH_SALES",
                    "allowedContracts": [
                      {"contractId": "dish_sales.single_dish", "wire": "dish_sales_single_dish"}
                    ]
                  }
                }
                """;

        AiQuerySemanticParseResult p = AiQuerySemanticParseResultJsonParser.parseRaw(raw);
        assertThat(p.isParseMissing()).isTrue();
        assertThat(AiQuerySemanticParseResultJsonParser.describeParseFailureReason(raw))
                .isEqualTo("echoed_input_contract_catalog");
    }

    @Test
    void parsesRequestedTargetGrossMarginRateAsNumberOrString() {
        String rawNumber =
                """
                {
                  "intent": "DISH_COST",
                  "confidence": 0.9,
                  "semanticSlots": {
                    "selectedContractId": "dish.profit.prescription.v1",
                    "requestedTargetGrossMarginRate": 55
                  },
                  "needClarification": false
                }
                """;
        AiQuerySemanticParseResult pNum = AiQuerySemanticParseResultJsonParser.parseRaw(rawNumber);
        assertThat(pNum.getSemanticSlots().getRequestedTargetGrossMarginRate()).isEqualTo("55");
        assertThat(pNum.effectiveRequestedTargetGrossMarginRate()).isEqualTo("55");

        String rawString =
                """
                {
                  "intent": "DISH_COST",
                  "confidence": 0.9,
                  "semanticSlots": {
                    "selectedContractId": "dish.profit.prescription.v1",
                    "requestedTargetGrossMarginRate": "55"
                  },
                  "needClarification": false
                }
                """;
        AiQuerySemanticParseResult pStr = AiQuerySemanticParseResultJsonParser.parseRaw(rawString);
        assertThat(pStr.effectiveRequestedTargetGrossMarginRate()).isEqualTo("55");
    }
}
