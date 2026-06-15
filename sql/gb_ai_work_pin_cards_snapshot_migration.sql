-- gb_ai_work_pin 增加卡片快照字段
-- 使图钉不依赖原消息长期存在，可独立还原业务卡片
ALTER TABLE gb_ai_work_pin
    ADD COLUMN gb_ai_wp_cards_snapshot_json MEDIUMTEXT DEFAULT NULL COMMENT '创建时从 gb_ai_message_cards_json 复制的完整 cards[] JSON 快照',
    ADD COLUMN gb_ai_wp_primary_card_type VARCHAR(128) DEFAULT NULL COMMENT '首张业务卡的 cardType',
    ADD COLUMN gb_ai_wp_card_count INT NOT NULL DEFAULT 0 COMMENT '业务卡片数量';
