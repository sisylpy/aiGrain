package com.nongxinle.ai.routing;

import java.util.List;
import java.util.function.Predicate;

/**
 * Skill 合并规则：在 LLM / {@link com.nongxinle.ai.orchestration.SkillRouteFallback} 初选之后，
 * 按问法补齐缺省 skill，避免「单价波动」等仅命中营收类模型输出而无法进入成本/采购事实链路。
 *
 * @param priority         数值越小越先执行（仅决定 facet 填空顺序；skill 合并顺序无关）
 * @param id               日志与排障用稳定 ID
 * @param match            用户原句是否命中该路由意图
 * @param addSkills        若当前 CSV 中尚无该文件名则追加（文件名须含 {@code ai-skill-}）
 * @param costFacetIfBlank 仅当当前 costFacet 为空时写入，便于下游 procurement/supplier 子意图
 * @param metricIds        关联 {@code ai-metrics/catalog-v1.yaml} 中的 metric_id（供事实层与时间/范围解析）
 */
public record SkillEnrichmentRule(
        int priority,
        String id,
        Predicate<String> match,
        List<String> addSkills,
        String costFacetIfBlank,
        List<String> metricIds
) {
    public SkillEnrichmentRule {
        metricIds = metricIds == null ? List.of() : List.copyOf(metricIds);
    }
}
