package com.nongxinle.ai.harness.replay;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiHarnessReplayResponse {

    private long conversationId;
    /**
     * 常规断言回放为断言结果；探索型（无预期比对）可为 {@code null}。
     */
    private Boolean overallPass;
    private String frozenClockDate;
    private String caseId;

    /** true：跳过 expectations 断言，{@link #overallPass} 为 {@code null}，各轮带 {@link AiHarnessReplayRoundResult#getProbe()} */
    private Boolean exploreProbeReplay;

    /**
     * C-54：Gate-only replay 根摘要（如 {@code harnessReplayMode}、{@code gateReasonCode}）；非 Gate case 为 null。
     */
    private Map<String, Object> harnessRootSummary;

    @Builder.Default
    private List<AiHarnessReplayRoundResult> rounds = new ArrayList<>();
}
