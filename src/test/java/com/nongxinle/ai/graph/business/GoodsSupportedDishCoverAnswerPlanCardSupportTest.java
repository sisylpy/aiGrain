package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.dto.business.GoodsSupportedDishCoverAnswerPlan;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GoodsSupportedDishCoverAnswerPlanCardSupportTest {

    @Test
    void buildRunCards_projectsGoodsSupportedDishCoverCard() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("dishName", "宫保鸡丁");
        row.put("recipeUnitPerDish", "0.2");
        row.put("coverDays", "3.5");

        GoodsSupportedDishCoverAnswerPlan plan =
                GoodsSupportedDishCoverAnswerPlan.builder()
                        .planType(GoodsSupportedDishCoverAnswerPlan.TYPE)
                        .goodsName("三黄鸡")
                        .currentStockQty("12")
                        .stockUnit("kg")
                        .stockSnapshotLabel("当前库存 12kg")
                        .salesBaselineLabel("近7天销量基线")
                        .firstImpactedDishName("宫保鸡丁")
                        .firstImpactedCoverDays("3.5")
                        .dishRows(List.of(row))
                        .build();

        List<Map<String, Object>> cards = GoodsSupportedDishCoverAnswerPlanCardSupport.buildRunCards(plan);

        assertFalse(cards.isEmpty());
        assertEquals(GoodsSupportedDishCoverAnswerPlan.CARD_TYPE, cards.get(0).get("cardType"));
        assertEquals("三黄鸡 · 关联菜品", cards.get(0).get("title"));
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) cards.get(0).get("payload");
        assertEquals("三黄鸡", payload.get("goodsName"));
        assertEquals(1, ((List<?>) payload.get("dishRows")).size());
    }
}
