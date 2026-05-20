-- 旧「会话结束抽取记忆」流程标记列；GbAiMemoryService 已移除，已无写入逻辑。
-- 执行前请备份。
ALTER TABLE `gb_ai_message` DROP COLUMN `gb_ai_message_memory_extracted`;
