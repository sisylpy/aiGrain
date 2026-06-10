package com.nongxinle.ai.semantic.contract;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 合同 {@code planOutputs} 执行 SSOT：Tool / Builder 只读 Catalog 导出字段，禁止按 contractId if/else 推导事实面。
 */
public final class SemanticContractPlanOutputSupport {

    private SemanticContractPlanOutputSupport() {}

    /**
     * contract locked 时本轮应产出的 AnswerPlan 类型键；优先 {@link SemanticCapabilityContract#getPlanOutputs()}，
     * 为空则回退 {@link SemanticCapabilityContract#getAnswerPlanType()} 单值。
     */
    public static List<String> resolveRequestedPlanOutputs(AiResolvedQueryContext ctx) {
        if (ctx == null || ctx.getQuerySemanticParse() == null) {
            return List.of();
        }
        AiQuerySemanticParseResult sem = ctx.getQuerySemanticParse();
        if (!SemanticContractCompletionEngine.isContractLockedParse(sem)) {
            return List.of();
        }
        AiQuerySemanticParseResult.SemanticSlotsPart slots = sem.getSemanticSlots();
        if (slots == null || !StringUtils.hasText(slots.getSelectedContractId())) {
            return List.of();
        }
        String contractId = slots.getSelectedContractId().trim();
        SemanticCapabilityContract contract =
                SemanticContractCatalog.findActiveCapabilityContractById(contractId, null);
        if (contract == null) {
            return List.of();
        }
        List<String> outputs = contract.getPlanOutputs();
        if (outputs != null && !outputs.isEmpty()) {
            return List.copyOf(outputs);
        }
        if (StringUtils.hasText(contract.getAnswerPlanType())) {
            return List.of(contract.getAnswerPlanType().trim());
        }
        return List.of();
    }

    public static boolean requestsPlanOutput(AiResolvedQueryContext ctx, String planOutput) {
        if (!StringUtils.hasText(planOutput)) {
            return false;
        }
        String key = planOutput.trim();
        return resolveRequestedPlanOutputs(ctx).contains(key);
    }
}
