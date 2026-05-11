package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.AiBusinessOverviewResult;
import com.nongxinle.ai.dto.cost.AiCostDiagnosisResult;
import com.nongxinle.ai.gateway.LlmGateway;
import com.nongxinle.ai.trace.AiSseEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class StubAnswerComposerNodeTest {

    @Mock
    private AiSseEventPublisher publisher;

    @Test
    void cost_blankLlm_shortFallbackDoesNotEmitKeyMetricsBlock() {
        LlmGateway stubLlm = (system, user) -> "";
        StubAnswerComposerNode node = new StubAnswerComposerNode(stubLlm, publisher);

        AiCostDiagnosisResult diagnosis = AiCostDiagnosisResult.builder()
                .summary("本月成本判断还不完整。")
                .riskLevel("data_incomplete")
                .keyMetrics(List.of(
                        AiCostDiagnosisResult.metric("测试指标仅应在卡片展示", "999", "元")
                ))
                .findings(List.of("采购有数据", "核销不足", "出库链不连续"))
                .recommendations(List.of("核对入库核销", "补营业额", "排损耗菜"))
                .needMoreData(true)
                .build();

        AiRunState state = AiRunState.builder()
                .runId(1L)
                .normalizedUserInput("本月成本怎么样？")
                .costDiagnosisResult(diagnosis)
                .build();

        node.run(state);

        String text = state.getFinalAnswerText();
        assertThat(text).doesNotContain("关键指标");
        assertThat(text).doesNotContain("999");
        assertThat(text).doesNotContain("测试指标仅应在卡片展示");
        assertThat(text).contains("成本诊断卡片");
        assertThat(text).contains("本月成本判断还不完整");
    }

    @Test
    void business_blankLlm_shortFallbackDoesNotEmitKeyMetricsBlock() {
        LlmGateway stubLlm = (system, user) -> "";
        StubAnswerComposerNode node = new StubAnswerComposerNode(stubLlm, publisher);

        AiBusinessOverviewResult overview = AiBusinessOverviewResult.builder()
                .summary("目前有营业额和采购数据。")
                .riskLevel("data_incomplete")
                .keyMetrics(List.of(
                        AiBusinessOverviewResult.metric("仅卡片", "1", "")
                ))
                .findings(List.of("差异需关注", "核销要补齐"))
                .recommendations(List.of("先补链路", "再做贡献分析"))
                .needMoreData(true)
                .build();

        AiRunState state = AiRunState.builder()
                .runId(2L)
                .normalizedUserInput("这个月生意怎么样？")
                .businessOverviewResult(overview)
                .build();

        node.run(state);

        String text = state.getFinalAnswerText();
        assertThat(text).doesNotContain("关键指标");
        assertThat(text).doesNotContain("仅卡片");
        assertThat(text).contains("经营概览卡片");
    }

    @Test
    void fallbackStripRemovesTechnicalLines_echoedFromLlm() {
        LlmGateway evil = (system, user) ->
                "开始\ndataPlanTools 为空。\ntoolResults 为空。\n系统尚未执行任何数据查询工具\n结束";
        StubAnswerComposerNode node = new StubAnswerComposerNode(evil, publisher);
        AiRunState state = AiRunState.builder()
                .runId(4L)
                .normalizedUserInput("随便闲聊")
                .statStartDate("2026-05-01")
                .statEndDate("2026-05-31")
                .workspaceMode(com.nongxinle.ai.core.AiWorkspaceMode.BUSINESS_CHAT)
                .build();
        node.run(state);
        assertThat(state.getFinalAnswerText()).doesNotContain("dataPlanTools");
        assertThat(state.getFinalAnswerText()).doesNotContain("toolResults");
        assertThat(state.getFinalAnswerText()).doesNotContain("系统尚未执行任何数据查询工具");
    }

}