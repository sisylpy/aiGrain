package com.nongxinle.ai.inventory;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.context.AiResolvedTimeWindowDisplaySupport;
import com.nongxinle.ai.graph.business.DishIngredientCoverSalesBaseline;
import com.nongxinle.ai.graph.business.DishIngredientCoverSalesBaselineSupport;
import org.springframework.util.StringUtils;

import java.time.LocalDate;

/**
 * Cover-days 销量基线对用户可见的时间口径（AnswerPlan / 卡片 / Composer 唯一入口）。
 * <p>
 * 只消费 Time Layer / {@link DishIngredientCoverSalesBaseline} 已有结构化字段（timeType、起止日、baselineSource），
 * 不读 rawMessage；禁止按日期跨度、endDate=anchor 或 baselineDays 形状推断「最近 N 天」（仅 Time Layer
 * 正式标签 {@code ROLLING_7} 可出滚动窗文案；泛化滚动 N 天/V2 仍标 {@code CUSTOM}，见 periodKind 技术债）。
 */
public final class CoverDaysSalesBaselinePresentationSupport {

    private CoverDaysSalesBaselinePresentationSupport() {}

    /** AnswerPlan / 卡片 / Composer 共用的销量基线展示行。 */
    public static String formatSalesBaselineDisplayLabel(
            AiResolvedQueryContext rq, DishIngredientCoverSalesBaseline baseline) {
        String phrase = formatPeriodPhrase(rq, baseline);
        if (!StringUtils.hasText(phrase)) {
            return "销量基线：最近7天";
        }
        return "销量基线：" + phrase.trim();
    }

    /**
     * 无销量时的统一口径（菜品/原料共用结构）。
     * 例：皮蛋豆腐在上个月（2026-05-01～2026-05-31）没有销量，暂不能按该时段的销售节奏估算可用天数。
     */
    public static String composeNoSalesCannotEstimateNote(
            String entityDisplayName,
            AiResolvedQueryContext rq,
            DishIngredientCoverSalesBaseline baseline) {
        String entity =
                StringUtils.hasText(entityDisplayName) ? entityDisplayName.trim() : "该菜品";
        String period = formatPeriodPhrase(rq, baseline);
        return entity + "在" + period + "没有销量，暂不能按该时段的销售节奏估算可用天数。";
    }

    /** AnswerPlan 已投影 {@code salesBaselinePeriodPhrase} 时的无销量 fallback。 */
    public static String composeNoSalesCannotEstimateNote(
            String entityDisplayName, String salesBaselinePeriodPhrase) {
        String entity =
                StringUtils.hasText(entityDisplayName) ? entityDisplayName.trim() : "该菜品";
        String period = defaultPeriodPhraseOr(salesBaselinePeriodPhrase);
        return entity + "在" + period + "没有销量，暂不能按该时段的销售节奏估算可用天数。";
    }

    /** boundary / summary 用：仅时段短语，不含「销量基线：」前缀。 */
    public static String formatPeriodPhrase(
            AiResolvedQueryContext rq, DishIngredientCoverSalesBaseline baseline) {
        if (baseline == null) {
            return defaultRollingPhrase(DishIngredientCoverSalesBaseline.DEFAULT_BASELINE_DAYS, null, null);
        }
        LocalDate start = parseIsoDate(baseline.getStartDateIso());
        LocalDate end = parseIsoDate(baseline.getStopDateIso());
        String baselineSource = baseline.getBaselineSource();
        String timeType = resolveNormalizedTimeType(rq);

        if (DishIngredientCoverSalesBaseline.SOURCE_DEFAULT_LAST_7_DAYS.equals(baselineSource)) {
            return defaultRollingPhrase(DishIngredientCoverSalesBaseline.DEFAULT_BASELINE_DAYS, start, end);
        }

        if (AiResolvedTimeWindow.ROLLING_7.equals(timeType)) {
            return rollingDaysPhrase(DishIngredientCoverSalesBaseline.DEFAULT_BASELINE_DAYS, start, end);
        }

        if (AiResolvedTimeWindow.LAST_MONTH.equals(timeType) || "PREVIOUS_MONTH".equals(timeType)) {
            return AiResolvedTimeWindowDisplaySupport.formatDisplayRange("上个月", start, end);
        }

        if (AiResolvedTimeWindow.THIS_MONTH.equals(timeType)) {
            return AiResolvedTimeWindowDisplaySupport.formatDisplayRange("本月至今", start, end);
        }

        if (AiResolvedTimeWindow.TODAY.equals(timeType)) {
            return formatSingleDayPhrase("今天", start, end);
        }

        if (AiResolvedTimeWindow.YESTERDAY.equals(timeType)) {
            return formatSingleDayPhrase("昨天", start, end);
        }

        if (AiResolvedTimeWindow.CUSTOM.equals(timeType) || !StringUtils.hasText(timeType)) {
            return isoRangePeriodPhrase(start, end);
        }

        String cn = AiResolvedTimeWindowDisplaySupport.labelDisplayCn(timeType);
        if (cn != null) {
            return AiResolvedTimeWindowDisplaySupport.formatDisplayRange(cn, start, end);
        }

        return isoRangePeriodPhrase(start, end);
    }

    public static String formatPeriodPhraseFromContext(AiResolvedQueryContext rq) {
        if (rq == null) {
            return defaultRollingPhrase(DishIngredientCoverSalesBaseline.DEFAULT_BASELINE_DAYS, null, null);
        }
        DishIngredientCoverSalesBaseline baseline = DishIngredientCoverSalesBaselineSupport.resolve(null, rq);
        return formatPeriodPhrase(rq, baseline);
    }

