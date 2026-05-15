package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.AiDishProfitDishBrief;
import com.nongxinle.ai.dto.business.AiDishProfitOverviewResult;
import com.nongxinle.ai.dto.business.AiOverviewStoreIssueItem;
import com.nongxinle.ai.dto.business.AiOverviewVisibleStoreItem;
import com.nongxinle.ai.dto.business.BusinessDiagnosisPlan;
import com.nongxinle.ai.dto.business.DiagnosisPlan;
import com.nongxinle.ai.dto.business.DishProfitAnswerPlan;
import com.nongxinle.ai.security.AiAnswerBoundary;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.nongxinle.ai.util.AiTimeWindowTextFormatter;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 从本轮 Tool 产物与菜品毛利 Agent 输出组装 {@link BusinessDiagnosisPlan}（Composer 只读）。
 */
final class BusinessDiagnosisPlanBuilder {

    private static final String DAILY_REVENUE_ANSWER_PLAN_PREFIX = "DailyRevenueAnswerPlan";

    /** Shown when {@link BusinessDiagnosisPlan.DataCompletenessBlock#getRevenue()} was {@code MISSING}. */
    private static final String REVENUE_BACKFILL_ACTION_PHRASE = "先补全日营业额或营收数据";

    private BusinessDiagnosisPlanBuilder() {
    }

    /**
     * After {@link DiagnosisPlanBuilder#attachIfApplicable} (and Multi-Agent AnswerPlan mounts), fixes legacy
     * {@link BusinessDiagnosisPlan#getDataCompleteness()}{@code .revenue} and {@link BusinessDiagnosisPlan#getActionItems()}
     * when {@link com.nongxinle.ai.dto.business.DailyRevenueAnswerPlan} / {@code consumedAnswerPlans} /
     * {@link AiBusinessToolIds#REVENUE_QUERY}
     * show revenue is actually available — {@link #build(AiRunState)} often runs earlier and could not see them.
     */
    static void reconcileBusinessDiagnosisRevenueCompleteness(AiRunState state) {
        if (state == null || state.getBusinessDiagnosisPlan() == null) {
            return;
        }
        if (!diagnosisRevenueEvidencePresent(state)) {
            return;
        }
        if (AiAnswerBoundary.isToolPermissionDenied(state.getPermissionDenials(), AiBusinessToolIds.REVENUE_QUERY)) {
            return;
        }
        BusinessDiagnosisPlan plan = state.getBusinessDiagnosisPlan();
        BusinessDiagnosisPlan.DataCompletenessBlock dc = plan.getDataCompleteness();
        if (dc == null) {
            return;
        }
        if ("MISSING".equalsIgnoreCase(stringify(dc.getRevenue()))) {
            dc.setRevenue("OK");
        }
        stripIncorrectRevenueBackfillAction(plan);
    }

    static BusinessDiagnosisPlan build(AiRunState state) {
        if (state == null) {
            return null;
        }
        List<String> planned = state.getDataPlanTools() == null ? List.of() : state.getDataPlanTools();
        List<String> used = new ArrayList<>();
        for (String id : planned) {
            if (toolEnvelopeSuccess(state, id)) {
                used.add(id);
            }
        }

        Map<String, Object> purInner = toolEnvelopeData(state, AiBusinessToolIds.PURCHASE_OVERVIEW);
        @SuppressWarnings("unchecked")
        Map<String, Object> purchaseOverview =
                purInner.get("purchaseOverview") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();

        Map<String, Object> stkInner = toolEnvelopeData(state, AiBusinessToolIds.STOCK_REDUCE_QUERY);

        AiDishProfitOverviewResult dp = state.getDishProfitOverviewResult();
        DishProfitAnswerPlan ap = state.getDishProfitAnswerPlan();

        boolean revenueDenied = AiAnswerBoundary.isToolPermissionDenied(
                state.getPermissionDenials(), AiBusinessToolIds.REVENUE_QUERY);
        boolean dishDenied = AiAnswerBoundary.isToolPermissionDenied(
                state.getPermissionDenials(), AiBusinessToolIds.DISH_PROFIT_ANALYSIS);
        AiDishProfitOverviewResult dpEff = dishDenied ? null : dp;
        DishProfitAnswerPlan apEff = dishDenied ? null : ap;

        BusinessDiagnosisPlan.SourceResultSummary srs = BusinessDiagnosisPlan.SourceResultSummary.builder()
                .purchase(sketchPurchase(purchaseOverview))
                .stockReduce(sketchStock(stkInner))
                .dishProfit(sketchDish(dpEff, apEff))
                .build();

        BusinessDiagnosisPlan.DataCompletenessBlock dc = BusinessDiagnosisPlan.DataCompletenessBlock.builder()
                .purchase(completeness(AiBusinessToolIds.PURCHASE_OVERVIEW, planned, state))
                .stockReduce(completeness(AiBusinessToolIds.STOCK_REDUCE_QUERY, planned, state))
                .dishProfit(completeness(AiBusinessToolIds.DISH_PROFIT_ANALYSIS, planned, state))
                .revenue(diagnosisRevenueCompletenessBootstrap(state, planned, revenueDenied))
                .build();

        List<BusinessDiagnosisPlan.DiagnosisRiskItem> risks = new ArrayList<>();
        List<String> findings = new ArrayList<>();
        appendDishRisks(dpEff, apEff, risks, findings);
        appendPurchaseRisk(purchaseOverview, planned, state, risks, findings);
        appendStockRisk(stkInner, planned, state, risks, findings);

        List<String> actionItems = buildDefaultActionItems(dc, risks, dpEff, planned, srs);

        BusinessDiagnosisPlan.StorePriorityRankingPlan storePriorityRanking =
                buildStorePriorityRankingPlan(state, purchaseOverview, dpEff, planned, dc, srs);

        String headline = buildHeadline(srs, dc);
        if (StringUtils.hasText(headline) && findings.isEmpty()) {
            findings.add(headline);
        }

        if (storePriorityRanking != null && storePriorityRanking.getFocusStores() != null
                && !storePriorityRanking.getFocusStores().isEmpty()) {
            findings.add(0, "老板先看哪家店：下列门店已按数据缺口与异常信号排序；建议从排名第 1 的门店开始处理。");
            BusinessDiagnosisPlan.StorePriorityFocus head = storePriorityRanking.getFocusStores().get(0);
            if (head != null && StringUtils.hasText(head.getSuggestion())) {
                actionItems.add(0, "优先：" + head.getSuggestion().trim());
            }
        }

        String riskLevel = aggregateRiskLevel(risks, dpEff);

        BusinessDiagnosisPlan.DebugRef dbg = BusinessDiagnosisPlan.DebugRef.builder()
                .purchaseSnapshotId(stringifyToolRef(state, AiBusinessToolIds.PURCHASE_OVERVIEW))
                .stockReduceSnapshotId(stringifyToolRef(state, AiBusinessToolIds.STOCK_REDUCE_QUERY))
                .dishProfitAnswerPlanType(apEff != null ? apEff.getPlanType() : null)
                .build();

        String timeLabel = AiTimeWindowTextFormatter.forAnswer(state).getDisplayTimeRange();

        return BusinessDiagnosisPlan.builder()
                .planType(BusinessDiagnosisPlan.TYPE_BUSINESS_DIAGNOSIS)
                .scopeLabel(resolveScopeLabel(state))
                .timeLabel(timeLabel)
                .riskLevel(riskLevel)
                .overallSummary(BusinessDiagnosisPlan.OverallSummary.builder()
                        .normalized(true)
                        .dataSufficient("INFO".equals(riskLevel) || "WARN".equals(riskLevel))
                        .headline(headline)
                        .build())
                .mainFindings(findings)
                .riskItems(risks)
                .focusTargets(buildFocusTargets(dpEff, apEff))
                .actionItems(actionItems)
                .sourceTools(new ArrayList<>(AiBusinessToolIds.DEFAULT_BUSINESS_DIAGNOSIS_TOOLS))
                .usedTools(used)
                .dataCompleteness(dc)
                .debugRef(dbg)
                .sourceResultSummary(srs)
                .storePriorityRanking(storePriorityRanking)
                .debug(new LinkedHashMap<>())
                .build();
    }

