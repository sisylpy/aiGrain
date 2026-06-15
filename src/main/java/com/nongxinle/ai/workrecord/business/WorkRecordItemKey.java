package com.nongxinle.ai.workrecord.business;

import com.nongxinle.ai.workrecord.WorkRecordBusinessCardErrors;
import com.nongxinle.ai.workrecord.WorkRecordBusinessCardException;
import com.nongxinle.ai.workrecord.WorkRecordConstants;
import org.springframework.util.StringUtils;

public record WorkRecordItemKey(String raw, String field, String value, boolean wholeCard) {

    public static WorkRecordItemKey parse(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new WorkRecordBusinessCardException(
                    WorkRecordBusinessCardErrors.ITEM_NOT_FOUND, "sourceItemKey required");
        }
        String trimmed = raw.trim();
        if (WorkRecordConstants.ITEM_KEY_WHOLE_CARD.equals(trimmed)) {
            return new WorkRecordItemKey(trimmed, null, null, true);
        }
        int colon = trimmed.indexOf(':');
        if (colon <= 0 || colon >= trimmed.length() - 1) {
            throw new WorkRecordBusinessCardException(
                    WorkRecordBusinessCardErrors.ITEM_NOT_FOUND, "invalid sourceItemKey: " + raw);
        }
        String field = trimmed.substring(0, colon).trim();
        String value = trimmed.substring(colon + 1).trim();
        if (!StringUtils.hasText(field) || !StringUtils.hasText(value)) {
            throw new WorkRecordBusinessCardException(
                    WorkRecordBusinessCardErrors.ITEM_NOT_FOUND, "invalid sourceItemKey: " + raw);
        }
        if (!isSupportedField(field)) {
            throw new WorkRecordBusinessCardException(
                    WorkRecordBusinessCardErrors.ITEM_NOT_FOUND, "unsupported itemKey field: " + field);
        }
        return new WorkRecordItemKey(trimmed, field, value, false);
    }

    private static boolean isSupportedField(String field) {
        return "disGoodsId".equals(field)
                || "goodsId".equals(field)
                || "batchId".equals(field)
                || "stockBatchId".equals(field);
    }

    public boolean matchesRowValue(Object rowValue) {
        if (rowValue == null) {
            return false;
        }
        return value.equals(String.valueOf(rowValue).trim());
    }
}
