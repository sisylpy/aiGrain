-- ============================================================================
-- assistant 消息持久化轻量 contextSummary：gb_ai_message.gb_ai_message_context_summary_json
-- 仅用户可见字段（contextBar / storeText / timeText / dateRangeText / scopeText /
-- permissionScopeText / noticeText）；与 Run/SSE result.contextSummary 同源。
-- 仅 assistant 行写入；user 行保持 NULL。
-- 可重复执行：列已存在则跳过。
-- ============================================================================

SET @gb_ai_message_context_summary_json_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'gb_ai_message'
      AND column_name = 'gb_ai_message_context_summary_json'
);

SET @sql_gb_ai_message_context_summary_json := IF(
    @gb_ai_message_context_summary_json_exists = 0,
    'ALTER TABLE gb_ai_message
        ADD COLUMN gb_ai_message_context_summary_json JSON NULL
        COMMENT ''assistant contextSummary 快照（contextBar/store/time/scope/notice）''
        AFTER gb_ai_message_cards_json',
    'SELECT ''skip: gb_ai_message_context_summary_json already exists'' AS gb_ai_message_context_summary_json_notice'
);

PREPARE stmt_gb_ai_message_context_summary_json FROM @sql_gb_ai_message_context_summary_json;
EXECUTE stmt_gb_ai_message_context_summary_json;
DEALLOCATE PREPARE stmt_gb_ai_message_context_summary_json;
