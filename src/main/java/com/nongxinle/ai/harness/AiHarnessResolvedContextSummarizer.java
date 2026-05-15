package com.nongxinle.ai.harness;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiFollowUpResolution;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.context.AiResolvedDataScope;
import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiSemanticStoreNarrowingDiagnostics;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.resolver.AiMultiTurnTimeWindowPolicy;
import com.nongxinle.ai.harness.replay.AiHarnessReplayContextProbes;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.dto.business.StockReduceAnswerPlan;
import com.nongxinle.ai.dto.business.DailyRevenueAnswerPlan;
import com.nongxinle.ai.dto.business.DiagnosisPlan;
import com.nongxinle.ai.dto.business.DishProfitAnswerPlan;
import com.nongxinle.ai.dto.business.DishSalesAnswerPlan;
import com.nongxinle.ai.dto.business.BusinessDiagnosisPlan;
import com.nongxinle.ai.dto.business.BusinessDiagnosisCompositeComposeResult;
import com.nongxinle.ai.planner.BusinessDiagnosisCompositeExecutionMode;
import com.nongxinle.ai.planner.BusinessDiagnosisCompositeExecutionResult;
import com.nongxinle.ai.planner.BusinessDiagnosisCompositeGateResult;
import com.nongxinle.ai.planner.BusinessDiagnosisCompositeReadonlyComposer;
import com.nongxinle.ai.security.AiPermissionDenied;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.alibaba.fastjson2.JSON;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 将 {@link AiResolvedQueryContext} 压成 GET /api/ai/runs/{id} 可用的调试摘要（仅 harness / local 开启开关时下发）。
 */
public final class AiHarnessResolvedContextSummarizer {

    /** 与 {@link com.nongxinle.ai.graph.business.BusinessToolExecutionNode} Promote Keys 的子集对齐；若在 masterDebug 仅存于 nested multiMaster，则回填顶层摘要。 */
    private static final String[] BUSINESS_OVERVIEW_MULTI_ORCHESTRATION_FLAT_FALLBACK_KEYS = {
            "businessOverviewMultiAgentBatchCompleted",
            "businessOverviewAllExpectedDomainsAttempted",
            "businessOverviewMultiAgentAnyDomainSuccess",
            "businessOverviewMultiAgentBatchAttempted",
            "businessOverviewMultiAgentAllDomainsSkipped",
            "businessOverviewMultiAgentBatchUsableForDiagnosis",
            "businessOverviewSuccessfulDomains",
    };

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
        String effTimeSrc =
                reconcileEffectiveTimeWindowSourceForHarness(
                        ctx.getEffectiveTimeWindowSource(), ctx.getTimeWindow(), ctx.getPreviousTurn(), ctx);
        out.put("effectiveTimeWindowSource", blankToNull(effTimeSrc));
        out.put("timeSource", blankToNull(effTimeSrc));
        out.put("effectiveIntentSource", blankToNull(ctx.getEffectiveIntentSource()));
        out.put("effectiveScopeSource", blankToNull(ctx.getEffectiveScopeSource()));
        AiQuerySemanticParseResult qsp = ctx.getQuerySemanticParse();
        out.put("querySemanticLlm", summarizeQuerySemanticParse(qsp));
        out.put("needSemanticClarification", ctx.isNeedSemanticClarification());
        out.put("semanticClarificationQuestion", blankToNull(ctx.getSemanticClarificationQuestion()));
        if (qsp != null && !qsp.isParseMissing()) {
            List<String> eff = qsp.effectiveMentionedStoreNames();
            out.put("querySemanticEffectiveMentionedStoreNames",
                    eff == null || eff.isEmpty() ? null : new ArrayList<>(eff));
        } else {
            out.put("querySemanticEffectiveMentionedStoreNames", null);
        }
        out.put("semanticPrimaryVersion", blankToNull(ctx.getSemanticPrimaryVersion()));
        out.put("semanticFallbackUsed", ctx.getSemanticFallbackUsed());
        out.put("semanticFallbackReason", blankToNull(ctx.getSemanticFallbackReason()));
        out.put("semanticAdoptedFrom", blankToNull(ctx.getSemanticAdoptedFrom()));
        List<String> adoptedFields = ctx.getSemanticAdoptedFields();
        out.put(
                "semanticAdoptedFields",
                adoptedFields == null || adoptedFields.isEmpty() ? null : new ArrayList<>(adoptedFields));
        List<String> rejectedFields = ctx.getSemanticAdoptionRejectedFields();
        out.put(
                "semanticAdoptionRejectedFields",
                rejectedFields == null || rejectedFields.isEmpty() ? null : new ArrayList<>(rejectedFields));
        out.put("semanticAdoptionRejectedReason", blankToNull(ctx.getSemanticAdoptionRejectedReason()));
        out.put("semanticMetricNormalizedFrom", blankToNull(ctx.getSemanticMetricNormalizedFrom()));
        out.put("semanticMetricNormalizedTo", blankToNull(ctx.getSemanticMetricNormalizedTo()));
        out.put(
                "semanticV2AbstractIntentNormalizationNotes",
                jsonDeepCopyMap(ctx.getSemanticV2AbstractIntentNormalizationNotes()));
        Map<String, Object> v2NormNotes = ctx.getSemanticV2AbstractIntentNormalizationNotes();
        if (v2NormNotes != null && v2NormNotes.containsKey("degradedBusinessCompareByRevenue")) {
            out.put("degradedBusinessCompareByRevenue", v2NormNotes.get("degradedBusinessCompareByRevenue"));
        }
        out.put("querySemanticV1", jsonDeepCopyMap(ctx.getQuerySemanticV1()));
        out.put("querySemanticV2InputPreview", jsonDeepCopyMap(ctx.getQuerySemanticV2InputPreview()));
        out.put("querySemanticV2", jsonDeepCopyMap(ctx.getQuerySemanticV2()));
        out.put("querySemanticV2ParseMissing", ctx.getQuerySemanticV2ParseMissing());
        out.put("querySemanticV2Confidence", ctx.getQuerySemanticV2Confidence());
        out.put("querySemanticV2TimeAction", blankToNull(ctx.getQuerySemanticV2TimeAction()));
        out.put("querySemanticV2ScopeAction", blankToNull(ctx.getQuerySemanticV2ScopeAction()));
        out.put("querySemanticV2IntentAction", blankToNull(ctx.getQuerySemanticV2IntentAction()));
        out.put("querySemanticV2MetricAction", blankToNull(ctx.getQuerySemanticV2MetricAction()));
        List<String> v2stores = ctx.getQuerySemanticV2MentionedStoreNames();
        out.put(
                "querySemanticV2MentionedStoreNames",
                v2stores == null || v2stores.isEmpty() ? null : new ArrayList<>(v2stores));
        out.put("querySemanticV2MentionedDishName", blankToNull(ctx.getQuerySemanticV2MentionedDishName()));
        out.put("querySemanticV2RawText", blankToNull(ctx.getQuerySemanticV2RawText()));
        out.put("querySemanticV2ParseError", blankToNull(ctx.getQuerySemanticV2ParseError()));

        out.put("orchestrationTaskMode", blankToNull(ctx.getOrchestrationTaskMode()));
        out.put(
                "orchestrationSelectedAgents",
                ctx.getOrchestrationSelectedAgents() == null || ctx.getOrchestrationSelectedAgents().isEmpty()
                        ? null
                        : new ArrayList<>(ctx.getOrchestrationSelectedAgents()));
        out.put(
                "orchestrationSelectedTools",
                ctx.getOrchestrationSelectedTools() == null || ctx.getOrchestrationSelectedTools().isEmpty()
                        ? null
                        : new ArrayList<>(ctx.getOrchestrationSelectedTools()));
        out.put("orchestrationPlannerRequired", ctx.getOrchestrationPlannerRequired());
        out.put("orchestrationMultiAgentRequired", ctx.getOrchestrationMultiAgentRequired());
        out.put("orchestrationApprovalRequired", ctx.getOrchestrationApprovalRequired());
        out.put("orchestrationClarificationRequired", ctx.getOrchestrationClarificationRequired());
        out.put("orchestrationClarificationQuestion", blankToNull(ctx.getOrchestrationClarificationQuestion()));
        out.put("orchestrationConfidence", ctx.getOrchestrationConfidence());
        out.put("orchestrationReason", blankToNull(ctx.getOrchestrationReason()));

