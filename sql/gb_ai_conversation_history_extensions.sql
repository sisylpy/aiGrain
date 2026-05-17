-- ============================================================================
-- 会话历史：扩列 + 会话置顶 / 标签 / 笔记本（不改 Harness/SSE）
-- 执行前请备份。
--
-- 【锚点列】gb_ai_tag / gb_ai_notebook 的 anchor_* 必须为 NOT NULL DEFAULT 0，
--           禁止用 NULL 参与 UNIQUE（MySQL 对 UNIQUE 中 NULL 语义易导致多条「空」重复）。
--           本脚本 CREATE TABLE 段已按 NOT NULL DEFAULT 0 定义。
--
-- 【首次 vs 升级 —— 不可在同一路径上重复盲跑 ALTER】
--
-- 1) 目标库从未执行过本会话历史的 DDL：
--      → 整文件执行一次：sql/gb_ai_conversation_history_extensions.sql
--
-- 2) 仅在「很旧的版本」里已存在 gb_ai_tag / gb_ai_notebook，且仍为 uk_gb_ai_tag_user_name /
--    uk_gb_ai_nb_user_name（无锚点列）：
--      → 一次性执行：sql/gb_ai_conversation_history_extensions_tag_notebook_scope_upgrade.sql
--      （勿在同库重复执行该升级脚本：第二次会因 ADD COLUMN / DROP INDEX 已存在而失败。）
--
-- 3) 已在当前分支执行过本文件的 ALTER gb_ai_conversation / gb_ai_message：
--      → 不要再重复执行这两段 ALTER；否则会报 Duplicate column name。
--      → 若仅需补表：可单独手工执行下方「CREATE TABLE IF NOT EXISTS」各段（幂等，不误删数据）。
--
-- 【幂等与数据安全】
-- - CREATE TABLE IF NOT EXISTS：可重复执行，不会 DROP 表，不会删数据。
-- - ALTER TABLE ADD COLUMN：仅首次成功；重复执行报错，不会删数据。
-- - 本文件不包含 DROP TABLE / DELETE / TRUNCATE。
--
-- ============================================================================

-- ========== 扩展 gb_ai_conversation（⚠ 仅首次执行；重复执行会报错） ==========
ALTER TABLE gb_ai_conversation
    ADD COLUMN gb_ai_conversation_archived TINYINT NOT NULL DEFAULT 0 COMMENT '0 正常 1 归档' AFTER gb_ai_conversation_type,
    ADD COLUMN gb_ai_conversation_last_run_id BIGINT DEFAULT NULL COMMENT '最近 Run（追溯）' AFTER gb_ai_conversation_archived,
    ADD COLUMN gb_ai_conversation_last_message_id BIGINT DEFAULT NULL AFTER gb_ai_conversation_last_run_id,
    ADD COLUMN gb_ai_conversation_last_intent VARCHAR(128) DEFAULT NULL AFTER gb_ai_conversation_last_message_id,
    ADD COLUMN gb_ai_conversation_last_path VARCHAR(128) DEFAULT NULL AFTER gb_ai_conversation_last_intent;

-- ========== 扩展 gb_ai_message（⚠ 仅首次执行；重复执行会报错） ==========
ALTER TABLE gb_ai_message
    ADD COLUMN gb_ai_message_run_id BIGINT DEFAULT NULL COMMENT '多智能体 Run 锚点（后续 Run 落库任务写入）' AFTER gb_ai_message_memory_extracted,
    ADD COLUMN gb_ai_message_status VARCHAR(32) DEFAULT NULL COMMENT 'PENDING/RUNNING/COMPLETED/FAILED' AFTER gb_ai_message_run_id,
    ADD COLUMN gb_ai_message_update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER gb_ai_message_create_time;

