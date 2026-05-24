package com.nongxinle.ai.semantic;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.semantic.SemanticTimeContractCheck;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import com.nongxinle.ai.semantic.contract.ContractExecutionMappingSupport;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;

/**
 * 将 {@link AiQuerySemanticParseResult} 合并入意图/时间草稿（时间日期镜像 LLM {@code startDate}/{@code endDate}，合同见 {@link SemanticTimeContractCheck}）。
 * Phase1-J 第三批：intent 路由仅读 v2 {@code intent} + semanticSlots；不读用户原文做 Matrix wire 纠偏，不 {@code inheritIntentFromMemory}。
 */
public final class AiQuerySemanticLlmMergeHelper {

    private AiQuerySemanticLlmMergeHelper() {
    }

    public static AiResolvedQueryIntent mergeIntent(
            AiResolvedQueryIntent baselineIntent,
            AiQuerySemanticParseResult sem,
            double minConfidence) {
        return mergeIntent(baselineIntent, sem, minConfidence, null, null);
    }

    public static AiResolvedQueryIntent mergeIntent(
            AiResolvedQueryIntent baselineIntent,
            AiQuerySemanticParseResult sem,
            double minConfidence,
            String normalizedUserMessage) {
        return mergeIntent(baselineIntent, sem, minConfidence, normalizedUserMessage, null);
    }

    /**
     * @param baselineIntent V2-only：通常为 {@link AiResolvedQueryIntent#builder()} 空草稿；不再做 Java 关键词路由。
     * @param previousTurn 非首轮时传入，用于解析 intentAction/timeAction 等多轮语义动作。
     */
    public static AiResolvedQueryIntent mergeIntent(
            AiResolvedQueryIntent baselineIntent,
            AiQuerySemanticParseResult sem,
            double minConfidence,
            String normalizedUserMessage,
            AiConversationTurnMemory previousTurn) {
        AiResolvedQueryIntent base = baselineIntent != null ? copyIntent(baselineIntent) : AiResolvedQueryIntent.builder().build();
        if (sem == null || sem.isParseMissing() || !sem.isStructuralConfidenceOk(minConfidence)) {
            return base;
        }

        String ia = semanticActionNormalize(sem.getIntentAction());
        boolean requestedInheritPrevious =
                "INHERIT_PREVIOUS".equals(ia) && previousTurn != null && StringUtils.hasText(previousTurn.getLastPathCode());
        String prevIntent =
                previousTurn != null && StringUtils.hasText(previousTurn.getLastIntentCode())
                        ? previousTurn.getLastIntentCode()
                        : null;
        WireIntent mappedFromCurrent = mapLlmIntent(sem.getIntent());
        if (mappedFromCurrent == null && StringUtils.hasText(sem.getSemanticDomain())) {
            mappedFromCurrent = mapLlmIntent(sem.getSemanticDomain());
        }
        if (mappedFromCurrent == null && SemanticContractCompletionEngine.isContractLockedParse(sem)) {
            mappedFromCurrent = wireIntentForContractLockedParse(sem);
        }
        if (mappedFromCurrent == null && requestedInheritPrevious && StringUtils.hasText(prevIntent)) {
            mappedFromCurrent = mapLlmIntent(prevIntent);
        }
        if (mappedFromCurrent == null && hasExplicitStockReduceRouteSignal(sem)) {
            mappedFromCurrent =
                    new WireIntent(
                            AiResolvedQueryIntent.STOCK_REDUCE_QUERY,
                            AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY,
                            "出库/核销查询");
        }
        if (mappedFromCurrent == null) {
            return base;
        }
        boolean currentPathDiffersFromPrevious =
                previousTurn != null
                        && StringUtils.hasText(previousTurn.getLastPathCode())
                        && !previousTurn.getLastPathCode().trim().equals(mappedFromCurrent.pathCode());
        boolean intentInherited = requestedInheritPrevious && !currentPathDiffersFromPrevious;
        AiResolvedQueryIntent merged =
                AiResolvedQueryIntent.builder()
                        .intentCode(mappedFromCurrent.intentCode())
                        .pathCode(mappedFromCurrent.pathCode())
                        .topic(mappedFromCurrent.topic())
                        .structuredIntentDetail(base.getStructuredIntentDetail())
                        .purchaseSourceType(base.getPurchaseSourceType())
                        .inheritedFromPreviousTurn(intentInherited)
                        .inheritedFromIntentCode(intentInherited ? prevIntent : null)
                        .build();

        if (SemanticContractCompletionEngine.isContractLockedParse(sem)) {
            // contract-locked：唯一标准 selectedContractId + ACTIVE entry + completed semanticSlots；不读 rankingType/slots 推导 wire。
            applyCompletedContractFieldsToIntent(merged, sem);
            return merged;
        }

        // LEGACY_ONLY — 以下 Matrix / slots→wire 收口不得影响 contract-locked 主链。
        applyCanonicalStructuredIntentDetailWireFromSemanticSlots(merged, sem);
        // applyBusinessOverviewStructuredWireFromSemanticSlots DELETED — BusinessOverview slots→wire cleanup P1
        // applyBusinessDiagnosisStructuredWireFromSemanticSlots DELETED — BusinessDiagnosis slots→wire cleanup P1

        return merged;
    }