        out.put("multiStoreScopeDetected", ctx.isHarnessMultiStoreScopeDetected());
        out.put("multiStoreScopeApplied", ctx.isHarnessMultiStoreScopeApplied());
        out.put("multiStoreScopeSource", blankToNull(ctx.getHarnessMultiStoreScopeSource()));
        out.put(
                "multiStoreMatchedStores",
                ctx.getHarnessMultiStoreMatchedStores() == null
                        ? null
                        : new ArrayList<>(ctx.getHarnessMultiStoreMatchedStores()));
        out.put("singleStoreNarrowingBlocked", ctx.isHarnessSingleStoreNarrowingBlocked());
        AiResolvedTimeWindow tw = ctx.getTimeWindow();
        LinkedHashMap<String, Object> timeBlock = new LinkedHashMap<>();
        if (tw != null) {
            out.put("startDate", tw.getStartDate() != null ? tw.getStartDate().toString() : null);
            out.put("endDate", tw.getEndDate() != null ? tw.getEndDate().toString() : null);
            out.put("timeLabel", blankToNull(tw.getTimeLabel()));
            out.put("timeDisplayText", blankToNull(tw.getDisplayText()));
            out.put("timeInheritedFromPrevious", tw.isInheritedFromPreviousTurn());
            out.put("timeExplicitInMessage", harnessTimeExplicitForSummary(ctx, tw));
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
        out.put("scopeLabel", org != null ? blankToNull(org.getQueryScopeBanner()) : null);
        out.put("visibleStores", summarizeStores(org));

        AiSemanticStoreNarrowingDiagnostics narrowDiag = ctx.getSemanticStoreNarrowingDebug();
        if (narrowDiag != null) {
            out.put(
                    "semanticMentionedStoreNames",
                    narrowDiag.getSemanticMentionedStoreNames() == null
                            ? new ArrayList<String>()
                            : new ArrayList<>(narrowDiag.getSemanticMentionedStoreNames()));
            out.put(
                    "storeRootCandidates",
                    narrowDiag.getStoreRootCandidates() == null
                            ? new ArrayList<String>()
                            : new ArrayList<>(narrowDiag.getStoreRootCandidates()));
            out.put(
                    "visibleStoreCandidates",
                    narrowDiag.getVisibleStoreCandidates() == null
                            ? new ArrayList<String>()
                            : new ArrayList<>(narrowDiag.getVisibleStoreCandidates()));
            out.put("matchedStoreCandidate", blankToNull(narrowDiag.getMatchedStoreCandidate()));
            out.put("narrowingFailureReason", blankToNull(narrowDiag.getNarrowingFailureReason()));
        } else {
            out.put("semanticMentionedStoreNames", null);
            out.put("storeRootCandidates", null);
            out.put("visibleStoreCandidates", null);
            out.put("matchedStoreCandidate", null);
            out.put("narrowingFailureReason", null);
        }

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
        String sidWire = sidWireRaw;
        AiQuerySemanticParseResult sem = ctx.getQuerySemanticParse();
        if (sidWire == null && sem != null && sem.getMetric() != null) {
            String rt = sem.getMetric().getRankingType();
            if (StringUtils.hasText(rt)) {
                sidWire = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(rt);
            }
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
            String prevSid = prev.getLastStructuredIntentDetail();
            if (StringUtils.hasText(prevSid)
                    && AiQuerySemanticLexicon.isStructuredStockReduceDetail(prevSid)) {
                String prevCode = AiQuerySemanticLexicon.toStructuredIntentDetailDebugCode(prevSid);
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
        if (state == null) {
            AiHarnessReplayContextProbes.appendResolvedOnlyProbes(out, ctx);
        }
        appendExecutionHints(out, state);
        if (state == null && ctx.getDataScope() != null) {
            overlayReplayResolvedExecutionMirrorsFromDataScope(out, ctx.getDataScope());
        }
        return out;
    }

    /**
     * Replay 无 RunState 时对齐「执行后镜像」观测键：{@code resolved*} 本应来自 AiRunState#resolvedQueryContext.dataScope，
     * Resolver-only 回放用 {@link AiResolvedDataScope} 回填，便于 Harness subset 断言单店 SQL 与非空门禁。
     */
    private static void overlayReplayResolvedExecutionMirrorsFromDataScope(
            LinkedHashMap<String, Object> out, AiResolvedDataScope ds) {
        if (ds == null) {
            return;
        }
        List<Long> roots = longList(ds.getVisibleStoreRootIds());
        if (!roots.isEmpty()) {
            out.put("resolvedVisibleStoreRootIds", new ArrayList<>(roots));
        }
        List<Long> sqlExpanded = longList(ds.getEffectiveSqlDepartmentIds());
        if (!sqlExpanded.isEmpty()) {
            out.put("resolvedEffectiveSqlDepartmentIds", new ArrayList<>(sqlExpanded));
        }
        List<Long> dishSql =
                longList(ds.getSqlDepartmentIdsForDomain(AiResolvedDataScope.SQL_DOMAIN_DISH_PROFIT));
        out.put("resolvedDishProfitSqlDepartmentIds", new ArrayList<>(dishSql));
        Object hintObj = out.get("departmentIdSemanticsHint");
        if (!(hintObj instanceof String h && StringUtils.hasText(h.trim()))) {
            out.put(
                    "departmentIdSemanticsHint",
                    "门店展示=visibleStores/queryStoreIds；department_id IN=expandedSqlDepartmentIds；语义部门=queryRealDepartmentIds（仅 DEPARTMENT 口径）");
        }
    }

    private static void appendExecutionHints(LinkedHashMap<String, Object> out, AiRunState state) {
        if (state == null) {
            if (!out.containsKey("answerPreview")) {
                out.put("answerPreview", null);
            }
            if (!out.containsKey("consumedAnswerPlans")) {
                out.put("consumedAnswerPlans", null);
            }
            if (!out.containsKey("missingAnswerPlans")) {
                out.put("missingAnswerPlans", null);
            }
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
            out.put("dishProfitAnswerPlan", null);
            out.put("dishProfitAnswerPlanPresent", false);
            out.put("dishSalesAnswerPlan", null);
            out.put("dishSalesAnswerPlanPresent", false);
            out.put("purchaseAnswerPlan", null);
            out.put("purchaseAnswerPlanPresent", false);
            out.put("purchaseAnswerPlanType", null);
            out.put("purchaseAnswerPlanSortKey", null);
            out.put("purchaseAnswerPlanSortDirection", null);
            out.put("purchaseAnswerPlanFocusRows", null);
            out.put("purchaseAnswerPlanSecondaryRows", null);
            out.put("purchaseAnswerPlanDebug", null);
            out.put("stockReduceAnswerPlan", null);
            out.put("stockReduceAnswerPlanPresent", false);
            out.put("stockReduceAnswerPlanType", null);
            out.put("stockReduceAnswerPlanSortKey", null);
            out.put("stockReduceAnswerPlanSortDirection", null);
            out.put("stockReduceAnswerPlanFocusRows", null);
            out.put("stockReduceAnswerPlanSecondaryRows", null);
            out.put("stockReduceAnswerPlanDebug", null);
            out.put("revenueAnswerPlan", null);
            out.put("revenueAnswerPlanPresent", false);
            out.put("revenueAnswerPlanType", null);
            out.put("revenueAnswerPlanSortKey", null);
            out.put("revenueAnswerPlanSortDirection", null);
            out.put("revenueAnswerPlanFocusRows", null);
            out.put("revenueAnswerPlanSecondaryRows", null);
            out.put("revenueAnswerPlanDebug", null);
            out.put("planSource", null);
            out.put("dataPlanTools", null);
            out.put("usedTools", null);
            out.put("diagnosisPlan", null);
            out.put("diagnosisPlanPresent", false);
            out.put("diagnosisPlanType", null);
            out.put("businessDiagnosisPlan", null);
            out.put("businessDiagnosisPlanType", null);
            applyStorePriorityRankingSummaryFields(out, null);
            out.put("diagnosisRiskLevel", null);
            out.put("diagnosisDataCompleteness", null);
            out.put("businessStoreCompareEvidenceRowsLen", null);
            out.put("businessStoreCompareTop1StoreName", null);
            out.put("businessStoreCompareTop2StoreName", null);
            putMasterBusinessAgentDebugDefaults(out);
            putHarnessReplayGraphRunStateProbeDefaults(out);
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
        out.put("dataPlanTools", tools == null || tools.isEmpty() ? null : new ArrayList<>(tools));
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
        if (state.getDishProfitAnswerPlan() != null) {
            try {
                out.put("dishProfitAnswerPlan", JSON.parseObject(JSON.toJSONString(state.getDishProfitAnswerPlan())));
                out.put("dishProfitAnswerPlanPresent", true);
            } catch (Exception ex) {
                out.put("dishProfitAnswerPlan", null);
                out.put("dishProfitAnswerPlanWarning", "serialize_failed");
                out.put("dishProfitAnswerPlanPresent", false);
            }
        } else {
            out.put("dishProfitAnswerPlan", null);
            out.put("dishProfitAnswerPlanPresent", false);
        }
        if (state.getDishSalesAnswerPlan() != null) {
            try {
                out.put("dishSalesAnswerPlan", JSON.parseObject(JSON.toJSONString(state.getDishSalesAnswerPlan())));
                out.put("dishSalesAnswerPlanPresent", true);
            } catch (Exception ex) {
                out.put("dishSalesAnswerPlan", null);
                out.put("dishSalesAnswerPlanWarning", "serialize_failed");
                out.put("dishSalesAnswerPlanPresent", false);
            }
        } else {
            out.put("dishSalesAnswerPlan", null);
            out.put("dishSalesAnswerPlanPresent", false);
        }
        PurchaseAnswerPlan pap = state.getPurchaseAnswerPlan();
        if (pap != null) {
            try {
                out.put("purchaseAnswerPlan", JSON.parseObject(JSON.toJSONString(pap)));
                out.put("purchaseAnswerPlanPresent", true);
                out.put("purchaseAnswerPlanType", pap.getPlanType());
                Map<String, Object> dbg = pap.getDebug();
                if (dbg != null && !dbg.isEmpty()) {
                    out.put("purchaseAnswerPlanSortKey", dbg.get("sortKey"));
                    out.put("purchaseAnswerPlanSortDirection", dbg.get("sortDirection"));
                    out.put("purchaseAnswerPlanDebug", new LinkedHashMap<>(dbg));
                } else {
                    out.put("purchaseAnswerPlanSortKey", null);
                    out.put("purchaseAnswerPlanSortDirection", null);
                    out.put("purchaseAnswerPlanDebug", null);
                }
                out.put("purchaseAnswerPlanFocusRows",
                        pap.getFocusRows() == null ? null : new ArrayList<>(pap.getFocusRows()));
                out.put("purchaseAnswerPlanSecondaryRows",
                        pap.getSecondaryRows() == null ? null : new ArrayList<>(pap.getSecondaryRows()));
            } catch (Exception ex) {
                out.put("purchaseAnswerPlan", null);
                out.put("purchaseAnswerPlanWarning", "serialize_failed");
                out.put("purchaseAnswerPlanPresent", false);
                out.put("purchaseAnswerPlanType", null);
                out.put("purchaseAnswerPlanSortKey", null);
                out.put("purchaseAnswerPlanSortDirection", null);
                out.put("purchaseAnswerPlanFocusRows", null);
                out.put("purchaseAnswerPlanSecondaryRows", null);
                out.put("purchaseAnswerPlanDebug", null);
            }
        } else {
            out.put("purchaseAnswerPlan", null);
            out.put("purchaseAnswerPlanPresent", false);
            out.put("purchaseAnswerPlanType", null);
            out.put("purchaseAnswerPlanSortKey", null);
            out.put("purchaseAnswerPlanSortDirection", null);
            out.put("purchaseAnswerPlanFocusRows", null);
            out.put("purchaseAnswerPlanSecondaryRows", null);
            out.put("purchaseAnswerPlanDebug", null);
        }
        StockReduceAnswerPlan srap = state.getStockReduceAnswerPlan();
        if (srap != null) {
            try {
                out.put("stockReduceAnswerPlan", JSON.parseObject(JSON.toJSONString(srap)));
                out.put("stockReduceAnswerPlanPresent", true);
                out.put("stockReduceAnswerPlanType", srap.getPlanType());
                Map<String, Object> sdbg = srap.getDebug();
                if (sdbg != null && !sdbg.isEmpty()) {
                    out.put("stockReduceAnswerPlanSortKey", sdbg.get("sortKey"));
                    out.put("stockReduceAnswerPlanSortDirection", sdbg.get("sortDirection"));
                    out.put("stockReduceAnswerPlanDebug", new LinkedHashMap<>(sdbg));
                } else {
                    out.put("stockReduceAnswerPlanSortKey", null);
                    out.put("stockReduceAnswerPlanSortDirection", null);
                    out.put("stockReduceAnswerPlanDebug", null);
                }
                out.put("stockReduceAnswerPlanFocusRows",
                        srap.getFocusRows() == null ? null : new ArrayList<>(srap.getFocusRows()));
                out.put("stockReduceAnswerPlanSecondaryRows",
                        srap.getSecondaryRows() == null ? null : new ArrayList<>(srap.getSecondaryRows()));
            } catch (Exception ex) {
                out.put("stockReduceAnswerPlan", null);
                out.put("stockReduceAnswerPlanWarning", "serialize_failed");
                out.put("stockReduceAnswerPlanPresent", false);
                out.put("stockReduceAnswerPlanType", null);
                out.put("stockReduceAnswerPlanSortKey", null);
                out.put("stockReduceAnswerPlanSortDirection", null);
                out.put("stockReduceAnswerPlanFocusRows", null);
                out.put("stockReduceAnswerPlanSecondaryRows", null);
                out.put("stockReduceAnswerPlanDebug", null);
            }
        } else {
            out.put("stockReduceAnswerPlan", null);
            out.put("stockReduceAnswerPlanPresent", false);
            out.put("stockReduceAnswerPlanType", null);
            out.put("stockReduceAnswerPlanSortKey", null);
            out.put("stockReduceAnswerPlanSortDirection", null);
            out.put("stockReduceAnswerPlanFocusRows", null);
            out.put("stockReduceAnswerPlanSecondaryRows", null);
            out.put("stockReduceAnswerPlanDebug", null);
        }
        DailyRevenueAnswerPlan rap = state.getRevenueAnswerPlan();
        if (rap != null) {
            try {
                out.put("revenueAnswerPlan", JSON.parseObject(JSON.toJSONString(rap)));
                out.put("revenueAnswerPlanPresent", true);
                out.put("revenueAnswerPlanType", rap.getPlanType());
                Map<String, Object> rdbg = rap.getDebug();
                if (rdbg != null && !rdbg.isEmpty()) {
                    out.put("revenueAnswerPlanSortKey", rdbg.get("sortKey"));
                    out.put("revenueAnswerPlanSortDirection", rdbg.get("sortDirection"));
                    out.put("revenueAnswerPlanDebug", new LinkedHashMap<>(rdbg));
                } else {
                    out.put("revenueAnswerPlanSortKey", null);
                    out.put("revenueAnswerPlanSortDirection", null);
                    out.put("revenueAnswerPlanDebug", null);
                }
                out.put("revenueAnswerPlanFocusRows",
                        rap.getFocusRows() == null ? null : new ArrayList<>(rap.getFocusRows()));
                out.put("revenueAnswerPlanSecondaryRows",
                        rap.getSecondaryRows() == null ? null : new ArrayList<>(rap.getSecondaryRows()));
            } catch (Exception ex) {
                out.put("revenueAnswerPlan", null);
                out.put("revenueAnswerPlanWarning", "serialize_failed");
                out.put("revenueAnswerPlanPresent", false);
                out.put("revenueAnswerPlanType", null);
                out.put("revenueAnswerPlanSortKey", null);
                out.put("revenueAnswerPlanSortDirection", null);
                out.put("revenueAnswerPlanFocusRows", null);
                out.put("revenueAnswerPlanSecondaryRows", null);
                out.put("revenueAnswerPlanDebug", null);
            }
        } else {
            out.put("revenueAnswerPlan", null);
            out.put("revenueAnswerPlanPresent", false);
            out.put("revenueAnswerPlanType", null);
            out.put("revenueAnswerPlanSortKey", null);
            out.put("revenueAnswerPlanSortDirection", null);
            out.put("revenueAnswerPlanFocusRows", null);
            out.put("revenueAnswerPlanSecondaryRows", null);
            out.put("revenueAnswerPlanDebug", null);
        }
        List<String> allPlanned = state.getDataPlanTools();
        out.put("usedTools", resolveHarnessUsedTools(state, rqExe, allPlanned));
        if (state.getDiagnosisPlan() != null) {
            try {
                out.put("diagnosisPlan", JSON.parseObject(JSON.toJSONString(state.getDiagnosisPlan())));
                out.put("diagnosisPlanPresent", true);
                out.put("diagnosisPlanType", state.getDiagnosisPlan().getPlanType());
            } catch (Exception ex) {
                out.put("diagnosisPlan", null);
                out.put("diagnosisPlanWarning", "serialize_failed");
                out.put("diagnosisPlanPresent", false);
                out.put("diagnosisPlanType", null);
            }
        } else {
            out.put("diagnosisPlan", null);
            out.put("diagnosisPlanPresent", false);
            out.put("diagnosisPlanType", null);
        }
        applyBusinessStoreCompareEvidenceHarnessSummaryFields(out, state);
        if (state.getBusinessDiagnosisPlan() != null) {
            try {
                BusinessDiagnosisPlan bdp = state.getBusinessDiagnosisPlan();
                out.put("businessDiagnosisPlan", JSON.parseObject(JSON.toJSONString(bdp)));
                out.put("businessDiagnosisPlanType", blankToNull(bdp.getPlanType()));
                applyStorePriorityRankingSummaryFields(out, bdp.getStorePriorityRanking());
                out.put("diagnosisRiskLevel", bdp.getRiskLevel());
                out.put("diagnosisDataCompleteness", bdp.getDataCompleteness());
            } catch (Exception ex) {
                out.put("businessDiagnosisPlan", null);
                out.put("businessDiagnosisPlanWarning", "serialize_failed");
                out.put("businessDiagnosisPlanType", null);
                applyStorePriorityRankingSummaryFields(out, null);
                out.put("diagnosisRiskLevel", null);
                out.put("diagnosisDataCompleteness", null);
            }
        } else {
            out.put("businessDiagnosisPlan", null);
            out.put("businessDiagnosisPlanType", null);
            applyStorePriorityRankingSummaryFields(out, null);
            out.put("diagnosisRiskLevel", null);
            out.put("diagnosisDataCompleteness", null);
        }

        String pathEff = rqExe != null ? blankToNull(rqExe.getEffectivePathCode()) : null;
        String planSourceResolved = null;
        if (state.getDiagnosisPlan() != null) {
            planSourceResolved = "diagnosisPlan";
        } else if (AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW.equals(pathEff)) {
            planSourceResolved = "revenueAnswerPlan";
        } else if (AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY.equals(pathEff)) {
            planSourceResolved = "stockReduceAnswerPlan";
        } else if (AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW.equals(pathEff)) {
            planSourceResolved = "purchaseAnswerPlan";
        } else if (AiResolvedQueryIntent.PATH_DISH_SALES_QUERY.equals(pathEff)) {
            planSourceResolved = AiHarnessReplayContextProbes.HARNESS_PLAN_SOURCE_DISH_SALES_REUSES_DISH_PROFIT_TOOL;
        } else if (AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(pathEff)) {
            planSourceResolved = "dishProfitAnswerPlan";
        }
        out.put("planSource", planSourceResolved);
        mergeMasterBusinessAgentDebug(out, state);
        mergeCompositeProductionGateHarnessFields(out, state);
        mergeCompositeHarnessExecutionFields(out, state);
        appendAnswerPreviewAndDiagnosisPlanWireFields(out, state);
        mirrorHarnessReplayProbesPresenceFromAnswerPlans(out, state);
        appendHarnessReplayGraphRunStateProbes(out, state);
    }

    /**
     * GRAPH_RUN / Replay：Planner→Composer 之后的 RunState 扁平探针（只读镜像，不参与路由）。
     */
    private static void putHarnessReplayGraphRunStateProbeDefaults(LinkedHashMap<String, Object> out) {
        out.put("needSemanticClarification", null);
        out.put("needClarification", null);
        out.put("clarificationQuestion", null);
        out.put("businessDiagnosisPath", null);
        out.put("businessOverviewPath", null);
        out.put("revenueOverviewPath", null);
        out.put("purchaseOverviewPath", null);
        out.put("stockReduceQueryPath", null);
        out.put("warehouseStockOverviewPath", null);
        out.put("groupWarehouseStockOverview", null);
        out.put("permissionDenials", null);
        out.put("finalAnswerTextBlank", null);
        out.put("couponCostInsightBlocked", null);
        out.put("diagnosisPlanExists", null);
        out.put("businessDiagnosisPlanExists", null);
    }

    private static void appendHarnessReplayGraphRunStateProbes(LinkedHashMap<String, Object> out, AiRunState state) {
        if (state == null) {
            putHarnessReplayGraphRunStateProbeDefaults(out);
            return;
        }
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        out.put("needSemanticClarification",
                rq != null ? Boolean.valueOf(rq.isNeedSemanticClarification()) : null);
        out.put("needClarification", Boolean.valueOf(state.isNeedClarification()));
        out.put("clarificationQuestion", blankToNull(state.getClarificationQuestion()));
        out.put("businessDiagnosisPath", Boolean.valueOf(state.isBusinessDiagnosisPath()));
        out.put("businessOverviewPath", Boolean.valueOf(state.isBusinessOverviewPath()));
        out.put("revenueOverviewPath", Boolean.valueOf(state.isRevenueOverviewPath()));
        out.put("purchaseOverviewPath", Boolean.valueOf(state.isPurchaseOverviewPath()));
        out.put("stockReduceQueryPath", Boolean.valueOf(state.isStockReduceQueryPath()));
        out.put("warehouseStockOverviewPath", Boolean.valueOf(state.isWarehouseStockOverviewPath()));
        out.put("groupWarehouseStockOverview", Boolean.valueOf(state.isGroupWarehouseStockOverview()));

        List<AiPermissionDenied> denials = state.getPermissionDenials();
        if (denials == null || denials.isEmpty()) {
            out.put("permissionDenials", null);
        } else {
            List<Map<String, Object>> rows = new ArrayList<>(denials.size());
            for (AiPermissionDenied d : denials) {
                if (d != null) {
                    rows.add(d.asDataMap());
                }
            }
            out.put("permissionDenials", rows.isEmpty() ? null : rows);
        }

        String fat = state.getFinalAnswerText();
        out.put("finalAnswerTextBlank", Boolean.valueOf(!StringUtils.hasText(fat)));
        out.put("couponCostInsightBlocked", Boolean.valueOf(state.isCouponCostInsightBlocked()));
        out.put("diagnosisPlanExists", Boolean.valueOf(state.getDiagnosisPlan() != null));
        out.put("businessDiagnosisPlanExists", Boolean.valueOf(state.getBusinessDiagnosisPlan() != null));
    }

    private static final int ANSWER_PREVIEW_MAX_LEN = 500;

    /**
     * Graph 完成后：{@code finalAnswerText} 为 Composer **全文**（Harness / Replay 探针与用户终稿对齐）；{@code answerPreview}
     * 仅保留前 {@link #ANSWER_PREVIEW_MAX_LEN} 字作为历史「简略预览」；及对账 {@link DiagnosisPlan#getDebug()} 列表。
     */
    private static void appendAnswerPreviewAndDiagnosisPlanWireFields(LinkedHashMap<String, Object> out, AiRunState state) {
        String fat = state.getFinalAnswerText();
        if (!StringUtils.hasText(fat)) {
            out.put("finalAnswerText", null);
            out.put("answerPreview", null);
        } else {
            out.put("finalAnswerText", fat);
            out.put("answerPreview", fat.substring(0, Math.min(ANSWER_PREVIEW_MAX_LEN, fat.length())));
        }
        DiagnosisPlan dp = state.getDiagnosisPlan();
        if (dp == null || dp.getDebug() == null || dp.getDebug().isEmpty()) {
            synthesizeConsumedAnswerPlansFromWireAnswerPlans(out, state);
            return;
        }
        List<String> consumed = stringListFromDebugList(dp.getDebug().get("consumedAnswerPlans"));
        List<String> missing = stringListFromDebugList(dp.getDebug().get("missingAnswerPlans"));
        out.put("consumedAnswerPlans", consumed == null || consumed.isEmpty() ? null : consumed);
        out.put("missingAnswerPlans", missing == null || missing.isEmpty() ? null : missing);
    }

    /**
     * 无 {@link DiagnosisPlan#debug} 时（单域专线），按 RunState 上已落地的 AnswerPlan 镜像
     * {@code consumedAnswerPlans}，与 MultiAgent/Diagnosis 摘要键对齐，供 GRAPH_RUN Harness 子集断言。
     */
    private static void synthesizeConsumedAnswerPlansFromWireAnswerPlans(
            LinkedHashMap<String, Object> out, AiRunState state) {
        if (state == null) {
            out.put("consumedAnswerPlans", null);
            out.put("missingAnswerPlans", null);
            return;
        }
        List<String> acc = new ArrayList<>();
        if (state.getRevenueAnswerPlan() != null) {
            acc.add("DailyRevenueAnswerPlan");
        }
        if (state.getPurchaseAnswerPlan() != null) {
            acc.add("PurchaseAnswerPlan");
        }
        if (state.getStockReduceAnswerPlan() != null) {
            acc.add("StockReduceAnswerPlan");
        }
        boolean dishSalesPrimary = isDishSalesPrimaryHarnessPath(state);
        if (dishSalesPrimary && state.getDishSalesAnswerPlan() != null) {
            acc.add("DishSalesAnswerPlan");
        }
        if (state.getDishProfitAnswerPlan() != null) {
            acc.add("DishProfitAnswerPlan");
        }
        if (!dishSalesPrimary && state.getDishSalesAnswerPlan() != null) {
            acc.add("DishSalesAnswerPlan");
        }
        if (acc.isEmpty()) {
            out.put("consumedAnswerPlans", null);
            out.put("missingAnswerPlans", null);
        } else {
            out.put("consumedAnswerPlans", acc);
            out.put("missingAnswerPlans", new ArrayList<String>());
        }
    }

    private static boolean isDishSalesPrimaryHarnessPath(AiRunState state) {
        if (state == null || state.getResolvedQueryContext() == null) {
            return false;
        }
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        String effIntent =
                rq.getEffectiveIntentCode() == null ? null : rq.getEffectiveIntentCode().trim();
        String effPath = rq.getEffectivePathCode() == null ? null : rq.getEffectivePathCode().trim();
        return AiResolvedQueryIntent.DISH_SALES_QUERY.equals(effIntent)
                || AiResolvedQueryIntent.PATH_DISH_SALES_QUERY.equals(effPath);
    }

    private static List<String> stringListFromDebugList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return null;
        }
        List<String> acc = new ArrayList<>();
        for (Object x : list) {
            if (x == null) {
                continue;
            }
            String s = x.toString().trim();
            if (StringUtils.hasText(s)) {
                acc.add(s);
            }
        }
        return acc.isEmpty() ? null : acc;
    }

    private static void putMasterBusinessAgentDebugDefaults(LinkedHashMap<String, Object> out) {
        out.put("businessOverviewMultiMaster", null);
        out.put("masterAgentEnabled", null);
        out.put("masterAgentUsed", null);
        out.put("selectedAgents", null);
        out.put("dispatchPlan", null);
        out.put("agentResults", null);
        out.put("agentResultStatus", null);
        out.put("degraded", null);
        out.put("failurePolicy", null);
        out.put("fallbackUsed", null);
        out.put("fallbackReason", null);
        out.put("legacyRevenueSkipped", null);
        out.put("masterRevenueToolResultKey", null);
        out.put("masterRevenueToolResultSuccess", null);
        out.put("revenueToolExecutedByMasterPath", null);
        out.put("purchaseMasterAgentEnabled", null);
        out.put("purchaseMasterAgentUsed", null);
        out.put("purchaseSelectedAgents", null);
        out.put("purchaseDispatchPlan", null);
        out.put("purchaseAgentResults", null);
        out.put("purchaseAgentResultStatus", null);
        out.put("purchaseDegraded", null);
        out.put("purchaseFailurePolicy", null);
        out.put("purchaseFallbackUsed", null);
        out.put("purchaseFallbackReason", null);
        out.put("legacyPurchaseSkipped", null);
        out.put("masterPurchaseToolResultKey", null);
        out.put("masterPurchaseToolResultSuccess", null);
        out.put("purchaseToolExecutedByMasterPath", null);
        out.put("purchaseRequestResolutionDebug", null);
        out.put("stockReduceMasterAgentEnabled", null);
        out.put("stockReduceMasterAgentUsed", null);
        out.put("stockReduceSelectedAgents", null);
        out.put("stockReduceDispatchPlan", null);
        out.put("stockReduceAgentResults", null);
        out.put("stockReduceAgentResultStatus", null);
        out.put("stockReduceDegraded", null);
        out.put("stockReduceFailurePolicy", null);
        out.put("stockReduceFallbackUsed", null);
        out.put("stockReduceFallbackReason", null);
        out.put("legacyStockReduceSkipped", null);
        out.put("masterStockReduceToolResultKey", null);
        out.put("masterStockReduceToolResultSuccess", null);
        out.put("stockReduceToolExecutedByMasterPath", null);
        out.put("stockReduceRequestResolutionDebug", null);
        out.put("dishProfitMasterAgentEnabled", null);
        out.put("dishProfitMasterAgentUsed", null);
        out.put("dishProfitSelectedAgents", null);
        out.put("dishProfitDispatchPlan", null);
        out.put("dishProfitAgentResults", null);
        out.put("dishProfitAgentResultStatus", null);
        out.put("dishProfitFallbackUsed", null);
        out.put("dishProfitFallbackReason", null);
        out.put("legacyDishProfitSkipped", null);
        out.put("masterDishProfitToolResultKey", null);
        out.put("masterDishProfitToolResultSuccess", null);
        out.put("dishProfitToolExecutedByMasterPath", null);
        out.put("dishProfitRequestResolutionDebug", null);
        out.put("compositeGateAllowed", null);
        out.put("compositeGateReasonCode", null);
        out.put("compositeGateReason", null);
        out.put("compositeGateScopeType", null);
        out.put("compositeGateRecommendedCaseKind", null);
        out.put("compositeGateFinalAnswerPlanType", null);
        out.put("compositeGateDebug", null);
        out.put("compositeGateProductionEnabledSource", null);
        out.put("compositeGateProductionEnabledEffective", null);
        putCompositeHarnessExecutionFieldDefaults(out);
    }

    private static void putCompositeHarnessExecutionFieldDefaults(LinkedHashMap<String, Object> out) {
        out.put("compositeExecutionMode", null);
        out.put("compositeExecuted", false);
        out.put("compositeExecutionSuccess", false);
        out.put("compositeFallbackRequired", false);
        out.put("compositeFallbackReason", null);
        out.put("compositePlannerOverallStatus", null);
        out.put("compositePlannerDegradedSteps", null);
        out.put("compositeFinalAnswerText", null);
        out.put("compositeComposerVersion", null);
        out.put("compositeAnswerPlanType", null);
        out.put("compositeExecutionErrorCode", null);
        out.put("compositeExecutionErrorMessage", null);
        out.put("compositeShadowLatencyMs", null);
        out.put("compositeShadowComparedWithLegacy", null);
        out.put("compositeShadowLegacyAnswerPresent", null);
        out.put("compositeShadowCompositeAnswerPresent", null);
        out.put("compositeShadowFinalAnswerReplaced", null);
        out.put("compositeShadowSkipped", null);
        out.put("compositeShadowSkipReason", null);
        out.put("compositeShadowThrottleHit", null);
        out.put("compositeShadowWhitelistMatched", null);
    }

    private static void mergeMasterBusinessAgentDebug(LinkedHashMap<String, Object> out, AiRunState state) {
        Map<String, Object> md = state != null ? state.getMasterBusinessAgentDebug() : null;
        String[] revenueKeys = {
                "masterAgentEnabled",
                "masterAgentUsed",
                "selectedAgents",
                "dispatchPlan",
                "agentResults",
                "agentResultStatus",
                "degraded",
                "failurePolicy",
                "fallbackUsed",
                "fallbackReason",
                "legacyRevenueSkipped",
                "masterRevenueToolResultKey",
                "masterRevenueToolResultSuccess",
                "revenueToolExecutedByMasterPath",
        };
        String[] purchaseKeys = {
                "purchaseMasterAgentEnabled",
                "purchaseMasterAgentUsed",
                "purchaseSelectedAgents",
                "purchaseDispatchPlan",
                "purchaseAgentResults",
                "purchaseAgentResultStatus",
                "purchaseDegraded",
                "purchaseFailurePolicy",
                "purchaseFallbackUsed",
                "purchaseFallbackReason",
                "legacyPurchaseSkipped",
                "masterPurchaseToolResultKey",
                "masterPurchaseToolResultSuccess",
                "purchaseToolExecutedByMasterPath",
                "purchaseRequestResolutionDebug",
        };
        String[] stockReduceKeys = {
                "stockReduceMasterAgentEnabled",
                "stockReduceMasterAgentUsed",
                "stockReduceSelectedAgents",
                "stockReduceDispatchPlan",
                "stockReduceAgentResults",
                "stockReduceAgentResultStatus",
                "stockReduceDegraded",
                "stockReduceFailurePolicy",
                "stockReduceFallbackUsed",
                "stockReduceFallbackReason",
                "legacyStockReduceSkipped",
                "masterStockReduceToolResultKey",
                "masterStockReduceToolResultSuccess",
                "stockReduceToolExecutedByMasterPath",
                "stockReduceRequestResolutionDebug",
        };
        String[] dishProfitKeys = {
                "dishProfitMasterAgentEnabled",
                "dishProfitMasterAgentUsed",
                "dishProfitSelectedAgents",
                "dishProfitDispatchPlan",
                "dishProfitAgentResults",
                "dishProfitAgentResultStatus",
                "dishProfitFallbackUsed",
                "dishProfitFallbackReason",
                "legacyDishProfitSkipped",
                "masterDishProfitToolResultKey",
                "masterDishProfitToolResultSuccess",
                "dishProfitToolExecutedByMasterPath",
                "dishProfitRequestResolutionDebug",
        };
        if (md == null || md.isEmpty()) {
            out.put("businessOverviewMultiMaster", null);
            for (String k : revenueKeys) {
                out.put(k, null);
            }
            for (String k : purchaseKeys) {
                out.put(k, null);
            }
            for (String k : stockReduceKeys) {
                out.put(k, null);
            }
            for (String k : dishProfitKeys) {
                out.put(k, null);
            }
            for (String k : BUSINESS_OVERVIEW_MULTI_ORCHESTRATION_FLAT_FALLBACK_KEYS) {
                out.put(k, null);
            }
            return;
        }
        Object boNest = md.get("businessOverviewMultiMaster");
        if (boNest instanceof Map<?, ?> && !((Map<?, ?>) boNest).isEmpty()) {
            out.put("businessOverviewMultiMaster",
                    JSON.parseObject(JSON.toJSONString(boNest), Map.class));
        } else {
            out.put("businessOverviewMultiMaster", null);
        }
        for (String k : revenueKeys) {
            out.put(k, md.get(k));
        }
        for (String k : purchaseKeys) {
            out.put(k, md.get(k));
        }
        for (String k : stockReduceKeys) {
            out.put(k, md.get(k));
        }
        for (String k : dishProfitKeys) {
            out.put(k, md.get(k));
        }
        fillFlatBusinessOverviewOrchestrationFieldsFromNestedMultiMaster(out);
    }

    /**
     * C-55：主链路观测用 — 将 {@link AiRunState#getBusinessDiagnosisCompositeGateResult()} 摊入 Harness / GET-run 摘要；不改变路由。
     */
    private static void mergeCompositeProductionGateHarnessFields(LinkedHashMap<String, Object> out, AiRunState state) {
        BusinessDiagnosisCompositeGateResult gr =
                state != null ? state.getBusinessDiagnosisCompositeGateResult() : null;
        if (gr == null) {
            out.put("compositeGateAllowed", null);
            out.put("compositeGateReasonCode", null);
            out.put("compositeGateReason", null);
            out.put("compositeGateScopeType", null);
            out.put("compositeGateRecommendedCaseKind", null);
            out.put("compositeGateFinalAnswerPlanType", null);
            out.put("compositeGateDebug", null);
            out.put("compositeGateProductionEnabledSource", null);
            out.put("compositeGateProductionEnabledEffective", null);
            return;
        }
        out.put("compositeGateAllowed", gr.isAllowed());
        out.put("compositeGateReasonCode", gr.getReasonCode() != null ? gr.getReasonCode().name() : null);
        out.put("compositeGateReason", blankToNull(gr.getReason()));
        out.put("compositeGateScopeType", blankToNull(gr.getScopeType()));
        BusinessDiagnosisCompositeGateResult.RecommendedCaseKind rk = gr.getRecommendedCaseKind();
        out.put("compositeGateRecommendedCaseKind", rk != null ? rk.name() : null);
        out.put("compositeGateFinalAnswerPlanType", blankToNull(gr.getFinalAnswerPlanType()));
        Map<String, Object> dbg = gr.getDebug();
        if (dbg == null || dbg.isEmpty()) {
            out.put("compositeGateDebug", null);
            out.put("compositeGateProductionEnabledSource", null);
            out.put("compositeGateProductionEnabledEffective", null);
        } else {
            out.put("compositeGateDebug", JSON.parseObject(JSON.toJSONString(dbg), Map.class));
            Object psrc = dbg.get("productionEnabledSource");
            out.put("compositeGateProductionEnabledSource", psrc != null ? psrc.toString() : null);
            Object peff = dbg.get("productionEnabledEffective");
            out.put(
                    "compositeGateProductionEnabledEffective",
                    peff instanceof Boolean ? peff : null);
        }
    }

    /** C-58：{@link AiRunState#getBusinessDiagnosisCompositeExecutionResult()} 摊平到 Harness / GET-run 摘要。 */
    private static void mergeCompositeHarnessExecutionFields(LinkedHashMap<String, Object> out, AiRunState state) {
        BusinessDiagnosisCompositeExecutionResult ex =
                state != null ? state.getBusinessDiagnosisCompositeExecutionResult() : null;
        if (ex == null) {
            putCompositeHarnessExecutionFieldDefaults(out);
            return;
        }
        BusinessDiagnosisCompositeExecutionMode m = ex.getMode();
        out.put("compositeExecutionMode", m != null ? m.name() : null);
        out.put("compositeExecuted", ex.isExecuted());
        out.put("compositeExecutionSuccess", ex.isSuccess());
        out.put("compositeFallbackRequired", ex.isFallbackRequired());
        out.put("compositeFallbackReason", blankToNull(ex.getFallbackReason()));
        var overall = ex.getPlannerOverallStatus();
        out.put("compositePlannerOverallStatus", overall != null ? overall.name() : null);
        List<String> ds = ex.getDegradedSteps();
        out.put(
                "compositePlannerDegradedSteps",
                ds == null || ds.isEmpty() ? null : new ArrayList<>(ds));
        BusinessDiagnosisCompositeComposeResult cr = ex.getComposeResult();
        String fat = cr != null ? cr.getFinalAnswerText() : null;
        out.put("compositeFinalAnswerText", blankToNull(fat));

        String cv = BusinessDiagnosisCompositeReadonlyComposer.COMPOSER_VERSION;
        if (cr != null && cr.getDebug() != null) {
            Object v = cr.getDebug().get("composerVersion");
            if (v != null && StringUtils.hasText(v.toString())) {
                cv = v.toString().trim();
            }
        }
        out.put("compositeComposerVersion", cv);

        String apt = null;
        if (cr != null) {
            apt = blankToNull(cr.getAnswerPlanType());
        }
        if (apt == null && ex.getBusinessDiagnosisCompositeAnswerPlan() != null) {
            apt = blankToNull(ex.getBusinessDiagnosisCompositeAnswerPlan().getType());
        }
        out.put("compositeAnswerPlanType", apt);
        out.put("compositeExecutionErrorCode", blankToNull(ex.getErrorCode()));
        out.put("compositeExecutionErrorMessage", blankToNull(ex.getErrorMessage()));
        mergeCompositeShadowObservationFields(out, m, ex);
    }

    /** C-61：SHADOW 旁路耗时/对比观测；C-63：SKIP 闸门观测；其他模式键清空。 */
    private static void mergeCompositeShadowObservationFields(
            LinkedHashMap<String, Object> out,
            BusinessDiagnosisCompositeExecutionMode mode,
            BusinessDiagnosisCompositeExecutionResult ex) {
        if (mode != BusinessDiagnosisCompositeExecutionMode.SHADOW || ex == null) {
            shadowObservationNullDefaults(out);
            return;
        }
        if (Boolean.TRUE.equals(ex.getCompositeShadowSkipped())) {
            shadowObservationNullLatencyDefaults(out);
            out.put("compositeShadowSkipped", Boolean.TRUE);
            out.put("compositeShadowSkipReason", blankToNull(ex.getCompositeShadowSkipReason()));
            out.put(
                    "compositeShadowThrottleHit",
                    ex.getCompositeShadowThrottleHit() != null
                            ? ex.getCompositeShadowThrottleHit()
                            : Boolean.FALSE);
            out.put(
                    "compositeShadowWhitelistMatched", ex.getCompositeShadowWhitelistMatched());
            return;
        }

        shadowObservationSkippedDefaults(out);

        if (!ex.isExecuted()) {
            shadowObservationNullLatencyDefaults(out);
            return;
        }
        out.put("compositeShadowLatencyMs", ex.getCompositeShadowLatencyMs());
        out.put("compositeShadowComparedWithLegacy", ex.getCompositeShadowComparedWithLegacy());
        out.put("compositeShadowLegacyAnswerPresent", ex.getCompositeShadowLegacyAnswerPresent());
        out.put("compositeShadowCompositeAnswerPresent", ex.getCompositeShadowCompositeAnswerPresent());
        Boolean replaced = ex.getCompositeShadowFinalAnswerReplaced();
        out.put("compositeShadowFinalAnswerReplaced", replaced != null ? replaced : Boolean.FALSE);
        out.put("compositeShadowWhitelistMatched", ex.getCompositeShadowWhitelistMatched());
    }

    private static void shadowObservationNullDefaults(LinkedHashMap<String, Object> out) {
        out.put("compositeShadowLatencyMs", null);
        out.put("compositeShadowComparedWithLegacy", null);
        out.put("compositeShadowLegacyAnswerPresent", null);
        out.put("compositeShadowCompositeAnswerPresent", null);
        out.put("compositeShadowFinalAnswerReplaced", null);
        out.put("compositeShadowSkipped", null);
        out.put("compositeShadowSkipReason", null);
        out.put("compositeShadowThrottleHit", null);
        out.put("compositeShadowWhitelistMatched", null);
    }

    private static void shadowObservationSkippedDefaults(LinkedHashMap<String, Object> out) {
        out.put("compositeShadowSkipped", Boolean.FALSE);
        out.put("compositeShadowSkipReason", null);
        out.put("compositeShadowThrottleHit", Boolean.FALSE);
    }

    private static void shadowObservationNullLatencyDefaults(LinkedHashMap<String, Object> out) {
        out.put("compositeShadowLatencyMs", null);
        out.put("compositeShadowComparedWithLegacy", null);
        out.put("compositeShadowLegacyAnswerPresent", null);
        out.put("compositeShadowCompositeAnswerPresent", null);
        out.put("compositeShadowFinalAnswerReplaced", null);
    }

    /**
     * C-60：普通 Run / SSE — 摊平 Composite Gate（C-55）与 Composite Execution（C-58/C-60），不重跑摘要全量路径；
     * C-61：`compositeShadow*`（仅 SHADOW executed）。
     */
    public static Map<String, Object> summarizeCompositeGateAndExecutionOnly(AiRunState state) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        mergeCompositeProductionGateHarnessFields(out, state);
        mergeCompositeHarnessExecutionFields(out, state);
        return out;
    }