    private static BusinessDiagnosisPlan.StorePriorityRankingPlan buildStorePriorityRankingPlan(AiRunState state,
            Map<String, Object> purchaseOverview, AiDishProfitOverviewResult dp,
            List<String> planned, BusinessDiagnosisPlan.DataCompletenessBlock dc,
            BusinessDiagnosisPlan.SourceResultSummary srs) {
        AiResolvedQueryContext ctx = state.getResolvedQueryContext();
        if (ctx == null || ctx.getQueryIntent() == null
                || !AiQuerySemanticLexicon.isStorePriorityRankingStructuredDetail(
                        ctx.getQueryIntent().getStructuredIntentDetail())) {
            return null;
        }
        AiResolvedOrgScope org = ctx.getOrgScope();
        if (org != null && AiResolvedOrgScope.SCOPE_WAREHOUSE.equals(org.getScopeType())) {
            return null;
        }
        if (org == null || org.getVisibleStores() == null || org.getVisibleStores().isEmpty()) {
            return null;
        }

        boolean okPurchase = planned.contains(AiBusinessToolIds.PURCHASE_OVERVIEW)
                && toolEnvelopeSuccess(state, AiBusinessToolIds.PURCHASE_OVERVIEW);
        boolean okStock = planned.contains(AiBusinessToolIds.STOCK_REDUCE_QUERY)
                && toolEnvelopeSuccess(state, AiBusinessToolIds.STOCK_REDUCE_QUERY);
        boolean okDish = planned.contains(AiBusinessToolIds.DISH_PROFIT_ANALYSIS)
                && toolEnvelopeSuccess(state, AiBusinessToolIds.DISH_PROFIT_ANALYSIS) && dp != null;

        Double stockTotal = srs != null && srs.getStockReduce() != null ? srs.getStockReduce().getTotalAmount() : null;
        boolean groupStockZero = okStock && dc != null && "OK".equalsIgnoreCase(stringify(dc.getStockReduce()))
                && (stockTotal == null || stockTotal <= 0);

        Double purTotal = srs != null && srs.getPurchase() != null ? srs.getPurchase().getTotalAmount() : null;
        boolean groupPurchaseZero = okPurchase && dc != null && "OK".equalsIgnoreCase(stringify(dc.getPurchase()))
                && (purTotal == null || purTotal <= 0);

        boolean groupDishIncomplete =
                okDish && "data_incomplete".equalsIgnoreCase(stringify(dp.getRiskLevel()));
        int lowProfitCount = 0;
        if (okDish && dp.getLowProfitDishes() != null) {
            for (AiDishProfitDishBrief b : dp.getLowProfitDishes()) {
                if (b != null) {
                    lowProfitCount++;
                }
            }
        }
        boolean groupLowProfitSignal = lowProfitCount > 0;

        Map<Long, String> dishMissing = new HashMap<>();
        Set<Long> dishCovered = new HashSet<>();
        if (okDish) {
            if (dp.getDataMissingStores() != null) {
                for (AiOverviewStoreIssueItem it : dp.getDataMissingStores()) {
                    if (it != null && it.getStoreDepartmentId() != null) {
                        dishMissing.put(it.getStoreDepartmentId(), stringify(it.getReason()));
                    }
                }
            }
            if (dp.getCoveredStores() != null) {
                for (AiOverviewVisibleStoreItem v : dp.getCoveredStores()) {
                    if (v != null && v.getStoreDepartmentId() != null) {
                        dishCovered.add(v.getStoreDepartmentId());
                    }
                }
            }
        }

        Map<Long, BigDecimal> purchaseByStore = new HashMap<>();
        Set<Long> purchaseMissing = new HashSet<>();
        if (okPurchase && purchaseOverview != null) {
            for (Map<String, Object> row : mapRows(purchaseOverview.get("coveredStores"))) {
                Long sid = longFrom(row.get("storeDepartmentId"));
                if (sid == null) {
                    continue;
                }
                purchaseByStore.put(sid, parseDecimal(stringify(row.get("purchaseSubtotal"))));
            }
            for (Map<String, Object> row : mapRows(purchaseOverview.get("dataMissingStores"))) {
                Long sid = longFrom(row.get("storeDepartmentId"));
                if (sid != null) {
                    purchaseMissing.add(sid);
                }
            }
        }

        List<StorePriScratch> scratches = new ArrayList<>();
        for (AiStoreScopeDTO vs : org.getVisibleStores()) {
            if (vs == null || vs.getStoreDepartmentId() == null) {
                continue;
            }
            long sid = vs.getStoreDepartmentId();
            String sname = StringUtils.hasText(vs.getStoreName()) ? vs.getStoreName().trim() : "门店";
            StorePriScratch sc = new StorePriScratch();
            sc.id = sid;
            sc.name = sname;
            LinkedHashMap<String, Object> sig = new LinkedHashMap<>();
            List<String> reasons = new ArrayList<>();

            boolean dishRowMissing = dishMissing.containsKey(sid);
            boolean purchaseRowMissing = okPurchase && purchaseMissing.contains(sid);
            BigDecimal pSub = purchaseByStore.get(sid);
            boolean purchaseZero =
                    okPurchase && !purchaseRowMissing && pSub != null && pSub.compareTo(BigDecimal.ZERO) <= 0;
            boolean purchaseNoRow = okPurchase && !purchaseRowMissing && !purchaseByStore.containsKey(sid)
                    && (!purchaseByStore.isEmpty() || !purchaseMissing.isEmpty());

            if (okDish && dc != null && "OK".equalsIgnoreCase(stringify(dc.getDishProfit()))) {
                if (dishRowMissing) {
                    sc.gapScore += 4;
                    String r = dishMissing.get(sid);
                    sig.put("dataCompleteness", "MISSING");
                    if (StringUtils.hasText(r)) {
                        reasons.add("菜品毛利数据不完整：" + r);
                    } else {
                        reasons.add("菜品毛利数据不完整（缺 BOM/出库或透视未覆盖）。");
                    }
                } else if (!dishCovered.isEmpty() && !dishCovered.contains(sid)) {
                    sc.gapScore += 2;
                    sig.put("dataCompleteness", "PARTIAL");
                    reasons.add("本期菜品毛利未覆盖该门店（与已覆盖门店相比存在透视缺口）。");
                } else if (dishCovered.contains(sid)) {
                    sig.put("dataCompleteness", groupDishIncomplete ? "UNCERTAIN" : "OK");
                }
            } else if (groupDishIncomplete) {
                sc.gapScore += 1;
                reasons.add("集团口径菜品成本数据不完整，单店毛利判断偏粗。");
            }

            if (okPurchase && dc != null && "OK".equalsIgnoreCase(stringify(dc.getPurchase()))) {
                if (purchaseRowMissing) {
                    sc.gapScore += 3;
                    sc.purchaseIssue = true;
                    sig.put("purchaseAmount", "MISSING");
                    reasons.add("采购数据未入账或未覆盖该门店。");
                } else if (purchaseNoRow) {
                    sc.gapScore += 2;
                    sc.purchaseIssue = true;
                    sig.put("purchaseAmount", "MISSING");
                    reasons.add("采购透视未返回该门店分行，无法核对进货。");
                } else if (purchaseZero) {
                    sc.gapScore += 2;
                    sc.purchaseIssue = true;
                    sig.put("purchaseAmount", 0);
                    reasons.add("本期该门店采购额为 0（若无真实停机需核对是否漏录）。");
                } else if (pSub != null) {
                    sig.put("purchaseAmount", pSub.doubleValue());
                }
                if (groupPurchaseZero) {
                    sc.gapScore += 1;
                    reasons.add("集团口径采购合计为 0，各店进货信号均需复核。");
                }
            }

            if (groupStockZero) {
                sc.gapScore += 2;
                sig.put("stockReduceAmount", 0);
                reasons.add("集团口径出库/核销四类合计为 0（若有销售需优先核对核销链路）。");
            }

            if (groupLowProfitSignal && okDish && dishCovered.contains(sid) && !dishRowMissing) {
                sc.gapScore += 1;
                sig.put("dishProfitRiskCount", Math.min(lowProfitCount, 20));
                reasons.add("集团视角存在低毛利菜信号，该店在透视覆盖内，需重点盯菜品结构。");
            }

            if (reasons.isEmpty()) {
                reasons.add("相对其它可见门店，本期异常信号较少；仍建议抽查核销与配方一致性。");
                sig.put("dataCompleteness", sig.getOrDefault("dataCompleteness", "OK"));
            }

            sc.reasons = reasons;
            sc.signals = sig;
            sc.riskOrd = riskOrdinal(sc.gapScore, dishRowMissing, purchaseRowMissing || purchaseZero || purchaseNoRow,
                    groupStockZero);
            scratches.add(sc);
        }

        scratches.sort(Comparator
                .comparingInt((StorePriScratch a) -> a.riskOrd).reversed()
                .thenComparingInt((StorePriScratch a) -> a.gapScore).reversed()
                .thenComparingInt(a -> a.purchaseIssue ? 1 : 0)
                .thenComparing(a -> a.name, Comparator.naturalOrder()));

        List<BusinessDiagnosisPlan.StorePriorityFocus> focus = new ArrayList<>();
        int rank = 1;
        for (StorePriScratch sc : scratches) {
            String risk = sc.riskOrd >= 3 ? "HIGH" : (sc.riskOrd >= 2 ? "MEDIUM" : "LOW");
            String reason = String.join("；", sc.reasons);
            if (reason.length() > 220) {
                reason = reason.substring(0, 217) + "…";
            }
            focus.add(BusinessDiagnosisPlan.StorePriorityFocus.builder()
                    .storeDepartmentId(sc.id)
                    .storeName(sc.name)
                    .priorityRank(rank++)
                    .riskLevel(risk)
                    .reason(reason)
                    .signals(new LinkedHashMap<>(sc.signals))
                    .suggestion(buildStorePriSuggestion(sc))
                    .build());
        }

        if (focus.isEmpty()) {
            return null;
        }
        return BusinessDiagnosisPlan.StorePriorityRankingPlan.builder()
                .rankingType("STORE_PRIORITY_RANKING")
                .focusStores(focus)
                .build();
    }