    public static boolean mapsToPurchaseOverviewPath(String llmIntent) {
        if (!StringUtils.hasText(llmIntent)) {
            return false;
        }
        String u = llmIntent.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (u) {
            case "PURCHASE_OVERVIEW", "PROCUREMENT_OVERVIEW", "PURCHASE" -> true;
            default -> false;
        };
    }

    /**
     * 本轮 V2 {@code intent} 经 {@link #mapLlmIntent} 已路由到非 {@code purchase_overview_path} 的专线；
     * 采购 {@link PurchaseCurrentSemanticFrameValidator} 门禁不得介入（典型：采购后切回 {@code BUSINESS_OVERVIEW}）。
     */
    public static boolean currentTurnMapsToExplicitNonPurchasePath(AiQuerySemanticParseResult sem) {
        if (sem == null || sem.isParseMissing()) {
            return false;
        }
        WireIntent mapped = mapLlmIntent(sem.getIntent());
        return mapped != null
                && !AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW.equals(mapped.pathCode());
    }

    /**
     * Resolver 采购 frame 校验门禁：仅当本轮 V2 JSON 显式给出采购信号时才进入 {@link PurchaseCurrentSemanticFrameValidator}；
     * 不用 {@code metric.rankingType}、仅凭上一轮 path 或用户话术推断采购域。
     * <p>显式信号：顶层 {@code domain=PURCHASE}、{@code intent} 为采购 overview、编排 {@code purchase_overview}、或
     * {@code semanticSlots.structuredIntentDetailWire} canonical 后为采购 overview wire。</p>
     */
    public static boolean shouldUsePurchaseSemanticFrameAdoption(AiQuerySemanticParseResult sem) {
        if (sem == null || sem.isParseMissing()) {
            return false;
        }
        if (currentTurnMapsToExplicitNonPurchasePath(sem)) {
            return false;
        }
        WireIntent mappedIntentPath = mapLlmIntent(sem.getIntent());
        if (mappedIntentPath != null
                && !AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW.equals(mappedIntentPath.pathCode())) {
            return false;
        }
        if (explicitSemanticDomainPurchase(sem)) {
            return true;
        }
        if (mapsToPurchaseOverviewPath(sem.getIntent())) {
            return true;
        }
        AiQuerySemanticParseResult.OrchestrationDecisionCandidatePart orch = sem.getOrchestrationDecisionCandidate();
        if (orch != null && orch.getSelectedTools() != null) {
            for (String t : orch.getSelectedTools()) {
                if (t != null && "purchase_overview".equalsIgnoreCase(t.trim())) {
                    return true;
                }
            }
        }
        return explicitPurchaseOverviewWireInSemanticSlots(sem);
    }

