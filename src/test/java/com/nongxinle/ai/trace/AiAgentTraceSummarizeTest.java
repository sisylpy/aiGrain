package com.nongxinle.ai.trace;

import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.core.AiWorkspaceMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiAgentTraceSummarizeTest {

    @Test
    void summarizeStateBefore_containsWorkspaceSnapshot() {
        AiRunState s = AiRunState.builder()
                .runId(1L)
                .workspaceMode(AiWorkspaceMode.BUSINESS_CHAT)
                .build();
        assertThat(AiAgentTraceService.summarizeStateBefore(s))
                .containsEntry("workspaceModeBefore", "BUSINESS_CHAT")
                .containsEntry("cancelledBefore", false);
    }

    @Test
    void summarizeStateAfter_includesNodeName() {
        AiRunState s = AiRunState.builder().runId(2L).build();
        assertThat(AiAgentTraceService.summarizeStateAfter(s, "EchoStub"))
                .containsEntry("node", "EchoStub");
    }
}
