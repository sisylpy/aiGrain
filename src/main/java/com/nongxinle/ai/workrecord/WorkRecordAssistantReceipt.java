package com.nongxinle.ai.workrecord;

import org.springframework.util.StringUtils;

/** 工作记录 assistant message 确定性回执（无第二次 LLM）。 */
public final class WorkRecordAssistantReceipt {

    private WorkRecordAssistantReceipt() {
    }

    public static String success(String categoryName, String polishedContent) {
        String cat = StringUtils.hasText(categoryName) ? categoryName.trim() : "其他";
        String body = polishedContent != null ? polishedContent.trim() : "";
        return "已记录 · " + cat + "\n" + body;
    }

    public static String failure() {
        return WorkRecordConstants.ASSISTANT_FAILURE_TEXT;
    }
}
