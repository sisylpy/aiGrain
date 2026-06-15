package com.nongxinle.ai.platform;

import com.alibaba.fastjson2.JSON;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.capability.dish.DishCostAnalysisCapabilityResult;
import com.nongxinle.ai.capability.dish.DishSalesAnalysisCapabilityResult;
import com.nongxinle.ai.graph.business.GoodsStockBatchDetailCardSupport;
import com.nongxinle.ai.graph.business.WarehouseGoodsAnchorAnswerPlanCardWireSupport;
import com.nongxinle.ai.graph.business.DishIngredientCoverAnswerPlanCardSupport;
import com.nongxinle.ai.graph.business.DishProfitAnswerPlanCardSupport;
import com.nongxinle.ai.graph.business.DishProfitPrescriptionAnswerPlanCardSupport;
import com.nongxinle.ai.graph.business.DishSalesAnswerPlanCardSupport;
import com.nongxinle.ai.graph.business.WarehouseAnswerPlanCardSupport;
import com.nongxinle.ai.composer.menu.MenuExpertPresentationPlan;
import com.nongxinle.ai.composer.menu.MenuExpertPresentationPlanCardSupport;
import com.nongxinle.ai.dto.business.DishProfitAnswerPlan;
import com.nongxinle.ai.dto.business.DishSalesAnswerPlan;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan;
import com.nongxinle.ai.dto.business.PurchaseGoodsBusinessAnalysisAnswerPlan;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.dto.business.WarehouseAnswerPlan;
import com.nongxinle.ai.graph.business.BusinessStatusCardTypes;
import com.nongxinle.ai.graph.business.BusinessStatusCardWireSupport;
import com.nongxinle.ai.graph.business.PurchaseAnswerPlanCardSupport;
import com.nongxinle.ai.graph.business.PurchaseGoodsAnchorDetailCardSupport;
import com.nongxinle.ai.graph.business.PurchaseSupplierGoodsDetailCardSupport;
import com.nongxinle.ai.graph.business.PurchaseGoodsBusinessAnalysisCardSupport;
import com.nongxinle.ai.graph.business.MenuOperationAnswerPlanCardSupport;
import com.nongxinle.ai.graph.business.execution.ToolRequestContractExecutionParamSupport;
import com.nongxinle.ai.history.dto.AiConversationMessageDTO;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.nongxinle.ai.trace.AiRunSessionRegistry;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Run 卡片写出中枢：从 Tool 信封 / AnswerPlan 提升原始卡片，归一化为统一 {@code cards[]}，
 * 并写入 SSE / GET / 历史消息。前端主协议为 {@code cards[]}（{@code payload} 承载渲染数据）；
 * 根级 {@code cardPayload} 为短期兼容（{@code data} 镜像 {@code payload}），deprecated，勿再扩展。
 */
public final class AiCardPayloadWireSupport {

    private static final String FIELD_CARD_TYPE = "cardType";
    private static final String FIELD_TITLE = "title";
    private static final String FIELD_SUBTITLE = "subtitle";
    private static final String FIELD_CHART_TYPE = "chartType";
    private static final String FIELD_PAYLOAD = "payload";
    /** 旧协议字段，仅 {@link #buildDeprecatedCardPayloadCompat} 写入根级 cardPayload */
    @Deprecated
    private static final String FIELD_DATA = "data";

    private static final String TITLE_DISH_SALES = "菜品销售";
    private static final String TITLE_DISH_SALES_RANKING = "菜品销量排行";
    private static final String TITLE_DISH_COST = "菜品成本";
    private static final String TITLE_DISH_PROFIT_PRESCRIPTION = "单菜利润处方";
    private static final String TITLE_DISH_PROFIT_RANKING = "菜品利润排行";
    private static final String TITLE_MENU_PORTFOLIO = "菜单结构四象限";
    private static final String TITLE_MENU_HIGH_SALES_LOW_MARGIN = "畅销低利菜";
    private static final String TITLE_MENU_ACTION_RECOMMENDATION = "菜单优化方案";
    private static final String TITLE_WAREHOUSE_STOCK_RANKING = "账面库存金额排行";
    private static final String TITLE_WAREHOUSE_INVENTORY_RISK = "库存风险关注列表";
    private static final String TITLE_WAREHOUSE_NEAR_EXPIRY_RISK = "库存临期/过期风险";
    private static final String TITLE_WAREHOUSE_INVENTORY_SUPERVISION = "库存监督/诊断";
    private static final String CHART_TYPE_MENU_ACTION_PLAN = "PLAN";
    private static final String SOURCE_ANSWER_PLAN_MENU_OPERATION = "menuOperationAnswerPlan";
    private static final String SOURCE_DATA_REF_MENU_OPTIMIZATION_PLAN = "menuOptimizationPlan";
    private static final String PAYLOAD_STATUS_EMPTY = "EMPTY";
    private static final String CARD_TYPE_MENU_ACTION_RECOMMENDATION = "MENU_ACTION_RECOMMENDATION_CARD";

    private AiCardPayloadWireSupport() {
    }

