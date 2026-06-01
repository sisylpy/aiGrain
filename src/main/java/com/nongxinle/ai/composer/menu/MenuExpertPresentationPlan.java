package com.nongxinle.ai.composer.menu;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜单专家 LLM 展示计划：只决定如何呈现事实包，不新增业务事实。
 * 仅用于 {@code MENU_ACTION_RECOMMENDATION} 主卡 payload 投影。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuExpertPresentationPlan {

    private String mainSummary;

    @Builder.Default
    private List<String> keyFindings = new ArrayList<>();

    @Builder.Default
    private List<MenuExpertPresentationFocusSection> focusSections = new ArrayList<>();

    @Builder.Default
    private List<String> nextSteps = new ArrayList<>();

    @Builder.Default
    private List<Map<String, Object>> supportingEvidence = new ArrayList<>();

    private String capabilityBoundaryZh;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuExpertPresentationFocusSection {

        private String sectionTitle;
        private String sectionSummary;

        @Builder.Default
        private List<MenuExpertPresentationDish> dishes = new ArrayList<>();

        private String suggestedAction;
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuExpertPresentationDish {

        private String dishName;
        private String blendedGrossMarginRateOnListPrice;
        private String actualProfitAmount;
        private String suggestedAction;
        private String reason;
    }

    /** 转为 cards[] payload 用的 Map（不含 detailData）。 */
    public Map<String, Object> toCardPayloadMap() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("mainSummary", mainSummary);
        payload.put("keyFindings", keyFindings == null ? List.of() : keyFindings);
        payload.put("focusSections", serializeFocusSections());
        payload.put("nextSteps", nextSteps == null ? List.of() : nextSteps);
        payload.put(
                "supportingEvidence",
                supportingEvidence == null ? List.of() : new ArrayList<>(supportingEvidence));
        if (capabilityBoundaryZh != null && !capabilityBoundaryZh.isBlank()) {
            payload.put("capabilityBoundaryZh", capabilityBoundaryZh.trim());
        }
        return payload;
    }

    private List<Map<String, Object>> serializeFocusSections() {
        if (focusSections == null || focusSections.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (MenuExpertPresentationFocusSection section : focusSections) {
            if (section == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sectionTitle", section.getSectionTitle());
            row.put("sectionSummary", section.getSectionSummary());
            row.put("suggestedAction", section.getSuggestedAction());
            row.put("reason", section.getReason());
            row.put("dishes", serializeDishes(section.getDishes()));
            out.add(row);
        }
        return out;
    }

    private static List<Map<String, Object>> serializeDishes(List<MenuExpertPresentationDish> dishes) {
        if (dishes == null || dishes.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (MenuExpertPresentationDish dish : dishes) {
            if (dish == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("dishName", dish.getDishName());
            row.put("blendedGrossMarginRateOnListPrice", dish.getBlendedGrossMarginRateOnListPrice());
            row.put("actualProfitAmount", dish.getActualProfitAmount());
            row.put("suggestedAction", dish.getSuggestedAction());
            row.put("reason", dish.getReason());
            out.add(row);
        }
        return out;
    }
}
