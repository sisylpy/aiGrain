package com.nongxinle.ai.semantic;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * LLM 将「经营得怎么样 / 生意怎么样」误标为 {@code REVENUE_OVERVIEW} 时纠偏为 {@link AiResolvedQueryIntent#BUSINESS_OVERVIEW}（仅读
 * {@code intent} / {@code metric.primaryMetric}）。
 */
public final class AiQuerySemanticV2BusinessHolisticIntentNormalizer {

    public record Result(AiQuerySemanticParseResult semantic, Map<String, Object> notes) {
        static Result of(AiQuerySemanticParseResult s, Map<String, Object> n) {
            return new Result(s, n == null || n.isEmpty() ? null : n);
        }
    }

    private AiQuerySemanticV2BusinessHolisticIntentNormalizer() {
    }

    public static Result apply(AiQuerySemanticParseResult in) {
        if (in == null || in.isParseMissing() || !StringUtils.hasText(in.getIntent())) {
            return Result.of(in, null);
        }
        String iu = in.getIntent().trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (!"REVENUE_OVERVIEW".equals(iu) && !"REVENUE".equals(iu)) {
            return Result.of(in, null);
        }
        String pm = in.getMetric() != null ? in.getMetric().getPrimaryMetric() : null;
        if (AiQuerySemanticV2MetricPrimarySignals.isRevenueExplicitPrimary(pm)) {
            return Result.of(in, null);
        }
        if (!AiQuerySemanticV2MetricPrimarySignals.isBusinessHolisticPrimary(pm)) {
            return Result.of(in, null);
        }
        Map<String, Object> notes = new LinkedHashMap<>();
        notes.put("businessHolisticVsRevenueDeconflict", true);
        notes.put("fromIntent", iu);
        notes.put("toIntent", AiResolvedQueryIntent.BUSINESS_OVERVIEW);
        AiQuerySemanticParseResult out =
                in.toBuilder()
                        .intent(AiResolvedQueryIntent.BUSINESS_OVERVIEW)
                        .intentAction("OVERRIDE")
                        .build();
        return Result.of(out, notes);
    }
}
