-- ============================================================================
-- 升级脚本：旧版 gb_ai_tag / gb_ai_notebook（仅 uk_(user,name)）→ 含租户锚点的 UNIQUE
--
-- 【何时用】
--   仅在「库中已是旧结构」时执行一次。
--   若表由 gb_ai_conversation_history_extensions.sql 新建（已含锚点列），切勿执行本文件。
--
-- 【幂等】
--   非可重复执行：第二次执行会因「列已存在」或「索引不存在」失败；不会 DELETE/TRUNCATE/DROP TABLE。
--
-- 【锚点】ADD 列为 NOT NULL DEFAULT 0，唯一索引不含 NULL，避免 MySQL UNIQUE + NULL 语义问题。
-- ============================================================================

-- ========== gb_ai_tag（⚠ 一次性） ==========
ALTER TABLE gb_ai_tag
    ADD COLUMN gb_ai_tag_anchor_distributer_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户锚点：批发商 disId，无则 0' AFTER gb_ai_tag_user_id,
    ADD COLUMN gb_ai_tag_anchor_department_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户锚点：门店父部门 ID，无则 0' AFTER gb_ai_tag_anchor_distributer_id;

ALTER TABLE gb_ai_tag DROP INDEX uk_gb_ai_tag_user_name;

ALTER TABLE gb_ai_tag
    ADD UNIQUE KEY uk_gb_ai_tag_user_scope_name (gb_ai_tag_user_id, gb_ai_tag_anchor_distributer_id, gb_ai_tag_anchor_department_id, gb_ai_tag_name);

-- ========== gb_ai_notebook（⚠ 一次性） ==========
ALTER TABLE gb_ai_notebook
    ADD COLUMN gb_ai_nb_anchor_distributer_id BIGINT NOT NULL DEFAULT 0 AFTER gb_ai_nb_user_id,
    ADD COLUMN gb_ai_nb_anchor_department_id BIGINT NOT NULL DEFAULT 0 AFTER gb_ai_nb_anchor_distributer_id;

ALTER TABLE gb_ai_notebook DROP INDEX uk_gb_ai_nb_user_name;

ALTER TABLE gb_ai_notebook
    ADD UNIQUE KEY uk_gb_ai_nb_user_scope_name (gb_ai_nb_user_id, gb_ai_nb_anchor_distributer_id, gb_ai_nb_anchor_department_id, gb_ai_nb_name);
