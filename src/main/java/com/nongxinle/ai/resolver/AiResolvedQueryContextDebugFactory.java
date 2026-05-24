package com.nongxinle.ai.resolver;

import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.intake.route.SemanticDomainRouteResult;
import com.nongxinle.ai.semantic.intake.route.SemanticDomainRouteType;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@link AiResolvedQueryContext} builder 用 debug 字段组装（字段名与 map 结构不变）。
 */
public final class AiResolvedQueryContextDebugFactory {

    private AiResolvedQueryContextDebugFactory() {}

    public static String blankToNullSemantic(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    public static List<String> describeAdoptedSemanticFields(AiQuerySemanticParseResult r) {
        if (r == null) {
            return null;
        }
        List<String> keys = new ArrayList<>();
        if (StringUtils.hasText(r.getIntent())) {
            keys.add("intent");
        }
        if (StringUtils.hasText(r.getSemanticDomain())) {
            keys.add("domain");
        }
        if (StringUtils.hasText(r.getMentionedDishName())) {
            keys.add("mentionedDishName");
        }
        if (r.getConfidence() != null) {
            keys.add("confidence");
        }
        if (Boolean.TRUE.equals(r.getFollowUp())) {
            keys.add("followUp");
        }
        if (StringUtils.hasText(r.getIntentAction())) {
            keys.add("intentAction");
        }
        if (StringUtils.hasText(r.getTimeAction())) {
            keys.add("timeAction");
        }
        if (StringUtils.hasText(r.getScopeAction())) {
            keys.add("scopeAction");
        }
        if (StringUtils.hasText(r.getMetricAction())) {
            keys.add("metricAction");
        }
        if (r.getTime() != null && StringUtils.hasText(r.getTime().getTimeType())) {
            keys.add("time.timeType");
        }
        if (r.getRequestedScope() != null) {
            keys.add("requestedScope");
        }
        if (r.getSemanticSlots() != null) {
            keys.add("semanticSlots");
        }
        return keys.isEmpty() ? null : keys;
    }

    /** debug-only / deprecated 观测字段；不计入主链 adopted semantic。 */
    public static List<String> describeDeprecatedDebugSemanticFields(AiQuerySemanticParseResult r) {
        if (r == null) {
            return null;
        }
        List<String> keys = new ArrayList<>();
        if (r.getMetric() != null && StringUtils.hasText(r.getMetric().getRankingType())) {
            keys.add("metric.rankingType");
        }
        return keys.isEmpty() ? null : keys;
    }

    public static Boolean observeRouteParserDomainMismatch(
            SemanticDomainRouteResult route, AiQuerySemanticParseResult v2Raw) {
        String reason = observeRouteParserDomainMismatchReason(route, v2Raw);
        if (reason == null) {
            return null;
        }
        return reason.startsWith("domain_mismatch:") || reason.startsWith("router_ambiguous:");
    }

    public static String observeRouteParserDomainMismatchReason(
            SemanticDomainRouteResult route, AiQuerySemanticParseResult v2Raw) {
        if (v2Raw == null || v2Raw.isParseMissing()) {
            return null;
        }
        String parserDomain = normalizeRouteParserDomain(v2Raw.getSemanticDomain());
        if (!StringUtils.hasText(parserDomain)) {
            return null;
        }
        String routePrimary =
                route != null ? normalizeRouteParserDomain(route.getPrimaryDomain()) : null;
        if (StringUtils.hasText(routePrimary)) {
            if (routePrimary.equals(parserDomain)) {
                return null;
            }
            return "domain_mismatch:router=" + routePrimary + ",parser=" + parserDomain;
        }
        if (route != null && route.getRouteType() == SemanticDomainRouteType.AMBIGUOUS) {
            return "router_ambiguous:parser=" + parserDomain;
        }
        if (route != null && route.getRouteType() == SemanticDomainRouteType.UNKNOWN) {
            return "router_unknown:parser=" + parserDomain;
        }
        return null;
    }

    public static List<String> querySemanticV2EffectiveStoreNames(AiQuerySemanticParseResult r) {
        if (r == null) {
            return null;
        }
        List<String> e = r.effectiveMentionedStoreNames();
        return e == null || e.isEmpty() ? null : new ArrayList<>(e);
    }

    private static String normalizeRouteParserDomain(String domain) {
        if (!StringUtils.hasText(domain)) {
            return null;
        }
        String normalized = domain.trim().toUpperCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }
}
