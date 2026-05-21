package com.nongxinle.ai.semantic;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiFollowUpResolver;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.harness.followup.BusinessDiagnosisDrilldownMatrix;
import com.nongxinle.ai.harness.followup.BusinessDiagnosisDrilldownMatrixRow;
import com.nongxinle.ai.harness.followup.BusinessOverviewDrilldownMatrix;
import com.nongxinle.ai.harness.followup.DishProfitDrilldownMatrix;
import com.nongxinle.ai.harness.followup.DishSalesDrilldownMatrix;
import com.nongxinle.ai.harness.followup.RevenueDrilldownMatrix;
import com.nongxinle.ai.harness.followup.StockReduceDrilldownMatrix;
import com.nongxinle.ai.harness.followup.WarehouseDrilldownMatrix;
import com.nongxinle.ai.semantic.SemanticTimeContractCheck;
import com.nongxinle.ai.semantic.TimeContractPreviousTurnSupport;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Locale;

/**
 * 将 {@link AiQuerySemanticParseResult} 合并入意图/时间草稿（时间日期镜像 LLM {@code startDate}/{@code endDate}，合同见 {@link SemanticTimeContractCheck}）；不得在合并阶段写入任何数据库 ID。
 */
public final class AiQuerySemanticLlmMergeHelper {

    private AiQuerySemanticLlmMergeHelper() {
    }

    public static AiResolvedQueryIntent mergeIntent(
            AiResolvedQueryIntent baselineIntent,
            AiQuerySemanticParseResult sem,
            double minConfidence) {
        return mergeIntent(baselineIntent, sem, minConfidence, null, null);
    }

    public static AiResolvedQueryIntent mergeIntent(
            AiResolvedQueryIntent baselineIntent,
            AiQuerySemanticParseResult sem,
            double minConfidence,
            String normalizedUserMessage) {
        return mergeIntent(baselineIntent, sem, minConfidence, normalizedUserMessage, null);
    }

    /**
     * @param baselineIntent V2-only：通常为 {@link AiResolvedQueryIntent#builder()} 空草稿；不再做 Java 关键词路由。
     * @param previousTurn 非首轮时传入，用于解析 intentAction/timeAction 等多轮语义动作。
     */
    public static AiResolvedQueryIntent mergeIntent(
            AiResolvedQueryIntent baselineIntent,
            AiQuerySemanticParseResult sem,
            double minConfidence,
            String normalizedUserMessage,
            AiConversationTurnMemory previousTurn) {
        AiResolvedQueryIntent base = baselineIntent != null ? copyIntent(baselineIntent) : AiResolvedQueryIntent.builder().build();
        String norm = normalizedUserMessage != null ? normalizedUserMessage.trim() : "";
        AiResolvedQueryIntent diagnosisContinuation =
                buildBusinessDiagnosisDrilldownContinuationIntent(previousTurn, norm);
        if (diagnosisContinuation != null) {
            return diagnosisContinuation;
        }
        if (!blocksDishSalesMatrixOverride(sem)) {
            AiResolvedQueryIntent matrixStoreSingleDish =
                    buildDishSalesMatrixStoreSingleDishIntent(previousTurn, sem, norm);
            if (matrixStoreSingleDish != null) {
                return matrixStoreSingleDish;
            }
            AiResolvedQueryIntent matrixGroupSingleDish =
                    buildDishSalesMatrixGroupSingleDishIntent(previousTurn, sem, norm);
            if (matrixGroupSingleDish != null) {
                return matrixGroupSingleDish;
            }
            AiResolvedQueryIntent matrixRankingFollowUp =
                    buildDishSalesMatrixRankingFollowUpIntent(previousTurn, norm);
            if (matrixRankingFollowUp != null) {
                return matrixRankingFollowUp;
            }
            boolean semReliable =
                    sem != null && !sem.isParseMissing() && sem.isStructuralConfidenceOk(minConfidence);
            if (!(semReliable && v2MapsToExplicitDishProfitPath(sem))) {
                AiResolvedQueryIntent matrixUtterancePin =
                        buildDishSalesMatrixUtterancePinIntent(previousTurn, sem, norm);
                if (matrixUtterancePin != null) {
                    return matrixUtterancePin;
                }
            }
        }
        if (sem == null || sem.isParseMissing() || !sem.isStructuralConfidenceOk(minConfidence)) {
            return base;
        }

        String ia = semanticActionNormalize(sem.getIntentAction());

        boolean requestedInheritPrevious =
                "INHERIT_PREVIOUS".equals(ia) && previousTurn != null && StringUtils.hasText(previousTurn.getLastPathCode());
        boolean inheritPrevIntent =
                requestedInheritPrevious && !hasExplicitStockReduceRouteSignal(sem);

        AiResolvedQueryIntent merged;
        if (inheritPrevIntent) {
            merged =
                    AiFollowUpResolver.inheritIntentFromMemory(
                            previousTurn, StringUtils.hasText(norm) ? norm : "");
        } else {
            WireIntent mapped = mapLlmIntent(sem.getIntent());
            if (mapped == null && hasExplicitStockReduceRouteSignal(sem)) {
                mapped =
                        new WireIntent(
                                AiResolvedQueryIntent.STOCK_REDUCE_QUERY,
                                AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY,
                                "出库/核销查询");
            }
            if (mapped == null) {
                return base;
            }
            merged =
                    AiResolvedQueryIntent.builder()
                            .intentCode(mapped.intentCode())
                            .pathCode(mapped.pathCode())
                            .topic(mapped.topic())
                            .structuredIntentDetail(base.getStructuredIntentDetail())
                            .purchaseSourceType(base.getPurchaseSourceType())
                            .inheritedFromPreviousTurn(base.isInheritedFromPreviousTurn())
                            .inheritedFromIntentCode(base.getInheritedFromIntentCode())
                            .build();
        }

        applyPurchaseStructuredWireFromSemanticSlots(merged, sem);
        applyStockReduceStructuredWireFromSemanticSlots(merged, sem);
        applyCanonicalStructuredIntentDetailWireFromSemanticSlots(merged, sem);
        applyBusinessOverviewStructuredWireFromSemanticSlots(merged, sem);
        merged = pinBusinessDiagnosisPathForDrilldownContinuation(merged, previousTurn, norm);
        applyBusinessDiagnosisStructuredWireFromSemanticSlots(merged, sem, norm);
        applyBusinessDiagnosisStructuredWireFromMessage(merged, norm);
        applyRevenueStructuredWireFromSemanticSlots(merged, sem, norm);
        merged = pinDishSalesPathForMatrixCrossDomainProfitFollowUp(merged, previousTurn, norm, sem);
        merged = pinDishSalesPathForMatrixRankingFollowUp(merged, previousTurn, norm, sem);
        applyDishSalesStructuredWireFromSemanticSlots(merged, sem, norm, previousTurn);
        applyDishProfitStructuredWireFromSemanticSlots(merged, sem);
        applyWarehouseStockStructuredWireFromSemanticSlots(merged, sem);

        return merged;
    }

