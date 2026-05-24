package com.nongxinle.ai.semantic.frame;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.semantic.AiQuerySemanticLlmMergeHelper;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.contract.DomainContractSelectionResult;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 按 contract-entry 域分发 {@link CurrentSemanticFrame} 校验（Harness debug / adoption 共用）。
 * <p>contract-locked 或非采购 effective path 时，禁止 {@link PurchaseCurrentSemanticFrameValidator} 跨域介入。
 */
public final class CurrentSemanticFrameValidatorRegistry {

    public record HarnessSemanticFrameValidation(
            String validationDomain, CurrentSemanticFrame frame, SemanticFrameValidationResult result) {}

    private static final Map<String, ContractEntrySemanticFrameValidationSupport.DomainContractEntryConfig>
            GENERIC_CONTRACT_ENTRY_CONFIGS = buildGenericContractEntryConfigs();

    private CurrentSemanticFrameValidatorRegistry() {}

    /** Harness / resolvedQueryContextSummary：null 表示不输出 semanticFrameValidation。 */
    public static HarnessSemanticFrameValidation validateForHarness(AiResolvedQueryContext ctx) {
        if (ctx == null) {
            return null;
        }
        AiQuerySemanticParseResult parse = ctx.getQuerySemanticParse();
        if (parse == null || parse.isParseMissing()) {
            return null;
        }
        DomainContractSelectionResult contractSelection = ctx.getDomainContractSelection();
        boolean contractLocked = SemanticContractCompletionEngine.isContractLockedParse(parse);
        String domain = resolveValidationDomain(ctx, parse, contractSelection);
        if (!StringUtils.hasText(domain)) {
            return null;
        }
        if (!shouldRunFrameValidation(ctx, parse, contractSelection, contractLocked, domain)) {
            return null;
        }
        CurrentSemanticFrame frame = CurrentSemanticFrame.fromParseResult(parse);
        SemanticFrameValidationResult result =
                validate(
                        domain,
                        frame,
                        parse,
                        ctx.getPreviousTurn(),
                        ctx.getNormalizedQuestion(),
                        Boolean.TRUE.equals(ctx.getFollowUpRewriteApplied()),
                        contractSelection,
                        contractLocked);
        if (result == null) {
            return null;
        }
        return new HarnessSemanticFrameValidation(domain, frame, result);
    }

    public static SemanticFrameValidationResult validate(
            String domainCode,
            CurrentSemanticFrame frame,
            AiQuerySemanticParseResult parse,
            AiConversationTurnMemory previousTurn,
            String normalizedUserMessage,
            boolean followUpRewriteApplied,
            DomainContractSelectionResult contractSelection,
            boolean contractLocked) {
        String domain = normalizeDomain(domainCode);
        if (!StringUtils.hasText(domain)) {
            return null;
        }
        if ("PURCHASE".equals(domain)) {
            if (!shouldRunPurchaseFrameValidation(null, parse, contractLocked, domain)) {
                return null;
            }
            return PurchaseCurrentSemanticFrameValidator.validate(
                    frame,
                    parse,
                    previousTurn,
                    normalizedUserMessage,
                    followUpRewriteApplied,
                    contractSelection);
        }
        return switch (domain) {
            case "REVENUE" ->
                    RevenueCurrentSemanticFrameValidator.validate(
                            frame,
                            parse,
                            previousTurn,
                            normalizedUserMessage,
                            followUpRewriteApplied,
                            contractSelection);
            case "STOCK_REDUCE" ->
                    StockReduceCurrentSemanticFrameValidator.validate(
                            frame,
                            parse,
                            previousTurn,
                            normalizedUserMessage,
                            followUpRewriteApplied,
                            contractSelection);
            case "WAREHOUSE" ->
                    WarehouseCurrentSemanticFrameValidator.validate(
                            frame,
                            parse,
                            previousTurn,
                            normalizedUserMessage,
                            followUpRewriteApplied,
                            contractSelection);
            default -> validateGenericContractEntry(domain, frame, parse, contractSelection);
        };
    }

