package com.nongxinle.ai.harness.replay;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiHarnessReplayRoundResult {

    private int roundIndex;
    private String message;
    private long runId;
    private long conversationId;

    private Map<String, Object> resolvedQueryContextSummary = new LinkedHashMap<>();

    /**
     * 探索型 Replay 专用：从 {@link #resolvedQueryContextSummary} 抽取的扁平字段，便于审阅；常规断言模式为 null。
     */
    private Map<String, Object> probe;

    private boolean pass;

    private List<AiHarnessMismatch> failedFields = new ArrayList<>();
}
