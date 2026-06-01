package com.nongxinle.ai.resolver;

import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.contract.SemanticCapabilityContract;
import com.nongxinle.ai.semantic.contract.SemanticContractCatalog;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

/**
 * Contract-locked 单店查询收窄：读 {@code selectedContractId} + 结构化门店槽位，
 * 不读 rawMessage / contains 推断业务意图。
 */
public final class ContractDrivenStoreScopeSupport {

    private static final Set<String> STORE_SCOPED_QUERY_CONTRACT_IDS =
            Set.of(
                    "revenue.single_store_overview",
                    "warehouse.single_store_overview",
                    "dish_sales.store_count_ranking",
                    "dish_sales.store_single_dish");

    private ContractDrivenStoreScopeSupport() {}

    /**
     * ACTIVE 合同为「须在当前轮点名门店」的查询（单店营业额、单店菜品排行等），需要结构化门店锚点才能执行。
     */
    public static boolean requiresCurrentTurnStoreQueryAnchor(AiQuerySemanticParseResult semantic) {
        if (semantic == null || !SemanticContractCompletionEngine.isContractLockedParse(semantic)) {
            return false;
        }
        String contractId = SemanticContractCompletionEngine.extractSelectedContractId(semantic);
        if (!StringUtils.hasText(contractId)) {
            return false;
        }
        String id = contractId.trim();
        if (STORE_SCOPED_QUERY_CONTRACT_IDS.contains(id)) {
            return true;
        }
        SemanticCapabilityContract contract = SemanticContractCatalog.findActiveCapabilityContractById(id, null);
        return contract != null
                && contract.getQueryObjects() != null
                && contract.getQueryObjects().contains("STORE")
                && contract.getOperations() != null
                && contract.getOperations().contains("SUMMARY");
    }

    public static boolean hasStructuredStoreMention(AiQuerySemanticParseResult semantic) {
        if (semantic == null) {
            return false;
        }
        if (!semantic.effectiveMentionedStoreNames().isEmpty()) {
            return true;
        }
        AiQuerySemanticParseResult.RequestedScopePart rs = semantic.getRequestedScope();
        return rs != null
                && AiResolvedOrgScope.SCOPE_STORE.equals(rs.getRequestedScopeType())
                && StringUtils.hasText(rs.getMentionedStoreName());
    }

    /**
     * 显式 {@code scopeMode=GROUP} 下，允许将查询范围收窄到合同要求的单店：
     * 合同已锁定为单店 SUMMARY，且 V2 输出了结构化门店名（非 rawMessage 猜测）。
     */
    public static boolean allowExplicitGroupNarrowingForContractStoreQuery(
            AiQuerySemanticParseResult semantic) {
        if (!requiresCurrentTurnStoreQueryAnchor(semantic) || !hasStructuredStoreMention(semantic)) {
            return false;
        }
        String action = normalizeScopeAction(semantic.getScopeAction());
        if ("INHERIT_PREVIOUS".equals(action)) {
            return false;
        }
        if ("OVERRIDE".equals(action) || "NEW".equals(action)) {
            return true;
        }
        return isCurrentTurnStoreScopeSource(semantic);
    }

    /**
     * 单店 SUMMARY 合同已选中且带结构化门店名时，不应视为「纯 GROUP 覆盖、无门店」。
     */
    public static boolean contractSingleStoreOverridesGroupScopeDeclaration(AiQuerySemanticParseResult semantic) {
        return requiresCurrentTurnStoreQueryAnchor(semantic) && hasStructuredStoreMention(semantic);
    }

    private static boolean isCurrentTurnStoreScopeSource(AiQuerySemanticParseResult semantic) {
        AiQuerySemanticParseResult.RequestedScopePart rs = semantic.getRequestedScope();
        if (rs == null || !StringUtils.hasText(rs.getScopeSource())) {
            return false;
        }
        return "CURRENT_MESSAGE".equalsIgnoreCase(rs.getScopeSource().trim());
    }

    private static String normalizeScopeAction(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        return raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }
}
