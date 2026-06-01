package com.nongxinle.ai.composer.menu;

import com.alibaba.fastjson2.JSON;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuOperationDisplayCard;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuOptimizationPlan;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 将校验通过的 {@link MenuExpertPresentationPlan} 投影为 {@code MENU_ACTION_RECOMMENDATION_CARD}。 */
public final class MenuExpertPresentationPlanCardSupport {

    private static final String SOURCE_ANSWER_PLAN = "menuOperationAnswerPlan";
    private static final String PAYLOAD_STATUS_ACTIVE = "ACTIVE";
    private static final String PAYLOAD_STATUS_EMPTY = "EMPTY";
    private static final String PRESENTATION_SOURCE_LLM = "llm_expert_presentation";

    private MenuExpertPresentationPlanCardSupport() {}

    public static Map<String, Object> buildActionRecommendationCard(
            MenuOperationAnswerPlan plan, MenuExpertPresentationPlan presentation) {
        MenuOperationDisplayCard display = findDisplayCard(plan);
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("cardType", MenuOperationAnswerPlan.CARD_TYPE_MENU_ACTION_RECOMMENDATION);
        card.put(
                "title",
                display != null && StringUtils.hasText(display.getTitle())
                        ? display.getTitle().trim()
                        : "菜单优化方案");
        card.put(
                "subtitle",
                display != null && StringUtils.hasText(display.getSubtitle())
                        ? display.getSubtitle().trim()
                        : "基于销量、毛利与利润事实生成的顾问式展示方案");
        card.put(
                "chartType",
                display != null && StringUtils.hasText(display.getChartType())
                        ? display.getChartType().trim()
                        : MenuOperationAnswerPlan.CHART_TYPE_PLAN);
        card.put("payload", buildPresentationPayload(plan, presentation));

        Map<String, Object> source = new LinkedHashMap<>();
        source.put("answerPlan", SOURCE_ANSWER_PLAN);
        source.put(
                "dataRef",
                display != null && StringUtils.hasText(display.getDataRef())
                        ? display.getDataRef().trim()
                        : MenuOperationAnswerPlan.DATA_REF_MENU_OPTIMIZATION_PLAN);
        source.put("presentationSource", PRESENTATION_SOURCE_LLM);
        card.put("source", source);
        return card;
    }

    private static Map<String, Object> buildPresentationPayload(
            MenuOperationAnswerPlan plan, MenuExpertPresentationPlan presentation) {
        Map<String, Object> payload = new LinkedHashMap<>(presentation.toCardPayloadMap());
        boolean hasContent =
                StringUtils.hasText(presentation.getMainSummary())
                        || (presentation.getFocusSections() != null
                                && !presentation.getFocusSections().isEmpty());
        payload.put("status", hasContent ? PAYLOAD_STATUS_ACTIVE : PAYLOAD_STATUS_EMPTY);
        payload.put("presentationSource", PRESENTATION_SOURCE_LLM);
        payload.put("summary", presentation.getMainSummary());
        payload.put("detailDataScope", "harness_only");
        payload.put("detailDataVisible", false);
        payload.put("detailData", buildDetailData(plan));
        return payload;
    }

    private static Map<String, Object> buildDetailData(MenuOperationAnswerPlan plan) {
        if (plan == null || plan.getMenuOptimizationPlan() == null) {
            return Map.of();
        }
        MenuOptimizationPlan optimization = plan.getMenuOptimizationPlan();
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("optimizationSummary", optimization.getOptimizationSummary());
        detail.put("priorityGroups", serializeList(optimization.getPriorityGroups()));
        detail.put("costReviewDishes", serializeList(optimization.getCostReviewDishes()));
        detail.put("protectDishes", serializeList(optimization.getProtectDishes()));
        detail.put("promotionDishes", serializeList(optimization.getPromotionDishes()));
        detail.put("watchListDishes", serializeList(optimization.getWatchListDishes()));
        detail.put(
                "evidenceRows",
                plan.getEvidenceRows() == null ? List.of() : serializeList(plan.getEvidenceRows()));
        return detail;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> serializeList(List<?> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        try {
            Object parsed = JSON.parse(JSON.toJSONString(items));
            if (!(parsed instanceof List<?> list)) {
                return List.of();
            }
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    out.add(new LinkedHashMap<>((Map<String, Object>) map));
                }
            }
            return out;
        } catch (Exception ignore) {
            return List.of();
        }
    }

    private static MenuOperationDisplayCard findDisplayCard(MenuOperationAnswerPlan plan) {
        if (plan == null || plan.getDisplayCards() == null) {
            return null;
        }
        for (MenuOperationDisplayCard card : plan.getDisplayCards()) {
            if (card != null
                    && MenuOperationAnswerPlan.CARD_TYPE_MENU_ACTION_RECOMMENDATION.equals(card.getCardType())) {
                return card;
            }
        }
        return null;
    }
}
