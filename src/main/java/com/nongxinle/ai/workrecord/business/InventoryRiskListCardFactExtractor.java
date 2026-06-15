package com.nongxinle.ai.workrecord.business;

import com.nongxinle.ai.graph.business.WarehouseAnswerPlanCardSupport;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@link WarehouseAnswerPlanCardSupport#CARD_TYPE_INVENTORY_RISK} 单商品行。 */
final class InventoryRiskListCardFactExtractor {

    static final String CARD_TYPE = WarehouseAnswerPlanCardSupport.CARD_TYPE_INVENTORY_RISK;

    private InventoryRiskListCardFactExtractor() {
    }

    static boolean supports(String cardType) {
        return CARD_TYPE.equals(cardType);
    }

    static WorkRecordBusinessCardFactResult extract(
            Map<String, Object> card, Map<String, Object> payload, WorkRecordItemKey itemKey) {
        List<Map<String, Object>> rows =
                WorkRecordBusinessCardCardsJsonSupport.mergeRows(payload, "riskItems");
        Map<String, Object> row =
                WorkRecordBusinessCardCardsJsonSupport.findRowByItemKey(rows, itemKey);

        String goodsName = WorkRecordBusinessCardFactTextSupport.firstNonBlank(row, "goodsName");
        String goodsId = WorkRecordBusinessCardFactTextSupport.firstNonBlank(row, "goodsId");

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("cardType", CARD_TYPE);
        snapshot.put("row", row);
        snapshot.put("timeLabel", payload.get("timeLabel"));
        snapshot.put("stockSnapshotLabel", payload.get("stockSnapshotLabel"));
        snapshot.put("scopeLabel", payload.get("scopeLabel"));

        String factText =
                WorkRecordBusinessCardFactTextSupport.joinLines(
                        List.of(
                                WorkRecordBusinessCardFactTextSupport.line("商品", goodsName),
                                WorkRecordBusinessCardFactTextSupport.line(
                                        "当前库存",
                                        formatWeight(row.get("restWeight"), row.get("weightUnit"))),
                                WorkRecordBusinessCardFactTextSupport.line("可支撑天数", row.get("coverDays")),
                                WorkRecordBusinessCardFactTextSupport.line("风险等级", row.get("riskLevel")),
                                WorkRecordBusinessCardFactTextSupport.line("说明", row.get("riskReason")),
                                WorkRecordBusinessCardFactTextSupport.line(
                                        "时间",
                                        WorkRecordBusinessCardFactTextSupport.firstNonBlank(
                                                payload, "stockSnapshotLabel", "timeLabel")),
                                WorkRecordBusinessCardFactTextSupport.line(
                                        "范围", payload.get("scopeLabel"))));

        return WorkRecordBusinessCardFactResult.builder()
                .sourceEntityType("GOODS")
                .sourceEntityId(goodsId)
                .sourceEntityName(goodsName)
                .sourceFactSnapshot(WorkRecordBusinessCardFactTextSupport.snapshotJson(snapshot))
                .sourceFactText(factText)
                .build();
    }

    private static String formatWeight(Object weight, Object unit) {
        if (weight == null) {
            return null;
        }
        String w = String.valueOf(weight).trim();
        if (unit == null || String.valueOf(unit).trim().isEmpty()) {
            return w;
        }
        return w + String.valueOf(unit).trim();
    }
}
