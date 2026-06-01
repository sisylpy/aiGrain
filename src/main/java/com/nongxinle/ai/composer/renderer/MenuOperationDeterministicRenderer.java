package com.nongxinle.ai.composer.renderer;

import com.nongxinle.ai.composer.menu.MenuOperationPortfolioExpressionSupport;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuOptimizationDishItem;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuOptimizationPlan;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuOptimizationPriorityGroup;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuPortfolioCategory;
import com.nongxinle.ai.dto.business.MenuOperationRecommendedAction;
import com.nongxinle.ai.graph.business.MenuPortfolioSalesEvidenceSupport;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * MenuOperation 确定性宣读：只读 {@link MenuOperationAnswerPlan}，不读 toolResults、不算术、不排序。
 */
@Component
public final class MenuOperationDeterministicRenderer {

    public String render(MenuOperationAnswerPlan plan) {
        if (plan == null) {
            return "当前未能读取菜单经营顾问计划。";
        }
        if (MenuOperationAnswerPlan.TYPE_MENU_ACTION_RECOMMENDATION.equals(plan.getPlanType())) {
            return renderActionRecommendationPlan(plan);
        }

        StringBuilder sb = new StringBuilder();

        appendSectionTitle(sb, "结论");
        appendConclusion(sb, plan);

        if (MenuOperationAnswerPlan.TYPE_MENU_OPERATION_OVERVIEW.equals(plan.getPlanType())) {
            appendSectionTitle(sb, "菜单结构四象限");
            appendPortfolioClassification(sb, plan);
        }

        appendSectionTitle(sb, "关键证据");
        appendEvidence(sb, plan);

        appendSectionTitle(sb, "重点菜品");
        appendDishList(sb, plan);

        appendSectionTitle(sb, "建议动作");
        appendActions(sb, plan);

        appendCapabilityLimitsNotice(sb, plan);
        appendKnownGapsUserNotice(sb, plan);
        return sb.toString().trim();
    }

    /** 菜单优化方案：范围/时间 + 优化方案 + nextSteps；不重复输出经营概览指标。 */
    private static String renderActionRecommendationPlan(MenuOperationAnswerPlan plan) {
        StringBuilder sb = new StringBuilder();
        appendScopeAndTime(sb, plan);
        appendSectionTitle(sb, "优化方案");
        appendOptimizationPlan(sb, plan);
        appendCapabilityLimitsNotice(sb, plan);
        appendKnownGapsUserNotice(sb, plan);
        return sb.toString().trim();
    }

    private static void appendScopeAndTime(StringBuilder sb, MenuOperationAnswerPlan plan) {
        boolean any = false;
        if (StringUtils.hasText(plan.getScopeLabel())) {
            sb.append("范围：").append(plan.getScopeLabel().trim()).append('\n');
            any = true;
        }
        if (StringUtils.hasText(plan.getTimeLabel())) {
            sb.append("时间：").append(plan.getTimeLabel().trim()).append('\n');
            any = true;
        }
        if (any) {
            sb.append('\n');
        }
    }

    /** 菜单优化方案：只读 plan.menuOptimizationPlan，不重算分组。 */
    private static void appendOptimizationPlan(StringBuilder sb, MenuOperationAnswerPlan plan) {
        MenuOptimizationPlan optimization = plan.getMenuOptimizationPlan();
        if (optimization == null) {
            sb.append("暂无菜单优化方案。").append('\n');
            return;
        }
        if (StringUtils.hasText(optimization.getOptimizationSummary())) {
            sb.append(optimization.getOptimizationSummary().trim()).append('\n');
        }
        List<MenuOptimizationPriorityGroup> groups = optimization.getPriorityGroups();
        if (groups != null && !groups.isEmpty()) {
            for (MenuOptimizationPriorityGroup group : groups) {
                if (group == null || group.getDishes() == null || group.getDishes().isEmpty()) {
                    continue;
                }
                sb.append('\n')
                        .append(group.getPriority())
                        .append(". ")
                        .append(nz(group.getGroupName()));
                if (StringUtils.hasText(group.getReason())) {
                    sb.append("：").append(group.getReason().trim());
                }
                sb.append('\n');
                appendOptimizationDishItems(sb, group.getDishes(), 5);
            }
        }
        List<String> nextSteps = optimization.getNextSteps();
        if (nextSteps != null && !nextSteps.isEmpty()) {
            sb.append('\n').append("本周可先做的事").append('\n');
            int i = 1;
            for (String step : nextSteps) {
                if (!StringUtils.hasText(step)) {
                    continue;
                }
                sb.append(i++).append(". ").append(step.trim()).append('\n');
            }
        }
    }

