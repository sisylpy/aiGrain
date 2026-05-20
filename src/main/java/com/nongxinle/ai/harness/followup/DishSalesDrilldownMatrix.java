package com.nongxinle.ai.harness.followup;

import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.DishSalesAnswerPlan;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import lombok.experimental.UtilityClass;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Phase 1：菜品销量本域矩阵（Harness Engineering 契约表）。
 */
@UtilityClass
public final class DishSalesDrilldownMatrix {

    public static final String ROW_KIND_FIRST_TURN = "FIRST_TURN";
    public static final String ROW_KIND_TIME_FOLLOWUP = "TIME_FOLLOWUP";
    public static final String ROW_KIND_RANKING_FOLLOWUP = "RANKING_FOLLOWUP";

    public static final String MATRIX_WIRE_MISSING = "MATRIX_WIRE_MISSING";

    public static final String ANCHOR_STRATEGY_NONE = "NONE";
    public static final String ANCHOR_STRATEGY_DISH = "DISH";
    public static final String ANCHOR_STRATEGY_STORE = "STORE";

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

    private static final Set<String> TIME_FOLLOWUP_PRIOR_PLAN_TYPES =
            Set.of(
                    DishSalesAnswerPlan.TYPE_DISH_SALES_COUNT_RANKING_HIGH,
                    DishSalesAnswerPlan.TYPE_DISH_SALES_AMOUNT_RANKING_HIGH,
                    DishSalesAnswerPlan.TYPE_DISH_SALES_COUNT_RANKING_LOW,
                    DishSalesAnswerPlan.TYPE_DISH_SALES_SINGLE_DISH);

    public static final DishSalesDrilldownMatrixRow COUNT_RANKING_HIGH_A =
            firstTurnRow(
                    "DS-A",
                    AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH,
                    DishSalesAnswerPlan.TYPE_DISH_SALES_COUNT_RANKING_HIGH,
                    "DISH",
                    "RANKING",
                    "SOLD_PORTIONS",
                    SALES_FACET_RANKING_HIGH,
                    ANCHOR_STRATEGY_NONE,
                    null);

    public static final DishSalesDrilldownMatrixRow COUNT_RANKING_LOW =
            firstTurnRow(
                    "DS-C",
                    AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_COUNT_RANKING_LOW,
                    DishSalesAnswerPlan.TYPE_DISH_SALES_COUNT_RANKING_LOW,
                    "DISH",
                    "RANKING",
                    "SOLD_PORTIONS",
                    SALES_FACET_RANKING_LOW,
                    ANCHOR_STRATEGY_NONE,
                    null);

    public static final DishSalesDrilldownMatrixRow SINGLE_DISH =
            firstTurnRow(
                    "DS-D",
                    AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_SINGLE_DISH,
                    DishSalesAnswerPlan.TYPE_DISH_SALES_SINGLE_DISH,
                    "DISH",
                    "DETAIL",
                    "SOLD_PORTIONS",
                    SALES_FACET_SINGLE_DISH,
                    ANCHOR_STRATEGY_DISH,
                    null);

    public static final DishSalesDrilldownMatrixRow STORE_COUNT_RANKING =
            firstTurnRow(
                    "DS-E",
                    AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_STORE_RANKING,
                    DishSalesAnswerPlan.TYPE_DISH_SALES_COUNT_RANKING_HIGH,
                    "DISH",
                    "RANKING",
                    "SOLD_PORTIONS",
                    SALES_FACET_RANKING_HIGH,
                    ANCHOR_STRATEGY_STORE,
                    null);

    public static final DishSalesDrilldownMatrixRow STORE_SINGLE_DISH =
            firstTurnRow(
                    "DS-F",
                    AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_STORE_SINGLE_DISH,
                    DishSalesAnswerPlan.TYPE_DISH_SALES_SINGLE_DISH,
                    "DISH",
                    "DETAIL",
                    "SOLD_PORTIONS",
                    SALES_FACET_SINGLE_DISH,
                    ANCHOR_STRATEGY_STORE,
                    null);

