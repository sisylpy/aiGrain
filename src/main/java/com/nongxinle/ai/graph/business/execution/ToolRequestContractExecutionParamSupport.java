package com.nongxinle.ai.graph.business.execution;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.identity.BusinessEntityIdentityBridge;
import com.nongxinle.ai.identity.BusinessEntityIdentityGoodsProjection;
import com.nongxinle.ai.identity.EntityIdentityResolutionStatus;
import com.nongxinle.ai.identity.ResolvedEntityIdentity;
import com.nongxinle.ai.semantic.InventoryCoverDaysContractSupport;
import com.nongxinle.ai.semantic.intake.WarehouseInventorySupervisionSemanticsSupport;
import com.nongxinle.ai.semantic.intake.WarehouseInventoryShortageSemanticsSupport;
import com.nongxinle.ai.dto.business.PurchaseGoodsBusinessAnalysisAnswerPlan;
import com.nongxinle.ai.dto.business.GoodsStockBatchDetailAnswerPlan;
import com.nongxinle.ai.dto.business.GoodsSupportedDishCoverAnswerPlan;
import com.nongxinle.ai.semantic.contract.SemanticContractPlanOutputSupport;
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

    /** contract locked 且 {@code selectedContractId=warehouse.near_expiry}。 */
    public static boolean isWarehouseNearExpiryContract(AiResolvedQueryContext ctx) {
        if (!isContractLocked(ctx)) {
            return false;
        }
        return WarehouseInventoryShortageSemanticsSupport.CONTRACT_NEAR_EXPIRY.equals(
                selectedContractId(ctx));
    }

    /** contract locked 且 {@code selectedContractId=warehouse.inventory_supervision.v1}。 */
    public static boolean isWarehouseInventorySupervisionContract(AiResolvedQueryContext ctx) {
        if (!isContractLocked(ctx)) {
            return false;
        }
        return WarehouseInventorySupervisionSemanticsSupport.CONTRACT_INVENTORY_SUPERVISION.equals(
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

    /** contract locked 且 {@code selectedContractId=warehouse.goods_stock_batch_detail.v1}。 */
    public static boolean isGoodsStockBatchDetailContract(AiResolvedQueryContext ctx) {
        if (!isContractLocked(ctx)) {
            return false;
        }
        return GoodsStockBatchDetailAnswerPlan.CONTRACT_ID.equals(selectedContractId(ctx));
    }

    /** GOODS 锚点库房库存 Tool（planOutputs 含 cover 或 batch 时启用）。 */
    public static boolean isWarehouseGoodsAnchorInventoryToolContract(AiResolvedQueryContext ctx) {
        return SemanticContractPlanOutputSupport.requestsPlanOutput(
                        ctx, GoodsSupportedDishCoverAnswerPlan.TYPE)
                || SemanticContractPlanOutputSupport.requestsPlanOutput(
                        ctx, GoodsStockBatchDetailAnswerPlan.TYPE);
    }

    /** contract locked 且 {@code selectedContractId=purchase.goods_business_analysis.v1}。 */
    public static boolean isPurchaseGoodsBusinessAnalysisContract(AiResolvedQueryContext ctx) {
        if (!isContractLocked(ctx)) {
            return false;
        }
        return PurchaseGoodsBusinessAnalysisAnswerPlan.CONTRACT_ID.equals(selectedContractId(ctx));
    }

    /** contract locked 且 GOODS 锚：商品名 hint（读 Identity Resolver 执行投影）。 */
    public static String resolveGoodsNameFocusHint(AiResolvedQueryContext ctx) {
        if (!isContractLocked(ctx)) {
            return null;
        }
        ResolvedEntityIdentity identity = BusinessEntityIdentityBridge.resolveGoods(ctx);
        return BusinessEntityIdentityGoodsProjection.executionGoodsNameHint(identity);
    }

    public static Integer resolveDisGoodsIdFromContract(AiResolvedQueryContext ctx) {
        if (!isContractLocked(ctx)) {
            return null;
        }
        ResolvedEntityIdentity identity = BusinessEntityIdentityBridge.resolveGoods(ctx);
        if (identity.getResolutionStatus() != EntityIdentityResolutionStatus.OK) {
            return null;
        }
        return BusinessEntityIdentityGoodsProjection.executionDisGoodsId(identity);
    }

    /** contract locked 且 {@code selectedContractId=dish.ingredient_cover_days.v1}。 */
    public static boolean isDishIngredientCoverDaysContract(AiResolvedQueryContext ctx) {
        if (!isContractLocked(ctx)) {
            return false;
        }
        return DishCostAnalysisSemanticCapabilityMatrix.CONTRACT_DISH_INGREDIENT_COVER_DAYS.equals(
                selectedContractId(ctx));
    }

    /**
     * 库存支撑天数类能力：当前库存快照 + 独立 salesBaselineWindow（goods 反查关联菜 / 单菜配料 / WH-K bundle cover 子计划）。
     */
    public static boolean isInventoryCoverDaysCapability(AiResolvedQueryContext ctx) {
        if (!isContractLocked(ctx)) {
            return false;
        }
        return InventoryCoverDaysContractSupport.isInventoryCoverDaysContractId(selectedContractId(ctx));
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
