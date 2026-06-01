package com.nongxinle.ai.semantic.contract;

import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

/**
 * 合同主权下的业务槽位必要性：区分「合同登记的可选 metric 集合」与「本轮语义是否必须给出 metric」。
 * <p>LIST / DETAIL / OVERVIEW / SUMMARY 等能力不以 metric 为执行主键；不得因 LLM {@code metric=null}
 * 或无效占位 token 阻断 contract-locked 主链，也不得为通过校验而编造 metric。
 */
public final class ContractBusinessSlotRequirementSupport {

    /** 执行主链不依赖 metric 的 operation（与具体域无关，按 operation 判定）。 */
    private static final Set<String> METRIC_OPTIONAL_OPERATIONS =
            Set.of("LIST", "DETAIL", "OVERVIEW", "SUMMARY");

    private ContractBusinessSlotRequirementSupport() {}

    /**
     * 本轮 resolved operation 下，metric 是否为业务必要槽位。
     *
     * @param contract ACTIVE entry；{@code metrics} 非空仅表示允许值集合，不等于必填
     * @param resolvedOperation completion 后或 frame 上的 operation（可 null）
     */
    public static boolean isMetricSemanticallyRequired(
            SemanticCapabilityContract contract, String resolvedOperation) {
        if (contract == null || contract.getMetrics() == null || contract.getMetrics().isEmpty()) {
            return false;
        }
        String op = normalizeToken(resolvedOperation);
        if (StringUtils.hasText(op)) {
            return !METRIC_OPTIONAL_OPERATIONS.contains(op);
        }
        Set<String> contractOps = contract.getOperations();
        if (contractOps == null || contractOps.isEmpty()) {
            return false;
        }
        return !operationsAreAllMetricOptional(contractOps);
    }

    /**
     * Completion 用：保留合同允许集合内的 LLM metric；可选能力不编造默认值；必填能力在缺失/非法时取合同首项。
     */
    public static String coalesceMetricFromContract(
            String llmMetric, SemanticCapabilityContract contract, String resolvedOperation) {
        if (contract == null) {
            return normalizeToken(llmMetric);
        }
        Set<String> allowed = contract.getMetrics();
        if (allowed == null || allowed.isEmpty()) {
            return normalizeToken(llmMetric);
        }
        String token = normalizeToken(llmMetric);
        if (StringUtils.hasText(token) && tokenInSet(token, allowed)) {
            return token;
        }
        if (!isMetricSemanticallyRequired(contract, resolvedOperation)) {
            return null;
        }
        return normalizeToken(allowed.iterator().next());
    }

    static boolean operationsAreAllMetricOptional(Set<String> operations) {
        if (operations == null || operations.isEmpty()) {
            return false;
        }
        for (String raw : operations) {
            String op = normalizeToken(raw);
            if (!StringUtils.hasText(op) || !METRIC_OPTIONAL_OPERATIONS.contains(op)) {
                return false;
            }
        }
        return true;
    }

    static boolean tokenInSet(String token, Set<String> allowed) {
        if (allowed == null || allowed.isEmpty() || !StringUtils.hasText(token)) {
            return false;
        }
        for (String allowedToken : allowed) {
            if (token.equals(normalizeToken(allowedToken))) {
                return true;
            }
        }
        return false;
    }

    static String normalizeToken(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }
}
