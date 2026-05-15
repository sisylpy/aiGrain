package com.nongxinle.ai.semantic;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * v2 偶发将「出库/核销」口径排进采购：依赖 LLM 结构化字段纠偏，不扫描用户原文。
 * <p>
 * 触发时把 {@code intent} 归一为 {@link AiResolvedQueryIntent#STOCK_REDUCE_QUERY}，并清空误设的
 * {@code purchaseSourceType=OUTBOUND}、采购类 {@code rankingType} 等；合并阶段再由
 * {@link AiQuerySemanticLlmMergeHelper} 落默认 overview 或多店出库排行 wire。
 */
public final class AiQuerySemanticV2StockReducePurchaseDeconflictNormalizer {

    public record Result(AiQuerySemanticParseResult semantic, Map<String, Object> notes) {
        static Result of(AiQuerySemanticParseResult s, Map<String, Object> n) {
            return new Result(s, n == null || n.isEmpty() ? null : n);
        }
    }

    private AiQuerySemanticV2StockReducePurchaseDeconflictNormalizer() {
    }

    public static Result apply(AiQuerySemanticParseResult in) {
        if (in == null || in.isParseMissing()) {
            return Result.of(in, null);
        }
        String iu = intentUpper(in.getIntent());
        AiQuerySemanticParseResult.MetricPart m = in.getMetric();
        boolean purchaseOutbound = purchaseOutboundSourceType(m);
        boolean pmStock = primaryMetricSuggestsStockReduce(m == null ? null : m.getPrimaryMetric());
        boolean rtStock = rankingTypeSuggestsStockReduce(m == null ? null : m.getRankingType());
        boolean stockSignal = purchaseOutbound || pmStock || rtStock;

        boolean purchaseMislabeled = isPurchaseClassIntent(iu) && stockSignal;
        boolean compareStoreStock = "COMPARE_STORE".equals(iu) && (pmStock || rtStock);
        boolean alreadyStock = isStockReduceClassIntent(iu);

        if (!purchaseMislabeled && !compareStoreStock && !(alreadyStock && purchaseOutbound)) {
            return Result.of(in, null);
        }

        Map<String, Object> notes = new LinkedHashMap<>();
        notes.put("stockReducePurchaseDeconflict", true);
        notes.put("fromIntent", iu);

        if (purchaseMislabeled || compareStoreStock) {
            notes.put("toIntent", AiResolvedQueryIntent.STOCK_REDUCE_QUERY);
            AiQuerySemanticParseResult.MetricPart clean =
                    AiQuerySemanticParseResult.MetricPart.builder()
                            .primaryMetric("stock_reduce")
                            .rankingType(null)
                            .purchaseSourceType(null)
                            .stockReduceType(null)
                            .build();
            AiQuerySemanticParseResult out =
                    in.toBuilder()
                            .intent(AiResolvedQueryIntent.STOCK_REDUCE_QUERY)
                            .intentAction("OVERRIDE")
                            .metric(clean)
                            .build();
            return Result.of(out, notes);
        }

        // alreadyStock + OUTBOUND in purchaseSourceType：仅去掉非法采购源标签
        notes.put("clearedPurchaseSourceTypeOutbound", true);
        AiQuerySemanticParseResult.MetricPart stripped =
                AiQuerySemanticParseResult.MetricPart.builder()
                        .primaryMetric(m != null ? m.getPrimaryMetric() : null)
                        .rankingType(m != null ? m.getRankingType() : null)
                        .purchaseSourceType(null)
                        .stockReduceType(m != null ? m.getStockReduceType() : null)
                        .build();
        return Result.of(in.toBuilder().metric(stripped).build(), notes);
    }

    private static String intentUpper(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        return raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private static boolean isPurchaseClassIntent(String iu) {
        return "PURCHASE_OVERVIEW".equals(iu)
                || "PROCUREMENT_OVERVIEW".equals(iu)
                || "PURCHASE".equals(iu)
                || "PROCUREMENT".equals(iu);
    }

    private static boolean isStockReduceClassIntent(String iu) {
        return "STOCK_REDUCE_QUERY".equals(iu) || "STOCK_OUT".equals(iu) || "WRITE_OFF".equals(iu);
    }

    private static boolean purchaseOutboundSourceType(AiQuerySemanticParseResult.MetricPart m) {
        if (m == null || !StringUtils.hasText(m.getPurchaseSourceType())) {
            return false;
        }
        return "OUTBOUND".equals(m.getPurchaseSourceType().trim().toUpperCase(Locale.ROOT));
    }

    /** 基于 metric.primaryMetric 结构化串（模型输出，非用户原文）。 */
    private static boolean primaryMetricSuggestsStockReduce(String pm) {
        if (!StringUtils.hasText(pm)) {
            return false;
        }
        String t = pm.trim();
        String u = t.toUpperCase(Locale.ROOT).replace('-', '_');
        if (u.contains("OUTBOUND")) {
            return true;
        }
        if (u.contains("STOCK_REDUCE") || u.contains("STOCKREDUCE")) {
            return true;
        }
        if (u.contains("WRITE_OFF") || u.contains("WRITEOFF")) {
            return true;
        }
        if (u.contains("CONSUMPTION") || u.contains("CONSUME")) {
            return true;
        }
        if (u.contains("WASTE") || u.contains("SCRAP") || u.contains("LOSS") || u.contains("SHRINK")) {
            return true;
        }
        if (u.contains("RETURN")) {
            return true;
        }
        return t.contains("出库")
                || t.contains("核销")
                || t.contains("耗用")
                || t.contains("报损")
                || t.contains("损耗")
                || t.contains("废弃")
                || t.contains("退货");
    }

    private static boolean rankingTypeSuggestsStockReduce(String rt) {
        if (!StringUtils.hasText(rt)) {
            return false;
        }
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(rt.trim());
        String wire = canon != null ? canon : rt.trim();
        return AiQuerySemanticLexicon.isStructuredStockReduceDetail(wire);
    }
}
