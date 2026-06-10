-- gb_dep_food_sales 菜品消费类型与价格快照（父表 SSOT）
-- 历史数据：type=1，保留 gb_dfs_subtotal，actual/original 由迁移脚本回填

ALTER TABLE `gb_dep_food_sales`
    ADD COLUMN `gb_dfs_type` tinyint NOT NULL DEFAULT 1 COMMENT '消费类型：1正常 2折扣 3会员 4赠送 5员工餐' AFTER `gb_dfs_distributer_id`,
    ADD COLUMN `gb_dfs_original_unit_price` decimal(12,4) DEFAULT NULL COMMENT '标价快照（元/份）' AFTER `gb_dfs_type`,
    ADD COLUMN `gb_dfs_actual_unit_price` decimal(12,4) DEFAULT NULL COMMENT '实际成交价（元/份）' AFTER `gb_dfs_original_unit_price`,
    ADD COLUMN `gb_dfs_discount_rate` decimal(8,4) DEFAULT NULL COMMENT '折扣率（展示用，可选）' AFTER `gb_dfs_actual_unit_price`;

-- 同日同菜多类型：唯一约束含 type（执行前请确认无重复 dep+food+date 多行，否则先清洗）
ALTER TABLE `gb_dep_food_sales`
    ADD UNIQUE KEY `uk_dep_food_date_type` (`gb_dfs_dep_id`, `gb_dfs_food_id`, `gb_dfs_full_date`, `gb_dfs_type`);

-- 历史迁移：type=1；subtotal 不动；actual 由 subtotal/amount 反推；original 由当时主档价回填（快照局限）
UPDATE `gb_dep_food_sales` s
LEFT JOIN `gb_dep_food` df ON df.gb_df_dep_id = s.gb_dfs_dep_id AND df.gb_df_food_id = s.gb_dfs_food_id
SET
    s.gb_dfs_type = 1,
    s.gb_dfs_actual_unit_price = CASE
        WHEN s.gb_dfs_amount IS NOT NULL AND s.gb_dfs_amount <> '' AND CAST(s.gb_dfs_amount AS DECIMAL(18,6)) > 0
            THEN ROUND(CAST(IFNULL(s.gb_dfs_subtotal, '0') AS DECIMAL(18,6)) / CAST(s.gb_dfs_amount AS DECIMAL(18,6)), 4)
        ELSE 0
    END,
    s.gb_dfs_original_unit_price = CASE
        WHEN df.gb_df_food_price IS NOT NULL AND df.gb_df_food_price <> ''
            THEN CAST(df.gb_df_food_price AS DECIMAL(12,4))
        WHEN s.gb_dfs_amount IS NOT NULL AND s.gb_dfs_amount <> '' AND CAST(s.gb_dfs_amount AS DECIMAL(18,6)) > 0
            THEN ROUND(CAST(IFNULL(s.gb_dfs_subtotal, '0') AS DECIMAL(18,6)) / CAST(s.gb_dfs_amount AS DECIMAL(18,6)), 4)
        ELSE 0
    END
WHERE s.gb_dfs_type IS NULL OR s.gb_dfs_type = 0 OR s.gb_dfs_original_unit_price IS NULL;
