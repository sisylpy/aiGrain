package com.nongxinle.ai.workrecord.business;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.nongxinle.ai.workrecord.WorkRecordBusinessCardErrors;
import com.nongxinle.ai.workrecord.WorkRecordBusinessCardException;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WorkRecordBusinessCardCardsJsonSupport {

    private WorkRecordBusinessCardCardsJsonSupport() {
    }

    public static List<Map<String, Object>> parseCardsArray(String cardsJson) {
        if (!StringUtils.hasText(cardsJson)) {
            throw new WorkRecordBusinessCardException(
                    WorkRecordBusinessCardErrors.CARDS_MISSING, "assistant message has no cards_json");
        }
        try {
            JSONArray arr = JSON.parseArray(cardsJson.trim());
            if (arr == null || arr.isEmpty()) {
                throw new WorkRecordBusinessCardException(
                        WorkRecordBusinessCardErrors.CARDS_MISSING, "cards_json is empty");
            }
            List<Map<String, Object>> out = new ArrayList<>(arr.size());
            for (int i = 0; i < arr.size(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                if (obj != null && !obj.isEmpty()) {
                    out.add(new LinkedHashMap<>(obj));
                }
            }
            if (out.isEmpty()) {
                throw new WorkRecordBusinessCardException(
                        WorkRecordBusinessCardErrors.CARDS_MISSING, "cards_json has no valid card objects");
            }
            return out;
        } catch (WorkRecordBusinessCardException e) {
            throw e;
        } catch (Exception e) {
            throw new WorkRecordBusinessCardException(
                    WorkRecordBusinessCardErrors.CARDS_MISSING, "cards_json parse failed: " + e.getMessage());
        }
    }

    public static Map<String, Object> requireCard(List<Map<String, Object>> cards, String cardType) {
        if (!StringUtils.hasText(cardType)) {
            throw new WorkRecordBusinessCardException(
                    WorkRecordBusinessCardErrors.CARD_NOT_FOUND, "sourceCardType required");
        }
        String expected = cardType.trim();
        for (Map<String, Object> card : cards) {
            Object ct = card.get("cardType");
            if (ct != null && expected.equals(ct.toString().trim())) {
                return card;
            }
        }
        throw new WorkRecordBusinessCardException(
                WorkRecordBusinessCardErrors.CARD_NOT_FOUND, "cardType not found in cards_json: " + expected);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> payload(Map<String, Object> card) {
        Object payload = card.get("payload");
        if (payload instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Map.of();
    }

    public static String resolveAnswerPlanType(Map<String, Object> card, Map<String, Object> payload) {
        Object onCard = card.get("sourceAnswerPlanType");
        if (onCard != null && StringUtils.hasText(onCard.toString())) {
            return onCard.toString().trim();
        }
        Object inPayload = payload.get("planType");
        if (inPayload != null && StringUtils.hasText(inPayload.toString())) {
            return inPayload.toString().trim();
        }
        Object source = card.get("source");
        if (source instanceof Map<?, ?> sm) {
            Object ap = sm.get("answerPlan");
            if (ap != null && StringUtils.hasText(ap.toString())) {
                return ap.toString().trim();
            }
        }
        return null;
    }

    public static void assertOptionalAnswerPlanType(
            String requested, String resolved, String cardType) {
        if (!StringUtils.hasText(requested)) {
            return;
        }
        if (!StringUtils.hasText(resolved) || !requested.trim().equals(resolved)) {
            throw new WorkRecordBusinessCardException(
                    WorkRecordBusinessCardErrors.CARD_NOT_FOUND,
                    "sourceAnswerPlanType mismatch for cardType=" + cardType);
        }
    }

    public static Map<String, Object> findRowByItemKey(
            List<Map<String, Object>> rows, WorkRecordItemKey itemKey) {
        if (itemKey.wholeCard()) {
            throw new WorkRecordBusinessCardException(
                    WorkRecordBusinessCardErrors.ITEM_NOT_FOUND, "row lookup invalid for whole card itemKey");
        }
        if (rows == null || rows.isEmpty()) {
            throw new WorkRecordBusinessCardException(
                    WorkRecordBusinessCardErrors.ITEM_NOT_FOUND, "card has no rows for " + itemKey.raw());
        }
        for (Map<String, Object> row : rows) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            Object val = row.get(itemKey.field());
            if (itemKey.matchesRowValue(val)) {
                return row;
            }
        }
        throw new WorkRecordBusinessCardException(
                WorkRecordBusinessCardErrors.ITEM_NOT_FOUND, "item not found: " + itemKey.raw());
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> mergeRows(Map<String, Object> payload, String... arrayKeys) {
        List<Map<String, Object>> merged = new ArrayList<>();
        for (String key : arrayKeys) {
            Object raw = payload.get(key);
            if (raw instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> m) {
                        merged.add(new LinkedHashMap<>((Map<String, Object>) m));
                    }
                }
            }
        }
        return merged;
    }
}
