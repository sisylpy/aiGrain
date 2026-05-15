package com.nongxinle.ai.planner;

import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.BusinessDiagnosisCompositeAnswerPlan;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * C-53：经营诊断 Composite 生产入口 Gate — <b>只读</b>结构化字段，不接 Master、不跑 PlannerExecutor、不执行 Tool、不调 LLM、不改 {@link AiRunState}。
 *
 * <p>判定规则对齐 {@code docs/ai/business-diagnosis-production-gate-design.md} C-52 / C-52.1。</p>
 *
 * @see BusinessDiagnosisCompositeGateResult
 * @see BusinessDiagnosisCompositeGateReasonCode
 */
public final class BusinessDiagnosisCompositeProductionGate {

    private BusinessDiagnosisCompositeProductionGate() {
    }

    /**
     * @param resolvedQueryContext 解析上下文（必填语义来自 Resolver；不得为 null 方可继续判定）
     * @param runState             可选；仅读取 {@link AiRunState#isNeedClarification()} 等，不修改
     * @param compositeProductionEnabled 功能开关；<b>默认应由调用方传 {@code false}</b>，为 false 时一律拒绝
     */
    public static BusinessDiagnosisCompositeGateResult evaluate(
            AiResolvedQueryContext resolvedQueryContext,
            AiRunState runState,
            boolean compositeProductionEnabled) {

        Map<String, Object> debug = new LinkedHashMap<>();
        debug.put("gateVersion", "C-53_SKELETON");

        if (!compositeProductionEnabled) {
            return deny(BusinessDiagnosisCompositeGateReasonCode.FEATURE_FLAG_DISABLED,
                    "composite production disabled by feature flag",
                    resolvedQueryContext,
                    debug);
        }

        if (resolvedQueryContext == null) {
            return BusinessDiagnosisCompositeGateResult.builder()
                    .allowed(false)
                    .reasonCode(BusinessDiagnosisCompositeGateReasonCode.MISSING_RESOLVED_CONTEXT)
                    .reason("resolvedQueryContext is null")
                    .recommendedCaseKind(BusinessDiagnosisCompositeGateResult.RecommendedCaseKind.NONE)
                    .debug(debug)
                    .build();
        }

        if (resolvedQueryContext.isNeedSemanticClarification()
                || Boolean.TRUE.equals(resolvedQueryContext.getOrchestrationClarificationRequired())
                || (runState != null && runState.isNeedClarification())) {
            return deny(BusinessDiagnosisCompositeGateReasonCode.CLARIFICATION_REQUIRED,
                    "clarification required before composite",
                    resolvedQueryContext,
                    debug);
        }

        AiResolvedTimeWindow tw = resolvedQueryContext.getTimeWindow();
        if (tw == null || tw.getStartDate() == null || tw.getEndDate() == null) {
            return deny(BusinessDiagnosisCompositeGateReasonCode.MISSING_TIME_WINDOW,
                    "timeWindow startDate/endDate required",
                    resolvedQueryContext,
                    debug);
        }

        String effIntent = trimToNull(resolvedQueryContext.getEffectiveIntentCode());
        String effPath = trimToNull(resolvedQueryContext.getEffectivePathCode());

        if (isBlockedSingleDomainIntent(effIntent, effPath)) {
            return deny(BusinessDiagnosisCompositeGateReasonCode.DOMAIN_SINGLE_INTENT_NOT_COMPOSITE,
                    "single-domain intent/path not eligible for composite",
                    resolvedQueryContext,
                    debug);
        }

        if (StringUtils.hasText(resolvedQueryContext.getMentionedDishName())) {
            return deny(BusinessDiagnosisCompositeGateReasonCode.NAMED_DISH_DEEP_DIVE_NOT_COMPOSITE,
                    "mentionedDishName set",
                    resolvedQueryContext,
                    debug);
        }

        String structuredRaw = resolvedQueryContext.getQueryIntent() != null
                ? resolvedQueryContext.getQueryIntent().getStructuredIntentDetail()
                : null;
        if (isRankingOrDeepDiveStructuredDetail(structuredRaw)) {
            return deny(BusinessDiagnosisCompositeGateReasonCode.RANKING_OR_DEEP_DIVE_NOT_COMPOSITE,
                    "structuredIntentDetail is ranking or deep-dive wire",
                    resolvedQueryContext,
                    debug);
        }

        AiResolvedOrgScope org = resolvedQueryContext.getOrgScope();
        if (org == null || !StringUtils.hasText(org.getScopeType())) {
            return deny(BusinessDiagnosisCompositeGateReasonCode.UNSUPPORTED_SCOPE,
                    "orgScope or scopeType missing",
                    resolvedQueryContext,
                    debug);
        }
        String scopeType = org.getScopeType().trim();

        if (AiResolvedOrgScope.SCOPE_STORE.equals(scopeType)) {
            if (!storeScopeLocatesCurrent(org, debug)) {
                return deny(BusinessDiagnosisCompositeGateReasonCode.STORE_SCOPE_MISSING_ANCHOR,
                        "STORE scope missing anchor or visible store match",
                        resolvedQueryContext,
                        debug);
            }
        } else if (AiResolvedOrgScope.SCOPE_GROUP.equals(scopeType)) {
            int validRoots = countValidVisibleStoreRoots(org.getVisibleStores());
            if (validRoots < 2) {
                return deny(BusinessDiagnosisCompositeGateReasonCode.GROUP_SCOPE_INSUFFICIENT_VISIBLE_STORES,
                        "GROUP scope needs at least 2 visible store roots",
                        resolvedQueryContext,
                        debug);
            }
        } else {
            return deny(BusinessDiagnosisCompositeGateReasonCode.UNSUPPORTED_SCOPE,
                    "scopeType must be STORE or GROUP",
                    resolvedQueryContext,
                    debug);
        }

        String canonicalDetail = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(structuredRaw);

        boolean allowA = AiResolvedQueryIntent.BUSINESS_DIAGNOSIS.equals(effIntent)
                && AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS.equals(effPath)
                && isAllowedBusinessDiagnosisStructured(canonicalDetail);

        boolean orchestrationSignal = orchestrationMultiAgentSignal(resolvedQueryContext);
        boolean overviewStructuredOk = isAllowedBusinessOverviewStructured(canonicalDetail);
        boolean allowB = AiResolvedQueryIntent.BUSINESS_OVERVIEW.equals(effIntent)
                && AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW.equals(effPath)
                && orchestrationSignal
                && overviewStructuredOk;

        if (!allowA && !allowB) {
            return deny(BusinessDiagnosisCompositeGateReasonCode.INTENT_PATH_NOT_WHITELISTED,
                    "intent/path/structured not whitelisted for composite",
                    resolvedQueryContext,
                    debug);
        }

        BusinessDiagnosisCompositeGateReasonCode okCode = AiResolvedOrgScope.SCOPE_GROUP.equals(scopeType)
                ? BusinessDiagnosisCompositeGateReasonCode.ALLOWED_GROUP
                : BusinessDiagnosisCompositeGateReasonCode.ALLOWED_STORE;
        BusinessDiagnosisCompositeGateResult.RecommendedCaseKind kind =
                AiResolvedOrgScope.SCOPE_GROUP.equals(scopeType)
                        ? BusinessDiagnosisCompositeGateResult.RecommendedCaseKind.GROUP
                        : BusinessDiagnosisCompositeGateResult.RecommendedCaseKind.STORE;

        debug.put("whitelistAllowA", allowA);
        debug.put("whitelistAllowB", allowB);
        debug.put("effectiveIntentCode", effIntent);
        debug.put("effectivePathCode", effPath);
        debug.put("canonicalStructuredIntentDetail", canonicalDetail);

        return BusinessDiagnosisCompositeGateResult.builder()
                .allowed(true)
                .reasonCode(okCode)
                .reason(okCode == BusinessDiagnosisCompositeGateReasonCode.ALLOWED_GROUP
                        ? "allowed for GROUP composite"
                        : "allowed for STORE composite")
                .scopeType(scopeType)
                .finalAnswerPlanType(BusinessDiagnosisCompositeAnswerPlan.TYPE_BUSINESS_DIAGNOSIS_COMPOSITE)
                .recommendedCaseKind(kind)
                .debug(debug)
                .build();
    }

