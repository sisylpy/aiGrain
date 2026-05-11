package com.nongxinle.ai.tool;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolRequest {

    private Long runId;
    private Long userId;
    private String toolName;

    @Builder.Default
    private Map<String, Object> args = new HashMap<>();

    /**
     * 解析后的统一查询上下文；经营类 Tool 用于读时间窗、数据范围、可见门店抬头等（{@code args} 仍为执行契约主入口）。
     */
    private AiResolvedQueryContext resolvedQueryContext;
}
