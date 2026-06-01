package com.nongxinle.ai.semantic.matrix;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import lombok.experimental.UtilityClass;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * MenuOperation 能力矩阵：Java 仅查表，不做用户原话推断。
 */
@UtilityClass
public final class MenuOperationSemanticCapabilityMatrix {

    public static final String MATRIX_WIRE_MISSING = "MATRIX_WIRE_MISSING";
    public static final String MENU_FACET_OVERVIEW = "OVERVIEW";
    public static final String MENU_FACET_HIGH_SALES_LOW_PROFIT = "HIGH_SALES_LOW_PROFIT";
    public static final String MENU_FACET_ACTION_RECOMMENDATION = "ACTION_RECOMMENDATION";

    public static final MenuOperationSemanticCapabilityMatrixRow OVERVIEW =
            row(
                    "MO-A",
                    AiQuerySemanticLexicon.STRUCTURED_MENU_OPERATION_OVERVIEW,
                    MenuOperationAnswerPlan.TYPE_MENU_OPERATION_OVERVIEW,
                    "MENU",
                    "OVERVIEW",
                    "PORTFOLIO",
                    MENU_FACET_OVERVIEW,
                    null);

    public static final MenuOperationSemanticCapabilityMatrixRow HIGH_SALES_LOW_PROFIT =
            row(
                    "MO-B",
                    AiQuerySemanticLexicon.STRUCTURED_MENU_DISH_HIGH_SALES_LOW_PROFIT,
                    MenuOperationAnswerPlan.TYPE_MENU_DISH_HIGH_SALES_LOW_PROFIT,
                    "MENU",
                    "ANALYSIS",
                    "PROFITABILITY",
                    MENU_FACET_HIGH_SALES_LOW_PROFIT,
                    null);

    public static final MenuOperationSemanticCapabilityMatrixRow ACTION_RECOMMENDATION =
            row(
                    "MO-C",
                    AiQuerySemanticLexicon.STRUCTURED_MENU_ACTION_RECOMMENDATION,
                    MenuOperationAnswerPlan.TYPE_MENU_ACTION_RECOMMENDATION,
                    "MENU",
                    "RECOMMENDATION",
                    "ACTION",
                    MENU_FACET_ACTION_RECOMMENDATION,
                    null);

    private static final List<MenuOperationSemanticCapabilityMatrixRow> FIRST_TURN_ROWS =
            List.of(OVERVIEW, HIGH_SALES_LOW_PROFIT, ACTION_RECOMMENDATION);

    public static List<MenuOperationSemanticCapabilityMatrixRow> firstTurnRows() {
        return FIRST_TURN_ROWS;
    }

    public static MenuOperationSemanticCapabilityMatrixRow resolveMatrixRow(
            String pathCode, String wire, AiQuerySemanticParseResult sem) {
        if (!StringUtils.hasText(wire)) {
            return null;
        }
        if (!AiResolvedQueryIntent.PATH_MENU_OPERATION.equals(pathCode)) {
            return null;
        }
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wire.trim());
        for (MenuOperationSemanticCapabilityMatrixRow row : FIRST_TURN_ROWS) {
            if (canon.equals(row.getStructuredIntentDetailWire())) {
                return row;
            }
        }
        return null;
    }

    public static String knownGapForResolvedRow(MenuOperationSemanticCapabilityMatrixRow row) {
        return row == null ? null : row.getKnownGapCode();
    }

    public static boolean detectMatrixWireMissing(
            AiQuerySemanticParseResult sem, String pathCode, String wire) {
        if (!AiResolvedQueryIntent.PATH_MENU_OPERATION.equals(pathCode)) {
            return false;
        }
        if (!StringUtils.hasText(wire)) {
            return true;
        }
        return resolveMatrixRow(pathCode, wire, sem) == null;
    }

    public static boolean isAcceptedMenuOperationWire(String wire) {
        if (!StringUtils.hasText(wire)) {
            return false;
        }
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wire.trim());
        return AiQuerySemanticLexicon.isStructuredMenuOperationDetail(canon);
    }

    private static MenuOperationSemanticCapabilityMatrixRow row(
            String rowId,
            String wire,
            String planType,
            String queryObject,
            String operation,
            String metric,
            String menuFacet,
            String knownGap) {
        return MenuOperationSemanticCapabilityMatrixRow.builder()
                .rowId(rowId)
                .structuredIntentDetailWire(wire)
                .targetMenuOperationPlanType(planType)
                .queryObject(queryObject)
                .operation(operation)
                .metric(metric)
                .menuFacet(menuFacet)
                .knownGapCode(knownGap)
                .build();
    }
}
