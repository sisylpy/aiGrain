package com.nongxinle.ai.harness;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiFollowUpResolution;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.context.AiResolvedDataScope;
import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将 {@link AiResolvedQueryContext} 压成 GET /api/ai/runs/{id} 可用的调试摘要（仅 harness / local 开启开关时下发）。
 */
public final class AiHarnessResolvedContextSummarizer {

    private AiHarnessResolvedContextSummarizer() {
    }

    public static Map<String, Object> summarize(AiResolvedQueryContext ctx, Long conversationId) {
        return summarize(ctx, conversationId, null);
    }

    public static Map<String, Object> summarize(AiResolvedQueryContext ctx, Long conversationId, AiRunState state) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        if (ctx == null) {
            return out;
        }
        Long cid = conversationId;
        if (cid == null && ctx.getPreviousTurn() != null) {
            cid = ctx.getPreviousTurn().getConversationId();
        }
        out.put("conversationId", cid);
        out.put("runId", ctx.getRunId());
        out.put("effectiveIntentCode", blankToNull(ctx.getEffectiveIntentCode()));
        out.put("effectivePathCode", blankToNull(ctx.getEffectivePathCode()));
        out.put("intent", blankToNull(ctx.getEffectiveIntentCode()));
        out.put("path", blankToNull(ctx.getEffectivePathCode()));
        out.put("effectiveTimeWindowSource", blankToNull(ctx.getEffectiveTimeWindowSource()));
        out.put("timeSource", blankToNull(ctx.getEffectiveTimeWindowSource()));
        out.put("effectiveIntentSource", blankToNull(ctx.getEffectiveIntentSource()));
        out.put("effectiveScopeSource", blankToNull(ctx.getEffectiveScopeSource()));
        AiResolvedTimeWindow tw = ctx.getTimeWindow();
        LinkedHashMap<String, Object> timeBlock = new LinkedHashMap<>();
        if (tw != null) {
            out.put("startDate", tw.getStartDate() != null ? tw.getStartDate().toString() : null);
            out.put("endDate", tw.getEndDate() != null ? tw.getEndDate().toString() : null);
            out.put("timeLabel", blankToNull(tw.getTimeLabel()));
            out.put("timeDisplayText", blankToNull(tw.getDisplayText()));
            out.put("timeInheritedFromPrevious", tw.isInheritedFromPreviousTurn());
            out.put("timeExplicitInMessage", tw.isExplicitTimeMentioned());
            timeBlock.put("start", tw.getStartDate() != null ? tw.getStartDate().toString() : null);
            timeBlock.put("end", tw.getEndDate() != null ? tw.getEndDate().toString() : null);
            timeBlock.put("label", blankToNull(tw.getTimeLabel()));
            timeBlock.put("displayText", blankToNull(tw.getDisplayText()));
        } else {
            out.put("startDate", null);
            out.put("endDate", null);
            out.put("timeLabel", null);
        }
        out.put("time", timeBlock);

        AiResolvedOrgScope org = ctx.getOrgScope();
        out.put("scopeType", org != null ? blankToNull(org.getScopeType()) : null);
        out.put("visibleStores", summarizeStores(org));

