package com.nongxinle.ai.graph.business;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class DishProfitPrescriptionPricingSupportTest {

    @Test
    void computeSuggestedPrice_prefersRequestedTarget() {
        var result = DishProfitPrescriptionPricingSupport.computeSuggestedPrice(
                new BigDecimal("10.00"), "55", "45");
        assertThat(result).isNotNull();
        assertThat(result.suggestedPricePerPortion()).isEqualTo("22.22");
        assertThat(result.targetGrossMarginRate()).isEqualTo("55.00");
    }

    @Test
    void computeSuggestedPrice_fallsBackToStandardTarget() {
        var result = DishProfitPrescriptionPricingSupport.computeSuggestedPrice(
                new BigDecimal("8.00"), null, "50");
        assertThat(result).isNotNull();
        assertThat(result.suggestedPricePerPortion()).isEqualTo("16.00");
    }
}