    private static int riskOrdinal(int gapScore, boolean dishMissing, boolean purchaseBad, boolean groupStockZero) {
        if (dishMissing || gapScore >= 6) {
            return 3;
        }
        if (gapScore >= 3 || (purchaseBad && groupStockZero)) {
            return 2;
        }
        return gapScore >= 1 ? 2 : 1;
    }

    private static String buildStorePriSuggestion(StorePriScratch sc) {
        List<String> parts = new ArrayList<>();
        if (sc.signals.get("dataCompleteness") instanceof String dc && "MISSING".equals(dc)) {
            parts.add("核对「" + sc.name + "」菜品配方/BOM 与出库核销是否进透视");
        }
        if (Boolean.TRUE.equals(sc.purchaseIssue) || "MISSING".equals(String.valueOf(sc.signals.get("purchaseAmount")))) {
            parts.add("核对「" + sc.name + "」采购入库是否在统计周期内完整入账");
        }
        if (stockLikeZero(sc.signals.get("stockReduceAmount"))) {
            parts.add("核对「" + sc.name + "」营业额与出库核销是否漏录或接口未同步");
        }
        if (parts.isEmpty()) {
            return "对照集团菜品毛利清单，抽查「" + sc.name + "」结构与配方用量。";
        }
        return String.join("；", parts);
    }

