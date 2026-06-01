-- ============================================================================
-- Workflow 推荐问句：一个 Workflow 下多条自然语言入口（Suggested Question）
-- 依赖：gb_ai_advisor_workflow_mvp.sql（gb_ai_workflow 已存在）
-- question_code 全局唯一，须带业务域前缀（MenuOperation 用 mo_；ADV_BOSS 用 bo_）
-- 注意：本表无 gb_ai_wsq_advisor_id；多顾问可绑定同一 workflow，问句归属靠 question_code 前缀 +
--       AdvisorCapability 查询时的顾问级前缀过滤（见 AdvisorSuggestedQuestionScopeSupport）。
-- 种子：sql/gb_ai_workflow_suggested_question_seed_menu_operation.sql
--       sql/gb_ai_workflow_suggested_question_seed_adv_boss.sql
-- ============================================================================

CREATE TABLE IF NOT EXISTS gb_ai_workflow_suggested_question (
    gb_ai_wsq_id                BIGINT       NOT NULL AUTO_INCREMENT,
    gb_ai_wsq_workflow_id       BIGINT       NOT NULL,
    gb_ai_wsq_workflow_code     VARCHAR(64)  NOT NULL COMMENT '冗余稳定键',
    gb_ai_wsq_topic_id          VARCHAR(64)  NOT NULL,
    gb_ai_wsq_topic_title       VARCHAR(128) NOT NULL,
    gb_ai_wsq_topic_description VARCHAR(512) DEFAULT NULL,
    gb_ai_wsq_topic_sort        INT          NOT NULL DEFAULT 0,
    gb_ai_wsq_question_code     VARCHAR(64)  NOT NULL COMMENT '全局唯一，域前缀如 mo_',
    gb_ai_wsq_question_text     VARCHAR(512) NOT NULL COMMENT '填入输入框的自然语言',
    gb_ai_wsq_enabled           TINYINT      NOT NULL DEFAULT 1 COMMENT '1 参与查询 0 不参与',
    gb_ai_wsq_status            VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE'
        COMMENT 'ACTIVE|COMING_SOON|DISABLED',
    gb_ai_wsq_scene             VARCHAR(16)  NOT NULL DEFAULT 'BOTH'
        COMMENT 'MINIAPP|DESKTOP|BOTH',
    gb_ai_wsq_sort              INT          NOT NULL DEFAULT 0,
    gb_ai_wsq_intent_hint       VARCHAR(128) DEFAULT NULL,
    gb_ai_wsq_contract_hint     VARCHAR(128) DEFAULT NULL,
    gb_ai_wsq_create_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gb_ai_wsq_update_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (gb_ai_wsq_id),
    UNIQUE KEY uk_gb_ai_wsq_question_code (gb_ai_wsq_question_code),
    KEY idx_gb_ai_wsq_workflow (gb_ai_wsq_workflow_id),
    KEY idx_gb_ai_wsq_workflow_code (gb_ai_wsq_workflow_code),
    KEY idx_gb_ai_wsq_topic (gb_ai_wsq_topic_id, gb_ai_wsq_topic_sort, gb_ai_wsq_sort),
    KEY idx_gb_ai_wsq_scene_status (gb_ai_wsq_scene, gb_ai_wsq_status, gb_ai_wsq_enabled),
    CONSTRAINT fk_gb_ai_wsq_workflow
        FOREIGN KEY (gb_ai_wsq_workflow_id)
        REFERENCES gb_ai_workflow (gb_ai_workflow_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='Workflow 推荐问句（老板常问/试问问题）';
