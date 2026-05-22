package com.nongxinle.ai.semantic.matrix;

import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.dto.business.DishSalesAnswerPlan;
import com.nongxinle.ai.semantic.AiQuerySemanticLlmMergeHelper;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.AiQuerySemanticSlotMerge;
import lombok.experimental.UtilityClass;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Phase 1：菜品销量本域矩阵（wire / planType / capability 注册表）。
 * <p>
 * 省略追问（「那毛利呢」「那哪个菜最高」等）由 {@link com.nongxinle.ai.followup.rewrite.llm.LlmFollowUpQueryRewriter}
 * 前置补全为完整问句后再进 v2；本类不再做 utterance pin / synthetic adoption / 跨域省略识别。
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
                    null);

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
                    null);


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

    public static String targetPlanTypeForWire(String wire) {
        DishSalesSemanticCapabilityMatrixRow row = findFirstTurnRowByWire(wire);
        return row == null ? null : row.getTargetDishSalesPlanType();
    }

    /**
     * dish_sales_query_path 下 structured wire 最终口径：Matrix 问句/槽位形状优先于 LLM 误标的毛利排行 wire。
     */
    public static String resolveStructuredIntentDetailWire(
            AiQuerySemanticParseResult sem,
            String pathCode,
            String mergedStructuredDetail,
            String normalizedUserMessage) {
        return resolveStructuredIntentDetailWire(
                sem, pathCode, mergedStructuredDetail, normalizedUserMessage, null);
    }

    public static String resolveStructuredIntentDetailWire(
            AiQuerySemanticParseResult sem,
            String pathCode,
            String mergedStructuredDetail,
            String normalizedUserMessage,
            AiConversationTurnMemory previousTurn) {
        if (!AiResolvedQueryIntent.PATH_DISH_SALES_QUERY.equals(pathCode)) {
            return null;
        }
        if (AiQuerySemanticLlmMergeHelper.hasExplicitStockReduceRouteSignal(sem)) {
            return null;
        }
        if (AiQuerySemanticSlotMerge.hasCanonicalStructuredIntentWireFromSlots(sem)) {
            String slotCanon =
                    AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                            sem.getSemanticSlots().getStructuredIntentDetailWire().trim());
            return adoptWireViaMatrix(pathCode, slotCanon, sem, normalizedUserMessage, previousTurn);
        }
        String fromShape = inferMatrixWireFromSemantics(sem, normalizedUserMessage, previousTurn);
        if (StringUtils.hasText(fromShape)) {
            return adoptWireViaMatrix(pathCode, fromShape, sem, normalizedUserMessage, previousTurn);
        }
        String mergedCanon =
                StringUtils.hasText(mergedStructuredDetail)
                        ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                                mergedStructuredDetail.trim())
                        : null;
        if (StringUtils.hasText(mergedCanon)
                && AiQuerySemanticLexicon.isStructuredDishSalesDetail(mergedCanon)) {
            return adoptWireViaMatrix(pathCode, mergedCanon, sem, normalizedUserMessage, previousTurn);
        }
        return null;
    }

    private static String adoptWireViaMatrix(
            String pathCode,
            String canonWire,
            AiQuerySemanticParseResult sem,
            String normalizedUserMessage,
            AiConversationTurnMemory previousTurn) {
        if (!StringUtils.hasText(canonWire)) {
            return null;
        }
        String msgForCorrection =
                AiQuerySemanticSlotMerge.hasCanonicalStructuredIntentWireFromSlots(sem)
                        ? ""
                        : normalizedUserMessage;
        String corrected =
                correctMislabeledDishProfitWireOnSalesPath(canonWire, msgForCorrection, previousTurn);
        corrected = correctMislabeledSalesRankingWireOnSalesPath(corrected, msgForCorrection);
        DishSalesSemanticCapabilityMatrixRow row =
                resolveMatrixRow(pathCode, corrected, sem, null, previousTurn, normalizedUserMessage);
        return row != null ? row.getStructuredIntentDetailWire() : corrected;
    }

    private static String inferMatrixWireFromSemantics(
            AiQuerySemanticParseResult sem,
            String normalizedUserMessage,
            AiConversationTurnMemory previousTurn) {
        if (AiQuerySemanticLlmMergeHelper.hasExplicitStockReduceRouteSignal(sem)) {
            return null;
        }
        String fromSlots = inferMatrixWireFromSemanticSlots(sem, normalizedUserMessage);
        if (StringUtils.hasText(fromSlots)) {
            return fromSlots;
        }
        if (!AiQuerySemanticSlotMerge.hasCanonicalStructuredIntentWireFromSlots(sem)) {
            String fromMsg = inferMatrixWireFromNormalizedQuestion(normalizedUserMessage);
            if (StringUtils.hasText(fromMsg)) {
                return fromMsg;
            }
        }
        return null;
    }

    private static String inferMatrixWireFromNormalizedQuestion(String normalizedUserMessage) {
        if (!StringUtils.hasText(normalizedUserMessage)) {
            return null;
        }
        String msg = compactMessage(normalizedUserMessage);
        if (msg.contains("趋势")) {
            return AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_TREND;
        }
        if (utteranceRequestsSingleDishSalesDetail(normalizedUserMessage)) {
            if (utteranceMentionsExplicitStoreInQuestion(normalizedUserMessage)) {
                return AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_STORE_SINGLE_DISH;
            }
            return AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_SINGLE_DISH;
        }
        if (utteranceMentionsExplicitStoreInQuestion(normalizedUserMessage)
                && (msg.contains("哪个菜") || msg.contains("什么菜"))
                && (msg.contains("最多") || msg.contains("最高") || msg.contains("最好"))) {
            return AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_STORE_RANKING;
        }
        if ((msg.contains("最低") || msg.contains("最少") || msg.contains("垫底") || msg.contains("滞销"))
                && (msg.contains("销量") || msg.contains("卖得") || msg.contains("份") || msg.contains("菜"))) {
            return AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_COUNT_RANKING_LOW;
        }
        if ((msg.contains("最高") || msg.contains("最好") || msg.contains("最多"))
                && (msg.contains("销售额") || msg.contains("营收") || msg.contains("金额"))) {
            return AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_AMOUNT_RANKING_HIGH;
        }
        if ((msg.contains("最高") || msg.contains("最好") || msg.contains("最多"))
                && (msg.contains("销量") || msg.contains("卖得") || msg.contains("份") || msg.contains("菜"))) {
            return AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH;
        }
        if (utteranceRequestsCountRankingHigh(normalizedUserMessage)
                && !utteranceRequestsAmountRankingHigh(normalizedUserMessage)) {
            return AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH;
        }
        return null;
    }

    private static String inferMatrixWireFromSemanticSlots(
            AiQuerySemanticParseResult sem, String normalizedUserMessage) {
        if (sem == null || sem.getSemanticSlots() == null) {
            return null;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = sem.getSemanticSlots();
        String op = normalizeMatrixToken(s.getOperation());
        String qo = normalizeMatrixToken(s.getQueryObject());
        if ("TREND".equals(op)) {
            return AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_TREND;
        }
        if ("DISH".equals(qo) && "DETAIL".equals(op)) {
            if (StringUtils.hasText(normalizedUserMessage)
                    && utteranceRequestsSingleDishSalesDetail(normalizedUserMessage)) {
                if (utteranceMentionsExplicitStoreInQuestion(normalizedUserMessage)
                        || semanticDeclaresNamedStore(sem)) {
                    return AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_STORE_SINGLE_DISH;
                }
                return AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_SINGLE_DISH;
            }
        }
        if ("DISH".equals(qo) && "RANKING".equals(op)) {
            String metric = normalizeMatrixToken(s.getMetric());
            if (isSalesCountRankingMetric(metric)) {
                if (StringUtils.hasText(normalizedUserMessage)
                        && utteranceRequestsCountRankingLow(normalizedUserMessage)) {
                    return AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_COUNT_RANKING_LOW;
                }
                if (StringUtils.hasText(normalizedUserMessage)
                        && utteranceMentionsExplicitStoreInQuestion(normalizedUserMessage)
                        && (compactMessage(normalizedUserMessage).contains("哪个菜")
                                || compactMessage(normalizedUserMessage).contains("什么菜"))) {
                    return AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_STORE_RANKING;
                }
                return AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH;
            }
            if (isSalesAmountRankingMetric(metric)) {
                return AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_AMOUNT_RANKING_HIGH;
            }
            if ("SOLD_PORTIONS".equals(metric) || metric == null || metric.isBlank()) {
                if (StringUtils.hasText(normalizedUserMessage)
                        && utteranceMentionsExplicitStoreInQuestion(normalizedUserMessage)
                        && (compactMessage(normalizedUserMessage).contains("哪个菜")
                                || compactMessage(normalizedUserMessage).contains("什么菜"))) {
                    return AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_STORE_RANKING;
                }
                if (StringUtils.hasText(normalizedUserMessage)
                        && utteranceRequestsCountRankingLow(normalizedUserMessage)) {
                    return AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_COUNT_RANKING_LOW;
                }
                return AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH;
            }
        }
        return null;
    }

    private static boolean isSalesCountRankingMetric(String metric) {
        if (!StringUtils.hasText(metric)) {
            return false;
        }
        String m = metric.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_');
        return "SALES_COUNT".equals(m)
                || "SOLD_PORTIONS".equals(m)
                || "SOLD_PORTION".equals(m)
                || m.contains("SALES_COUNT")
                || m.contains("SOLD_PORTION");
    }

    private static boolean isSalesAmountRankingMetric(String metric) {
        if (!StringUtils.hasText(metric)) {
            return false;
        }
        String m = metric.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_');
        return "SALES_AMOUNT".equals(m)
                || "LIST_PRICE_REVENUE".equals(m)
                || m.contains("SALES_AMOUNT")
                || m.contains("REVENUE");
    }

    private static String correctMislabeledDishProfitWireOnSalesPath(
            String canon, String normalizedUserMessage, AiConversationTurnMemory previousTurn) {
        if (!AiQuerySemanticLexicon.isDishProfitRankingStructuredDetail(canon)
                && !AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_RANKING_LOW_MARGIN.equals(canon)
                && !AiQuerySemanticLexicon.STRUCTURED_DISH_GROSS_MARGIN_QUERY.equals(canon)) {
            return canon;
        }
        if (utteranceRequestsCountRankingLow(normalizedUserMessage)) {
            return AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_COUNT_RANKING_LOW;
        }
        if (utteranceRequestsAmountRankingHigh(normalizedUserMessage)) {
            return AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_AMOUNT_RANKING_HIGH;
        }
        if (utteranceRequestsCountRankingHigh(normalizedUserMessage)) {
            return AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH;
        }
        return canon;
    }

    private static String correctMislabeledSalesRankingWireOnSalesPath(
            String canon, String normalizedUserMessage) {
        if (!StringUtils.hasText(normalizedUserMessage)
                || !utteranceRequestsSingleDishSalesDetail(normalizedUserMessage)) {
            return canon;
        }
        if (utteranceMentionsExplicitStoreInQuestion(normalizedUserMessage)) {
            return AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_STORE_SINGLE_DISH;
        }
        return AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_SINGLE_DISH;
    }

    /**
     * 集团级销量趋势首轮（DS-J）：不继承上一轮门店收窄；须回到 GROUP baseline。
     */
    public static boolean shouldSuppressStoreScopeInheritanceForTrend(
            String structuredIntentDetailWire, String rawMessage, AiQuerySemanticParseResult semanticLlm) {
        String canon =
                StringUtils.hasText(structuredIntentDetailWire)
                        ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                                structuredIntentDetailWire.trim())
                        : null;
        boolean trendWire =
                AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_TREND.equals(canon)
                        || (StringUtils.hasText(rawMessage)
                                && compactMessage(rawMessage).contains("趋势"));
        if (!trendWire) {
            return false;
        }
        if (semanticDeclaresNamedStore(semanticLlm)) {
            return false;
        }
        return !utteranceMentionsExplicitStoreInQuestion(rawMessage);
    }

    private static boolean semanticDeclaresNamedStore(AiQuerySemanticParseResult sem) {
        if (sem == null || sem.isParseMissing()) {
            return false;
        }
        if (!sem.effectiveMentionedStoreNames().isEmpty()) {
            return true;
        }
        AiQuerySemanticParseResult.RequestedScopePart rs = sem.getRequestedScope();
        return rs != null && StringUtils.hasText(rs.getMentionedStoreName());
    }

    /** 从「AAA门店水煮鱼卖了多少」等问句提取菜名（窄口径，非全局 DISH 解析）。 */
    public static String extractMentionedDishNameFromSingleDishDetailQuestion(String normalizedUserMessage) {
        String msg = compactMessage(normalizedUserMessage);
        if (!StringUtils.hasText(msg)) {
            return null;
        }
        int storePos = msg.indexOf("门店");
        if (storePos >= 0) {
            String tail = msg.substring(storePos + 2);
            int sellIdx = indexOfSingleDishDetailCueStart(tail);
            if (sellIdx > 0) {
                String candidate = tail.substring(0, sellIdx).replaceAll("^[A-Za-z0-9\\s]+", "");
                if (candidate.length() >= 2 && !candidate.contains("哪个菜")) {
                    return candidate;
                }
            }
        }
        for (String cue : List.of("这个月卖", "卖了多少", "多少份", "销量多少")) {
            int idx = msg.indexOf(cue);
            if (idx > 0) {
                String before = msg.substring(0, idx);
                if (before.endsWith("这个月")) {
                    before = before.substring(0, before.length() - 3);
                }
                if (before.length() >= 2
                        && !before.contains("哪个菜")
                        && !before.contains("门店")) {
                    return before;
                }
            }
        }
        return null;
    }

    /** 从「AAA 门店…」提取门店标签（Harness 为 AAA）。 */
    public static String extractMentionedStoreLabelFromQuestion(String normalizedUserMessage) {
        String msg = compactMessage(normalizedUserMessage);
        if (!StringUtils.hasText(msg) || !msg.contains("门店")) {
            return null;
        }
        int idx = msg.indexOf("门店");
        if (idx <= 0) {
            return null;
        }
        String before = msg.substring(0, idx).trim();
        if (!StringUtils.hasText(before)) {
            return null;
        }
        return before;
    }

    private static int indexOfSingleDishDetailCueStart(String msg) {
        int best = -1;
        for (String cue : List.of("卖了多少", "多少份", "销量多少")) {
            int i = msg.indexOf(cue);
            if (i > 0 && (best < 0 || i < best)) {
                best = i;
            }
        }
        if (best < 0 && msg.contains("卖了") && msg.contains("多少")) {
            best = msg.indexOf("卖了多少");
            if (best < 0) {
                best = msg.indexOf("多少");
            }
        }
        return best;
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

    private static boolean utteranceRequestsCountRankingLow(String normalizedUserMessage) {
        String msg = compactMessage(normalizedUserMessage);
        return (msg.contains("最低") || msg.contains("最少") || msg.contains("垫底") || msg.contains("滞销"))
                && (msg.contains("销量") || msg.contains("菜") || msg.contains("份"));
    }

    private static boolean utteranceRequestsCountRankingHigh(String normalizedUserMessage) {
        String msg = compactMessage(normalizedUserMessage);
        if (msg.contains("卖得好") || msg.contains("卖得最好")) {
            return true;
        }
        return (msg.contains("最高") || msg.contains("最好") || msg.contains("最多"))
                && (msg.contains("销量") || msg.contains("菜"))
                && !msg.contains("最低");
    }

    private static boolean utteranceRequestsAmountRankingHigh(String normalizedUserMessage) {
        String msg = compactMessage(normalizedUserMessage);
        return (msg.contains("最高") || msg.contains("最好"))
                && (msg.contains("销售额") || msg.contains("金额"));
    }

    /**
     * 单菜销量明细：点名菜 + 卖了多少/多少份，且无最高/最低/最多等排行极值词。
     */
    private static boolean utteranceRequestsSingleDishSalesDetail(String normalizedUserMessage) {
        String msg = compactMessage(normalizedUserMessage);
        if (!StringUtils.hasText(msg)) {
            return false;
        }
        if (utteranceRequestsCountRankingLow(normalizedUserMessage)
                || utteranceRequestsCountRankingHigh(normalizedUserMessage)
                || utteranceRequestsAmountRankingHigh(normalizedUserMessage)) {
            return false;
        }
        if (msg.contains("哪个菜")
                && (msg.contains("最高")
                        || msg.contains("最低")
                        || msg.contains("最多")
                        || msg.contains("最好"))) {
            return false;
        }
        boolean detailCue =
                msg.contains("卖了多少")
                        || msg.contains("多少份")
                        || msg.contains("销量多少")
                        || (msg.contains("卖了") && msg.contains("多少"));
        if (!detailCue) {
            return false;
        }
        return !msg.contains("哪个菜");
    }

    private static boolean utteranceMentionsExplicitStoreInQuestion(String normalizedUserMessage) {
        String msg = compactMessage(normalizedUserMessage);
        return msg.contains("门店");
    }

    private static String compactMessage(String normalizedUserMessage) {
        if (!StringUtils.hasText(normalizedUserMessage)) {
            return "";
        }
        return normalizedUserMessage.replace(" ", "").replace("\u3000", "").trim();
    }

    private static String normalizeMatrixToken(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_');
    }

    public static DishSalesSemanticCapabilityMatrixRow resolveMatrixRow(
            String pathCode, String resolvedWire, AiQuerySemanticParseResult sem) {
        return resolveMatrixRow(pathCode, resolvedWire, sem, null);
    }

    public static DishSalesSemanticCapabilityMatrixRow resolveMatrixRow(
            String pathCode,
            String resolvedWire,
            AiQuerySemanticParseResult sem,
            AiResolvedQueryContext rq) {
        AiConversationTurnMemory previousTurn = rq != null ? rq.getPreviousTurn() : null;
        String normalizedUserMessage = rq != null ? rq.getNormalizedQuestion() : null;
        return resolveMatrixRow(pathCode, resolvedWire, sem, rq, previousTurn, normalizedUserMessage);
    }

    private static DishSalesSemanticCapabilityMatrixRow resolveMatrixRow(
            String pathCode,
            String resolvedWire,
            AiQuerySemanticParseResult sem,
            AiResolvedQueryContext rq,
            AiConversationTurnMemory previousTurn,
            String normalizedUserMessage) {
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
            if (isSingleStoreFirstTurnScope(rq)) {
                return STORE_COUNT_RANKING;
            }
            return COUNT_RANKING_HIGH_A;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_STORE_RANKING.equals(canon)) {
            return STORE_COUNT_RANKING;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_SINGLE_DISH.equals(canon)) {
if (isSingleStoreFirstTurnScope(rq)) {
                return STORE_SINGLE_DISH;
            }
            return SINGLE_DISH;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_STORE_SINGLE_DISH.equals(canon)) {
return STORE_SINGLE_DISH;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_OVERVIEW.equals(canon)) {
return COUNT_RANKING_HIGH_A;
        }
        DishSalesSemanticCapabilityMatrixRow first = findFirstTurnRowByWire(canon);
        if (first != null) {
            return first;
        }
        return null;
    }


    private static boolean isSingleStoreFirstTurnScope(AiResolvedQueryContext rq) {
        if (rq == null || rq.getOrgScope() == null) {
            return false;
        }
        String st = rq.getOrgScope().getScopeType();
        return AiResolvedOrgScope.SCOPE_STORE.equals(st) || AiResolvedOrgScope.SCOPE_PURCHASER.equals(st);
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

    public static boolean isDishSalesRankingWire(String wire) {
        if (!StringUtils.hasText(wire)) {
            return false;
        }
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wire.trim());
        return AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH.equals(canon)
                || AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_AMOUNT_RANKING_HIGH.equals(canon)
                || AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_COUNT_RANKING_LOW.equals(canon);
    }

    public static String detectPriorRankingWireLeak(String priorCanonicalWire, String currentCanonicalWire) {
        if (!StringUtils.hasText(priorCanonicalWire) || !StringUtils.hasText(currentCanonicalWire)) {
            return null;
        }
        String prior = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(priorCanonicalWire.trim());
        String cur = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(currentCanonicalWire.trim());
        return null;
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
        return DishSalesSemanticCapabilityMatrixRow.builder()
                .rowId(rowId)
                .queryObject(queryObject)
                .operation(operation)
                .metric(metric)
                .salesFacet(salesFacet)
                .structuredIntentDetailWire(wire)
                .targetDishSalesPlanType(planType)
                .knownGapCode(knownGap)
                .build();
    }

    /** Contract observe：按 Matrix 槽位形状补全 wire / 四槽（不读用户原文）。 */
    public static AiQuerySemanticParseResult canonicalizeDishSalesContractFrame(
            AiQuerySemanticParseResult raw) {
        if (raw == null || raw.isParseMissing() || raw.getSemanticSlots() == null) {
            return raw;
        }
        if (AiQuerySemanticLlmMergeHelper.hasExplicitStockReduceRouteSignal(raw)) {
            return raw;
        }
        String inferred = inferMatrixWireFromSemanticSlots(raw, "");
        if (!StringUtils.hasText(inferred)) {
            return raw;
        }
        DishSalesSemanticCapabilityMatrixRow row = findFirstTurnRowByWire(inferred);
        if (row == null || row.getKnownGapCode() != null) {
            return raw;
        }
        return mergeDishSalesContractRow(raw, row);
    }

    private static AiQuerySemanticParseResult mergeDishSalesContractRow(
            AiQuerySemanticParseResult raw, DishSalesSemanticCapabilityMatrixRow row) {
        if (raw == null || raw.getSemanticSlots() == null || row == null) {
            return raw;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = raw.getSemanticSlots();
        String wire = row.getStructuredIntentDetailWire();
        String slotWire =
                AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                        s.getStructuredIntentDetailWire());
        boolean needsUpdate =
                !wire.equals(slotWire)
                        || !row.getQueryObject().equals(normalizeMatrixToken(s.getQueryObject()))
                        || !row.getOperation().equals(normalizeMatrixToken(s.getOperation()))
                        || !row.getMetric().equals(normalizeMatrixToken(s.getMetric()))
                        || !row.getTargetDishSalesPlanType().equals(normalizeMatrixToken(s.getAnswerPlanType()));
        if (!needsUpdate) {
            return raw;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart updated =
                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                        .queryObject(row.getQueryObject())
                        .operation(row.getOperation())
                        .metric(row.getMetric())
                        .sourceFacet(s.getSourceFacet())
                        .anchorPolicy(s.getAnchorPolicy())
                        .detailWanted(s.getDetailWanted())
                        .structuredIntentDetailWire(wire)
                        .answerPlanType(row.getTargetDishSalesPlanType())
                        .build();
        return raw.toBuilder().semanticSlots(updated).build();
    }
}
