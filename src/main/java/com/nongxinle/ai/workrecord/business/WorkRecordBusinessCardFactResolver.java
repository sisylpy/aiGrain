package com.nongxinle.ai.workrecord.business;

import com.nongxinle.ai.workrecord.WorkRecordBusinessCardErrors;
import com.nongxinle.ai.workrecord.WorkRecordBusinessCardException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Component
public class WorkRecordBusinessCardFactResolver {

    public WorkRecordBusinessCardFactResult resolve(
            List<Map<String, Object>> cards,
            String cardType,
            WorkRecordItemKey itemKey,
            String optionalAnswerPlanType) {

        Map<String, Object> card = WorkRecordBusinessCardCardsJsonSupport.requireCard(cards, cardType);
        Map<String, Object> payload = WorkRecordBusinessCardCardsJsonSupport.payload(card);
        String resolvedPlanType = WorkRecordBusinessCardCardsJsonSupport.resolveAnswerPlanType(card, payload);
        WorkRecordBusinessCardCardsJsonSupport.assertOptionalAnswerPlanType(
                optionalAnswerPlanType, resolvedPlanType, cardType);

        WorkRecordBusinessCardFactResult fact;
        if (InventoryRiskListCardFactExtractor.supports(cardType)) {
            fact = InventoryRiskListCardFactExtractor.extract(card, payload, itemKey);
        } else if (PurchaseGoodsRankingCardFactExtractor.supports(cardType)) {
            fact = PurchaseGoodsRankingCardFactExtractor.extract(card, payload, itemKey);
        } else if (PurchaseGoodsBusinessAnalysisCardFactExtractor.supports(cardType)) {
            fact = PurchaseGoodsBusinessAnalysisCardFactExtractor.extract(card, payload, itemKey);
        } else {
            throw new WorkRecordBusinessCardException(
                    WorkRecordBusinessCardErrors.CARD_UNSUPPORTED,
                    "unsupported sourceCardType for work record: " + cardType);
        }

        if (!StringUtils.hasText(fact.getSourceFactText())) {
            throw new WorkRecordBusinessCardException(
                    WorkRecordBusinessCardErrors.ITEM_NOT_FOUND, "resolved fact text is empty");
        }
        fact.setResolvedAnswerPlanType(resolvedPlanType);
        return fact;
    }
}
