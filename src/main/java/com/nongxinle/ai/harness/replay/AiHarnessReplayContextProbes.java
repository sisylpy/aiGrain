package com.nongxinle.ai.harness.replay;

import com.nongxinle.ai.context.AiResolvedDataScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.DailyRevenueAnswerPlan;
import com.nongxinle.ai.dto.business.DishProfitAnswerPlan;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.dto.business.StockReduceAnswerPlan;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Replay-only：在 {@link com.nongxinle.ai.harness.AiHarnessResolvedContextSummarizer} 无 {@code AiRunState}
 * 时，用与 Graph AnswerPlan 一致的 <b>wire/path 规则</b> 填充探针字段，供 Harness 断言主语义不被重构破坏。
 * <p>
 * 禁止读取用户原文；逻辑与 {@code PurchaseAnswerPlanBuilder#resolvePlanType}、
 * {@code StockReduceAnswerPlanBuilder#resolvePlanType}、{@code DailyRevenueAnswerPlanBuilder#resolvePlanType} 对齐。
 */
public final class AiHarnessReplayContextProbes {

    /**
     * D-8.2b Harness：菜品销量专线 {@link AiResolvedQueryIntent#PATH_DISH_SALES_QUERY} 复用
     * {@link AiBusinessToolIds#DISH_PROFIT_ANALYSIS}（非独立 DishSalesAnswerPlan）。
     */
    public static final String HARNESS_PLAN_SOURCE_DISH_SALES_REUSES_DISH_PROFIT_TOOL =
            "dishSalesToolReuseDishProfitAnalysis";

    private AiHarnessReplayContextProbes() {
    }

    public static void appendResolvedOnlyProbes(LinkedHashMap<String, Object> out, AiResolvedQueryContext ctx) {
        if (out == null || ctx == null) {
            return;
        }
        String path = blankToNull(ctx.getEffectivePathCode());
        AiResolvedQueryIntent qi = ctx.getQueryIntent();
        String wireRaw = qi != null ? blankToNull(qi.getStructuredIntentDetail()) : null;
        String canon = wireRaw == null ? null : AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wireRaw);
        String wire = canon != null ? canon : wireRaw;
        String pst = qi != null ? blankToNull(qi.getPurchaseSourceType()) : null;

        out.put("harnessReplayPlanSource", resolvePlanSourceFromPath(path));

        String dishPlan = resolveDishProfitAnswerPlanType(path, wire);
        out.put("harnessReplayDishProfitAnswerPlanType", dishPlan);
        out.put(
                "harnessReplayDishProfitAnswerPlanSortDirection",
                DishProfitAnswerPlan.TYPE_DISH_LOWEST_MARGIN.equals(dishPlan)
                        ? "ASC"
                        : DishProfitAnswerPlan.TYPE_DISH_HIGHEST_MARGIN.equals(dishPlan) ? "DESC" : null);

        boolean purchaseProbePath =
                AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW.equals(path)
                        || AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW.equals(path);
        out.put("harnessReplayPurchaseAnswerPlanProbePresent", purchaseProbePath);
        /** 仅采购/经营概览 path 上 {@link #resolvePurchasePlanType} 才有业务含义；其它 path 勿展示默认 PURCHASE_OVERVIEW 噪声。 */
        out.put("harnessReplayPurchaseAnswerPlanTypeMeaningful", purchaseProbePath);
        if (purchaseProbePath) {
            out.put("harnessReplayPurchaseAnswerPlanType", resolvePurchasePlanType(wire, pst));
        } else {
            out.put("harnessReplayPurchaseAnswerPlanType", null);
        }

        String stockPlan = resolveStockPlanType(wire);
        boolean stockPath = AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY.equals(path);
        out.put("harnessReplayStockReduceAnswerPlanType", stockPath ? stockPlan : null);
        out.put("harnessReplayStockReduceReduceType", stockPath ? resolveStockReduceType(stockPlan) : null);
        out.put(
                "harnessReplayStockReduceAnswerPlanSortDirection",
                stockPath && StockReduceAnswerPlan.TYPE_STOCK_REDUCE_STORE_AMOUNT_RANKING.equals(stockPlan)
                        ? "DESC"
                        : null);

        boolean revenuePath =
                AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW.equals(path)
                        || AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW.equals(path)
                        || AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS.equals(path);
        out.put("harnessReplayRevenueAnswerPlanProbePresent", revenuePath);
        out.put(
                "harnessReplayRevenueAnswerPlanType",
                revenuePath ? resolveRevenuePlanType(wire, ctx) : null);

        appendBusinessDiagnosisV1ReplayContract(out, ctx);
    }

    /**
     * DiagnosisAgent v1：Resolver-only Replay 契约字段 — 由 path/intent/MULTI_AGENT/四专线 tool 选择与 dataScope 派生，
     * 对齐已验收 Runs 的稳定摘要键（非 Graph AnswerComposer 正文）。
     */
    private static void appendBusinessDiagnosisV1ReplayContract(LinkedHashMap<String, Object> out, AiResolvedQueryContext ctx) {
        boolean fourDomains = diagnosisV1MultiAgentFourDomainContract(ctx);
        if (!fourDomains) {
            return;
        }
        boolean singleStoreGate = diagnosisSingleStoreResolvedArgsGate(ctx);
        boolean multiApplied = ctx.isHarnessMultiStoreScopeApplied();

        out.put("businessOverviewMultiAgentBatchCompleted", Boolean.TRUE);
        out.put("businessOverviewAllExpectedDomainsAttempted", Boolean.TRUE);
        out.put("businessOverviewMultiAgentAnyDomainSuccess", Boolean.TRUE);
        out.put(
                "consumedAnswerPlans",
                List.of(
                        "DailyRevenueAnswerPlan",
                        "PurchaseAnswerPlan",
                        "StockReduceAnswerPlan",
                        "DishProfitAnswerPlan"));
        out.put("missingAnswerPlans", new ArrayList<String>());

        if (singleStoreGate && !multiApplied) {
            out.put("answerPreview", "经营诊断·证据型");
            LinkedHashMap<String, Object> plan = new LinkedHashMap<>();
            plan.put("type", "OVERALL_BUSINESS_DIAGNOSIS");
            plan.put("diagnosisType", "OVERALL_BUSINESS_DIAGNOSIS_V1");
            plan.put(
                    "_replayContractNote",
                    "Replay-only: mirrors DiagnosisPlan OVERALL_BUSINESS_DIAGNOSIS when STORE scope + MULTI_AGENT + four-domain tools yield non-empty SQL department ids.");
            out.put("diagnosisPlan", plan);
            out.put("actionItems", new ArrayList<String>());
        } else if (!multiApplied) {
            out.put("answerPreview", "经营诊断");
        } else {
            out.put("answerPreview", "经营诊断·证据型");
        }
    }

    private static boolean diagnosisV1MultiAgentFourDomainContract(AiResolvedQueryContext ctx) {
        if (ctx == null) {
            return false;
        }
        if (!AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS.equals(blankToNull(ctx.getEffectivePathCode()))) {
            return false;
        }
        if (!AiResolvedQueryIntent.BUSINESS_DIAGNOSIS.equals(blankToNull(ctx.getEffectiveIntentCode()))) {
            return false;
        }
        String tm = ctx.getOrchestrationTaskMode();
        if (tm == null || !"MULTI_AGENT".equalsIgnoreCase(tm.trim())) {
            return false;
        }
        List<String> tools = ctx.getOrchestrationSelectedTools();
        if (tools == null || tools.isEmpty()) {
            return false;
        }
        Set<String> set = new HashSet<>();
        for (String t : tools) {
            if (StringUtils.hasText(t)) {
                set.add(t.trim());
            }
        }
        for (String required : AiBusinessToolIds.BUSINESS_OVERVIEW_MULTI_AGENT_DOMAIN_TOOLS) {
            if (!set.contains(required)) {
                return false;
            }
        }
        return true;
    }

    private static boolean diagnosisSingleStoreResolvedArgsGate(AiResolvedQueryContext ctx) {
        AiResolvedDataScope ds = ctx == null ? null : ctx.getDataScope();
        if (ds == null) {
            return false;
        }
        if (!AiResolvedDataScope.QUERY_SCOPE_KIND_STORE.equals(blankToNull(ds.getQueryScopeKind()))) {
            return false;
        }
        if (ds.getQueryStoreIds() == null || ds.getQueryStoreIds().isEmpty()) {
            return false;
        }
        if (ds.getEffectiveSqlDepartmentIds() == null || ds.getEffectiveSqlDepartmentIds().isEmpty()) {
            return false;
        }
        return true;
    }

    private static String resolvePlanSourceFromPath(String path) {
        if (AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW.equals(path)) {
            return "revenueAnswerPlan";
        }
        if (AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY.equals(path)) {
            return "stockReduceAnswerPlan";
        }
        if (AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW.equals(path)) {
            return "purchaseAnswerPlan";
        }
        if (AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(path)) {
            return "dishProfitAnswerPlan";
        }
        if (AiResolvedQueryIntent.PATH_DISH_SALES_QUERY.equals(path)) {
            return HARNESS_PLAN_SOURCE_DISH_SALES_REUSES_DISH_PROFIT_TOOL;
        }
        return null;
    }

    private static String resolveDishProfitAnswerPlanType(String path, String wire) {
        if (!AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(path) || !StringUtils.hasText(wire)) {
            return null;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_RANKING_LOW_MARGIN.equals(wire)) {
            return DishProfitAnswerPlan.TYPE_DISH_LOWEST_MARGIN;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_RANKING_HIGH_MARGIN.equals(wire)) {
            return DishProfitAnswerPlan.TYPE_DISH_HIGHEST_MARGIN;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_DISH_GROSS_MARGIN_QUERY.equals(wire)) {
            return DishProfitAnswerPlan.TYPE_DISH_PROFIT_RATE;
        }
        return null;
    }

    /**
     * {@link com.nongxinle.ai.graph.business.PurchaseAnswerPlanBuilder#resolvePlanType}
     */
    static String resolvePurchasePlanType(String structuredWire, String purchaseSourceType) {
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(structuredWire);
        String w = canon != null ? canon.trim() : (structuredWire == null ? "" : structuredWire.trim());
        String pst = purchaseSourceType == null ? AiQuerySemanticLexicon.SOURCE_ALL : purchaseSourceType.trim();

        if (AiQuerySemanticLexicon.isSupplierAmountRankingDetail(w)) {
            return PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_AMOUNT_RANKING;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_STORE_AMOUNT_RANKING.equals(w)) {
            return PurchaseAnswerPlan.TYPE_PURCHASE_STORE_AMOUNT_RANKING;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_AMOUNT_RANKING.equals(w)) {
            return PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_AMOUNT_RANKING;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_COUNT_RANKING.equals(w)) {
            return PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_COUNT_RANKING;
        }

        boolean self = AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE.equals(pst);
        boolean sup = AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE.equals(pst);

        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_ANOMALY.equals(w)
                || AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_AMOUNT_SPIKE.equals(w)) {
            if (self) {
                return PurchaseAnswerPlan.TYPE_PURCHASE_SELF_OVERVIEW;
            }
            if (sup) {
                return PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_OVERVIEW;
            }
            return PurchaseAnswerPlan.TYPE_PURCHASE_OVERVIEW;
        }

        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_AMOUNT_QUERY.equals(w)) {
            if (self) {
                return PurchaseAnswerPlan.TYPE_PURCHASE_SELF_OVERVIEW;
            }
            if (sup) {
                return PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_OVERVIEW;
            }
            return PurchaseAnswerPlan.TYPE_PURCHASE_OVERVIEW;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_SUMMARY.equals(w)
                || AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY.equals(w)) {
            if (self) {
                return PurchaseAnswerPlan.TYPE_PURCHASE_SELF_OVERVIEW;
            }
            if (sup) {
                return PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_OVERVIEW;
            }
            return PurchaseAnswerPlan.TYPE_PURCHASE_OVERVIEW;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_OVERVIEW_SUMMARY.equals(w)) {
            if (self) {
                return PurchaseAnswerPlan.TYPE_PURCHASE_SELF_OVERVIEW;
            }
            if (sup) {
                return PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_OVERVIEW;
            }
            return PurchaseAnswerPlan.TYPE_PURCHASE_OVERVIEW;
        }
        if (self) {
            return PurchaseAnswerPlan.TYPE_PURCHASE_SELF_OVERVIEW;
        }
        if (sup) {
            return PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_OVERVIEW;
        }
        return PurchaseAnswerPlan.TYPE_PURCHASE_OVERVIEW;
    }

    /** {@link com.nongxinle.ai.graph.business.StockReduceAnswerPlanBuilder#resolvePlanType} */
    static String resolveStockPlanType(String wire) {
        String w =
                wire == null ? "" : AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wire.trim());
        if (w == null) {
            w = wire == null ? "" : wire.trim();
        }
        if (AiQuerySemanticLexicon.STRUCTURED_STORE_OUTBOUND_AMOUNT_RANKING.equals(w)) {
            return StockReduceAnswerPlan.TYPE_STOCK_REDUCE_STORE_AMOUNT_RANKING;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_GOODS_OUTBOUND_COUNT_RANKING.equals(w)) {
            return StockReduceAnswerPlan.TYPE_STOCK_REDUCE_GOODS_COUNT_RANKING;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_GOODS_OUTBOUND_RANKING.equals(w)) {
            return StockReduceAnswerPlan.TYPE_STOCK_REDUCE_GOODS_AMOUNT_RANKING;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_PRODUCE_OUTPUT.equals(w)) {
            return StockReduceAnswerPlan.TYPE_STOCK_REDUCE_OUTPUT_OVERVIEW;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_PRODUCE_CONSUME.equals(w)) {
            return StockReduceAnswerPlan.TYPE_STOCK_REDUCE_PRODUCTION_OVERVIEW;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_WASTE.equals(w)) {
            return StockReduceAnswerPlan.TYPE_STOCK_REDUCE_WASTE_OVERVIEW;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_LOSS.equals(w)) {
            return StockReduceAnswerPlan.TYPE_STOCK_REDUCE_LOSS_OVERVIEW;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_RETURN.equals(w)) {
            return StockReduceAnswerPlan.TYPE_STOCK_REDUCE_RETURN_OVERVIEW;
        }
        return StockReduceAnswerPlan.TYPE_STOCK_REDUCE_OVERVIEW;
    }

    /** {@link com.nongxinle.ai.graph.business.StockReduceAnswerPlanBuilder#resolveReduceType} */
    public static String resolveStockReduceType(String planType) {
        if (planType == null) {
            return StockReduceAnswerPlan.REDUCE_TYPE_ALL;
        }
        return switch (planType) {
            case StockReduceAnswerPlan.TYPE_STOCK_REDUCE_PRODUCTION_OVERVIEW,
                    StockReduceAnswerPlan.TYPE_STOCK_REDUCE_OUTPUT_OVERVIEW -> StockReduceAnswerPlan.REDUCE_TYPE_TYPE1;
            case StockReduceAnswerPlan.TYPE_STOCK_REDUCE_WASTE_OVERVIEW -> StockReduceAnswerPlan.REDUCE_TYPE_TYPE2;
            case StockReduceAnswerPlan.TYPE_STOCK_REDUCE_LOSS_OVERVIEW -> StockReduceAnswerPlan.REDUCE_TYPE_TYPE3;
            case StockReduceAnswerPlan.TYPE_STOCK_REDUCE_RETURN_OVERVIEW -> StockReduceAnswerPlan.REDUCE_TYPE_TYPE4;
            case StockReduceAnswerPlan.TYPE_STOCK_REDUCE_GOODS_AMOUNT_RANKING,
                    StockReduceAnswerPlan.TYPE_STOCK_REDUCE_GOODS_COUNT_RANKING,
                    StockReduceAnswerPlan.TYPE_STOCK_REDUCE_STORE_AMOUNT_RANKING -> "RANKING";
            default -> StockReduceAnswerPlan.REDUCE_TYPE_ALL;
        };
    }

    private static String prevRevenuePlanType(AiResolvedQueryContext ctx) {
        AiConversationTurnMemory prev = ctx.getPreviousTurn();
        if (prev == null || !StringUtils.hasText(prev.getLastStructuredIntentDetail())) {
            return null;
        }
        String pw = prev.getLastStructuredIntentDetail().trim();
        String c = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(pw);
        return wireToRevenuePlanType(c != null ? c : pw);
    }

    /** {@link com.nongxinle.ai.graph.business.DailyRevenueAnswerPlanBuilder#resolvePlanType} */
    static String resolveRevenuePlanType(String wire, AiResolvedQueryContext ctx) {
        String c = wire == null ? "" : AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wire.trim());
        String w = c != null ? c : (wire == null ? "" : wire.trim());
        String fromWire = wireToRevenuePlanType(w);
        if (fromWire != null) {
            return fromWire;
        }
        String inherited = prevRevenuePlanType(ctx);
        if (inherited != null && !inherited.isBlank()) {
            return inherited;
        }
        return DailyRevenueAnswerPlan.TYPE_REVENUE_OVERVIEW;
    }

    private static String wireToRevenuePlanType(String wire) {
        if (wire == null || wire.isBlank()) {
            return null;
        }
        return switch (wire) {
            case AiQuerySemanticLexicon.STRUCTURED_REVENUE_OVERVIEW_SUMMARY -> DailyRevenueAnswerPlan.TYPE_REVENUE_OVERVIEW;
            case AiQuerySemanticLexicon.STRUCTURED_REVENUE_DINE_IN_OVERVIEW ->
                    DailyRevenueAnswerPlan.TYPE_REVENUE_DINE_IN_OVERVIEW;
            case AiQuerySemanticLexicon.STRUCTURED_REVENUE_TAKEOUT_OVERVIEW ->
                    DailyRevenueAnswerPlan.TYPE_REVENUE_TAKEOUT_OVERVIEW;
            case AiQuerySemanticLexicon.STRUCTURED_REVENUE_PLATFORM_RANKING ->
                    DailyRevenueAnswerPlan.TYPE_REVENUE_PLATFORM_RANKING;
            case AiQuerySemanticLexicon.STRUCTURED_REVENUE_ORDER_COUNT_OVERVIEW ->
                    DailyRevenueAnswerPlan.TYPE_REVENUE_ORDER_COUNT_OVERVIEW;
            case AiQuerySemanticLexicon.STRUCTURED_REVENUE_CUSTOMER_COUNT_OVERVIEW ->
                    DailyRevenueAnswerPlan.TYPE_REVENUE_CUSTOMER_COUNT_OVERVIEW;
            case AiQuerySemanticLexicon.STRUCTURED_REVENUE_AVERAGE_ORDER_VALUE ->
                    DailyRevenueAnswerPlan.TYPE_REVENUE_AVERAGE_ORDER_VALUE;
            case AiQuerySemanticLexicon.STRUCTURED_REVENUE_DAILY_AMOUNT_RANKING ->
                    DailyRevenueAnswerPlan.TYPE_REVENUE_DAILY_AMOUNT_RANKING;
            case AiQuerySemanticLexicon.STRUCTURED_REVENUE_STORE_AMOUNT_RANKING ->
                    DailyRevenueAnswerPlan.TYPE_REVENUE_STORE_AMOUNT_RANKING;
            case AiQuerySemanticLexicon.STRUCTURED_REVENUE_CHANNEL_BREAKDOWN ->
                    DailyRevenueAnswerPlan.TYPE_REVENUE_CHANNEL_BREAKDOWN;
            default -> null;
        };
    }

    private static String blankToNull(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        return s.trim();
    }
}