    /** Master debug 已将 Multi 编排写进 nested {@code businessOverviewMultiMaster}，部分运行态未再在 map 顶层重复 promote；摘要摊平以供 Harness Comparator 与同 GET-run 语义对齐。 */
    private static void fillFlatBusinessOverviewOrchestrationFieldsFromNestedMultiMaster(LinkedHashMap<String, Object> out) {
        Object nest = out.get("businessOverviewMultiMaster");
        if (!(nest instanceof Map<?, ?> nm) || nm.isEmpty()) {
            return;
        }
        for (String k : BUSINESS_OVERVIEW_MULTI_ORCHESTRATION_FLAT_FALLBACK_KEYS) {
            if (out.get(k) != null) {
                continue;
            }
            if (nm.containsKey(k)) {
                out.put(k, nm.get(k));
            }
        }
    }

    /**
     * Graph 完成后 {@link AiHarnessReplayContextProbes} 不执行；对已验收子域占位探针与其它 resolver-only 键对齐。
     */
    private static void mirrorHarnessReplayProbesPresenceFromAnswerPlans(LinkedHashMap<String, Object> out, AiRunState state) {
        if (state == null) {
            return;
        }
        AiResolvedQueryContext rqExe = state.getResolvedQueryContext();
        String pathEff = rqExe != null ? blankToNull(rqExe.getEffectivePathCode()) : null;
        String intentEff = rqExe != null ? blankToNull(rqExe.getEffectiveIntentCode()) : null;

        Object planSrc = out.get("planSource");
        if (planSrc != null && StringUtils.hasText(planSrc.toString())) {
            out.putIfAbsent("harnessReplayPlanSource", planSrc.toString().trim());
        }

        if (AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW.equals(pathEff)
                || AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW.equals(pathEff)
                || AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS.equals(pathEff)) {
            boolean revenueEvidence =
                    state.getRevenueAnswerPlan() != null
                            || Boolean.TRUE.equals(out.get("revenueAnswerPlanPresent"))
                            || harnessConsumedAnswerPlansIndicatesDailyRevenue(out);
            out.putIfAbsent("harnessReplayRevenueAnswerPlanProbePresent", revenueEvidence);
            Object rtp = out.get("revenueAnswerPlanType");
            if (rtp != null && StringUtils.hasText(rtp.toString())) {
                out.putIfAbsent("harnessReplayRevenueAnswerPlanType", rtp.toString().trim());
            }
        }

        if (AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW.equals(pathEff)) {
            out.putIfAbsent(
                    "harnessReplayPurchaseAnswerPlanProbePresent",
                    Boolean.TRUE.equals(out.get("purchaseAnswerPlanPresent")));
            Object pt = out.get("purchaseAnswerPlanType");
            if (pt != null && StringUtils.hasText(pt.toString())) {
                out.putIfAbsent("harnessReplayPurchaseAnswerPlanType", pt.toString().trim());
            }
        }

        if (AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY.equals(pathEff)) {
            Object st = out.get("stockReduceAnswerPlanType");
            if (st != null && StringUtils.hasText(st.toString())) {
                out.putIfAbsent("harnessReplayStockReduceAnswerPlanType", st.toString().trim());
            }
            Object sd = out.get("stockReduceAnswerPlanSortDirection");
            if (sd != null && StringUtils.hasText(sd.toString())) {
                out.putIfAbsent("harnessReplayStockReduceAnswerPlanSortDirection", sd.toString().trim());
            }
            StockReduceAnswerPlan srp = state.getStockReduceAnswerPlan();
            if (srp != null && StringUtils.hasText(srp.getPlanType())) {
                String rt = AiHarnessReplayContextProbes.resolveStockReduceType(srp.getPlanType());
                if (StringUtils.hasText(rt)) {
                    out.putIfAbsent("harnessReplayStockReduceReduceType", rt.trim());
                }
            }
        }

        if (AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(pathEff)) {
            DishProfitAnswerPlan dpp = state.getDishProfitAnswerPlan();
            if (dpp != null && StringUtils.hasText(dpp.getPlanType())) {
                String dpt = dpp.getPlanType().trim();
                out.putIfAbsent("harnessReplayDishProfitAnswerPlanType", dpt);
                if (DishProfitAnswerPlan.TYPE_DISH_LOWEST_MARGIN.equals(dpt)) {
                    out.putIfAbsent("harnessReplayDishProfitAnswerPlanSortDirection", "ASC");
                } else if (DishProfitAnswerPlan.TYPE_DISH_HIGHEST_MARGIN.equals(dpt)) {
                    out.putIfAbsent("harnessReplayDishProfitAnswerPlanSortDirection", "DESC");
                }
            }
        }

        if (AiResolvedQueryIntent.PATH_DISH_SALES_QUERY.equals(pathEff)
                || AiResolvedQueryIntent.DISH_SALES_QUERY.equals(intentEff)) {
            DishSalesAnswerPlan dsp = state.getDishSalesAnswerPlan();
            if (dsp != null) {
                if (StringUtils.hasText(dsp.getPlanType())) {
                    out.putIfAbsent("harnessReplayDishSalesAnswerPlanType", dsp.getPlanType().trim());
                }
                if (dsp.getRankingRows() != null) {
                    out.putIfAbsent("harnessReplayDishSalesRankingRowCount", dsp.getRankingRows().size());
                }
                if (StringUtils.hasText(dsp.getMetricType())) {
                    out.putIfAbsent("harnessReplayDishSalesMetricType", dsp.getMetricType().trim());
                }
                List<Map<String, Object>> rrows = dsp.getRankingRows();
                if (rrows != null && !rrows.isEmpty()) {
                    Map<String, Object> top = rrows.get(0);
                    if (top != null) {
                        Object nm = top.get("dishName");
                        if (nm != null && StringUtils.hasText(nm.toString())) {
                            out.putIfAbsent("harnessReplayDishSalesTopDishName", nm.toString().trim());
                        }
                    }
                }
            }
        }

        BusinessDiagnosisPlan bdp = state.getBusinessDiagnosisPlan();
        if (bdp != null) {
            if (StringUtils.hasText(bdp.getPlanType())) {
                out.putIfAbsent("harnessReplayBusinessDiagnosisPlanType", bdp.getPlanType().trim());
            }
            BusinessDiagnosisPlan.StorePriorityRankingPlan spr = bdp.getStorePriorityRanking();
            if (spr != null) {
                if (StringUtils.hasText(spr.getRankingType())) {
                    out.putIfAbsent("harnessReplayStorePriorityRankingPlanType", spr.getRankingType().trim());
                }
                List<BusinessDiagnosisPlan.StorePriorityFocus> fs = spr.getFocusStores();
                int rowLen = fs == null ? 0 : fs.size();
                out.putIfAbsent("harnessReplayStorePriorityRankingRowsLen", rowLen);
                if (fs != null && !fs.isEmpty()) {
                    BusinessDiagnosisPlan.StorePriorityFocus top = fs.get(0);
                    if (top != null) {
                        if (StringUtils.hasText(top.getStoreName())) {
                            out.putIfAbsent(
                                    "harnessReplayStorePriorityRankingTop1StoreName", top.getStoreName().trim());
                        }
                        if (top.getPriorityRank() != null) {
                            out.putIfAbsent("harnessReplayStorePriorityRankingTop1PriorityRank", top.getPriorityRank());
                        }
                    }
                }
            }
        }

        if (isHarnessStoreCompareDiagnosisWire(rqExe)) {
            DiagnosisPlan dpc = state.getDiagnosisPlan();
            List<Map<String, Object>> cev = dpc != null ? dpc.getStoreCompareEvidence() : null;
            int crLen = cev == null ? 0 : cev.size();
            out.putIfAbsent("harnessReplayStoreCompareEvidenceRowsLen", crLen);
        }
    }

