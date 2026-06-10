package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.dto.business.GoodsStockBatchDetailAnswerPlan;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GoodsStockBatchDetailCardSupport {

    public static final String CARD_TITLE = "库存批次";

    private GoodsStockBatchDetailCardSupport() {}

    public static List<Map<String, Object>> buildRunCards(GoodsStockBatchDetailAnswerPlan plan) {
        return buildRunCards(plan, null);
    }

    public static List<Map<String, Object>> buildRunCards(
            GoodsStockBatchDetailAnswerPlan plan, AiResolvedQueryContext rq) {
        if (plan == null || !GoodsStockBatchDetailAnswerPlan.TYPE.equals(plan.getPlanType())) {
            return List.of();
        }
        String goodsName =
                GoodsEntityDisplayNameSupport.resolveDisplayGoodsNameForPlan(rq, plan.getGoodsName());
        Integer disGoodsId =
                GoodsEntityDisplayNameSupport.resolveDisplayDisGoodsIdForPlan(rq, plan.getDisGoodsId());

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("cardType", GoodsStockBatchDetailAnswerPlan.CARD_TYPE);
        String title =
                goodsName == null || goodsName.isBlank()
                        ? CARD_TITLE
                        : goodsName + " · " + CARD_TITLE;
        card.put("title", title);
        card.put("subtitle", plan.getStockSnapshotLabel() == null ? "" : plan.getStockSnapshotLabel());
        card.put("sourceAnswerPlanType", plan.getPlanType());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("goodsName", goodsName);
        payload.put("disGoodsId", disGoodsId);
        payload.put("stockSnapshotLabel", plan.getStockSnapshotLabel());
        payload.put(
                "batchRowFields",
                List.of(
                        "stockBatchId",
                        "inboundDate",
                        "inboundQty",
                        "produceQty",
                        "wasteQty",
                        "lossQty",
                        "returnQty",
                        "employeeMealQty",
                        "otherConsumedQty",
                        "restQty",
                        "unitPrice",
                        "unit",
                        "balanceDifference",
                        "balanceOk"));
        payload.put("batchRows", plan.getBatchRows() == null ? List.of() : plan.getBatchRows());
        payload.put("batchesByUnit", plan.getBatchesByUnit() == null ? List.of() : plan.getBatchesByUnit());
        payload.put("summary", plan.getSummary());
        payload.put("knownGaps", plan.getKnownGaps());
        card.put("payload", payload);
        return List.of(card);
    }
}
