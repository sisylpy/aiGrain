-- ============================================================================
-- assistant 消息持久化统一 cards[]：gb_ai_message.gb_ai_message_cards_json
-- 与 Run/SSE 同源结构（cardType / title / subtitle / chartType / payload / source）
-- 仅 assistant 行写入；user 行保持 NULL。
-- 可重复执行：列已存在则跳过。
-- ============================================================================

SET @gb_ai_message_cards_json_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'gb_ai_message'
      AND column_name = 'gb_ai_message_cards_json'
);

SET @sql_gb_ai_message_cards_json := IF(
    @gb_ai_message_cards_json_exists = 0,
    'ALTER TABLE gb_ai_message
        ADD COLUMN gb_ai_message_cards_json JSON NULL
        COMMENT ''assistant 统一 cards[] 快照（cardType/title/subtitle/chartType/payload/source）''
        AFTER gb_ai_message_status',
    'SELECT ''skip: gb_ai_message_cards_json already exists'' AS gb_ai_message_cards_json_notice'
);

PREPARE stmt_gb_ai_message_cards_json FROM @sql_gb_ai_message_cards_json;
EXECUTE stmt_gb_ai_message_cards_json;
DEALLOCATE PREPARE stmt_gb_ai_message_cards_json;
