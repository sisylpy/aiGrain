package com.nongxinle.ai.composer.renderer;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.util.AiNumericPlainText;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * Shared helpers for deterministic renderers (tool envelopes, numeric hints).
 */
final class DeterministicRendererSupport {

    private DeterministicRendererSupport() {
    }

    /**
     * V2 canonical structured wire from {@link AiResolvedQueryIntent#getStructuredIntentDetail()} only.
     * Does not read {@code metric.rankingType} (D-CLEAN-RENDERER-FALLBACK-FINAL).
     */
    static String resolveStructuredIntentDetailWireFromQueryIntent(AiRunState state) {
        if (state == null || state.getResolvedQueryContext() == null) {
            return null;
        }
        AiResolvedQueryIntent qi = state.getResolvedQueryContext().getQueryIntent();
        if (qi == null || !StringUtils.hasText(qi.getStructuredIntentDetail())) {
            return null;
        }
        return AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(qi.getStructuredIntentDetail().trim());
    }

    static String nz(String s) {
        return s == null ? "" : s;
    }

    static String nz(Object o) {
        return o == null ? "" : o.toString();
    }

    static String plainNumericHint(Object v) {
        if (v == null) {
            return "暂无";
        }
        if (v instanceof java.math.BigDecimal bd) {
            return AiNumericPlainText.plainNumber(bd);
        }
        if (v instanceof Number n) {
            return AiNumericPlainText.plainNumber(n);
        }
        String s = v.toString().trim();
        return s.isEmpty() ? "暂无" : s;
    }

    static int intHint(Object v) {
        if (v == null) {
            return 0;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(v.toString().trim());
        } catch (Exception e) {
            return 0;
        }
    }

    static double parseDoubleLoose(Object v) {
        if (v == null) {
            return 0;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(v.toString().trim());
        } catch (Exception e) {
            return 0;
        }
    }
}