    private static boolean orchestrationMultiAgentSignal(AiResolvedQueryContext ctx) {
        String tm = ctx.getOrchestrationTaskMode();
        if (StringUtils.hasText(tm) && "MULTI_AGENT".equalsIgnoreCase(tm.trim())) {
            return true;
        }
        if (Boolean.TRUE.equals(ctx.getOrchestrationMultiAgentRequired())) {
            return true;
        }
        String structured = ctx.getQueryIntent() != null ? ctx.getQueryIntent().getStructuredIntentDetail() : null;
        return AiQuerySemanticLexicon.isStructuredBusinessOverviewFourDomainOrchestrationSurface(structured);
    }

    private static boolean isAllowedBusinessDiagnosisStructured(String canonicalDetail) {
        if (!StringUtils.hasText(canonicalDetail)) {
            return false;
        }
        return AiQuerySemanticLexicon.STRUCTURED_BUSINESS_DIAGNOSIS_SUMMARY.equals(canonicalDetail)
                || AiQuerySemanticLexicon.STRUCTURED_BUSINESS_COST_PRESSURE_DIAGNOSIS.equals(canonicalDetail)
                || AiQuerySemanticLexicon.STRUCTURED_BUSINESS_STORE_COMPARE_DIAGNOSIS.equals(canonicalDetail);
    }

