package com.nongxinle.ai.composer.renderer;

import com.nongxinle.ai.dto.business.DishProfitPrescriptionAnswerPlan;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DishProfitPrescriptionDeterministicRendererTest {

    private final DishProfitPrescriptionDeterministicRenderer renderer =
            new DishProfitPrescriptionDeterministicRenderer();

    @Test
    void render_doesNotExposeKnownGapCodesInUserText() {
        Map<String, Object> suggested = new LinkedHashMap<>();
        suggested.put("targetGrossMarginRate", "55");
        suggested.put("suggestedPricePerPortion", "22.22");

        DishProfitPrescriptionAnswerPlan plan =
                DishProfitPrescriptionAnswerPlan.builder()
                        .planType(DishProfitPrescriptionAnswerPlan.TYPE)
                        .contractId(DishProfitPrescriptionAnswerPlan.CONTRACT_ID)
                        .dishName("香煎青鱼")
                        .status(DishProfitPrescriptionAnswerPlan.STATUS_SUCCESS)
                        .suggestedPrice(suggested)
                        .knownGaps(List.of("LATEST_PURCHASE_PRICE_NOT_IN_P1", "TARGET_MARGIN_UNSPECIFIED"))
                        .diagnosis(Map.of("headlineZh", "香煎青鱼当前毛利率低于目标带，建议优先关注定价与成本"))
                        .build();

        String text = renderer.render(plan);
        assertThat(text).contains("香煎青鱼");
        assertThat(text).contains("建议售价");
        assertThat(text).doesNotContain("LATEST_PURCHASE_PRICE_NOT_IN_P1");
        assertThat(text).doesNotContain("TARGET_MARGIN_UNSPECIFIED");
        assertThat(text).contains("最新采购价");
    }
}
