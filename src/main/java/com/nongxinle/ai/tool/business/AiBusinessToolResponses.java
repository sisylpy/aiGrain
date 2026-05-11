package com.nongxinle.ai.tool.business;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AiBusinessToolResponses {

    public static Map<String, Object> envelope(String toolId, boolean success, boolean mock,
            String startDate, String stopDate, Long departmentFatherId, Long disId,
            Map<String, Object> data, String note) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("schemaVersion", "v1");
        m.put("tool", toolId);
        m.put("success", success);
        m.put("mock", mock);
        if (startDate != null) {
            m.put(AiBusinessToolIds.ARG_START_DATE, startDate);
        }
        if (stopDate != null) {
            m.put(AiBusinessToolIds.ARG_STOP_DATE, stopDate);
        }
        if (departmentFatherId != null) {
            m.put(AiBusinessToolIds.ARG_DEPARTMENT_FATHER_ID, departmentFatherId);
        }
        if (disId != null) {
            m.put(AiBusinessToolIds.ARG_DIS_ID, disId);
        }
        if (note != null && !note.isEmpty()) {
            m.put("note", note);
        }
        m.put("data", data == null ? Map.of() : data);
        return m;
    }

    /**
     * 半真实占位：权限/Trace/SSE 已走通，payload 可先 mock。
     */
    public static Map<String, Object> mockPayload(String detail) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mockPlaceholder", true);
        m.put("detail", detail);
        return m;
    }
}