    /**
     * 门店优先排序：读 {@link BusinessDiagnosisPlan#getStorePriorityRanking()}，勿从 {@link DiagnosisPlan} 取（无此字段）。
     */
    private static void applyStorePriorityRankingSummaryFields(
            LinkedHashMap<String, Object> out, BusinessDiagnosisPlan.StorePriorityRankingPlan sprp) {
        if (sprp == null) {
            out.put("storePriorityRankingPlanType", null);
            out.put("storePriorityRankingRowsLen", null);
            out.put("storePriorityRankingTop1StoreName", null);
            out.put("storePriorityRankingTop1PriorityRank", null);
            return;
        }
        out.put("storePriorityRankingPlanType", blankToNull(sprp.getRankingType()));
        List<BusinessDiagnosisPlan.StorePriorityFocus> fs = sprp.getFocusStores();
        int len = fs == null ? 0 : fs.size();
        out.put("storePriorityRankingRowsLen", len);
        if (fs != null && !fs.isEmpty()) {
            BusinessDiagnosisPlan.StorePriorityFocus top = fs.get(0);
            if (top != null) {
                out.put("storePriorityRankingTop1StoreName", blankToNull(top.getStoreName()));
                out.put("storePriorityRankingTop1PriorityRank", top.getPriorityRank());
            } else {
                out.put("storePriorityRankingTop1StoreName", null);
                out.put("storePriorityRankingTop1PriorityRank", null);
            }
        } else {
            out.put("storePriorityRankingTop1StoreName", null);
            out.put("storePriorityRankingTop1PriorityRank", null);
        }
    }