        AiResolvedDataScope ds = ctx.getDataScope();
        if (ds != null) {
            List<Long> roots = longList(ds.getVisibleStoreRootIds());
            List<Long> childOnly = longList(ds.getChildDepartmentIds());
            List<Long> sqlExpanded = longList(ds.getEffectiveSqlDepartmentIds());
            String qsm = blankToNull(ds.getQueryScopeMode());

            out.put("queryScopeKind", blankToNull(ds.getQueryScopeKind()));
            out.put("queryStoreIds", intList(ds.getQueryStoreIds()));
            out.put("queryRealDepartmentIds", intList(ds.getQueryRealDepartmentIds()));
            out.put("queryDistributerId", ds.getQueryDistributerId());
            out.put("storeToDepartmentIds", stringifyStoreToDeptMap(ds.getStoreToDepartmentIds()));

            out.put("visibleStoreRootIds", new ArrayList<>(roots));
            out.put("storeRootDepartmentIds", new ArrayList<>(roots));
            out.put("childDepartmentIds", new ArrayList<>(childOnly));
            out.put("expandedChildDepartmentIds", new ArrayList<>(childOnly));
            out.put("expandedSqlDepartmentIds", new ArrayList<>(sqlExpanded));
            out.put("effectiveSqlDepartmentIds", new ArrayList<>(sqlExpanded));
            out.put("revenueSqlDepartmentIds", longList(ds.getSqlDepartmentIdsForDomain(AiResolvedDataScope.SQL_DOMAIN_REVENUE)));
            out.put("purchaseSqlDepartmentIds", longList(ds.getSqlDepartmentIdsForDomain(AiResolvedDataScope.SQL_DOMAIN_PURCHASE)));
            out.put("stockSqlDepartmentIds", longList(ds.getSqlDepartmentIdsForDomain(AiResolvedDataScope.SQL_DOMAIN_STOCK)));
            out.put("dishProfitSqlDepartmentIds", longList(ds.getSqlDepartmentIdsForDomain(AiResolvedDataScope.SQL_DOMAIN_DISH_PROFIT)));
            out.put("stockReduceSqlDepartmentIds", longList(ds.getSqlDepartmentIdsForDomain(AiResolvedDataScope.SQL_DOMAIN_STOCK_REDUCE)));

            out.put("visibleStoreIds", longList(ds.getVisibleStoreIds()));
            out.put("visibleWarehouseIds", longList(ds.getVisibleWarehouseIds()));
            out.put("explicitChildDepartmentIds", longList(ds.getExplicitChildDepartmentIds()));
            out.put("queryScopeMode", qsm);
            out.put("queryLevel", qsm);
            out.put("storeToChildDepartmentIds", stringifyStoreChildMap(ds.getStoreToChildDepartmentIds()));
            out.put("departmentScopeModelNote",
                    "主查询维度：queryScopeKind=STORE 用 queryStoreIds（门店根）；DEPARTMENT 用 queryRealDepartmentIds（仅真实部门）；"
                            + "DISTRIBUTER 用 queryDistributerId。业务表 department_id IN 用 expandedSqlDepartmentIds（根∪子），"
                            + "勿与门店列表混淆。storeToDepartmentIds 仅结构说明。");
        } else {
            out.put("visibleStoreIds", null);
            out.put("visibleStoreRootIds", null);
            out.put("storeRootDepartmentIds", null);
            out.put("childDepartmentIds", null);
            out.put("expandedChildDepartmentIds", null);
            out.put("queryScopeKind", null);
            out.put("queryStoreIds", null);
            out.put("queryRealDepartmentIds", null);
            out.put("queryDistributerId", null);
            out.put("storeToDepartmentIds", null);
            out.put("expandedSqlDepartmentIds", null);
            out.put("effectiveSqlDepartmentIds", null);
            out.put("revenueSqlDepartmentIds", null);
            out.put("purchaseSqlDepartmentIds", null);
            out.put("stockSqlDepartmentIds", null);
            out.put("dishProfitSqlDepartmentIds", null);
            out.put("stockReduceSqlDepartmentIds", null);
            out.put("visibleWarehouseIds", null);
            out.put("explicitChildDepartmentIds", null);
            out.put("queryScopeMode", null);
            out.put("queryLevel", null);
            out.put("storeToChildDepartmentIds", null);
            out.put("departmentScopeModelNote", null);
        }

