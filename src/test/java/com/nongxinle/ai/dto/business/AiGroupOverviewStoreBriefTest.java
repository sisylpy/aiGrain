package com.nongxinle.ai.dto.business;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiGroupOverviewStoreBriefTest {

    @Test
    void formatPriorityBrief_dataMissingFirst_thenAttention_capAtThree() {
        List<AiOverviewStoreIssueItem> missing = List.of(
                AiOverviewStoreIssueItem.builder().storeName("A").reason("无日营收").build(),
                AiOverviewStoreIssueItem.builder().storeName("B").reason("画像缺失").build());
        List<AiOverviewStoreIssueItem> attention = List.of(
                AiOverviewStoreIssueItem.builder().storeName("C").reason("采购偏高").riskLevel("warning").build(),
                AiOverviewStoreIssueItem.builder().storeName("D").reason("订单低").riskLevel("warning").build());

        String brief = AiGroupOverviewStoreBrief.formatPriorityBrief(missing, attention);
        assertThat(brief).startsWith("需要优先关注的门店：");
        assertThat(brief).contains("1. A：无日营收；");
        assertThat(brief).contains("2. B：画像缺失；");
        assertThat(brief).contains("3. C：采购偏高；");
        assertThat(brief).doesNotContain("D：");
    }

    @Test
    void formatPriorityBrief_empty_returnsNull() {
        assertThat(AiGroupOverviewStoreBrief.formatPriorityBrief(List.of(), List.of())).isNull();
        assertThat(AiGroupOverviewStoreBrief.formatPriorityBrief(null, null)).isNull();
    }

    @Test
    void noIssuesLine_fixedCopy() {
        assertThat(AiGroupOverviewStoreBrief.noIssuesLine()).isEqualTo("当前没有识别到明显异常门店。");
    }
}