    private static void appendOptimizationDishItems(
            StringBuilder sb, List<MenuOptimizationDishItem> dishes, int max) {
        int n = Math.min(dishes.size(), max);
        for (int i = 0; i < n; i++) {
            MenuOptimizationDishItem dish = dishes.get(i);
            if (dish == null) {
                continue;
            }
            sb.append("- ").append(StringUtils.hasText(dish.getDishName()) ? dish.getDishName().trim() : "（未命名）");
            if (StringUtils.hasText(dish.getSuggestedActionLabel())) {
                sb.append("（").append(dish.getSuggestedActionLabel().trim()).append("）");
            }
            if (StringUtils.hasText(dish.getBlendedGrossMarginRateOnListPrice())) {
                sb.append(" 毛利率 ").append(dish.getBlendedGrossMarginRateOnListPrice().trim());
            }
            if (StringUtils.hasText(dish.getActualProfitAmount())) {
                sb.append(" 实际利润 ").append(dish.getActualProfitAmount().trim()).append("元");
            }
            sb.append('\n');
        }
    }

    private static void appendCapabilityLimitsNotice(StringBuilder sb, MenuOperationAnswerPlan plan) {
        MenuOptimizationPlan optimization = plan.getMenuOptimizationPlan();
        if (optimization == null || optimization.getCapabilityLimits() == null) {
            return;
        }
        Map<String, Object> limits = optimization.getCapabilityLimits();
        LinkedHashSet<String> phrases = new LinkedHashSet<>();
        if (capabilityNotInP1(limits, "latestPurchasePrice")) {
            phrases.add("最新采购价");
        }
        if (capabilityNotInP1(limits, "externalMarketBenchmark")) {
            phrases.add("外部市场比价");
        }
        if (capabilityNotInP1(limits, "multiPeriodTrend")) {
            phrases.add("连续多周期趋势");
        }
        if (capabilityNotInP1(limits, "crossStoreDishRank")) {
            phrases.add("跨门店排名");
        }
        if (capabilityNotInP1(limits, "comboOrderAnalysis")) {
            phrases.add("套餐点单组合分析");
        }
        if (phrases.isEmpty()) {
            return;
        }
        appendSectionTitle(sb, "说明");
        sb.append("当前版本暂不提供")
                .append(joinWithAnd(new ArrayList<>(phrases)))
                .append("。")
                .append('\n');
    }

    private static boolean capabilityNotInP1(Map<String, Object> limits, String key) {
        if (limits == null || key == null) {
            return false;
        }
        Object value = limits.get(key);
        return value != null && "NOT_IN_P1".equals(value.toString().trim());
    }

