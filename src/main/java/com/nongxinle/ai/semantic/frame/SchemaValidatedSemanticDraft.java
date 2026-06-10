package com.nongxinle.ai.semantic.frame;

import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * V2 JSON 解析后的结构化语义草稿。
 * <p>
 * 这是 raw JSON 与 contract completion 之间的唯一可变语义载体：parser 负责把协议字段搬入本对象，
 * 后续 adoption / inheritance 只能基于结构化字段工作，不读 raw/debug 反推业务语义。
 */
@Value
public class SchemaValidatedSemanticDraft {

    ContractFields contractFields;
    BusinessSlots businessSlots;
    TimeSlots timeSlots;
    ScopeSlots scopeSlots;
    EntitySlots entitySlots;
    DomainExtensions domainExtensions;
    Map<String, FieldPresence> presence;
    List<String> protocolErrors;

    @Builder(toBuilder = true)
    public SchemaValidatedSemanticDraft(
            ContractFields contractFields,
            BusinessSlots businessSlots,
            TimeSlots timeSlots,
            ScopeSlots scopeSlots,
            EntitySlots entitySlots,
            DomainExtensions domainExtensions,
            Map<String, FieldPresence> presence,
            List<String> protocolErrors) {
        this.contractFields = contractFields;
        this.businessSlots = businessSlots;
        this.timeSlots = timeSlots;
        this.scopeSlots = scopeSlots;
        this.entitySlots = entitySlots;
        this.domainExtensions = domainExtensions;
        this.presence = presence == null ? null : Map.copyOf(presence);
        this.protocolErrors = protocolErrors == null ? null : List.copyOf(protocolErrors);
    }

    public enum PresenceState {
        RAW_PRESENT,
        CANONICALIZED_FROM_NESTED,
        INHERITED,
        DEFAULTED,
        OVERRIDDEN,
        CONTRACT_DERIVED,
        MISSING,
        PROTOCOL_ERROR
    }

    @Value
    public static class ContractFields {
        String selectedContractId;
        String llmStructuredIntentDetailWire;
        String llmAnswerPlanType;
        List<String> llmSelectedTools;

        @Builder(toBuilder = true)
        public ContractFields(
                String selectedContractId,
                String llmStructuredIntentDetailWire,
                String llmAnswerPlanType,
                List<String> llmSelectedTools) {
            this.selectedContractId = selectedContractId;
            this.llmStructuredIntentDetailWire = llmStructuredIntentDetailWire;
            this.llmAnswerPlanType = llmAnswerPlanType;
            this.llmSelectedTools = llmSelectedTools == null ? null : List.copyOf(llmSelectedTools);
        }

        public List<String> getLlmSelectedTools() {
            return llmSelectedTools == null ? null : List.copyOf(llmSelectedTools);
        }
    }

    @Value
    @Builder(toBuilder = true)
    public static class BusinessSlots {
        AiQuerySemanticParseResult.SemanticSlotsPart semanticSlots;
        AiQuerySemanticParseResult.MetricPart metric;
    }

    @Value
    @Builder(toBuilder = true)
    public static class TimeSlots {
        AiQuerySemanticParseResult.TimePart time;
    }

    @Value
    @Builder(toBuilder = true)
    public static class ScopeSlots {
        AiQuerySemanticParseResult.RequestedScopePart requestedScope;
    }

    @Value
    @Builder(toBuilder = true)
    public static class EntitySlots {
        String mentionedDishName;
        String mentionedGoodsName;
    }

    @Value
    @Builder(toBuilder = true)
    public static class DomainExtensions {
        AiQuerySemanticParseResult.SalesBaselineWindowPart salesBaselineWindow;
        AiQuerySemanticParseResult.StockSnapshotPart stockSnapshot;
    }

    @Value
    public static class FieldPresence {
        PresenceState state;
        boolean inherited;
        boolean overridden;
        Set<String> rawLocations;
        String protocolError;

        @Builder(toBuilder = true)
        public FieldPresence(
                PresenceState state,
                boolean inherited,
                boolean overridden,
                Set<String> rawLocations,
                String protocolError) {
            this.state = state;
            this.inherited = inherited;
            this.overridden = overridden;
            this.rawLocations = rawLocations == null ? null : Set.copyOf(rawLocations);
            this.protocolError = protocolError;
        }

        public List<String> rawLocationList() {
            return rawLocations == null ? List.of() : List.copyOf(rawLocations);
        }

        public Set<String> getRawLocations() {
            return rawLocations == null ? null : Set.copyOf(rawLocations);
        }
    }

    public Map<String, FieldPresence> getPresence() {
        return presence == null ? null : Map.copyOf(presence);
    }

    public List<String> getProtocolErrors() {
        return protocolErrors == null ? null : List.copyOf(protocolErrors);
    }

    public AiQuerySemanticParseResult.SalesBaselineWindowPart salesBaselineWindow() {
        return domainExtensions != null ? domainExtensions.getSalesBaselineWindow() : null;
    }

    public AiQuerySemanticParseResult.StockSnapshotPart stockSnapshot() {
        return domainExtensions != null ? domainExtensions.getStockSnapshot() : null;
    }
}
