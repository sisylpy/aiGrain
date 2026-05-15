package com.nongxinle.ai.semantic;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiFollowUpResolver;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.resolver.AiMultiTurnTimeWindowPolicy;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Locale;

/**
 * 将 {@link AiQuerySemanticParseResult} 合并入意图/时间草稿（时间落地见 {@link AiResolvedTimeWindow#fromSemanticTimeType}）；不得在合并阶段写入任何数据库 ID。
 */
public final class AiQuerySemanticLlmMergeHelper {

    private AiQuerySemanticLlmMergeHelper() {
    }

    public static AiResolvedQueryIntent mergeIntent(
            AiResolvedQueryIntent keywordIntent,
            AiQuerySemanticParseResult sem,
            double minConfidence) {
        return mergeIntent(keywordIntent, sem, minConfidence, null, null);
    }

    public static AiResolvedQueryIntent mergeIntent(
            AiResolvedQueryIntent keywordIntent,
            AiQuerySemanticParseResult sem,
            double minConfidence,
            String normalizedUserMessage) {
        return mergeIntent(keywordIntent, sem, minConfidence, normalizedUserMessage, null);
    }

    /**
     * @param previousTurn 非首轮时传入，用于解析 intentAction/timeAction 等多轮语义动作。
     */
    public static AiResolvedQueryIntent mergeIntent(
            AiResolvedQueryIntent keywordIntent,
            AiQuerySemanticParseResult sem,
            double minConfidence,
            String normalizedUserMessage,
            AiConversationTurnMemory previousTurn) {
        AiResolvedQueryIntent base = keywordIntent != null ? copyIntent(keywordIntent) : AiResolvedQueryIntent.builder().build();
        if (sem == null || sem.isParseMissing() || !sem.isStructuralConfidenceOk(minConfidence)) {
            return base;
        }

        String norm = normalizedUserMessage != null ? normalizedUserMessage.trim() : "";
        String ia = semanticActionNormalize(sem.getIntentAction());

        boolean inheritPrevIntent =
                "INHERIT_PREVIOUS".equals(ia) && previousTurn != null && StringUtils.hasText(previousTurn.getLastPathCode());

        AiResolvedQueryIntent merged;
        if (inheritPrevIntent) {
            merged =
                    AiFollowUpResolver.inheritIntentFromMemory(
                            previousTurn, StringUtils.hasText(norm) ? norm : "");
        } else {
            WireIntent mapped = mapLlmIntent(sem.getIntent());
            if (mapped == null) {
                return base;
            }
            merged =
                    AiResolvedQueryIntent.builder()
                            .intentCode(mapped.intentCode())
                            .pathCode(mapped.pathCode())
                            .topic(mapped.topic())
                            .structuredIntentDetail(base.getStructuredIntentDetail())
                            .purchaseSourceType(base.getPurchaseSourceType())
                            .inheritedFromPreviousTurn(base.isInheritedFromPreviousTurn())
                            .inheritedFromIntentCode(base.getInheritedFromIntentCode())
                            .build();
        }

        remapWarehouseToStockReduceWhenOutboundRankingWire(merged, sem);
        remapCostDiagnosisToDishProfitWhenDishMetricWire(merged, sem);
        applyMetricStructuredWire(merged, sem);
        remapResidualCostDiagnosisToBusinessEvidenceDiagnosis(merged);
        AiQuerySemanticV2DishProfitGate.ensureDishGrossMarginQueryWireWhenSingleDishProfit(merged, sem);
        applyRevenueStoreAmountRankingWhenMultiStoreMentioned(merged, sem, normalizedUserMessage);
        applyPurchaseStoreAmountRankingWhenMultiStoreMentioned(merged, sem);
        applyPurchaseAnomalyStructuredWireOverrideWhenMetricOrToolsSignal(merged, sem);
        applyStockReduceStoreAmountRankingWhenMultiStoreMentioned(merged, sem, previousTurn);
        applyBusinessStoreStatusCompareWhenMultiStoreMentioned(merged, sem);
        remapBusinessOverviewCompareToBusinessDiagnosisWhenMetricSignals(merged, sem);
        applyBusinessDiagnosisStructuredCompareWhenMultiStore(merged, sem);
        if (AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW.equals(merged.getPathCode())
                && sem.getMetric() != null && StringUtils.hasText(sem.getMetric().getPurchaseSourceType())) {
            merged.setPurchaseSourceType(sem.getMetric().getPurchaseSourceType().trim());
        }
        if (AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY.equals(merged.getPathCode())
                && sem.getMetric() != null && StringUtils.hasText(sem.getMetric().getStockReduceType())) {
            String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(merged.getStructuredIntentDetail());
            boolean keepRankingWire =
                    AiQuerySemanticLexicon.STRUCTURED_STORE_OUTBOUND_AMOUNT_RANKING.equals(canon)
                            || AiQuerySemanticLexicon.STRUCTURED_GOODS_OUTBOUND_RANKING.equals(canon)
                            || AiQuerySemanticLexicon.STRUCTURED_GOODS_OUTBOUND_COUNT_RANKING.equals(canon);
            if (!keepRankingWire) {
                merged.setStructuredIntentDetail(sem.getMetric().getStockReduceType().trim());
            }
        }

        normalizePathsLikeKeywordResolver(merged);
        return merged;
    }

    /**
     * @deprecated 解析器侧应传入用户原文与合并后意图以便经营类话术过滤 LLM 幻觉时间。
     */
    @Deprecated
    public static AiResolvedTimeWindow mergeTentativeTime(
            AiResolvedTimeWindow tentativeTime,
            AiQuerySemanticParseResult sem,
            LocalDate today,
            double minConfidence) {
        return mergeTentativeTime(tentativeTime, sem, today, minConfidence, null, null, null);
    }