    public static String readPeriodPhraseFromPlanSummary(java.util.Map<String, Object> summary) {
        if (summary == null) {
            return null;
        }
        Object raw = summary.get("salesBaselinePeriodPhrase");
        if (raw != null && StringUtils.hasText(raw.toString())) {
            return raw.toString().trim();
        }
        Object label = summary.get("salesBaselineLabel");
        if (label != null && StringUtils.hasText(label.toString())) {
            String text = label.toString().trim();
            if (text.startsWith("销量基线：")) {
                return text.substring("销量基线：".length()).trim();
            }
            return text;
        }
        return null;
    }

    public static String readNoSalesBaselineNoteFromPlanSummary(java.util.Map<String, Object> summary) {
        if (summary == null) {
            return null;
        }
        Object raw = summary.get("noSalesBaselineNote");
        if (raw != null && StringUtils.hasText(raw.toString())) {
            return raw.toString().trim();
        }
        return null;
    }

    public static String defaultPeriodPhraseOr(String salesBaselinePeriodPhrase) {
        return StringUtils.hasText(salesBaselinePeriodPhrase)
                ? salesBaselinePeriodPhrase.trim()
                : "最近7天";
    }

    /**
     * Cover-days 成功正文：消费 AnswerPlan {@code salesBaselinePeriodPhrase}，禁止固定写「当前销量」。
     */
    public static String composeCoverDaysSuccessPreview(
            String entityDisplayName,
            String salesBaselinePeriodPhrase,
            String coverDays,
            String bottleneckName) {
        String entity = StringUtils.hasText(entityDisplayName) ? entityDisplayName.trim() : "";
        String period = defaultPeriodPhraseOr(salesBaselinePeriodPhrase);
        String days = coverDays == null ? "" : coverDays.trim();
        String core = entity + "按" + period + "销量与库存，大约还能支撑 " + days + " 天";
        if (StringUtils.hasText(bottleneckName)) {
            return core
                    + "；最先不够的是「"
                    + bottleneckName.trim()
                    + "」。详情见下方卡片。";
        }
        return core + "。详情见下方卡片。";
    }

    /** 原料关联菜品 cover-days 成功正文。 */
    public static String composeGoodsCoverDaysSuccessPreview(
            String goodsDisplayLabel,
            String salesBaselinePeriodPhrase,
            String firstImpactedDishName,
            String coverDays) {
        String goods = StringUtils.hasText(goodsDisplayLabel) ? goodsDisplayLabel.trim() : "该原料";
        String period = defaultPeriodPhraseOr(salesBaselinePeriodPhrase);
        return goods
                + "按"
                + period
                + "销量与当前库存，最先受影响的是「"
                + firstImpactedDishName.trim()
                + "」（约 "
                + coverDays.trim()
                + " 天）。详情见下方卡片。";
    }

    /** 无销量正文后接卡片引导；去掉 lead 句末「。」再拼「；」，避免「。；」。 */
    public static String composeNoSalesWithIngredientCardHint(String noSalesNote) {
        if (!StringUtils.hasText(noSalesNote)) {
            return "下方卡片仍可查看各配料当前库存。";
        }
        return joinClauses(noSalesNote.trim(), "下方卡片仍可查看各配料当前库存。");
    }

    /** 分句拼接：lead 去掉句末「。」/「；」后，用「；」连接 continuation。 */
    public static String joinClauses(String lead, String continuation) {
        if (!StringUtils.hasText(lead)) {
            return StringUtils.hasText(continuation) ? continuation.trim() : "";
        }
        if (!StringUtils.hasText(continuation)) {
            return lead.trim();
        }
        return stripTrailingSentencePunctuation(lead) + "；" + continuation.trim();
    }

    static String stripTrailingSentencePunctuation(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        String s = text.trim();
        while (s.endsWith("。") || s.endsWith("；") || s.endsWith(";")) {
            s = s.substring(0, s.length() - 1).trim();
        }
        return s;
    }

    private static String resolveNormalizedTimeType(AiResolvedQueryContext rq) {
        if (rq != null && StringUtils.hasText(rq.getSalesBaselineTimeType())) {
            return AiResolvedTimeWindow.normalizeSemanticTimeTypeLabel(rq.getSalesBaselineTimeType());
        }
        if (rq == null || rq.getTimeWindow() == null) {
            return null;
        }
        return AiResolvedTimeWindow.normalizeSemanticTimeTypeLabel(rq.getTimeWindow().getTimeLabel());
    }

    private static String rollingDaysPhrase(int days, LocalDate start, LocalDate end) {
        return AiResolvedTimeWindowDisplaySupport.formatDisplayRange("最近" + days + "天", start, end);
    }

    private static String defaultRollingPhrase(int days, LocalDate start, LocalDate end) {
        if (start != null && end != null) {
            return rollingDaysPhrase(days, start, end);
        }
        return "最近" + days + "天";
    }

    private static String formatSingleDayPhrase(String subject, LocalDate start, LocalDate end) {
        LocalDate day = end != null ? end : start;
        if (day == null) {
            return subject;
        }
        return AiResolvedTimeWindowDisplaySupport.formatDisplayRange(subject, day, day);
    }

    private static String isoRangePeriodPhrase(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            return "该统计区间";
        }
        if (start.equals(end)) {
            return start.toString();
        }
        return start + "～" + end + "期间";
    }

    private static LocalDate parseIsoDate(String iso) {
        if (!StringUtils.hasText(iso)) {
            return null;
        }
        try {
            return LocalDate.parse(iso.trim());
        } catch (Exception ignored) {
            return null;
        }
    }
}
