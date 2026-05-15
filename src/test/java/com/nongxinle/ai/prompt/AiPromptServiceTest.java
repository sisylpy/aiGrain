package com.nongxinle.ai.prompt;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiPromptServiceTest {

    @Test
    void require_loadsComposerBusinessOverviewBody_trimmedMarkdownHeader() {
        AiPromptService svc = new AiPromptService(new DefaultResourceLoader(), new AiPromptRegistry());
        String body = svc.require(AiPromptIds.COMPOSER_BUSINESS_OVERVIEW_V1);
        assertThat(body).startsWith("【Harness 约束（必须遵守）】");
        assertThat(body).contains("你是餐饮门店/集团经营 AI 助手");
        assertThat(body).doesNotContain("# Prompt ID");
        assertThat(body).doesNotContain("# Prompt 正文");
    }

    @Test
    void unknownPromptId_raises() {
        AiPromptService svc = new AiPromptService(new DefaultResourceLoader(), new AiPromptRegistry());
        assertThatThrownBy(() -> svc.require("not.a.prompt"))
                .isInstanceOf(AiPromptNotFoundException.class)
                .hasMessageContaining("unknown promptId");
    }
}