    private static boolean isAllowedBusinessOverviewStructured(String canonicalDetail) {
        if (!StringUtils.hasText(canonicalDetail)) {
            return false;
        }
        return AiQuerySemanticLexicon.STRUCTURED_BUSINESS_OVERVIEW_SUMMARY.equals(canonicalDetail)
                || AiQuerySemanticLexicon.STRUCTURED_BUSINESS_OVERVIEW_STATUS.equals(canonicalDetail)
                || AiQuerySemanticLexicon.STRUCTURED_BUSINESS_STORE_STATUS_COMPARE.equals(canonicalDetail);
    }

    private static boolean isBlockedSingleDomainIntent(String effIntent, String effPath) {
        if (!StringUtils.hasText(effIntent) || !StringUtils.hasText(effPath)) {
            return false;
        }
        if (AiResolvedQueryIntent.REVENUE_OVERVIEW.equals(effIntent)
                && AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW.equals(effPath)) {
            return true;
        }
        if (AiResolvedQueryIntent.PURCHASE_OVERVIEW.equals(effIntent)
                && AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW.equals(effPath)) {
            return true;
        }
        if (AiResolvedQueryIntent.STOCK_REDUCE_QUERY.equals(effIntent)
                && AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY.equals(effPath)) {
            return true;
        }
        if (AiResolvedQueryIntent.DISH_PROFIT.equals(effIntent)
                && AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(effPath)) {
            return true;
        }
        if (AiResolvedQueryIntent.COST_DIAGNOSIS.equals(effIntent)
                && AiResolvedQueryIntent.PATH_COST_DIAGNOSIS.equals(effPath)) {
            return true;
        }
        return AiResolvedQueryIntent.WAREHOUSE_STOCK_OVERVIEW.equals(effIntent)
                && AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK.equals(effPath);
    }

