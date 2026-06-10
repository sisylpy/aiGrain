package com.nongxinle.ai.semantic.inheritance;

import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.AiQuerySemanticSlotMerge;
import com.nongxinle.ai.semantic.contract.ContractBusinessSlotRequirementSupport;
import com.nongxinle.ai.semantic.contract.ContractExecutionMappingSupport;
import com.nongxinle.ai.semantic.frame.SemanticDraftSyncSupport;
import com.nongxinle.ai.semantic.contract.SemanticCapabilityContract;
import lombok.Value;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 从 ACTIVE {@link SemanticCapabilityContract} 派生完整 canonical Business Frame；
 * 不读 previousTurn slots，不与 current raw 做业务槽位 coalesce。
 */
public final class CanonicalContractFrameSupport {

    private CanonicalContractFrameSupport() {}

    @Value
    public static class CanonicalBusinessFrame {
        SemanticCapabilityContract contract;
        AiQuerySemanticParseResult.SemanticSlotsPart semanticSlots;
        AiQuerySemanticParseResult.OrchestrationDecisionCandidatePart orchestration;
        String canonicalStructuredIntentDetailWire;
    }

    public static CanonicalBusinessFrame fromActiveContract(
            SemanticCapabilityContract contract, String anchorPolicy) {
        if (contract == null) {
            return null;
        }
        String wire = authoritativeWire(contract);
        String resolvedAnchorPolicy = resolveAnchorPolicy(anchorPolicy, contract);

        AiQuerySemanticParseResult.SemanticSlotsPart slots =
                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                        .selectedContractId(contract.getContractId())
                        .queryObject(firstSetValue(contract.getQueryObjects()))
                        .operation(firstSetValue(contract.getOperations()))
                        .metric(metricFromContractEntry(contract))
                        .sourceFacet(normalizeToken(contract.getSourceFacet()))
                        .detailWanted(normalizeToken(contract.getDetailWanted()))
                        .structuredIntentDetailWire(wire)
                        .answerPlanType(normalizeToken(contract.getAnswerPlanType()))
                        .anchorPolicy(resolvedAnchorPolicy)
                        .mentionedDishName(null)
                        .build();

        AiQuerySemanticParseResult.OrchestrationDecisionCandidatePart orchestration = null;
        if (contract.getSelectedTools() != null && !contract.getSelectedTools().isEmpty()) {
            orchestration =
                    AiQuerySemanticParseResult.OrchestrationDecisionCandidatePart.builder()
                            .selectedTools(new ArrayList<>(contract.getSelectedTools()))
                            .build();
        }

        return new CanonicalBusinessFrame(contract, slots, orchestration, wire);
    }

