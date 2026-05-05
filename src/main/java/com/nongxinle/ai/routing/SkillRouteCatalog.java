package com.nongxinle.ai.routing;

import cn.hutool.core.util.StrUtil;
import com.nongxinle.ai.orchestration.ChatRouteSource;
import com.nongxinle.ai.orchestration.SkillRouteFallback;
import com.nongxinle.ai.orchestration.SkillSelectionResult;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Skill 路由编目：声明「问法 → 缺省补全哪些 skill 文件」，
 * 与 {@link com.nongxinle.ai.orchestration.SkillRouteFallback} 的关键词兜底互补，并修正模型漏选。
 * <p>
 * 编排顺序：DeepSeek JSON → {@code SkillRouteFallback.apply} → 本类 {@link #enrich}。
 * </p>
 */
@Slf4j
public final class SkillRouteCatalog {

    private static final List<SkillEnrichmentRule> RULES = buildRules();

    private SkillRouteCatalog() {
    }

    private static List<SkillEnrichmentRule> buildRules() {
        List<SkillEnrichmentRule> r = new ArrayList<>();
        // 采购单价/进价波动或极值排行：必须进采购结构 skill，才能稳定消费 procurement-structure 提示与事实块组织方式
        r.add(new SkillEnrichmentRule(10, "procurement_unit_price_volatility",
                SkillRouteFallback::shouldAttachPurchasePriceVolatilityFacts,
                List.of("ai-skill-procurement-structure.md"), "procurement",
                List.of("procurement_buy_subtotal_mtd")));
        r.add(new SkillEnrichmentRule(20, "supplier_unsettled",
                m -> SkillRouteFallback.shouldAttachSupplierUnsettledFacts(m, null),
                List.of("ai-skill-procurement-structure.md"), "supplier",
                List.of("procurement_amount")));
        r.add(new SkillEnrichmentRule(25, "supplier_order_delivery_channel",
                SkillRouteFallback::shouldAttachSupplierOrderPurchaseFacts,
                List.of("ai-skill-procurement-structure.md"), null,
                List.of("procurement_amount")));
        r.add(new SkillEnrichmentRule(30, "reorder_habit_rhythm",
                SkillRouteFallback::shouldAttachReorderHabitFacts,
                List.of("ai-skill-procurement-structure.md"), "procurement",
                List.of("procurement_amount")));
        r.add(new SkillEnrichmentRule(40, "procurement_keywords",
                SkillRouteFallback::matchesProcurementStructureIntent,
                List.of("ai-skill-procurement-structure.md"), "procurement",
                List.of("procurement_amount")));
        r.add(new SkillEnrichmentRule(50, "dish_cost_diagnosis",
                SkillRouteFallback::matchesDishCostDiagnosisIntent,
                List.of("ai-skill-dish-cost-diagnosis.md"), null,
                List.of()));
        r.sort(Comparator.comparingInt(SkillEnrichmentRule::priority));
        return List.copyOf(r);
    }

    /**
     * 在初选结果上合并缺省 skill，并可选补全 costFacet。
     */
    public static SkillSelectionResult enrich(String userMessage, SkillSelectionResult primary) {
        if (primary == null) {
            return new SkillSelectionResult("none", null, false, null, false, ChatRouteSource.RULE_FALLBACK, List.of(),
                    null);
        }
        LinkedHashSet<String> skills = parseSkillCsv(primary.skillsCsv());
        String facet = primary.costFacet();
        LinkedHashSet<String> metricIds = new LinkedHashSet<>(primary.suggestedMetricIds());
        List<String> matched = new ArrayList<>();
        boolean touched = false;

        for (SkillEnrichmentRule rule : RULES) {
            if (!rule.match().test(userMessage)) {
                continue;
            }
            matched.add(rule.id());
            for (String file : rule.addSkills()) {
                String norm = normalizeSkillFilename(file);
                if (skills.add(norm)) {
                    touched = true;
                }
            }
            for (String mid : rule.metricIds()) {
                if (StrUtil.isNotBlank(mid) && metricIds.add(mid.trim())) {
                    touched = true;
                }
            }
            if (StrUtil.isNotBlank(rule.costFacetIfBlank()) && StrUtil.isBlank(facet)) {
                facet = rule.costFacetIfBlank();
                touched = true;
            }
        }

        touched = ensureCostFamilyForVolatility(userMessage, skills) || touched;
        touched = ensureProcurementMetricForVolatility(userMessage, metricIds) || touched;

        if (!touched) {
            return primary;
        }

        String newCsv = skills.isEmpty() ? "none" : String.join(",", skills);
        ChatRouteSource src = primary.routeSource() == ChatRouteSource.LLM || primary.routeSource() == ChatRouteSource.RULE_FALLBACK
                ? ChatRouteSource.ENRICHED
                : primary.routeSource();

        SkillSelectionResult out = new SkillSelectionResult(
                newCsv,
                facet,
                primary.broadQuestion(),
                primary.confidence(),
                primary.llmStructuredOk(),
                src,
                List.copyOf(metricIds),
                primary.skillPhaseStatTime());
        log.info("[SKILL-ROUTER] enrich matchedRuleIds={} skillsCsv={} costFacet={} suggestedMetricIds={}",
                matched, out.skillsCsv(), out.costFacet(), out.suggestedMetricIds());
        return out.withNormalizedCostFacet();
    }

    /**
     * 单价波动事实挂在 {@code queryCostData → appendPurchaseReorderSupplierFacts} 链路上，须至少命中一条「算账/采购」族 skill。
     */
    private static boolean ensureCostFamilyForVolatility(String userMessage, Set<String> skills) {
        if (!SkillRouteFallback.shouldAttachPurchasePriceVolatilityFacts(userMessage)) {
            return false;
        }
        if (hasCostFamilySkill(skills)) {
            return false;
        }
        boolean added = skills.add("ai-skill-procurement-structure.md");
        if (added) {
            log.info("[SKILL-ROUTER] enrich volatility_safety_net added ai-skill-procurement-structure.md");
        }
        return added;
    }

    private static boolean ensureProcurementMetricForVolatility(String userMessage, Set<String> metricIds) {
        if (!SkillRouteFallback.shouldAttachPurchasePriceVolatilityFacts(userMessage)) {
            return false;
        }
        boolean added = metricIds.add("procurement_buy_subtotal_mtd");
        if (added) {
            log.info("[SKILL-ROUTER] enrich volatility_safety_net metric procurement_buy_subtotal_mtd");
        }
        return added;
    }

    private static boolean hasCostFamilySkill(Set<String> skills) {
        for (String s : skills) {
            String x = s.toLowerCase(Locale.ROOT);
            if (x.contains("ai-skill-cost.md")
                    || x.contains("ai-skill-procurement-structure.md")
                    || x.contains("ai-skill-dish-cost-diagnosis.md")
                    || x.contains("ai-skill-profit-pilot.md")) {
                return true;
            }
        }
        return false;
    }

    static LinkedHashSet<String> parseSkillCsv(String skillsCsv) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (StrUtil.isBlank(skillsCsv)) {
            return out;
        }
        String t = skillsCsv.trim().toLowerCase(Locale.ROOT);
        if ("none".equals(t) || "null".equalsIgnoreCase(t)) {
            return out;
        }
        for (String part : skillsCsv.split("[,，]")) {
            if (StrUtil.isNotBlank(part)) {
                out.add(normalizeSkillFilename(part.trim()));
            }
        }
        return out;
    }

    static String normalizeSkillFilename(String name) {
        String n = name.trim().toLowerCase(Locale.ROOT);
        if (!n.endsWith(".md")) {
            n = n + ".md";
        }
        return n;
    }
}
