package com.nongxinle.ai.composer.menu;

import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuPortfolioCategory;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuPortfolioClassification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/** 菜单四象限 companion / deterministic / 卡片投影表达层：时间标签、分层口径与老板可读文案。 */
public final class MenuOperationPortfolioExpressionSupport {

    private static final String TIME_FALLBACK = "本轮";

    /** 四象限规则说明（面向老板，不含算法字段名）。 */
    public static final String PORTFOLIO_RULE_EXPLANATION =
            "系统会把本期参与销售的菜品放在一起比较，根据销量和利润贡献分成四类。";

    public static final String PORTFOLIO_CARD_SUBTITLE = PORTFOLIO_RULE_EXPLANATION;

    public static final String ELIMINATE_RECOMMENDED_ACTION =
            "销量和利润贡献暂时都不突出，建议先观察是否需要调整做法、价格或推荐位置。";

    private MenuOperationPortfolioExpressionSupport() {}

    public static String portfolioCardSubtitle() {
        return PORTFOLIO_CARD_SUBTITLE;
    }

    public static String portfolioRuleExplanation() {
        return PORTFOLIO_RULE_EXPLANATION;
    }

    /** 结论区：菜单结构分布一句概括。 */
    public static String formatPortfolioDistributionSummary(
            int total, int star, int traffic, int potential, int eliminate) {
        return "菜单结构：共分析 "
                + total
                + " 道菜，其中明星菜 "
                + star
                + " 道、引流菜 "
                + traffic
                + " 道、潜力菜 "
                + potential
                + " 道、观察菜 "
                + eliminate
                + " 道。";
    }

    /** 四象限章节：各类经营侧重点（不含算法口径）。 */
    public static String portfolioCategoryGuidance() {
        return "明星菜可优先保供并继续主推；引流菜关注成本与定价；潜力菜可加强曝光与推荐；"
                + "观察菜建议先观察是否需要调整做法、价格或推荐位置，不代表建议下架。";
    }

    public static String categoryRecommendedAction(String categoryCode) {
        if (!StringUtils.hasText(categoryCode)) {
            return "";
        }
        return switch (categoryCode.trim()) {
            case MenuOperationAnswerPlan.CATEGORY_STAR -> "稳定供应、继续主推";
            case MenuOperationAnswerPlan.CATEGORY_TRAFFIC -> "保留客流价值，优先复核成本，必要时小幅调价";
            case MenuOperationAnswerPlan.CATEGORY_POTENTIAL -> "增加曝光，放到推荐位，可尝试套餐搭配";
            case MenuOperationAnswerPlan.CATEGORY_ELIMINATE -> ELIMINATE_RECOMMENDED_ACTION;
            default -> "";
        };
    }

    public static String rewriteCategorySummary(
            String summary, String categoryName, int count, String ratio) {
        if (count <= 0) {
            return "本期暂无" + nzCategoryName(categoryName);
        }
        if (!StringUtils.hasText(summary)
                || summary.contains("相对分类")
                || summary.contains("中位")
                || summary.contains("阈值")) {
            return nzCategoryName(categoryName)
                    + " "
                    + count
                    + " 道，占本期分析菜品 "
                    + nz(ratio);
        }
        return summary.replace("本轮菜单内相对分类：", "")
                .replace("占分析菜品", "占本期分析菜品")
                .replace("淘汰菜", "观察菜");
    }

    /** 将 AnswerPlan 内工程口径 reason 转为老板可读说明。 */
    public static String rewritePortfolioDishReason(String reason, String categoryCode) {
        if (!StringUtils.hasText(reason) || containsTechnicalPortfolioWording(reason)) {
            return defaultDishReasonForCategory(categoryCode);
        }
        String rewritten = reason.trim();
        rewritten = rewritten.replace("销量高于本轮中位", "销量高于本期大多数菜");
        rewritten = rewritten.replace("销量低于本轮中位", "销量低于本期大多数菜");
        rewritten =
                rewritten.replace("实际利润不低于本轮中位（type123）", "利润贡献高于本期大多数菜");
        rewritten = rewritten.replace("实际利润低于本轮中位（type123）", "利润贡献低于本期大多数菜");
        rewritten = rewritten.replace("实际利润不低于本轮中位", "利润贡献高于本期大多数菜");
        rewritten = rewritten.replace("实际利润低于本轮中位", "利润贡献低于本期大多数菜");
        rewritten =
                rewritten.replace(
                        "；但实际利润为负，归入相对引流档而非明星档",
                        "；虽然卖得好，但利润贡献偏弱，归入引流菜");
        rewritten =
                rewritten.replace(
                        "；仍有正利润且毛利率未明显低于整体，建议先观察一个周期，可增加曝光或复核菜单位置",
                        "；建议先观察一个周期，可调整推荐位置或做法");
        rewritten =
                rewritten.replace(
                        "；毛利率明显低于整体或利润贡献偏弱，建议重点复核成本、定价与备货",
                        "；建议重点复核成本、定价与备货");
        rewritten =
                rewritten.replace(
                        "；实际利润为负；P1 无多期趋势，仅建议复核是否下架或调整配方，不宜单凭一轮数据武断淘汰",
                        "；当前利润为负，建议复核配方与定价后再决定是否调整");
        rewritten = rewritten.replace("相对观察档（低销量低利润）", "观察菜");
        rewritten = rewritten.replace("相对引流档", "引流菜");
        rewritten = rewritten.replace("type123", "");
        if (containsTechnicalPortfolioWording(rewritten)) {
            return defaultDishReasonForCategory(categoryCode);
        }
        return rewritten;
    }

