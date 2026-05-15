package com.nongxinle.ai.composer.payload;

import com.alibaba.fastjson2.JSON;
import com.nongxinle.ai.context.AiUserContext;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.AiBusinessOverviewResult;
import com.nongxinle.ai.dto.business.AiDishProfitDishBrief;
import com.nongxinle.ai.dto.business.AiDishProfitOverviewResult;
import com.nongxinle.ai.dto.business.AiOverviewCoveredStoreItem;
import com.nongxinle.ai.dto.business.AiOverviewStoreIssueItem;
import com.nongxinle.ai.dto.business.BusinessDiagnosisPlan;
import com.nongxinle.ai.dto.business.DishProfitAnswerPlan;
import com.nongxinle.ai.dto.cost.AiCostDiagnosisResult;
import com.nongxinle.ai.composer.summary.BusinessOverviewDeterministicSummaryBuilder;
import com.nongxinle.ai.mapping.AiRoleMapper;
import com.nongxinle.ai.security.AiRoleCodes;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.nongxinle.ai.util.AiNumericPlainText;
import com.nongxinle.ai.util.AiTimeWindowTextFormatter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds JSON payloads appended to Composer LLM user messages. Structural fields only —
 * branching on paths already encoded in {@link AiRunState} / tool envelopes.
 */
@Component
public class AnswerComposerPayloadFactory {

    private static final String W_STOCK_WEIGHT_UNIT = "斤";

