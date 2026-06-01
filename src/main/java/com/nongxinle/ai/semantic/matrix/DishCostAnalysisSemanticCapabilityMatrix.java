package com.nongxinle.ai.semantic.matrix;

import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 菜品成本+销售单菜分析能力矩阵（P1：成本卡 + 利润处方卡）。 */
public final class DishCostAnalysisSemanticCapabilityMatrix {

    public static final String MATRIX_WIRE_MISSING = "DISH_COST_ANALYSIS_WIRE_NOT_IN_MATRIX";

    public static final String CONTRACT_DISH_COST_SINGLE = "dish_cost.single_dish_analysis";
    public static final String CONTRACT_DISH_PROFIT_PRESCRIPTION = "dish.profit.prescription.v1";
    public static final String CONTRACT_DISH_INGREDIENT_COVER_DAYS = "dish.ingredient_cover_days.v1";

    public static final DishCostAnalysisSemanticCapabilityMatrixRow SINGLE_DISH_ANALYSIS =
            DishCostAnalysisSemanticCapabilityMatrixRow.builder()
                    .rowId("DC-A")
                    .capabilityId(CONTRACT_DISH_COST_SINGLE)
                    .queryObject("DISH")
                    .operation("DETAIL")
                    .metric("COST_SALES_ANALYSIS")
                    .structuredIntentDetailWire(AiQuerySemanticLexicon.STRUCTURED_DISH_COST_ANALYSIS)
                    .targetAnswerPlanType("DISH_COST_ANALYSIS")
                    .build();

    public static final DishCostAnalysisSemanticCapabilityMatrixRow DISH_PROFIT_PRESCRIPTION =
            DishCostAnalysisSemanticCapabilityMatrixRow.builder()
                    .rowId("DC-B")
                    .capabilityId(CONTRACT_DISH_PROFIT_PRESCRIPTION)
                    .queryObject("DISH")
                    .operation("RECOMMENDATION")
                    .metric("PROFIT_PRESCRIPTION")
                    .structuredIntentDetailWire(AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_PRESCRIPTION)
                    .targetAnswerPlanType("DISH_PROFIT_PRESCRIPTION")
                    .build();

    public static final DishCostAnalysisSemanticCapabilityMatrixRow DISH_INGREDIENT_COVER_DAYS =
            DishCostAnalysisSemanticCapabilityMatrixRow.builder()
                    .rowId("DC-C")
                    .capabilityId(CONTRACT_DISH_INGREDIENT_COVER_DAYS)
                    .queryObject("DISH")
                    .operation("DETAIL")
                    .metric("INGREDIENT_COVER_DAYS")
                    .structuredIntentDetailWire(AiQuerySemanticLexicon.STRUCTURED_DISH_INGREDIENT_COVER_DAYS)
                    .targetAnswerPlanType("DISH_INGREDIENT_COVER_DAYS")
                    .build();

    private static final List<DishCostAnalysisSemanticCapabilityMatrixRow> FIRST_TURN_ROWS =
            List.of(SINGLE_DISH_ANALYSIS, DISH_PROFIT_PRESCRIPTION, DISH_INGREDIENT_COVER_DAYS);

    private static final Map<String, DishCostAnalysisSemanticCapabilityMatrixRow> BY_WIRE = buildWireIndex();

    private DishCostAnalysisSemanticCapabilityMatrix() {}

    public static List<DishCostAnalysisSemanticCapabilityMatrixRow> firstTurnRows() {
        return FIRST_TURN_ROWS;
    }

    public static DishCostAnalysisSemanticCapabilityMatrixRow findByContractId(String contractId) {
        if (contractId == null || contractId.isBlank()) {
            return null;
        }
        String id = contractId.trim();
        for (DishCostAnalysisSemanticCapabilityMatrixRow row : FIRST_TURN_ROWS) {
            if (row != null && id.equals(row.getCapabilityId())) {
                return row;
            }
        }
        return null;
    }

    public static DishCostAnalysisSemanticCapabilityMatrixRow findFirstTurnRowByWire(String wire) {
        if (!StringUtils.hasText(wire)) {
            return null;
        }
        return BY_WIRE.get(AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wire.trim()));
    }

    private static Map<String, DishCostAnalysisSemanticCapabilityMatrixRow> buildWireIndex() {
        Map<String, DishCostAnalysisSemanticCapabilityMatrixRow> index = new LinkedHashMap<>();
        for (DishCostAnalysisSemanticCapabilityMatrixRow row : firstTurnRows()) {
            if (row != null && StringUtils.hasText(row.getStructuredIntentDetailWire())) {
                index.put(row.getStructuredIntentDetailWire(), row);
            }
        }
        return Map.copyOf(index);
    }
}
