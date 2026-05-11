package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.core.AgentNode;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.security.AiPermissionDenied;
import com.nongxinle.ai.security.AiPermissionGuard;
import com.nongxinle.ai.tool.ToolRegistry;
import com.nongxinle.ai.tool.ToolRequest;
import com.nongxinle.ai.tool.ToolResult;
import com.nongxinle.ai.trace.AiSseEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class EchoStubToolNode implements AgentNode {

    private final ToolRegistry toolRegistry;
    private final AiPermissionGuard permissionGuard;
    private final AiSseEventPublisher publisher;

    @Override
    public String name() {
        return "EchoStubTool";
    }

    @Override
    public boolean shouldRun(AiRunState state) {
        return true;
    }

    @Override
    public AiRunState run(AiRunState state) {
        long rid = state.getRunId();
        ToolRequest req = ToolRequest.builder()
                .runId(rid)
                .userId(state.getUserId())
                .toolName("echo_context")
                .args(new HashMap<>(Map.of("q", state.getNormalizedUserInput() == null ? "" : state.getNormalizedUserInput())))
                .build();

        var dec = permissionGuard.evaluateToolInvocation(state, req);
        if (!dec.isAllowed()) {
            AiPermissionDenied denial = dec.getDenial();
            Map<String, Object> ex = new HashMap<>();
            ex.put("tool", "echo_context");
            publisher.publishError(rid,
                    denial != null ? denial.getReason() : "无权调用演示工具",
                    "tool permission denied",
                    "TOOL_PERMISSION_DENIED",
                    "BusinessError",
                    ex,
                    denial);
            return state;
        }

        publisher.publish(rid, "agent_started", Map.of(
                "agent", "EchoStubTool",
                "displayText", "演示工具链路开始…"
        ));

        publisher.publish(rid, "tool_started", Map.of(
                "tool", "echo_context",
                "displayText", "正在执行演示工具 echo_context…"
        ));

        ToolResult result = toolRegistry.find("echo_context")
                .orElseThrow(() -> new IllegalStateException("echo_context tool not registered"))
                .execute(req);
        state.getToolResults().put("echo_context", result.getData());

        publisher.publish(rid, "tool_finished", Map.of(
                "tool", "echo_context",
                "displayText", "演示工具已返回",
                "success", result.isSuccess()
        ));

        publisher.publish(rid, "agent_finished", Map.of(
                "agent", "EchoStubTool",
                "displayText", "演示工具链路结束"
        ));
        return state;
    }
}
