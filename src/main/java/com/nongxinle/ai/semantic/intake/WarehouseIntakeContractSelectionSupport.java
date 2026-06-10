package com.nongxinle.ai.semantic.intake;

import com.nongxinle.ai.semantic.contract.DomainContractSelectionResult;
import org.springframework.util.StringUtils;

/**
 * WAREHOUSE 域 allowed contract catalog 与 canonical {@link SemanticIntakeResult} 对齐的唯一入口。
 * <p>Pre-V2 不得按 raw {@code SUPERVISION_QUERY} 收窄为 WH-I only（Intake LLM 常漏 {@code coverDaysEntityName}，
 * 会在 Adoption 存在性落地后再 canonicalize）。Supervision 收窄仅发生在 canonical intake 已确立的全局监督语义。
 */
public final class WarehouseIntakeContractSelectionSupport {

    private WarehouseIntakeContractSelectionSupport() {}

    /**
     * Resolver → V2 前：仅按已 canonical 的 WH-H / 库房风险窄化；不执行 supervision-only 收窄。
     */
    public static DomainContractSelectionResult applyPreV2WarehouseIntakeFilters(
            DomainContractSelectionResult selection, SemanticIntakeResult intake) {
        if (selection == null || intake == null) {
            return selection;
        }
        if (!SemanticIntakePrimaryDomain.WAREHOUSE.equals(blank(selection.getSelectedDomain()))) {
            return selection;
        }
        selection =
                SemanticIntakeGoodsStockBatchDetailSupport.filterContractSelection(selection, intake);
        selection =
                SemanticIntakeGoodsAnchorInventoryBundleSupport.filterContractSelection(
                        selection, intake);
        selection =
                SemanticIntakeGoodsSupportedDishCoverSupport.filterContractSelection(selection, intake);
        selection =
                WarehouseInventoryShortageSemanticsSupport.filterContractSelection(selection, intake);
        return selection;
    }

    /**
     * Canonical intake 已确立（含 Adoption 存在性落地后）：完整 WAREHOUSE intake 合同窄化。
     */
    public static DomainContractSelectionResult applyCanonicalWarehouseIntakeFilters(
            DomainContractSelectionResult selection, SemanticIntakeResult canonicalIntake) {
        if (selection == null || canonicalIntake == null) {
            return selection;
        }
        if (!SemanticIntakePrimaryDomain.WAREHOUSE.equals(blank(selection.getSelectedDomain()))) {
            return selection;
        }
        selection =
                SemanticIntakeGoodsStockBatchDetailSupport.filterContractSelection(
                        selection, canonicalIntake);
        selection =
                SemanticIntakeGoodsAnchorInventoryBundleSupport.filterContractSelection(
                        selection, canonicalIntake);
        selection =
                SemanticIntakeGoodsSupportedDishCoverSupport.filterContractSelection(
                        selection, canonicalIntake);
        selection =
                WarehouseInventoryShortageSemanticsSupport.filterContractSelection(
                        selection, canonicalIntake);
        selection =
                WarehouseInventorySupervisionSemanticsSupport.filterContractSelection(
                        selection, canonicalIntake);
        return selection;
    }

    private static String blank(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