    private static boolean stockLikeZero(Object v) {
        if (!(v instanceof Number n)) {
            return false;
        }
        return n.doubleValue() == 0.0d;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mapRows(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) {
                out.add((Map<String, Object>) m);
            }
        }
        return out;
    }

    private static Long longFrom(Object v) {
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

    private static final class StorePriScratch {
        private long id;
        private String name;
        private int gapScore;
        private int riskOrd;
        private boolean purchaseIssue;
        private LinkedHashMap<String, Object> signals = new LinkedHashMap<>();
        private List<String> reasons = new ArrayList<>();
    }

    private static String resolveScopeLabel(AiRunState state) {
        if (state.getResolvedQueryContext() != null
                && StringUtils.hasText(state.getResolvedQueryContext().getQueryScopeBanner())) {
            return state.getResolvedQueryContext().getQueryScopeBanner().trim();
        }
        return "";
    }

    private static void appendPurchaseRisk(Map<String, Object> purchaseOverview, List<String> planned,
            AiRunState state, List<BusinessDiagnosisPlan.DiagnosisRiskItem> risks, List<String> findings) {
        if (!planned.contains(AiBusinessToolIds.PURCHASE_OVERVIEW)) {
            return;
        }
        if (!toolEnvelopeSuccess(state, AiBusinessToolIds.PURCHASE_OVERVIEW)) {
            risks.add(risk("WARN", "PURCHASE", "采购数据未就绪", "采购概览工具未成功返回或未调用。", "核对采购权限与查询时间窗。"));
            return;
        }
        int cnt = intVal(purchaseOverview.get("purchaseOrderCount"));
        BigDecimal amt = parseDecimal(stringify(purchaseOverview.get("totalPurchaseAmount")));
        if (cnt == 0 && (amt == null || amt.compareTo(BigDecimal.ZERO) == 0)) {
            findings.add("统计周期内暂无采购入库记录。");
        }
    }

    private static void appendStockRisk(Map<String, Object> stkInner, List<String> planned, AiRunState state,
            List<BusinessDiagnosisPlan.DiagnosisRiskItem> risks, List<String> findings) {
        if (!planned.contains(AiBusinessToolIds.STOCK_REDUCE_QUERY)) {
            return;
        }
        if (!toolEnvelopeSuccess(state, AiBusinessToolIds.STOCK_REDUCE_QUERY)) {
            risks.add(risk("WARN", "STOCK_REDUCE", "出库/核销数据未就绪", "出库核销汇总工具未成功返回。", "核对库存权限与门店范围。"));
            return;
        }
        BigDecimal grand = nzDecimal(stkInner.get("grandTotalFourTypes"));
        if (grand.compareTo(BigDecimal.ZERO) == 0) {
            findings.add("统计周期内生产耗用、废弃、损耗、退货四类核销金额合计为 0。");
        }
    }

    private static void appendDishRisks(AiDishProfitOverviewResult dp, DishProfitAnswerPlan ap,
            List<BusinessDiagnosisPlan.DiagnosisRiskItem> risks, List<String> findings) {
        boolean fromPlan = appendDishLowestMarginFromAnswerPlan(ap, risks, findings);
        if (dp == null) {
            return;
        }
        if (!fromPlan && dp.getLowProfitDishes() != null && !dp.getLowProfitDishes().isEmpty()) {
            AiDishProfitDishBrief d0 = dp.getLowProfitDishes().get(0);
            if (d0 != null) {
                appendDishDragMarginRiskFromRowMap(lowProfitBriefToDetailRow(d0), risks, findings);
            }
        }
        if ("data_incomplete".equalsIgnoreCase(stringify(dp.getRiskLevel()))) {
            risks.add(risk("WARN", "DISH_PROFIT", "菜品成本数据不完整",
                    "部分菜品缺少 BOM 或出库核销，综合毛利率为粗算。", "补全成本卡与出库后再看排行。"));
            findings.add("菜品毛利透视存在成本数据不完整行，排行与综合毛利率仅供参考。");
        }
    }

    /**
     * 与 Debug 中 dishProfitAnswerPlan 一致：DISH_LOWEST_MARGIN 时以 focusRows[0] 为唯一核心风险对象。
     */
    private static boolean appendDishLowestMarginFromAnswerPlan(DishProfitAnswerPlan ap,
            List<BusinessDiagnosisPlan.DiagnosisRiskItem> risks, List<String> findings) {
        if (ap == null || !DishProfitAnswerPlan.TYPE_DISH_LOWEST_MARGIN.equals(ap.getPlanType())) {
            return false;
        }
        if (ap.getFocusRows() == null || ap.getFocusRows().isEmpty()) {
            return false;
        }
        Map<String, Object> r0 = ap.getFocusRows().get(0);
        if (r0 == null) {
            return false;
        }
        appendDishDragMarginRiskFromRowMap(r0, risks, findings);
        return true;
    }

    /**
     * 低毛利风险行：仅使用 dish 明细口径字段（与 AnswerPlan focusRows 同源），禁止套用透视汇总金额。
     */
    private static Map<String, Object> lowProfitBriefToDetailRow(AiDishProfitDishBrief b) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("dishName", b.getDishName());
        m.put("listPriceRevenue", diagnosisRowRaw(b.getSalesAmount()));
        m.put("theoryCostAmount", diagnosisRowRaw(b.getTheoreticalCost()));
        m.put("actualCostAmount", diagnosisRowRaw(b.getActualCost()));
        m.put("blendedGrossMarginRateOnListPrice", diagnosisMarginRaw(b.getGrossProfitRate()));
        m.put("riskReason", diagnosisRowRaw(b.getRiskReason()));
        return m;
    }

    private static String diagnosisRowRaw(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        String t = s.trim();
        if ("暂无".equals(t) || "—".equals(t) || t.contains("不适用")) {
            return null;
        }
        return t;
    }

    /** 将 brief 上毛利率可读串转为可与 focusRows 一致的 Number 或 String。 */
    private static Object diagnosisMarginRaw(String grossProfitRate) {
        if (!StringUtils.hasText(grossProfitRate)) {
            return null;
        }
        String t = grossProfitRate.trim();
        if ("暂无".equals(t) || "—".equals(t) || t.contains("不适用")) {
            return null;
        }
        if (t.endsWith("%")) {
            try {
                return Double.parseDouble(t.substring(0, t.length() - 1).trim().replace(",", ""));
            } catch (Exception e) {
                return t;
            }
        }
        try {
            return Double.parseDouble(t.replace(",", ""));
        } catch (Exception e) {
            return t;
        }
    }

    private static void appendDishDragMarginRiskFromRowMap(Map<String, Object> row,
            List<BusinessDiagnosisPlan.DiagnosisRiskItem> risks, List<String> findings) {
        if (row == null) {
            return;
        }
        String name = stringify(row.get("dishName"));
        if (!StringUtils.hasText(name)) {
            name = "该菜品";
        }
        String evidence = buildDiagnosisDishDragEvidenceSentence(name, row);
        risks.add(risk("WARN", "DISH_PROFIT", name + "拖累毛利/毛利偏低", evidence, "优先核对出库、配方和售价。"));
        String rate = fmtMarginPercentForDiagnosis(row.get("blendedGrossMarginRateOnListPrice"));
        if (diagnosisTextPresent(rate)) {
            findings.add(String.format(Locale.CHINA, "拖累毛利最明显的是 %s，毛利率约 %s。", name, rate));
        } else {
            findings.add(String.format(Locale.CHINA, "拖累毛利最明显的是 %s。", name));
        }
    }

    /**
     * 只拼非空明细；不输出「理论成本为，」类半截。金额/毛利率缺失则整句省略该段（必要时只保留 riskReason）。
     */
    private static String buildDiagnosisDishDragEvidenceSentence(String dishName, Map<String, Object> row) {
        List<String> segs = new ArrayList<>();
        String rate = fmtMarginPercentForDiagnosis(row.get("blendedGrossMarginRateOnListPrice"));
        if (diagnosisTextPresent(rate)) {
            segs.add("毛利率约 " + rate);
        }
        String rev = fmtYuanForDiagnosis(row.get("listPriceRevenue"));
        if (diagnosisTextPresent(rev)) {
            segs.add("销售额 " + rev + " 元");
        }
        String theory = fmtYuanForDiagnosis(row.get("theoryCostAmount"));
        if (diagnosisTextPresent(theory)) {
            segs.add("理论成本 " + theory + " 元");
        }
        String actual = fmtYuanForDiagnosis(row.get("actualCostAmount"));
        if (diagnosisTextPresent(actual)) {
            segs.add("实际成本 " + actual + " 元");
        }
        String reason = stringify(row.get("riskReason"));
        StringBuilder ev = new StringBuilder(dishName);
        if (!segs.isEmpty()) {
            ev.append(" ");
            ev.append(String.join("，", segs));
        }
        if (StringUtils.hasText(reason)) {
            if (!segs.isEmpty()) {
                ev.append("。");
            } else {
                ev.append(" ");
            }
            ev.append(reason);
        } else if (segs.isEmpty()) {
            ev.append(" 本期可用明细不足，建议核对单菜销售额与成本行是否返回完整。");
        }
        return ev.toString();
    }

    private static boolean diagnosisTextPresent(String s) {
        if (s == null || s.isBlank()) {
            return false;
        }
        String t = s.trim();
        return !"—".equals(t) && !"暂无".equals(t);
    }

    private static String fmtMarginPercentForDiagnosis(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return String.format(Locale.CHINA, "%.2f%%", n.doubleValue());
        }
        String s = stringify(v);
        if (s.isEmpty()) {
            return null;
        }
        if ("暂无".equals(s) || "—".equals(s) || s.contains("不适用")) {
            return null;
        }
        if (s.endsWith("%")) {
            return s;
        }
        try {
            double d = Double.parseDouble(s.replace(",", ""));
            return String.format(Locale.CHINA, "%.2f%%", d);
        } catch (Exception e) {
            return s;
        }
    }

    private static String fmtYuanForDiagnosis(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            BigDecimal b = n instanceof BigDecimal bd ? bd : BigDecimal.valueOf(n.doubleValue());
            return b.setScale(2, RoundingMode.HALF_UP).toPlainString();
        }
        String s = stringify(v);
        if (s.isEmpty() || "暂无".equals(s) || "—".equals(s) || s.contains("不适用")) {
            return null;
        }
        return s;
    }

    /** actionItems 为空时的兜底三件事：对齐 dataCompleteness / 摘要 / 风险。 */
    private static List<String> buildDefaultActionItems(BusinessDiagnosisPlan.DataCompletenessBlock dc,
            List<BusinessDiagnosisPlan.DiagnosisRiskItem> risks, AiDishProfitOverviewResult dp,
            List<String> planned, BusinessDiagnosisPlan.SourceResultSummary srs) {
        LinkedHashSet<String> acc = new LinkedHashSet<>();
        if (dc != null && "MISSING".equalsIgnoreCase(stringify(dc.getRevenue()))) {
            acc.add("先补全日营业额或营收数据，便于把采购、出库与菜品毛利串起来看。");
        }
        if (planned.contains(AiBusinessToolIds.PURCHASE_OVERVIEW) && dc != null && "OK".equals(dc.getPurchase())) {
            BusinessDiagnosisPlan.PurchaseSketch p = srs == null ? null : srs.getPurchase();
            if (p != null && (p.getTotalAmount() == null || p.getTotalAmount() <= 0)) {
                acc.add("本期采购金额为 0 或明显偏低：确认是否真实无采购，还是数据未同步或范围过窄。");
            }
        }
        if (planned.contains(AiBusinessToolIds.STOCK_REDUCE_QUERY) && dc != null && "OK".equals(dc.getStockReduce())) {
            BusinessDiagnosisPlan.StockReduceSketch st = srs == null ? null : srs.getStockReduce();
            if (st != null && (st.getTotalAmount() == null || st.getTotalAmount() <= 0)) {
                acc.add("本期出库/核销四类合计为 0：确认是否无耗用，还是核销流水未及时同步。");
            }
        }
        if (dp != null && "data_incomplete".equalsIgnoreCase(stringify(dp.getRiskLevel()))) {
            acc.add("菜品成本不完整：补全成本卡/BOM，并核对出库核销与配方用量。");
        }
        for (BusinessDiagnosisPlan.DiagnosisRiskItem ri : risks) {
            if (ri != null && StringUtils.hasText(ri.getSuggestion())) {
                acc.add(ri.getSuggestion().trim());
            }
            if (acc.size() >= 3) {
                break;
            }
        }
        List<String> out = new ArrayList<>(acc);
        String[] fallbacks = new String[] {
                "对照诊断中的时间窗与门店范围，与后台报表口径核对一致。",
                "将异常菜品与采购、出库峰值日期对齐复查。",
                "与门店同步本周优先核对项，避免只看汇总忽略结构性问题。"
        };
        for (String f : fallbacks) {
            if (out.size() >= 3) {
                break;
            }
            if (!out.contains(f)) {
                out.add(f);
            }
        }
        return out.size() > 3 ? new ArrayList<>(out.subList(0, 3)) : out;
    }

    private static BusinessDiagnosisPlan.FocusTargets buildFocusTargets(AiDishProfitOverviewResult dp,
            DishProfitAnswerPlan ap) {
        List<String> dishes = new ArrayList<>();
        if (ap != null && ap.getFocusRows() != null) {
            for (Map<String, Object> row : ap.getFocusRows()) {
                if (row == null) {
                    continue;
                }
                String n = stringify(row.get("dishName"));
                if (StringUtils.hasText(n)) {
                    dishes.add(n);
                }
            }
        }
        if (dishes.isEmpty() && dp != null && dp.getLowProfitDishes() != null) {
            for (AiDishProfitDishBrief b : dp.getLowProfitDishes()) {
                if (b != null && StringUtils.hasText(b.getDishName())) {
                    dishes.add(b.getDishName());
                    if (dishes.size() >= 3) {
                        break;
                    }
                }
            }
        }
        return BusinessDiagnosisPlan.FocusTargets.builder()
                .dishes(dishes)
                .build();
    }

    private static String buildHeadline(BusinessDiagnosisPlan.SourceResultSummary srs,
            BusinessDiagnosisPlan.DataCompletenessBlock dc) {
        StringBuilder sb = new StringBuilder();
        if (srs != null && srs.getPurchase() != null && srs.getPurchase().getTotalAmount() != null) {
            sb.append(String.format(Locale.CHINA, "采购约 %.0f 元", srs.getPurchase().getTotalAmount()));
        }
        if (srs != null && srs.getStockReduce() != null && srs.getStockReduce().getTotalAmount() != null) {
            if (sb.length() > 0) {
                sb.append("；");
            }
            sb.append(String.format(Locale.CHINA, "出库/核销四类合计约 %.1f 元", srs.getStockReduce().getTotalAmount()));
        }
        if (srs != null && srs.getDishProfit() != null && srs.getDishProfit().getGrossMarginRate() != null) {
            if (sb.length() > 0) {
                sb.append("；");
            }
            sb.append(String.format(Locale.CHINA, "菜品综合毛利率约 %.2f%%", srs.getDishProfit().getGrossMarginRate()));
        }
        if (sb.length() == 0) {
            return "本轮经营诊断数据部分缺失，以下为可用摘要。";
        }
        return sb.toString();
    }

    private static String aggregateRiskLevel(List<BusinessDiagnosisPlan.DiagnosisRiskItem> risks,
            AiDishProfitOverviewResult dp) {
        boolean high = risks.stream().anyMatch(r -> r != null && "HIGH".equalsIgnoreCase(stringify(r.getLevel())));
        if (high) {
            return "HIGH";
        }
        boolean warn = risks.stream().anyMatch(r -> r != null && "WARN".equalsIgnoreCase(stringify(r.getLevel())));
        if (warn) {
            return "WARN";
        }
        if (dp != null && "warning".equalsIgnoreCase(stringify(dp.getRiskLevel()))) {
            return "WARN";
        }
        if (dp != null && "data_incomplete".equalsIgnoreCase(stringify(dp.getRiskLevel()))) {
            return "WARN";
        }
        return "INFO";
    }

    private static BusinessDiagnosisPlan.DiagnosisRiskItem risk(String level, String domain, String title,
            String evidence, String suggestion) {
        return BusinessDiagnosisPlan.DiagnosisRiskItem.builder()
                .level(level)
                .domain(domain)
                .title(title)
                .evidence(evidence)
                .suggestion(suggestion)
                .build();
    }

    private static String completeness(String toolId, List<String> planned, AiRunState state) {
        if (!planned.contains(toolId)) {
            return "SKIPPED";
        }
        return toolEnvelopeSuccess(state, toolId) ? "OK" : "ERROR";
    }

    /** Before outcome review / {@link DiagnosisPlan} attachment: revenue AnswerPlan may not exist yet. */
    private static String diagnosisRevenueCompletenessBootstrap(AiRunState state, List<String> planned,
            boolean revenueDenied) {
        if (revenueDenied) {
            return "SKIPPED";
        }
        if (state != null && state.getRevenueAnswerPlan() != null) {
            return "OK";
        }
        List<String> p = planned == null ? List.of() : planned;
        return p.contains(AiBusinessToolIds.REVENUE_QUERY) && toolEnvelopeSuccess(state, AiBusinessToolIds.REVENUE_QUERY)
                ? "OK"
                : "MISSING";
    }

    private static boolean diagnosisRevenueEvidencePresent(AiRunState state) {
        if (state == null) {
            return false;
        }
        if (AiAnswerBoundary.isToolPermissionDenied(state.getPermissionDenials(), AiBusinessToolIds.REVENUE_QUERY)) {
            return false;
        }
        if (state.getRevenueAnswerPlan() != null) {
            return true;
        }
        if (consumedPlansListContainsDailyRevenue(state)) {
            return true;
        }
        List<String> planned = state.getDataPlanTools();
        List<String> p = planned == null ? List.of() : planned;
        return (p.contains(AiBusinessToolIds.REVENUE_QUERY)
                && toolEnvelopeSuccess(state, AiBusinessToolIds.REVENUE_QUERY))
                || toolEnvelopeSuccess(state, AiBusinessToolIds.REVENUE_QUERY);
    }

    private static boolean consumedPlansListContainsDailyRevenue(AiRunState state) {
        DiagnosisPlan dp = state.getDiagnosisPlan();
        if (dp == null || dp.getDebug() == null || dp.getDebug().isEmpty()) {
            return false;
        }
        Object raw = dp.getDebug().get("consumedAnswerPlans");
        if (!(raw instanceof List<?> list)) {
            return false;
        }
        for (Object o : list) {
            String s = stringify(o);
            if (s.startsWith(DAILY_REVENUE_ANSWER_PLAN_PREFIX)) {
                return true;
            }
        }
        return false;
    }

    private static void stripIncorrectRevenueBackfillAction(BusinessDiagnosisPlan plan) {
        List<String> items = plan.getActionItems();
        if (items == null || items.isEmpty()) {
            return;
        }
        items.removeIf(s -> StringUtils.hasText(s) && s.contains(REVENUE_BACKFILL_ACTION_PHRASE));
    }

    private static BusinessDiagnosisPlan.PurchaseSketch sketchPurchase(Map<String, Object> purchaseOverview) {
        if (purchaseOverview == null || purchaseOverview.isEmpty()) {
            return BusinessDiagnosisPlan.PurchaseSketch.builder().riskSignals(List.of()).build();
        }
        Double total = doubleOrNull(parseDecimal(stringify(purchaseOverview.get("totalPurchaseAmount"))));
        Double self = null;
        Double sup = null;
        Object br = purchaseOverview.get("purchaseMethodBreakdown");
        if (br instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> row)) {
                    continue;
                }
                String label = stringify(row.get("label"));
                BigDecimal a = parseDecimal(stringify(row.get("amountYuan")));
                if (label.contains("自采")) {
                    self = doubleOrNull(a);
                } else if (label.contains("供货商")) {
                    sup = doubleOrNull(a);
                }
            }
        }
        return BusinessDiagnosisPlan.PurchaseSketch.builder()
                .totalAmount(total)
                .selfPurchaseAmount(self)
                .supplierPurchaseAmount(sup)
                .riskSignals(List.of())
                .build();
    }

    private static BusinessDiagnosisPlan.StockReduceSketch sketchStock(Map<String, Object> stkInner) {
        if (stkInner == null || stkInner.isEmpty()) {
            return BusinessDiagnosisPlan.StockReduceSketch.builder().riskSignals(List.of()).build();
        }
        BigDecimal prod = nzDecimal(stkInner.get("produceTotal"));
        BigDecimal waste = nzDecimal(stkInner.get("wasteTotal"));
        BigDecimal loss = nzDecimal(stkInner.get("lossTotal"));
        BigDecimal ret = nzDecimal(stkInner.get("returnTotal"));
        BigDecimal grand = nzDecimal(stkInner.get("grandTotalFourTypes"));
        if (grand.compareTo(BigDecimal.ZERO) == 0) {
            grand = prod.add(waste).add(loss).add(ret);
        }
        return BusinessDiagnosisPlan.StockReduceSketch.builder()
                .totalAmount(doubleOrNull(grand))
                .produceAmount(doubleOrNull(prod))
                .wasteAmount(doubleOrNull(waste))
                .lossAmount(doubleOrNull(loss))
                .returnAmount(doubleOrNull(ret))
                .riskSignals(List.of())
                .build();
    }

    private static BusinessDiagnosisPlan.DishProfitSketch sketchDish(AiDishProfitOverviewResult dp,
            DishProfitAnswerPlan ap) {
        if (dp == null) {
            return BusinessDiagnosisPlan.DishProfitSketch.builder().riskSignals(List.of()).build();
        }
        Double sales = doubleOrNull(parseDecimal(dp.getTotalDishSalesAmount()));
        Double cost = doubleOrNull(parseDecimal(dp.getTotalActualCost()));
        Double rate = parseMarginPercent(dp.getGrossProfitRate());
        String lowest = null;
        if (dp.getLowProfitDishes() != null && !dp.getLowProfitDishes().isEmpty()
                && dp.getLowProfitDishes().get(0) != null) {
            lowest = dp.getLowProfitDishes().get(0).getDishName();
        }
        if (!StringUtils.hasText(lowest) && ap != null && ap.getFocusRows() != null) {
            for (Map<String, Object> row : ap.getFocusRows()) {
                if (row == null) {
                    continue;
                }
                lowest = stringify(row.get("dishName"));
                if (StringUtils.hasText(lowest)) {
                    break;
                }
            }
        }
        return BusinessDiagnosisPlan.DishProfitSketch.builder()
                .salesAmount(sales)
                .actualCostAmount(cost)
                .grossMarginRate(rate)
                .lowestMarginDish(lowest)
                .riskSignals(List.of())
                .build();
    }

    private static Double parseMarginPercent(String raw) {
        if (!StringUtils.hasText(raw) || raw.contains("不适用") || raw.contains("暂无")) {
            return null;
        }
        String t = raw.trim();
        if (t.endsWith("%")) {
            t = t.substring(0, t.length() - 1).trim();
        }
        BigDecimal d = parseDecimal(t);
        return doubleOrNull(d);
    }

    private static int intVal(Object v) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(stringify(v));
        } catch (Exception e) {
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toolEnvelopeData(AiRunState state, String toolKey) {
        Object env = state.getToolResults().get(toolKey);
        if (!(env instanceof Map)) {
            return Map.of();
        }
        Object nested = ((Map<String, Object>) env).get("data");
        if (!(nested instanceof Map)) {
            return Map.of();
        }
        return (Map<String, Object>) nested;
    }

    private static boolean toolEnvelopeSuccess(AiRunState state, String toolKey) {
        Object env = state.getToolResults().get(toolKey);
        if (!(env instanceof Map)) {
            return false;
        }
        return Boolean.TRUE.equals(((Map<?, ?>) env).get("success"));
    }

    private static String stringifyToolRef(AiRunState state, String toolKey) {
        Object env = state.getToolResults().get(toolKey);
        if (!(env instanceof Map<?, ?> m)) {
            return null;
        }
        Object tool = m.get("tool");
        Object start = m.get(AiBusinessToolIds.ARG_START_DATE);
        Object stop = m.get(AiBusinessToolIds.ARG_STOP_DATE);
        String ok = Boolean.TRUE.equals(m.get("success")) ? "ok" : "fail";
        return stringify(tool) + "|" + stringify(start) + ".." + stringify(stop) + "|" + ok;
    }

    private static String stringify(Object v) {
        return v == null ? "" : v.toString().trim();
    }

    private static BigDecimal nzDecimal(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v instanceof BigDecimal b) {
            return b;
        }
        return parseDecimal(v.toString());
    }

    private static BigDecimal parseDecimal(String s) {
        if (s == null || s.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(s.trim().replace(",", ""));
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private static Double doubleOrNull(BigDecimal b) {
        if (b == null) {
            return null;
        }
        return b.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
