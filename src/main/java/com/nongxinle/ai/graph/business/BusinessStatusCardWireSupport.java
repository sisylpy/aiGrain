package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.harness.BusinessOverviewDishSalesReasonAgentHarnessSupport;
import com.nongxinle.ai.graph.business.execution.PurchaseSemanticExecutionIntent;
import com.nongxinle.ai.graph.business.execution.PurchaseSemanticExecutionIntentResolver;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import com.nongxinle.ai.semantic.contract.SemanticContractValidationDebug;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 经营状态四卡投影编排（不查库，除订货卡外只读 AnswerPlan / Tool 信封）。 */
public final class BusinessStatusCardWireSupport {

    private BusinessStatusCardWireSupport() {}

    public static BusinessStatusCardProjection resolveProjection(AiRunState state) {
        if (state == null) {
            return BusinessStatusCardProjection.NONE;
        }
        if (isBusinessOverviewMultiAgentMainline(state)) {
            return BusinessStatusCardProjection.FULL_QUARTET;
        }
        if (isRevenueOverviewComposerMainline(state)) {
            return BusinessStatusCardProjection.REVENUE_ONLY;
        }
        if (isPurchaseOverviewComposerMainline(state)) {
            if (isPurchasePeriodGoodsDetailMainline(state)) {
                return BusinessStatusCardProjection.NONE;
            }
            return BusinessStatusCardProjection.PURCHASE_ONLY;
        }
        if (isStockReduceComposerMainline(state)) {
            return BusinessStatusCardProjection.STOCK_RECONCILE_ONLY;
        }
        return BusinessStatusCardProjection.NONE;
    }

    public static List<Map<String, Object>> buildCards(
            AiRunState state,
            BusinessStatusCardProjection projection,
            BusinessStatusCardBuildDeps deps) {
        if (state == null || projection == null || projection == BusinessStatusCardProjection.NONE) {
            return List.of();
        }
        BusinessStatusCardBuildRequest req = BusinessStatusCardBuildRequest.fromRunState(state);
        BusinessStatusCardBuildDeps effective = deps != null ? deps : BusinessStatusCardBuildDeps.builder().build();
        List<Map<String, Object>> cards = new ArrayList<>(4);
        switch (projection) {
            case FULL_QUARTET -> {
                cards.add(RevenueReportCardSupport.build(state, req));
                cards.add(PurchaseCheckCardSupport.build(state, req, effective));
                cards.add(StockReconcileCardSupport.build(
                        state,
                        req,
                        effective.getDishCostAnalysisService(),
                        effective.getToolDepartmentResolutionSupport()));
                cards.add(ReorderReminderCardSupport.build(
                        state, req, effective.getReorderReminderService()));
            }
            case REVENUE_ONLY -> cards.add(RevenueReportCardSupport.build(state, req));
            case PURCHASE_ONLY -> cards.add(PurchaseCheckCardSupport.build(state, req, effective));
            case STOCK_RECONCILE_ONLY -> cards.add(
                    StockReconcileCardSupport.build(
                            state,
                            req,
                            effective.getDishCostAnalysisService(),
                            effective.getToolDepartmentResolutionSupport()));
            case REORDER_ONLY -> cards.add(
                    ReorderReminderCardSupport.build(state, req, effective.getReorderReminderService()));
            default -> { }
        }
        enrichRevenueCardDishSalesAgent(state, req, cards, effective);
        return normalizeCards(cards);
    }