    /** DS-G：时间追问切上月；继承域/菜名/门店 anchor，不继承上一轮时间窗（Harness 禁止 INHERITED_PREVIOUS）。 */
    public static final DishSalesDrilldownMatrixRow TIME_FOLLOWUP_PREV_MONTH =
            timeFollowupRow(
                    "DS-G",
                    AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH,
                    DishSalesAnswerPlan.TYPE_DISH_SALES_COUNT_RANKING_HIGH);

    public static final DishSalesDrilldownMatrixRow RANKING_FOLLOWUP_HIGH =
            rankingFollowupRow(
                    "DS-H",
                    AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH,
                    DishSalesAnswerPlan.TYPE_DISH_SALES_COUNT_RANKING_HIGH);

    public static final DishSalesDrilldownMatrixRow CROSS_DOMAIN_PROFIT =
            DishSalesDrilldownMatrixRow.builder()
                    .rowId("DS-I")
                    .rowKind(ROW_KIND_FIRST_TURN)
                    .queryObject("DISH")
                    .operation("DETAIL")
                    .metric("GROSS_MARGIN")
                    .salesFacet(SALES_FACET_CROSS_DOMAIN_PROFIT)
                    .structuredIntentDetailWire(AiQuerySemanticLexicon.STRUCTURED_DISH_GROSS_MARGIN_QUERY)
                    .targetDishSalesPlanType(DishSalesAnswerPlan.TYPE_DISH_SALES_COUNT_RANKING_HIGH)
                    .resultAnchorStrategy(ANCHOR_STRATEGY_DISH)
                    .knownGapCode(KNOWN_GAP_CROSS_DOMAIN_DISH_PROFIT_NOT_IN_P1)
                    .allowedPriorPlanTypes(Set.of())
                    .rejectPriorRankingWire(false)
                    .build();

    public static final DishSalesDrilldownMatrixRow TREND =
            DishSalesDrilldownMatrixRow.builder()
                    .rowId("DS-J")
                    .rowKind(ROW_KIND_FIRST_TURN)
                    .queryObject("DISH")
                    .operation("TREND")
                    .metric("SOLD_PORTIONS")
                    .salesFacet(SALES_FACET_TREND)
                    .structuredIntentDetailWire(AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_TREND)
                    .targetDishSalesPlanType(DishSalesAnswerPlan.TYPE_DISH_SALES_COUNT_RANKING_HIGH)
                    .resultAnchorStrategy(ANCHOR_STRATEGY_NONE)
                    .knownGapCode(KNOWN_GAP_TREND_SERIES_NOT_IMPLEMENTED)
                    .allowedPriorPlanTypes(Set.of())
                    .rejectPriorRankingWire(false)
                    .build();

    private static final Map<String, DishSalesDrilldownMatrixRow> FIRST_TURN_BY_WIRE = buildFirstTurnIndex();

    private static Map<String, DishSalesDrilldownMatrixRow> buildFirstTurnIndex() {
        Map<String, DishSalesDrilldownMatrixRow> index = new LinkedHashMap<>();
        for (DishSalesDrilldownMatrixRow row : firstTurnRows()) {
            index.put(row.getStructuredIntentDetailWire(), row);
        }
        return index;
    }

    public static List<DishSalesDrilldownMatrixRow> firstTurnRows() {
        return List.of(
                COUNT_RANKING_HIGH_A,
                COUNT_RANKING_LOW,
                SINGLE_DISH,
                STORE_COUNT_RANKING,
                STORE_SINGLE_DISH,
                CROSS_DOMAIN_PROFIT,
                TREND);
    }

    public static List<DishSalesDrilldownMatrixRow> followUpRows() {
        return List.of(TIME_FOLLOWUP_PREV_MONTH, RANKING_FOLLOWUP_HIGH);
    }

