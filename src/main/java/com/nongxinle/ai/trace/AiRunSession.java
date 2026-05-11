package com.nongxinle.ai.trace;

import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.platform.AiRunStatus;
import com.alibaba.fastjson2.JSON;
import lombok.Getter;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 单次 Run 内存会话：事件缓冲（统一 JSON 信封） + SSE 订阅。
 */
public class AiRunSession {

    @Getter
    private final long runId;
    @Getter
    private final AiRunState state;
    @Getter
    private volatile AiRunStatus status = AiRunStatus.PENDING;

    private final List<Map<String, Object>> eventEnvelopeBuffer = new ArrayList<>();
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public AiRunSession(long runId, AiRunState state) {
        this.runId = runId;
        this.state = state;
    }

    public void setStatus(AiRunStatus status) {
        this.status = status;
    }

    /** @param envelope 已由 {@link AiSseEventPublisher} 补齐 event、runId、timestamp、status 等 */
    public synchronized void appendEnvelope(Map<String, Object> envelope) {
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>(envelope);
        eventEnvelopeBuffer.add(copy);
        String sseName = String.valueOf(copy.get("event"));
        fanout(sseName, copy);
    }

    public synchronized void subscribe(SseEmitter emitter) {
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        emitters.add(emitter);
        for (Map<String, Object> env : eventEnvelopeBuffer) {
            String sseName = String.valueOf(env.get("event"));
            sendOne(emitter, sseName, env);
        }
    }

    public synchronized void completeEmitters() {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.complete();
            } catch (Exception ignored) {
                // ignore
            }
        }
        emitters.clear();
    }

    private void fanout(String sseEventName, Map<String, Object> envelope) {
        for (SseEmitter emitter : emitters) {
            sendOne(emitter, sseEventName, envelope);
        }
    }

    /** 包内可见：单测断言 SSE {@code data:} 行负载为单行对象 JSON，外层不得再嵌套 quoted SSE 片段。 */
    static String sseDataJson(Map<String, Object> envelope) {
        return JSON.toJSONString(envelope);
    }

    private void sendOne(SseEmitter emitter, String sseEventName, Map<String, Object> envelope) {
        try {
            // 只用 SseEmitter 生成 event:/data: 帧：.name(...) → event 行，.data(对象, APPLICATION_JSON) → data 行正文由 MessageConverter 写 JSON。
            // 勿拼整帧字符串再 data(...)；勿 data(预序列化字符串) 而不带 mediaType（易被当成需再编码的标量）。
            emitter.send(SseEmitter.event()
                    .name(sseEventName)
                    .data(envelope, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            emitters.remove(emitter);
            try {
                emitter.completeWithError(e);
            } catch (Exception ignored) {
                // ignore
            }
        }
    }
}