    private static void enrichRevenueCardDishSalesAgent(
            AiRunState state,
            BusinessStatusCardBuildRequest req,
            List<Map<String, Object>> cards,
            BusinessStatusCardBuildDeps deps) {
        if (cards == null || cards.isEmpty() || deps == null) {
            return;
        }
        if (deps.getDepFoodBusinessInsightService() == null || deps.getDishSalesReasonAgent() == null) {
            return;
        }
        for (Map<String, Object> card : cards) {
            if (card == null) {
                continue;
            }
            if (!BusinessStatusCardTypes.REVENUE_REPORT_CARD.equals(String.valueOf(card.get("cardType")))) {
                continue;
            }
            Object payloadObj = card.get("payload");
            if (!(payloadObj instanceof Map<?, ?> payloadRaw)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) payloadRaw;
            Map<String, Object> factPack =
                    BusinessOverviewDishSalesReasonFactBuilder.build(
                            state,
                            req,
                            deps.getDepFoodBusinessInsightService(),
                            deps.getToolDepartmentResolutionSupport());
            BusinessOverviewDishSalesReasonOutputGuard.ComposeResult composed =
                    deps.getDishSalesReasonAgent().tryCompose(factPack, state);
            if (composed != null && StringUtils.hasText(composed.summary())) {
                LinkedHashMap<String, Object> dishSalesReason = new LinkedHashMap<>();
                dishSalesReason.put("summary", composed.summary());
                dishSalesReason.put("items", composed.items());
                payload.put("dishSalesReason", dishSalesReason);
                payload.put("dishSalesReasonSummary", composed.summary());
                payload.put("revenueReasonSummary", composed.summary());
            }
            payload.remove("topDishes");
            BusinessOverviewDishSalesReasonAgentHarnessSupport.recordRevenueCardWrittenSummary(
                    state, composed != null ? composed.summary() : null);
            break;
        }
    }

    public static boolean hasBusinessStatusCards(List<Map<String, Object>> cards) {
        if (cards == null || cards.isEmpty()) {
            return false;
        }
        for (Map<String, Object> card : cards) {
            if (card == null) {
                continue;
            }
            Object ct = card.get("cardType");
            if (ct != null && BusinessStatusCardTypes.isBusinessStatusCardType(ct.toString())) {
                return true;
            }
        }
        return false;
    }