    private static boolean explicitSemanticDomainPurchase(AiQuerySemanticParseResult sem) {
        if (sem == null || !StringUtils.hasText(sem.getSemanticDomain())) {
            return false;
        }
        String u = sem.getSemanticDomain().trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return "PURCHASE".equals(u);
    }

    /** {@code semanticSlots.structuredIntentDetailWire} canonical 落在采购 overview wire 集合（LLM 显式输出）。 */
    private static boolean explicitPurchaseOverviewWireInSemanticSlots(AiQuerySemanticParseResult sem) {
        if (sem == null || sem.getSemanticSlots() == null) {
            return false;
        }
        String w = sem.getSemanticSlots().getStructuredIntentDetailWire();
        if (!StringUtils.hasText(w)) {
            return false;
        }
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(w.trim());
        return AiQuerySemanticLexicon.isPurchaseOverviewDomainCanonicalWire(canon);
    }

    /**
     * {@code semanticSlots.sourceFacet} 或 slots wire 已明确时，compat {@code metric.purchaseSourceType} 不得再覆盖 {@code qi.purchaseSourceType}。
     */
    public static boolean purchaseSemanticChannelLockedBySlots(AiQuerySemanticParseResult sem) {
        if (sem == null) {
            return false;
        }
        if (AiQuerySemanticSlotMerge.hasPurchaseStructuredIntentWireFromSlots(sem)) {
            return true;
        }
        return semanticSlotsHaveExplicitNonUnknownSourceFacet(sem);
    }

    private static boolean semanticSlotsHaveExplicitNonUnknownSourceFacet(AiQuerySemanticParseResult sem) {
        if (sem == null || sem.getSemanticSlots() == null) {
            return false;
        }
        String sf = sem.getSemanticSlots().getSourceFacet();
        if (!StringUtils.hasText(sf)) {
            return false;
        }
        String u = sf.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return !AiQuerySemanticSlotMerge.UNKNOWN.equals(u);
    }

    private static String purchaseSourceTypeFromSemanticSourceFacet(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String u = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (AiQuerySemanticLexicon.SOURCE_ALL.equals(u) || "ALL".equals(u)) {
            return AiQuerySemanticLexicon.SOURCE_ALL;
        }
        if (AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE.equals(u)) {
            return AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE;
        }
        if (AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE.equals(u)) {
            return AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE;
        }
        return null;
    }

    /** metric.stockReduceType=ALL 表示「全部出库类型」口径，不得覆盖 structured wire。 */
    private static boolean stockReduceTypeIsAllTypesFacetToken(String raw) {
        if (!StringUtils.hasText(raw)) {
            return false;
        }
        String u = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return AiQuerySemanticLexicon.SOURCE_ALL.equals(u) || "ALL".equals(u);
    }