    public static String resolveValidationDomain(
            AiResolvedQueryContext ctx,
            AiQuerySemanticParseResult parse,
            DomainContractSelectionResult contractSelection) {
        if (contractSelection != null && StringUtils.hasText(contractSelection.getSelectedDomain())) {
            return normalizeDomain(contractSelection.getSelectedDomain());
        }
        if (parse != null && SemanticContractCompletionEngine.hasSelectedContractId(parse)) {
            String contractId = SemanticContractCompletionEngine.extractSelectedContractId(parse);
            if (StringUtils.hasText(contractId)) {
                int dot = contractId.indexOf('.');
                if (dot > 0) {
                    return normalizeDomain(contractId.substring(0, dot));
                }
            }
        }
        if (parse != null && StringUtils.hasText(parse.getSemanticDomain())) {
            String fromSemantic = normalizeDomain(parse.getSemanticDomain());
            if ("WAREHOUSE_STOCK".equals(fromSemantic) || "INVENTORY".equals(fromSemantic)) {
                return "WAREHOUSE";
            }
            if ("STOCK_OUT".equals(fromSemantic) || "WRITE_OFF".equals(fromSemantic)) {
                return "STOCK_REDUCE";
            }
            return fromSemantic;
        }
        if (ctx != null && StringUtils.hasText(ctx.getEffectivePathCode())) {
            return domainFromEffectivePath(ctx.getEffectivePathCode());
        }
        return null;
    }

    static boolean shouldRunFrameValidation(
            AiResolvedQueryContext ctx,
            AiQuerySemanticParseResult parse,
            DomainContractSelectionResult contractSelection,
            boolean contractLocked,
            String domain) {
        if (contractLocked) {
            return true;
        }
        if ("PURCHASE".equals(domain)) {
            return shouldRunPurchaseFrameValidation(ctx, parse, false, domain);
        }
        if (GENERIC_CONTRACT_ENTRY_CONFIGS.containsKey(domain)
                || "REVENUE".equals(domain)
                || "STOCK_REDUCE".equals(domain)
                || "WAREHOUSE".equals(domain)) {
            return hasAllowedContracts(contractSelection)
                    || explicitDomainRouteSignal(parse, domain);
        }
        return false;
    }

    static boolean shouldRunPurchaseFrameValidation(
            AiResolvedQueryContext ctx,
            AiQuerySemanticParseResult parse,
            boolean contractLocked,
            String domain) {
        if (contractLocked) {
            return "PURCHASE".equals(domain);
        }
        if (!"PURCHASE".equals(domain)) {
            return false;
        }
        if (AiQuerySemanticLlmMergeHelper.currentTurnMapsToExplicitNonPurchasePath(parse)) {
            return false;
        }
        String effectivePath = ctx != null ? ctx.getEffectivePathCode() : null;
        if (StringUtils.hasText(effectivePath)
                && !AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW.equals(effectivePath)) {
            return false;
        }
        return AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW.equals(effectivePath)
                || AiQuerySemanticLlmMergeHelper.shouldUsePurchaseSemanticFrameAdoption(parse);
    }

    private static SemanticFrameValidationResult validateGenericContractEntry(
            String domain,
            CurrentSemanticFrame frame,
            AiQuerySemanticParseResult parse,
            DomainContractSelectionResult contractSelection) {
        ContractEntrySemanticFrameValidationSupport.DomainContractEntryConfig config =
                GENERIC_CONTRACT_ENTRY_CONFIGS.get(domain);
        if (config == null) {
            return SemanticFrameValidationResult.success();
        }
        return ContractEntrySemanticFrameValidationSupport.validateSelectedContractEntry(
                frame, parse, contractSelection, config, null);
    }

