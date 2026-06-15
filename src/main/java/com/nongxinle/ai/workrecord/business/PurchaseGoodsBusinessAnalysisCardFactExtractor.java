package com.nongxinle.ai.workrecord.business;

import com.nongxinle.ai.dto.business.PurchaseGoodsBusinessAnalysisAnswerPlan;
import com.nongxinle.ai.workrecord.WorkRecordConstants;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@link PurchaseGoodsBusinessAnalysisAnswerPlan#CARD_TYPE} 整卡。 */
final class PurchaseGoodsBusinessAnalysisCardFactExtractor {

    static final String CARD_TYPE = PurchaseGoodsBusinessAnalysisAnswerPlan.CARD_TYPE;

    private PurchaseGoodsBusinessAnalysisCardFactExtractor() {
    }

    static boolean supports(String cardType) {
        return CARD_TYPE.equals(cardType);
    }

    static WorkRecordBusinessCardFactResult extract(
            Map<String, Object> card, Map<String, Object> payload, WorkRecordItemKey itemKey) {
        if (!itemKey.wholeCard()) {
            throw new com.nongxinle.ai.workrecord.WorkRecordBusinessCardException(
                    com.nongxinle.ai.workrecord.WorkRecordBusinessCardErrors.ITEM_NOT_FOUND,
                    CARD_TYPE + " only supports sourceItemKey=" + WorkRecordConstants.ITEM_KEY_WHOLE_CARD);
        }

        String goodsName = WorkRecordBusinessCardFactTextSupport.firstNonBlank(payload, "goodsName");
        String disGoodsId = WorkRecordBusinessCardFactTextSupport.firstNonBlank(payload, "disGoodsId");

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("cardType", CARD_TYPE);
        snapshot.put("goodsName", payload.get("goodsName"));
        snapshot.put("disGoodsId", payload.get("disGoodsId"));
        snapshot.put("scopeLabel", payload.get("scopeLabel"));
        snapshot.put("purchaseTimeLabel", payload.get("purchaseTimeLabel"));
        snapshot.put("inventorySnapshotLabel", payload.get("inventorySnapshotLabel"));
        snapshot.put("salesBaselineLabel", payload.get("salesBaselineLabel"));
        snapshot.put("dominantPurchaseSource", payload.get("dominantPurchaseSource"));
        snapshot.put("status", payload.get("status"));

        String factText =
                WorkRecordBusinessCardFactTextSupport.joinLines(
                        List.of(
                                WorkRecordBusinessCardFactTextSupport.line("商品", goodsName),
                                WorkRecordBusinessCardFactTextSupport.line(
                                        "采购周期", payload.get("purchaseTimeLabel")),
                                WorkRecordBusinessCardFactTextSupport.line(
                                        "库存快照", payload.get("inventorySnapshotLabel")),
                                WorkRecordBusinessCardFactTextSupport.line(
                                        "销量基线", payload.get("salesBaselineLabel")),
                                WorkRecordBusinessCardFactTextSupport.line(
                                        "主要采购来源", payload.get("dominantPurchaseSource")),
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
