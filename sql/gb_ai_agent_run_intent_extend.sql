-- 已有库升级：gb_ai_agent_run.intent 扩展到可存多轮追问快照 JSON
ALTER TABLE gb_ai_agent_run MODIFY COLUMN intent MEDIUMTEXT NULL COMMENT '可选：追问快照等';
