package com.nongxinle.controller;

import com.alibaba.fastjson2.JSON;
import com.nongxinle.ai.platform.AiRunService;
import com.nongxinle.ai.platform.dto.AiRunStartResult;
import com.nongxinle.ai.platform.dto.AiRunCreateRequest;
import com.nongxinle.ai.trace.AiRunSessionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ai Run REST 冒烟：不启动 Spring 容器，避免无数据源时 MyBatis Mapper 无法装配。
 */
@ExtendWith(MockitoExtension.class)
class AiRunControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AiRunService aiRunService;

    @Mock
    private AiRunSessionRegistry aiRunSessionRegistry;

    @InjectMocks
    private AiRunController aiRunController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(aiRunController).build();
    }

    @Test
    void createRun_returnsRunId() throws Exception {
        when(aiRunService.startRun(any(AiRunCreateRequest.class)))
                .thenReturn(new AiRunStartResult(918273645L, 88L));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", 101L);
        body.put("message", "你好");

        mockMvc.perform(post("/ai/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(918273645))
                .andExpect(jsonPath("$.conversationId").value(88))
                .andExpect(jsonPath("$.status").value("STARTED"));

        ArgumentCaptor<AiRunCreateRequest> cap = ArgumentCaptor.forClass(AiRunCreateRequest.class);
        verify(aiRunService).startRun(cap.capture());
        assertThat(cap.getValue().getUserId()).isEqualTo(101L);
        assertThat(cap.getValue().getMessage()).isEqualTo("你好");
    }

    @Test
    void createRun_rejectsMissingUserId() throws Exception {
        when(aiRunService.startRun(any())).thenThrow(new IllegalArgumentException("userId required"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "你好");

        mockMvc.perform(post("/ai/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }
}
