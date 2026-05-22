-- AI Run / Step Trace（见 docs/AI_AGENT_DEVELOPMENT_GUIDE.md、docs/API_INTEGRATION.md）
-- 执行前请确认库名与字符集；id 可与应用内 runId 一致（INPUT 写入）。

CREATE TABLE IF NOT EXISTS gb_ai_agent_run (
  id BIGINT PRIMARY KEY COMMENT '与 AiRunSessionRegistry 分配的 runId 一致',
  conversation_id BIGINT NULL,
  user_id BIGINT NULL,
  department_id BIGINT NULL,
  distributer_id BIGINT NULL,
  workspace_mode VARCHAR(50) NULL,
  user_input TEXT NULL,
  normalized_input TEXT NULL,
  intent MEDIUMTEXT NULL COMMENT '可选：追问快照 JSON 等（varchar(100) 不够用）',
  status VARCHAR(50) NULL,
  start_time DATETIME NULL,
  end_time DATETIME NULL,
  total_duration_ms INT NULL,
  model_provider VARCHAR(50) NULL,
  model_name VARCHAR(100) NULL,
  created_at DATETIME NULL
) COMMENT='AI 多智能体单次运行';

CREATE TABLE IF NOT EXISTS gb_ai_agent_step (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  run_id BIGINT NOT NULL,
  step_order INT NOT NULL,
  step_type VARCHAR(50) NULL,
  step_name VARCHAR(100) NULL,
  input_json JSON NULL,
  output_json JSON NULL,
  status VARCHAR(50) NULL,
  duration_ms INT NULL,
  error_message TEXT NULL,
  created_at DATETIME NULL,
  KEY idx_run_id (run_id)
) COMMENT='AI Run 内步骤';
