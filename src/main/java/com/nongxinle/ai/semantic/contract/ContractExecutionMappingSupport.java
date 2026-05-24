package com.nongxinle.ai.semantic.contract;

import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import lombok.Builder;
import lombok.Value;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 已校验 {@code selectedContractId} → 确定性 execution metadata（intent / path / tools）。
 * <p>只读 {@link SemanticCapabilityContract} Catalog；不做用户原文语义推断，不反推 wire。
 */
public final class ContractExecutionMappingSupport {

    private ContractExecutionMappingSupport() {}

    @Value
    @Builder
    public static class Mapping {
        String contractId;
        String domain;
        String intentCode;
        String pathCode;
        String topic;
        String wire;
        String answerPlanType;
        List<String> selectedTools;
        /** true：合同存在但缺少 intent/path，应 clarification / known gap，禁止 Java 猜测。 */
        boolean missingExecutionMetadata;
        String gapReason;
        String mappingSource;

        public boolean hasRoutableExecution() {
            return !missingExecutionMetadata
                    && StringUtils.hasText(intentCode)
                    && StringUtils.hasText(pathCode);
        }
    }

    /** 从 parse 的 validated {@code selectedContractId} 解析 ACTIVE 合同 execution metadata。 */
    public static Mapping resolve(AiQuerySemanticParseResult sem) {
        if (sem == null || !SemanticContractCompletionEngine.isContractLockedParse(sem)) {
            return null;
        }
        Mapping fromTrace = resolveFromCompletionTrace(sem.getContractCompletionTrace());
        if (fromTrace != null && fromTrace.hasRoutableExecution()) {
            return fromTrace;
        }
        String contractId = SemanticContractCompletionEngine.extractSelectedContractId(sem);
        if (!StringUtils.hasText(contractId)) {
            return null;
        }
        String domainHint = domainHint(sem);
        return resolve(contractId.trim(), domainHint);
    }

    public static Mapping resolve(String contractId, String domainHint) {
        if (!StringUtils.hasText(contractId)) {
            return null;
        }
        SemanticCapabilityContract contract =
                SemanticContractCatalog.findActiveCapabilityContractById(contractId.trim(), domainHint);
        if (contract == null) {
            return Mapping.builder()
                    .contractId(contractId.trim())
                    .domain(domainHint)
                    .missingExecutionMetadata(true)
                    .gapReason("contract_not_active_or_not_found")
                    .mappingSource("SemanticContractCatalog")
                    .build();
        }
        return fromActiveContract(contract);
    }

    static Mapping fromActiveContract(SemanticCapabilityContract contract) {
        if (contract == null) {
            return null;
        }
        String intent = blank(contract.getIntentCode());
        String path = blank(contract.getPathCode());
        if (!StringUtils.hasText(intent) || !StringUtils.hasText(path)) {
            return Mapping.builder()
                    .contractId(contract.getContractId())
                    .domain(contract.getDomain())
                    .wire(contract.getWire())
                    .answerPlanType(contract.getAnswerPlanType())
                    .selectedTools(contract.getSelectedTools())
                    .missingExecutionMetadata(true)
                    .gapReason("missing_intent_or_path_metadata")
                    .mappingSource("SemanticCapabilityContract")
                    .build();
        }
        return Mapping.builder()
                .contractId(contract.getContractId())
                .domain(contract.getDomain())
                .intentCode(intent)
                .pathCode(path)
                .topic(topicForDomain(contract.getDomain()))
                .wire(contract.getWire())
                .answerPlanType(contract.getAnswerPlanType())
                .selectedTools(contract.getSelectedTools())
                .missingExecutionMetadata(false)
                .mappingSource("SemanticCapabilityContract")
                .build();
    }