    /**
     * {@code business_store_status_compare_diagnosis}：摊平 {@link DiagnosisPlan#getStoreCompareEvidence()} 行数与 Top 门店名（排序与
     * {@link com.nongxinle.ai.composer.renderer.DiagnosisDeterministicRenderer#renderStoreCompareEvidenceAnswer(DiagnosisPlan)} 一致：营业额降序、再按标签）。
     */
    private static void applyBusinessStoreCompareEvidenceHarnessSummaryFields(
            LinkedHashMap<String, Object> out, AiRunState state) {
        if (state == null || !isHarnessStoreCompareDiagnosisWire(state.getResolvedQueryContext())) {
            return;
        }
        DiagnosisPlan dp = state.getDiagnosisPlan();
        List<Map<String, Object>> ev = dp != null ? dp.getStoreCompareEvidence() : null;
        int len = ev == null ? 0 : ev.size();
        out.put("businessStoreCompareEvidenceRowsLen", Integer.valueOf(len));
        List<Map<String, Object>> sorted = sortedStoreCompareEvidenceRowsForHarness(ev);
        out.put(
                "businessStoreCompareTop1StoreName",
                sorted.isEmpty() ? null : blankToNull(storeCompareRowPrimaryLabel(sorted.get(0))));
        out.put(
                "businessStoreCompareTop2StoreName",
                sorted.size() < 2 ? null : blankToNull(storeCompareRowPrimaryLabel(sorted.get(1))));
    }

