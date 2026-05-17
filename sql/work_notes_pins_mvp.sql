-- 工作笔记 / 图钉 MVP（V1）
-- 执行前请确认库名与字符集；与 gb_ai_* 命名一致。
-- 不改 gb_ai_conversation / gb_ai_agent_run。

CREATE TABLE IF NOT EXISTS gb_ai_work_pin (
  gb_ai_wp_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  gb_ai_wp_user_id BIGINT NOT NULL COMMENT '归属 gb_department_user',
  gb_ai_wp_conversation_id BIGINT NOT NULL COMMENT 'gb_ai_conversation.gb_ai_conversation_id',
  gb_ai_wp_run_id BIGINT DEFAULT NULL,
  gb_ai_wp_message_id BIGINT DEFAULT NULL,
  gb_ai_wp_title VARCHAR(512) DEFAULT NULL,
  gb_ai_wp_source_type VARCHAR(32) NOT NULL COMMENT 'RUN / MESSAGE / SELECTION',
  gb_ai_wp_source_text_snapshot TEXT NOT NULL COMMENT '完整快照，详情与防丢失',
  gb_ai_wp_source_answer_preview VARCHAR(500) DEFAULT NULL COMMENT '列表预览',
  gb_ai_wp_source_role VARCHAR(32) DEFAULT NULL,
  gb_ai_wp_source_agent_name VARCHAR(128) DEFAULT NULL,
  gb_ai_wp_source_created_at DATETIME DEFAULT NULL,
  gb_ai_wp_created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  gb_ai_wp_updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  gb_ai_wp_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '0 正常 1 软删',
  PRIMARY KEY (gb_ai_wp_id),
  KEY idx_gb_ai_wp_conv_user_del_created (gb_ai_wp_user_id, gb_ai_wp_conversation_id, gb_ai_wp_deleted, gb_ai_wp_created_at DESC),
  KEY idx_gb_ai_wp_run (gb_ai_wp_run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 会话图钉';

CREATE TABLE IF NOT EXISTS gb_ai_work_note (
  gb_ai_wn_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  gb_ai_wn_user_id BIGINT NOT NULL COMMENT '归属 gb_department_user',
  gb_ai_wn_conversation_id BIGINT DEFAULT NULL COMMENT '手动笔记可为空',
  gb_ai_wn_title VARCHAR(512) NOT NULL,
  gb_ai_wn_content_md TEXT NOT NULL COMMENT 'Markdown，可为空串',
  gb_ai_wn_note_type VARCHAR(32) NOT NULL COMMENT 'MANUAL / FROM_RUN / FROM_PIN / FROM_SELECTION',
  gb_ai_wn_primary_source_type VARCHAR(32) DEFAULT NULL COMMENT '与 promote/from 对齐',
  gb_ai_wn_primary_conversation_id BIGINT DEFAULT NULL,
  gb_ai_wn_primary_run_id BIGINT DEFAULT NULL,
  gb_ai_wn_primary_message_id BIGINT DEFAULT NULL,
  gb_ai_wn_source_text_snapshot TEXT DEFAULT NULL COMMENT '有来源创建时必填',
  gb_ai_wn_source_answer_preview VARCHAR(500) DEFAULT NULL,
  gb_ai_wn_created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  gb_ai_wn_updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  gb_ai_wn_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '0 正常 1 软删',
  PRIMARY KEY (gb_ai_wn_id),
  KEY idx_gb_ai_wn_conv_user_del_upd (gb_ai_wn_user_id, gb_ai_wn_conversation_id, gb_ai_wn_deleted, gb_ai_wn_updated_at DESC),
  KEY idx_gb_ai_wn_user_del (gb_ai_wn_user_id, gb_ai_wn_deleted),
  KEY idx_gb_ai_wn_primary_run (gb_ai_wn_primary_run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 工作笔记';
