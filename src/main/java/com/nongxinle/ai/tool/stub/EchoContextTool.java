package com.nongxinle.ai.tool.stub;

import com.nongxinle.ai.tool.AiTool;
import com.nongxinle.ai.tool.ToolRequest;
import com.nongxinle.ai.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 演示用 Tool：将入参回显，便于验证 Registry + Guard 链路。 */
@Component
public class EchoContextTool implements AiTool {

    @Override
    public String name() {
        return "echo_context";
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        return ToolResult.builder()
                .success(true)
                .message("echo")
                .data(Map.of(
                        "tool", name(),
                        "args", request.getArgs() == null ? Map.of() : request.getArgs()
                ))
                .build();
    }
}
