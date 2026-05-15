-- 存量库执行一次：与 GbAiConversationTurnMemoryEntity.gbAiCtmMentionedDishName 对齐。
-- 若列已存在会报错，可忽略或先检查 information_schema。
ALTER TABLE gb_ai_conversation_turn_memory
    ADD COLUMN gb_ai_ctm_mentioned_dish_name VARCHAR(256) DEFAULT NULL
        COMMENT '菜品毛利单菜追问：点名菜名，多轮继承'
    AFTER gb_ai_ctm_mentioned_store;
