package com.nongxinle.ai.graph.business;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CostInsightIntentConvergenceTest {

    @Test
    void asksGroupWideCost_wordsMatch() {
        assertThat(CostInsightIntentConvergence.asksGroupWideCostWording("集团成本怎么样")).isTrue();
        assertThat(CostInsightIntentConvergence.asksGroupWideCostWording("集团本月毛利如何")).isTrue();
        assertThat(CostInsightIntentConvergence.asksGroupWideCostWording("本月成本怎么样")).isFalse();
        assertThat(CostInsightIntentConvergence.asksGroupWideCostWording("集团有多少门店")).isFalse();
    }

    @Test
    void procurement_roles_detected() {
        assertThat(CostInsightIntentConvergence.isProcurementCostConvergenceRole("STORE_PURCHASER")).isTrue();
        assertThat(CostInsightIntentConvergence.isProcurementCostConvergenceRole("GROUP_PURCHASER")).isTrue();
        assertThat(CostInsightIntentConvergence.isProcurementCostConvergenceRole("WAREHOUSE_PURCHASER")).isTrue();
        assertThat(CostInsightIntentConvergence.isProcurementCostConvergenceRole("STORE_MANAGER")).isFalse();
        assertThat(CostInsightIntentConvergence.isProcurementCostConvergenceRole("GROUP_MANAGER")).isFalse();
    }
}