    /**
     * 销量 Matrix 多轮：「那毛利呢」等跨域追问须留在 {@link AiResolvedQueryIntent#PATH_DISH_SALES_QUERY}，
     * 由 {@link DishSalesDrilldownMatrix#CROSS_DOMAIN_PROFIT} + knownGap 承接，不得切到毛利专线。
     */
    /**
     * 销量 Matrix 排行追问：上一轮 dish_sales_query_path + 本句「那哪个菜最高/最多」等，
     * 钉住 path/wire，不依赖本句 V2 intent 或「销量」字样。
     */
    /**
     * 诊断 Matrix 多轮：上一轮 {@link AiResolvedQueryIntent#PATH_BUSINESS_DIAGNOSIS} + 本句子域归因 / 改进行动，
     * 钉住 diagnosis path/wire，不得被销量 Matrix utterance pin（「毛利」→ dish_sales）抢走。
     */
    public static AiResolvedQueryIntent buildBusinessDiagnosisDrilldownContinuationIntent(
            AiConversationTurnMemory previousTurn, String normalizedUserMessage) {
        if (!BusinessDiagnosisDrilldownMatrix.canAdoptDiagnosisDrilldownContinuation(
                previousTurn, normalizedUserMessage)) {
            return null;
        }
        BusinessDiagnosisDrilldownMatrixRow row =
                BusinessDiagnosisDrilldownMatrix.resolveRowFromMessage(normalizedUserMessage);
        if (row == null
                || (row.getChildDomain() == null && !BusinessDiagnosisDrilldownMatrix.ACTION_FOLLOWUP.equals(row))) {
            return null;
        }
        String wire = row.getStructuredIntentDetailWire();
        if (!StringUtils.hasText(wire)) {
            return null;
        }
        String prevIntent =
                previousTurn != null && StringUtils.hasText(previousTurn.getLastIntentCode())
                        ? previousTurn.getLastIntentCode()
                        : AiResolvedQueryIntent.BUSINESS_DIAGNOSIS;
        return AiResolvedQueryIntent.builder()
                .intentCode(AiResolvedQueryIntent.BUSINESS_DIAGNOSIS)
                .pathCode(AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS)
                .topic("经营诊断")
                .structuredIntentDetail(wire)
                .purchaseSourceType(null)
                .inheritedFromPreviousTurn(true)
                .inheritedFromIntentCode(prevIntent)
                .build();
    }

    public static AiQuerySemanticParseResult buildSyntheticSemanticForBusinessDiagnosisDrilldownContinuation(
            String normalizedUserMessage, AiConversationTurnMemory previousTurn, LocalDate today) {
        BusinessDiagnosisDrilldownMatrixRow row =
                BusinessDiagnosisDrilldownMatrix.resolveRowFromMessage(normalizedUserMessage);
        if (row == null || !StringUtils.hasText(row.getStructuredIntentDetailWire())) {
            return null;
        }
        if (!BusinessDiagnosisDrilldownMatrix.canAdoptDiagnosisDrilldownContinuation(
                previousTurn, normalizedUserMessage)) {
            return null;
        }
        if (row.getChildDomain() == null && !BusinessDiagnosisDrilldownMatrix.ACTION_FOLLOWUP.equals(row)) {
            return null;
        }
        AiQuerySemanticParseResult.TimePart timePart =
                buildMatrixDetailTimePart(normalizedUserMessage, previousTurn, today);
        String operation = row.getChildDomain() != null ? "EXPLAIN" : "ADVISE";
        return AiQuerySemanticParseResult.builder()
                .parseMissing(false)
                .confidence(1.0d)
                .followUp(true)
                .intent(AiResolvedQueryIntent.BUSINESS_DIAGNOSIS)
                .intentAction("INHERIT_PREVIOUS")
                .timeAction(
                        timePart != null
                                        && SemanticTimeContractCheck.SOURCE_INHERITED_PREVIOUS.equals(
                                                timePart.getTimeSource())
                                ? "INHERIT_PREVIOUS"
                                : "NEW")
                .semanticSlots(
                        AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                .queryObject("STORE")
                                .operation(operation)
                                .structuredIntentDetailWire(row.getStructuredIntentDetailWire())
                                .build())
                .time(timePart)
                .build();
    }

    public static AiResolvedQueryIntent buildDishSalesMatrixStoreSingleDishIntent(
            AiConversationTurnMemory previousTurn,
            AiQuerySemanticParseResult sem,
            String normalizedUserMessage) {
        if (!DishSalesDrilldownMatrix.canAdoptDishSalesStoreSingleDishQuestion(
                previousTurn, sem, normalizedUserMessage)) {
            return null;
        }
        return buildDishSalesMatrixPinnedIntent(
                previousTurn,
                normalizedUserMessage,
                AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_STORE_SINGLE_DISH);
    }

    public static AiResolvedQueryIntent buildDishSalesMatrixGroupSingleDishIntent(
            AiConversationTurnMemory previousTurn,
            AiQuerySemanticParseResult sem,
            String normalizedUserMessage) {
        if (!DishSalesDrilldownMatrix.canAdoptDishSalesGroupSingleDishQuestion(
                previousTurn, sem, normalizedUserMessage)) {
            return null;
        }
        return buildDishSalesMatrixPinnedIntent(
                previousTurn,
                normalizedUserMessage,
                AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_SINGLE_DISH);
    }

    public static AiResolvedQueryIntent buildDishSalesMatrixUtterancePinIntent(
            AiConversationTurnMemory previousTurn,
            AiQuerySemanticParseResult sem,
            String normalizedUserMessage) {
        if (!DishSalesDrilldownMatrix.canAdoptDishSalesMatrixUtterancePin(
                previousTurn, sem, normalizedUserMessage)) {
            return null;
        }
        String wire =
                DishSalesDrilldownMatrix.resolveStructuredIntentDetailWire(
                        null,
                        AiResolvedQueryIntent.PATH_DISH_SALES_QUERY,
                        null,
                        normalizedUserMessage,
                        previousTurn);
        if (!StringUtils.hasText(wire)) {
            return null;
        }
        return buildDishSalesMatrixPinnedIntent(previousTurn, normalizedUserMessage, wire);
    }

    public static AiResolvedQueryIntent buildDishSalesMatrixRankingFollowUpIntent(
            AiConversationTurnMemory previousTurn, String normalizedUserMessage) {
        if (!DishSalesDrilldownMatrix.canPinDishSalesPathForRankingFollowUp(previousTurn, normalizedUserMessage)) {
            return null;
        }
        return buildDishSalesMatrixPinnedIntent(
                previousTurn,
                normalizedUserMessage,
                AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH);
    }

    public static AiResolvedQueryIntent buildDishSalesMatrixCrossDomainProfitFollowUpIntent(
            AiConversationTurnMemory previousTurn, String normalizedUserMessage) {
        if (!DishSalesDrilldownMatrix.canAdoptDishSalesMatrixCrossDomainProfitFollowUp(
                previousTurn, normalizedUserMessage)) {
            return null;
        }
        String wire =
                DishSalesDrilldownMatrix.resolveStructuredIntentDetailWire(
                        null,
                        AiResolvedQueryIntent.PATH_DISH_SALES_QUERY,
                        null,
                        normalizedUserMessage,
                        previousTurn);
        if (!StringUtils.hasText(wire)) {
            wire = AiQuerySemanticLexicon.STRUCTURED_DISH_GROSS_MARGIN_QUERY;
        }
        return buildDishSalesMatrixPinnedIntent(previousTurn, normalizedUserMessage, wire);
    }

