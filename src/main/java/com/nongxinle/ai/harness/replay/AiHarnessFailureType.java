package com.nongxinle.ai.harness.replay;

/**
 * Harness Replay 断言失败时的分类，便于回归时快速定位层（非 LLM）。
 */
public enum AiHarnessFailureType {

    INTENT_MISMATCH,
    PATH_MISMATCH,
    TIME_WINDOW_MISMATCH,
    TIME_SOURCE_MISMATCH,
    SCOPE_TYPE_MISMATCH,
    STORE_SCOPE_MISMATCH,
    DEPARTMENT_SCOPE_MISMATCH,
    PURCHASE_SOURCE_MISMATCH,
    TOOL_ARGUMENT_MISMATCH,
    SQL_RESULT_MISMATCH,
    COMPOSER_TEXT_MISMATCH
}
