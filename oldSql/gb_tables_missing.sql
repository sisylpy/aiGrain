-- =============================================
-- GB 项目缺少的表结构补充 - 从 nongxinle原表.sql 提取
-- 生成时间: 2026-04-11
-- =============================================

-- ----------------------------
-- 1. gb_dep_dis_goods_settle - 社区商品结算表
-- ----------------------------
DROP TABLE IF EXISTS `gb_dep_dis_goods_settle`;
CREATE TABLE `gb_dep_dis_goods_settle` (
  `gb_dep_dis_goods_settle_id` int NOT NULL AUTO_INCREMENT COMMENT '社区商品id',
  `gb_ddgs_dfg_goods_father_id` int DEFAULT NULL COMMENT '批发商父类商品id',
  `gb_ddgs_distributer_id` int DEFAULT NULL COMMENT '批发商id',
  `gb_ddgs_dis_goods_id` int DEFAULT NULL COMMENT '商品名称',
  `gb_ddgs_dis_goods_name` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '商品名称',
  `gb_ddgs_dis_goods_standardname` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '商品规格',
  `gb_ddgs_dis_goods_standard_weight` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_ddgs_dis_goods_type` tinyint DEFAULT NULL,
  `gb_ddgs_dis_goods_price` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '价格',
  `gb_ddgs_dis_goods_lowest_price` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_ddgs_dis_goods_highest_price` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_ddgs_dis_control_price` tinyint DEFAULT NULL COMMENT '是否控制价格',
  `gb_ddgs_dis_control_fresh` tinyint DEFAULT NULL COMMENT '是否控制鲜度',
  `gb_ddgs_dis_fresh_warn_hour` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '鲜度报警小时',
  `gb_ddgs_dis_fresh_waste_hour` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '废弃报警小时',
  `gb_ddgs_dis_goods_inventory_type` tinyint DEFAULT NULL COMMENT '盘库方式1 月，2周，3日',
  `gb_ddgs_dis_goods_inventory_dep_id` int DEFAULT NULL COMMENT '盘库方式1 月，2周，3日',
  `gb_ddgs_status` tinyint DEFAULT NULL COMMENT '商品状态',
  `gb_ddgs_settle_department_id` int DEFAULT NULL COMMENT '库房或中央厨房id',
  `gb_ddgs_settle_department_father_id` int DEFAULT NULL COMMENT '库房或中央厨房id',
  `gb_ddgs_settle_amount` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '废弃报警小时',
  `gb_ddgs_settle_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '废弃报警小时',
  `gb_ddgs_settle_id` int DEFAULT NULL COMMENT '库房或中央厨房id',
  `gb_ddgs_settle_month` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '废弃报警小时',
  `gb_ddgs_settle_year` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '废弃报警小时',
  `gb_ddgs_sales_amount` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '原料销售数量',
  `gb_ddgs_sales_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '废弃报警小时',
  PRIMARY KEY (`gb_dep_dis_goods_settle_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- 2. gb_dep_father_goods_settle - 批发商父类商品结算表
-- ----------------------------
DROP TABLE IF EXISTS `gb_dep_father_goods_settle`;
CREATE TABLE `gb_dep_father_goods_settle` (
  `gb_dep_father_goods_settle_statics_id` int NOT NULL AUTO_INCREMENT,
  `gb_dfgss_father_goods_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dfgss_fathers_father_id` int DEFAULT NULL,
  `gb_dfgss_father_goods_level` tinyint DEFAULT NULL,
  `gb_dfgss_department_father_id` int DEFAULT NULL,
  `gb_dfgss_distributer_id` int DEFAULT NULL,
  `gb_dfgss_out_stock_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dfgss_settle_id` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dfgss_out_stock_type` tinyint DEFAULT NULL COMMENT '1 cost, 2 loss, 3 waste, 4 return',
  `gb_dfgss_father_goods_id` int DEFAULT NULL,
  `gb_dfgss_settle_month` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dfgss_settle_year` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`gb_dep_father_goods_settle_statics_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- 3. gb_dep_food - 部门食品表
-- ----------------------------
DROP TABLE IF EXISTS `gb_dep_food`;
CREATE TABLE `gb_dep_food` (
  `gb_dep_food_id` int NOT NULL AUTO_INCREMENT COMMENT '供货商id',
  `gb_DF_dep_id` int DEFAULT NULL COMMENT '供货商名称',
  `gb_DF_food_id` int DEFAULT NULL COMMENT 'gbDisid',
  `gb_DF_dep_father_id` int DEFAULT NULL COMMENT '供货商名称',
  `gb_DF_status` tinyint DEFAULT NULL COMMENT 'gbDisid',
  `gb_DF_food_price` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`gb_dep_food_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- 4. gb_dep_food_goods_sales - 部门食品销售表
-- ----------------------------
DROP TABLE IF EXISTS `gb_dep_food_goods_sales`;
CREATE TABLE `gb_dep_food_goods_sales` (
  `gb_dep_food_goods_sales_id` int NOT NULL AUTO_INCREMENT COMMENT '供货商id',
  `gb_DFGS_dep_id` int DEFAULT NULL COMMENT '供货商名称',
  `gb_DFGS_dep_father_id` int DEFAULT NULL COMMENT '供货商名称',
  `gb_DFGS_food_sales_id` int DEFAULT NULL COMMENT 'gbDisid',
  `gb_DFGS_food_goods_id` int DEFAULT NULL COMMENT 'gbDisid',
  `gb_DFGS_goods_amount` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'gbDisid',
  `gb_DFGS_settle_id` int DEFAULT NULL,
  `gb_DFGS_month` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'gbDisid',
  `gb_DFGS_full_Date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'gbDisid',
  `gb_DFGS_dis_goods_id` int DEFAULT NULL COMMENT 'gbDisid',
  PRIMARY KEY (`gb_dep_food_goods_sales_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- 5. gb_dep_food_sales - 部门食品销售主表
-- ----------------------------
DROP TABLE IF EXISTS `gb_dep_food_sales`;
CREATE TABLE `gb_dep_food_sales` (
  `gb_dep_food_sales_id` int NOT NULL AUTO_INCREMENT COMMENT '供货商id',
  `gb_DFS_dep_id` int DEFAULT NULL COMMENT '供货商名称',
  `gb_DFS_food_id` int DEFAULT NULL COMMENT 'gbDisid',
  `gb_DFS_dep_father_id` int DEFAULT NULL COMMENT '供货商名称',
  `gb_DFS_amount` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'gbDisid',
  `gb_DFS_settle_id` int DEFAULT NULL,
  `gb_DFS_month` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'gbDisid',
  `gb_DFS_full_Date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'gbDisid',
  `gb_DFS_user_id` int DEFAULT NULL COMMENT 'gbDisid',
  `gb_DFS_year` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'gbDisid',
  `gb_DFS_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'gbDisid',
  PRIMARY KEY (`gb_dep_food_sales_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- 6. gb_dep_inventory_daily - 部门日库存表
-- ----------------------------
DROP TABLE IF EXISTS `gb_dep_inventory_daily`;
CREATE TABLE `gb_dep_inventory_daily` (
  `gb_inventory_daily_id` int NOT NULL AUTO_INCREMENT,
  `gb_id_department_father_id` int DEFAULT NULL,
  `gb_id_department_id` int DEFAULT NULL,
  `gb_id_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_id_total` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_id_distributer_id` int DEFAULT NULL,
  `gb_id_waste_total` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_id_week` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_id_year` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_id_status` tinyint DEFAULT NULL,
  `gb_id_loss_total` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_id_dep_settle_id` int DEFAULT NULL,
  `gb_id_return_total` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_id_produce_total` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`gb_inventory_daily_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- 7. gb_dep_inventory_goods_daily - 部门商品日库存明细表
-- ----------------------------
DROP TABLE IF EXISTS `gb_dep_inventory_goods_daily`;
CREATE TABLE `gb_dep_inventory_goods_daily` (
  `gb_inventory_goods_daily_id` int NOT NULL AUTO_INCREMENT,
  `gb_igd_department_father_id` int DEFAULT NULL,
  `gb_igd_department_id` int DEFAULT NULL,
  `gb_igd_distributer_id` int DEFAULT NULL,
  `gb_igd_dis_goods_id` int DEFAULT NULL,
  `gb_igd_dis_goods_father_id` int DEFAULT NULL,
  `gb_igd_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igd_status` tinyint DEFAULT NULL,
  `gb_igd_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igd_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igd_gb_dep_stock_id` int DEFAULT NULL,
  `gb_igd_full_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igd_waste_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igd_waste_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igd_loss_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igd_loss_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igd_dep_settle_id` int DEFAULT NULL,
  `gb_igd_return_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igd_return_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igd_dep_dis_goods_id` int DEFAULT NULL,
  `gb_igd_gb_dep_stock_record_id` int DEFAULT NULL,
  `gb_igd_produce_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igd_produce_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igd_profit_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`gb_inventory_goods_daily_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- 8. gb_dep_inventory_goods_daily_total - 部门商品日库存汇总表
-- ----------------------------
DROP TABLE IF EXISTS `gb_dep_inventory_goods_daily_total`;
CREATE TABLE `gb_dep_inventory_goods_daily_total` (
  `gb_inventory_goods_daily_total_id` int NOT NULL AUTO_INCREMENT,
  `gb_igdt_department_father_id` int DEFAULT NULL,
  `gb_igdt_department_id` int DEFAULT NULL,
  `gb_igdt_distributer_id` int DEFAULT NULL,
  `gb_igdt_dis_goods_id` int DEFAULT NULL,
  `gb_igdt_dis_goods_father_id` int DEFAULT NULL,
  `gb_igdt_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igdt_status` tinyint DEFAULT NULL,
  `gb_igdt_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igdt_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igdt_full_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igdt_waste_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igdt_waste_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igdt_loss_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igdt_loss_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igdt_return_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igdt_return_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igdt_dep_dis_goods_id` int DEFAULT NULL,
  `gb_igdt_produce_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igdt_produce_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igdt_profit_price` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '利润单价',
  `gb_igdt_profit_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '利润总计',
  `gb_igdt_profit_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '制作成本-损耗-废弃',
  PRIMARY KEY (`gb_inventory_goods_daily_total_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- 9. gb_dep_inventory_goods_month - 部门商品月库存明细表
-- ----------------------------
DROP TABLE IF EXISTS `gb_dep_inventory_goods_month`;
CREATE TABLE `gb_dep_inventory_goods_month` (
  `gb_inventory_goods_month_id` int NOT NULL AUTO_INCREMENT,
  `gb_igm_department_father_id` int DEFAULT NULL,
  `gb_igm_department_id` int DEFAULT NULL,
  `gb_igm_distributer_id` int DEFAULT NULL,
  `gb_igm_dis_goods_id` int DEFAULT NULL,
  `gb_igm_dis_goods_father_id` int DEFAULT NULL,
  `gb_igm_month` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igm_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igm_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igm_gb_dep_stock_id` int DEFAULT NULL,
  `gb_igm_year` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igm_full_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igm_waste_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igm_waste_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igm_status` tinyint DEFAULT NULL,
  `gb_igm_loss_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igm_loss_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igm_dep_settle_id` int DEFAULT NULL,
  `gb_igm_return_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igm_return_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igm_dep_dis_goods_id` int DEFAULT NULL,
  `gb_igm_gb_dep_stock_record_id` int DEFAULT NULL,
  `gb_igm_produce_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igm_produce_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`gb_inventory_goods_month_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- 10. gb_dep_inventory_goods_month_total - 部门商品月库存汇总表
-- ----------------------------
DROP TABLE IF EXISTS `gb_dep_inventory_goods_month_total`;
CREATE TABLE `gb_dep_inventory_goods_month_total` (
  `gb_inventory_goods_month_total_id` int NOT NULL AUTO_INCREMENT,
  `gb_igmt_department_father_id` int DEFAULT NULL,
  `gb_igmt_department_id` int DEFAULT NULL,
  `gb_igmt_distributer_id` int DEFAULT NULL,
  `gb_igmt_dis_goods_id` int DEFAULT NULL,
  `gb_igmt_dis_goods_father_id` int DEFAULT NULL,
  `gb_igmt_month` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igmt_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igmt_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igmt_year` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igmt_full_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igmt_waste_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igmt_waste_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igmt_status` tinyint DEFAULT NULL,
  `gb_igmt_loss_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igmt_loss_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igmt_return_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igmt_return_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igmt_dep_dis_goods_id` int DEFAULT NULL,
  `gb_igmt_produce_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igmt_produce_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igmt_profit_price` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igmt_profit_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igmt_profit_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`gb_inventory_goods_month_total_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- 11. gb_dep_inventory_goods_week - 部门商品周库存明细表
-- ----------------------------
DROP TABLE IF EXISTS `gb_dep_inventory_goods_week`;
CREATE TABLE `gb_dep_inventory_goods_week` (
  `gb_inventory_goods_week_id` int NOT NULL AUTO_INCREMENT,
  `gb_igw_department_father_id` int DEFAULT NULL,
  `gb_igw_department_id` int DEFAULT NULL,
  `gb_igw_distributer_id` int DEFAULT NULL,
  `gb_igw_dis_goods_id` int DEFAULT NULL,
  `gb_igw_dis_goods_father_id` int DEFAULT NULL,
  `gb_igw_week` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igw_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igw_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igw_gb_dep_stock_id` int DEFAULT NULL,
  `gb_igw_year` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igw_full_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igw_waste_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igw_waste_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igw_status` tinyint DEFAULT NULL,
  `gb_igw_loss_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igw_loss_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igw_dep_settle_id` int DEFAULT NULL,
  `gb_igw_return_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igw_return_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igw_dep_dis_goods_id` int DEFAULT NULL,
  `gb_igw_gb_dep_stock_record_id` int DEFAULT NULL,
  `gb_igw_produce_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igw_produce_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`gb_inventory_goods_week_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- 12. gb_dep_inventory_goods_week_total - 部门商品周库存汇总表
-- ----------------------------
DROP TABLE IF EXISTS `gb_dep_inventory_goods_week_total`;
CREATE TABLE `gb_dep_inventory_goods_week_total` (
  `gb_inventory_goods_week_total_id` int NOT NULL AUTO_INCREMENT,
  `gb_igwt_department_father_id` int DEFAULT NULL,
  `gb_igwt_department_id` int DEFAULT NULL,
  `gb_igwt_distributer_id` int DEFAULT NULL,
  `gb_igwt_dis_goods_id` int DEFAULT NULL,
  `gb_igwt_dis_goods_father_id` int DEFAULT NULL,
  `gb_igwt_week` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igwt_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igwt_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igwt_year` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igwt_full_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igwt_waste_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igwt_waste_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igwt_status` tinyint DEFAULT NULL,
  `gb_igwt_loss_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igwt_loss_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igwt_return_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igwt_return_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igwt_dep_dis_goods_id` int DEFAULT NULL,
  `gb_igwt_produce_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igwt_produce_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igwt_profit_price` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igwt_profit_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_igwt_profit_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`gb_inventory_goods_week_total_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- 13. gb_dep_inventory_month - 部门月库存汇总表
-- ----------------------------
DROP TABLE IF EXISTS `gb_dep_inventory_month`;
CREATE TABLE `gb_dep_inventory_month` (
  `gb_inventory_month_id` int NOT NULL AUTO_INCREMENT,
  `gb_im_department_father_id` int DEFAULT NULL,
  `gb_im_department_id` int DEFAULT NULL,
  `gb_im_month` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_im_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_im_distributer_id` int DEFAULT NULL,
  `gb_im_waste_total` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_im_year` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_im_status` tinyint DEFAULT NULL,
  `gb_im_loss_total` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_im_dep_settle_id` int DEFAULT NULL,
  `gb_im_return_total` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_im_produce_total` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`gb_inventory_month_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- 14. gb_dep_inventory_week - 部门周库存汇总表
-- ----------------------------
DROP TABLE IF EXISTS `gb_dep_inventory_week`;
CREATE TABLE `gb_dep_inventory_week` (
  `gb_inventory_week_id` int NOT NULL AUTO_INCREMENT,
  `gb_diw_department_father_id` int DEFAULT NULL,
  `gb_diw_department_id` int DEFAULT NULL,
  `gb_diw_week` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_diw_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_diw_distributer_id` int DEFAULT NULL,
  `gb_diw_waste_total` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_diw_year` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_diw_status` tinyint DEFAULT NULL,
  `gb_diw_loss_total` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_diw_dep_settle_id` int DEFAULT NULL,
  `gb_diw_return_total` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_diw_produce_total` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`gb_inventory_week_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- 15. gb_department_bill - 部门账单表
-- ----------------------------
DROP TABLE IF EXISTS `gb_department_bill`;
CREATE TABLE `gb_department_bill` (
  `gb_department_bill_id` int NOT NULL AUTO_INCREMENT,
  `gb_DB_dis_id` int DEFAULT NULL,
  `gb_DB_dep_id` int DEFAULT NULL,
  `gb_DB_total` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_DB_status` tinyint DEFAULT NULL,
  `gb_DB_time` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_DB_issue_user_id` int DEFAULT NULL,
  `gb_DB_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_DB_month` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_DB_week` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_DB_trade_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_DB_print_times` int DEFAULT NULL,
  `gb_DB_day` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '星期',
  `gb_DB_issue_order_type` tinyint DEFAULT NULL,
  `gb_DB_issue_dep_id` int DEFAULT NULL,
  `gb_DB_order_amount` int DEFAULT NULL,
  `gb_DB_confirm_goods_user_id` int DEFAULT NULL,
  `gb_DB_confirm_price_user_id` int DEFAULT NULL,
  `gb_DB_confirm_settle_user_id` int DEFAULT NULL,
  `gb_DB_confirm_goods_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_DB_confirm_price_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_DB_confirm_settle_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_DB_dep_settle_id` int DEFAULT NULL,
  `gb_DB_issue_nx_dis_id` int DEFAULT NULL,
  `gb_DB_selling_total` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_DB_gb_supplier_payment_id` int DEFAULT NULL,
  `gb_DB_wx_out_trade_no` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_DB_will_pay_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_DB_dep_father_id` int DEFAULT NULL,
  `gb_DB_year` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_DB_set_auto_goods` tinyint DEFAULT NULL,
  `gb_DB_pay_total` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_DB_user_coupon_id` int DEFAULT NULL,
  `gb_DB_user_coupon_total` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_DB_return_order_total` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_DB_return_order_id` int DEFAULT NULL,
  PRIMARY KEY (`gb_department_bill_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- 16. gb_department_goods_daily - 部门商品日统计表
-- ----------------------------
DROP TABLE IF EXISTS `gb_department_goods_daily`;
CREATE TABLE `gb_department_goods_daily` (
  `gb_department_goods_daily_id` int NOT NULL AUTO_INCREMENT,
  `gb_dgd_gb_distributer_id` int DEFAULT NULL,
  `gb_dgd_gb_department_id` int DEFAULT NULL,
  `gb_dgd_gb_department_father_id` int DEFAULT NULL,
  `gb_dgd_gb_dis_goods_id` int DEFAULT NULL,
  `gb_dgd_gb_dis_goods_father_id` int DEFAULT NULL,
  `gb_dgd_gb_dep_dis_goods_id` int DEFAULT NULL,
  `gb_dgd_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_rest_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_produce_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_loss_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_waste_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_return_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_subtotal` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_date` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_day` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_week` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_month` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_full_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_year` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_produce_subtotal` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_rest_subtotal` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_loss_subtotal` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_waste_subtotal` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_return_subtotal` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_profit_subtotal` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_sales_subtotal` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_after_profit_subtotal` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_sell_clear_hour` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_sell_clear_minute` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_last_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_last_subtotal` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_fresh_rate` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_task_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_last_produce_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgd_status` tinyint DEFAULT NULL,
  `gb_dgd_gb_dis_goods_grand_id` int DEFAULT NULL,
  `gb_dgd_gb_dis_goods_great_grand_id` int DEFAULT NULL,
  PRIMARY KEY (`gb_department_goods_daily_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- 17. gb_department_goods_stock_record - 部门商品库存记录表
-- ----------------------------
DROP TABLE IF EXISTS `gb_department_goods_stock_record`;
CREATE TABLE `gb_department_goods_stock_record` (
  `gb_department_goods_stock_record_id` int NOT NULL AUTO_INCREMENT,
  `gb_dgsc_gb_department_id` int DEFAULT NULL,
  `gb_dgsc_gb_department_father_id` int DEFAULT NULL,
  `gb_dgsc_gb_distributer_id` int DEFAULT NULL,
  `gb_dgsc_gb_dis_goods_id` int DEFAULT NULL,
  `gb_dgsc_gb_dep_dis_goods_id` int DEFAULT NULL,
  `gb_dgsc_gb_department_order_id` int DEFAULT NULL,
  `gb_dgsc_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgsc_price` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgsc_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgsc_rest_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgsc_rest_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgsc_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgsc_receive_user_id` int DEFAULT NULL,
  PRIMARY KEY (`gb_department_goods_stock_record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- 18. gb_department_goods_stock_reduce - 部门商品库存减少表
-- ----------------------------
DROP TABLE IF EXISTS `gb_department_goods_stock_reduce`;
CREATE TABLE `gb_department_goods_stock_reduce` (
  `gb_department_goods_stock_reduce_id` int NOT NULL AUTO_INCREMENT,
  `gb_dgsr_department_id` int DEFAULT NULL,
  `gb_dgsr_department_father_id` int DEFAULT NULL,
  `gb_dgsr_distributer_id` int DEFAULT NULL,
  `gb_dgsr_dis_goods_id` int DEFAULT NULL,
  `gb_dgsr_dep_dis_goods_id` int DEFAULT NULL,
  `gb_dgsr_goods_stock_id` int DEFAULT NULL,
  `gb_dgsr_type` tinyint DEFAULT NULL COMMENT '1 出库 2 损耗 3 废弃 4 退货',
  `gb_dgsr_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgsr_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgsr_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgsr_full_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgsr_user_id` int DEFAULT NULL,
  `gb_dgsr_dep_settle_id` int DEFAULT NULL,
  `gb_dgsr_week` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgsr_month` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgsr_year` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`gb_department_goods_stock_reduce_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- 19. gb_department_goods_stock_reduce_attachment - 部门商品库存减少附件表
-- ----------------------------
DROP TABLE IF EXISTS `gb_department_goods_stock_reduce_attachment`;
CREATE TABLE `gb_department_goods_stock_reduce_attachment` (
  `gb_department_goods_stock_reduce_attachment_id` int NOT NULL AUTO_INCREMENT,
  `gb_dgsra_reduce_id` int DEFAULT NULL,
  `gb_dgsra_file_path` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgsra_type` tinyint DEFAULT NULL,
  PRIMARY KEY (`gb_department_goods_stock_reduce_attachment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- 20. gb_department_goods_stock_reduce_daily - 部门商品库存减少日统计表
-- ----------------------------
DROP TABLE IF EXISTS `gb_department_goods_stock_reduce_daily`;
CREATE TABLE `gb_department_goods_stock_reduce_daily` (
  `gb_department_goods_stock_reduce_daily_id` int NOT NULL AUTO_INCREMENT,
  `gb_dgsrd_department_father_id` int DEFAULT NULL,
  `gb_dgsrd_department_id` int DEFAULT NULL,
  `gb_dgsrd_distributer_id` int DEFAULT NULL,
  `gb_dgsrd_type` tinyint DEFAULT NULL COMMENT '1 出库 2 损耗 3 废弃 4 退货',
  `gb_dgsrd_total` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgsrd_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgsrd_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgsrd_full_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgsrd_week` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgsrd_month` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgsrd_year` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgsrd_dep_settle_id` int DEFAULT NULL,
  PRIMARY KEY (`gb_department_goods_stock_reduce_daily_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- 21. gb_department_orders_history - 部门订单历史表
-- ----------------------------
DROP TABLE IF EXISTS `gb_department_orders_history`;
CREATE TABLE `gb_department_orders_history` (
  `gb_department_orders_history_id` int NOT NULL AUTO_INCREMENT,
  `gb_doh_department_id` int DEFAULT NULL,
  `gb_doh_department_father_id` int DEFAULT NULL,
  `gb_doh_distributer_id` int DEFAULT NULL,
  `gb_doh_orders_id` int DEFAULT NULL,
  `gb_doh_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`gb_department_orders_history_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- 22. gb_department_settle - 部门结算表
-- ----------------------------
DROP TABLE IF EXISTS `gb_department_settle`;
CREATE TABLE `gb_department_settle` (
  `gb_department_settle_id` int NOT NULL AUTO_INCREMENT,
  `gb_ds_department_father_id` int DEFAULT NULL,
  `gb_ds_department_id` int DEFAULT NULL,
  `gb_ds_distributer_id` int DEFAULT NULL,
  `gb_ds_orders_subtotal` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_ds_paid_subtotal` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_ds_rest_subtotal` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_ds_settle_month` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_ds_settle_year` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_ds_settle_status` tinyint DEFAULT NULL,
  `gb_ds_settle_full_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_ds_settle_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_ds_settle_week` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`gb_department_settle_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- 23. gb_dis_nx_dis - 批发商与配送商关联表
-- ----------------------------
DROP TABLE IF EXISTS `gb_dis_nx_dis`;
CREATE TABLE `gb_dis_nx_dis` (
  `gb_dis_nx_dis_id` int NOT NULL AUTO_INCREMENT,
  `gb_dnd_gb_dis_id` int DEFAULT NULL,
  `gb_dnd_nx_dis_id` int DEFAULT NULL,
  PRIMARY KEY (`gb_dis_nx_dis_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- 24. gb_distributer_food - 批发商食品表
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_food`;
CREATE TABLE `gb_distributer_food` (
  `gb_distributer_food_id` int NOT NULL AUTO_INCREMENT,
  `gb_df_dis_id` int DEFAULT NULL,
  `gb_df_nx_food_id` int DEFAULT NULL,
  `gb_df_food_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_df_food_price` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_df_status` tinyint DEFAULT NULL,
  PRIMARY KEY (`gb_distributer_food_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- 25. gb_distributer_food_goods - 批发商食品商品关联表
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_food_goods`;
CREATE TABLE `gb_distributer_food_goods` (
  `gb_distributer_food_goods_id` int NOT NULL AUTO_INCREMENT,
  `gb_dfg_dis_id` int DEFAULT NULL,
  `gb_dfg_food_id` int DEFAULT NULL,
  `gb_dfg_dis_goods_id` int DEFAULT NULL,
  PRIMARY KEY (`gb_distributer_food_goods_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- 26. gb_distributer_goods_price - 批发商商品价格表
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_goods_price`;
CREATE TABLE `gb_distributer_goods_price` (
  `gb_distributer_goods_price_id` int NOT NULL AUTO_INCREMENT COMMENT '价格商品id',
  `gb_dgp_dfg_goods_father_id` int DEFAULT NULL COMMENT 'dg父类商品id',
  `gb_dgp_distributer_goods_id` int DEFAULT NULL COMMENT 'dgGoodsId',
  `gb_dgp_distributer_id` tinyint DEFAULT NULL COMMENT 'dg',
  `gb_dgp_goods_price` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '价格',
  `gb_dgp_goods_lowest_price` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '最低价格',
  `gb_dgp_goods_highest_price` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '最高价格',
  `gb_dgp_pur_goods_id` int DEFAULT NULL COMMENT '采购商品id',
  `gb_dgp_pur_user_id` int DEFAULT NULL COMMENT '采购员',
  `gb_dgp_pur_date` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '采购日期',
  `gb_dgp_pur_price` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '采购价格',
  `gb_dgp_pur_what` tinyint DEFAULT NULL COMMENT '采购价高或低',
  `gb_dgp_pur_scale` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '高或低比例',
  `gb_dgp_pur_what_total` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '高或低差额',
  `gb_dgp_pur_department_id` int DEFAULT NULL COMMENT '采购部门id',
  `gb_dgp_status` tinyint DEFAULT NULL,
  `gb_dgp_week` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgp_month` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgp_year` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_dgp_dep_settle_id` int DEFAULT NULL,
  `gb_dgp_pur_total` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '高或低差额',
  `gb_dgp_goods_lowest_total` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '高或低差额',
  `gb_dgp_goods_highest_total` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '高或低差额',
  `gb_dgp_pur_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '采购shuliang',
  `gb_dgp_pur_nx_distributer_id` int DEFAULT NULL COMMENT '采购部门id',
  PRIMARY KEY (`gb_distributer_goods_price_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- 27. gb_distributer_goods_shelf - 批发商商品货架表
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_goods_shelf`;
CREATE TABLE `gb_distributer_goods_shelf` (
  `gb_distributer_goods_shelf_id` int NOT NULL AUTO_INCREMENT COMMENT '货架id',
  `gb_distributer_goods_shelf_name` varchar(20) DEFAULT NULL COMMENT '货架名称',
  `gb_distributer_goods_shelf_sort` int DEFAULT NULL COMMENT '货架排序',
  `gb_distributer_goods_shelf_dis_id` int DEFAULT NULL COMMENT '批发商id',
  `gb_distributer_goods_shelf_dep_id` int DEFAULT NULL COMMENT '批发商库房id',
  PRIMARY KEY (`gb_distributer_goods_shelf_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- 28. gb_distributer_goods_shelf_goods - 批发商货架商品关联表
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_goods_shelf_goods`;
CREATE TABLE `gb_distributer_goods_shelf_goods` (
  `gb_distributer_goods_shelf_goods_id` int NOT NULL AUTO_INCREMENT COMMENT '货架商品id',
  `gb_DGSG_dis_goods_id` int DEFAULT NULL COMMENT '批发商商品id',
  `gb_DGSG_shelf_id` int DEFAULT NULL COMMENT '货架id',
  `gb_DGSG_sort` int DEFAULT NULL COMMENT '货架商品排序',
  `gb_DGSG_dep_id` int DEFAULT NULL,
  `gb_DGSG_dep_father_id` int DEFAULT NULL,
  `gb_DGSG_dep_dis_goods_id` int DEFAULT NULL,
  PRIMARY KEY (`gb_distributer_goods_shelf_goods_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- 29. gb_distributer_pay_list - 批发商支付清单表
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_pay_list`;
CREATE TABLE `gb_distributer_pay_list` (
  `gb_distributer_pay_list_id` int NOT NULL AUTO_INCREMENT,
  `gb_ndpl_gb_dis_id` int DEFAULT NULL,
  `gb_ndpl_pay_subtotal` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_ndpl_pay_time` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_ndpl_type` tinyint DEFAULT NULL,
  `gb_ndpl_status` tinyint DEFAULT NULL,
  `gb_ndpl_pay_date` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_ndpl_gb_pb_id` int DEFAULT NULL,
  `gb_ndpl_pay_month` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_ndpl_pay_year` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_ndpl_rest_points` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_ndpl_nx_supplier_id` int DEFAULT NULL,
  `gb_ndpl_gb_department_father_id` int DEFAULT NULL,
  `gb_ndpl_gb_department_id` int DEFAULT NULL,
  `gb_ndpl_gb_dis_goods_id` int DEFAULT NULL,
  PRIMARY KEY (`gb_distributer_pay_list_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- 30. gb_distributer_supplier - 批发商供货商表
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_supplier`;
CREATE TABLE `gb_distributer_supplier` (
  `gb_distributer_supplier_id` int NOT NULL AUTO_INCREMENT COMMENT '供货商id',
  `gb_distributer_supplier_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '供货商名称',
  `gb_distributer_supplier_father_id` int DEFAULT NULL COMMENT '父级id',
  `gb_DS_gb_distributer_id` int DEFAULT NULL COMMENT 'gbDisid',
  `gb_DS_gb_department_id` int DEFAULT NULL COMMENT 'gbDepid',
  `gb_DS_suppplier_is_group` tinyint DEFAULT NULL COMMENT '总部供货商1，门店自采2，',
  `gb_DS_order_type` tinyint DEFAULT NULL COMMENT '订单类型',
  `gb_DS_supplier_user_id` int DEFAULT NULL COMMENT '接单元id',
  `gb_DS_pur_user_id` int DEFAULT NULL COMMENT '采购员id',
  PRIMARY KEY (`gb_distributer_supplier_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- 31. gb_distributer_supplier_payment - 批发商供货商支付表
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_supplier_payment`;
CREATE TABLE `gb_distributer_supplier_payment` (
  `gb_distributer_supplier_payment_id` int NOT NULL AUTO_INCREMENT COMMENT '供货商id',
  `gb_dsp_date` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '供货商名称',
  `gb_dsp_supplier_id` int DEFAULT NULL COMMENT '父级id',
  `gb_dsp_pay_user_id` int DEFAULT NULL COMMENT 'gbDisid',
  `gb_dsp_pay_total` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'gbDepid',
  `gb_dsp_distributer_id` int DEFAULT NULL COMMENT 'gbDisid',
  `gb_dsp_nx_distributer_id` int DEFAULT NULL COMMENT '配送商id',
  `gb_dsp_wx_out_trade_no` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '配送商id',
  `gb_dsp_status` int DEFAULT NULL COMMENT '配送商id',
  `gb_dsp_pay_full_time` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '供货商名称',
  PRIMARY KEY (`gb_distributer_supplier_payment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- 32. gb_distributer_supplier_user - 批发商供货商用户表
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_supplier_user`;
CREATE TABLE `gb_distributer_supplier_user` (
  `gb_distributer_supplier_user_id` int NOT NULL AUTO_INCREMENT COMMENT '供货商用户id',
  `gb_DSU_department_id` int DEFAULT NULL COMMENT '订货部门id',
  `gb_DSU_wx_avartra_url` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '订货部门用户微信头像',
  `gb_DSU_wx_nick_name` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '订货部门用户微信昵称',
  `gb_DSU_wx_open_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '订货部门用户微信openid',
  `gb_DSU_wx_phone` varchar(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '订货部门用户微信手机号码',
  `gb_DSU_admin` tinyint DEFAULT NULL COMMENT '0 指定供货商用户 1 转发微信用户',
  `gb_DSU_distributer_id` int DEFAULT NULL COMMENT '订货部门批发商id',
  `gb_DSU_url_change` tinyint DEFAULT NULL,
  `gb_DSU_department_father_id` int DEFAULT NULL,
  `gb_DSU_join_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_DSU_print_device_id` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_DSU_print_bill_device_id` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_DSU_supplier_id` int DEFAULT NULL COMMENT '订货部门id',
  PRIMARY KEY (`gb_distributer_supplier_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- 33. gb_distributer_weight_goods - 批发商称重商品表
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_weight_goods`;
CREATE TABLE `gb_distributer_weight_goods` (
  `gb_distributer_weight_goods_id` int NOT NULL AUTO_INCREMENT COMMENT '称重单id',
  `gb_dwg_weight_id` int DEFAULT NULL COMMENT '称重disid',
  `gb_dwg_dep_dis_goods_id` int DEFAULT NULL COMMENT '称重单总重量',
  `gb_dwg_prepare_weight` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '称重单总日期',
  `gb_dwg_dis_goods_id` int DEFAULT NULL COMMENT '称重单总重量',
  `gb_dwg_order_amount` int DEFAULT NULL COMMENT '订单数量',
  `gb_dwg_status` tinyint DEFAULT NULL COMMENT '状态',
  `gb_dwg_save_user_id` int DEFAULT NULL COMMENT '员工',
  `gb_dwg_dep_id` int DEFAULT NULL COMMENT '员工',
  `gb_dwg_dep_father_id` int DEFAULT NULL COMMENT '员工',
  `gb_dwg_order_finish_amount` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '订单数量',
  `gb_dwg_date` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '员工',
  `gb_dwg_loss_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '员工',
  `gb_dwg_waste_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '员工',
  `gb_dwg_return_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '员工',
  PRIMARY KEY (`gb_distributer_weight_goods_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- 34. gb_distributer_weight_total - 批发商称重汇总表
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_weight_total`;
CREATE TABLE `gb_distributer_weight_total` (
  `gb_distributer_weight_total_id` int NOT NULL AUTO_INCREMENT COMMENT '称重单id',
  `gb_gwt_user_id` int DEFAULT NULL COMMENT '称重用户id',
  `gb_gwt_dis_id` int DEFAULT NULL COMMENT '称重disid',
  `gb_gwt_weight_total` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '称重单总重量',
  `gb_gwt_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '称重单总日期',
  `gb_gwt_subtotal` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '称重单总金额',
  `gb_gwt_status` tinyint DEFAULT NULL COMMENT '称重单状态',
  `gb_gwt_order_names` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '称重单总金额',
  `gb_gwt_dep_father_id` int DEFAULT NULL COMMENT '称重disid',
  `gb_gwt_trade_no` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '称重单号',
  `gb_gwt_type` tinyint DEFAULT NULL COMMENT '1 出库单 2 采购单',
  `gb_gwt_is_self` tinyint DEFAULT NULL COMMENT '0 进货 1 自制',
  `gb_gwt_order_count` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '订单数量',
  `gb_gwt_order_finish_count` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '订单完成数量',
  `gb_gwt_print_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '称重单总日期',
  PRIMARY KEY (`gb_distributer_weight_total_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- 35. gb_report - 批发商报表表
-- ----------------------------
DROP TABLE IF EXISTS `gb_report`;
CREATE TABLE `gb_report` (
  `gb_report_id` int NOT NULL AUTO_INCREMENT,
  `gb_rep_ids` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_rep_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_rep_dis_user_id` int DEFAULT NULL,
  `gb_rep_start_date` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `gb_rep_stop_date` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`gb_report_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- 36. gb_route - 批发商线路表
-- ----------------------------
DROP TABLE IF EXISTS `gb_route`;
CREATE TABLE `gb_route` (
  `gb_route_id` int NOT NULL AUTO_INCREMENT COMMENT '线路id',
  `gb_route_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '线路名称',
  `gb_route_dis_id` int DEFAULT NULL,
  PRIMARY KEY (`gb_route_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =============================================
-- 补充表创建完成
-- 共计 36 张 GB 项目缺少的表
-- =============================================