    /** knownGaps 保留在 AnswerPlan 供 debug/harness；此处只输出老板可读的自然语言说明。 */
    private static void appendKnownGapsUserNotice(StringBuilder sb, MenuOperationAnswerPlan plan) {
        List<String> gaps = plan.getKnownGaps();
        if (gaps == null || gaps.isEmpty()) {
            return;
        }
        LinkedHashSet<String> limitationPhrases = new LinkedHashSet<>();
        boolean smallSampleNoted = false;
        for (String gap : gaps) {
            if (!StringUtils.hasText(gap)) {
                continue;
            }
            switch (gap.trim()) {
                case "DISH_INGREDIENT_COST_BREAKDOWN_NOT_IN_P1" ->
                        limitationPhrases.add("食材级成本拆解");
                case "MENU_PRICING_ADVICE_NOT_IN_P1" -> limitationPhrases.add("自动定价建议");
                case "MENU_SINGLE_ANALYSIS_NOT_IN_P1" -> limitationPhrases.add("单菜深度分析");
                case "MENU_PORTFOLIO_CLASSIFICATION_SMALL_SAMPLE" -> smallSampleNoted = true;
                case "TIME_SCOPE_FOLLOW_UP_INHERITANCE_LABEL_INACCURATE",
                        "STOCK_REDUCE_EVIDENCE_OPTIONAL" -> { /* harness/debug only */ }
                default -> { /* unknown gap codes stay off user-visible text */ }
            }
        }
        if (limitationPhrases.isEmpty() && !smallSampleNoted) {
            return;
        }
        appendSectionTitle(sb, "说明");
        if (!limitationPhrases.isEmpty()) {
            sb.append("当前暂不支持")
                    .append(joinWithAnd(new ArrayList<>(limitationPhrases)))
                    .append('。')
                    .append('\n');
        }
        if (smallSampleNoted) {
            sb.append("当前样本较少，四象限分层仅供参考。").append('\n');
        }
    }

