package com.nongxinle.ai.composer.summary;

import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.AiBusinessOverviewResult;
import com.nongxinle.ai.dto.business.DailyRevenueAnswerPlan;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.nongxinle.ai.util.AiNumericPlainText;
import com.nongxinle.ai.util.AiTimeWindowTextFormatter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Deterministic prose snippets for business-overview payloads and fallbacks:
 * revenue paragraph from {@link DailyRevenueAnswerPlan}, numeric headline from overview result / tool stats,
 * purchase one-liner from {@link PurchaseAnswerPlan}. Reads structured state only.
 */
public final class BusinessOverviewDeterministicSummaryBuilder {

    private BusinessOverviewDeterministicSummaryBuilder() {
    }

    /** 经营概览 + 已挂载日营收 AnswerPlan：确定性正文必须与 revenue_query 计划一致（禁止混用 business_overview_query 营业额/天数）。 */
    public static String businessOverviewResolvedRevenueParagraph(AiRunState state) {
        if (state == null || !state.isBusinessOverviewPath()) {
            return null;
        }
        DailyRevenueAnswerPlan rap = state.getRevenueAnswerPlan();
        if (rap == null) {
            return null;
        }
        AiTimeWindowTextFormatter.UserPhrases tw = AiTimeWindowTextFormatter.forAnswer(state);
        return composeRevenueDeterministicFromAnswerPlan(rap, tw);
    }

    public static boolean hasAuthoritativeBusinessOverviewRevenuePlan(AiRunState state) {
        String p = businessOverviewResolvedRevenueParagraph(state);
        return p != null && !p.isBlank();
    }

    /**
     * 经营概览最终摘要一句：取自 {@link PurchaseAnswerPlan} focus 行（与 purchase_overview 工具一致）。
     */
    public static String businessOverviewPurchaseCoreSentence(AiRunState state) {
        if (state == null || !state.isBusinessOverviewPath()) {
            return "";
        }
        PurchaseAnswerPlan pap = state.getPurchaseAnswerPlan();
        if (pap == null || pap.getPlanType() == null || pap.getPlanType().isBlank()
                || pap.getFocusRows() == null || pap.getFocusRows().isEmpty()) {
            return "";
        }
        String type = pap.getPlanType().trim();
        Map<String, Object> row = pap.getFocusRows().get(0);
        int cnt = intHint(row.get("purchaseOrderCount"));
        String amt = plainNumericHint(row.get("totalPurchaseAmount"));
        if (PurchaseAnswerPlan.TYPE_PURCHASE_SELF_OVERVIEW.equals(type)) {
            return "同期自采金额为 " + amt + " 元，共 " + cnt + " 笔自采入库。";
        }
        if (PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_OVERVIEW.equals(type)) {
            return "同期供货商渠道采购金额为 " + amt + " 元，共 " + cnt + " 笔供货商采购入库。";
        }
        if (PurchaseAnswerPlan.TYPE_PURCHASE_OVERVIEW.equals(type)) {
            return "同期采购入库总金额为 " + amt + " 元，共 " + cnt + " 笔。";
        }
        return "";
    }

    public static String extractOverviewNumericHeadline(AiRunState state, AiBusinessOverviewResult o) {
        Map<String, Object> st = o.getDashboardStatsCn();
        if (st == null || st.isEmpty()) {
            st = loadStatsFallbackFromTool(state);
        }
        if (st != null && !st.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            appendDistinctRevenueDayLead(sb, st);
            sb.append("，总营业额 ");
            appendPlainValue(sb, st.get("总营业额"));
            sb.append(" 元，日均营业额 ");
            appendPlainValue(sb, st.get("日均营业额"));
            sb.append(" 元，日均订单数 ");
            appendPlainValue(sb, st.get("日均订单数"));
            sb.append(" 单/天，客单价 ");
            appendPlainValue(sb, st.get("客单价"));
            sb.append(" 元。优惠券/平台费合计 ");
            appendPlainValue(sb, st.get("平台费合计"));
            sb.append(" 元，退款合计 ");
            appendPlainValue(sb, st.get("退款合计"));
            sb.append(" 元，外卖营业额合计 ");
            appendPlainValue(sb, st.get("外卖营业额合计"));
            sb.append(" 元");
            Object profit = st.get("盈亏状态");
            if (profit != null && !profit.toString().isBlank()) {
                String ps = profit.toString().trim();
                if (!"-".equals(ps) && !"—".equals(ps)) {
                    sb.append("。盈亏状态：").append(ps);
                }
            }
            sb.append("。");
            return sb.toString();
        }
        String fromSummary = o.getSummary();
        if (fromSummary != null && !fromSummary.isBlank()) {
            return fromSummary.trim();
        }
        return "暂无日营收经营看板数据，无法列出查询区间内具体数字。";
    }

    /**
     * 营业额 AnswerPlan：仅宣读 {@link DailyRevenueAnswerPlan} 的 focusRows / secondaryRows，不重算、不重排。
     * <p>
     * 非 {@link DailyRevenueAnswerPlan#TYPE_REVENUE_OVERVIEW} 且本轮无可用 metric 行时，返回确定性「指标不可用」话术，
     * 禁止返回 {@code null} 以免外层误走营业额工具信封兜底（总额口径污染细指标问法）。
     *
     * @return 可展示的确定性正文；{@code null} 仅表示(plan/planType 缺失)或 REVENUE_OVERVIEW 无行时可退回信封朗读。
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

    /**
     * 与 {@link com.nongxinle.service.impl.GbAiDailyRevenueDashboardServiceImpl#buildGroupWideIncomeFlattened}
     * 等指标一致：先说明本次查询日期边界，再说明「统计天数」是区间内有营业额入账的自然日数（非日历满跨度）。
     */
    private static void appendDistinctRevenueDayLead(StringBuilder sb, Map<String, Object> statsCn) {
        String qStart = trimStatDate(statsCn.get("统计开始日期"));
        String qEnd = trimStatDate(statsCn.get("统计结束日期"));
        if (!qStart.isEmpty() && !qEnd.isEmpty()) {
            if (qStart.equals(qEnd)) {
                sb.append(qStart).append(" 当日");
            } else {
                sb.append("所选区间 ").append(qStart).append("～").append(qEnd).append(" 内");
            }
        } else if (!qStart.isEmpty()) {
            sb.append("自 ").append(qStart).append(" 起");
        } else if (!qEnd.isEmpty()) {
            sb.append("截至 ").append(qEnd);
        } else {
            sb.append("本查询区间内");
        }
        sb.append("，录入营业额的自然日共 ");
        appendPlainValue(sb, statsCn.get("统计天数"));
        sb.append(" 天");
    }

    private static String trimStatDate(Object raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.toString().trim();
        return t.isBlank() ? "" : t;
    }

    private static Map<String, Object> loadStatsFallbackFromTool(AiRunState state) {
        Map<String, Object> bo = overviewToolData(state);
        @SuppressWarnings("unchecked")
        Map<String, Object> st = bo.get("stats") instanceof Map ? (Map<String, Object>) bo.get("stats") : Map.of();
        return st;
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> overviewToolData(AiRunState state) {
        Object env = state.getToolResults().get(AiBusinessToolIds.BUSINESS_OVERVIEW_QUERY);
        if (!(env instanceof Map)) {
            return Map.of();
        }
        Object data = ((Map<String, Object>) env).get("data");
        if (!(data instanceof Map)) {
            return Map.of();
        }
        return (Map<String, Object>) data;
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
