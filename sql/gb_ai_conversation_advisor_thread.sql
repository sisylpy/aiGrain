-- ============================================================================
-- 顾问会话线程：gb_ai_conversation 锚定 userId + advisorId（D-Advisor-09B）
--   gb_ai_conversation_advisor_id  —— 绑定 gb_ai_advisor
--   gb_ai_conversation_thread_kind —— 线程类别：顾问填 ADVISOR
--
-- 建议：已有 gb_ai_conversation 时再执行；一般放在 gb_ai_conversation_history_extensions.sql 之后。
-- 幂等：列已存在则跳过整条 ALTER。
-- ============================================================================

SET @col_advisor_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'gb_ai_conversation'
      AND column_name = 'gb_ai_conversation_advisor_id'
);

SET @sql_advisor_thread := IF(
    @col_advisor_exists = 0,
    'ALTER TABLE gb_ai_conversation '
        'ADD COLUMN gb_ai_conversation_advisor_id BIGINT DEFAULT NULL '
        'COMMENT ''gb_ai_advisor，顾问专属会话'', '
        'ADD COLUMN gb_ai_conversation_thread_kind VARCHAR(32) DEFAULT NULL '
        'COMMENT ''ADVISOR=顾问长期会话'' AFTER gb_ai_conversation_advisor_id',
    'SELECT ''skip: advisor/thread columns exist'' AS notice'
);

PREPARE stmt_advisor_thread FROM @sql_advisor_thread;
EXECUTE stmt_advisor_thread;
DEALLOCATE PREPARE stmt_advisor_thread;