    /**
     * 本句 V2 已明确出库/核销路由信号（intent/path、domain、slots wire 或 stockReduceType facet）。
     * Resolver 用于禁止误入采购 frame 校验；merge 用于禁止 {@code intentAction=INHERIT_PREVIOUS} 钉死上一轮采购 path
     *（典型：采购后「那核销呢？」）。
     */
    public static boolean hasExplicitBusinessOverviewRouteSignal(AiQuerySemanticParseResult sem) {
        if (sem == null) {
            return false;
        }
        WireIntent mapped = mapLlmIntent(sem.getIntent());
        if (mapped != null && AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW.equals(mapped.pathCode())) {
            return true;
        }
        if (StringUtils.hasText(sem.getSemanticDomain())) {
            String d = sem.getSemanticDomain().trim().toUpperCase(Locale.ROOT).replace('-', '_');
            if ("BUSINESS".equals(d) || "BUSINESS_OVERVIEW".equals(d) || "OPERATIONS".equals(d)) {
                return true;
            }
        }
        AiQuerySemanticParseResult.SemanticSlotsPart ss = sem.getSemanticSlots();
        if (ss != null && StringUtils.hasText(ss.getStructuredIntentDetailWire())) {
            String c =
                    AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                            ss.getStructuredIntentDetailWire().trim());
            if (StringUtils.hasText(c)
                    && AiQuerySemanticLexicon.isStructuredBusinessOverviewFourDomainOrchestrationSurface(c)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasExplicitBusinessDiagnosisRouteSignal(AiQuerySemanticParseResult sem) {
        if (sem == null) {
            return false;
        }
        WireIntent mapped = mapLlmIntent(sem.getIntent());
        if (mapped != null && AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS.equals(mapped.pathCode())) {
            return true;
        }
        if (StringUtils.hasText(sem.getSemanticDomain())) {
            String d = sem.getSemanticDomain().trim().toUpperCase(Locale.ROOT).replace('-', '_');
            if ("BUSINESS_DIAGNOSIS".equals(d)) {
                return true;
            }
        }
        AiQuerySemanticParseResult.SemanticSlotsPart ss = sem.getSemanticSlots();
        if (ss != null && StringUtils.hasText(ss.getStructuredIntentDetailWire())) {
            String c =
                    AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                            ss.getStructuredIntentDetailWire().trim());
            if (StringUtils.hasText(c) && AiQuerySemanticLexicon.isStructuredBusinessDiagnosisDetail(c)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasExplicitRevenueRouteSignal(AiQuerySemanticParseResult sem) {
        if (sem == null) {
            return false;
        }
        if (hasExplicitBusinessOverviewRouteSignal(sem) || hasExplicitBusinessDiagnosisRouteSignal(sem)) {
            return false;
        }
        WireIntent mapped = mapLlmIntent(sem.getIntent());
        if (mapped != null && AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW.equals(mapped.pathCode())) {
            return true;
        }
        if (StringUtils.hasText(sem.getSemanticDomain())) {
            String d = sem.getSemanticDomain().trim().toUpperCase(Locale.ROOT).replace('-', '_');
            if ("REVENUE".equals(d)) {
                return true;
            }
        }
        AiQuerySemanticParseResult.SemanticSlotsPart ss = sem.getSemanticSlots();
        if (ss != null && StringUtils.hasText(ss.getStructuredIntentDetailWire())) {
            String c =
                    AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                            ss.getStructuredIntentDetailWire().trim());
            if (StringUtils.hasText(c) && AiQuerySemanticLexicon.isStructuredRevenueDetail(c)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasExplicitWarehouseRouteSignal(AiQuerySemanticParseResult sem) {
        if (sem == null) {
            return false;
        }
        if (hasExplicitStockReduceRouteSignal(sem)) {
            return false;
        }
        if (hasExplicitBusinessOverviewRouteSignal(sem) || hasExplicitBusinessDiagnosisRouteSignal(sem)) {
            return false;
        }
        WireIntent mapped = mapLlmIntent(sem.getIntent());
        if (mapped != null && AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK.equals(mapped.pathCode())) {
            return true;
        }
        if (StringUtils.hasText(sem.getSemanticDomain())) {
            String d = sem.getSemanticDomain().trim().toUpperCase(Locale.ROOT).replace('-', '_');
            if ("WAREHOUSE".equals(d) || "WAREHOUSE_STOCK".equals(d) || "INVENTORY".equals(d)) {
                return true;
            }
        }
        AiQuerySemanticParseResult.SemanticSlotsPart ss = sem.getSemanticSlots();
        if (ss != null && StringUtils.hasText(ss.getStructuredIntentDetailWire())) {
            String c =
                    AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                            ss.getStructuredIntentDetailWire().trim());
            if (StringUtils.hasText(c) && AiQuerySemanticLexicon.isStructuredWarehouseStockDetail(c)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasExplicitDishSalesRouteSignal(AiQuerySemanticParseResult sem) {
        if (sem == null) {
            return false;
        }
        if (hasExplicitStockReduceRouteSignal(sem)) {
            return false;
        }
        if (hasExplicitBusinessOverviewRouteSignal(sem) || hasExplicitBusinessDiagnosisRouteSignal(sem)) {
            return false;
        }
        if (v2MapsToExplicitDishProfitPath(sem)) {
            return false;
        }
        WireIntent mapped = mapLlmIntent(sem.getIntent());
        if (mapped != null && AiResolvedQueryIntent.PATH_DISH_SALES_QUERY.equals(mapped.pathCode())) {
            return true;
        }
        if (StringUtils.hasText(sem.getSemanticDomain())) {
            String d = sem.getSemanticDomain().trim().toUpperCase(Locale.ROOT).replace('-', '_');
            if ("DISH_SALES".equals(d)) {
                return true;
            }
        }
        AiQuerySemanticParseResult.SemanticSlotsPart ss = sem.getSemanticSlots();
        if (ss != null && StringUtils.hasText(ss.getStructuredIntentDetailWire())) {
            String c =
                    AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                            ss.getStructuredIntentDetailWire().trim());
            if (StringUtils.hasText(c) && AiQuerySemanticLexicon.isStructuredDishSalesDetail(c)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasExplicitStockReduceRouteSignal(AiQuerySemanticParseResult sem) {
        if (sem == null) {
            return false;
        }
        if (hasExplicitBusinessOverviewRouteSignal(sem) || hasExplicitBusinessDiagnosisRouteSignal(sem)) {
            return false;
        }
        WireIntent mapped = mapLlmIntent(sem.getIntent());
        if (mapped != null && AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY.equals(mapped.pathCode())) {
            return true;
        }
        if (StringUtils.hasText(sem.getSemanticDomain())) {
            String d = sem.getSemanticDomain().trim().toUpperCase(Locale.ROOT).replace('-', '_');
            if ("STOCK_REDUCE".equals(d)
                    || "STOCK_OUT".equals(d)
                    || "WRITE_OFF".equals(d)) {
                return true;
            }
        }
        AiQuerySemanticParseResult.SemanticSlotsPart ss = sem.getSemanticSlots();
        if (ss != null && StringUtils.hasText(ss.getStructuredIntentDetailWire())) {
            String c = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(ss.getStructuredIntentDetailWire().trim());
            if (StringUtils.hasText(c) && AiQuerySemanticLexicon.isStructuredStockReduceDetail(c)) {
                return true;
            }
        }
        if (sem.getMetric() != null && StringUtils.hasText(sem.getMetric().getStockReduceType())) {
            String raw = sem.getMetric().getStockReduceType().trim();
            if (!stockReduceTypeIsAllTypesFacetToken(raw)) {
                String c =
                        AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(raw);
                if (StringUtils.hasText(c) && AiQuerySemanticLexicon.isStructuredStockReduceDetail(c)) {
                    return true;
                }
            }
        }
        AiQuerySemanticParseResult.OrchestrationDecisionCandidatePart orch = sem.getOrchestrationDecisionCandidate();
        if (orch != null && orch.getSelectedTools() != null) {
            for (String t : orch.getSelectedTools()) {
                if (t != null && "stock_reduce_query".equalsIgnoreCase(t.trim())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 本轮 {@code semanticSlots.structuredIntentDetailWire} 已 canonical 时，落到 {@code queryIntent}，
     * 避免后续 merge 或 {@code intentAction=INHERIT_PREVIOUS} 用空 structured 或上轮形态覆盖（如双域 {@code purchase_stock_reduce_mismatch}）。
     */
    private static void applyCanonicalStructuredIntentDetailWireFromSemanticSlots(
            AiResolvedQueryIntent qi, AiQuerySemanticParseResult sem) {
        if (qi == null || sem == null || SemanticContractCompletionEngine.isContractLockedParse(sem)) {
            return;
        }
        if (!AiQuerySemanticSlotMerge.hasCanonicalStructuredIntentWireFromSlots(sem)) {
            return;
        }
        String canon =
                AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                        sem.getSemanticSlots().getStructuredIntentDetailWire().trim());
        if (!StringUtils.hasText(canon)) {
            return;
        }
        if (AiQuerySemanticLexicon.isStructuredBusinessOverviewFourDomainOrchestrationSurface(canon)
                && !AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW.equals(qi.getPathCode())) {
            return;
        }
        if (AiQuerySemanticLexicon.isStructuredBusinessDiagnosisDetail(canon)
                && !AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS.equals(qi.getPathCode())) {
            return;
        }
        if (AiQuerySemanticLexicon.isStructuredStockReduceDetail(canon)
                && !AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY.equals(qi.getPathCode())) {
            return;
        }
        if (AiQuerySemanticLexicon.isStructuredWarehouseStockDetail(canon)
                && !AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK.equals(qi.getPathCode())) {
            return;
        }
        if (AiQuerySemanticLexicon.isStructuredDishSalesDetail(canon)
                && !AiResolvedQueryIntent.PATH_DISH_SALES_QUERY.equals(qi.getPathCode())) {
            return;
        }
        if (AiQuerySemanticLexicon.isStructuredRevenueDetail(canon)
                && !AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW.equals(qi.getPathCode())) {
            return;
        }
        if (AiQuerySemanticLexicon.isNonOverviewDishProfitStructuredDetail(canon)
                && !AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(qi.getPathCode())) {
            return;
        }
        if (AiQuerySemanticLexicon.isPurchaseOverviewDomainCanonicalWire(canon)
                && !AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW.equals(qi.getPathCode())) {
            return;
        }
        qi.setStructuredIntentDetail(canon);
    }

    /** V2 已明确路由到 {@link AiResolvedQueryIntent#PATH_DISH_PROFIT}（省略毛利追问经 SemanticIntake 补全后由 v2 产出）。 */
    public static boolean v2MapsToExplicitDishProfitPath(AiQuerySemanticParseResult sem) {
        if (sem == null || sem.isParseMissing()) {
            return false;
        }
        WireIntent mapped = mapLlmIntent(sem.getIntent());
        return mapped != null && AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(mapped.pathCode());
    }

    /**
     * @deprecated 保留签名兼容；时间合并仅读 V2 {@code timeAction} / {@code time} 结构化字段。
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
     * @deprecated 保留签名兼容；{@code normalizedUserMessage} / {@code mergedIntentHint} 不参与时间合并。
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
     * 从 V2 LLM {@code time} 块镜像候选时间窗；不做自然语言解析或 Java 语义重判。
     * 最终落地以 {@link SemanticTimeContractCheck} 通过后的结果为准。
     */
    public static AiResolvedTimeWindow mergeTentativeTime(
            AiResolvedTimeWindow tentativeTime,
            AiQuerySemanticParseResult sem,
            LocalDate today,
            double minConfidence,
            String normalizedUserMessage,
            AiResolvedQueryIntent mergedIntentHint,
            AiConversationTurnMemory previousTurn) {
        if (sem == null || sem.isParseMissing() || !sem.isStructuralConfidenceOk(minConfidence)) {
            return tentativeTime;
        }
        AiQuerySemanticParseResult.TimePart tp = sem.getTime();
        if (tp == null) {
            return tentativeTime;
        }
        LocalDate sd = AiResolvedTimeWindow.parseIsoDateOrNull(tp.getStartDate());
        LocalDate ed = AiResolvedTimeWindow.parseIsoDateOrNull(tp.getEndDate());
        if (sd == null || ed == null) {
            return tentativeTime;
        }
        String label = AiResolvedTimeWindow.normalizeSemanticTimeTypeLabel(tp.getTimeType());
        if (!StringUtils.hasText(label)) {
            label = AiResolvedTimeWindow.CUSTOM;
        }
        String src = SemanticTimeContractCheck.normalizeProductionTimeSource(tp.getTimeSource());
        boolean inherited = SemanticTimeContractCheck.SOURCE_INHERITED_PREVIOUS.equals(src);
        boolean explicit = SemanticTimeContractCheck.SOURCE_CURRENT_MESSAGE_EXPLICIT.equals(src);
        return AiResolvedTimeWindow.builder()
                .timeLabel(label)
                .startDate(sd)
                .endDate(ed)
                .displayText(sd + "～" + ed)
                .inheritedFromPreviousTurn(inherited)
                .explicitTimeMentioned(explicit)
                .build();
    }

    /** 语义 LLM intentAction/timeAction… 等大写归一（仅合并层使用）。 */
    private static String semanticActionNormalize(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        return raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
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

    /**
     * P4 contract-locked：path 来自 {@link ContractExecutionMappingSupport}（Catalog execution metadata），
     * 或 completion trace / 合同 completion 遗留 fallback；不解析用户原文。
     */
    private static WireIntent wireIntentForContractLockedParse(AiQuerySemanticParseResult sem) {
        if (sem == null) {
            return null;
        }
        ContractExecutionMappingSupport.Mapping execution = ContractExecutionMappingSupport.resolve(sem);
        if (execution != null && execution.hasRoutableExecution()) {
            return new WireIntent(
                    execution.getIntentCode(), execution.getPathCode(), execution.getTopic());
        }
        Map<String, Object> trace = sem.getContractCompletionTrace();
        if (trace != null) {
            Object domainObj = trace.get("domain");
            if (domainObj instanceof String domainStr && StringUtils.hasText(domainStr)) {
                WireIntent mapped = mapLlmIntent(domainStr);
                if (mapped != null) {
                    return mapped;
                }
            }
        }
        if (explicitPurchaseOverviewWireInSemanticSlots(sem)) {
            return new WireIntent(
                    AiResolvedQueryIntent.PURCHASE_OVERVIEW,
                    AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW,
                    "采购概览");
        }
        String wire =
                StringUtils.hasText(sem.getCurrentTurnStructuredIntentDetailWire())
                        ? sem.getCurrentTurnStructuredIntentDetailWire()
                        : sem.getSemanticSlots() != null
                                ? sem.getSemanticSlots().getStructuredIntentDetailWire()
                                : null;
        if (StringUtils.hasText(wire)) {
            String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wire.trim());
            if (AiQuerySemanticLexicon.isPurchaseOverviewDomainCanonicalWire(canon)) {
                return new WireIntent(
                        AiResolvedQueryIntent.PURCHASE_OVERVIEW,
                        AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW,
                        "采购概览");
            }
        }
        return null;
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

    /** P4-J2：completedParse 已锁定合同；structuredIntentDetail 仅来自 semanticSlots，不走 Matrix 推断。 */
    private static void applyCompletedContractFieldsToIntent(
            AiResolvedQueryIntent qi, AiQuerySemanticParseResult sem) {
        if (qi == null || sem == null || sem.getSemanticSlots() == null) {
            return;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart ss = sem.getSemanticSlots();
        if (StringUtils.hasText(ss.getStructuredIntentDetailWire())) {
            String canon =
                    AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                            ss.getStructuredIntentDetailWire().trim());
            if (StringUtils.hasText(canon)) {
                qi.setStructuredIntentDetail(canon);
            }
        }
        String pstFacet = purchaseSourceTypeFromSemanticSourceFacet(ss.getSourceFacet());
        if (pstFacet != null) {
            qi.setPurchaseSourceType(pstFacet);
        }
    }
}
