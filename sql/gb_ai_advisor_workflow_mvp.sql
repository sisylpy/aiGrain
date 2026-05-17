-- ============================================================================
-- 业务顾问 Advisor + 工作流 Workflow（第一阶段：元数据与 WorkflowRun 锚点）
-- 不接入 Harness / AnswerPlan / SSE；仅表结构与查询接口。
-- 执行前请备份。CREATE TABLE IF NOT EXISTS 可重复执行。
-- ============================================================================

CREATE TABLE IF NOT EXISTS gb_ai_advisor (
    gb_ai_advisor_id BIGINT NOT NULL AUTO_INCREMENT,
    gb_ai_advisor_code VARCHAR(64) NOT NULL COMMENT '稳定业务键',
    gb_ai_advisor_name VARCHAR(128) NOT NULL,
    gb_ai_advisor_subtitle VARCHAR(256) DEFAULT NULL,
    gb_ai_advisor_description TEXT DEFAULT NULL,
    gb_ai_advisor_avatar_url VARCHAR(512) DEFAULT NULL,
    gb_ai_advisor_sort_order INT NOT NULL DEFAULT 0,
    gb_ai_advisor_enabled TINYINT NOT NULL DEFAULT 1 COMMENT '1 启用 0 停用',
    gb_ai_advisor_distributer_id BIGINT DEFAULT NULL COMMENT '集团/批发商，空表示全局',
    gb_ai_advisor_department_id BIGINT DEFAULT NULL COMMENT '门店/部门，空表示不限',
    gb_ai_advisor_create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gb_ai_advisor_update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (gb_ai_advisor_id),
    UNIQUE KEY uk_gb_ai_advisor_code (gb_ai_advisor_code),
    KEY idx_gb_ai_advisor_dis_dept (gb_ai_advisor_distributer_id, gb_ai_advisor_department_id),
    KEY idx_gb_ai_advisor_enabled_sort (gb_ai_advisor_enabled, gb_ai_advisor_sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 业务顾问（用户入口）';

CREATE TABLE IF NOT EXISTS gb_ai_workflow (
    gb_ai_workflow_id BIGINT NOT NULL AUTO_INCREMENT,
    gb_ai_workflow_code VARCHAR(64) NOT NULL COMMENT '稳定业务键',
    gb_ai_workflow_name VARCHAR(128) NOT NULL,
    gb_ai_workflow_description TEXT DEFAULT NULL,
    gb_ai_workflow_category VARCHAR(64) DEFAULT NULL,
    gb_ai_workflow_intent_code VARCHAR(128) DEFAULT NULL COMMENT '预留：语义/Harness intent',
    gb_ai_workflow_path_code VARCHAR(128) DEFAULT NULL COMMENT '预留：Harness path',
    gb_ai_workflow_harness_entry_type VARCHAR(64) DEFAULT NULL COMMENT '预留：入口类型',
    gb_ai_workflow_harness_path_key VARCHAR(128) DEFAULT NULL COMMENT '预留：路径键',
    gb_ai_workflow_input_schema_json TEXT DEFAULT NULL COMMENT '预留：入参 JSON Schema',
    gb_ai_workflow_default_params_json TEXT DEFAULT NULL COMMENT '预留：默认参数 JSON',
    gb_ai_workflow_sort_order INT NOT NULL DEFAULT 0,
    gb_ai_workflow_enabled TINYINT NOT NULL DEFAULT 1 COMMENT '1 启用 0 停用',
    gb_ai_workflow_distributer_id BIGINT DEFAULT NULL,
    gb_ai_workflow_department_id BIGINT DEFAULT NULL,
    gb_ai_workflow_create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gb_ai_workflow_update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (gb_ai_workflow_id),
    UNIQUE KEY uk_gb_ai_workflow_code (gb_ai_workflow_code),
    KEY idx_gb_ai_workflow_enabled_sort (gb_ai_workflow_enabled, gb_ai_workflow_sort_order),
    KEY idx_gb_ai_workflow_dis_dept (gb_ai_workflow_distributer_id, gb_ai_workflow_department_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 工作流（任务定义，执行走 Harness）';

CREATE TABLE IF NOT EXISTS gb_ai_advisor_workflow (
    gb_ai_aw_id BIGINT NOT NULL AUTO_INCREMENT,
    gb_ai_aw_advisor_id BIGINT NOT NULL,
    gb_ai_aw_workflow_id BIGINT NOT NULL,
    gb_ai_aw_relation_type VARCHAR(32) NOT NULL DEFAULT 'BOUND' COMMENT '如 BOUND / RECOMMENDED',
    gb_ai_aw_sort_order INT NOT NULL DEFAULT 0,
    gb_ai_aw_pinned TINYINT NOT NULL DEFAULT 0 COMMENT '1 钉选展示',
    gb_ai_aw_is_default TINYINT NOT NULL DEFAULT 0 COMMENT '1 默认推荐',
    gb_ai_aw_create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gb_ai_aw_update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (gb_ai_aw_id),
    UNIQUE KEY uk_gb_ai_aw_advisor_workflow (gb_ai_aw_advisor_id, gb_ai_aw_workflow_id),
    KEY idx_gb_ai_aw_advisor (gb_ai_aw_advisor_id),
    KEY idx_gb_ai_aw_workflow (gb_ai_aw_workflow_id),
    CONSTRAINT fk_gb_ai_aw_advisor FOREIGN KEY (gb_ai_aw_advisor_id) REFERENCES gb_ai_advisor (gb_ai_advisor_id),
    CONSTRAINT fk_gb_ai_aw_workflow FOREIGN KEY (gb_ai_aw_workflow_id) REFERENCES gb_ai_workflow (gb_ai_workflow_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='顾问与工作流绑定';

CREATE TABLE IF NOT EXISTS gb_ai_workflow_run (
    gb_ai_wr_id BIGINT NOT NULL AUTO_INCREMENT,
    gb_ai_wr_workflow_id BIGINT NOT NULL,
    gb_ai_wr_advisor_id BIGINT DEFAULT NULL,
    gb_ai_wr_user_id BIGINT NOT NULL COMMENT 'gb_department_user',
    gb_ai_wr_conversation_id BIGINT DEFAULT NULL,
    gb_ai_wr_message_id BIGINT DEFAULT NULL,
    gb_ai_wr_run_id BIGINT DEFAULT NULL COMMENT 'Harness Run 锚点，后续写入',
    gb_ai_wr_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/COMPLETED/FAILED/CANCELLED',
    gb_ai_wr_input_params_json TEXT DEFAULT NULL,
    gb_ai_wr_resolved_context_json TEXT DEFAULT NULL COMMENT 'AiResolvedQueryContext 等快照',
    gb_ai_wr_answer_plan_json TEXT DEFAULT NULL COMMENT 'AnswerPlan 快照',
    gb_ai_wr_result_summary VARCHAR(512) DEFAULT NULL,
    gb_ai_wr_error_message TEXT DEFAULT NULL,
    gb_ai_wr_distributer_id BIGINT DEFAULT NULL,
    gb_ai_wr_department_id BIGINT DEFAULT NULL,
    gb_ai_wr_started_at DATETIME DEFAULT NULL,
    gb_ai_wr_finished_at DATETIME DEFAULT NULL,
    gb_ai_wr_create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gb_ai_wr_update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (gb_ai_wr_id),
    KEY idx_gb_ai_wr_workflow (gb_ai_wr_workflow_id),
    KEY idx_gb_ai_wr_user (gb_ai_wr_user_id),
    KEY idx_gb_ai_wr_advisor (gb_ai_wr_advisor_id),
    KEY idx_gb_ai_wr_conv (gb_ai_wr_conversation_id),
    KEY idx_gb_ai_wr_run (gb_ai_wr_run_id),
    KEY idx_gb_ai_wr_status (gb_ai_wr_status),
    CONSTRAINT fk_gb_ai_wr_workflow FOREIGN KEY (gb_ai_wr_workflow_id) REFERENCES gb_ai_workflow (gb_ai_workflow_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流运行记录（对接 Harness/回放）';
