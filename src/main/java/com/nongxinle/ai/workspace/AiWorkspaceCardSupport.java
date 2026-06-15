package com.nongxinle.ai.workspace;

import com.alibaba.fastjson2.JSON;
import com.nongxinle.ai.graph.business.BusinessStatusCardTypes;
import com.nongxinle.ai.graph.business.GoodsStockBatchDetailCardSupport;
import com.nongxinle.ai.graph.business.PurchaseGoodsAnchorDetailCardSupport;
import com.nongxinle.ai.graph.business.PurchaseSupplierGoodsDetailCardSupport;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pin 卡片快照支持：从统一 {@code cards[]} 提取 title / preview / primaryCardType / cardCount，
 * 不调用 LLM，不做出卡片中不存在的判断或建议。
 */
public final class AiWorkspaceCardSupport {

    private static final String FIELD_CARD_TYPE = "cardType";
    private static final String FIELD_TITLE = "title";
    private static final String FIELD_PAYLOAD = "payload";

    // ── UI 展示标签（与 AiCardPayloadWireSupport.defaultTitleForCardType 同源） ──

    private static final String LABEL_DISH_SALES = "菜品销售";
    private static final String LABEL_DISH_SALES_RANKING = "菜品销量排行";
    private static final String LABEL_DISH_COST = "菜品成本";
    private static final String LABEL_DISH_PROFIT_PRESCRIPTION = "单菜利润处方";
    private static final String LABEL_DISH_PROFIT_RANKING = "菜品利润排行";
    private static final String LABEL_MENU_PORTFOLIO = "菜单结构四象限";
    private static final String LABEL_MENU_HIGH_SALES_LOW_MARGIN = "畅销低利菜";
    private static final String LABEL_MENU_ACTION_RECOMMENDATION = "菜单优化方案";
    private static final String LABEL_WAREHOUSE_INVENTORY_RISK = "库存风险关注列表";
    private static final String LABEL_WAREHOUSE_NEAR_EXPIRY_RISK = "库存临期/过期风险";
    private static final String LABEL_WAREHOUSE_INVENTORY_SUPERVISION = "库存监督/诊断";
    private static final String LABEL_WAREHOUSE_STOCK_RANKING = "账面库存金额排行";
    private static final String LABEL_REVENUE_REPORT = "营业额";
    private static final String LABEL_PURCHASE_CHECK = "采购";
    private static final String LABEL_STOCK_RECONCILE = "库存 / 销货核对";
    private static final String LABEL_REORDER_REMINDER = "订货";
    private static final String LABEL_PURCHASE_GOODS_DETAIL = "原料采购";
    private static final String LABEL_GOODS_BUSINESS_ANALYSIS_SUFFIX = "·采购经营分析";

    private AiWorkspaceCardSupport() {}

    // ── 卡片 → JSON ──

    public static String cardsToJson(List<Map<String, Object>> cards) {
        if (cards == null || cards.isEmpty()) {
            return null;
        }
        try {
            return JSON.toJSONString(cards);
        } catch (Exception ignore) {
            return null;
        }
    }

    // ── primaryCardType / cardCount ──

    public static String extractPrimaryCardType(List<Map<String, Object>> cards) {
        if (cards == null || cards.isEmpty()) {
            return null;
        }
        Map<String, Object> first = cards.get(0);
        if (first == null || first.isEmpty()) {
            return null;
        }
        Object ct = first.get(FIELD_CARD_TYPE);
        return ct != null && StringUtils.hasText(ct.toString()) ? ct.toString().trim() : null;
    }

    public static int countCards(List<Map<String, Object>> cards) {
        return cards == null ? 0 : cards.size();
    }

    public static boolean hasCards(List<Map<String, Object>> cards) {
        return cards != null && !cards.isEmpty();
    }

    // ── 标题优先级：卡片的显式 title > cardType+payload 推导 > null（调用方回退文本截断） ──

