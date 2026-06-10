package com.nongxinle.ai.semantic.intake.grounding;

import com.nongxinle.ai.dto.business.PurchaseGoodsBusinessAnalysisAnswerPlan;
import com.nongxinle.ai.semantic.matrix.DishCostAnalysisSemanticCapabilityMatrix;
import com.nongxinle.ai.semantic.matrix.WarehouseSemanticCapabilityMatrix;
import com.nongxinle.ai.semantic.intake.SemanticIntakePrimaryDomain;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 实体落地族 SSOT：trigger contractId → family；peer 合同仅来自 matrix 常量，不读用户原文。
 */
@Component
public class EntityGroundingFamilyRegistry {

    public static final String CONTRACT_DISH_SALES_SINGLE_DISH = "dish_sales.single_dish";
    public static final String CONTRACT_DISH_SALES_STORE_SINGLE_DISH = "dish_sales.store_single_dish";

    private final Map<String, FamilyEntry> byTriggerContract = buildIndex();

    public Optional<FamilyEntry> findByTriggerContract(String contractId) {
        if (!StringUtils.hasText(contractId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(byTriggerContract.get(contractId.trim()));
    }

    public FamilyEntry coverDaysEntry() {
        return byTriggerContract.get(DishCostAnalysisSemanticCapabilityMatrix.CONTRACT_DISH_INGREDIENT_COVER_DAYS);
    }

    /** 族规格：peer 切换或澄清策略由 {@link EntityGroundingFamily} 决定。 */
    public record FamilyEntry(
            EntityGroundingFamily family,
            String triggerContractId,
            String dishPeerContractId,
            String dishPeerDomain,
            String goodsPeerContractId,
            String goodsPeerDomain,
            boolean autoSwitchPeerContracts) {

        static FamilyEntry coverDays(String triggerContractId) {
            return coverDaysWithGoodsPeer(
                    triggerContractId,
                    WarehouseSemanticCapabilityMatrix.CONTRACT_GOODS_SUPPORTED_DISH_COVER);
        }

        static FamilyEntry coverDaysWithGoodsPeer(
                String triggerContractId, String goodsPeerContractId) {
            return new FamilyEntry(
                    EntityGroundingFamily.COVER_DAYS,
                    triggerContractId,
                    DishCostAnalysisSemanticCapabilityMatrix.CONTRACT_DISH_INGREDIENT_COVER_DAYS,
                    SemanticIntakePrimaryDomain.DISH_COST,
                    goodsPeerContractId,
                    SemanticIntakePrimaryDomain.WAREHOUSE,
                    true);
        }

        static FamilyEntry namedSales(String triggerContractId) {
            return new FamilyEntry(
                    EntityGroundingFamily.NAMED_SALES,
                    triggerContractId,
                    triggerContractId,
                    SemanticIntakePrimaryDomain.DISH_SALES,
                    null,
                    null,
                    false);
        }

        static FamilyEntry purchaseGoodsBiz() {
            return new FamilyEntry(
                    EntityGroundingFamily.PURCHASE_GOODS_BIZ,
                    PurchaseGoodsBusinessAnalysisAnswerPlan.CONTRACT_ID,
                    null,
                    null,
                    PurchaseGoodsBusinessAnalysisAnswerPlan.CONTRACT_ID,
                    SemanticIntakePrimaryDomain.PURCHASE,
                    false);
        }
    }

    private static Map<String, FamilyEntry> buildIndex() {
        Map<String, FamilyEntry> index = new LinkedHashMap<>();
        index.put(
                DishCostAnalysisSemanticCapabilityMatrix.CONTRACT_DISH_INGREDIENT_COVER_DAYS,
                FamilyEntry.coverDays(
                        DishCostAnalysisSemanticCapabilityMatrix.CONTRACT_DISH_INGREDIENT_COVER_DAYS));
        index.put(
                WarehouseSemanticCapabilityMatrix.CONTRACT_GOODS_SUPPORTED_DISH_COVER,
                FamilyEntry.coverDaysWithGoodsPeer(
                        WarehouseSemanticCapabilityMatrix.CONTRACT_GOODS_SUPPORTED_DISH_COVER,
                        WarehouseSemanticCapabilityMatrix.CONTRACT_GOODS_SUPPORTED_DISH_COVER));
        index.put(
                WarehouseSemanticCapabilityMatrix.CONTRACT_GOODS_ANCHOR_INVENTORY_BUNDLE,
                FamilyEntry.coverDaysWithGoodsPeer(
                        WarehouseSemanticCapabilityMatrix.CONTRACT_GOODS_ANCHOR_INVENTORY_BUNDLE,
                        WarehouseSemanticCapabilityMatrix.CONTRACT_GOODS_ANCHOR_INVENTORY_BUNDLE));
        index.put(
                WarehouseSemanticCapabilityMatrix.CONTRACT_GOODS_STOCK_BATCH_DETAIL,
                FamilyEntry.coverDaysWithGoodsPeer(
                        WarehouseSemanticCapabilityMatrix.CONTRACT_GOODS_STOCK_BATCH_DETAIL,
                        WarehouseSemanticCapabilityMatrix.CONTRACT_GOODS_STOCK_BATCH_DETAIL));
        index.put(CONTRACT_DISH_SALES_SINGLE_DISH, FamilyEntry.namedSales(CONTRACT_DISH_SALES_SINGLE_DISH));
        index.put(
                CONTRACT_DISH_SALES_STORE_SINGLE_DISH,
                FamilyEntry.namedSales(CONTRACT_DISH_SALES_STORE_SINGLE_DISH));
        index.put(
                PurchaseGoodsBusinessAnalysisAnswerPlan.CONTRACT_ID,
                FamilyEntry.purchaseGoodsBiz());
        return Map.copyOf(index);
    }
}
