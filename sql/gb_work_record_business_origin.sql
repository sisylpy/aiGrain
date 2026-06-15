-- 业务卡片 → 店长工作记录 来源字段（P1）
-- 可重复执行：列已存在则跳过（MySQL 8 需手工判重；开发环境可直接执行一次）

ALTER TABLE gb_work_record
  ADD COLUMN gb_wr_origin_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL / BUSINESS_CARD' AFTER gb_wr_input_type,
  ADD COLUMN gb_wr_biz_conversation_id BIGINT DEFAULT NULL COMMENT '业务来源会话' AFTER gb_wr_origin_type,
  ADD COLUMN gb_wr_biz_message_id BIGINT DEFAULT NULL COMMENT '业务来源 assistant 消息' AFTER gb_wr_biz_conversation_id,
  ADD COLUMN gb_wr_biz_run_id BIGINT DEFAULT NULL COMMENT '业务来源 Run' AFTER gb_wr_biz_message_id,
  ADD COLUMN gb_wr_biz_answer_plan_type VARCHAR(128) DEFAULT NULL COMMENT '业务 AnswerPlan 类型（辅助）' AFTER gb_wr_biz_run_id,
  ADD COLUMN gb_wr_biz_card_type VARCHAR(128) DEFAULT NULL COMMENT '业务卡片 cardType' AFTER gb_wr_biz_answer_plan_type,
  ADD COLUMN gb_wr_biz_item_key VARCHAR(128) DEFAULT NULL COMMENT '业务行键，如 disGoodsId:123 或 __CARD__' AFTER gb_wr_biz_card_type,
  ADD COLUMN gb_wr_biz_fact_snapshot MEDIUMTEXT DEFAULT NULL COMMENT '选中卡片/行 JSON 快照' AFTER gb_wr_biz_item_key;

ALTER TABLE gb_work_record
  ADD UNIQUE KEY uk_gb_wr_biz_origin (gb_wr_recorder_user_id, gb_wr_biz_message_id, gb_wr_biz_card_type, gb_wr_biz_item_key);
