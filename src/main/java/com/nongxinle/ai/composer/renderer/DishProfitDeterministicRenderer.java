package com.nongxinle.ai.composer.renderer;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.AiDishProfitDishBrief;
import com.nongxinle.ai.dto.business.AiDishProfitOverviewResult;
import com.nongxinle.ai.dto.business.DishProfitAnswerPlan;
import com.nongxinle.ai.util.AiTimeWindowTextFormatter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public final class DishProfitDeterministicRenderer {

    public String renderDishProfitFallback(AiDishProfitOverviewResult r, AiRunState state) {
        return shortFallbackDishProfit(r, state);
    }

    /**
     * 仅宣读 {@link DishProfitAnswerPlan} 已定稿的 focus/secondary 行（如经营概览 MultiAgent 汇总中的一句菜品侧摘要）。
     */
    public String renderAnswerPlanOneLiner(DishProfitAnswerPlan plan) {
        String s = composeDishProfitDeterministicFromAnswerPlan(plan);
        return s != null ? s.trim() : "";
    }

    /**
     * Harness：排行/原因类已有 {@link DishProfitAnswerPlan} 时，只宣读 {@code focusRows[0]}（及可选 {@code secondaryRows[0]}），
     * 不读取工具 summary、不排序、不算术。
     */
    private static String composeDishProfitDeterministicFromAnswerPlan(DishProfitAnswerPlan plan) {
        if (plan == null || plan.getFocusRows() == null || plan.getFocusRows().isEmpty()) {
            return null;
        }
        Map<String, Object> row = plan.getFocusRows().get(0);
        if (row == null) {
            return null;
        }
        String type = plan.getPlanType() != null ? plan.getPlanType().trim() : "";
        if (DishProfitAnswerPlan.TYPE_DISH_LOWEST_MARGIN.equals(type)) {
            StringBuilder sb = new StringBuilder(DiagnosisDeterministicRenderer.lowestMarginDiagnosisFallbackLine(row));
            appendOptionalSecondaryContrast(sb, plan);
            return sb.toString().trim();
        }
        if (DishProfitAnswerPlan.TYPE_DISH_PROFIT_REASON.equals(type)) {
            return composeDishProfitReasonFromAnswerPlanRow(row, plan);
        }
        if (DishProfitAnswerPlan.TYPE_DISH_HIGHEST_ACTUAL_COST.equals(type)) {
            return composeDishHighestActualCostFromAnswerPlanRow(row, plan);
        }
        if (DishProfitAnswerPlan.TYPE_DISH_COST_GAP.equals(type)) {
            return composeDishCostGapFromAnswerPlanRow(row, plan);
        }
        if (DishProfitAnswerPlan.TYPE_DISH_THEORETICAL_COST.equals(type)) {
            return composeDishTheoreticalCostFromAnswerPlanRow(row, plan);
        }
        if (DishProfitAnswerPlan.TYPE_DISH_ACTUAL_OUTBOUND_COST.equals(type)) {
            return composeDishActualOutboundFromAnswerPlanRow(row, plan);
        }
        if (DishProfitAnswerPlan.TYPE_DISH_PROFIT_RATE.equals(type)) {
            return composeDishProfitRateFromAnswerPlanRow(row, plan);
        }
        return composeDishProfitGenericFocusRowFromAnswerPlan(row, plan);
    }

    private static String composeDishProfitReasonFromAnswerPlanRow(Map<String, Object> row, DishProfitAnswerPlan plan) {
        String name = DeterministicRendererSupport.nz(row.get("dishName"));
        if (name.isEmpty()) {
            name = "该菜品";
        }
        String core = DiagnosisDeterministicRenderer.buildDiagnosisDishDragEvidenceSentenceStub(name, row);
        String prefix = "拖累毛利最明显的是" + name;
        String body = core;
        if (body.startsWith(prefix)) {
            body = body.substring(prefix.length());
            if (body.startsWith("，")) {
                body = body.substring(1);
            }
            body = body.trim();
        }
        StringBuilder sb = new StringBuilder("「").append(name).append("」毛利偏低");
        if (!body.isBlank()) {
            sb.append("：").append(body);
        }
        sb.append("。建议核对出库、配方和售价。");
        appendOptionalSecondaryContrast(sb, plan);
        return sb.toString().trim();
    }

    private static String composeDishHighestActualCostFromAnswerPlanRow(Map<String, Object> row,
            DishProfitAnswerPlan plan) {
        String name = DeterministicRendererSupport.nz(row.get("dishName"));
        if (name.isEmpty()) {
            name = "该菜品";
        }
        StringBuilder sb = new StringBuilder("实际成本最高的是「").append(name).append("」");
        String ac = DiagnosisDeterministicRenderer.diagnosisFmtYuan(DiagnosisDeterministicRenderer.r0Get(row, "actualCostAmount"));
        String ac123 = DiagnosisDeterministicRenderer.diagnosisFmtYuan(DiagnosisDeterministicRenderer.r0Get(row, "actualCostTotalAmount123"));
        String primaryAc = DiagnosisDeterministicRenderer.diagnosisDetailPresent(ac) ? ac : ac123;
        if (DiagnosisDeterministicRenderer.diagnosisDetailPresent(primaryAc)) {
            sb.append("，实际成本").append(primaryAc).append("元");
        }
        String th = DiagnosisDeterministicRenderer.diagnosisFmtYuan(DiagnosisDeterministicRenderer.r0Get(row, "theoryCostAmount"));
        if (DiagnosisDeterministicRenderer.diagnosisDetailPresent(th)) {
            sb.append("，理论成本").append(th).append("元");
        }
        String rate = DiagnosisDeterministicRenderer.diagnosisFmtPercent(DiagnosisDeterministicRenderer.r0Get(row, "blendedGrossMarginRateOnListPrice"));
        if (DiagnosisDeterministicRenderer.diagnosisDetailPresent(rate)) {
            sb.append("，综合毛利率约").append(rate);
        }
        String reason = DeterministicRendererSupport.nz(row.get("riskReason"));
        if (!reason.isEmpty()) {
            sb.append("。").append(reason);
        } else {
            sb.append("。");
        }
        appendOptionalSecondaryContrast(sb, plan);
        return sb.toString().trim();
    }

    private static String composeDishCostGapFromAnswerPlanRow(Map<String, Object> row, DishProfitAnswerPlan plan) {
        String name = DeterministicRendererSupport.nz(row.get("dishName"));
        if (name.isEmpty()) {
            name = "该菜品";
        }
        StringBuilder sb = new StringBuilder("理论与实际成本差异最大的是「").append(name).append("」");
        String diff = DiagnosisDeterministicRenderer.diagnosisFmtYuan(DiagnosisDeterministicRenderer.r0Get(row, "diffCostAmount"));
        if (DiagnosisDeterministicRenderer.diagnosisDetailPresent(diff)) {
            sb.append("，成本差额约").append(diff).append("元");
        }
        String th = DiagnosisDeterministicRenderer.diagnosisFmtYuan(DiagnosisDeterministicRenderer.r0Get(row, "theoryCostAmount"));
        String ac = DiagnosisDeterministicRenderer.diagnosisFmtYuan(DiagnosisDeterministicRenderer.r0Get(row, "actualCostAmount"));
        if (DiagnosisDeterministicRenderer.diagnosisDetailPresent(th)) {
            sb.append("，理论成本").append(th).append("元");
        }
        if (DiagnosisDeterministicRenderer.diagnosisDetailPresent(ac)) {
            sb.append("，实际成本").append(ac).append("元");
        }
        String rate = DiagnosisDeterministicRenderer.diagnosisFmtPercent(DiagnosisDeterministicRenderer.r0Get(row, "blendedGrossMarginRateOnListPrice"));
        if (DiagnosisDeterministicRenderer.diagnosisDetailPresent(rate)) {
            sb.append("，综合毛利率约").append(rate);
        }
        String reason = DeterministicRendererSupport.nz(row.get("riskReason"));
        if (!reason.isEmpty()) {
            sb.append("。").append(reason);
        } else {
            sb.append("。");
        }
        appendOptionalSecondaryContrast(sb, plan);
        return sb.toString().trim();
    }

    private static String composeDishTheoreticalCostFromAnswerPlanRow(Map<String, Object> row,
            DishProfitAnswerPlan plan) {
        String name = DeterministicRendererSupport.nz(row.get("dishName"));
        if (name.isEmpty()) {
            name = "该菜品";
        }
        StringBuilder sb = new StringBuilder("理论成本最高的是「").append(name).append("」");
        String th = DiagnosisDeterministicRenderer.diagnosisFmtYuan(DiagnosisDeterministicRenderer.r0Get(row, "theoryCostAmount"));
        if (DiagnosisDeterministicRenderer.diagnosisDetailPresent(th)) {
            sb.append("，理论成本").append(th).append("元");
        }
        String ac = DiagnosisDeterministicRenderer.diagnosisFmtYuan(DiagnosisDeterministicRenderer.r0Get(row, "actualCostAmount"));
        if (DiagnosisDeterministicRenderer.diagnosisDetailPresent(ac)) {
            sb.append("，实际成本").append(ac).append("元");
        }
        String rate = DiagnosisDeterministicRenderer.diagnosisFmtPercent(DiagnosisDeterministicRenderer.r0Get(row, "blendedGrossMarginRateOnListPrice"));
        if (DiagnosisDeterministicRenderer.diagnosisDetailPresent(rate)) {
            sb.append("，综合毛利率约").append(rate);
        }
        String reason = DeterministicRendererSupport.nz(row.get("riskReason"));
        if (!reason.isEmpty()) {
            sb.append("。").append(reason);
        } else {
            sb.append("。");
        }
        appendOptionalSecondaryContrast(sb, plan);
        return sb.toString().trim();
    }

    private static String composeDishActualOutboundFromAnswerPlanRow(Map<String, Object> row,
            DishProfitAnswerPlan plan) {
        String name = DeterministicRendererSupport.nz(row.get("dishName"));
        if (name.isEmpty()) {
            name = "该菜品";
        }
        StringBuilder sb = new StringBuilder("实际出库成本最高的是「").append(name).append("」");
        String ob = DiagnosisDeterministicRenderer.diagnosisFmtYuan(DiagnosisDeterministicRenderer.r0Get(row, "actualCostTotalAmount123"));
        String ac = DiagnosisDeterministicRenderer.diagnosisFmtYuan(DiagnosisDeterministicRenderer.r0Get(row, "actualCostAmount"));
        String primary = DiagnosisDeterministicRenderer.diagnosisDetailPresent(ob) ? ob : ac;
        if (DiagnosisDeterministicRenderer.diagnosisDetailPresent(primary)) {
            sb.append("，出库口径成本约").append(primary).append("元");
        }
        String th = DiagnosisDeterministicRenderer.diagnosisFmtYuan(DiagnosisDeterministicRenderer.r0Get(row, "theoryCostAmount"));
        if (DiagnosisDeterministicRenderer.diagnosisDetailPresent(th)) {
            sb.append("，理论成本").append(th).append("元");
        }
        String rate = DiagnosisDeterministicRenderer.diagnosisFmtPercent(DiagnosisDeterministicRenderer.r0Get(row, "blendedGrossMarginRateOnListPrice"));
        if (DiagnosisDeterministicRenderer.diagnosisDetailPresent(rate)) {
            sb.append("，综合毛利率约").append(rate);
        }
        String reason = DeterministicRendererSupport.nz(row.get("riskReason"));
        if (!reason.isEmpty()) {
            sb.append("。").append(reason);
        } else {
            sb.append("。");
        }
        appendOptionalSecondaryContrast(sb, plan);
        return sb.toString().trim();
    }

    private static String composeDishProfitRateFromAnswerPlanRow(Map<String, Object> row, DishProfitAnswerPlan plan) {
        String name = DeterministicRendererSupport.nz(row.get("dishName"));
        if (name.isEmpty()) {
            name = "该菜品";
        }
        StringBuilder sb = new StringBuilder("「").append(name).append("」");
        String rate = DiagnosisDeterministicRenderer.diagnosisFmtPercent(DiagnosisDeterministicRenderer.r0Get(row, "blendedGrossMarginRateOnListPrice"));
        if (DiagnosisDeterministicRenderer.diagnosisDetailPresent(rate)) {
            sb.append("综合毛利率约").append(rate);
        }
        String rev = DiagnosisDeterministicRenderer.diagnosisFmtYuan(DiagnosisDeterministicRenderer.r0Get(row, "listPriceRevenue"));
        if (DiagnosisDeterministicRenderer.diagnosisDetailPresent(rev)) {
            sb.append("，销售额").append(rev).append("元");
        }
        String reason = DeterministicRendererSupport.nz(row.get("riskReason"));
        if (!reason.isEmpty()) {
            sb.append("。").append(reason);
        } else {
            sb.append("。");
        }
        appendOptionalSecondaryContrast(sb, plan);
        return sb.toString().trim();
    }

    private static String composeDishProfitGenericFocusRowFromAnswerPlan(Map<String, Object> row,
            DishProfitAnswerPlan plan) {
        String name = DeterministicRendererSupport.nz(row.get("dishName"));
        if (name.isEmpty()) {
            name = "该菜品";
        }
        StringBuilder sb = new StringBuilder(DiagnosisDeterministicRenderer.buildDiagnosisDishDragEvidenceSentenceStub(name, row));
        sb.append("，建议核对出库、配方和售价。");
        appendOptionalSecondaryContrast(sb, plan);
        return sb.toString().trim();
    }

    private static void appendOptionalSecondaryContrast(StringBuilder sb, DishProfitAnswerPlan plan) {
        if (plan == null || plan.getSecondaryRows() == null || plan.getSecondaryRows().isEmpty()) {
            return;
        }
        Map<String, Object> s0 = plan.getSecondaryRows().get(0);
        if (s0 == null) {
            return;
        }
        String n2 = DeterministicRendererSupport.nz(s0.get("dishName"));
        if (n2.isEmpty()) {
            return;
        }
        sb.append("\n对照：「").append(n2).append("」");
        String rate = DiagnosisDeterministicRenderer.diagnosisFmtPercent(DiagnosisDeterministicRenderer.r0Get(s0, "blendedGrossMarginRateOnListPrice"));
        String ac = DiagnosisDeterministicRenderer.diagnosisFmtYuan(DiagnosisDeterministicRenderer.r0Get(s0, "actualCostAmount"));
        String gap = DiagnosisDeterministicRenderer.diagnosisFmtYuan(DiagnosisDeterministicRenderer.r0Get(s0, "diffCostAmount"));
        boolean any = false;
        if (DiagnosisDeterministicRenderer.diagnosisDetailPresent(rate)) {
            sb.append("综合毛利率约").append(rate);
            any = true;
        }
        if (DiagnosisDeterministicRenderer.diagnosisDetailPresent(ac)) {
            sb.append(any ? "，" : "").append("实际成本").append(ac).append("元");
            any = true;
        }
        if (DiagnosisDeterministicRenderer.diagnosisDetailPresent(gap)) {
            sb.append(any ? "，" : "").append("成本差额约").append(gap).append("元");
        }
        sb.append("。");
    }

    private static String maybePrependDishProfitQueryScopeBanner(AiDishProfitOverviewResult r, String body) {
        if (r == null || body == null || body.isBlank()) {
            return body;
        }
        String b = r.getQueryScopeBanner();
        if (b == null || b.isBlank()) {
            return body;
        }
        String bt = b.trim();
        if (body.startsWith(bt)) {
            return body;
        }
        return bt + "\n\n" + body;
    }

    private static String shortFallbackDishProfit(AiDishProfitOverviewResult r, AiRunState state) {
        AiTimeWindowTextFormatter.UserPhrases tw = AiTimeWindowTextFormatter.forAnswer(state);
        if (r == null) {
            String range = tw != null && tw.getDisplayTimeRange() != null ? tw.getDisplayTimeRange() : "该统计区间";
            return "按「" + range + "」口径，当前可用的菜品利润/毛利数据不足，暂时无法给出有效分析。"
                    + "请确认该门店在该统计周期内是否有完整销售、成本与配方/核销数据。";
        }
        // 有 AnswerPlan.focusRows 的窄意图优先于下方的 dishProfitUseDeterministicSummaryOnly（仅吞工具 summary），避免误以为后者会覆盖计划行。
        DishProfitAnswerPlan apPlan = state != null ? state.getDishProfitAnswerPlan() : null;
        if (apPlan != null && apPlan.getFocusRows() != null && !apPlan.getFocusRows().isEmpty()
                && dishProfitNarrowRankingOrReasonPlan(state)) {
            String composed = composeDishProfitDeterministicFromAnswerPlan(apPlan);
            if (composed != null && !composed.isBlank()) {
                return maybePrependDishProfitQueryScopeBanner(r, composed);
            }
        }
        if (dishProfitUseDeterministicSummaryOnly(state)) {
            if (r.getSummary() != null && !r.getSummary().isBlank()) {
                return r.getSummary().trim();
            }
            if (r.getQueryScopeBanner() != null && !r.getQueryScopeBanner().isBlank()) {
                return r.getQueryScopeBanner().trim();
            }
            return "当前结构化菜品毛利数据不足或本轮工具未返回明细，请先核对配方与出库核销数据是否齐备。";
        }
        if (dishProfitNarrowRankingOrReasonPlan(state)) {
            StringBuilder narrow = new StringBuilder();
            if (r.getQueryScopeBanner() != null && !r.getQueryScopeBanner().isBlank()) {
                narrow.append(r.getQueryScopeBanner().trim());
            }
            if (r.getSummary() != null && !r.getSummary().isBlank()) {
                if (narrow.length() > 0) {
                    narrow.append("\n\n");
                }
                narrow.append(r.getSummary().trim());
            }
            String ns = narrow.toString().trim();
            return !ns.isEmpty() ? ns : "当前结构化菜品毛利数据不足或本轮工具未返回明细，请先核对配方与出库核销数据是否齐备。";
        }
        StringBuilder sb = new StringBuilder();
        if (r.getQueryScopeBanner() != null && !r.getQueryScopeBanner().isBlank()) {
            sb.append(r.getQueryScopeBanner().trim());
        }
        if (r.getSummary() != null && !r.getSummary().isBlank()) {
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append(r.getSummary().trim());
        }
        if (dishProfitAnswerIsActualOutboundOnly(state)) {
            String s = sb.toString().trim();
            return !s.isEmpty() ? s : DeterministicRendererSupport.nz(r.getSummary());
        }
        appendDishSectionOrPlaceholder(sb, "毛利表现较好的菜（成本口径相对完整）", r.getReliableProfitDishes(), 3, false,
                tw);
        appendDishSectionOrPlaceholder(sb, "需要关注的低毛利或成本偏高菜", r.getLowProfitDishes(), 3, false, tw);
        appendDishSectionOrPlaceholder(sb, "成本数据不完整、毛利率仅供参考的菜", r.getCostDataIncompleteDishes(), 4, true,
                tw);
        String s = sb.toString().trim();
        if (!s.isEmpty()) {
            return s;
        }
        if (r.getDishCount() > 0 && r.getSummary() != null && !r.getSummary().isBlank()) {
            return r.getSummary().trim();
        }
        if (r.getDishCount() > 0) {
            return "本轮识别到 " + r.getDishCount()
                    + " 道菜品销量记录，但草稿中未能展开结构化明细行；请查看上方摘要或菜品卡片。";
        }
        return "当前结构化菜品毛利数据不足或本轮工具未返回明细，请先核对配方与出库核销数据是否齐备。";
    }

    private static boolean dishProfitUseDeterministicSummaryOnly(AiRunState state) {
        if (state == null) {
            return false;
        }
        AiResolvedQueryContext ctx = state.getResolvedQueryContext();
        var qi = ctx != null ? ctx.getQueryIntent() : null;
        if (qi == null) {
            return false;
        }
        String sid = qi.getStructuredIntentDetail();
        if (sid == null || sid.isBlank()) {
            return false;
        }
        return AiQuerySemanticLexicon.STRUCTURED_DISH_ACTUAL_OUTBOUND_COST.equals(sid);
    }

    /**
     * 排行/原因类子意图：服务端已压缩 summary 与 answerPlan，Composer 走确定性短文，避免总览模板。
     */
    private static boolean dishProfitNarrowRankingOrReasonPlan(AiRunState state) {
        DishProfitAnswerPlan plan = state != null ? state.getDishProfitAnswerPlan() : null;
        if (plan == null || plan.getPlanType() == null || plan.getPlanType().isBlank()) {
            return false;
        }
        String t = plan.getPlanType().trim();
        return DishProfitAnswerPlan.TYPE_DISH_LOWEST_MARGIN.equals(t)
                || DishProfitAnswerPlan.TYPE_DISH_HIGHEST_ACTUAL_COST.equals(t)
                || DishProfitAnswerPlan.TYPE_DISH_PROFIT_REASON.equals(t)
                || DishProfitAnswerPlan.TYPE_DISH_THEORETICAL_COST.equals(t)
                || DishProfitAnswerPlan.TYPE_DISH_ACTUAL_OUTBOUND_COST.equals(t)
                || DishProfitAnswerPlan.TYPE_DISH_PROFIT_RATE.equals(t)
                || DishProfitAnswerPlan.TYPE_DISH_COST_GAP.equals(t);
    }

    private static boolean dishProfitAnswerIsActualOutboundOnly(AiRunState state) {
        if (state == null) {
            return false;
        }
        AiResolvedQueryContext ctx = state.getResolvedQueryContext();
        var qi = ctx != null ? ctx.getQueryIntent() : null;
        return qi != null
                && AiQuerySemanticLexicon.STRUCTURED_DISH_ACTUAL_OUTBOUND_COST.equals(qi.getStructuredIntentDetail());
    }

    private static List<AiDishProfitDishBrief> dedupeDishBriefsForComposer(List<AiDishProfitDishBrief> xs) {
        LinkedHashMap<String, AiDishProfitDishBrief> m = new LinkedHashMap<>();
        for (AiDishProfitDishBrief b : xs) {
            String key;
            if (b.getFoodId() != null && !b.getFoodId().isBlank()) {
                key = "id:" + b.getFoodId().trim();
            } else if (b.getDishName() != null && !b.getDishName().isBlank()) {
                key = "n:" + b.getDishName().trim();
            } else {
                key = "row:" + m.size();
            }
            m.putIfAbsent(key, b);
        }
        return new ArrayList<>(m.values());
    }

    private static void appendDishSectionOrPlaceholder(StringBuilder sb, String title, List<AiDishProfitDishBrief> dishes,
            int max, boolean incompleteCost, AiTimeWindowTextFormatter.UserPhrases tw) {
        if (dishes == null || dishes.isEmpty()) {
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            if (title != null && title.contains("毛利表现较好")) {
                sb.append(title).append("：").append(tw.getDisplayTimeRange())
                        .append("内暂未识别到成本数据完整且毛利表现突出的菜品。");
            } else {
                sb.append(title).append("：暂无");
            }
            return;
        }
        appendDishSection(sb, title, dishes, max, incompleteCost);
    }

    private static void appendDishSection(StringBuilder sb, String title, List<AiDishProfitDishBrief> dishes, int max,
            boolean incompleteCost) {
        if (dishes == null || dishes.isEmpty()) {
            return;
        }
        List<AiDishProfitDishBrief> use = dedupeDishBriefsForComposer(dishes);
        if (sb.length() > 0) {
            sb.append("\n\n");
        }
        sb.append(title).append("：");
        int n = 0;
        for (AiDishProfitDishBrief b : use) {
            if (n >= max || b == null) {
                break;
            }
            if (b.getDishName() == null || b.getDishName().isBlank()) {
                continue;
            }
            sb.append("\n• ").append(DeterministicRendererSupport.nz(b.getDishName()))
                    .append("：销量约 ").append(DeterministicRendererSupport.nz(b.getSalesQty()))
                    .append("，销售额约 ").append(DeterministicRendererSupport.nz(b.getSalesAmount()))
                    .append("，理论成本 ").append(DeterministicRendererSupport.nz(b.getTheoreticalCost()))
                    .append("，实际成本 ").append(DeterministicRendererSupport.nz(b.getActualCost()))
                    .append("，毛利率 ").append(AiQuerySemanticLexicon.formatGrossMarginRateForNaturalLanguage(b.getGrossProfitRate()));
            if (incompleteCost) {
                sb.append("（成本未齐，该毛利率不可靠；请先补 BOM/出库核销）");
            } else if (b.getRiskReason() != null && !b.getRiskReason().isBlank()) {
                sb.append("（").append(b.getRiskReason().trim()).append("）");
            }
            n++;
        }
        if (n == 0) {
            sb.append("\n暂无");
        }
    }
}
