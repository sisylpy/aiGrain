package com.nongxinle.ai.semantic.matrix;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.dto.business.DishSalesAnswerPlan;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import lombok.experimental.UtilityClass;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DishSales 纯 capability registry。
 *
 * <p><b>职责：</b></p>
 * <ul>
 *   <li>定义 ACTIVE / KNOWN_GAP matrix rows</li>
 *   <li>提供 wire → row 查表</li>
 *   <li>提供 planType / knownGap / anchor helper</li>
 *   <li>提供 contract-locked canonicalize（走 {@code ContractFrameLightNormalizer}）</li>
 * </ul>
 *
 * <p><b>禁止职责：</b></p>
 * <ul>
 *   <li>不读取 {@code rawMessage / normalizedUserMessage} 或任何用户原话</li>
 *   <li>不做业务语义判断（contains / alias / fallback / substring / indexOf）</li>
 *   <li>不做范围语义判断（门店继承/收窄逻辑应在 Scope 层基于 semantic scope / contract result 处理）</li>
 *   <li>不从 semanticSlots 反推 structuredIntentDetailWire</li>
 *   <li>不从中文句子截取任何实体标签</li>
 * </ul>
 *
 * <p>DishSales 主链语义唯一来源：
 * selectedContractId → ACTIVE contract entry → SemanticContractCompletionEngine。</p>
 */
@UtilityClass
public final class DishSalesSemanticCapabilityMatrix {

    public static final String MATRIX_WIRE_MISSING = "MATRIX_WIRE_MISSING";
    public static final String ANCHOR_STRATEGY_DISH = "DISH";
    public static final String SALES_FACET_OVERVIEW = "OVERVIEW";
    public static final String SALES_FACET_RANKING_HIGH = "RANKING_HIGH";
    public static final String SALES_FACET_RANKING_LOW = "RANKING_LOW";
    public static final String SALES_FACET_SINGLE_DISH = "SINGLE_DISH";
    public static final String SALES_FACET_TREND = "TREND";
    public static final String SALES_FACET_CROSS_DOMAIN_PROFIT = "CROSS_DOMAIN_PROFIT";

    /** 销量域追问毛利：须走 DishProfit 专线，不在 DishSales P1 假装成功。 */
    public static final String KNOWN_GAP_CROSS_DOMAIN_DISH_PROFIT_NOT_IN_P1 =
            "DISH_SALES_CROSS_DOMAIN_DISH_PROFIT_NOT_IN_P1";

    /** 销量趋势：无日序列 / trend planType。 */
    public static final String KNOWN_GAP_TREND_SERIES_NOT_IMPLEMENTED =
            "DISH_SALES_TREND_SERIES_NOT_IMPLEMENTED";

    public static final DishSalesSemanticCapabilityMatrixRow OVERVIEW =
            firstTurnRow(
                    "DS-G",
                    AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_OVERVIEW,
                    DishSalesAnswerPlan.TYPE_DISH_SALES_COUNT_RANKING_HIGH,
                    "DISH",
                    "OVERVIEW",
                    "SOLD_PORTIONS",
                    SALES_FACET_OVERVIEW,
                    null);

    public static final DishSalesSemanticCapabilityMatrixRow COUNT_RANKING_HIGH_A =
            firstTurnRow(
                    "DS-A",
                    AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH,
                    DishSalesAnswerPlan.TYPE_DISH_SALES_COUNT_RANKING_HIGH,
                    "DISH",
                    "RANKING",
                    "SOLD_PORTIONS",
                    SALES_FACET_RANKING_HIGH,
                    null);

    public static final DishSalesSemanticCapabilityMatrixRow AMOUNT_RANKING_HIGH =
            firstTurnRow(
                    "DS-B",
                    AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_AMOUNT_RANKING_HIGH,
                    DishSalesAnswerPlan.TYPE_DISH_SALES_AMOUNT_RANKING_HIGH,
                    "DISH",
                    "RANKING",
                    "SALES_AMOUNT",
                    SALES_FACET_RANKING_HIGH,
                    null);

