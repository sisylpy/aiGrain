package com.nongxinle.ai.semantic.contract.canonicalizer;

import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * P4-J2：canonicalizer 降级路径 — 仅大小写 / 空值 / Lexicon wire 格式整理。
 * <p>Contract selection only from {@code selectedContractId}；禁止按槽位推断 wire 或改选合同。
 */
public final class ContractFrameLightNormalizer {

    private ContractFrameLightNormalizer() {}

    public static AiQuerySemanticParseResult normalize(AiQuerySemanticParseResult raw) {
        if (raw == null || raw.isParseMissing() || raw.getSemanticSlots() == null) {
            return raw;
        }
        if (!SemanticContractCompletionEngine.hasSelectedContractId(raw)) {
            return raw;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = raw.getSemanticSlots();
        String wire =
                StringUtils.hasText(s.getStructuredIntentDetailWire())
                        ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                                s.getStructuredIntentDetailWire().trim())
                        : null;
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
                        .build();
        return raw.toBuilder()
                .semanticSlots(updated)
                .currentTurnStructuredIntentDetailWire(wire)
                .build();
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
