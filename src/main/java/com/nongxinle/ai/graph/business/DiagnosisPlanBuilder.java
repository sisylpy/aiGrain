package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.agent.business.BusinessDiagnosisAgentV1;
import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.BusinessOverviewAnswerPlan;
import com.nongxinle.ai.dto.business.DailyRevenueAnswerPlan;
import com.nongxinle.ai.dto.business.DiagnosisPlan;
import com.nongxinle.ai.dto.business.DishProfitAnswerPlan;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.dto.business.StockReduceAnswerPlan;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import com.nongxinle.ai.security.AiAnswerBoundary;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.alibaba.fastjson2.JSON;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * 只读子域 AnswerPlan，组装 {@link DiagnosisPlan}；不查库、不从 {@link AiRunState#getToolResults()} 重算指标。
 */
public final class DiagnosisPlanBuilder {

    private static final String SRC_PURCHASE = "PurchaseAnswerPlan";
    private static final String SRC_STOCK = "StockReduceAnswerPlan";
    private static final String SRC_DISH = "DishProfitAnswerPlan";
    private static final String SRC_REVENUE = "DailyRevenueAnswerPlan";

    private static final int MAX_SUMMARY_KEYS_PER_PLAN = 5;
    private static final int MAX_FOCUS_ROW_KEYS = 8;

    private DiagnosisPlanBuilder() {
    }

    /**
     * 满足 {@link #shouldAttachDiagnosisPlan} 时组装 {@link DiagnosisPlan} 写入 state；否则清空。
     * <p>
     * 典型表面：{@code business_diagnosis_path}（**不校验本句话术**，追问/省略主语亦挂载）、{@code business_overview_path}、经营问句收敛到采购/库房视角
     * （intentConvergence from BUSINESS_OVERVIEW）。应在 Tools 与相关 Agent 完成后调用（如 {@link StubOutcomeReviewNode}）。
     */
    public static void attachIfApplicable(AiRunState state) {
        if (state == null) {
            return;
        }
        if (!shouldAttachDiagnosisPlan(state)) {
            state.setDiagnosisPlan(null);
            return;
        }

        List<String> consumed = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        PurchaseAnswerPlan pPurchase = state.getPurchaseAnswerPlan();
        StockReduceAnswerPlan pStock = state.getStockReduceAnswerPlan();
        DishProfitAnswerPlan pDish = state.getDishProfitAnswerPlan();
        DailyRevenueAnswerPlan pRevenue = state.getRevenueAnswerPlan();

        boolean revenueDenied = AiAnswerBoundary.isToolPermissionDenied(
                state.getPermissionDenials(), AiBusinessToolIds.REVENUE_QUERY);
        boolean dishDenied = AiAnswerBoundary.isToolPermissionDenied(
                state.getPermissionDenials(), AiBusinessToolIds.DISH_PROFIT_ANALYSIS);
        if (revenueDenied) {
            pRevenue = null;
        }
        if (dishDenied) {
            pDish = null;
        }

        if (pPurchase != null) {
            consumed.add(SRC_PURCHASE + ":" + nullToEmpty(pPurchase.getPlanType()));
        } else {
            missing.add(SRC_PURCHASE);
        }
        if (pStock != null) {
            consumed.add(SRC_STOCK + ":" + nullToEmpty(pStock.getPlanType()));
        } else {
            missing.add(SRC_STOCK);
        }
        if (pDish != null) {
            consumed.add(SRC_DISH + ":" + nullToEmpty(pDish.getPlanType()));
        } else {
            missing.add(SRC_DISH);
        }
        if (pRevenue != null) {
            consumed.add(SRC_REVENUE + ":" + nullToEmpty(pRevenue.getPlanType()));
        } else {
            missing.add(SRC_REVENUE);
        }

        String scopeLabel = firstNonBlank(
                pPurchase != null ? pPurchase.getScopeLabel() : null,
                pStock != null ? pStock.getScopeLabel() : null,
                pDish != null ? pDish.getScopeLabel() : null,
                pRevenue != null ? pRevenue.getScopeLabel() : null);
        String timeLabel = firstNonBlank(
                pPurchase != null ? pPurchase.getTimeLabel() : null,
                pStock != null ? pStock.getTimeLabel() : null,
                pDish != null ? pDish.getTimeLabel() : null,
                pRevenue != null ? pRevenue.getTimeLabel() : null);

        List<Map<String, Object>> evidence = new ArrayList<>();
        if (pPurchase != null) {
            appendPlanAttached(evidence, SRC_PURCHASE, pPurchase.getPlanType());
            appendSummarySlice(evidence, SRC_PURCHASE, pPurchase.getPlanType(), pPurchase.getSummary());
        }
        if (pStock != null) {
            appendPlanAttached(evidence, SRC_STOCK, pStock.getPlanType());
            appendSummarySlice(evidence, SRC_STOCK, pStock.getPlanType(), pStock.getSummary());
        }
        if (pDish != null) {
            appendPlanAttached(evidence, SRC_DISH, pDish.getPlanType());
            appendFirstFocusRowSlice(evidence, SRC_DISH, pDish.getPlanType(), pDish.getFocusRows());
        }
        if (pRevenue != null) {
            appendPlanAttached(evidence, SRC_REVENUE, pRevenue.getPlanType());
            appendSummarySlice(evidence, SRC_REVENUE, pRevenue.getPlanType(), pRevenue.getSummary());
        }

        StringBuilder summarySb = new StringBuilder();
        boolean overviewHarness = isBusinessOverviewHarnessSurface(state);
        if (overviewHarness) {
            summarySb.append("本轮经营概览摘要（来自子域 AnswerPlan 聚合，未重算）：已挂载 ");
            summarySb.append(consumed.size());
            summarySb.append(" 份子域计划");
            if (!missing.isEmpty()) {
                summarySb.append("。本轮数据链暂未覆盖或未挂载的子域计划：");
                summarySb.append(String.join("、", missing));
                summarySb.append("；下方仍可基于已挂载数据做简述。");
            } else {
                summarySb.append("。");
            }
        } else {
            summarySb.append("本轮综合经营诊断（AnswerPlan 聚合，未重算）：已挂载 ");
            summarySb.append(consumed.size());
            summarySb.append(" 份子域计划");
            if (!missing.isEmpty()) {
                summarySb.append("；未挂载：");
                summarySb.append(String.join("、", missing));
            }
            summarySb.append("。详细证据见 evidenceRows。");
        }

        LinkedHashMap<String, Object> debug = new LinkedHashMap<>();
        debug.put("consumedAnswerPlans", new ArrayList<>(consumed));
        debug.put("missingAnswerPlans", new ArrayList<>(missing));
        debug.put("fallbackUsed", false);
        if (revenueDenied || dishDenied) {
            List<String> excludedSubjects = new ArrayList<>();
            if (revenueDenied) {
                excludedSubjects.add(AiBusinessToolIds.REVENUE_QUERY);
            }
            if (dishDenied) {
                excludedSubjects.add(AiBusinessToolIds.DISH_PROFIT_ANALYSIS);
            }
            debug.put("permissionExcludedAnswerPlanSubjects", excludedSubjects);
        }
        if (state.isBusinessDiagnosisPath()) {
            debug.put("attachSurface", "business_diagnosis_path");
        } else if (state.isBusinessOverviewPath()) {
            debug.put("attachSurface", "business_overview_path");
        } else {
            debug.put("attachSurface", "business_overview_or_converged");
        }
        if (consumed.isEmpty()) {
            debug.put("undiagnosableReason", "本轮可读的子域 AnswerPlan 均缺失（仅聚合契约骨架）");
        }

        List<Map<String, Object>> focusFindings = new ArrayList<>();
        List<String> diagnosisMissingSections = new ArrayList<>();
        List<String> diagnosisWarningsFromAvailability = new ArrayList<>();
        if (state.isBusinessDiagnosisPath()) {
            populateDiagnosisAvailabilityGaps(missing, diagnosisMissingSections, diagnosisWarningsFromAvailability);
        } else if (overviewHarness) {
            // 经营概览：出库/菜品毛利链路可能未编入规划，缺失不应等同「不可用」或与诊断 path 同款告警。
            if (consumed.isEmpty()) {
                LinkedHashMap<String, Object> f0 = new LinkedHashMap<>();
                f0.put("code", "NO_SUB_PLANS_ATTACHED");
                f0.put("detail", "本轮未挂载任何可读子域 AnswerPlan；请先确认营收与采购链路是否已成功执行工具。");
                focusFindings.add(f0);
            }
        } else {
            appendMissingSubPlanFocusFindingsIfAny(focusFindings, missing);
        }

        overlayBusinessOverviewAggregateDebug(debug, focusFindings, state, overviewHarness);

        List<Map<String, Object>> storeCompareEvidence =
                buildStoreCompareEvidenceIfApplicable(state, debug);
        String storeCompareConclusion = finalizeStoreCompareEvidence(storeCompareEvidence);

        DiagnosisPlan plan = DiagnosisPlan.builder()
                .planType(DiagnosisPlan.TYPE_OVERALL_BUSINESS_DIAGNOSIS)
                .scopeLabel(scopeLabel)
                .timeLabel(timeLabel)
                .diagnosisLevel(consumed.isEmpty() ? "NOTICE" : "NORMAL")
                .summary(summarySb.toString())
                .focusFindings(focusFindings)
                .evidenceRows(evidence)
                .storeCompareEvidence(storeCompareEvidence)
                .storeCompareConclusion(storeCompareConclusion)
                .debug(debug)
                .build();

        if (state.isBusinessDiagnosisPath()) {
            plan.getMissingSections().addAll(diagnosisMissingSections);
            plan.getWarnings().addAll(diagnosisWarningsFromAvailability);
            BusinessOverviewAnswerPlan bopMerge = state.getBusinessOverviewAnswerPlan();
            if (bopMerge != null
                    && BusinessOverviewAnswerPlan.PLAN_TYPE_BUSINESS_DIAGNOSIS_MULTI_AGENT_V1.equals(
                    bopMerge.getPlanType())) {
                if (bopMerge.getWarnings() != null && !bopMerge.getWarnings().isEmpty()) {
                    plan.getWarnings().addAll(bopMerge.getWarnings());
                }
                if (bopMerge.getMissingSections() != null && !bopMerge.getMissingSections().isEmpty()) {
                    for (String s : bopMerge.getMissingSections()) {
                        if (s != null && !s.isBlank() && !plan.getMissingSections().contains(s)) {
                            plan.getMissingSections().add(s.trim());
                        }
                    }
                }
            }
            if (!consumed.isEmpty()) {
                plan.getDebug().put("diagnosisAgentV1", "BusinessDiagnosisAgentV1.enrich");
                BusinessDiagnosisAgentV1.enrich(state, plan, pPurchase, pStock, pDish, pRevenue);
                if (!missing.isEmpty()) {
                    plan.getDebug().put("diagnosisV1IncompleteEvidenceDomains", new ArrayList<>(missing));
                }
            } else {
                plan.setDiagnosisType(DiagnosisPlan.DIAGNOSIS_TYPE_V1_AGGREGATE);
                plan.setRiskLevel("NOTICE");
                plan.setOverallJudgement("当前查询范围内未能汇总诊断所需的子域经营数据，无法给出基于证据的诊断。");
                plan.getDebug().put("diagnosisAgentV1", "skipped_no_answer_plans");
            }
        }

        state.setDiagnosisPlan(plan);

        if (state.isBusinessDiagnosisPath() && consumed.isEmpty()
                && (state.getClarificationQuestion() == null || state.getClarificationQuestion().isBlank())) {
            state.setNeedClarification(true);
            state.setClarificationQuestion("当前查询范围内未能汇总诊断所需经营数据，请确认时间、门店范围或权限后再试。");
        }
    }

    private static void populateDiagnosisAvailabilityGaps(
            List<String> missing,
            List<String> outSections,
            List<String> outWarnings) {
        if (missing == null) {
            return;
        }
        for (String m : missing) {
            String sec = diagnosisSectionKey(m);
            if (sec != null && !outSections.contains(sec)) {
                outSections.add(sec);
            }
        }
        if (!missing.isEmpty()) {
            outWarnings.add("以下子域 AnswerPlan 本轮未挂载，诊断仅在已挂载域上继续：" + String.join("、", missing));
        }
    }

    private static String diagnosisSectionKey(String missingToken) {
        if (missingToken == null) {
            return null;
        }
        if (missingToken.startsWith(SRC_REVENUE)) {
            return "revenue";
        }
        if (missingToken.startsWith(SRC_PURCHASE)) {
            return "purchase";
        }
        if (missingToken.startsWith(SRC_STOCK)) {
            return "stockReduce";
        }
        if (missingToken.startsWith(SRC_DISH)) {
            return "dishProfit";
        }
        return null;
    }

    /**
     * 是否应为本轮挂载/保留 DiagnosisPlan。必须 contract-locked parse + completed canonical wire 在 BusinessDiagnosis/BusinessOverview
     * 矩阵内才放行。非 contract-locked 时直接 return false（不允许仅凭 path / old intent 挂载）。
     */
    public static boolean shouldAttachDiagnosisPlan(AiRunState state) {
        if (state == null) {
            return false;
        }
        // gate: must be contract-locked parse
        AiQuerySemanticParseResult sem =
                state.getResolvedQueryContext() != null
                        ? state.getResolvedQueryContext().getQuerySemanticParse()
                        : null;
        if (!SemanticContractCompletionEngine.isContractLockedParse(sem)) {
            return false;
        }
        // must have completed canonical wire
        AiResolvedQueryIntent qi =
                state.getResolvedQueryContext() != null
                        ? state.getResolvedQueryContext().getQueryIntent()
                        : null;
        String wire = qi != null ? qi.getStructuredIntentDetail() : null;
        if (!StringUtils.hasText(wire)) {
            return false;
        }
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wire.trim());
        if (!StringUtils.hasText(canon)) {
            return false;
        }
        // wire must belong to accepted BusinessDiagnosis / BusinessOverview canonical wires
        if (AiQuerySemanticLexicon.isBusinessDiagnosisSummaryStructuredDetail(canon)
                || AiQuerySemanticLexicon.isStorePriorityRankingStructuredDetail(canon)
                || AiQuerySemanticLexicon.isStoreRiskReasonExplanationStructuredDetail(canon)
                || AiQuerySemanticLexicon.isStoreDomainAttributionPurchaseStructuredDetail(canon)
                || AiQuerySemanticLexicon.isStoreDomainAttributionStockReduceStructuredDetail(canon)
                || AiQuerySemanticLexicon.isStoreDomainAttributionDishProfitStructuredDetail(canon)
                || AiQuerySemanticLexicon.isDiagnosisActionSuggestionStructuredDetail(canon)
                || AiQuerySemanticLexicon.STRUCTURED_BUSINESS_STORE_COMPARE_DIAGNOSIS.equals(canon)) {
            return true;
        }
        if (AiQuerySemanticLexicon.isStructuredBusinessOverviewFourDomainOrchestrationSurface(canon)) {
            return true;
        }
        return false;
    }

    /**
     * Composer：新版 {@link DiagnosisPlan} 已挂载时，在哪些编排表面上优先确定性宣读（与 {@link #shouldAttachDiagnosisPlan} 表面一致，不再校验话术）。
     */
    public static boolean shouldPreferDiagnosisPlanInComposer(AiRunState state) {
        if (state == null || state.getDiagnosisPlan() == null) {
            return false;
        }
        if (state.isDishProfitPath() || state.isRevenueOverviewPath() || state.isStockReduceQueryPath()) {
            return false;
        }
        if (state.isBusinessDiagnosisPath()) {
            return true;
        }
        // 经营概览 MULTI_AGENT 表面：对白走四域 AnswerPlan / Composer，不向用户宣读 DiagnosisPlan 内部骨架文案。
        if (state.isBusinessOverviewPath()) {
            return false;
        }
        if (state.isPurchaseCostInsightPath() && isBusinessToPurchaseOverviewConvergence(state)) {
            return true;
        }
        return state.isWarehouseStockOverviewPath() && isBusinessToWarehouseStockConvergence(state);
    }

    private static boolean isBusinessToPurchaseOverviewConvergence(AiRunState state) {
        Map<String, String> ic = state.getIntentConvergence();
        return ic != null
                && "BUSINESS_OVERVIEW".equals(ic.get("from"))
                && "PURCHASE_OVERVIEW".equals(ic.get("to"));
    }

    private static boolean isBusinessToWarehouseStockConvergence(AiRunState state) {
        Map<String, String> ic = state.getIntentConvergence();
        return ic != null
                && "BUSINESS_OVERVIEW".equals(ic.get("from"))
                && "WAREHOUSE_STOCK_OVERVIEW".equals(ic.get("to"));
    }

    private static boolean isBusinessOverviewHarnessSurface(AiRunState state) {
        return state != null && state.isBusinessOverviewPath() && !state.isBusinessDiagnosisPath();
    }

    private static void overlayBusinessOverviewAggregateDebug(LinkedHashMap<String, Object> debug,
            List<Map<String, Object>> focusFindings,
            AiRunState state,
            boolean overviewHarness) {
        if (!overviewHarness || state == null) {
            return;
        }
        BusinessOverviewAnswerPlan bop = state.getBusinessOverviewAnswerPlan();
        if (bop == null) {
            return;
        }
        if (bop.getWarnings() != null && !bop.getWarnings().isEmpty()) {
            debug.put("businessOverviewWarningsSnapshot", new ArrayList<>(bop.getWarnings()));
        }
        if (bop.getMissingSections() != null && !bop.getMissingSections().isEmpty()) {
            debug.put("businessOverviewMissingSectionsSnapshot", new ArrayList<>(bop.getMissingSections()));
        }
        Map<String, Object> bd = bop.getDebug();
        if (bd == null) {
            return;
        }
        Object md = bd.get("missingAnswerPlans");
        if (md instanceof List<?> lap) {
            List<String> copy = new ArrayList<>();
            for (Object o : lap) {
                copy.add(String.valueOf(o));
            }
            debug.put("missingAnswerPlanDetails", copy);
            appendOverviewDomainGapFindings(focusFindings, copy);
        }
    }

    private static void appendOverviewDomainGapFindings(List<Map<String, Object>> focusFindings, List<String> details) {
        if (focusFindings == null || details == null) {
            return;
        }
        for (String line : details) {
            if (line == null || line.isBlank()) {
                continue;
            }
            if (line.startsWith(SRC_STOCK + ":") || line.startsWith(SRC_DISH + ":")) {
                LinkedHashMap<String, Object> f = new LinkedHashMap<>();
                f.put("code", "OVERVIEW_DOMAIN_GAP");
                f.put("detail", line);
                focusFindings.add(f);
            }
        }
    }

    private static void appendMissingSubPlanFocusFindingsIfAny(List<Map<String, Object>> focusFindings, List<String> missing) {
        if (missing == null || missing.isEmpty()) {
            return;
        }
        LinkedHashMap<String, Object> f1 = new LinkedHashMap<>();
        f1.put("code", "MISSING_SUB_PLANS");
        f1.put("detail", "以下子域计划本轮未挂载：" + String.join("、", missing));
        focusFindings.add(f1);
    }

    private static void appendPlanAttached(List<Map<String, Object>> evidence, String sourcePlan, String planType) {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("sourcePlan", sourcePlan);
        row.put("planType", planType);
        row.put("label", "__planAttached");
        row.put("value", true);
        evidence.add(row);
    }

    private static void appendSummarySlice(
            List<Map<String, Object>> evidence,
            String sourcePlan,
            String planType,
            Map<String, Object> summary) {
        if (summary == null || summary.isEmpty()) {
            return;
        }
        int n = 0;
        for (Map.Entry<String, Object> e : summary.entrySet()) {
            if (n >= MAX_SUMMARY_KEYS_PER_PLAN) {
                break;
            }
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("sourcePlan", sourcePlan);
            row.put("planType", planType);
            row.put("label", e.getKey());
            row.put("value", e.getValue());
            evidence.add(row);
            n++;
        }
    }

    private static void appendFirstFocusRowSlice(
            List<Map<String, Object>> evidence,
            String sourcePlan,
            String planType,
            List<Map<String, Object>> focusRows) {
        if (focusRows == null || focusRows.isEmpty()) {
            return;
        }
        Map<String, Object> first = focusRows.get(0);
        int n = 0;
        for (Map.Entry<String, Object> e : first.entrySet()) {
            if (n >= MAX_FOCUS_ROW_KEYS) {
                break;
            }
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("sourcePlan", sourcePlan);
            row.put("planType", planType);
            row.put("label", "focusRows[0]." + e.getKey());
            row.put("value", e.getValue());
            evidence.add(row);
            n++;
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String firstNonBlank(String... parts) {
        if (parts == null) {
            return null;
        }
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                return p;
            }
        }
        return null;
    }

    private static boolean isStoreCompareDiagnosisWire(AiResolvedQueryContext rq) {
        if (rq == null || rq.getQueryIntent() == null) {
            return false;
        }
        String raw = rq.getQueryIntent().getStructuredIntentDetail();
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(raw);
        return AiQuerySemanticLexicon.STRUCTURED_BUSINESS_STORE_COMPARE_DIAGNOSIS.equals(canon);
    }

    /**
     * 仅 business_store_status_compare_diagnosis：从四域 toolResults 组装门店横表；不读 AnswerPlan、不重算 SQL。
     */
    private static List<Map<String, Object>> buildStoreCompareEvidenceIfApplicable(
            AiRunState state,
            LinkedHashMap<String, Object> diagDebug) {
        if (state != null && state.getResolvedQueryContext() != null) {
            AiResolvedOrgScope org = state.getResolvedQueryContext().getOrgScope();
            if (org != null && AiResolvedOrgScope.SCOPE_WAREHOUSE.equals(org.getScopeType())) {
                diagDebug.put("storeCompareEvidenceEmptyReason", "warehouse_scope_no_multi_store_compare");
                diagDebug.put("storeCompareEvidenceRows", 0);
                return new ArrayList<>();
            }
        }
        if (state == null || !isStoreCompareDiagnosisWire(state.getResolvedQueryContext())) {
            return new ArrayList<>();
        }
        diagDebug.put("storeCompareEvidenceWire", AiQuerySemanticLexicon.STRUCTURED_BUSINESS_STORE_COMPARE_DIAGNOSIS);

        boolean revenueOk = toolEnvelopeSuccess(state, AiBusinessToolIds.REVENUE_QUERY);
        boolean purchaseOk = toolEnvelopeSuccess(state, AiBusinessToolIds.PURCHASE_OVERVIEW);
        boolean stockOk = toolEnvelopeSuccess(state, AiBusinessToolIds.STOCK_REDUCE_QUERY);
        boolean dishOk = toolEnvelopeSuccess(state, AiBusinessToolIds.DISH_PROFIT_ANALYSIS);

        Map<Long, Double> revenueByStore = revenueOk
                ? loadRevenueRankingByStore(state.getToolResults().get(AiBusinessToolIds.REVENUE_QUERY))
                : Collections.emptyMap();
        PurchaseOverviewExtract purchaseEx = purchaseOk
                ? loadPurchaseStoreAmounts(state.getToolResults().get(AiBusinessToolIds.PURCHASE_OVERVIEW))
                : PurchaseOverviewExtract.empty();
        Map<Long, Double> stockByStore = stockOk
                ? loadStockStoreGrandTotals(state.getToolResults().get(AiBusinessToolIds.STOCK_REDUCE_QUERY))
                : Collections.emptyMap();
        boolean stockTablePresent = !stockByStore.isEmpty();

        String dishProfitCoverage = resolveDishProfitCoverageFlag(dishOk,
                state.getToolResults().get(AiBusinessToolIds.DISH_PROFIT_ANALYSIS));

        Map<Long, String> nameById = new LinkedHashMap<>(purchaseEx.nameByStoreDepartmentId());
        if (revenueOk) {
            mergeRevenueStoreNames(nameById, state.getToolResults().get(AiBusinessToolIds.REVENUE_QUERY));
        }

        TreeSet<Long> union = new TreeSet<>();
        union.addAll(revenueByStore.keySet());
        union.addAll(purchaseEx.amountByStoreDepartmentId().keySet());
        union.addAll(purchaseEx.visibleStoreDepartmentIds());
        union.addAll(stockByStore.keySet());

        if (union.isEmpty()) {
            diagDebug.put("storeCompareEvidenceRows", 0);
            diagDebug.put("storeCompareEvidenceEmptyReason", "no_store_keys_from_tools");
            return new ArrayList<>();
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (Long storeId : union) {
            out.add(buildOneStoreCompareRow(
                    storeId,
                    nameById,
                    revenueByStore,
                    purchaseEx,
                    stockByStore,
                    stockTablePresent,
                    revenueOk,
                    purchaseOk,
                    stockOk,
                    dishProfitCoverage));
        }
        diagDebug.put("storeCompareEvidenceRows", out.size());
        return out;
    }

    /**
     * 门店对比 Plan 物化：稳定排序、预计算采购/营业额比、生成谨慎结论（Composer 只宣读，不重算）。
     *
     * @return 谨慎结论文本；无证据行时 {@code null}
     */
    private static String finalizeStoreCompareEvidence(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        rows.removeIf(Objects::isNull);
        rows.sort(Comparator.comparing(
                        DiagnosisPlanBuilder::storeCompareRowRevenueAmount,
                        Comparator.nullsFirst(Double::compareTo))
                .reversed()
                .thenComparing(DiagnosisPlanBuilder::storeCompareRowLabel));
        for (Map<String, Object> row : rows) {
            if (row != null) {
                row.put("purchaseToRevenueRatioLine", formatStoreComparePurchaseToRevenueRatioLine(row));
            }
        }
        return buildStoreCompareCautiousConclusion(rows);
    }

    private static Double storeCompareRowRevenueAmount(Map<String, Object> row) {
        return parseDoubleLoose(row == null ? null : row.get("revenueAmount"));
    }

    private static String storeCompareRowLabel(Map<String, Object> row) {
        if (row == null) {
            return "";
        }
        Object name = row.get("storeName");
        if (name != null && !name.toString().isBlank()) {
            return name.toString().trim();
        }
        Object id = row.get("storeDepartmentId");
        return id != null ? ("门店 " + id) : "未知门店";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> storeCompareRowCoverage(Map<String, Object> row) {
        if (row == null) {
            return Collections.emptyMap();
        }
        Object dc = row.get("dataCoverage");
        if (dc instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Collections.emptyMap();
    }

    private static String formatStoreComparePurchaseToRevenueRatioLine(Map<String, Object> row) {
        Map<String, Object> dc = storeCompareRowCoverage(row);
        boolean revOk = Boolean.TRUE.equals(dc.get("revenueAvailable"));
        boolean purOk = Boolean.TRUE.equals(dc.get("purchaseAvailable"));
        Double rev = parseDoubleLoose(row.get("revenueAmount"));
        Double pur = parseDoubleLoose(row.get("purchaseAmount"));
        if (!revOk || !purOk || rev == null || pur == null || rev <= 0) {
            return "暂无法计算（营业额或采购缺失，或营业额≤0）";
        }
        double pct = pur / rev * 100.0;
        return String.format(Locale.CHINA, "约 %.1f%%", pct);
    }

    private static String buildStoreCompareCautiousConclusion(List<Map<String, Object>> rows) {
        String bestRevName = null;
        Double bestRev = null;
        final class RatioPick {
            String name;
            double ratio;
        }
        List<RatioPick> ratioPicks = new ArrayList<>();
        boolean anyStockStoreLevel = false;

        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            Map<String, Object> dc = storeCompareRowCoverage(row);
            Double rev = parseDoubleLoose(row.get("revenueAmount"));
            if (Boolean.TRUE.equals(dc.get("revenueAvailable")) && rev != null) {
                if (bestRev == null || rev > bestRev) {
                    bestRev = rev;
                    bestRevName = storeCompareRowLabel(row);
                }
            }
            Double pur = parseDoubleLoose(row.get("purchaseAmount"));
            if (Boolean.TRUE.equals(dc.get("revenueAvailable"))
                    && Boolean.TRUE.equals(dc.get("purchaseAvailable"))
                    && rev != null
                    && rev > 0
                    && pur != null) {
                RatioPick p = new RatioPick();
                p.name = storeCompareRowLabel(row);
                p.ratio = pur / rev;
                ratioPicks.add(p);
            }
            if (Boolean.TRUE.equals(dc.get("stockReduceAvailable"))) {
                anyStockStoreLevel = true;
            }
        }

        StringBuilder c = new StringBuilder();
        if (bestRevName != null && bestRev != null) {
            String y = storeCompareFmtYuan(bestRev);
            c.append("从营业额看，").append(bestRevName).append("更高（约 ").append(y != null ? y : bestRev).append(" 元）。");
        }

        if (ratioPicks.size() >= 2) {
            RatioPick lowest = ratioPicks.get(0);
            for (RatioPick p : ratioPicks) {
                if (p.ratio < lowest.ratio) {
                    lowest = p;
                }
            }
            if (c.length() > 0) {
                c.append(" ");
            }
            c.append("从采购占营业额比例看，").append(lowest.name).append("占比更低（约 ")
                    .append(String.format(Locale.CHINA, "%.1f%%", lowest.ratio * 100.0))
                    .append("），相对采购压力更小。");
        } else if (ratioPicks.size() == 1) {
            RatioPick only = ratioPicks.get(0);
            if (c.length() > 0) {
                c.append(" ");
            }
            c.append(only.name).append(" 的采购占营业额比例约 ")
                    .append(String.format(Locale.CHINA, "%.1f%%", only.ratio * 100.0))
                    .append("（仅单店可算，不做门店间优劣排序）。");
        }

        if (c.length() > 0) {
            c.append('\n');
        }
        if (!anyStockStoreLevel) {
            c.append("本轮缺少可靠的门店级出库合计，无法把出库压力纳入门店对比。\n");
        } else {
            c.append("出库虽有个别门店级合计，仍建议结合完整出库明细再做判断。\n");
        }
        c.append("菜品毛利仅集团/范围汇总，没有门店级拆分，不能用于门店对比。\n");
        c.append("因此不宜仅凭营业额判断哪家「经营更好」，也不宜在缺少完备门店级证据时得出完整经营优劣定论。");
        return c.toString().trim();
    }

    private static String storeCompareFmtYuan(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            BigDecimal b = n instanceof BigDecimal bd ? bd : BigDecimal.valueOf(n.doubleValue());
            b = b.setScale(2, RoundingMode.HALF_UP);
            if (b.stripTrailingZeros().scale() <= 0) {
                return b.stripTrailingZeros().toPlainString();
            }
            return b.toPlainString();
        }
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static Map<String, Object> buildOneStoreCompareRow(
            Long storeDepartmentId,
            Map<Long, String> nameById,
            Map<Long, Double> revenueByStore,
            PurchaseOverviewExtract purchaseEx,
            Map<Long, Double> stockByStore,
            boolean stockTablePresent,
            boolean revenueToolOk,
            boolean purchaseToolOk,
            boolean stockToolOk,
            String dishProfitCoverage) {

        Double revenueAmount = revenueByStore.get(storeDepartmentId);
        Double purchaseAmount = purchaseEx.amountByStoreDepartmentId().get(storeDepartmentId);
        Double stockAmount = stockByStore.get(storeDepartmentId);

        String storeName = nameById.getOrDefault(storeDepartmentId, "");

        List<String> missingReasons = new ArrayList<>();
        if (!revenueToolOk) {
            missingReasons.add("revenue_tool_not_successful");
        } else if (revenueAmount == null) {
            missingReasons.add("revenue_no_row_for_store");
        }
        if (!purchaseToolOk) {
            missingReasons.add("purchase_tool_not_successful");
        } else if (purchaseAmount == null
                && !purchaseEx.storesInPurchasePayload().contains(storeDepartmentId)) {
            missingReasons.add("purchase_no_row_for_store");
        }
        if (!stockToolOk) {
            missingReasons.add("stock_reduce_tool_not_successful");
        } else if (!stockTablePresent) {
            missingReasons.add("stock_reduce_per_store_table_absent");
        } else if (stockAmount == null) {
            missingReasons.add("stock_reduce_no_row_for_store");
        }
        missingReasons.add("dish_profit_no_store_level_in_tool");

        boolean revenueAvailable = revenueToolOk && revenueAmount != null;
        boolean purchaseAvailable = purchaseToolOk
                && (purchaseAmount != null || purchaseEx.storesInPurchasePayload().contains(storeDepartmentId));
        boolean stockReduceAvailable = stockToolOk && stockTablePresent && stockAmount != null;

        Map<String, Object> dataCoverage = new LinkedHashMap<>();
        dataCoverage.put("revenueAvailable", revenueAvailable);
        dataCoverage.put("purchaseAvailable", purchaseAvailable);
        dataCoverage.put("stockReduceAvailable", stockReduceAvailable);
        dataCoverage.put("dishProfitStoreLevelAvailable", Boolean.FALSE);
        dataCoverage.put("missingReasons", new ArrayList<>(missingReasons));

        List<String> mainReasons = new ArrayList<>();
        if (!revenueAvailable && revenueToolOk) {
            mainReasons.add("本轮营收工具未提供该门店排行金额");
        }
        if (!purchaseAvailable && purchaseToolOk) {
            mainReasons.add("本轮采购工具未覆盖该门店金额行");
        }
        if (!stockReduceAvailable && stockToolOk) {
            mainReasons.add(stockTablePresent ? "本轮出库工具未提供该门店合计" : "本轮出库工具未附带门店级出库合计表");
        }
        mainReasons.add("菜品毛利工具无门店级拆分，仅集团/范围汇总");

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("storeDepartmentId", storeDepartmentId);
        row.put("storeName", storeName);
        row.put("revenueAmount", revenueAmount);
        row.put("purchaseAmount", purchaseAmount);
        row.put("stockReduceAmount", stockAmount);
        row.put("dishProfitCoverage", dishProfitCoverage);
        row.put("mainReasons", mainReasons);
        row.put("dataCoverage", dataCoverage);
        return row;
    }

    private static String resolveDishProfitCoverageFlag(boolean dishToolOk, Object dishEnv) {
        if (!dishToolOk) {
            return "NA";
        }
        Object dataObj = toolEnvelopeData(dishEnv);
        if (!(dataObj instanceof Map<?, ?> dm) || dm.isEmpty()) {
            return "NA";
        }
        Object rows = dm.get("dishRows");
        if (rows instanceof List<?> list && !list.isEmpty()) {
            return "AGGREGATE_ONLY";
        }
        Object gp = dm.get("portfolioGrossProfitAmount");
        if (gp != null && !gp.toString().isBlank()) {
            return "AGGREGATE_ONLY";
        }
        Object bis = dm.get("businessInsightSummary");
        if (bis instanceof Map<?, ?> bm && !bm.isEmpty()) {
            return "AGGREGATE_ONLY";
        }
        return "NA";
    }

    private static boolean toolEnvelopeSuccess(AiRunState state, String toolKey) {
        if (state == null || state.getToolResults() == null) {
            return false;
        }
        return toolEnvelopeSuccess(state.getToolResults().get(toolKey));
    }

    private static boolean toolEnvelopeSuccess(Object env) {
        if (!(env instanceof Map<?, ?> m)) {
            return false;
        }
        return Boolean.TRUE.equals(m.get("success"));
    }

    /**
     * JSON 数组字符串或 {@link List}；无法解析时返回 null。
     */
    private static List<?> coerceList(Object raw) {
        if (raw instanceof List<?> list) {
            return list;
        }
        if (raw instanceof String s && !s.isBlank()) {
            try {
                Object parsed = JSON.parse(s);
                if (parsed instanceof List<?> list) {
                    return list;
                }
            } catch (Exception ignore) {
                return null;
            }
        }
        return null;
    }

    private static Map<Long, Double> loadRevenueRankingByStore(Object revenueEnv) {
        Object dataObj = toolEnvelopeData(revenueEnv);
        if (!(dataObj instanceof Map<?, ?> dm)) {
            return Map.of();
        }
        Object raw = dm.get("storeRevenueRanking");
        List<?> list = coerceList(raw);
        if (list == null || list.isEmpty()) {
            return Map.of();
        }
        Map<Long, Double> out = new LinkedHashMap<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> row)) {
                continue;
            }
            Long id = normalizeStoreId(row.get("storeDepartmentId"));
            if (id == null) {
                continue;
            }
            Double amt = parseDoubleLoose(row.get("revenueAmount"));
            if (amt != null) {
                out.put(id, amt);
            }
        }
        return out;
    }

    private static void mergeRevenueStoreNames(Map<Long, String> names, Object revenueEnv) {
        Object dataObj = toolEnvelopeData(revenueEnv);
        if (!(dataObj instanceof Map<?, ?> dm)) {
            return;
        }
        Object raw = dm.get("storeRevenueRanking");
        List<?> list = coerceList(raw);
        if (list == null) {
            return;
        }
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> row)) {
                continue;
            }
            Long id = normalizeStoreId(row.get("storeDepartmentId"));
            if (id == null) {
                continue;
            }
            Object sn = row.get("storeName");
            if (sn != null && !sn.toString().isBlank()) {
                names.putIfAbsent(id, sn.toString().trim());
            }
        }
    }

    private record PurchaseOverviewExtract(
            Map<Long, Double> amountByStoreDepartmentId,
            Map<Long, String> nameByStoreDepartmentId,
            List<Long> visibleStoreDepartmentIds,
            /** covered ∪ dataMissing ∪ visible 中出现的门店 */
            Set<Long> storesInPurchasePayload) {
        static PurchaseOverviewExtract empty() {
            return new PurchaseOverviewExtract(
                    Map.of(),
                    Map.of(),
                    List.of(),
                    Collections.emptySet());
        }
    }

    private static PurchaseOverviewExtract loadPurchaseStoreAmounts(Object purchaseEnv) {
        Object dataObj = toolEnvelopeData(purchaseEnv);
        if (!(dataObj instanceof Map<?, ?> dm)) {
            return PurchaseOverviewExtract.empty();
        }
        Object poObj = dm.get("purchaseOverview");
        if (!(poObj instanceof Map<?, ?> po)) {
            return PurchaseOverviewExtract.empty();
        }
        Map<Long, Double> amounts = new LinkedHashMap<>();
        Map<Long, String> names = new LinkedHashMap<>();
        HashSet<Long> inPayload = new HashSet<>();

        appendStorePurchaseRows(amounts, names, inPayload, po.get("coveredStores"));
        appendStorePurchaseRows(amounts, names, inPayload, po.get("dataMissingStores"));
        List<Long> visibleIds = new ArrayList<>();
        appendVisibleStoreIds(visibleIds, names, po.get("visibleStores"));
        for (Long v : visibleIds) {
            inPayload.add(v);
        }
        return new PurchaseOverviewExtract(
                amounts,
                names,
                visibleIds,
                Collections.unmodifiableSet(inPayload));
    }

    private static void appendVisibleStoreIds(List<Long> out, Map<Long, String> names, Object raw) {
        if (!(raw instanceof List<?> list)) {
            return;
        }
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> row)) {
                continue;
            }
            Long id = normalizeStoreId(row.get("storeDepartmentId"));
            if (id != null && !out.contains(id)) {
                out.add(id);
            }
            Object nm = row.get("storeName");
            if (id != null && nm != null && !nm.toString().isBlank()) {
                names.putIfAbsent(id, nm.toString().trim());
            }
        }
    }

    private static void appendStorePurchaseRows(
            Map<Long, Double> amounts,
            Map<Long, String> names,
            Set<Long> inPayload,
            Object rawRows) {
        if (!(rawRows instanceof List<?> list)) {
            return;
        }
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> row)) {
                continue;
            }
            Long id = normalizeStoreId(row.get("storeDepartmentId"));
            if (id == null) {
                continue;
            }
            inPayload.add(id);
            Object nm = row.get("storeName");
            if (nm != null && !nm.toString().isBlank()) {
                names.put(id, nm.toString().trim());
            }
            Double sub = parseDoubleLoose(row.get("purchaseSubtotal"));
            if (sub != null) {
                amounts.put(id, sub);
            } else {
                amounts.putIfAbsent(id, null);
            }
        }
    }

    private static Map<Long, Double> loadStockStoreGrandTotals(Object stockEnv) {
        Object dataObj = toolEnvelopeData(stockEnv);
        dataObj = unwrapStockReduceInnerMap(dataObj);
        if (!(dataObj instanceof Map<?, ?> dm)) {
            return Map.of();
        }
        Object raw = dm.get("topStoresOutboundByGrandTotal");
        List<?> list = coerceList(raw);
        if (list == null || list.isEmpty()) {
            return Map.of();
        }
        Map<Long, Double> out = new LinkedHashMap<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> row)) {
                continue;
            }
            Long id = normalizeStoreId(row.get("storeDepartmentId"));
            if (id == null) {
                continue;
            }
            Double amt = parseDoubleLoose(row.get("amount"));
            if (amt == null) {
                amt = parseDoubleLoose(row.get("grandTotalFourTypes"));
            }
            if (amt != null) {
                out.put(id, amt);
            }
        }
        return out;
    }

    /**
     * 与 {@link StockReduceAnswerPlanBuilder} 一致：信封 data 内偶见再包一层 data。
     */
    private static Object unwrapStockReduceInnerMap(Object dataObj) {
        if (!(dataObj instanceof Map<?, ?> m)) {
            return dataObj;
        }
        Object nested = m.get("data");
        if (nested instanceof Map<?, ?> && m.containsKey("schemaVersion")) {
            return nested;
        }
        return dataObj;
    }

    private static Object toolEnvelopeData(Object env) {
        if (!(env instanceof Map<?, ?> m)) {
            return null;
        }
        return unwrapDataMaybeJsonString(m.get("data"));
    }

    private static Object unwrapDataMaybeJsonString(Object data) {
        if (data instanceof Map<?, ?> mp) {
            return mp;
        }
        if (data instanceof String s && !s.isBlank()) {
            try {
                Object parsed = JSON.parseObject(s);
                if (parsed instanceof Map<?, ?> pm) {
                    return pm;
                }
            } catch (Exception ignore) {
                return null;
            }
        }
        return null;
    }

    private static Long normalizeStoreId(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(v.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static Double parseDoubleLoose(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v instanceof BigDecimal bd) {
            return bd.doubleValue();
        }
        String s = v.toString().trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(s);
        } catch (Exception e) {
            return null;
        }
    }
}