    public Map<String, Object> buildCostPayload(AiRunState state, AiCostDiagnosisResult d) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("hint", "成本诊断卡片已含关键指标；聊天正文勿重复罗列指标数值");
        m.put("userQuestion", nz(state.getNormalizedUserInput()));
        m.put("summary", d.getSummary());
        m.put("riskLevel", d.getRiskLevel());
        m.put("needMoreData", d.getNeedMoreData());
        m.put("findings", d.getFindings());
        m.put("recommendations", d.getRecommendations());
        m.put("questions", d.getQuestions());
        return m;
    }

    public Map<String, Object> buildDishProfitPayload(AiRunState state, AiDishProfitOverviewResult dp) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        if (state.getDishProfitAnswerPlan() != null) {
            try {
                m.put("answerPlan", JSON.parseObject(JSON.toJSONString(state.getDishProfitAnswerPlan())));
            } catch (Exception ignore) {
                m.put("answerPlan", null);
            }
        }
        m.put("queryScopeBanner", nz(dp.getQueryScopeBanner()));
        m.put("scopeIntro", nz(state.getScopeConvergenceNote()));
        m.put("userQuestion", nz(state.getNormalizedUserInput()));
        m.put("scopeType", nz(dp.getScopeType()));
        m.put("scopeName", nz(dp.getScopeName()));
        m.put("visibleStores", dp.getVisibleStores() == null ? List.of() : dp.getVisibleStores());
        m.put("coveredStores", dp.getCoveredStores() == null ? List.of() : dp.getCoveredStores());
        m.put("dataMissingStores", dp.getDataMissingStores() == null ? List.of() : dp.getDataMissingStores());
        m.put("summary", nz(dp.getSummary()));
        m.put("statStartDate", nz(dp.getStatStartDate()));
        m.put("statEndDate", nz(dp.getStatEndDate()));
        m.put("dishCount", dp.getDishCount());
        m.put("totalDishSalesAmount", nz(dp.getTotalDishSalesAmount()));
        m.put("totalTheoreticalCost", nz(dp.getTotalTheoreticalCost()));
        m.put("totalActualCost", nz(dp.getTotalActualCost()));
        m.put("grossProfitAmount", nz(dp.getGrossProfitAmount()));
        m.put("grossProfitRate", nz(dp.getGrossProfitRate()));
        m.put("grossProfitRateUncertain", dp.isGrossProfitRateUncertain());
        m.put("riskLevel", nz(dp.getRiskLevel()));
        m.put("reliableProfitDishes", capDishBriefs(dp.getReliableProfitDishes(), 5));
        m.put("lowProfitDishes", capDishBriefs(dp.getLowProfitDishes(), 5));
        m.put("costDataIncompleteDishes", capDishBriefs(dp.getCostDataIncompleteDishes(), 8));
        m.put("topProfitDishes", capDishBriefs(dp.getTopProfitDishes(), 5));
        m.put("abnormalDishes", capDishBriefs(dp.getAbnormalDishes(), 6));
        m.put("recommendations", dp.getRecommendations() == null ? List.of() : dp.getRecommendations());
        return m;
    }

    public LinkedHashMap<String, Object> buildBusinessDiagnosisPayload(AiRunState state,
            BusinessDiagnosisPlan bdPlan) {
        LinkedHashMap<String, Object> bdPayload = new LinkedHashMap<>();
        bdPayload.put("hint", "优先 dishProfitAnswerPlan.plan；DISH_LOWEST_MARGIN 用 focusRows[0]；禁止否认风险");
        bdPayload.put("userQuestion", nz(state != null ? state.getNormalizedUserInput() : null));
        bdPayload.put("diagnosisPlan", bdPlan);
        LinkedHashMap<String, Object> apWrap = new LinkedHashMap<>();
        DishProfitAnswerPlan ap = state != null ? state.getDishProfitAnswerPlan() : null;
        boolean present = ap != null;
        apWrap.put("present", present);
        if (present) {
            try {
                apWrap.put("plan", JSON.parseObject(JSON.toJSONString(ap)));
            } catch (Exception e) {
                apWrap.put("plan", null);
                apWrap.put("planSerializeWarning", "failed");
            }
        } else {
            apWrap.put("plan", null);
        }
        bdPayload.put("dishProfitAnswerPlan", apWrap);
        return bdPayload;
    }

    public LinkedHashMap<String, Object> buildBusinessDiagnosisStorePriorityPayload(AiRunState state,
            BusinessDiagnosisPlan bdPlan) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("mode", "STORE_PRIORITY_RANKING");
        m.put("hint",
                "门店优先级答复：先处理谁、原因、其它店、2-3 动作；禁止集团采购+出库+毛利率总览式开头。");
        m.put("userQuestion", nz(state != null ? state.getNormalizedUserInput() : null));
        m.put("scopeLabel", bdPlan != null ? nz(bdPlan.getScopeLabel()) : "");
        m.put("timeLabel", bdPlan != null ? nz(bdPlan.getTimeLabel()) : "");
        if (bdPlan != null && bdPlan.getStorePriorityRanking() != null) {
            try {
                m.put("storePriorityRanking",
                        JSON.parseObject(JSON.toJSONString(bdPlan.getStorePriorityRanking())));
            } catch (Exception e) {
                m.put("storePriorityRanking", bdPlan.getStorePriorityRanking());
            }
        } else {
            m.put("storePriorityRanking", null);
        }
        if (bdPlan != null && bdPlan.getDataCompleteness() != null) {
            m.put("dataCompleteness", bdPlan.getDataCompleteness());
        }
        return m;
    }

    public Map<String, Object> buildBusinessOverviewPayload(AiRunState state, AiBusinessOverviewResult o) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> os = o.getOverviewScope();
        if (os != null && !os.isEmpty()) {
            Object pb = os.get("primaryBanner");
            Object cd = os.get("coverageDetail");
            m.put("queryScopeBanner", pb == null ? "" : pb.toString().trim());
            m.put("queryScopeCoverage", cd == null ? "" : cd.toString().trim());
            m.put("overviewScope", os);
        } else {
            m.put("queryScopeBanner", "");
            m.put("queryScopeCoverage", "");
        }
        m.put("visibleStores", o.getVisibleStores() == null ? List.of() : o.getVisibleStores());
        m.put("hint", authoritativeBusinessOverviewComposerHint(state));
        boolean authRev = BusinessOverviewDeterministicSummaryBuilder.hasAuthoritativeBusinessOverviewRevenuePlan(state);
        String headline = authRev
                ? nz(BusinessOverviewDeterministicSummaryBuilder.businessOverviewResolvedRevenueParagraph(state)).trim()
                : nz(BusinessOverviewDeterministicSummaryBuilder.extractOverviewNumericHeadline(state, o)).trim();
        if (headline.isBlank()) {
            headline = nz(BusinessOverviewDeterministicSummaryBuilder.extractOverviewNumericHeadline(state, o)).trim();
        }
        m.put("numericHeadlineText", headline);
        m.put("dashboardStatsCn摘录", excerptDashboardStatsCnForBusinessOverview(state, o));
        String purchaseBrief = "";
        if (state.isBusinessOverviewPath()) {
            purchaseBrief = nz(BusinessOverviewDeterministicSummaryBuilder.businessOverviewPurchaseCoreSentence(state)).trim();
        }
        m.put("purchaseCoreAnswerPlanSentence", purchaseBrief);
        m.put("userQuestion", nz(state.getNormalizedUserInput()));
        m.put("summary", o.getSummary());
        m.put("priorityStoresBrief", nz(o.getPriorityStoresBrief()));
        m.put("coveredStoresBrief", nz(o.getCoveredStoresBrief()));
        m.put("coveredStores", capCoveredStoresPreview(o.getCoveredStores(), 80));
        m.put("dataMissingStores", capIssueItemsPreview(o.getDataMissingStores(), 12));
        m.put("attentionStores", capIssueItemsPreview(o.getAttentionStores(), 12));
        m.put("riskLevel", o.getRiskLevel());
        m.put("needMoreData", o.getNeedMoreData());
        m.put("keyMetrics", o.getKeyMetrics());
        m.put("findings", o.getFindings());
        m.put("recommendations", o.getRecommendations());
        m.put("questions", o.getQuestions());
        return m;
    }

    public LinkedHashMap<String, Object> buildPurchaseOverviewPayload(AiRunState state) {
        LinkedHashMap<String, Object> purchaseCtx = new LinkedHashMap<>();
        purchaseCtx.put("用户问题", nz(state.getNormalizedUserInput()));
        purchaseCtx.putAll(summarizePurchaseToolPresenceCn(state));
        return purchaseCtx;
    }

    public LinkedHashMap<String, Object> buildWarehouseOverviewPayload(AiRunState state) {
        return summarizeWarehouseToolPresenceCn(state);
    }

    private static String authoritativeBusinessOverviewComposerHint(AiRunState state) {
        if (BusinessOverviewDeterministicSummaryBuilder.hasAuthoritativeBusinessOverviewRevenuePlan(state)) {
            return "numericHeadlineText 必须为 DailyRevenueAnswerPlan（revenue_query）口径，禁止抄写 business_overview_query"
                    + "看板中的总营业额/统计天数；purchaseCoreAnswerPlanSentence 若为采购 AnswerPlan 摘要须复述；"
                    + "摘录中与 headline 的数字冲突时一律以 headline 为准。";
        }
        return "numericHeadlineText 必须由模型在查询范围复述之后照抄复述；缺失项仅写暂无";
    }

    private static List<Map<String, Object>> capDishBriefs(List<AiDishProfitDishBrief> xs, int max) {
        if (xs == null || xs.isEmpty()) {
            return List.of();
        }
        List<AiDishProfitDishBrief> deduped = dedupeDishBriefsForComposer(xs);
        List<AiDishProfitDishBrief> sub = deduped.size() <= max ? deduped : deduped.subList(0, max);
        List<Map<String, Object>> out = new ArrayList<>();
        for (AiDishProfitDishBrief b : sub) {
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("dishName", nz(b.getDishName()));
            row.put("salesQty", nz(b.getSalesQty()));
            row.put("salesAmount", nz(b.getSalesAmount()));
            row.put("theoreticalCost", nz(b.getTheoreticalCost()));
            row.put("actualCost", nz(b.getActualCost()));
            row.put("grossProfitAmount", nz(b.getGrossProfitAmount()));
            row.put("grossProfitRate", nz(b.getGrossProfitRate()));
            row.put("riskReason", nz(b.getRiskReason()));
            out.add(row);
        }
        return out;
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

    private static List<AiOverviewCoveredStoreItem> capCoveredStoresPreview(List<AiOverviewCoveredStoreItem> full,
            int max) {
        if (full == null || full.isEmpty()) {
            return List.of();
        }
        if (full.size() <= max) {
            return new ArrayList<>(full);
        }
        return new ArrayList<>(full.subList(0, max));
    }

    private static List<AiOverviewStoreIssueItem> capIssueItemsPreview(List<AiOverviewStoreIssueItem> full, int max) {
        if (full == null || full.isEmpty()) {
            return List.of();
        }
        if (full.size() <= max) {
            return new ArrayList<>(full);
        }
        return new ArrayList<>(full.subList(0, max));
    }

    private static Map<String, Object> excerptDashboardStatsCn(Map<String, Object> stats) {
        if (stats == null || stats.isEmpty()) {
            return Map.of();
        }
        String[] keys = {
                "数据口径说明",
                "统计开始日期", "统计结束日期",
                "统计天数", "总营业额", "日均营业额", "日均订单数", "客单价",
                "平台费合计", "退款合计", "外卖营业额合计", "日均净收入",
                "盈亏状态", "利润率", "日均利润含库存成本"
        };
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        for (String k : keys) {
            if (stats.containsKey(k)) {
                Object v = stats.get(k);
                if (v == null) {
                    out.put(k, "暂无");
                } else if ("数据口径说明".equals(k) || "盈亏状态".equals(k)) {
                    out.put(k, v.toString().trim());
                } else if ("统计开始日期".equals(k) || "统计结束日期".equals(k)) {
                    String d = v.toString().trim();
                    out.put(k, d.isBlank() ? "暂无" : d);
                } else {
                    out.put(k, plainStatSnippet(v));
                }
            }
        }
        return out;
    }

    private static Map<String, Object> excerptDashboardStatsCnForBusinessOverview(AiRunState state,
            AiBusinessOverviewResult o) {
        Map<String, Object> excerpt = excerptDashboardStatsCn(o.getDashboardStatsCn());
        if (!BusinessOverviewDeterministicSummaryBuilder.hasAuthoritativeBusinessOverviewRevenuePlan(state)) {
            return excerpt;
        }
        if (excerpt == null || excerpt.isEmpty()) {
            return excerpt;
        }
        LinkedHashMap<String, Object> scrubbed = new LinkedHashMap<>(excerpt);
        scrubbed.remove("总营业额");
        scrubbed.remove("日均营业额");
        scrubbed.remove("统计天数");
        return scrubbed;
    }

    private static String plainStatSnippet(Object v) {
        if (v == null || v.toString().isBlank()) {
            return "暂无";
        }
        String raw = v.toString().trim();
        if ("-".equals(raw) || "—".equals(raw) || "不适用".equals(raw)) {
            return raw;
        }
        if (v instanceof String) {
            return AiNumericPlainText.plainNumber(v);
        }
        if (v instanceof Number) {
            return AiNumericPlainText.plainNumber(v);
        }
        try {
            return AiNumericPlainText.plainNumber(raw);
        } catch (Exception e) {
            return raw;
        }
    }

    private static LinkedHashMap<String, Object> summarizePurchaseToolPresenceCn(AiRunState state) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        AiTimeWindowTextFormatter.UserPhrases tw = AiTimeWindowTextFormatter.forAnswer(state);
        m.put("timeRangeForAnswer", tw.getBracketTimeRangeLine());
        Map<String, Object> innerPo = toolDataInnerMap(state, AiBusinessToolIds.PURCHASE_OVERVIEW);
        Object pOverview = innerPo.get("purchaseOverview");
        if (pOverview instanceof Map<?, ?> pom) {
            m.put("采购概览", pom);
            Object scs = pom.get("storeCoverageSummary");
            if (scs != null && !scs.toString().isBlank()) {
                m.put("集团门店采购覆盖说明_须向用户复述", scs.toString().trim());
            }
        }
        Map<String, Object> pu = toolDataInnerMap(state, AiBusinessToolIds.PURCHASE_QUERY);
        Map<String, Object> stk = toolDataInnerMap(state, AiBusinessToolIds.STOCK_REDUCE_QUERY);
        m.put("采购入库有可读结果", !pu.isEmpty());
        m.put("核销出库有可读结果", !stk.isEmpty());
        if (isBusinessOverviewToPurchaseConvergence(state)) {
            m.put("答复口径",
                    "用户用经营类话术提问，但账号为门店采购：仅总结采购笔数、采购金额、采购方式拆分（若有）及核销/出库结构；禁止营业额与毛利类表述；勿提采购总重量。");
        }
        if (!pu.isEmpty()) {
            m.put("统计周期内采购入库金额_元", plainNumericHint(pu.get("purchaseSubTotal")));
            m.put("采购明细行数", plainNumericHint(pu.get("purchaseRowCount")));
        }
        if (!stk.isEmpty()) {
            m.put("核销生产耗用合计", plainNumericHint(stk.get("productionTotal")));
            m.put("核销出品合计", plainNumericHint(stk.get("produceTotal")));
            m.put("核销废弃合计_type2", plainNumericHint(stk.get("wasteTotal")));
            m.put("核销损耗合计_type3", plainNumericHint(stk.get("lossTotal")));
            m.put("核销退货合计", plainNumericHint(stk.get("returnTotal")));
        }
        return m;
    }

    private static boolean isBusinessOverviewToPurchaseConvergence(AiRunState state) {
        Map<String, String> ic = state.getIntentConvergence();
        return ic != null
                && "BUSINESS_OVERVIEW".equals(ic.get("from"))
                && "PURCHASE_OVERVIEW".equals(ic.get("to"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toolDataInnerMap(AiRunState state, String toolKey) {
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

    private static LinkedHashMap<String, Object> summarizeWarehouseToolPresenceCn(AiRunState state) {
        Map<String, Object> wo = extractWarehouseOverviewPayload(state);
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("用户问题", nz(state.getNormalizedUserInput()));
        m.put("称谓与开篇_模型须严格遵守", warehouseSalutationDirective(state, wo));
        if (!wo.isEmpty()) {
            m.put("库房库存概览工具已聚合", true);
            Object qb = wo.get("queryScopeBanner");
            if (qb != null && !qb.toString().isBlank()) {
                m.put("查询范围_queryScopeBanner", qb.toString().trim());
            }
            if (state.isGroupWarehouseStockOverview()) {
                boolean mixWh = warehouseOverviewHasVisibleWarehouses(wo);
                m.put("查询范围",
                        mixWh ? "集团下属门店/库房库存汇总（默认不按登录 departmentId 单一门店）"
                                : "集团下属门店库存汇总（默认不按登录 departmentId 单一门店）");
                m.put("答复禁忌", "禁止反问指定门店或品类；勿默认称呼「店长」；禁止营业额/订单/客单价。");
            }
            Object st = wo.get("scopeType");
            if (st != null && !st.toString().isBlank()) {
                m.put("scopeType", st.toString().trim());
            }
            Object sn = wo.get("scopeName");
            if (sn != null && !sn.toString().isBlank()) {
                m.put("scopeName", sn.toString().trim());
            }
            if (wo.containsKey("visibleStoreCount")) {
                m.put("纳入门店数_visibleStoreCount", plainNumericHint(wo.get("visibleStoreCount")));
            }
            if (wo.containsKey("dataAvailableStoreCount")) {
                m.put("有库存信号门店数", plainNumericHint(wo.get("dataAvailableStoreCount")));
            }
            if (wo.containsKey("dataMissingStoreCount")) {
                m.put("暂无库存信号门店数", plainNumericHint(wo.get("dataMissingStoreCount")));
            }
            if (wo.get("coveredStores") instanceof List<?> cov && !cov.isEmpty()) {
                m.put("有数据门店摘要条数", cov.size());
            }
            if (wo.get("dataMissingStores") instanceof List<?> miss && !miss.isEmpty()) {
                m.put("缺数据门店摘要条数", miss.size());
            }
            m.put("摘要_summary", nz(wo.get("summary")));
            m.put("库存商品种数", plainNumericHint(wo.get("stockItemCount")));
            m.put("库存批次行数", plainNumericHint(wo.get("stockBatchRowCount")));
            m.put("库存剩余总金额约_元", plainNumericHint(wo.get("totalStockAmount")));
            m.put("库存剩余总重量", fmtStockWeightCn(wo.get("totalStockWeight")));
            m.put("区间内入库金额约_元", plainNumericHint(wo.get("inboundAmount")));
            m.put("区间内入库重量", fmtStockWeightCn(wo.get("inboundWeight")));
            m.put("核销出品金额", plainNumericHint(wo.get("produceAmount")));
            m.put("核销出库合计金额", plainNumericHint(wo.get("stockReduceAmount")));
            m.put("核销废弃金额_type2", plainNumericHint(wo.get("wasteAmount")));
            m.put("核销损耗金额_type3", plainNumericHint(wo.get("lossAmount")));
            m.put("核销退货金额", plainNumericHint(wo.get("returnAmount")));
            m.put("低库存商品条目", wo.get("lowStockItems"));
            m.put("积压偏高商品条目", wo.get("overStockItems"));
            m.put("早入库仍有剩余批次", wo.get("inactiveStockItems"));
            m.put("建议动作", wo.get("recommendations"));
        } else {
            Map<String, Object> sq = toolDataInnerMap(state, AiBusinessToolIds.STOCK_QUERY);
            Map<String, Object> stk = toolDataInnerMap(state, AiBusinessToolIds.STOCK_REDUCE_QUERY);
            m.put("库房库存快照有结果", !sq.isEmpty());
            m.put("核销出库有结果", !stk.isEmpty());
            if (!sq.isEmpty()) {
                m.put("库存批次行数", plainNumericHint(sq.get("stockBatchRowCount")));
                m.put("库存剩余金额约_元", plainNumericHint(sq.get("stockRestSubtotal")));
                m.put("库存剩余重量汇总", fmtStockWeightCn(sq.get("stockRestWeightTotal")));
                m.put("区间内入库批次金额约_元", plainNumericHint(sq.get("periodInboundSubtotal")));
                m.put("区间内入库重量汇总", fmtStockWeightCn(sq.get("periodInboundWeightTotal")));
            }
            if (!stk.isEmpty()) {
                m.put("核销生产耗用合计", plainNumericHint(stk.get("productionTotal")));
                m.put("核销出品", plainNumericHint(stk.get("produceTotal")));
                m.put("核销废弃_type2", plainNumericHint(stk.get("wasteTotal")));
                m.put("核销损耗_type3", plainNumericHint(stk.get("lossTotal")));
                m.put("核销退货", plainNumericHint(stk.get("returnTotal")));
            }
        }
        if (isBusinessToWarehouseStockConvergence(state)) {
            m.put("答复口径", "经营类话术已切换为库房库存视角：禁止营业额与菜品销售；不作采购员式采购分析主线。");
        } else if (state.isGroupWarehouseStockOverview()) {
            m.put("答复口径", "集团库存汇总：开篇写明集团范围；禁止反问指定门店；禁止营业额/订单/客单价；勿默认称呼店长。");
        }
        return m;
    }

    private static boolean isBusinessToWarehouseStockConvergence(AiRunState state) {
        Map<String, String> ic = state.getIntentConvergence();
        return ic != null
                && "BUSINESS_OVERVIEW".equals(ic.get("from"))
                && "WAREHOUSE_STOCK_OVERVIEW".equals(ic.get("to"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractWarehouseOverviewPayload(AiRunState state) {
        Map<String, Object> inner = toolDataInnerMap(state, AiBusinessToolIds.WAREHOUSE_STOCK_OVERVIEW);
        Object wo = inner.get("warehouseOverview");
        if (!(wo instanceof Map)) {
            return Map.of();
        }
        Map<String, Object> raw = (Map<String, Object>) wo;
        if (raw.isEmpty()) {
            return Map.of();
        }
        return new LinkedHashMap<>(raw);
    }

    private static boolean warehouseOverviewHasVisibleWarehouses(Map<String, Object> wo) {
        if (wo == null) {
            return false;
        }
        Object v = wo.get("visibleWarehouses");
        return v instanceof List<?> l && !l.isEmpty();
    }

    private static String fmtStockWeightCn(Object value) {
        return plainNumericHint(value) + " " + W_STOCK_WEIGHT_UNIT;
    }

    private static String warehouseSalutationDirective(AiRunState state, Map<String, Object> wo) {
        String rc = resolveAiRoleCode(state);
        boolean groupScope = state.isGroupWarehouseStockOverview() || warehouseOverviewIndicatesGroupScope(wo);
        if (AiRoleCodes.GROUP_MANAGER.equals(rc) || groupScope) {
            return "【开篇】用「以下是集团范围库存汇总」或等价客观句起首（可接门店名枚举）；禁止「店长」「老板」及「好的，店长」类寒暄；不要反问指定门店。";
        }
        if (AiRoleCodes.STORE_MANAGER.equals(rc)) {
            return "【开篇】可称呼「店长」，也可无称呼直接写库存数据。";
        }
        if (isWarehouseStaffRole(rc)) {
            return "【开篇】用「以下是你当前可查看库房/所属门店」类客观句起首（可与 queryScopeBanner 一致带出门店名）；禁止「店长」「老板」；勿写「店长，本库房…」。";
        }
        if (isPurchasingRoleForWarehouse(rc)) {
            return "【开篇】可用「以下按采购视角分析」起首（再写库存数字）；禁止「店长」「老板」。";
        }
        return "【开篇】若不确定对方具体岗位，不要使用老板/店长/库管等称呼；直接写库存客观表述。";
    }

    private static boolean warehouseOverviewIndicatesGroupScope(Map<String, Object> wo) {
        return wo != null && !wo.isEmpty()
                && "GROUP".equalsIgnoreCase(String.valueOf(wo.get("scopeType")).trim());
    }

    private static String resolveAiRoleCode(AiRunState state) {
        AiUserContext ctx = state.getAiUserContext();
        if (ctx != null && ctx.getRoleCode() != null && !ctx.getRoleCode().isBlank()) {
            return ctx.getRoleCode().trim();
        }
        if (ctx != null && ctx.getSourceAdminRole() != null) {
            return AiRoleMapper.resolveAdmin(ctx.getSourceAdminRole())
                    .map(AiRoleMapper.AiRoleDefinition::roleCode)
                    .orElse("");
        }
        return "";
    }

    private static boolean isPurchasingRoleForWarehouse(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return false;
        }
        return AiRoleCodes.STORE_PURCHASER.equals(roleCode)
                || AiRoleCodes.GROUP_PURCHASER.equals(roleCode)
                || AiRoleCodes.WAREHOUSE_PURCHASER.equals(roleCode)
                || AiRoleCodes.CENTRAL_KITCHEN_PURCHASER.equals(roleCode)
                || AiRoleCodes.REGION_PURCHASER.equals(roleCode);
    }

    private static boolean isWarehouseStaffRole(String roleCode) {
        return AiRoleCodes.WAREHOUSE_MANAGER.equals(roleCode)
                || AiRoleCodes.REGION_WAREHOUSE.equals(roleCode);
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

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String nz(Object o) {
        return o == null ? "" : o.toString();
    }
}
