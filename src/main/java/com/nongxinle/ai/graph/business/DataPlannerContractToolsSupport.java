package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.contract.ContractExecutionMappingSupport;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Phase 2：contract entry {@code selectedTools} → {@code dataPlanTools} 收口。
 * <p>{@code effectivePathCode} 仍是调度主轴；contract tools 只能在当前 path 允许工具集合内生效。
 */
public final class DataPlannerContractToolsSupport {

    private DataPlannerContractToolsSupport() {}

    public record ResolveResult(List<String> tools, String plannerToolsSource) {}

    /**
     * 将 path 分支产出的默认工具表与 contract-locked selectedTools 对齐。
     * 非 contract-locked 时原样返回 {@code pathDefaultTools}。
     */
    public static ResolveResult resolveDataPlanTools(
            AiResolvedQueryContext ctx, String effectivePathCode, List<String> pathDefaultTools) {
        if (pathDefaultTools == null || pathDefaultTools.isEmpty()) {
            return new ResolveResult(List.of(), null);
        }
        List<String> pathAllowed = allowedToolsForPath(effectivePathCode, pathDefaultTools);
        if (pathAllowed.isEmpty()) {
            pathAllowed = new ArrayList<>(pathDefaultTools);
        }

        List<String> contractTools = contractSelectedTools(ctx);
        if (contractTools.isEmpty() || !isContractLocked(ctx)) {
            return new ResolveResult(new ArrayList<>(pathDefaultTools), null);
        }

        List<String> intersected = intersectPreservingOrder(pathAllowed, contractTools);
        if (intersected.isEmpty()) {
            return new ResolveResult(new ArrayList<>(pathDefaultTools), "path_default_contract_tools_out_of_path");
        }
        return new ResolveResult(intersected, "contract_execution_mapping");
    }

    private static boolean isContractLocked(AiResolvedQueryContext ctx) {
        if (ctx == null) {
            return false;
        }
        AiQuerySemanticParseResult sem = ctx.getQuerySemanticParse();
        return SemanticContractCompletionEngine.isContractLockedParse(sem);
    }

    private static List<String> contractSelectedTools(AiResolvedQueryContext ctx) {
        if (ctx == null) {
            return List.of();
        }
        AiQuerySemanticParseResult sem = ctx.getQuerySemanticParse();
        ContractExecutionMappingSupport.Mapping execution = ContractExecutionMappingSupport.resolve(sem);
        if (execution != null && execution.getSelectedTools() != null && !execution.getSelectedTools().isEmpty()) {
            return execution.getSelectedTools();
        }
        if (ctx.getOrchestrationSelectedTools() != null && !ctx.getOrchestrationSelectedTools().isEmpty()) {
            return ctx.getOrchestrationSelectedTools();
        }
        return List.of();
    }

    /** 当前 path 允许的工具上界；单域 path 以分支默认表为准，多域 path 用 Matrix 全集。 */
    private static List<String> allowedToolsForPath(String effectivePathCode, List<String> pathDefaultTools) {
        if (!StringUtils.hasText(effectivePathCode)) {
            return new ArrayList<>(pathDefaultTools);
        }
        String path = effectivePathCode.trim();
        if (AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(path)
                || AiResolvedQueryIntent.PATH_DISH_SALES_QUERY.equals(path)) {
            LinkedHashSet<String> allowed = new LinkedHashSet<>(pathDefaultTools);
            allowed.add(AiBusinessToolIds.DISH_INGREDIENT_COST_BREAKDOWN);
            return new ArrayList<>(allowed);
        }
        return new ArrayList<>(pathDefaultTools);
    }

    private static List<String> intersectPreservingOrder(List<String> pathAllowed, List<String> contractTools) {
        Set<String> contractSet = new LinkedHashSet<>();
        for (String t : contractTools) {
            if (StringUtils.hasText(t)) {
                contractSet.add(t.trim());
            }
        }
        List<String> out = new ArrayList<>();
        for (String allowed : pathAllowed) {
            if (StringUtils.hasText(allowed) && contractSet.contains(allowed.trim())) {
                out.add(allowed.trim());
            }
        }
        return out;
    }
}
