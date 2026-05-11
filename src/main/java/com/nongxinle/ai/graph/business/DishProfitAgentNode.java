package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiOrgScope;
import com.nongxinle.ai.context.AiOrgScopeResolver;
import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.context.AiUserContext;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.core.AgentNode;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.AiDishProfitDishBrief;
import com.nongxinle.ai.dto.business.AiDishProfitOverviewResult;
import com.nongxinle.ai.dto.business.AiOverviewStoreIssueItem;
import com.nongxinle.ai.dto.business.AiOverviewVisibleStoreItem;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.nongxinle.ai.trace.AiSseEventPublisher;
import com.nongxinle.ai.util.AiTimeWindowTextFormatter;
import com.nongxinle.utils.GbDepartmentGoodsStockReduceSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * {@code dish_profit_path}：消费 {@link AiBusinessToolIds#DISH_PROFIT_ANALYSIS}，产出 SSE 结构化
 * {@code dishProfitOverview}。
 */
@Component
@RequiredArgsConstructor
public class DishProfitAgentNode implements AgentNode {

    private static final Pattern BEFORE_MAO = Pattern.compile("([\\u4e00-\\u9fa5]{2,16})毛利");

    private final AiSseEventPublisher publisher;

    @Override
    public String name() {
        return "DishProfitAgent";
    }

    @Override
    public boolean shouldRun(AiRunState state) {
        return state.isDishProfitPath()
                && state.getDataPlanTools() != null
                && !state.getDataPlanTools().isEmpty();
    }

    @Override
    public AiRunState run(AiRunState state) {
        long rid = state.getRunId();
        publisher.publish(rid, "agent_started", Map.of(
                "agent", "DishProfitAgent",
                "displayText", "正在汇总菜品毛利透视…"
        ));

        maybeMergeStoreDisclaimer(state);
        AiDishProfitOverviewResult result = deriveOverview(state);
        state.setDishProfitOverviewResult(result);

        publisher.publish(rid, "agent_finished", Map.of(
                "agent", "DishProfitAgent",
                "displayText", "菜品毛利透视已就绪"
        ));
        return state;
    }

    private static void maybeMergeStoreDisclaimer(AiRunState state) {
        AiUserContext ctx = state.getAiUserContext();
        if (ctx == null) {
            return;
        }
        String q = nz(state.getNormalizedUserInput());
        if (CostInsightIntentConvergence.shouldAddStoreScopedGroupCostDisclaimer(ctx, q)) {
            String note = "你当前账号只能查看本门店数据。下面是本门店菜品毛利情况。";
            String cur = state.getScopeConvergenceNote();
            if (cur == null || cur.isBlank()) {
                state.setScopeConvergenceNote(note);
            } else if (!cur.contains("本门店菜品")) {
                state.setScopeConvergenceNote(cur + "\n" + note);
            }
        }
    }

    private static AiDishProfitOverviewResult deriveOverview(AiRunState state) {
        Map<String, Object> data = toolEnvelopeData(state, AiBusinessToolIds.DISH_PROFIT_ANALYSIS);
        Map<String, Object> cov = coverageFromToolEnvelope(data);
        boolean ok = toolSuccess(state, AiBusinessToolIds.DISH_PROFIT_ANALYSIS);
        String[] scope = deriveScopePresentation(state);

        AiDishProfitOverviewResult empty = AiDishProfitOverviewResult.builder()
                .agentName("DishProfitAgent")
                .statStartDate(nz(state.getStatStartDate()))
                .statEndDate(nz(state.getStatEndDate()))
                .scopeType(scope[0])
                .scopeName(scope[1])
                .queryScopeBanner(buildQueryScopeOpening(state, cov))
                .visibleStores(visibleStoresFromResolved(state))
                .summary(toolFailedOrEmpty(ok))
                .dishCount(0)
                .totalDishSalesAmount("暂无")
                .totalTheoreticalCost("暂无")
                .totalActualCost("暂无")
                .grossProfitAmount("暂无")
                .grossProfitRate("暂无")
                .grossProfitRateUncertain(false)
                .riskLevel("data_incomplete")
                .recommendations(emptyRecs())
                .build();
        enrichCoverageOnto(empty, cov);
        applyResolvedVisibleStores(empty, state);

        if (!ok) {
            return empty;
        }

        String hint = stringify(data.get("userQuestionHint"));
        List<Map<String, Object>> dishRowsRaw = dedupeDishRowsByFoodIdOrName(extractDishRows(data));
        if (dishRowsRaw.isEmpty()) {
            int bisDishHint = dishRowCountHintFromToolData(data);
            if (bisDishHint > 0) {
                PortfolioAgg aggB = summarizePortfolio(List.of(), data, false);
                AiTimeWindowTextFormatter.UserPhrases tw = AiTimeWindowTextFormatter.forAnswer(state);
                String p = tw != null ? nz(tw.getDisplayTimeRange()) : "";
                String summaryMsg = String.format(Locale.CHINA,
                        "%s，本轮识别到 %d 道菜品销量记录，但成本字段不完整，暂不能计算可靠毛利。",
                        p.isEmpty() ? "该统计区间" : p, bisDishHint);
                empty.setSummary(summaryMsg);
                empty.setDishCount(bisDishHint);
                empty.setTotalDishSalesAmount(stripBd(aggB.revenue));
                empty.setTotalTheoreticalCost(stripBd(aggB.theory));
                empty.setTotalActualCost(stripBd(aggB.actual));
                empty.setGrossProfitAmount(stripBd(aggB.profitAmt));
                empty.setGrossProfitRate("暂不适用");
                empty.setGrossProfitRateUncertain(true);
                empty.setRiskLevel("data_incomplete");
                empty.setRecommendations(List.of(
                        "请先补齐菜品 BOM/出库核销与本区间销量口径，再解读综合毛利率。",
                        "若需核对单笔出库流水，可用出库/核销专线查看 type1 生产耗用。"));
                if (isActualOutboundCostOnlyQuestion(state)) {
                    shrinkToActualOutboundOnlyPresentation(empty, aggB, bisDishHint, state);
                }
                return empty;
            }
            empty.setSummary(toolsReturnedNoRows(state));
            return empty;
        }

        List<AiDishProfitDishBrief> allPeerBriefs = dishRowsRaw.stream()
                .map(DishProfitAgentNode::briefFromRow)
                .collect(Collectors.toList());

        boolean focusMode = false;
        List<Map<String, Object>> scoped = dishRowsRaw;
        if (!hint.isEmpty()) {
            List<Map<String, Object>> sub = narrowByUserHint(dishRowsRaw, hint);
            if (!sub.isEmpty() && sub.size() < dishRowsRaw.size()) {
                scoped = sub;
                focusMode = true;
            }
        }

        PortfolioAgg agg = summarizePortfolio(scoped, data, focusMode);
        List<AiDishProfitDishBrief> allBriefs = scoped.stream().map(DishProfitAgentNode::briefFromRow).collect(Collectors.toList());
        List<AiDishProfitDishBrief> costIncomplete = allBriefs.stream()
                .filter(DishProfitAgentNode::isCostDataIncomplete)
                .limit(24)
                .collect(Collectors.toList());

        boolean rateUncertain = !costIncomplete.isEmpty() && agg.revenue.signum() > 0;
        String summaryBody = buildSummarySentence(agg, focusMode, scoped.size(), rateUncertain,
                AiTimeWindowTextFormatter.forAnswer(state));
        String opening = buildQueryScopeOpening(state, cov);

        AiDishProfitOverviewResult.AiDishProfitOverviewResultBuilder b = AiDishProfitOverviewResult.builder()
                .agentName("DishProfitAgent")
                .statStartDate(nz(state.getStatStartDate()))
                .statEndDate(nz(state.getStatEndDate()))
                .scopeType(scope[0])
                .scopeName(scope[1])
                .queryScopeBanner(opening)
                .summary(summaryBody)
                .dishCount(scoped.size())
                .totalDishSalesAmount(stripBd(agg.revenue))
                .totalTheoreticalCost(stripBd(agg.theory))
                .totalActualCost(stripBd(agg.actual))
                .grossProfitAmount(stripBd(agg.profitAmt))
                .grossProfitRate(agg.portfolioRate)
                .grossProfitRateUncertain(rateUncertain);

        List<AiDishProfitDishBrief> poolReliable = allBriefs.stream()
                .filter(x -> soldQtyGtZero(x.getSalesQty()) && !isCostDataIncomplete(x))
                .collect(Collectors.toList());

        Comparator<AiDishProfitDishBrief> byMarginDesc = Comparator.comparingDouble(DishProfitAgentNode::percentSortKeyDesc);
        List<AiDishProfitDishBrief> reliableTop = poolReliable.stream()
                .filter(x -> !isLowMarginOrCostConcern(x))
                .sorted(byMarginDesc.reversed())
                .limit(5)
                .collect(Collectors.toList());
        List<AiDishProfitDishBrief> lowConcern = poolReliable.stream()
                .filter(DishProfitAgentNode::isLowMarginOrCostConcern)
                .sorted(Comparator.comparingDouble(DishProfitAgentNode::percentSortKeyAsc))
                .limit(5)
                .collect(Collectors.toList());
        List<AiDishProfitDishBrief> abnormal = allBriefs.stream()
                .filter(x -> abnormalBrief(x) && !isCostDataIncomplete(x))
                .limit(12)
                .collect(Collectors.toList());

        b.reliableProfitDishes(reliableTop)
                .topProfitDishes(reliableTop)
                .lowProfitDishes(lowConcern)
                .costDataIncompleteDishes(costIncomplete)
                .abnormalDishes(abnormal);

        LinkedHashMap<String, String> recPairs = deriveRecommendations(agg, abnormal.size(), scoped.size(), costIncomplete.size());
        List<String> recList = new ArrayList<>(recPairs.values());
        b.recommendations(recList.isEmpty()
                ? List.of("对成本数据完整的菜品保持稳定出品；对成本缺失菜品先补 BOM 与出库核销，再解读毛利率。", "定期核对标价收入与出库成本，避免账实不一致。")
                : recList);
        String risk = "ok";
        if (agg.revenue.signum() <= 0 || recPairs.containsKey("incomplete")) {
            risk = "data_incomplete";
        } else if (!costIncomplete.isEmpty() || abnormal.size() >= 3 || !lowConcern.isEmpty()) {
            risk = "warning";
        }
        b.riskLevel(risk);

        AiDishProfitOverviewResult out = b.build();
        enrichCoverageOnto(out, cov);
        applyResolvedVisibleStores(out, state);
        if (isLowMarginRankingQuestion(state)) {
            shrinkToLowestMarginRankingPresentation(out, allPeerBriefs, state);
        } else if (isHighActualCostRankingQuestion(state)) {
            shrinkToHighActualCostRankingPresentation(out, allPeerBriefs, state);
        } else if (isLowProfitReasonQuestion(state) && (focusMode || allBriefs.size() == 1)) {
            shrinkToLowProfitReasonPresentation(out, allBriefs, allPeerBriefs, state);
        } else if (isActualOutboundCostOnlyQuestion(state)) {
            shrinkToActualOutboundOnlyPresentation(out, agg, scoped.size(), state);
        }
        return out;
    }

    private static boolean isLowMarginRankingQuestion(AiRunState state) {
        var qi = queryIntentFrom(state);
        return qi != null
                && AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_RANKING_LOW_MARGIN.equals(qi.getStructuredIntentDetail());
    }

    private static boolean isHighActualCostRankingQuestion(AiRunState state) {
        var qi = queryIntentFrom(state);
        return qi != null
                && AiQuerySemanticLexicon.STRUCTURED_DISH_ACTUAL_COST_RANKING_HIGH.equals(qi.getStructuredIntentDetail());
    }

    private static boolean isLowProfitReasonQuestion(AiRunState state) {
        var qi = queryIntentFrom(state);
        return qi != null
                && AiQuerySemanticLexicon.STRUCTURED_DISH_LOW_PROFIT_REASON.equals(qi.getStructuredIntentDetail());
    }

    private static com.nongxinle.ai.context.AiResolvedQueryIntent queryIntentFrom(AiRunState state) {
        AiResolvedQueryContext ctx = state != null ? state.getResolvedQueryContext() : null;
        return ctx != null ? ctx.getQueryIntent() : null;
    }

    private static void shrinkToLowestMarginRankingPresentation(
            AiDishProfitOverviewResult out,
            List<AiDishProfitDishBrief> peerBriefs,
            AiRunState state) {
        if (out == null) {
            return;
        }
        List<AiDishProfitDishBrief> pool = peerBriefs == null ? List.of() : peerBriefs.stream()
                .filter(x -> soldQtyGtZero(x.getSalesQty()) && !isCostDataIncomplete(x))
                .collect(Collectors.toList());
        AiDishProfitDishBrief best = pool.stream()
                .min(Comparator.comparingDouble(DishProfitAgentNode::percentSortKeyAsc))
                .orElse(null);
        if (best == null) {
            out.setSummary("当前可见菜品中，暂无成本口径完整、可比较综合毛利率的明细行。");
        } else {
            String prefix = dishProfitAnswerScopeTimePrefix(out, state);
            String head = prefix.isEmpty() ? "" : prefix;
            String body = String.format(Locale.CHINA,
                    "%s毛利率最低的菜品是「%s」，综合毛利率约 %s，销售额 %s 元，理论成本 %s 元，实际成本 %s 元。",
                    head,
                    nz(best.getDishName()),
                    nzRatePlain(best.getGrossProfitRate()),
                    nz(best.getSalesAmount()),
                    nz(best.getTheoreticalCost()),
                    nz(best.getActualCost()));
            out.setSummary(body.trim());
        }
        clearDishListPanels(out);
        out.setQueryScopeBanner("");
        out.setRecommendations(List.of());
    }

    private static void shrinkToHighActualCostRankingPresentation(
            AiDishProfitOverviewResult out,
            List<AiDishProfitDishBrief> peerBriefs,
            AiRunState state) {
        if (out == null) {
            return;
        }
        List<AiDishProfitDishBrief> pool = peerBriefs == null ? List.of() : peerBriefs.stream()
                .filter(x -> soldQtyGtZero(x.getSalesQty()))
                .sorted(Comparator.comparingDouble((AiDishProfitDishBrief x) -> nzDec(x.getActualCost()).doubleValue())
                        .reversed())
                .collect(Collectors.toList());
        if (pool.isEmpty()) {
            out.setSummary("当前可见菜品中暂无有效销量与实际成本行，无法做「实际成本最高」排行。");
        } else {
            AiDishProfitDishBrief top = pool.get(0);
            String prefix = dishProfitAnswerScopeTimePrefix(out, state);
            String head = prefix.isEmpty() ? "" : prefix;
            String main = String.format(Locale.CHINA,
                    "%s实际出库成本最高的菜品是「%s」，实际成本 %s 元，销量 %s 份，销售额 %s 元，综合毛利率约 %s。",
                    head,
                    nz(top.getDishName()),
                    nz(top.getActualCost()),
                    nz(top.getSalesQty()),
                    nz(top.getSalesAmount()),
                    nzRatePlain(top.getGrossProfitRate()));
            StringBuilder sb = new StringBuilder(main.trim());
            if (pool.size() >= 2) {
                sb.append("\n\n其次是");
                int lim = Math.min(3, pool.size());
                for (int i = 1; i < lim; i++) {
                    AiDishProfitDishBrief x = pool.get(i);
                    if (i > 1) {
                        sb.append("、");
                    }
                    sb.append(String.format(Locale.CHINA, "「%s」%s 元", nz(x.getDishName()), nz(x.getActualCost())));
                }
                sb.append("。");
            }
            out.setSummary(sb.toString().trim());
        }
        clearDishListPanels(out);
        out.setQueryScopeBanner("");
        out.setRecommendations(List.of());
    }

    private static void shrinkToLowProfitReasonPresentation(
            AiDishProfitOverviewResult out,
            List<AiDishProfitDishBrief> focusBriefs,
            List<AiDishProfitDishBrief> peerBriefs,
            AiRunState state) {
        if (out == null || focusBriefs == null || focusBriefs.isEmpty()) {
            return;
        }
        AiDishProfitDishBrief f = focusBriefs.get(0);
        if (isCostDataIncomplete(f)) {
            out.setSummary(String.format(Locale.CHINA,
                    "「%s」成本数据不完整（缺 BOM/出库核销等），暂无法可靠解释毛利原因；请先补齐后再问。",
                    nz(f.getDishName())));
            clearDishListPanels(out);
            out.setQueryScopeBanner("");
            out.setRecommendations(List.of());
            return;
        }
        List<AiDishProfitDishBrief> pool = peerBriefs == null ? List.of() : peerBriefs.stream()
                .filter(x -> soldQtyGtZero(x.getSalesQty()) && !isCostDataIncomplete(x))
                .collect(Collectors.toList());
        long peerSoldN = peerBriefs == null ? 0 : peerBriefs.stream().filter(x -> soldQtyGtZero(x.getSalesQty())).count();
        AiDishProfitDishBrief minMargin = pool.stream()
                .min(Comparator.comparingDouble(DishProfitAgentNode::percentSortKeyAsc))
                .orElse(f);
        boolean lowestAmong = sameDishBrief(f, minMargin);

        BigDecimal gpAmt = nzDec(f.getGrossProfitAmount());
        BigDecimal marginPts = pctToPercentPoints(f.getGrossProfitRate());
        boolean lossLike = gpAmt.signum() < 0 || (marginPts != null && marginPts.compareTo(BigDecimal.ZERO) < 0);

        String prefix = dishProfitAnswerScopeTimePrefix(out, state);
        String scope = prefix.isEmpty() ? "该统计区间" : prefix;

        String share = costShareOfRevenueForDisplay(f);
        BigDecimal theory = nzDec(f.getTheoreticalCost());
        BigDecimal actual = nzDec(f.getActualCost());
        String overUnder;
        if (theory.signum() > 0 && actual.compareTo(theory) < 0) {
            overUnder = "实际成本低于理论成本，说明不是出库超耗导致，更像是定价或配方成本本身偏高。";
        } else if (theory.signum() > 0
                && actual.subtract(theory.multiply(BigDecimal.valueOf(1.01))).signum() > 0) {
            overUnder = "实际成本明显高于理论成本，可优先核对出库与配方；同时关注标价是否偏低。";
        } else {
            overUnder = "可结合标价与 BOM 复核是否定价或配方成本本身偏高。";
        }

        StringBuilder sb = new StringBuilder();
        if (lossLike) {
            sb.append(String.format(Locale.CHINA,
                    "「%s」%s综合毛利率约 %s，销售额 %s 元，理论成本 %s 元，实际成本 %s 元。",
                    nz(f.getDishName()), scope, nzRatePlain(f.getGrossProfitRate()),
                    nz(f.getSalesAmount()), nz(f.getTheoreticalCost()), nz(f.getActualCost())));
        } else if (lowestAmong) {
            sb.append(String.format(Locale.CHINA,
                    "「%s」不是亏损菜；在%s可见的 %d 道有销量菜品里，其综合毛利率最低，约 %s。"
                            + "销售额 %s 元，理论成本 %s 元，实际成本 %s 元。",
                    nz(f.getDishName()),
                    scope,
                    Math.max(1, peerSoldN),
                    nzRatePlain(f.getGrossProfitRate()),
                    nz(f.getSalesAmount()),
                    nz(f.getTheoreticalCost()),
                    nz(f.getActualCost())));
        } else {
            sb.append(String.format(Locale.CHINA,
                    "「%s」不是亏损菜；在%s可见菜品中其综合毛利率约 %s，相对偏低。"
                            + "销售额 %s 元，理论成本 %s 元，实际成本 %s 元。",
                    nz(f.getDishName()),
                    scope,
                    nzRatePlain(f.getGrossProfitRate()),
                    nz(f.getSalesAmount()),
                    nz(f.getTheoreticalCost()),
                    nz(f.getActualCost())));
        }
        if (share != null) {
            sb.append(String.format(Locale.CHINA, "成本占标价收入比例约 %s%%。", share));
        }
        sb.append(overUnder);
        out.setSummary(sb.toString().trim());
        clearDishListPanels(out);
        out.setQueryScopeBanner("");
        out.setRecommendations(List.of());
    }

    private static String costShareOfRevenueForDisplay(AiDishProfitDishBrief f) {
        BigDecimal rev = nzDec(f.getSalesAmount());
        BigDecimal act = nzDec(f.getActualCost());
        if (rev.signum() <= 0) {
            return null;
        }
        return act.divide(rev, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static boolean sameDishBrief(AiDishProfitDishBrief a, AiDishProfitDishBrief b) {
        if (a == null || b == null) {
            return false;
        }
        if (StringUtils.hasText(a.getFoodId()) && StringUtils.hasText(b.getFoodId())) {
            return a.getFoodId().trim().equals(b.getFoodId().trim());
        }
        String na = a.getDishName() == null ? "" : a.getDishName().trim();
        String nb = b.getDishName() == null ? "" : b.getDishName().trim();
        return !na.isEmpty() && na.equals(nb);
    }

    private static String dishProfitAnswerScopeTimePrefix(AiDishProfitOverviewResult out, AiRunState state) {
        String store = firstSingleVisibleStoreLabel(out);
        AiTimeWindowTextFormatter.UserPhrases tw = AiTimeWindowTextFormatter.forAnswer(state);
        String time = tw != null ? nz(tw.getDisplayTimeRange()) : "";
        if (!store.isEmpty() && !time.isEmpty()) {
            return store + time;
        }
        if (!store.isEmpty()) {
            return store;
        }
        if (!time.isEmpty()) {
            return time;
        }
        return "";
    }

    private static void clearDishListPanels(AiDishProfitOverviewResult out) {
        if (out == null) {
            return;
        }
        out.setReliableProfitDishes(List.of());
        out.setLowProfitDishes(List.of());
        out.setTopProfitDishes(List.of());
        out.setCostDataIncompleteDishes(List.of());
        out.setAbnormalDishes(List.of());
    }

    private static boolean isActualOutboundCostOnlyQuestion(AiRunState state) {
        AiResolvedQueryContext ctx = state != null ? state.getResolvedQueryContext() : null;
        var qi = ctx != null ? ctx.getQueryIntent() : null;
        return qi != null
                && AiQuerySemanticLexicon.STRUCTURED_DISH_ACTUAL_OUTBOUND_COST.equals(qi.getStructuredIntentDetail());
    }

    private static int dishRowCountHintFromToolData(Map<String, Object> data) {
        if (data == null) {
            return 0;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> bis = data.get("businessInsightSummary") instanceof Map
                ? (Map<String, Object>) data.get("businessInsightSummary") : null;
        if (bis != null && bis.get("dishRowCount") instanceof Number n) {
            return Math.max(0, n.intValue());
        }
        Object sc = data.get("salesDishCount");
        if (sc instanceof Number n) {
            return Math.max(0, n.intValue());
        }
        Object dlc = data.get("dishLineCountFull");
        if (dlc instanceof Number n) {
            return Math.max(0, n.intValue());
        }
        return 0;
    }

    private static String firstSingleVisibleStoreLabel(AiDishProfitOverviewResult out) {
        if (out == null || out.getVisibleStores() == null || out.getVisibleStores().size() != 1) {
            return "";
        }
        var x = out.getVisibleStores().get(0);
        return x != null && x.getStoreName() != null ? x.getStoreName().trim() : "";
    }

    private static String buildActualOutboundCostOneLiner(
            AiDishProfitOverviewResult out, PortfolioAgg agg, AiTimeWindowTextFormatter.UserPhrases tw) {
        String store = firstSingleVisibleStoreLabel(out);
        String time = tw != null ? nz(tw.getDisplayTimeRange()) : "";
        String amount = stripBd(agg.actual);
        if (!store.isEmpty()) {
            return String.format(Locale.CHINA, "%s%s实际出库成本约 %s 元。", store, time.isEmpty() ? "" : time, amount);
        }
        if (!time.isEmpty()) {
            return String.format(Locale.CHINA, "%s实际出库成本约 %s 元。", time, amount);
        }
        return String.format(Locale.CHINA, "实际出库成本约 %s 元。", amount);
    }

    /** 子意图「实际出库成本」：正文只回答金额，不重复高/低毛利菜清单。 */
    private static void shrinkToActualOutboundOnlyPresentation(
            AiDishProfitOverviewResult out, PortfolioAgg agg, int dishCount, AiRunState state) {
        if (out == null || !isActualOutboundCostOnlyQuestion(state)) {
            return;
        }
        AiTimeWindowTextFormatter.UserPhrases tw = AiTimeWindowTextFormatter.forAnswer(state);
        out.setSummary(buildActualOutboundCostOneLiner(out, agg, tw));
        out.setReliableProfitDishes(List.of());
        out.setLowProfitDishes(List.of());
        out.setTopProfitDishes(List.of());
        out.setCostDataIncompleteDishes(List.of());
        out.setAbnormalDishes(List.of());
        out.setRecommendations(List.of("如需菜品毛利总览与排行，可再问「菜品毛利怎么样」。"));
        if (dishCount >= 0) {
            out.setDishCount(dishCount);
        }
    }

    private static Map<String, Object> coverageFromToolEnvelope(Map<String, Object> data) {
        Object o = data == null ? null : data.get("dishProfitStoreCoverage");
        if (!(o instanceof Map)) {
            return Map.of();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> cast = (Map<String, Object>) o;
        return cast;
    }

    private static String[] deriveScopePresentation(AiRunState state) {
        if (BusinessToolExecutionNode.shouldRouteGroupWideDishInsight(state)) {
            return new String[]{AiOrgScopeResolver.SCOPE_GROUP, "集团范围"};
        }
        AiOrgScope os = state.getAiOrgScope();
        if (os != null && os.getScopeType() != null && !os.getScopeType().isBlank()) {
            String t = os.getScopeType().trim();
            return new String[]{t, humanizeScopeType(t)};
        }
        return new String[]{AiOrgScopeResolver.SCOPE_STORE, "门店范围"};
    }

    private static String humanizeScopeType(String t) {
        if (AiOrgScopeResolver.SCOPE_GROUP.equals(t)) {
            return "集团范围";
        }
        if (AiOrgScopeResolver.SCOPE_REGION.equals(t)) {
            return "区域范围";
        }
        if (AiOrgScopeResolver.SCOPE_STORE.equals(t)) {
            return "门店范围";
        }
        return "组织范围";
    }

    private static String buildQueryScopeOpening(AiRunState state, Map<String, Object> cov) {
        StringBuilder sb = new StringBuilder();
        String single = buildSingleStoreScopeBanner(state);
        if (!single.isEmpty()) {
            sb.append(single.trim());
        }
        String groupBanner = buildGroupResolvedStoreBanner(state, cov);
        if (!groupBanner.isEmpty()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(groupBanner.trim());
        }
        String participation = appendStoreParticipationDetail(state, cov);
        if (!participation.isEmpty()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(participation.trim());
        }
        return sb.toString().trim();
    }

    /** 单店解析范围：与 {@link AiResolvedOrgScope#SCOPE_STORE} + 唯一可见门店对齐；勿用集团抬头。 */
    private static String buildSingleStoreScopeBanner(AiRunState state) {
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        if (rq == null || rq.getOrgScope() == null) {
            return "";
        }
        AiResolvedOrgScope org = rq.getOrgScope();
        if (!AiResolvedOrgScope.SCOPE_STORE.equals(org.getScopeType())) {
            return "";
        }
        if (org.getVisibleStores() == null || org.getVisibleStores().size() != 1) {
            return "";
        }
        AiStoreScopeDTO s0 = org.getVisibleStores().get(0);
        if (s0 == null) {
            return "";
        }
        String name = s0.getStoreName();
        if (name == null || name.isBlank()) {
            return "下面按当前门店范围汇总菜品利润。";
        }
        return String.format(Locale.CHINA, "下面按「%s」门店范围汇总菜品利润。", name.trim());
    }

    /** 集团抬头：仅在解析范围为 {@link AiResolvedOrgScope#SCOPE_GROUP} 时使用（追问单店时勿套集团话术）。 */
    private static String buildGroupResolvedStoreBanner(AiRunState state, Map<String, Object> cov) {
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        if (rq == null || rq.getOrgScope() == null
                || !AiResolvedOrgScope.SCOPE_GROUP.equals(rq.getOrgScope().getScopeType())) {
            return "";
        }
        if (!BusinessToolExecutionNode.shouldRouteGroupWideBusinessOverview(state)) {
            return "";
        }
        List<AiStoreScopeDTO> stores = rq.getOrgScope().getVisibleStores();
        if (stores == null || stores.isEmpty()) {
            int vc = intFromMap(cov, "visibleStoreCount");
            if (vc > 0) {
                return String.format(Locale.CHINA,
                        "你当前可查看集团范围，本次统计范围内共 %d 家门店。下面按集团范围汇总菜品利润。", vc);
            }
            return "你当前可查看集团范围。下面按集团范围汇总菜品利润。";
        }
        List<String> names = new ArrayList<>();
        for (AiStoreScopeDTO s : stores) {
            if (s != null && s.getStoreName() != null && !s.getStoreName().isBlank()) {
                names.add(s.getStoreName().trim());
            }
        }
        int n = stores.size();
        if (names.isEmpty()) {
            return String.format(Locale.CHINA, "你当前可查看集团范围，本次识别到 %d 家门店。下面按集团范围汇总菜品利润。", n);
        }
        return String.format(Locale.CHINA, "你当前可查看集团范围，本次识别到 %d 家门店：%s。下面按集团范围汇总菜品利润。",
                n, String.join("、", names));
    }

    private static String appendStoreParticipationDetail(AiRunState state, Map<String, Object> cov) {
        if (cov == null || cov.isEmpty()) {
            return "";
        }
        int vis = intFromMap(cov, "visibleStoreCount");
        boolean multi = BusinessToolExecutionNode.shouldRouteGroupWideDishInsight(state) || vis > 1;
        if (!multi) {
            return "";
        }
        List<String> coveredNames = storeNamesFromCovList(cov.get("coveredStores"));
        List<String> missingNames = storeNamesFromCovList(cov.get("dataMissingStores"));
        if (coveredNames.isEmpty() && missingNames.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (!coveredNames.isEmpty()) {
            sb.append("本次参与统计的门店：").append(String.join("、", coveredNames)).append("。");
        }
        if (!missingNames.isEmpty()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append("暂无菜品销售数据的门店：").append(String.join("、", missingNames)).append("。");
        }
        return sb.toString().trim();
    }

    private static List<String> storeNamesFromCovList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        LinkedHashSet<String> uniq = new LinkedHashSet<>();
        for (Object o : list) {
            if (o instanceof Map<?, ?> row) {
                Object sn = row.get("storeName");
                if (sn != null && !sn.toString().isBlank()) {
                    uniq.add(sn.toString().trim());
                }
            }
        }
        return new ArrayList<>(uniq);
    }

    private static List<AiOverviewVisibleStoreItem> visibleStoresFromResolved(AiRunState state) {
        if (!BusinessToolExecutionNode.shouldRouteGroupWideBusinessOverview(state)) {
            return List.of();
        }
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        if (rq == null || rq.getOrgScope() == null) {
            return List.of();
        }
        List<AiStoreScopeDTO> vs = rq.getOrgScope().getVisibleStores();
        if (vs == null || vs.isEmpty()) {
            return List.of();
        }
        List<AiOverviewVisibleStoreItem> out = new ArrayList<>(vs.size());
        for (AiStoreScopeDTO s : vs) {
            if (s == null) {
                continue;
            }
            out.add(AiOverviewVisibleStoreItem.builder()
                    .storeDepartmentId(s.getStoreDepartmentId())
                    .storeName(s.getStoreName() != null ? s.getStoreName().trim() : "")
                    .build());
        }
        return out;
    }

    private static void applyResolvedVisibleStores(AiDishProfitOverviewResult r, AiRunState state) {
        if (r == null) {
            return;
        }
        List<AiOverviewVisibleStoreItem> vis = visibleStoresFromResolved(state);
        if (!vis.isEmpty()) {
            r.setVisibleStores(vis);
            r.setVisibleStoreCount(vis.size());
        }
    }

    private static void enrichCoverageOnto(AiDishProfitOverviewResult r, Map<String, Object> cov) {
        if (r == null || cov == null || cov.isEmpty()) {
            return;
        }
        r.setVisibleStoreCount(intFromMap(cov, "visibleStoreCount"));
        r.setDataAvailableStoreCount(intFromMap(cov, "dataAvailableStoreCount"));
        r.setDataMissingStoreCount(intFromMap(cov, "dataMissingStoreCount"));
        r.setCoveredStores(mapCovVisibleStores(cov.get("coveredStores")));
        r.setDataMissingStores(mapCovMissingStores(cov.get("dataMissingStores")));
    }

    private static int intFromMap(Map<String, Object> m, String key) {
        if (m == null) {
            return 0;
        }
        Object v = m.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v == null) {
            return 0;
        }
        try {
            return new BigDecimal(v.toString().trim()).intValue();
        } catch (Exception e) {
            return 0;
        }
    }

    private static List<AiOverviewVisibleStoreItem> mapCovVisibleStores(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<AiOverviewVisibleStoreItem> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map<?, ?> row) {
                Object sn = row.get("storeName");
                Long lid = null;
                Object sid = row.get("storeDepartmentId");
                if (sid instanceof Number n) {
                    lid = n.longValue();
                } else if (sid != null) {
                    try {
                        lid = Long.parseLong(sid.toString().trim());
                    } catch (NumberFormatException ignored) {
                        lid = null;
                    }
                }
                out.add(AiOverviewVisibleStoreItem.builder()
                        .storeDepartmentId(lid)
                        .storeName(sn == null ? "" : sn.toString().trim())
                        .build());
            }
        }
        return out;
    }

    private static List<AiOverviewStoreIssueItem> mapCovMissingStores(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<AiOverviewStoreIssueItem> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map<?, ?> row) {
                Object sn = row.get("storeName");
                Object reason = row.get("reason");
                Long lid = null;
                Object sid = row.get("storeDepartmentId");
                if (sid instanceof Number n) {
                    lid = n.longValue();
                } else if (sid != null) {
                    try {
                        lid = Long.parseLong(sid.toString().trim());
                    } catch (NumberFormatException ignored) {
                        lid = null;
                    }
                }
                out.add(AiOverviewStoreIssueItem.builder()
                        .storeDepartmentId(lid)
                        .storeName(sn == null ? "" : sn.toString().trim())
                        .reason(reason == null ? "" : reason.toString().trim())
                        .build());
            }
        }
        return out;
    }

    private static List<Map<String, Object>> dedupeDishRowsByFoodIdOrName(List<Map<String, Object>> rows) {
        if (rows == null || rows.size() <= 1) {
            return rows;
        }
        LinkedHashMap<String, Map<String, Object>> by = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            Object fid = r.get("foodId");
            String key;
            if (fid != null) {
                String fs = fid.toString().trim();
                if (!fs.isEmpty()) {
                    key = "id:" + fs;
                } else {
                    key = "n:" + stringify(r.get("dishName")).trim();
                }
            } else {
                key = "n:" + stringify(r.get("dishName")).trim();
            }
            by.putIfAbsent(key.isEmpty() ? "row:" + by.size() : key, r);
        }
        return new ArrayList<>(by.values());
    }

    private static List<Map<String, Object>> extractDishRows(Map<String, Object> data) {
        Object raw = data.get("dishRows");
        if (!(raw instanceof List)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : (List<?>) raw) {
            if (item instanceof Map) {
                out.add((Map<String, Object>) item);
            }
        }
        return out;
    }

    private static String toolFailedOrEmpty(boolean ok) {
        if (!ok) {
            return "菜品毛利透视工具执行失败或未成功返回数据（请稍后在网络与权限正常时重试）。";
        }
        return "未识别到可统计菜品行（请确认菜谱维护与区间内销量口径）。";
    }

    private static String toolsReturnedNoRows(AiRunState state) {
        return "本轮工具未返回结构化菜品明细行；若确实有销量数据，多半是配方/BOM或出库核销未齐备，请先补齐。"
                + (nz(state.getStatStartDate()).isEmpty() ? "" : "（区间：" + state.getStatStartDate() + "～" + nz(state.getStatEndDate()) + "）");
    }

    private static PortfolioAgg summarizePortfolio(List<Map<String, Object>> rows, Map<String, Object> toolData,
            boolean focusMode) {
        PortfolioAgg agg = new PortfolioAgg();
        @SuppressWarnings("unchecked")
        Map<String, Object> bis = toolData == null ? null : (Map<String, Object>) toolData.get("businessInsightSummary");
        if (!focusMode && bis != null) {
            agg.revenue = moneyFromSummaryField(bis.get("totalListPriceRevenue"));
            agg.theory = moneyFromSummaryField(bis.get("totalTheoryCostAmount"));
            agg.actual = moneyFromSummaryField(bis.get("totalActualCostAmount"));
            agg.profitAmt = agg.revenue.subtract(agg.actual).setScale(2, RoundingMode.HALF_UP);
            String blended = stringify(bis.get("blendedGrossMarginRateOnListPrice"));
            agg.portfolioRate = formatPercentTokenForDisplay(blended);
            return agg;
        }
        for (Map<String, Object> row : rows) {
            agg.revenue = agg.revenue.add(dec(row.get("listPriceRevenue")));
            agg.theory = agg.theory.add(dec(row.get("theoryCostAmount")));
            agg.actual = agg.actual.add(dec(row.get("actualCostAmount")));
        }
        agg.profitAmt = agg.revenue.subtract(agg.actual).setScale(2, RoundingMode.HALF_UP);
        if (rows.size() == 1) {
            Map<String, Object> r = rows.get(0);
            String blended = stringify(r.get("blendedGrossMarginRateOnListPrice"));
            if (blended.isEmpty()) {
                blended = stringify(r.get("grossMarginRateOnListPrice"));
            }
            agg.portfolioRate = formatPercentTokenForDisplay(blended);
        } else if (agg.revenue.signum() <= 0) {
            agg.portfolioRate = agg.revenue.signum() == 0 ? "暂不适用（无标价收入汇总）" : "暂无";
        } else {
            BigDecimal pct = agg.profitAmt.divide(agg.revenue, 8, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
            agg.portfolioRate = pct.stripTrailingZeros().toPlainString() + "%";
        }
        return agg;
    }

    private static BigDecimal moneyFromSummaryField(Object raw) {
        if (raw == null) {
            return BigDecimal.ZERO;
        }
        String s = raw.toString().trim();
        if (s.isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(s);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private static String formatPercentTokenForDisplay(String raw) {
        if (raw == null || raw.isBlank() || "暂无".equals(raw)) {
            return "暂无";
        }
        String t = raw.trim();
        return t.contains("%") ? t : t + "%";
    }

    private static String buildSummarySentence(PortfolioAgg agg, boolean focusMode, int n, boolean rateUncertain,
            AiTimeWindowTextFormatter.UserPhrases tw) {
        AiTimeWindowTextFormatter.UserPhrases p =
                tw != null ? tw : AiTimeWindowTextFormatter.fromIsoRange(null, null, null);
        if (agg.revenue.signum() <= 0 && n > 0) {
            return p.getDisplayTimeRange()
                    + "，有菜品行但菜品标价销售额汇总约为 0，综合毛利率不适用；请核对售价与区间销量是否正确维护。";
        }
        String tail = "";
        if (rateUncertain && agg.revenue.signum() > 0) {
            tail = " 由于部分菜品缺少完整配方/BOM 或出库核销记录，上述综合毛利率为按当前可取得成本的粗算参考，不能作为最终可审计的毛利结论。";
        }
        if (focusMode && n <= 32) {
            return String.format(Locale.CHINA,
                    "已聚焦匹配到的 %d 道菜品（和你问的焦点一致）；菜品标价收入约 %s 元，毛利额（菜品标价收入−实际出库成本汇总）约 %s 元，综合毛利率（按标价与实际出库成本）约 %s。%s",
                    n, stripBd(agg.revenue), stripBd(agg.profitAmt), nzRatePlain(agg.portfolioRate), tail).trim();
        }
        return String.format(Locale.CHINA,
                "本轮共透视 %d 道菜品参与汇总；菜品标价销售额约 %s 元，理论成本约 %s 元，实际出库成本约 %s 元，毛利额约 %s 元，综合毛利率（按标价与实际出库成本）约 %s。%s",
                n, stripBd(agg.revenue), stripBd(agg.theory), stripBd(agg.actual), stripBd(agg.profitAmt),
                nzRatePlain(agg.portfolioRate), tail).trim();
    }

    private static String nzRatePlain(String r) {
        return r == null ? "暂不适用" : r;
    }

    private static LinkedHashMap<String, String> deriveRecommendations(PortfolioAgg agg, int abnormalCt, int n,
            int costIncompleteCt) {
        LinkedHashMap<String, String> rec = new LinkedHashMap<>();
        if (agg.revenue.signum() <= 0) {
            rec.put("incomplete", "若有销量但售价为 0 或未维护，毛利率不可信；请先完善菜单标价与出库核销。");
        }
        if (costIncompleteCt > 0) {
            rec.put("costGaps", String.format(Locale.CHINA,
                    "有 %d 道菜品成本数据不完整（缺 BOM/出库核销等），其表面毛利率（如 100%%）不可靠；请补齐后再纳入「毛利较好」评价。", costIncompleteCt));
        }
        if (abnormalCt > 0) {
            rec.put("outboundGap", String.format(Locale.CHINA,
                    "%d 道菜的实际出库摊销相对理论有明显放大，优先核对 BOM 与实际投料。", abnormalCt));
        }
        if (n >= 5 && agg.profitAmt.signum() > 0 && abnormalCt < 8) {
            rec.put("ops", "对「销量不低但毛利靠后」的菜做减法：调价、减量、原料替代或减少促销力度。");
        }
        rec.putIfAbsent("generic", "建议固定周期比对「标价收入 − 出库成本」与日营收回款，避免出现账实不一致。");
        return rec;
    }

    private static AiDishProfitDishBrief briefFromRow(Map<String, Object> row) {
        String name = stringify(row.get("dishName"));
        Object fidRaw = row.get("foodId");
        String foodIdStr = fidRaw == null ? null : fidRaw.toString().trim();
        if (foodIdStr != null && foodIdStr.isEmpty()) {
            foodIdStr = null;
        }
        BigDecimal rev = dec(row.get("listPriceRevenue"));
        BigDecimal theory = dec(row.get("theoryCostAmount"));
        BigDecimal actual = dec(row.get("actualCostAmount"));
        AiDishProfitDishBrief b = AiDishProfitDishBrief.builder()
                .foodId(foodIdStr)
                .dishName(name)
                .salesQty(stringify(row.get("soldPortionsTotal")))
                .salesAmount(stripBdRound(rev, 2))
                .theoreticalCost(stripBdRound(theory, 2))
                .actualCost(stripBdRound(actual, 2))
                .grossProfitAmount(stripBdRound(rev.subtract(actual), 2))
                .build();

        String rate = stringify(row.get("blendedGrossMarginRateOnListPrice"));
        if (rate.isEmpty()) {
            rate = stringify(row.get("grossMarginRateOnListPrice"));
        }
        b.setGrossProfitRate(rate.isEmpty() ? "暂无" : rate);

        List<String> mains = new ArrayList<>();
        if (theory.signum() <= 0 && actual.signum() <= 0) {
            mains.add("配方或出库核销未齐备，主料拆解暂不可用（见明细报表）。");
            b.setRiskReason("暂无完整配方/BOM或出库核销，单项毛利率可能不可靠");
        } else if (theory.signum() > 0 && actual.compareTo(theory.multiply(BigDecimal.valueOf(1.1))) > 0) {
            mains.add("实际出库成本高于配方理论口径");
            b.setRiskReason("实际成本明显高于理论用量成本，建议核对出库与配方");
        } else {
            b.setRiskReason("");
        }
        b.setMainCostItems(mains.isEmpty() ? List.of("主料摊销明细见出库成本报表，此处不累述") : mains);

        return b;
    }

    private static boolean abnormalBrief(AiDishProfitDishBrief b) {
        if (!soldQtyGtZero(b.getSalesQty())) {
            return false;
        }
        BigDecimal pct = pctToPercentPoints(b.getGrossProfitRate());
        if (pct != null && pct.compareTo(BigDecimal.valueOf(12)) < 0) {
            return true;
        }
        BigDecimal theory = nzDec(b.getTheoreticalCost());
        BigDecimal actual = nzDec(b.getActualCost());
        return theory.signum() > 0 && actual.subtract(theory.multiply(BigDecimal.valueOf(1.1))).signum() > 0;
    }

    /** 无完整 BOM/无出库成本等：表面毛利率（含 100%）不可信，不可列入「毛利较好」。 */
    private static boolean isCostDataIncomplete(AiDishProfitDishBrief b) {
        if (b == null || !soldQtyGtZero(b.getSalesQty())) {
            return false;
        }
        BigDecimal theory = nzDec(b.getTheoreticalCost());
        BigDecimal actual = nzDec(b.getActualCost());
        if (theory.signum() <= 0 && actual.signum() <= 0) {
            return true;
        }
        String rr = b.getRiskReason();
        if (rr != null && (rr.contains("不可靠") || rr.contains("未齐备") || rr.contains("不完整"))) {
            return true;
        }
        BigDecimal pct = pctToPercentPoints(b.getGrossProfitRate());
        if (pct != null && pct.compareTo(BigDecimal.valueOf(99.5)) >= 0
                && theory.signum() <= 0 && actual.signum() <= 0) {
            return true;
        }
        return false;
    }

    /** 低毛利或实际成本显著高于理论（仅在意成本口径已相对完整的菜）。 */
    private static boolean isLowMarginOrCostConcern(AiDishProfitDishBrief b) {
        if (b == null || !soldQtyGtZero(b.getSalesQty()) || isCostDataIncomplete(b)) {
            return false;
        }
        BigDecimal pct = pctToPercentPoints(b.getGrossProfitRate());
        if (pct != null && pct.compareTo(BigDecimal.valueOf(18)) < 0) {
            return true;
        }
        BigDecimal theory = nzDec(b.getTheoreticalCost());
        BigDecimal actual = nzDec(b.getActualCost());
        return theory.signum() > 0 && actual.subtract(theory.multiply(BigDecimal.valueOf(1.1))).signum() > 0;
    }

    /** 低毛利列表：按毛利率升序（最差在前）；不可解析的排在后面。 */
    private static double percentSortKeyAsc(AiDishProfitDishBrief brief) {
        BigDecimal pct = pctToPercentPoints(brief.getGrossProfitRate());
        if (pct != null) {
            return pct.doubleValue();
        }
        return 1_000_000d;
    }

    private static BigDecimal pctToPercentPoints(String rateStr) {
        if (rateStr == null || rateStr.isBlank() || "暂无".equals(rateStr) || rateStr.startsWith("暂")) {
            return null;
        }
        try {
            String s = rateStr.trim().replace("%", "").replace("％", "");
            return new BigDecimal(s);
        } catch (Exception e) {
            return null;
        }
    }

    /** 从高到低比较的键：百分比 points（如 "22.05%"→22.05）；不可解析则用 -Infinity。 */
    private static double percentSortKeyDesc(AiDishProfitDishBrief brief) {
        BigDecimal pct = pctToPercentPoints(brief.getGrossProfitRate());
        if (pct != null) {
            return pct.doubleValue();
        }
        return Double.NEGATIVE_INFINITY;
    }

    private static boolean soldQtyGtZero(String q) {
        try {
            if (q == null || q.isBlank()) {
                return false;
            }
            return new BigDecimal(q.trim()).compareTo(BigDecimal.ZERO) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static List<String> emptyRecs() {
        ArrayList<String> r = new ArrayList<>();
        r.add("先确认区间内菜品销量与出库是否齐备；再在「菜谱 / 菜品成本」补齐配方。");
        r.add("数据齐全后重申「菜品毛利」，即可分层看到高毛利、低毛利与异常菜。");
        return r;
    }

    /**
     * 根据用户话术收窄菜品列表；若不匹配任一规则则返回空列表（外层回退全集）。
     */
    private static List<Map<String, Object>> narrowByUserHint(List<Map<String, Object>> rows, String hint) {
        if (rows.isEmpty()) {
            return List.of();
        }
        String needle = hint.replace(" ", "");
        if (needle.isEmpty()) {
            return List.of();
        }

        Matcher m = BEFORE_MAO.matcher(needle);
        if (m.find()) {
            String k = m.group(1);
            List<Map<String, Object>> hits = rows.stream()
                    .filter(row -> stringify(row.get("dishName")).contains(k))
                    .collect(Collectors.toList());
            if (!hits.isEmpty()) {
                return hits;
            }
        }
        String dn = longestNameContained(rows, needle);
        if (!dn.isEmpty()) {
            final String bk = dn;
            List<Map<String, Object>> eq = rows.stream()
                    .filter(row -> bk.equalsIgnoreCase(stringify(row.get("dishName"))))
                    .collect(Collectors.toList());
            if (!eq.isEmpty()) {
                return eq;
            }
            List<Map<String, Object>> pref = rows.stream()
                    .filter(row -> stringify(row.get("dishName")).contains(bk))
                    .collect(Collectors.toList());
            if (!pref.isEmpty()) {
                return pref;
            }
        }
        return List.of();
    }

    private static String longestNameContained(List<Map<String, Object>> rows, String needle) {
        String best = "";
        for (Map<String, Object> row : rows) {
            String name = stringify(row.get("dishName"));
            if (!name.isEmpty() && needle.contains(name) && name.length() > best.length()) {
                best = name;
            }
        }
        return best;
    }

    private static Map<String, Object> toolEnvelopeData(AiRunState state, String toolKey) {
        Object env = state.getToolResults().get(toolKey);
        if (!(env instanceof Map)) {
            return Map.of();
        }
        Object nested = ((Map<String, Object>) env).get("data");
        if (!(nested instanceof Map)) {
            return Map.of();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> cast = (Map<String, Object>) nested;
        return cast;
    }

    private static boolean toolSuccess(AiRunState state, String toolKey) {
        Object env = state.getToolResults().get(toolKey);
        if (!(env instanceof Map)) {
            return false;
        }
        return Boolean.TRUE.equals(((Map<?, ?>) env).get("success"));
    }

    private static BigDecimal dec(Object v) {
        return GbDepartmentGoodsStockReduceSupport.coerceDecimal(v);
    }

    private static String stripBd(BigDecimal bd) {
        if (bd == null) {
            return "0";
        }
        return bd.stripTrailingZeros().toPlainString();
    }

    private static String stripBdRound(BigDecimal bd, int scale) {
        if (bd == null) {
            return "0";
        }
        return bd.setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static BigDecimal nzDec(String s) {
        if (s == null || s.isBlank() || "暂无".equals(s)) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(s.trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private static String stringify(Object v) {
        return v == null ? "" : v.toString().trim();
    }

    private static String nz(Object v) {
        return stringify(v);
    }

    private static final class PortfolioAgg {
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal theory = BigDecimal.ZERO;
        BigDecimal actual = BigDecimal.ZERO;
        BigDecimal profitAmt = BigDecimal.ZERO;
        String portfolioRate = "";
    }
}
