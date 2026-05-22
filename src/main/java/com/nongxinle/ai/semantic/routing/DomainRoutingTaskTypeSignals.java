package com.nongxinle.ai.semantic.routing;

import lombok.experimental.UtilityClass;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
/**
 * Step 1 路由层 taskType 信号（粗粒度；非 Step 2 {@code operation} 精确语义）。
 * <p>通用问句模式，不绑定具体业务域。
 */
@UtilityClass
final class DomainRoutingTaskTypeSignals {

    static final String OVERVIEW = "OVERVIEW";
    static final String RANKING = "RANKING";
    static final String DETAIL = "DETAIL";
    static final String COMPARE = "COMPARE";
    static final String ANOMALY = "ANOMALY";
    static final String TREND = "TREND";
    static final String DIAGNOSIS = "DIAGNOSIS";

    private static final List<TaskPattern> PATTERNS =
            List.of(
                    task(OVERVIEW, "怎么样", "如何", "情况", "多少", "总览", "概况", "概况怎么样", "整体"),
                    task(RANKING, "最高", "最多", "最大", "最低", "最少", "最小", "哪个", "哪些", "排行", "排名", "前几", "比较多", "比较少", "偏少", "偏多", "偏低", "偏高"),
                    task(DETAIL, "明细", "详情", "是谁供的", "构成", "清单", "列表"),
                    task(COMPARE, "对比", "哪个更高", "哪个更低", "相比", "比对"),
                    task(ANOMALY, "异常", "波动", "突增", "问题", "风险"),
                    task(TREND, "趋势", "走势", "变化趋势"),
                    task(DIAGNOSIS, "诊断", "什么原因", "为什么"));

    static Set<String> detect(String message) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (!StringUtils.hasText(message)) {
            return out;
        }
        String text = message.trim();
        for (TaskPattern pattern : PATTERNS) {
            if (pattern.matches(text)) {
                out.add(pattern.taskType());
            }
        }
        return out;
    }

    static boolean intersectsSupported(Set<String> detected, List<String> supportedTaskTypes) {
        if (detected.isEmpty() || supportedTaskTypes == null || supportedTaskTypes.isEmpty()) {
            return false;
        }
        for (String supported : supportedTaskTypes) {
            if (!StringUtils.hasText(supported)) {
                continue;
            }
            String code = supported.trim().toUpperCase(Locale.ROOT);
            if (detected.contains(code)) {
                return true;
            }
        }
        return false;
    }

    private static TaskPattern task(String taskType, String... phrases) {
        return new TaskPattern(taskType, phrases);
    }

    private record TaskPattern(String taskType, String[] phrases) {
        boolean matches(String message) {
            for (String phrase : phrases) {
                if (message.contains(phrase)) {
                    return true;
                }
            }
            return false;
        }
    }
}
