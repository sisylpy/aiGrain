package com.nongxinle.ai.metric;

import com.nongxinle.ai.scope.AiQueryScope;
import lombok.Value;

import java.time.LocalDate;

/**
 * 指标时间窗（相对 {@link MetricDefinition#getTimeDefault()}）；一期仅支持 MTD。
 */
public final class MetricTimeWindows {

    private MetricTimeWindows() {
    }

    @Value
    public static class Range {
        LocalDate startInclusive;
        LocalDate endInclusive;
        /** 业务日期字段语义名，对应 YAML time_strategy.field */
        String bizDateFieldHint;
    }

    /**
     * @param scope   当前未使用，预留 GROUP / 时区
     * @param metric  指标定义（取 timeDefault / timeField）
     */
    public static Range resolve(AiQueryScope scope, MetricDefinition metric) {
        LocalDate now = LocalDate.now();
        String def = metric != null && metric.getTimeDefault() != null
                ? metric.getTimeDefault().trim().toUpperCase()
                : "MTD";
        String field = metric != null ? metric.getTimeField() : null;

        if ("RANGE".equals(def)) {
            // 二期：由请求传入起止；一期与 MTD 一致避免空窗
            return new Range(now.withDayOfMonth(1), now, field);
        }
        if ("MTD".equals(def) || def.isEmpty()) {
            return new Range(now.withDayOfMonth(1), now, field);
        }
        return new Range(now.withDayOfMonth(1), now, field);
    }
}
