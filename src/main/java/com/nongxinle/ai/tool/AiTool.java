package com.nongxinle.ai.tool;

/** 可调用的业务能力单元；Agent 不直连 DB，只通过 Tool。 */
public interface AiTool {

    String name();

    ToolResult execute(ToolRequest request);
}
