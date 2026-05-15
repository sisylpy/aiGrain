package com.nongxinle.ai.agent.business;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本轮子 Agent 调度计划（阶段 A 骨架；未接入 Graph）。
 *
 * @see docs/ai/master-business-agent-design.md
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessAgentDispatchPlan {

    private String dispatchId;
    private String intentCode;
    private String pathCode;

    @Builder.Default
    private List<BusinessAgentDispatchStep> steps = new ArrayList<>();

    private boolean parallelAllowed;

    @Builder.Default
    private Map<String, Object> debug = new LinkedHashMap<>();
}