    /**
     * 扫描 Tool 信封与 AnswerPlan，归一化后写入 {@link AiRunState#getCards()} 与兼容 {@code cardPayload}。
     */
    public static void refreshAllCardPayloads(AiRunState state) {
        refreshFromToolResults(state);
        promoteFromDishSalesAnalysisToolEnvelope(state);
        promoteFromDishSalesAnswerPlan(state);
        promoteFromDishProfitAnswerPlan(state);
        if (shouldSuppressLegacyDishCostCard(state)) {
            clearLegacyDishCostCard(state);
        }
        Map<String, Object> legacyRaw =
                state.getCardPayload() != null && !state.getCardPayload().isEmpty()
                        ? deepCopyMap(state.getCardPayload())
                        : null;
        if (state != null && (BusinessStatusCardWireSupport.hasBusinessStatusCards(state.getCards())
                || PurchaseAnswerPlanCardSupport.hasPurchaseAnswerPlanCard(state.getCards())
                || WarehouseGoodsAnchorAnswerPlanCardWireSupport.hasWarehouseGoodsAnchorAnswerPlanCard(
                        state.getCards()))) {
            finalizeBusinessStatusCardsOnState(state);
            mirrorCardFieldsToMasterBusinessAgentDebug(state);
            return;
        }
        rebuildUnifiedCards(state, legacyRaw);
        mirrorCardFieldsToMasterBusinessAgentDebug(state);
    }

    /** 已投影 cards[]（经营四卡或采购 AnswerPlan 卡）时，只做字段归一化与兼容镜像。 */
    static void finalizeBusinessStatusCardsOnState(AiRunState state) {
        if (state == null || state.getCards() == null || state.getCards().isEmpty()) {
            return;
        }
        List<Map<String, Object>> normalized = new ArrayList<>(state.getCards().size());
        for (Map<String, Object> card : state.getCards()) {
            if (card == null || card.isEmpty()) {
                continue;
            }
            Map<String, Object> unified =
                    card.containsKey(FIELD_PAYLOAD)
                            ? ensureUnifiedCardFields(card)
                            : normalizeLegacyCard(card);
            if (unified != null && !unified.isEmpty()) {
                normalized.add(deepCopyMap(enrichBusinessStatusCardTitles(unified)));
            }
        }
        state.setCards(normalized);
        state.setCardPayload(buildDeprecatedCardPayloadCompatFromCards(normalized));
    }

    private static Map<String, Object> enrichBusinessStatusCardTitles(Map<String, Object> unified) {
        if (unified == null || unified.isEmpty()) {
            return unified;
        }
        Object cardTypeObj = unified.get(FIELD_CARD_TYPE);
        String cardType = cardTypeObj != null ? cardTypeObj.toString().trim() : "";
        if (!BusinessStatusCardTypes.isBusinessStatusCardType(cardType)) {
            return unified;
        }
        if (!unified.containsKey(FIELD_TITLE) || unified.get(FIELD_TITLE) == null) {
            unified.put(FIELD_TITLE, defaultTitleForCardType(cardType));
        }
        return unified;
    }

    /**
     * Harness / GET {@code /ai/runs} 调试摘要：与 {@link #appendFlatCardFields} 同源，写入 summary 顶层字段。
     * 调用方须先 {@link #refreshAllCardPayloads(AiRunState)}。
     */
    public static void enrichHarnessSummaryWithCardFields(Map<String, Object> summary, AiRunState state) {
        if (summary == null || state == null) {
            return;
        }
        appendCardFieldsToDataMap(summary, state);
        summary.put("cardPayloadPresent", state.isCardPayloadPresent());
        summary.put("cardsPresent", state.isCardsPresent());
        summary.put("cardsCardTypes", extractCardTypes(state.getCards()));
    }

    private static List<String> extractCardTypes(List<Map<String, Object>> cards) {
        if (cards == null || cards.isEmpty()) {
            return List.of();
        }
        List<String> types = new ArrayList<>(cards.size());
        for (Map<String, Object> card : cards) {
            if (card == null) {
                continue;
            }
            Object ct = card.get(FIELD_CARD_TYPE);
            if (ct != null && StringUtils.hasText(ct.toString())) {
                types.add(ct.toString().trim());
            }
        }
        return types;
    }

    /** GET {@code harnessDebug}：card 字段仅写入 {@code resolvedQueryContextSummary}（与 SSE 同源）。 */
    public static void enrichHarnessDebugWithRunCardFields(
            Map<String, Object> harnessDebug, AiRunState state) {
        if (harnessDebug == null || state == null) {
            return;
        }
        refreshAllCardPayloads(state);
        Object summaryObj = harnessDebug.get("resolvedQueryContextSummary");
        if (summaryObj instanceof Map<?, ?> summaryRaw) {
            @SuppressWarnings("unchecked")
            Map<String, Object> summary = (Map<String, Object>) summaryRaw;
            enrichHarnessSummaryWithCardFields(summary, state);
            harnessDebug.put("resolvedQueryContextSummary", summary);
        }
    }

    /**
     * 扫描本轮全部 tool 信封，将最近出现的 {@code cardPayload} 提升到 {@link AiRunState}。
     */
    public static void refreshFromToolResults(AiRunState state) {
        if (state == null || state.getToolResults() == null || state.getToolResults().isEmpty()) {
            return;
        }
        for (Object raw : state.getToolResults().values()) {
            if (raw instanceof Map<?, ?> envelope) {
                @SuppressWarnings("unchecked")
                Map<String, Object> env = (Map<String, Object>) envelope;
                promoteFromToolEnvelope(state, env);
            }
        }
    }

    /**
     * 菜品销量 AnswerPlan → {@code DISH_SALES_CARD}（单菜）或 {@code DISH_SALES_RANKING_CARD}（排行）；
     * 不覆盖已有效的 Tool {@code cardPayload} 或 {@link DishCostAnalysisCapabilityResult#CARD_TYPE_DISH_COST_ANALYSIS}。
     */
    public static void promoteFromDishSalesAnswerPlan(AiRunState state) {
        if (state == null || state.getDishSalesAnswerPlan() == null) {
            return;
        }
        if (hasCostAnalysisCard(state.getCardPayload())) {
            return;
        }
        if (hasRenderableCardOnState(state)) {
            return;
        }
        Map<String, Object> card =
                DishSalesAnswerPlanCardSupport.buildCardPayload(state.getDishSalesAnswerPlan(), state);
        if (card == null || card.isEmpty()) {
            return;
        }
        assignLegacyRawCard(state, card);
    }

