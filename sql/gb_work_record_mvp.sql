-- 店长工作记录 MVP（第一阶段）
-- 与 gb_ai_conversation / gb_ai_message / gb_ai_agent_run 配合使用，不替代消息表。
-- 可重复执行：CREATE TABLE IF NOT EXISTS；种子数据用 INSERT IGNORE。

CREATE TABLE IF NOT EXISTS gb_work_record_category (
  gb_wrc_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '分类主键',
  gb_wrc_code VARCHAR(64) NOT NULL COMMENT '稳定编码，如 EMPLOYEE_MANAGEMENT',
  gb_wrc_name VARCHAR(128) NOT NULL COMMENT '展示名称',
  gb_wrc_description VARCHAR(512) DEFAULT NULL COMMENT '分类说明',
  gb_wrc_sort_order INT NOT NULL DEFAULT 0 COMMENT '排序，越小越靠前',
  gb_wrc_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / DISABLED',
  gb_wrc_source VARCHAR(16) NOT NULL DEFAULT 'SYSTEM' COMMENT 'SYSTEM / MANUAL / AI_SUGGESTED',
  gb_wrc_distributer_id BIGINT NOT NULL DEFAULT 0 COMMENT '0=全集团共享系统默认',
  gb_wrc_created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  gb_wrc_updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (gb_wrc_id),
  UNIQUE KEY uk_gb_wrc_code_dis (gb_wrc_code, gb_wrc_distributer_id),
  KEY idx_gb_wrc_dis_status_sort (gb_wrc_distributer_id, gb_wrc_status, gb_wrc_sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='店长工作记录分类';

CREATE TABLE IF NOT EXISTS gb_work_record (
  gb_wr_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '工作记录主键',
  gb_wr_conversation_id BIGINT NOT NULL COMMENT 'gb_ai_conversation',
  gb_wr_source_message_id BIGINT DEFAULT NULL COMMENT '用户消息 gb_ai_message_id',
  gb_wr_source_run_id BIGINT DEFAULT NULL COMMENT '本轮 gb_ai_agent_run.id',
  gb_wr_distributer_id BIGINT NOT NULL COMMENT '集团/分销商',
  gb_wr_department_id BIGINT NOT NULL COMMENT '门店锚点部门（父部门）',
  gb_wr_recorder_user_id BIGINT NOT NULL COMMENT '记录人 gb_department_user',
  gb_wr_input_type VARCHAR(32) NOT NULL COMMENT 'TEXT / VOICE_TRANSCRIPT',
  gb_wr_raw_content TEXT NOT NULL COMMENT '用户原始输入',
  gb_wr_polished_content TEXT DEFAULT NULL COMMENT 'AI 整理书面内容',
  gb_wr_category_id BIGINT DEFAULT NULL COMMENT '正式分类 ID',
  gb_wr_category_code VARCHAR(64) DEFAULT NULL COMMENT '分类编码快照',
  gb_wr_category_name_snapshot VARCHAR(128) DEFAULT NULL COMMENT '分类名称快照',
  gb_wr_category_decision VARCHAR(16) DEFAULT NULL COMMENT 'EXISTING / SUGGEST_NEW / OTHER',
  gb_wr_suggested_category_name VARCHAR(128) DEFAULT NULL COMMENT 'AI 建议新分类名',
  gb_wr_ai_status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/SUCCESS/FAILED',
  gb_wr_ai_confidence DECIMAL(5,4) DEFAULT NULL COMMENT '0-1',
  gb_wr_ai_reason VARCHAR(512) DEFAULT NULL COMMENT '分类简短理由',
  gb_wr_ai_error_code VARCHAR(64) DEFAULT NULL COMMENT '失败错误码',
  gb_wr_recorded_at DATETIME NOT NULL COMMENT '事项记录时间（归档用）',
  gb_wr_created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  gb_wr_updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  gb_wr_status TINYINT NOT NULL DEFAULT 0 COMMENT '0 正常 1 软删',
  PRIMARY KEY (gb_wr_id),
  KEY idx_gb_wr_dept_recorded (gb_wr_department_id, gb_wr_recorded_at DESC),
  KEY idx_gb_wr_dis_recorded (gb_wr_distributer_id, gb_wr_recorded_at DESC),
  KEY idx_gb_wr_conv (gb_wr_conversation_id),
  KEY idx_gb_wr_user_recorded (gb_wr_recorder_user_id, gb_wr_recorded_at DESC),
  KEY idx_gb_wr_category (gb_wr_category_id),
  KEY idx_gb_wr_ai_status (gb_wr_ai_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='店长工作记录';

-- 系统默认一级分类（distributer_id=0 全集团共享）
INSERT IGNORE INTO gb_work_record_category
  (gb_wrc_code, gb_wrc_name, gb_wrc_description, gb_wrc_sort_order, gb_wrc_status, gb_wrc_source, gb_wrc_distributer_id)
VALUES
  ('EMPLOYEE_MANAGEMENT', '员工管理', '排班、考勤、培训、纪律等', 10, 'ACTIVE', 'SYSTEM', 0),
  ('SUPPLIER_PURCHASE', '供应商与采购', '供应商、采购、到货、价格等', 20, 'ACTIVE', 'SYSTEM', 0),
  ('FOOD_QUALITY', '食品质量', '食材品质、验收、保质期等', 30, 'ACTIVE', 'SYSTEM', 0),
  ('HYGIENE_SAFETY', '卫生与安全', '清洁、消毒、消防、食安检查等', 40, 'ACTIVE', 'SYSTEM', 0),
  ('DISH_SALES', '菜品与销售', '菜品、销量、菜单、促销等', 50, 'ACTIVE', 'SYSTEM', 0),
  ('CUSTOMER_SERVICE', '顾客服务', '客诉、服务、会员、体验等', 60, 'ACTIVE', 'SYSTEM', 0),
  ('EQUIPMENT_FACILITY', '设备与设施', '设备维修、水电、装修等', 70, 'ACTIVE', 'SYSTEM', 0),
  ('INVENTORY_WASTE', '库存与损耗', '盘点、报损、临期、库存异常等', 80, 'ACTIVE', 'SYSTEM', 0),
  ('BUSINESS_MANAGEMENT', '经营管理', '成本、营收、目标、会议决策等', 90, 'ACTIVE', 'SYSTEM', 0),
  ('OTHER', '其他', '无法归入以上分类的事项', 999, 'ACTIVE', 'SYSTEM', 0);
