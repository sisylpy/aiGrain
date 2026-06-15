package com.nongxinle.ai.workrecord.business;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class WorkRecordBusinessCardFactTextSupport {

    private WorkRecordBusinessCardFactTextSupport() {
    }

    public static String line(String label, Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return label + "：" + text;
    }

    public static String joinLines(List<String> lines) {
        List<String> kept = new ArrayList<>();
        for (String line : lines) {
            if (StringUtils.hasText(line)) {
                kept.add(line.trim());
            }
        }
        if (kept.isEmpty()) {
            return "";
        }
        return String.join("\n", kept);
    }

    public static String firstNonBlank(Map<String, Object> map, String... keys) {
        if (map == null) {
            return null;
        }
        for (String key : keys) {
            Object v = map.get(key);
            if (v != null && StringUtils.hasText(v.toString())) {
                return v.toString().trim();
            }
        }
        return null;
    }

    public static String snapshotJson(Object value) {
        if (value == null) {
            return "{}";
        }
        return com.alibaba.fastjson2.JSON.toJSONString(value);
    }
}
