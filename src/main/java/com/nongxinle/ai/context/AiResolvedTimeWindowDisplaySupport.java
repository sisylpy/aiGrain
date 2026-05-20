package com.nongxinle.ai.context;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.resolver.AiMultiTurnOrgScopePolicy;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将 Resolver 已解析完成的 {@link AiResolvedTimeWindow} 渲染为答案边界提示文案。
 * <p>
 * 不参与时间解析、时间继承或 {@code effectiveTimeWindowSource} 归因；仅消费 ctx 已有字段。
 * {@link #LABEL_DISPLAY_CN} 与相关方法<b>仅用于显示</b>，不参与时间解析和决策。
 */
public final class AiResolvedTimeWindowDisplaySupport {

    /**
     * LLM {@code timeType} 归一化 label → 中文短词；仅用于显示，不参与时间解析和决策。
     * 未命中时调用方应优先展示 {@code startDate～endDate}。
     */
    static final Map<String, String> LABEL_DISPLAY_CN;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(AiResolvedTimeWindow.TODAY, "今天");
        m.put(AiResolvedTimeWindow.YESTERDAY, "昨天");
        m.put(AiResolvedTimeWindow.THIS_WEEK, "本周");
        m.put(AiResolvedTimeWindow.THIS_MONTH, "本月至今");
        m.put(AiResolvedTimeWindow.LAST_MONTH, "上个月");
        m.put(AiResolvedTimeWindow.LAST_YEAR, "去年");
        m.put(AiResolvedTimeWindow.LAST_YEAR_SAME_PERIOD, "去年同期");
        m.put(AiResolvedTimeWindow.ROLLING_7, "最近7天");
        m.put(AiResolvedTimeWindow.YEAR_TO_DATE, "本年累计");
        LABEL_DISPLAY_CN = Collections.unmodifiableMap(m);
    }

    private AiResolvedTimeWindowDisplaySupport() {
    }

    /** label → 中文短词；未命中返回 {@code null}。 */
    public static String labelDisplayCn(String timeLabel) {
        if (!StringUtils.hasText(timeLabel)) {
            return null;
        }
        String key = AiResolvedTimeWindow.normalizeSemanticTimeTypeLabel(timeLabel);
        if (!StringUtils.hasText(key) || AiResolvedTimeWindow.CUSTOM.equals(key)) {
            return null;
        }
        return LABEL_DISPLAY_CN.get(key);
    }

    /** 答案展示用短词：优先 label 映射，否则「该统计区间」。 */
    public static String answerTimeSubject(AiResolvedTimeWindow tw) {
        if (tw == null) {
            return "该统计区间";
        }
        String cn = labelDisplayCn(tw.getTimeLabel());
        return cn != null ? cn : "该统计区间";
    }

    /** 「主题（起～止）」或单日「主题（日）」；缺日期时仅返回主题。 */
    public static String formatDisplayRange(String subject, LocalDate start, LocalDate end) {
        if (!StringUtils.hasText(subject)) {
            subject = "该统计区间";
        }
        if (start == null || end == null) {
            return subject;
        }
        if (start.equals(end)) {
            return subject + "（" + start + "）";
        }
        return subject + "（" + start + "～" + end + "）";
    }

    /** 基于已解析窗生成答案展示行（主题 + 括号日期）。 */
    public static String formatAnswerTimeRangeLine(AiResolvedTimeWindow tw) {
        if (tw == null) {
            return "该统计区间";
        }
        return formatDisplayRange(answerTimeSubject(tw), tw.getStartDate(), tw.getEndDate());
    }

    public static String buildCombinedBoundaryNote(
            String effectiveTimeWindowSource,
            String effectiveScopeSource,
            AiResolvedTimeWindow tw,
            AiResolvedOrgScope org,
            AiConversationTurnMemory previousTurn) {
        boolean timeInh = "INHERITED_PREVIOUS".equals(effectiveTimeWindowSource);
        boolean scopeInh = "INHERITED_PREVIOUS".equals(effectiveScopeSource);
        List<String> hints = new ArrayList<>();
        if (scopeInh) {
            AiMultiTurnOrgScopePolicy.singleVisibleStoreName(org).ifPresent(hints::add);
        }
        if (timeInh && tw != null) {
            hints.add(humanReadableTimeCarryover(tw));
        }
        if (!hints.isEmpty()) {
            return "按上文「" + String.join(" + ", hints) + "」口径查询；本句未指定新的时间和门店。若需调整请直接说明。";
        }
        return buildAnswerBoundaryNote(effectiveTimeWindowSource, tw, previousTurn);
    }

    /**
     * @param previousTurn 用于判断是否在多轮会话中「本可继承却落到默认本月」等需提示场景；首轮单句为 null 时不输出 DEFAULT 提示以减少干扰。
     */
    public static String buildAnswerBoundaryNote(
            String effectiveTimeWindowSource,
            AiResolvedTimeWindow tw,
            AiConversationTurnMemory previousTurn) {
        if (tw == null || effectiveTimeWindowSource == null) {
            return null;
        }
        if ("INHERITED_PREVIOUS".equals(effectiveTimeWindowSource)) {
            String hint = humanReadableTimeCarryover(tw);
            return "按上文「" + hint + "」口径查询；本句未指定新的统计时间。若需其他时间段（例如本月），可直接说明。";
        }
        if ("DEFAULT_MONTH_TO_DATE".equals(effectiveTimeWindowSource) && previousTurn != null) {
            return "本句未指定统计时间，已按本月至今口径查询；若需其他时间段可说明。";
        }
        return null;
    }

    public static String humanReadableTimeCarryover(AiResolvedTimeWindow tw) {
        if (tw == null) {
            return "时间范围";
        }
        String cn = labelDisplayCn(tw.getTimeLabel());
        if (cn != null) {
            return cn;
        }
        if (tw.getStartDate() != null && tw.getEndDate() != null) {
            return tw.getStartDate() + "～" + tw.getEndDate();
        }
        String d = tw.getDisplayText();
        return d != null && !d.isBlank() ? d : "上文时间范围";
    }
}
