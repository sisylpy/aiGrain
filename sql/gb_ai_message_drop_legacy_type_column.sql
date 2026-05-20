-- ============================================================================
-- 移除单智能体时期 gb_ai_message.gb_ai_message_type（0/1/2 与会话旧 type 同源枚举）。
--
-- Java 已从 GbAiMessageEntity / upsertRunScopedMessage / API DTO 删除该列语义；
-- 消息区分请使用 gb_ai_message_role、gb_ai_message_run_id、gb_ai_message_status。
--
-- 执行前：在 information_schema.columns 确认存在 gb_ai_message_type（MySQL 5.7 ALTER 不支持 IF EXISTS）。
-- 重复执行会报错 Unknown column。
-- ============================================================================

ALTER TABLE gb_ai_message
    DROP COLUMN gb_ai_message_type;
