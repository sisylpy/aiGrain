package com.nongxinle.ai.semantic.intake.llm;

import cn.hutool.json.JSONUtil;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.semantic.intake.SemanticIntakeInput;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LlmSemanticIntakePromptBuilder {

    private LlmSemanticIntakePromptBuilder() {}

    public static String toUserJson(SemanticIntakeInput request) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("rawUserMessage", request.getRawUserMessage());
        root.put("normalizedUserMessage", request.getNormalizedUserMessage());
        root.put("today", request.getToday() != null ? request.getToday().toString() : null);
        root.put("hasPreviousTurn", request.isHasPreviousTurn());
        root.put("previousTurn", mapPreviousTurn(request));
        root.put("visibleStores", mapVisibleStores(request.getVisibleStoreNames()));
        root.put("resultAnchors", mapResultAnchors(request.getResultAnchors()));
        root.put("orgScope", mapOrgScope(request));
        return JSONUtil.toJsonStr(root);
    }

    private static Map<String, Object> mapOrgScope(SemanticIntakeInput req) {
        if (req.getVisibleStoreNames() == null || req.getVisibleStoreNames().isEmpty()) {
            return null;
        }
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("visibleStoreNames", req.getVisibleStoreNames());
        return m;
    }

    private static List<Map<String, String>> mapVisibleStores(List<String> names) {
        if (names == null || names.isEmpty()) {
            return null;
        }
        List<Map<String, String>> out = new ArrayList<>();
        for (String n : names) {
            if (!StringUtils.hasText(n)) {
                continue;
            }
            LinkedHashMap<String, String> row = new LinkedHashMap<>();
            row.put("storeName", n.trim());
            out.add(row);
        }
        return out.isEmpty() ? null : out;
    }

    private static Map<String, Object> mapPreviousTurn(SemanticIntakeInput req) {
        if (!req.isHasPreviousTurn()) {
            return null;
        }
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        putIfText(m, "intentCode", req.getPreviousIntentCode());
        putIfText(m, "pathCode", req.getPreviousPathCode());
        putIfText(m, "structuredIntentDetail", req.getPreviousStructuredIntentDetail());
        putIfText(m, "timeLabel", req.getPreviousTimeLabel());
        putIfText(m, "startDate", req.getPreviousStartDate());
        putIfText(m, "endDate", req.getPreviousEndDate());
        putIfText(m, "scopeType", req.getPreviousScopeType());
        putIfText(m, "mentionedStoreName", req.getPreviousMentionedStoreName());
        putIfText(m, "mentionedDishName", req.getPreviousMentionedDishName());
        putIfText(m, "effectiveQuestion", req.getPreviousEffectiveQuestion());
        putIfText(m, "answerSummary", req.getPreviousAnswerSummary());
        if (req.getPreviousSemanticSlots() != null) {
            LinkedHashMap<String, Object> slots = new LinkedHashMap<>();
            putIfText(slots, "queryObject", req.getPreviousSemanticSlots().getQueryObject());
            putIfText(slots, "operation", req.getPreviousSemanticSlots().getOperation());
            putIfText(slots, "metric", req.getPreviousSemanticSlots().getMetric());
            if (!slots.isEmpty()) {
                m.put("semanticSlotsSummary", slots);
            }
        }
        return m.isEmpty() ? null : m;
   }

    private static List<Map<String, Object>> mapResultAnchors(List<AiResultAnchor> anchors) {
        if (anchors == null || anchors.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (AiResultAnchor a : anchors) {
            if (a == null || !StringUtils.hasText(a.getEntityName())) {
                continue;
            }
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            putIfText(row, "entityType", a.getEntityType());
            putIfText(row, "entityName", a.getEntityName());
            if (a.getRank() != null) {
                row.put("rank", a.getRank());
            }
            out.add(row);
        }
        return out.isEmpty() ? null : out;
    }

    private static void putIfText(Map<String, Object> m, String key, String value) {
        if (StringUtils.hasText(value)) {
            m.put(key, value.trim());
        }
    }
}
