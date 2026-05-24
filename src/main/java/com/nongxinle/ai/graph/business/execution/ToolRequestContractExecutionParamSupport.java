package com.nongxinle.ai.graph.business.execution;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * P2-K：contract-locked Tool Request 执行参数收口（采购 sourceFocus、菜品 structured wire / dish focus）。
 * 只读 {@link AiQuerySemanticParseResult#getSemanticSlots()} 与 completed parse / 结构化 anchor；不读 raw queryIntent 自由字段。
 */
public final class ToolRequestContractExecutionParamSupport {

    public static final String PARAM_SOURCE_CONTRACT_ENTRY = "contract_entry";
    public static final String PARAM_SOURCE_UNRESOLVED = "unresolved";

    private ToolRequestContractExecutionParamSupport() {}

    /**
     * 采购 {@code purchaseSourceFocus}：contract locked 时仅来自 {@code semanticSlots.sourceFacet}。
     * 未 locked 或 facet 不可映射时返回 null（不写 Tool arg，等同 ALL / clarification-safe）。
     */
    public static String resolvePurchaseSourceFocus(AiResolvedQueryContext ctx) {
        if (!isContractLocked(ctx)) {
            return null;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart slots = semanticSlots(ctx);
        if (slots == null) {
            return null;
        }
        return purchaseSourceTypeFromSourceFacet(slots.getSourceFacet());
    }

    /**
     * contract locked 时 canonical {@code structuredIntentDetailWire}（跨域通用，仅读 semanticSlots）。
     */
    public static String resolveContractStructuredIntentDetailWire(AiResolvedQueryContext ctx) {
        if (!isContractLocked(ctx)) {
            return null;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart slots = semanticSlots(ctx);
        if (slots == null || !StringUtils.hasText(slots.getStructuredIntentDetailWire())) {
            return null;
        }
        return AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(slots.getStructuredIntentDetailWire().trim());
    }

    /**
     * 菜品毛利 presentation wire：contract locked 时仅来自 {@code semanticSlots.structuredIntentDetailWire} canonical。
     */
    public static String resolveDishProfitStructuredDetailWire(AiResolvedQueryContext ctx) {
        return resolveContractStructuredIntentDetailWire(ctx);
    }

    /**
     * 菜品 focus hint：contract locked 且非排行类 wire 时，来自 completed parse / 结构化 anchor。
     * 未 locked 或无法确定时返回 null。
     */
    public static String resolveDishNameFocusHint(AiResolvedQueryContext ctx) {
        if (!isContractLocked(ctx)) {
            return null;
        }
        String wire = resolveDishProfitStructuredDetailWire(ctx);
        if (StringUtils.hasText(wire) && AiQuerySemanticLexicon.isDishProfitRankingStructuredDetail(wire)) {
            return null;
        }
        AiQuerySemanticParseResult sem = ctx.getQuerySemanticParse();
        if (sem != null && StringUtils.hasText(sem.getMentionedDishName())) {
            String fromParse =
                    AiQuerySemanticLexicon.finalizeMentionedDishNameForDishProfit(sem.getMentionedDishName().trim());
            if (StringUtils.hasText(fromParse)) {
                return fromParse;
            }
        }
        String fromAnchor = dishNameFromStructuredDishAnchors(ctx);
        if (StringUtils.hasText(fromAnchor)) {
            return fromAnchor;
        }
        if (StringUtils.hasText(ctx.getRewriteInheritedAnchorName())) {
            String inherited =
                    AiQuerySemanticLexicon.finalizeMentionedDishNameForDishProfit(
                            ctx.getRewriteInheritedAnchorName().trim());
            if (StringUtils.hasText(inherited)) {
                return inherited;
            }
        }
        if (StringUtils.hasText(ctx.getMentionedDishName())) {
            return ctx.getMentionedDishName().trim();
        }
        return null;
    }

    /**
     * contract locked 时 dishProfitMetricType：由 {@code semanticSlots.structuredIntentDetailWire} canonical 映射；
     * 不读 {@link com.nongxinle.ai.context.AiResolvedQueryContext#getDishProfitMetricType()} 或 queryIntent wire 推导残留。
     */
    public static String resolveDishProfitMetricType(AiResolvedQueryContext ctx) {
        if (!isContractLocked(ctx)) {
            return null;
        }
        return com.nongxinle.ai.conversation.AiQuerySemanticLexicon.dishProfitMetricTypeFromStructuredWire(
                resolveDishProfitStructuredDetailWire(ctx));
    }

    private static String dishNameFromStructuredDishAnchors(AiResolvedQueryContext ctx) {
        if (ctx == null || ctx.getPreviousTurn() == null) {
            return null;
        }
        List<AiResultAnchor> anchors = ctx.getPreviousTurn().getLastResultAnchors();
        if (anchors == null || anchors.isEmpty()) {
            return null;
        }
        for (AiResultAnchor a : anchors) {
            if (a == null || !StringUtils.hasText(a.getEntityType())) {
                continue;
            }
            if (!AiResultAnchor.ENTITY_TYPE_DISH.equalsIgnoreCase(a.getEntityType().trim())) {
                continue;
            }
            if (StringUtils.hasText(a.getEntityName())) {
                return AiQuerySemanticLexicon.finalizeMentionedDishNameForDishProfit(a.getEntityName().trim());
            }
        }
        return null;
    }

    private static boolean isContractLocked(AiResolvedQueryContext ctx) {
        return ctx != null
                && SemanticContractCompletionEngine.isContractLockedParse(ctx.getQuerySemanticParse());
    }

    private static AiQuerySemanticParseResult.SemanticSlotsPart semanticSlots(AiResolvedQueryContext ctx) {
        if (ctx == null || ctx.getQuerySemanticParse() == null) {
            return null;
        }
        return ctx.getQuerySemanticParse().getSemanticSlots();
    }

    private static String purchaseSourceTypeFromSourceFacet(String raw) {
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
}