        AiResolvedQueryIntent qi = ctx.getQueryIntent();
        String pst = qi != null ? blankToNull(qi.getPurchaseSourceType()) : null;
        String sidWireRaw = qi != null ? blankToNull(qi.getStructuredIntentDetail()) : null;
        String utteranceProbe = utteranceProbeText(ctx);
        String sidWire = sidWireRaw;
        if (sidWire == null && StringUtils.hasText(utteranceProbe)
                && AiQuerySemanticLexicon.looksLikeSupplierRanking(utteranceProbe)) {
            sidWire = AiQuerySemanticLexicon.STRUCTURED_SUPPLIER_AMOUNT_RANKING;
        }
        String sidCode = AiQuerySemanticLexicon.toStructuredIntentDetailDebugCode(sidWire);
        // 调试/UI：structuredIntentDetail 为人类可读枚举名（如 SUPPLIER_AMOUNT_RANKING）；wire 放 structuredIntentDetailWire 供 Harness 比对。
        String sidDisplay = sidCode != null ? sidCode : sidWire;
        // 供货商排行回合故意不把 purchaseSourceType 放进查询意图（全口径挑出真实供货商 Top）；调试面板若为 null 易显示成「未返回」，这里显式标 ALL。
        if (pst == null && "SUPPLIER_AMOUNT_RANKING".equals(sidCode)) {
            pst = AiQuerySemanticLexicon.SOURCE_ALL;
        }
        out.put("purchaseSourceType", pst);
        out.put("structuredIntentDetailWire", sidWire);
        out.put("structuredIntentDetail", sidDisplay);
        out.put("structuredIntentDetailCode", sidCode);
        out.put("structuredIntentDetailPresent", sidWire != null && !sidWire.isBlank());

        String effectivePath = blankToNull(ctx.getEffectivePathCode());
        boolean stockReduceStructured = AiQuerySemanticLexicon.isStructuredStockReduceDetail(sidWire);
        // Run Debug：与 structuredIntentDetail / structuredIntentDetailCode 对齐；出库 path 下用枚举名便于比对 GOODS_OUTBOUND_RANKING、PRODUCE_CONSUME 等
        String stockReduceTypeVal = null;
        if (AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY.equals(effectivePath) && sidCode != null) {
            stockReduceTypeVal = sidCode;
        } else if (stockReduceStructured && sidDisplay != null) {
            stockReduceTypeVal = sidDisplay;
        }
        out.put("stockReduceType", stockReduceTypeVal);

        boolean dishStructuredProbe = AiQuerySemanticLexicon.isNonOverviewDishProfitStructuredDetail(sidWire);
        String dishProfitStructuredDetailVal = null;
        if (AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(effectivePath) && sidCode != null) {
            dishProfitStructuredDetailVal = sidCode;
        } else if (dishStructuredProbe && sidDisplay != null) {
            dishProfitStructuredDetailVal = sidDisplay;
        }
        out.put("dishProfitStructuredDetail", dishProfitStructuredDetailVal);

        out.put("mentionedDishName", blankToNull(ctx.getMentionedDishName()));
        out.put("dishName", blankToNull(ctx.getMentionedDishName()));
        out.put("dishProfitMetricType", blankToNull(ctx.getDishProfitMetricType()));

        out.put("mentionedStore", resolveMentionedStore(ctx));

