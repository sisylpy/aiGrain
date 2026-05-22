package com.nongxinle.ai.followup.rewrite.llm;

import com.alibaba.fastjson2.JSON;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.followup.rewrite.FollowUpRewriteRequest;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 组装 Follow-up Rewrite LLM 的 user JSON（脱敏，无数据库 ID）。
 */
public final class LlmFollowUpRewritePromptBuilder {

    private LlmFollowUpRewritePromptBuilder() {}

    public static String toUserJson(FollowUpRewriteRequest request) {
        LinkedHashMap<String, Object> root = new LinkedHashMap<>();
        root.put("rawUserMessage", request.getRawUserMessage());
        root.put("normalizedUserMessage", request.getNormalizedUserMessage());
        root.put(
                "today",
                request.getToday() != null ? request.getToday().toString() : null);
        root.put("hasPreviousTurn", request.isHasPreviousTurn());
        root.put("previousTurn", mapPreviousTurn(request));
        root.put("visibleStores", mapVisibleStores(request.getVisibleStoreNames()));
        root.put("resultAnchors", mapResultAnchors(request.getResultAnchors()));
        root.put("anchorsByType", mapAnchorsByType(request.getResultAnchors()));
        return JSON.toJSONString(root);
    }

    private static Map<String, Object> mapPreviousTurn(FollowUpRewriteRequest req) {
        if (!req.isHasPreviousTurn()) {
            return null;
        }
        LinkedHashMap<String, Object> p = new LinkedHashMap<>();
        p.put("intentCode", req.getPreviousIntentCode());
        p.put("pathCode", req.getPreviousPathCode());
        p.put("structuredIntentDetail", req.getPreviousStructuredIntentDetail());
        p.put("timeLabel", req.getPreviousTimeLabel());
        p.put("startDate", req.getPreviousStartDate());
        p.put("endDate", req.getPreviousEndDate());
        p.put("scopeType", req.getPreviousScopeType());
        p.put("mentionedStoreName", req.getPreviousMentionedStoreName());
        p.put("mentionedDishName", req.getPreviousMentionedDishName());
        p.put("effectiveQuestion", req.getPreviousEffectiveQuestion());
        p.put("answerSummary", req.getPreviousAnswerSummary());
        p.put("resultAnchors", mapResultAnchors(req.getResultAnchors()));
        p.put(
                "resultAnchorsSummary",
                summarizeAnchors(req.getResultAnchors()));
        p.put("semanticSlots", mapSemanticSlots(req.getPreviousSemanticSlots()));
        return p;
    }

    private static Map<String, String> mapSemanticSlots(
            com.nongxinle.ai.semantic.AiQuerySemanticParseResult.SemanticSlotsPart slots) {
        if (slots == null) {
            return null;
        }
        LinkedHashMap<String, String> s = new LinkedHashMap<>();
        s.put("queryObject", blank(slots.getQueryObject()));
        s.put("operation", blank(slots.getOperation()));
        s.put("metric", blank(slots.getMetric()));
        s.put("structuredIntentDetailWire", blank(slots.getStructuredIntentDetailWire()));
        boolean any = false;
        for (String v : s.values()) {
            if (v != null) {
                any = true;
                break;
            }
        }
        return any ? s : null;
    }

    private static Map<String, List<Map<String, Object>>> mapAnchorsByType(List<AiResultAnchor> anchors) {
        if (anchors == null || anchors.isEmpty()) {
            return null;
        }
        LinkedHashMap<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (AiResultAnchor a : anchors) {
            if (a == null || !StringUtils.hasText(a.getEntityName())) {
                continue;
            }
            String type =
                    StringUtils.hasText(a.getEntityType())
                            ? a.getEntityType().trim().toUpperCase(Locale.ROOT)
                            : "UNKNOWN";
            grouped.computeIfAbsent(type, k -> new ArrayList<>()).add(singleAnchorRow(a));
        }
        return grouped.isEmpty() ? null : grouped;
    }

    private static Map<String, Object> singleAnchorRow(AiResultAnchor a) {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("entityType", blank(a.getEntityType()));
        row.put("entityName", a.getEntityName().trim());
        if (a.getRank() != null) {
            row.put("rank", a.getRank());
        }
        if (StringUtils.hasText(a.getSourcePlanType())) {
            row.put("sourcePlanType", a.getSourcePlanType().trim());
        }
        return row;
    }

    private static List<Map<String, String>> mapVisibleStores(List<String> names) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        List<Map<String, String>> out = new ArrayList<>();
        for (String n : names) {
            if (!StringUtils.hasText(n)) {
                continue;
            }
            out.add(Map.of("storeName", n.trim()));
        }
        return out;
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
            out.add(singleAnchorRow(a));
        }
        return out.isEmpty() ? null : out;
    }

    private static String summarizeAnchors(List<AiResultAnchor> anchors) {
        if (anchors == null || anchors.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (AiResultAnchor a : anchors) {
            if (a == null || !StringUtils.hasText(a.getEntityName())) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(blank(a.getEntityType()) != null ? a.getEntityType() : "?")
                    .append(": ")
                    .append(a.getEntityName().trim());
            if (a.getRank() != null) {
                sb.append(" (rank=").append(a.getRank()).append(')');
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private static String blank(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
