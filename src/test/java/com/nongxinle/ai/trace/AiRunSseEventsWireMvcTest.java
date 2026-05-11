package com.nongxinle.ai.trace;

import com.alibaba.fastjson2.support.spring6.http.converter.FastJsonHttpMessageConverter;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.platform.AiRunService;
import com.nongxinle.controller.AiRunController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 断言 Spring SSE 写入层：正文以 {@code event:} 起头（首字节应为 {@code e}），不得整段被打成 JSON 字符串（前缀 {@code "}）。
 */
@ExtendWith(MockitoExtension.class)
class AiRunSseEventsWireMvcTest {

    @Mock
    private AiRunService aiRunService;

    private AiRunSessionRegistry registry;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        registry = new AiRunSessionRegistry();
        AiRunController controller = new AiRunController(aiRunService, registry);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(
                        new StringHttpMessageConverter(StandardCharsets.UTF_8),
                        new FastJsonHttpMessageConverter())
                .build();
    }

    @Test
    void eventsStream_payloadNotJsonQuotedSseFrame() throws Exception {
        long runId = 4242L;
        AiRunSession session = new AiRunSession(runId, new AiRunState());
        registry.register(session);

        LinkedHashMap<String, Object> env = new LinkedHashMap<>();
        env.put("displayText", "任务已接收，开始执行…");
        env.put("event", "run_started");
        env.put("runId", runId);
        env.put("timestamp", "2030-01-01T00:00:00+08:00");
        env.put("status", "running");
        session.appendEnvelope(env);

        MvcResult started = mockMvc.perform(get("/ai/runs/" + runId + "/events"))
                .andExpect(request().asyncStarted())
                .andReturn();

        session.completeEmitters();

        byte[] raw = mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertThat(raw.length).isGreaterThan(10);
        assertThat(raw[0]).as("first wire byte must be 'e' of event:")
                .isEqualTo((byte) 'e');

        String head = new String(raw, 0, Math.min(raw.length, 80), StandardCharsets.UTF_8);
        assertThat(head).startsWith("event:run_started");
        assertThat(head).doesNotStartWith("\"event:");
        assertThat(head).doesNotContain("\"event:run_started\\ndata:");
        assertThat(head).doesNotContain("\\n\"");
    }
}
