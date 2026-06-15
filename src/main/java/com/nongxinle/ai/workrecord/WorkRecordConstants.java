package com.nongxinle.ai.workrecord;

public final class WorkRecordConstants {

    private WorkRecordConstants() {
    }

    public static final String THREAD_KIND_WORK_RECORD = "WORK_RECORD";
    public static final String CONVERSATION_TYPE_WORK_RECORD = "WORK_RECORD";

    public static final String INPUT_TEXT = "TEXT";
    public static final String INPUT_VOICE_TRANSCRIPT = "VOICE_TRANSCRIPT";
    public static final String INPUT_BUSINESS_CARD = "BUSINESS_CARD";

    public static final String ORIGIN_MANUAL = "MANUAL";
    public static final String ORIGIN_BUSINESS_CARD = "BUSINESS_CARD";

    public static final String ITEM_KEY_WHOLE_CARD = "__CARD__";

    public static final String AI_PENDING = "PENDING";
    public static final String AI_PROCESSING = "PROCESSING";
    public static final String AI_SUCCESS = "SUCCESS";
    public static final String AI_FAILED = "FAILED";

    public static final String CATEGORY_ACTIVE = "ACTIVE";
    public static final String CATEGORY_DISABLED = "DISABLED";

    public static final String DECISION_EXISTING = "EXISTING";
    public static final String DECISION_SUGGEST_NEW = "SUGGEST_NEW";
    public static final String DECISION_OTHER = "OTHER";

    public static final String CATEGORY_CODE_OTHER = "OTHER";

    public static final String WORKSPACE_MODE = "WORK_RECORD";

    public static final String ASSISTANT_FAILURE_TEXT = "记录已保存，但 AI 暂时未完成整理和分类。";
}
