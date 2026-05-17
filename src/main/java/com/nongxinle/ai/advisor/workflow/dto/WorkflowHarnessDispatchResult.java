package com.nongxinle.ai.advisor.workflow.dto;

/**
 * Workflow → Harness 调度成功后立刻返回的快照（与异步 Graph 执行进度无关）。
 */
public record WorkflowHarnessDispatchResult(long runId, Long conversationId, String status) {}
