package com.nongxinle.ai.composer.renderer;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.nongxinle.ai.util.AiTimeWindowTextFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public final class WarehouseDeterministicRenderer {

    private static final Logger log = LoggerFactory.getLogger(WarehouseDeterministicRenderer.class);

    public String renderWarehouseStockFallback(AiRunState state) {
        return warehouseStockFallback(state);
    }

    private static String warehouseStockFallback(AiRunState state) {
        AiTimeWindowTextFormatter.UserPhrases tw = AiTimeWindowTextFormatter.forAnswer(state);
        Map<String, Object> wo = DeterministicRendererSupport.extractWarehouseOverviewPayloadForRenderer(state);
        String rankingWire = resolveStockRankingWire(state);
        if (rankingWire != null && isStoreOrWarehouseStockRankingWire(rankingWire)) {
            if (log.isInfoEnabled()) {
                List<Map<String, Object>> amountN = extractStoreRankingList(wo, "storeStockAmountRanking");
                List<Map<String, Object>> itemN = extractStoreRankingList(wo, "storeStockItemCountRanking");
                log.info(
                        "[D6-4B-WH-RANKING] renderer rankingBranch runId={} wire={} woEmpty={} storeStockAmountRankingSize={}"
                                + " storeStockItemCountRankingSize={} hasWarehouseStockRankingDegradedNote={}",
                        state != null ? state.getRunId() : null,
                        rankingWire,
                        wo.isEmpty(),
                        amountN.size(),
                        itemN.size(),
                        StringUtils.hasText(rankingNoteTrim(wo.get("warehouseStockRankingDegradedNote"))));
            }
            return renderStockRankingDeterministic(state, tw, wo, rankingWire).trim();
        }
        if (!wo.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            Object qb = wo.get("queryScopeBanner");
            if (qb != null && !qb.toString().isBlank()) {
                sb.append(qb.toString().trim()).append("\n\n");
            }
            sb.append(tw.getBracketTimeRangeLine()).append("\n");
            boolean group = "GROUP".equalsIgnoreCase(String.valueOf(wo.get("scopeType")).trim());
            boolean groupStoresOnly = group && !DeterministicRendererSupport.warehouseOverviewHasVisibleWarehouses(wo);
            sb.append(group
                    ? (groupStoresOnly
                            ? "说明：以下为集团下属门店合并库存汇总（按门店根部门逐店聚合），不包含营业额、订单、客单价、毛利或利润。\n"
                            : "说明：以下为集团下属门店/库房合并库存汇总（按门店根部门逐店聚合），不包含营业额、订单、客单价、毛利或利润。\n")
                    : "说明：以下按库房库存视角汇总，不包含营业额、订单、客单价、毛利或利润；不作采购员式采购分析。\n");
            sb.append(DeterministicRendererSupport.nz(wo.get("summary"))).append("\n\n");
            sb.append("【库存规模】约有 ").append(DeterministicRendererSupport.plainNumericHint(wo.get("stockItemCount")))
                    .append(" 种商品仍有账面剩余（全库批次约 ")
                    .append(DeterministicRendererSupport.plainNumericHint(wo.get("stockBatchRowCount"))).append(" 行）；库存剩余总金额约 ")
                    .append(DeterministicRendererSupport.plainNumericHint(wo.get("totalStockAmount"))).append(" 元，")
                    .append("账面剩余数量/重量合计约 ").append(DeterministicRendererSupport.plainNumericHint(wo.get("totalStockWeight")))
                    .append("（含不同规格商品）。\n");
            sb.append("【入库】入库金额约 ").append(DeterministicRendererSupport.plainNumericHint(wo.get("inboundAmount")))
                    .append(" 元，入库数量/重量合计约 ").append(DeterministicRendererSupport.plainNumericHint(wo.get("inboundWeight")))
                    .append("（含不同规格商品）。\n");
            sb.append("【核销/出库】出品约 ").append(DeterministicRendererSupport.plainNumericHint(wo.get("produceAmount")))
                    .append(" 元；废弃 ").append(DeterministicRendererSupport.plainNumericHint(wo.get("wasteAmount")))
                    .append(" 元，损耗 ").append(DeterministicRendererSupport.plainNumericHint(wo.get("lossAmount")))
                    .append(" 元，退货 ").append(DeterministicRendererSupport.plainNumericHint(wo.get("returnAmount")))
                    .append(" 元；各类型合计约 ").append(DeterministicRendererSupport.plainNumericHint(wo.get("stockReduceAmount")))
                    .append(" 元。\n\n");
            appendWarehouseConcernSection(sb, "低库存 / 需补货", wo.get("lowStockItems"),
                    WarehouseConcernKind.LOW);
            appendWarehouseConcernSection(sb, "库存偏高 / 建议优先消耗", wo.get("overStockItems"),
                    WarehouseConcernKind.OVER);
            appendWarehouseConcernSection(sb, "早入库批次 / 建议盘点", wo.get("priorityStocktakeItems"),
                    WarehouseConcernKind.INACTIVE);
            DeterministicRendererSupport.appendWarehouseRecommendations(sb, wo.get("recommendations"));
            return sb.toString().trim();
        }
        Map<String, Object> sq = DeterministicRendererSupport.toolDataInnerMap(state, AiBusinessToolIds.STOCK_QUERY);
        Map<String, Object> stk = DeterministicRendererSupport.toolDataInnerMap(state, AiBusinessToolIds.STOCK_REDUCE_QUERY);
        StringBuilder sb = new StringBuilder();
        sb.append(tw.getBracketTimeRangeLine()).append("\n");
        sb.append("说明：以下按库房库存视角汇总，不包含营业额、订单、客单价、毛利或集团经营口径；不作采购员式的采购专项分析。\n");
        boolean hasStock = DeterministicRendererSupport.stockSnapshotHasSignal(sq, stk,
                DeterministicRendererSupport.extractWarehouseOverviewPayloadForRenderer(state));
        if (!sq.isEmpty() && hasStock) {
            sb.append("当前库房库存侧：可见批次约 ")
                    .append(DeterministicRendererSupport.plainNumericHint(sq.get("stockBatchRowCount")))
                    .append(" 行；库存剩余金额约 ")
                    .append(DeterministicRendererSupport.plainNumericHint(sq.get("stockRestSubtotal")))
                    .append(" 元，剩余重量汇总 ")
                    .append(DeterministicRendererSupport.fmtStockWeightCn(sq.get("stockRestWeightTotal")))
                    .append("。\n");
            sb.append("查询区间内入库批次金额约 ")
                    .append(DeterministicRendererSupport.plainNumericHint(sq.get("periodInboundSubtotal")))
                    .append(" 元，入库重量汇总 ")
                    .append(DeterministicRendererSupport.fmtStockWeightCn(sq.get("periodInboundWeightTotal")))
                    .append("。\n");
        }
        if (!stk.isEmpty()) {
            sb.append("核销/出库：生产耗用合计约 ")
                    .append(DeterministicRendererSupport.plainNumericHint(stk.get("productionTotal")))
                    .append("（出品 ")
                    .append(DeterministicRendererSupport.plainNumericHint(stk.get("produceTotal")))
                    .append("，废弃 ")
                    .append(DeterministicRendererSupport.plainNumericHint(stk.get("wasteTotal")))
                    .append("，损耗 ")
                    .append(DeterministicRendererSupport.plainNumericHint(stk.get("lossTotal")))
                    .append("，退货 ")
                    .append(DeterministicRendererSupport.plainNumericHint(stk.get("returnTotal")))
                    .append("）。\n");
        }
        if (!hasStock && stk.isEmpty()) {
            sb.append("你当前账号可查看库房库存数据，但当前库房暂未查询到有效库存记录。\n");
        } else if (!hasStock && !stk.isEmpty()) {
            sb.append("库存快照侧暂未拉到有效剩余汇总，可先依据核销/出库数据核对是否与实物一致。\n");
        }
        sb.append("如需单品预警或批次明细，请在库存管理模块按商品/批次下钻。");
        return sb.toString().trim();
    }

    private static String resolveStockRankingWire(AiRunState state) {
        if (state == null || state.getResolvedQueryContext() == null) {
            return null;
        }
        AiResolvedQueryContext ctx = state.getResolvedQueryContext();
        String raw = null;
        AiResolvedQueryIntent qi = ctx.getQueryIntent();
        if (qi != null && StringUtils.hasText(qi.getStructuredIntentDetail())) {
            raw = qi.getStructuredIntentDetail().trim();
        }
        if (!StringUtils.hasText(raw)
                && ctx.getQuerySemanticParse() != null
                && ctx.getQuerySemanticParse().getMetric() != null) {
            String rt = ctx.getQuerySemanticParse().getMetric().getRankingType();
            if (StringUtils.hasText(rt)) {
                raw = rt.trim();
            }
        }
        return AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(raw);
    }

    private static boolean isStoreOrWarehouseStockRankingWire(String wire) {
        return AiQuerySemanticLexicon.STRUCTURED_STORE_STOCK_AMOUNT_RANKING.equals(wire)
                || AiQuerySemanticLexicon.STRUCTURED_STORE_STOCK_ITEM_COUNT_RANKING.equals(wire)
                || AiQuerySemanticLexicon.STRUCTURED_WAREHOUSE_STOCK_AMOUNT_RANKING.equals(wire)
                || AiQuerySemanticLexicon.STRUCTURED_WAREHOUSE_STOCK_ITEM_COUNT_RANKING.equals(wire);
    }

    private static String renderStockRankingDeterministic(AiRunState state, AiTimeWindowTextFormatter.UserPhrases tw,
            Map<String, Object> wo, String wire) {
        StringBuilder sb = new StringBuilder();
        appendRankingScopeTimeLines(sb, tw, wo, state);
        List<Map<String, Object>> amountRank = extractStoreRankingList(wo, "storeStockAmountRanking");
        List<Map<String, Object>> itemRank = extractStoreRankingList(wo, "storeStockItemCountRanking");
        String degradedNote = rankingNoteTrim(wo.get("warehouseStockRankingDegradedNote"));
        appendWarehouseStockRankingPreambleForWarehouseWire(sb, wire, degradedNote);

        if (AiQuerySemanticLexicon.STRUCTURED_STORE_STOCK_AMOUNT_RANKING.equals(wire)) {
            sb.append("首句结论：");
            if (amountRank.isEmpty()) {
                sb.append("当前没有可用的门店库存排行数据。\n");
            } else {
                Map<String, Object> top = amountRank.get(0);
                sb.append("库存金额最高的是 ").append(rankingStoreName(top)).append("，库存金额 ")
                        .append(DeterministicRendererSupport.plainNumericHint(top.get("totalStockAmount")))
                        .append(" 元，库存商品 ")
                        .append(DeterministicRendererSupport.plainNumericHint(top.get("stockItemCount")))
                        .append(" 种。\n");
            }
            sb.append("门店库存金额排行 Top3：\n");
            appendTop3StoreAmountLines(sb, amountRank, false);
            return sb.toString();
        }

        if (AiQuerySemanticLexicon.STRUCTURED_STORE_STOCK_ITEM_COUNT_RANKING.equals(wire)) {
            sb.append("首句结论：");
            if (itemRank.isEmpty()) {
                sb.append("当前没有可用的门店库存排行数据。\n");
            } else {
                Map<String, Object> top = itemRank.get(0);
                sb.append("库存商品种类最多的是 ").append(rankingStoreName(top)).append("，共 ")
                        .append(DeterministicRendererSupport.plainNumericHint(top.get("stockItemCount")))
                        .append(" 种，库存金额 ")
                        .append(DeterministicRendererSupport.plainNumericHint(top.get("totalStockAmount")))
                        .append(" 元。\n");
            }
            sb.append("门店库存商品种类排行 Top3：\n");
            appendTop3StoreItemCountLines(sb, itemRank, false);
            return sb.toString();
        }

        if (AiQuerySemanticLexicon.STRUCTURED_WAREHOUSE_STOCK_AMOUNT_RANKING.equals(wire)) {
            sb.append("首句结论：");
            appendWarehouseRankingConclusionLine(sb, amountRank.isEmpty());
            sb.append("门店库存金额排行 Top3：\n");
            appendTop3StoreAmountLines(sb, amountRank, false);
            return sb.toString();
        }

        if (AiQuerySemanticLexicon.STRUCTURED_WAREHOUSE_STOCK_ITEM_COUNT_RANKING.equals(wire)) {
            sb.append("首句结论：");
            appendWarehouseRankingConclusionLine(sb, itemRank.isEmpty());
            sb.append("门店库存商品种类排行 Top3：\n");
            appendTop3StoreItemCountLines(sb, itemRank, false);
            return sb.toString();
        }

        return sb.toString();
    }

    private static boolean isWarehouseStockRankingWire(String wire) {
        return AiQuerySemanticLexicon.STRUCTURED_WAREHOUSE_STOCK_AMOUNT_RANKING.equals(wire)
                || AiQuerySemanticLexicon.STRUCTURED_WAREHOUSE_STOCK_ITEM_COUNT_RANKING.equals(wire);
    }

    /**
     * 仓库维排行问法：先 tool 降级说明，再固定一句话明确非仓库榜；不包含在「首句结论」内以免与 Top3 重复。
     */
    private static void appendWarehouseStockRankingPreambleForWarehouseWire(StringBuilder sb, String wire,
            String degradedNote) {
        if (!isWarehouseStockRankingWire(wire)) {
            return;
        }
        if (StringUtils.hasText(degradedNote)) {
            sb.append(degradedNote.trim()).append("\n");
        }
        sb.append("说明：当前不提供真实仓库级库存排行，下面仅为门店维度参考，请勿理解为仓库榜。\n");
    }

    /** 仓库维「首句结论」仅用一行点明有无数据，不重复 preamble 中的降级说明。 */
    private static void appendWarehouseRankingConclusionLine(StringBuilder sb, boolean listEmpty) {
        if (listEmpty) {
            sb.append("当前没有可用的门店库存排行数据。\n");
        } else {
            sb.append("以下为门店维度库存排行参考（非仓库级）。\n");
        }
    }

    private static void appendRankingScopeTimeLines(StringBuilder sb, AiTimeWindowTextFormatter.UserPhrases tw,
            Map<String, Object> wo, AiRunState state) {
        String scope = "";
        if (wo != null && !wo.isEmpty()) {
            Object qb = wo.get("queryScopeBanner");
            if (qb != null && !qb.toString().isBlank()) {
                scope = qb.toString().trim();
            }
        }
        if (scope.isBlank() && state != null && state.getResolvedQueryContext() != null) {
            String b = state.getResolvedQueryContext().getQueryScopeBanner();
            if (StringUtils.hasText(b)) {
                scope = b.trim();
            }
        }
        sb.append("查询范围：").append(scope.isBlank() ? "暂无" : scope).append("\n");
        sb.append("统计时间：").append(plainStatTimeLine(tw)).append("\n");
    }

    /** 排行分支不用「【时间范围】」前缀，避免与 boundary/收敛头重复。 */
    private static String plainStatTimeLine(AiTimeWindowTextFormatter.UserPhrases tw) {
        if (tw == null) {
            return "暂无";
        }
        String d = tw.getDisplayTimeRange();
        if (d != null && !d.isBlank()) {
            return d.trim();
        }
        String b = tw.getBracketTimeRangeLine();
        return b != null && !b.isBlank() ? b.trim() : "暂无";
    }

    private static String rankingNoteTrim(Object o) {
        if (o == null) {
            return "";
        }
        return o.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> extractStoreRankingList(Map<String, Object> wo, String key) {
        if (wo == null || wo.isEmpty()) {
            return List.of();
        }
        Object v = wo.get(key);
        if (!(v instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) {
                out.add((Map<String, Object>) m);
            }
        }
        return out;
    }

    private static String rankingStoreName(Map<String, Object> row) {
        Object n = row.get("storeName");
        if (n != null && !n.toString().isBlank()) {
            return n.toString().trim();
        }
        return "未知门店";
    }

    /**
     * @param repeatEmptyMessage 当 rows 为空时是否输出「当前没有可用的门店库存排行数据」；
     *                           门店 wire 已在首句说明时为 {@code false}。
     */
    private static void appendTop3StoreAmountLines(StringBuilder sb, List<Map<String, Object>> rows,
            boolean repeatEmptyMessage) {
        if (rows == null || rows.isEmpty()) {
            if (repeatEmptyMessage) {
                sb.append("当前没有可用的门店库存排行数据\n");
            }
            return;
        }
        int n = Math.min(3, rows.size());
        for (int i = 0; i < n; i++) {
            Map<String, Object> row = rows.get(i);
            sb.append(i + 1).append(". ").append(rankingStoreName(row)).append("：库存金额 ")
                    .append(DeterministicRendererSupport.plainNumericHint(row.get("totalStockAmount")))
                    .append(" 元，商品 ").append(DeterministicRendererSupport.plainNumericHint(row.get("stockItemCount")))
                    .append(" 种\n");
        }
    }

    private static void appendTop3StoreItemCountLines(StringBuilder sb, List<Map<String, Object>> rows,
            boolean repeatEmptyMessage) {
        if (rows == null || rows.isEmpty()) {
            if (repeatEmptyMessage) {
                sb.append("当前没有可用的门店库存排行数据\n");
            }
            return;
        }
        int n = Math.min(3, rows.size());
        for (int i = 0; i < n; i++) {
            Map<String, Object> row = rows.get(i);
            sb.append(i + 1).append(". ").append(rankingStoreName(row)).append("：商品 ")
                    .append(DeterministicRendererSupport.plainNumericHint(row.get("stockItemCount")))
                    .append(" 种，金额 ")
                    .append(DeterministicRendererSupport.plainNumericHint(row.get("totalStockAmount")))
                    .append(" 元\n");
        }
    }

    private enum WarehouseConcernKind {
        LOW,
        OVER,
        INACTIVE
    }

    private static void appendWarehouseConcernSection(StringBuilder sb, String title, Object listObj,
            WarehouseConcernKind kind) {
        if (!(listObj instanceof java.util.List<?> list) || list.isEmpty()) {
            return;
        }
        sb.append(title).append("：\n");
        int i = 1;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> mm)) {
                continue;
            }
            Object snStore = mm.get("storeName");
            Object nm = mm.get("goodsName");
            if (nm == null || nm.toString().isBlank()) {
                continue;
            }
            String goods = sanitizeWarehouseGoodsLabel(nm.toString().trim());
            Object rw = mm.get("restWeightTotal");
            Object ra = mm.get("restAmountTotal");
            Object bd = mm.get("batchDate");
            Object rw2 = mm.get("restWeight");
            Object batchId = mm.get("stockBatchId");

            sb.append(i++).append(". ");
            if (snStore != null && !snStore.toString().isBlank()) {
                sb.append(snStore.toString().trim()).append(" · ");
            }
            if (kind == WarehouseConcernKind.LOW) {
                Object wSrc = pickWeightForDisplay(rw, rw2);
                sb.append(goods).append("：")
                        .append(formatRestWeightPhrase(wSrc, mm))
                        .append("，金额 ")
                        .append(DeterministicRendererSupport.plainNumericHint(ra))
                        .append(" 元。建议关注补货。\n");
            } else if (kind == WarehouseConcernKind.OVER) {
                sb.append(goods).append("：")
                        .append(formatRestWeightPhrase(rw, mm))
                        .append("，金额 ")
                        .append(DeterministicRendererSupport.plainNumericHint(ra))
                        .append(" 元。\n");
            } else {
                Object wSrc = pickWeightForDisplay(rw2, rw);
                sb.append(goods).append("：");
                if (batchId != null && !batchId.toString().isBlank()) {
                    sb.append("库存批次号 ").append(batchId.toString().trim()).append("，");
                }
                if (bd != null && !bd.toString().isBlank()) {
                    sb.append(bd.toString().trim()).append(" 入库的批次仍有剩余 ")
                            .append(DeterministicRendererSupport.stockWeightNumberOnly(wSrc))
                            .append(" ")
                            .append(weightUnitSuffix(mm))
                            .append("，建议盘点核对。\n");
                } else {
                    sb.append("仍有剩余 ")
                            .append(DeterministicRendererSupport.stockWeightNumberOnly(wSrc))
                            .append(" ")
                            .append(weightUnitSuffix(mm))
                            .append("，建议盘点核对。\n");
                }
            }
            if (i > 9) {
                break;
            }
        }
        sb.append("\n");
    }

    private static Object pickWeightForDisplay(Object primary, Object secondary) {
        if (primary != null && !primary.toString().isBlank()) {
            return primary;
        }
        return secondary;
    }

    /** 与「剩余 0.7 斤」可读口径一致；若条目带 weightDisplayUnit 则用该单位，否则用斤。 */
    private static String formatRestWeightPhrase(Object weightObj, Map<?, ?> item) {
        return "剩余 " + DeterministicRendererSupport.stockWeightNumberOnly(weightObj) + " " + weightUnitSuffix(item);
    }

    private static String weightUnitSuffix(Map<?, ?> item) {
        if (item == null) {
            return DeterministicRendererSupport.W_STOCK_WEIGHT_UNIT;
        }
        Object u = item.get("weightDisplayUnit");
        if (u != null && !u.toString().isBlank()) {
            return u.toString().trim();
        }
        return DeterministicRendererSupport.W_STOCK_WEIGHT_UNIT;
    }

    private static String sanitizeWarehouseGoodsLabel(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("（积压）", "").replace("(积压)", "").trim();
    }
}
