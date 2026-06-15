package com.nongxinle.ai.workrecord.business;

import com.nongxinle.ai.workrecord.WorkRecordSourceCardErrors;
import com.nongxinle.ai.workrecord.WorkRecordSourceCardException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class WorkRecordBusinessCardPayloadLocator {

    public Map<String, Object> resolvePayload(
            String cardType, Map<String, Object> card, WorkRecordItemKey itemKey) {
        Map<String, Object> cardPayload = WorkRecordBusinessCardCardsJsonSupport.payload(card);
        if (itemKey.wholeCard()) {
            if (!PurchaseGoodsBusinessAnalysisCardFactExtractor.supports(cardType)) {
                throw new WorkRecordSourceCardException(
                        WorkRecordSourceCardErrors.BUSINESS_CARD_NOT_FOUND,
                        "whole card itemKey not supported for cardType=" + cardType);
            }
            return new LinkedHashMap<>(cardPayload);
        }
        if (InventoryRiskListCardFactExtractor.supports(cardType)) {
            return rowPayload(cardPayload, itemKey, "riskItems");
        }
        if (PurchaseGoodsRankingCardFactExtractor.supports(cardType)) {
            List<Map<String, Object>> rows =
                    WorkRecordBusinessCardCardsJsonSupport.mergeRows(
                            cardPayload, "focusRows", "secondaryRows");
            return WorkRecordBusinessCardCardsJsonSupport.findRowByItemKey(rows, itemKey);
        }
        throw new WorkRecordSourceCardException(
                WorkRecordSourceCardErrors.BUSINESS_CARD_NOT_FOUND,
                "unsupported cardType for source payload: " + cardType);
    }

    private static Map<String, Object> rowPayload(
            Map<String, Object> cardPayload, WorkRecordItemKey itemKey, String... arrayKeys) {
        try {
            List<Map<String, Object>> rows =
                    WorkRecordBusinessCardCardsJsonSupport.mergeRows(cardPayload, arrayKeys);
            Map<String, Object> row =
                    WorkRecordBusinessCardCardsJsonSupport.findRowByItemKey(rows, itemKey);
            return new LinkedHashMap<>(row);
        } catch (com.nongxinle.ai.workrecord.WorkRecordBusinessCardException ex) {
            throw new WorkRecordSourceCardException(
                    WorkRecordSourceCardErrors.BUSINESS_CARD_NOT_FOUND, ex.getMessage());
        }
    }
}