    /**
     * 从 cards 推导标题；无法推导时返回 null，让调用方回退正文截断。
     */
    public static String derivePinTitleFromCards(
            List<Map<String, Object>> cards, String fallbackPreview, String snapshot) {
        if (cards == null || cards.isEmpty()) {
            return null;
        }
        Map<String, Object> first = cards.get(0);
        if (first == null || first.isEmpty()) {
            return null;
        }

        // 1) 卡片显式 title（结构化标题，非截断正文）
        Object explicitTitle = first.get(FIELD_TITLE);
        if (explicitTitle != null && StringUtils.hasText(explicitTitle.toString())) {
            return explicitTitle.toString().trim();
        }

        // 2) 基于 cardType + payload 生成确定性标题
        String cardType = extractPrimaryCardType(cards);
        if (cardType == null) {
            return null;
        }

        String derived = deriveTitleForCardType(cardType, first);
        if (derived != null) {
            return derived;
        }

        // 3) 无法推导，返回 null（调用方走正文截断）
        return null;
    }

    /**
     * 基于 cardType 和 payload 生成确定性标题；所有标题基于受支持 cardType 及其结构化字段。
     */
    @SuppressWarnings("unchecked")
    private static String deriveTitleForCardType(String cardType, Map<String, Object> card) {
        Map<String, Object> payload = card.get(FIELD_PAYLOAD) instanceof Map<?, ?>
                ? (Map<String, Object>) card.get(FIELD_PAYLOAD)
                : null;

        return switch (cardType) {
            case "PURCHASE_ANOMALY_CARD" -> {
                String kind = payload != null ? stringOrNull(payload.get("anomalyKind")) : null;
                if (kind != null) {
                    yield "采购异常·" + kind;
                }
                yield "采购异常";
            }
            case "DISH_SALES_CARD" -> LABEL_DISH_SALES;
            case "DISH_SALES_RANKING_CARD" -> LABEL_DISH_SALES_RANKING;
            case "DISH_COST_ANALYSIS_CARD" -> LABEL_DISH_COST;
            case "DISH_PROFIT_PRESCRIPTION_CARD" -> LABEL_DISH_PROFIT_PRESCRIPTION;
            case "DISH_PROFIT_RANKING_CARD", "DISH_PROFIT_COST_RANKING_CARD" -> LABEL_DISH_PROFIT_RANKING;
            case "MENU_PORTFOLIO_QUADRANT_CARD" -> LABEL_MENU_PORTFOLIO;
            case "MENU_HIGH_SALES_LOW_MARGIN_CARD" -> LABEL_MENU_HIGH_SALES_LOW_MARGIN;
            case "MENU_ACTION_RECOMMENDATION_CARD" -> LABEL_MENU_ACTION_RECOMMENDATION;
            case "WAREHOUSE_INVENTORY_RISK_LIST_CARD" -> LABEL_WAREHOUSE_INVENTORY_RISK;
            case "WAREHOUSE_NEAR_EXPIRY_RISK_CARD" -> LABEL_WAREHOUSE_NEAR_EXPIRY_RISK;
            case "WAREHOUSE_INVENTORY_SUPERVISION_CARD" -> LABEL_WAREHOUSE_INVENTORY_SUPERVISION;
            case "WAREHOUSE_STOCK_RANKING_CARD" -> LABEL_WAREHOUSE_STOCK_RANKING;
            case "PURCHASE_GOODS_DETAIL_CARD" -> {
                // 优先使用已生成的卡 title
                if (payload != null && StringUtils.hasText(stringOrNull(payload.get("timeLabel")))) {
                    String sourceType = stringOrNull(payload.get("purchaseSourceType"));
                    String suffix = sourceType != null ? sourceTypeSuffix(sourceType) : "原料采购";
                    yield payload.get("timeLabel") + "·" + suffix;
                }
                yield LABEL_PURCHASE_GOODS_DETAIL;
            }
            case "PURCHASE_GOODS_ANCHOR_DETAIL_CARD" -> PurchaseGoodsAnchorDetailCardSupport.CARD_TITLE;
            case "PURCHASE_SUPPLIER_GOODS_DETAIL_CARD" -> PurchaseSupplierGoodsDetailCardSupport.CARD_TITLE;
            case "PURCHASE_GOODS_AMOUNT_RANKING_CARD" -> "商品采购金额排行";
            case "PURCHASE_GOODS_COUNT_RANKING_CARD" -> "商品采购次数排行";
            case "PURCHASE_GOODS_QUANTITY_RANKING_CARD" -> "商品采购数量排行";
            case "PURCHASE_STORE_AMOUNT_RANKING_CARD" -> "门店采购金额排行";
            case "PURCHASE_SUPPLIER_AMOUNT_RANKING_CARD" -> "供货商采购金额排行";
            case "PURCHASE_GOODS_BUSINESS_ANALYSIS_CARD" -> {
                String goodsName = payload != null ? stringOrNull(payload.get("goodsName")) : null;
                if (goodsName != null) {
                    yield goodsName + LABEL_GOODS_BUSINESS_ANALYSIS_SUFFIX;
                }
                yield "原料采购经营分析";
            }
            case "DISH_INGREDIENT_COVER_DAYS_CARD" -> "配料可支撑天数";
            case "GOODS_STOCK_BATCH_DETAIL_CARD" -> GoodsStockBatchDetailCardSupport.CARD_TITLE;
            default -> {
                // 经营状态卡
                if (BusinessStatusCardTypes.isBusinessStatusCardType(cardType)) {
                    yield labelForBusinessStatusCardType(cardType);
                }
                yield null;
            }
        };
    }

