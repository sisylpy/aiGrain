package com.nongxinle.ai.semantic.contract;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.contract.canonicalizer.DomainContractFrameCanonicalizeContext;
import com.nongxinle.ai.semantic.contract.canonicalizer.DomainContractFrameCanonicalizerRegistry;
import com.nongxinle.ai.semantic.frame.CurrentSemanticFrame;
import lombok.Builder;
import lombok.Value;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * Contract validation 的有效语义视图（非业务 Frame 层）：在 {@link CurrentSemanticFrame} 与 SlotMerge 之后，
 * 经 {@link DomainContractFrameCanonicalizerRegistry} 做 domain canonicalize，再供
 * {@link SemanticContractValidator} / {@link SemanticContractStrictDecisionEvaluator} 使用。
 * <p>本类不承载业务规则；各域槽位 / wire 补齐由对应 {@link com.nongxinle.ai.semantic.contract.canonicalizer.DomainContractFrameCanonicalizer}
 * 及其 Matrix 提供。命名中的 Frame 仅表示「合同校验槽位视图」，不替代 graph/composer 侧 business context frame。
 */
@Value
@Builder
public class EffectiveSemanticContractFrame {

    AiQuerySemanticParseResult parse;
    String domain;
    AiConversationTurnMemory previousTurn;
    String rewriteInheritedAnchorType;
    String rewriteInheritedAnchorName;

    public static EffectiveSemanticContractFrame of(
            AiQuerySemanticParseResult parse,
            String domain,
            AiConversationTurnMemory previousTurn,
            String rewriteInheritedAnchorType,
            String rewriteInheritedAnchorName) {
        if (parse == null || parse.isParseMissing()) {
            return null;
        }
        return EffectiveSemanticContractFrame.builder()
                .parse(parse)
                .domain(domain)
                .previousTurn(previousTurn)
                .rewriteInheritedAnchorType(rewriteInheritedAnchorType)
                .rewriteInheritedAnchorName(rewriteInheritedAnchorName)
                .build();
    }

    /** Matcher / Validator 只读槽位快照（含 domain canonicalizer / Matrix contract frame completion）。 */
    public SemanticCapabilityContractMatcher.SlotSnapshot slotSnapshot() {
        return buildSlotSnapshot(
                parse, domain, previousTurn, rewriteInheritedAnchorType, rewriteInheritedAnchorName);
    }

    /**
     * requiresAnchor 合同：prior resultAnchors、rewrite 继承实体、本轮点名实体等均可 satisfy；
     * 不因 raw {@code anchorPolicy=IGNORE_PREVIOUS_ANCHOR} 单独判违例。
     */
    public boolean hasAnchorEvidence(String anchorType) {
        if (!StringUtils.hasText(anchorType)) {
            return true;
        }
        String type = normalizeToken(anchorType);
        if ("NONE".equals(type)) {
            return true;
        }
        return switch (type) {
            case "GOODS" -> hasGoodsAnchorEvidence();
            case "SUPPLIER" -> hasSupplierAnchorEvidence();
            case "DISH" -> hasDishAnchorEvidence();
            case "STORE" -> hasStoreAnchorEvidence();
            default -> false;
        };
    }

    private boolean hasGoodsAnchorEvidence() {
        if (hasResultAnchorOfType(AiResultAnchor.ENTITY_TYPE_GOODS)) {
            return true;
        }
        return matchesRewriteAnchor(AiResultAnchor.ENTITY_TYPE_GOODS);
    }

    private boolean hasSupplierAnchorEvidence() {
        if (hasResultAnchorOfType(AiResultAnchor.ENTITY_TYPE_SUPPLIER)) {
            return true;
        }
        return matchesRewriteAnchor(AiResultAnchor.ENTITY_TYPE_SUPPLIER);
    }

