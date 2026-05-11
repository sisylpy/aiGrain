package com.nongxinle.ai.platform.dto;

/**
 * {@code POST /ai/runs} 同步返回：{@code runId} 订阅 SSE；{@code conversationId} 由首轮创建或客户端回传，须持久化以便多轮追问。
 */
public record AiRunStartResult(long runId, Long conversationId) {
}
