package com.nongxinle.ai.semantic.contract.canonicalizer;

import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * P4-J2：contract-locked parse 轻量规范化。
 * <p>Contract selection only from {@code selectedContractId}；{@code structuredIntentDetailWire} 以
 * {@link SemanticContractCompletionEngine} completion trace / contract entry 为准，不用 LLM 槽位 wire 抢主链。
 */
public final class ContractFrameLightNormalizer {

    private ContractFrameLightNormalizer() {}

    public static AiQuerySemanticParseResult normalize(AiQuerySemanticParseResult raw) {
        if (raw == null || raw.isParseMissing() || raw.getSemanticSlots() == null) {
            return raw;
        }
        if (!SemanticContractCompletionEngine.isContractLockedParse(raw)) {
            return raw;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = raw.getSemanticSlots();
        String wire = resolveAuthoritativeWire(raw, s);
        AiQuerySemanticParseResult.SemanticSlotsPart updated =
                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                        .selectedContractId(trim(s.getSelectedContractId()))
                        .queryObject(normalizeToken(s.getQueryObject()))
                        .operation(normalizeToken(s.getOperation()))
                        .metric(normalizeToken(s.getMetric()))
                        .sourceFacet(normalizeToken(s.getSourceFacet()))
                        .anchorPolicy(normalizeToken(s.getAnchorPolicy()))
                        .detailWanted(normalizeToken(s.getDetailWanted()))
                        .structuredIntentDetailWire(wire)
                        .answerPlanType(normalizeToken(s.getAnswerPlanType()))
                        .mentionedDishName(trim(raw.effectiveMentionedDishName()))
                        .mentionedGoodsName(trim(raw.effectiveMentionedGoodsName()))
                        .requestedTargetGrossMarginRate(trim(s.getRequestedTargetGrossMarginRate()))
                        .expiryRiskFilter(trim(s.getExpiryRiskFilter()))
                        .build();
        return raw.toBuilder()
                .semanticSlots(updated)
                .mentionedDishName(trim(raw.effectiveMentionedDishName()))
                .mentionedGoodsName(trim(raw.effectiveMentionedGoodsName()))
                .currentTurnStructuredIntentDetailWire(wire)
                .build();
    }

    private static String resolveAuthoritativeWire(
            AiQuerySemanticParseResult raw, AiQuerySemanticParseResult.SemanticSlotsPart s) {
        java.util.Map<String, Object> trace = raw.getContractCompletionTrace();
        if (trace != null) {
            Object completed = trace.get("completedWire");
            if (completed instanceof String cs && StringUtils.hasText(cs)) {
                return AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(cs.trim());
            }
            Object w = trace.get("wire");
            if (w instanceof String ws && StringUtils.hasText(ws)) {
                return AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(ws.trim());
            }
        }
        if (s != null && StringUtils.hasText(s.getStructuredIntentDetailWire())) {
            return AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                    s.getStructuredIntentDetailWire().trim());
        }
        return null;
    }

    private static String normalizeToken(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private static String trim(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
