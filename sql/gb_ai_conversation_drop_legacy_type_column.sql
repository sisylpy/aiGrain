-- ============================================================================
-- 移除单智能体时期 gb_ai_conversation.gb_ai_conversation_type（0/1/2 主题分类）。
--
-- Java 实体与 Mapper 已删除该列；MyBatis-Plus 不再读写。
-- 已在库的实例：执行本脚本前请在 information_schema.columns 确认列仍存在（无 IF EXISTS，重复执行会报错）。
--
-- ⚠️ 仅在已确认无遗留依赖后手工执行一次；重复 DROP 会报错。
-- ============================================================================

ALTER TABLE gb_ai_conversation
    DROP COLUMN gb_ai_conversation_type;
