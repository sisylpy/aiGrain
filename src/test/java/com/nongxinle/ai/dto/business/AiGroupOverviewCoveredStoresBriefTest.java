package com.nongxinle.ai.dto.business;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiGroupOverviewCoveredStoresBriefTest {

    @Test
    void format_listsAllLinesWithHeader() {
        List<AiOverviewCoveredStoreItem> items = List.of(
                AiOverviewCoveredStoreItem.builder()
                        .storeName("朝阳店")
                        .hasRevenueData(true)
                        .totalRevenue(new BigDecimal("854"))
                        .days(2)
                        .orderCount(new BigDecimal("10"))
                        .avgOrderCount(new BigDecimal("5"))
                        .avgPerCustomer(new BigDecimal("85.4"))
                        .build(),
                AiOverviewCoveredStoreItem.builder()
                        .storeName("国贸店")
                        .hasRevenueData(true)
                        .totalRevenue(new BigDecimal("600"))
                        .days(2)
                        .orderCount(new BigDecimal("8"))
                        .avgOrderCount(new BigDecimal("4"))
                        .avgPerCustomer(new BigDecimal("75"))
                        .build());

        String brief = AiGroupOverviewCoveredStoresBrief.format(items);
        assertThat(brief).startsWith("本次参与统计的门店：");
        assertThat(brief).contains("1. 朝阳店：");
        assertThat(brief).contains("营业额 854 元");
        assertThat(brief).contains("统计 2 天");
        assertThat(brief).contains("订单数 10 单");
        assertThat(brief).contains("客单价 85.4 元");
        assertThat(brief).contains("2. 国贸店：");
    }

    @Test
    void format_empty_returnsBlank() {
        assertThat(AiGroupOverviewCoveredStoresBrief.format(List.of())).isEmpty();
        assertThat(AiGroupOverviewCoveredStoresBrief.format(null)).isEmpty();
    }
}