    private static String labelForBusinessStatusCardType(String cardType) {
        return switch (cardType) {
            case BusinessStatusCardTypes.REVENUE_REPORT_CARD -> LABEL_REVENUE_REPORT;
            case BusinessStatusCardTypes.PURCHASE_CHECK_CARD -> LABEL_PURCHASE_CHECK;
            case BusinessStatusCardTypes.STOCK_RECONCILE_CARD -> LABEL_STOCK_RECONCILE;
            case BusinessStatusCardTypes.REORDER_REMINDER_CARD -> LABEL_REORDER_REMINDER;
            default -> null;
        };
    }

    // ── 预览生成：优先卡片结构化事实 → 正文短截断 ──

    /**
     * 生成列表展示短摘要。
     * <ol>
     *   <li>存在结构化业务卡时，基于卡片事实生成简短预览；</li>
     *   <li>否则截断正文到 PREVIEW_MAX_CHARS。</li>
     * </ol>
     * 不调用 LLM，不增加卡片中不存在的判断或建议。
     */
    public static String derivePinPreview(List<Map<String, Object>> cards,
                                           String normalizedSnapshot) {
        if (cards != null && !cards.isEmpty()) {
            String fromCards = generatePreviewForFirstCard(cards);
            if (fromCards != null) {
                return fromCards;
            }
        }
        return AiWorkspaceTextSupport.truncatePreview(normalizedSnapshot);
    }

    @SuppressWarnings("unchecked")
    private static String generatePreviewForFirstCard(List<Map<String, Object>> cards) {
        Map<String, Object> first = cards.get(0);
        if (first == null || first.isEmpty()) {
            return null;
        }
        String cardType = stringOrNull(first.get(FIELD_CARD_TYPE));
        if (cardType == null) {
            return null;
        }
        Map<String, Object> payload = first.get(FIELD_PAYLOAD) instanceof Map<?, ?>
                ? (Map<String, Object>) first.get(FIELD_PAYLOAD)
                : null;

        return switch (cardType) {
            case "PURCHASE_ANOMALY_CARD" ->
                    generateAnomalyCardPreview(payload);
            case "PURCHASE_GOODS_AMOUNT_RANKING_CARD",
                    "PURCHASE_GOODS_COUNT_RANKING_CARD",
                    "PURCHASE_GOODS_QUANTITY_RANKING_CARD",
                    "PURCHASE_STORE_AMOUNT_RANKING_CARD",
                    "PURCHASE_SUPPLIER_AMOUNT_RANKING_CARD" ->
                    generateRankingCardPreview(cardType, payload);
            case "PURCHASE_GOODS_BUSINESS_ANALYSIS_CARD" ->
                    generateGoodsBusinessAnalysisPreview(payload);
            default -> null;
        };
    }

