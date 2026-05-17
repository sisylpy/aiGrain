package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiUserContext;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.dto.business.AiBusinessOverviewResult;
import com.nongxinle.ai.dto.business.AiGroupOverviewCoveredStoresBrief;
import com.nongxinle.ai.dto.business.AiGroupOverviewStoreBrief;
import com.nongxinle.ai.dto.business.AiOverviewCoveredStoreItem;
import com.nongxinle.ai.dto.business.AiOverviewStoreIssueItem;
import com.nongxinle.ai.dto.business.AiOverviewVisibleStoreItem;
import com.nongxinle.ai.mapping.AiRoleMapper;
import com.nongxinle.ai.scope.AiQueryScope;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.nongxinle.ai.trace.AiSseEventPublisher;
import com.nongxinle.ai.util.AiTimeWindowTextFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 经营概览：优先消费 {@link AiBusinessToolIds#BUSINESS_OVERVIEW_QUERY}（旧版日营收看板 stats），再结合菜品/采购/毛利 Tool。
 * <p>在 {@link StubOutcomeReviewNode} 调用 {@link #aggregateIfApplicable(AiRunState)}，不再作为 Graph AgentNode。
 */
@Component
@RequiredArgsConstructor
public class BusinessOverviewAgentNode {

    private final AiSseEventPublisher publisher;

    public void aggregateIfApplicable(AiRunState state) {
        if (state == null || !state.isBusinessOverviewPath()
                || state.getDataPlanTools() == null
                || state.getDataPlanTools().isEmpty()) {
            return;
        }
        long rid = state.getRunId();
        publisher.publish(rid, "agent_started", Map.of(
                "agent", "BusinessOverviewAgent",
                "displayText", "正在汇总经营概况…"
        ));

        Map<String, Object> bo = toolEnvelopeData(state, AiBusinessToolIds.BUSINESS_OVERVIEW_QUERY);
        @SuppressWarnings("unchecked")
        Map<String, Object> statsCnRaw = bo.get("stats") instanceof Map ? (Map<String, Object>) bo.get("stats") : Map.of();
        Map<String, Object> statsCn = new LinkedHashMap<>(statsCnRaw);
        overlayStatRangeOntoDashboardStats(state, statsCn);

        @SuppressWarnings("unchecked")
        Map<String, Object> bindingsRaw = bo.get("dashboardBindings") instanceof Map
                ? (Map<String, Object>) bo.get("dashboardBindings")
                : Map.of();
        Map<String, Object> bindings = new LinkedHashMap<>(bindingsRaw);

        @SuppressWarnings("unchecked")
        List<String> boHints = bo.get("anomalyHints") instanceof List ? (List<String>) bo.get("anomalyHints") : List.of();

        boolean boOk = toolSuccess(state, AiBusinessToolIds.BUSINESS_OVERVIEW_QUERY) && !statsCn.isEmpty();
        boolean boMock = envMock(state, AiBusinessToolIds.BUSINESS_OVERVIEW_QUERY);

        Map<String, Object> purD = section(state, AiBusinessToolIds.PURCHASE_QUERY);
        Map<String, Object> dishD = section(state, AiBusinessToolIds.DISH_SALES_QUERY);
        Map<String, Object> marginD = section(state, AiBusinessToolIds.GROSS_MARGIN_CALCULATOR);

        BigDecimal purchase = nz(purD.get("purchaseSubTotal"));
        BigDecimal listRev = nz(dishD.get("listPriceRevenueTotal"));
        boolean marginReliable = !(Boolean.FALSE.equals(marginD.get("grossMarginReliable")));
        BigDecimal marginPctNumeric = nz(marginD.get("estimatedGrossMarginPercent"));
        String marginDisplayed = marginDisplayForOverview(marginD, marginReliable, marginPctNumeric);

        int days = intVal(statsCn.get("统计天数"));
        BigDecimal totalRevenue = decimalOf(statsCn.get("总营业额"));
        BigDecimal avgDailyRev = decimalOf(statsCn.get("日均营业额"));

        BigDecimal totalOrdersApprox = nzFromSupplementOrDerive(state, statsCn);

        BigDecimal avgOrderFromStats = decimalOf(statsCn.get("日均订单数"));
        String avgPerCust = stringify(statsCn.get("客单价"));
        String profitStatus = stringify(statsCn.get("盈亏状态"));
        String marginDash = stringify(statsCn.get("利润率"));

        BigDecimal grossMarginDash = decimalOf(statsCn.get("利润率"));

        boolean haveBoardRevenue = boOk && (totalRevenue.signum() > 0 || avgDailyRev.signum() > 0);
        boolean haveRevenue = haveBoardRevenue || listRev.signum() > 0;

        boolean mockHeavy = boMock || envMock(state, AiBusinessToolIds.PURCHASE_QUERY);

        List<Map<String, Object>> metrics = new ArrayList<>();
        metrics.add(AiBusinessOverviewResult.metric("统计天数",
                boOk ? String.valueOf(days) : "暂无", boOk ? "天" : null));
        metrics.add(AiBusinessOverviewResult.metric("总营业额", boOk ? stripScale(totalRevenue) : "暂无", boOk ? "元" : null));
        metrics.add(AiBusinessOverviewResult.metric("日均营业额", boOk ? stripScale(avgDailyRev) : "暂无", boOk ? "元" : null));
        metrics.add(AiBusinessOverviewResult.metric("订单数(近似)", totalOrdersApprox.signum() > 0 ? stripScale(totalOrdersApprox) : "暂无",
                totalOrdersApprox.signum() > 0 ? "单" : null));
        metrics.add(AiBusinessOverviewResult.metric("日均订单数",
                avgOrderFromStats.signum() > 0 ? stripScale(avgOrderFromStats) : "暂无",
                avgOrderFromStats.signum() > 0 ? "单/天" : null));
        metrics.add(AiBusinessOverviewResult.metric("客单价", nzStr(avgPerCust, "暂无"), boOk ? "元" : null));
        metrics.add(AiBusinessOverviewResult.metric(
                "优惠券/平台相关费用(看板：原聚合字段)",
                boOk ? nzStr(stringify(statsCn.get("平台费合计")), "暂无") : "暂无", "元"));
        metrics.add(AiBusinessOverviewResult.metric(
                "退款合计", boOk ? nzStr(stringify(statsCn.get("退款合计")), "暂无") : "暂无", "元"));
        metrics.add(AiBusinessOverviewResult.metric(
                "外卖营业额合计", boOk ? nzStr(stringify(statsCn.get("外卖营业额合计")), "暂无") : "暂无", "元"));
        metrics.add(AiBusinessOverviewResult.metric("菜品标价收入", listRev.signum() > 0 ? stripScale(listRev) : "暂无", "元"));
        metrics.add(AiBusinessOverviewResult.metric("采购额(入库汇总)", purchase.signum() > 0 ? stripScale(purchase) : "暂无", "元"));
        metrics.add(AiBusinessOverviewResult.metric("食材毛利率%(看板)", nzStr(marginDash, "暂无"), boOk ? "%" : null));
        metrics.add(AiBusinessOverviewResult.metric("盈亏状态", nzStr(profitStatus, "暂无"), null));
        metrics.add(AiBusinessOverviewResult.metric("估算毛利率%(Tool链)", marginDisplayed, marginReliable ? "%" : "口径不完整仅供参考"));

        AiQueryScope qScope = state.getScope();

        String roleCode = effectiveAiRoleCode(state);

        LinkedHashMap<String, Object> overviewScope = buildOverviewScope(
                roleCode, bo, qScope, boOk, state);
        augmentPlatformFeeVsTakeoutFacts(overviewScope, boOk, statsCn);
        augmentOverviewScopeStoreIssues(roleCode, overviewScope, bo, state.getResolvedQueryContext());

        List<AiOverviewCoveredStoreItem> coveredStores = readCoveredStoreItems(bo);
        String coveredStoresBrief = AiGroupOverviewCoveredStoresBrief.format(coveredStores);

        List<AiOverviewStoreIssueItem> dataMissingStores = readStoreIssueItems(bo, "dataMissingStores");
        List<AiOverviewStoreIssueItem> attentionStores = readStoreIssueItems(bo, "attentionStores");
        String priorityStoresBriefFromTool = stringify(bo.get("priorityStoresBrief"));
        String priorityStoresBriefResolved = resolvePriorityStoresBrief(
                roleCode, boOk, dataMissingStores, attentionStores, priorityStoresBriefFromTool);

        List<String> findings = new ArrayList<>();

        boolean takeoutLowerThanFee = boOk
                && takeoutVsPlatformFeesNeedAttribution(
                decimalOf(statsCn.get("外卖营业额合计")),
                decimalOf(statsCn.get("平台费合计")));

        if (takeoutLowerThanFee) {
            BigDecimal takeoutAgg = decimalOf(statsCn.get("外卖营业额合计"));
            BigDecimal feeAgg = decimalOf(statsCn.get("平台费合计"));
            findings.add(String.format(Locale.CHINA,
                    "「优惠券/平台费」合计 %s 元高于同期「外卖营业额合计」%s 元，不能直接判断外卖净贡献为负；"
                            + "请核对费用是否全部归属外卖口径，是否混入了堂食或全店营销分摊。",
                    stripScale(feeAgg), stripScale(takeoutAgg)));
        }

        if (!boOk) {
            findings.add("经营看板未返回有效统计：若已录日营收仍为空，请核对门店画像、组织归属与时间区间是否与后台一致。");
        }
        if (state.getDepartmentId() == null || state.getDistributerId() == null) {
            findings.add("组织或分销商上下文不完整，可能影响采购与菜品侧汇总对齐。");
        }
        if (boHints != null && !boHints.isEmpty()) {
            for (String h : boHints) {
                if (h != null && !h.isBlank()) {
                    findings.add(h.trim());
                }
            }
        }
        if (!haveRevenue) {
            AiTimeWindowTextFormatter.UserPhrases tw = AiTimeWindowTextFormatter.forAnswer(state);
            findings.add("营业额与菜品标价收入均未汇总到，可先确认" + tw.getTimeSubjectText() + "是否已录入日营收与菜品销售。");
        }
        if (boOk && nzStr(profitStatus, "").length() > 0 && profitStatusContainsLoss(profitStatus)) {
            findings.add("日营收看板显示盈亏状态为：" + profitStatus + "（已含画像工资/房租按÷30分摊与生产核销等口径）。");
        }
        if (!marginReliable && haveRevenue) {
            findings.add("Tool 链毛利率暂不可精确计算（核销或出库分摊不足）；看板侧的食材毛利率若为数字，可作参考：" + nzStr(marginDash, "暂无") + "%。");
        }
        if (purchase.signum() > 0 && !haveRevenue) {
            findings.add("有采购入库但收入侧偏空，关注销售数据是否同步入账。");
        }
        if (mockHeavy) {
            findings.add("部分数据源为占位或异常返回，结论仅供参考。");
        }

        if (Boolean.TRUE.equals(overviewScope.get("fallbackSummaryOnly_anchor"))) {
            findings.add("⚠本轮仅按当前页面锚定部门单行汇总，未带入集团侧完整可视门店；请确认组织范围选择是否与预期一致后再读汇总数字。");
        }

        List<String> recs = new ArrayList<>();
        if (boOk && days > 0) {
            recs.add("按已录 " + days + " 天数据，优先核对日营收是否覆盖完整营业日，再对比畅销菜与客单变化。");
        }
        if (listRev.signum() > 0 && totalRevenue.signum() > 0) {
            recs.add("对照「总营业额」与「菜品标价收入」差异，检查未入系统的折扣或线下单。");
        }
        if (marginReliable && marginPctNumeric.compareTo(BigDecimal.valueOf(25)) < 0 && haveRevenue) {
            recs.add("估算毛利率偏低时，结合采购 Top 品与高价菜成本复核。");
        }
        if (recs.isEmpty()) {
            recs.add("补全日营收与核销数据后，再拆「人流 / 客单 / 结构」三项复盘。");
        }

        String risk = "ok";
        if (!boOk && !haveRevenue) {
            risk = state.getDepartmentId() == null ? "warning" : "data_incomplete";
        } else if (!haveRevenue) {
            risk = "high";
        } else if (!marginReliable && haveRevenue) {
            risk = "data_incomplete";
        } else if (boOk && grossMarginDash.compareTo(BigDecimal.ZERO) > 0 && grossMarginDash.compareTo(new BigDecimal("15")) < 0) {
            risk = "warning";
        } else if (marginReliable && marginPctNumeric.compareTo(BigDecimal.valueOf(18)) < 0) {
            risk = "warning";
        }

        boolean needMore = !boOk || (!haveRevenue);

        String summary;
        if (!boOk) {
            summary = "当前未能从经营看板拉取到有效日营收统计，无法给出带具体数字的经营结论。";
        } else if (!haveRevenue) {
            summary = "看板已返回区间，但营业额与菜品收入仍接近空，需先补录或调整查询区间。";
        } else {
            StringBuilder s = new StringBuilder();
            s.append(summaryRangeLead(state));
            s.append("营业额约 ").append(stripScale(totalRevenue)).append(" 元");
            s.append("，有营业额入账的自然日 ").append(days).append(" 天");
            if (avgDailyRev.signum() > 0) {
                s.append("，日均约 ").append(stripScale(avgDailyRev)).append(" 元");
            }
            if (totalOrdersApprox.signum() > 0) {
                s.append("；估算订单合计约 ").append(stripScale(totalOrdersApprox)).append(" 单");
            }
            if (nzStr(avgPerCust, "").length() > 0 && !"-".equals(avgPerCust)) {
                s.append("，客单价约 ").append(avgPerCust).append(" 元");
            }
            s.append("。");
            summary = s.toString();
        }

        String primaryBanner = overviewScope.get("primaryBanner") instanceof String
                ? (String) overviewScope.get("primaryBanner") : "";
        String coverageDetail = overviewScope.get("coverageDetail") instanceof String
                ? (String) overviewScope.get("coverageDetail") : "";
        overviewScope.remove("fallbackSummaryOnly_anchor");
        String coveredHead = coveredStoresBrief == null ? "" : coveredStoresBrief.trim();
        String dataMissingStoresBrief = overviewScope.get("dataMissingStoresBrief") instanceof String
                ? ((String) overviewScope.get("dataMissingStoresBrief")).trim() : "";
        boolean wantHead = !primaryBanner.isBlank() || !coverageDetail.isBlank() || !coveredHead.isBlank()
                || !dataMissingStoresBrief.isBlank();
        if (wantHead) {
            StringBuilder head = new StringBuilder();
            if (!primaryBanner.isBlank()) {
                head.append(primaryBanner.trim());
            }
            if (!coverageDetail.isBlank()) {
                if (head.length() > 0) {
                    head.append('\n');
                }
                head.append(coverageDetail.trim());
            }
            if (!coveredHead.isBlank()) {
                if (head.length() > 0) {
                    head.append('\n');
                }
                head.append(coveredHead);
            }
            if (!dataMissingStoresBrief.isBlank()) {
                if (head.length() > 0) {
                    head.append('\n');
                }
                head.append(dataMissingStoresBrief);
            }
            if (head.length() > 0) {
                summary = head.append('\n').append(summary).toString();
            }
        }

        List<String> questions = new ArrayList<>();
        if (needMore) {
            questions.add("请确认问的月份与后台「日营业额」录入区间是否一致？");
            questions.add("若刚换门店绑定，请到餐厅画像与日营收表里核对组织归属是否一致。");
        }

        List<AiOverviewVisibleStoreItem> visibleStores = readVisibleStoreItems(bo);
        if (visibleStores.isEmpty()) {
            visibleStores = visibleStoresFromResolvedContext(state.getResolvedQueryContext());
        }

        AiBusinessOverviewResult ov = AiBusinessOverviewResult.builder()
                .agentName("BusinessOverviewAgent")
                .summary(summary)
                .riskLevel(risk)
                .keyMetrics(metrics)
                .findings(findings)
                .recommendations(recs.stream().distinct().limit(5).toList())
                .needMoreData(needMore)
                .questions(questions)
                .dashboardStatsCn(statsCn.isEmpty() ? null : statsCn)
                .dashboardBindings(bindings.isEmpty() ? null : bindings)
                .overviewScope(overviewScope.isEmpty() ? null : overviewScope)
                .dataMissingStores(dataMissingStores.isEmpty() ? List.of() : List.copyOf(dataMissingStores))
                .attentionStores(attentionStores.isEmpty() ? List.of() : List.copyOf(attentionStores))
                .priorityStoresBrief(priorityStoresBriefResolved.isBlank() ? null : priorityStoresBriefResolved)
                .visibleStores(visibleStores.isEmpty() ? List.of() : List.copyOf(visibleStores))
                .coveredStores(coveredStores.isEmpty() ? List.of() : List.copyOf(coveredStores))
                .coveredStoresBrief(coveredStoresBrief == null || coveredStoresBrief.isBlank() ? null : coveredStoresBrief.trim())
                .build();
        state.setBusinessOverviewResult(ov);

        publisher.publish(rid, "agent_finished", Map.of(
                "agent", "BusinessOverviewAgent",
                "displayText", "经营概况结构化结果已就绪",
                "riskLevel", risk,
                "needMoreData", needMore
        ));
    }

    private static boolean profitStatusContainsLoss(String profitStatus) {
        if (profitStatus == null) {
            return false;
        }
        String t = profitStatus.toLowerCase(Locale.ROOT);
        return t.contains("亏") || t.contains("预警") || "loss".equals(t);
    }

    private static BigDecimal nzFromSupplementOrDerive(AiRunState state, Map<String, Object> statsCn) {
        Map<String, Object> bo = toolEnvelopeData(state, AiBusinessToolIds.BUSINESS_OVERVIEW_QUERY);
        @SuppressWarnings("unchecked")
        Map<String, Object> sup = bo.get("mapperSupplement") instanceof Map
                ? (Map<String, Object>) bo.get("mapperSupplement")
                : Map.of();
        Object totalOrd = sup.get("订单汇总(区间)");
        if (totalOrd != null) {
            BigDecimal t = decimalOf(totalOrd);
            if (t.signum() > 0) {
                return t;
            }
        }
        int days = intVal(statsCn.get("统计天数"));
        BigDecimal avg = decimalOf(statsCn.get("日均订单数"));
        if (days > 0 && avg.signum() > 0) {
            return avg.multiply(BigDecimal.valueOf(days)).setScale(0, java.math.RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    private static String nzStr(String s, String dflt) {
        if (s == null || s.isBlank() || "-".equals(s)) {
            return dflt;
        }
        return s;
    }

    private static String stringify(Object v) {
        if (v == null) {
            return "";
        }
        return v.toString().trim();
    }

    private static int intVal(Object v) {
        if (v == null) {
            return 0;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return new BigDecimal(v.toString().trim()).intValue();
        } catch (Exception e) {
            return 0;
        }
    }

    /** Tool stats 缺省时补全日期边界，供 headline/summary 与卡片口径一致。 */
    private static void overlayStatRangeOntoDashboardStats(AiRunState state, Map<String, Object> statsCn) {
        if (state == null || statsCn == null || statsCn.isEmpty()) {
            return;
        }
        String runStart = trimIsoDate(state.getStatStartDate());
        String runEnd = trimIsoDate(state.getStatEndDate());
        if (statsCn.get("统计开始日期") == null || statsCn.get("统计开始日期").toString().isBlank()) {
            if (!runStart.isEmpty()) {
                statsCn.put("统计开始日期", runStart);
            }
        }
        if (statsCn.get("统计结束日期") == null || statsCn.get("统计结束日期").toString().isBlank()) {
            if (!runEnd.isEmpty()) {
                statsCn.put("统计结束日期", runEnd);
            }
        }
    }

    private static String trimIsoDate(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.trim();
        return t.isEmpty() ? "" : t;
    }

    /** 与 StubComposer headline、卡片摘要同源：优先 yyyy-MM-dd 边界，否则中性说法。 */
    private static String summaryRangeLead(AiRunState state) {
        if (state == null) {
            return "所选区间内 ";
        }
        String a = trimIsoDate(state.getStatStartDate());
        String b = trimIsoDate(state.getStatEndDate());
        if (!a.isEmpty() && !b.isEmpty()) {
            if (a.equals(b)) {
                return a + " ";
            }
            return a + " 至 " + b + " ";
        }
        return "所选区间内 ";
    }

    private static BigDecimal decimalOf(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        try {
            return new BigDecimal(v.toString().trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private static String stripScale(BigDecimal b) {
        if (b == null) {
            return "0";
        }
        return b.stripTrailingZeros().toPlainString();
    }

    private static String marginDisplayForOverview(Map<String, Object> marginD, boolean marginReliable, BigDecimal marginPctNumeric) {
        if (!marginReliable) {
            Object disp = marginD.get("estimatedGrossMarginPercentDisplay");
            if (disp != null && !disp.toString().isBlank()) {
                return disp.toString().trim();
            }
            return "毛利率暂不可准确计算（核销/出库数据不足）";
        }
        return marginPctNumeric.stripTrailingZeros().toPlainString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toolEnvelopeData(AiRunState state, String toolKey) {
        Object env = state.getToolResults().get(toolKey);
        if (!(env instanceof Map)) {
            return Map.of();
        }
        Object data = ((Map<String, Object>) env).get("data");
        if (!(data instanceof Map)) {
            return Map.of();
        }
        return (Map<String, Object>) data;
    }

    private static Map<String, Object> section(AiRunState state, String toolKey) {
        return toolEnvelopeData(state, toolKey);
    }

    private static BigDecimal nz(Object v) {
        return decimalOf(v);
    }

    private static boolean toolSuccess(AiRunState state, String toolKey) {
        Object env = state.getToolResults().get(toolKey);
        if (!(env instanceof Map)) {
            return false;
        }
        return Boolean.TRUE.equals(((Map<?, ?>) env).get("success"));
    }

    @SuppressWarnings("unchecked")
    private static boolean envMock(AiRunState state, String toolKey) {
        Object env = state.getToolResults().get(toolKey);
        if (!(env instanceof Map)) {
            return false;
        }
        return Boolean.TRUE.equals(((Map<String, Object>) env).get("mock"));
    }

    private static String effectiveAiRoleCode(AiRunState state) {
        if (state == null || state.getAiUserContext() == null) {
            return "";
        }
        AiUserContext ctx = state.getAiUserContext();
        if (ctx.getRoleCode() != null && !ctx.getRoleCode().isBlank()) {
            return ctx.getRoleCode().trim();
        }
        if (ctx.getSourceAdminRole() != null) {
            return AiRoleMapper.resolveAdmin(ctx.getSourceAdminRole())
                    .map(AiRoleMapper.AiRoleDefinition::roleCode)
                    .orElse("");
        }
        return "";
    }

    private static List<AiOverviewVisibleStoreItem> visibleStoresFromResolvedContext(AiResolvedQueryContext ctx) {
        if (ctx == null || ctx.getOrgScope() == null || ctx.getOrgScope().getVisibleStores() == null) {
            return List.of();
        }
        List<AiOverviewVisibleStoreItem> out = new ArrayList<>();
        for (AiStoreScopeDTO s : ctx.getOrgScope().getVisibleStores()) {
            if (s == null) {
                continue;
            }
            String name = s.getStoreName() == null ? "" : s.getStoreName().trim();
            if (name.isBlank()) {
                continue;
            }
            out.add(AiOverviewVisibleStoreItem.builder()
                    .storeDepartmentId(s.getStoreDepartmentId())
                    .storeName(name)
                    .build());
        }
        return out;
    }

    private static void augmentOverviewScopeStoreIssues(String roleCode,
            LinkedHashMap<String, Object> overviewScope,
            Map<String, Object> boInner,
            AiResolvedQueryContext rqCtx) {

        List<AiOverviewCoveredStoreItem> covered = readCoveredStoreItems(boInner);
        if (!covered.isEmpty()) {
            overviewScope.put("coveredStores", covered);
        }

        List<AiOverviewVisibleStoreItem> vis = readVisibleStoreItems(boInner);
        if (vis.isEmpty()) {
            vis = visibleStoresFromResolvedContext(rqCtx);
        }
        if (!vis.isEmpty()) {
            overviewScope.put("visibleStores", vis);
        }

        if (!AiRoleMapper.isGroupWideOrgScope(roleCode != null ? roleCode : "") || overviewScope.isEmpty()) {
            return;
        }
        List<AiOverviewStoreIssueItem> dm = readStoreIssueItems(boInner, "dataMissingStores");
        List<AiOverviewStoreIssueItem> at = readStoreIssueItems(boInner, "attentionStores");
        overviewScope.put("dataMissingStoreCount", dm.size());
        overviewScope.put("attentionStoreCount", at.size());
        overviewScope.put("dataMissingStores", dm.isEmpty() ? List.of() : List.copyOf(dm));
        overviewScope.put("attentionStores", at.isEmpty() ? List.of() : List.copyOf(at));
        String missingBrief = AiGroupOverviewStoreBrief.formatStoresWithoutRevenueBrief(dm);
        if (!missingBrief.isBlank()) {
            overviewScope.put("dataMissingStoresBrief", missingBrief);
        }
    }

    /**
     * 集团广角 + 看板有效：正文点名列表仅用结构化两类门店拼装；若均为空则固定一句「无明显异常门店」。
     * 其它角色沿用 Tool 带回的 priorityStoresBrief。
     */
    private static String resolvePriorityStoresBrief(String roleCode, boolean boOk,
            List<AiOverviewStoreIssueItem> dataMissingStores,
            List<AiOverviewStoreIssueItem> attentionStores,
            String priorityStoresBriefFromTool) {

        if (AiRoleMapper.isGroupWideOrgScope(roleCode != null ? roleCode : "") && boOk) {
            String computed = AiGroupOverviewStoreBrief.formatPriorityBrief(dataMissingStores, attentionStores);
            return computed != null ? computed : AiGroupOverviewStoreBrief.noIssuesLine();
        }
        return priorityStoresBriefFromTool == null ? "" : priorityStoresBriefFromTool.trim();
    }

    /** 从 {@code business_overview_query.data} 读出门店清单（兼容 Map / DTO）。 */
    private static List<AiOverviewStoreIssueItem> readStoreIssueItems(Map<String, Object> inner, String key) {
        Object raw = inner == null ? null : inner.get(key);
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<AiOverviewStoreIssueItem> out = new ArrayList<>(list.size());
        for (Object o : list) {
            if (o instanceof AiOverviewStoreIssueItem it) {
                out.add(it);
                continue;
            }
            if (!(o instanceof Map<?, ?> mm)) {
                continue;
            }
            String name = "";
            String reason = "";
            String risk = "";
            Object sn = mm.get("storeName");
            if (sn != null) {
                name = sn.toString().trim();
            }
            Object rn = mm.get("reason");
            if (rn != null) {
                reason = rn.toString().trim();
            }
            Object rk = mm.get("riskLevel");
            if (rk != null && !rk.toString().isBlank()) {
                risk = rk.toString().trim();
            }
            if (name.isBlank() && reason.isBlank()) {
                continue;
            }
            AiOverviewStoreIssueItem item = AiOverviewStoreIssueItem.builder()
                    .storeName(name.isBlank() ? "-" : name)
                    .reason(reason.isBlank() ? "-" : reason)
                    .riskLevel(risk.isBlank() ? null : risk)
                    .build();
            out.add(item);
        }
        return out;
    }

    private static List<AiOverviewVisibleStoreItem> readVisibleStoreItems(Map<String, Object> inner) {
        Object raw = inner == null ? null : inner.get("visibleStores");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<AiOverviewVisibleStoreItem> out = new ArrayList<>(list.size());
        for (Object o : list) {
            if (o instanceof AiOverviewVisibleStoreItem it) {
                out.add(it);
                continue;
            }
            if (!(o instanceof Map<?, ?> mm)) {
                continue;
            }
            Object sn = mm.get("storeName");
            String name = sn == null ? "" : sn.toString().trim();
            Long storeDeptId = null;
            Object sid = mm.get("storeDepartmentId");
            if (sid instanceof Number n) {
                storeDeptId = n.longValue();
            } else if (sid != null) {
                try {
                    storeDeptId = Long.parseLong(sid.toString().trim());
                } catch (Exception ignored) {
                    storeDeptId = null;
                }
            }
            if (name.isBlank()) {
                continue;
            }
            out.add(AiOverviewVisibleStoreItem.builder()
                    .storeDepartmentId(storeDeptId)
                    .storeName(name)
                    .build());
        }
        return out;
    }

    private static List<AiOverviewCoveredStoreItem> readCoveredStoreItems(Map<String, Object> inner) {
        Object raw = inner == null ? null : inner.get("coveredStores");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<AiOverviewCoveredStoreItem> out = new ArrayList<>(list.size());
        for (Object o : list) {
            if (o instanceof AiOverviewCoveredStoreItem it) {
                out.add(it);
                continue;
            }
            if (!(o instanceof Map<?, ?> mm)) {
                continue;
            }
            String name = "";
            Object sn = mm.get("storeName");
            if (sn != null) {
                name = sn.toString().trim();
            }
            Boolean hasRev = null;
            Object hr = mm.get("hasRevenueData");
            if (hr instanceof Boolean b) {
                hasRev = b;
            } else if (hr != null) {
                hasRev = Boolean.parseBoolean(hr.toString().trim());
            }
            BigDecimal totalRev = decimalOf(mm.get("totalRevenue"));
            int dayz = intVal(mm.get("days"));
            BigDecimal orderCnt = decimalOf(mm.get("orderCount"));
            BigDecimal avgOrd = decimalOf(mm.get("avgOrderCount"));
            BigDecimal avgPc = decimalOf(mm.get("avgPerCustomer"));
            if (name.isBlank()) {
                continue;
            }
            out.add(AiOverviewCoveredStoreItem.builder()
                    .storeName(name)
                    .hasRevenueData(hasRev != null ? hasRev : totalRev.signum() > 0 && orderCnt.signum() > 0)
                    .totalRevenue(totalRev)
                    .days(dayz)
                    .orderCount(orderCnt)
                    .avgOrderCount(avgOrd)
                    .avgPerCustomer(avgPc)
                    .build());
        }
        return out;
    }

    /** 集团抬头：优先 Tool 快照店名，否则公共解析 visibleStores。 */
    private static List<String> visibleStoreNamesForGroupBanner(Map<String, Object> boInner,
            AiResolvedQueryContext rqCtx) {
        List<String> out = new ArrayList<>();
        for (AiOverviewVisibleStoreItem it : readVisibleStoreItems(boInner)) {
            if (it.getStoreName() != null && !it.getStoreName().isBlank()) {
                out.add(it.getStoreName().trim());
            }
        }
        if (!out.isEmpty()) {
            return out;
        }
        if (rqCtx == null || rqCtx.getOrgScope() == null || rqCtx.getOrgScope().getVisibleStores() == null) {
            return out;
        }
        for (AiStoreScopeDTO s : rqCtx.getOrgScope().getVisibleStores()) {
            if (s != null && s.getStoreName() != null && !s.getStoreName().isBlank()) {
                out.add(s.getStoreName().trim());
            }
        }
        return out;
    }

    /** 不包含部门 id：供前端卡片与对话抬头；集团 SQL 聚合时补齐门店覆盖语义。 */
    private static LinkedHashMap<String, Object> buildOverviewScope(
            String roleCode,
            Map<String, Object> boInner,
            AiQueryScope scope,
            boolean boOk,
            AiRunState state) {
        AiResolvedQueryContext rqCtx = state == null ? null : state.getResolvedQueryContext();
        AiTimeWindowTextFormatter.UserPhrases tw = AiTimeWindowTextFormatter.forAnswer(state);
        String periodSubject = tw.getTimeSubjectText();
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        @SuppressWarnings("unchecked")
        Map<String, Object> rm = boInner.get("rollupMeta") instanceof Map
                ? (Map<String, Object>) boInner.get("rollupMeta")
                : null;

        boolean groupWide = AiRoleMapper.isGroupWideOrgScope(roleCode != null ? roleCode : "")
                || (rm != null && "GROUP_SQL_ROLLUP".equals(String.valueOf(rm.get("aggregationMode"))));

        if (rm != null && groupWide) {
            int visible = intVal(rm.get("visibleDepartmentNodeCount"));
            int withRec = intVal(rm.get("dataAvailableRecordingDepartmentCount"));
            int nodeMissingApprox = intVal(rm.get("dataMissingVisibleNodeApprox"));
            boolean fallbackAnchor = Boolean.TRUE.equals(rm.get("fallbackSingleAnchorOnly"));

            int storeSignal = scope != null ? scope.getParentStoreCount() : 0;
            int departmentCountFallback = storeSignal > 0 ? storeSignal : visible;

            boolean snapStoreCounts = rm.containsKey("visibleStoreCount");
            int vs = snapStoreCounts ? intVal(rm.get("visibleStoreCount")) : departmentCountFallback;
            if (snapStoreCounts && vs <= 0) {
                vs = departmentCountFallback;
            }
            int wr = snapStoreCounts ? intVal(rm.get("storeWithRevenueCount")) : withRec;
            int ms = snapStoreCounts
                    ? intVal(rm.get("storeMissingRevenueCount"))
                    : (vs > 0 ? Math.max(0, vs - withRec) : Math.max(0, nodeMissingApprox));

            m.put("scopeType", "GROUP");
            m.put("scopeName", "集团汇总（账号可见组织）");
            m.put("aggregationModeHint", "GROUP_SQL_ROLLUP");
            m.put("visibleDepartmentNodeCount", visible);
            m.put("visibleStoreCount", vs);
            m.put("departmentCount", vs);
            m.put("dataAvailableRecordingDepartmentCount", withRec);
            m.put("dataAvailableDepartmentCount", wr);
            m.put("dataMissingDepartmentCount", ms);
            List<String> bannerNames = visibleStoreNamesForGroupBanner(boInner, rqCtx);
            int bannerStoreCount = !bannerNames.isEmpty() ? bannerNames.size() : vs;
            String joined = String.join("、", bannerNames);
            if (!joined.isEmpty()) {
                m.put("primaryBanner", String.format(
                        Locale.CHINA, "你当前账号可查看集团范围，本次识别到 %d 家门店：%s。", bannerStoreCount, joined));
            } else {
                m.put("primaryBanner", "你当前账号可查看集团范围。");
            }

            String coverage;
            if (vs <= 0) {
                if (wr > 0) {
                    coverage = String.format(
                            Locale.CHINA, "%s内，系统识别到 %d 家门店有日营收数据，下面按这部分数据汇总分析。",
                            tw.getDisplayTimeRange(), wr);
                } else {
                    coverage = tw.getDisplayTimeRange() + "范围内暂未识别到可用的门店日营收汇总，下面仅展示已返回的汇总字段（若有）。";
                }
            } else if (ms <= 0) {
                if (vs == 1) {
                    coverage = periodSubject + "，该门店有日营收数据，下面按集团范围汇总分析。";
                } else {
                    coverage = String.format(Locale.CHINA, "%d 家均有日营收数据，下面按集团范围汇总分析。", vs);
                }
            } else if (wr <= 0) {
                BigDecimal aggRev = readBoStatsDecimal(boInner, "总营业额");
                int statDays = readBoStatsInt(boInner, "统计天数");
                if (aggRev.signum() > 0 || statDays > 0) {
                    coverage = String.format(Locale.CHINA,
                            "%s内，汇总账已显示营业额或统计天数（见下文），但门店级日营收在可见的 %d 家门店中尚未完整覆盖；"
                                    + "以下数字为汇总口径，不宜理解为逐店均有日营收台账。",
                            tw.getDisplayTimeRange(), vs);
                } else {
                    coverage = String.format(
                            Locale.CHINA, "其中暂无有日营收数据的门店（共 %d 家可见），下面仅展示已返回的汇总字段（若有）。", vs);
                }
            } else {
                coverage = String.format(
                        Locale.CHINA, "其中 %d 家有日营收数据，%d 家暂无日营收记录。下面按有数据门店汇总分析。", wr, ms);
            }
            m.put("coverageDetail", coverage);
            if (fallbackAnchor) {
                m.put("fallbackSummaryOnly_anchor", Boolean.TRUE);
            }
            applySingleStoreQueryScopeOverlayIfNeeded(m, rqCtx, tw, periodSubject);
            return m;
        }

        int visFallback = scope != null && scope.getResolvedDepartmentIds() != null
                ? scope.getResolvedDepartmentIds().size()
                : 0;
        m.put("scopeType", "STORE");
        m.put("scopeName", "门店汇总（所选子树）");
        m.put("aggregationModeHint", "SINGLE_STORE_DASHBOARD");
        m.put("visibleDepartmentNodeCount", Math.max(visFallback, 1));
        m.put("departmentCount", 1);
        m.put("dataAvailableDepartmentCount", boOk ? 1 : 0);
        m.put("dataMissingDepartmentCount", boOk ? 0 : 1);
        m.put("primaryBanner", "【查询范围】以下为当前门店（所选组织子树）范围内的日营收口径汇总。");
        m.put("coverageDetail",
                "单店／子树口径：营业额与下文数字仅覆盖当前页面所选组织，不可直接等同「全集团相加」。");
        applySingleStoreQueryScopeOverlayIfNeeded(m, rqCtx, tw, periodSubject);
        return m;
    }

    /**
     * 仅有「外卖营业额」「平台费/券」分列时不足以支撑「外卖净利」断言；仅在金额关系异常时触发提醒。
     */
    private static boolean takeoutVsPlatformFeesNeedAttribution(BigDecimal takeoutTurnoverAgg, BigDecimal platformFeeAgg) {
        return takeoutTurnoverAgg.signum() > 0
                && platformFeeAgg.compareTo(takeoutTurnoverAgg) > 0;
    }

    /** 供 Composer 硬约束：仅当 platformFee &gt; takeout 时才可说「平台费高于外卖」。 */
    private static void augmentPlatformFeeVsTakeoutFacts(
            LinkedHashMap<String, Object> overviewScope,
            boolean boOk,
            Map<String, Object> statsCn) {
        if (overviewScope == null) {
            return;
        }
        if (!boOk || statsCn == null || statsCn.isEmpty()) {
            return;
        }
        BigDecimal takeout = decimalOf(statsCn.get("外卖营业额合计"));
        BigDecimal fee = decimalOf(statsCn.get("平台费合计"));
        if (takeout.signum() <= 0 && fee.signum() <= 0) {
            return;
        }
        overviewScope.put("platformFeeExceedsTakeoutRevenue", fee.compareTo(takeout) > 0);
    }

    private static BigDecimal readBoStatsDecimal(Map<String, Object> boInner, String key) {
        if (boInner == null || key == null || key.isBlank()) {
            return BigDecimal.ZERO;
        }
        Object st = boInner.get("stats");
        if (!(st instanceof Map<?, ?> mm)) {
            return BigDecimal.ZERO;
        }
        return decimalOf(mm.get(key));
    }

    private static int readBoStatsInt(Map<String, Object> boInner, String key) {
        if (boInner == null || key == null || key.isBlank()) {
            return 0;
        }
        Object st = boInner.get("stats");
        if (!(st instanceof Map<?, ?> mm)) {
            return 0;
        }
        return intVal(mm.get(key));
    }

    /** 本轮解析为单店（与账号可见多店区分）：回答抬头应展示 currentQueryScope。 */
    private static boolean isResolvedSingleStoreQuery(AiResolvedQueryContext rqCtx) {
        if (rqCtx == null || rqCtx.getOrgScope() == null) {
            return false;
        }
        if (!AiResolvedOrgScope.SCOPE_STORE.equals(rqCtx.getOrgScope().getScopeType())) {
            return false;
        }
        List<AiStoreScopeDTO> vs = rqCtx.getOrgScope().getVisibleStores();
        return vs != null && vs.size() == 1;
    }

    private static String resolvedSingleStoreLabel(AiResolvedQueryContext rqCtx) {
        if (!isResolvedSingleStoreQuery(rqCtx)) {
            return "";
        }
        AiStoreScopeDTO s = rqCtx.getOrgScope().getVisibleStores().get(0);
        if (s == null) {
            return "当前门店";
        }
        String n = s.getStoreName();
        if (n != null && !n.isBlank()) {
            return n.trim() + "门店";
        }
        if (s.getStoreDepartmentId() != null) {
            return "门店（所选组织）";
        }
        return "当前门店";
    }

    private static void applySingleStoreQueryScopeOverlayIfNeeded(
            LinkedHashMap<String, Object> m,
            AiResolvedQueryContext rqCtx,
            AiTimeWindowTextFormatter.UserPhrases tw,
            String periodSubject) {
        if (m == null || !isResolvedSingleStoreQuery(rqCtx)) {
            return;
        }
        String label = resolvedSingleStoreLabel(rqCtx);
        m.put("primaryBanner", "【查询范围】" + label);
        m.put("scopeName", label);
        m.put("scopeType", "STORE");
        String range = tw.getDisplayTimeRange();
        if (periodSubject != null && !periodSubject.isBlank()) {
            m.put("coverageDetail", "【时间范围】" + periodSubject + "（" + range + "）。以下为该门店经营汇总，非全集团口径。");
        } else {
            m.put("coverageDetail", "【时间范围】" + range + "。以下为该门店经营汇总，非全集团口径。");
        }
    }
}
