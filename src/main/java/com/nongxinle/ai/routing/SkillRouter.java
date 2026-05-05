package com.nongxinle.ai.routing;

import com.nongxinle.ai.orchestration.SkillRouteFallback;
import com.nongxinle.ai.orchestration.SkillSelectionResult;
import org.springframework.stereotype.Component;

/**
 * Skill 路由门面：先 LLM 结构化或规则兜底，再按 {@link SkillRouteCatalog} 做缺省合并。
 * <p>单价/进价/波动类问法依赖 {@link SkillRouteFallback#shouldAttachPurchasePriceVolatilityFacts} 与编目规则
 * 将 {@code ai-skill-procurement-structure.md} 等接入事实查询主链。</p>
 */
@Component
public class SkillRouter {

    /**
     * @param userMessage 用户本轮原话
     * @param llmParsed   第一步 DeepSeek 解析结果（可为空字段）
     */
    public SkillSelectionResult route(String userMessage, SkillSelectionResult llmParsed) {
        SkillSelectionResult primary = SkillRouteFallback.apply(userMessage, llmParsed);
        return SkillRouteCatalog.enrich(userMessage, primary);
    }
}