    /**
     * @deprecated 请使用带 {@code previousTurn} 的重载以支持 LAST_YEAR_SAME_PERIOD。
     */
    @Deprecated
    public static AiResolvedTimeWindow mergeTentativeTime(
            AiResolvedTimeWindow tentativeTime,
            AiQuerySemanticParseResult sem,
            LocalDate today,
            double minConfidence,
            String normalizedUserMessage,
            AiResolvedQueryIntent mergedIntentHint) {
        return mergeTentativeTime(tentativeTime, sem, today, minConfidence, normalizedUserMessage, mergedIntentHint, null);
    }

    /**
     * 合并语义 LLM 时间窗：依据 {@code timeAction} / {@code timeType} 与 {@link AiResolvedTimeWindow#fromSemanticTimeType}
     * 落地日期；{@code tentativeTime} 为上游已确定的显式窗（若有）时优先保留。
     */
    public static AiResolvedTimeWindow mergeTentativeTime(
            AiResolvedTimeWindow tentativeTime,
            AiQuerySemanticParseResult sem,
            LocalDate today,
            double minConfidence,
            String normalizedUserMessage,
            AiResolvedQueryIntent mergedIntentHint,
            AiConversationTurnMemory previousTurn) {
        LocalDate anchor = today != null ? today : LocalDate.now();
        if (sem == null || sem.isParseMissing() || !sem.isStructuralConfidenceOk(minConfidence)) {
            return tentativeTime;
        }
        String ta = semanticActionNormalize(sem.getTimeAction());
        if (semanticTimeStructuredToDeferToPreviousTurn(sem, ta)) {
            return tentativeTime;
        }
        if ("INHERIT_PREVIOUS".equals(ta)) {
            return tentativeTime;
        }
        if (shouldDeferSemanticThisMonthPlaceholderToPreviousTurn(sem, previousTurn, ta, normalizedUserMessage)) {
            return tentativeTime;
        }
        if (samePathScopeOrMetricOverrideInheritsPreviousCalendar(
                sem, previousTurn, mergedIntentHint, ta, normalizedUserMessage)) {
            return tentativeTime;
        }
        if (shouldInheritTimeForSamePathDishMentionStructuredFollowUp(sem, previousTurn, mergedIntentHint, ta)) {
            return tentativeTime;
        }
        AiQuerySemanticParseResult.TimePart tp = sem.getTime();
        if (tp == null || !StringUtils.hasText(tp.getTimeType())) {
            return tentativeTime;
        }
        boolean timeActsLikeOverride =
                "OVERRIDE".equals(ta) || "NEW".equals(ta);
        String label = AiResolvedTimeWindow.normalizeSemanticTimeTypeLabel(tp.getTimeType());
        if (AiResolvedTimeWindow.LAST_YEAR_SAME_PERIOD.equals(label)) {
            if (!timeActsLikeOverride && Boolean.TRUE.equals(tp.getNeedInheritFromPrevious())) {
                return tentativeTime;
            }
            AiResolvedTimeWindow shifted = buildLastYearSamePeriodFromPreviousTurn(previousTurn, anchor);
            if (shifted == null) {
                return tentativeTime;
            }
            if (tentativeTime != null && tentativeTime.isExplicitTimeMentioned()) {
                return tentativeTime;
            }
            return shifted;
        }
        AiResolvedTimeWindow fromLlm = timePartToWindow(tp, anchor);
        if (fromLlm == null) {
            return tentativeTime;
        }
        if (!timeActsLikeOverride && Boolean.TRUE.equals(tp.getNeedInheritFromPrevious())) {
            return tentativeTime;
        }
        if (tentativeTime != null && tentativeTime.isExplicitTimeMentioned()) {
            return tentativeTime;
        }
        return fromLlm;
    }

    /** 本句对统计时间做了结构化切换（与 {@code INHERIT_PREVIOUS} / 缺省相对）。 */
    private static boolean timeActionRequestsExplicitWindowChange(String timeActionNormalized) {
        return "NEW".equals(timeActionNormalized) || "OVERRIDE".equals(timeActionNormalized);
    }

    /**
     * 多轮且上轮已有落地统计窗：模型常把「未口述新时间」误标为 OVERRIDE + THIS_MONTH（本月至今占位）。
     * 若本句无 v2「明确时间词」中的当前月用语（见 {@link AiQuerySemanticTimeLexicon#explicitCurrentMonthMentioned}），则仅当
     * {@code time.timeSource=CURRENT_MESSAGE} 时信任 LLM 的 THIS_MONTH；否则交由
     * {@link AiMultiTurnTimeWindowPolicy#finalizeTimeWindow} 继承上一轮起止日。
     * <p>
     * 与 {@code query_semantic_parser.v2.md} 对齐：本句含「这个月/本月/当前月」时必须落地新窗，不得仅因
     * {@code time.timeSource} 未写 {@code CURRENT_MESSAGE} 而退回继承上一轮。
     */
    private static boolean shouldDeferSemanticThisMonthPlaceholderToPreviousTurn(
            AiQuerySemanticParseResult sem,
            AiConversationTurnMemory previousTurn,
            String taNorm,
            String normalizedUserMessage) {
        if (AiQuerySemanticTimeLexicon.explicitCurrentMonthMentioned(normalizedUserMessage)) {
            return false;
        }
        if (!AiMultiTurnTimeWindowPolicy.hasTurnMemoryDates(previousTurn)) {
            return false;
        }
        if (!timeActionRequestsExplicitWindowChange(taNorm)) {
            return false;
        }
        AiQuerySemanticParseResult.TimePart tp = sem != null ? sem.getTime() : null;
        if (tp == null || !StringUtils.hasText(tp.getTimeType())) {
            return false;
        }
        String label = AiResolvedTimeWindow.normalizeSemanticTimeTypeLabel(tp.getTimeType());
        if (!AiResolvedTimeWindow.THIS_MONTH.equals(label)) {
            return false;
        }
        return !semanticTimeSourceIndicatesUtteranceExplicitCalendar(tp);
    }

