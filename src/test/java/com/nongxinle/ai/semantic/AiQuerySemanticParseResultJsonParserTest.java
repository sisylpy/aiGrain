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
}