    private static boolean isHarnessStoreCompareDiagnosisWire(AiResolvedQueryContext rq) {
        if (rq == null || rq.getQueryIntent() == null) {
            return false;
        }
        String canon =
                AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                        rq.getQueryIntent().getStructuredIntentDetail());
        return AiQuerySemanticLexicon.STRUCTURED_BUSINESS_STORE_COMPARE_DIAGNOSIS.equals(canon);
    }

    private static List<Map<String, Object>> sortedStoreCompareEvidenceRowsForHarness(
            List<Map<String, Object>> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> x : raw) {
            if (x != null) {
                rows.add(x);
            }
        }
        rows.sort(
                Comparator.<Map<String, Object>, Double>comparing(
                                AiHarnessResolvedContextSummarizer::storeCompareEvidenceRevenueOrNull,
                                Comparator.nullsFirst(Double::compareTo))
                        .reversed()
                        .thenComparing(r -> plainOrEmpty(storeCompareRowPrimaryLabel(r))));
        return rows;
    }

    /** 确定性门店对比渲染：营业额字段解析（缺省为 null）。 */
    private static Double storeCompareEvidenceRevenueOrNull(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        Object v = row.get("revenueAmount");
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        String s = v.toString().trim().replace(",", "");
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(s);
        } catch (Exception ignore) {
            return null;
        }
    }

    private static String plainOrEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String storeCompareRowPrimaryLabel(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        Object n = row.get("storeName");
        if (n != null && StringUtils.hasText(n.toString())) {
            return n.toString().trim();
        }
        Object id = row.get("storeDepartmentId");
        return id != null ? ("门店 " + id) : null;
    }

    private static boolean harnessConsumedAnswerPlansIndicatesDailyRevenue(LinkedHashMap<String, Object> out) {
        Object raw = out.get("consumedAnswerPlans");
        if (!(raw instanceof List<?> list)) {
            return false;
        }
        for (Object x : list) {
            if (x == null) {
                continue;
            }
            String s = x.toString().trim();
            if (s.startsWith("DailyRevenueAnswerPlan")) {
                return true;
            }
        }
        return false;
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
        if (StringUtils.hasText(ctx.getResolvedMatchedSemanticStoreMention())) {
            return ctx.getResolvedMatchedSemanticStoreMention().trim();
        }
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

    private static Map<String, Object> summarizeQuerySemanticParse(AiQuerySemanticParseResult r) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        if (r == null) {
            m.put("parseMissing", true);
            return m;
        }
        m.put("parseMissing", r.isParseMissing());
        m.put("intent", blankToNull(r.getIntent()));
        m.put("confidence", r.getConfidence());
        if (r.getTime() != null) {
            LinkedHashMap<String, Object> t = new LinkedHashMap<>();
            t.put("timeType", blankToNull(r.getTime().getTimeType()));
            t.put("startDate", blankToNull(r.getTime().getStartDate()));
            t.put("endDate", blankToNull(r.getTime().getEndDate()));
            t.put("timeSource", blankToNull(r.getTime().getTimeSource()));
            t.put("needInheritFromPrevious", r.getTime().getNeedInheritFromPrevious());
            m.put("time", t);
        } else {
            m.put("time", null);
        }
        if (r.getRequestedScope() != null) {
            LinkedHashMap<String, Object> rs = new LinkedHashMap<>();
            rs.put("requestedScopeType", blankToNull(r.getRequestedScope().getRequestedScopeType()));
            rs.put(
                    "mentionedStoreName",
                    AiQuerySemanticParseResult.sanitizeMentionedStoreNameToken(
                            r.getRequestedScope().getMentionedStoreName()));
            rs.put("mentionedStoreNames", emptyToNullCopy(r.getRequestedScope().getMentionedStoreNames()));
            rs.put("mentionedDepartmentName", blankToNull(r.getRequestedScope().getMentionedDepartmentName()));
            rs.put("mentionedWarehouseName", blankToNull(r.getRequestedScope().getMentionedWarehouseName()));
            rs.put("scopeSource", blankToNull(r.getRequestedScope().getScopeSource()));
            rs.put("needInheritFromPrevious", r.getRequestedScope().getNeedInheritFromPrevious());
            m.put("requestedScope", rs);
        } else {
            m.put("requestedScope", null);
        }
        if (r.getMetric() != null) {
            LinkedHashMap<String, Object> met = new LinkedHashMap<>();
            met.put("primaryMetric", blankToNull(r.getMetric().getPrimaryMetric()));
            met.put("rankingType", blankToNull(r.getMetric().getRankingType()));
            met.put("purchaseSourceType", blankToNull(r.getMetric().getPurchaseSourceType()));
            met.put("stockReduceType", blankToNull(r.getMetric().getStockReduceType()));
            m.put("metric", met);
        } else {
            m.put("metric", null);
        }
        m.put("needClarification", r.getNeedClarification());
        m.put("clarificationQuestion", blankToNull(r.getClarificationQuestion()));
        m.put("reason", blankToNull(r.getReason()));
        m.put("mentionedStoreNames", emptyToNullCopy(r.effectiveMentionedStoreNames()));
        return m;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> jsonDeepCopyMap(Map<String, Object> in) {
        if (in == null) {
            return null;
        }
        return (Map<String, Object>) JSON.parseObject(JSON.toJSONString(in), Map.class);
    }

    private static String blankToNull(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        return s.trim();
    }

    private static List<String> emptyToNullCopy(List<String> in) {
        if (in == null || in.isEmpty()) {
            return null;
        }
        List<String> out = new ArrayList<>();
        for (String s : in) {
            String t = AiQuerySemanticParseResult.sanitizeMentionedStoreNameToken(s);
            if (t != null) {
                out.add(t);
            }
        }
        return out.isEmpty() ? null : out;
    }

    private static boolean semanticV2StructuredTimeInheritsPrevious(AiResolvedQueryContext ctx) {
        if (ctx == null) {
            return false;
        }
        if ("INHERIT_PREVIOUS".equals(blankToNull(ctx.getQuerySemanticV2TimeAction()))) {
            return true;
        }
        AiQuerySemanticParseResult q = ctx.getQuerySemanticParse();
        if (q == null || q.isParseMissing()) {
            return false;
        }
        AiQuerySemanticParseResult.TimePart tp = q.getTime();
        if (tp == null) {
            return false;
        }
        if (Boolean.TRUE.equals(tp.getNeedInheritFromPrevious())) {
            return true;
        }
        if (StringUtils.hasText(tp.getTimeSource())) {
            String ts = tp.getTimeSource().trim().toUpperCase(Locale.ROOT).replace('-', '_');
            return "INHERITED_PREVIOUS".equals(ts);
        }
        return false;
    }

    private static boolean harnessTimeExplicitForSummary(AiResolvedQueryContext ctx, AiResolvedTimeWindow tw) {
        if (tw == null) {
            return false;
        }
        if (semanticV2StructuredTimeInheritsPrevious(ctx)) {
            return false;
        }
        return tw.isExplicitTimeMentioned();
    }

    /**
     * 仅 Harness 摘要：若业务时间窗已与上一轮起止日一致，不应再标 {@code DEFAULT_MONTH_TO_DATE}。
     * 不修改 {@link AiResolvedTimeWindow} 本体。
     */
    private static String reconcileEffectiveTimeWindowSourceForHarness(
            String declared,
            AiResolvedTimeWindow tw,
            AiConversationTurnMemory previousTurn,
            AiResolvedQueryContext ctx) {
        if (semanticV2StructuredTimeInheritsPrevious(ctx)) {
            if (tw != null && tw.isInheritedFromPreviousTurn()) {
                return "INHERITED_PREVIOUS";
            }
            if (tw != null && previousTurn != null && AiMultiTurnTimeWindowPolicy.hasTurnMemoryDates(previousTurn)) {
                try {
                    LocalDate s = tw.getStartDate();
                    LocalDate e = tw.getEndDate();
                    LocalDate ps = LocalDate.parse(previousTurn.getLastStartDate());
                    LocalDate pe = LocalDate.parse(previousTurn.getLastEndDate());
                    if (s != null && e != null && s.equals(ps) && e.equals(pe)) {
                        return "INHERITED_PREVIOUS";
                    }
                } catch (Exception ignore) {
                }
            }
        }
        if (tw != null
                && tw.isExplicitTimeMentioned()
                && !semanticV2StructuredTimeInheritsPrevious(ctx)
                && "DEFAULT_MONTH_TO_DATE".equals(declared)) {
            return "SEMANTIC_EXPLICIT";
        }
        if (!"DEFAULT_MONTH_TO_DATE".equals(declared)) {
            return declared;
        }
        if (tw == null || previousTurn == null || !AiMultiTurnTimeWindowPolicy.hasTurnMemoryDates(previousTurn)) {
            return declared;
        }
        try {
            LocalDate s = tw.getStartDate();
            LocalDate e = tw.getEndDate();
            if (s == null || e == null) {
                return declared;
            }
            LocalDate ps = LocalDate.parse(previousTurn.getLastStartDate());
            LocalDate pe = LocalDate.parse(previousTurn.getLastEndDate());
            if (s.equals(ps) && e.equals(pe)) {
                return "INHERITED_PREVIOUS";
            }
        } catch (Exception ignored) {
        }
        return declared;
    }

    /**
     * 经营概览 MULTI_AGENT：usedTools 优先列出四专线中 success=true 的工具；尚无成功时仍可回退到已编排的四域工具 id，
     * 避免 harness 上出现旧默认链路的「计划即 used」误判。
     */
    private static List<String> resolveHarnessUsedTools(AiRunState state,
            AiResolvedQueryContext rq,
            List<String> allPlanned) {
        if (allPlanned == null) {
            return null;
        }
        if (state == null || !state.isBusinessOverviewPath()) {
            return new ArrayList<>(allPlanned);
        }
        String tm = rq != null ? rq.getOrchestrationTaskMode() : null;
        boolean multi = (tm != null && "MULTI_AGENT".equalsIgnoreCase(tm.trim()))
                || Boolean.TRUE.equals(rq != null ? rq.getOrchestrationMultiAgentRequired() : null);
        if (!multi) {
            return new ArrayList<>(allPlanned);
        }
        List<String> orderedSuccess = new ArrayList<>();
        for (String domainId : AiBusinessToolIds.BUSINESS_OVERVIEW_MULTI_AGENT_DOMAIN_TOOLS) {
            if (allPlanned.contains(domainId) && harnessToolEnvelopeSuccess(state, domainId)) {
                orderedSuccess.add(domainId);
            }
        }
        if (!orderedSuccess.isEmpty()) {
            return orderedSuccess;
        }
        List<String> plannedDomainOnly = new ArrayList<>();
        for (String domainId : AiBusinessToolIds.BUSINESS_OVERVIEW_MULTI_AGENT_DOMAIN_TOOLS) {
            if (allPlanned.contains(domainId)) {
                plannedDomainOnly.add(domainId);
            }
        }
        return plannedDomainOnly.isEmpty() ? new ArrayList<>(allPlanned) : plannedDomainOnly;
    }

    private static boolean harnessToolEnvelopeSuccess(AiRunState state, String toolKey) {
        if (state.getToolResults() == null || toolKey == null) {
            return false;
        }
        Object env = state.getToolResults().get(toolKey);
        if (!(env instanceof Map<?, ?> map)) {
            return false;
        }
        return Boolean.TRUE.equals(map.get("success"));
    }
}