    /**
     * 菜品毛利 AnswerPlan → {@code DISH_PROFIT_RANKING_CARD}（成本/毛利排行，含 EMPTY 无数据态）；
     * 不覆盖已有效的 Tool / 销量 / 成本分析卡。
     */
    public static void promoteFromDishProfitAnswerPlan(AiRunState state) {
        if (state == null || state.getDishProfitAnswerPlan() == null) {
            return;
        }
        if (hasCostAnalysisCard(state.getCardPayload())) {
            return;
        }
        if (hasRenderableCardOnState(state)) {
            return;
        }
        Map<String, Object> card =
                DishProfitAnswerPlanCardSupport.buildCardPayload(state.getDishProfitAnswerPlan(), state);
        if (card == null || card.isEmpty()) {
            return;
        }
        assignLegacyRawCard(state, card);
    }

    /**
     * 从 {@link AiBusinessToolIds#DISH_SALES_ANALYSIS_CARD} 信封补提卡片（Tool data 已有 cardPayload 或 SUCCESS 字段）。
     */
    @SuppressWarnings("unchecked")
    public static void promoteFromDishSalesAnalysisToolEnvelope(AiRunState state) {
        if (state == null || state.getToolResults() == null) {
            return;
        }
        if (hasRenderableCardOnState(state)) {
            return;
        }
        Object raw = state.getToolResults().get(AiBusinessToolIds.DISH_SALES_ANALYSIS_CARD);
        if (!(raw instanceof Map<?, ?> envelopeRaw)) {
            return;
        }
        Map<String, Object> envelope = (Map<String, Object>) envelopeRaw;
        Map<String, Object> cardPayload = extractCardPayload(envelope);
        if (cardPayload == null || cardPayload.isEmpty()) {
            cardPayload = buildCardPayloadFromDishSalesToolData(envelope);
        }
        if (cardPayload == null || cardPayload.isEmpty()) {
            return;
        }
        assignLegacyRawCard(state, cardPayload);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> buildCardPayloadFromDishSalesToolData(Map<String, Object> envelope) {
        if (envelope == null || envelope.isEmpty()) {
            return null;
        }
        Object dataObj = envelope.get("data");
        if (!(dataObj instanceof Map<?, ?> dataMapRaw)) {
            return null;
        }
        Map<String, Object> data = (Map<String, Object>) dataMapRaw;
        Object status = data.get("status");
        if (status == null || !"SUCCESS".equalsIgnoreCase(status.toString().trim())) {
            return null;
        }
        Object dishName = data.get("dishName");
        if (dishName == null || dishName.toString().isBlank()) {
            return null;
        }
        Integer foodId = parseFoodId(data.get("dishId"), data.get("foodId"));
        String salesPortions = firstNonBlank(data.get("salesPortions"), data.get("soldPortionsTotal"));
        String salesAmount = firstNonBlank(data.get("salesAmount"), data.get("actualRevenue"));
        String salesUnitPrice = stringify(data.get("salesUnitPrice"));
        Integer ranking = parseRanking(data.get("ranking"), data.get("rank"));
        String timeLabel = stringify(data.get("timeLabel"));
        String scopeLabel = stringify(data.get("scopeLabel"));
        return DishSalesAnalysisCapabilityResult.buildCardPayload(
                dishName.toString().trim(),
                foodId,
                salesPortions,
                salesAmount,
                salesUnitPrice,
                ranking,
                timeLabel,
                scopeLabel);
    }

    private static boolean hasDishSalesCard(Map<String, Object> cardPayload) {
        if (cardPayload == null || cardPayload.isEmpty()) {
            return false;
        }
        Object cardType = cardPayload.get("cardType");
        String ct = cardType != null ? cardType.toString().trim() : null;
        return DishSalesAnalysisCapabilityResult.CARD_TYPE.equals(ct)
                || DishSalesAnswerPlan.CARD_TYPE_DISH_SALES.equals(ct)
                || DishSalesAnswerPlan.CARD_TYPE_DISH_SALES_RANKING.equals(ct);
    }

    /** Tool 或 AnswerPlan 已产出可渲染的菜品销量卡时不再兜底覆盖。 */
    @SuppressWarnings("unchecked")
    private static boolean hasValidDishSalesCard(Map<String, Object> cardPayload) {
        if (!hasDishSalesCard(cardPayload)) {
            return false;
        }
        Object cardTypeObj = cardPayload.get("cardType");
        String cardType = cardTypeObj != null ? cardTypeObj.toString().trim() : "";
        if (DishSalesAnswerPlan.CARD_TYPE_DISH_SALES_RANKING.equals(cardType)) {
            Object payloadObj = cardPayload.get(FIELD_PAYLOAD);
            if (payloadObj instanceof Map<?, ?> payloadMap && !payloadMap.isEmpty()) {
                Object rows = payloadMap.get("rows");
                return rows instanceof List<?> list && !list.isEmpty();
            }
            Object dataObj = cardPayload.get(FIELD_DATA);
            if (dataObj instanceof Map<?, ?> dataMap && !dataMap.isEmpty()) {
                Object rows = dataMap.get("rows");
                return rows instanceof List<?> list && !list.isEmpty();
            }
            return false;
        }
        Object dataObj = cardPayload.get("data");
        if (!(dataObj instanceof Map<?, ?> dataRaw) || dataRaw.isEmpty()) {
            Object payloadObj = cardPayload.get(FIELD_PAYLOAD);
            if (payloadObj instanceof Map<?, ?> payloadMap && !payloadMap.isEmpty()) {
                Object rows = payloadMap.get("rows");
                if (rows instanceof List<?> list && !list.isEmpty()) {
                    return true;
                }
                Object dishName = payloadMap.get("dishName");
                return dishName != null && StringUtils.hasText(dishName.toString());
            }
            return false;
        }
        Map<String, Object> data = (Map<String, Object>) dataRaw;
        Object rows = data.get("rows");
        if (rows instanceof List<?> list && !list.isEmpty()) {
            return true;
        }
        Object dishName = data.get("dishName");
        return dishName != null && StringUtils.hasText(dishName.toString());
    }

    private static void mirrorCardFieldsToMasterBusinessAgentDebug(AiRunState state) {
        if (state == null) {
            return;
        }
        if (!state.isCardPayloadPresent() && !state.isCardsPresent()) {
            return;
        }
        Map<String, Object> md = state.getMasterBusinessAgentDebug();
        LinkedHashMap<String, Object> next;
        if (md == null || md.isEmpty()) {
            next = new LinkedHashMap<>();
        } else {
            next = new LinkedHashMap<>(md);
        }
        if (state.isCardPayloadPresent()) {
            next.put("cardPayload", deepCopyMap(state.getCardPayload()));
        }
        if (state.isCardsPresent()) {
            next.put("cards", deepCopyCards(state.getCards()));
        }
        next.put("cardPayloadPresent", state.isCardPayloadPresent());
        next.put("cardsPresent", state.isCardsPresent());
        state.setMasterBusinessAgentDebug(next);
    }

    private static boolean hasRenderableCardOnState(AiRunState state) {
        if (state == null) {
            return false;
        }
        return hasCostAnalysisCard(state.getCardPayload())
                || hasValidDishSalesCard(state.getCardPayload())
                || hasValidDishProfitRankingCard(state.getCardPayload());
    }

    private static boolean hasCostAnalysisCard(Map<String, Object> cardPayload) {
        if (cardPayload == null || cardPayload.isEmpty()) {
            return false;
        }
        Object cardType = cardPayload.get("cardType");
        return DishCostAnalysisCapabilityResult.CARD_TYPE_DISH_COST_ANALYSIS.equals(
                cardType != null ? cardType.toString().trim() : null);
    }

    @SuppressWarnings("unchecked")
    private static boolean hasValidDishProfitRankingCard(Map<String, Object> cardPayload) {
        if (cardPayload == null || cardPayload.isEmpty()) {
            return false;
        }
        Object cardTypeObj = cardPayload.get("cardType");
        String cardType = cardTypeObj != null ? cardTypeObj.toString().trim() : "";
        if (!DishProfitAnswerPlan.CARD_TYPE_DISH_PROFIT_RANKING.equals(cardType)
                && !"DISH_PROFIT_COST_RANKING_CARD".equals(cardType)) {
            return false;
        }
        Object payloadObj = cardPayload.get(FIELD_PAYLOAD);
        if (!(payloadObj instanceof Map<?, ?> payloadMap) || payloadMap.isEmpty()) {
            return false;
        }
        Object status = payloadMap.get("status");
        if (PAYLOAD_STATUS_EMPTY.equals(status)) {
            return true;
        }
        Object rows = payloadMap.get("rows");
        return rows instanceof List<?> list && !list.isEmpty();
    }

    /**
     * 若信封（或其 {@code data}）含 {@code cardPayload} / {@code cards}，写入 Run 状态。
     */
    @SuppressWarnings("unchecked")
    public static void promoteFromToolEnvelope(AiRunState state, Map<String, Object> toolEnvelope) {
        if (state == null || toolEnvelope == null || toolEnvelope.isEmpty()) {
            return;
        }
        if (shouldSuppressLegacyDishCostCard(state)) {
            return;
        }
        Map<String, Object> cardPayload = extractCardPayload(toolEnvelope);
        if (cardPayload != null && !cardPayload.isEmpty()
                && (hasCostAnalysisCard(cardPayload) || hasValidDishSalesCard(cardPayload))) {
            assignLegacyRawCard(state, cardPayload);
            return;
        }
        List<Map<String, Object>> cards = extractCards(toolEnvelope);
        if (cards != null && !cards.isEmpty()) {
            Map<String, Object> first = cards.get(0);
            if (first != null && !first.isEmpty()
                    && (hasCostAnalysisCard(first) || hasValidDishSalesCard(first))) {
                assignLegacyRawCard(state, first);
            }
        }
    }

    /** 提升阶段：仅写入旧形 {@code { cardType, data }} 到 state.cardPayload，归一化由 {@link #rebuildUnifiedCards} 完成。 */
    private static void assignLegacyRawCard(AiRunState state, Map<String, Object> legacyRaw) {
        if (state == null || legacyRaw == null || legacyRaw.isEmpty()) {
            return;
        }
        state.setCardPayload(deepCopyMap(legacyRaw));
    }

    /**
     * 将提升阶段遗留的原始卡片或 MenuOperation AnswerPlan 归一化为统一 {@code cards[]}，
     * 并写入 deprecated 兼容 {@code cardPayload}（{@code data} 镜像 {@code payload}）。
     */
    static void rebuildUnifiedCards(AiRunState state, Map<String, Object> legacyRaw) {
        if (state == null) {
            return;
        }
        List<Map<String, Object>> cards = new ArrayList<>();

        List<Map<String, Object>> fromGoodsBusinessAnalysis =
                promoteFromPurchaseGoodsBusinessAnalysisAnswerPlan(state);
        if (fromGoodsBusinessAnalysis != null && !fromGoodsBusinessAnalysis.isEmpty()) {
            for (Map<String, Object> card : fromGoodsBusinessAnalysis) {
                if (card != null && !card.isEmpty()) {
                    cards.add(deepCopyMap(card));
                }
            }
        }

        if (cards.isEmpty()) {
            List<Map<String, Object>> fromPrescription = promoteFromDishProfitPrescriptionAnswerPlan(state);
            if (fromPrescription != null && !fromPrescription.isEmpty()) {
                for (Map<String, Object> card : fromPrescription) {
                    if (card != null && !card.isEmpty()) {
                        cards.add(deepCopyMap(card));
                    }
                }
            }
        }

        if (cards.isEmpty()) {
            List<Map<String, Object>> fromIngredientCover = promoteFromDishIngredientCoverAnswerPlan(state);
            if (fromIngredientCover != null && !fromIngredientCover.isEmpty()) {
                for (Map<String, Object> card : fromIngredientCover) {
                    if (card != null && !card.isEmpty()) {
                        cards.add(deepCopyMap(card));
                    }
                }
            }
        }

        if (cards.isEmpty()) {
            List<Map<String, Object>> fromWarehouseRisk = promoteFromWarehouseInventoryRiskAnswerPlan(state);
            if (fromWarehouseRisk != null && !fromWarehouseRisk.isEmpty()) {
                for (Map<String, Object> card : fromWarehouseRisk) {
                    if (card != null && !card.isEmpty()) {
                        cards.add(deepCopyMap(card));
                    }
                }
            }
        }

        if (cards.isEmpty()) {
            List<Map<String, Object>> fromMenuPlan = promoteAllFromMenuOperationAnswerPlan(state);
            if (fromMenuPlan != null) {
                for (Map<String, Object> card : fromMenuPlan) {
                    if (card != null && !card.isEmpty()) {
                        cards.add(deepCopyMap(card));
                    }
                }
            }
        }
        if (cards.isEmpty() && legacyRaw != null && !legacyRaw.isEmpty()) {
            Map<String, Object> unified =
                    isUnifiedCard(legacyRaw) ? ensureUnifiedCardFields(legacyRaw) : normalizeLegacyCard(legacyRaw);
            if (unified != null && !unified.isEmpty()) {
                cards.add(deepCopyMap(unified));
            }
        }
        if (cards.isEmpty()) {
            state.setCards(new ArrayList<>());
            state.setCardPayload(null);
            return;
        }
        List<Map<String, Object>> normalizedCards = new ArrayList<>(cards.size());
        for (Map<String, Object> card : cards) {
            if (card == null || card.isEmpty()) {
                continue;
            }
            normalizedCards.add(ensureUnifiedCardFields(card));
        }
        state.setCards(normalizedCards);
        state.setCardPayload(buildDeprecatedCardPayloadCompatFromCards(normalizedCards));
    }

    /** MenuOperation 业务卡：严格按 planType 投影主卡（overview / high_sales_low_profit / action_recommendation）。 */
    static List<Map<String, Object>> promoteAllFromMenuOperationAnswerPlan(AiRunState state) {
        if (state == null || state.getMenuOperationAnswerPlan() == null) {
            return List.of();
        }
        MenuOperationAnswerPlan plan = state.getMenuOperationAnswerPlan();
        if (MenuOperationAnswerPlan.TYPE_MENU_ACTION_RECOMMENDATION.equals(plan.getPlanType())
                && state.getMenuExpertPresentationPlan() != null) {
            Map<String, Object> card =
                    MenuExpertPresentationPlanCardSupport.buildActionRecommendationCard(
                            plan, state.getMenuExpertPresentationPlan());
            return card == null ? List.of() : List.of(card);
        }
        return MenuOperationAnswerPlanCardSupport.buildRunCards(plan);
    }

    /** 单菜利润处方 AnswerPlan → {@code DISH_PROFIT_PRESCRIPTION_CARD}。 */
    static List<Map<String, Object>> promoteFromDishProfitPrescriptionAnswerPlan(AiRunState state) {
        if (state == null || state.getDishProfitPrescriptionAnswerPlan() == null) {
            return List.of();
        }
        return DishProfitPrescriptionAnswerPlanCardSupport.buildRunCards(
                state.getDishProfitPrescriptionAnswerPlan());
    }

    /** GOODS 锚原料采购经营分析 AnswerPlan → {@code PURCHASE_GOODS_BUSINESS_ANALYSIS_CARD}。 */
    static List<Map<String, Object>> promoteFromPurchaseGoodsBusinessAnalysisAnswerPlan(AiRunState state) {
        if (state == null || state.getPurchaseGoodsBusinessAnalysisAnswerPlan() == null) {
            return List.of();
        }
        return PurchaseGoodsBusinessAnalysisCardSupport.buildRunCards(
                state.getPurchaseGoodsBusinessAnalysisAnswerPlan(),
                state.getResolvedQueryContext());
    }

    /** 单菜配料可支撑天数 AnswerPlan → {@code DISH_INGREDIENT_COVER_DAYS_CARD}。 */
    static List<Map<String, Object>> promoteFromDishIngredientCoverAnswerPlan(AiRunState state) {
        if (state == null || state.getDishIngredientCoverAnswerPlan() == null) {
            return List.of();
        }
        return DishIngredientCoverAnswerPlanCardSupport.buildRunCards(
                state.getDishIngredientCoverAnswerPlan(), state.getResolvedQueryContext());
    }

    /** 库房 AnswerPlan → {@code WAREHOUSE_INVENTORY_RISK_LIST_CARD} / {@code WAREHOUSE_STOCK_RANKING_CARD}。 */
    static List<Map<String, Object>> promoteFromWarehouseInventoryRiskAnswerPlan(AiRunState state) {
        if (state == null || state.getWarehouseAnswerPlan() == null) {
            return List.of();
        }
        return WarehouseAnswerPlanCardSupport.buildRunCards(state.getWarehouseAnswerPlan());
    }

    private static boolean shouldSuppressLegacyDishCostCard(AiRunState state) {
        if (state == null) {
            return false;
        }
        if (state.getDishProfitPrescriptionAnswerPlan() != null) {
            return true;
        }
        if (state.getDishIngredientCoverAnswerPlan() != null) {
            return true;
        }
        if (state.getGoodsSupportedDishCoverAnswerPlan() != null) {
            return true;
        }
        if (state.getGoodsStockBatchDetailAnswerPlan() != null) {
            return true;
        }
        return ToolRequestContractExecutionParamSupport.isDishProfitPrescriptionContract(
                        state.getResolvedQueryContext())
                || ToolRequestContractExecutionParamSupport.isDishIngredientCoverDaysContract(
                        state.getResolvedQueryContext())
                || ToolRequestContractExecutionParamSupport.isGoodsSupportedDishCoverContract(
                        state.getResolvedQueryContext())
                || ToolRequestContractExecutionParamSupport.isGoodsStockBatchDetailContract(
                        state.getResolvedQueryContext());
    }

    private static void clearLegacyDishCostCard(AiRunState state) {
        if (state == null) {
            return;
        }
        if (hasCostAnalysisCard(state.getCardPayload())) {
            state.setCardPayload(null);
        }
    }

    /**
     * 旧 {@code { cardType, data }} → 统一 card；{@code data} 迁移为 {@code payload}。
     */
    static Map<String, Object> normalizeLegacyCard(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        if (isUnifiedCard(raw)) {
            return ensureUnifiedCardFields(raw);
        }
        Object cardTypeObj = raw.get(FIELD_CARD_TYPE);
        if (cardTypeObj == null || cardTypeObj.toString().isBlank()) {
            return null;
        }
        String cardType = cardTypeObj.toString().trim();
        Object payload = extractLegacyPayload(raw);
        if (payload == null) {
            return null;
        }
        Map<String, Object> unified = new LinkedHashMap<>();
        unified.put(FIELD_CARD_TYPE, cardType);
        unified.put(FIELD_TITLE, defaultTitleForCardType(cardType));
        unified.put(FIELD_SUBTITLE, null);
        unified.put(FIELD_CHART_TYPE, null);
        unified.put(FIELD_PAYLOAD, payload);
        return enrichMenuActionOptimizationCard(unified);
    }

    private static boolean isUnifiedCard(Map<String, Object> raw) {
        return raw != null
                && raw.containsKey(FIELD_PAYLOAD)
                && raw.get(FIELD_CARD_TYPE) != null
                && !raw.get(FIELD_CARD_TYPE).toString().isBlank();
    }

    private static Map<String, Object> ensureUnifiedCardFields(Map<String, Object> unified) {
        Map<String, Object> out = deepCopyMap(unified);
        Object cardTypeObj = out.get(FIELD_CARD_TYPE);
        String cardType = cardTypeObj != null ? cardTypeObj.toString().trim() : "";
        if (!out.containsKey(FIELD_TITLE) || out.get(FIELD_TITLE) == null) {
            out.put(FIELD_TITLE, defaultTitleForCardType(cardType));
        }
        if (!out.containsKey(FIELD_SUBTITLE)) {
            out.put(FIELD_SUBTITLE, null);
        }
        if (!out.containsKey(FIELD_CHART_TYPE)) {
            out.put(FIELD_CHART_TYPE, null);
        }
        return enrichMenuActionOptimizationCard(out);
    }

    /**
     * 菜单优化方案卡：补齐 title / chartType / source；payload 结构不变。
     * 仅当 payload 含 {@code optimizationSummary} 时视为优化方案卡（非 overview 副卡 action list）。
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> enrichMenuActionOptimizationCard(Map<String, Object> card) {
        if (card == null || card.isEmpty()) {
            return card;
        }
        Object cardTypeObj = card.get(FIELD_CARD_TYPE);
        if (cardTypeObj == null
                || !CARD_TYPE_MENU_ACTION_RECOMMENDATION.equals(cardTypeObj.toString().trim())) {
            return card;
        }
        Object payloadObj = card.get(FIELD_PAYLOAD);
        if (!(payloadObj instanceof Map<?, ?> payloadMap) || payloadMap.isEmpty()) {
            return card;
        }
        if (!payloadMap.containsKey("optimizationSummary")) {
            return card;
        }
        card.put(FIELD_TITLE, TITLE_MENU_ACTION_RECOMMENDATION);
        card.put(FIELD_CHART_TYPE, CHART_TYPE_MENU_ACTION_PLAN);
        Object sourceObj = card.get("source");
        Map<String, Object> source;
        if (sourceObj instanceof Map<?, ?> sourceMap && !sourceMap.isEmpty()) {
            source = deepCopyMap((Map<String, Object>) sourceMap);
        } else {
            source = new LinkedHashMap<>();
        }
        source.put("answerPlan", SOURCE_ANSWER_PLAN_MENU_OPERATION);
        source.put("dataRef", SOURCE_DATA_REF_MENU_OPTIMIZATION_PLAN);
        card.put("source", source);
        return card;
    }

    @SuppressWarnings("unchecked")
    private static Object extractLegacyPayload(Map<String, Object> raw) {
        Object payload = raw.get(FIELD_PAYLOAD);
        if (payload instanceof Map<?, ?> payloadMap && !payloadMap.isEmpty()) {
            return deepCopyMap((Map<String, Object>) payloadMap);
        }
        Object data = raw.get(FIELD_DATA);
        if (data instanceof Map<?, ?> dataMap && !dataMap.isEmpty()) {
            return deepCopyMap((Map<String, Object>) dataMap);
        }
        return null;
    }

    private static String defaultTitleForCardType(String cardType) {
        if (cardType == null) {
            return "";
        }
        return switch (cardType) {
            case "DISH_SALES_CARD" -> TITLE_DISH_SALES;
            case "DISH_SALES_RANKING_CARD" -> TITLE_DISH_SALES_RANKING;
            case "DISH_COST_ANALYSIS_CARD" -> TITLE_DISH_COST;
            case "DISH_PROFIT_PRESCRIPTION_CARD" -> TITLE_DISH_PROFIT_PRESCRIPTION;
            case "DISH_PROFIT_RANKING_CARD", "DISH_PROFIT_COST_RANKING_CARD" -> TITLE_DISH_PROFIT_RANKING;
            case "MENU_PORTFOLIO_QUADRANT_CARD" -> TITLE_MENU_PORTFOLIO;
            case "MENU_HIGH_SALES_LOW_MARGIN_CARD" -> TITLE_MENU_HIGH_SALES_LOW_MARGIN;
            case "MENU_ACTION_RECOMMENDATION_CARD" -> TITLE_MENU_ACTION_RECOMMENDATION;
            case WarehouseAnswerPlanCardSupport.CARD_TYPE_INVENTORY_RISK -> TITLE_WAREHOUSE_INVENTORY_RISK;
            case WarehouseAnswerPlanCardSupport.CARD_TYPE_NEAR_EXPIRY_RISK -> TITLE_WAREHOUSE_NEAR_EXPIRY_RISK;
            case WarehouseAnswerPlanCardSupport.CARD_TYPE_INVENTORY_SUPERVISION ->
                    TITLE_WAREHOUSE_INVENTORY_SUPERVISION;
            case WarehouseAnswerPlan.CARD_TYPE_STOCK_RANKING -> TITLE_WAREHOUSE_STOCK_RANKING;
            case BusinessStatusCardTypes.REVENUE_REPORT_CARD -> "营业额";
            case BusinessStatusCardTypes.PURCHASE_CHECK_CARD -> "采购";
            case BusinessStatusCardTypes.STOCK_RECONCILE_CARD -> "库存 / 销货核对";
            case BusinessStatusCardTypes.REORDER_REMINDER_CARD -> "订货";
            case "PURCHASE_GOODS_DETAIL_CARD" -> "原料采购";
            case PurchaseAnswerPlan.CARD_TYPE_PURCHASE_GOODS_ANCHOR_DETAIL -> PurchaseGoodsAnchorDetailCardSupport.CARD_TITLE;
            case PurchaseSupplierGoodsDetailCardSupport.CARD_TYPE -> PurchaseSupplierGoodsDetailCardSupport.CARD_TITLE;
            case "GOODS_STOCK_BATCH_DETAIL_CARD" -> GoodsStockBatchDetailCardSupport.CARD_TITLE;
            default -> "";
        };
    }

    /** 根级 cardPayload 短期兼容：{@code data} 镜像 {@code cards[0].payload}，供旧 storeAiChat 读取。 */
    @Deprecated
    @SuppressWarnings("unchecked")
    private static Map<String, Object> buildDeprecatedCardPayloadCompat(Map<String, Object> unified) {
        Map<String, Object> compat = new LinkedHashMap<>();
        compat.put(FIELD_CARD_TYPE, unified.get(FIELD_CARD_TYPE));
        Object payload = unified.get(FIELD_PAYLOAD);
        if (payload instanceof Map<?, ?> payloadMap) {
            compat.put(FIELD_DATA, deepCopyMap((Map<String, Object>) payloadMap));
        } else if (payload != null) {
            compat.put(FIELD_DATA, payload);
        }
        return compat;
    }

    /** 将 Run 状态中的卡片字段追加到 SSE 扁平 envelope（根级 {@code cardPayload} / {@code cards}）。 */
    public static void appendFlatCardFields(Map<String, Object> target, AiRunState state) {
        if (target == null || state == null) {
            return;
        }
        if (state.getCardPayload() != null && !state.getCardPayload().isEmpty()) {
            target.put("cardPayload", deepCopyMap(state.getCardPayload()));
        }
        if (state.getCards() != null && !state.getCards().isEmpty()) {
            target.put("cards", deepCopyCards(state.getCards()));
        }
    }

    /** 将 Run 状态中的卡片字段追加到 {@code answer_delta.data} / {@code run_finished.data}。 */
    public static void appendCardFieldsToDataMap(Map<String, Object> data, AiRunState state) {
        if (data == null || state == null) {
            return;
        }
        if (state.getCardPayload() != null && !state.getCardPayload().isEmpty()) {
            try {
                data.put("cardPayload", JSON.parseObject(JSON.toJSONString(state.getCardPayload())));
            } catch (Exception ignore) {
                data.put("cardPayloadWarning", "serialize_failed");
            }
        }
        if (state.getCards() != null && !state.getCards().isEmpty()) {
            try {
                data.put("cards", JSON.parse(JSON.toJSONString(state.getCards())));
            } catch (Exception ignore) {
                data.put("cardsWarning", "serialize_failed");
            }
        }
    }

    /**
     * {@code tool_finished} 等 step：在基础字段上合并卡片并回写 state。
     */
    public static Map<String, Object> toolFinishedPayload(
            AiRunState state,
            Map<String, Object> toolEnvelope,
            Map<String, Object> baseFields) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        if (baseFields != null) {
            out.putAll(baseFields);
        }
        promoteFromToolEnvelope(state, toolEnvelope);
        appendFlatCardFields(out, state);
        return out;
    }