    @SuppressWarnings("unchecked")
    private static String generateAnomalyCardPreview(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        String kind = stringOrNull(payload.get("anomalyKind"));
        String timeLabel = stringOrNull(payload.get("timeLabel"));
        if (kind == null) {
            return null;
        }
        Object focusRowsObj = payload.get("focusRows");
        List<Map<String, Object>> focusRows =
                focusRowsObj instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();

        StringBuilder sb = new StringBuilder();
        if (timeLabel != null) {
            sb.append(timeLabel);
        }
        if (!sb.isEmpty()) {
            sb.append("检出");
        } else {
            sb.append("检出");
        }
        int count = focusRows.size();
        sb.append(count).append("项").append(kind).append("异常");
        if (count > 0) {
            Map<String, Object> firstRow = focusRows.get(0);
            if (firstRow != null) {
                String goodsName = stringOrNull(firstRow.get("goodsName"));
                if (goodsName != null) {
                    sb.append("：").append(goodsName);
                    if ("单价波动".equals(kind)) {
                        Object currentPrice = firstRow.get("currentUnitPrice");
                        Object previousPrice = firstRow.get("previousUnitPrice");
                        if (currentPrice != null && previousPrice != null) {
                            sb.append("，单价由").append(previousPrice).append("升至").append(currentPrice);
                        }
                    }
                }
            }
        }
        sb.append("。");
        return sb.toString();
    }

    private static String generateRankingCardPreview(String cardType, Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        String label = deriveTitleForCardType(cardType, Map.of(FIELD_PAYLOAD, payload));
        String timeLabel = stringOrNull(payload.get("timeLabel"));
        Object focusRowsObj = payload.get("focusRows");
        int rowCount = focusRowsObj instanceof List<?> list ? list.size() : 0;
        StringBuilder sb = new StringBuilder();
        if (timeLabel != null) {
            sb.append(timeLabel);
        }
        if (label == null) {
            label = "排行";
        }
        if (!sb.isEmpty()) {
            sb.append("·");
        }
        sb.append(label);
        if (rowCount > 0) {
            Object secondaryCount = payload.get("secondaryRows");
            int total = rowCount + (secondaryCount instanceof List<?> sec ? sec.size() : 0);
            sb.append("，共").append(total).append("项");
        }
        sb.append("。");
        return sb.toString();
    }

    private static String generateGoodsBusinessAnalysisPreview(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        String goodsName = stringOrNull(payload.get("goodsName"));
        String purchaseTimeLabel = stringOrNull(payload.get("purchaseTimeLabel"));
        StringBuilder sb = new StringBuilder();
        if (purchaseTimeLabel != null) {
            sb.append(purchaseTimeLabel);
        }
        if (goodsName != null) {
            if (!sb.isEmpty()) sb.append("·");
            sb.append(goodsName).append("采购经营分析");
        } else {
            if (!sb.isEmpty()) sb.append("·");
            sb.append("采购经营分析");
        }
        sb.append("。");
        return sb.toString();
    }

    // ── 工具方法 ──

    private static String sourceTypeSuffix(String sourceType) {
        if ("SELF_PURCHASE".equalsIgnoreCase(sourceType)) {
            return "自采商品";
        }
        if ("SUPPLIER_PURCHASE".equalsIgnoreCase(sourceType)) {
            return "供货商订货";
        }
        return "原料采购";
    }

    private static String stringOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