    public static DishSalesDrilldownMatrixRow findFirstTurnRowByWire(String wire) {
        if (!StringUtils.hasText(wire)) {
            return null;
        }
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wire.trim());
        return canon == null ? null : FIRST_TURN_BY_WIRE.get(canon);
    }

    public static DishSalesDrilldownMatrixRow findTimeFollowupRowByWire(String wire) {
        if (!StringUtils.hasText(wire)) {
            return null;
        }
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wire.trim());
        if (AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH.equals(canon)
                || AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_SINGLE_DISH.equals(canon)
                || AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_STORE_SINGLE_DISH.equals(canon)) {
            return TIME_FOLLOWUP_PREV_MONTH;
        }
        return null;
    }

    public static DishSalesDrilldownMatrixRow findRankingFollowupRowByWire(String wire) {
        if (!StringUtils.hasText(wire)) {
            return null;
        }
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wire.trim());
        if (AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH.equals(canon)
                || AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_AMOUNT_RANKING_HIGH.equals(canon)) {
            return RANKING_FOLLOWUP_HIGH;
        }
        return null;
    }

    /**
     * dish_sales_query_path 下 structured wire 最终口径：Matrix 问句/槽位形状优先于 LLM 误标的毛利排行 wire。
     */
    public static String resolveStructuredIntentDetailWire(
            AiQuerySemanticParseResult sem,
            String pathCode,
            String mergedStructuredDetail,
            String normalizedUserMessage) {
        if (!AiResolvedQueryIntent.PATH_DISH_SALES_QUERY.equals(pathCode)) {
            return null;
        }
        String fromMatrixShape = inferMatrixWireFromSemantics(sem, normalizedUserMessage);
        if (StringUtils.hasText(fromMatrixShape)) {
            return fromMatrixShape;
        }
        String canon =
                StringUtils.hasText(mergedStructuredDetail)
                        ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                                mergedStructuredDetail.trim())
                        : null;
        if (canon == null && sem != null && sem.getSemanticSlots() != null) {
            String slotRaw = sem.getSemanticSlots().getStructuredIntentDetailWire();
            if (StringUtils.hasText(slotRaw)) {
                canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(slotRaw.trim());
            }
        }
        if (canon == null) {
            return null;
        }
        canon = correctMislabeledDishProfitWireOnSalesPath(canon, normalizedUserMessage);
        canon = correctMislabeledSalesRankingWireOnSalesPath(canon, normalizedUserMessage);
        DishSalesDrilldownMatrixRow row = resolveMatrixRow(pathCode, canon, sem, null);
        return row != null ? row.getStructuredIntentDetailWire() : canon;
    }

    private static String inferMatrixWireFromSemantics(
            AiQuerySemanticParseResult sem, String normalizedUserMessage) {
        if (isCrossDomainProfitFollowupFromMessage(normalizedUserMessage)) {
            return AiQuerySemanticLexicon.STRUCTURED_DISH_GROSS_MARGIN_QUERY;
        }
        if (isTimeFollowupFromMessage(normalizedUserMessage)) {
            return AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH;
        }
        if (isDishSalesCountRankingFollowupMessage(normalizedUserMessage)) {
            return AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH;
        }
        String fromMsg = inferMatrixWireFromNormalizedQuestion(normalizedUserMessage);
        if (fromMsg != null) {
            return fromMsg;
        }
        return inferMatrixWireFromSemanticSlots(sem, normalizedUserMessage);
    }

    private static String inferMatrixWireFromNormalizedQuestion(String normalizedUserMessage) {
        if (!StringUtils.hasText(normalizedUserMessage)) {
            return null;
        }
        String msg = compactMessage(normalizedUserMessage);
        if (msg.contains("趋势")) {
            return AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_TREND;
        }
        if (msg.contains("毛利") || msg.contains("毛利率") || msg.contains("利润")) {
            return AiQuerySemanticLexicon.STRUCTURED_DISH_GROSS_MARGIN_QUERY;
        }
        if (isDishSalesCountRankingFollowupMessage(normalizedUserMessage)) {
            return AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH;
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
            if ("SOLD_PORTIONS".equals(metric) || metric == null || metric.isBlank()) {
                if (StringUtils.hasText(normalizedUserMessage)
                        && utteranceMentionsExplicitStoreInQuestion(normalizedUserMessage)
                        && (compactMessage(normalizedUserMessage).contains("哪个菜")
                                || compactMessage(normalizedUserMessage).contains("什么菜"))) {
                    return AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_STORE_RANKING;
                }
                return AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH;
            }
        }
        return null;
    }

    private static String correctMislabeledDishProfitWireOnSalesPath(
            String canon, String normalizedUserMessage) {
        if (!AiQuerySemanticLexicon.isDishProfitRankingStructuredDetail(canon)
                && !AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_RANKING_LOW_MARGIN.equals(canon)) {
            return canon;
        }
        if (isCrossDomainProfitFollowupFromMessage(normalizedUserMessage)
                || utteranceMentionsProfit(normalizedUserMessage)) {
            return AiQuerySemanticLexicon.STRUCTURED_DISH_GROSS_MARGIN_QUERY;
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
        return AiQuerySemanticLexicon.STRUCTURED_DISH_GROSS_MARGIN_QUERY;
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

    /**
     * 销量域多轮：上一轮已在 {@link AiResolvedQueryIntent#PATH_DISH_SALES_QUERY}，
     * 本句为销量排行追问（如「那哪个菜最高？」），即使 V2 未产出 intent 也须钉住销量 path。
     */
    public static boolean canPinDishSalesPathForRankingFollowUp(
            AiConversationTurnMemory previousTurn, String normalizedUserMessage) {
        if (previousTurn == null || !StringUtils.hasText(previousTurn.getLastPathCode())) {
            return false;
        }
        if (!AiResolvedQueryIntent.PATH_DISH_SALES_QUERY.equals(previousTurn.getLastPathCode().trim())) {
            return false;
        }
        return isDishSalesCountRankingFollowupMessage(normalizedUserMessage);
    }

    /**
     * 门店 + 单菜 + 卖了多少：V2 parse 失败时仍钉住 {@link AiResolvedQueryIntent#PATH_DISH_SALES_QUERY} /
     * {@link AiQuerySemanticLexicon#STRUCTURED_DISH_SALES_STORE_SINGLE_DISH}（Matrix P1 R6）。
     */
    public static boolean canPinDishSalesPathForStoreSingleDishFollowUp(
            AiConversationTurnMemory previousTurn,
            AiQuerySemanticParseResult sem,
            String normalizedUserMessage) {
        return canAdoptDishSalesStoreSingleDishQuestion(previousTurn, sem, normalizedUserMessage);
    }

    /** 集团口径单菜销量明细（Matrix P1 R4），无门店词。 */
    public static boolean canPinDishSalesPathForGroupSingleDishQuestion(
            AiConversationTurnMemory previousTurn,
            AiQuerySemanticParseResult sem,
            String normalizedUserMessage) {
        return canAdoptDishSalesGroupSingleDishQuestion(previousTurn, sem, normalizedUserMessage);
    }

    public static boolean canAdoptDishSalesStoreSingleDishQuestion(
            AiConversationTurnMemory previousTurn,
            AiQuerySemanticParseResult sem,
            String normalizedUserMessage) {
        if (!utteranceRequestsSingleDishSalesDetail(normalizedUserMessage)) {
            return false;
        }
        if (!utteranceMentionsExplicitStoreInQuestion(normalizedUserMessage)) {
            return false;
        }
        if (isDishSalesCountRankingFollowupMessage(normalizedUserMessage)
                || isCrossDomainProfitFollowupFromMessage(normalizedUserMessage)
                || isTimeFollowupFromMessage(normalizedUserMessage)) {
            return false;
        }
        if (!StringUtils.hasText(extractMentionedDishNameFromSingleDishDetailQuestion(normalizedUserMessage))) {
            return false;
        }
        if (!StringUtils.hasText(extractMentionedStoreLabelFromQuestion(normalizedUserMessage))) {
            return false;
        }
        return priorDishSalesContext(previousTurn, sem)
                || isSelfContainedStoreSingleDishDetailQuestion(normalizedUserMessage);
    }

    /**
     * V2/LLM 不可用时：仅凭 Matrix 契约问句形态钉住首轮/独立轮（如「这个月哪个菜卖得最好？」），
     * 不依赖 {@code previousTurn.lastPathCode}。仅限 {@link #inferMatrixWireFromNormalizedQuestion} 已覆盖的问句。
     */
    public static boolean canAdoptDishSalesMatrixUtterancePin(
            AiConversationTurnMemory previousTurn,
            AiQuerySemanticParseResult sem,
            String normalizedUserMessage) {
        if (BusinessDiagnosisDrilldownMatrix.shouldBlockDishSalesMatrixUtterancePin(
                previousTurn, normalizedUserMessage)) {
            return false;
        }
        if (canAdoptDishSalesStoreSingleDishQuestion(previousTurn, sem, normalizedUserMessage)
                || canAdoptDishSalesGroupSingleDishQuestion(previousTurn, sem, normalizedUserMessage)
                || canPinDishSalesPathForRankingFollowUp(previousTurn, normalizedUserMessage)) {
            return false;
        }
        if (isTimeFollowupFromMessage(normalizedUserMessage)
                || isCrossDomainProfitFollowupFromMessage(normalizedUserMessage)) {
            return false;
        }
        return StringUtils.hasText(inferMatrixWireFromNormalizedQuestion(normalizedUserMessage));
    }

    public static boolean canAdoptDishSalesGroupSingleDishQuestion(
            AiConversationTurnMemory previousTurn,
            AiQuerySemanticParseResult sem,
            String normalizedUserMessage) {
        if (!utteranceRequestsSingleDishSalesDetail(normalizedUserMessage)) {
            return false;
        }
        if (utteranceMentionsExplicitStoreInQuestion(normalizedUserMessage)) {
            return false;
        }
        if (isDishSalesCountRankingFollowupMessage(normalizedUserMessage)
                || isCrossDomainProfitFollowupFromMessage(normalizedUserMessage)
                || isTimeFollowupFromMessage(normalizedUserMessage)) {
            return false;
        }
        if (!StringUtils.hasText(extractMentionedDishNameFromSingleDishDetailQuestion(normalizedUserMessage))) {
            return false;
        }
        return priorDishSalesContext(previousTurn, sem)
                || isSelfContainedGroupSingleDishDetailQuestion(normalizedUserMessage);
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

    private static boolean priorDishSalesContext(
            AiConversationTurnMemory previousTurn, AiQuerySemanticParseResult sem) {
        if (previousTurn != null && StringUtils.hasText(previousTurn.getLastPathCode())) {
            if (AiResolvedQueryIntent.PATH_DISH_SALES_QUERY.equals(previousTurn.getLastPathCode().trim())) {
                return true;
            }
        }
        if (sem == null || sem.isParseMissing()) {
            return false;
        }
        if (AiResolvedQueryIntent.DISH_SALES_QUERY.equalsIgnoreCase(
                sem.getIntent() != null ? sem.getIntent().trim() : "")) {
            return true;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart slots = sem.getSemanticSlots();
        if (slots == null) {
            return false;
        }
        String wire = slots.getStructuredIntentDetailWire();
        return StringUtils.hasText(wire) && wire.trim().startsWith("dish_sales");
    }

    private static boolean isSelfContainedStoreSingleDishDetailQuestion(String normalizedUserMessage) {
        return StringUtils.hasText(extractMentionedStoreLabelFromQuestion(normalizedUserMessage))
                && StringUtils.hasText(extractMentionedDishNameFromSingleDishDetailQuestion(normalizedUserMessage))
                && utteranceRequestsSingleDishSalesDetail(normalizedUserMessage)
                && utteranceMentionsExplicitStoreInQuestion(normalizedUserMessage);
    }

    private static boolean isSelfContainedGroupSingleDishDetailQuestion(String normalizedUserMessage) {
        return StringUtils.hasText(extractMentionedDishNameFromSingleDishDetailQuestion(normalizedUserMessage))
                && utteranceRequestsSingleDishSalesDetail(normalizedUserMessage)
                && !utteranceMentionsExplicitStoreInQuestion(normalizedUserMessage);
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

    /** 销量 count 排行追问问句（不要求本句出现「销量」）。 */
    public static boolean isDishSalesCountRankingFollowupMessage(String normalizedUserMessage) {
        if (!StringUtils.hasText(normalizedUserMessage)) {
            return false;
        }
        if (isTimeFollowupFromMessage(normalizedUserMessage)
                || isCrossDomainProfitFollowupFromMessage(normalizedUserMessage)) {
            return false;
        }
        return isRankingFollowupFromMessage(normalizedUserMessage)
                || utteranceRequestsCountRankingHigh(normalizedUserMessage);
    }

    /** Matrix P1：销量域内跨域毛利追问（如「那毛利呢」），不得切到 {@code dish_profit_path}。 */
    public static boolean isCrossDomainProfitFollowupMessage(String normalizedUserMessage) {
        return isCrossDomainProfitFollowupFromMessage(normalizedUserMessage);
    }

    public static boolean canAdoptDishSalesMatrixCrossDomainProfitFollowUp(
            AiConversationTurnMemory previousTurn, String normalizedUserMessage) {
        if (previousTurn == null || !StringUtils.hasText(previousTurn.getLastPathCode())) {
            return false;
        }
        if (!AiResolvedQueryIntent.PATH_DISH_SALES_QUERY.equals(previousTurn.getLastPathCode().trim())) {
            return false;
        }
        return isCrossDomainProfitFollowupFromMessage(normalizedUserMessage);
    }

    private static boolean isRankingFollowupFromMessage(String normalizedUserMessage) {
        String msg = compactMessage(normalizedUserMessage);
        if (!StringUtils.hasText(msg)) {
            return false;
        }
        if (msg.contains("那哪个菜") && (msg.contains("最高") || msg.contains("最多"))) {
            return true;
        }
        if (msg.contains("那销量最高") || msg.contains("销量最高的是哪个") || msg.contains("销量最多的是哪个")) {
            return true;
        }
        return false;
    }

    private static boolean isCrossDomainProfitFollowupFromMessage(String normalizedUserMessage) {
        String msg = compactMessage(normalizedUserMessage);
        if (!StringUtils.hasText(msg)) {
            return false;
        }
        return msg.contains("那毛利") || msg.contains("毛利呢") || msg.equals("毛利");
    }

    private static boolean isTimeFollowupFromMessage(String normalizedUserMessage) {
        String msg = compactMessage(normalizedUserMessage);
        if (!StringUtils.hasText(msg)) {
            return false;
        }
        return msg.contains("那上个月") || msg.contains("上个月呢") || msg.contains("上月呢");
    }

    private static boolean utteranceMentionsProfit(String normalizedUserMessage) {
        String msg = compactMessage(normalizedUserMessage);
        return msg.contains("毛利") || msg.contains("毛利率") || msg.contains("利润");
    }

    private static boolean utteranceRequestsCountRankingLow(String normalizedUserMessage) {
        String msg = compactMessage(normalizedUserMessage);
        return (msg.contains("最低") || msg.contains("最少") || msg.contains("垫底") || msg.contains("滞销"))
                && (msg.contains("销量") || msg.contains("菜") || msg.contains("份"));
    }

    private static boolean utteranceRequestsCountRankingHigh(String normalizedUserMessage) {
        String msg = compactMessage(normalizedUserMessage);
        if (msg.contains("那哪个菜") && msg.contains("最高")) {
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

    public static DishSalesDrilldownMatrixRow resolveMatrixRow(
            String pathCode, String resolvedWire, AiQuerySemanticParseResult sem) {
        return resolveMatrixRow(pathCode, resolvedWire, sem, null);
    }

    public static DishSalesDrilldownMatrixRow resolveMatrixRow(
            String pathCode,
            String resolvedWire,
            AiQuerySemanticParseResult sem,
            AiResolvedQueryContext rq) {
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
        if (AiQuerySemanticLexicon.STRUCTURED_DISH_GROSS_MARGIN_QUERY.equals(canon)
                || AiQuerySemanticLexicon.isDishProfitRankingStructuredDetail(canon)
                || AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_RANKING_LOW_MARGIN.equals(canon)) {
            return CROSS_DOMAIN_PROFIT;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_TREND.equals(canon)) {
            return TREND;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_COUNT_RANKING_LOW.equals(canon)) {
            return COUNT_RANKING_LOW;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH.equals(canon)) {
            if (isRankingFollowupShape(sem, rq != null ? rq.getNormalizedQuestion() : null)) {
                return RANKING_FOLLOWUP_HIGH;
            }
            if (isSingleStoreFirstTurnScope(rq)) {
                return STORE_COUNT_RANKING;
            }
            return COUNT_RANKING_HIGH_A;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_STORE_RANKING.equals(canon)) {
            return STORE_COUNT_RANKING;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_SINGLE_DISH.equals(canon)) {
            if (isTimeFollowupShape(sem)) {
                return TIME_FOLLOWUP_PREV_MONTH;
            }
            if (isSingleStoreFirstTurnScope(rq)) {
                return STORE_SINGLE_DISH;
            }
            return SINGLE_DISH;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_STORE_SINGLE_DISH.equals(canon)) {
            if (isTimeFollowupShape(sem)) {
                return TIME_FOLLOWUP_PREV_MONTH;
            }
            return STORE_SINGLE_DISH;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_OVERVIEW.equals(canon)) {
            if (isTimeFollowupShape(sem)) {
                return TIME_FOLLOWUP_PREV_MONTH;
            }
            return COUNT_RANKING_HIGH_A;
        }
        DishSalesDrilldownMatrixRow first = findFirstTurnRowByWire(canon);
        if (first != null) {
            return first;
        }
        return null;
    }

    public static boolean isRankingFollowupShape(AiQuerySemanticParseResult sem) {
        return isRankingFollowupShape(sem, null);
    }

    private static boolean isRankingFollowupShape(
            AiQuerySemanticParseResult sem, String normalizedUserMessage) {
        if (StringUtils.hasText(normalizedUserMessage)) {
            if (isTimeFollowupFromMessage(normalizedUserMessage)
                    || isCrossDomainProfitFollowupFromMessage(normalizedUserMessage)) {
                return false;
            }
            if (isDishSalesCountRankingFollowupMessage(normalizedUserMessage)) {
                return true;
            }
        }
        if (sem == null) {
            return false;
        }
        if (Boolean.TRUE.equals(sem.getFollowUp())) {
            if (isTimeFollowupFromMessage(normalizedUserMessage)
                    || isCrossDomainProfitFollowupFromMessage(normalizedUserMessage)) {
                return false;
            }
            String wire =
                    sem.getSemanticSlots() != null
                            ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                                    sem.getSemanticSlots().getStructuredIntentDetailWire())
                            : null;
            if (AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH.equals(wire)
                    || AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_AMOUNT_RANKING_HIGH.equals(wire)) {
                if (StringUtils.hasText(normalizedUserMessage)) {
                    return utteranceRequestsCountRankingHigh(normalizedUserMessage)
                            || isRankingFollowupFromMessage(normalizedUserMessage);
                }
                return true;
            }
        }
        if (sem.getSemanticSlots() != null && StringUtils.hasText(sem.getSemanticSlots().getOperation())) {
            String op = sem.getSemanticSlots().getOperation().trim().toUpperCase();
            if (op.contains("RANKING") && op.contains("FOLLOW")) {
                return true;
            }
        }
        return false;
    }

    public static boolean isTimeFollowupShape(AiQuerySemanticParseResult sem) {
        if (sem == null) {
            return false;
        }
        if (Boolean.TRUE.equals(sem.getFollowUp())) {
            String wire =
                    sem.getSemanticSlots() != null
                            ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                                    sem.getSemanticSlots().getStructuredIntentDetailWire())
                            : null;
            if (AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH.equals(wire)
                    || AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_SINGLE_DISH.equals(wire)
                    || AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_STORE_SINGLE_DISH.equals(wire)) {
                return true;
            }
        }
        if (sem.getSemanticSlots() == null) {
            return false;
        }
        String op = sem.getSemanticSlots().getOperation();
        if (StringUtils.hasText(op) && op.trim().toUpperCase().contains("TIME")) {
            return true;
        }
        String metric = sem.getSemanticSlots().getMetric();
        if (StringUtils.hasText(metric)) {
            String m = metric.trim().toUpperCase();
            if (m.contains("PREV") || m.contains("LAST_MONTH") || m.contains("上月")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSingleStoreFirstTurnScope(AiResolvedQueryContext rq) {
        if (rq == null || rq.getOrgScope() == null) {
            return false;
        }
        String st = rq.getOrgScope().getScopeType();
        return AiResolvedOrgScope.SCOPE_STORE.equals(st) || AiResolvedOrgScope.SCOPE_PURCHASER.equals(st);
    }

    public static String knownGapForResolvedRow(DishSalesDrilldownMatrixRow row) {
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
        if (!isDishSalesRankingWire(prior)
                && !AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_SINGLE_DISH.equals(prior)
                && !AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_STORE_SINGLE_DISH.equals(prior)) {
            return null;
        }
        DishSalesDrilldownMatrixRow fu = findRankingFollowupRowByWire(cur);
        if (fu != null && fu.isRejectPriorRankingWire()) {
            return "PRIOR_RANKING_OR_SINGLE_DISH_WIRE_NOT_CLEARED";
        }
        DishSalesDrilldownMatrixRow timeFu = findTimeFollowupRowByWire(cur);
        if (timeFu != null && timeFu.isRejectPriorRankingWire()) {
            return "PRIOR_RANKING_OR_SINGLE_DISH_WIRE_NOT_CLEARED";
        }
        return null;
    }

    private static DishSalesDrilldownMatrixRow firstTurnRow(
            String rowId,
            String wire,
            String planType,
            String queryObject,
            String operation,
            String metric,
            String salesFacet,
            String anchorStrategy,
            String knownGap) {
        return DishSalesDrilldownMatrixRow.builder()
                .rowId(rowId)
                .rowKind(ROW_KIND_FIRST_TURN)
                .queryObject(queryObject)
                .operation(operation)
                .metric(metric)
                .salesFacet(salesFacet)
                .structuredIntentDetailWire(wire)
                .targetDishSalesPlanType(planType)
                .resultAnchorStrategy(anchorStrategy)
                .knownGapCode(knownGap)
                .allowedPriorPlanTypes(Set.of())
                .rejectPriorRankingWire(false)
                .build();
    }

    private static DishSalesDrilldownMatrixRow timeFollowupRow(String rowId, String wire, String planType) {
        return DishSalesDrilldownMatrixRow.builder()
                .rowId(rowId)
                .rowKind(ROW_KIND_TIME_FOLLOWUP)
                .queryObject("DISH")
                .operation("TIME_SHIFT")
                .metric("SOLD_PORTIONS")
                .salesFacet(SALES_FACET_OVERVIEW)
                .structuredIntentDetailWire(wire)
                .targetDishSalesPlanType(planType)
                .resultAnchorStrategy(ANCHOR_STRATEGY_NONE)
                .knownGapCode(null)
                .allowedPriorPlanTypes(TIME_FOLLOWUP_PRIOR_PLAN_TYPES)
                .rejectPriorRankingWire(true)
                .build();
    }

    private static DishSalesDrilldownMatrixRow rankingFollowupRow(String rowId, String wire, String planType) {
        return DishSalesDrilldownMatrixRow.builder()
                .rowId(rowId)
                .rowKind(ROW_KIND_RANKING_FOLLOWUP)
                .queryObject("DISH")
                .operation("RANKING")
                .metric("SOLD_PORTIONS")
                .salesFacet(SALES_FACET_RANKING_HIGH)
                .structuredIntentDetailWire(wire)
                .targetDishSalesPlanType(planType)
                .resultAnchorStrategy(ANCHOR_STRATEGY_NONE)
                .knownGapCode(null)
                .allowedPriorPlanTypes(TIME_FOLLOWUP_PRIOR_PLAN_TYPES)
                .rejectPriorRankingWire(true)
                .build();
    }
}
