package com.nongxinle.ai.trace;

import com.nongxinle.ai.security.AiPermissionDenied;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 向当前 Run 推送 SSE：data 为统一 JSON 信封（含 event、runId、timestamp、status、可选 agent/displayText）。
 *
 * @see docs/SSE_BACKEND_EVENT_CONTRACT.md
 */
@Component
@RequiredArgsConstructor
public class AiSseEventPublisher {

    private static final ZoneId SSE_TS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final AiRunSessionRegistry registry;

    public void publish(long runId, String eventName, Map<String, Object> extraFields) {
        Map<String, Object> envelope = buildEnvelope(runId, eventName, extraFields);
        registry.get(runId).ifPresent(session -> session.appendEnvelope(envelope));
    }

    public void publish(long runId, String eventName) {
        publish(runId, eventName, null);
    }

    /**
     * 统一 {@code error} 帧：根级扁平字段 + {@code data} 对象（与 {@code SSE_BACKEND_EVENT_CONTRACT.md} 一致）。
     *
     * @param errorCode 业务可展示的稳定错误码（如 {@code TOOL_PERMISSION_DENIED}）；若为 null 则用 {@code throwableType}
     */
    public void publishError(long runId, String displayText, String message,
                             String errorCode, String throwableType, Map<String, Object> optionalExtras) {
        publishError(runId, displayText, message, errorCode, throwableType, optionalExtras, null);
    }

    /**
     * 可选在 {@code data} 内挂载 {@code permissionDenied}（结构化越权说明）。
     */
    public void publishError(long runId, String displayText, String message,
                             String errorCode, String throwableType, Map<String, Object> optionalExtras,
                             AiPermissionDenied permissionDenied) {
        LinkedHashMap<String, Object> extras = new LinkedHashMap<>(4);
        if (optionalExtras != null) {
            extras.putAll(optionalExtras);
        }
        String type = throwableType != null ? throwableType : "Error";
        String code = errorCode != null ? errorCode : type;
        String msg = message != null ? message : displayText != null ? displayText : "";
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("errorCode", code);
        data.put("message", msg);
        data.put("type", type);
        if (permissionDenied != null) {
            data.put("permissionDenied", permissionDenied.asDataMap());
        }
        extras.put("displayText", displayText != null ? displayText : msg);
        extras.put("message", msg);
        extras.put("type", type);
        extras.put("data", data);
        publish(runId, "error", extras);
    }

    private Map<String, Object> buildEnvelope(long runId, String eventName, Map<String, Object> extras) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        if (extras != null) {
            m.putAll(extras);
        }
        if (!m.containsKey("displayText") || m.get("displayText") == null) {
            m.put("displayText", defaultDisplayText(eventName));
        }
        m.put("event", eventName);
        m.put("runId", runId);
        m.put("timestamp", ZonedDateTime.now(SSE_TS_ZONE).format(TS_FMT));
        if (!m.containsKey("status") || m.get("status") == null) {
            m.put("status", defaultStatus(eventName));
        }
        return m;
    }

    private static String defaultStatus(String eventName) {
        if ("run_finished".equals(eventName)) {
            return "completed";
        }
        if ("error".equals(eventName)) {
            return "failed";
        }
        return "running";
    }

    private static String defaultDisplayText(String eventName) {
        return switch (eventName) {
            case "run_started" -> "运行已开始…";
            case "agent_started" -> "智能体已开始执行…";
            case "agent_finished" -> "智能体已结束本节…";
            case "tool_started" -> "正在调用工具…";
            case "tool_finished" -> "工具调用完成";
            case "review_started" -> "开始审核输出…";
            case "review_finished" -> "审核完成";
            case "answer_delta" -> "回答生成中";
            case "run_finished" -> "运行已结束";
            case "error" -> "发生错误";
            default -> eventName;
        };
    }
}
