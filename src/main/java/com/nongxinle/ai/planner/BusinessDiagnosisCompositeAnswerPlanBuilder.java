package com.nongxinle.ai.planner;

import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.AiDishProfitOverviewResult;
import com.nongxinle.ai.dto.business.BusinessDiagnosisCompositeAnswerPlan;
import com.nongxinle.ai.dto.business.BusinessDiagnosisCompositeAnswerPlanDebug;
import com.nongxinle.ai.dto.business.BusinessDiagnosisCompositeDishProfitSummary;
import com.nongxinle.ai.dto.business.BusinessDiagnosisCompositePurchaseSummary;
import com.nongxinle.ai.dto.business.BusinessDiagnosisCompositeRevenueSummary;
import com.nongxinle.ai.dto.business.BusinessDiagnosisCompositeRiskLevel;
import com.nongxinle.ai.dto.business.BusinessDiagnosisCompositeStockReduceSummary;
import com.nongxinle.ai.dto.business.BusinessDiagnosisDataDomain;
import com.nongxinle.ai.dto.business.BusinessDiagnosisDomainCoverage;
import com.nongxinle.ai.dto.business.BusinessDiagnosisEvidenceRef;
import com.nongxinle.ai.dto.business.BusinessDiagnosisSignal;
import com.nongxinle.ai.dto.business.BusinessDiagnosisSignals;
import com.nongxinle.ai.dto.business.BusinessDiagnosisSignalSeverity;
import com.nongxinle.ai.dto.business.DailyRevenueAnswerPlan;
import com.nongxinle.ai.dto.business.DishProfitAnswerPlan;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.alibaba.fastjson2.JSON;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * C-37 / C-38：从 {@link PlannerExecutorTrace} 等价物（已完成步 {@code priorStepResults}）+ 计划级执行上下文挂载的
 * AnswerPlan（及其次优 {@link AiRunState#getToolResults()} 快照）物化 {@link BusinessDiagnosisCompositeAnswerPlan}。
 *
 * <p><b>旁路边界</b>：本 Builder 仅服务于 BusinessDiagnosisComposite
 * {@link BusinessDiagnosisCompositeExecutionMode#SHADOW} /
 * {@link BusinessDiagnosisCompositeExecutionMode#HARNESS_ONLY} 旁路观测链；<strong>不属于</strong> Master Graph 主回答链；
 * 产出仅供 Composite 旁路对照与只读 Composer；<strong>不替换</strong>
 * {@link AiRunState#getFinalAnswerText()}；<strong>不负责</strong>生产用户正文。
 * {@link BusinessDiagnosisCompositeExecutionMode#PRIMARY} 为预留/未接生产主链。</p>
 *
 * <p>C-38.2：出库 / 菜品标量 <strong>不</strong>用 AnswerPlan 的 nz 默认 0 冒充 Tool 真值；以 Tool payload 的 key 存在性为准，
 * {@code debug.mappingNotes} 区分 <strong>real zero</strong> 与 <strong>missing</strong>。
 * C-39：在四域 summary + {@code dataCoverage} 上生成<strong>最小确定性</strong> {@code diagnosisSignals}（保守规则）；<strong>不</strong>调 LLM。
 * C-40：{@link BusinessDiagnosisCompositeAnswerPlan#getSummaryText()} 为确定性中文短摘要（仅拼接既有字段；**非** LLM 终稿；不说「经营正常」）。
 * C-42：出库步 DEGRADED 时 {@code summaryText} 明示「出库/核销未完整读取」；{@code stockReduceSummary=null}；{@code riskLevel=INSUFFICIENT_DATA}。
 * C-48：{@code orgScope.scopeType=GROUP}（以营收 executionContext 为准）时，营收/采购/菜品摘要句保守表述；{@code
 * DishProfitAnswerPlan}{@code #TYPE_AGGREGATED_DISH_PORTFOLIO_FALLBACK} 时不强调单店代表性。
 * C-49：文档与 {@link #BUILDER_VERSION} 标记收口（curl 已验收 GROUP Composite）；{@code debug.mappingNotes}
 * 仍保留 {@code phase=C-38.2_zero_vs_missing}、{@code signalsPhase=C-39_minimal_deterministic}、{@code
 * summaryPhase=C-40_deterministic_zh}；语义同 C-38.2 / C-39 / C-40 / C-42 / C-48。
 * <strong>不</strong>读原始 DB、<strong>不</strong>调 LLM。</p>
 */
public final class BusinessDiagnosisCompositeAnswerPlanBuilder {

    public static final String BUILDER_VERSION = "C-49";

    private static final String DIAGNOSIS_COMPOSE_STEP_ID = "step_diagnosis_compose";

    private BusinessDiagnosisCompositeAnswerPlanBuilder() {
    }

    public static BusinessDiagnosisCompositeAnswerPlan build(PlannerStepExecutionRequest request) {
        Objects.requireNonNull(request, "request");
        PlannerExecutionPlan plan = request.getPlanSnapshot();
        List<PlannerStepResult> prior = request.getPriorStepResults();
        if (prior == null) {
            prior = List.of();
        }

        String scopeLabel = resolveScopeLabel(request);
        String timeLabel = resolveTimeLabel(request);
        boolean groupWideScope = isGroupScopeFromRequest(request);
        String dishProfitAnswerPlanType = resolveDishProfitAnswerPlanType(request);

        PlannerStepResult revStep =
                findPrior(prior, CompositeBusinessDiagnosisStepIds.COMPOSITE_STEP_ID_REVENUE_HYDRATED);
        PlannerStepResult purStep =
                findPrior(prior, CompositeBusinessDiagnosisStepIds.COMPOSITE_STEP_ID_PURCHASE_HYDRATED);
        PlannerStepResult stStep =
                findPrior(prior, CompositeBusinessDiagnosisStepIds.COMPOSITE_STEP_ID_STOCK_REDUCE_HYDRATED);
        PlannerStepResult dishStep =
                findPrior(prior, CompositeBusinessDiagnosisAllDataRealHybridPlannerStepExecutor.COMPOSITE_STEP_ID_DISH_PROFIT_HYDRATED);

        List<BusinessDiagnosisDomainCoverage> coverage = new ArrayList<>();
        coverage.add(domainRow(BusinessDiagnosisDataDomain.REVENUE, revStep, RevenuePlannerAgentAdapter.TARGET_TOOL));
        coverage.add(domainRow(BusinessDiagnosisDataDomain.PURCHASE, purStep, PurchasePlannerAgentAdapter.TARGET_TOOL));
        coverage.add(domainRow(BusinessDiagnosisDataDomain.STOCK_REDUCE, stStep, StockReducePlannerAgentAdapter.TARGET_TOOL));
        coverage.add(domainRow(BusinessDiagnosisDataDomain.DISH_PROFIT, dishStep, DishProfitPlannerAgentAdapter.TARGET_TOOL));

        boolean allStepsSuccess = coverage.stream().allMatch(BusinessDiagnosisDomainCoverage::isSuccess);
        boolean hydrationComplete =
                coverage.stream().allMatch(
                        c -> c.isSuccess() && c.isRealToolInvoked());

        List<String> degraded =
                request.getDegradedStepsSoFar() == null
                        ? new ArrayList<>()
                        : new ArrayList<>(request.getDegradedStepsSoFar());

        List<String> revenueNotes = new ArrayList<>();
        List<String> purchaseNotes = new ArrayList<>();
        List<String> stockNotes = new ArrayList<>();
        List<String> dishNotes = new ArrayList<>();

        BusinessDiagnosisCompositeRevenueSummary revenueSummary = buildRevenueSummary(request, revStep, revenueNotes);
        BusinessDiagnosisCompositePurchaseSummary purchaseSummary = buildPurchaseSummary(request, purStep, purchaseNotes);
        BusinessDiagnosisCompositeStockReduceSummary stockSummary = buildStockSummary(request, stStep, stockNotes);
        BusinessDiagnosisCompositeDishProfitSummary dishSummary = buildDishSummary(request, dishStep, dishNotes);

        BusinessDiagnosisSignal dataIncomplete = buildDataIncompleteSignal(coverage, hydrationComplete);
        BusinessDiagnosisSignal purchaseHigh = buildPurchaseHighSignal(revenueSummary, purchaseSummary);
        BusinessDiagnosisSignal stockHigh = buildStockReduceHighSignal(revenueSummary, stockSummary);
        BusinessDiagnosisSignal dishLow = buildDishProfitLowSignal(dishSummary);
        BusinessDiagnosisSignals signals =
                BusinessDiagnosisSignals.builder()
                        .revenueWeakSignal(null)
                        .purchaseHighSignal(purchaseHigh)
                        .stockReduceHighSignal(stockHigh)
                        .dishProfitLowSignal(dishLow)
                        .dataIncompleteSignal(dataIncomplete)
                        .build();

        BusinessDiagnosisCompositeRiskLevel risk =
                resolveCompositeRiskLevel(allStepsSuccess, signals);

        List<String> findings =
                buildKeyFindings(
                        hydrationComplete,
                        allStepsSuccess,
                        dataIncomplete,
                        purchaseHigh,
                        stockHigh,
                        dishLow);

        List<String> nextQs = buildBaseSuggestedNextQuestions(allStepsSuccess, hydrationComplete, purchaseHigh, stockHigh);
        nextQs = mergeDeterministicSuggestedNextQuestions(nextQs, scopeLabel);

        String summaryText =
                buildSummaryText(
                        scopeLabel,
                        timeLabel,
                        coverage,
                        hydrationComplete,
                        allStepsSuccess,
                        revenueSummary,
                        purchaseSummary,
                        stockSummary,
                        dishSummary,
                        signals,
                        risk,
                        groupWideScope,
                        dishProfitAnswerPlanType);

        String finalType =
                plan != null && plan.getFinalAnswerPlanType() != null
                        ? plan.getFinalAnswerPlanType()
                        : BusinessDiagnosisCompositeAnswerPlan.TYPE_BUSINESS_DIAGNOSIS_COMPOSITE;

        LinkedHashMap<String, Object> noteMap = new LinkedHashMap<>();
        noteMap.put("phase", "C-38.2_zero_vs_missing");
        noteMap.put("signalsPhase", "C-39_minimal_deterministic");
        noteMap.put("summaryPhase", "C-40_deterministic_zh");
        if (coverage.stream()
                .anyMatch(c ->
                        c.getDomain() == BusinessDiagnosisDataDomain.STOCK_REDUCE && !c.isSuccess())) {
            noteMap.put("harnessStockReduceDegraded", Boolean.TRUE);
            noteMap.put("degradeClausePhase", "C-42_stock_reduce_step_not_success");
        }
        putJoined(noteMap, "revenue", revenueNotes);
        putJoined(noteMap, "purchase", purchaseNotes);
        putJoined(noteMap, "stockReduce", stockNotes);
        putJoined(noteMap, "dishProfit", dishNotes);

        BusinessDiagnosisCompositeAnswerPlanDebug dbg =
                BusinessDiagnosisCompositeAnswerPlanDebug.builder()
                        .builderVersion(BUILDER_VERSION)
                        .mappingNotes(noteMap)
                        .build();

        return BusinessDiagnosisCompositeAnswerPlan.builder()
                .type(finalType)
                .scopeLabel(scopeLabel)
                .timeLabel(timeLabel)
                .revenueSummary(revenueSummary)
                .purchaseSummary(purchaseSummary)
                .stockReduceSummary(stockSummary)
                .dishProfitSummary(dishSummary)
                .diagnosisSignals(signals)
                .riskLevel(risk)
                .summaryText(summaryText)
                .keyFindings(findings)
                .suggestedNextQuestions(nextQs)
                .dataCoverage(coverage)
                .degradedSteps(degraded)
                .debug(dbg)
                .build();
    }

    private static PlannerStepResult findPrior(List<PlannerStepResult> prior, String stepId) {
        for (PlannerStepResult r : prior) {
            if (r.getStepId() != null && stepId.equals(r.getStepId().trim())) {
                return r;
            }
        }
        return null;
    }

    private static BusinessDiagnosisDomainCoverage domainRow(
            BusinessDiagnosisDataDomain domain, PlannerStepResult r, String productionToolId) {
        boolean success = r != null && r.getStatus() == PlannerStepStatus.SUCCESS;
        boolean real = r != null && containsTool(r.getUsedTools(), productionToolId);
        String used = singleToolOrNull(r);
        String deg = r != null ? r.getDegradedReason() : null;
        String stepId =
                domain == BusinessDiagnosisDataDomain.REVENUE
                        ? CompositeBusinessDiagnosisStepIds.COMPOSITE_STEP_ID_REVENUE_HYDRATED
                        : domain == BusinessDiagnosisDataDomain.PURCHASE
                                ? CompositeBusinessDiagnosisStepIds.COMPOSITE_STEP_ID_PURCHASE_HYDRATED
                                : domain == BusinessDiagnosisDataDomain.STOCK_REDUCE
                                        ? CompositeBusinessDiagnosisStepIds.COMPOSITE_STEP_ID_STOCK_REDUCE_HYDRATED
                                        : CompositeBusinessDiagnosisAllDataRealHybridPlannerStepExecutor
                                                .COMPOSITE_STEP_ID_DISH_PROFIT_HYDRATED;
        return BusinessDiagnosisDomainCoverage.builder()
                .domain(domain)
                .success(success)
                .realToolInvoked(real)
                .stepId(stepId)
                .usedTool(used)
                .degradedReason(deg)
                .build();
    }

    private static boolean containsTool(List<String> tools, String id) {
        if (tools == null || id == null) {
            return false;
        }
        for (String t : tools) {
            if (id.equals(t)) {
                return true;
            }
        }
        return false;
    }

    private static String singleToolOrNull(PlannerStepResult r) {
        if (r == null || r.getUsedTools() == null || r.getUsedTools().isEmpty()) {
            return null;
        }
        return r.getUsedTools().get(0);
    }

    private static BusinessDiagnosisEvidenceRef ev(String domain, String refKind, String key) {
        return BusinessDiagnosisEvidenceRef.builder().domain(domain).refKind(refKind).key(key).build();
    }

    /**
     * C-39：四域 {@code success} 且 {@code realToolInvoked} 均为 true 时为空；否则按失败类型标 NOTICE / WARNING。
     */
    private static BusinessDiagnosisSignal buildDataIncompleteSignal(
            List<BusinessDiagnosisDomainCoverage> coverage, boolean hydrationComplete) {
        if (hydrationComplete) {
            return null;
        }
        List<BusinessDiagnosisEvidenceRef> refs = new ArrayList<>();
        boolean anyStepFailed = false;
        for (BusinessDiagnosisDomainCoverage row : coverage) {
            if (!row.isSuccess()) {
                anyStepFailed = true;
                refs.add(ev(row.getDomain().name(), "DATA_COVERAGE", "success=false"));
                if (row.getStepId() != null && !row.getStepId().isBlank()) {
                    refs.add(ev(row.getDomain().name(), "PLANNER_STEP", row.getStepId().trim()));
                }
            } else if (!row.isRealToolInvoked()) {
                refs.add(ev(row.getDomain().name(), "DATA_COVERAGE", "realToolInvoked=false"));
            }
        }
        if (refs.isEmpty()) {
            return null;
        }
        String reason =
                anyStepFailed
                        ? "data_coverage_not_all_success_or_tool_not_invoked"
                        : "data_coverage_success_but_real_tool_not_invoked_for_some_domain";
        return BusinessDiagnosisSignal.builder()
                .sourceStep(DIAGNOSIS_COMPOSE_STEP_ID)
                .severity(anyStepFailed ? BusinessDiagnosisSignalSeverity.WARNING : BusinessDiagnosisSignalSeverity.NOTICE)
                .reason(reason)
                .evidenceRefs(refs)
                .build();
    }

    private static BusinessDiagnosisSignal buildPurchaseHighSignal(
            BusinessDiagnosisCompositeRevenueSummary revenue, BusinessDiagnosisCompositePurchaseSummary purchase) {
        if (revenue == null || purchase == null) {
            return null;
        }
        Double tr = revenue.getTotalRevenue();
        Double pa = purchase.getPurchaseAmount();
        if (tr == null || pa == null) {
            return null;
        }
        if (pa.compareTo(tr) <= 0) {
            return null;
        }
        return BusinessDiagnosisSignal.builder()
                .sourceStep(DIAGNOSIS_COMPOSE_STEP_ID)
                .severity(BusinessDiagnosisSignalSeverity.WARNING)
                .reason(
                        "purchase_amount_exceeds_total_revenue_anomaly_signal_only_not_a_conclusion;"
                                + " compare purchaseAmount vs totalRevenue")
                .evidenceRefs(
                        List.of(
                                ev("PURCHASE", "SUMMARY_FIELD", "purchaseAmount"),
                                ev("REVENUE", "SUMMARY_FIELD", "totalRevenue")))
                .build();
    }

    private static BusinessDiagnosisSignal buildStockReduceHighSignal(
            BusinessDiagnosisCompositeRevenueSummary revenue, BusinessDiagnosisCompositeStockReduceSummary stock) {
        if (revenue == null || stock == null) {
            return null;
        }
        Double tr = revenue.getTotalRevenue();
        Double g = stock.getGrandTotalAmount();
        if (tr == null || g == null) {
            return null;
        }
        if (g.compareTo(tr) <= 0) {
            return null;
        }
        return BusinessDiagnosisSignal.builder()
                .sourceStep(DIAGNOSIS_COMPOSE_STEP_ID)
                .severity(BusinessDiagnosisSignalSeverity.WARNING)
                .reason(
                        "stock_reduce_grand_total_exceeds_total_revenue_anomaly_signal_only; "
                                + "compare grandTotalAmount vs totalRevenue")
                .evidenceRefs(
                        List.of(
                                ev("STOCK_REDUCE", "SUMMARY_FIELD", "grandTotalAmount"),
                                ev("REVENUE", "SUMMARY_FIELD", "totalRevenue")))
                .build();
    }

    /**
     * C-39：毛利率 &lt; 0 → WARNING；毛利率=0 仅在确认销售额 &gt; 0 时给 NOTICE；销售额与成本均为 0 不报的「毛利低」。
     */
    private static BusinessDiagnosisSignal buildDishProfitLowSignal(BusinessDiagnosisCompositeDishProfitSummary dish) {
        if (dish == null) {
            return null;
        }
        Double rate = dish.getGrossProfitRate();
        if (rate == null) {
            return null;
        }
        Double sales = dish.getSalesAmount();
        Double cost = dish.getCostAmount();
        boolean salesZero = sales != null && sales.compareTo(0.0) == 0;
        boolean costZero = cost != null && cost.compareTo(0.0) == 0;
        boolean bothZeroNoMislabel = salesZero && costZero;

        if (rate < 0.0) {
            return BusinessDiagnosisSignal.builder()
                    .sourceStep(DIAGNOSIS_COMPOSE_STEP_ID)
                    .severity(BusinessDiagnosisSignalSeverity.WARNING)
                    .reason("gross_profit_rate_negative")
                    .evidenceRefs(
                            List.of(
                                    ev("DISH_PROFIT", "SUMMARY_FIELD", "grossProfitRate"),
                                    ev("DISH_PROFIT", "SUMMARY_FIELD", "salesAmount"),
                                    ev("DISH_PROFIT", "SUMMARY_FIELD", "costAmount")))
                    .build();
        }
        if (bothZeroNoMislabel && rate.compareTo(0.0) == 0) {
            return null;
        }
        if (sales != null && sales.compareTo(0.0) > 0 && rate.compareTo(0.0) == 0) {
            return BusinessDiagnosisSignal.builder()
                    .sourceStep(DIAGNOSIS_COMPOSE_STEP_ID)
                    .severity(BusinessDiagnosisSignalSeverity.NOTICE)
                    .reason("gross_profit_rate_zero_with_positive_sales_not_low_margin_worded")
                    .evidenceRefs(
                            List.of(
                                    ev("DISH_PROFIT", "SUMMARY_FIELD", "grossProfitRate"),
                                    ev("DISH_PROFIT", "SUMMARY_FIELD", "salesAmount")))
                    .build();
        }
        return null;
    }

    private static BusinessDiagnosisCompositeRiskLevel resolveCompositeRiskLevel(
            boolean allStepsSuccess, BusinessDiagnosisSignals signals) {
        if (!allStepsSuccess) {
            return BusinessDiagnosisCompositeRiskLevel.INSUFFICIENT_DATA;
        }
        if (hasAnySeverity(signals, BusinessDiagnosisSignalSeverity.WARNING)) {
            return BusinessDiagnosisCompositeRiskLevel.MEDIUM;
        }
        return BusinessDiagnosisCompositeRiskLevel.NORMAL_OBSERVATION;
    }

    private static boolean hasAnySeverity(BusinessDiagnosisSignals s, BusinessDiagnosisSignalSeverity sev) {
        if (s == null || sev == null) {
            return false;
        }
        return matches(s.getDataIncompleteSignal(), sev)
                || matches(s.getPurchaseHighSignal(), sev)
                || matches(s.getStockReduceHighSignal(), sev)
                || matches(s.getDishProfitLowSignal(), sev)
                || matches(s.getRevenueWeakSignal(), sev);
    }

    private static boolean matches(BusinessDiagnosisSignal sig, BusinessDiagnosisSignalSeverity sev) {
        return sig != null && sig.getSeverity() == sev;
    }

    private static List<String> buildKeyFindings(
            boolean hydrationComplete,
            boolean allStepsSuccess,
            BusinessDiagnosisSignal dataIncomplete,
            BusinessDiagnosisSignal purchaseHigh,
            BusinessDiagnosisSignal stockHigh,
            BusinessDiagnosisSignal dishLow) {
        List<String> findings = new ArrayList<>();
        if (hydrationComplete) {
            findings.add("四域数据覆盖 success=true 且已检测到生产 Tool 执行（realToolInvoked=true）。");
        } else if (!allStepsSuccess) {
            findings.add("部分数据域步骤未成功；请关注 dataCoverage 与 dataIncompleteSignal，未编造缺失域数值。");
        } else {
            findings.add("四域步骤均已成功，但部分域未检测到生产 Tool 执行（见 dataIncompleteSignal）。");
        }
        if (dataIncomplete != null && dataIncomplete.getReason() != null) {
            findings.add("数据覆盖提示：" + dataIncomplete.getReason());
        }
        if (purchaseHigh != null) {
            findings.add("采购额高于同期营收（仅作跨域异常提示，不作经营结论）。");
        }
        if (stockHigh != null) {
            findings.add("出库核销合计高于同期营收（仅作跨域异常提示，不作经营结论）。");
        }
        if (dishLow != null) {
            if (dishLow.getSeverity() == BusinessDiagnosisSignalSeverity.WARNING) {
                findings.add("菜品毛利率为负（基于已映射的 grossProfitRate）。");
            } else {
                findings.add("菜品毛利率为 0 且销售额大于 0（不作「毛利偏低」表述）。");
            }
        }
        return findings;
    }

    private static List<String> buildBaseSuggestedNextQuestions(
            boolean allStepsSuccess,
            boolean hydrationComplete,
            BusinessDiagnosisSignal purchaseHigh,
            BusinessDiagnosisSignal stockHigh) {
        List<String> nextQs = new ArrayList<>();
        if (!allStepsSuccess) {
            nextQs.add("可检查失败域对应权限、时间窗或数据源后重试。");
        }
        if (!hydrationComplete && allStepsSuccess) {
            nextQs.add("部分域未检测到生产 Tool 执行痕迹，请核对 trace.usedTools 与 Harness 诚实字段。");
        }
        if (purchaseHigh != null || stockHigh != null) {
            nextQs.add("跨域金额对比仅作异常提示，需结合口径（含税/期间/范围）再判断。");
        }
        return nextQs;
    }

    /**
     * C-40：与 scope 无关的通用下钻问法 + 条件问法；不引用用户原文、不编造店名。
     */
    private static List<String> mergeDeterministicSuggestedNextQuestions(List<String> base, String scopeLabel) {
        LinkedHashMap<String, Boolean> ordered = new LinkedHashMap<>();
        for (String s : base) {
            if (s != null && !s.isBlank()) {
                ordered.putIfAbsent(s.trim(), Boolean.TRUE);
            }
        }
        addUniqueQuestion(
                ordered,
                "要不要查看菜品毛利明细？");
        addUniqueQuestion(
                ordered,
                "要不要查看采购商品金额排行？");
        String scope = scopeLabel == null || scopeLabel.isBlank() ? "本门店" : scopeLabel.trim();
        addUniqueQuestion(ordered, "要不要对比「" + scope + "」与其他门店？");
        return new ArrayList<>(ordered.keySet());
    }

    private static void addUniqueQuestion(LinkedHashMap<String, Boolean> ordered, String q) {
        if (q != null && !q.isBlank()) {
            ordered.putIfAbsent(q.trim(), Boolean.TRUE);
        }
    }

    /**
     * 仅描述 {@link BusinessDiagnosisCompositeAnswerPlan} 已填充事实；0 与 null 分离表述；不宣称「经营无问题」；
     * C-42：出库域未成功时明示未完整读取（不说「四类均已读取」、不说无来源的出库 0）。
     */
    private static String buildSummaryText(
            String scopeLabel,
            String timeLabel,
            List<BusinessDiagnosisDomainCoverage> coverageRows,
            boolean hydrationComplete,
            boolean allStepsSuccess,
            BusinessDiagnosisCompositeRevenueSummary revenueSummary,
            BusinessDiagnosisCompositePurchaseSummary purchaseSummary,
            BusinessDiagnosisCompositeStockReduceSummary stockSummary,
            BusinessDiagnosisCompositeDishProfitSummary dishSummary,
            BusinessDiagnosisSignals signals,
            BusinessDiagnosisCompositeRiskLevel risk,
            boolean groupWideScope,
            String dishProfitAnswerPlanType) {
        String scope = scopeLabel == null || scopeLabel.isBlank() ? "本单" : scopeLabel.trim();
        String time = timeLabel == null || timeLabel.isBlank() ? "未标明时间窗" : timeLabel.trim();
        StringBuilder sb = new StringBuilder();
        sb.append(scope).append(" 在 ").append(time).append("，");
        if (hydrationComplete && allStepsSuccess) {
            if (groupWideScope) {
                sb.append("在当前可见多门店（GROUP）下，已完成营收、采购、出库核销、菜品毛利四类数据读取。");
            } else {
                sb.append("已完成营收、采购、出库核销、菜品毛利四类数据读取。");
            }
        } else if (!allStepsSuccess) {
            sb.append("部分数据域未成功读取，摘要仅反映已可用字段。");
            appendExplicitStockReduceDegradedClause(sb, coverageRows);
        } else {
            sb.append("四域步骤已成功，但数据覆盖或未检测到全部生产 Tool，摘要仅反映已可用字段。");
        }

        sb.append(revenueFact(revenueSummary, groupWideScope));
        sb.append(purchaseFact(purchaseSummary, groupWideScope));
        sb.append(stockFact(stockSummary));
        sb.append(dishFact(dishSummary, dishProfitAnswerPlanType));

        if (risk != null && risk != BusinessDiagnosisCompositeRiskLevel.NORMAL_OBSERVATION) {
            sb.append("当前 riskLevel=").append(risk.name()).append("。");
        }

        if (hasAnySeverity(signals, BusinessDiagnosisSignalSeverity.WARNING)) {
            sb.append("已触发确定性 WARNING 级信号，详见 diagnosisSignals。");
        } else if (hasNoticeSignalsOnly(signals)) {
            sb.append("存在 NOTICE 级提示，详见 diagnosisSignals。");
        } else {
            sb.append("当前未触发确定性异常信号（不构成「经营正常」结论）。");
        }
        return sb.toString();
    }

    /** C-42：出库域步骤未成功时，摘要须明示未完整读取，避免与「四类均已读取」或无来源的 0 混淆。 */
    private static void appendExplicitStockReduceDegradedClause(
            StringBuilder sb, List<BusinessDiagnosisDomainCoverage> coverageRows) {
        if (coverageRows == null) {
            return;
        }
        for (BusinessDiagnosisDomainCoverage row : coverageRows) {
            if (row.getDomain() == BusinessDiagnosisDataDomain.STOCK_REDUCE && !row.isSuccess()) {
                sb.append(
                        " 出库/核销（stock_reduce）数据未完整读取：该域步骤已降级或未成功完成生产 Tool，未给出出库核销金额。");
                return;
            }
        }
    }

    private static boolean hasNoticeSignalsOnly(BusinessDiagnosisSignals s) {
        if (s == null || hasAnySeverity(s, BusinessDiagnosisSignalSeverity.WARNING)) {
            return false;
        }
        return matches(s.getDataIncompleteSignal(), BusinessDiagnosisSignalSeverity.NOTICE)
                || matches(s.getDishProfitLowSignal(), BusinessDiagnosisSignalSeverity.NOTICE);
    }

    private static String revenueFact(BusinessDiagnosisCompositeRevenueSummary r, boolean groupWideScope) {
        if (r == null) {
            return " 营收摘要未形成。";
        }
        if (groupWideScope) {
            if (r.getTotalRevenue() != null) {
                return " 营收数据已读取，门店维度见 focusRows；合计 "
                        + formatYuan(r.getTotalRevenue())
                        + " 元（是否覆盖全部可见门店以数据源为准）。";
            }
            return " 营收数据已读取；合计口径与门店明细见 focusRows / 数据源。";
        }
        if (r.getTotalRevenue() != null) {
            return " 营收 " + formatYuan(r.getTotalRevenue()) + " 元。";
        }
        return " 营收合计未映射。";
    }

    private static String purchaseFact(BusinessDiagnosisCompositePurchaseSummary p, boolean groupWideScope) {
        if (p == null) {
            return " 采购摘要未形成。";
        }
        if (p.getPurchaseAmount() != null) {
            if (groupWideScope && (p.getFocusRows() == null || p.getFocusRows().size() <= 1)) {
                return " 采购金额 "
                        + formatYuan(p.getPurchaseAmount())
                        + " 元；门店明细较少，不作门店排行推断。";
            }
            return " 采购金额 " + formatYuan(p.getPurchaseAmount()) + " 元。";
        }
        return " 采购金额未映射。";
    }

    private static String stockFact(BusinessDiagnosisCompositeStockReduceSummary st) {
        if (st == null) {
            return " 出库核销摘要未形成。";
        }
        if (st.getGrandTotalAmount() != null) {
            return " 出库核销金额为 " + formatYuan(st.getGrandTotalAmount()) + " 元。";
        }
        return " 出库核销合计未映射。";
    }

    private static String dishFact(BusinessDiagnosisCompositeDishProfitSummary d, String dishProfitAnswerPlanType) {
        if (d == null) {
            return " 菜品毛利摘要未形成。";
        }
        if (DishProfitAnswerPlan.TYPE_AGGREGATED_DISH_PORTFOLIO_FALLBACK.equals(dishProfitAnswerPlanType)) {
            return " 菜品毛利为聚合回退口径（AGGREGATED_DISH_PORTFOLIO_FALLBACK）；指标已读取，摘要不作单店或集团总数结论，详见 focusRows。";
        }
        Double sales = d.getSalesAmount();
        Double cost = d.getCostAmount();
        Double gAmt = d.getGrossProfitAmount();
        Double rate = d.getGrossProfitRate();
        if (sales != null && cost != null && gAmt != null && rate != null) {
            if (sales.compareTo(0.0) == 0
                    && cost.compareTo(0.0) == 0
                    && gAmt.compareTo(0.0) == 0
                    && rate.compareTo(0.0) == 0) {
                return " 菜品毛利展示为销售额、成本、毛利额与毛利率均为 0，需结合菜品销售数据口径继续确认。";
            }
            return " 菜品销售额 "
                    + formatYuan(sales)
                    + " 元，成本 "
                    + formatYuan(cost)
                    + " 元，毛利额 "
                    + formatYuan(gAmt)
                    + " 元，毛利率 "
                    + formatYuan(rate)
                    + "（百分比口径以数据源为准）。";
        }
        return " 菜品毛利部分指标未映射，需结合口径确认。";
    }

    private static String formatYuan(Double v) {
        if (v == null || v.isNaN() || v.isInfinite()) {
            return "?";
        }
        double x = v;
        if (x == Math.rint(x)) {
            return String.valueOf((long) x);
        }
        return String.valueOf(x);
    }

    private static String resolveScopeLabel(PlannerStepExecutionRequest request) {
        PlannerRevenueExecutionContext ctx = request.getRevenueExecutionContext();
        if (ctx != null && ctx.getResolvedQueryContext() != null) {
            AiResolvedOrgScope org = ctx.getResolvedQueryContext().getOrgScope();
            if (org != null) {
                if (org.getScopeName() != null && !org.getScopeName().isBlank()) {
                    return org.getScopeName().trim();
                }
                if (org.getCurrentStoreDepartmentId() != null) {
                    return "STORE dept=" + org.getCurrentStoreDepartmentId();
                }
            }
        }
        DailyRevenueAnswerPlan rp = revenuePlanFrom(request);
        if (rp != null && rp.getScopeLabel() != null) {
            return rp.getScopeLabel();
        }
        return "unknown_scope";
    }

    private static String resolveTimeLabel(PlannerStepExecutionRequest request) {
        PlannerRevenueExecutionContext ctx = request.getRevenueExecutionContext();
        if (ctx != null && ctx.getResolvedQueryContext() != null) {
            AiResolvedTimeWindow tw = ctx.getResolvedQueryContext().getTimeWindow();
            if (tw != null) {
                if (tw.getDisplayText() != null && !tw.getDisplayText().isBlank()) {
                    return tw.getDisplayText().trim();
                }
                if (tw.getTimeLabel() != null && !tw.getTimeLabel().isBlank()) {
                    return tw.getTimeLabel().trim();
                }
            }
        }
        DailyRevenueAnswerPlan rp = revenuePlanFrom(request);
        if (rp != null && rp.getTimeLabel() != null) {
            return rp.getTimeLabel();
        }
        return "unknown_time";
    }

    private static boolean isGroupScopeFromRequest(PlannerStepExecutionRequest request) {
        PlannerRevenueExecutionContext ctx = request.getRevenueExecutionContext();
        if (ctx == null || ctx.getResolvedQueryContext() == null || ctx.getResolvedQueryContext().getOrgScope() == null) {
            return false;
        }
        return AiResolvedOrgScope.SCOPE_GROUP.equals(ctx.getResolvedQueryContext().getOrgScope().getScopeType());
    }

    private static String resolveDishProfitAnswerPlanType(PlannerStepExecutionRequest request) {
        if (request.getDishProfitExecutionContext() == null
                || request.getDishProfitExecutionContext().getRunState() == null) {
            return null;
        }
        DishProfitAnswerPlan p = request.getDishProfitExecutionContext().getRunState().getDishProfitAnswerPlan();
        return p != null ? p.getPlanType() : null;
    }

    private static DailyRevenueAnswerPlan revenuePlanFrom(PlannerStepExecutionRequest request) {
        PlannerRevenueExecutionContext ctx = request.getRevenueExecutionContext();
        if (ctx == null || ctx.getRunState() == null) {
            return null;
        }
        return ctx.getRunState().getRevenueAnswerPlan();
    }

    private static BusinessDiagnosisCompositeRevenueSummary buildRevenueSummary(
            PlannerStepExecutionRequest request, PlannerStepResult step, List<String> notes) {
        if (step == null || step.getStatus() != PlannerStepStatus.SUCCESS) {
            return null;
        }
        AiRunState run = revenueRunState(request);
        DailyRevenueAnswerPlan p = revenuePlanFrom(request);

        Double total = null;
        List<Map<String, Object>> storeRows = new ArrayList<>();
        boolean fromPlan = false;
        boolean fromTool = false;

        if (p != null && p.getSummary() != null && !p.getSummary().isEmpty()) {
            total = toDouble(p.getSummary().get("totalRevenue"));
            storeRows = copyRowMaps(p.getFocusRows());
            fromPlan = true;
        }
        if ((total == null || storeRows.isEmpty()) && run != null) {
            Map<String, Object> inner = revenueInnerFromTool(run);
            if (!inner.isEmpty()) {
                if (total == null) {
                    total = toDouble(inner.get("totalRevenue"));
                }
                if (storeRows.isEmpty() && inner.get("storeRevenueRanking") instanceof List<?> ranking) {
                    storeRows = rankingMapsAsStoreRows(ranking);
                }
                fromTool = true;
            }
        }

        if (total == null) {
            notes.add("totalRevenue missing after sources="
                    + sourceList(fromPlan, fromTool, "DailyRevenueAnswerPlan", "toolResults[revenue_query].data"));
        }
        if (storeRows.isEmpty()) {
            notes.add("storeRows empty (no focusRows or storeRevenueRanking list)");
        }
        if (!fromPlan && fromTool) {
            notes.add("revenue AnswerPlan.summary absent; used toolResults fallback");
        }

        return BusinessDiagnosisCompositeRevenueSummary.builder()
                .totalRevenue(total)
                .storeRows(storeRows)
                .build();
    }

    private static BusinessDiagnosisCompositePurchaseSummary buildPurchaseSummary(
            PlannerStepExecutionRequest request, PlannerStepResult step, List<String> notes) {
        if (step == null || step.getStatus() != PlannerStepStatus.SUCCESS) {
            return null;
        }
        PurchasePlannerExecutionContext ctx = request.getPurchaseExecutionContext();
        if (ctx == null || ctx.getRunState() == null) {
            notes.add("purchaseExecutionContext or runState null");
            return null;
        }
        AiRunState run = ctx.getRunState();
        PurchaseAnswerPlan p = run.getPurchaseAnswerPlan();
        Map<String, Object> overview = purchaseOverviewFromTool(run);

        Double purchaseAmount = null;
        Long purchaseCount = null;
        String purchaseSourceType = p != null ? p.getPurchaseSourceType() : null;
        List<Map<String, Object>> focusRows =
                p != null ? copyRowMaps(p.getFocusRows()) : new ArrayList<>();

        if (!overview.isEmpty()) {
            purchaseAmount = toDouble(overview.get("totalPurchaseAmount"));
            purchaseCount = toLong(overview.get("purchaseOrderCount"));
        }

        if (purchaseAmount == null && p != null && p.getSummary() != null) {
            Map<String, Object> s = p.getSummary();
            boolean hadMethodBreakdown =
                    overview.get("purchaseMethodBreakdown") instanceof List<?> br && !br.isEmpty();
            if (hadMethodBreakdown) {
                Double selfAmt = toDouble(s.get("selfPurchaseAmount"));
                Double supAmt = toDouble(s.get("supplierPurchaseAmount"));
                if (selfAmt != null || supAmt != null) {
                    purchaseAmount =
                            (selfAmt == null ? 0.0 : selfAmt.doubleValue())
                                    + (supAmt == null ? 0.0 : supAmt.doubleValue());
                }
            }
            if (purchaseAmount == null) {
                purchaseAmount = toDouble(firstFocusField(focusRows, "totalPurchaseAmount"));
            }
            if (purchaseAmount == null) {
                Double ta = toDouble(s.get("totalAmount"));
                if (ta != null && ta != 0.0) {
                    purchaseAmount = ta;
                }
            }
        }

        if (purchaseCount == null && !focusRows.isEmpty()) {
            purchaseCount = toLong(firstFocusField(focusRows, "purchaseOrderCount"));
        }
        if (purchaseCount == null && p != null && p.getSummary() != null) {
            Map<String, Object> s = p.getSummary();
            Long lines = sumLineCountsFromSummary(s);
            if (lines != null && lines > 0) {
                purchaseCount = lines;
            } else {
                Long tc = toLong(s.get("totalCount"));
                if (tc != null
                        && tc > 0
                        && (overview.containsKey("purchaseOrderCount")
                                || hasPositiveIndicatorForCount(overview))) {
                    purchaseCount = tc;
                } else if (tc != null && tc == 0 && overview.containsKey("purchaseOrderCount")) {
                    purchaseCount = toLong(overview.get("purchaseOrderCount"));
                    if (purchaseCount == null) {
                        notes.add("purchaseCount: summary totalCount=0 treated as placeholder; tool count absent");
                    }
                }
            }
        }

        if (purchaseAmount == null) {
            notes.add("purchaseAmount missing (tool totalPurchaseAmount, breakdown, focusRows, summary)");
        }
        if (purchaseCount == null) {
            notes.add("purchaseCount missing (tool purchaseOrderCount, line counts, reliable totalCount)");
        }
        if (overview.isEmpty()) {
            notes.add("purchase tool overview envelope empty; relied on PurchaseAnswerPlan only");
        }

        return BusinessDiagnosisCompositePurchaseSummary.builder()
                .purchaseAmount(purchaseAmount)
                .purchaseCount(purchaseCount)
                .purchaseSourceType(purchaseSourceType)
                .focusRows(focusRows)
                .build();
    }

    private static BusinessDiagnosisCompositeStockReduceSummary buildStockSummary(
            PlannerStepExecutionRequest request, PlannerStepResult step, List<String> notes) {
        if (step != null && step.getStatus() == PlannerStepStatus.DEGRADED) {
            notes.add(
                    "stock_reduce: planner step DEGRADED; stockReduceSummary=null (no stock_reduce_query usable result; "
                            + "no fabricated totals)");
            if (step.getDegradedReason() != null && !step.getDegradedReason().isBlank()) {
                notes.add("degradedReason=" + step.getDegradedReason().trim());
            }
            return null;
        }
        if (step == null || step.getStatus() != PlannerStepStatus.SUCCESS) {
            return null;
        }
        StockReducePlannerExecutionContext ctx = request.getStockReduceExecutionContext();
        if (ctx == null || ctx.getRunState() == null) {
            notes.add("stockReduceExecutionContext or runState null");
            return null;
        }
        AiRunState run = ctx.getRunState();
        Map<String, Object> inner = stockInnerFromTool(run, notes);
        if (inner.isEmpty()) {
            notes.add(
                    "stockReduce: tool inner empty; scalar totals=null "
                            + "(StockReduceAnswerPlan.summary not used—cannot distinguish nz-coerced zeros from"
                            + " missing Tool fields)");
            return emptyStockReduceSummary(null);
        }
        boolean anyScalarKey =
                inner.containsKey("produceTotal")
                        || inner.containsKey("wasteTotal")
                        || inner.containsKey("lossTotal")
                        || inner.containsKey("returnTotal")
                        || inner.containsKey("grandTotalFourTypes");
        if (!anyScalarKey) {
            notes.add(
                    "stockReduce: tool inner has no scalar total keys (e.g. ranking-only payload); "
                            + "grand/produce/waste/loss/return=null");
            String basisOnly = stockToolString(inner, "totalsBasis", notes, "totalsBasis", false);
            return emptyStockReduceSummary(basisOnly);
        }

        Double produce = stockToolDouble(inner, "produceTotal", notes, "produceTotal");
        Double waste = stockToolDouble(inner, "wasteTotal", notes, "wasteTotal");
        Double loss = stockToolDouble(inner, "lossTotal", notes, "lossTotal");
        Double ret = stockToolDouble(inner, "returnTotal", notes, "returnTotal");

        Double grand;
        if (inner.containsKey("grandTotalFourTypes")) {
            grand = stockToolDouble(inner, "grandTotalFourTypes", notes, "grandTotalAmount(grandTotalFourTypes)");
        } else if (inner.containsKey("produceTotal")
                && inner.containsKey("wasteTotal")
                && inner.containsKey("lossTotal")
                && inner.containsKey("returnTotal")) {
            if (produce != null && waste != null && loss != null && ret != null) {
                grand = produce + waste + loss + ret;
                notes.add(
                        "grandTotalAmount derived as sum of four type totals (all keys present in "
                                + "toolResults[stock_reduce_query])");
                if (grand == 0.0) {
                    notes.add("grandTotalAmount real zero (sum of four type totals from tool)");
                }
            } else {
                grand = null;
                notes.add(
                        "grandTotalAmount missing source (four type keys present but one or more values "
                                + "null/unparsable)");
            }
        } else {
            grand = null;
            notes.add(
                    "grandTotalAmount missing source (no grandTotalFourTypes key and not all four type keys"
                            + " present)");
        }

        String totalsBasis = stockToolString(inner, "totalsBasis", notes, "totalsBasis", true);

        return BusinessDiagnosisCompositeStockReduceSummary.builder()
                .grandTotalAmount(grand)
                .produceTotal(produce)
                .wasteTotal(waste)
                .lossTotal(loss)
                .returnTotal(ret)
                .totalsBasis(totalsBasis)
                .build();
    }

    private static BusinessDiagnosisCompositeStockReduceSummary emptyStockReduceSummary(String totalsBasis) {
        return BusinessDiagnosisCompositeStockReduceSummary.builder()
                .grandTotalAmount(null)
                .produceTotal(null)
                .wasteTotal(null)
                .lossTotal(null)
                .returnTotal(null)
                .totalsBasis(totalsBasis)
                .build();
    }

    private static Double stockToolDouble(Map<String, Object> inner, String key, List<String> notes, String logical) {
        if (!inner.containsKey(key)) {
            notes.add(logical + " missing key in toolResults[stock_reduce_query] inner");
            return null;
        }
        Object raw = inner.get(key);
        if (raw == null) {
            notes.add(logical + " null in tool payload (key present)");
            return null;
        }
        Double d = toDouble(raw);
        if (d == null) {
            notes.add(logical + " unparsable in tool payload");
            return null;
        }
        if (d == 0.0) {
            notes.add(logical + " real zero from toolResults[stock_reduce_query]");
        }
        return d;
    }

    /**
     * @param quietIfMissing if true, do not add a note when the key is absent (optional field)
     */
    private static String stockToolString(
            Map<String, Object> inner, String key, List<String> notes, String logical, boolean quietIfMissing) {
        if (!inner.containsKey(key)) {
            if (!quietIfMissing) {
                notes.add(logical + " missing key in toolResults[stock_reduce_query] inner");
            }
            return null;
        }
        Object raw = inner.get(key);
        if (raw == null) {
            notes.add(logical + " null in tool payload (key present)");
            return null;
        }
        String t = raw.toString().trim();
        if (t.isEmpty()) {
            notes.add(logical + " empty string in tool payload");
            return null;
        }
        return t;
    }

    private static BusinessDiagnosisCompositeDishProfitSummary buildDishSummary(
            PlannerStepExecutionRequest request, PlannerStepResult step, List<String> notes) {
        if (step == null || step.getStatus() != PlannerStepStatus.SUCCESS) {
            return null;
        }
        DishProfitPlannerExecutionContext ctx = request.getDishProfitExecutionContext();
        if (ctx == null || ctx.getRunState() == null) {
            notes.add("dishProfitExecutionContext or runState null");
            return null;
        }
        AiRunState run = ctx.getRunState();
        AiDishProfitOverviewResult ov = run.getDishProfitOverviewResult();
        DishProfitAnswerPlan plan = run.getDishProfitAnswerPlan();

        Double grossProfitAmount = null;
        Double grossProfitRate = null;
        Double salesAmount = null;
        Double costAmount = null;
        List<Map<String, Object>> focusRows = plan != null ? copyRowMaps(plan.getFocusRows()) : new ArrayList<>();

        if (ov != null) {
            salesAmount = parseOverviewMoney(ov.getTotalDishSalesAmount(), notes, "salesAmount");
            costAmount = parseOverviewMoney(ov.getTotalActualCost(), notes, "costAmount");
            grossProfitAmount = parseOverviewMoney(ov.getGrossProfitAmount(), notes, "grossProfitAmount");
            grossProfitRate = parseOverviewRate(ov.getGrossProfitRate(), notes, "grossProfitRate");
        } else {
            notes.add("AiDishProfitOverviewResult null; skipped overview string mapping");
        }

        Map<String, Object> inner = dishToolDataMap(run);
        Map<String, Object> bis = bisMap(inner.get("businessInsightSummary"));
        if (!bis.isEmpty()) {
            if (salesAmount == null) {
                salesAmount = bisNumeric(bis, "totalActualRevenue", notes, "salesAmount");
            }
            if (costAmount == null) {
                costAmount = bisNumeric(bis, "totalActualCostAmount", notes, "costAmount");
            }
            if (grossProfitRate == null) {
                grossProfitRate = bisRate(bis, "blendedGrossMarginRateOnListPrice", notes, "grossProfitRate");
            }
            notes.add("dishProfit: consulted toolResults[dish_profit_analysis].data.businessInsightSummary");
        } else if (!inner.isEmpty()) {
            notes.add("businessInsightSummary missing or empty in dish tool data");
        }

        if (grossProfitAmount == null && salesAmount != null && costAmount != null) {
            grossProfitAmount = salesAmount - costAmount;
            notes.add("grossProfitAmount derived as salesAmount-costAmount (both mapped non-null)");
            if (grossProfitAmount == 0.0) {
                notes.add("grossProfitAmount real zero (derived difference)");
            }
        }

        if (grossProfitAmount == null) {
            notes.add("grossProfitAmount missing (overview, businessInsightSummary, and derivation)");
        }
        if (grossProfitRate == null) {
            notes.add("grossProfitRate missing after overview + businessInsightSummary");
        }
        if (salesAmount == null) {
            notes.add("salesAmount missing after overview + businessInsightSummary");
        }
        if (costAmount == null) {
            notes.add("costAmount missing after overview + businessInsightSummary");
        }
        if (focusRows.isEmpty()) {
            notes.add("focusRows empty");
        }

        return BusinessDiagnosisCompositeDishProfitSummary.builder()
                .grossProfitAmount(grossProfitAmount)
                .grossProfitRate(grossProfitRate)
                .salesAmount(salesAmount)
                .costAmount(costAmount)
                .focusRows(focusRows)
                .build();
    }

    private static Double parseOverviewMoney(String s, List<String> notes, String logical) {
        if (s == null || s.isBlank()) {
            notes.add(logical + " missing/unset string in AiDishProfitOverviewResult");
            return null;
        }
        String t = s.trim();
        if ("暂无".equals(t)) {
            notes.add(logical + " unavailable (暂无) in AiDishProfitOverviewResult");
            return null;
        }
        if (t.contains("不适用")) {
            notes.add(logical + " not applicable in AiDishProfitOverviewResult; left null");
            return null;
        }
        String num = t.replace(",", "").replace("元", "");
        Double d = toDouble(num);
        if (d == null) {
            notes.add(logical + " unparsable in AiDishProfitOverviewResult");
            return null;
        }
        if (d == 0.0) {
            notes.add(logical + " real zero from AiDishProfitOverviewResult");
        }
        return d;
    }

    private static Double parseOverviewRate(String s, List<String> notes, String logical) {
        if (s == null || s.isBlank()) {
            notes.add(logical + " missing/unset string in AiDishProfitOverviewResult");
            return null;
        }
        String t = s.trim();
        if ("暂无".equals(t) || t.contains("不适用")) {
            notes.add(logical + " unavailable or N/A in AiDishProfitOverviewResult; left null");
            return null;
        }
        String num = t.replace("%", "").trim();
        Double d = toDouble(num);
        if (d == null) {
            notes.add(logical + " unparsable in AiDishProfitOverviewResult");
            return null;
        }
        if (d == 0.0) {
            notes.add(logical + " real zero from AiDishProfitOverviewResult");
        }
        return d;
    }

    private static Double bisNumeric(Map<String, Object> bis, String key, List<String> notes, String logical) {
        if (!bis.containsKey(key)) {
            notes.add(logical + " missing key businessInsightSummary." + key);
            return null;
        }
        Object raw = bis.get(key);
        if (raw == null) {
            notes.add(logical + " null in businessInsightSummary." + key);
            return null;
        }
        String s = raw.toString().trim();
        if (s.isEmpty() || "暂无".equals(s)) {
            notes.add(logical + " empty/暂无 in businessInsightSummary." + key);
            return null;
        }
        try {
            Double d = Double.parseDouble(s.replace(",", ""));
            if (d == 0.0) {
                notes.add(
                        logical + " real zero from toolResults[dish_profit_analysis].businessInsightSummary." + key);
            }
            return d;
        } catch (NumberFormatException ex) {
            notes.add(logical + " unparsable in businessInsightSummary." + key);
            return null;
        }
    }

    private static Double bisRate(Map<String, Object> bis, String key, List<String> notes, String logical) {
        if (!bis.containsKey(key)) {
            notes.add(logical + " missing key businessInsightSummary." + key);
            return null;
        }
        Object raw = bis.get(key);
        if (raw == null) {
            notes.add(logical + " null in businessInsightSummary." + key);
            return null;
        }
        String t = raw.toString().trim();
        if (t.isEmpty() || "暂无".equals(t) || t.contains("不适用")) {
            notes.add(logical + " empty/暂无/N/A in businessInsightSummary." + key);
            return null;
        }
        if (!t.contains("%")) {
            t = t + "%";
        }
        String num = t.replace("%", "").trim();
        Double d = toDouble(num);
        if (d == null) {
            notes.add(logical + " unparsable in businessInsightSummary." + key);
            return null;
        }
        if (d == 0.0) {
            notes.add(
                    logical + " real zero from toolResults[dish_profit_analysis].businessInsightSummary." + key);
        }
        return d;
    }

    private static AiRunState revenueRunState(PlannerStepExecutionRequest request) {
        PlannerRevenueExecutionContext ctx = request.getRevenueExecutionContext();
        return ctx == null ? null : ctx.getRunState();
    }

    private static void putJoined(LinkedHashMap<String, Object> dest, String key, List<String> parts) {
        if (parts == null || parts.isEmpty()) {
            return;
        }
        dest.put(key, String.join("; ", parts));
    }

    private static String sourceList(
            boolean a, boolean b, String labelA, String labelB) {
        List<String> xs = new ArrayList<>();
        if (a) {
            xs.add(labelA);
        }
        if (b) {
            xs.add(labelB);
        }
        return xs.isEmpty() ? "none" : String.join(", ", xs);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> revenueInnerFromTool(AiRunState run) {
        if (run == null || run.getToolResults() == null) {
            return Map.of();
        }
        Object env = run.getToolResults().get(AiBusinessToolIds.REVENUE_QUERY);
        if (!(env instanceof Map<?, ?> envMap) || !Boolean.TRUE.equals(envMap.get("success"))) {
            return Map.of();
        }
        Object dataRaw = unwrapJson(envMap.get("data"));
        if (!(dataRaw instanceof Map<?, ?>)) {
            return Map.of();
        }
        return new LinkedHashMap<>((Map<String, Object>) dataRaw);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rankingMapsAsStoreRows(List<?> ranking) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : ranking) {
            if (o instanceof Map<?, ?> m) {
                out.add(new LinkedHashMap<>((Map<String, Object>) m));
            }
        }
        return out;
    }

    private static List<Map<String, Object>> copyRowMaps(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (row != null) {
                out.add(new LinkedHashMap<>(row));
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> purchaseOverviewFromTool(AiRunState run) {
        if (run == null || run.getToolResults() == null) {
            return Map.of();
        }
        Object env = run.getToolResults().get(AiBusinessToolIds.PURCHASE_OVERVIEW);
        if (!(env instanceof Map<?, ?> envMap) || !Boolean.TRUE.equals(envMap.get("success"))) {
            return Map.of();
        }
        Object data = unwrapJson(envMap.get("data"));
        if (data instanceof Map<?, ?> dm) {
            Map<String, Object> dmap = (Map<String, Object>) dm;
            Object po = dmap.get("purchaseOverview");
            if (po instanceof Map<?, ?> pom) {
                return new LinkedHashMap<>((Map<String, Object>) pom);
            }
            if (dmap.containsKey("totalPurchaseAmount") || dmap.containsKey("purchaseOrderCount")) {
                return new LinkedHashMap<>(dmap);
            }
        }
        Object poTop = envMap.get("purchaseOverview");
        if (poTop instanceof Map<?, ?> pom) {
            return new LinkedHashMap<>((Map<String, Object>) pom);
        }
        return deepFindPurchaseOverview(envMap, 5);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepFindPurchaseOverview(Object node, int depthLeft) {
        if (depthLeft <= 0 || node == null) {
            return Map.of();
        }
        if (node instanceof Map<?, ?> m) {
            Object po = m.get("purchaseOverview");
            if (po instanceof Map<?, ?> pom && !pom.isEmpty()) {
                return new LinkedHashMap<>((Map<String, Object>) pom);
            }
            for (Object v : m.values()) {
                Map<String, Object> hit = deepFindPurchaseOverview(v, depthLeft - 1);
                if (!hit.isEmpty()) {
                    return hit;
                }
            }
        }
        return Map.of();
    }

    private static Object firstFocusField(List<Map<String, Object>> focusRows, String key) {
        if (focusRows == null || focusRows.isEmpty()) {
            return null;
        }
        return focusRows.get(0).get(key);
    }

    private static Long sumLineCountsFromSummary(Map<String, Object> s) {
        if (s == null) {
            return null;
        }
        Long self = toLong(s.get("selfPurchaseLineCount"));
        Long sup = toLong(s.get("supplierPurchaseLineCount"));
        if (self == null && sup == null) {
            return null;
        }
        return (self == null ? 0L : self) + (sup == null ? 0L : sup);
    }

    private static boolean hasPositiveIndicatorForCount(Map<String, Object> overview) {
        if (overview == null) {
            return false;
        }
        Object cnt = overview.get("purchaseOrderCount");
        if (cnt instanceof Number n) {
            return n.longValue() > 0;
        }
        Long parsed = toLong(cnt);
        return parsed != null && parsed > 0;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> stockInnerFromTool(AiRunState run, List<String> notes) {
        if (run == null || run.getToolResults() == null) {
            return Map.of();
        }
        Object env = run.getToolResults().get(AiBusinessToolIds.STOCK_REDUCE_QUERY);
        if (!(env instanceof Map<?, ?> envMap) || !Boolean.TRUE.equals(envMap.get("success"))) {
            return Map.of();
        }
        Object dataObj = unwrapJson(envMap.get("data"));
        LinkedHashMap<String, Object> diag = new LinkedHashMap<>();
        Map<String, Object> inner = extractStockReduceInnerSnapshot(dataObj, diag);
        if (!diag.isEmpty() && diag.get("foundDataPath") != null) {
            notes.add("stock tool data path=" + diag.get("foundDataPath"));
        }
        return inner;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractStockReduceInnerSnapshot(Object dataObj, Map<String, Object> diag) {
        Object node = dataObj;
        if (node instanceof Map<?, ?> m) {
            Object nested = m.get("data");
            if (nested instanceof Map<?, ?> && m.containsKey("schemaVersion")) {
                node = nested;
                diag.put("foundDataPath", "envelope.data.data");
            } else {
                diag.put("foundDataPath", "envelope.data");
            }
        }
        if (!(node instanceof Map<?, ?> dm)) {
            return Map.of();
        }
        Map<String, Object> map = (Map<String, Object>) dm;
        if (looksLikeStockReduceDataMap(map)) {
            return new LinkedHashMap<>(map);
        }
        Map<String, Object> deep = deepFindStockReduceShape(map, 4);
        if (!deep.isEmpty()) {
            diag.put("foundDataPath", diag.getOrDefault("foundDataPath", "?") + "+deepScan");
            return deep;
        }
        Object rr = map.get("rawReduceTotals");
        if (rr instanceof Map<?, ?>) {
            LinkedHashMap<String, Object> lifted = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : ((Map<?, ?>) rr).entrySet()) {
                lifted.put(String.valueOf(e.getKey()), e.getValue());
            }
            lifted.putAll(map);
            if (looksLikeStockReduceDataMap(lifted)) {
                diag.put("foundDataPath", "rawReduceTotals+merge");
                return lifted;
            }
        }
        return Map.of();
    }

    private static boolean looksLikeStockReduceDataMap(Map<String, Object> map) {
        return map.containsKey("produceTotal")
                || map.containsKey("grandTotalFourTypes")
                || map.containsKey("topGoodsOutboundBySubtotal")
                || map.containsKey("topGoodsOutboundByOutboundTimes")
                || map.containsKey("topStoresOutboundByGrandTotal");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepFindStockReduceShape(Object node, int depth) {
        if (depth <= 0 || !(node instanceof Map<?, ?> m)) {
            return Map.of();
        }
        Map<String, Object> asObj = (Map<String, Object>) m;
        if (looksLikeStockReduceDataMap(asObj)) {
            return new LinkedHashMap<>(asObj);
        }
        for (Object v : m.values()) {
            Map<String, Object> hit = deepFindStockReduceShape(v, depth - 1);
            if (!hit.isEmpty()) {
                return hit;
            }
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> dishToolDataMap(AiRunState run) {
        if (run == null || run.getToolResults() == null) {
            return Map.of();
        }
        Object env = run.getToolResults().get(AiBusinessToolIds.DISH_PROFIT_ANALYSIS);
        if (!(env instanceof Map<?, ?> envMap) || !Boolean.TRUE.equals(envMap.get("success"))) {
            return Map.of();
        }
        Object data = unwrapJson(envMap.get("data"));
        if (!(data instanceof Map<?, ?>)) {
            return Map.of();
        }
        return new LinkedHashMap<>((Map<String, Object>) data);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> bisMap(Object o) {
        if (!(o instanceof Map<?, ?> m)) {
            return Map.of();
        }
        return new LinkedHashMap<>((Map<String, Object>) m);
    }

    private static Object unwrapJson(Object data) {
        if (data instanceof String s && !s.isBlank()) {
            try {
                Object parsed = JSON.parse(s);
                return parsed != null ? parsed : data;
            } catch (Exception ignore) {
                return data;
            }
        }
        return data;
    }

    private static Double toDouble(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(v.toString().trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Long toLong(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(v.toString().trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
