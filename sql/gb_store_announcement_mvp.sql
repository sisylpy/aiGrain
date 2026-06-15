-- 店内公告栏 MVP（第一阶段）
-- 统一主表 gb_store_announcement；发布时写入快照，展示不依赖来源对象仍存在。
-- 可重复执行：CREATE TABLE IF NOT EXISTS。

CREATE TABLE IF NOT EXISTS gb_store_announcement (
  gb_sa_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '公告主键',
  gb_sa_distributer_id BIGINT NOT NULL COMMENT '集团/分销商',
  gb_sa_department_id BIGINT NOT NULL COMMENT '门店锚点部门（父部门）',
  gb_sa_publisher_user_id BIGINT NOT NULL COMMENT '发布人 gb_department_user',
  gb_sa_announcement_type VARCHAR(32) NOT NULL COMMENT 'TEXT / BUSINESS_CARD / AI_MESSAGE',
  gb_sa_source_type VARCHAR(32) NOT NULL COMMENT 'WORK_RECORD / WORK_PIN / WORK_NOTE / DIRECT',
  gb_sa_source_id BIGINT DEFAULT NULL COMMENT '来源主键；DIRECT 可为空',
  gb_sa_title VARCHAR(512) DEFAULT NULL COMMENT '列表标题',
  gb_sa_text_content MEDIUMTEXT DEFAULT NULL COMMENT '文字正文或 AI 回答正文',
  gb_sa_card_type VARCHAR(128) DEFAULT NULL COMMENT 'BUSINESS_CARD 时 cardType',
  gb_sa_card_snapshot_json MEDIUMTEXT DEFAULT NULL COMMENT 'BUSINESS_CARD 单卡完整 JSON 快照',
  gb_sa_cards_snapshot_json MEDIUMTEXT DEFAULT NULL COMMENT 'AI_MESSAGE 时 cards[] 完整 JSON 快照',
  gb_sa_source_conversation_id BIGINT DEFAULT NULL COMMENT '来源会话（追溯用）',
  gb_sa_source_message_id BIGINT DEFAULT NULL COMMENT '来源消息（追溯用）',
  gb_sa_source_run_id BIGINT DEFAULT NULL COMMENT '来源 Run（追溯用）',
  gb_sa_source_item_key VARCHAR(128) DEFAULT NULL COMMENT 'BUSINESS_CARD 行级来源 itemKey，如 goodsId:101',
  gb_sa_publish_status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED' COMMENT 'PUBLISHED / DELETED',
  gb_sa_published_at DATETIME NOT NULL COMMENT '发布时间',
  gb_sa_created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  gb_sa_updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (gb_sa_id),
  KEY idx_gb_sa_dept_status_published (gb_sa_department_id, gb_sa_publish_status, gb_sa_published_at DESC),
  KEY idx_gb_sa_dis_status_published (gb_sa_distributer_id, gb_sa_publish_status, gb_sa_published_at DESC),
  KEY idx_gb_sa_source (gb_sa_source_type, gb_sa_source_id),
  KEY idx_gb_sa_publisher (gb_sa_publisher_user_id, gb_sa_published_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='店内公告栏';

-- 若已执行过无 gb_sa_source_item_key 的版本，可单独执行：
-- ALTER TABLE gb_store_announcement
--   ADD COLUMN gb_sa_source_item_key VARCHAR(128) DEFAULT NULL
--     COMMENT 'BUSINESS_CARD 行级来源 itemKey，如 goodsId:101'
--   AFTER gb_sa_source_run_id;
