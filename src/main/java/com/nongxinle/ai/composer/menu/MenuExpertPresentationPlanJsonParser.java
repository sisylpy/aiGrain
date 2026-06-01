package com.nongxinle.ai.composer.menu;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 从 LLM 原始输出解析 {@link MenuExpertPresentationPlan}。 */
public final class MenuExpertPresentationPlanJsonParser {

    private MenuExpertPresentationPlanJsonParser() {}

    public static ParseResult parse(String raw) {
        if (!StringUtils.hasText(raw)) {
            return ParseResult.failure("empty_llm_response");
        }
        String jsonText = extractJsonObjectText(raw.trim());
        if (!StringUtils.hasText(jsonText)) {
            return ParseResult.failure("json_object_not_found");
        }
        try {
            JSONObject root = JSON.parseObject(jsonText);
            if (root == null || root.isEmpty()) {
                return ParseResult.failure("empty_json_object");
            }
            MenuExpertPresentationPlan plan =
                    MenuExpertPresentationPlan.builder()
                            .mainSummary(text(root.get("mainSummary")))
                            .keyFindings(readStringList(root.get("keyFindings")))
                            .focusSections(readFocusSections(root.get("focusSections")))
                            .nextSteps(readStringList(root.get("nextSteps")))
                            .supportingEvidence(readEvidenceList(root.get("supportingEvidence")))
                            .capabilityBoundaryZh(text(root.get("capabilityBoundaryZh")))
                            .build();
            return ParseResult.success(plan, jsonText);
        } catch (Exception e) {
            return ParseResult.failure("json_parse_error: " + e.getMessage());
        }
    }

    private static String extractJsonObjectText(String raw) {
        if (raw.startsWith("{") && raw.endsWith("}")) {
            return raw;
        }
        int fenceStart = raw.indexOf("```");
        if (fenceStart >= 0) {
            int bodyStart = raw.indexOf('\n', fenceStart);
            if (bodyStart >= 0) {
                int fenceEnd = raw.indexOf("```", bodyStart + 1);
                if (fenceEnd > bodyStart) {
                    String fenced = raw.substring(bodyStart + 1, fenceEnd).trim();
                    if (fenced.startsWith("json")) {
                        int nl = fenced.indexOf('\n');
                        if (nl >= 0) {
                            fenced = fenced.substring(nl + 1).trim();
                        }
                    }
                    if (fenced.startsWith("{")) {
                        return fenced;
                    }
                }
            }
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1);
        }
        return null;
    }

    private static List<MenuExpertPresentationPlan.MenuExpertPresentationFocusSection> readFocusSections(Object node) {
        if (!(node instanceof JSONArray array) || array.isEmpty()) {
            return List.of();
        }
        List<MenuExpertPresentationPlan.MenuExpertPresentationFocusSection> sections = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            Object item = array.get(i);
            if (!(item instanceof JSONObject sectionObj)) {
                continue;
            }
            sections.add(
                    MenuExpertPresentationPlan.MenuExpertPresentationFocusSection.builder()
                            .sectionTitle(text(sectionObj.get("sectionTitle")))
                            .sectionSummary(text(sectionObj.get("sectionSummary")))
                            .dishes(readDishes(sectionObj.get("dishes")))
                            .suggestedAction(text(sectionObj.get("suggestedAction")))
                            .reason(text(sectionObj.get("reason")))
                            .build());
        }
        return sections;
    }

    private static List<MenuExpertPresentationPlan.MenuExpertPresentationDish> readDishes(Object node) {
        if (!(node instanceof JSONArray array) || array.isEmpty()) {
            return List.of();
        }
        List<MenuExpertPresentationPlan.MenuExpertPresentationDish> dishes = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            Object item = array.get(i);
            if (!(item instanceof JSONObject dishObj)) {
                continue;
            }
            dishes.add(
                    MenuExpertPresentationPlan.MenuExpertPresentationDish.builder()
                            .dishName(text(dishObj.get("dishName")))
                            .blendedGrossMarginRateOnListPrice(
                                    text(dishObj.get("blendedGrossMarginRateOnListPrice")))
                            .actualProfitAmount(text(dishObj.get("actualProfitAmount")))
                            .suggestedAction(text(dishObj.get("suggestedAction")))
                            .reason(text(dishObj.get("reason")))
                            .build());
        }
        return dishes;
    }

    private static List<Map<String, Object>> readEvidenceList(Object node) {
        if (!(node instanceof JSONArray array) || array.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            Object item = array.get(i);
            if (item instanceof JSONObject obj) {
                rows.add(new LinkedHashMap<>(obj));
            }
        }
        return rows;
    }

    private static List<String> readStringList(Object node) {
        if (!(node instanceof JSONArray array) || array.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            String value = text(array.get(i));
            if (StringUtils.hasText(value)) {
                out.add(value.trim());
            }
        }
        return out;
    }

    private static String text(Object value) {
        return value == null ? null : value.toString().trim();
    }

    public record ParseResult(
            boolean success,
            MenuExpertPresentationPlan plan,
            String normalizedJson,
            String errorCode) {

        static ParseResult success(MenuExpertPresentationPlan plan, String normalizedJson) {
            return new ParseResult(true, plan, normalizedJson, null);
        }

        static ParseResult failure(String errorCode) {
            return new ParseResult(false, null, null, errorCode);
        }
    }
}
