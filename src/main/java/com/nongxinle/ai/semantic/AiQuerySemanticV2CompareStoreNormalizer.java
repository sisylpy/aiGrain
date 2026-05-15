package com.nongxinle.ai.semantic;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 将 v2 抽象意图 {@code COMPARE_STORE} 映射为内部可路由的 intent，仅读 LLM 结构化字段（intent / metric.primaryMetric），不解析用户原文。
 * <p>
 * 合并阶段由 {@link AiQuerySemanticLlmMergeHelper} 继续落 wire（营业额/采购/经营多店对比等）。
 */
public final class AiQuerySemanticV2CompareStoreNormalizer {

    public record Result(AiQuerySemanticParseResult semantic, Map<String, Object> notes) {
        static Result of(AiQuerySemanticParseResult s, Map<String, Object> n) {
            return new Result(s, n == null || n.isEmpty() ? null : n);
        }
    }

    private AiQuerySemanticV2CompareStoreNormalizer() {
    }

    public static Result apply(AiQuerySemanticParseResult in) {
        if (in == null || in.isParseMissing() || !StringUtils.hasText(in.getIntent())) {
            return Result.of(in, null);
        }
        String iu = in.getIntent().trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (!"COMPARE_STORE".equals(iu)) {
            return Result.of(in, null);
        }

        String pmRaw = in.getMetric() != null ? in.getMetric().getPrimaryMetric() : null;
        int storeCount = in.effectiveMentionedStoreNames().size();

        if (AiQuerySemanticV2MetricPrimarySignals.isBusinessHolisticPrimary(pmRaw)) {
            Map<String, Object> notes = baseNotes(in);
            notes.put("toIntent", AiResolvedQueryIntent.BUSINESS_OVERVIEW);
            if (storeCount >= 2) {
                notes.put("degradedBusinessCompareByRevenue", Boolean.TRUE);
            }
            AiQuerySemanticParseResult out = in.toBuilder()
                    .intent(AiResolvedQueryIntent.BUSINESS_OVERVIEW)
                    .intentAction("OVERRIDE")
                    .build();
            return Result.of(out, notes);
        }
        if (AiQuerySemanticV2MetricPrimarySignals.isRevenueExplicitPrimary(pmRaw)) {
            Map<String, Object> notes = baseNotes(in);
            notes.put("toIntent", AiResolvedQueryIntent.REVENUE_OVERVIEW);
            AiQuerySemanticParseResult out = in.toBuilder()
                    .intent(AiResolvedQueryIntent.REVENUE_OVERVIEW)
                    .intentAction("OVERRIDE")
                    .build();
            return Result.of(out, notes);
        }
        if (isPurchasePrimary(pmRaw)) {
            Map<String, Object> notes = baseNotes(in);
            notes.put("toIntent", AiResolvedQueryIntent.PURCHASE_OVERVIEW);
            if (storeCount < 2) {
                notes.put("degradedToPurchaseOverview", true);
                notes.put("mentionedStoreCount", storeCount);
            }
            AiQuerySemanticParseResult out = in.toBuilder()
                    .intent(AiResolvedQueryIntent.PURCHASE_OVERVIEW)
                    .intentAction("OVERRIDE")
                    .build();
            return Result.of(out, notes);
        }
        if (storeCount >= 2) {
            Map<String, Object> notes = baseNotes(in);
            notes.put("toIntent", AiResolvedQueryIntent.BUSINESS_OVERVIEW);
            notes.put("degradedBusinessCompareByRevenue", Boolean.TRUE);
            notes.put("ambiguousCompareStorePrimaryMetric", true);
            AiQuerySemanticParseResult out = in.toBuilder()
                    .intent(AiResolvedQueryIntent.BUSINESS_OVERVIEW)
                    .intentAction("OVERRIDE")
                    .build();
            return Result.of(out, notes);
        }

        return Result.of(in, null);
    }

    private static Map<String, Object> baseNotes(AiQuerySemanticParseResult in) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fromIntent", "COMPARE_STORE");
        if (in.getMetric() != null && StringUtils.hasText(in.getMetric().getPrimaryMetric())) {
            m.put("primaryMetric", in.getMetric().getPrimaryMetric().trim());
        }
        return m;
    }

    private static boolean isPurchasePrimary(String pmRaw) {
        if (!StringUtils.hasText(pmRaw)) {
            return false;
        }
        String t = pmRaw.trim();
        if (t.contains("采购")) {
            return true;
        }
        String u = t.toUpperCase(Locale.ROOT).replace('-', '_');
        return u.contains("PURCHASE")
                || u.contains("PROCUREMENT")
                || "PROCURE".equals(u);
    }
}