    private static boolean containsTechnicalPortfolioWording(String text) {
        if (!StringUtils.hasText(text)) {
            return true;
        }
        String t = text;
        return t.contains("中位")
                || t.contains("阈值")
                || t.contains("median")
                || t.contains("salesHighThreshold")
                || t.contains("profitHighThreshold")
                || t.contains("thresholdMethod")
                || t.contains("相对")
                        && (t.contains("档") || t.contains("分层"))
                || t.contains("type123")
                || t.contains("P1 无多期");
    }

    private static String defaultDishReasonForCategory(String categoryCode) {
        if (!StringUtils.hasText(categoryCode)) {
            return "本期与其他菜品相比，销量和利润贡献各有差异。";
        }
        return switch (categoryCode.trim()) {
            case MenuOperationAnswerPlan.CATEGORY_STAR ->
                    "销量和利润贡献都高于本期大多数菜，适合继续主推。";
            case MenuOperationAnswerPlan.CATEGORY_TRAFFIC ->
                    "销量高于本期大多数菜，但利润贡献相对偏弱，适合保留客流并复核成本。";
            case MenuOperationAnswerPlan.CATEGORY_POTENTIAL ->
                    "利润贡献高于本期大多数菜，但销量低于本期大多数菜，适合加强曝光。";
            case MenuOperationAnswerPlan.CATEGORY_ELIMINATE ->
                    "销量和利润贡献都低于本期大多数菜，建议先观察是否需要调整做法、价格或推荐位置。";
            default -> "本期与其他菜品相比，销量和利润贡献各有差异。";
        };
    }

    private static String nzCategoryName(String categoryName) {
        return StringUtils.hasText(categoryName) ? categoryName.trim() : "该类菜品";
    }

    private static String nz(String value) {
        return value == null ? "" : value.trim();
    }

    public static String resolveTimePhrase(MenuOperationAnswerPlan plan) {
        if (plan != null && StringUtils.hasText(plan.getTimeLabel())) {
            return plan.getTimeLabel().trim();
        }
        return TIME_FALLBACK;
    }

    public static String composeOverviewDefaultHint(MenuOperationAnswerPlan plan) {
        String time = resolveTimePhrase(plan);
        return "已按"
                + time
                + "数据生成菜单结构分析，下方卡片展示"
                + time
                + "菜单四象限分布和各类菜品建议。";
    }

    /** 菜单经营 overview 卡片 companion 短导语（含并列最高处理）。 */
    public static String composeOverviewShortHint(MenuOperationAnswerPlan plan) {
        MenuPortfolioClassification portfolio = plan.getMenuPortfolioClassification();
        String time = resolveTimePhrase(plan);
        int total = portfolio.getTotalDishCount();
        List<MenuPortfolioCategory> categories = portfolio.getCategories();
        if (categories == null || categories.isEmpty()) {
            return time + "共分析 " + total + " 道菜。下方卡片可查看完整四象限分布。";
        }

        int maxCount = 0;
        for (MenuPortfolioCategory category : categories) {
            if (category != null && category.getCount() > maxCount) {
                maxCount = category.getCount();
            }
        }
        if (maxCount <= 0) {
            return time + "共分析 " + total + " 道菜。下方卡片可查看完整四象限分布。";
        }

        List<MenuPortfolioCategory> tiedAtTop = new ArrayList<>();
        for (MenuPortfolioCategory category : categories) {
            if (category != null && category.getCount() == maxCount) {
                tiedAtTop.add(category);
            }
        }

        if (tiedAtTop.size() > 1) {
            return time
                    + "共分析 "
                    + total
                    + " 道菜，"
                    + formatTiedDistribution(tiedAtTop, maxCount)
                    + "。下方卡片可查看完整分布。";
        }

        MenuPortfolioCategory dominant = tiedAtTop.get(0);
        String categoryName =
                StringUtils.hasText(dominant.getCategoryName())
                        ? dominant.getCategoryName().trim()
                        : "某一类";
        return time
                + "共分析 "
                + total
                + " 道菜，"
                + categoryName
                + "占比最高，"
                + dominantCategoryAdvice(dominant.getCategoryCode())
                + "下方卡片可查看完整四象限分布。";
    }

    public static String composePortfolioAnalysisLead(MenuOperationAnswerPlan plan, int totalDishCount) {
        return resolveTimePhrase(plan) + "共分析 " + totalDishCount + " 道菜";
    }

    private static String formatTiedDistribution(List<MenuPortfolioCategory> tied, int tiedCount) {
        if (tied.size() == 4) {
            return "四类各 " + tiedCount + " 道，结构比较平均";
        }
        StringBuilder names = new StringBuilder();
        for (MenuPortfolioCategory category : tied) {
            if (category == null || !StringUtils.hasText(category.getCategoryName())) {
                continue;
            }
            if (names.length() > 0) {
                names.append('、');
            }
            names.append(category.getCategoryName().trim());
        }
        if (names.length() == 0) {
            return "多类数量并列最高，结构比较平均";
        }
        return names + "各 " + tiedCount + " 道，结构比较平均";
    }

    static String dominantCategoryAdvice(String categoryCode) {
        if (!StringUtils.hasText(categoryCode)) {
            return "建议结合各类菜品占比制定调整重点。";
        }
        return switch (categoryCode.trim()) {
            case MenuOperationAnswerPlan.CATEGORY_TRAFFIC -> "建议优先关注引流菜成本与定价。";
            case MenuOperationAnswerPlan.CATEGORY_STAR -> "建议优先稳住明星菜供应与推荐位。";
            case MenuOperationAnswerPlan.CATEGORY_POTENTIAL -> "建议优先提升潜力菜曝光与转化。";
            case MenuOperationAnswerPlan.CATEGORY_ELIMINATE ->
                    "建议先观察这类菜是否需要调整做法、价格或推荐位置。";
            default -> "建议结合各类菜品占比制定调整重点。";
        };
    }
}
