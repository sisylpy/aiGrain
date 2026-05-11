package com.nongxinle.ai.tool;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ToolRegistry {

    private final Map<String, AiTool> toolMap;

    public ToolRegistry(List<AiTool> tools) {
        this.toolMap = tools.stream().collect(Collectors.toMap(AiTool::name, Function.identity(), (a, b) -> a));
    }

    public Optional<AiTool> find(String name) {
        return Optional.ofNullable(toolMap.get(name));
    }

    public Collection<String> names() {
        return toolMap.keySet();
    }
}