    /**
     * C-52.1 §3.3.4：排行/深挖 wire；仅调用 {@link AiQuerySemanticLexicon} 辅助方法与公开常量比较。
     */
    private static boolean isRankingOrDeepDiveStructuredDetail(String structuredIntentDetail) {
        if (!StringUtils.hasText(structuredIntentDetail)) {
            return false;
        }
        String c = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(structuredIntentDetail);
        if (!StringUtils.hasText(c)) {
            return false;
        }
        if (AiQuerySemanticLexicon.isDishProfitRankingStructuredDetail(structuredIntentDetail)) {
            return true;
        }
        if (AiQuerySemanticLexicon.isSingleDishMetricOrReasonStructuredDetail(structuredIntentDetail)) {
            return true;
        }
        if (AiQuerySemanticLexicon.isStorePriorityRankingStructuredDetail(structuredIntentDetail)) {
            return true;
        }
        if (AiQuerySemanticLexicon.isSupplierAmountRankingDetail(structuredIntentDetail)) {
            return true;
        }
        if (AiQuerySemanticLexicon.isNonOverviewStockReduceStructuredDetail(structuredIntentDetail)) {
            return true;
        }
        if (AiQuerySemanticLexicon.isNonOverviewDishProfitStructuredDetail(structuredIntentDetail)) {
            return true;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_AMOUNT_RANKING.equals(c)
                || AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_COUNT_RANKING.equals(c)
                || AiQuerySemanticLexicon.STRUCTURED_PURCHASE_STORE_AMOUNT_RANKING.equals(c)) {
            return true;
        }
        return AiQuerySemanticLexicon.STRUCTURED_REVENUE_PLATFORM_RANKING.equals(c)
                || AiQuerySemanticLexicon.STRUCTURED_REVENUE_DAILY_AMOUNT_RANKING.equals(c)
                || AiQuerySemanticLexicon.STRUCTURED_REVENUE_STORE_AMOUNT_RANKING.equals(c)
                || AiQuerySemanticLexicon.STRUCTURED_REVENUE_CHANNEL_BREAKDOWN.equals(c);
    }

    private static boolean storeScopeLocatesCurrent(AiResolvedOrgScope org, Map<String, Object> debug) {
        Long anchor = org.getCurrentStoreDepartmentId() != null
                ? org.getCurrentStoreDepartmentId()
                : org.getRequestDepartmentId();
        if (anchor == null) {
            debug.put("storeGate", "missing_anchor");
            return false;
        }
        List<AiStoreScopeDTO> stores = org.getVisibleStores();
        if (stores == null || stores.isEmpty()) {
            debug.put("storeGate", "no_visible_stores");
            return false;
        }
        for (AiStoreScopeDTO s : stores) {
            if (s == null || s.getStoreDepartmentId() == null) {
                continue;
            }
            if (anchor.equals(s.getStoreDepartmentId())) {
                debug.put("storeGate", "matched_visible_store");
                return true;
            }
        }
        debug.put("storeGate", "anchor_not_in_visible_stores");
        return false;
    }

    private static int countValidVisibleStoreRoots(List<AiStoreScopeDTO> stores) {
        if (stores == null || stores.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (AiStoreScopeDTO s : stores) {
            if (s != null && s.getStoreDepartmentId() != null) {
                n++;
            }
        }
        return n;
    }

    private static String trimToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }

    private static BusinessDiagnosisCompositeGateResult deny(
            BusinessDiagnosisCompositeGateReasonCode code,
            String reason,
            AiResolvedQueryContext ctx,
            Map<String, Object> debug) {

        String scope = ctx != null && ctx.getOrgScope() != null ? ctx.getOrgScope().getScopeType() : null;
        if (ctx != null) {
            debug.put("effectiveIntentCode", ctx.getEffectiveIntentCode());
            debug.put("effectivePathCode", ctx.getEffectivePathCode());
        }

        return BusinessDiagnosisCompositeGateResult.builder()
                .allowed(false)
                .reasonCode(code)
                .reason(reason)
                .scopeType(scope)
                .finalAnswerPlanType(null)
                .recommendedCaseKind(BusinessDiagnosisCompositeGateResult.RecommendedCaseKind.NONE)
                .debug(debug)
                .build();
    }
}
