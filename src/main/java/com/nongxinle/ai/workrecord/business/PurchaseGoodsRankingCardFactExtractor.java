package com.nongxinle.ai.workrecord.business;

import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 采购商品排行卡（金额/次数/数量）单商品行。 */
final class PurchaseGoodsRankingCardFactExtractor {

    private PurchaseGoodsRankingCardFactExtractor() {
    }

    static boolean supports(String cardType) {
        return "PURCHASE_GOODS_AMOUNT_RANKING_CARD".equals(cardType)
                || "PURCHASE_GOODS_COUNT_RANKING_CARD".equals(cardType)
                || PurchaseAnswerPlan.CARD_TYPE_PURCHASE_GOODS_QUANTITY_RANKING.equals(cardType);
    }

    static WorkRecordBusinessCardFactResult extract(
            Map<String, Object> card, Map<String, Object> payload, WorkRecordItemKey itemKey) {
        List<Map<String, Object>> rows =
                WorkRecordBusinessCardCardsJsonSupport.mergeRows(payload, "focusRows", "secondaryRows");
        Map<String, Object> row =
                WorkRecordBusinessCardCardsJsonSupport.findRowByItemKey(rows, itemKey);

        String goodsName =
                WorkRecordBusinessCardFactTextSupport.firstNonBlank(row, "goodsName", "goodsTitle", "name");
        String disGoodsId =
                WorkRecordBusinessCardFactTextSupport.firstNonBlank(row, "disGoodsId", "goodsId", "gbDisGoodsId");

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("cardType", card.get("cardType"));
        snapshot.put("row", row);
        snapshot.put("timeLabel", payload.get("timeLabel"));
        snapshot.put("scopeLabel", payload.get("scopeLabel"));
        snapshot.put("planType", payload.get("planType"));

        String metric =
                WorkRecordBusinessCardFactTextSupport.firstNonBlank(
                        row,
                        "purchaseSubtotal",
                        "purchaseAmount",
                        "purchaseCount",
                        "purchaseQuantity",
                        "totalPurchaseQuantity");

        String factText =
                WorkRecordBusinessCardFactTextSupport.joinLines(
                        List.of(
                                WorkRecordBusinessCardFactTextSupport.line("商品", goodsName),
                                WorkRecordBusinessCardFactTextSupport.line("排行", row.get("rank")),
                                WorkRecordBusinessCardFactTextSupport.line("指标值", metric),
                                WorkRecordBusinessCardFactTextSupport.line("时间", payload.get("timeLabel")),
                                WorkRecordBusinessCardFactTextSupport.line("范围", payload.get("scopeLabel"))));

        return WorkRecordBusinessCardFactResult.builder()
                .sourceEntityType("DIS_GOODS")
                .sourceEntityId(disGoodsId)
                .sourceEntityName(goodsName)
                .sourceFactSnapshot(WorkRecordBusinessCardFactTextSupport.snapshotJson(snapshot))
                .sourceFactText(factText)
                .build();
    }
}
