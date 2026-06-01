package com.nongxinle.ai.semantic.intake;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import com.nongxinle.ai.semantic.intake.llm.LlmSemanticIntakeParsed;
import com.nongxinle.ai.semantic.matrix.WarehouseSemanticCapabilityMatrix;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * Intake 后处理：原料 → 受影响菜品（{@code warehouse.goods_supported_dish_cover.v1}）与
 * 单菜配料可支撑天数 / 库房风险 / 金额排行边界。
 */
public final class SemanticIntakeGoodsSupportedDishCoverSupport {

    public static final String REASON_MARKER = "goods_supported_dish_cover";

    private SemanticIntakeGoodsSupportedDishCoverSupport() {}

    public static boolean reasonDeclaresGoodsSupportedDishCover(String reason) {
        if (!StringUtils.hasText(reason)) {
            return false;
        }
        String n = reason.trim().toLowerCase(Locale.ROOT);
        return n.contains(REASON_MARKER) || n.contains("goods_supported_dish");
    }

    public static boolean parsedDeclaresGoodsSupportedDishCover(LlmSemanticIntakeParsed parsed) {
        if (parsed == null) {
            return false;
        }
        if (reasonDeclaresGoodsSupportedDishCover(parsed.getReason())) {
            return true;
        }
        String primary = SemanticIntakePrimaryDomain.normalize(parsed.getPrimaryDomain());
        return SemanticIntakePrimaryDomain.WAREHOUSE.equals(primary)
                && parsed.getCandidateDomains() != null
                && parsed.getCandidateDomains().stream()
                        .anyMatch(d -> AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK.equalsIgnoreCase(String.valueOf(d)))
                && reasonDeclaresGoodsSupportedDishCover(parsed.getReason());
    }

    public static boolean intakeDeclaresGoodsSupportedDishCover(SemanticIntakeResult intake) {
        if (intake == null) {
            return false;
        }
        return reasonDeclaresGoodsSupportedDishCover(intake.getReason());
    }

    public static boolean mustNotApplyWarehouseInventoryShortagePipeline(
            SemanticIntakeResult intake, AiQuerySemanticParseResult completedParse) {
        if (intakeDeclaresGoodsSupportedDishCover(intake)) {
            return true;
        }
        if (completedParse == null) {
            return false;
        }
        String selected = SemanticContractCompletionEngine.extractSelectedContractId(completedParse);
        return WarehouseSemanticCapabilityMatrix.CONTRACT_GOODS_SUPPORTED_DISH_COVER.equals(
                blank(selected));
    }

    public static boolean mustNotApplyDishIngredientCoverPipeline(LlmSemanticIntakeParsed parsed) {
        return parsedDeclaresGoodsSupportedDishCover(parsed);
    }

    public static void collectGoodsSupportedDishCoverProtocolErrors(
            LlmSemanticIntakeParsed parsed, List<String> errors) {
        if (parsed == null || errors == null || !parsedDeclaresGoodsSupportedDishCover(parsed)) {
            return;
        }
        if (SemanticIntakeDishIngredientCoverDaysSupport.parsedDeclaresDishIngredientCoverDays(parsed)) {
            errors.add(
                    "goods_supported_dish_cover: cannot combine with dish_ingredient_cover_days (§34b)");
        }
        if (WarehouseInventoryShortageSemanticsSupport.parsedDeclaresInventoryRisk(parsed)) {
            errors.add(
                    "goods_supported_dish_cover: warehouseInventorySemantics must be empty (§34b)");
        }
        String primary = SemanticIntakePrimaryDomain.normalize(parsed.getPrimaryDomain());
        if (SemanticIntakePrimaryDomain.DISH_COST.equals(primary)
                || SemanticIntakePrimaryDomain.DISH_PROFIT.equals(primary)) {
            errors.add(
                    "goods_supported_dish_cover: primaryDomain must be WAREHOUSE, not dish domain (§34b)");
        }
    }

    private static String blank(String s) {
        return s == null ? null : s.trim();
    }
}
