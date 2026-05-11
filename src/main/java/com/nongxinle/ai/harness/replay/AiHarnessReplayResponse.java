package com.nongxinle.ai.harness.replay;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiHarnessReplayResponse {

    private long conversationId;
    private boolean overallPass;
    private String frozenClockDate;
    private String caseId;

    @Builder.Default
    private List<AiHarnessReplayRoundResult> rounds = new ArrayList<>();
}
