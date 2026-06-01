package com.nongxinle.ai.graph.business;

import lombok.Builder;
import lombok.Value;

import java.util.LinkedHashMap;
import java.util.Map;

/** 配料可支撑天数：日均销量/用量统计基线（与库存快照分离）。 */
@Value
@Builder
public class DishIngredientCoverSalesBaseline {

    public static final int DEFAULT_BASELINE_DAYS = 7;

    public static final String SOURCE_DEFAULT_LAST_7_DAYS = "DEFAULT_LAST_7_DAYS";
    public static final String SOURCE_USER_EXPLICIT_TIME_WINDOW = "USER_EXPLICIT_TIME_WINDOW";

    String startDateIso;
    String stopDateIso;
    int baselineDays;
    String baselineSource;
    String displayLabel;

    Map<String, Object> toWireMap() {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("startDate", startDateIso);
        m.put("stopDate", stopDateIso);
        m.put("baselineDays", baselineDays);
        m.put("baselineSource", baselineSource);
        m.put("displayLabel", displayLabel);
        return m;
    }

    @SuppressWarnings("unchecked")
    static DishIngredientCoverSalesBaseline fromWireMap(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return null;
        }
        Object start = map.get("startDate");
        Object stop = map.get("stopDate");
        if (start == null || stop == null) {
            return null;
        }
        int days = 7;
        Object daysObj = map.get("baselineDays");
        if (daysObj instanceof Number n) {
            days = Math.max(1, n.intValue());
        }
        String source = map.get("baselineSource") == null ? SOURCE_DEFAULT_LAST_7_DAYS : map.get("baselineSource").toString();
        String label = map.get("displayLabel") == null ? null : map.get("displayLabel").toString();
        return DishIngredientCoverSalesBaseline.builder()
                .startDateIso(start.toString().trim())
                .stopDateIso(stop.toString().trim())
                .baselineDays(days)
                .baselineSource(source)
                .displayLabel(label)
                .build();
    }
}
