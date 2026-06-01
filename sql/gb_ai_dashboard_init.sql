-- 智能经营看板 - 数据库初始化
-- 用户看板表
CREATE TABLE IF NOT EXISTS `gb_ai_dashboard` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `distributer_id` BIGINT NOT NULL COMMENT '配送商/租户ID',
    `dashboard_code` VARCHAR(64) NOT NULL DEFAULT 'SMART_BUSINESS_DASHBOARD' COMMENT '看板编码',
    `dashboard_name` VARCHAR(128) NOT NULL DEFAULT '智能经营看板' COMMENT '看板名称',
    `is_default` TINYINT NOT NULL DEFAULT 1 COMMENT '是否默认看板 1=是 0=否',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 1=正常 0=停用',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_dis_code` (`user_id`, `distributer_id`, `dashboard_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户看板表';

-- 用户看板模块表
CREATE TABLE IF NOT EXISTS `gb_ai_dashboard_module` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `dashboard_id` BIGINT NOT NULL COMMENT '看板ID',
    `module_key` VARCHAR(64) NOT NULL COMMENT '模块标识 business/dish/ingredient/custom_focus',
    `module_type` VARCHAR(64) NOT NULL COMMENT '模块类型 SYSTEM_BUSINESS/SYSTEM_DISH/SYSTEM_INGREDIENT/USER_CUSTOM_FOCUS',
    `module_title` VARCHAR(128) NOT NULL COMMENT '模块标题',
    `position` INT NOT NULL DEFAULT 0 COMMENT '排序位置',
    `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用 1=启用 0=禁用',
    `removable` TINYINT NOT NULL DEFAULT 0 COMMENT '是否可删除 1=可删 0=不可删',
    `configurable` TINYINT NOT NULL DEFAULT 1 COMMENT '是否可配置 1=可配 0=不可配',
    `config_json` TEXT COMMENT '模块配置 JSON',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dashboard_module` (`dashboard_id`, `module_key`),
    KEY `idx_dashboard_id` (`dashboard_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户看板模块表';

-- 用户关注卡片表
CREATE TABLE IF NOT EXISTS `gb_ai_dashboard_widget` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `dashboard_id` BIGINT NOT NULL COMMENT '看板ID',
    `module_id` BIGINT NOT NULL COMMENT '所属模块ID',
    `widget_type` VARCHAR(64) NOT NULL COMMENT '卡片类型 DISH_FOCUS/INGREDIENT_FOCUS',
    `title` VARCHAR(256) NOT NULL COMMENT '卡片标题',
    `position` INT NOT NULL DEFAULT 0 COMMENT '排序位置',
    `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用 1=启用 0=禁用',
    `config_json` TEXT COMMENT '卡片配置 JSON',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_dashboard_id` (`dashboard_id`),
    KEY `idx_module_id` (`module_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户关注卡片表';
