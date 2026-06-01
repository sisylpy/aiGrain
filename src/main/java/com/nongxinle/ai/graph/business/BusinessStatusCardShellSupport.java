package com.nongxinle.ai.graph.business;

import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/** 统一 cards[] 外壳：title / subtitle / chartType / payload / source。 */
final class BusinessStatusCardShellSupport {

    static final String STATUS_OK = "OK";
    static final String STATUS_EMPTY = "EMPTY";
    static final String STATUS_PARTIAL = "PARTIAL";
    static final String STATUS_SKIPPED = "SKIPPED";

    static final String CHART_KPI = "KPI";
    static final String CHART_TABLE = "TABLE";

    private BusinessStatusCardShellSupport() {}

    static Map<String, Object> buildCard(
            String cardType,
            String title,
            String subtitle,
            String chartType,
            Map<String, Object> payload,
            String answerPlanSource) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("cardType", cardType);
        card.put("title", title);
        card.put("subtitle", StringUtils.hasText(subtitle) ? subtitle.trim() : null);
        card.put("chartType", chartType);
        card.put("payload", payload == null ? new LinkedHashMap<>() : payload);
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("answerPlan", answerPlanSource);
        source.put("dataRef", "businessStatusCardProjection");
        card.put("source", source);
        return card;
    }

    static void putRangeFields(Map<String, Object> payload, BusinessStatusCardBuildRequest req) {
        if (payload == null || req == null) {
            return;
        }
        putIfPresent(payload, "startDate", req.getStartDate());
        putIfPresent(payload, "endDate", req.getEndDate());
        putIfPresent(payload, "reportLabel", req.getReportLabel());
        putIfPresent(payload, "scopeLabel", req.getScopeLabel());
        putIfPresent(payload, "timeLabel", req.getTimeLabel());
        putIfPresent(payload, "timeExpression", req.getTimeExpression());
        putIfPresent(payload, "periodDayCount", req.getPeriodDayCount());
        putIfPresent(payload, "compareStartDate", req.getCompareStartDate());
        putIfPresent(payload, "compareEndDate", req.getCompareEndDate());
        putIfPresent(payload, "compareLabel", req.getCompareLabel());
    }

    static void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (map == null || key == null || value == null) {
            return;
        }
        if (value instanceof String s && !StringUtils.hasText(s)) {
            return;
        }
        map.put(key, value);
    }

    static String titled(String reportLabel, String suffix) {
        if (StringUtils.hasText(reportLabel)) {
            return reportLabel.trim() + suffix;
        }
        return suffix.startsWith("·") ? suffix.substring(1) : suffix;
    }
}
