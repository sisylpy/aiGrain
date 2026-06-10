package com.nongxinle.ai.semantic.frame;

import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.contract.SemanticCapabilityContract;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * Contract Completion 成功后的不可变语义帧。
 * <p>
 * contract-owned 字段只来自 ACTIVE contract entry；用户业务槽位从 {@link SchemaValidatedSemanticDraft}
 * 结构化字段保留。Planner / ToolRequest 应以本对象为语义 SSOT。
 */
@Value
public class ContractLockedSemanticFrame {

    ContractFields contractFields;
    SchemaValidatedSemanticDraft.BusinessSlots businessSlots;
    SchemaValidatedSemanticDraft.TimeSlots timeSlots;
    SchemaValidatedSemanticDraft.ScopeSlots scopeSlots;
    SchemaValidatedSemanticDraft.EntitySlots entitySlots;
    SchemaValidatedSemanticDraft.DomainExtensions domainExtensions;
    Map<String, SchemaValidatedSemanticDraft.FieldPresence> presence;
    List<String> protocolErrors;

    @Builder(toBuilder = true)
    public ContractLockedSemanticFrame(
            ContractFields contractFields,
            SchemaValidatedSemanticDraft.BusinessSlots businessSlots,
            SchemaValidatedSemanticDraft.TimeSlots timeSlots,
            SchemaValidatedSemanticDraft.ScopeSlots scopeSlots,
            SchemaValidatedSemanticDraft.EntitySlots entitySlots,
            SchemaValidatedSemanticDraft.DomainExtensions domainExtensions,
            Map<String, SchemaValidatedSemanticDraft.FieldPresence> presence,
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

    @Value
    public static class ContractFields {
        String selectedContractId;
        String canonicalStructuredIntentDetailWire;
        String answerPlanType;
        List<String> selectedTools;
        String intentCode;
        String pathCode;
        Map<String, Object> executionMetadata;

        @Builder(toBuilder = true)
        public ContractFields(
                String selectedContractId,
                String canonicalStructuredIntentDetailWire,
                String answerPlanType,
                List<String> selectedTools,
                String intentCode,
                String pathCode,
                Map<String, Object> executionMetadata) {
            this.selectedContractId = selectedContractId;
            this.canonicalStructuredIntentDetailWire = canonicalStructuredIntentDetailWire;
            this.answerPlanType = answerPlanType;
            this.selectedTools = selectedTools == null ? null : List.copyOf(selectedTools);
            this.intentCode = intentCode;
            this.pathCode = pathCode;
            this.executionMetadata = executionMetadata == null ? null : Map.copyOf(executionMetadata);
        }

        public List<String> getSelectedTools() {
            return selectedTools == null ? null : List.copyOf(selectedTools);
        }

        public Map<String, Object> getExecutionMetadata() {
            return executionMetadata == null ? null : Map.copyOf(executionMetadata);
        }
    }

    public Map<String, SchemaValidatedSemanticDraft.FieldPresence> getPresence() {
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

    public static ContractLockedSemanticFrame fromDraftAndActiveContract(
            SchemaValidatedSemanticDraft draft,
            SemanticCapabilityContract contract) {
        SchemaValidatedSemanticDraft.BusinessSlots businessSlots =
                draft != null ? draft.getBusinessSlots() : null;
        SchemaValidatedSemanticDraft.TimeSlots timeSlots =
                draft != null ? draft.getTimeSlots() : null;
        SchemaValidatedSemanticDraft.ScopeSlots scopeSlots =
                draft != null ? draft.getScopeSlots() : null;
        SchemaValidatedSemanticDraft.EntitySlots entitySlots =
                draft != null ? draft.getEntitySlots() : null;
        SchemaValidatedSemanticDraft.DomainExtensions domainExtensions =
                draft != null ? draft.getDomainExtensions() : null;
        return ContractLockedSemanticFrame.builder()
                .contractFields(
                        ContractFields.builder()
                                .selectedContractId(contract != null ? contract.getContractId() : null)
                                .canonicalStructuredIntentDetailWire(
                                        contract != null ? contract.getWire() : null)
                                .answerPlanType(contract != null ? contract.getAnswerPlanType() : null)
                                .selectedTools(contract != null ? contract.getSelectedTools() : null)
                                .intentCode(contract != null ? contract.getIntentCode() : null)
                                .pathCode(contract != null ? contract.getPathCode() : null)
                                .executionMetadata(null)
                                .build())
                .businessSlots(businessSlots)
                .timeSlots(timeSlots)
                .scopeSlots(scopeSlots)
                .entitySlots(entitySlots)
                .domainExtensions(domainExtensions)
                .presence(draft != null ? draft.getPresence() : null)
                .protocolErrors(draft != null ? draft.getProtocolErrors() : null)
                .build();
    }
}