    /**
     * 仅替换 Business Frame 白名单字段；保留 current 的 time / requestedScope / permission context。
     */
    public static AiQuerySemanticParseResult applyBusinessFrameWhitelist(
            AiQuerySemanticParseResult current, CanonicalBusinessFrame frame) {
        if (current == null || frame == null || frame.getSemanticSlots() == null) {
            return current;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart cur = current.getSemanticSlots();
        String preserveMargin =
                cur != null ? cur.getRequestedTargetGrossMarginRate() : null;
        String preserveExpiryFilter =
                cur != null ? cur.getExpiryRiskFilter() : null;

        AiQuerySemanticParseResult.SemanticSlotsPart slots = frame.getSemanticSlots();
        if (StringUtils.hasText(preserveMargin) || StringUtils.hasText(preserveExpiryFilter)) {
            slots =
                    AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                            .selectedContractId(slots.getSelectedContractId())
                            .queryObject(slots.getQueryObject())
                            .operation(slots.getOperation())
                            .metric(slots.getMetric())
                            .sourceFacet(slots.getSourceFacet())
                            .anchorPolicy(slots.getAnchorPolicy())
                            .detailWanted(slots.getDetailWanted())
                            .structuredIntentDetailWire(slots.getStructuredIntentDetailWire())
                            .answerPlanType(slots.getAnswerPlanType())
                            .mentionedDishName(slots.getMentionedDishName())
                            .requestedTargetGrossMarginRate(preserveMargin)
                            .expiryRiskFilter(preserveExpiryFilter)
                            .build();
        }

        AiQuerySemanticParseResult.AiQuerySemanticParseResultBuilder builder =
                current.toBuilder()
                        .semanticSlots(slots)
                        .currentTurnStructuredIntentDetailWire(frame.getCanonicalStructuredIntentDetailWire())
                        .mentionedDishName(null);

        SemanticCapabilityContract contract = frame.getContract();
        if (contract != null) {
            if (StringUtils.hasText(contract.getDomain())) {
                builder.semanticDomain(contract.getDomain().trim());
            }
            if (StringUtils.hasText(contract.getIntentCode())) {
                builder.intent(contract.getIntentCode().trim());
            }
        }

        if (frame.getOrchestration() != null) {
            builder.orchestrationDecisionCandidate(frame.getOrchestration());
        }

        Map<String, Object> frameTrace = inheritanceFrameTrace(frame);
        if (!frameTrace.isEmpty()) {
            Map<String, Object> trace = new LinkedHashMap<>();
            if (current.getMultiTurnInheritanceTrace() != null) {
                trace.putAll(current.getMultiTurnInheritanceTrace());
            }
            trace.putAll(frameTrace);
            builder.multiTurnInheritanceTrace(trace);
        }

        return SemanticDraftSyncSupport.syncDraftFromParse(builder.build());
    }

    static Map<String, Object> inheritanceFrameTrace(CanonicalBusinessFrame frame) {
        Map<String, Object> trace = new LinkedHashMap<>();
        if (frame == null || frame.getContract() == null) {
            return trace;
        }
        trace.put("businessFrameSource", "SemanticContractCatalog");
        trace.put("inheritedContractId", frame.getContract().getContractId());
        trace.putAll(ContractExecutionMappingSupport.executionTraceFields(frame.getContract()));
        if (frame.getContract().isRequiresAnchor()) {
            trace.put("requiresAnchor", true);
            if (StringUtils.hasText(frame.getContract().getAnchorType())) {
                trace.put("anchorType", frame.getContract().getAnchorType().trim());
            }
        }
        if (frame.getSemanticSlots() != null
                && StringUtils.hasText(frame.getSemanticSlots().getAnchorPolicy())) {
            trace.put("anchorPolicy", frame.getSemanticSlots().getAnchorPolicy());
        }
        return trace;
    }

    private static String resolveAnchorPolicy(String anchorPolicy, SemanticCapabilityContract contract) {
        if (StringUtils.hasText(anchorPolicy)) {
            return normalizeToken(anchorPolicy);
        }
        if (contract != null && contract.isRequiresAnchor()) {
            return AiQuerySemanticSlotMerge.ANCHOR_USE_PREVIOUS;
        }
        return AiQuerySemanticSlotMerge.ANCHOR_IGNORE_PREVIOUS;
    }

    private static String authoritativeWire(SemanticCapabilityContract contract) {
        if (contract == null || !StringUtils.hasText(contract.getWire())) {
            return null;
        }
        return AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(contract.getWire().trim());
    }

    private static String firstSetValue(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return normalizeToken(values.iterator().next());
    }

    /** 继承帧：metric 非必要能力不编造默认值。 */
    private static String metricFromContractEntry(SemanticCapabilityContract contract) {
        if (contract == null) {
            return null;
        }
        String operation = firstSetValue(contract.getOperations());
        if (!ContractBusinessSlotRequirementSupport.isMetricSemanticallyRequired(contract, operation)) {
            return null;
        }
        return firstSetValue(contract.getMetrics());
    }

    private static String normalizeToken(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }
}