-- ========== 会话置顶（左侧栏，与工作区 gb_ai_work_pin 区分） ==========
CREATE TABLE IF NOT EXISTS gb_ai_conversation_pin (
    gb_ai_cp_id BIGINT NOT NULL AUTO_INCREMENT,
    gb_ai_cp_user_id BIGINT NOT NULL COMMENT 'gb_department_user',
    gb_ai_cp_conversation_id BIGINT NOT NULL,
    gb_ai_cp_pinned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gb_ai_cp_created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (gb_ai_cp_id),
    UNIQUE KEY uk_gb_ai_cp_user_conv (gb_ai_cp_user_id, gb_ai_cp_conversation_id),
    KEY idx_gb_ai_cp_conv (gb_ai_cp_conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话列表置顶';

-- ========== 标签（名称唯一含租户锚点） ==========
CREATE TABLE IF NOT EXISTS gb_ai_tag (
    gb_ai_tag_id BIGINT NOT NULL AUTO_INCREMENT,
    gb_ai_tag_user_id BIGINT NOT NULL,
    gb_ai_tag_anchor_distributer_id BIGINT NOT NULL DEFAULT 0 COMMENT '来自会话 gb_ai_conversation_distributer_id，空会话字段存 0',
    gb_ai_tag_anchor_department_id BIGINT NOT NULL DEFAULT 0 COMMENT '来自会话 gb_ai_conversation_department_id，空会话字段存 0',
    gb_ai_tag_name VARCHAR(128) NOT NULL,
    gb_ai_tag_color VARCHAR(32) DEFAULT NULL,
    gb_ai_tag_created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (gb_ai_tag_id),
    UNIQUE KEY uk_gb_ai_tag_user_scope_name (gb_ai_tag_user_id, gb_ai_tag_anchor_distributer_id, gb_ai_tag_anchor_department_id, gb_ai_tag_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 会话标签定义';

CREATE TABLE IF NOT EXISTS gb_ai_conversation_tag (
    gb_ai_ct_id BIGINT NOT NULL AUTO_INCREMENT,
    gb_ai_ct_user_id BIGINT NOT NULL,
    gb_ai_ct_conversation_id BIGINT NOT NULL,
    gb_ai_ct_tag_id BIGINT NOT NULL,
    gb_ai_ct_created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (gb_ai_ct_id),
    UNIQUE KEY uk_gb_ai_ct_user_conv_tag (gb_ai_ct_user_id, gb_ai_ct_conversation_id, gb_ai_ct_tag_id),
    KEY idx_gb_ai_ct_conv (gb_ai_ct_conversation_id),
    KEY idx_gb_ai_ct_tag (gb_ai_ct_tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话-标签';

-- ========== 笔记本 ==========
CREATE TABLE IF NOT EXISTS gb_ai_notebook (
    gb_ai_nb_id BIGINT NOT NULL AUTO_INCREMENT,
    gb_ai_nb_user_id BIGINT NOT NULL,
    gb_ai_nb_anchor_distributer_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户锚点：批发商 disId；无则 0，勿用 NULL',
    gb_ai_nb_anchor_department_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户锚点：门店父部门 ID；无则 0，勿用 NULL',
    gb_ai_nb_name VARCHAR(256) NOT NULL,
    gb_ai_nb_description TEXT,
    gb_ai_nb_created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gb_ai_nb_updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (gb_ai_nb_id),
    UNIQUE KEY uk_gb_ai_nb_user_scope_name (gb_ai_nb_user_id, gb_ai_nb_anchor_distributer_id, gb_ai_nb_anchor_department_id, gb_ai_nb_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记本';

CREATE TABLE IF NOT EXISTS gb_ai_conversation_notebook (
    gb_ai_cnb_id BIGINT NOT NULL AUTO_INCREMENT,
    gb_ai_cnb_user_id BIGINT NOT NULL,
    gb_ai_cnb_conversation_id BIGINT NOT NULL,
    gb_ai_cnb_notebook_id BIGINT NOT NULL,
    gb_ai_cnb_created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (gb_ai_cnb_id),
    UNIQUE KEY uk_gb_ai_cnb_user_conv_nb (gb_ai_cnb_user_id, gb_ai_cnb_conversation_id, gb_ai_cnb_notebook_id),
    KEY idx_gb_ai_cnb_conv (gb_ai_cnb_conversation_id),
    KEY idx_gb_ai_cnb_nb (gb_ai_cnb_notebook_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话-笔记本';