        AiFollowUpResolution fur = ctx.getFollowUpResolution();
        if (fur != null) {
            out.put("followUp", fur.isFollowUp());
            out.put("followUpType", blankToNull(fur.getFollowUpType()));
        } else {
            out.put("followUp", false);
            out.put("followUpType", null);
        }
        AiConversationTurnMemory prev = ctx.getPreviousTurn();
        if (prev != null) {
            LinkedHashMap<String, Object> p = new LinkedHashMap<>();
            p.put("lastIntentCode", blankToNull(prev.getLastIntentCode()));
            p.put("lastPathCode", blankToNull(prev.getLastPathCode()));
            p.put("lastStructuredIntentDetail", blankToNull(prev.getLastStructuredIntentDetail()));
            if (StringUtils.hasText(prev.getLastStructuredIntentDetail())) {
                String prevCode = AiQuerySemanticLexicon.toStructuredIntentDetailDebugCode(
                        prev.getLastStructuredIntentDetail());
                if (prevCode != null) {
                    p.put("lastStockReduceType", prevCode);
                }
            }
            p.put("lastPurchaseSourceType", blankToNull(prev.getLastPurchaseSourceType()));
            p.put("lastStartDate", blankToNull(prev.getLastStartDate()));
            p.put("lastEndDate", blankToNull(prev.getLastEndDate()));
            p.put("lastTimeLabel", blankToNull(prev.getLastTimeLabel()));
            p.put("lastScopeType", blankToNull(prev.getLastScopeType()));
            p.put("lastMentionedDishName", blankToNull(prev.getLastMentionedDishName()));
            out.put("previousTurnSummary", p);
        } else {
            out.put("previousTurnSummary", null);
        }
        appendExecutionHints(out, state);
        return out;
    }

    private static void appendExecutionHints(LinkedHashMap<String, Object> out, AiRunState state) {
        if (state == null) {
            out.put("usedToolId", null);
            out.put("buildInsightUsed", false);
            out.put("usedBuildInsight", false);
            out.put("buildInsightRequest", null);
            out.put("buildInsightInputStoreRootIds", null);
            out.put("buildInsightInputDepartmentIdsAllowFilter", null);
            out.put("dishesCount", null);
            out.put("dishLineReturned", null);
            out.put("salesDishCount", null);
            out.put("riskLevel", null);
            out.put("resolvedVisibleStoreRootIds", null);
            out.put("resolvedEffectiveSqlDepartmentIds", null);
            out.put("resolvedDishProfitSqlDepartmentIds", null);
            out.put("departmentIdSemanticsHint", null);
            return;
        }
        String used = null;
        List<String> tools = state.getDataPlanTools();
        if (tools != null) {
            for (String t : tools) {
                if (AiBusinessToolIds.DISH_PROFIT_ANALYSIS.equals(t)) {
                    used = t;
                    break;
                }
            }
            if (used == null && !tools.isEmpty()) {
                used = tools.get(0);
            }
        }
        out.put("usedToolId", used);
        boolean bi = false;
        Object bir = null;
        Object dishesCount = null;
        Object dishLineRet = null;
        Object salesDishCount = null;
        Object riskLevel = null;
        Object pay = state.getToolResults() == null ? null : state.getToolResults().get(AiBusinessToolIds.DISH_PROFIT_ANALYSIS);
        if (pay instanceof Map<?, ?> tm) {
            Object data = tm.get("data");
            if (data instanceof Map<?, ?> dm) {
                bi = Boolean.TRUE.equals(dm.get("buildInsightUsed")) || Boolean.TRUE.equals(dm.get("usedBuildInsight"))
                        || dm.containsKey("businessInsightSummary");
                bir = dm.get("buildInsightRequest");
                dishesCount = dm.get("dishLineCountFull");
                dishLineRet = dm.get("dishLineReturned");
                salesDishCount = dm.get("salesDishCount");
                riskLevel = dm.get("riskLevel");
            }
        }
        out.put("buildInsightUsed", bi);
        out.put("usedBuildInsight", bi);
        out.put("buildInsightRequest", bir);
        applyFlattenedBuildInsightDebugFields(out, bir);
        out.put("dishesCount", dishesCount);
        out.put("dishLineReturned", dishLineRet);
        out.put("salesDishCount", salesDishCount);
        out.put("riskLevel", riskLevel);
        AiResolvedQueryContext rqExe = state.getResolvedQueryContext();
        if (rqExe != null && rqExe.getDataScope() != null) {
            AiResolvedDataScope dsx = rqExe.getDataScope();
            out.put("resolvedVisibleStoreRootIds", new ArrayList<>(longList(dsx.getVisibleStoreRootIds())));
            out.put("resolvedEffectiveSqlDepartmentIds", new ArrayList<>(longList(dsx.getEffectiveSqlDepartmentIds())));
            out.put("resolvedDishProfitSqlDepartmentIds",
                    new ArrayList<>(longList(dsx.getSqlDepartmentIdsForDomain(AiResolvedDataScope.SQL_DOMAIN_DISH_PROFIT))));
            out.put("departmentIdSemanticsHint",
                    "门店展示=visibleStores/queryStoreIds；department_id IN=expandedSqlDepartmentIds；语义部门=queryRealDepartmentIds（仅 DEPARTMENT 口径）");
        } else {
            out.put("resolvedVisibleStoreRootIds", null);
            out.put("resolvedEffectiveSqlDepartmentIds", null);
            out.put("resolvedDishProfitSqlDepartmentIds", null);
            out.put("departmentIdSemanticsHint", null);
        }
    }

    /**
     * Run Debug / GET run：将嵌套 {@code buildInsightRequest} 中的关键输入拉到顶层，避免 UI 只读平铺字段时漏掉。
     */
    private static void applyFlattenedBuildInsightDebugFields(LinkedHashMap<String, Object> out, Object buildInsightRequest) {
        if (!(buildInsightRequest instanceof Map<?, ?> m)) {
            out.put("buildInsightInputStoreRootIds", null);
            out.put("buildInsightInputDepartmentIdsAllowFilter", null);
            return;
        }
        out.put("buildInsightInputStoreRootIds", m.get("buildInsightInputStoreRootIds"));
        out.put("buildInsightInputDepartmentIdsAllowFilter", m.get("buildInsightInputDepartmentIdsAllowFilter"));
    }

    private static String resolveMentionedStore(AiResolvedQueryContext ctx) {
        AiFollowUpResolution fur = ctx.getFollowUpResolution();
        if (fur != null && StringUtils.hasText(fur.getStoreScopeFollowUpMentionedName())) {
            return fur.getStoreScopeFollowUpMentionedName().trim();
        }
        AiResolvedOrgScope org = ctx.getOrgScope();
        if (org != null && org.getVisibleStores() != null && org.getVisibleStores().size() == 1) {
            AiStoreScopeDTO s = org.getVisibleStores().get(0);
            if (s != null && StringUtils.hasText(s.getStoreName())) {
                return s.getStoreName().trim();
            }
        }
        return null;
    }

    private static List<Long> longList(List<Long> in) {
        if (in == null || in.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(in);
    }

    private static List<Integer> intList(List<Integer> in) {
        if (in == null || in.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(in);
    }

    /**
     * JSON 友好：{@code {"1":[2,5],"3":[4]}}。
     */
    private static Map<String, List<Integer>> stringifyStoreToDeptMap(Map<Integer, List<Integer>> raw) {
        Map<String, List<Integer>> out = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        for (Map.Entry<Integer, List<Integer>> e : raw.entrySet()) {
            if (e.getKey() == null) {
                continue;
            }
            String k = String.valueOf(e.getKey());
            List<Integer> v = e.getValue();
            out.put(k, v != null ? new ArrayList<>(v) : new ArrayList<>());
        }
        return out;
    }

    /**
     * JSON 友好的 {@code {"1":[2,5],"3":[4]}} 形式（字符串键更易读）。
     */
    private static Map<String, List<Long>> stringifyStoreChildMap(Map<Long, List<Long>> raw) {
        Map<String, List<Long>> out = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        for (Map.Entry<Long, List<Long>> e : raw.entrySet()) {
            if (e.getKey() == null) {
                continue;
            }
            String k = String.valueOf(e.getKey());
            List<Long> v = e.getValue();
            out.put(k, v != null ? new ArrayList<>(v) : new ArrayList<>());
        }
        return out;
    }

    private static List<Map<String, Object>> summarizeStores(AiResolvedOrgScope org) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (org == null || org.getVisibleStores() == null) {
            return list;
        }
        for (AiStoreScopeDTO s : org.getVisibleStores()) {
            if (s == null) {
                continue;
            }
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("storeDepartmentId", s.getStoreDepartmentId());
            row.put("storeName", s.getStoreName());
            list.add(row);
        }
        return list;
    }

    private static String utteranceProbeText(AiResolvedQueryContext ctx) {
        if (ctx == null) {
            return null;
        }
        if (StringUtils.hasText(ctx.getOriginalQuestion())) {
            return ctx.getOriginalQuestion().trim();
        }
        if (StringUtils.hasText(ctx.getNormalizedQuestion())) {
            return ctx.getNormalizedQuestion().trim();
        }
        return null;
    }

    private static String blankToNull(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        return s.trim();
    }
}