    private static boolean explicitDomainRouteSignal(AiQuerySemanticParseResult parse, String domain) {
        if (parse == null) {
            return false;
        }
        return switch (domain) {
            case "REVENUE" -> AiQuerySemanticLlmMergeHelper.hasExplicitRevenueRouteSignal(parse);
            case "BUSINESS_DIAGNOSIS" -> AiQuerySemanticLlmMergeHelper.hasExplicitBusinessDiagnosisRouteSignal(parse);
            case "STOCK_REDUCE" -> AiQuerySemanticLlmMergeHelper.hasExplicitStockReduceRouteSignal(parse);
            case "WAREHOUSE" -> AiQuerySemanticLlmMergeHelper.hasExplicitWarehouseRouteSignal(parse);
            default -> SemanticContractCompletionEngine.hasSelectedContractId(parse);
        };
    }

    private static boolean hasAllowedContracts(DomainContractSelectionResult contractSelection) {
        return contractSelection != null
                && contractSelection.getParserAllowedOutputContract() != null
                && contractSelection.getParserAllowedOutputContract().getAllowedContracts() != null
                && !contractSelection.getParserAllowedOutputContract().getAllowedContracts().isEmpty();
    }

    private static String domainFromEffectivePath(String pathCode) {
        if (!StringUtils.hasText(pathCode)) {
            return null;
        }
        return switch (pathCode.trim()) {
            case AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW -> "BUSINESS_OVERVIEW";
            case AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS -> "BUSINESS_DIAGNOSIS";
            case AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW -> "PURCHASE";
            case AiResolvedQueryIntent.PATH_DISH_SALES_QUERY -> "DISH_SALES";
            case AiResolvedQueryIntent.PATH_DISH_PROFIT -> "DISH_PROFIT";
            case AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW -> "REVENUE";
            case AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY -> "STOCK_REDUCE";
            case AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK -> "WAREHOUSE";
            default -> null;
        };
    }

    private static String normalizeDomain(String domain) {
        return domain == null ? null : domain.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private static boolean isDishProfitWire(String wire) {
        if (!StringUtils.hasText(wire)) {
            return false;
        }
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wire.trim());
        if (AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_OVERVIEW.equals(canon)) {
            return true;
        }
        return AiQuerySemanticLexicon.isDishProfitRankingStructuredDetail(wire)
                || AiQuerySemanticLexicon.isSingleDishMetricOrReasonStructuredDetail(wire);
    }

    private static Map<String, ContractEntrySemanticFrameValidationSupport.DomainContractEntryConfig>
            buildGenericContractEntryConfigs() {
        Map<String, ContractEntrySemanticFrameValidationSupport.DomainContractEntryConfig> map =
                new LinkedHashMap<>();
        map.put(
                "BUSINESS_OVERVIEW",
                new ContractEntrySemanticFrameValidationSupport.DomainContractEntryConfig(
                        "BUSINESS_OVERVIEW",
                        "经营概览",
                        AiQuerySemanticLexicon::isStructuredBusinessOverviewFourDomainOrchestrationSurface));
        map.put(
                "BUSINESS_DIAGNOSIS",
                new ContractEntrySemanticFrameValidationSupport.DomainContractEntryConfig(
                        "BUSINESS_DIAGNOSIS",
                        "经营诊断",
                        AiQuerySemanticLexicon::isStructuredBusinessDiagnosisDetail));
        map.put(
                "DISH_SALES",
                new ContractEntrySemanticFrameValidationSupport.DomainContractEntryConfig(
                        "DISH_SALES", "菜品销售", AiQuerySemanticLexicon::isStructuredDishSalesDetail));
        map.put(
                "DISH_PROFIT",
                new ContractEntrySemanticFrameValidationSupport.DomainContractEntryConfig(
                        "DISH_PROFIT", "菜品毛利", CurrentSemanticFrameValidatorRegistry::isDishProfitWire));
        return Map.copyOf(map);
    }
}