    /**
     * 销量排行 Top1 DISH 锚承接：短句「毛利是多少？」等切到 {@link AiResolvedQueryIntent#PATH_DISH_PROFIT}，
     * 与 DS-I「那毛利呢」互斥。
     */
    public static AiResolvedQueryIntent buildDishSalesRankingAnchorProfitDrillDownIntent(
            AiConversationTurnMemory previousTurn, String normalizedUserMessage) {
        if (!DishSalesDrilldownMatrix.canAdoptDishSalesRankingAnchorProfitDrillDownFollowUp(
                previousTurn, normalizedUserMessage)) {
            return null;
        }
        String prevIntent =
                previousTurn != null && StringUtils.hasText(previousTurn.getLastIntentCode())
                        ? previousTurn.getLastIntentCode()
                        : AiResolvedQueryIntent.DISH_SALES_QUERY;
        return AiResolvedQueryIntent.builder()
                .intentCode(AiResolvedQueryIntent.DISH_PROFIT)
                .pathCode(AiResolvedQueryIntent.PATH_DISH_PROFIT)
                .topic("菜品毛利/利润")
                .structuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_DISH_GROSS_MARGIN_QUERY)
                .purchaseSourceType(null)
                .inheritedFromPreviousTurn(true)
                .inheritedFromIntentCode(prevIntent)
                .build();
    }

    public static AiQuerySemanticParseResult buildSyntheticSemanticForDishSalesRankingAnchorProfitDrillDown(
            String normalizedUserMessage, AiConversationTurnMemory previousTurn, LocalDate today) {
        if (!DishSalesDrilldownMatrix.canAdoptDishSalesRankingAnchorProfitDrillDownFollowUp(
                previousTurn, normalizedUserMessage)) {
            return null;
        }
        AiResultAnchor anchor =
                DishSalesDrilldownMatrix.resolveUniqueDishSalesRankingAnchor(
                        previousTurn != null ? previousTurn.getLastResultAnchors() : null);
        if (anchor == null) {
            return null;
        }
        AiQuerySemanticParseResult.TimePart timePart =
                buildMatrixDetailTimePart(normalizedUserMessage, previousTurn, today);
        String dishName = StringUtils.hasText(anchor.getEntityName()) ? anchor.getEntityName().trim() : null;
        return AiQuerySemanticParseResult.builder()
                .parseMissing(false)
                .confidence(1.0d)
                .followUp(true)
                .intent(AiResolvedQueryIntent.DISH_PROFIT)
                .intentAction("INHERIT_PREVIOUS")
                .timeAction("INHERIT_PREVIOUS")
                .metricAction("OVERRIDE")
                .mentionedDishName(dishName)
                .semanticSlots(
                        AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                .queryObject("DISH")
                                .operation("DETAIL")
                                .metric("GROSS_MARGIN")
                                .anchorPolicy(AiQuerySemanticSlotMerge.ANCHOR_USE_PREVIOUS)
                                .structuredIntentDetailWire(
                                        AiQuerySemanticLexicon.STRUCTURED_DISH_GROSS_MARGIN_QUERY)
                                .build())
                .time(timePart)
                .build();
    }

    private static AiResolvedQueryIntent buildDishSalesMatrixPinnedIntent(
            AiConversationTurnMemory previousTurn,
            String normalizedUserMessage,
            String defaultWire) {
        String wire =
                DishSalesDrilldownMatrix.resolveStructuredIntentDetailWire(
                        null,
                        AiResolvedQueryIntent.PATH_DISH_SALES_QUERY,
                        null,
                        normalizedUserMessage,
                        previousTurn);
        if (!StringUtils.hasText(wire)) {
            wire = defaultWire;
        }
        String prevIntent =
                previousTurn != null && StringUtils.hasText(previousTurn.getLastIntentCode())
                        ? previousTurn.getLastIntentCode()
                        : AiResolvedQueryIntent.DISH_SALES_QUERY;
        boolean inherited =
                previousTurn != null
                        && StringUtils.hasText(previousTurn.getLastPathCode())
                        && AiResolvedQueryIntent.PATH_DISH_SALES_QUERY.equals(
                                previousTurn.getLastPathCode().trim());
        return AiResolvedQueryIntent.builder()
                .intentCode(AiResolvedQueryIntent.DISH_SALES_QUERY)
                .pathCode(AiResolvedQueryIntent.PATH_DISH_SALES_QUERY)
                .topic("菜品销量/销售额")
                .structuredIntentDetail(wire)
                .purchaseSourceType(null)
                .inheritedFromPreviousTurn(inherited)
                .inheritedFromIntentCode(inherited ? prevIntent : null)
                .build();
    }

    /**
     * V2 parse 失败时的 Matrix 收养用最小语义帧（非 parseMissing，供 {@link com.nongxinle.ai.resolver.SemanticAdoptionAttempt#adopted()}）。
     */
    public static AiQuerySemanticParseResult buildSyntheticSemanticForDishSalesStoreSingleDish(
            String normalizedUserMessage, AiConversationTurnMemory previousTurn, LocalDate today) {
        return buildSyntheticSemanticForDishSalesMatrixDetail(
                normalizedUserMessage,
                previousTurn,
                today,
                AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_STORE_SINGLE_DISH,
                true);
    }

    public static AiQuerySemanticParseResult buildSyntheticSemanticForDishSalesGroupSingleDish(
            String normalizedUserMessage, AiConversationTurnMemory previousTurn, LocalDate today) {
        return buildSyntheticSemanticForDishSalesMatrixDetail(
                normalizedUserMessage,
                previousTurn,
                today,
                AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_SINGLE_DISH,
                false);
    }

    public static AiQuerySemanticParseResult buildSyntheticSemanticForDishSalesMatrixUtterancePin(
            String normalizedUserMessage, AiConversationTurnMemory previousTurn, LocalDate today) {
        String wire =
                DishSalesDrilldownMatrix.resolveStructuredIntentDetailWire(
                        null,
                        AiResolvedQueryIntent.PATH_DISH_SALES_QUERY,
                        null,
                        normalizedUserMessage,
                        previousTurn);
        if (!StringUtils.hasText(wire)) {
            return null;
        }
        boolean storeScoped =
                AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_STORE_SINGLE_DISH.equals(wire)
                        || AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_STORE_RANKING.equals(wire);
        return buildSyntheticSemanticForDishSalesMatrixDetail(
                normalizedUserMessage, previousTurn, today, wire, storeScoped);
    }

    public static AiQuerySemanticParseResult buildSyntheticSemanticForDishSalesRankingFollowUp(
            String normalizedUserMessage, AiConversationTurnMemory previousTurn) {
        String wire =
                DishSalesDrilldownMatrix.resolveStructuredIntentDetailWire(
                        null,
                        AiResolvedQueryIntent.PATH_DISH_SALES_QUERY,
                        null,
                        normalizedUserMessage,
                        previousTurn);
        if (!StringUtils.hasText(wire)) {
            wire = AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH;
        }
        AiQuerySemanticParseResult.TimePart timePart = null;
        if (TimeContractPreviousTurnSupport.hasTurnMemoryDates(previousTurn)) {
            timePart =
                    AiQuerySemanticParseResult.TimePart.builder()
                            .timeType(
                                    StringUtils.hasText(previousTurn.getLastTimeLabel())
                                            ? previousTurn.getLastTimeLabel()
                                            : AiResolvedTimeWindow.CUSTOM)
                            .startDate(previousTurn.getLastStartDate())
                            .endDate(previousTurn.getLastEndDate())
                            .timeSource(SemanticTimeContractCheck.SOURCE_INHERITED_PREVIOUS)
                            .needInheritFromPrevious(true)
                            .build();
        }
        return AiQuerySemanticParseResult.builder()
                .parseMissing(false)
                .confidence(1.0d)
                .followUp(true)
                .intent(AiResolvedQueryIntent.DISH_SALES_QUERY)
                .intentAction("INHERIT_PREVIOUS")
                .timeAction("INHERIT_PREVIOUS")
                .semanticSlots(
                        AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                .queryObject("DISH")
                                .operation("RANKING")
                                .metric("SOLD_PORTIONS")
                                .structuredIntentDetailWire(wire)
                                .build())
                .time(timePart)
                .build();
    }

    private static AiQuerySemanticParseResult buildSyntheticSemanticForDishSalesMatrixDetail(
            String normalizedUserMessage,
            AiConversationTurnMemory previousTurn,
            LocalDate today,
            String defaultWire,
            boolean storeScoped) {
        String wire =
                DishSalesDrilldownMatrix.resolveStructuredIntentDetailWire(
                        null,
                        AiResolvedQueryIntent.PATH_DISH_SALES_QUERY,
                        null,
                        normalizedUserMessage,
                        previousTurn);
        if (!StringUtils.hasText(wire)) {
            wire = defaultWire;
        }
        AiQuerySemanticParseResult.TimePart timePart = buildMatrixDetailTimePart(normalizedUserMessage, previousTurn, today);
        String dishName =
                DishSalesDrilldownMatrix.extractMentionedDishNameFromSingleDishDetailQuestion(normalizedUserMessage);
        AiQuerySemanticParseResult.RequestedScopePart scopePart = null;
        if (storeScoped) {
            String storeLabel =
                    DishSalesDrilldownMatrix.extractMentionedStoreLabelFromQuestion(normalizedUserMessage);
            if (StringUtils.hasText(storeLabel)) {
                scopePart =
                        AiQuerySemanticParseResult.RequestedScopePart.builder()
                                .mentionedStoreName(storeLabel)
                                .build();
            }
        }
        String operation = matrixSlotsOperationForWire(wire);
        AiQuerySemanticParseResult.SemanticSlotsPart slots =
                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                        .queryObject("DISH")
                        .operation(operation)
                        .metric("SOLD_PORTIONS")
                        .structuredIntentDetailWire(wire)
                        .build();
        AiQuerySemanticParseResult.AiQuerySemanticParseResultBuilder builder =
                AiQuerySemanticParseResult.builder()
                        .parseMissing(false)
                        .confidence(1.0d)
                        .followUp(previousTurn != null)
                        .intent(AiResolvedQueryIntent.DISH_SALES_QUERY)
                        .intentAction("NEW")
                        .timeAction(
                                timePart != null
                                                && SemanticTimeContractCheck.SOURCE_INHERITED_PREVIOUS.equals(
                                                        timePart.getTimeSource())
                                        ? "INHERIT_PREVIOUS"
                                        : "NEW")
                        .semanticSlots(slots)
                        .requestedScope(scopePart)
                        .time(timePart);
        if (StringUtils.hasText(dishName)) {
            builder.mentionedDishName(dishName);
        }
        return builder.build();
    }

    private static String matrixSlotsOperationForWire(String wire) {
        if (!StringUtils.hasText(wire)) {
            return "RANKING";
        }
        if (AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_TREND.equals(wire)) {
            return "TREND";
        }
        if (AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_SINGLE_DISH.equals(wire)
                || AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_STORE_SINGLE_DISH.equals(wire)) {
            return "DETAIL";
        }
        return "RANKING";
    }

    private static AiQuerySemanticParseResult.TimePart buildMatrixDetailTimePart(
            String normalizedUserMessage, AiConversationTurnMemory previousTurn, LocalDate today) {
        String msg =
                normalizedUserMessage != null
                        ? normalizedUserMessage.replace(" ", "").replace("\u3000", "")
                        : "";
        if (msg.contains("这个月") || msg.contains("当月")) {
            SemanticTimeContractCheck.Result explicit =
                    SemanticTimeContractCheck.defaultMonthToDateOnAnchor(today);
            if (explicit != null && explicit.valid()) {
                return AiQuerySemanticParseResult.TimePart.builder()
                        .timeType(AiResolvedTimeWindow.THIS_MONTH)
                        .startDate(explicit.normalizedStartDate().toString())
                        .endDate(explicit.normalizedEndDate().toString())
                        .timeSource(SemanticTimeContractCheck.SOURCE_CURRENT_MESSAGE_EXPLICIT)
                        .needInheritFromPrevious(false)
                        .build();
            }
        }
        SemanticTimeContractCheck.Result fallback = SemanticTimeContractCheck.defaultMonthToDateOnAnchor(today);
        if (fallback != null && fallback.valid()) {
            return AiQuerySemanticParseResult.TimePart.builder()
                    .timeType(AiResolvedTimeWindow.THIS_MONTH)
                    .startDate(fallback.normalizedStartDate().toString())
                    .endDate(fallback.normalizedEndDate().toString())
                    .timeSource(SemanticTimeContractCheck.SOURCE_DEFAULT_MONTH_TO_DATE)
                    .needInheritFromPrevious(false)
                    .build();
        }
        return null;
    }

    /**
     * Phase1-F：本轮 V2 已显式路由到非销量域时，销量 Matrix pin / ranking follow-up 不得覆盖 effective intent/path。
     */
    public static boolean blocksDishSalesMatrixOverride(AiQuerySemanticParseResult sem) {
        if (sem == null) {
            return false;
        }
        if (hasExplicitStockReduceRouteSignal(sem)) {
            return true;
        }
        if (hasExplicitRevenueRouteSignal(sem)) {
            return true;
        }
        if (hasExplicitWarehouseRouteSignal(sem)) {
            return true;
        }
        if (v2MapsToExplicitDishProfitPath(sem)) {
            return true;
        }
        if (hasExplicitBusinessOverviewRouteSignal(sem) || hasExplicitBusinessDiagnosisRouteSignal(sem)) {
            return true;
        }
        if (shouldUsePurchaseSemanticFrameAdoption(sem)) {
            return true;
        }
        WireIntent mapped = mapLlmIntent(sem.getIntent());
        return mapped != null
                && !AiResolvedQueryIntent.PATH_DISH_SALES_QUERY.equals(mapped.pathCode());
    }

    private static AiResolvedQueryIntent pinDishSalesPathForMatrixRankingFollowUp(
            AiResolvedQueryIntent merged,
            AiConversationTurnMemory previousTurn,
            String normalizedUserMessage,
            AiQuerySemanticParseResult sem) {
        if (blocksDishSalesMatrixOverride(sem)) {
            return merged;
        }
        AiResolvedQueryIntent pinned =
                buildDishSalesMatrixRankingFollowUpIntent(previousTurn, normalizedUserMessage);
        if (pinned == null) {
            return merged;
        }
        if (merged == null || !StringUtils.hasText(merged.getPathCode())) {
            return pinned;
        }
        if (!AiResolvedQueryIntent.PATH_DISH_SALES_QUERY.equals(merged.getPathCode())) {
            return merged;
        }
        if (!StringUtils.hasText(merged.getStructuredIntentDetail())
                && StringUtils.hasText(pinned.getStructuredIntentDetail())) {
            return AiResolvedQueryIntent.builder()
                    .intentCode(merged.getIntentCode())
                    .pathCode(merged.getPathCode())
                    .topic(merged.getTopic())
                    .structuredIntentDetail(pinned.getStructuredIntentDetail())
                    .purchaseSourceType(merged.getPurchaseSourceType())
                    .inheritedFromPreviousTurn(merged.isInheritedFromPreviousTurn())
                    .inheritedFromIntentCode(merged.getInheritedFromIntentCode())
                    .build();
        }
        return merged;
    }

    private static void applyBusinessDiagnosisStructuredWireFromMessage(
            AiResolvedQueryIntent qi, String normalizedUserMessage) {
        if (qi == null || !AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS.equals(qi.getPathCode())) {
            return;
        }
        BusinessDiagnosisDrilldownMatrixRow msgRow =
                BusinessDiagnosisDrilldownMatrix.resolveRowFromMessage(normalizedUserMessage);
        if (msgRow == null || !StringUtils.hasText(msgRow.getStructuredIntentDetailWire())) {
            return;
        }
        if (BusinessDiagnosisDrilldownMatrix.shouldPreferMessageRowOverWire(
                qi.getStructuredIntentDetail(), msgRow)) {
            qi.setStructuredIntentDetail(msgRow.getStructuredIntentDetailWire());
        }
    }

    private static AiResolvedQueryIntent pinBusinessDiagnosisPathForDrilldownContinuation(
            AiResolvedQueryIntent merged, AiConversationTurnMemory previousTurn, String normalizedUserMessage) {
        AiResolvedQueryIntent pinned =
                buildBusinessDiagnosisDrilldownContinuationIntent(previousTurn, normalizedUserMessage);
        return pinned != null ? pinned : merged;
    }

    private static AiResolvedQueryIntent pinDishSalesPathForMatrixCrossDomainProfitFollowUp(
            AiResolvedQueryIntent merged,
            AiConversationTurnMemory previousTurn,
            String normalizedUserMessage,
            AiQuerySemanticParseResult sem) {
        if (blocksDishSalesMatrixOverride(sem)) {
            return merged;
        }
        if (merged == null || previousTurn == null || !StringUtils.hasText(previousTurn.getLastPathCode())) {
            return merged;
        }
        if (!AiResolvedQueryIntent.PATH_DISH_SALES_QUERY.equals(previousTurn.getLastPathCode().trim())) {
            return merged;
        }
        if (!DishSalesDrilldownMatrix.isCrossDomainProfitFollowupMessage(normalizedUserMessage)) {
            return merged;
        }
        return AiResolvedQueryIntent.builder()
                .intentCode(AiResolvedQueryIntent.DISH_SALES_QUERY)
                .pathCode(AiResolvedQueryIntent.PATH_DISH_SALES_QUERY)
                .topic("菜品销量/销售额")
                .structuredIntentDetail(merged.getStructuredIntentDetail())
                .purchaseSourceType(merged.getPurchaseSourceType())
                .inheritedFromPreviousTurn(merged.isInheritedFromPreviousTurn())
                .inheritedFromIntentCode(merged.getInheritedFromIntentCode())
                .build();
    }

    public static boolean mapsToPurchaseOverviewPath(String llmIntent) {
        if (!StringUtils.hasText(llmIntent)) {
            return false;
        }
        String u = llmIntent.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (u) {
            case "PURCHASE_OVERVIEW", "PROCUREMENT_OVERVIEW", "PURCHASE" -> true;
            default -> false;
        };
    }

    /**
     * 本轮 V2 {@code intent} 经 {@link #mapLlmIntent} 已路由到非 {@code purchase_overview_path} 的专线；
     * 采购 {@link CurrentSemanticFrameValidator} 门禁不得介入（典型：采购后切回 {@code BUSINESS_OVERVIEW}）。
     */
    public static boolean currentTurnMapsToExplicitNonPurchasePath(AiQuerySemanticParseResult sem) {
        if (sem == null || sem.isParseMissing()) {
            return false;
        }
        WireIntent mapped = mapLlmIntent(sem.getIntent());
        return mapped != null
                && !AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW.equals(mapped.pathCode());
    }

    /**
     * Resolver 采购 frame 校验门禁：仅当本轮 V2 JSON 显式给出采购信号时才进入 {@link CurrentSemanticFrameValidator}；
     * 不用 {@code metric.rankingType}、仅凭上一轮 path 或用户话术推断采购域。
     * <p>显式信号：顶层 {@code domain=PURCHASE}、{@code intent} 为采购 overview、编排 {@code purchase_overview}、或
     * {@code semanticSlots.structuredIntentDetailWire} canonical 后为采购 overview wire。</p>
     */
    public static boolean shouldUsePurchaseSemanticFrameAdoption(AiQuerySemanticParseResult sem) {
        if (sem == null || sem.isParseMissing()) {
            return false;
        }
        if (currentTurnMapsToExplicitNonPurchasePath(sem)) {
            return false;
        }
        WireIntent mappedIntentPath = mapLlmIntent(sem.getIntent());
        if (mappedIntentPath != null
                && !AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW.equals(mappedIntentPath.pathCode())) {
            return false;
        }
        if (explicitSemanticDomainPurchase(sem)) {
            return true;
        }
        if (mapsToPurchaseOverviewPath(sem.getIntent())) {
            return true;
        }
        AiQuerySemanticParseResult.OrchestrationDecisionCandidatePart orch = sem.getOrchestrationDecisionCandidate();
        if (orch != null && orch.getSelectedTools() != null) {
            for (String t : orch.getSelectedTools()) {
                if (t != null && "purchase_overview".equalsIgnoreCase(t.trim())) {
                    return true;
                }
            }
        }
        return explicitPurchaseOverviewWireInSemanticSlots(sem);
    }

    private static boolean explicitSemanticDomainPurchase(AiQuerySemanticParseResult sem) {
        if (sem == null || !StringUtils.hasText(sem.getSemanticDomain())) {
            return false;
        }
        String u = sem.getSemanticDomain().trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return "PURCHASE".equals(u);
    }

    /** {@code semanticSlots.structuredIntentDetailWire} canonical 落在采购 overview wire 集合（LLM 显式输出）。 */
    private static boolean explicitPurchaseOverviewWireInSemanticSlots(AiQuerySemanticParseResult sem) {
        if (sem == null || sem.getSemanticSlots() == null) {
            return false;
        }
        String w = sem.getSemanticSlots().getStructuredIntentDetailWire();
        if (!StringUtils.hasText(w)) {
            return false;
        }
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(w.trim());
        return AiQuerySemanticLexicon.isPurchaseOverviewDomainCanonicalWire(canon);
    }

    /**
     * {@code semanticSlots.sourceFacet} 或 slots wire 已明确时，compat {@code metric.purchaseSourceType} 不得再覆盖 {@code qi.purchaseSourceType}。
     */
    public static boolean purchaseSemanticChannelLockedBySlots(AiQuerySemanticParseResult sem) {
        if (sem == null) {
            return false;
        }
        if (AiQuerySemanticSlotMerge.hasPurchaseStructuredIntentWireFromSlots(sem)) {
            return true;
        }
        return semanticSlotsHaveExplicitNonUnknownSourceFacet(sem);
    }

    private static boolean semanticSlotsHaveExplicitNonUnknownSourceFacet(AiQuerySemanticParseResult sem) {
        if (sem == null || sem.getSemanticSlots() == null) {
            return false;
        }
        String sf = sem.getSemanticSlots().getSourceFacet();
        if (!StringUtils.hasText(sf)) {
            return false;
        }
        String u = sf.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return !AiQuerySemanticSlotMerge.UNKNOWN.equals(u);
    }

    private static void applyPurchaseStructuredWireFromSemanticSlots(
            AiResolvedQueryIntent qi, AiQuerySemanticParseResult sem) {
        if (qi == null || sem == null) {
            return;
        }
        if (!AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW.equals(qi.getPathCode())) {
            return;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart ss = sem.getSemanticSlots();
        if (ss == null || !StringUtils.hasText(ss.getStructuredIntentDetailWire())) {
            return;
        }
        String canon =
                AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(ss.getStructuredIntentDetailWire().trim());
        if (StringUtils.hasText(canon)) {
            qi.setStructuredIntentDetail(canon);
        }
        String pstFacet = purchaseSourceTypeFromSemanticSourceFacet(ss.getSourceFacet());
        if (pstFacet != null) {
            qi.setPurchaseSourceType(pstFacet);
        }
    }

    private static String purchaseSourceTypeFromSemanticSourceFacet(String raw) {
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

    /** metric.stockReduceType=ALL 表示「全部出库类型」口径，不得覆盖 structured wire。 */
    private static boolean stockReduceTypeIsAllTypesFacetToken(String raw) {
        if (!StringUtils.hasText(raw)) {
            return false;
        }
        String u = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return AiQuerySemanticLexicon.SOURCE_ALL.equals(u) || "ALL".equals(u);
    }

    /**
     * 本句 V2 已明确出库/核销路由信号（intent/path、domain、slots wire 或 stockReduceType facet）。
     * Resolver 用于禁止误入采购 frame 校验；merge 用于禁止 {@code intentAction=INHERIT_PREVIOUS} 钉死上一轮采购 path
     *（典型：采购后「那核销呢？」）。
     */
    public static boolean hasExplicitBusinessOverviewRouteSignal(AiQuerySemanticParseResult sem) {
        if (sem == null) {
            return false;
        }
        WireIntent mapped = mapLlmIntent(sem.getIntent());
        if (mapped != null && AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW.equals(mapped.pathCode())) {
            return true;
        }
        if (StringUtils.hasText(sem.getSemanticDomain())) {
            String d = sem.getSemanticDomain().trim().toUpperCase(Locale.ROOT).replace('-', '_');
            if ("BUSINESS".equals(d) || "BUSINESS_OVERVIEW".equals(d) || "OPERATIONS".equals(d)) {
                return true;
            }
        }
        AiQuerySemanticParseResult.SemanticSlotsPart ss = sem.getSemanticSlots();
        if (ss != null && StringUtils.hasText(ss.getStructuredIntentDetailWire())) {
            String c =
                    AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                            ss.getStructuredIntentDetailWire().trim());
            if (StringUtils.hasText(c)
                    && AiQuerySemanticLexicon.isStructuredBusinessOverviewFourDomainOrchestrationSurface(c)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasExplicitBusinessDiagnosisRouteSignal(AiQuerySemanticParseResult sem) {
        if (sem == null) {
            return false;
        }
        WireIntent mapped = mapLlmIntent(sem.getIntent());
        if (mapped != null && AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS.equals(mapped.pathCode())) {
            return true;
        }
        if (StringUtils.hasText(sem.getSemanticDomain())) {
            String d = sem.getSemanticDomain().trim().toUpperCase(Locale.ROOT).replace('-', '_');
            if ("BUSINESS_DIAGNOSIS".equals(d)) {
                return true;
            }
        }
        AiQuerySemanticParseResult.SemanticSlotsPart ss = sem.getSemanticSlots();
        if (ss != null && StringUtils.hasText(ss.getStructuredIntentDetailWire())) {
            String c =
                    AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                            ss.getStructuredIntentDetailWire().trim());
            if (StringUtils.hasText(c) && AiQuerySemanticLexicon.isStructuredBusinessDiagnosisDetail(c)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasExplicitRevenueRouteSignal(AiQuerySemanticParseResult sem) {
        if (sem == null) {
            return false;
        }
        if (hasExplicitBusinessOverviewRouteSignal(sem) || hasExplicitBusinessDiagnosisRouteSignal(sem)) {
            return false;
        }
        WireIntent mapped = mapLlmIntent(sem.getIntent());
        if (mapped != null && AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW.equals(mapped.pathCode())) {
            return true;
        }
        if (StringUtils.hasText(sem.getSemanticDomain())) {
            String d = sem.getSemanticDomain().trim().toUpperCase(Locale.ROOT).replace('-', '_');
            if ("REVENUE".equals(d)) {
                return true;
            }
        }
        AiQuerySemanticParseResult.SemanticSlotsPart ss = sem.getSemanticSlots();
        if (ss != null && StringUtils.hasText(ss.getStructuredIntentDetailWire())) {
            String c =
                    AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                            ss.getStructuredIntentDetailWire().trim());
            if (StringUtils.hasText(c) && AiQuerySemanticLexicon.isStructuredRevenueDetail(c)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasExplicitWarehouseRouteSignal(AiQuerySemanticParseResult sem) {
        if (sem == null) {
            return false;
        }
        if (hasExplicitStockReduceRouteSignal(sem)) {
            return false;
        }
        if (hasExplicitBusinessOverviewRouteSignal(sem) || hasExplicitBusinessDiagnosisRouteSignal(sem)) {
            return false;
        }
        WireIntent mapped = mapLlmIntent(sem.getIntent());
        if (mapped != null && AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK.equals(mapped.pathCode())) {
            return true;
        }
        if (StringUtils.hasText(sem.getSemanticDomain())) {
            String d = sem.getSemanticDomain().trim().toUpperCase(Locale.ROOT).replace('-', '_');
            if ("WAREHOUSE".equals(d) || "WAREHOUSE_STOCK".equals(d) || "INVENTORY".equals(d)) {
                return true;
            }
        }
        AiQuerySemanticParseResult.SemanticSlotsPart ss = sem.getSemanticSlots();
        if (ss != null && StringUtils.hasText(ss.getStructuredIntentDetailWire())) {
            String c =
                    AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                            ss.getStructuredIntentDetailWire().trim());
            if (StringUtils.hasText(c) && AiQuerySemanticLexicon.isStructuredWarehouseStockDetail(c)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasExplicitDishSalesRouteSignal(AiQuerySemanticParseResult sem) {
        if (sem == null) {
            return false;
        }
        if (hasExplicitStockReduceRouteSignal(sem)) {
            return false;
        }
        if (hasExplicitBusinessOverviewRouteSignal(sem) || hasExplicitBusinessDiagnosisRouteSignal(sem)) {
            return false;
        }
        if (v2MapsToExplicitDishProfitPath(sem)) {
            return false;
        }
        WireIntent mapped = mapLlmIntent(sem.getIntent());
        if (mapped != null && AiResolvedQueryIntent.PATH_DISH_SALES_QUERY.equals(mapped.pathCode())) {
            return true;
        }
        if (StringUtils.hasText(sem.getSemanticDomain())) {
            String d = sem.getSemanticDomain().trim().toUpperCase(Locale.ROOT).replace('-', '_');
            if ("DISH_SALES".equals(d)) {
                return true;
            }
        }
        AiQuerySemanticParseResult.SemanticSlotsPart ss = sem.getSemanticSlots();
        if (ss != null && StringUtils.hasText(ss.getStructuredIntentDetailWire())) {
            String c =
                    AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                            ss.getStructuredIntentDetailWire().trim());
            if (StringUtils.hasText(c) && AiQuerySemanticLexicon.isStructuredDishSalesDetail(c)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasExplicitStockReduceRouteSignal(AiQuerySemanticParseResult sem) {
        if (sem == null) {
            return false;
        }
        if (hasExplicitBusinessOverviewRouteSignal(sem) || hasExplicitBusinessDiagnosisRouteSignal(sem)) {
            return false;
        }
        WireIntent mapped = mapLlmIntent(sem.getIntent());
        if (mapped != null && AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY.equals(mapped.pathCode())) {
            return true;
        }
        if (StringUtils.hasText(sem.getSemanticDomain())) {
            String d = sem.getSemanticDomain().trim().toUpperCase(Locale.ROOT).replace('-', '_');
            if ("STOCK_REDUCE".equals(d)
                    || "STOCK_OUT".equals(d)
                    || "WRITE_OFF".equals(d)) {
                return true;
            }
        }
        AiQuerySemanticParseResult.SemanticSlotsPart ss = sem.getSemanticSlots();
        if (ss != null && StringUtils.hasText(ss.getStructuredIntentDetailWire())) {
            String c = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(ss.getStructuredIntentDetailWire().trim());
            if (StringUtils.hasText(c) && AiQuerySemanticLexicon.isStructuredStockReduceDetail(c)) {
                return true;
            }
        }
        if (sem.getMetric() != null && StringUtils.hasText(sem.getMetric().getStockReduceType())) {
            String raw = sem.getMetric().getStockReduceType().trim();
            if (!stockReduceTypeIsAllTypesFacetToken(raw)) {
                String c =
                        AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(raw);
                if (StringUtils.hasText(c) && AiQuerySemanticLexicon.isStructuredStockReduceDetail(c)) {
                    return true;
                }
            }
        }
        AiQuerySemanticParseResult.OrchestrationDecisionCandidatePart orch = sem.getOrchestrationDecisionCandidate();
        if (orch != null && orch.getSelectedTools() != null) {
            for (String t : orch.getSelectedTools()) {
                if (t != null && "stock_reduce_query".equalsIgnoreCase(t.trim())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * stock_reduce_query_path：Matrix + semanticSlots 驱动 wire 收口（不以 rankingType / 问句 contains 抢权）。
     */
    private static void applyStockReduceStructuredWireFromSemanticSlots(
            AiResolvedQueryIntent qi, AiQuerySemanticParseResult sem) {
        if (qi == null || sem == null) {
            return;
        }
        if (!AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY.equals(qi.getPathCode())) {
            return;
        }
        String resolved =
                StockReduceDrilldownMatrix.resolveStructuredIntentDetailWire(
                        sem, qi.getPathCode(), qi.getStructuredIntentDetail());
        if (StringUtils.hasText(resolved)) {
            qi.setStructuredIntentDetail(resolved);
        }
    }

    /**
     * 本轮 {@code semanticSlots.structuredIntentDetailWire} 已 canonical 时，落到 {@code queryIntent}，
     * 避免后续 merge 或 {@code intentAction=INHERIT_PREVIOUS} 用空 structured 或上轮形态覆盖（如双域 {@code purchase_stock_reduce_mismatch}）。
     */
    private static void applyCanonicalStructuredIntentDetailWireFromSemanticSlots(
            AiResolvedQueryIntent qi, AiQuerySemanticParseResult sem) {
        if (qi == null || sem == null) {
            return;
        }
        if (!AiQuerySemanticSlotMerge.hasCanonicalStructuredIntentWireFromSlots(sem)) {
            return;
        }
        String canon =
                AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                        sem.getSemanticSlots().getStructuredIntentDetailWire().trim());
        if (!StringUtils.hasText(canon)) {
            return;
        }
        if (AiQuerySemanticLexicon.isStructuredBusinessOverviewFourDomainOrchestrationSurface(canon)
                && !AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW.equals(qi.getPathCode())) {
            return;
        }
        if (AiQuerySemanticLexicon.isStructuredBusinessDiagnosisDetail(canon)
                && !AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS.equals(qi.getPathCode())) {
            return;
        }
        if (AiQuerySemanticLexicon.isStructuredStockReduceDetail(canon)
                && !AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY.equals(qi.getPathCode())) {
            return;
        }
        if (AiQuerySemanticLexicon.isStructuredWarehouseStockDetail(canon)
                && !AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK.equals(qi.getPathCode())) {
            return;
        }
        if (AiQuerySemanticLexicon.isStructuredDishSalesDetail(canon)
                && !AiResolvedQueryIntent.PATH_DISH_SALES_QUERY.equals(qi.getPathCode())) {
            return;
        }
        if (AiQuerySemanticLexicon.isStructuredRevenueDetail(canon)
                && !AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW.equals(qi.getPathCode())) {
            return;
        }
        if (AiQuerySemanticLexicon.isNonOverviewDishProfitStructuredDetail(canon)
                && !AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(qi.getPathCode())) {
            return;
        }
        if (AiQuerySemanticLexicon.isPurchaseOverviewDomainCanonicalWire(canon)
                && !AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW.equals(qi.getPathCode())) {
            return;
        }
        qi.setStructuredIntentDetail(canon);
    }

    private static void applyBusinessOverviewStructuredWireFromSemanticSlots(
            AiResolvedQueryIntent qi, AiQuerySemanticParseResult sem) {
        if (qi == null || sem == null) {
            return;
        }
        if (!AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW.equals(qi.getPathCode())) {
            return;
        }
        String resolved =
                BusinessOverviewDrilldownMatrix.resolveStructuredIntentDetailWire(
                        sem,
                        AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW,
                        qi.getStructuredIntentDetail());
        if (StringUtils.hasText(resolved)
                && !BusinessOverviewDrilldownMatrix.isMatrixWireMissing(resolved)) {
            qi.setStructuredIntentDetail(resolved);
        }
    }

    private static void applyBusinessDiagnosisStructuredWireFromSemanticSlots(
            AiResolvedQueryIntent qi, AiQuerySemanticParseResult sem, String normalizedUserMessage) {
        if (qi == null || sem == null) {
            return;
        }
        if (!AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS.equals(qi.getPathCode())) {
            return;
        }
        String resolved =
                BusinessDiagnosisDrilldownMatrix.resolveStructuredIntentDetailWire(
                        sem, AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS, qi.getStructuredIntentDetail());
        if (StringUtils.hasText(resolved)
                && !BusinessDiagnosisDrilldownMatrix.isMatrixWireMissing(resolved)) {
            qi.setStructuredIntentDetail(resolved);
        }
        BusinessDiagnosisDrilldownMatrixRow msgRow =
                BusinessDiagnosisDrilldownMatrix.resolveRowFromMessage(normalizedUserMessage);
        if (msgRow != null
                && StringUtils.hasText(msgRow.getStructuredIntentDetailWire())
                && BusinessDiagnosisDrilldownMatrix.shouldPreferMessageRowOverWire(
                        qi.getStructuredIntentDetail(), msgRow)) {
            qi.setStructuredIntentDetail(msgRow.getStructuredIntentDetailWire());
        }
    }

    /**
     * revenue_overview_path：Matrix P1 驱动 wire 收口（环比/日峰/趋势不得 silent fallback 为门店排行）。
     */
    private static void applyRevenueStructuredWireFromSemanticSlots(
            AiResolvedQueryIntent qi, AiQuerySemanticParseResult sem, String normalizedUserMessage) {
        if (qi == null || sem == null) {
            return;
        }
        if (!AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW.equals(qi.getPathCode())) {
            return;
        }
        String resolved =
                RevenueDrilldownMatrix.resolveStructuredIntentDetailWire(
                        sem, qi.getPathCode(), qi.getStructuredIntentDetail(), normalizedUserMessage);
        if (StringUtils.hasText(resolved)) {
            qi.setStructuredIntentDetail(resolved);
        }
    }

    /**
     * dish_profit_path：矩阵驱动 wire 收口（点名单菜覆盖排行 inherit；DISH 锚原料构成 canonical）。
     */
    /**
     * dish_sales_query_path：Matrix P1 驱动 wire 收口（最低销量 / 跨域毛利追问不得 silent fallback 为毛利排行）。
     */
    private static void applyDishSalesStructuredWireFromSemanticSlots(
            AiResolvedQueryIntent qi,
            AiQuerySemanticParseResult sem,
            String normalizedUserMessage,
            AiConversationTurnMemory previousTurn) {
        if (qi == null || sem == null) {
            return;
        }
        if (!AiResolvedQueryIntent.PATH_DISH_SALES_QUERY.equals(qi.getPathCode())) {
            return;
        }
        String resolved =
                DishSalesDrilldownMatrix.resolveStructuredIntentDetailWire(
                        sem, qi.getPathCode(), qi.getStructuredIntentDetail(), normalizedUserMessage, previousTurn);
        if (StringUtils.hasText(resolved)) {
            qi.setStructuredIntentDetail(resolved);
        }
    }

    /** V2 已明确路由到 {@link AiResolvedQueryIntent#PATH_DISH_PROFIT} 时，销量 utterance pin 不得覆盖。 */
    public static boolean v2MapsToExplicitDishProfitPath(AiQuerySemanticParseResult sem) {
        if (sem == null || sem.isParseMissing()) {
            return false;
        }
        WireIntent mapped = mapLlmIntent(sem.getIntent());
        return mapped != null && AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(mapped.pathCode());
    }

    private static boolean v2MapsToExplicitDishProfitPath(
            AiQuerySemanticParseResult sem, double minConfidence) {
        if (sem == null || sem.isParseMissing() || !sem.isStructuralConfidenceOk(minConfidence)) {
            return false;
        }
        return v2MapsToExplicitDishProfitPath(sem);
    }

    private static void applyDishProfitStructuredWireFromSemanticSlots(
            AiResolvedQueryIntent qi, AiQuerySemanticParseResult sem) {
        if (qi == null || sem == null) {
            return;
        }
        if (!AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(qi.getPathCode())) {
            return;
        }
        String resolved =
                DishProfitDrilldownMatrix.resolveStructuredIntentDetailWire(
                        sem, qi.getPathCode(), qi.getStructuredIntentDetail());
        if (StringUtils.hasText(resolved)) {
            qi.setStructuredIntentDetail(resolved);
        }
    }

    private static void applyWarehouseStockStructuredWireFromSemanticSlots(
            AiResolvedQueryIntent qi, AiQuerySemanticParseResult sem) {
        if (qi == null || !AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK.equals(qi.getPathCode())) {
            return;
        }
        String resolved = resolveWarehouseStockStructuredWire(sem, qi.getStructuredIntentDetail());
        if (StringUtils.hasText(resolved)) {
            qi.setStructuredIntentDetail(resolved);
        }
    }

    /**
     * 库房现量 path 下：slots wire 不得落出库域 {@code stock_reduce_*}；缺省时默认 {@code warehouse_stock_overview}。
     */
    private static String resolveWarehouseStockStructuredWire(
            AiQuerySemanticParseResult sem, String currentStructuredDetail) {
        String resolved =
                WarehouseDrilldownMatrix.resolveStructuredIntentDetailWire(
                        sem,
                        AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK,
                        currentStructuredDetail);
        if (StringUtils.hasText(resolved)) {
            return resolved;
        }
        if (!AiQuerySemanticSlotMerge.hasCanonicalStructuredIntentWireFromSlots(sem)) {
            String fromRanking = warehouseStructuredWireFromMetricRankingType(sem);
            if (StringUtils.hasText(fromRanking)) {
                return fromRanking;
            }
        }
        return null;
    }

    private static String warehouseStructuredWireFromSemanticSlots(AiQuerySemanticParseResult sem) {
        if (sem == null || sem.getSemanticSlots() == null) {
            return null;
        }
        String raw = sem.getSemanticSlots().getStructuredIntentDetailWire();
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(raw.trim());
        if (AiQuerySemanticLexicon.isStructuredWarehouseStockDetail(canon)) {
            return canon;
        }
        if (AiQuerySemanticLexicon.isStructuredStockReduceDetail(canon)) {
            return AiQuerySemanticLexicon.STRUCTURED_WAREHOUSE_STOCK_OVERVIEW;
        }
        return null;
    }

    private static String warehouseStructuredWireFromMetricRankingType(AiQuerySemanticParseResult sem) {
        if (sem == null || sem.getMetric() == null) {
            return null;
        }
        String raw = sem.getMetric().getRankingType();
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(raw.trim());
        return AiQuerySemanticLexicon.isStructuredWarehouseStockDetail(canon) ? canon : null;
    }

    /**
     * @deprecated 保留签名兼容；时间合并仅读 V2 {@code timeAction} / {@code time} 结构化字段。
     */
    @Deprecated
    public static AiResolvedTimeWindow mergeTentativeTime(
            AiResolvedTimeWindow tentativeTime,
            AiQuerySemanticParseResult sem,
            LocalDate today,
            double minConfidence) {
        return mergeTentativeTime(tentativeTime, sem, today, minConfidence, null, null, null);
    }

    /**
     * @deprecated 保留签名兼容；{@code normalizedUserMessage} / {@code mergedIntentHint} 不参与时间合并。
     */
    @Deprecated
    public static AiResolvedTimeWindow mergeTentativeTime(
            AiResolvedTimeWindow tentativeTime,
            AiQuerySemanticParseResult sem,
            LocalDate today,
            double minConfidence,
            String normalizedUserMessage,
            AiResolvedQueryIntent mergedIntentHint) {
        return mergeTentativeTime(tentativeTime, sem, today, minConfidence, normalizedUserMessage, mergedIntentHint, null);
    }

    /**
     * 从 V2 LLM {@code time} 块镜像候选时间窗；不做自然语言解析或 Java 语义重判。
     * 最终落地以 {@link SemanticTimeContractCheck} 通过后的结果为准。
     */
    public static AiResolvedTimeWindow mergeTentativeTime(
            AiResolvedTimeWindow tentativeTime,
            AiQuerySemanticParseResult sem,
            LocalDate today,
            double minConfidence,
            String normalizedUserMessage,
            AiResolvedQueryIntent mergedIntentHint,
            AiConversationTurnMemory previousTurn) {
        if (sem == null || sem.isParseMissing() || !sem.isStructuralConfidenceOk(minConfidence)) {
            return tentativeTime;
        }
        AiQuerySemanticParseResult.TimePart tp = sem.getTime();
        if (tp == null) {
            return tentativeTime;
        }
        LocalDate sd = AiResolvedTimeWindow.parseIsoDateOrNull(tp.getStartDate());
        LocalDate ed = AiResolvedTimeWindow.parseIsoDateOrNull(tp.getEndDate());
        if (sd == null || ed == null) {
            return tentativeTime;
        }
        String label = AiResolvedTimeWindow.normalizeSemanticTimeTypeLabel(tp.getTimeType());
        if (!StringUtils.hasText(label)) {
            label = AiResolvedTimeWindow.CUSTOM;
        }
        String src = SemanticTimeContractCheck.normalizeProductionTimeSource(tp.getTimeSource());
        boolean inherited = SemanticTimeContractCheck.SOURCE_INHERITED_PREVIOUS.equals(src);
        boolean explicit = SemanticTimeContractCheck.SOURCE_CURRENT_MESSAGE_EXPLICIT.equals(src);
        return AiResolvedTimeWindow.builder()
                .timeLabel(label)
                .startDate(sd)
                .endDate(ed)
                .displayText(sd + "～" + ed)
                .inheritedFromPreviousTurn(inherited)
                .explicitTimeMentioned(explicit)
                .build();
    }

    /** 语义 LLM intentAction/timeAction… 等大写归一（仅合并层使用）。 */
    private static String semanticActionNormalize(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        return raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private static AiResolvedQueryIntent copyIntent(AiResolvedQueryIntent in) {
        if (in == null) {
            return AiResolvedQueryIntent.builder().build();
        }
        return AiResolvedQueryIntent.builder()
                .intentCode(in.getIntentCode())
                .pathCode(in.getPathCode())
                .topic(in.getTopic())
                .structuredIntentDetail(in.getStructuredIntentDetail())
                .purchaseSourceType(in.getPurchaseSourceType())
                .inheritedFromPreviousTurn(in.isInheritedFromPreviousTurn())
                .inheritedFromIntentCode(in.getInheritedFromIntentCode())
                .build();
    }

    private record WireIntent(String intentCode, String pathCode, String topic) {
    }

    private static WireIntent mapLlmIntent(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String u = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (u) {
            case "BUSINESS_OVERVIEW", "OPERATIONS_OVERVIEW" -> new WireIntent(
                    AiResolvedQueryIntent.BUSINESS_OVERVIEW,
                    AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW,
                    "经营概览");
            case "REVENUE_OVERVIEW", "REVENUE" -> new WireIntent(
                    AiResolvedQueryIntent.REVENUE_OVERVIEW,
                    AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW,
                    "营业额/营收");
            case "PURCHASE_OVERVIEW", "PROCUREMENT_OVERVIEW", "PURCHASE" -> new WireIntent(
                    AiResolvedQueryIntent.PURCHASE_OVERVIEW,
                    AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW,
                    "采购概览");
            case "WAREHOUSE_STOCK_OVERVIEW", "STOCK_OVERVIEW", "WAREHOUSE_OVERVIEW", "STOCK_QUERY" ->
                    new WireIntent(
                            AiResolvedQueryIntent.WAREHOUSE_STOCK_OVERVIEW,
                            AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK,
                            "库存概览");
            case "STOCK_REDUCE_QUERY", "STOCK_OUT", "WRITE_OFF" -> new WireIntent(
                    AiResolvedQueryIntent.STOCK_REDUCE_QUERY,
                    AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY,
                    "出库/核销查询");
            case "DISH_PROFIT", "DISH_MARGIN" -> new WireIntent(
                    AiResolvedQueryIntent.DISH_PROFIT,
                    AiResolvedQueryIntent.PATH_DISH_PROFIT,
                    "菜品毛利/利润");
            case "DISH_SALES_QUERY" -> new WireIntent(
                    AiResolvedQueryIntent.DISH_SALES_QUERY,
                    AiResolvedQueryIntent.PATH_DISH_SALES_QUERY,
                    "菜品销量/销售额");
            case "COST_DIAGNOSIS", "COST_DIAG" -> new WireIntent(
                    AiResolvedQueryIntent.COST_DIAGNOSIS,
                    AiResolvedQueryIntent.PATH_COST_DIAGNOSIS,
                    "成本诊断");
            case "BUSINESS_DIAGNOSIS" -> new WireIntent(
                    AiResolvedQueryIntent.BUSINESS_DIAGNOSIS,
                    AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS,
                    "经营诊断");
            default -> null;
        };
    }
}
