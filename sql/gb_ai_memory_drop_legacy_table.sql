-- 旧单 Agent：长期记忆表（gb_ai_memory / gb_ai_memory_type），已由多智能体会话内
-- AiConversationTurnMemory + AiFollowUpIntentSnapshot 替代；Harness 主链无任何读写。
-- 执行前请备份。
DROP TABLE IF EXISTS `gb_ai_memory`;
