package com.nongxinle.ai.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;

import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 查询意图（关键词规则 + {@link AiQuerySemanticLexicon} 结构化补强；多轮追问由 {@link com.nongxinle.ai.conversation.AiFollowUpResolver} 合并）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiResolvedQueryIntent {

    public static final String BUSINESS_OVERVIEW = "BUSINESS_OVERVIEW";
    public static final String PURCHASE_OVERVIEW = "PURCHASE_OVERVIEW";
    public static final String WAREHOUSE_STOCK_OVERVIEW = "WAREHOUSE_STOCK_OVERVIEW";
    public static final String DISH_PROFIT = "DISH_PROFIT";
    public static final String COST_DIAGNOSIS = "COST_DIAGNOSIS";
    public static final String STOCK_REDUCE_QUERY = "STOCK_REDUCE_QUERY";

    public static final String PATH_BUSINESS_OVERVIEW = "business_overview_path";
    public static final String PATH_PURCHASE_OVERVIEW = "purchase_overview_path";
    public static final String PATH_WAREHOUSE_STOCK = "warehouse_stock_overview_path";
    public static final String PATH_DISH_PROFIT = "dish_profit_path";
    public static final String PATH_COST_DIAGNOSIS = "cost_diagnosis_path";
    public static final String PATH_STOCK_REDUCE_QUERY = "stock_reduce_query_path";

    private String intentCode;
    private String pathCode;
    private String topic;

    private boolean inheritedFromPreviousTurn;
    private String inheritedFromIntentCode;

    /**
     * 结构化子意图（如 {@link AiQuerySemanticLexicon#STRUCTURED_PURCHASE_SOURCE_SUMMARY}、{@link AiQuerySemanticLexicon#STRUCTURED_SUPPLIER_AMOUNT_RANKING}）。
     */
    private String structuredIntentDetail;

    /**
     * 采购来源聚焦：{@link AiQuerySemanticLexicon#SOURCE_SELF_PURCHASE} / {@link AiQuerySemanticLexicon#SOURCE_SUPPLIER_PURCHASE} 等。
     */
    private String purchaseSourceType;

    /**
     * 第一版：经营/生意、库存、采购、菜品利润、成本 等关键词；无命中时 intent/path 为 null。
     * 采购口语（自采/供货商采购等）见 {@link AiQuerySemanticLexicon#mergePurchaseCuesInto}。
     * <p>
     * <b>仅处理本轮用户消息中的显式意图</b>；多轮追问（如「那汀兰餐厅呢？」「这个月呢？」）由
     * {@link com.nongxinle.ai.conversation.AiFollowUpResolver} / {@link com.nongxinle.ai.resolver.AiResolvedQueryContextResolver}
     * 合并后再得到 {@link com.nongxinle.ai.context.AiResolvedQueryContext#getEffectiveIntentCode()} 等。
     */
    public static AiResolvedQueryIntent fromUserMessage(String rawMessage) {
        String msg = rawMessage == null ? "" : rawMessage.trim().toLowerCase(Locale.ROOT);
        AiResolvedQueryIntent out;

        if (containsAny(msg, "菜品利润", "菜品毛利", "哪些菜赚钱", "菜赚钱", "菜不赚钱", "不赚钱的菜", "毛利怎么样", "菜品赚钱",
                "利润怎么样", "利润如何", "利润多少", "利润情况", "利润呢",
                "盈利怎么样", "盈利如何", "盈利多少",
                "门店利润", "经营利润", "盈亏怎么样", "盈亏如何",
                "赚钱怎么样", "赚钱如何")) {
            out = build(DISH_PROFIT, PATH_DISH_PROFIT, "菜品毛利/利润");
        } else if (containsAny(msg, "库存怎么样", "库存有多少", "库存情况", "现在库存", "库存如何", "还有多少库存")) {
            out = build(WAREHOUSE_STOCK_OVERVIEW, PATH_WAREHOUSE_STOCK, "库存概览");
        } else if (declaresStandaloneStockReduceQuery(rawMessage, msg)) {
            out = build(STOCK_REDUCE_QUERY, PATH_STOCK_REDUCE_QUERY, "出库/核销查询");
        } else if (containsAny(msg, "采购怎么样", "采购情况", "采购如何", "采购金额", "采购多少", "采购总额", "采购笔数",
                "进货多少", "进货金额", "进货笔数", "订货多少",
                "采购呢", "采购吗", "集团采购", "所有门店采购", "全部门店采购", "全部门店的采购", "门店采购")) {
            out = build(PURCHASE_OVERVIEW, PATH_PURCHASE_OVERVIEW, "采购概览");
        } else if (containsAny(msg, "成本怎么样", "成本情况", "成本如何")) {
            out = build(COST_DIAGNOSIS, PATH_COST_DIAGNOSIS, "成本诊断");
        } else if (containsAny(msg, "经营怎么样", "生意怎么样", "经营情况", "生意如何", "这个月生意", "本月经营",
                "经营怎么样呢", "生意怎么样呢", "经营如何", "生意如何", "经营咋样", "生意咋样")) {
            out = build(BUSINESS_OVERVIEW, PATH_BUSINESS_OVERVIEW, "经营概览");
        } else if (messageDeclaresPurchasePathWithoutStrongerDomain(msg)) {
            // 兜底：口语里带「采购」且未命中上方经营/库存/菜品/成本域，统一走采购概览（含「全部门店的采购呢？」等）
            out = build(PURCHASE_OVERVIEW, PATH_PURCHASE_OVERVIEW, "采购概览");
        } else {
            out = AiResolvedQueryIntent.builder()
                    .intentCode(null)
                    .pathCode(null)
                    .topic(null)
                    .inheritedFromPreviousTurn(false)
                    .inheritedFromIntentCode(null)
                    .build();
        }
        String compactNoSpaces = rawMessage == null ? "" : rawMessage.replace(" ", "");
        if (AiQuerySemanticLexicon.looksLikeDishLowProfitReasonQuestion(compactNoSpaces)
                && StringUtils.hasText(AiQuerySemanticLexicon.tryExtractDishNameForReasonQuestion(rawMessage))) {
            String p = out.getPathCode();
            if (p == null || !PATH_DISH_PROFIT.equals(p)) {
                out.setIntentCode(DISH_PROFIT);
                out.setPathCode(PATH_DISH_PROFIT);
                out.setTopic("菜品毛利/利润");
            }
        }
        AiQuerySemanticLexicon.mergePurchaseCuesInto(out, rawMessage);
        AiQuerySemanticLexicon.mergeStockReduceCuesInto(out, rawMessage);
        AiQuerySemanticLexicon.mergeDishProfitCuesInto(out, rawMessage);
        if (PATH_PURCHASE_OVERVIEW.equals(out.getPathCode())
                && (out.getStructuredIntentDetail() == null || out.getStructuredIntentDetail().isBlank())) {
            out.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_OVERVIEW_SUMMARY);
        }
        if (PATH_STOCK_REDUCE_QUERY.equals(out.getPathCode())
                && (out.getStructuredIntentDetail() == null || out.getStructuredIntentDetail().isBlank())) {
            out.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY);
        }
        if (PATH_DISH_PROFIT.equals(out.getPathCode())
                && (out.getStructuredIntentDetail() == null || out.getStructuredIntentDetail().isBlank())) {
            out.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_OVERVIEW);
        }
        return out;
    }

    /** 本条是否像「出库/核销」独占问法（先于采购/兜底采购命中）。 */
    private static boolean declaresStandaloneStockReduceQuery(String rawMessage, String normalizedLower) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return false;
        }
        String s = rawMessage.replace(" ", "").toLowerCase(Locale.ROOT);
        if (s.contains("出库成本")) {
            return false;
        }
        if (AiQuerySemanticLexicon.looksLikeGoodsOutboundRanking(rawMessage)) {
            return true;
        }
        if (s.contains("生产耗用") || s.contains("生产成本") || s.contains("成本耗用")
                || s.contains("制作成本") || s.contains("制作消耗") || s.contains("做菜成本")
                || s.contains("菜品制作消耗") || s.contains("正常制作消耗")) {
            return true;
        }
        if ((s.contains("损耗") || s.contains("废弃"))
                && !normalizedLower.contains("库存怎么样")
                && !normalizedLower.contains("库存有多少")
                && !normalizedLower.contains("库存情况")
                && !normalizedLower.contains("库存如何")
                && !normalizedLower.contains("还有多少库存")) {
            return true;
        }
        if (s.contains("报损") || s.contains("损失")) {
            return true;
        }
        if (s.contains("退货")) {
            return s.contains("出库") || s.contains("核销") || s.contains("多少") || s.contains("金额") || s.contains("钱");
        }
        return s.contains("出库") || s.contains("核销");
    }

    /** 本条是否应独占走 {@link #PATH_STOCK_REDUCE_QUERY}（Planner 与时间/成本链路分流用）。 */
    public static boolean messageDeclaresStandaloneStockReduce(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return false;
        }
        String normalizedLower = rawMessage.trim().toLowerCase(Locale.ROOT);
        return declaresStandaloneStockReduceQuery(rawMessage, normalizedLower);
    }

    private static AiResolvedQueryIntent build(String intent, String path, String topic) {
        return AiResolvedQueryIntent.builder()
                .intentCode(intent)
                .pathCode(path)
                .topic(topic)
                .inheritedFromPreviousTurn(false)
                .inheritedFromIntentCode(null)
                .build();
    }

    private static boolean containsAny(String normalizedLower, String... needles) {
        for (String n : needles) {
            if (normalizedLower.contains(n.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 前置分支已排除经营/库存/菜品/成本等域；此处仅兜底「包含采购域」的口语（如全部门店的采购呢）。
     */
    private static boolean messageDeclaresPurchasePathWithoutStrongerDomain(String normalizedLower) {
        return normalizedLower.contains("采购");
    }
}