    static List<Map<String, Object>> normalizeCards(List<Map<String, Object>> cards) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (cards == null) {
            return out;
        }
        for (Map<String, Object> card : cards) {
            if (card == null || card.isEmpty()) {
                continue;
            }
            out.add(ensurePayloadField(card));
        }
        return out;
    }

    private static Map<String, Object> ensurePayloadField(Map<String, Object> card) {
        Map<String, Object> copy = new LinkedHashMap<>(card);
        if (!copy.containsKey("payload") && copy.containsKey("data")) {
            copy.put("payload", copy.get("data"));
            copy.remove("data");
        }
        return copy;
    }

    private static boolean isBusinessOverviewMultiAgentMainline(AiRunState state) {
        if (state == null || !state.isBusinessOverviewPath()) {
            return false;
        }
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        if (rq == null) {
            return false;
        }
        if (!AiResolvedQueryIntent.BUSINESS_OVERVIEW.equals(rq.getEffectiveIntentCode())) {
            return false;
        }
        if (!AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW.equals(rq.getEffectivePathCode())) {
            return false;
        }
        if (!resolvedContextOrchestrationMultiAgentOverview(rq)) {
            return false;
        }
        if (rq.getQueryIntent() == null || !StringUtils.hasText(rq.getQueryIntent().getStructuredIntentDetail())) {
            return false;
        }
        return AiQuerySemanticLexicon.isStructuredBusinessOverviewFourDomainOrchestrationSurface(
                rq.getQueryIntent().getStructuredIntentDetail());
    }

    private static boolean resolvedContextOrchestrationMultiAgentOverview(AiResolvedQueryContext rq) {
        if (rq == null) {
            return false;
        }
        String tm = rq.getOrchestrationTaskMode();
        if (tm != null && "MULTI_AGENT".equalsIgnoreCase(tm.trim())) {
            return true;
        }
        return Boolean.TRUE.equals(rq.getOrchestrationMultiAgentRequired());
    }

    private static boolean isRevenueOverviewComposerMainline(AiRunState state) {
        if (state == null) {
            return false;
        }
        if (state.isRevenueOverviewPath()) {
            return true;
        }
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        if (rq == null) {
            return false;
        }
        return AiResolvedQueryIntent.REVENUE_OVERVIEW.equals(rq.getEffectiveIntentCode())
                && AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW.equals(rq.getEffectivePathCode());
    }

    private static boolean isPurchaseOverviewComposerMainline(AiRunState state) {
        if (state == null) {
            return false;
        }
        if (state.isPurchaseOverviewPath() || state.isPurchaseCostInsightPath()) {
            return true;
        }
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        if (rq == null) {
            return false;
        }
        return AiResolvedQueryIntent.PURCHASE_OVERVIEW.equals(rq.getEffectiveIntentCode())
                && AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW.equals(rq.getEffectivePathCode());
    }

    /** 「买了什么」明细卡与 {@code PURCHASE_CHECK_CARD} 互斥。 */
    public static boolean isPurchasePeriodGoodsDetailMainline(AiRunState state) {
        if (state == null) {
            return false;
        }
        PurchaseAnswerPlan plan = state.getPurchaseAnswerPlan();
        if (plan != null
                && PurchaseAnswerPlan.TYPE_PURCHASE_PERIOD_GOODS_DETAIL.equals(plan.getPlanType())) {
            return true;
        }
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        if (rq == null) {
            return false;
        }
        if (PurchaseSemanticExecutionIntentResolver.isPeriodGoodsListContractId(
                resolveMatchedContractId(rq))) {
            return true;
        }
        PurchaseSemanticExecutionIntent executionIntent = PurchaseSemanticExecutionIntentResolver.resolve(rq);
        if (executionIntent != null
                && executionIntent.isActive()
                && PurchaseSemanticExecutionIntent.EXEC_PERIOD_GOODS_LIST.equals(
                        executionIntent.getExecutionIntentType())) {
            return true;
        }
        if (rq.getQueryIntent() != null) {
            String wire = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                    rq.getQueryIntent().getStructuredIntentDetail());
            if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_PERIOD_GOODS_LIST.equals(wire)) {
                return true;
            }
        }
        return false;
    }

    private static String resolveMatchedContractId(AiResolvedQueryContext rq) {
        if (rq == null || rq.getQuerySemanticParse() == null) {
            return null;
        }
        String fromSlots = SemanticContractCompletionEngine.extractSelectedContractId(rq.getQuerySemanticParse());
        if (StringUtils.hasText(fromSlots)) {
            return fromSlots.trim();
        }
        SemanticContractValidationDebug v = rq.getSemanticContractValidation();
        if (v != null && StringUtils.hasText(v.getMatchedContractId())) {
            return v.getMatchedContractId().trim();
        }
        return null;
    }

    private static boolean isStockReduceComposerMainline(AiRunState state) {
        if (state == null) {
            return false;
        }
        if (state.isStockReduceQueryPath()) {
            return true;
        }
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        if (rq == null) {
            return false;
        }
        return AiResolvedQueryIntent.STOCK_REDUCE_QUERY.equals(rq.getEffectiveIntentCode())
                && AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY.equals(rq.getEffectivePathCode());
    }

    private static boolean isDishSalesComposerMainline(AiRunState state) {
        if (state == null) {
            return false;
        }
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        if (rq == null) {
            return false;
        }
        String effIntentRaw = rq.getEffectiveIntentCode();
        String effPathRaw = rq.getEffectivePathCode();
        if (!StringUtils.hasText(effIntentRaw) || !StringUtils.hasText(effPathRaw)) {
            return false;
        }
        return AiResolvedQueryIntent.DISH_SALES_QUERY.equals(effIntentRaw.trim())
                && AiResolvedQueryIntent.PATH_DISH_SALES_QUERY.equals(effPathRaw.trim());
    }
}