    /**
     * 与 v1/v2 prompt 对齐：用户在本句明确说出「这个月/上周/具体日期」等新时间时填 {@code CURRENT_MESSAGE}。
     */
    private static boolean semanticTimeSourceIndicatesUtteranceExplicitCalendar(AiQuerySemanticParseResult.TimePart tp) {
        if (tp == null || !StringUtils.hasText(tp.getTimeSource())) {
            return false;
        }
        String ts = tp.getTimeSource().trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return "CURRENT_MESSAGE".equals(ts);
    }

    /**
     * 供 {@link com.nongxinle.ai.resolver.AiResolvedQueryContextResolver} Harness 摘要：在合并层纠正后的时间动作标签，
     * 避免 raw LLM 误标 OVERRIDE 导致 Replay 与实际上生效的继承窗不一致。
     */
    public static String canonicalQuerySemanticV2TimeActionForHarness(
            AiQuerySemanticParseResult sem,
            AiConversationTurnMemory previousTurn,
            double minConfidence) {
        return canonicalQuerySemanticV2TimeActionForHarness(sem, previousTurn, minConfidence, null);
    }

    /**
     * @param normalizedUserMessage 经 Resolver 归一的本轮用户句；用于与 v2「明确时间词」对齐 Harness 展示字段。
     */
    public static String canonicalQuerySemanticV2TimeActionForHarness(
            AiQuerySemanticParseResult sem,
            AiConversationTurnMemory previousTurn,
            double minConfidence,
            String normalizedUserMessage) {
        if (sem == null || sem.isParseMissing() || !sem.isStructuralConfidenceOk(minConfidence)) {
            return sem != null ? sem.getTimeAction() : null;
        }
        String ta = semanticActionNormalize(sem.getTimeAction());
        if (semanticTimeStructuredToDeferToPreviousTurn(sem, ta)) {
            return "INHERIT_PREVIOUS";
        }
        if ("INHERIT_PREVIOUS".equals(ta)) {
            return "INHERIT_PREVIOUS";
        }
        if (shouldDeferSemanticThisMonthPlaceholderToPreviousTurn(sem, previousTurn, ta, normalizedUserMessage)) {
            return "INHERIT_PREVIOUS";
        }
        AiQuerySemanticParseResult.TimePart tp = sem.getTime();
        if (tp == null || !StringUtils.hasText(tp.getTimeType())) {
            return sem.getTimeAction();
        }
        if (!timeActionRequestsExplicitWindowChange(ta)) {
            return sem.getTimeAction();
        }
        boolean timeActsLikeOverride = "OVERRIDE".equals(ta) || "NEW".equals(ta);
        if (!timeActsLikeOverride && Boolean.TRUE.equals(tp.getNeedInheritFromPrevious())) {
            return "INHERIT_PREVIOUS";
        }
        return sem.getTimeAction();
    }

    /**
     * v2：结构化要求沿用上一轮时间窗（勿仅因 timeType=THIS_MONTH/LAST_MONTH 走 LLM 重算并标成「本句显式」）。
     */
    private static boolean semanticTimeStructuredToDeferToPreviousTurn(
            AiQuerySemanticParseResult sem, String taNorm) {
        if (sem == null) {
            return false;
        }
        if ("INHERIT_PREVIOUS".equals(taNorm)) {
            return true;
        }
        AiQuerySemanticParseResult.TimePart tp = sem.getTime();
        if (tp != null && Boolean.TRUE.equals(tp.getNeedInheritFromPrevious())) {
            return true;
        }
        if (tp != null && StringUtils.hasText(tp.getTimeSource())) {
            String ts = tp.getTimeSource().trim().toUpperCase(Locale.ROOT).replace('-', '_');
            return "INHERITED_PREVIOUS".equals(ts);
        }
        return false;
    }

    /**
     * 同 path 追问仅切换 scope/metric：未给出「非本月占位」的显式日历时，沿用上一轮起止（如经营对比追问接在上个月经营概览后）。
     */
    private static boolean samePathScopeOrMetricOverrideInheritsPreviousCalendar(
            AiQuerySemanticParseResult sem,
            AiConversationTurnMemory previousTurn,
            AiResolvedQueryIntent mergedIntentHint,
            String taNorm,
            String normalizedUserMessage) {
        if (AiQuerySemanticTimeLexicon.explicitCurrentMonthMentioned(normalizedUserMessage)) {
            return false;
        }
        if (sem == null || previousTurn == null || mergedIntentHint == null) {
            return false;
        }
        String cur = mergedIntentHint.getPathCode();
        String prev = previousTurn.getLastPathCode();
        if (!StringUtils.hasText(cur) || !cur.equals(prev)) {
            return false;
        }
        if (!AiMultiTurnTimeWindowPolicy.hasTurnMemoryDates(previousTurn)) {
            return false;
        }
        boolean smOv =
                "OVERRIDE".equals(semanticActionNormalize(sem.getScopeAction()))
                        || "OVERRIDE".equals(semanticActionNormalize(sem.getMetricAction()));
        if (!smOv) {
            return false;
        }
        if (semanticTimeStructuredToDeferToPreviousTurn(sem, taNorm)) {
            return true;
        }
        return !explicitNonThisMonthSemanticCalendarShift(sem, taNorm);
    }