    /** 历史消息列表：从仍在内存中的 Run Session 补全 {@code cards}（{@code cards_json} 为空时的兜底）。 */
    public static void hydrateMessageCardFromRunSession(
            AiConversationMessageDTO message,
            AiRunSessionRegistry sessionRegistry,
            Long runId) {
        if (message == null || sessionRegistry == null || runId == null) {
            return;
        }
        if (message.getCards() != null && !message.getCards().isEmpty()) {
            return;
        }
        sessionRegistry.get(runId).ifPresent(session -> {
            AiRunState st = session.getState();
            if (st == null) {
                return;
            }
            refreshAllCardPayloads(st);
            applyCardsToMessageDto(message, st.getCards());
        });
    }

    /** 将统一 {@code cards[]} 序列化为落库 JSON；无卡时返回 null。 */
    public static String serializeCardsForPersistence(List<Map<String, Object>> cards) {
        if (cards == null || cards.isEmpty()) {
            return null;
        }
        try {
            return JSON.toJSONString(deepCopyCards(cards));
        } catch (Exception ignore) {
            return null;
        }
    }

    /** 从 {@code gb_ai_message_cards_json} 反序列化统一 {@code cards[]}。 */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> parseCardsFromPersistence(String cardsJson) {
        if (cardsJson == null || cardsJson.isBlank()) {
            return null;
        }
        try {
            Object parsed = JSON.parse(cardsJson);
            if (!(parsed instanceof List<?> list) || list.isEmpty()) {
                return null;
            }
            List<Map<String, Object>> out = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item instanceof Map<?, ?> m && !m.isEmpty()) {
                    out.add(deepCopyMap((Map<String, Object>) m));
                }
            }
            return out.isEmpty() ? null : out;
        } catch (Exception ignore) {
            return null;
        }
    }

    /** 历史消息 DTO：写入 {@code cards} 并生成 deprecated {@code cardPayload}（不单独落库）。 */
    public static void applyCardsToMessageDto(
            AiConversationMessageDTO message, List<Map<String, Object>> cards) {
        if (message == null || cards == null || cards.isEmpty()) {
            return;
        }
        message.setCards(deepCopyCards(cards));
        Map<String, Object> compat = buildDeprecatedCardPayloadCompatFromCards(cards);
        if (compat != null && !compat.isEmpty()) {
            message.setCardPayload(compat);
        }
    }

    /** 从持久化 JSON 恢复历史消息卡片字段；无 JSON 时不改动 DTO。 */
    public static void hydrateMessageCardsFromPersistence(
            AiConversationMessageDTO message, String cardsJson) {
        if (message == null) {
            return;
        }
        List<Map<String, Object>> cards = parseCardsFromPersistence(cardsJson);
        if (cards == null || cards.isEmpty()) {
            return;
        }
        applyCardsToMessageDto(message, cards);
    }

    /** 由 {@code cards[0]} 生成根级兼容 {@code { cardType, data }}，{@code data} 镜像 {@code payload}。 */
    @Deprecated
    public static Map<String, Object> buildDeprecatedCardPayloadCompatFromCards(
            List<Map<String, Object>> cards) {
        if (cards == null || cards.isEmpty()) {
            return null;
        }
        Map<String, Object> first = cards.get(0);
        if (first == null || first.isEmpty()) {
            return null;
        }
        return buildDeprecatedCardPayloadCompat(first);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> extractCardPayload(Map<String, Object> envelope) {
        if (envelope == null || envelope.isEmpty()) {
            return null;
        }
        Object direct = envelope.get("cardPayload");
        if (direct instanceof Map<?, ?> dm && !dm.isEmpty()) {
            return (Map<String, Object>) direct;
        }
        Object dataObj = envelope.get("data");
        if (dataObj instanceof Map<?, ?> data) {
            Object nested = data.get("cardPayload");
            if (nested instanceof Map<?, ?> nm && !nm.isEmpty()) {
                return (Map<String, Object>) nested;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> extractCards(Map<String, Object> envelope) {
        if (envelope == null || envelope.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> fromRoot = readCardsList(envelope.get("cards"));
        if (fromRoot != null && !fromRoot.isEmpty()) {
            return fromRoot;
        }
        Object dataObj = envelope.get("data");
        if (dataObj instanceof Map<?, ?> data) {
            return readCardsList(data.get("cards"));
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> readCardsList(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> m && !m.isEmpty()) {
                out.add((Map<String, Object>) m);
            }
        }
        return out.isEmpty() ? null : out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopyMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return new LinkedHashMap<>();
        }
        try {
            return JSON.parseObject(JSON.toJSONString(source));
        } catch (Exception ignore) {
            return new LinkedHashMap<>(source);
        }
    }

    private static List<Map<String, Object>> deepCopyCards(List<Map<String, Object>> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>(source.size());
        for (Map<String, Object> card : source) {
            out.add(deepCopyMap(card));
        }
        return out;
    }

    private static String stringify(Object raw) {
        return raw == null ? "" : raw.toString().trim();
    }

    private static String firstNonBlank(Object a, Object b) {
        String sa = stringify(a);
        if (!sa.isEmpty()) {
            return sa;
        }
        return stringify(b);
    }

    private static Integer parseFoodId(Object dishId, Object foodId) {
        Object raw = dishId != null ? dishId : foodId;
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return n.intValue();
        }
        String s = raw.toString().trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseRanking(Object ranking, Object rank) {
        Object raw = ranking != null ? ranking : rank;
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return n.intValue();
        }
        String s = raw.toString().trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
