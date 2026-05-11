package com.nongxinle.ai.trace;

import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SSE 字节契约：每条 {@code data:} 行负载须为单行对象 JSON（以左花括号开头），不能是裹着整段 SSE 的另一层 JSON 字符串。
 */
class AiRunSessionSseWireTest {

    @Test
    void sseDataJsonProducesObjectJsonWithoutQuotedSseEnvelope() {
        LinkedHashMap<String, Object> env = new LinkedHashMap<>();
        env.put("displayText", "任务已接收，开始执行…");
        env.put("event", "run_started");
        env.put("runId", 42L);
        env.put("timestamp", "2030-01-01T00:00:00+08:00");
        env.put("status", "running");

        String line = AiRunSession.sseDataJson(env);

        assertThat(line).startsWith("{");
        assertThat(line.charAt(0)).isEqualTo('{');
        assertThat(line).doesNotStartWith("\"event:");
        assertThat(JSON.isValidObject(line)).isTrue();
        assertThat(JSON.parseObject(line).getString("event")).isEqualTo("run_started");
    }
}
