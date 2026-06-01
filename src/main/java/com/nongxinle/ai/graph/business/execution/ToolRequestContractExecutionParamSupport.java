package com.nongxinle.ai.graph.business.execution;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.semantic.intake.WarehouseInventoryShortageSemanticsSupport;
import com.nongxinle.ai.semantic.matrix.DishCostAnalysisSemanticCapabilityMatrix;
import com.nongxinle.ai.semantic.matrix.WarehouseSemanticCapabilityMatrix;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import org.springframework.util.StringUtils;

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
     * 菜品 focus hint：contract locked 时来自 {@link EffectiveDishAnchorSupport} 统一 anchor。
     */
    public static String resolveDishNameFocusHint(AiResolvedQueryContext ctx) {
        if (!isContractLocked(ctx)) {
            return null;
        }
        String wire = resolveDishProfitStructuredDetailWire(ctx);
        if (StringUtils.hasText(wire) && AiQuerySemanticLexicon.isDishProfitRankingStructuredDetail(wire)) {
            return null;
        }
        EffectiveDishAnchor anchor = EffectiveDishAnchorSupport.resolve(ctx);
        return StringUtils.hasText(anchor.getDishName()) ? anchor.getDishName().trim() : null;
    }

    /**
     * contract locked 时 foodId：来自 {@link EffectiveDishAnchorSupport}；当前轮显式菜名时不带上一轮 foodId。
     */
    public static Integer resolveDishFoodIdFromContract(AiResolvedQueryContext ctx) {
        if (!isContractLocked(ctx)) {
            return null;
        }
        return EffectiveDishAnchorSupport.resolve(ctx).getFoodId();
    }

    /** contract locked 且 wire 为 {@code dish_cost_analysis} 或 {@code dish_profit_prescription} 时，是否已有菜名或 foodId。 */
    public static boolean hasDishCostAnalysisSelector(AiResolvedQueryContext ctx) {
        if (!isContractLocked(ctx)) {
            return false;
        }
        String wire = resolveContractStructuredIntentDetailWire(ctx);
        if (!AiQuerySemanticLexicon.isDishCostPathStructuredDetail(wire)) {
            return false;
        }
        if (StringUtils.hasText(resolveDishNameFocusHint(ctx))) {
            return true;
        }
        return resolveDishFoodIdFromContract(ctx) != null;
    }

    /** contract locked 且 {@code selectedContractId=warehouse.inventory_risk_list}。 */
    public static boolean isWarehouseInventoryRiskListContract(AiResolvedQueryContext ctx) {
        if (!isContractLocked(ctx)) {
            return false;
        }
        return WarehouseInventoryShortageSemanticsSupport.CONTRACT_INVENTORY_RISK_LIST.equals(
                selectedContractId(ctx));
    }

    /** contract locked 且 {@code selectedContractId=warehouse.goods_supported_dish_cover.v1}。 */
    public static boolean isGoodsSupportedDishCoverContract(AiResolvedQueryContext ctx) {
        if (!isContractLocked(ctx)) {
            return false;
        }
        return WarehouseSemanticCapabilityMatrix.CONTRACT_GOODS_SUPPORTED_DISH_COVER.equals(
                selectedContractId(ctx));
    }

    /** contract locked 且 GOODS 锚：商品名 hint（读 {@link EffectiveGoodsAnchorSupport}）。 */
    public static String resolveGoodsNameFocusHint(AiResolvedQueryContext ctx) {
        if (!isContractLocked(ctx)) {
            return null;
        }
        EffectiveGoodsAnchor anchor = EffectiveGoodsAnchorSupport.resolve(ctx);
        return anchor.hasGoodsName() ? anchor.getGoodsName().trim() : null;
    }

    public static Integer resolveDisGoodsIdFromContract(AiResolvedQueryContext ctx) {
        if (!isContractLocked(ctx)) {
            return null;
        }
        EffectiveGoodsAnchor anchor = EffectiveGoodsAnchorSupport.resolve(ctx);
        return anchor.hasDisGoodsId() ? anchor.getDisGoodsId() : null;
    }

    /** contract locked 且 {@code selectedContractId=dish.ingredient_cover_days.v1}。 */
    public static boolean isDishIngredientCoverDaysContract(AiResolvedQueryContext ctx) {
        if (!isContractLocked(ctx)) {
            return false;
        }
        return DishCostAnalysisSemanticCapabilityMatrix.CONTRACT_DISH_INGREDIENT_COVER_DAYS.equals(
                selectedContractId(ctx));
    }

    /** contract locked 且 {@code selectedContractId=dish.profit.prescription.v1}。 */
    public static boolean isDishProfitPrescriptionContract(AiResolvedQueryContext ctx) {
        if (!isContractLocked(ctx)) {
            return false;
        }
        String contractId = selectedContractId(ctx);
        return DishCostAnalysisSemanticCapabilityMatrix.CONTRACT_DISH_PROFIT_PRESCRIPTION.equals(contractId);
    }

    private static String selectedContractId(AiResolvedQueryContext ctx) {
        if (ctx == null || ctx.getQuerySemanticParse() == null) {
            return null;
        }
        var slots = ctx.getQuerySemanticParse().getSemanticSlots();
        if (slots == null || !StringUtils.hasText(slots.getSelectedContractId())) {
            return null;
        }
        return slots.getSelectedContractId().trim();
    }

    /** contract locked 且 wire 为菜品销量单菜合同时，是否已有菜名或 foodId / DISH anchor。 */
    public static boolean hasDishSalesSingleDishSelector(AiResolvedQueryContext ctx) {
        if (!isContractLocked(ctx)) {
            return false;
        }
        String wire = resolveContractStructuredIntentDetailWire(ctx);
        if (!AiQuerySemanticLexicon.isDishSalesSingleDishStructuredDetail(wire)) {
            return false;
        }
        if (StringUtils.hasText(resolveDishNameFocusHint(ctx))) {
            return true;
        }
        return resolveDishFoodIdFromContract(ctx) != null;
    }

    /** contract locked 且 wire 为菜品销量单菜（含门店单菜）合同。 */
    public static boolean isDishSalesSingleDishContract(AiResolvedQueryContext ctx) {
        if (!isContractLocked(ctx)) {
            return false;
        }
        return AiQuerySemanticLexicon.isDishSalesSingleDishStructuredDetail(
                resolveContractStructuredIntentDetailWire(ctx));
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
