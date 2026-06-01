package com.nongxinle.ai.scope;

import com.nongxinle.ai.platform.dto.AiRunCreateRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiConversationScopeModeInferForRunTest {

    @Test
    void requestScopeModeOverridesConversationCode() {
        AiRunCreateRequest req = new AiRunCreateRequest();
        req.setScopeMode("STORE");
        req.setDistributerId(2L);

        assertThat(AiConversationScopeMode.inferForRun(req, AiConversationScopeMode.GROUP.getCode()))
                .isEqualTo(AiConversationScopeMode.STORE);
    }

    @Test
    void fallsBackToConversationCodeWhenRequestHasNoMode() {
        AiRunCreateRequest req = new AiRunCreateRequest();
        req.setDistributerId(2L);

        assertThat(AiConversationScopeMode.inferForRun(req, AiConversationScopeMode.GROUP.getCode()))
                .isEqualTo(AiConversationScopeMode.GROUP);
    }
}
