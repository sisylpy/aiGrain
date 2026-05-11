package com.nongxinle.ai.core;

/**
 * Agent 图节点：轻量编排单元（与架构文档 §6 一致）。
 */
public interface AgentNode {

    String name();

    boolean shouldRun(AiRunState state);

    AiRunState run(AiRunState state);
}
