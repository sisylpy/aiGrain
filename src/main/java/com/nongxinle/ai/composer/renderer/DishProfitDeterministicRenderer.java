package com.nongxinle.ai.composer.renderer;

import com.nongxinle.ai.dto.business.DishProfitAnswerPlan;
import com.nongxinle.ai.graph.business.DishProfitRankingSalesEvidenceSupport;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public final class DishProfitDeterministicRenderer {

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
        if (plan == null) {
            return null;
        }
        String type = plan.getPlanType() != null ? plan.getPlanType().trim() : "";
        if (DishProfitAnswerPlan.TYPE_DISH_PROFIT_RANKING_NO_DATA.equals(type)
                || DishProfitRankingSalesEvidenceSupport.isNoDataRankingPlan(plan)) {
            return DishProfitRankingSalesEvidenceSupport.EMPTY_RANKING_MESSAGE;
        }
        if (plan.getFocusRows() == null || plan.getFocusRows().isEmpty()) {
            return null;
        }
        Map<String, Object> row = plan.getFocusRows().get(0);
        if (row == null) {
            return null;
        }
        if (DishProfitAnswerPlan.TYPE_DISH_INGREDIENT_COST_BREAKDOWN.equals(type)) {
            if (plan.getIngredientBreakdownAvailable() != null && plan.getIngredientBreakdownAvailable()) {
                return composeIngredientBreakdownAvailableFromAnswerPlan(plan);
            }
            return composeIngredientBreakdownUnavailableFromAnswerPlan(row, plan);
        }
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

    private static String composeIngredientBreakdownUnavailableFromAnswerPlan(
            Map<String, Object> row, DishProfitAnswerPlan plan) {
        StringBuilder sb = new StringBuilder();
        String reason = plan != null ? plan.getIngredientBreakdownUnavailableReason() : null;
        if ("FOOD_NOT_FOUND".equals(reason)) {
            sb.append("未在菜品库中定位到该菜品，原料成本构成无法展开。");
        } else if ("RECIPE_NOT_FOUND".equals(reason)) {
            sb.append("该菜品暂无有效配方行，原料成本构成无法拆分。");
        } else if ("NEED_DISH_CLARIFICATION".equals(reason)) {
            sb.append("存在多道同名菜，请先明确具体菜品后再查原料成本构成。");
        } else if ("EMPTY_INGREDIENT_ROWS".equals(reason)) {
            sb.append("配方或出库成本数据不足，本期原料成本构成暂无明细行（未编造配料）。");
        } else if ("NO_INGREDIENT_BREAKDOWN_TOOL_RUN".equals(reason)) {
            sb.append("原料成本构成查询未执行，暂无法拆分明细。");
        } else if ("DISH_INGREDIENT_TOOL_FAILED".equals(reason)) {
            sb.append("原料成本构成查询失败，请稍后重试。");
        } else {
            sb.append("当前无法给出原料成本构成明细。");
            if (reason != null && !reason.isBlank()) {
                sb.append("（").append(reason.trim()).append("）");
            }
        }
        if (row != null && !row.isEmpty()) {
            sb.append("\n\n整菜汇总：");
            appendWholeDishSummaryFields(sb, row);
            sb.append("。");
        }
        return sb.toString().trim();
    }

    private static String composeIngredientBreakdownAvailableFromAnswerPlan(DishProfitAnswerPlan plan) {
        StringBuilder sb = new StringBuilder();
        List<Map<String, Object>> rows = plan.getIngredientRows();
        if (rows == null || rows.isEmpty()) {
            sb.append("原料成本构成暂无明细行（可能区间内无销量或未出库核销），未编造配料名目。");
            return sb.toString().trim();
        }
        String dish = DeterministicRendererSupport.nz(
                plan.getFocusRows() != null && !plan.getFocusRows().isEmpty()
                        ? plan.getFocusRows().get(0).get("dishName")
                        : null);
        if (dish.isEmpty()) {
            dish = "该菜品";
        }
        String tr = plan.getTimeLabel();
        if (tr.isEmpty()) {
            tr = "所选时间范围";
        }
        sb.append("按上文菜品【").append(dish).append("】和【").append(tr).append("】查询，原料成本构成如下：\n");

        List<Map<String, Object>> sortedByPerDish = new ArrayList<>(rows);
        sortedByPerDish.sort(
                Comparator.comparingDouble((Map<String, Object> r) ->
                                parseDoubleLoose(DeterministicRendererSupport.nz(r.get("costPerDish"))))
                        .reversed());

        List<Map<String, Object>> sortedByTotal = new ArrayList<>(rows);
        sortedByTotal.sort(
                Comparator.comparingDouble((Map<String, Object> r) ->
                                parseDoubleLoose(DeterministicRendererSupport.nz(r.get("totalCost"))))
                        .reversed());

        Map<String, Object> topPerDish = pickFirstNamedRow(sortedByPerDish);
        Map<String, Object> topTotal = pickFirstNamedRow(sortedByTotal);

        int n = 0;
        for (Map<String, Object> r : sortedByPerDish) {
            if (n >= 6) {
                break;
            }
            String iname = DeterministicRendererSupport.nz(r.get("ingredientName"));
            if (iname.isEmpty()) {
                continue;
            }
            String rq = blankToDash(r.get("recipeQuantityPerDish"));
            String ru = DeterministicRendererSupport.nz(r.get("recipeUnit"));
            String perUse = (rq.equals("—") && ru.isEmpty()) ? "—" : rq + (ru.isEmpty() ? "" : ru);
            sb.append("\n- ")
                    .append(iname)
                    .append("：每份配方用量 ")
                    .append(perUse)
                    .append("，每份菜摊销成本 ")
                    .append(blankToDash(r.get("costPerDish")))
                    .append("，本期总成本 ")
                    .append(blankToDash(r.get("totalCost")))
                    .append("，成本占比 ")
                    .append(blankToDash(r.get("costRatio")));
            String usageGap = formatUsageGapLine(r);
            if (!usageGap.isEmpty()) {
                sb.append("；").append(usageGap);
            }
            String w = DeterministicRendererSupport.nz(r.get("warning"));
            if (!w.isEmpty()) {
                sb.append("；提示：").append(w);
            }
            n++;
        }
        if (n == 0) {
            sb.append("\n（明细行缺少可展示原料名，未编造。）");
        }

        if (plan.getMissingPriceItems() != null && !plan.getMissingPriceItems().isEmpty()) {
            sb.append("\n\n部分原料本期未能摊入有效出库成本，上表已标注；整体结论请以工具明细为准。");
        }

        appendIngredientExecutiveSummary(sb, topPerDish, topTotal, sortedByPerDish);

        String topPdName = topPerDish == null ? "" : DeterministicRendererSupport.nz(topPerDish.get("ingredientName"));
        if (StringUtils.hasText(topPdName)) {
            sb.append("\n\n从当前数据看，原料侧压力较突出的是「")
                    .append(topPdName)
                    .append("」（按每份菜摊销原料成本排序；完整清单以服务端明细为准）。");
        } else {
            sb.append("\n\n从当前数据看，暂不能从明细中单点「主要拖累」原料名（行数据不足）。");
        }

        return sb.toString().trim();
    }

    private static Map<String, Object> pickFirstNamedRow(List<Map<String, Object>> rows) {
        for (Map<String, Object> r : rows) {
            if (r != null && StringUtils.hasText(DeterministicRendererSupport.nz(r.get("ingredientName")))) {
                return r;
            }
        }
        return null;
    }

    private static void appendIngredientExecutiveSummary(
            StringBuilder sb, Map<String, Object> topPerDish, Map<String, Object> topTotal, List<Map<String, Object>> rows) {
        String nPd = topPerDish == null ? "" : DeterministicRendererSupport.nz(topPerDish.get("ingredientName"));
        String nTot = topTotal == null ? "" : DeterministicRendererSupport.nz(topTotal.get("ingredientName"));
        if (StringUtils.hasText(nPd) || StringUtils.hasText(nTot)) {
            sb.append("\n\n老板视角摘要：");
            if (StringUtils.hasText(nPd)) {
                sb.append("每份菜摊销成本最高的是「")
                        .append(nPd)
                        .append("」（")
                        .append(blankToDash(topPerDish.get("costPerDish")))
                        .append(" / 份）。");
            }
            if (StringUtils.hasText(nTot) && !nTot.equals(nPd)) {
                sb.append("本期总成本最高的是「")
                        .append(nTot)
                        .append("」（")
                        .append(blankToDash(topTotal.get("totalCost")))
                        .append("）。");
            } else if (StringUtils.hasText(nTot) && nTot.equals(nPd)) {
                sb.append("本期总成本最高的同样是「").append(nTot).append("」。");
            }
        }
        String gapIng = findLargestUsageGapIngredient(rows);
        if (StringUtils.hasText(gapIng)) {
            sb.append("理论用量与实摊出库差距突出的是：").append(gapIng).append("。");
        }
        String convWarn = firstRowMatchingSubstring(rows, "UNIT_CONVERSION_MISSING");
        if (StringUtils.hasText(convWarn)) {
            sb.append("存在单位/规格与出库口径可能不一致的原料（").append(convWarn).append("），金额以系统已算结果为准，请勿手工换算单价。");
        }
    }

    private static String firstRowMatchingSubstring(List<Map<String, Object>> rows, String needle) {
        if (rows == null || needle == null) {
            return "";
        }
        for (Map<String, Object> r : rows) {
            String w = DeterministicRendererSupport.nz(r.get("warning"));
            if (w.contains(needle)) {
                return DeterministicRendererSupport.nz(r.get("ingredientName"));
            }
        }
        return "";
    }

    /** 按 |理论与实摊出库|/理论 找偏差最大一行（理论为 0 则跳过）。 */
    private static String findLargestUsageGapIngredient(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return "";
        }
        double best = -1;
        String bestName = "";
        for (Map<String, Object> r : rows) {
            String name = DeterministicRendererSupport.nz(r.get("ingredientName"));
            if (name.isEmpty()) {
                continue;
            }
            double th = parseDoubleLoose(DeterministicRendererSupport.nz(r.get("theoryUsage")));
            double act = parseDoubleLoose(DeterministicRendererSupport.nz(r.get("actualUsage")));
            if (th <= 0) {
                continue;
            }
            double ratio = Math.abs(act - th) / th;
            if (ratio > best && ratio >= 0.15) {
                best = ratio;
                bestName = name + "（偏差约 " + String.format(Locale.ROOT, "%.0f%%", ratio * 100) + "）";
            }
        }
        return bestName;
    }

    private static String formatUsageGapLine(Map<String, Object> r) {
        double th = parseDoubleLoose(DeterministicRendererSupport.nz(r.get("theoryUsage")));
        double act = parseDoubleLoose(DeterministicRendererSupport.nz(r.get("actualUsage")));
        if (th <= 0) {
            return "";
        }
        double ratio = Math.abs(act - th) / th;
        if (ratio < 0.15) {
            return "";
        }
        return "理论用量 "
                + blankToDash(r.get("theoryUsage"))
                + "、实摊出库 "
                + blankToDash(r.get("actualUsage"))
                + "（相对偏差约 "
                + String.format(Locale.ROOT, "%.0f%%", ratio * 100)
                + "）";
    }

    private static double parseDoubleLoose(String s) {
        if (s == null || s.isBlank()) {
            return 0d;
        }
        String t = s.replace("%", "").trim();
        try {
            return Double.parseDouble(t);
        } catch (Exception e) {
            return 0d;
        }
    }

    private static String blankToDash(Object v) {
        String s = DeterministicRendererSupport.nz(v);
        return s.isEmpty() ? "—" : s;
    }

    private static void appendWholeDishSummaryFields(StringBuilder sb, Map<String, Object> row) {
        if (row == null) {
            sb.append("暂无");
            return;
        }
        String dish = DeterministicRendererSupport.nz(row.get("dishName"));
        boolean any = false;
        if (!dish.isEmpty()) {
            sb.append("「").append(dish).append("」");
            any = true;
        }
        any = appendLabeledFieldAfterDish(sb, any, "销量", row.get("salesQuantity"));
        any = appendLabeledFieldAfterDish(sb, any, "标价收入", row.get("listPriceRevenue"));
        any = appendLabeledFieldAfterDish(sb, any, "理论成本", row.get("theoryCostAmount"));
        any = appendLabeledFieldAfterDish(sb, any, "实际成本", row.get("actualCostAmount"));
        any = appendLabeledFieldAfterDish(sb, any, "毛利率", row.get("blendedGrossMarginRateOnListPrice"));
        String rr = DeterministicRendererSupport.nz(row.get("riskReason"));
        if (!rr.isEmpty()) {
            sb.append(any ? "；" : "").append("风险说明：").append(rr);
        } else if (!any) {
            sb.append("暂无");
        }
    }

    private static boolean appendLabeledFieldAfterDish(StringBuilder sb, boolean any, String label, Object raw) {
        String v = DeterministicRendererSupport.nz(raw);
        if (v.isEmpty() || "暂无".equals(v)) {
            return any;
        }
        sb.append(any ? "；" : "").append(label).append(" ").append(v);
        return true;
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
}
