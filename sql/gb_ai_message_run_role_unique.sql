-- ============================================================================
-- Run 消息幂等：同一会话 + Run + 角色唯一（真实 POST /api/ai/runs 落库）
--
-- 【重复执行】下方脚本已按「索引是否已存在」分支处理：已存在则跳过，不报 1061。
--   若你曾在库里手工建过同名索引，也会被视为已存在。
--
-- 【数据冲突】若表中已存在重复的 (conversation_id, run_id, role) 且 run_id 非 NULL，
--   首次 ADD UNIQUE 仍会失败，需先清理重复行后再执行。
--
-- MySQL InnoDB：唯一索引中「NULL」与「NULL」不相等，多条 (同一 conversation_id, run_id IS NULL, role)
--   可同时存在，因此普通聊天未填 gb_ai_message_run_id 时仍可插入任意多条 user/assistant。
-- Run 落库行必须写入非 NULL 的 gb_ai_message_run_id，由 (conversation_id, run_id, role) 三元组幂等。
-- ============================================================================

SET @uk_gb_ai_msg_conv_run_role_exists := (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'gb_ai_message'
      AND index_name = 'uk_gb_ai_msg_conv_run_role'
);

SET @sql_gb_ai_msg_run_role := IF(
    @uk_gb_ai_msg_conv_run_role_exists = 0,
    'ALTER TABLE gb_ai_message ADD UNIQUE KEY uk_gb_ai_msg_conv_run_role (gb_ai_message_conversation_id, gb_ai_message_run_id, gb_ai_message_role)',
    'SELECT ''skip: uk_gb_ai_msg_conv_run_role already exists'' AS gb_ai_message_index_notice'
);

PREPARE stmt_gb_ai_msg_run_role FROM @sql_gb_ai_msg_run_role;
EXECUTE stmt_gb_ai_msg_run_role;
DEALLOCATE PREPARE stmt_gb_ai_msg_run_role;