    /** timeAction=NEW/OVERRIDE 且 timeType 表达真实换月/自定义等（不含「本月至今」类占位）。 */
    private static boolean explicitNonThisMonthSemanticCalendarShift(
            AiQuerySemanticParseResult sem, String taNorm) {
        if (!timeActionRequestsExplicitWindowChange(taNorm)) {
            return false;
        }
        AiQuerySemanticParseResult.TimePart tp = sem.getTime();
        if (tp == null || !StringUtils.hasText(tp.getTimeType())) {
            return false;
        }
        String label = AiResolvedTimeWindow.normalizeSemanticTimeTypeLabel(tp.getTimeType());
        if (AiResolvedTimeWindow.THIS_MONTH.equals(label)) {
            return false;
        }
        if ("CURRENT_MONTH".equalsIgnoreCase(tp.getTimeType().trim())) {
            return false;
        }
        return true;
    }

    /**
     * 同业务 path 追问且 LLM 已结构化点名菜名：本句未对时间做 OVERRIDE 时，不采用 LLM 默认「本月至今」等，
     * 交由 {@link AiMultiTurnTimeWindowPolicy#finalizeTimeWindow} 继承上一轮起止日（仅依赖 followUp / path / mentionedDishName / timeAction）。
     */
    private static boolean shouldInheritTimeForSamePathDishMentionStructuredFollowUp(
            AiQuerySemanticParseResult sem,
            AiConversationTurnMemory previousTurn,
            AiResolvedQueryIntent mergedIntentHint,
            String timeActionNormalized) {
        if (sem == null
                || !Boolean.TRUE.equals(sem.getFollowUp())
                || previousTurn == null
                || !AiMultiTurnTimeWindowPolicy.hasTurnMemoryDates(previousTurn)
                || mergedIntentHint == null
                || !StringUtils.hasText(mergedIntentHint.getPathCode())) {
            return false;
        }
        if (!mergedIntentHint.getPathCode().equals(previousTurn.getLastPathCode())) {
            return false;
        }
        if (!StringUtils.hasText(sem.getMentionedDishName())) {
            return false;
        }
        if ("OVERRIDE".equals(timeActionNormalized)) {
            return false;
        }
        return true;
    }

    private static AiResolvedTimeWindow buildLastYearSamePeriodFromPreviousTurn(
            AiConversationTurnMemory previousTurn, LocalDate anchor) {
        if (!AiMultiTurnTimeWindowPolicy.hasTurnMemoryDates(previousTurn)) {
            return null;
        }
        try {
            LocalDate s = LocalDate.parse(previousTurn.getLastStartDate());
            LocalDate e = LocalDate.parse(previousTurn.getLastEndDate());
            AiResolvedTimeWindow prev = AiResolvedTimeWindow.builder().startDate(s).endDate(e).build();
            return AiResolvedTimeWindow.fromSemanticTimeType(
                    AiResolvedTimeWindow.LAST_YEAR_SAME_PERIOD, anchor, prev);
        } catch (Exception ex) {
            return null;
        }
    }

