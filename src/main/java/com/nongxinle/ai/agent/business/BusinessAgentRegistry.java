package com.nongxinle.ai.agent.business;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 子 Agent 注册表：构造期注入全部 {@link BusinessSubAgent} Bean，供 {@link MasterBusinessAgent} 编排查找。
 *
 * @see docs/ai/master-business-agent-design.md
 */
@Component
public class BusinessAgentRegistry {

    private final Map<String, BusinessSubAgent> agentsByName;

    public BusinessAgentRegistry(List<BusinessSubAgent> agents) {
        Map<String, BusinessSubAgent> map = new LinkedHashMap<>();
        List<BusinessSubAgent> safe = agents != null ? agents : List.of();
        for (BusinessSubAgent agent : safe) {
            String name = agent.agentName();
            BusinessSubAgent prev = map.put(name, agent);
            if (prev != null) {
                throw new IllegalStateException("Duplicate BusinessSubAgent agentName: " + name);
            }
        }
        this.agentsByName = Collections.unmodifiableMap(map);
    }

    public Optional<BusinessSubAgent> getAgent(String agentName) {
        return Optional.ofNullable(agentsByName.get(agentName));
    }

    public Collection<BusinessSubAgent> getAllAgents() {
        return agentsByName.values();
    }

    public boolean isRegistered(String agentName) {
        return agentsByName.containsKey(agentName);
    }
}