    private static String joinWithAnd(List<String> parts) {
        if (parts == null || parts.isEmpty()) {
            return "";
        }
        if (parts.size() == 1) {
            return parts.get(0);
        }
        if (parts.size() == 2) {
            return parts.get(0) + "和" + parts.get(1);
        }
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < parts.size() - 1; i++) {
            if (i > 0) {
                joined.append('、');
            }
            joined.append(parts.get(i));
        }
        return joined.append('和').append(parts.get(parts.size() - 1)).toString();
    }

    private static void appendSectionTitle(StringBuilder sb, String title) {
        if (sb.length() > 0) {
            sb.append('\n');
        }
        sb.append(title).append('\n');
    }

    private static void appendConclusion(StringBuilder sb, MenuOperationAnswerPlan plan) {
        if (StringUtils.hasText(plan.getScopeLabel())) {
            sb.append("范围：").append(plan.getScopeLabel().trim()).append('\n');
        }
        if (StringUtils.hasText(plan.getTimeLabel())) {
            sb.append("时间：").append(plan.getTimeLabel().trim()).append('\n');
        }
        if (MenuOperationAnswerPlan.TYPE_MENU_DISH_HIGH_SALES_LOW_PROFIT.equals(plan.getPlanType())) {
            Map<String, Object> facts = plan.getSummaryFacts();
            String matchMode =
                    facts != null && facts.get("highSalesLowMarginMatchMode") != null
                            ? facts.get("highSalesLowMarginMatchMode").toString().trim()
                            : "";
            if ("LOW_MARGIN_FALLBACK".equals(matchMode)) {
                sb.append("本期未发现典型「销量靠前且毛利偏低」组合；以下展示毛利率相对偏低的菜品供复核。")
                        .append('\n');
            } else {
                sb.append("在销量前30%菜品中，筛出毛利率处于后30%、低于菜单整体水平，或实际利润≤0需优先处理的菜品。")
                        .append('\n');
            }
        }
        Map<String, Object> facts = plan.getSummaryFacts();
        if (facts == null || facts.isEmpty()) {
            sb.append("暂无汇总结论。");
            return;
        }
        appendFactLine(sb, facts, "dishCountAnalyzed", "分析菜品数", "");
        appendFactLine(sb, facts, "totalListPriceRevenue", "标价销售额", "元");
        appendFactLine(sb, facts, "portfolioActualProfitAmount", "实际利润（type123）", "元");
        appendFactLine(sb, facts, "comprehensiveGrossMarginRate", "综合毛利率", "");
        appendFactLine(sb, facts, "highSalesLowProfitCount", "重点关注菜品数", "道");
        appendFactLine(sb, facts, "wasteLossRatioInOutbound123", "区间损耗率", "");
        appendFactLine(sb, facts, "riskDishCount", "风险菜品数", "道");
        if (MenuPortfolioSalesEvidenceSupport.isNoSalesPortfolioPeriod(plan)) {
            sb.append(MenuPortfolioSalesEvidenceSupport.EMPTY_PORTFOLIO_MESSAGE).append('\n');
            return;
        }
        appendPortfolioConclusionLine(sb, plan);
    }

    /** 只读 plan.menuPortfolioClassification，不重算分类。 */
    private static void appendPortfolioConclusionLine(StringBuilder sb, MenuOperationAnswerPlan plan) {
        var portfolio = plan.getMenuPortfolioClassification();
        if (portfolio == null || portfolio.getCategories() == null || portfolio.getCategories().isEmpty()) {
            return;
        }
        int star = countForCategory(portfolio, MenuOperationAnswerPlan.CATEGORY_STAR);
        int traffic = countForCategory(portfolio, MenuOperationAnswerPlan.CATEGORY_TRAFFIC);
        int potential = countForCategory(portfolio, MenuOperationAnswerPlan.CATEGORY_POTENTIAL);
        int eliminate = countForCategory(portfolio, MenuOperationAnswerPlan.CATEGORY_ELIMINATE);
        sb.append(
                        MenuOperationPortfolioExpressionSupport.formatPortfolioDistributionSummary(
                                portfolio.getTotalDishCount(), star, traffic, potential, eliminate))
                .append('\n');
    }

    private static void appendPortfolioClassification(StringBuilder sb, MenuOperationAnswerPlan plan) {
        if (MenuPortfolioSalesEvidenceSupport.isNoSalesPortfolioPeriod(plan)) {
            sb.append(MenuPortfolioSalesEvidenceSupport.EMPTY_PORTFOLIO_MESSAGE);
            return;
        }
        var portfolio = plan.getMenuPortfolioClassification();
        if (portfolio == null) {
            sb.append("暂无四象限分类数据。");
            return;
        }
        int star = countForCategory(portfolio, MenuOperationAnswerPlan.CATEGORY_STAR);
        int traffic = countForCategory(portfolio, MenuOperationAnswerPlan.CATEGORY_TRAFFIC);
        int potential = countForCategory(portfolio, MenuOperationAnswerPlan.CATEGORY_POTENTIAL);
        int eliminate = countForCategory(portfolio, MenuOperationAnswerPlan.CATEGORY_ELIMINATE);
        sb.append(
                        MenuOperationPortfolioExpressionSupport.composePortfolioAnalysisLead(
                                plan, portfolio.getTotalDishCount()))
                .append("，其中明星菜 ")
                .append(star)
                .append(" 道、引流菜 ")
                .append(traffic)
                .append(" 道、潜力菜 ")
                .append(potential)
                .append(" 道、观察菜 ")
                .append(eliminate)
                .append(" 道。")
                .append('\n');
        sb.append(MenuOperationPortfolioExpressionSupport.portfolioRuleExplanation()).append('\n');
        sb.append(MenuOperationPortfolioExpressionSupport.portfolioCategoryGuidance()).append('\n');
        if (portfolio.getCategories() == null) {
            return;
        }
        for (MenuPortfolioCategory cat : portfolio.getCategories()) {
            if (cat == null || cat.getCount() <= 0) {
                continue;
            }
            sb.append("- ")
                    .append(cat.getCategoryName())
                    .append(" ")
                    .append(cat.getCount())
                    .append(" 道（")
                    .append(nz(cat.getRatio()))
                    .append("）");
            if (StringUtils.hasText(cat.getSummary())) {
                sb.append("：")
                        .append(
                                MenuOperationPortfolioExpressionSupport.rewriteCategorySummary(
                                                cat.getSummary(),
                                                cat.getCategoryName(),
                                                cat.getCount(),
                                                cat.getRatio())
                                        .trim());
            }
            String recommendedAction =
                    MenuOperationPortfolioExpressionSupport.categoryRecommendedAction(cat.getCategoryCode());
            if (!StringUtils.hasText(recommendedAction)) {
                recommendedAction = cat.getRecommendedAction();
            }
            if (StringUtils.hasText(recommendedAction)) {
                sb.append("；").append(recommendedAction.trim());
            }
            sb.append('\n');
        }
    }

    private static int countForCategory(
            MenuOperationAnswerPlan.MenuPortfolioClassification portfolio, String code) {
        if (portfolio.getCategories() == null) {
            return 0;
        }
        for (MenuPortfolioCategory cat : portfolio.getCategories()) {
            if (cat != null && code.equals(cat.getCategoryCode())) {
                return cat.getCount();
            }
        }
        return 0;
    }

    private static void appendFactLine(
            StringBuilder sb, Map<String, Object> facts, String key, String label, String unit) {
        Object v = facts.get(key);
        if (v == null || !StringUtils.hasText(v.toString())) {
            return;
        }
        sb.append("- ").append(label).append("：").append(v.toString().trim());
        if (StringUtils.hasText(unit)) {
            sb.append(unit);
        }
        sb.append('\n');
    }

    private static void appendEvidence(StringBuilder sb, MenuOperationAnswerPlan plan) {
        List<Map<String, Object>> rows = plan.getEvidenceRows();
        if (rows == null || rows.isEmpty()) {
            sb.append("暂无结构化证据行。");
            return;
        }
        int limit = Math.min(rows.size(), 8);
        for (int i = 0; i < limit; i++) {
            Map<String, Object> row = rows.get(i);
            if (row == null) {
                continue;
            }
            String label = nz(row.get("displayLabel"));
            String value = nz(row.get("value"));
            if (!StringUtils.hasText(label)) {
                continue;
            }
            sb.append("- ").append(label.trim());
            if (StringUtils.hasText(value)) {
                sb.append(" ").append(value.trim());
                String unit = nz(row.get("unit"));
                if (StringUtils.hasText(unit)) {
                    sb.append(unit.trim());
                }
            }
            sb.append('\n');
        }
    }

    private static void appendDishList(StringBuilder sb, MenuOperationAnswerPlan plan) {
        List<Map<String, Object>> focus = plan.getFocusDishes();
        List<Map<String, Object>> risk = plan.getRiskDishes();
        List<Map<String, Object>> opportunity = plan.getOpportunityDishes();
        if (MenuOperationAnswerPlan.TYPE_MENU_OPERATION_OVERVIEW.equals(plan.getPlanType())) {
            appendOverviewDishList(sb, focus, risk, opportunity);
            return;
        }
        boolean highSalesPlan =
                MenuOperationAnswerPlan.TYPE_MENU_DISH_HIGH_SALES_LOW_PROFIT.equals(plan.getPlanType());
        if ((focus == null || focus.isEmpty()) && (risk == null || risk.isEmpty())) {
            sb.append("暂无重点菜品。");
            return;
        }
        if (focus != null && !focus.isEmpty()) {
            sb.append("重点分析菜品：");
            appendDishNames(sb, focus, 5);
            sb.append('\n');
        }
        if (risk != null && !risk.isEmpty()) {
            sb.append(highSalesPlan ? "重点关注：" : "风险关注：");
            if (highSalesPlan) {
                appendHighSalesRiskDishes(sb, risk, 5);
            } else {
                appendDishNames(sb, risk, 5);
            }
            sb.append('\n');
        }
    }

    /** Overview：focus 仅作分析样本，主推只从 opportunity 宣读。 */
    private static void appendOverviewDishList(
            StringBuilder sb,
            List<Map<String, Object>> focus,
            List<Map<String, Object>> risk,
            List<Map<String, Object>> opportunity) {
        boolean any = false;
        if (focus != null && !focus.isEmpty()) {
            sb.append("重点分析菜品：");
            appendDishNames(sb, focus, 5);
            sb.append('\n');
            any = true;
        }
        if (risk != null && !risk.isEmpty()) {
            sb.append("风险关注：");
            appendDishNames(sb, risk, 5);
            sb.append('\n');
            any = true;
        }
        if (opportunity != null && !opportunity.isEmpty()) {
            sb.append("可继续主推：");
            appendDishNames(sb, opportunity, 5);
            sb.append('\n');
            any = true;
        }
        if (!any) {
            sb.append("暂无重点菜品。");
        }
    }

    private static void appendHighSalesRiskDishes(StringBuilder sb, List<Map<String, Object>> dishes, int max) {
        int n = Math.min(dishes.size(), max);
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            Map<String, Object> d = dishes.get(i);
            String name = d == null ? "" : nz(d.get("dishName"));
            sb.append("- ")
                    .append(StringUtils.hasText(name) ? name.trim() : "（未命名）");
            String reason = d == null ? "" : nz(d.get("riskReason"));
            if (StringUtils.hasText(reason)) {
                sb.append("：").append(reason.trim());
            }
            String salesLevel = d == null ? "" : nz(d.get("salesLevelDescription"));
            if (StringUtils.hasText(salesLevel)) {
                sb.append("（").append(salesLevel.trim()).append("）");
            } else {
                String margin = d == null ? "" : nz(d.get("blendedGrossMarginRateOnListPrice"));
                if (StringUtils.hasText(margin)) {
                    sb.append("（毛利率 ").append(margin.trim()).append("）");
                }
            }
        }
    }

    private static void appendDishNames(StringBuilder sb, List<Map<String, Object>> dishes, int max) {
        int n = Math.min(dishes.size(), max);
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append("、");
            }
            Map<String, Object> d = dishes.get(i);
            String name = d == null ? "" : nz(d.get("dishName"));
            sb.append(StringUtils.hasText(name) ? name.trim() : "（未命名）");
        }
    }

    private static void appendActions(StringBuilder sb, MenuOperationAnswerPlan plan) {
        List<MenuOperationRecommendedAction> actions = plan.getRecommendedActions();
        if (actions == null || actions.isEmpty()) {
            sb.append("暂无带证据的建议动作。");
            return;
        }
        int limit = Math.min(actions.size(), 6);
        for (int i = 0; i < limit; i++) {
            MenuOperationRecommendedAction a = actions.get(i);
            if (a == null || !StringUtils.hasText(a.getActionCode())) {
                continue;
            }
            sb.append(i + 1).append(". ").append(actionLabel(a.getActionCode()));
            if (StringUtils.hasText(a.getRationaleKey())) {
                sb.append("（").append(rationaleLabel(a.getRationaleKey().trim())).append("）");
            }
            sb.append('\n');
        }
    }

    private static String rationaleLabel(String key) {
        return switch (key) {
            case "NEGATIVE_ACTUAL_PROFIT" -> "实际利润为负，存在亏损";
            case "HIGH_SALES_LOW_MARGIN" -> "卖得多但毛利偏低，建议降本复核";
            case "LOW_MARGIN_FALLBACK" -> "毛利率相对偏低，建议复核成本结构";
            case "LOW_MARGIN_OR_LOSS" -> "毛利率偏低或实际利润为负";
            case "HEAD_PROFIT_DISH" -> "利润贡献领先";
            default -> key;
        };
    }

    private static String actionLabel(String code) {
        return switch (code.trim()) {
            case MenuOperationRecommendedAction.KEEP_AND_PROMOTE -> "继续主推";
            case MenuOperationRecommendedAction.RAISE_PRICE -> "考虑调价";
            case MenuOperationRecommendedAction.REDUCE_COST -> "压降成本";
            case MenuOperationRecommendedAction.CONSIDER_DROP -> "评估下架";
            case MenuOperationRecommendedAction.RECIPE_REVIEW -> "复核配方";
            case MenuOperationRecommendedAction.CHECK_STOCK_REDUCE -> "关注损耗";
            default -> code;
        };
    }

    private static String nz(Object v) {
        return v == null ? "" : v.toString().trim();
    }
}
