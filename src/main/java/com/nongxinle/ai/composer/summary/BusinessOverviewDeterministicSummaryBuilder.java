package com.nongxinle.ai.composer.summary;

import com.nongxinle.ai.dto.business.DailyRevenueAnswerPlan;
import com.nongxinle.ai.util.AiNumericPlainText;
import com.nongxinle.ai.util.AiTimeWindowTextFormatter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Deterministic prose from {@link DailyRevenueAnswerPlan} for Answer Composer / MULTI_AGENT revenue blocks.
 * Reads structured AnswerPlan only.
 */
public final class BusinessOverviewDeterministicSummaryBuilder {

    private BusinessOverviewDeterministicSummaryBuilder() {
    }

    /**
     * 营业额 AnswerPlan：仅宣读 {@link DailyRevenueAnswerPlan} 的 focusRows / secondaryRows，不重算、不重排。
     * <p>
     * 非 {@link DailyRevenueAnswerPlan#TYPE_REVENUE_OVERVIEW} 且本轮无可用 metric 行时，返回确定性「指标不可用」话术。
     *
     * @return 可展示的确定性正文；{@code null} 表示 plan/planType 缺失或 compose 无法宣读，由 Composer no-plan 兜底。
     */
    public static String composeRevenueDeterministicFromAnswerPlan(DailyRevenueAnswerPlan plan,
            AiTimeWindowTextFormatter.UserPhrases tw) {
        if (plan == null || plan.getPlanType() == null || plan.getPlanType().isBlank()) {
            return null;
        }
        AiTimeWindowTextFormatter.UserPhrases p =
                tw != null ? tw : AiTimeWindowTextFormatter.fromIsoRange(null, null, java.time.LocalDate.now());
        String type = plan.getPlanType().trim();

        if (!DailyRevenueAnswerPlan.TYPE_REVENUE_OVERVIEW.equals(type)
                && revenuePlanMissingMetricRows(plan)) {
            return composeRevenueEmptyFocusDeterministic(plan, p);
        }

        if (DailyRevenueAnswerPlan.TYPE_REVENUE_OVERVIEW.equals(type)) {
            return composeRevenueOverviewFromPlan(plan, p);
        }
        if (DailyRevenueAnswerPlan.TYPE_REVENUE_DINE_IN_OVERVIEW.equals(type)) {
            return composeRevenueSingleChannelOverviewFromPlan(plan, p, "堂食");
        }
        if (DailyRevenueAnswerPlan.TYPE_REVENUE_TAKEOUT_OVERVIEW.equals(type)) {
            return composeRevenueSingleChannelOverviewFromPlan(plan, p, "外卖");
        }
        if (DailyRevenueAnswerPlan.TYPE_REVENUE_ORDER_COUNT_OVERVIEW.equals(type)) {
            return composeRevenueOrderCountFromPlan(plan, p);
        }
        if (DailyRevenueAnswerPlan.TYPE_REVENUE_AVERAGE_ORDER_VALUE.equals(type)) {
            return composeRevenueAovFromPlan(plan, p);
        }
        if (DailyRevenueAnswerPlan.TYPE_REVENUE_DAILY_AMOUNT_RANKING.equals(type)) {
            return composeRevenueDailyRankingFromPlan(plan, p);
        }
        if (DailyRevenueAnswerPlan.TYPE_REVENUE_CHANNEL_BREAKDOWN.equals(type)) {
            return composeRevenueChannelBreakdownFromPlan(plan, p);
        }
        if (DailyRevenueAnswerPlan.TYPE_REVENUE_STORE_AMOUNT_RANKING.equals(type)) {
            return composeRevenueStoreRankingFromPlan(plan, p);
        }
        if (DailyRevenueAnswerPlan.TYPE_REVENUE_PLATFORM_RANKING.equals(type)) {
            return composeRevenueSingleChannelOverviewFromPlan(plan, p, "外卖");
        }
        return null;
    }