    private boolean hasDishAnchorEvidence() {
        if (parse != null && StringUtils.hasText(parse.getMentionedDishName())) {
            return true;
        }
        if (hasResultAnchorOfType(AiResultAnchor.ENTITY_TYPE_DISH)) {
            return true;
        }
        return matchesRewriteAnchor(AiResultAnchor.ENTITY_TYPE_DISH);
    }

    private boolean hasStoreAnchorEvidence() {
        if (parse != null && parse.getRequestedScope() != null) {
            if (StringUtils.hasText(parse.getRequestedScope().getMentionedStoreName())) {
                return true;
            }
            var names = parse.getRequestedScope().getMentionedStoreNames();
            if (names != null && !names.isEmpty()) {
                return true;
            }
        }
        if (hasResultAnchorOfType(AiResultAnchor.ENTITY_TYPE_STORE)) {
            return true;
        }
        return matchesRewriteAnchor(AiResultAnchor.ENTITY_TYPE_STORE);
    }

    private boolean hasResultAnchorOfType(String entityType) {
        if (previousTurn == null || previousTurn.getLastResultAnchors() == null || !StringUtils.hasText(entityType)) {
            return false;
        }
        for (AiResultAnchor anchor : previousTurn.getLastResultAnchors()) {
            if (anchor == null || !StringUtils.hasText(anchor.getEntityType())) {
                continue;
            }
            if (!entityType.equalsIgnoreCase(anchor.getEntityType().trim())) {
                continue;
            }
            if (StringUtils.hasText(anchor.getEntityId()) || StringUtils.hasText(anchor.getEntityName())) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesRewriteAnchor(String entityType) {
        if (!StringUtils.hasText(rewriteInheritedAnchorName)) {
            return false;
        }
        if (!StringUtils.hasText(rewriteInheritedAnchorType)) {
            return AiResultAnchor.ENTITY_TYPE_GOODS.equals(entityType);
        }
        return entityType.equalsIgnoreCase(rewriteInheritedAnchorType.trim());
    }

    private static SemanticCapabilityContractMatcher.SlotSnapshot buildSlotSnapshot(
            AiQuerySemanticParseResult parse,
            String domain,
            AiConversationTurnMemory previousTurn,
            String rewriteInheritedAnchorType,
            String rewriteInheritedAnchorName) {
        if (parse == null || parse.isParseMissing()) {
            return SemanticCapabilityContractMatcher.SlotSnapshot.empty();
        }
        AiQuerySemanticParseResult normalized =
                DomainContractFrameCanonicalizerRegistry.canonicalize(
                        DomainContractFrameCanonicalizeContext.builder()
                                .selectedDomain(domain)
                                .parse(parse)
                                .previousTurn(previousTurn)
                                .rewriteInheritedAnchorType(rewriteInheritedAnchorType)
                                .rewriteInheritedAnchorName(rewriteInheritedAnchorName)
                                .build());

        CurrentSemanticFrame frame = CurrentSemanticFrame.buildFrame(normalized);
        AiQuerySemanticParseResult.SemanticSlotsPart slots = normalized.getSemanticSlots();
        String answerPlanType =
                slots != null && StringUtils.hasText(slots.getAnswerPlanType())
                        ? slots.getAnswerPlanType().trim()
                        : null;
        String anchorPolicy =
                frame.getAnchorPolicy() != null
                        ? frame.getAnchorPolicy()
                        : slots != null ? normalizeToken(slots.getAnchorPolicy()) : null;
        return new SemanticCapabilityContractMatcher.SlotSnapshot(
                frame.getQueryObject(),
                frame.getOperation(),
                frame.getMetric(),
                frame.getSourceFacet(),
                frame.getDetailWanted(),
                answerPlanType,
                anchorPolicy,
                frame.getStructuredIntentDetailWire());
    }

    private static String normalizeDomain(String domain) {
        return domain == null ? null : domain.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeToken(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String t = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return t.isEmpty() ? null : t;
    }
}
