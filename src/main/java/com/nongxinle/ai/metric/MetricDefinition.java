package com.nongxinle.ai.metric;

import lombok.Builder;
import lombok.Value;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单条指标目录定义（由 {@code ai-metrics/*.yaml} 加载）。
 * <p>字段名尽量覆盖 GPT 建议骨架；{@code datasource} / {@code fields} / {@code filters} 一期供文档与排障，
 * 执行层仍以现有 Java Provider 为主，逐步迁移。</p>
 */
@Value
@Builder
public class MetricDefinition {

    String catalogVersion;
    String metricId;
    String name;
    String description;
    String category;
    String unit;
    /** number | ratio */
    String valueType;
    List<String> dimensions;
    String providerKey;
    String providerMethod;
    Map<String, String> fieldMap;
    Map<String, String> filters;
    DepartmentScopeStrategy departmentScope;
    StoreScopeStrategy storeScope;
    String timeDefault;
    String timeField;
    String aggregationType;
    Integer displayPrecision;
    Boolean displayHighlight;

    public static MetricDefinition fromYamlMap(String catalogVersion, Map<String, Object> m) {
        if (m == null || m.isEmpty()) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> ds = (Map<String, Object>) m.get("datasource");
        String providerKey = ds != null ? str(ds.get("provider")) : null;
        String providerMethod = ds != null ? str(ds.get("method")) : null;

        @SuppressWarnings("unchecked")
        Map<String, Object> scopeMap = (Map<String, Object>) m.get("scope_strategy");
        DepartmentScopeStrategy deptScope = DepartmentScopeStrategy.SELF_OR_CHILDREN;
        StoreScopeStrategy storeScope = StoreScopeStrategy.CURRENT;
        if (scopeMap != null) {
            deptScope = DepartmentScopeStrategy.fromYaml(scopeMap.get("department_scope"));
            storeScope = StoreScopeStrategy.fromYaml(scopeMap.get("store_scope"));
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> timeMap = (Map<String, Object>) m.get("time_strategy");
        String timeDefault = timeMap != null ? str(timeMap.get("default")) : "MTD";
        String timeField = timeMap != null ? str(timeMap.get("field")) : null;

        @SuppressWarnings("unchecked")
        Map<String, Object> agg = (Map<String, Object>) m.get("aggregation");
        String aggType = agg != null ? str(agg.get("type")) : "sum";

        @SuppressWarnings("unchecked")
        Map<String, Object> disp = (Map<String, Object>) m.get("display");
        Integer prec = null;
        Boolean hl = null;
        if (disp != null) {
            if (disp.get("precision") != null) {
                prec = ((Number) disp.get("precision")).intValue();
            }
            if (disp.get("highlight") instanceof Boolean b) {
                hl = b;
            }
        }

        return MetricDefinition.builder()
                .catalogVersion(catalogVersion)
                .metricId(firstStr(m, "metric_id", "metricId"))
                .name(str(m.get("name")))
                .description(str(m.get("description")))
                .category(str(m.get("category")))
                .unit(str(m.get("unit")))
                .valueType(str(m.get("value_type")))
                .dimensions(toStrList(m.get("dimensions")))
                .providerKey(providerKey)
                .providerMethod(providerMethod)
                .fieldMap(toStrStrMap(m.get("fields")))
                .filters(toStrStrMap(m.get("filters")))
                .departmentScope(deptScope)
                .storeScope(storeScope)
                .timeDefault(timeDefault)
                .timeField(timeField)
                .aggregationType(aggType)
                .displayPrecision(prec)
                .displayHighlight(hl)
                .build();
    }

    private static String firstStr(Map<String, Object> m, String... keys) {
        for (String k : keys) {
            Object v = m.get(k);
            if (v != null && !v.toString().isBlank()) {
                return v.toString().trim();
            }
        }
        return null;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private static List<String> toStrList(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        return list.stream().map(Object::toString).map(String::trim).toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> toStrStrMap(Object raw) {
        if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (e.getKey() != null && e.getValue() != null) {
                out.put(e.getKey().toString().trim(), e.getValue().toString().trim());
            }
        }
        return Collections.unmodifiableMap(out);
    }
}