    /** 写入 parse completion trace 的 execution 字段（供 Harness debug）。 */
    public static Map<String, Object> executionTraceFields(SemanticCapabilityContract contract) {
        java.util.LinkedHashMap<String, Object> trace = new java.util.LinkedHashMap<>();
        if (contract == null) {
            return trace;
        }
        trace.put("selectedContractId", contract.getContractId());
        trace.put("domain", contract.getDomain());
        trace.put("wire", contract.getWire());
        if (StringUtils.hasText(contract.getIntentCode())) {
            trace.put("intentCode", contract.getIntentCode().trim());
        }
        if (StringUtils.hasText(contract.getPathCode())) {
            trace.put("pathCode", contract.getPathCode().trim());
        }
        if (StringUtils.hasText(contract.getAnswerPlanType())) {
            trace.put("answerPlanType", contract.getAnswerPlanType().trim());
        }
        if (contract.getSelectedTools() != null && !contract.getSelectedTools().isEmpty()) {
            trace.put("selectedTools", contract.getSelectedTools());
        }
        trace.put("executionMappingSource", "SemanticCapabilityContract");
        return trace;
    }

    private static Mapping resolveFromCompletionTrace(Map<String, Object> trace) {
        if (trace == null || trace.isEmpty()) {
            return null;
        }
        Object intentObj = trace.get("intentCode");
        Object pathObj = trace.get("pathCode");
        if (!(intentObj instanceof String intentCode) || !(pathObj instanceof String pathCode)) {
            return null;
        }
        if (!StringUtils.hasText(intentCode) || !StringUtils.hasText(pathCode)) {
            return null;
        }
        Object domainObj = trace.get("domain");
        String domain = domainObj instanceof String s ? blank(s) : null;
        Object contractObj = trace.get("selectedContractId");
        String contractId =
                contractObj instanceof String s
                        ? blank(s)
                        : trace.get("rawSelectedContractId") instanceof String r ? blank(r) : null;
        return Mapping.builder()
                .contractId(contractId)
                .domain(domain)
                .intentCode(intentCode.trim())
                .pathCode(pathCode.trim())
                .topic(topicForDomain(domain))
                .wire(trace.get("wire") instanceof String w ? blank(w) : null)
                .answerPlanType(
                        trace.get("answerPlanType") instanceof String a ? blank(a) : null)
                .selectedTools(
                        trace.get("selectedTools") instanceof List<?> tools
                                ? tools.stream()
                                        .filter(String.class::isInstance)
                                        .map(String.class::cast)
                                        .toList()
                                : null)
                .missingExecutionMetadata(false)
                .mappingSource(
                        trace.get("executionMappingSource") instanceof String src
                                ? src
                                : "contractCompletionTrace")
                .build();
    }

    private static String domainHint(AiQuerySemanticParseResult sem) {
        if (sem == null) {
            return null;
        }
        Map<String, Object> trace = sem.getContractCompletionTrace();
        if (trace != null) {
            Object domainObj = trace.get("domain");
            if (domainObj instanceof String domainStr && StringUtils.hasText(domainStr)) {
                return domainStr.trim();
            }
        }
        return StringUtils.hasText(sem.getSemanticDomain()) ? sem.getSemanticDomain().trim() : null;
    }

    static String topicForDomain(String domain) {
        if (!StringUtils.hasText(domain)) {
            return "查询";
        }
        return switch (domain.trim().toUpperCase(Locale.ROOT)) {
            case "REVENUE" -> "营业额/营收";
            case "PURCHASE" -> "采购概览";
            case "STOCK_REDUCE" -> "出库/核销查询";
            case "WAREHOUSE" -> "库存概览";
            case "DISH_PROFIT" -> "菜品毛利/利润";
            case "DISH_SALES" -> "菜品销量/销售额";
            case "BUSINESS_DIAGNOSIS" -> "经营诊断";
            case "BUSINESS_OVERVIEW", "OPERATIONS_OVERVIEW" -> "经营概览";
            default -> domain.trim().toLowerCase(Locale.ROOT);
        };
    }

    private static String blank(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