    /** 语义 LLM intentAction/timeAction… 等大写归一（仅合并层使用）。 */
    private static String semanticActionNormalize(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        return raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private static boolean isAnalyticsDefaultMonthToDatePath(String pathCode) {
        if (!StringUtils.hasText(pathCode)) {
            return false;
        }
        return AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW.equals(pathCode)
                || AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW.equals(pathCode)
                || AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW.equals(pathCode)
                || AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY.equals(pathCode)
                || AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(pathCode)
                || AiResolvedQueryIntent.PATH_DISH_SALES_QUERY.equals(pathCode)
                || AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS.equals(pathCode)
                || AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK.equals(pathCode)
                || AiResolvedQueryIntent.PATH_COST_DIAGNOSIS.equals(pathCode);
    }

    private static void normalizePathsLikeKeywordResolver(AiResolvedQueryIntent qi) {
        if (qi == null) {
            return;
        }
        if (AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW.equals(qi.getPathCode())
                && (!StringUtils.hasText(qi.getStructuredIntentDetail()))) {
            qi.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_REVENUE_OVERVIEW_SUMMARY);
        }
        if (AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW.equals(qi.getPathCode())
                && (!StringUtils.hasText(qi.getStructuredIntentDetail()))) {
            qi.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_OVERVIEW_SUMMARY);
        }
        if (AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY.equals(qi.getPathCode())
                && (!StringUtils.hasText(qi.getStructuredIntentDetail()))) {
            qi.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY);
        }
        if (AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(qi.getPathCode())
                && (!StringUtils.hasText(qi.getStructuredIntentDetail()))) {
            qi.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_OVERVIEW);
        }
        if (AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS.equals(qi.getPathCode())
                && (!StringUtils.hasText(qi.getStructuredIntentDetail()))) {
            qi.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_BUSINESS_DIAGNOSIS_SUMMARY);
        }
        if (AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW.equals(qi.getPathCode())
                && (!StringUtils.hasText(qi.getStructuredIntentDetail()))) {
            qi.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_BUSINESS_OVERVIEW_SUMMARY);
        }
    }

    private static void applyMetricStructuredWire(AiResolvedQueryIntent qi, AiQuerySemanticParseResult sem) {
        if (qi == null || sem == null || sem.getMetric() == null) {
            return;
        }
        AiQuerySemanticParseResult.MetricPart m = sem.getMetric();
        String pm = m.getPrimaryMetric();
        if (StringUtils.hasText(pm)) {
            String u = pm.trim().toUpperCase(Locale.ROOT).replace('-', '_');
            if ("COST_STRUCTURE".equals(u)
                    || "COST_PRESSURE".equals(u)
                    || "BUSINESS_COST_PRESSURE".equals(u)
                    || "OPERATION_HEALTH".equals(u)) {
                if (AiResolvedQueryIntent.PATH_COST_DIAGNOSIS.equals(qi.getPathCode())) {
                    qi.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_BUSINESS_COST_PRESSURE_DIAGNOSIS);
                    promoteOverviewCostPathToEvidenceDiagnosisIfRankingWireRequires(
                            qi, AiQuerySemanticLexicon.STRUCTURED_BUSINESS_COST_PRESSURE_DIAGNOSIS);
                }
            }
            if ("BUSINESS_STATUS".equals(u)
                    || "OPERATION_STATUS".equals(u)
                    || "OPERATIONS_STATUS".equals(u)
                    || "BUSINESS_OVERVIEW_PRIMARY".equals(u)) {
                if (AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW.equals(qi.getPathCode())) {
                    qi.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_BUSINESS_OVERVIEW_STATUS);
                }
            }
        }
        String rt = m.getRankingType();
        if (!StringUtils.hasText(rt)) {
            return;
        }
        String w = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(rt.trim());
        String wire = w != null ? w : rt.trim();
        if (AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW.equals(qi.getPathCode())
                || AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(qi.getPathCode())
                || AiResolvedQueryIntent.PATH_DISH_SALES_QUERY.equals(qi.getPathCode())
                || AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW.equals(qi.getPathCode())
                || AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY.equals(qi.getPathCode())
                || AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW.equals(qi.getPathCode())
                || AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS.equals(qi.getPathCode())
                || AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK.equals(qi.getPathCode())) {
            qi.setStructuredIntentDetail(wire);
            promoteOverviewCostPathToEvidenceDiagnosisIfRankingWireRequires(qi, wire);
        }
    }

    /**
     * rankingType wire 已为证据型诊断子口径时（解析 JSON），将误落在概览/COST_DIAGNOSIS 的 intent 升格。
     */
    private static void promoteOverviewCostPathToEvidenceDiagnosisIfRankingWireRequires(
            AiResolvedQueryIntent qi,
            String wireOrRanking) {
        if (qi == null || wireOrRanking == null) {
            return;
        }
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wireOrRanking);
        boolean costPressureWire =
                AiQuerySemanticLexicon.STRUCTURED_BUSINESS_COST_PRESSURE_DIAGNOSIS.equals(canon);
        boolean compareDiagnosisWire =
                AiQuerySemanticLexicon.STRUCTURED_BUSINESS_STORE_COMPARE_DIAGNOSIS.equals(canon);
        if (!costPressureWire && !compareDiagnosisWire) {
            return;
        }
        if (!(AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW.equals(qi.getPathCode())
                || AiResolvedQueryIntent.PATH_COST_DIAGNOSIS.equals(qi.getPathCode()))) {
            return;
        }
        qi.setIntentCode(AiResolvedQueryIntent.BUSINESS_DIAGNOSIS);
        qi.setPathCode(AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS);
        qi.setTopic("经营诊断");
        qi.setStructuredIntentDetail(canon);
    }

    /** 语义 LLM 点到 ≥2 店名时，固定走门店营业额排行 wire（不读用户话术）。 */
    private static void applyRevenueStoreAmountRankingWhenMultiStoreMentioned(
            AiResolvedQueryIntent qi,
            AiQuerySemanticParseResult sem,
            String normalizedUserMessage) {
        if (qi == null || sem == null) {
            return;
        }
        if (!AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW.equals(qi.getPathCode())) {
            return;
        }
        if (sem.effectiveMentionedStoreNames().size() >= 2) {
            qi.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_REVENUE_STORE_AMOUNT_RANKING);
            return;
        }
    }

    /**
     * 多店点名 + 采购路径：固定门店采购金额对比 wire；覆盖误识别的 supplier_amount_ranking（仅依赖结构化 scope + path，不读用户话术）。
     * 若解析层 metric/orchestration 已指向商品侧异常/冲高/商品排行，则不覆盖（避免继承 INHERIT_PREVIOUS 的 store ranking 压住异常链）。
     */
    private static void applyPurchaseStoreAmountRankingWhenMultiStoreMentioned(
            AiResolvedQueryIntent qi,
            AiQuerySemanticParseResult sem) {
        if (qi == null || sem == null) {
            return;
        }
        if (!AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW.equals(qi.getPathCode())) {
            return;
        }
        if (AiQuerySemanticLexicon.isStructuredPurchaseGoodsFocusedDetail(qi.getStructuredIntentDetail())) {
            return;
        }
        if (purchaseSemanticSignalsGoodsAnomalySpikeOrSimilar(sem)) {
            return;
        }
        if (sem.effectiveMentionedStoreNames().size() >= 2) {
            qi.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_STORE_AMOUNT_RANKING);
        }
    }

    /**
     * 当前轮 metric.rankingType / primaryMetric 或编排候选 tools 结构化信号表明「商品采购异常/冲高」时，覆盖误继承的
     * {@link AiQuerySemanticLexicon#STRUCTURED_PURCHASE_STORE_AMOUNT_RANKING}。
     */
    private static void applyPurchaseAnomalyStructuredWireOverrideWhenMetricOrToolsSignal(
            AiResolvedQueryIntent qi, AiQuerySemanticParseResult sem) {
        if (qi == null || sem == null) {
            return;
        }
        if (!AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW.equals(qi.getPathCode())) {
            return;
        }
        String prevCanon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(qi.getStructuredIntentDetail());
        if (!AiQuerySemanticLexicon.STRUCTURED_PURCHASE_STORE_AMOUNT_RANKING.equals(prevCanon)) {
            return;
        }
        if (!purchaseSemanticSignalsGoodsAnomalySpikeOrSimilar(sem)) {
            return;
        }
        AiQuerySemanticParseResult.MetricPart m = sem.getMetric();
        if (m != null && StringUtils.hasText(m.getRankingType())) {
            String rtc = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(m.getRankingType().trim());
            if (StringUtils.hasText(rtc)
                    && !AiQuerySemanticLexicon.STRUCTURED_PURCHASE_STORE_AMOUNT_RANKING.equals(rtc)) {
                qi.setStructuredIntentDetail(rtc);
                return;
            }
        }
        if (m != null && StringUtils.hasText(m.getPrimaryMetric())) {
            String u = m.getPrimaryMetric().trim().toUpperCase(Locale.ROOT).replace('-', '_');
            if (u.contains("SPIKE") || u.contains("AMOUNT_SPIKE")) {
                qi.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_AMOUNT_SPIKE);
                return;
            }
        }
        qi.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_ANOMALY);
    }

    /**
     * 基于解析 JSON 内的 metric / orchestration 工具列表（非用户原文）。
     */
    private static boolean purchaseSemanticSignalsGoodsAnomalySpikeOrSimilar(AiQuerySemanticParseResult sem) {
        if (sem == null) {
            return false;
        }
        AiQuerySemanticParseResult.MetricPart m = sem.getMetric();
        if (m != null && StringUtils.hasText(m.getRankingType())) {
            String rc = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(m.getRankingType().trim());
            if (StringUtils.hasText(rc)) {
                if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_ANOMALY.equals(rc)
                        || AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_AMOUNT_SPIKE.equals(rc)) {
                    return true;
                }
                if (AiQuerySemanticLexicon.isStructuredPurchaseGoodsFocusedDetail(rc)) {
                    return true;
                }
            }
        }
        if (m != null && StringUtils.hasText(m.getPrimaryMetric())) {
            String u = m.getPrimaryMetric().trim().toUpperCase(Locale.ROOT).replace('-', '_');
            if (u.contains("PURCHASE_GOODS_ANOMALY")
                    || u.contains("GOODS_PURCHASE_ANOMALY")
                    || u.contains("GOODS_AMOUNT_ANOMALY")
                    || u.contains("GOODS_AMOUNT_ABNORMAL")
                    || u.contains("PURCHASE_ABNORMAL")
                    || u.contains("PURCHASE_ANOMALY")
                    || u.contains("ABNORMAL_PURCHASE")
                    || u.contains("ANOMALY_QUERY")
                    || u.contains("ANOMALY_DETECTION")
                    || u.contains("ABNORMAL_QUERY")
                    || u.contains("ABNORMAL_DETECTION")
                    || u.contains("GOODS_AMOUNT_SPIKE")
                    || u.contains("PURCHASE_AMOUNT_SPIKE")
                    || u.contains("AMOUNT_SPIKE")
                    || u.contains("SPIKE_DETECTION")) {
                return true;
            }
        }
        AiQuerySemanticParseResult.OrchestrationDecisionCandidatePart od = sem.getOrchestrationDecisionCandidate();
        if (od != null && od.getSelectedTools() != null) {
            for (String t : od.getSelectedTools()) {
                if (!StringUtils.hasText(t)) {
                    continue;
                }
                String x = t.trim().toUpperCase(Locale.ROOT).replace('-', '_');
                // LLM/tool 注册表存在 abnormal / anomaly 两套命名（如 purchase_abnormal_query）。
                if (x.contains("ANOMALY")
                        || x.contains("ABNORMAL")
                        || x.contains("SPIKE")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 多店点名 + 出库核销路径：门店出库金额对比 wire（仅依赖结构化 scope + path，不读用户话术）。
     * 若当前 structured 或 metric.rankingType 已为商品出库金额/次数排行 canonical，则不覆盖。
     */
    private static void applyStockReduceStoreAmountRankingWhenMultiStoreMentioned(
            AiResolvedQueryIntent qi,
            AiQuerySemanticParseResult sem,
            AiConversationTurnMemory previousTurn) {
        if (qi == null || sem == null) {
            return;
        }
        if (!AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY.equals(qi.getPathCode())) {
            return;
        }
        // 商品出库金额/次数排行已由 applyMetricStructuredWire 等处落 canonical；多店门店对比不得覆盖。
        String detailCanon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(qi.getStructuredIntentDetail());
        if (StringUtils.hasText(detailCanon)
                && (AiQuerySemanticLexicon.STRUCTURED_GOODS_OUTBOUND_RANKING.equals(detailCanon)
                        || AiQuerySemanticLexicon.STRUCTURED_GOODS_OUTBOUND_COUNT_RANKING.equals(detailCanon))) {
            return;
        }
        if (sem.getMetric() != null && StringUtils.hasText(sem.getMetric().getRankingType())) {
            String rtCanon =
                    AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(sem.getMetric().getRankingType().trim());
            if (StringUtils.hasText(rtCanon)
                    && (AiQuerySemanticLexicon.STRUCTURED_GOODS_OUTBOUND_RANKING.equals(rtCanon)
                            || AiQuerySemanticLexicon.STRUCTURED_GOODS_OUTBOUND_COUNT_RANKING.equals(rtCanon))) {
                return;
            }
        }
        if (sem.effectiveMentionedStoreNames().size() >= 2) {
            qi.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_STORE_OUTBOUND_AMOUNT_RANKING);
            return;
        }
        if (inheritsMultiStoreComparableAmountShapeFromTurn(previousTurn)) {
            qi.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_STORE_OUTBOUND_AMOUNT_RANKING);
        }
    }

    /**
     * 上轮为「≥2 店可见/.harness 对齐」下的门店营业额/采购/出库或多店经营对比 structured wire，
     * 本轮换到出库专线且语义未重复点名≥2店时仍继承门店出库金额对比形态（不靠用户话术匹配）。
     */
    private static boolean inheritsMultiStoreComparableAmountShapeFromTurn(AiConversationTurnMemory previousTurn) {
        if (previousTurn == null) {
            return false;
        }
        String canon =
                AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                        previousTurn.getLastStructuredIntentDetail());
        if (!StringUtils.hasText(canon)) {
            return false;
        }
        boolean comparableWire =
                AiQuerySemanticLexicon.STRUCTURED_REVENUE_STORE_AMOUNT_RANKING.equals(canon)
                        || AiQuerySemanticLexicon.STRUCTURED_PURCHASE_STORE_AMOUNT_RANKING.equals(canon)
                        || AiQuerySemanticLexicon.STRUCTURED_STORE_OUTBOUND_AMOUNT_RANKING.equals(canon)
                        || AiQuerySemanticLexicon.STRUCTURED_BUSINESS_STORE_STATUS_COMPARE.equals(canon)
                        || AiQuerySemanticLexicon.STRUCTURED_BUSINESS_STORE_COMPARE_DIAGNOSIS.equals(canon);
        if (!comparableWire) {
            return false;
        }
        boolean multiVenueHarness =
                previousTurn.getLastHarnessMultiStoreMatchedStores() != null
                        && previousTurn.getLastHarnessMultiStoreMatchedStores().size() >= 2;
        boolean multiVenueVisible =
                previousTurn.getLastVisibleStoreIds() != null
                        && previousTurn.getLastVisibleStoreIds().size() >= 2;
        return multiVenueHarness || multiVenueVisible;
    }

    /**
     * 多店点名 + 经营概览路径：综合经营对比 wire（非 revenue_store_amount_ranking；不读用户话术）。
     */
    private static void applyBusinessStoreStatusCompareWhenMultiStoreMentioned(
            AiResolvedQueryIntent qi,
            AiQuerySemanticParseResult sem) {
        if (qi == null || sem == null) {
            return;
        }
        if (!AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW.equals(qi.getPathCode())) {
            return;
        }
        if (sem.effectiveMentionedStoreNames().size() >= 2) {
            qi.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_BUSINESS_STORE_STATUS_COMPARE);
        }
    }

    /**
     * 多店综合对比 + metric.primaryMetric 指向「可比 + 归因/因果」的诊断口径时，升格为 {@link AiResolvedQueryIntent#BUSINESS_DIAGNOSIS}。
     */
    private static void remapBusinessOverviewCompareToBusinessDiagnosisWhenMetricSignals(
            AiResolvedQueryIntent qi, AiQuerySemanticParseResult sem) {
        if (qi == null || sem == null || sem.getMetric() == null) {
            return;
        }
        if (!AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW.equals(qi.getPathCode())) {
            return;
        }
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(qi.getStructuredIntentDetail());
        if (!AiQuerySemanticLexicon.STRUCTURED_BUSINESS_STORE_STATUS_COMPARE.equals(canon)) {
            return;
        }
        if (!metricPrimarySignalsBusinessCompareDiagnosis(sem.getMetric().getPrimaryMetric())) {
            return;
        }
        qi.setIntentCode(AiResolvedQueryIntent.BUSINESS_DIAGNOSIS);
        qi.setPathCode(AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS);
        qi.setTopic("经营诊断");
        qi.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_BUSINESS_STORE_COMPARE_DIAGNOSIS);
    }

    /** 已为经营诊断专线且点名 ≥2 店时补上「对比归因」结构化 wire（结构化字段与 scope，不读自由话术）。 */
    private static void applyBusinessDiagnosisStructuredCompareWhenMultiStore(
            AiResolvedQueryIntent qi, AiQuerySemanticParseResult sem) {
        if (qi == null || sem == null) {
            return;
        }
        if (!AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS.equals(qi.getPathCode())) {
            return;
        }
        if (sem.effectiveMentionedStoreNames().size() < 2) {
            return;
        }
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(qi.getStructuredIntentDetail());
        boolean blank =
                canon == null
                        || !StringUtils.hasText(canon.trim())
                        || AiQuerySemanticLexicon.STRUCTURED_BUSINESS_DIAGNOSIS_SUMMARY.equals(canon);
        boolean costTone =
                AiQuerySemanticLexicon.STRUCTURED_BUSINESS_COST_PRESSURE_DIAGNOSIS.equals(canon);
        if (!(blank || costTone)) {
            return;
        }
        qi.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_BUSINESS_STORE_COMPARE_DIAGNOSIS);
    }

    private static boolean metricPrimarySignalsBusinessCompareDiagnosis(String primaryMetric) {
        if (!StringUtils.hasText(primaryMetric)) {
            return false;
        }
        String u = primaryMetric.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return "BUSINESS_STATUS_COMPARE_DIAGNOSIS".equals(u)
                || "BUSINESS_STORE_COMPARE_DIAGNOSIS".equals(u)
                || "COMPARE_WITH_REASON".equals(u);
    }

    /**
     * 余量 {@link AiResolvedQueryIntent#PATH_COST_DIAGNOSIS} → 证据型 {@link AiResolvedQueryIntent#PATH_BUSINESS_DIAGNOSIS}
     * （菜品排行 wire 已由 {@link #remapCostDiagnosisToDishProfitWhenDishMetricWire} 校正者除外）。
     */
    private static void remapResidualCostDiagnosisToBusinessEvidenceDiagnosis(AiResolvedQueryIntent qi) {
        if (qi == null) {
            return;
        }
        if (!AiResolvedQueryIntent.PATH_COST_DIAGNOSIS.equals(qi.getPathCode())) {
            return;
        }
        qi.setIntentCode(AiResolvedQueryIntent.BUSINESS_DIAGNOSIS);
        qi.setPathCode(AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS);
        qi.setTopic("经营诊断");
        qi.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_BUSINESS_COST_PRESSURE_DIAGNOSIS);
    }

    /**
     * LLM 误标 {@link AiResolvedQueryIntent#PATH_COST_DIAGNOSIS}，但 metric.rankingType 为菜品毛利 structured wire 时，
     * 校正为菜品专线（仅依赖解析 JSON，不读用户原文）。
     */
    private static void remapCostDiagnosisToDishProfitWhenDishMetricWire(
            AiResolvedQueryIntent qi, AiQuerySemanticParseResult sem) {
        if (qi == null || sem == null || sem.getMetric() == null) {
            return;
        }
        if (!AiResolvedQueryIntent.PATH_COST_DIAGNOSIS.equals(qi.getPathCode())) {
            return;
        }
        String rt = sem.getMetric().getRankingType();
        if (!StringUtils.hasText(rt)) {
            return;
        }
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(rt.trim());
        String wire = canon != null ? canon : rt.trim();
        if (!AiQuerySemanticLexicon.isStructuredDishProfitDetail(wire)) {
            return;
        }
        qi.setIntentCode(AiResolvedQueryIntent.DISH_PROFIT);
        qi.setPathCode(AiResolvedQueryIntent.PATH_DISH_PROFIT);
        qi.setTopic("菜品毛利/利润");
    }

    /**
     * LLM 误标 WAREHOUSE_STOCK_OVERVIEW 但 metric 给出出库排行/门店出库对比 wire 时，校正为出库核销专线（仅依赖 metric.rankingType 规范）。
     */
    private static void remapWarehouseToStockReduceWhenOutboundRankingWire(
            AiResolvedQueryIntent qi,
            AiQuerySemanticParseResult sem) {
        if (qi == null || sem == null || sem.getMetric() == null) {
            return;
        }
        if (!AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK.equals(qi.getPathCode())) {
            return;
        }
        String rt = sem.getMetric().getRankingType();
        if (!StringUtils.hasText(rt)) {
            return;
        }
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(rt.trim());
        String wire = canon != null ? canon : rt.trim();
        if (AiQuerySemanticLexicon.STRUCTURED_GOODS_OUTBOUND_RANKING.equals(wire)
                || AiQuerySemanticLexicon.STRUCTURED_GOODS_OUTBOUND_COUNT_RANKING.equals(wire)
                || AiQuerySemanticLexicon.STRUCTURED_STORE_OUTBOUND_AMOUNT_RANKING.equals(wire)) {
            qi.setIntentCode(AiResolvedQueryIntent.STOCK_REDUCE_QUERY);
            qi.setPathCode(AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY);
            qi.setTopic("出库/核销查询");
        }
    }

    private static AiResolvedTimeWindow timePartToWindow(AiQuerySemanticParseResult.TimePart tp, LocalDate anchor) {
        if (tp == null || anchor == null) {
            return null;
        }
        String label = AiResolvedTimeWindow.normalizeSemanticTimeTypeLabel(tp.getTimeType());
        if (AiResolvedTimeWindow.CUSTOM.equals(label)) {
            LocalDate sd = AiResolvedTimeWindow.parseIsoDateOrNull(tp.getStartDate());
            LocalDate ed = AiResolvedTimeWindow.parseIsoDateOrNull(tp.getEndDate());
            return AiResolvedTimeWindow.fromSemanticCustomRange(sd, ed);
        }
        return AiResolvedTimeWindow.fromSemanticTimeType(label, anchor, null);
    }

    private static AiResolvedQueryIntent copyIntent(AiResolvedQueryIntent in) {
        if (in == null) {
            return AiResolvedQueryIntent.builder().build();
        }
        return AiResolvedQueryIntent.builder()
                .intentCode(in.getIntentCode())
                .pathCode(in.getPathCode())
                .topic(in.getTopic())
                .structuredIntentDetail(in.getStructuredIntentDetail())
                .purchaseSourceType(in.getPurchaseSourceType())
                .inheritedFromPreviousTurn(in.isInheritedFromPreviousTurn())
                .inheritedFromIntentCode(in.getInheritedFromIntentCode())
                .build();
    }

    private record WireIntent(String intentCode, String pathCode, String topic) {
    }

    private static WireIntent mapLlmIntent(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String u = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (u) {
            case "BUSINESS_OVERVIEW", "OPERATIONS_OVERVIEW" -> new WireIntent(
                    AiResolvedQueryIntent.BUSINESS_OVERVIEW,
                    AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW,
                    "经营概览");
            case "REVENUE_OVERVIEW", "REVENUE" -> new WireIntent(
                    AiResolvedQueryIntent.REVENUE_OVERVIEW,
                    AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW,
                    "营业额/营收");
            case "PURCHASE_OVERVIEW", "PROCUREMENT_OVERVIEW", "PURCHASE" -> new WireIntent(
                    AiResolvedQueryIntent.PURCHASE_OVERVIEW,
                    AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW,
                    "采购概览");
            case "WAREHOUSE_STOCK_OVERVIEW", "STOCK_OVERVIEW", "WAREHOUSE_OVERVIEW", "STOCK_QUERY" ->
                    new WireIntent(
                            AiResolvedQueryIntent.WAREHOUSE_STOCK_OVERVIEW,
                            AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK,
                            "库存概览");
            case "STOCK_REDUCE_QUERY", "STOCK_OUT", "WRITE_OFF" -> new WireIntent(
                    AiResolvedQueryIntent.STOCK_REDUCE_QUERY,
                    AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY,
                    "出库/核销查询");
            case "DISH_PROFIT", "DISH_MARGIN" -> new WireIntent(
                    AiResolvedQueryIntent.DISH_PROFIT,
                    AiResolvedQueryIntent.PATH_DISH_PROFIT,
                    "菜品毛利/利润");
            case "DISH_SALES_QUERY" -> new WireIntent(
                    AiResolvedQueryIntent.DISH_SALES_QUERY,
                    AiResolvedQueryIntent.PATH_DISH_SALES_QUERY,
                    "菜品销量/销售额");
            case "COST_DIAGNOSIS", "COST_DIAG" -> new WireIntent(
                    AiResolvedQueryIntent.COST_DIAGNOSIS,
                    AiResolvedQueryIntent.PATH_COST_DIAGNOSIS,
                    "成本诊断");
            case "BUSINESS_DIAGNOSIS" -> new WireIntent(
                    AiResolvedQueryIntent.BUSINESS_DIAGNOSIS,
                    AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS,
                    "经营诊断");
            default -> null;
        };
    }
}