    public static final DishSalesSemanticCapabilityMatrixRow COUNT_RANKING_LOW =
            firstTurnRow(
                    "DS-C",
                    AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_COUNT_RANKING_LOW,
                    DishSalesAnswerPlan.TYPE_DISH_SALES_COUNT_RANKING_LOW,
                    "DISH",
                    "RANKING",
                    "SOLD_PORTIONS",
                    SALES_FACET_RANKING_LOW,
                    null);

    public static final DishSalesSemanticCapabilityMatrixRow SINGLE_DISH =
            firstTurnRow(
                    "DS-D",
                    AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_SINGLE_DISH,
                    DishSalesAnswerPlan.TYPE_DISH_SALES_SINGLE_DISH,
                    "DISH",
                    "DETAIL",
                    "SOLD_PORTIONS",
                    SALES_FACET_SINGLE_DISH,
                    null,
                    true,
                    ANCHOR_STRATEGY_DISH);

    public static final DishSalesSemanticCapabilityMatrixRow STORE_COUNT_RANKING =
            firstTurnRow(
                    "DS-E",
                    AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_STORE_RANKING,
                    DishSalesAnswerPlan.TYPE_DISH_SALES_COUNT_RANKING_HIGH,
                    "DISH",
                    "RANKING",
                    "SOLD_PORTIONS",
                    SALES_FACET_RANKING_HIGH,
                    null);

    public static final DishSalesSemanticCapabilityMatrixRow STORE_SINGLE_DISH =
            firstTurnRow(
                    "DS-F",
                    AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_STORE_SINGLE_DISH,
                    DishSalesAnswerPlan.TYPE_DISH_SALES_SINGLE_DISH,
                    "DISH",
                    "DETAIL",
                    "SOLD_PORTIONS",
                    SALES_FACET_SINGLE_DISH,
                    null,
                    true,
                    ANCHOR_STRATEGY_DISH);

    public static final DishSalesSemanticCapabilityMatrixRow CROSS_DOMAIN_PROFIT =
            DishSalesSemanticCapabilityMatrixRow.builder()
                    .rowId("DS-I")
                    .queryObject("DISH")
                    .operation("DETAIL")
                    .metric("GROSS_MARGIN")
                    .salesFacet(SALES_FACET_CROSS_DOMAIN_PROFIT)
                    .structuredIntentDetailWire(AiQuerySemanticLexicon.STRUCTURED_DISH_GROSS_MARGIN_QUERY)
                    .targetDishSalesPlanType(DishSalesAnswerPlan.TYPE_DISH_SALES_COUNT_RANKING_HIGH)
                    .knownGapCode(KNOWN_GAP_CROSS_DOMAIN_DISH_PROFIT_NOT_IN_P1)
                    .build();

    public static final DishSalesSemanticCapabilityMatrixRow TREND =
            DishSalesSemanticCapabilityMatrixRow.builder()
                    .rowId("DS-J")
                    .queryObject("DISH")
                    .operation("TREND")
                    .metric("SOLD_PORTIONS")
                    .salesFacet(SALES_FACET_TREND)
                    .structuredIntentDetailWire(AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_TREND)
                    .targetDishSalesPlanType(DishSalesAnswerPlan.TYPE_DISH_SALES_COUNT_RANKING_HIGH)
                    .knownGapCode(KNOWN_GAP_TREND_SERIES_NOT_IMPLEMENTED)
                    .build();

    private static final Map<String, DishSalesSemanticCapabilityMatrixRow> FIRST_TURN_BY_WIRE = buildFirstTurnIndex();

    private static Map<String, DishSalesSemanticCapabilityMatrixRow> buildFirstTurnIndex() {
        Map<String, DishSalesSemanticCapabilityMatrixRow> index = new LinkedHashMap<>();
        for (DishSalesSemanticCapabilityMatrixRow row : firstTurnRows()) {
            index.put(row.getStructuredIntentDetailWire(), row);
        }
        return index;
    }

