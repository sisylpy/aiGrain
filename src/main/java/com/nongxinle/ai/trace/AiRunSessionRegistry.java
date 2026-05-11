package com.nongxinle.ai.trace;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class AiRunSessionRegistry {

    private final AtomicLong idSeq = new AtomicLong(System.currentTimeMillis());
    private final Map<Long, AiRunSession> sessions = new ConcurrentHashMap<>();

    public long nextRunId() {
        return idSeq.incrementAndGet();
    }

    public void register(AiRunSession session) {
        sessions.put(session.getRunId(), session);
    }

    public Optional<AiRunSession> get(long runId) {
        return Optional.ofNullable(sessions.get(runId));
    }

    /** 进程内调试可清理；生产可配合 TTL 任务 */
    public void remove(long runId) {
        sessions.remove(runId);
    }
}
