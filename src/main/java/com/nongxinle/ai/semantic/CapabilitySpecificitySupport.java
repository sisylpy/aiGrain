package com.nongxinle.ai.semantic;

import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.conversation.AiSemanticWireConstants;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * V2 结构化信号：用户是否已明确指定当前 capability 子类型（如采购异常细分）。
 * Java 只读该字段与 {@code selectedContractId} 一致性，不读用户原文。
 */
public final class CapabilitySpecificitySupport {

    public static final String EXPLICIT = "EXPLICIT";
    public static final String UNSPECIFIED = "UNSPECIFIED";

    private CapabilitySpecificitySupport() {}

    public static String normalize(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String t = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (EXPLICIT.equals(t)) {
            return EXPLICIT;
        }
        if (UNSPECIFIED.equals(t)) {
            return UNSPECIFIED;
        }
        return null;
    }

    public static String extract(AiQuerySemanticParseResult parse) {
        if (parse == null || parse.getSemanticSlots() == null) {
            return null;
        }
        return normalize(parse.getSemanticSlots().getCapabilitySpecificity());
    }

    public static boolean isPurchaseAnomalyContractId(String contractId) {
        return StringUtils.hasText(contractId) && contractId.trim().startsWith("purchase.anomaly.");
    }

    /**
     * 采购异常子类型是否已由结构化槽位/合同/wire/metric 唯一确定（不读用户原文）。
     * 用于 V2 已选对细分合同但未输出 {@link #EXPLICIT} 时的门禁放行。
     */
    public static boolean isPurchaseAnomalyStructurallyExplicit(
            String contractId, String structuredIntentDetailWire, String metric) {
        if (isPurchaseAnomalyContractId(contractId)) {
            return true;
        }
        String wire =
                AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(structuredIntentDetailWire);
        if (AiSemanticWireConstants.isPurchaseAnomalyDetectionWire(wire)) {
            return true;
        }
        return isPurchaseAnomalySpecificMetric(metric);
    }

    private static boolean isPurchaseAnomalySpecificMetric(String metric) {
        if (!StringUtils.hasText(metric)) {
            return false;
        }
        String m = metric.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return m.contains("UNIT_PRICE")
                || m.contains("PURCHASE_COUNT")
                || m.contains("PURCHASE_QUANTITY");
    }
}