    public static List<DishSalesSemanticCapabilityMatrixRow> firstTurnRows() {
        return List.of(
                OVERVIEW,
                COUNT_RANKING_HIGH_A,
                AMOUNT_RANKING_HIGH,
                COUNT_RANKING_LOW,
                SINGLE_DISH,
                STORE_COUNT_RANKING,
                STORE_SINGLE_DISH,
                CROSS_DOMAIN_PROFIT,
                TREND);
    }

    public static DishSalesSemanticCapabilityMatrixRow findFirstTurnRowByWire(String wire) {
        if (!StringUtils.hasText(wire)) {
            return null;
        }
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wire.trim());
        return canon == null ? null : FIRST_TURN_BY_WIRE.get(canon);
    }


    private static final Set<String> DISH_SALES_RANKING_ANCHOR_SOURCE_PLAN_TYPES =
            Set.of(
                    DishSalesAnswerPlan.TYPE_DISH_SALES_COUNT_RANKING_HIGH,
                    DishSalesAnswerPlan.TYPE_DISH_SALES_AMOUNT_RANKING_HIGH,
                    DishSalesAnswerPlan.TYPE_DISH_SALES_COUNT_RANKING_LOW);

    public static boolean isDishSalesRankingAnchorSourcePlanType(String planType) {
        if (!StringUtils.hasText(planType)) {
            return false;
        }
        return DISH_SALES_RANKING_ANCHOR_SOURCE_PLAN_TYPES.contains(planType.trim());
    }

    public static boolean planTypeEmitsDishSalesRankingResultAnchor(String planType) {
        return isDishSalesRankingAnchorSourcePlanType(planType);
    }

    /** 单菜成功轮也沉淀 DISH resultAnchor，供多轮 USE_PREVIOUS_ANCHOR 继承。 */
    public static boolean planTypeEmitsDishSalesResultAnchor(String planType) {
        if (!StringUtils.hasText(planType)) {
            return false;
        }
        String pt = planType.trim();
        return isDishSalesRankingAnchorSourcePlanType(pt)
                || DishSalesAnswerPlan.TYPE_DISH_SALES_SINGLE_DISH.equals(pt);
    }

    public static AiResultAnchor resolveUniqueDishSalesRankingAnchor(List<AiResultAnchor> anchors) {
        if (anchors == null || anchors.isEmpty()) {
            return null;
        }
        AiResultAnchor picked = null;
        for (AiResultAnchor a : anchors) {
            if (a == null || !StringUtils.hasText(a.getEntityType())) {
                continue;
            }
            if (!AiResultAnchor.ENTITY_TYPE_DISH.equalsIgnoreCase(a.getEntityType().trim())) {
                continue;
            }
            if (!isDishSalesRankingAnchorSourcePlanType(a.getSourcePlanType())) {
                continue;
            }
            Integer rk = a.getRank();
            boolean rankOne = rk != null && rk == 1;
            boolean singleUnranked = rk == null && anchors.size() == 1;
            if (!(rankOne || singleUnranked)) {
                continue;
            }
            if (!StringUtils.hasText(a.getEntityName()) && !StringUtils.hasText(a.getEntityId())) {
                continue;
            }
            if (picked != null) {
                return null;
            }
            picked = a;
        }
        return picked;
    }

    private static boolean isCrossDomainProfitStructuredWire(String canon) {
        return AiQuerySemanticLexicon.STRUCTURED_DISH_GROSS_MARGIN_QUERY.equals(canon)
                || AiQuerySemanticLexicon.isDishProfitRankingStructuredDetail(canon)
                || AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_RANKING_LOW_MARGIN.equals(canon);
    }

    /**
     * contract-completed canonical wire → matrix row 纯查表。
     * {@code rq} 保留仅为调用方兼容；不参与 wire→row 决策（scope 不得改写主链 wire/row）。
     */
    public static DishSalesSemanticCapabilityMatrixRow resolveMatrixRow(
            String pathCode,
            String resolvedWire,
            AiQuerySemanticParseResult sem,
            AiResolvedQueryContext rq) {
        return resolveMatrixRow(pathCode, resolvedWire, sem);
    }

    public static DishSalesSemanticCapabilityMatrixRow resolveMatrixRow(
            String pathCode, String resolvedWire, AiQuerySemanticParseResult sem) {
        if (!AiResolvedQueryIntent.PATH_DISH_SALES_QUERY.equals(pathCode)) {
            return null;
        }
        String canon =
                StringUtils.hasText(resolvedWire)
                        ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(resolvedWire.trim())
                        : null;
        if (canon == null) {
            return null;
        }
        if (isCrossDomainProfitStructuredWire(canon)) {
            return null;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_TREND.equals(canon)) {
            return TREND;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_COUNT_RANKING_LOW.equals(canon)) {
            return COUNT_RANKING_LOW;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH.equals(canon)) {
            return COUNT_RANKING_HIGH_A;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_STORE_RANKING.equals(canon)) {
            return STORE_COUNT_RANKING;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_SINGLE_DISH.equals(canon)) {
            return SINGLE_DISH;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_STORE_SINGLE_DISH.equals(canon)) {
            return STORE_SINGLE_DISH;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_OVERVIEW.equals(canon)) {
            return OVERVIEW;
        }
        DishSalesSemanticCapabilityMatrixRow first = findFirstTurnRowByWire(canon);
        if (first != null) {
            return first;
        }
        return null;
    }

    public static String knownGapForResolvedRow(DishSalesSemanticCapabilityMatrixRow row) {
        return row == null ? null : row.getKnownGapCode();
    }

    public static boolean detectMatrixWireMissing(
            AiQuerySemanticParseResult sem, String pathCode, String resolvedWire) {
        if (!AiResolvedQueryIntent.PATH_DISH_SALES_QUERY.equals(pathCode)) {
            return false;
        }
        if (StringUtils.hasText(resolvedWire)) {
            if (resolveMatrixRow(pathCode, resolvedWire, sem, null) != null) {
                return false;
            }
        }
        if (sem == null || sem.getSemanticSlots() == null) {
            return false;
        }
        String slotWire =
                AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                        sem.getSemanticSlots().getStructuredIntentDetailWire());
        if (StringUtils.hasText(slotWire) && AiQuerySemanticLexicon.isStructuredDishSalesDetail(slotWire)) {
            return resolveMatrixRow(pathCode, slotWire, sem, null) == null;
        }
        return false;
    }


    private static DishSalesSemanticCapabilityMatrixRow firstTurnRow(
            String rowId,
            String wire,
            String planType,
            String queryObject,
            String operation,
            String metric,
            String salesFacet,
            String knownGap) {
        return firstTurnRow(
                rowId, wire, planType, queryObject, operation, metric, salesFacet, knownGap, false, null);
    }

    private static DishSalesSemanticCapabilityMatrixRow firstTurnRow(
            String rowId,
            String wire,
            String planType,
            String queryObject,
            String operation,
            String metric,
            String salesFacet,
            String knownGap,
            boolean requiresAnchor,
            String anchorType) {
        return DishSalesSemanticCapabilityMatrixRow.builder()
                .rowId(rowId)
                .queryObject(queryObject)
                .operation(operation)
                .metric(metric)
                .salesFacet(salesFacet)
                .structuredIntentDetailWire(wire)
                .targetDishSalesPlanType(planType)
                .knownGapCode(knownGap)
                .requiresAnchor(requiresAnchor)
                .anchorType(anchorType)
                .build();
    }

    /** Contract observe：contract-locked 时走 light normalize；非 contract-locked 原样返回。 */
    public static AiQuerySemanticParseResult canonicalizeDishSalesContractFrame(
            AiQuerySemanticParseResult raw) {
        if (raw == null || raw.isParseMissing() || raw.getSemanticSlots() == null) {
            return raw;
        }
        if (com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine.isContractLockedParse(raw)) {
            return com.nongxinle.ai.semantic.contract.canonicalizer.ContractFrameLightNormalizer.normalize(raw);
        }
        return raw;
    }
}