    private static void appendPlainValue(StringBuilder sb, Object v) {
        if (v == null || v.toString().isBlank()) {
            sb.append("暂无");
            return;
        }
        String raw = v.toString().trim();
        if ("-".equals(raw) || "—".equals(raw) || "不适用".equals(raw)) {
            sb.append(raw);
            return;
        }
        sb.append(AiNumericPlainText.plainNumber(v));
    }

    private static String plainNumericHint(Object v) {
        if (v == null) {
            return "暂无";
        }
        if (v instanceof BigDecimal bd) {
            return AiNumericPlainText.plainNumber(bd);
        }
        if (v instanceof Number n) {
            return AiNumericPlainText.plainNumber(n);
        }
        String s = v.toString().trim();
        return s.isEmpty() ? "暂无" : s;
    }

    private static int intHint(Object v) {
        if (v == null) {
            return 0;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(v.toString().trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static double parseDoubleLoose(Object v) {
        if (v == null) {
            return 0;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(v.toString().trim());
        } catch (Exception e) {
            return 0;
        }
    }

    /** REVENUE_CHANNEL_BREAKDOWN 允许仅用 secondaryRows；其它类型只看 focusRows。 */
    private static boolean revenuePlanMissingMetricRows(DailyRevenueAnswerPlan plan) {
        if (plan == null || plan.getPlanType() == null || plan.getPlanType().isBlank()) {
            return true;
        }
        String type = plan.getPlanType().trim();
        if (DailyRevenueAnswerPlan.TYPE_REVENUE_CHANNEL_BREAKDOWN.equals(type)) {
            boolean frEmpty = plan.getFocusRows() == null || plan.getFocusRows().isEmpty();
            boolean secEmpty = plan.getSecondaryRows() == null || plan.getSecondaryRows().isEmpty();
            return frEmpty && secEmpty;
        }
        return plan.getFocusRows() == null || plan.getFocusRows().isEmpty();
    }

    /**
     * AnswerPlan 已为细分营收类型但 focus（及渠道拆分所需的 secondary）为空：朗读不可用说明，禁止套用总营业额信封。
     */
    private static String composeRevenueEmptyFocusDeterministic(DailyRevenueAnswerPlan plan,
            AiTimeWindowTextFormatter.UserPhrases tw) {
        String type = plan.getPlanType().trim();
        Map<String, Object> dbg = plan.getDebug();
        String failureReason = dbg != null && dbg.get("failureReason") != null
                ? String.valueOf(dbg.get("failureReason")).trim()
                : "";

        String suffix;
        if (DailyRevenueAnswerPlan.TYPE_REVENUE_CUSTOMER_COUNT_OVERVIEW.equals(type)) {
            suffix = "当前口径下暂未查询到顾客数数据；RevenueQueryTool 未返回顾客数字段。";
        } else if (DailyRevenueAnswerPlan.TYPE_REVENUE_AVERAGE_ORDER_VALUE.equals(type)) {
            if ("missing_average_order_value".equals(failureReason)) {
                suffix = "当前口径下订单数或营业额为 0，暂无法计算客单价。";
            } else {
                suffix = "当前口径下暂无法计算客单价。";
            }
        } else if (DailyRevenueAnswerPlan.TYPE_REVENUE_STORE_AMOUNT_RANKING.equals(type)
                && "missing_store_ranking".equals(failureReason)) {
            suffix = "当前口径下暂未查询到门店营业额排行数据。";
        } else if (DailyRevenueAnswerPlan.TYPE_REVENUE_CHANNEL_BREAKDOWN.equals(type)
                && failureReason != null && !failureReason.isBlank()) {
            suffix = "当前口径下暂未查询到堂食与外卖的有效拆分数据。";
        } else if (!failureReason.isBlank()) {
            suffix = "当前口径下暂未查询到该指标的有效数据（数据来源不齐）。";
        } else {
            suffix = "当前口径下暂未查询到该指标的有效数据。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(tw.getBracketTimeRangeLine()).append('\n');
        sb.append(revenuePlanLead(plan, tw)).append("，").append(suffix);
        return sb.toString();
    }

    private static String revenuePlanLead(DailyRevenueAnswerPlan plan, AiTimeWindowTextFormatter.UserPhrases tw) {
        String lead = tw.getDisplayTimeRange();
        String scope = plan.getScopeLabel();
        if (scope != null && !scope.isBlank()) {
            lead = scope.trim() + "；" + lead;
        }
        return lead;
    }

    private static String composeRevenueOverviewFromPlan(DailyRevenueAnswerPlan plan,
            AiTimeWindowTextFormatter.UserPhrases tw) {
        List<Map<String, Object>> focus = plan.getFocusRows();
        if (focus == null || focus.isEmpty()) {
            return null;
        }
        Map<String, Object> row0 = focus.get(0);
        String total = plainNumericHint(row0.get("totalRevenue"));
        int days = intHint(row0.get("days"));
        String avg = plainNumericHint(row0.get("avgDailyRevenue"));
        StringBuilder sb = new StringBuilder();
        sb.append(tw.getBracketTimeRangeLine()).append('\n');
        sb.append(revenuePlanLead(plan, tw)).append("，营业额合计 ").append(total).append(" 元");
        if (days > 0) {
            sb.append("（录入营业额的自然日 ").append(days).append(" 天");
            if (parseDoubleLoose(row0.get("avgDailyRevenue")) > 0) {
                sb.append("，日均约 ").append(avg).append(" 元");
            }
            sb.append("）");
        }
        sb.append("。");
        appendRevenueChannelBreakdownSentence(plan.getSecondaryRows(), sb);
        return sb.toString();
    }

    private static String composeRevenueSingleChannelOverviewFromPlan(DailyRevenueAnswerPlan plan,
            AiTimeWindowTextFormatter.UserPhrases tw, String channelPhrase) {
        List<Map<String, Object>> focus = plan.getFocusRows();
        if (focus == null || focus.isEmpty()) {
            return null;
        }
        String amt = plainNumericHint(focus.get(0).get("revenueAmount"));
        boolean explainTakeoutAggregate = plan.getDebug() != null
                && Boolean.TRUE.equals(plan.getDebug().get("explainTakeoutChannelAggregateOnly"))
                && "外卖".equals(channelPhrase);
        StringBuilder sb = new StringBuilder();
        sb.append(tw.getBracketTimeRangeLine()).append('\n');
        if (explainTakeoutAggregate) {
            sb.append(revenuePlanLead(plan, tw)).append("，当前营业额数据未区分具体外卖平台，仅统计外卖渠道合计。");
            sb.append("本期外卖营业额为 ").append(amt).append(" 元。");
        } else {
            sb.append(revenuePlanLead(plan, tw)).append("，").append(channelPhrase).append("营业额合计 ")
                    .append(amt).append(" 元。");
        }
        return sb.toString();
    }

    private static String composeRevenueOrderCountFromPlan(DailyRevenueAnswerPlan plan,
            AiTimeWindowTextFormatter.UserPhrases tw) {
        List<Map<String, Object>> focus = plan.getFocusRows();
        if (focus == null || focus.isEmpty()) {
            return null;
        }
        String cnt = plainNumericHint(focus.get(0).get("orderCount"));
        StringBuilder sb = new StringBuilder();
        sb.append(tw.getBracketTimeRangeLine()).append('\n');
        sb.append(revenuePlanLead(plan, tw)).append("，订单数合计 ").append(cnt).append(" 单。");
        return sb.toString();
    }

    private static String composeRevenueAovFromPlan(DailyRevenueAnswerPlan plan,
            AiTimeWindowTextFormatter.UserPhrases tw) {
        List<Map<String, Object>> focus = plan.getFocusRows();
        if (focus == null || focus.isEmpty()) {
            return null;
        }
        String aov = plainNumericHint(focus.get(0).get("averageOrderValue"));
        StringBuilder sb = new StringBuilder();
        sb.append(tw.getBracketTimeRangeLine()).append('\n');
        sb.append(revenuePlanLead(plan, tw)).append("，客单价约 ").append(aov).append(" 元。");
        Map<String, Object> summary = plan.getSummary();
        if (summary != null && summary.get("totalOrders") != null) {
            sb.append("（按订单数 ").append(plainNumericHint(summary.get("totalOrders"))).append(" 单均摊口径。）");
        }
        return sb.toString();
    }

    private static String composeRevenueDailyRankingFromPlan(DailyRevenueAnswerPlan plan,
            AiTimeWindowTextFormatter.UserPhrases tw) {
        List<Map<String, Object>> focus = plan.getFocusRows();
        if (focus == null || focus.isEmpty()) {
            return null;
        }
        Map<String, Object> row = focus.get(0);
        String amt = plainNumericHint(row.get("revenueAmount"));
        boolean asc = "ASC".equals(String.valueOf(row.get("sortDirection")));
        StringBuilder sb = new StringBuilder();
        sb.append(tw.getBracketTimeRangeLine()).append('\n');
        sb.append(revenuePlanLead(plan, tw)).append("，");
        if (asc) {
            sb.append("周期内单日营业额低谷约为 ").append(amt).append(" 元");
        } else {
            sb.append("周期内单日营业额峰值约为 ").append(amt).append(" 元");
        }
        sb.append("（区间为已汇总的最大/最小单日金额口径；若需具体日期请在后台按日明细核对）。");
        List<Map<String, Object>> sec = plan.getSecondaryRows();
        if (sec != null && !sec.isEmpty()) {
            Map<String, Object> alt = sec.get(0);
            if (alt != null && alt.get("revenueAmount") != null) {
                String other = plainNumericHint(alt.get("revenueAmount"));
                if (asc) {
                    sb.append("相对地，区间内较高的单日金额约为 ").append(other).append(" 元。");
                } else {
                    sb.append("相对地，区间内较低的单日金额约为 ").append(other).append(" 元。");
                }
            }
        }
        return sb.toString();
    }

    private static String composeRevenueStoreRankingFromPlan(DailyRevenueAnswerPlan plan,
            AiTimeWindowTextFormatter.UserPhrases tw) {
        List<Map<String, Object>> focus = plan.getFocusRows();
        if (focus == null || focus.isEmpty()) {
            return null;
        }
        Map<String, Object> row = focus.get(0);
        Map<String, Object> dbg = plan.getDebug();
        boolean asc = dbg != null && "ASC".equals(String.valueOf(dbg.get("sortDirection")));
        String storeName = row.get("storeName") != null ? String.valueOf(row.get("storeName")).trim() : "";
        if (storeName.isBlank()) {
            Object sid = row.get("storeDepartmentId");
            storeName = sid != null ? ("门店 " + sid) : "该门店";
        }
        String amt = plainNumericHint(row.get("revenueAmount"));
        StringBuilder sb = new StringBuilder();
        sb.append(tw.getBracketTimeRangeLine()).append('\n');
        sb.append(revenuePlanLead(plan, tw)).append("，");
        if (asc) {
            sb.append("本期营业额最低的门店是 ").append(storeName).append("，营业额 ").append(amt).append(" 元。");
        } else {
            sb.append("本期营业额最高的门店是 ").append(storeName).append("，营业额 ").append(amt).append(" 元。");
        }
        List<Map<String, Object>> sec = plan.getSecondaryRows();
        if (sec != null && !sec.isEmpty()) {
            sb.append("其后依次为：");
            List<String> parts = new ArrayList<>();
            for (Map<String, Object> r : sec) {
                if (r == null) {
                    continue;
                }
                String nm = r.get("storeName") != null ? String.valueOf(r.get("storeName")).trim() : "";
                if (nm.isBlank()) {
                    Object sid = r.get("storeDepartmentId");
                    nm = sid != null ? ("门店 " + sid) : "";
                }
                String ra = plainNumericHint(r.get("revenueAmount"));
                if (!nm.isBlank()) {
                    parts.add(nm + " " + ra + " 元");
                }
            }
            if (!parts.isEmpty()) {
                sb.append(String.join("、", parts)).append("。");
            }
        }
        return sb.toString();
    }

    private static String composeRevenueChannelBreakdownFromPlan(DailyRevenueAnswerPlan plan,
            AiTimeWindowTextFormatter.UserPhrases tw) {
        List<Map<String, Object>> all = new ArrayList<>();
        if (plan.getFocusRows() != null) {
            all.addAll(plan.getFocusRows());
        }
        if (plan.getSecondaryRows() != null) {
            all.addAll(plan.getSecondaryRows());
        }
        if (all.isEmpty()) {
            return null;
        }
        String dineAmt = null;
        String takeAmt = null;
        List<Map<String, Object>> extras = new ArrayList<>();
        for (Map<String, Object> r : all) {
            Object ch = r.get("channel");
            if (ch == null) {
                continue;
            }
            String chs = String.valueOf(ch);
            Object amt = r.get("revenueAmount");
            if (amt == null) {
                amt = r.get("feeAmount");
            }
            if (amt == null) {
                continue;
            }
            String plain = plainNumericHint(amt);
            if ("DINE_IN".equals(chs) || DailyRevenueAnswerPlan.CHANNEL_DINE_IN.equals(chs)) {
                dineAmt = plain;
            } else if ("TAKEOUT".equals(chs) || DailyRevenueAnswerPlan.CHANNEL_TAKEOUT.equals(chs)) {
                takeAmt = plain;
            } else {
                extras.add(r);
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append(tw.getBracketTimeRangeLine()).append('\n');
        sb.append(revenuePlanLead(plan, tw)).append("，");
        if (dineAmt != null && takeAmt != null) {
            sb.append("堂食营业额 ").append(dineAmt).append(" 元，外卖营业额 ").append(takeAmt).append(" 元");
            List<String> tailParts = new ArrayList<>();
            for (Map<String, Object> r : extras) {
                Object ch = r.get("channel");
                String label = revenueChannelLabelCn(String.valueOf(ch));
                Object amt = r.get("revenueAmount");
                if (amt == null) {
                    amt = r.get("feeAmount");
                }
                if (amt == null) {
                    continue;
                }
                tailParts.add(label + " " + plainNumericHint(amt) + " 元");
            }
            if (!tailParts.isEmpty()) {
                sb.append("；").append(String.join("；", tailParts));
            }
            sb.append("。");
            return sb.toString();
        }
        sb.append("渠道拆分：");
        List<String> parts = new ArrayList<>();
        for (Map<String, Object> r : all) {
            Object ch = r.get("channel");
            if (ch == null) {
                continue;
            }
            String label = revenueChannelLabelCn(String.valueOf(ch));
            Object amt = r.get("revenueAmount");
            if (amt == null) {
                amt = r.get("feeAmount");
            }
            if (amt == null) {
                continue;
            }
            parts.add(label + " " + plainNumericHint(amt) + " 元");
        }
        if (parts.isEmpty()) {
            return null;
        }
        sb.append(String.join("；", parts)).append("。");
        return sb.toString();
    }

    private static void appendRevenueChannelBreakdownSentence(List<Map<String, Object>> secondaryRows,
            StringBuilder sb) {
        if (secondaryRows == null || secondaryRows.isEmpty()) {
            return;
        }
        List<String> parts = new ArrayList<>();
        for (Map<String, Object> r : secondaryRows) {
            Object ch = r.get("channel");
            if (ch == null) {
                continue;
            }
            String label = revenueChannelLabelCn(String.valueOf(ch));
            Object amt = r.get("revenueAmount");
            if (amt == null) {
                amt = r.get("feeAmount");
            }
            if (amt == null) {
                continue;
            }
            parts.add(label + "约 " + plainNumericHint(amt) + " 元");
        }
        if (!parts.isEmpty()) {
            sb.append("其中").append(String.join("、", parts)).append("。");
        }
    }

    private static String revenueChannelLabelCn(String channel) {
        if (channel == null || channel.isBlank()) {
            return channel;
        }
        if (DailyRevenueAnswerPlan.CHANNEL_DINE_IN.equals(channel) || "DINE_IN".equals(channel)) {
            return "堂食";
        }
        if (DailyRevenueAnswerPlan.CHANNEL_TAKEOUT.equals(channel) || "TAKEOUT".equals(channel)) {
            return "外卖";
        }
        if ("PLATFORM_FEE".equals(channel)) {
            return "平台相关费用";
        }
        return channel;
    }
}
