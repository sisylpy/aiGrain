-- 删除 gb_ai_daily_revenue 表的 department_id 列及相关约束/索引
-- 原先的 UNIQUE KEY 和 INDEX 都依赖 department_id，需要先删除再重建

ALTER TABLE `gb_ai_daily_revenue`
  DROP INDEX `uk_gb_ai_dr_dep_date`,
  DROP INDEX `idx_gb_ai_dr_department`,
  DROP COLUMN `gb_ai_daily_revenue_department_id`;

-- 用 parent_department_id + record_date 重建唯一约束（匹配当前查询模式）
ALTER TABLE `gb_ai_daily_revenue`
  ADD UNIQUE KEY `uk_gb_ai_dr_parent_date` (`gb_ai_daily_revenue_parent_department_id`, `gb_ai_daily_revenue_record_date`);
