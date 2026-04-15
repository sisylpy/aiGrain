/*
 Navicat Premium Data Transfer

 Source Server         : gpt
 Source Server Type    : MySQL
 Source Server Version : 80027 (8.0.27-0ubuntu0.20.04.1)
 Source Host           : localhost:3306
 Source Schema         : nongxinle

 Target Server Type    : MySQL
 Target Server Version : 80027 (8.0.27-0ubuntu0.20.04.1)
 File Encoding         : 65001

 Date: 11/04/2026 14:07:51
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for gb_dep_dis_goods_settle
-- ----------------------------
DROP TABLE IF EXISTS `gb_dep_dis_goods_settle`;
CREATE TABLE `gb_dep_dis_goods_settle` (
  `gb_dep_dis_goods_settle_id` int NOT NULL AUTO_INCREMENT COMMENT '社区商品id',
  `gb_ddgs_dfg_goods_father_id` int DEFAULT NULL COMMENT '批发商父类商品id',
  `gb_ddgs_distributer_id` int DEFAULT NULL COMMENT '批发商id',
  `gb_ddgs_dis_goods_id` int DEFAULT NULL COMMENT '商品名称',
  `gb_ddgs_dis_goods_name` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '商品名称',
  `gb_ddgs_dis_goods_standardname` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '商品规格',
  `gb_ddgs_dis_goods_standard_weight` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_ddgs_dis_goods_type` tinyint DEFAULT NULL,
  `gb_ddgs_dis_goods_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '价格',
  `gb_ddgs_dis_goods_lowest_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_ddgs_dis_goods_highest_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_ddgs_dis_control_price` tinyint DEFAULT NULL COMMENT '是否控制价格',
  `gb_ddgs_dis_control_fresh` tinyint DEFAULT NULL COMMENT '是否控制鲜度',
  `gb_ddgs_dis_fresh_warn_hour` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '鲜度报警小时',
  `gb_ddgs_dis_fresh_waste_hour` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '废弃报警小时',
  `gb_ddgs_dis_goods_inventory_type` tinyint DEFAULT NULL COMMENT '盘库方式1 月，2周，3日',
  `gb_ddgs_dis_goods_inventory_dep_id` int DEFAULT NULL COMMENT '盘库方式1 月，2周，3日',
  `gb_ddgs_status` tinyint DEFAULT NULL COMMENT '商品状态',
  `gb_ddgs_settle_department_id` int DEFAULT NULL COMMENT '库房或中央厨房id',
  `gb_ddgs_settle_department_father_id` int DEFAULT NULL COMMENT '库房或中央厨房id',
  `gb_ddgs_settle_amount` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '废弃报警小时',
  `gb_ddgs_settle_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '废弃报警小时',
  `gb_ddgs_settle_id` int DEFAULT NULL COMMENT '库房或中央厨房id',
  `gb_ddgs_settle_month` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '废弃报警小时',
  `gb_ddgs_settle_year` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '废弃报警小时',
  `gb_ddgs_sales_amount` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '原料销售数量',
  `gb_ddgs_sales_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '废弃报警小时',
  PRIMARY KEY (`gb_dep_dis_goods_settle_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for gb_dep_father_goods_settle
-- ----------------------------
DROP TABLE IF EXISTS `gb_dep_father_goods_settle`;
CREATE TABLE `gb_dep_father_goods_settle` (
  `gb_dep_father_goods_settle_statics_id` int NOT NULL AUTO_INCREMENT,
  `gb_dfgss_father_goods_name` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_dfgss_fathers_father_id` int DEFAULT NULL,
  `gb_dfgss_father_goods_level` tinyint DEFAULT NULL,
  `gb_dfgss_department_father_id` int DEFAULT NULL,
  `gb_dfgss_distributer_id` int DEFAULT NULL,
  `gb_dfgss_out_stock_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_dfgss_settle_id` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_dfgss_out_stock_type` tinyint DEFAULT NULL COMMENT '1 cost, 2 loss, 3 waste, 4 return',
  `gb_dfgss_father_goods_id` int DEFAULT NULL,
  `gb_dfgss_settle_month` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_dfgss_settle_year` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`gb_dep_father_goods_settle_statics_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for gb_dep_food
-- ----------------------------
DROP TABLE IF EXISTS `gb_dep_food`;
CREATE TABLE `gb_dep_food` (
  `gb_dep_food_id` int NOT NULL AUTO_INCREMENT COMMENT '供货商id',
  `gb_DF_dep_id` int DEFAULT NULL COMMENT '供货商名称',
  `gb_DF_food_id` int DEFAULT NULL COMMENT 'gbDisid',
  `gb_DF_dep_father_id` int DEFAULT NULL COMMENT '供货商名称',
  `gb_DF_status` tinyint DEFAULT NULL COMMENT 'gbDisid',
  `gb_DF_food_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`gb_dep_food_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for gb_dep_food_goods_sales
-- ----------------------------
DROP TABLE IF EXISTS `gb_dep_food_goods_sales`;
CREATE TABLE `gb_dep_food_goods_sales` (
  `gb_dep_food_goods_sales_id` int NOT NULL AUTO_INCREMENT COMMENT '供货商id',
  `gb_DFGS_dep_id` int DEFAULT NULL COMMENT '供货商名称',
  `gb_DFGS_dep_father_id` int DEFAULT NULL COMMENT '供货商名称',
  `gb_DFGS_food_sales_id` int DEFAULT NULL COMMENT 'gbDisid',
  `gb_DFGS_food_goods_id` int DEFAULT NULL COMMENT 'gbDisid',
  `gb_DFGS_goods_amount` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT 'gbDisid',
  `gb_DFGS_settle_id` int DEFAULT NULL,
  `gb_DFGS_month` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT 'gbDisid',
  `gb_DFGS_full_Date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT 'gbDisid',
  `gb_DFGS_dis_goods_id` int DEFAULT NULL COMMENT 'gbDisid',
  PRIMARY KEY (`gb_dep_food_goods_sales_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for gb_dep_food_sales
-- ----------------------------
DROP TABLE IF EXISTS `gb_dep_food_sales`;
CREATE TABLE `gb_dep_food_sales` (
  `gb_dep_food_sales_id` int NOT NULL AUTO_INCREMENT COMMENT '供货商id',
  `gb_DFS_dep_id` int DEFAULT NULL COMMENT '供货商名称',
  `gb_DFS_food_id` int DEFAULT NULL COMMENT 'gbDisid',
  `gb_DFS_dep_father_id` int DEFAULT NULL COMMENT '供货商名称',
  `gb_DFS_amount` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT 'gbDisid',
  `gb_DFS_settle_id` int DEFAULT NULL,
  `gb_DFS_month` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT 'gbDisid',
  `gb_DFS_full_Date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT 'gbDisid',
  `gb_DFS_user_id` int DEFAULT NULL COMMENT 'gbDisid',
  `gb_DFS_year` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT 'gbDisid',
  `gb_DFS_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT 'gbDisid',
  PRIMARY KEY (`gb_dep_food_sales_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for gb_dep_inventory_daily
-- ----------------------------
DROP TABLE IF EXISTS `gb_dep_inventory_daily`;
CREATE TABLE `gb_dep_inventory_daily` (
  `gb_inventory_daily_id` int NOT NULL AUTO_INCREMENT,
  `gb_id_department_father_id` int DEFAULT NULL,
  `gb_id_department_id` int DEFAULT NULL,
  `gb_id_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_id_total` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_id_distributer_id` int DEFAULT NULL,
  `gb_id_waste_total` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_id_week` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_id_year` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_id_status` tinyint DEFAULT NULL,
  `gb_id_loss_total` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_id_dep_settle_id` int DEFAULT NULL,
  `gb_id_return_total` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_id_produce_total` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`gb_inventory_daily_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for gb_dep_inventory_goods_daily
-- ----------------------------
DROP TABLE IF EXISTS `gb_dep_inventory_goods_daily`;
CREATE TABLE `gb_dep_inventory_goods_daily` (
  `gb_inventory_goods_daily_id` int NOT NULL AUTO_INCREMENT,
  `gb_igd_department_father_id` int DEFAULT NULL,
  `gb_igd_department_id` int DEFAULT NULL,
  `gb_igd_distributer_id` int DEFAULT NULL,
  `gb_igd_dis_goods_id` int DEFAULT NULL,
  `gb_igd_dis_goods_father_id` int DEFAULT NULL,
  `gb_igd_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igd_status` tinyint DEFAULT NULL,
  `gb_igd_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igd_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igd_gb_dep_stock_id` int DEFAULT NULL,
  `gb_igd_full_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igd_waste_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igd_waste_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igd_loss_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igd_loss_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igd_dep_settle_id` int DEFAULT NULL,
  `gb_igd_return_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igd_return_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igd_dep_dis_goods_id` int DEFAULT NULL,
  `gb_igd_gb_dep_stock_record_id` int DEFAULT NULL,
  `gb_igd_produce_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igd_produce_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igd_profit_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`gb_inventory_goods_daily_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for gb_dep_inventory_goods_daily_total
-- ----------------------------
DROP TABLE IF EXISTS `gb_dep_inventory_goods_daily_total`;
CREATE TABLE `gb_dep_inventory_goods_daily_total` (
  `gb_inventory_goods_daily_total_id` int NOT NULL AUTO_INCREMENT,
  `gb_igdt_department_father_id` int DEFAULT NULL,
  `gb_igdt_department_id` int DEFAULT NULL,
  `gb_igdt_distributer_id` int DEFAULT NULL,
  `gb_igdt_dis_goods_id` int DEFAULT NULL,
  `gb_igdt_dis_goods_father_id` int DEFAULT NULL,
  `gb_igdt_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igdt_status` tinyint DEFAULT NULL,
  `gb_igdt_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igdt_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igdt_full_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igdt_waste_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igdt_waste_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igdt_loss_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igdt_loss_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igdt_return_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igdt_return_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igdt_dep_dis_goods_id` int DEFAULT NULL,
  `gb_igdt_produce_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igdt_produce_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igdt_profit_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '利润单价',
  `gb_igdt_profit_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '利润总计',
  `gb_igdt_profit_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '制作成本-损耗-废弃',
  PRIMARY KEY (`gb_inventory_goods_daily_total_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for gb_dep_inventory_goods_month
-- ----------------------------
DROP TABLE IF EXISTS `gb_dep_inventory_goods_month`;
CREATE TABLE `gb_dep_inventory_goods_month` (
  `gb_inventory_goods_month_id` int NOT NULL AUTO_INCREMENT,
  `gb_igm_department_father_id` int DEFAULT NULL,
  `gb_igm_department_id` int DEFAULT NULL,
  `gb_igm_distributer_id` int DEFAULT NULL,
  `gb_igm_dis_goods_id` int DEFAULT NULL,
  `gb_igm_dis_goods_father_id` int DEFAULT NULL,
  `gb_igm_month` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igm_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igm_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igm_gb_dep_stock_id` int DEFAULT NULL,
  `gb_igm_year` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igm_full_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igm_waste_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igm_waste_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igm_status` tinyint DEFAULT NULL,
  `gb_igm_loss_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igm_loss_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igm_dep_settle_id` int DEFAULT NULL,
  `gb_igm_return_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igm_return_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igm_dep_dis_goods_id` int DEFAULT NULL,
  `gb_igm_gb_dep_stock_record_id` int DEFAULT NULL,
  `gb_igm_produce_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igm_produce_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`gb_inventory_goods_month_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for gb_dep_inventory_goods_month_total
-- ----------------------------
DROP TABLE IF EXISTS `gb_dep_inventory_goods_month_total`;
CREATE TABLE `gb_dep_inventory_goods_month_total` (
  `gb_inventory_goods_month_total_id` int NOT NULL AUTO_INCREMENT,
  `gb_igmt_department_father_id` int DEFAULT NULL,
  `gb_igmt_department_id` int DEFAULT NULL,
  `gb_igmt_distributer_id` int DEFAULT NULL,
  `gb_igmt_dis_goods_id` int DEFAULT NULL,
  `gb_igmt_dis_goods_father_id` int DEFAULT NULL,
  `gb_igmt_month` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igmt_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igmt_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igmt_year` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igmt_full_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igmt_waste_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igmt_waste_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igmt_status` tinyint DEFAULT NULL,
  `gb_igmt_loss_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igmt_loss_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igmt_return_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igmt_return_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igmt_dep_dis_goods_id` int DEFAULT NULL,
  `gb_igmt_produce_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igmt_produce_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igmt_profit_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igmt_profit_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igmt_profit_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`gb_inventory_goods_month_total_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for gb_dep_inventory_goods_week
-- ----------------------------
DROP TABLE IF EXISTS `gb_dep_inventory_goods_week`;
CREATE TABLE `gb_dep_inventory_goods_week` (
  `gb_inventory_goods_week_id` int NOT NULL AUTO_INCREMENT,
  `gb_igw_department_father_id` int DEFAULT NULL,
  `gb_igw_department_id` int DEFAULT NULL,
  `gb_igw_distributer_id` int DEFAULT NULL,
  `gb_igw_dis_goods_id` int DEFAULT NULL,
  `gb_igw_dis_goods_father_id` int DEFAULT NULL,
  `gb_igw_week` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igw_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igw_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igw_gb_dep_stock_id` int DEFAULT NULL,
  `gb_igw_year` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igw_full_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igw_waste_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igw_waste_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igw_status` tinyint DEFAULT NULL,
  `gb_igw_loss_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igw_loss_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igw_dep_settle_id` int DEFAULT NULL,
  `gb_igw_return_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igw_return_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igw_dep_dis_goods_id` int DEFAULT NULL,
  `gb_igw_gb_dep_stock_record_id` int DEFAULT NULL,
  `gb_igw_produce_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igw_produce_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`gb_inventory_goods_week_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for gb_dep_inventory_goods_week_total
-- ----------------------------
DROP TABLE IF EXISTS `gb_dep_inventory_goods_week_total`;
CREATE TABLE `gb_dep_inventory_goods_week_total` (
  `gb_inventory_goods_week_total_id` int NOT NULL AUTO_INCREMENT,
  `gb_igwt_department_father_id` int DEFAULT NULL,
  `gb_igwt_department_id` int DEFAULT NULL,
  `gb_igwt_distributer_id` int DEFAULT NULL,
  `gb_igwt_dis_goods_id` int DEFAULT NULL,
  `gb_igwt_dis_goods_father_id` int DEFAULT NULL,
  `gb_igwt_week` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igwt_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igwt_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igwt_year` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igwt_full_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igwt_waste_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igwt_waste_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igwt_status` tinyint DEFAULT NULL,
  `gb_igwt_loss_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igwt_loss_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igwt_return_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igwt_return_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igwt_dep_dis_goods_id` int DEFAULT NULL,
  `gb_igwt_produce_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igwt_produce_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igwt_profit_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igwt_profit_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_igwt_profit_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`gb_inventory_goods_week_total_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for gb_dep_inventory_month
-- ----------------------------
DROP TABLE IF EXISTS `gb_dep_inventory_month`;
CREATE TABLE `gb_dep_inventory_month` (
  `gb_inventory_month_id` int NOT NULL AUTO_INCREMENT,
  `gb_im_department_father_id` int DEFAULT NULL,
  `gb_im_department_id` int DEFAULT NULL,
  `gb_im_month` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_im_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_im_distributer_id` int DEFAULT NULL,
  `gb_im_waste_total` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_im_year` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_im_status` tinyint DEFAULT NULL,
  `gb_im_loss_total` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_im_dep_settle_id` int DEFAULT NULL,
  `gb_im_return_total` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_im_produce_total` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`gb_inventory_month_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for gb_dep_inventory_week
-- ----------------------------
DROP TABLE IF EXISTS `gb_dep_inventory_week`;
CREATE TABLE `gb_dep_inventory_week` (
  `gb_inventory_week_id` int NOT NULL AUTO_INCREMENT,
  `gb_diw_department_father_id` int DEFAULT NULL,
  `gb_diw_department_id` int DEFAULT NULL,
  `gb_diw_week` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_diw_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_diw_distributer_id` int DEFAULT NULL,
  `gb_diw_waste_total` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_diw_year` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_diw_status` tinyint DEFAULT NULL,
  `gb_diw_loss_total` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_diw_dep_settle_id` int DEFAULT NULL,
  `gb_diw_return_total` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_diw_produce_total` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`gb_inventory_week_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for gb_department
-- ----------------------------
DROP TABLE IF EXISTS `gb_department`;
CREATE TABLE `gb_department` (
  `gb_department_id` int NOT NULL AUTO_INCREMENT COMMENT '订货部门id',
  `gb_department_name` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订货部门名称',
  `gb_department_father_id` int DEFAULT NULL COMMENT '订货部门上级id',
  `gb_department_type` tinyint DEFAULT NULL COMMENT '订货部门类型',
  `gb_department_sub_amount` int DEFAULT NULL COMMENT '订货部门子部门数量',
  `gb_department_dis_id` int DEFAULT NULL COMMENT '订货部门批发商id',
  `gb_department_file_path` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_department_is_group_dep` tinyint DEFAULT NULL COMMENT '是客户吗',
  `gb_department_print_name` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_department_show_weeks` tinyint DEFAULT '1',
  `gb_department_settle_type` tinyint DEFAULT NULL,
  `gb_department_attr_name` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '客户简称',
  `gb_department_route_id` int DEFAULT NULL,
  `gb_department_settle_full_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '结算时间',
  `gb_department_settle_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '结算时间',
  `gb_department_settle_week` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '结算周',
  `gb_department_settle_month` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '结算月',
  `gb_department_settle_times` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '结算月',
  `gb_department_settle_year` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '结算月',
  `gb_department_dep_settle_id` int DEFAULT NULL COMMENT '结算月',
  `gb_department_level` int DEFAULT NULL COMMENT '加盟级别',
  `gb_department_sort` int DEFAULT NULL COMMENT '排序',
  `gb_department_print_set` int DEFAULT NULL COMMENT '排序',
  `gb_department_name_py` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订货部门名称拼音',
  `gb_department_latitude` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_department_longitude` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DB_dep_father_id` int DEFAULT NULL,
  PRIMARY KEY (`gb_department_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=32 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for gb_department_bill
-- ----------------------------
DROP TABLE IF EXISTS `gb_department_bill`;
CREATE TABLE `gb_department_bill` (
  `gb_department_bill_id` int NOT NULL AUTO_INCREMENT,
  `gb_DB_dis_id` int DEFAULT NULL,
  `gb_DB_dep_id` int DEFAULT NULL,
  `gb_DB_total` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DB_status` tinyint DEFAULT NULL,
  `gb_DB_time` varchar(30) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DB_issue_user_id` int DEFAULT NULL,
  `gb_DB_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DB_month` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DB_week` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DB_trade_no` varchar(32) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DB_print_times` int DEFAULT NULL,
  `gb_DB_day` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '星期',
  `gb_DB_issue_order_type` tinyint DEFAULT NULL,
  `gb_DB_issue_dep_id` int DEFAULT NULL,
  `gb_DB_order_amount` int DEFAULT NULL,
  `gb_DB_confirm_goods_user_id` int DEFAULT NULL,
  `gb_DB_confirm_price_user_id` int DEFAULT NULL,
  `gb_DB_confirm_settle_user_id` int DEFAULT NULL,
  `gb_DB_confirm_goods_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DB_confirm_price_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DB_confirm_settle_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DB_dep_settle_id` int DEFAULT NULL,
  `gb_DB_issue_nx_dis_id` int DEFAULT NULL,
  `gb_DB_selling_total` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DB_gb_supplier_payment_id` int DEFAULT NULL,
  `gb_DB_wx_out_trade_no` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DB_will_pay_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DB_dep_father_id` int DEFAULT NULL,
  `gb_DB_year` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DB_set_auto_goods` tinyint DEFAULT NULL,
  `gb_DB_pay_total` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DB_user_coupon_id` int DEFAULT NULL,
  `gb_DB_user_coupon_total` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DB_return_order_total` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DB_return_order_id` int DEFAULT NULL,
  PRIMARY KEY (`gb_department_bill_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for gb_department_dis_goods
-- ----------------------------
DROP TABLE IF EXISTS `gb_department_dis_goods`;
CREATE TABLE `gb_department_dis_goods` (
  `gb_department_dis_goods_id` int NOT NULL AUTO_INCREMENT,
  `gb_DDG_department_father_id` int DEFAULT NULL,
  `gb_DDG_department_id` int DEFAULT NULL,
  `gb_DDG_dis_goods_id` int DEFAULT NULL,
  `gb_DDG_dis_goods_father_id` int DEFAULT NULL,
  `gb_DDG_dep_goods_name` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DDG_dep_goods_pinyin` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DDG_dep_goods_py` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DDG_dep_goods_standardname` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DDG_dep_goods_detail` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DDG_dep_goods_brand` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DDG_dep_goods_place` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DDG_gb_department_id` int DEFAULT NULL COMMENT '库房或中央厨房id',
  `gb_DDG_goods_type` tinyint DEFAULT NULL,
  `gb_DDG_nx_distributer_id` int DEFAULT NULL COMMENT '商品库父类id',
  `gb_DDG_nx_distributer_goods_id` int DEFAULT NULL,
  `gb_DDG_inventory_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DDG_inventory_full_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DDG_stock_total_weight` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DDG_stock_total_subtotal` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DDG_gb_supplier_id` int DEFAULT NULL COMMENT '指定供货商id',
  `gb_DDG_prepare_total_weight` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DDG_show_standard_name` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DDG_show_standard_id` int DEFAULT NULL,
  `gb_DDG_show_standard_scale` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DDG_level_price` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '加盟级别商品价格',
  `gb_DDG_prepare_status` int DEFAULT NULL,
  `gb_DDG_selling_price` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '加盟级别商品价格',
  `gb_DDG_show_standard_weight` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DDG_order_price` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DDG_order_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DDG_order_remark` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DDG_order_quantity` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DDG_order_standard` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DDG_print_standard` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DDG_order_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DDG_gb_dis_id` int DEFAULT NULL,
  `gb_DDG_dep_goods_pull_off` int DEFAULT NULL,
  `gb_DDG_dep_goods_status` int DEFAULT NULL,
  `gb_DDG_dis_goods_grand_id` int DEFAULT NULL,
  `gb_DDG_dis_goods_great_id` int DEFAULT NULL,
  `gb_DDG_order_goods_name` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DDG_order_price_level` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`gb_department_dis_goods_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=75 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for gb_department_goods_daily
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
  `gb_dgd_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃用户',
  `gb_dgd_rest_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃用户',
  `gb_dgd_produce_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃用户',
  `gb_dgd_loss_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃用户',
  `gb_dgd_waste_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃用户',
  `gb_dgd_return_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃用户',
  `gb_dgd_subtotal` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃用户',
  `gb_dgd_date` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '盘库日期',
  `gb_dgd_day` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃用户',
  `gb_dgd_week` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '盘库周',
  `gb_dgd_month` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '盘库月',
  `gb_dgd_full_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃时间',
  `gb_dgd_year` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '盘库月',
  `gb_dgd_produce_subtotal` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃数量',
  `gb_dgd_rest_subtotal` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃用户',
  `gb_dgd_loss_subtotal` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃数量',
  `gb_dgd_waste_subtotal` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃数量',
  `gb_dgd_return_subtotal` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃数量',
  `gb_dgd_profit_subtotal` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃数量',
  `gb_dgd_sales_subtotal` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃数量',
  `gb_dgd_after_profit_subtotal` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃用户',
  `gb_dgd_sell_clear_hour` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '沽清小时',
  `gb_dgd_sell_clear_minute` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '沽清分钟',
  `gb_dgd_last_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃用户',
  `gb_dgd_last_subtotal` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃用户',
  `gb_dgd_fresh_rate` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '沽清分钟',
  `gb_dgd_task_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '沽清分钟',
  `gb_dgd_last_produce_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '剩余下手数量',
  `gb_dgd_status` tinyint DEFAULT NULL COMMENT '-1，停用',
  `gb_dgd_gb_dis_goods_grand_id` int DEFAULT NULL,
  `gb_dgd_gb_dis_goods_great_grand_id` int DEFAULT NULL,
  PRIMARY KEY (`gb_department_goods_daily_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=411 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for gb_department_goods_stock
-- ----------------------------
DROP TABLE IF EXISTS `gb_department_goods_stock`;
CREATE TABLE `gb_department_goods_stock` (
  `gb_department_goods_stock_id` int NOT NULL AUTO_INCREMENT,
  `gb_dgs_gb_distributer_id` int DEFAULT NULL,
  `gb_dgs_gb_department_id` int DEFAULT NULL,
  `gb_dgs_gb_department_father_id` int DEFAULT NULL,
  `gb_dgs_gb_dis_goods_id` int DEFAULT NULL,
  `gb_dgs_gb_dis_goods_father_id` int DEFAULT NULL,
  `gb_dgs_gb_dep_dis_goods_id` int DEFAULT NULL,
  `gb_dgs_gb_department_order_id` int DEFAULT NULL,
  `gb_dgs_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '批次数量',
  `gb_dgs_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '批次单价',
  `gb_dgs_selling_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '销售单价',
  `gb_dgs_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '批次成本',
  `gb_dgs_rest_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '剩余数量',
  `gb_dgs_rest_weight_show_standard` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '剩余数量显示规格',
  `gb_dgs_rest_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '批次剩余成本',
  `gb_dgs_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '批次日期',
  `gb_dgs_full_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '盘库日期',
  `gb_dgs_out_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '出库日期',
  `gb_dgs_out_full_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '出库时间',
  `gb_dgs_out_hour` int DEFAULT NULL COMMENT '出货小时',
  `gb_dgs_receive_user_id` int DEFAULT NULL COMMENT '接收用户',
  `gb_dgs_status` tinyint DEFAULT NULL COMMENT '批次状态',
  `gb_dgs_gb_pur_goods_id` int DEFAULT NULL COMMENT '批次采购商品id',
  `gb_dgs_gb_price_goods_id` int DEFAULT NULL COMMENT '价格异常商品id',
  `gb_dgs_gb_price_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '价格异常价格',
  `gb_dgs_gb_price_subtotal_scale` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '价格异常价格',
  `gb_dgs_gb_goods_stock_id` int DEFAULT NULL COMMENT '批次采购商品id',
  `gb_dgs_gb_from_department_id` int DEFAULT NULL COMMENT '出库部门id',
  `gb_dgs_week` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '批次周',
  `gb_dgs_month` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '批次月',
  `gb_dgs_year` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '批次年',
  `gb_dgs_time_stamp` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '时间戳',
  `gb_dgs_warn_full_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '盘库日期',
  `gb_dgs_waste_full_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '盘库日期',
  `gb_dgs_warn_time_quantum_name` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '报警时间段名称',
  `gb_dgs_waste_time_quantum_name` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '废弃时间段名称',
  `gb_dgs_do_waste_full_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃时间',
  `gb_dgs_inventory_full_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '盘库日期',
  `gb_dgs_inventory_date` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '盘库日期',
  `gb_dgs_inventory_week` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '盘库周',
  `gb_dgs_inventory_month` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '盘库月',
  `gb_dgs_inventory_year` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '盘库月',
  `gb_dgs_dep_settle_id` int DEFAULT NULL,
  `gb_dgs_from_dep_settle_id` int DEFAULT NULL COMMENT '出货部门settleId',
  `gb_dgs_return_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '退货数量',
  `gb_dgs_return_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '退货成本',
  `gb_dgs_produce_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '制作数量',
  `gb_dgs_produce_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '制作成本',
  `gb_dgs_loss_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '损耗数量',
  `gb_dgs_loss_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '损耗成本',
  `gb_dgs_waste_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '制作数量',
  `gb_dgs_waste_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '制作数量',
  `gb_dgs_between_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '利润单价',
  `gb_dgs_profit_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '利润小计',
  `gb_dgs_profit_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '利润重量',
  `gb_dgs_selling_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '销售小计',
  `gb_dgs_weight_goods_id` int DEFAULT NULL COMMENT '备货商品id',
  `gb_dgs_after_profit_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '销售利润',
  `gb_dgs_cost_rate` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '成本率',
  `gb_dgs_rest_weight_show_standard_name` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '剩余数量显示规格',
  `gb_dgs_nx_distributer_id` int DEFAULT NULL COMMENT '备货商品id',
  `gb_dgs_produce_selling_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '销售小计',
  `gb_dgs_gb_dis_goods_grand_id` int DEFAULT NULL,
  `gb_dgs_gb_dis_goods_great_id` int DEFAULT NULL,
  `gb_dgs_stars` int DEFAULT NULL,
  `gb_dgs_nx_supplier_id` int DEFAULT NULL COMMENT '备货商品id',
  `gb_dgs_pur_user_id` int DEFAULT NULL COMMENT '备货商品id',
  PRIMARY KEY (`gb_department_goods_stock_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=75 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for gb_department_goods_stock_record
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
  `gb_dgsc_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '批次数量',
  `gb_dgsc_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '批次单价',
  `gb_dgsc_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '批次成本',
  `gb_dgsc_rest_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '剩余数量',
  `gb_dgsc_rest_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '批次剩余成本',
  `gb_dgsc_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '批次日期',
  `gb_dgsc_receive_user_id` int DEFAULT NULL COMMENT '接收用户',
  `gb_dgsc_status` tinyint DEFAULT NULL COMMENT '批次状态',
  `gb_dgsc_gb_pur_goods_id` int DEFAULT NULL COMMENT '批次采购商品id',
  `gb_dgsc_gb_goods_stock_id` int DEFAULT NULL COMMENT '批次采购商品id',
  `gb_dgsc_gb_from_department_id` int DEFAULT NULL COMMENT '出库部门id',
  `gb_dgsc_inventory_date` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '盘库日期',
  `gb_dgsc_inventory_week` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '盘库周',
  `gb_dgsc_inventory_month` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '盘库月',
  `gb_dgsc_gb_dis_goods_father_id` int DEFAULT NULL,
  `gb_dgsc_full_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '盘库日期',
  `gb_dgsc_warn_full_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '盘库日期',
  `gb_dgsc_waste_full_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '盘库日期',
  `gb_dgsc_do_waste_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃数量',
  `gb_dgsc_do_waste_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃金额',
  `gb_dgsc_do_waste_full_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃时间',
  `gb_dgsc_do_waste_user_id` int DEFAULT NULL COMMENT '执行废弃用户',
  `gb_dgsc_loss_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃数量',
  `gb_dgsc_loss_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃数量',
  `gb_dgsc_dep_settle_id` int DEFAULT NULL,
  `gb_dgsc_return_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃数量',
  `gb_dgsc_return_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃数量',
  `gb_dgsc_from_dep_settle_id` int DEFAULT NULL,
  `gb_dgsc_dep_dis_goods_settle_id` int DEFAULT NULL COMMENT '统计商品成本id',
  `gb_dgsc_from_gb_goods_stock_id` int DEFAULT NULL COMMENT '批次采购商品id',
  `gb_dgsc_produce_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '制作数量',
  `gb_dgsc_produce_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '制作成本',
  `gb_dgsc_inventory_many` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '盘库周',
  `gb_dgsc_gb_inventory_type` tinyint DEFAULT NULL COMMENT '盘库周',
  `gb_dgsc_selling_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '制作数量',
  `gb_dgsc_selling_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '制作数量',
  `gb_dgsc_profit_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '制作数量',
  `gb_dgsc_profit_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '制作数量',
  `gb_dgsc_profit_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '制作数量',
  `gb_dgsc_week` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '盘库日期',
  `gb_dgsc_month` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '盘库日期',
  `gb_dgsc_year` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '盘库日期',
  `gb_dgsc_time_stamp` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '盘库日期',
  PRIMARY KEY (`gb_department_goods_stock_record_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for gb_department_goods_stock_reduce
-- ----------------------------
DROP TABLE IF EXISTS `gb_department_goods_stock_reduce`;
CREATE TABLE `gb_department_goods_stock_reduce` (
  `gb_department_goods_stock_reduce_id` int NOT NULL AUTO_INCREMENT,
  `gb_dgsr_gb_distributer_id` int DEFAULT NULL,
  `gb_dgsr_gb_department_id` int DEFAULT NULL,
  `gb_dgsr_gb_department_father_id` int DEFAULT NULL,
  `gb_dgsr_gb_dis_goods_id` int DEFAULT NULL,
  `gb_dgsr_gb_dep_dis_goods_id` int DEFAULT NULL,
  `gb_dgsr_gb_dis_goods_father_id` int DEFAULT NULL,
  `gb_dgsr_gb_goods_stock_id` int DEFAULT NULL COMMENT '批次采购商品id',
  `gb_dgsr_date` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '盘库日期',
  `gb_dgsr_week` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '盘库周',
  `gb_dgsr_month` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '盘库月',
  `gb_dgsr_full_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃时间',
  `gb_dgsr_type` tinyint DEFAULT NULL COMMENT '1,cost;2waste;3loass;4return',
  `gb_dgsr_do_user_id` int DEFAULT NULL COMMENT '执行废弃用户',
  `gb_dgsr_cost_weight` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃用户',
  `gb_dgsr_cost_subtotal` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃数量',
  `gb_dgsr_waste_weight` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃用户',
  `gb_dgsr_waste_subtotal` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃数量',
  `gb_dgsr_loss_weight` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃用户',
  `gb_dgsr_loss_subtotal` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃数量',
  `gb_dgsr_return_weight` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃用户',
  `gb_dgsr_return_subtotal` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃数量',
  `gb_dgsr_produce_weight` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃用户',
  `gb_dgsr_produce_subtotal` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃数量',
  `gb_dgsr_dep_settle_id` int DEFAULT NULL COMMENT '执行结算id',
  `gb_dgsr_from_dep_settle_id` int DEFAULT NULL COMMENT '执行结算id',
  `gb_dgsr_gb_goods_stock_record_id` int DEFAULT NULL COMMENT '批次采购商品id',
  `gb_dgsr_gb_pur_goods_id` int DEFAULT NULL COMMENT '批次采购商品id',
  `gb_dgsr_gb_goods_inventory_type` tinyint DEFAULT NULL COMMENT '批次采购商品id',
  `gb_dgsr_profit_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃数量',
  `gb_dgsr_sales_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃数量',
  `gb_dgsr_status` int DEFAULT NULL COMMENT '批次采购商品id',
  `gb_dgsr_stock_nx_distributer_id` int DEFAULT NULL COMMENT '批次采购商品id',
  `gb_dgsr_stock_nx_supplier_id` int DEFAULT NULL COMMENT '批次采购商品id',
  `gb_dgsr_gb_dis_goods_grand_id` int DEFAULT NULL,
  `gb_dgsr_gb_dis_goods_great_id` int DEFAULT NULL,
  `gb_dgsr_stock_pur_user_id` int DEFAULT NULL COMMENT '批次采购商品id',
  `gb_dgsr_gb_purchase_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃时间',
  PRIMARY KEY (`gb_department_goods_stock_reduce_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=81 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for gb_department_goods_stock_reduce_attachment
-- ----------------------------
DROP TABLE IF EXISTS `gb_department_goods_stock_reduce_attachment`;
CREATE TABLE `gb_department_goods_stock_reduce_attachment` (
  `gb_department_goods_stock_reduce_attach_id` int NOT NULL AUTO_INCREMENT,
  `gb_dgsra_gb_dgsr_id` int DEFAULT NULL,
  `gb_dgsra_content` text CHARACTER SET utf16 COLLATE utf16_czech_ci,
  `gb_dgsra_file_path` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_dgsra_status` int DEFAULT NULL,
  `gb_dgsra_file_large_path` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_dgsra_type` int DEFAULT NULL,
  `gb_dgsra_nx_supplier_id` int DEFAULT NULL,
  `gb_dgsra_nx_distributer_id` int DEFAULT NULL,
  `gb_dgsra_stars` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`gb_department_goods_stock_reduce_attach_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for gb_department_goods_stock_reduce_daily
-- ----------------------------
DROP TABLE IF EXISTS `gb_department_goods_stock_reduce_daily`;
CREATE TABLE `gb_department_goods_stock_reduce_daily` (
  `gb_department_goods_stock_reduce_daily_id` int NOT NULL AUTO_INCREMENT,
  `gb_dgsrd_gb_distributer_id` int DEFAULT NULL,
  `gb_dgsrd_gb_department_id` int DEFAULT NULL,
  `gb_dgsrd_gb_department_father_id` int DEFAULT NULL,
  `gb_dgsrd_gb_dis_goods_id` int DEFAULT NULL,
  `gb_dgsrd_gb_dep_dis_goods_id` int DEFAULT NULL,
  `gb_dgsrd_gb_dis_goods_father_id` int DEFAULT NULL,
  `gb_dgsrd_gb_goods_stock_id` int DEFAULT NULL COMMENT '批次采购商品id',
  `gb_dgsrd_date` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '盘库日期',
  `gb_dgsrd_week` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '盘库周',
  `gb_dgsrd_month` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '盘库月',
  `gb_dgsrd_full_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃时间',
  `gb_dgsrd_type` tinyint DEFAULT NULL COMMENT '1,cost;2waste;3loass;4return',
  `gb_dgsrd_do_user_id` int DEFAULT NULL COMMENT '执行废弃用户',
  `gb_dgsrd_cost_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃用户',
  `gb_dgsrd_cost_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃数量',
  `gb_dgsrd_waste_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃用户',
  `gb_dgsrd_waste_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃数量',
  `gb_dgsrd_loss_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃用户',
  `gb_dgsrd_loss_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃数量',
  `gb_dgsrd_return_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃用户',
  `gb_dgsrd_return_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃数量',
  `gb_dgsrd_produce_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃用户',
  `gb_dgsrd_produce_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃数量',
  `gb_dgsrd_gb_pur_goods_id` int DEFAULT NULL COMMENT '批次采购商品id',
  `gb_dgsrd_gb_goods_inventory_type` tinyint DEFAULT NULL COMMENT '批次采购商品id',
  `gb_dgsrd_profit_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃数量',
  `gb_dgsrd_sales_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃数量',
  `gb_dgsrd_year` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '盘库月',
  `gb_dgsrd_rest_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃用户',
  `gb_dgsrd_day` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃用户',
  `gb_dgsrd_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃用户',
  `gb_dgsrd_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃用户',
  `gb_dgsrd_produce_profit_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃用户',
  PRIMARY KEY (`gb_department_goods_stock_reduce_daily_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for gb_department_orders
-- ----------------------------
DROP TABLE IF EXISTS `gb_department_orders`;
CREATE TABLE `gb_department_orders` (
  `gb_department_orders_id` int NOT NULL AUTO_INCREMENT COMMENT '部门订单id',
  `gb_DO_nx_goods_id` int DEFAULT NULL COMMENT '部门订单nx商品id',
  `gb_DO_nx_goods_father_id` int DEFAULT NULL COMMENT '部门订单商品父id',
  `gb_DO_dis_goods_id` int DEFAULT NULL COMMENT '部门订单社区商品id',
  `gb_DO_dis_goods_father_id` int DEFAULT NULL COMMENT '批发商父级商品id',
  `gb_DO_dep_dis_goods_id` int DEFAULT NULL,
  `gb_DO_quantity` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请数量',
  `gb_DO_standard` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请规格',
  `gb_DO_remark` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请备注',
  `gb_DO_weight` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单重量',
  `gb_DO_price` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单商品单价',
  `gb_DO_subtotal` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请商品小计',
  `gb_DO_department_id` int DEFAULT NULL COMMENT '部门订单部门id',
  `gb_DO_department_father_id` int DEFAULT NULL,
  `gb_DO_distributer_id` int DEFAULT NULL COMMENT '部门订单批发商id',
  `gb_DO_purchase_user_id` int DEFAULT NULL COMMENT '部门商品采购员id',
  `gb_DO_bill_id` int DEFAULT NULL COMMENT '部门订单账单id',
  `gb_DO_status` tinyint DEFAULT NULL COMMENT '部门订单申请商品状态',
  `gb_DO_order_user_id` int DEFAULT NULL COMMENT '部门订单订货用户id',
  `gb_DO_pick_user_id` int DEFAULT NULL COMMENT '部门订单商品称重用户id',
  `gb_DO_receive_user_id` int DEFAULT NULL COMMENT '收货用户id',
  `gb_DO_buy_status` tinyint DEFAULT NULL COMMENT '部门订单商品进货状态',
  `gb_DO_purchase_goods_id` int DEFAULT NULL COMMENT '订单采购商品id',
  `gb_DO_apply_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请时间',
  `gb_DO_apply_what_day` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请星期',
  `gb_DO_apply_arrive_date` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请时间',
  `gb_DO_apply_full_time` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DO_apply_only_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DO_arrive_only_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DO_arrive_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单送达时间',
  `gb_DO_arrive_weeks_year` int DEFAULT NULL COMMENT '本年第几周',
  `gb_DO_arrive_what_day` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '星期几',
  `gb_DO_goods_type` tinyint DEFAULT NULL COMMENT '配送商品0，自采购商品1',
  `gb_DO_operation_time` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DO_is_agent` tinyint DEFAULT NULL,
  `gb_DO_cost_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '进货单价',
  `gb_DO_cost_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '进货重量',
  `gb_DO_cost_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '进货小计',
  `gb_DO_nx_distributer_id` int DEFAULT NULL COMMENT 'comGoods的批发商商品id',
  `gb_DO_nx_distributer_goods_id` int DEFAULT NULL COMMENT 'comGoods的批发商',
  `gb_DO_dg_goods_sell_type` tinyint DEFAULT NULL COMMENT '按规格销售方式',
  `gb_DO_nx_department_order_id` int DEFAULT NULL COMMENT 'nxDepartmentId',
  `gb_DO_to_department_id` int DEFAULT NULL COMMENT '库房或者中央厨房部门id',
  `gb_DO_order_type` tinyint DEFAULT NULL COMMENT '订单类型',
  `gb_DO_return_user_id` int DEFAULT NULL COMMENT '库房或者中央厨房部门id',
  `gb_DO_ds_standard_id` int DEFAULT NULL COMMENT '订货单位id',
  `gb_DO_ds_standard_scale` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订货单位比例',
  `gb_DO_scale_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单重量',
  `gb_DO_scale_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单重量',
  `gb_DO_weight_total_id` int DEFAULT NULL COMMENT '拣货单id',
  `gb_DO_selling_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '零售单价',
  `gb_DO_selling_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '零售小计',
  `gb_DO_weight_goods_id` int DEFAULT NULL COMMENT 'id',
  `gb_DO_price_different` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT 'id',
  `gb_DO_dgsr_return_id` int DEFAULT NULL COMMENT '库房或者中央厨房部门id',
  `gb_DO_dis_goods_grand_id` int DEFAULT NULL COMMENT '批发商父级商品id',
  `gb_DO_nx_goods_grand_id` int DEFAULT NULL COMMENT '批发商父级商品id',
  `gb_DO_nx_goods_great_id` int DEFAULT NULL COMMENT '批发商父级商品id',
  `gb_DO_print_standard` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT 'id',
  `gb_DO_cost_price_level` int DEFAULT NULL COMMENT '批发商父级商品id',
  `gb_DO_goods_name` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '商品名称',
  `gb_DO_dis_goods_great_id` int DEFAULT NULL COMMENT '批发商父级商品id',
  PRIMARY KEY (`gb_department_orders_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=93 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for gb_department_orders_history
-- ----------------------------
DROP TABLE IF EXISTS `gb_department_orders_history`;
CREATE TABLE `gb_department_orders_history` (
  `gb_department_orders_history_id` int NOT NULL AUTO_INCREMENT COMMENT '部门订单id',
  `gb_DOH_dep_dis_goods_id` int DEFAULT NULL COMMENT '部门id',
  `gb_DOH_quantity` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请数量',
  `gb_DOH_standard` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请规格',
  `gb_DOH_remark` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请备注',
  `gb_DOH_department_id` int DEFAULT NULL COMMENT '部门订单部门id',
  `gb_DOH_department_father_id` int DEFAULT NULL,
  `gb_DOH_order_user_id` int DEFAULT NULL COMMENT '部门订单订货用户id',
  `gb_DOH_apply_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请时间',
  `gb_DOH_sell_type` tinyint DEFAULT NULL COMMENT '出货方式0,日采;1,出库;2,供货商;3,加工',
  `gb_DOH_standard_id` int DEFAULT NULL COMMENT '部门订单申请规格',
  `gb_DOH_standard_scale` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请规格',
  `gb_DOH_dis_goods_id` int DEFAULT NULL COMMENT '部门id',
  `gb_DOH_distributer_id` int DEFAULT NULL COMMENT '部门id',
  PRIMARY KEY (`gb_department_orders_history_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for gb_department_settle
-- ----------------------------
DROP TABLE IF EXISTS `gb_department_settle`;
CREATE TABLE `gb_department_settle` (
  `gb_department_settle_id` int NOT NULL AUTO_INCREMENT,
  `gb_DS_dis_id` int DEFAULT NULL,
  `gb_DS_dep_id` int DEFAULT NULL,
  `gb_DS_status` tinyint DEFAULT NULL,
  `gb_DS_time` varchar(30) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DS_settle_user_id` int DEFAULT NULL,
  `gb_DS_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DS_month` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DS_week` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DS_day` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '星期',
  `gb_DS_year` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '星期',
  `gb_DS_cost_arr_total` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DS_waste_arr_total` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DS_loss_arr_total` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DS_return_arr_total` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DS_rest_total` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DS_start_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DS_start_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DS_stop_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DS_stop_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DS_stock_total` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DS_out_total` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DS_last_total` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`gb_department_settle_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for gb_department_user
-- ----------------------------
DROP TABLE IF EXISTS `gb_department_user`;
CREATE TABLE `gb_department_user` (
  `gb_department_user_id` int NOT NULL AUTO_INCREMENT COMMENT '订货部门用户id',
  `gb_DU_department_id` int DEFAULT NULL COMMENT '订货部门id',
  `gb_DU_wx_avartra_url` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订货部门用户微信头像',
  `gb_DU_wx_nick_name` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订货部门用户微信昵称',
  `gb_DU_wx_open_id` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订货部门用户微信openid',
  `gb_DU_wx_phone` varchar(15) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订货部门用户微信手机号码',
  `gb_DU_admin` tinyint DEFAULT NULL COMMENT '订货部门用户是否是管理员',
  `gb_DU_distributer_id` int DEFAULT NULL COMMENT '订货部门批发商id',
  `gb_DU_url_change` tinyint DEFAULT NULL,
  `gb_DU_department_father_id` int DEFAULT NULL,
  `gb_DU_join_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DU_print_device_id` varchar(40) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DU_print_bill_device_id` varchar(40) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DU_customer_service` int DEFAULT NULL,
  `gb_DU_login_times` int DEFAULT NULL,
  PRIMARY KEY (`gb_department_user_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for gb_dis_nx_dis
-- ----------------------------
DROP TABLE IF EXISTS `gb_dis_nx_dis`;
CREATE TABLE `gb_dis_nx_dis` (
  `gb_GDND_id` int NOT NULL COMMENT '批发商社区id',
  `gb_GDND_gb_dis_id` int DEFAULT NULL,
  `gb_GDND_nx_dis_id` int DEFAULT NULL,
  PRIMARY KEY (`gb_GDND_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for gb_distributer
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer`;
CREATE TABLE `gb_distributer` (
  `gb_distributer_id` int NOT NULL AUTO_INCREMENT COMMENT '批发商id',
  `gb_distributer_name` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '批发商名称',
  `gb_distributer_lan` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '批发商位置经度',
  `gb_distributer_lun` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '批发商位置纬度',
  `gb_distributer_business_type` int DEFAULT NULL COMMENT '批发商商业类型',
  `gb_distributer_manager` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_distributer_phone` varchar(30) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_distributer_address` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_distributer_img` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_distributer_settle_full_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '结算时间',
  `gb_distributer_settle_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '结算时间',
  `gb_distributer_settle_week` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '结算周',
  `gb_distributer_settle_month` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '结算月',
  `gb_distributer_settle_year` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '结算月',
  `gb_distributer_settle_times` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '结算月',
  `gb_distributer_time_quantum` tinyint DEFAULT NULL COMMENT '经营时间段',
  `gb_distributer_print_name` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '批发商名称',
  `gb_distributer_buy_quantity` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_distributer_sys_city_id` tinyint DEFAULT NULL COMMENT '批发商商业类型',
  `gb_distributer_nx_dis_id` tinyint DEFAULT NULL COMMENT '批发商商业类型',
  `gb_distributer_pick_name` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '批发商名称',
  `gb_distributer_record_seconds` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '批发商名称',
  `gb_distributer_stock_cycle` tinyint DEFAULT NULL COMMENT '库存显示周期\n',
  PRIMARY KEY (`gb_distributer_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for gb_distributer_alias
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_alias`;
CREATE TABLE `gb_distributer_alias` (
  `gb_distributer_alias_id` int NOT NULL AUTO_INCREMENT,
  `gb_DA_dis_goods_id` int DEFAULT NULL,
  `gb_DA_alias_name` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DA_gb_alias_id` int DEFAULT NULL,
  `gb_DA_alias_pinyin` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DA_alias_py` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`gb_distributer_alias_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for gb_distributer_father_goods
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_father_goods`;
CREATE TABLE `gb_distributer_father_goods` (
  `gb_distributer_father_goods_id` int NOT NULL AUTO_INCREMENT,
  `gb_dfg_father_goods_name` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_dfg_father_goods_img` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_dfg_father_goods_sort` int DEFAULT NULL,
  `gb_dfg_father_goods_color` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_dfg_fathers_father_id` int DEFAULT NULL,
  `gb_dfg_father_goods_level` tinyint DEFAULT NULL,
  `gb_dfg_distributer_id` int DEFAULT NULL,
  `gb_dfg_goods_amount` int DEFAULT NULL,
  `gb_dfg_nx_goods_id` int DEFAULT NULL,
  `gb_dfg_price_amount` int DEFAULT NULL,
  `gb_dfg_price_two_amount` int DEFAULT NULL,
  `gb_dfg_price_three_amount` int DEFAULT NULL,
  `gb_dfg_father_goods_img_large` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`gb_distributer_father_goods_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=71 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for gb_distributer_food
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_food`;
CREATE TABLE `gb_distributer_food` (
  `gb_distributer_food_id` int NOT NULL AUTO_INCREMENT COMMENT '供货商id',
  `gb_distributer_food_name` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '供货商名称',
  `gb_distributer_food_price` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT 'gbDisid',
  `gb_distributer_food_father_id` int DEFAULT NULL COMMENT '父级id',
  `gb_DF_gb_distributer_id` int DEFAULT NULL COMMENT 'gbDisid',
  `gb_distributer_food_img` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT 'gbDisid',
  `gb_distributer_food_method` varchar(400) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT 'gbDisid',
  PRIMARY KEY (`gb_distributer_food_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for gb_distributer_food_goods
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_food_goods`;
CREATE TABLE `gb_distributer_food_goods` (
  `gb_distributer_food_goods_id` int NOT NULL AUTO_INCREMENT COMMENT 'shangpid',
  `gb_DFoodG_food_id` int DEFAULT NULL COMMENT '父级id',
  `gb_DFoodG_gb_distributer_id` int DEFAULT NULL COMMENT 'gbDisid',
  `gb_DFoodG_dis_goods_id` int DEFAULT NULL COMMENT '父级id',
  `gb_DFoodG_goods_amount` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '原料数量',
  `gb_DFoodG_status` tinyint DEFAULT NULL COMMENT '父级id',
  PRIMARY KEY (`gb_distributer_food_goods_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for gb_distributer_goods
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_goods`;
CREATE TABLE `gb_distributer_goods` (
  `gb_distributer_goods_id` int NOT NULL AUTO_INCREMENT COMMENT '社区商品id',
  `gb_dg_dfg_goods_father_id` int DEFAULT NULL COMMENT '批发商父类商品id',
  `gb_dg_distributer_id` int DEFAULT NULL COMMENT '批发商id',
  `gb_dg_goods_status` tinyint DEFAULT NULL COMMENT '商品状态',
  `gb_dg_goods_is_weight` tinyint DEFAULT NULL COMMENT '是否称重',
  `gb_dg_goods_name` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '商品名称',
  `gb_dg_goods_detail` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '商品详细',
  `gb_dg_goods_standardname` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '商品规格',
  `gb_dg_goods_pinyin` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '社区商品拼音',
  `gb_dg_goods_py` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '社区商品拼音简拼',
  `gb_dg_nx_goods_id` int DEFAULT NULL COMMENT 'nxGoodsId',
  `gb_dg_nx_father_img` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '进货方式',
  `gb_dg_nx_father_id` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT 'nxGoodsFatherId',
  `gb_dg_nx_grand_id` int DEFAULT NULL COMMENT 'nxGoodsGrandid',
  `gb_dg_nx_great_grand_id` int DEFAULT NULL COMMENT 'nxGreatGrandid',
  `gb_dg_pull_off` tinyint DEFAULT NULL COMMENT '是否下架',
  `gb_dg_goods_brand` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_dg_goods_place` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_dg_nx_goods_father_color` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_dg_goods_standard_weight` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_dg_goods_type` tinyint DEFAULT NULL COMMENT '1 集采 2出库 3 自采',
  `gb_dg_goods_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '价格',
  `gb_dg_goods_lowest_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_dg_goods_highest_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_dg_nx_distributer_id` int DEFAULT NULL COMMENT '商品库父类id',
  `gb_dg_nx_distributer_goods_id` int DEFAULT NULL,
  `gb_dg_gb_department_id` int DEFAULT NULL COMMENT '库房或中央厨房id',
  `gb_dg_control_price` tinyint DEFAULT NULL COMMENT '是否控制价格',
  `gb_dg_control_fresh` tinyint DEFAULT NULL COMMENT '是否控制鲜度',
  `gb_dg_fresh_warn_hour` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '鲜度报警小时',
  `gb_dg_fresh_waste_hour` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '废弃报警小时',
  `gb_dg_goods_inventory_type` tinyint DEFAULT NULL COMMENT '盘库方式1 月，2周，3日',
  `gb_dg_gb_supplier_id` int DEFAULT NULL COMMENT '指定供货商id',
  `gb_dg_franchise_price_one` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_dg_franchise_price_two` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_dg_franchise_price_three` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_dg_franchise_price_one_update` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_dg_franchise_price_two_update` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_dg_franchise_price_three_update` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_dg_is_franchise_price` int DEFAULT NULL COMMENT '加盟商商品',
  `gb_dg_is_self_control` int DEFAULT NULL COMMENT '自制商品',
  `gb_dg_self_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '自制价格',
  `gb_dg_selling_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '销售价格',
  `gb_dg_goods_sort` int DEFAULT NULL COMMENT '商品状态',
  `gb_dg_goods_sons_sort` int DEFAULT NULL COMMENT '子商品顺序',
  `gb_dg_goods_is_hidden` int DEFAULT NULL COMMENT '是否显示',
  `gb_dg_nx_father_img_large` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '进货方式',
  `gb_dg_nx_distributer_goods_price` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_dg_dfg_goods_grand_id` int DEFAULT NULL COMMENT '批发商父类商品id',
  `gb_dg_dfg_goods_great_id` int DEFAULT NULL COMMENT '批发商父类商品id',
  `gb_dg_quantity_days` int DEFAULT NULL COMMENT '批发商父类商品id',
  PRIMARY KEY (`gb_distributer_goods_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=32 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for gb_distributer_goods_price
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_goods_price`;
CREATE TABLE `gb_distributer_goods_price` (
  `gb_distributer_goods_price_id` int NOT NULL AUTO_INCREMENT COMMENT '价格商品id',
  `gb_dgp_dfg_goods_father_id` int DEFAULT NULL COMMENT 'dg父类商品id',
  `gb_dgp_distributer_goods_id` int DEFAULT NULL COMMENT 'dgGoodsId',
  `gb_dgp_distributer_id` tinyint DEFAULT NULL COMMENT 'dg',
  `gb_dgp_goods_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '价格',
  `gb_dgp_goods_lowest_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '最低价格',
  `gb_dgp_goods_highest_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '最高价格',
  `gb_dgp_pur_goods_id` int DEFAULT NULL COMMENT '采购商品id',
  `gb_dgp_pur_user_id` int DEFAULT NULL COMMENT '采购员',
  `gb_dgp_pur_date` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '采购日期',
  `gb_dgp_pur_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '采购价格',
  `gb_dgp_pur_what` tinyint DEFAULT NULL COMMENT '采购价高或低',
  `gb_dgp_pur_scale` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '高或低比例',
  `gb_dgp_pur_what_total` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '高或低差额',
  `gb_dgp_pur_department_id` int DEFAULT NULL COMMENT '采购部门id',
  `gb_dgp_status` tinyint DEFAULT NULL,
  `gb_dgp_week` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_dgp_month` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_dgp_year` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_dgp_dep_settle_id` int DEFAULT NULL,
  `gb_dgp_pur_total` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '高或低差额',
  `gb_dgp_goods_lowest_total` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '高或低差额',
  `gb_dgp_goods_highest_total` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '高或低差额',
  `gb_dgp_pur_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '采购shuliang',
  `gb_dgp_pur_nx_distributer_id` int DEFAULT NULL COMMENT '采购部门id',
  PRIMARY KEY (`gb_distributer_goods_price_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for gb_distributer_goods_shelf
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_goods_shelf`;
CREATE TABLE `gb_distributer_goods_shelf` (
  `gb_distributer_goods_shelf_id` int NOT NULL AUTO_INCREMENT COMMENT '货架id',
  `gb_distributer_goods_shelf_name` varchar(20) DEFAULT NULL COMMENT '货架名称',
  `gb_distributer_goods_shelf_sort` int DEFAULT NULL COMMENT '货架排序',
  `gb_distributer_goods_shelf_dis_id` int DEFAULT NULL COMMENT '批发商id',
  `gb_distributer_goods_shelf_dep_id` int DEFAULT NULL COMMENT '批发商库房id',
  PRIMARY KEY (`gb_distributer_goods_shelf_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COMMENT='用户与角色对应关系';

-- ----------------------------
-- Table structure for gb_distributer_goods_shelf_goods
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
  PRIMARY KEY (`gb_distributer_goods_shelf_goods_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COMMENT='用户与角色对应关系';

-- ----------------------------
-- Table structure for gb_distributer_module
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_module`;
CREATE TABLE `gb_distributer_module` (
  `gb_distributer_module_id` int NOT NULL AUTO_INCREMENT,
  `gb_dm_fixed_supplier_number` int DEFAULT '-1',
  `gb_dm_purchase_number` int DEFAULT '-1',
  `gb_dm_stock_number` int DEFAULT '-1',
  `gb_dm_app_supplier_number` int DEFAULT '-1',
  `gb_dm_central_kitchen_number` int DEFAULT '-1',
  `gb_dm_direct_sales_number` int DEFAULT '-1',
  `gb_dm_franchisee_number` int DEFAULT '-1',
  `gb_dm_distributer_id` int NOT NULL DEFAULT '-1',
  PRIMARY KEY (`gb_distributer_module_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for gb_distributer_pay
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_pay`;
CREATE TABLE `gb_distributer_pay` (
  `gb_distributer_pay_id` int NOT NULL AUTO_INCREMENT,
  `gb_gdp_gb_dis_id` int DEFAULT NULL,
  `gb_gdp_pay_subtotal` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_gdp_from_time` date DEFAULT NULL,
  `gb_gdp_stop_time` date DEFAULT NULL,
  `gb_gdp_pay_time` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_gdp_type` tinyint DEFAULT NULL,
  `gb_gdp_status` tinyint DEFAULT NULL,
  `gb_gdp_trade_no` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_gdp_gb_new_dis_id` int DEFAULT NULL,
  `gb_gdp_buy_quantity` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_gdp_img_url` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_gdp_sell_detail` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`gb_distributer_pay_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for gb_distributer_pay_list
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_pay_list`;
CREATE TABLE `gb_distributer_pay_list` (
  `gb_distributer_pay_list_id` int NOT NULL AUTO_INCREMENT,
  `gb_ndpl_gb_dis_id` int DEFAULT NULL,
  `gb_ndpl_pay_subtotal` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_ndpl_pay_time` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_ndpl_type` tinyint DEFAULT NULL,
  `gb_ndpl_status` tinyint DEFAULT NULL,
  `gb_ndpl_pay_date` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_ndpl_gb_pb_id` int DEFAULT NULL,
  `gb_ndpl_pay_month` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_ndpl_pay_year` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_ndpl_rest_points` varchar(10) COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_ndpl_nx_supplier_id` int DEFAULT NULL,
  `gb_ndpl_gb_department_father_id` int DEFAULT NULL,
  `gb_ndpl_gb_department_id` int DEFAULT NULL,
  `gb_ndpl_gb_dis_goods_id` int DEFAULT NULL,
  PRIMARY KEY (`gb_distributer_pay_list_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for gb_distributer_purchase_batch
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_purchase_batch`;
CREATE TABLE `gb_distributer_purchase_batch` (
  `gb_distributer_purchase_batch_id` int NOT NULL AUTO_INCREMENT COMMENT '批发商进货批次id',
  `gb_DPB_status` tinyint DEFAULT NULL COMMENT '批发商进货批次状态',
  `gb_DPB_user_admin_type` tinyint DEFAULT NULL COMMENT '进货批次用户类型',
  `gb_DPB_time` varchar(12) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '批发商进货批次时间',
  `gb_DPB_pur_user_id` int DEFAULT NULL COMMENT '批发商进货采购员id',
  `gb_DPB_distributer_id` int DEFAULT NULL COMMENT '批发商id',
  `gb_DPB_date` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DPB_hour` varchar(4) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DPB_minute` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DPB_subtotal` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DPB_pur_department_id` int DEFAULT NULL COMMENT '采购部门id',
  `gb_DPB_pay_type` int DEFAULT NULL COMMENT '付款方式:0==现金; 1 ==记账，',
  `gb_DPB_pay_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '付款金额',
  `gb_DPB_supplier_id` int DEFAULT NULL COMMENT '供货商商id',
  `gb_DPB_purchase_month` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DPB_purchase_year` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DPB_purchase_full_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DPB_gb_supplier_payment_id` int DEFAULT NULL,
  `gb_DPB_purchase_week` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DPB_seller_reply_full_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DPB_finish_full_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DPB_nx_distributer_id` int DEFAULT NULL COMMENT '供货商商id',
  `gb_DPB_buy_user_id` int DEFAULT NULL COMMENT '供货商商id',
  `gb_DPB_sell_user_id` int DEFAULT NULL COMMENT '供货商商id',
  `gb_DPB_buy_user_open_id` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DPB_sell_user_open_id` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DPB_purchase_type` tinyint DEFAULT NULL COMMENT '0 手动订货，1 自动订货',
  `gb_DPB_dep_bill_id` int DEFAULT NULL COMMENT '供货商商id',
  PRIMARY KEY (`gb_distributer_purchase_batch_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for gb_distributer_purchase_goods
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_purchase_goods`;
CREATE TABLE `gb_distributer_purchase_goods` (
  `gb_distributer_purchase_goods_id` int NOT NULL AUTO_INCREMENT COMMENT '批发商采购商品id',
  `gb_DPG_dis_goods_id` int DEFAULT NULL COMMENT '采购商品id',
  `gb_DPG_dis_goods_father_id` int DEFAULT NULL COMMENT '采购父级商品id',
  `gb_DPG_quantity` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '采购数量',
  `gb_DPG_standard` varchar(6) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '采购规格',
  `gb_DPG_status` tinyint DEFAULT NULL COMMENT '采购状态',
  `gb_DPG_distributer_id` int DEFAULT NULL COMMENT '采购批发商id',
  `gb_DPG_purchase_type` tinyint DEFAULT NULL COMMENT '采购方式：“1 订单采购”“2 添加采购”',
  `gb_DPG_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '采购时间',
  `gb_DPG_batch_id` int DEFAULT NULL COMMENT '采购批次号',
  `gb_DPG_pur_user_id` int DEFAULT NULL COMMENT '采购方式为“采购”的采购员id',
  `gb_DPG_buy_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '采购单价',
  `gb_DPG_buy_quantity` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '采购数量',
  `gb_DPG_orders_amount` int DEFAULT NULL COMMENT '订单采购的订单数量',
  `gb_DPG_type_add_user_id` int DEFAULT NULL COMMENT '添加采购用户id',
  `gb_DPG_apply_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DPG_purchase_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '采购日期',
  `gb_DPG_input_type` tinyint DEFAULT NULL,
  `gb_DPG_buy_subtotal` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DPG_purchase_department_id` int DEFAULT NULL COMMENT '库房或者中央厨房采购部门id',
  `gb_DPG_purchase_month` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '采购日期',
  `gb_DPG_purchase_year` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '采购日期',
  `gb_DPG_purchase_full_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '采购日期',
  `gb_DPG_dis_goods_price_id` int DEFAULT NULL COMMENT '采购商品价格表id',
  `gb_DPG_purchase_week` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '采购week',
  `gb_DPG_purchase_week_year` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '第几周',
  `gb_DPG_buy_scale_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订货单位价格',
  `gb_DPG_buy_scale_quantity` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订货单位单价',
  `gb_DPG_buy_scale` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订货单位系数',
  `gb_DPG_buy_price_reason` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '采购单价异常原因',
  `gb_DPG_pay_type` tinyint DEFAULT NULL COMMENT '支付方式',
  `gb_DPG_is_check` tinyint DEFAULT NULL COMMENT '支付方式',
  `gb_DPG_waste_full_time` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '废弃时间',
  `gb_DPG_warn_full_time` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '报警时间',
  `gb_DPG_weight_id` int DEFAULT NULL COMMENT '称重disid',
  `gb_DPG_purchase_nx_distributer_id` int DEFAULT NULL COMMENT '库房或者中央厨房采购部门id',
  `gb_DPG_orders_finish_amount` int DEFAULT NULL COMMENT '订单采购的订单数量',
  `gb_DPG_orders_bill_amount` int DEFAULT NULL COMMENT 'bill的订单数量',
  `gb_DPG_purchase_nx_supplier_id` int DEFAULT NULL COMMENT 'jsSupplierId',
  `gb_DPG_dis_goods_grand_id` int DEFAULT NULL,
  `gb_DPG_supplier_finish_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '采购日期',
  `gb_DPG_stock_finish_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '采购日期',
  `gb_DPG_dis_goods_great_id` int DEFAULT NULL,
  `gb_DPG_orders_weight_amount` int DEFAULT NULL COMMENT '订单采购的订单数量',
  PRIMARY KEY (`gb_distributer_purchase_goods_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=88 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for gb_distributer_standard
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_standard`;
CREATE TABLE `gb_distributer_standard` (
  `gb_distributer_standard_id` int NOT NULL AUTO_INCREMENT,
  `gb_DS_dis_goods_id` int DEFAULT NULL,
  `gb_DS_standard_name` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DS_standard_file_path` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DS_standard_scale` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DS_standard_error` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DS_standard_sort` int DEFAULT NULL,
  `gb_DS_standard_weight` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`gb_distributer_standard_id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for gb_distributer_supplier
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_supplier`;
CREATE TABLE `gb_distributer_supplier` (
  `gb_distributer_supplier_id` int NOT NULL AUTO_INCREMENT COMMENT '供货商id',
  `gb_distributer_supplier_name` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '供货商名称',
  `gb_distributer_supplier_father_id` int DEFAULT NULL COMMENT '父级id',
  `gb_DS_gb_distributer_id` int DEFAULT NULL COMMENT 'gbDisid',
  `gb_DS_gb_department_id` int DEFAULT NULL COMMENT 'gbDepid',
  `gb_DS_suppplier_is_group` tinyint DEFAULT NULL COMMENT '总部供货商1，门店自采2，',
  `gb_DS_order_type` tinyint DEFAULT NULL COMMENT '订单类型',
  `gb_DS_supplier_user_id` int DEFAULT NULL COMMENT '接单元id',
  `gb_DS_pur_user_id` int DEFAULT NULL COMMENT '采购员id',
  PRIMARY KEY (`gb_distributer_supplier_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for gb_distributer_supplier_payment
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_supplier_payment`;
CREATE TABLE `gb_distributer_supplier_payment` (
  `gb_distributer_supplier_payment_id` int NOT NULL AUTO_INCREMENT COMMENT '供货商id',
  `gb_dsp_date` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '供货商名称',
  `gb_dsp_supplier_id` int DEFAULT NULL COMMENT '父级id',
  `gb_dsp_pay_user_id` int DEFAULT NULL COMMENT 'gbDisid',
  `gb_dsp_pay_total` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT 'gbDepid',
  `gb_dsp_distributer_id` int DEFAULT NULL COMMENT 'gbDisid',
  `gb_dsp_nx_distributer_id` int DEFAULT NULL COMMENT '配送商id',
  `gb_dsp_wx_out_trade_no` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '配送商id',
  `gb_dsp_status` int DEFAULT NULL COMMENT '配送商id',
  `gb_dsp_pay_full_time` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '供货商名称',
  PRIMARY KEY (`gb_distributer_supplier_payment_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=23 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for gb_distributer_supplier_user
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_supplier_user`;
CREATE TABLE `gb_distributer_supplier_user` (
  `gb_distributer_supplier_user_id` int NOT NULL AUTO_INCREMENT COMMENT '供货商用户id',
  `gb_DSU_department_id` int DEFAULT NULL COMMENT '订货部门id',
  `gb_DSU_wx_avartra_url` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订货部门用户微信头像',
  `gb_DSU_wx_nick_name` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订货部门用户微信昵称',
  `gb_DSU_wx_open_id` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订货部门用户微信openid',
  `gb_DSU_wx_phone` varchar(15) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订货部门用户微信手机号码',
  `gb_DSU_admin` tinyint(1) DEFAULT NULL COMMENT '0 指定供货商用户 1 转发微信用户',
  `gb_DSU_distributer_id` int DEFAULT NULL COMMENT '订货部门批发商id',
  `gb_DSU_url_change` tinyint(1) DEFAULT NULL,
  `gb_DSU_department_father_id` int DEFAULT NULL,
  `gb_DSU_join_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DSU_print_device_id` varchar(40) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DSU_print_bill_device_id` varchar(40) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DSU_supplier_id` int DEFAULT NULL COMMENT '订货部门id',
  PRIMARY KEY (`gb_distributer_supplier_user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for gb_distributer_user
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_user`;
CREATE TABLE `gb_distributer_user` (
  `gb_distributer_user_id` int NOT NULL AUTO_INCREMENT COMMENT '批发商用户id',
  `gb_DIU_wx_avartra_url` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '用户名',
  `gb_DIU_wx_nick_name` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '登陆密码',
  `gb_DIU_wx_open_id` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DIU_wx_phone` varchar(15) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DIU_distributer_id` int DEFAULT NULL,
  `gb_DIU_admin` tinyint DEFAULT NULL,
  `gb_DIU_print_device_id` varchar(40) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DIU_url_change` tinyint DEFAULT NULL,
  `gb_DIU_print_bill_device_id` varchar(40) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_DIU_qy_corp_user_id` int DEFAULT NULL COMMENT '企业用户id',
  `gb_DIU_login_times` int DEFAULT NULL COMMENT '企业用户id',
  PRIMARY KEY (`gb_distributer_user_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for gb_distributer_weight_goods
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_weight_goods`;
CREATE TABLE `gb_distributer_weight_goods` (
  `gb_distributer_weight_goods_id` int NOT NULL AUTO_INCREMENT COMMENT '称重单id',
  `gb_dwg_weight_id` int DEFAULT NULL COMMENT '称重disid',
  `gb_dwg_dep_dis_goods_id` int DEFAULT NULL COMMENT '称重单总重量',
  `gb_dwg_prepare_weight` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '称重单总日期',
  `gb_dwg_dis_goods_id` int DEFAULT NULL COMMENT '称重单总重量',
  `gb_dwg_order_amount` int DEFAULT NULL COMMENT '订单数量',
  `gb_dwg_status` tinyint DEFAULT NULL COMMENT '状态',
  `gb_dwg_save_user_id` int DEFAULT NULL COMMENT '员工',
  `gb_dwg_dep_id` int DEFAULT NULL COMMENT '员工',
  `gb_dwg_dep_father_id` int DEFAULT NULL COMMENT '员工',
  `gb_dwg_order_finish_amount` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订单数量',
  `gb_dwg_date` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '员工',
  `gb_dwg_loss_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '员工',
  `gb_dwg_waste_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '员工',
  `gb_dwg_return_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '员工',
  PRIMARY KEY (`gb_distributer_weight_goods_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for gb_distributer_weight_total
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_weight_total`;
CREATE TABLE `gb_distributer_weight_total` (
  `gb_distributer_weight_total_id` int NOT NULL AUTO_INCREMENT COMMENT '称重单id',
  `gb_gwt_user_id` int DEFAULT NULL COMMENT '称重用户id',
  `gb_gwt_dis_id` int DEFAULT NULL COMMENT '称重disid',
  `gb_gwt_weight_total` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '称重单总重量',
  `gb_gwt_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '称重单总日期',
  `gb_gwt_subtotal` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '称重单总金额',
  `gb_gwt_status` tinyint DEFAULT NULL COMMENT '称重单状态',
  `gb_gwt_order_names` varchar(2000) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '称重单总金额',
  `gb_gwt_dep_father_id` int DEFAULT NULL COMMENT '称重disid',
  `gb_gwt_trade_no` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '称重单号',
  `gb_gwt_type` tinyint DEFAULT NULL COMMENT '1 出库单 2 采购单',
  `gb_gwt_is_self` tinyint DEFAULT NULL COMMENT '0 进货 1 自制',
  `gb_gwt_order_count` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订单数量',
  `gb_gwt_order_finish_count` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订单完成数量',
  `gb_gwt_print_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '称重单总日期',
  PRIMARY KEY (`gb_distributer_weight_total_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for gb_report
-- ----------------------------
DROP TABLE IF EXISTS `gb_report`;
CREATE TABLE `gb_report` (
  `gb_report_id` int NOT NULL AUTO_INCREMENT,
  `gb_rep_ids` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_rep_type` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_rep_dis_user_id` int DEFAULT NULL,
  `gb_rep_start_date` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `gb_rep_stop_date` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`gb_report_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for gb_route
-- ----------------------------
DROP TABLE IF EXISTS `gb_route`;
CREATE TABLE `gb_route` (
  `gb_route_id` int NOT NULL AUTO_INCREMENT COMMENT '线路id',
  `gb_route_name` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '线路名称',
  `gb_route_dis_id` int DEFAULT NULL,
  PRIMARY KEY (`gb_route_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for msgaudit_key_backup
-- ----------------------------
DROP TABLE IF EXISTS `msgaudit_key_backup`;
CREATE TABLE `msgaudit_key_backup` (
  `qy_gb_dis_qy_corp_id` varchar(100) COLLATE utf16_czech_ci DEFAULT NULL COMMENT '企业微信企业ID',
  `qy_gb_dis_corp_msgaudit_private_key` text COLLATE utf16_czech_ci COMMENT '会话存档私钥',
  `backup_time` datetime NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_adsense
-- ----------------------------
DROP TABLE IF EXISTS `nx_adsense`;
CREATE TABLE `nx_adsense` (
  `nx_adsense_id` int NOT NULL AUTO_INCREMENT COMMENT '广告位id',
  `nx_adsense_file_path` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '广告位图片',
  `nx_adsense_click` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '广告位链接',
  `nx_adsense_community_id` int DEFAULT NULL COMMENT '社区id',
  `nx_adsense_sort` int DEFAULT NULL COMMENT '广告位排序',
  PRIMARY KEY (`nx_adsense_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_agent
-- ----------------------------
DROP TABLE IF EXISTS `nx_agent`;
CREATE TABLE `nx_agent` (
  `agent_id` int NOT NULL AUTO_INCREMENT COMMENT '批发商用户id',
  `agent_name` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '送货员名字',
  `agent_dis_id` int DEFAULT NULL COMMENT '批发商id',
  PRIMARY KEY (`agent_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_agent_user
-- ----------------------------
DROP TABLE IF EXISTS `nx_agent_user`;
CREATE TABLE `nx_agent_user` (
  `nx_agent_user_id` int NOT NULL AUTO_INCREMENT COMMENT '代理商用户id',
  `AU_wx_nick_name` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '代理商用户微信昵称',
  `AU_wx_avartra_url` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '代理商用户微信头像',
  `AU_wx_agender` varchar(4) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '代理商用户微信性别',
  `AU_name` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '代理商用户姓名',
  `AU_phone` varchar(11) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '代理商用户手机号码',
  PRIMARY KEY (`nx_agent_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_ai_forecast_log
-- ----------------------------
DROP TABLE IF EXISTS `nx_ai_forecast_log`;
CREATE TABLE `nx_ai_forecast_log` (
  `nx_ai_forecast_log_id` int NOT NULL AUTO_INCREMENT,
  `nx_department_id` int NOT NULL,
  `nx_dis_goods_id` int NOT NULL,
  `nx_forecast_date` varchar(10) COLLATE utf16_czech_ci NOT NULL,
  `nx_predicted_quantity` decimal(10,2) DEFAULT NULL,
  `nx_actual_quantity` decimal(10,2) DEFAULT NULL,
  `nx_day_of_week` int DEFAULT NULL,
  `nx_lag1` decimal(10,2) DEFAULT NULL,
  `nx_lag2` decimal(10,2) DEFAULT NULL,
  `nx_lag3` decimal(10,2) DEFAULT NULL,
  `nx_created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `nx_lag1_dow` int DEFAULT NULL,
  `nx_current_dow` int DEFAULT NULL,
  `nx_lag2_dow` int DEFAULT NULL,
  `nx_lag3_dow` int DEFAULT NULL,
  `nx_ai_apply_quantity` decimal(10,2) DEFAULT NULL,
  `nx_ai_apply_standard` varchar(10) COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_ai_error` decimal(10,2) DEFAULT NULL,
  `nx_ai_error_pct` decimal(10,2) DEFAULT NULL,
  PRIMARY KEY (`nx_ai_forecast_log_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_alert_record
-- ----------------------------
DROP TABLE IF EXISTS `nx_alert_record`;
CREATE TABLE `nx_alert_record` (
  `nx_ar_id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `nx_ar_device_id` int NOT NULL COMMENT '设备ID（外键→nx_printer_device）',
  `nx_ar_manager_id` int NOT NULL COMMENT '接收管理员ID（外键→nx_market_manager）',
  `nx_ar_alert_level` tinyint NOT NULL COMMENT '提醒级别（1-4）',
  `nx_ar_paper_count` int NOT NULL COMMENT '触发时的纸张数量',
  `nx_ar_message` varchar(500) DEFAULT NULL COMMENT '提醒内容',
  `nx_ar_send_status` tinyint DEFAULT '2' COMMENT '发送状态（0-失败 1-成功 2-待发送）',
  `nx_ar_send_time` datetime DEFAULT NULL COMMENT '发送时间',
  `nx_ar_is_cleared` tinyint DEFAULT '0' COMMENT '是否已清除（0-未清除 1-已清除）【防重复关键字段】',
  `nx_ar_clear_time` datetime DEFAULT NULL COMMENT '清除时间（加纸后）',
  `nx_ar_is_read` tinyint DEFAULT '0' COMMENT '是否已读（0-未读 1-已读）',
  `nx_ar_read_time` datetime DEFAULT NULL COMMENT '已读时间',
  `nx_ar_create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`nx_ar_id`),
  KEY `idx_device_id` (`nx_ar_device_id`),
  KEY `idx_manager_id` (`nx_ar_manager_id`),
  KEY `idx_cleared` (`nx_ar_is_cleared`,`nx_ar_alert_level`,`nx_ar_device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='提醒记录表';

-- ----------------------------
-- Table structure for nx_alert_threshold
-- ----------------------------
DROP TABLE IF EXISTS `nx_alert_threshold`;
CREATE TABLE `nx_alert_threshold` (
  `nx_at_id` int NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  `nx_at_device_id` int NOT NULL COMMENT '设备ID（外键→nx_printer_device）',
  `nx_at_level` tinyint NOT NULL COMMENT '提醒级别（1-低 2-中 3-高 4-紧急）',
  `nx_at_threshold` int NOT NULL COMMENT '阈值（剩余张数）',
  `nx_at_message` varchar(200) DEFAULT NULL COMMENT '提醒消息模板',
  `nx_at_enable` tinyint DEFAULT '1' COMMENT '是否启用（0-否 1-是）',
  `nx_at_create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`nx_at_id`),
  UNIQUE KEY `uk_device_level` (`nx_at_device_id`,`nx_at_level`),
  KEY `idx_device_id` (`nx_at_device_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='提醒阈值配置表';

-- ----------------------------
-- Table structure for nx_alias
-- ----------------------------
DROP TABLE IF EXISTS `nx_alias`;
CREATE TABLE `nx_alias` (
  `nx_alias_id` int NOT NULL AUTO_INCREMENT COMMENT '别名id',
  `nx_alias_name` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '别名名称',
  `nx_als_goods_id` int DEFAULT NULL COMMENT '别名商品id',
  `nx_als_sort` int DEFAULT NULL COMMENT '别名排序',
  `nx_alias_pinyin` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '别名名称',
  `nx_alias_py` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '别名名称',
  PRIMARY KEY (`nx_alias_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=123 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_buy_user
-- ----------------------------
DROP TABLE IF EXISTS `nx_buy_user`;
CREATE TABLE `nx_buy_user` (
  `nx_buy_user_id` int NOT NULL AUTO_INCREMENT COMMENT '订货用户id',
  `nx_BU_retailer_id` int DEFAULT NULL COMMENT '零售商id',
  `nx_BU_wx_avartra_url` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订货用户微信头像',
  `nx_BU_wx_nick_name` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订货用户微信昵称',
  `nx_BU_wx_open_id` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订货用户微信openid',
  `nx_BU_wx_phone` varchar(15) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订货户微信手机号码',
  `nx_BU_join_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '零售商用户加入日期',
  `nx_BU_nx_distributer_id` int DEFAULT NULL COMMENT '批发商id',
  `nx_BU_nx_dis_user_id` int DEFAULT NULL COMMENT '批发商用户id',
  PRIMARY KEY (`nx_buy_user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_community
-- ----------------------------
DROP TABLE IF EXISTS `nx_community`;
CREATE TABLE `nx_community` (
  `nx_community_id` int NOT NULL AUTO_INCREMENT,
  `nx_community_name` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_community_lat` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_community_lng` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_community_route_id` int DEFAULT NULL,
  `nx_community_commerce_id` int DEFAULT NULL,
  `nx_community_polygon` varchar(10000) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_community_region` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_community_delivery_address` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_community_type` int DEFAULT NULL,
  `nx_community_wx_file` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_community_open_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_community_close_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_community_door_file` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_community_pre_print_times` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_community_delivery_mix_subtotal` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_community_delivery_fee_free_distance` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_community_delivery_pay_percent` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_community_delivery_pay_max_fee` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_community_sys_city_id` int DEFAULT NULL,
  `nx_community_sys_business_area_id` int DEFAULT NULL,
  `nx_community_sys_district_id` int DEFAULT NULL,
  `nx_community_sys_province_id` int DEFAULT NULL,
  `nx_community_location` point DEFAULT NULL,
  `nx_community_bill_print_sn` varchar(200) COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_community_business_phone` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_community_delivery_day_offset` int DEFAULT '1',
  `nx_community_delivery_start_time` int DEFAULT '720',
  `nx_community_delivery_stop_time` int DEFAULT '1080',
  PRIMARY KEY (`nx_community_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_community_adsense
-- ----------------------------
DROP TABLE IF EXISTS `nx_community_adsense`;
CREATE TABLE `nx_community_adsense` (
  `nx_community_adsense_id` int NOT NULL AUTO_INCREMENT COMMENT '广告位id',
  `nx_CA_file_path` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '广告位图片',
  `nx_CA_click_to` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '广告位链接',
  `nx_CA_community_id` int DEFAULT NULL COMMENT '社区id',
  `nx_CA_sort` int DEFAULT NULL COMMENT '广告位排序',
  `nx_community_adsense_name` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '广告位图片',
  `nx_CA_status` tinyint DEFAULT NULL COMMENT '广告位排序',
  `nx_CA_cg_goods_id` int DEFAULT NULL COMMENT '社区id',
  `nx_CA_start_time_zone` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '是否是套餐',
  `nx_CA_stop_time_zone` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '是否是套餐',
  `nx_CA_start_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '是否是套餐',
  `nx_CA_stop_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '是否是套餐',
  PRIMARY KEY (`nx_community_adsense_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_community_agent
-- ----------------------------
DROP TABLE IF EXISTS `nx_community_agent`;
CREATE TABLE `nx_community_agent` (
  `nx_CA_id` int NOT NULL AUTO_INCREMENT,
  `CA_community_id` int DEFAULT NULL,
  `CA_angent_id` int DEFAULT NULL,
  PRIMARY KEY (`nx_CA_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_community_alias
-- ----------------------------
DROP TABLE IF EXISTS `nx_community_alias`;
CREATE TABLE `nx_community_alias` (
  `nx_community_alias_id` int NOT NULL AUTO_INCREMENT,
  `nx_CA_com_goods_id` int DEFAULT NULL,
  `nx_CA_alias_name` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`nx_community_alias_id`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_community_card
-- ----------------------------
DROP TABLE IF EXISTS `nx_community_card`;
CREATE TABLE `nx_community_card` (
  `nx_community_card_id` int NOT NULL AUTO_INCREMENT,
  `nx_community_card_name` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cc_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cc_words` varchar(1000) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cc_file_path` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cc_community_id` int DEFAULT NULL,
  `nx_cc_type` int DEFAULT NULL,
  `nx_cc_status` int DEFAULT NULL,
  `nx_cc_effective_days` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cc_user_count` int DEFAULT NULL,
  PRIMARY KEY (`nx_community_card_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_community_coupon
-- ----------------------------
DROP TABLE IF EXISTS `nx_community_coupon`;
CREATE TABLE `nx_community_coupon` (
  `nx_community_coupon_id` int NOT NULL AUTO_INCREMENT,
  `nx_community_coupon_name` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cp_cg_goods_id` int DEFAULT NULL,
  `nx_cp_original_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cp_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cp_standard` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cp_quantity` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cp_stop_time` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cp_start_time` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cp_words` varchar(1000) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cp_recommand_goods` varchar(1000) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cp_file_path` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cp_community_id` int DEFAULT NULL,
  `nx_promote_cg_father_id` int DEFAULT NULL,
  `nx_cp_type` int DEFAULT NULL,
  `nx_cp_status` int DEFAULT NULL,
  `nx_cp_start_time_zone` datetime(6) DEFAULT NULL,
  `nx_cp_stop_time_zone` datetime(6) DEFAULT NULL,
  `nx_cp_start_date` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cp_stop_date` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cp_down_count` int DEFAULT NULL,
  `nx_cp_use_count` int DEFAULT NULL,
  PRIMARY KEY (`nx_community_coupon_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_community_daytime
-- ----------------------------
DROP TABLE IF EXISTS `nx_community_daytime`;
CREATE TABLE `nx_community_daytime` (
  `nx_week_id` int NOT NULL AUTO_INCREMENT,
  `nx_day_name` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_day_open` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_day_close` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dis_id` int DEFAULT NULL,
  PRIMARY KEY (`nx_week_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_community_desk
-- ----------------------------
DROP TABLE IF EXISTS `nx_community_desk`;
CREATE TABLE `nx_community_desk` (
  `nx_community_desk_id` int NOT NULL AUTO_INCREMENT,
  `nx_community_desk_name` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cd_area_id` int DEFAULT NULL,
  `nx_cd_chair_num` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cd_community_id` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cd_type` int DEFAULT NULL,
  `nx_cd_status` int DEFAULT NULL,
  PRIMARY KEY (`nx_community_desk_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_community_father_goods
-- ----------------------------
DROP TABLE IF EXISTS `nx_community_father_goods`;
CREATE TABLE `nx_community_father_goods` (
  `nx_community_father_goods_id` int NOT NULL AUTO_INCREMENT,
  `nx_cfg_father_goods_name` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cfg_father_goods_img` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cfg_father_goods_sort` int DEFAULT NULL,
  `nx_cfg_father_goods_color` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cfg_fathers_father_id` int DEFAULT NULL,
  `nx_cfg_father_goods_level` tinyint DEFAULT NULL,
  `nx_cfg_community_id` int DEFAULT NULL,
  `nx_cfg_goods_amount` int DEFAULT NULL,
  `nx_cfg_nx_goods_id` int DEFAULT NULL,
  `nx_cfg_price_amount` int DEFAULT NULL,
  `nx_cfg_price_two_amount` int DEFAULT NULL,
  `nx_cfg_price_three_amount` int DEFAULT NULL,
  `nx_cfg_order_rank` tinyint DEFAULT NULL,
  PRIMARY KEY (`nx_community_father_goods_id`)
) ENGINE=InnoDB AUTO_INCREMENT=77 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_community_goods
-- ----------------------------
DROP TABLE IF EXISTS `nx_community_goods`;
CREATE TABLE `nx_community_goods` (
  `nx_community_goods_id` int NOT NULL AUTO_INCREMENT COMMENT '社区商品id',
  `nx_cg_cfg_goods_father_id` int DEFAULT NULL COMMENT '社区商品父类id',
  `nx_cg_commerce_id` int DEFAULT NULL COMMENT '平台id',
  `nx_cg_community_id` int DEFAULT NULL COMMENT '社区id',
  `nx_cg_goods_status` tinyint DEFAULT NULL COMMENT '商品状态',
  `nx_cg_goods_is_weight` tinyint DEFAULT NULL COMMENT '是否称重',
  `nx_cg_goods_name` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '商品名称',
  `nx_cg_goods_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '价格',
  `nx_cg_goods_two_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cg_goods_three_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cg_goods_price_integer` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cg_goods_price_decimal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '价格小数点部分',
  `nx_cg_nx_goods_file_path` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '商品图片',
  `nx_cg_nx_goods_id` int DEFAULT NULL COMMENT '购买热度',
  `nx_cg_nx_father_id` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '采购数量',
  `nx_cg_nx_father_img` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '进货方式',
  `nx_cg_nx_grand_id` int DEFAULT NULL,
  `nx_cg_nx_great_grand_id` int DEFAULT NULL,
  `nx_cg_goods_total_hits` int DEFAULT NULL COMMENT '自采购员工id',
  `nx_cg_goods_type` tinyint DEFAULT NULL COMMENT '0 供货商，1 社区库存，2 自采购 4 加工',
  `nx_cg_goods_standard_type` tinyint DEFAULT NULL COMMENT '订货规格0按商品规格. 1 按订货规格',
  `nx_cg_goods_buy_type` tinyint DEFAULT NULL COMMENT '自采购商品的状态',
  `nx_cg_sell_type` tinyint DEFAULT NULL COMMENT '0 按规格出货，1按子菜品数量出货；3，按套餐属性出货',
  `nx_cg_goods_stock` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '社区库存商品库存量',
  `nx_cg_purchase_quantity` int DEFAULT NULL COMMENT 'App订货供货商appid',
  `nx_cg_buy_purchase_user_id` int DEFAULT NULL COMMENT '供货商id',
  `nx_cg_buy_app_id` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '进价',
  `nx_cg_buy_status` int DEFAULT NULL COMMENT '商品id',
  `nx_cg_distributer_id` int DEFAULT NULL COMMENT '商品库父类id',
  `nx_cg_distributer_goods_id` int DEFAULT NULL,
  `nx_cg_buying_price` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '商品库image',
  `nx_cg_goods_detail` varchar(1000) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '商品详细',
  `nx_cg_goods_standardname` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '商品规格',
  `nx_cg_goods_pinyin` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '社区商品拼音',
  `nx_cg_goods_py` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '社区商品拼音简拼',
  `nx_cg_goods_brand` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '商品品牌',
  `nx_cg_goods_place` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cg_nx_goods_father_color` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cg_goods_standard_weight` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cg_pull_off` tinyint DEFAULT NULL,
  `nx_cg_expect_gross_profit` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cg_reality_gross_profit` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cg_goods_price_exchange` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cg_goods_two_price_exchange` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cg_goods_three_price_exchange` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cg_goods_price_exchange_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cg_goods_two_price_exchange_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cg_goods_three_price_exchange_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cg_buying_price_exchange` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cg_buying_price_exchange_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cg_community_supplier_id` int DEFAULT NULL COMMENT '商品库父类id',
  `nx_cg_goods_huaxian_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cg_goods_huaxian_price_different` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cg_is_set_menu` int DEFAULT NULL COMMENT '是否是套餐',
  `nx_cg_set_sub_number` int DEFAULT NULL COMMENT '套餐子商品数量',
  `nx_cg_goods_huaxian_quantity` int DEFAULT NULL,
  `nx_cg_goods_sort` int DEFAULT NULL,
  `nx_cg_start_time_zone` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cg_stop_time_zone` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cg_start_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cg_stop_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cg_card_id` int DEFAULT NULL,
  `nx_cg_nx_goods_top_file_path` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '商品图片',
  `nx_cg_goods_intro_video` varchar(1024) COLLATE utf16_czech_ci DEFAULT NULL COMMENT '介绍视频相对路径',
  `nx_cg_is_open_adsense` int DEFAULT NULL COMMENT '是否是套餐',
  `nx_cg_adsense_start_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '是否是套餐',
  `nx_cg_adsense_stop_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '是否是套餐',
  `nx_cg_adsense_stock_quantity` int DEFAULT NULL COMMENT '是否是套餐',
  `nx_cg_adsense_rest_quantity` int DEFAULT NULL COMMENT '是否是套餐',
  `nx_cg_adsense_start_time_zone` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '是否是套餐',
  `nx_cg_adsense_stop_time_zone` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '是否是套餐',
  `nx_cg_is_show_adsense` tinyint DEFAULT NULL,
  `nx_cg_promotion_type` tinyint DEFAULT NULL COMMENT '0 没有，1，划线；2满减；3 拼单',
  `nx_cg_promotion_amount` int DEFAULT NULL COMMENT '促销份数',
  `nx_cg_promotion_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '促销价格',
  `nx_cg_promotion_words` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '促销文字',
  `nx_cg_set_sub_limit_number` int DEFAULT NULL COMMENT '套餐子商品数量',
  `nx_cg_service_type` tinyint DEFAULT NULL COMMENT '0自提，1 外卖',
  `nx_cg_print_sn` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '打印机 sn',
  `nx_cg_goods_gross_weight` decimal(10,2) DEFAULT NULL COMMENT '毛重',
  `nx_cg_goods_gross_price` decimal(10,2) DEFAULT NULL COMMENT '毛单价',
  `nx_cg_goods_net_weight` decimal(10,2) DEFAULT NULL COMMENT '净重',
  `nx_cg_goods_net_price` decimal(10,2) DEFAULT NULL COMMENT '净单价',
  PRIMARY KEY (`nx_community_goods_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=103 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_community_goods_set_item
-- ----------------------------
DROP TABLE IF EXISTS `nx_community_goods_set_item`;
CREATE TABLE `nx_community_goods_set_item` (
  `nx_community_goods_set_item_id` int NOT NULL AUTO_INCREMENT,
  `nx_cgsi_item_name` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cgsi_item_cg_goods_id` int DEFAULT NULL,
  `nx_cgsi_cg_property_id` int DEFAULT NULL,
  `nx_cgsi_item_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cgsi_item_file_path` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cgsi_item_type` int DEFAULT NULL COMMENT '0 不可替换，1 可以替换',
  `nx_cgsi_item_quantity` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cgsi_item_sort` int DEFAULT NULL,
  `nx_cgsi_item_status` int DEFAULT NULL,
  `nx_cgsi_item_huaxian_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cgsi_item_limit_count` int DEFAULT NULL,
  `nx_cgsi_cg_goods_id` int DEFAULT NULL,
  PRIMARY KEY (`nx_community_goods_set_item_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=142 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_community_goods_set_property
-- ----------------------------
DROP TABLE IF EXISTS `nx_community_goods_set_property`;
CREATE TABLE `nx_community_goods_set_property` (
  `nx_community_goods_set_property_id` int NOT NULL AUTO_INCREMENT,
  `nx_cgsp_name` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cgsp_sort` int DEFAULT NULL,
  `nx_cgsp_cg_goods_id` int DEFAULT NULL,
  `nx_cgsp_limit_amount` int DEFAULT NULL,
  PRIMARY KEY (`nx_community_goods_set_property_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=29 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_community_orders
-- ----------------------------
DROP TABLE IF EXISTS `nx_community_orders`;
CREATE TABLE `nx_community_orders` (
  `nx_community_orders_id` int NOT NULL AUTO_INCREMENT COMMENT '订单id',
  `nx_CO_distributer_id` int DEFAULT NULL COMMENT '批发商id',
  `nx_CO_community_id` int DEFAULT NULL COMMENT '订单社区id',
  `nx_CO_customer_id` int DEFAULT NULL COMMENT '订单客户id',
  `nx_CO_user_id` int DEFAULT NULL COMMENT '订单用户id',
  `nx_CO_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订单日期',
  `nx_CO_status` tinyint DEFAULT NULL COMMENT '订单状态',
  `nx_CO_service` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订单送达时间',
  `nx_CO_amount` float(10,0) DEFAULT NULL COMMENT '订单总金额',
  `nx_CO_service_date` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订单送到日期',
  `nx_CO_service_time` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订单送到时间',
  `nx_CO_weigh_user_id` int DEFAULT NULL COMMENT '订单称重用户id',
  `nx_CO_delivery_user_id` int DEFAULT NULL COMMENT '订单配送员工id',
  `nx_CO_sub_amount` int DEFAULT NULL COMMENT '订单子商品数量',
  `nx_CO_sub_finished` int DEFAULT NULL COMMENT '订单子商品完成数量',
  `nx_CO_weigh_number` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订单称重订单号',
  `nx_CO_payment_status` tinyint DEFAULT NULL COMMENT '订单支付状态',
  `nx_CO_payment_send_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订单支付发送时间',
  `nx_CO_payment_time` varchar(0) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订单支付时间',
  `nx_CO_type` tinyint DEFAULT NULL COMMENT '订单类型 0先付款1后付款',
  `nx_CO_wx_out_trade_no` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订单支付时间',
  `nx_Co_total` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订单类型 0先付款1后付款',
  `nx_Co_youhui_total` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '优惠总额',
  `nx_CO_service_hour` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订单送到时间',
  `nx_CO_service_minute` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订单送到时间',
  `nx_CO_buy_member_card_time` int DEFAULT NULL COMMENT '订单送到时间',
  `nx_CO_buy_member_card_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订单送到时间',
  `nx_CO_commerce_id` int DEFAULT NULL COMMENT '订单社区id',
  `nx_CO_service_type` tinyint DEFAULT NULL COMMENT '0 自提订单 1 外卖订单',
  `nx_CO_delivery_address_id` int DEFAULT NULL COMMENT '外卖地址 id',
  `nx_CO_delivery_fee` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '外送费',
  `nx_CO_desk_id` int DEFAULT NULL COMMENT '桌号 id',
  PRIMARY KEY (`nx_community_orders_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=49 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_community_orders_sub
-- ----------------------------
DROP TABLE IF EXISTS `nx_community_orders_sub`;
CREATE TABLE `nx_community_orders_sub` (
  `nx_community_orders_sub_id` int NOT NULL AUTO_INCREMENT COMMENT '子订单id',
  `nx_COS_orders_id` int DEFAULT NULL COMMENT '订单id',
  `nx_COS_nx_goods_id` int DEFAULT NULL COMMENT '子订单nx商品id',
  `nx_COS_community_goods_id` int DEFAULT NULL COMMENT '子订单社区商品id',
  `nx_COS_community_goods_father_id` int DEFAULT NULL COMMENT '子订单商品父id',
  `nx_COS_quantity` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '子订单申请数量',
  `nx_COS_standard` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '子订单申请规格',
  `nx_COS_price` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '子订单申请商品单价',
  `nx_COS_remark` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '子订单申请备注',
  `nx_COS_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '子订单申请商品称重',
  `nx_COS_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '子订单申请商品小计',
  `nx_COS_status` tinyint DEFAULT NULL COMMENT '子订单申请商品状态',
  `nx_COS_weigh_user_id` int DEFAULT NULL COMMENT '子订单商品称重用户id',
  `nx_COS_account_user_id` int DEFAULT NULL COMMENT '子订单商品输入单价用户id',
  `nx_COS_purchase_user_id` int DEFAULT NULL COMMENT '子商品采购元id',
  `nx_COS_distributer_id` int DEFAULT NULL COMMENT '子订单批发商id',
  `nx_COS_buy_status` tinyint DEFAULT NULL COMMENT '子订单商品进货状态',
  `nx_COS_order_user_id` int DEFAULT NULL COMMENT '子订单订货用户id',
  `nx_COS_sub_weight` varchar(4) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '子订单重量',
  `nx_COS_sub_supplier_id` int DEFAULT NULL COMMENT '子订单商品供货商id',
  `nx_COS_community_id` int DEFAULT NULL COMMENT '子订单社区id',
  `nx_COS_goods_type` tinyint DEFAULT NULL COMMENT '子订单社区商品类型',
  `nx_COS_type` tinyint DEFAULT NULL COMMENT '0 非会员订单，1 会员订单',
  `nx_COS_huaxian_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '子订单申请商品单价',
  `nx_COS_huaxian_different_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '子订单申请商品单价',
  `nx_COS_goods_index` int DEFAULT NULL COMMENT '子订单社区商品id',
  `nx_COS_pick_up_code` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '子订单社区商品id',
  `nx_COS_service` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订单送达时间',
  `nx_COS_service_date` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订单送到日期',
  `nx_COS_service_time` int DEFAULT NULL COMMENT '订单送到时间',
  `nx_COS_print_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订单送到时间',
  `nx_COS_print_log` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订单送到时间',
  `nx_COS_cuc_id` int DEFAULT NULL COMMENT '用户优惠卷 id',
  `nx_COS_huaxian_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '子订单申请商品单价',
  `nx_COS_splicing_orders_id` int DEFAULT NULL COMMENT '拼单id',
  `nx_COS_commerce_id` int DEFAULT NULL COMMENT '子订单社区id',
  `nx_COS_service_type` tinyint DEFAULT NULL COMMENT '0 自提订单 1 外卖订单',
  `nx_COS_desk_id` int DEFAULT NULL COMMENT '子订单社区id',
  `nx_COS_comm_pur_goods_id` int DEFAULT NULL COMMENT '采购商品 id',
  PRIMARY KEY (`nx_community_orders_sub_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=148 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_community_promote
-- ----------------------------
DROP TABLE IF EXISTS `nx_community_promote`;
CREATE TABLE `nx_community_promote` (
  `nx_promote_id` int NOT NULL AUTO_INCREMENT,
  `nx_promote_cg_id` int DEFAULT NULL,
  `nx_orignal_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_promote_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_promote_standard` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_promote_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_promote_expired` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_promote_storage` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_promote_words` varchar(1000) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_promote_recommand_goods` varchar(1000) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_promote_file_path` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_promote_community_id` int DEFAULT NULL,
  `nx_promote_cg_father_id` int DEFAULT NULL,
  PRIMARY KEY (`nx_promote_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_community_purchase_batch
-- ----------------------------
DROP TABLE IF EXISTS `nx_community_purchase_batch`;
CREATE TABLE `nx_community_purchase_batch` (
  `nx_community_purchase_batch_id` int NOT NULL AUTO_INCREMENT COMMENT '区域商进货批次id',
  `nx_cpb_status` tinyint DEFAULT NULL COMMENT '区域商进货批次状态',
  `nx_cpb_purchase_type` tinyint DEFAULT NULL COMMENT '区域商复制=2，打印=1',
  `nx_cpb_time` varchar(12) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '区域商进货批次时间',
  `nx_cpb_pur_user_id` int DEFAULT NULL COMMENT '区域商进货采购员id',
  `nx_cpb_community_id` int DEFAULT NULL COMMENT '区域商id',
  `nx_cpb_date` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '日期',
  `nx_cpb_hour` varchar(4) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '时间',
  `nx_cpb_minute` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '分钟',
  `nx_cpb_distributer_id` int DEFAULT NULL COMMENT '批发商id',
  `nx_cpb_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '批次金额',
  `nx_cpb_buy_user_id` int DEFAULT NULL COMMENT '买方用户id',
  `nx_cpb_sell_user_id` int DEFAULT NULL COMMENT '卖方用户id',
  `nx_cpb_com_supplier_id` int DEFAULT NULL COMMENT '供应商id',
  `nx_cpb_paste_content` text CHARACTER SET utf16 COLLATE utf16_czech_ci COMMENT '批发商进货批次时间',
  PRIMARY KEY (`nx_community_purchase_batch_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_community_purchase_goods
-- ----------------------------
DROP TABLE IF EXISTS `nx_community_purchase_goods`;
CREATE TABLE `nx_community_purchase_goods` (
  `nx_community_purchase_goods_id` int NOT NULL AUTO_INCREMENT COMMENT '批发商采购商品id',
  `nx_cpg_com_goods_id` int DEFAULT NULL COMMENT '采购商品id',
  `nx_cpg_com_goods_father_id` int DEFAULT NULL COMMENT '采购父级商品id',
  `nx_cpg_quantity` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '采购数量',
  `nx_cpg_standard` varchar(6) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '采购规格',
  `nx_cpg_status` tinyint DEFAULT NULL COMMENT '采购状态',
  `nx_cpg_community_id` int DEFAULT NULL COMMENT '采购批发商id',
  `nx_cpg_purchase_type` tinyint DEFAULT NULL COMMENT '采购方式：“1 订单采购”“2 添加采购”',
  `nx_cpg_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '采购时间',
  `nx_cpg_batch_id` int DEFAULT NULL COMMENT '采购批次号',
  `nx_cpg_buy_user_id` int DEFAULT NULL COMMENT '采购方式为“采购”的采购员id',
  `nx_cpg_buy_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '采购单价',
  `nx_cpg_buy_quantity` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '采购数量',
  `nx_cpg_orders_amount` int DEFAULT NULL COMMENT '订单采购的订单数量',
  `nx_cpg_type_add_user_id` int DEFAULT NULL COMMENT '添加采购用户id',
  `nx_cpg_apply_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cpg_purchase_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '采购日期',
  `nx_CPG_input_type` tinyint DEFAULT NULL,
  `nx_cpg_buy_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`nx_community_purchase_goods_id`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_community_restrauant
-- ----------------------------
DROP TABLE IF EXISTS `nx_community_restrauant`;
CREATE TABLE `nx_community_restrauant` (
  `nx_community_restaruant_id` int NOT NULL AUTO_INCREMENT,
  `nx_CR_community_id` int DEFAULT NULL,
  `nx_CR_restaruant_id` int DEFAULT NULL,
  PRIMARY KEY (`nx_community_restaruant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_community_splicing_orders
-- ----------------------------
DROP TABLE IF EXISTS `nx_community_splicing_orders`;
CREATE TABLE `nx_community_splicing_orders` (
  `nx_community_splicing_orders_id` int NOT NULL AUTO_INCREMENT COMMENT '订单id',
  `nx_cso_co_order_id` int DEFAULT NULL COMMENT '批发商id',
  `nx_cso_community_id` int DEFAULT NULL COMMENT '订单社区id',
  `nx_cso_customer_id` int DEFAULT NULL COMMENT '订单客户id',
  `nx_cso_user_id` int DEFAULT NULL COMMENT '订单用户id',
  `nx_cso_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订单日期',
  `nx_cso_status` tinyint DEFAULT NULL COMMENT '订单状态',
  `nx_cso_service` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订单送达时间',
  `nx_cso_amount` float(10,0) DEFAULT NULL COMMENT '订单总金额',
  `nx_cso_service_date` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订单送到日期',
  `nx_cso_service_time` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订单送到时间',
  `nx_cso_weigh_user_id` int DEFAULT NULL COMMENT '订单称重用户id',
  `nx_cso_delivery_user_id` int DEFAULT NULL COMMENT '订单配送员工id',
  `nx_cso_sub_amount` int DEFAULT NULL COMMENT '订单子商品数量',
  `nx_cso_sub_finished` int DEFAULT NULL COMMENT '订单子商品完成数量',
  `nx_cso_weigh_number` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订单称重订单号',
  `nx_cso_payment_status` tinyint DEFAULT NULL COMMENT '订单支付状态',
  `nx_cso_payment_send_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订单支付发送时间',
  `nx_cso_payment_time` varchar(0) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订单支付时间',
  `nx_cso_type` tinyint DEFAULT NULL COMMENT '订单类型 0先付款1后付款',
  `nx_cso_wx_out_trade_no` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订单支付时间',
  `nx_cso_total` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订单类型 0先付款1后付款',
  `nx_cso_youhui_total` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订单类型 0先付款1后付款',
  `nx_cso_service_hour` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订单送到时间',
  `nx_cso_service_minute` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订单送到时间',
  `nx_cso_buy_member_card_time` int DEFAULT NULL COMMENT '订单送到时间',
  `nx_cso_buy_member_card_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订单送到时间',
  `nx_cso_commerce_id` int DEFAULT NULL COMMENT '订单社区id',
  `nx_cso_service_type` tinyint DEFAULT NULL COMMENT '0 到店 1 配送',
  PRIMARY KEY (`nx_community_splicing_orders_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_community_standard
-- ----------------------------
DROP TABLE IF EXISTS `nx_community_standard`;
CREATE TABLE `nx_community_standard` (
  `nx_community_standard_id` int NOT NULL AUTO_INCREMENT,
  `nx_CS_comm_goods_id` int DEFAULT NULL,
  `nx_CS_standard_name` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_CS_standard_file_path` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_CS_standard_scale` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_CS_standard_error` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_CS_standard_sort` int DEFAULT NULL,
  `nx_CS_standard_weight` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`nx_community_standard_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_community_statistics
-- ----------------------------
DROP TABLE IF EXISTS `nx_community_statistics`;
CREATE TABLE `nx_community_statistics` (
  `nx_community_statistics_id` int NOT NULL AUTO_INCREMENT,
  `nx_cs_com_goods_id` int DEFAULT NULL,
  `nx_cs_com_f_goods_id` int DEFAULT NULL,
  `nx_cs_com_gf_goods_id` int DEFAULT NULL,
  `nx_cs_com_ggf_goods_id` int DEFAULT NULL,
  `nx_cs_order_date` date DEFAULT NULL,
  `nx_cs_com_goods_profit` float(20,1) DEFAULT NULL,
  `nx_cs_com_goods_weight` float(10,1) DEFAULT NULL,
  `nx_cs_purchase_price` float(10,1) DEFAULT NULL,
  `nx_cs_order_quantity` int DEFAULT NULL,
  `nx_cs_com_id` int DEFAULT NULL,
  PRIMARY KEY (`nx_community_statistics_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_community_stock
-- ----------------------------
DROP TABLE IF EXISTS `nx_community_stock`;
CREATE TABLE `nx_community_stock` (
  `nx_community_stock_id` int NOT NULL AUTO_INCREMENT COMMENT '社区库存id',
  `nx_cs_community_id` int DEFAULT NULL COMMENT '社区库存社区id',
  `nx_stock_total` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '社区库存数量',
  `nx_stock_requier_date` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '社区库存请求入库时间',
  `nx_stock_requier_user_id` int DEFAULT NULL COMMENT '社区库存请求入库用户',
  `nx_stock_status` tinyint DEFAULT NULL COMMENT '社区库存状态',
  `nx_stock_in_date` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '社区库存入库时间',
  `nx_stock_in_user_id` int DEFAULT NULL COMMENT '社区库存入库用户',
  PRIMARY KEY (`nx_community_stock_id`)
) ENGINE=InnoDB AUTO_INCREMENT=22 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_community_stock_sub
-- ----------------------------
DROP TABLE IF EXISTS `nx_community_stock_sub`;
CREATE TABLE `nx_community_stock_sub` (
  `nx_community_sub_stock_id` int NOT NULL AUTO_INCREMENT COMMENT '社区子库存id',
  `nx_cs_id` int DEFAULT NULL COMMENT '社区子库存库存id',
  `nx_css_cg_id` int DEFAULT NULL COMMENT '社区子社区商品id',
  `nx_css_entry_amount` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '社区子库存入库数量',
  `nx_css_entry_standard` varchar(4) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '社区子库存入库单位',
  `nx_css_entry_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '社区子库存入库单价',
  `nx_css_entry_sub_total` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '社区子库存入库小计',
  `nx_css_entry_date` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '社区子库存入库日期',
  `nx_css_stock_amount` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '社区子库存库存数量',
  PRIMARY KEY (`nx_community_sub_stock_id`)
) ENGINE=InnoDB AUTO_INCREMENT=32 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_community_supplier
-- ----------------------------
DROP TABLE IF EXISTS `nx_community_supplier`;
CREATE TABLE `nx_community_supplier` (
  `nx_community_supplier_id` int NOT NULL AUTO_INCREMENT COMMENT '供货商id',
  `nx_community_supplier_name` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '供货商名称',
  `nx_CS_nx_community_id` int DEFAULT NULL COMMENT 'nx_community_id',
  `nx_CS_order_type` tinyint DEFAULT NULL COMMENT '订单类型',
  `nx_CS_jrdh_supplier_user_id` int DEFAULT NULL COMMENT '今日订货用户id',
  `nx_CS_jrdh_pur_user_id` int DEFAULT NULL COMMENT '今日订货App用户id',
  `nx_CS_nx_community_user_id` int DEFAULT NULL COMMENT 'nx_comm采购员id',
  PRIMARY KEY (`nx_community_supplier_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_community_supplier_payment
-- ----------------------------
DROP TABLE IF EXISTS `nx_community_supplier_payment`;
CREATE TABLE `nx_community_supplier_payment` (
  `nx_communtiy_supplier_payment_id` int NOT NULL AUTO_INCREMENT COMMENT '供货商付款id',
  `nx_csp_date` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '付款日期',
  `nx_csp_supplier_id` int DEFAULT NULL COMMENT '供货商id',
  `nx_csp_pay_comm_user_id` int DEFAULT NULL COMMENT 'community_user付款人',
  `nx_csp_pay_total` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '付款总额',
  `nx_csp_nx_community_id` int DEFAULT NULL COMMENT 'nxCommunityId',
  PRIMARY KEY (`nx_communtiy_supplier_payment_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_community_user
-- ----------------------------
DROP TABLE IF EXISTS `nx_community_user`;
CREATE TABLE `nx_community_user` (
  `nx_community_user_id` int NOT NULL AUTO_INCREMENT COMMENT '批发商用户id',
  `nx_COU_wx_avartra_url` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '用户名',
  `nx_COU_wx_nick_name` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '登陆密码',
  `nx_COU_wx_open_id` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_COU_wx_phone` varchar(15) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_COU_community_id` int DEFAULT NULL,
  `nx_COU_admin` tinyint DEFAULT NULL,
  `nx_COU_role_id` tinyint DEFAULT NULL COMMENT '用户角色 1,拣货员;2,打包员;3,司机;',
  `nx_COU_working_status` tinyint DEFAULT NULL,
  `nx_COU_moment_lat` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '实时坐标',
  `nx_COU_moment_lng` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_COU_device_id` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_COU_url_is_change` int DEFAULT NULL,
  PRIMARY KEY (`nx_community_user_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_customer
-- ----------------------------
DROP TABLE IF EXISTS `nx_customer`;
CREATE TABLE `nx_customer` (
  `nx_customer_id` int NOT NULL AUTO_INCREMENT COMMENT '客户id',
  `nx_customer_name` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '客户名称',
  `nx_customer_print_label` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '客户打印名称',
  `nx_customer_out_label` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '客户对外名称',
  `nx_customer_type` int DEFAULT NULL COMMENT '客户类型',
  `nx_customer_address` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '客户地址',
  `nx_customer_phone` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '客户手机',
  `nx_customer_call` int DEFAULT NULL COMMENT '客户称呼',
  `nx_customer_lat` varchar(30) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_customer_lng` varchar(30) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_customer_dis_id` int DEFAULT NULL COMMENT '客户批发商id',
  `nx_customer_community_id` int DEFAULT NULL COMMENT '社区id',
  `nx_customer_join_date` date DEFAULT NULL COMMENT '客户加入日期',
  `nx_customer_order_amount` float(10,1) DEFAULT NULL COMMENT '客户订货金额',
  `nx_customer_order_times` int DEFAULT NULL COMMENT '客户订货次数',
  `nx_customer_detail_address` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '客户详细地址',
  `nx_customer_card_waste_date` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '卡到期日期',
  `nx_customer_ecommerce_id` int DEFAULT NULL COMMENT '客户订货次数',
  `nx_customer_commerce_id` int DEFAULT NULL COMMENT '社区id',
  PRIMARY KEY (`nx_customer_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=38 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_customer_user
-- ----------------------------
DROP TABLE IF EXISTS `nx_customer_user`;
CREATE TABLE `nx_customer_user` (
  `nx_CU_user_id` int NOT NULL AUTO_INCREMENT COMMENT '客户用户id',
  `nx_CU_customer_id` int DEFAULT NULL COMMENT '客户id',
  `nx_CU_wx_nick_name` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '客户用户微信昵称',
  `nx_CU_wx_avatar_url` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '微信头像',
  `nx_CU_wx_gender` int DEFAULT NULL COMMENT '微信性别',
  `nx_CU_wx_open_id` varchar(40) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '微信openid',
  `nx_CU_wx_phone_number` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '微信手机号',
  `nx_CU_join_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '客户加入时间',
  `nx_CU_order_amount` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '客户订货总金额',
  `nx_CU_order_times` int DEFAULT NULL COMMENT '客户订货次数',
  `nx_CU_community_id` int DEFAULT NULL COMMENT '客户id',
  `nx_CU_commerce_id` int DEFAULT NULL COMMENT '客户id',
  PRIMARY KEY (`nx_CU_user_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=34 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_customer_user_address
-- ----------------------------
DROP TABLE IF EXISTS `nx_customer_user_address`;
CREATE TABLE `nx_customer_user_address` (
  `nx_customer_user_address_id` int NOT NULL AUTO_INCREMENT,
  `nx_cua_customer_user_id` int DEFAULT NULL,
  `nx_cua_community_id` int DEFAULT NULL,
  `nx_cua_status` int DEFAULT NULL,
  `nx_cua_type` int DEFAULT NULL,
  `nx_cua_user_name` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cua_user_phone` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cua_address_building_name` varchar(200) COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cua_address_detail` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cua_is_selected` tinyint DEFAULT NULL,
  `nx_cua_sys_city_id` int DEFAULT NULL,
  `nx_cua_sys_province_id` int DEFAULT NULL,
  `nx_cua_sys_district_id` int DEFAULT NULL,
  `nx_cua_sys_business_area_id` int DEFAULT NULL,
  `nx_cua_location` point DEFAULT NULL,
  `nx_cua_lat` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cua_lng` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`nx_customer_user_address_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=28 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_customer_user_card
-- ----------------------------
DROP TABLE IF EXISTS `nx_customer_user_card`;
CREATE TABLE `nx_customer_user_card` (
  `nx_customer_user_card_id` int NOT NULL AUTO_INCREMENT,
  `nx_cuca_card_id` int DEFAULT NULL,
  `nx_cuca_customer_user_id` int DEFAULT NULL,
  `nx_cuca_community_id` int DEFAULT NULL,
  `nx_cuca_status` int DEFAULT NULL,
  `nx_cuca_type` int DEFAULT NULL,
  `nx_cuca_start_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cuca_stop_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_cuca_com_order_id` int DEFAULT NULL,
  `nx_cuca_is_selected` tinyint DEFAULT NULL,
  `nx_cuca_com_splicing_order_id` int DEFAULT NULL,
  PRIMARY KEY (`nx_customer_user_card_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=24 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_customer_user_coupon
-- ----------------------------
DROP TABLE IF EXISTS `nx_customer_user_coupon`;
CREATE TABLE `nx_customer_user_coupon` (
  `nx_customer_user_coupon_id` int NOT NULL AUTO_INCREMENT,
  `nx_cuc_coupon_id` int DEFAULT NULL,
  `nx_cuc_customer_user_id` int DEFAULT NULL,
  `nx_cuc_share_user_id` int DEFAULT NULL,
  `nx_cuc_community_id` int DEFAULT NULL,
  `nx_cuc_status` int DEFAULT NULL,
  `nx_cuc_type` int DEFAULT NULL,
  `nx_cuc_sub_order_id` int DEFAULT NULL,
  `nx_cuc_from_share_user_id` int DEFAULT NULL,
  `nx_cuc_share_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`nx_customer_user_coupon_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_customer_user_goods
-- ----------------------------
DROP TABLE IF EXISTS `nx_customer_user_goods`;
CREATE TABLE `nx_customer_user_goods` (
  `nx_CUG_goods_id` int NOT NULL AUTO_INCREMENT COMMENT '客户用户商品id',
  `nx_CUG_user_id` int DEFAULT NULL COMMENT '客户用户id',
  `nx_CUG_community_goods_id` int DEFAULT NULL COMMENT '批发商商品id',
  `nx_CUG_first_order_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '客户用户第一次订货时间',
  `nx_CUG_last_order_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '客户用户最后一次订货时间',
  `nx_CUG_order_amount` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '客户用户订货总量',
  `nx_CUG_order_times` int DEFAULT NULL COMMENT '客户用户订货次数',
  `nx_CUG_is_love` tinyint DEFAULT NULL COMMENT '客户用户最爱',
  `nx_CUG_order_rate` int DEFAULT NULL COMMENT '客户订货频率',
  `nx_CUG_last_order_quantity` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '客户最后一次订货数量',
  `nx_CUG_last_order_standard` varchar(4) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '客户最后一次订货规格',
  `nx_CUG_join_my_template` tinyint DEFAULT NULL COMMENT '是否加入用户订货模版',
  `nx_CUG_order_quantity` varchar(4) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_CUG_order_standard` varchar(4) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_CUG_goods_color` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_CUG_type` tinyint DEFAULT NULL,
  PRIMARY KEY (`nx_CUG_goods_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_department
-- ----------------------------
DROP TABLE IF EXISTS `nx_department`;
CREATE TABLE `nx_department` (
  `nx_department_id` int NOT NULL AUTO_INCREMENT COMMENT '订货部门id',
  `nx_department_name` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订货部门名称',
  `nx_department_app_id` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_department_father_id` int DEFAULT NULL COMMENT '订货部门上级id',
  `nx_department_type` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订货部门类型',
  `nx_department_sub_amount` int DEFAULT NULL COMMENT '订货部门子部门数量',
  `nx_department_dis_id` int DEFAULT NULL COMMENT '订货部门批发商id',
  `nx_department_file_path` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_department_is_group_dep` tinyint DEFAULT NULL COMMENT '是客户吗',
  `nx_department_print_name` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_department_show_weeks` tinyint DEFAULT '1',
  `nx_department_settle_type` tinyint DEFAULT NULL,
  `nx_department_attr_name` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '客户简称',
  `nx_department_pick_name` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '客户简称',
  `nx_department_driver_id` int DEFAULT NULL,
  `nx_department_owe_box_number` int DEFAULT '0',
  `nx_department_delivery_box_number` int DEFAULT '0',
  `nx_department_working_status` int DEFAULT NULL,
  `nx_department_unPay_total` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_department_add_count` int DEFAULT NULL,
  `nx_department_pay_total` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_department_profit_total` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_department_promotion_goods_id` int DEFAULT NULL,
  `nx_department_join_date` varchar(10) COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_department_order_total` bigint DEFAULT NULL,
  `nx_department_pinyin` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_department_lat` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_department_lng` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_department_record_minutes` int DEFAULT NULL,
  `nx_department_earliest_delivery_time` int DEFAULT NULL COMMENT '最早配送时间（秒）',
  `nx_department_latest_delivery_time` int DEFAULT NULL COMMENT '最晚配送时间（秒）',
  `nx_department_address` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_department_unload_duration` int DEFAULT NULL,
  `nx_department_gb_distributer_id` int DEFAULT NULL,
  `nx_department_points` varchar(50) COLLATE utf16_czech_ci DEFAULT '0' COMMENT '部门积分',
  `nx_department_waiting_points` varchar(50) COLLATE utf16_czech_ci DEFAULT '0' COMMENT '等待积分（客户货品卖出前的积分，卖出后加到积分字段）',
  `nx_department_ocr_prompt_image` text COLLATE utf16_czech_ci COMMENT 'OCR修正指令-图片上传（用于存储用户的修正要求，针对图片识别）',
  `nx_department_ocr_prompt_excel` text CHARACTER SET utf16 COLLATE utf16_czech_ci COMMENT 'OCR修正指令累计（用于存储用户的修正要求，累计到部门级别）',
  `nx_department_ocr_prompt_paste` text COLLATE utf16_czech_ci COMMENT 'OCR修正指令-复制粘贴（用于存储用户的修正要求，针对粘贴识别）',
  `nx_department_order_code` varchar(255) COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订货代号（用于对客户名称保密）',
  PRIMARY KEY (`nx_department_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1510 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_department_bill
-- ----------------------------
DROP TABLE IF EXISTS `nx_department_bill`;
CREATE TABLE `nx_department_bill` (
  `nx_department_bill_id` int NOT NULL AUTO_INCREMENT,
  `nx_DB_dis_id` int DEFAULT NULL,
  `nx_DB_dep_id` int DEFAULT NULL,
  `nx_DB_total` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DB_status` tinyint DEFAULT NULL,
  `nx_DB_time` varchar(30) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DB_issue_user_id` int DEFAULT NULL,
  `nx_DB_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DB_month` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DB_week` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DB_trade_no` varchar(32) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DB_print_times` int DEFAULT NULL,
  `nx_DB_day` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '星期',
  `nx_DB_gb_dis_id` int DEFAULT NULL,
  `nx_DB_gb_dep_id` int DEFAULT NULL,
  `nx_DB_profit_total` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DB_profit_scale` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DB_nx_community_id` int DEFAULT NULL,
  `nx_DB_nx_restraunt_id` int DEFAULT NULL,
  `nx_DB_pay_points` varchar(50) COLLATE utf16_czech_ci DEFAULT '0' COMMENT '支付积分',
  `nx_DB_pay_cash` varchar(50) COLLATE utf16_czech_ci DEFAULT '0' COMMENT '支付现金',
  `nx_DB_cost_total` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DB_dep_father_id` int DEFAULT NULL,
  `nx_DB_wx_out_trade_no` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DB_year` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DB_gb_department_bill_id` int DEFAULT NULL,
  `nx_DB_gb_dep_father_id` int DEFAULT NULL,
  PRIMARY KEY (`nx_department_bill_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=10271 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_department_bill_copy1
-- ----------------------------
DROP TABLE IF EXISTS `nx_department_bill_copy1`;
CREATE TABLE `nx_department_bill_copy1` (
  `nx_department_bill_id` int NOT NULL AUTO_INCREMENT,
  `nx_DB_dis_id` int DEFAULT NULL,
  `nx_DB_dep_id` int DEFAULT NULL,
  `nx_DB_total` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DB_status` tinyint DEFAULT NULL,
  `nx_DB_time` varchar(30) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DB_issue_user_id` int DEFAULT NULL,
  `nx_DB_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DB_month` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DB_week` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DB_trade_no` varchar(32) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DB_print_times` int DEFAULT NULL,
  `nx_DB_day` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '星期',
  `nx_DB_gb_dis_id` int DEFAULT NULL,
  `nx_DB_gb_dep_id` int DEFAULT NULL,
  `nx_DB_profit_total` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DB_profit_scale` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DB_nx_community_id` int DEFAULT NULL,
  `nx_DB_nx_restraunt_id` int DEFAULT NULL,
  `nx_DB_cost_total` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DB_dep_father_id` int DEFAULT NULL,
  `nx_DB_wx_out_trade_no` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`nx_department_bill_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1674 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_department_dis_goods
-- ----------------------------
DROP TABLE IF EXISTS `nx_department_dis_goods`;
CREATE TABLE `nx_department_dis_goods` (
  `nx_department_dis_goods_id` int NOT NULL AUTO_INCREMENT,
  `nx_DDG_department_father_id` int DEFAULT NULL,
  `nx_DDG_department_id` int DEFAULT NULL,
  `nx_DDG_dis_goods_id` int DEFAULT NULL,
  `nx_DDG_dis_goods_father_id` int DEFAULT NULL,
  `nx_DDG_dep_goods_name` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DDG_dep_goods_pinyin` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DDG_dep_goods_py` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DDG_dep_goods_standardname` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DDG_dep_goods_detail` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DDG_dep_goods_brand` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DDG_dep_goods_place` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DDG_order_price` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DDG_order_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DDG_order_remark` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DDG_order_quantity` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DDG_order_standard` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DDG_is_gb_department` tinyint DEFAULT NULL,
  `nx_DDG_gb_department_father_id` int DEFAULT NULL,
  `nx_DDG_gb_department_id` int DEFAULT NULL,
  `nx_DDG_order_cost_price` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DDG_order_seller_user_id` int DEFAULT NULL,
  `nx_DDG_order_buyer_user_id` int DEFAULT NULL,
  `nx_DDG_dis_goods_grand_id` int DEFAULT NULL,
  `nx_DDG_dis_goods_great_id` int DEFAULT NULL,
  `nx_DDG_order_goods_name` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DDG_pick_detail` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DDG_nx_distributer_id` int DEFAULT NULL,
  `nx_DDG_gb_distributer_id` int DEFAULT NULL,
  `nx_DDG_order_price_level` varchar(10) COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DDG_goods_nx_distributer_id` int DEFAULT NULL,
  PRIMARY KEY (`nx_department_dis_goods_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=14346 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_department_dis_goods_copy1
-- ----------------------------
DROP TABLE IF EXISTS `nx_department_dis_goods_copy1`;
CREATE TABLE `nx_department_dis_goods_copy1` (
  `nx_department_dis_goods_id` int NOT NULL AUTO_INCREMENT,
  `nx_DDG_department_father_id` int DEFAULT NULL,
  `nx_DDG_department_id` int DEFAULT NULL,
  `nx_DDG_dis_goods_id` int DEFAULT NULL,
  `nx_DDG_dis_goods_father_id` int DEFAULT NULL,
  `nx_DDG_dep_goods_name` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DDG_dep_goods_pinyin` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DDG_dep_goods_py` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DDG_dep_goods_standardname` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DDG_dep_goods_detail` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DDG_dep_goods_brand` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DDG_dep_goods_place` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DDG_order_price` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DDG_order_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DDG_order_remark` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DDG_order_quantity` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DDG_order_standard` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DDG_is_gb_department` tinyint DEFAULT NULL,
  `nx_DDG_gb_department_father_id` int DEFAULT NULL,
  `nx_DDG_gb_department_id` int DEFAULT NULL,
  `nx_DDG_order_cost_price` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DDG_order_seller_user_id` int DEFAULT NULL,
  `nx_DDG_order_buyer_user_id` int DEFAULT NULL,
  `nx_DDG_dis_goods_grand_id` int DEFAULT NULL,
  `nx_DDG_order_goods_name` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DDG_pick_detail` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`nx_department_dis_goods_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=8738 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_department_independent_goods
-- ----------------------------
DROP TABLE IF EXISTS `nx_department_independent_goods`;
CREATE TABLE `nx_department_independent_goods` (
  `nx_department_independent_goods_id` int NOT NULL AUTO_INCREMENT,
  `nx_DIG_goods_name` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DIG_goods_standardname` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DIG_department_father_id` int DEFAULT NULL,
  `nx_DIG_department_id` int DEFAULT NULL,
  `nx_DIG_alarm_rate` int DEFAULT NULL,
  `nx_DIG_goods_pinyin` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DIG_goods_py` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`nx_department_independent_goods_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_department_order_history
-- ----------------------------
DROP TABLE IF EXISTS `nx_department_order_history`;
CREATE TABLE `nx_department_order_history` (
  `nx_department_orders_id` int NOT NULL,
  `nx_DO_nx_goods_id` int DEFAULT NULL COMMENT '部门订单nx商品id',
  `nx_DO_nx_goods_father_id` int DEFAULT NULL COMMENT '部门订单商品父id',
  `nx_DO_dis_goods_id` int DEFAULT NULL COMMENT '部门订单社区商品id',
  `nx_DO_dis_goods_father_id` int DEFAULT NULL COMMENT '批发商父级商品id',
  `nx_DO_dep_dis_goods_id` int DEFAULT NULL,
  `nx_DO_dep_dis_goods_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门商品价格',
  `nx_DO_quantity` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请数量',
  `nx_DO_standard` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请规格',
  `standard_weight` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '规格重量（如 250ml / 1.9L / 500g）',
  `item_unit` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '最小包装单位（如 盒 / 瓶 / 袋）',
  `nx_DO_remark` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请备注',
  `items_per_carton` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '每个大包装内的小包装数量',
  `carton_unit` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '大包装单位（如 箱 / 件）',
  `nx_DO_weight` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单重量',
  `nx_DO_weight_kg` varchar(50) COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单公斤重量（用于将斤的重量转换为公斤）',
  `nx_DO_price` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单商品单价',
  `nx_DO_price_kg` varchar(50) COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单商品公斤单价（用于将斤的单价转换为公斤）',
  `nx_DO_subtotal` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请商品小计',
  `nx_DO_department_id` int DEFAULT NULL COMMENT '部门订单部门id',
  `nx_DO_department_father_id` int DEFAULT NULL,
  `nx_DO_distributer_id` int DEFAULT NULL COMMENT '部门订单批发商id',
  `nx_DO_purchase_user_id` int DEFAULT NULL COMMENT '部门商品采购员id',
  `nx_DO_bill_id` int DEFAULT NULL COMMENT '部门订单账单id',
  `nx_DO_status` tinyint DEFAULT NULL COMMENT '部门订单申请商品状态',
  `nx_DO_order_user_id` int DEFAULT NULL COMMENT '部门订单订货用户id',
  `nx_DO_pick_user_id` int DEFAULT NULL COMMENT '部门订单商品称重用户id',
  `nx_DO_account_user_id` int DEFAULT NULL COMMENT '部门订单商品输入单价用户id',
  `nx_DO_purchase_status` tinyint DEFAULT NULL COMMENT '部门订单商品进货状态',
  `nx_DO_apply_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请时间',
  `nx_DO_arrive_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单送达时间',
  `nx_DO_purchase_goods_id` int DEFAULT NULL COMMENT '订单采购商品id',
  `nx_DO_arrive_only_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DO_apply_full_time` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DO_goods_type` tinyint DEFAULT NULL COMMENT '配送商品0，自采购商品1',
  `nx_DO_operation_time` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DO_arrive_what_day` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '星期几',
  `nx_DO_is_agent` int DEFAULT NULL COMMENT '配送商用户 id',
  `nx_DO_arrive_weeks_year` int DEFAULT NULL COMMENT '本年第几周',
  `nx_DO_apply_only_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DO_cost_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '进货单价',
  `nx_DO_cost_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '进货小计',
  `nx_DO_gb_distributer_id` int DEFAULT NULL COMMENT '连锁采购id',
  `nx_DO_gb_department_id` int DEFAULT NULL COMMENT '连锁采购客户订货部门id',
  `nx_DO_gb_department_father_id` int DEFAULT NULL COMMENT '连锁采购客户订货部门id',
  `nx_DO_return_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '退货数量',
  `nx_DO_return_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '退货金额',
  `nx_DO_return_bill_id` int DEFAULT NULL COMMENT '退货单号',
  `nx_DO_return_status` tinyint DEFAULT NULL COMMENT '退货状态',
  `nx_DO_weight_id` int DEFAULT NULL COMMENT '称重单号',
  `nx_DO_profit_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '利润小计',
  `nx_DO_profit_scale` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '利润率',
  `nx_DO_nx_community_id` int DEFAULT NULL COMMENT '连锁采购id',
  `nx_DO_nx_comm_restraunt_id` int DEFAULT NULL COMMENT '连锁采购客户订货部门id',
  `nx_DO_nx_comm_restraunt_father_id` int DEFAULT NULL COMMENT '连锁采购客户订货部门id',
  `nx_DO_gb_department_order_id` int DEFAULT NULL COMMENT '连锁采购订单id',
  `nx_DO_nx_restraunt_order_id` int DEFAULT NULL COMMENT '饭馆订单id',
  `nx_DO_training_data_id` int DEFAULT NULL COMMENT '训练数据ID（关联订单OCR训练数据表）',
  `nx_do_ocr_task_id` int DEFAULT NULL COMMENT 'OCR任务ID（关联OCR任务表）',
  `nx_DO_cost_price_update` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '进货单价更新日期',
  `nx_DO_cost_price_level` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '进货单价级别精选，优选，普通，单一',
  `nx_DO_dis_goods_grand_id` int DEFAULT NULL COMMENT '批发商父级商品id',
  `nx_DO_print_standard` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请规格',
  `nx_DO_today_order` int DEFAULT NULL COMMENT '订货顺序',
  `nx_DO_price_different` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请规格',
  `nx_DO_expect_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '现金单价',
  `nx_DO_goods_name` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '商品名称',
  `nx_DO_gb_dep_dis_goods_id` int DEFAULT NULL,
  `nx_DO_goods_original_name` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '商品名称',
  `nx_DO_collaborative_nx_dis_id` int DEFAULT NULL COMMENT '协作供货商id',
  PRIMARY KEY (`nx_department_orders_id`) USING BTREE,
  UNIQUE KEY `nx_department_orders_id` (`nx_department_orders_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_department_orders
-- ----------------------------
DROP TABLE IF EXISTS `nx_department_orders`;
CREATE TABLE `nx_department_orders` (
  `nx_department_orders_id` int NOT NULL AUTO_INCREMENT COMMENT '部门订单id',
  `nx_DO_nx_goods_id` int DEFAULT NULL COMMENT '部门订单nx商品id',
  `nx_DO_nx_goods_father_id` int DEFAULT NULL COMMENT '部门订单商品父id',
  `nx_DO_dis_goods_id` int DEFAULT NULL COMMENT '部门订单社区商品id',
  `nx_DO_dis_goods_father_id` int DEFAULT NULL COMMENT '批发商父级商品id',
  `nx_DO_dep_dis_goods_id` int DEFAULT NULL,
  `nx_DO_dep_dis_goods_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门商品价格',
  `nx_DO_quantity` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请数量',
  `nx_DO_standard` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请规格',
  `standard_weight` varchar(50) COLLATE utf16_czech_ci DEFAULT NULL COMMENT '规格重量（如 250ml / 1.9L / 500g）',
  `item_unit` varchar(50) COLLATE utf16_czech_ci DEFAULT NULL COMMENT '最小包装单位（如 盒 / 瓶 / 袋）',
  `items_per_carton` varchar(50) COLLATE utf16_czech_ci DEFAULT NULL COMMENT '每个大包装内的小包装数量',
  `carton_unit` varchar(50) COLLATE utf16_czech_ci DEFAULT NULL COMMENT '大包装单位（如 箱 / 件）',
  `nx_DO_remark` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请备注',
  `nx_DO_weight` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单重量',
  `nx_DO_weight_kg` varchar(50) COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单公斤重量（用于将斤的重量转换为公斤）',
  `nx_DO_price` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单商品单价',
  `nx_DO_price_kg` varchar(50) COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单商品公斤单价（用于将斤的单价转换为公斤）',
  `nx_DO_subtotal` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请商品小计',
  `nx_DO_department_id` int DEFAULT NULL COMMENT '部门订单部门id',
  `nx_DO_department_father_id` int DEFAULT NULL,
  `nx_DO_distributer_id` int DEFAULT NULL COMMENT '部门订单批发商id',
  `nx_DO_purchase_user_id` int DEFAULT NULL COMMENT '部门商品采购员id',
  `nx_DO_bill_id` int DEFAULT NULL COMMENT '部门订单账单id',
  `nx_DO_status` tinyint DEFAULT NULL COMMENT '部门订单申请商品状态',
  `nx_DO_order_user_id` int DEFAULT NULL COMMENT '部门订单订货用户id',
  `nx_DO_pick_user_id` int DEFAULT NULL COMMENT '部门订单商品称重用户id',
  `nx_DO_account_user_id` int DEFAULT NULL COMMENT '部门订单商品输入单价用户id',
  `nx_DO_purchase_status` tinyint DEFAULT NULL COMMENT '部门订单商品进货状态',
  `nx_DO_apply_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请时间',
  `nx_DO_arrive_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单送达时间',
  `nx_DO_purchase_goods_id` int DEFAULT NULL COMMENT '订单采购商品id',
  `nx_DO_arrive_only_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DO_apply_full_time` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DO_goods_type` tinyint DEFAULT NULL COMMENT '配送商品0，自采购商品1',
  `nx_DO_operation_time` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DO_arrive_what_day` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '星期几',
  `nx_DO_is_agent` int DEFAULT NULL COMMENT '配送商用户 id',
  `nx_DO_arrive_weeks_year` int DEFAULT NULL COMMENT '本年第几周',
  `nx_DO_apply_only_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DO_cost_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '进货单价',
  `nx_DO_cost_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '进货小计',
  `nx_DO_gb_distributer_id` int DEFAULT NULL COMMENT '连锁采购id',
  `nx_DO_gb_department_id` int DEFAULT NULL COMMENT '连锁采购客户订货部门id',
  `nx_DO_gb_department_father_id` int DEFAULT NULL COMMENT '连锁采购客户订货部门id',
  `nx_DO_return_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '退货数量',
  `nx_DO_return_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '退货金额',
  `nx_DO_return_bill_id` int DEFAULT NULL COMMENT '退货单号',
  `nx_DO_return_status` tinyint DEFAULT NULL COMMENT '退货状态',
  `nx_DO_weight_id` int DEFAULT NULL COMMENT '称重单号',
  `nx_DO_profit_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '利润小计',
  `nx_DO_profit_scale` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '利润率',
  `nx_DO_nx_community_id` int DEFAULT NULL COMMENT '连锁采购id',
  `nx_DO_nx_comm_restraunt_id` int DEFAULT NULL COMMENT '连锁采购客户订货部门id',
  `nx_DO_nx_comm_restraunt_father_id` int DEFAULT NULL COMMENT '连锁采购客户订货部门id',
  `nx_DO_gb_department_order_id` int DEFAULT NULL COMMENT '连锁采购订单id',
  `nx_DO_nx_restraunt_order_id` int DEFAULT NULL COMMENT '饭馆订单id',
  `nx_DO_training_data_id` int DEFAULT NULL COMMENT '训练数据ID（关联订单OCR训练数据表）',
  `nx_do_ocr_task_id` int DEFAULT NULL COMMENT 'OCR任务ID（关联OCR任务表）',
  `nx_DO_cost_price_update` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '进货单价更新日期',
  `nx_DO_cost_price_level` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '进货单价级别精选，优选，普通，单一',
  `nx_DO_dis_goods_grand_id` int DEFAULT NULL COMMENT '批发商父级商品id',
  `nx_DO_print_standard` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请规格',
  `nx_DO_today_order` int DEFAULT NULL COMMENT '订货顺序',
  `nx_DO_price_different` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请规格',
  `nx_DO_expect_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '现金单价',
  `nx_DO_goods_name` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '商品名称',
  `nx_DO_gb_dep_dis_goods_id` int DEFAULT NULL,
  `nx_DO_goods_original_name` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '商品名称',
  `nx_DO_collaborative_nx_dis_id` int DEFAULT NULL COMMENT '协作供货商id',
  PRIMARY KEY (`nx_department_orders_id`) USING BTREE,
  KEY `idx_ocr_task` (`nx_do_ocr_task_id`)
) ENGINE=InnoDB AUTO_INCREMENT=196779 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_department_orders_backup
-- ----------------------------
DROP TABLE IF EXISTS `nx_department_orders_backup`;
CREATE TABLE `nx_department_orders_backup` (
  `nx_department_orders_id` int NOT NULL DEFAULT '0' COMMENT '部门订单id',
  `nx_DO_nx_goods_id` int DEFAULT NULL COMMENT '部门订单nx商品id',
  `nx_DO_nx_goods_father_id` int DEFAULT NULL COMMENT '部门订单商品父id',
  `nx_DO_dis_goods_id` int DEFAULT NULL COMMENT '部门订单社区商品id',
  `nx_DO_dis_goods_father_id` int DEFAULT NULL COMMENT '批发商父级商品id',
  `nx_DO_dep_dis_goods_id` int DEFAULT NULL,
  `nx_DO_dep_dis_goods_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门商品价格',
  `nx_DO_quantity` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请数量',
  `nx_DO_standard` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请规格',
  `nx_DO_remark` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请备注',
  `nx_DO_weight` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单重量',
  `nx_DO_price` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单商品单价',
  `nx_DO_subtotal` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请商品小计',
  `nx_DO_department_id` int DEFAULT NULL COMMENT '部门订单部门id',
  `nx_DO_department_father_id` int DEFAULT NULL,
  `nx_DO_distributer_id` int DEFAULT NULL COMMENT '部门订单批发商id',
  `nx_DO_purchase_user_id` int DEFAULT NULL COMMENT '部门商品采购员id',
  `nx_DO_bill_id` int DEFAULT NULL COMMENT '部门订单账单id',
  `nx_DO_status` tinyint DEFAULT NULL COMMENT '部门订单申请商品状态',
  `nx_DO_order_user_id` int DEFAULT NULL COMMENT '部门订单订货用户id',
  `nx_DO_pick_user_id` int DEFAULT NULL COMMENT '部门订单商品称重用户id',
  `nx_DO_account_user_id` int DEFAULT NULL COMMENT '部门订单商品输入单价用户id',
  `nx_DO_purchase_status` tinyint DEFAULT NULL COMMENT '部门订单商品进货状态',
  `nx_DO_apply_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请时间',
  `nx_DO_arrive_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单送达时间',
  `nx_DO_purchase_goods_id` int DEFAULT NULL COMMENT '订单采购商品id',
  `nx_DO_arrive_only_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DO_apply_full_time` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DO_goods_type` tinyint DEFAULT NULL COMMENT '配送商品0，自采购商品1',
  `nx_DO_operation_time` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DO_arrive_what_day` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '星期几',
  `nx_DO_is_agent` int DEFAULT NULL COMMENT '配送商用户 id',
  `nx_DO_arrive_weeks_year` int DEFAULT NULL COMMENT '本年第几周',
  `nx_DO_apply_only_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DO_cost_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '进货单价',
  `nx_DO_cost_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '进货小计',
  `nx_DO_gb_distributer_id` int DEFAULT NULL COMMENT '连锁采购id',
  `nx_DO_gb_department_id` int DEFAULT NULL COMMENT '连锁采购客户订货部门id',
  `nx_DO_gb_department_father_id` int DEFAULT NULL COMMENT '连锁采购客户订货部门id',
  `nx_DO_return_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '退货数量',
  `nx_DO_return_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '退货金额',
  `nx_DO_return_bill_id` int DEFAULT NULL COMMENT '退货单号',
  `nx_DO_return_status` tinyint DEFAULT NULL COMMENT '退货状态',
  `nx_DO_weight_id` int DEFAULT NULL COMMENT '称重单号',
  `nx_DO_profit_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '利润小计',
  `nx_DO_profit_scale` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '利润率',
  `nx_DO_nx_community_id` int DEFAULT NULL COMMENT '连锁采购id',
  `nx_DO_nx_comm_restraunt_id` int DEFAULT NULL COMMENT '连锁采购客户订货部门id',
  `nx_DO_nx_comm_restraunt_father_id` int DEFAULT NULL COMMENT '连锁采购客户订货部门id',
  `nx_DO_gb_department_order_id` int DEFAULT NULL COMMENT '连锁采购订单id',
  `nx_DO_nx_restraunt_order_id` int DEFAULT NULL COMMENT '饭馆订单id',
  `nx_DO_cost_price_update` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '进货单价更新日期',
  `nx_DO_cost_price_level` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '进货单价级别精选，优选，普通，单一',
  `nx_DO_dis_goods_grand_id` int DEFAULT NULL COMMENT '批发商父级商品id',
  `nx_DO_print_standard` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请规格',
  `nx_DO_today_order` int DEFAULT NULL COMMENT '订货顺序',
  `nx_DO_price_different` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请规格',
  `nx_DO_expect_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '现金单价',
  `nx_DO_goods_name` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '商品名称',
  `nx_DO_gb_dep_dis_goods_id` int DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_department_orders_cash
-- ----------------------------
DROP TABLE IF EXISTS `nx_department_orders_cash`;
CREATE TABLE `nx_department_orders_cash` (
  `nx_department_orders_cash_id` int NOT NULL AUTO_INCREMENT COMMENT '部门订单id',
  `nx_DO_nx_goods_id` int DEFAULT NULL COMMENT '部门订单nx商品id',
  `nx_DO_nx_goods_father_id` int DEFAULT NULL COMMENT '部门订单商品父id',
  `nx_DO_dis_goods_id` int DEFAULT NULL COMMENT '部门订单社区商品id',
  `nx_DO_dis_goods_father_id` int DEFAULT NULL COMMENT '批发商父级商品id',
  `nx_DO_dep_dis_goods_id` int DEFAULT NULL,
  `nx_DO_dep_dis_goods_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门商品价格',
  `nx_DO_quantity` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请数量',
  `nx_DO_standard` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请规格',
  `nx_DO_remark` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请备注',
  `nx_DO_weight` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单重量',
  `nx_DO_price` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单商品单价',
  `nx_DO_subtotal` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请商品小计',
  `nx_DO_department_id` int DEFAULT NULL COMMENT '部门订单部门id',
  `nx_DO_department_father_id` int DEFAULT NULL,
  `nx_DO_distributer_id` int DEFAULT NULL COMMENT '部门订单批发商id',
  `nx_DO_purchase_user_id` int DEFAULT NULL COMMENT '部门商品采购员id',
  `nx_DO_bill_id` int DEFAULT NULL COMMENT '部门订单账单id',
  `nx_DO_status` tinyint DEFAULT NULL COMMENT '部门订单申请商品状态',
  `nx_DO_order_user_id` int DEFAULT NULL COMMENT '部门订单订货用户id',
  `nx_DO_pick_user_id` int DEFAULT NULL COMMENT '部门订单商品称重用户id',
  `nx_DO_account_user_id` int DEFAULT NULL COMMENT '部门订单商品输入单价用户id',
  `nx_DO_purchase_status` tinyint DEFAULT NULL COMMENT '部门订单商品进货状态',
  `nx_DO_apply_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请时间',
  `nx_DO_arrive_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单送达时间',
  `nx_DO_purchase_goods_id` int DEFAULT NULL COMMENT '订单采购商品id',
  `nx_DO_arrive_only_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DO_apply_full_time` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DO_goods_type` tinyint DEFAULT NULL COMMENT '配送商品0，自采购商品1',
  `nx_DO_operation_time` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DO_arrive_what_day` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '星期几',
  `nx_DO_is_agent` int DEFAULT NULL COMMENT '配送商用户 id',
  `nx_DO_arrive_weeks_year` int DEFAULT NULL COMMENT '本年第几周',
  `nx_DO_apply_only_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DO_cost_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '进货单价',
  `nx_DO_cost_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '进货小计',
  `nx_DO_gb_distributer_id` int DEFAULT NULL COMMENT '连锁采购id',
  `nx_DO_gb_department_id` int DEFAULT NULL COMMENT '连锁采购客户订货部门id',
  `nx_DO_gb_department_father_id` int DEFAULT NULL COMMENT '连锁采购客户订货部门id',
  `nx_DO_return_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '退货数量',
  `nx_DO_return_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '退货金额',
  `nx_DO_return_bill_id` int DEFAULT NULL COMMENT '退货单号',
  `nx_DO_return_status` tinyint DEFAULT NULL COMMENT '退货状态',
  `nx_DO_weight_id` int DEFAULT NULL COMMENT '称重单号',
  `nx_DO_profit_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '利润小计',
  `nx_DO_profit_scale` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '利润率',
  `nx_DO_nx_community_id` int DEFAULT NULL COMMENT '连锁采购id',
  `nx_DO_nx_comm_restraunt_id` int DEFAULT NULL COMMENT '连锁采购客户订货部门id',
  `nx_DO_nx_comm_restraunt_father_id` int DEFAULT NULL COMMENT '连锁采购客户订货部门id',
  `nx_DO_gb_department_order_id` int DEFAULT NULL COMMENT '连锁采购订单id',
  `nx_DO_nx_restraunt_order_id` int DEFAULT NULL COMMENT '饭馆订单id',
  `nx_DO_cost_price_update` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '进货单价更新日期',
  `nx_DO_cost_price_level` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '进货单价级别精选，优选，普通，单一',
  `nx_DO_dis_goods_grand_id` int DEFAULT NULL COMMENT '批发商父级商品id',
  `nx_DO_print_standard` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请规格',
  `nx_DO_today_order` int DEFAULT NULL COMMENT '订货顺序',
  `nx_DO_price_different` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请规格',
  `nx_DO_expect_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '现金单价',
  `nx_DO_goods_name` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '商品名称',
  PRIMARY KEY (`nx_department_orders_cash_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=83397 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_department_orders_history
-- ----------------------------
DROP TABLE IF EXISTS `nx_department_orders_history`;
CREATE TABLE `nx_department_orders_history` (
  `nx_department_orders_history_id` int NOT NULL AUTO_INCREMENT COMMENT '部门订单id',
  `nx_DOH_dep_dis_goods_id` int DEFAULT NULL COMMENT '部门id',
  `nx_DOH_quantity` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请数量',
  `nx_DOH_standard` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请规格',
  `nx_DOH_remark` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请备注',
  `nx_DOH_department_id` int DEFAULT NULL COMMENT '部门订单部门id',
  `nx_DOH_department_father_id` int DEFAULT NULL,
  `nx_DOH_order_user_id` int DEFAULT NULL COMMENT '部门订单订货用户id',
  `nx_DOH_apply_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请时间',
  `nx_DOH_sell_type` tinyint DEFAULT NULL COMMENT '出货方式0,日采;1,出库;2,供货商;3,加工',
  `nx_DOH_order` int DEFAULT NULL COMMENT '订货顺序',
  `nx_DOH_order_times` tinyint DEFAULT NULL COMMENT '顺序次数',
  `nx_DOH_dis_goods_id` int DEFAULT NULL COMMENT '部门id',
  `nx_DOH_distributer_id` int DEFAULT NULL COMMENT '部门订单部门id',
  `nx_DOH_apply_quantity` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请数量',
  `nx_DOH_apply_standard` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请规格',
  PRIMARY KEY (`nx_department_orders_history_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=76394 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_department_standard
-- ----------------------------
DROP TABLE IF EXISTS `nx_department_standard`;
CREATE TABLE `nx_department_standard` (
  `nx_department_standard_id` int NOT NULL AUTO_INCREMENT COMMENT '部门规格id',
  `nx_DDS_dds_goods_id` int DEFAULT NULL COMMENT '部门商品id',
  `nx_DDS_standard_name` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门规格名称',
  `nx_DDS_standard_sort` int DEFAULT NULL COMMENT '部门规格排序',
  PRIMARY KEY (`nx_department_standard_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_department_user
-- ----------------------------
DROP TABLE IF EXISTS `nx_department_user`;
CREATE TABLE `nx_department_user` (
  `nx_department_user_id` int NOT NULL AUTO_INCREMENT COMMENT '订货部门用户id',
  `nx_DU_department_id` int DEFAULT NULL COMMENT '订货部门id',
  `nx_DU_wx_avartra_url` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订货部门用户微信头像',
  `nx_DU_wx_nick_name` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订货部门用户微信昵称',
  `nx_DU_wx_open_id` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订货部门用户微信openid',
  `nx_DU_wx_phone` varchar(15) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订货部门用户微信手机号码',
  `nx_DU_admin` tinyint DEFAULT NULL COMMENT '订货部门用户是否是管理员',
  `nx_DU_distributer_id` int DEFAULT NULL COMMENT '订货部门批发商id',
  `nx_DU_url_change` tinyint DEFAULT NULL,
  `nx_DU_department_father_id` int DEFAULT NULL,
  `nx_DU_join_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DU_login_times` int DEFAULT NULL,
  `nx_DU_login_code` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '机器登录码',
  PRIMARY KEY (`nx_department_user_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=259 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_department_user_coupon
-- ----------------------------
DROP TABLE IF EXISTS `nx_department_user_coupon`;
CREATE TABLE `nx_department_user_coupon` (
  `nx_department_user_coupon_id` int NOT NULL AUTO_INCREMENT,
  `nx_duc_coupon_id` int DEFAULT NULL,
  `nx_duc_nx_dep_user_id` int DEFAULT NULL,
  `nx_duc_share_user_id` int DEFAULT NULL,
  `nx_duc_nx_distributer_id` int DEFAULT NULL,
  `nx_duc_status` int DEFAULT NULL,
  `nx_gduc_type` int DEFAULT NULL,
  `nx_duc_use_order_id` int DEFAULT NULL,
  `nx_duc_from_share_user_id` int DEFAULT NULL,
  `nx_duc_share_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_duc_nx_department_id` int DEFAULT NULL,
  `nx_duc_start_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_duc_stop_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_duc_start_time_zone` date DEFAULT NULL,
  `nx_duc_stop_time_zone` date DEFAULT NULL,
  PRIMARY KEY (`nx_department_user_coupon_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=38 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_device_manager
-- ----------------------------
DROP TABLE IF EXISTS `nx_device_manager`;
CREATE TABLE `nx_device_manager` (
  `nx_dm_id` int NOT NULL AUTO_INCREMENT COMMENT '绑定ID',
  `nx_dm_device_id` int NOT NULL COMMENT '设备ID（外键→nx_printer_device）',
  `nx_dm_manager_id` int NOT NULL COMMENT '管理员ID（外键→nx_market_manager）',
  `nx_dm_notify_level` tinyint DEFAULT '1' COMMENT '通知级别（1-4，≥此级别才通知）',
  `nx_dm_is_primary` tinyint DEFAULT '0' COMMENT '是否主要责任人（0-否 1-是，接收所有级别）',
  `nx_dm_enable` tinyint DEFAULT '1' COMMENT '是否启用（0-否 1-是）',
  `nx_dm_create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
  PRIMARY KEY (`nx_dm_id`),
  UNIQUE KEY `uk_device_manager` (`nx_dm_device_id`,`nx_dm_manager_id`),
  KEY `idx_device_id` (`nx_dm_device_id`),
  KEY `idx_manager_id` (`nx_dm_manager_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='设备责任人绑定表';

-- ----------------------------
-- Table structure for nx_dis_user_role
-- ----------------------------
DROP TABLE IF EXISTS `nx_dis_user_role`;
CREATE TABLE `nx_dis_user_role` (
  `id` int NOT NULL AUTO_INCREMENT,
  `user_id` int DEFAULT NULL COMMENT '用户ID',
  `role_id` int DEFAULT NULL COMMENT '角色ID',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COMMENT='用户与角色对应关系';

-- ----------------------------
-- Table structure for nx_distributer
-- ----------------------------
DROP TABLE IF EXISTS `nx_distributer`;
CREATE TABLE `nx_distributer` (
  `nx_distributer_id` int NOT NULL AUTO_INCREMENT COMMENT '批发商id',
  `nx_distributer_name` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '批发商名称',
  `nx_distributer_lan` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '批发商位置经度',
  `nx_distributer_lun` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '批发商位置纬度',
  `nx_distributer_business_type_id` tinyint DEFAULT NULL COMMENT '批发商商业类型',
  `nx_distributer_manager` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_distributer_phone` varchar(30) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_distributer_address` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_distributer_img` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_distributer_market_name` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_distributer_type` tinyint DEFAULT NULL COMMENT '批发商商业类型',
  `nx_distributer_app_id` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_distributer_buy_quantity` varchar(100) COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_distributer_shelf_quantity` int DEFAULT NULL,
  `nx_distributer_pay_url` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_distributer_sys_market_id` int DEFAULT NULL COMMENT '批发商商业类型',
  `nx_distributer_sys_city_id` tinyint DEFAULT NULL COMMENT '批发商商业类型',
  `nx_distributer_show_name` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '批发商名称',
  `invited_by_nx_distributer_id` int DEFAULT NULL COMMENT '邀请我注册的配送商id',
  `invite_code` varchar(16) COLLATE utf16_czech_ci DEFAULT NULL COMMENT '我的邀请码(用于生成邀请链接)',
  PRIMARY KEY (`nx_distributer_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=172 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_distributer_alias
-- ----------------------------
DROP TABLE IF EXISTS `nx_distributer_alias`;
CREATE TABLE `nx_distributer_alias` (
  `nx_distributer_alias_id` int NOT NULL AUTO_INCREMENT,
  `nx_DA_dis_goods_id` int DEFAULT NULL,
  `nx_DA_alias_name` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DA_nx_alias_id` int DEFAULT NULL,
  `nx_DA_alias_pinyin` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DA_alias_py` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`nx_distributer_alias_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=603 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_distributer_bill
-- ----------------------------
DROP TABLE IF EXISTS `nx_distributer_bill`;
CREATE TABLE `nx_distributer_bill` (
  `nx_distributer_bill_id` int NOT NULL AUTO_INCREMENT,
  `nx_DBD_order_dis_id` int DEFAULT NULL,
  `nx_DBD_offer_nx_dis_id` int DEFAULT NULL,
  `nx_DBD_total` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DBD_status` tinyint DEFAULT NULL,
  `nx_DBD_time` varchar(30) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DBD_issue_user_id` int DEFAULT NULL,
  `nx_DBD_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DBD_month` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DBD_week` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DBD_trade_no` varchar(32) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DBD_print_times` int DEFAULT NULL,
  `nx_DBD_day` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '星期',
  `nx_DBD_profit_total` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DBD_profit_scale` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DBD_pay_cash` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT '0' COMMENT '支付现金',
  `nx_DBD_cost_total` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DBD_wx_out_trade_no` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DBD_year` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`nx_distributer_bill_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=10144 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_distributer_block
-- ----------------------------
DROP TABLE IF EXISTS `nx_distributer_block`;
CREATE TABLE `nx_distributer_block` (
  `nx_distributer_block_id` int NOT NULL AUTO_INCREMENT COMMENT '主键',
  `blocker_nx_distributer_id` int NOT NULL COMMENT '屏蔽者配送商id（谁不想看）',
  `blocked_nx_distributer_id` int NOT NULL COMMENT '被屏蔽的配送商id（谁的商品被屏蔽）',
  PRIMARY KEY (`nx_distributer_block_id`),
  UNIQUE KEY `uk_blocker_blocked` (`blocker_nx_distributer_id`,`blocked_nx_distributer_id`),
  KEY `idx_blocker` (`blocker_nx_distributer_id`)
) ENGINE=InnoDB AUTO_INCREMENT=31 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='配送商屏蔽表：配送商可屏蔽协作伙伴，屏蔽后查询商品时不会看到被屏蔽者的商品';

-- ----------------------------
-- Table structure for nx_distributer_community
-- ----------------------------
DROP TABLE IF EXISTS `nx_distributer_community`;
CREATE TABLE `nx_distributer_community` (
  `nx_DC_id` int NOT NULL AUTO_INCREMENT COMMENT '批发商社区id',
  `nx_DC_community_id` int DEFAULT NULL,
  `nx_DC_distributer_id` int DEFAULT NULL,
  PRIMARY KEY (`nx_DC_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_distributer_coupon
-- ----------------------------
DROP TABLE IF EXISTS `nx_distributer_coupon`;
CREATE TABLE `nx_distributer_coupon` (
  `nx_distributer_coupon_id` int NOT NULL AUTO_INCREMENT,
  `nx_distributer_coupon_name` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dc_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dc_price_great_grand_id` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dc_stop_time` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dc_start_time` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dc_words` varchar(1000) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dc_file_path` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dc_distributer_id` int DEFAULT NULL,
  `nx_dc_type` int DEFAULT NULL,
  `nx_dc_status` int DEFAULT NULL,
  `nx_dc_start_time_zone` datetime(6) DEFAULT NULL,
  `nx_dc_stop_time_zone` datetime(6) DEFAULT NULL,
  `nx_dc_start_date` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dc_stop_date` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dc_down_count` int DEFAULT NULL,
  `nx_dc_use_count` int DEFAULT NULL,
  `nx_dc_subtotal_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dc_price_percent` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dc_city_id` int DEFAULT NULL,
  `nx_dc_market_id` int DEFAULT NULL,
  `nx_dc_start_which_day` int DEFAULT NULL,
  `nx_dc_stop_which_day` int DEFAULT NULL,
  PRIMARY KEY (`nx_distributer_coupon_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_distributer_customer
-- ----------------------------
DROP TABLE IF EXISTS `nx_distributer_customer`;
CREATE TABLE `nx_distributer_customer` (
  `dist_cust_id` int NOT NULL AUTO_INCREMENT COMMENT '批发商客户id',
  `dc_dist_id` int DEFAULT NULL COMMENT '批发商id',
  `dc_cust_id` int DEFAULT NULL COMMENT '客户id',
  `dc_cust_type` tinyint DEFAULT NULL COMMENT '客户类型',
  PRIMARY KEY (`dist_cust_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_distributer_daytime
-- ----------------------------
DROP TABLE IF EXISTS `nx_distributer_daytime`;
CREATE TABLE `nx_distributer_daytime` (
  `nx_week_id` int NOT NULL AUTO_INCREMENT,
  `nx_day_name` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_day_open` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_day_close` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dis_id` int DEFAULT NULL,
  PRIMARY KEY (`nx_week_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_distributer_department
-- ----------------------------
DROP TABLE IF EXISTS `nx_distributer_department`;
CREATE TABLE `nx_distributer_department` (
  `nx_distributer_dep_id` int NOT NULL AUTO_INCREMENT,
  `nx_DD_distributer_id` int DEFAULT NULL,
  `nx_DD_department_id` int DEFAULT NULL,
  PRIMARY KEY (`nx_distributer_dep_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_distributer_e_commerce
-- ----------------------------
DROP TABLE IF EXISTS `nx_distributer_e_commerce`;
CREATE TABLE `nx_distributer_e_commerce` (
  `nx_DEC_id` int NOT NULL COMMENT '批发商社区id',
  `nx_DEC_e_id` int DEFAULT NULL,
  `nx_DEC_distributer_id` int DEFAULT NULL,
  PRIMARY KEY (`nx_DEC_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_distributer_father_goods
-- ----------------------------
DROP TABLE IF EXISTS `nx_distributer_father_goods`;
CREATE TABLE `nx_distributer_father_goods` (
  `nx_distributer_father_goods_id` int NOT NULL AUTO_INCREMENT,
  `nx_dfg_father_goods_name` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dfg_father_goods_img` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dfg_father_goods_sort` int DEFAULT NULL,
  `nx_dfg_father_goods_color` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dfg_fathers_father_id` int DEFAULT NULL,
  `nx_dfg_father_goods_level` tinyint DEFAULT NULL,
  `nx_dfg_distributer_id` int DEFAULT NULL,
  `nx_dfg_goods_amount` int DEFAULT NULL,
  `nx_dfg_nx_goods_id` int DEFAULT NULL,
  `nx_dfg_father_goods_img_large` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`nx_distributer_father_goods_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=23289 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_distributer_gb_distributer
-- ----------------------------
DROP TABLE IF EXISTS `nx_distributer_gb_distributer`;
CREATE TABLE `nx_distributer_gb_distributer` (
  `nx_distributer_gb_distributer_id` int NOT NULL AUTO_INCREMENT,
  `nx_DGD_nx_distributer_id` int DEFAULT NULL COMMENT '批发商id',
  `nx_DGD_gb_distributer_id` int DEFAULT NULL COMMENT '客户id',
  `nx_DGD_gb_dep_id` int DEFAULT NULL,
  `nx_DGD_gb_dep_user_id` int DEFAULT NULL,
  `nx_DGD_gb_pay_method` tinyint DEFAULT NULL COMMENT '付款方式0现金1记账',
  `nx_DGD_gb_goods_price` tinyint DEFAULT NULL COMMENT '商品定价方式0随行就市1固定',
  `nx_DGD_status` tinyint DEFAULT NULL COMMENT '-1 请求 0 通过',
  `nx_DGD_gb_pay_period_week` tinyint DEFAULT NULL COMMENT '付款方式0现金1记账',
  `nx_DGD_from_nx_dep_id` int DEFAULT NULL,
  `nx_DGD_nx_supplier_id` int DEFAULT NULL,
  `nx_DGD_from_nx_dis_id` int DEFAULT NULL,
  PRIMARY KEY (`nx_distributer_gb_distributer_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_distributer_goods
-- ----------------------------
DROP TABLE IF EXISTS `nx_distributer_goods`;
CREATE TABLE `nx_distributer_goods` (
  `nx_distributer_goods_id` int NOT NULL AUTO_INCREMENT COMMENT '社区商品id',
  `nx_dg_dfg_goods_father_id` int DEFAULT NULL COMMENT '批发商父类商品id',
  `nx_dg_distributer_id` int DEFAULT NULL COMMENT '批发商id',
  `nx_dg_goods_status` tinyint DEFAULT NULL COMMENT '商品状态',
  `nx_dg_goods_is_weight` tinyint DEFAULT NULL COMMENT '是否称重',
  `nx_dg_goods_name` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '商品名称',
  `nx_dg_goods_detail` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '商品详细',
  `nx_dg_goods_standardname` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '商品规格',
  `nx_dg_goods_pinyin` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '社区商品拼音',
  `nx_dg_goods_py` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '社区商品拼音简拼',
  `nx_dg_nx_goods_id` int DEFAULT NULL COMMENT 'nxGoodsId',
  `nx_dg_nx_father_img` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '进货方式',
  `nx_dg_nx_father_id` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT 'nxGoodsFatherId',
  `nx_dg_nx_grand_id` int DEFAULT NULL COMMENT 'nxGoodsGrandid',
  `nx_dg_nx_great_grand_id` int DEFAULT NULL COMMENT 'nxGreatGrandid',
  `nx_dg_pull_off` tinyint DEFAULT NULL COMMENT '是否下架',
  `nx_dg_goods_brand` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dg_goods_place` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dg_nx_goods_father_color` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dg_goods_standard_weight` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dg_purchase_auto` int DEFAULT NULL,
  `nx_dg_supplier_id` int DEFAULT NULL,
  `nx_dg_buying_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dg_price_profit_one` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dg_price_profit_two` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dg_price_profit_three` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dg_price_profit_one_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dg_price_profit_two_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dg_price_profit_three_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dg_buying_price_update` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dg_buying_price_one` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dg_buying_price_two` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dg_buying_price_three` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dg_buying_price_one_update` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dg_buying_price_two_update` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dg_buying_price_three_update` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dg_buying_price_is_grade` tinyint DEFAULT NULL,
  `nx_dg_goods_file` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dg_dfg_goods_grand_id` int DEFAULT NULL COMMENT '批发商父类商品id',
  `nx_dg_is_oldest_son` int DEFAULT NULL COMMENT '批发商父类商品id',
  `nx_dg_price_first_day` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dg_price_second_day` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dg_price_third_day` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dg_will_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dg_goods_sort` int DEFAULT NULL COMMENT '商品状态',
  `nx_dg_goods_sons_sort` int DEFAULT NULL COMMENT '商品状态',
  `nx_dg_goods_is_hidden` int DEFAULT NULL COMMENT '商品状态',
  `nx_dg_trace_report_id` int DEFAULT NULL COMMENT '溯源报告ID',
  `nx_dg_will_price_update` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dg_goods_file_large` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dg_will_price_one` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dg_will_price_two` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dg_will_price_three` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dg_will_price_one_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dg_will_price_two_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dg_will_price_three_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dg_out_total_weight` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dg_will_price_one_standard` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dg_will_price_two_standard` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dg_will_price_three_standard` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dg_will_price_one_about_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dg_will_price_two_about_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dg_will_price_three_about_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dg_quantity_days` int DEFAULT NULL COMMENT '商品状态',
  `nx_dg_carton_unit` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '外箱名称',
  `nx_dg_items_per_carton` varchar(50) COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`nx_distributer_goods_id`) USING BTREE,
  KEY `idx_trace_report_id` (`nx_dg_trace_report_id`)
) ENGINE=InnoDB AUTO_INCREMENT=39548 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_distributer_goods_linshi
-- ----------------------------
DROP TABLE IF EXISTS `nx_distributer_goods_linshi`;
CREATE TABLE `nx_distributer_goods_linshi` (
  `nx_distributer_goods_ls_id` int NOT NULL AUTO_INCREMENT COMMENT '社区商品id',
  `nx_dg_dfg_goods_father_ls_id` int DEFAULT NULL COMMENT '批发商父类商品id',
  `nx_dg_distributer_ls_id` int DEFAULT NULL COMMENT '批发商id',
  `nx_dg_goods_ls_status` tinyint DEFAULT NULL COMMENT '商品状态',
  `nx_dg_goods_ls_name` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '商品名称',
  `nx_dg_goods_ls_detail` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '商品详细',
  `nx_dg_goods_ls_standardname` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '商品规格',
  `nx_dg_goods_ls_pinyin` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '社区商品拼音',
  `nx_dg_goods_ls_py` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '社区商品拼音简拼',
  `nx_dg_goods_ls_brand` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dg_goods_ls_place` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dg_to_nx_dis_goods_id` int DEFAULT NULL COMMENT '商品状态',
  `nx_dg_apply_date` varchar(20) COLLATE utf16_czech_ci DEFAULT NULL COMMENT '商品状态',
  `nx_dg_goods_ls_file_large` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dg_goods_ls_file` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_dg_from_nx_dis_goods_id` int DEFAULT NULL COMMENT '临时商品ID(nx_distributer_goods)',
  `nx_dg_recommend_nx_goods_ids` varchar(255) COLLATE utf16_czech_ci DEFAULT NULL COMMENT '推荐的nxGoodsId，逗号分隔如101,102,103',
  PRIMARY KEY (`nx_distributer_goods_ls_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=29225 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_distributer_goods_shelf
-- ----------------------------
DROP TABLE IF EXISTS `nx_distributer_goods_shelf`;
CREATE TABLE `nx_distributer_goods_shelf` (
  `nx_distributer_goods_shelf_id` int NOT NULL AUTO_INCREMENT COMMENT '货架id',
  `nx_distributer_goods_shelf_name` varchar(20) DEFAULT NULL COMMENT '货架名称',
  `nx_distributer_goods_shelf_sort` int DEFAULT NULL COMMENT '货架排序',
  `nx_distributer_goods_shelf_dis_id` int DEFAULT NULL COMMENT '批发商id',
  `nx_distributer_goods_shelf_user_id` bigint DEFAULT NULL COMMENT '负责员工ID',
  PRIMARY KEY (`nx_distributer_goods_shelf_id`)
) ENGINE=InnoDB AUTO_INCREMENT=107 DEFAULT CHARSET=utf8mb3 COMMENT='用户与角色对应关系';

-- ----------------------------
-- Table structure for nx_distributer_goods_shelf_goods
-- ----------------------------
DROP TABLE IF EXISTS `nx_distributer_goods_shelf_goods`;
CREATE TABLE `nx_distributer_goods_shelf_goods` (
  `nx_distributer_goods_shelf_goods_id` int NOT NULL AUTO_INCREMENT COMMENT '货架商品id',
  `nx_DGSG_dis_goods_id` int DEFAULT NULL COMMENT '批发商商品id',
  `nx_DGSG_shelf_id` int DEFAULT NULL COMMENT '货架id',
  `nx_DGSG_sort` int DEFAULT NULL COMMENT '货架商品排序',
  `nx_DGSG_shelf_sort` int DEFAULT NULL COMMENT '货架商品排序',
  `nx_DGSG_shelf_layer` int DEFAULT NULL COMMENT '层尾标记：null 表示非层尾，正整数表示所在层的结束商品',
  `nx_DGSG_shelf_layer_seq` int DEFAULT NULL COMMENT '同一层内序号，从1递增',
  `nx_DGSG_shelf_layer_last` int DEFAULT NULL COMMENT '是否层尾：1=层尾，0=非层尾',
  `nx_DGSG_is_duplicate` int DEFAULT '0' COMMENT '是否重复出现在多个货架：0=未重复（只出现在一个货架），1=重复（出现在多个不同货架）',
  PRIMARY KEY (`nx_distributer_goods_shelf_goods_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4451 DEFAULT CHARSET=utf8mb3 COMMENT='用户与角色对应关系';

-- ----------------------------
-- Table structure for nx_distributer_goods_shelf_stock
-- ----------------------------
DROP TABLE IF EXISTS `nx_distributer_goods_shelf_stock`;
CREATE TABLE `nx_distributer_goods_shelf_stock` (
  `nx_distributer_goods_shelf_stock_id` int NOT NULL AUTO_INCREMENT,
  `nx_dgss_nx_distributer_id` int DEFAULT NULL,
  `nx_dgss_nx_dis_goods_id` int DEFAULT NULL,
  `nx_dgss_nx_dis_goods_father_id` int DEFAULT NULL,
  `nx_dgss_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '批次数量',
  `nx_dgss_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '批次单价',
  `nx_dgss_price_carton` varchar(50) COLLATE utf16_czech_ci DEFAULT NULL COMMENT '外包装采购单价（按箱/按件等）',
  `nx_dgss_selling_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '销售单价',
  `nx_dgss_selling_price_carton` varchar(50) COLLATE utf16_czech_ci DEFAULT NULL COMMENT '外包装建议零售价（按箱/按件等）',
  `nx_dgss_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '批次成本',
  `nx_dgss_rest_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '剩余数量',
  `nx_dgss_rest_weight_show_standard` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '剩余数量显示规格',
  `nx_dgss_rest_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '批次剩余成本',
  `nx_dgss_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '批次日期',
  `nx_dgss_full_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '盘库日期',
  `nx_dgss_out_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '出库日期',
  `nx_dgss_out_full_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '出库时间',
  `nx_dgss_out_hour` int DEFAULT NULL COMMENT '出货小时',
  `nx_dgss_receive_user_id` int DEFAULT NULL COMMENT '接收用户',
  `nx_dgss_status` tinyint DEFAULT NULL COMMENT '批次状态',
  `nx_dgss_nx_pur_goods_id` int DEFAULT NULL COMMENT '批次采购商品id',
  `nx_dgss_week` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '批次周',
  `nx_dgss_month` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '批次月',
  `nx_dgss_year` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '批次年',
  `nx_dgss_time_stamp` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '时间戳',
  `nx_dgss_inventory_full_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '盘库日期',
  `nx_dgss_inventory_date` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '盘库日期',
  `nx_dgss_inventory_week` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '盘库周',
  `nx_dgss_inventory_month` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '盘库月',
  `nx_dgss_inventory_year` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '盘库月',
  `nx_dgss_return_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '退货数量',
  `nx_dgss_return_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '退货成本',
  `nx_dgss_produce_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '制作数量',
  `nx_dgss_produce_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '制作成本',
  `nx_dgss_loss_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '损耗数量',
  `nx_dgss_loss_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '损耗成本',
  `nx_dgss_waste_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '制作数量',
  `nx_dgss_waste_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '制作数量',
  `nx_dgss_profit_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '利润小计',
  `nx_dgss_profit_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '利润重量',
  `nx_dgss_selling_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '销售小计',
  `nx_dgss_after_profit_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '销售利润',
  `nx_dgss_cost_rate` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '成本率',
  `nx_dgss_rest_weight_show_standard_name` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '剩余数量显示规格',
  `nx_dgss_produce_selling_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '销售小计',
  `nx_dgss_nx_shelf_goods_id` int DEFAULT NULL,
  `nx_dgss_nx_department_father_id` int DEFAULT NULL COMMENT '部门父ID',
  `nx_dgss_stock_image` varchar(255) COLLATE utf16_czech_ci DEFAULT NULL COMMENT '库存图片',
  `nx_dgss_stock_remark` text COLLATE utf16_czech_ci COMMENT '库存说明',
  `nx_dgss_return_points` varchar(50) COLLATE utf16_czech_ci DEFAULT NULL COMMENT '返还积分（库存确认后返还给部门的积分，库存金额的75%）',
  `nx_dgss_return_points_time` varchar(19) COLLATE utf16_czech_ci DEFAULT NULL COMMENT '返还积分时间（精确到分钟，格式：YYYY-MM-DD HH:MM）',
  `nx_dgss_trace_report_id` int DEFAULT NULL COMMENT '溯源报告ID',
  `nx_dgss_produce_date` varchar(10) COLLATE utf16_czech_ci DEFAULT NULL COMMENT '生产日期（如 2025-03-18）',
  `nx_dgss_shelf_life` int DEFAULT NULL COMMENT '保质期（数值，如 3、7、15）',
  `nx_dgss_shelf_life_unit` varchar(5) COLLATE utf16_czech_ci DEFAULT NULL COMMENT '保质期单位（天/月/年）',
  `nx_dgss_expiry_date` varchar(10) COLLATE utf16_czech_ci DEFAULT NULL COMMENT '过期日期（如 2025-03-21）',
  PRIMARY KEY (`nx_distributer_goods_shelf_stock_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=179 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_distributer_goods_shelf_stock_reduce
-- ----------------------------
DROP TABLE IF EXISTS `nx_distributer_goods_shelf_stock_reduce`;
CREATE TABLE `nx_distributer_goods_shelf_stock_reduce` (
  `nx_distributer_goods_shelf_stock_reduce_id` int NOT NULL AUTO_INCREMENT,
  `nx_dgssr_nx_distributer_id` int DEFAULT NULL,
  `nx_dgssr_nx_dis_goods_id` int DEFAULT NULL,
  `nx_dgssr_nx_dis_goods_father_id` int DEFAULT NULL,
  `nx_dgssr_nx_stock_id` int DEFAULT NULL COMMENT '批次采购商品id',
  `nx_dgssr_date` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '盘库日期',
  `nx_dgssr_week` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '盘库周',
  `nx_dgssr_month` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '盘库月',
  `nx_dgssr_full_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃时间',
  `nx_dgssr_type` tinyint DEFAULT NULL COMMENT '1,cost;2waste;3loass;4return',
  `nx_dgssr_do_user_id` int DEFAULT NULL COMMENT '执行废弃用户',
  `nx_dgssr_cost_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃用户',
  `nx_dgssr_cost_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃数量',
  `nx_dgssr_waste_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃用户',
  `nx_dgssr_waste_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃数量',
  `nx_dgssr_loss_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃用户',
  `nx_dgssr_loss_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃数量',
  `nx_dgssr_return_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃用户',
  `nx_dgssr_return_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃数量',
  `nx_dgssr_produce_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃用户',
  `nx_dgssr_produce_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '执行废弃数量',
  `nx_dgssr_nx_pur_goods_id` int DEFAULT NULL COMMENT '批次采购商品id',
  `nx_dgssr_goods_inventory_type` tinyint DEFAULT NULL COMMENT '批次采购商品id',
  `nx_dgssr_status` int DEFAULT NULL COMMENT '批次采购商品id',
  `nx_dgssr_nx_dep_order_id` int DEFAULT NULL COMMENT '批次采购商品id',
  PRIMARY KEY (`nx_distributer_goods_shelf_stock_reduce_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=125 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_distributer_invite
-- ----------------------------
DROP TABLE IF EXISTS `nx_distributer_invite`;
CREATE TABLE `nx_distributer_invite` (
  `nx_distributer_invite_id` int NOT NULL AUTO_INCREMENT,
  `inviter_nx_distributer_id` int NOT NULL COMMENT '邀请人/宣传人配送商id',
  `invite_code` varchar(16) COLLATE utf16_czech_ci NOT NULL COMMENT '邀请码',
  `invitee_phone` varchar(20) COLLATE utf16_czech_ci DEFAULT NULL COMMENT '被邀请人手机号(预填)',
  `invite_type` tinyint DEFAULT '1' COMMENT '1=邀请成为我的供货商,2=邀请购买我的产品',
  `status` tinyint DEFAULT '0' COMMENT '0=待注册,1=已注册,2=已过期',
  `invitee_nx_distributer_id` int DEFAULT NULL COMMENT '被邀请人注册成功后的配送商id',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `registered_at` datetime DEFAULT NULL COMMENT '被邀请人注册成功时间',
  `reward_status` tinyint DEFAULT '0' COMMENT '0=待发放,1=已发放',
  `reward_amount` decimal(10,2) DEFAULT NULL COMMENT '奖励金额',
  `reward_type` varchar(32) COLLATE utf16_czech_ci DEFAULT NULL COMMENT '奖励类型:points=试用点数,cash=现金等',
  PRIMARY KEY (`nx_distributer_invite_id`),
  UNIQUE KEY `uk_invite_code` (`invite_code`),
  KEY `idx_inviter` (`inviter_nx_distributer_id`),
  KEY `idx_status` (`status`),
  KEY `idx_reward` (`reward_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci COMMENT='配送商邀请注册记录表';

-- ----------------------------
-- Table structure for nx_distributer_nx_distributer
-- ----------------------------
DROP TABLE IF EXISTS `nx_distributer_nx_distributer`;
CREATE TABLE `nx_distributer_nx_distributer` (
  `nx_distributer_nx_distributer_id` int NOT NULL AUTO_INCREMENT,
  `nx_distributer_id_1` int NOT NULL COMMENT '协作伙伴1（较小ID）',
  `nx_distributer_id_2` int NOT NULL COMMENT '协作伙伴2（较大ID）',
  `invite_type` int DEFAULT NULL COMMENT '1=邀请成为我的供货商,2=邀请购买我的产品',
  `inviter_nx_distributer_id` int DEFAULT NULL COMMENT '发起邀请的配送商id',
  PRIMARY KEY (`nx_distributer_nx_distributer_id`) USING BTREE,
  UNIQUE KEY `uk_distributer_pair` (`nx_distributer_id_1`,`nx_distributer_id_2`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_distributer_pay
-- ----------------------------
DROP TABLE IF EXISTS `nx_distributer_pay`;
CREATE TABLE `nx_distributer_pay` (
  `nx_distributer_pay_id` int NOT NULL AUTO_INCREMENT,
  `nx_ndp_nx_dis_id` int DEFAULT NULL,
  `nx_ndp_pay_subtotal` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_ndp_from_time` date DEFAULT NULL,
  `nx_ndp_stop_time` date DEFAULT NULL,
  `nx_ndp_pay_time` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_ndp_type` tinyint DEFAULT NULL,
  `nx_ndp_status` tinyint DEFAULT NULL,
  `nx_ndp_trade_no` varchar(200) COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_ndp_nx_new_dis_id` int DEFAULT NULL,
  `nx_ndp_buy_quantity` varchar(10) COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_ndp_order_quantity` bigint DEFAULT NULL,
  `nx_ndp_img_url` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_ndp_sell_detail` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_ndp_market_id` int DEFAULT NULL COMMENT '市场ID（外键→sys_city_market）',
  PRIMARY KEY (`nx_distributer_pay_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=58 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_distributer_pay_list
-- ----------------------------
DROP TABLE IF EXISTS `nx_distributer_pay_list`;
CREATE TABLE `nx_distributer_pay_list` (
  `nx_distributer_pay_list_id` int NOT NULL AUTO_INCREMENT,
  `nx_ndpl_nx_dis_id` int DEFAULT NULL,
  `nx_ndpl_pay_subtotal` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_ndpl_pay_time` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_ndpl_type` tinyint DEFAULT NULL,
  `nx_ndpl_status` tinyint DEFAULT NULL,
  `nx_ndpl_pay_date` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_ndpl_nx_department_father_id` int DEFAULT NULL,
  `nx_ndpl_nx_department_id` int DEFAULT NULL,
  `nx_ndpl_nx_db_id` int DEFAULT NULL,
  `nx_ndpl_pay_month` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_ndpl_pay_year` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_ndpl_rest_points` int DEFAULT NULL,
  `nx_ndpl_gb_department_father_id` int DEFAULT NULL,
  `nx_ndpl_gb_department_id` int DEFAULT NULL,
  `nx_ndpl_gb_db_id` int DEFAULT NULL,
  PRIMARY KEY (`nx_distributer_pay_list_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=8593 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_distributer_purchase_batch
-- ----------------------------
DROP TABLE IF EXISTS `nx_distributer_purchase_batch`;
CREATE TABLE `nx_distributer_purchase_batch` (
  `nx_distributer_purchase_batch_id` int NOT NULL AUTO_INCREMENT COMMENT '批发商进货批次id',
  `nx_DPB_status` tinyint DEFAULT NULL COMMENT '批发商进货批次状态',
  `nx_DPB_pay_type` tinyint DEFAULT NULL COMMENT '付款方式:0==现金; 1 ==记账，',
  `nx_DPB_time` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '批发商进货批次时间',
  `nx_DPB_pur_user_id` int DEFAULT NULL COMMENT '批发商进货采购员id',
  `nx_DPB_distributer_id` int DEFAULT NULL COMMENT '批发商id',
  `nx_DPB_date` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DPB_year` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DPB_month` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DPB_sell_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DPB_buy_user_id` int DEFAULT NULL,
  `nx_DPB_sell_user_id` int DEFAULT NULL COMMENT '卖方用户id',
  `nx_DPB_purchase_type` tinyint DEFAULT NULL COMMENT '0 手动订货，1 自动订货',
  `nx_DPB_pay_full_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '付款时间',
  `nx_DPB_order_is_notice` tinyint DEFAULT NULL COMMENT '付款时间',
  `nx_DPB_purchase_week` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DPB_supplier_id` int DEFAULT NULL COMMENT '卖方用户id',
  `nx_DPB_need_date` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DPB_buy_user_open_id` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DPB_sell_user_open_id` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DPB_paste_content` text CHARACTER SET utf16 COLLATE utf16_czech_ci COMMENT '批发商进货批次时间',
  `nx_DPB_nx_department_father_id` int DEFAULT NULL COMMENT '批发商id',
  PRIMARY KEY (`nx_distributer_purchase_batch_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=657 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_distributer_purchase_goods
-- ----------------------------
DROP TABLE IF EXISTS `nx_distributer_purchase_goods`;
CREATE TABLE `nx_distributer_purchase_goods` (
  `nx_distributer_purchase_goods_id` int NOT NULL AUTO_INCREMENT COMMENT '批发商采购商品id',
  `nx_DPG_dis_goods_id` int DEFAULT NULL COMMENT '采购商品id',
  `nx_DPG_dis_goods_father_id` int DEFAULT NULL COMMENT '采购父级商品id',
  `nx_DPG_quantity` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '采购数量',
  `nx_DPG_standard` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '采购规格',
  `nx_DPG_status` tinyint DEFAULT NULL COMMENT '采购状态',
  `nx_DPG_distributer_id` int DEFAULT NULL COMMENT '采购批发商id',
  `nx_DPG_purchase_type` tinyint DEFAULT NULL COMMENT '采购方式：“0订单采购”“1 添加采购”',
  `nx_DPG_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '采购时间',
  `nx_DPG_batch_id` int DEFAULT NULL COMMENT '采购批次号',
  `nx_DPG_buy_user_id` int DEFAULT NULL COMMENT 'jrdh表的用户id（采购员）',
  `nx_DPG_buy_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '采购单价',
  `nx_DPG_buy_quantity` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '采购数量',
  `nx_DPG_orders_amount` int DEFAULT NULL COMMENT '订单采购的订单数量',
  `nx_DPG_type_add_user_id` int DEFAULT NULL COMMENT '添加采购用户id',
  `nx_DPG_apply_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DPG_purchase_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '采购日期',
  `nx_DPG_input_type` tinyint DEFAULT NULL COMMENT '录入方式：0 按总量1 按订单分量',
  `nx_DPG_buy_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DPG_pur_user_id` int DEFAULT NULL COMMENT 'distributerUser表的采购员id',
  `nx_DPG_sell_user_id` int DEFAULT NULL COMMENT 'jrdh表的供货商用户id',
  `nx_DPG_expect_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '协议单价',
  `nx_DPG_expect_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '协议小计',
  `nx_DPG_pay_type` tinyint DEFAULT NULL COMMENT '支付方式',
  `nx_DPG_nx_weight_id` int DEFAULT NULL COMMENT '进货单id',
  `nx_DPG_cost_level` int DEFAULT NULL COMMENT '进货商品级别',
  `nx_DPG_dis_goods_grand_id` int DEFAULT NULL COMMENT '采购父级商品id',
  `nx_DPG_finish_amount` int DEFAULT NULL COMMENT '订单采购的订单数量',
  `nx_DPG_apply_shelf_id` int DEFAULT NULL COMMENT '申请货架ID',
  `nx_DPG_trace_report_id` int DEFAULT NULL COMMENT '溯源报告ID',
  `nx_DPG_produce_date` varchar(10) COLLATE utf16_czech_ci DEFAULT NULL COMMENT '生产日期（如 2025-03-18）',
  `nx_DPG_shelf_life` int DEFAULT NULL COMMENT '保质期（数值，如 3、7、15）',
  `nx_DPG_shelf_life_unit` varchar(5) COLLATE utf16_czech_ci DEFAULT NULL COMMENT '保质期单位（天/月/年）',
  `nx_DPG_expiry_date` varchar(10) COLLATE utf16_czech_ci DEFAULT NULL COMMENT '过期日期（如 2025-03-21）',
  PRIMARY KEY (`nx_distributer_purchase_goods_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=16919 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_distributer_route
-- ----------------------------
DROP TABLE IF EXISTS `nx_distributer_route`;
CREATE TABLE `nx_distributer_route` (
  `nx_distributer_route_id` int NOT NULL COMMENT '线路id',
  `nx_distributer_route_name` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '线路名称',
  `nx_distributer_route_dis_id` int DEFAULT NULL COMMENT '批发商id',
  PRIMARY KEY (`nx_distributer_route_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_distributer_service_city
-- ----------------------------
DROP TABLE IF EXISTS `nx_distributer_service_city`;
CREATE TABLE `nx_distributer_service_city` (
  `nx_distributer_service_city_id` int NOT NULL AUTO_INCREMENT,
  `nx_ds_city_id` int DEFAULT NULL,
  `nx_ds_dis_id` int DEFAULT NULL,
  `nx_ds_city_name` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`nx_distributer_service_city_id`)
) ENGINE=InnoDB AUTO_INCREMENT=145 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_distributer_standard
-- ----------------------------
DROP TABLE IF EXISTS `nx_distributer_standard`;
CREATE TABLE `nx_distributer_standard` (
  `nx_distributer_standard_id` int NOT NULL AUTO_INCREMENT,
  `nx_DS_dis_goods_id` int DEFAULT NULL,
  `nx_DS_standard_name` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DS_standard_file_path` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DS_standard_scale` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DS_standard_error` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DS_standard_sort` int DEFAULT NULL,
  `nx_DS_standard_weight` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`nx_distributer_standard_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5145 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_distributer_supplier
-- ----------------------------
DROP TABLE IF EXISTS `nx_distributer_supplier`;
CREATE TABLE `nx_distributer_supplier` (
  `nx_distributer_supplier_id` int NOT NULL AUTO_INCREMENT COMMENT '批发商供货商id',
  `nx_distributer_supplier_name` varchar(40) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '批发商名称',
  `nx_distributer_supplier_address` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '批发商地址',
  `nx_DS_nx_distributer_id` int DEFAULT NULL COMMENT '批发商id',
  `nx_DS_jrdh_user_id` int DEFAULT NULL COMMENT 'jrdh供货商用户id',
  `nx_DS_jrdh_nx_dis_user_id` int DEFAULT NULL COMMENT '邀请供货商的distributerUserId（采购员）',
  PRIMARY KEY (`nx_distributer_supplier_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=47 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_distributer_user
-- ----------------------------
DROP TABLE IF EXISTS `nx_distributer_user`;
CREATE TABLE `nx_distributer_user` (
  `nx_distributer_user_id` int NOT NULL AUTO_INCREMENT COMMENT '批发商用户id',
  `nx_DIU_wx_avartra_url` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '用户名',
  `nx_DIU_wx_nick_name` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '登陆密码',
  `nx_DIU_wx_open_id` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DIU_wx_phone` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DIU_distributer_id` int DEFAULT NULL,
  `nx_DIU_admin` tinyint DEFAULT NULL,
  `nx_DIU_print_device_id` varchar(40) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DIU_url_change` tinyint DEFAULT NULL,
  `nx_DIU_print_bill_device_id` varchar(40) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_DIU_role_id` tinyint DEFAULT NULL COMMENT '用户角色 1,拣货员;2,打包员;3,司机;',
  `nx_DIU_qy_corp_user_id` int DEFAULT NULL COMMENT '企业用户id',
  `nx_DIU_login_times` int DEFAULT NULL COMMENT '企业用户id',
  `nx_DIU_login_phone` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`nx_distributer_user_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=305 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_distributer_user_role
-- ----------------------------
DROP TABLE IF EXISTS `nx_distributer_user_role`;
CREATE TABLE `nx_distributer_user_role` (
  `nx_distributer_user_role_id` int NOT NULL AUTO_INCREMENT,
  `nx_DUR_user_id` int DEFAULT NULL COMMENT '用户ID',
  `nx_DUR_role_id` int DEFAULT NULL COMMENT '角色ID',
  PRIMARY KEY (`nx_distributer_user_role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COMMENT='用户与角色对应关系';

-- ----------------------------
-- Table structure for nx_distributer_weight
-- ----------------------------
DROP TABLE IF EXISTS `nx_distributer_weight`;
CREATE TABLE `nx_distributer_weight` (
  `nx_distributer_weight_id` int NOT NULL AUTO_INCREMENT COMMENT '称重单id',
  `nx_dw_user_id` int DEFAULT NULL COMMENT '称重用户id',
  `nx_dw_dis_id` int DEFAULT NULL COMMENT '称重disid',
  `nx_dw_weight_total` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '称重单总重量',
  `nx_dw_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '称重单总日期',
  `nx_dw_subtotal` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '称重单总金额',
  `nx_dw_status` tinyint DEFAULT NULL COMMENT '称重单状态',
  `nx_dw_order_names` varchar(2000) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '称重单总金额',
  `nx_dw_dep_father_id` int DEFAULT NULL COMMENT '称重disid',
  `nx_dw_gb_dep_father_id` int DEFAULT NULL COMMENT '称重disid',
  `nx_dw_res_father_id` int DEFAULT NULL COMMENT '称重disid',
  `nx_dw_trade_no` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '称重单号',
  `nx_dw_type` tinyint DEFAULT NULL COMMENT '1 门店称重单 2 采购单3出库单',
  `nx_dw_item_count` int DEFAULT NULL COMMENT '称重disid',
  `nx_dw_item_finish_count` int DEFAULT NULL COMMENT '称重disid',
  PRIMARY KEY (`nx_distributer_weight_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_e_commerce
-- ----------------------------
DROP TABLE IF EXISTS `nx_e_commerce`;
CREATE TABLE `nx_e_commerce` (
  `nx_e_commerce_id` int NOT NULL AUTO_INCREMENT,
  `nx_e_commerce_name` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_e_commerce_img` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_e_commerce_gb_dis_id` int DEFAULT NULL,
  PRIMARY KEY (`nx_e_commerce_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_e_commerce_community
-- ----------------------------
DROP TABLE IF EXISTS `nx_e_commerce_community`;
CREATE TABLE `nx_e_commerce_community` (
  `nx_ECC_id` int NOT NULL AUTO_INCREMENT COMMENT '批发商社区id',
  `nx_ECC_e_id` int DEFAULT NULL,
  `nx_ECC_community_id` int DEFAULT NULL,
  PRIMARY KEY (`nx_ECC_id`)
) ENGINE=InnoDB AUTO_INCREMENT=26 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_e_commerce_supplier
-- ----------------------------
DROP TABLE IF EXISTS `nx_e_commerce_supplier`;
CREATE TABLE `nx_e_commerce_supplier` (
  `nx_commerce_supplier_id` int NOT NULL AUTO_INCREMENT COMMENT '批发商供货商id',
  `nx_CS_commerce_id` int DEFAULT NULL COMMENT '平台id',
  `nx_CS_supplier_id` int DEFAULT NULL COMMENT '供货商id',
  PRIMARY KEY (`nx_commerce_supplier_id`)
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_gb_distibuter_user_coupon
-- ----------------------------
DROP TABLE IF EXISTS `nx_gb_distibuter_user_coupon`;
CREATE TABLE `nx_gb_distibuter_user_coupon` (
  `nx_gb_distributer_user_coupon_id` int NOT NULL AUTO_INCREMENT,
  `nx_gduc_coupon_id` int DEFAULT NULL,
  `nx_gduc_gb_distibtuer_user_id` int DEFAULT NULL,
  `nx_gduc_share_user_id` int DEFAULT NULL,
  `nx_gduc_nx_distributer_id` int DEFAULT NULL,
  `nx_gduc_status` int DEFAULT NULL,
  `nx_gduc_type` int DEFAULT NULL,
  `nx_gduc_use_order_id` int DEFAULT NULL,
  `nx_gduc_from_share_user_id` int DEFAULT NULL,
  `nx_gduc_share_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_gduc_gb_distibtuer_id` int DEFAULT NULL,
  `nx_gduc_start_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_gduc_stop_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_gduc_start_time_zone` date DEFAULT NULL,
  `nx_gduc_stop_time_zone` date DEFAULT NULL,
  PRIMARY KEY (`nx_gb_distributer_user_coupon_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_goods
-- ----------------------------
DROP TABLE IF EXISTS `nx_goods`;
CREATE TABLE `nx_goods` (
  `nx_goods_id` int NOT NULL AUTO_INCREMENT COMMENT '商品id',
  `nx_goods_name` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '商品名称',
  `nx_goods_standardname` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '商品规格',
  `nx_goods_detail` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '商品描述',
  `nx_goods_brand` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '商品品牌',
  `nx_goods_place` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '商品产地',
  `nx_goods_pinyin` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '拼音',
  `nx_goods_py` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '简拼',
  `nx_goods_father_id` int DEFAULT NULL COMMENT '父级id',
  `nx_goods_sort` int DEFAULT NULL COMMENT '商品排序',
  `nx_goods_file` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '图片',
  `nx_goods_standard_amount` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '销售规格数量',
  `nx_goods_standard_weight` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_goods_apply_nx_distributer_id` int DEFAULT NULL COMMENT '申请批发商id',
  `nx_goods_level` int DEFAULT NULL COMMENT '申请批发商id',
  `nx_goods_grand_id` int DEFAULT NULL COMMENT '父级id',
  `nx_goods_is_oldest_son` int DEFAULT NULL COMMENT '父级id',
  `nx_goods_sons_sort` int DEFAULT NULL COMMENT '父级id',
  `nx_goods_is_hidden` int DEFAULT NULL COMMENT '父级id',
  `nx_goods_file_big` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '图片',
  `nx_goods_great_grand_id` int DEFAULT NULL COMMENT '以级id',
  `nx_goods_quantity_days` int DEFAULT NULL COMMENT '保鲜天数',
  `nx_goods_carton_unit` varchar(255) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '外箱名称',
  `nx_goods_items_per_carton` varchar(50) COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`nx_goods_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=106289 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_goods_price
-- ----------------------------
DROP TABLE IF EXISTS `nx_goods_price`;
CREATE TABLE `nx_goods_price` (
  `nx_goods_price_id` int NOT NULL AUTO_INCREMENT COMMENT '商品价格id',
  `nx_gp_nx_goods_id` int DEFAULT NULL COMMENT '商品id',
  `nx_gp_date` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '日期',
  `nx_gp_lowest_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '最低单价',
  `nx_gp_lowest_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '最低单价数量',
  `nx_gp_lowest_nx_distributer_id` int DEFAULT NULL COMMENT '最低单价批发商',
  `nx_gp_lowest_jrdh_supplier_id` int DEFAULT NULL COMMENT '最低单价菜商',
  `nx_gp_highest_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '最高单价',
  `nx_gp_highest_weight` varchar(10) COLLATE utf16_czech_ci DEFAULT NULL COMMENT '最高单价数量',
  `nx_gp_highest_nx_distributer_id` int DEFAULT NULL COMMENT '最高单价批发商',
  `nx_gp_highest_jrdh_supplier_id` int DEFAULT NULL COMMENT '最高单价菜商',
  `nx_gp_sys_city_id` int DEFAULT NULL COMMENT '单价城市 id',
  `nx_gp_sys_market_id` int DEFAULT NULL COMMENT '单价批发市场 id',
  PRIMARY KEY (`nx_goods_price_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=109116 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_jrdh_business_relation
-- ----------------------------
DROP TABLE IF EXISTS `nx_jrdh_business_relation`;
CREATE TABLE `nx_jrdh_business_relation` (
  `nx_jrdh_business_relation_id` int NOT NULL,
  `nx_jrdh_br_buyer_user_id` int DEFAULT NULL,
  `nx_jrdh_br_nx_distributer_id` int DEFAULT NULL,
  `nx_jrdh_br_seller_user_id` int DEFAULT NULL,
  PRIMARY KEY (`nx_jrdh_business_relation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_jrdh_supplier
-- ----------------------------
DROP TABLE IF EXISTS `nx_jrdh_supplier`;
CREATE TABLE `nx_jrdh_supplier` (
  `nx_jrdh_supplier_id` int NOT NULL AUTO_INCREMENT COMMENT '供货商id',
  `nx_jrdhs_supplier_name` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '供货商名称',
  `nx_jrdhs_gb_distributer_id` int DEFAULT NULL COMMENT 'gbDisid',
  `nx_jrdhs_gb_department_id` int DEFAULT NULL COMMENT 'gbDepid',
  `nx_jrdhs_user_id` int DEFAULT NULL COMMENT '接单元id',
  `nx_jrdhs_nx_distributer_id` int DEFAULT NULL COMMENT 'gbDisid',
  `nx_jrdhs_nx_community_id` int DEFAULT NULL COMMENT 'gbDisid',
  `nx_jrdhs_nx_pur_user_id` int DEFAULT NULL COMMENT 'gbDisid',
  `nx_jrdhs_gb_pur_user_id` int DEFAULT NULL COMMENT 'gbDisid',
  `nx_jrdhs_comm_pur_user_id` int DEFAULT NULL COMMENT 'gbDisid',
  `nx_jrdhs_nx_jrdh_buy_user_id` int DEFAULT NULL COMMENT 'gbDisid',
  `nx_jrdhs_status` tinyint(1) DEFAULT NULL COMMENT '供货商名称',
  `nx_jrdhs_sys_city_id` int DEFAULT NULL COMMENT 'gbDisid',
  `nx_jrdhs_sys_market_id` int DEFAULT NULL COMMENT 'gbDisid',
  PRIMARY KEY (`nx_jrdh_supplier_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=34 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_jrdh_user
-- ----------------------------
DROP TABLE IF EXISTS `nx_jrdh_user`;
CREATE TABLE `nx_jrdh_user` (
  `nx_jrdh_user_id` int NOT NULL AUTO_INCREMENT COMMENT '订货用户id',
  `nx_jrdh_wx_avartra_url` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订货用户微信头像',
  `nx_jrdh_wx_nick_name` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订货用户微信昵称',
  `nx_jrdh_wx_open_id` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订货用户微信openid',
  `nx_jrdh_wx_phone` varchar(15) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订货户微信手机号码',
  `nx_jrdh_join_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '零售商用户加入日期',
  `nx_jrdh_nx_distributer_id` int DEFAULT NULL COMMENT '批发商id',
  `nx_jrdh_nx_purchaser_user_id` int DEFAULT NULL COMMENT '批发商用户id',
  `nx_jrdh_nx_community_id` int DEFAULT NULL COMMENT '批发商id',
  `nx_jrdh_nx_comm_purchaser_user_id` int DEFAULT NULL COMMENT '批发商用户id',
  `nx_jrdh_url_change` int DEFAULT NULL COMMENT '批发商用户id',
  `nx_jrdh_admin` int DEFAULT NULL COMMENT '0 seller, 1nxpurchaser 2 gbpurchaser ',
  `nx_jrdh_gb_distributer_id` int DEFAULT NULL COMMENT '批发商id',
  `nx_jrdh_gb_department_id` int DEFAULT NULL COMMENT '批发商用户id',
  `nx_jrdh_gb_department_user_id` int DEFAULT NULL COMMENT '批发商用户id',
  `nx_jrdh_device_id` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '批发商用户id',
  `nx_jrdh_device_print_id` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '批发商用户id',
  `nx_jrdh_auth_supplier_id` int DEFAULT NULL COMMENT '批发商id',
  PRIMARY KEY (`nx_jrdh_user_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=39 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_jrdh_user_auth_supplier_id
-- ----------------------------
DROP TABLE IF EXISTS `nx_jrdh_user_auth_supplier_id`;
CREATE TABLE `nx_jrdh_user_auth_supplier_id` (
  `nx_jrdh_user_auth_supplier_id` int NOT NULL AUTO_INCREMENT COMMENT '订货用户id',
  `nx_jrdhas_nx_distributer_id` int DEFAULT NULL COMMENT '批发商id',
  `nx_jrdhas_nx_community_id` int DEFAULT NULL COMMENT '批发商id',
  `nx_jrdhas_gb_distributer_id` int DEFAULT NULL COMMENT '批发商id',
  `nx_jrdhas_gb_department_id` int DEFAULT NULL COMMENT '批发商用户id',
  `nx_jrdhas_supplier_id` int DEFAULT NULL COMMENT '批发商id',
  `nx_jrdhas_user_id` int DEFAULT NULL COMMENT '批发商id',
  PRIMARY KEY (`nx_jrdh_user_auth_supplier_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_label
-- ----------------------------
DROP TABLE IF EXISTS `nx_label`;
CREATE TABLE `nx_label` (
  `nx_label_id` int NOT NULL AUTO_INCREMENT,
  `nx_label_name` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_label_hot` int DEFAULT NULL,
  `nx_label_type_id` int DEFAULT NULL,
  PRIMARY KEY (`nx_label_id`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_label_type
-- ----------------------------
DROP TABLE IF EXISTS `nx_label_type`;
CREATE TABLE `nx_label_type` (
  `nx_label_type_id` int NOT NULL AUTO_INCREMENT,
  `nx_label_type_name` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_label_amount` int DEFAULT NULL,
  `nx_label_type_sort` int DEFAULT NULL,
  PRIMARY KEY (`nx_label_type_id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_machine_alert_record
-- ----------------------------
DROP TABLE IF EXISTS `nx_machine_alert_record`;
CREATE TABLE `nx_machine_alert_record` (
  `nx_ar_id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `nx_ar_device_id` int NOT NULL COMMENT '设备ID（外键→nx_printer_device）',
  `nx_ar_manager_id` int NOT NULL COMMENT '接收管理员ID（外键→nx_market_manager）',
  `nx_ar_alert_level` tinyint NOT NULL COMMENT '提醒级别（1-4）',
  `nx_ar_paper_count` int NOT NULL COMMENT '触发时的纸张数量',
  `nx_ar_message` varchar(500) DEFAULT NULL COMMENT '提醒内容',
  `nx_ar_send_status` tinyint DEFAULT '2' COMMENT '发送状态（0-失败 1-成功 2-待发送）',
  `nx_ar_send_time` datetime DEFAULT NULL COMMENT '发送时间',
  `nx_ar_is_cleared` tinyint DEFAULT '0' COMMENT '是否已清除（0-未清除 1-已清除）【防重复关键字段】',
  `nx_ar_clear_time` datetime DEFAULT NULL COMMENT '清除时间（加纸后）',
  `nx_ar_is_read` tinyint DEFAULT '0' COMMENT '是否已读（0-未读 1-已读）',
  `nx_ar_read_time` datetime DEFAULT NULL COMMENT '已读时间',
  `nx_ar_create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`nx_ar_id`),
  KEY `idx_device_id` (`nx_ar_device_id`),
  KEY `idx_manager_id` (`nx_ar_manager_id`),
  KEY `idx_cleared` (`nx_ar_is_cleared`,`nx_ar_alert_level`,`nx_ar_device_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='提醒记录表';

-- ----------------------------
-- Table structure for nx_machine_alert_threshold
-- ----------------------------
DROP TABLE IF EXISTS `nx_machine_alert_threshold`;
CREATE TABLE `nx_machine_alert_threshold` (
  `nx_at_id` int NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  `nx_at_device_id` int NOT NULL COMMENT '设备ID（外键→nx_printer_device）',
  `nx_at_level` tinyint NOT NULL COMMENT '提醒级别（1-低 2-中 3-高 4-紧急）',
  `nx_at_threshold` int NOT NULL COMMENT '阈值（剩余张数）',
  `nx_at_message` varchar(200) DEFAULT NULL COMMENT '提醒消息模板',
  `nx_at_enable` tinyint DEFAULT '1' COMMENT '是否启用（0-否 1-是）',
  `nx_at_create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`nx_at_id`),
  UNIQUE KEY `uk_device_level` (`nx_at_device_id`,`nx_at_level`),
  KEY `idx_device_id` (`nx_at_device_id`)
) ENGINE=InnoDB AUTO_INCREMENT=29 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='提醒阈值配置表';

-- ----------------------------
-- Table structure for nx_machine_device_manager
-- ----------------------------
DROP TABLE IF EXISTS `nx_machine_device_manager`;
CREATE TABLE `nx_machine_device_manager` (
  `nx_dm_id` int NOT NULL AUTO_INCREMENT COMMENT '绑定ID',
  `nx_dm_device_id` int NOT NULL COMMENT '设备ID（外键→nx_printer_device）',
  `nx_dm_manager_id` int NOT NULL COMMENT '管理员ID（外键→nx_market_manager）',
  `nx_dm_notify_level` tinyint DEFAULT '1' COMMENT '通知级别（1-4，≥此级别才通知）',
  `nx_dm_is_primary` tinyint DEFAULT '0' COMMENT '是否主要责任人（0-否 1-是，接收所有级别）',
  `nx_dm_enable` tinyint DEFAULT '1' COMMENT '是否启用（0-否 1-是）',
  `nx_dm_create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
  PRIMARY KEY (`nx_dm_id`),
  UNIQUE KEY `uk_device_manager` (`nx_dm_device_id`,`nx_dm_manager_id`),
  KEY `idx_device_id` (`nx_dm_device_id`),
  KEY `idx_manager_id` (`nx_dm_manager_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='设备责任人绑定表';

-- ----------------------------
-- Table structure for nx_machine_market_manager
-- ----------------------------
DROP TABLE IF EXISTS `nx_machine_market_manager`;
CREATE TABLE `nx_machine_market_manager` (
  `nx_mm_id` int NOT NULL AUTO_INCREMENT COMMENT '管理员ID',
  `nx_mm_market_id` int NOT NULL COMMENT '所属市场ID（外键→sys_city_market）',
  `nx_mm_wx_openid` varchar(100) NOT NULL COMMENT '微信OpenID（唯一）',
  `nx_mm_wx_unionid` varchar(100) DEFAULT NULL COMMENT '微信UnionID',
  `nx_mm_wx_nickname` varchar(100) DEFAULT NULL COMMENT '微信昵称',
  `nx_mm_wx_avatar` varchar(200) DEFAULT NULL COMMENT '微信头像URL',
  `nx_mm_phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `nx_mm_name` varchar(50) DEFAULT NULL COMMENT '真实姓名',
  `nx_mm_role` tinyint DEFAULT '1' COMMENT '角色（1-普通管理员 2-市场主管）',
  `nx_mm_status` tinyint DEFAULT '1' COMMENT '状态（0-禁用 1-启用）',
  `nx_mm_subscribe_status` tinyint DEFAULT '0' COMMENT '模板消息订阅状态（0-未订阅 1-已订阅）',
  `nx_mm_create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `nx_mm_last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  PRIMARY KEY (`nx_mm_id`),
  UNIQUE KEY `uk_openid` (`nx_mm_wx_openid`),
  KEY `idx_market_id` (`nx_mm_market_id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='市场管理员表';

-- ----------------------------
-- Table structure for nx_machine_paper_refill_record
-- ----------------------------
DROP TABLE IF EXISTS `nx_machine_paper_refill_record`;
CREATE TABLE `nx_machine_paper_refill_record` (
  `nx_prr_id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `nx_prr_device_id` int NOT NULL COMMENT '设备ID（外键→nx_printer_device）',
  `nx_prr_before_count` int NOT NULL COMMENT '加纸前数量（张）',
  `nx_prr_add_count` int NOT NULL COMMENT '增加数量（张）',
  `nx_prr_waste_count` int DEFAULT '0' COMMENT '作废数量',
  `nx_prr_after_count` int NOT NULL COMMENT '加纸后数量（张）',
  `nx_prr_operator_id` int NOT NULL COMMENT '操作人ID（外键→nx_market_manager）',
  `nx_prr_operator_name` varchar(50) DEFAULT NULL COMMENT '操作人姓名',
  `nx_prr_refill_type` tinyint DEFAULT '1' COMMENT '加纸类型（1-正常加纸 2-初始化 3-手动校准）',
  `nx_prr_paper_type` int DEFAULT NULL COMMENT '纸张类型(1=整张,2=半张,3=三分之一张)',
  `nx_prr_remark` varchar(200) DEFAULT NULL COMMENT '备注',
  `nx_prr_create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`nx_prr_id`),
  KEY `idx_device_id` (`nx_prr_device_id`),
  KEY `idx_operator_id` (`nx_prr_operator_id`),
  KEY `idx_create_time` (`nx_prr_create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='加纸记录表';

-- ----------------------------
-- Table structure for nx_machine_print_record
-- ----------------------------
DROP TABLE IF EXISTS `nx_machine_print_record`;
CREATE TABLE `nx_machine_print_record` (
  `nx_pr_id` int NOT NULL AUTO_INCREMENT COMMENT '打印记录ID',
  `nx_pr_bill_id` int NOT NULL COMMENT '单据ID（外键→nx_department_bill.nx_department_bill_id）',
  `nx_pr_device_id` int NOT NULL COMMENT '打印机设备ID（外键→nx_machine_printer_device.nx_pd_id）',
  `nx_pr_market_id` int NOT NULL COMMENT '市场ID',
  `nx_pr_distributer_id` int NOT NULL COMMENT '配送商ID',
  `nx_pr_paper_type` tinyint NOT NULL DEFAULT '1' COMMENT '纸张类型（1-整张 2-半张 3-三分之一张）',
  `nx_pr_paper_count` int NOT NULL DEFAULT '1' COMMENT '消耗纸张数量（张）',
  `nx_pr_print_time` datetime NOT NULL COMMENT '打印时间',
  `nx_pr_print_status` tinyint DEFAULT '1' COMMENT '打印状态（1-成功 0-失败）',
  `nx_pr_bill_total` decimal(10,2) DEFAULT NULL COMMENT '单据金额（便于统计总金额）',
  `nx_pr_bill_date` date DEFAULT NULL COMMENT '单据日期（便于按日期统计）',
  `nx_pr_bill_trade_no` varchar(50) DEFAULT NULL COMMENT '单据流水号',
  `nx_pr_operator_id` int DEFAULT NULL COMMENT '操作人ID',
  `nx_pr_operator_name` varchar(50) DEFAULT NULL COMMENT '操作人姓名',
  `nx_pr_remark` varchar(200) DEFAULT NULL COMMENT '备注',
  `nx_pr_create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `nx_pr_distributer_type` int DEFAULT '0' COMMENT '打印时配送商状态（-1体验 0正式）',
  PRIMARY KEY (`nx_pr_id`),
  KEY `idx_device_time` (`nx_pr_device_id`,`nx_pr_print_time`),
  KEY `idx_market_time` (`nx_pr_market_id`,`nx_pr_print_time`),
  KEY `idx_distributer_time` (`nx_pr_distributer_id`,`nx_pr_print_time`),
  KEY `idx_bill_id` (`nx_pr_bill_id`),
  KEY `idx_print_time` (`nx_pr_print_time`),
  KEY `idx_bill_date` (`nx_pr_bill_date`),
  CONSTRAINT `fk_pr_device` FOREIGN KEY (`nx_pr_device_id`) REFERENCES `nx_machine_printer_device` (`nx_pd_id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='自助打印统计表';

-- ----------------------------
-- Table structure for nx_machine_printer_device
-- ----------------------------
DROP TABLE IF EXISTS `nx_machine_printer_device`;
CREATE TABLE `nx_machine_printer_device` (
  `nx_pd_id` int NOT NULL AUTO_INCREMENT COMMENT '设备ID',
  `nx_pd_market_id` int NOT NULL COMMENT '所属市场ID（外键→sys_city_market）',
  `nx_pd_device_no` varchar(50) NOT NULL COMMENT '设备编号（唯一，格式：PD202410140001）',
  `nx_pd_device_name` varchar(100) DEFAULT NULL COMMENT '设备名称（如"1号打印机"）',
  `nx_pd_model` varchar(50) DEFAULT 'Epson LQ-730K' COMMENT '设备型号',
  `nx_pd_paper_type` tinyint DEFAULT '1' COMMENT '纸张类型（1-整张 2-半张 3-三分之一张）',
  `nx_pd_print_price` decimal(10,2) DEFAULT '0.00' COMMENT '打印单据价格（元/张）',
  `nx_pd_location` varchar(100) DEFAULT NULL COMMENT '设备位置（如"北区出口"）',
  `nx_pd_paper_count` int DEFAULT '0' COMMENT '当前纸张数量（张）',
  `nx_pd_paper_max` int DEFAULT '1000' COMMENT '纸张最大容量（张）',
  `nx_pd_status` tinyint DEFAULT '1' COMMENT '设备状态（0-离线 1-正常 2-故障 3-缺纸）',
  `nx_pd_qr_code` varchar(200) DEFAULT NULL COMMENT '设备二维码URL',
  `nx_pd_install_date` datetime DEFAULT NULL COMMENT '安装日期',
  `nx_pd_last_update_time` datetime DEFAULT NULL COMMENT '余量最后更新时间',
  `nx_pd_create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`nx_pd_id`),
  UNIQUE KEY `uk_device_no` (`nx_pd_device_no`),
  KEY `idx_market_id` (`nx_pd_market_id`)
) ENGINE=InnoDB AUTO_INCREMENT=32 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='打印设备表';

-- ----------------------------
-- Table structure for nx_market_price_plan
-- ----------------------------
DROP TABLE IF EXISTS `nx_market_price_plan`;
CREATE TABLE `nx_market_price_plan` (
  `nx_mpp_id` int NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `nx_mpp_market_id` int NOT NULL COMMENT '市场ID（外键→sys_city_market）',
  `nx_mpp_type` tinyint NOT NULL COMMENT '方案类型（0-流量 1-设备）',
  `nx_mpp_plan_name` varchar(100) COLLATE utf16_czech_ci NOT NULL COMMENT '方案名称',
  `nx_mpp_quantity` varchar(20) COLLATE utf16_czech_ci NOT NULL COMMENT '数量/规格（如：10万条、便携式打印机）',
  `nx_mpp_price` int NOT NULL COMMENT '价格（单位：分）',
  `nx_mpp_unit_price` varchar(20) COLLATE utf16_czech_ci DEFAULT NULL COMMENT '单价说明（如：1分/条、便携式小票打印机）',
  `nx_mpp_description` text COLLATE utf16_czech_ci COMMENT '方案描述',
  `nx_mpp_image_url` varchar(200) COLLATE utf16_czech_ci DEFAULT NULL COMMENT '图片URL',
  `nx_mpp_sort_order` int DEFAULT '0' COMMENT '排序（数字越小越靠前）',
  `nx_mpp_status` tinyint DEFAULT '1' COMMENT '状态（1-启用 0-禁用）',
  `nx_mpp_create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `nx_mpp_update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`nx_mpp_id`),
  KEY `idx_market_type` (`nx_mpp_market_id`,`nx_mpp_type`),
  KEY `idx_market_status` (`nx_mpp_market_id`,`nx_mpp_status`),
  KEY `idx_sort` (`nx_mpp_sort_order`),
  CONSTRAINT `nx_market_price_plan_ibfk_1` FOREIGN KEY (`nx_mpp_market_id`) REFERENCES `sys_city_market` (`sys_city_market_id`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci COMMENT='市场价格方案表';

-- ----------------------------
-- Table structure for nx_ocr_task
-- ----------------------------
DROP TABLE IF EXISTS `nx_ocr_task`;
CREATE TABLE `nx_ocr_task` (
  `nx_ocr_task_id` int NOT NULL AUTO_INCREMENT COMMENT 'OCR任务ID（主键）',
  `nx_ocr_task_file_name` varchar(255) DEFAULT NULL COMMENT '文件名（原始文件名）',
  `nx_ocr_task_image_path` varchar(500) DEFAULT NULL COMMENT '图片存储路径（相对路径，如：ocrImages/20250124/xxx.jpg）',
  `nx_ocr_task_total_orders` int DEFAULT '0' COMMENT '总订单条数（初步解析出来的所有订单的总数）',
  `nx_ocr_task_completed_orders` int DEFAULT '0' COMMENT '已完成订单数（订单状态为0的订单数量）',
  `nx_ocr_task_pending_orders` int DEFAULT '0' COMMENT '未完成订单数（订单状态为-2的订单数量）',
  `nx_ocr_task_upload_time` varchar(50) DEFAULT NULL COMMENT '上传时间（记录图片或文件被上传的时间点）',
  `nx_ocr_task_upload_user_id` int DEFAULT NULL COMMENT '上传用户ID（负责上传图片的用户ID）',
  `nx_ocr_task_upload_user_name` varchar(100) DEFAULT NULL COMMENT '上传用户名称（负责上传图片的用户名称）',
  `nx_ocr_task_processor_user_id` int DEFAULT NULL COMMENT '处理人ID（负责解析和处理这些订单的工作人员ID）',
  `nx_ocr_task_processor_user_name` varchar(100) DEFAULT NULL COMMENT '处理人名称（负责解析和处理这些订单的工作人员名称）',
  `nx_ocr_task_status` int DEFAULT '0' COMMENT '任务状态（0=处理中，1=已完成，2=部分完成）',
  `nx_ocr_task_create_date` varchar(50) DEFAULT NULL COMMENT '创建时间',
  `nx_ocr_task_update_date` varchar(50) DEFAULT NULL COMMENT '更新时间',
  `nx_ocr_task_distributer_id` int DEFAULT '0' COMMENT '总订单条数（初步解析出来的所有订单的总数）',
  `nx_ocr_task_department_id` int DEFAULT '0' COMMENT '总订单条数（初步解析出来的所有订单的总数）',
  `nx_ocr_task_department_father_id` int DEFAULT '0' COMMENT '总订单条数（初步解析出来的所有订单的总数）',
  `nx_ocr_task_ocr_text` text COMMENT 'OCR识别的原始文本内容（用于后续使用 DeepSeek 重新解析）',
  `nx_ocr_task_type` int DEFAULT NULL COMMENT '任务类型（1=图片，2=Excel，3=文字）',
  PRIMARY KEY (`nx_ocr_task_id`),
  KEY `idx_upload_user` (`nx_ocr_task_upload_user_id`),
  KEY `idx_processor_user` (`nx_ocr_task_processor_user_id`),
  KEY `idx_status` (`nx_ocr_task_status`),
  KEY `idx_upload_time` (`nx_ocr_task_upload_time`)
) ENGINE=InnoDB AUTO_INCREMENT=403 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='OCR任务表';

-- ----------------------------
-- Table structure for nx_order_ocr_training_data
-- ----------------------------
DROP TABLE IF EXISTS `nx_order_ocr_training_data`;
CREATE TABLE `nx_order_ocr_training_data` (
  `nx_otd_id` int NOT NULL AUTO_INCREMENT COMMENT '训练数据ID',
  `nx_otd_order_id` int DEFAULT NULL COMMENT '订单ID',
  `nx_otd_department_id` int DEFAULT NULL COMMENT '部门ID',
  `nx_otd_department_father_id` int DEFAULT NULL COMMENT '父级部门ID',
  `nx_otd_distributer_id` int DEFAULT NULL COMMENT '分销商ID',
  `nx_otd_dis_goods_id` int DEFAULT NULL COMMENT '商品ID（分销商商品ID）',
  `nx_otd_original_goods_name` varchar(255) DEFAULT NULL COMMENT '原始商品名称值',
  `nx_otd_is_name_manually_annotated` int DEFAULT '0' COMMENT '商品名称是否手动标注（0=未标注，1=已标注）',
  `nx_otd_final_goods_name` varchar(255) DEFAULT NULL COMMENT '最终确认的商品名称值',
  `nx_otd_deepseek_recommended_name` varchar(255) DEFAULT NULL COMMENT 'DeepSeek 推荐的商品名称（纠错后的名称）',
  `nx_otd_original_quantity` varchar(50) DEFAULT NULL COMMENT '原始订货数量值',
  `nx_otd_is_quantity_manually_annotated` int DEFAULT '0' COMMENT '订货数量是否手动标注（0=未标注，1=已标注）',
  `nx_otd_final_quantity` varchar(50) DEFAULT NULL COMMENT '最终确认的订货数量值',
  `nx_otd_original_standard` varchar(100) DEFAULT NULL COMMENT '原始订货规格值',
  `nx_otd_is_standard_manually_annotated` int DEFAULT '0' COMMENT '订货规格是否手动标注（0=未标注，1=已标注）',
  `nx_otd_final_standard` varchar(100) DEFAULT NULL COMMENT '最终确认的订货规格值',
  `nx_otd_original_standard_weight` varchar(50) DEFAULT NULL COMMENT '原始规格重量值',
  `nx_otd_is_standard_weight_manually_annotated` int DEFAULT '0' COMMENT '规格重量是否手动标注（0=未标注，1=已标注）',
  `nx_otd_final_standard_weight` varchar(50) DEFAULT NULL COMMENT '最终确认的规格重量值',
  `nx_otd_original_remark` varchar(500) DEFAULT NULL COMMENT '原始备注值',
  `nx_otd_is_remark_manually_annotated` int DEFAULT '0' COMMENT '备注是否手动标注（0=未标注，1=已标注）',
  `nx_otd_final_remark` varchar(500) DEFAULT NULL COMMENT '最终确认的备注值',
  `nx_otd_data_source` varchar(50) DEFAULT NULL COMMENT '数据来源',
  `nx_otd_create_date` varchar(50) DEFAULT NULL COMMENT '创建时间',
  `nx_otd_update_date` varchar(50) DEFAULT NULL COMMENT '更新时间',
  `nx_otd_create_user_id` int DEFAULT NULL COMMENT '创建人ID',
  `nx_otd_ocr_text` text COMMENT 'OCR原文（该商品对应的OCR文本行，清洗后）',
  PRIMARY KEY (`nx_otd_id`),
  KEY `idx_order_id` (`nx_otd_order_id`),
  KEY `idx_department_id` (`nx_otd_department_id`),
  KEY `idx_distributer_id` (`nx_otd_distributer_id`),
  KEY `idx_dis_goods_id` (`nx_otd_dis_goods_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5776 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单OCR训练数据表';

-- ----------------------------
-- Table structure for nx_order_template
-- ----------------------------
DROP TABLE IF EXISTS `nx_order_template`;
CREATE TABLE `nx_order_template` (
  `nx_order_template_id` int NOT NULL AUTO_INCREMENT,
  `nx_OD_file_path` varchar(300) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_OD_name` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_OD_customer_user_id` int DEFAULT NULL,
  `nx_OD_item_amount` int DEFAULT NULL,
  PRIMARY KEY (`nx_order_template_id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_order_template_item
-- ----------------------------
DROP TABLE IF EXISTS `nx_order_template_item`;
CREATE TABLE `nx_order_template_item` (
  `nx_OT_item_id` int NOT NULL AUTO_INCREMENT,
  `nx_OT_dis_goods_id` int DEFAULT NULL,
  `nx_OT_amount` float(4,1) DEFAULT NULL,
  `nx_OT_order_template_id` int DEFAULT NULL,
  `nx_OT_customer_user_id` int DEFAULT NULL,
  `nx_OT_dis_goods_color` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`nx_OT_item_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_orders
-- ----------------------------
DROP TABLE IF EXISTS `nx_orders`;
CREATE TABLE `nx_orders` (
  `nx_orders_id` int NOT NULL AUTO_INCREMENT COMMENT '订单id',
  `nx_orders_distributer_id` int DEFAULT NULL COMMENT '批发商id',
  `nx_orders_community_id` int DEFAULT NULL COMMENT '订单社区id',
  `nx_orders_customer_id` int DEFAULT NULL COMMENT '订单客户id',
  `nx_orders_user_id` int DEFAULT NULL COMMENT '订单用户id',
  `nx_orders_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订单日期',
  `nx_orders_status` tinyint DEFAULT NULL COMMENT '订单状态',
  `nx_orders_service` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订单送达时间',
  `nx_orders_amount` float(10,0) DEFAULT NULL COMMENT '订单总金额',
  `nx_orders_service_date` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订单送到日期',
  `nx_orders_service_time` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订单送到时间',
  `nx_orders_weigh_user_id` int DEFAULT NULL COMMENT '订单称重用户id',
  `nx_orders_delivery_user_id` int DEFAULT NULL COMMENT '订单配送员工id',
  `nx_orders_sub_amount` int DEFAULT NULL COMMENT '订单子商品数量',
  `nx_orders_sub_finished` int DEFAULT NULL COMMENT '订单子商品完成数量',
  `nx_orders_weigh_number` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订单称重订单号',
  `nx_orders_payment_status` tinyint DEFAULT NULL COMMENT '订单支付状态',
  `nx_orders_payment_send_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订单支付发送时间',
  `nx_orders_payment_time` varchar(0) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订单支付时间',
  `nx_orders_type` tinyint DEFAULT NULL COMMENT '订单类型 0先付款1后付款',
  PRIMARY KEY (`nx_orders_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_orders_sub
-- ----------------------------
DROP TABLE IF EXISTS `nx_orders_sub`;
CREATE TABLE `nx_orders_sub` (
  `nx_orders_sub_id` int NOT NULL AUTO_INCREMENT COMMENT '子订单id',
  `nx_OS_orders_id` int DEFAULT NULL COMMENT '订单id',
  `nx_OS_nx_goods_id` int DEFAULT NULL COMMENT '子订单nx商品id',
  `nx_OS_community_goods_id` int DEFAULT NULL COMMENT '子订单社区商品id',
  `nx_OS_community_goods_father_id` int DEFAULT NULL COMMENT '子订单商品父id',
  `nx_OS_quantity` float(10,1) DEFAULT NULL COMMENT '子订单申请数量',
  `nx_OS_standard` varchar(4) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '子订单申请规格',
  `nx_OS_price` float(10,1) DEFAULT NULL COMMENT '子订单申请商品单价',
  `nx_OS_remark` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '子订单申请备注',
  `nx_OS_weight` float(10,1) DEFAULT NULL COMMENT '子订单申请商品称重',
  `nx_OS_subtotal` float(10,1) DEFAULT NULL COMMENT '子订单申请商品小计',
  `nx_OS_status` tinyint DEFAULT NULL COMMENT '子订单申请商品状态',
  `nx_OS_weigh_user_id` int DEFAULT NULL COMMENT '子订单商品称重用户id',
  `nx_OS_account_user_id` int DEFAULT NULL COMMENT '子订单商品输入单价用户id',
  `nx_OS_purchase_user_id` int DEFAULT NULL COMMENT '子商品采购元id',
  `nx_OS_distributer_id` int DEFAULT NULL COMMENT '子订单批发商id',
  `nx_OS_buy_status` tinyint DEFAULT NULL COMMENT '子订单商品进货状态',
  `nx_OS_order_user_id` int DEFAULT NULL COMMENT '子订单订货用户id',
  `nx_OS_sub_weight` float(4,1) DEFAULT NULL COMMENT '子订单重量',
  `nx_OS_sub_supplier_id` int DEFAULT NULL COMMENT '子订单商品供货商id',
  `nx_OS_community_id` int DEFAULT NULL COMMENT '子订单社区id',
  `nx_Os_goods_type` tinyint DEFAULT NULL COMMENT '子订单社区商品类型',
  PRIMARY KEY (`nx_orders_sub_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_paper_refill_record
-- ----------------------------
DROP TABLE IF EXISTS `nx_paper_refill_record`;
CREATE TABLE `nx_paper_refill_record` (
  `nx_prr_id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `nx_prr_device_id` int NOT NULL COMMENT '设备ID（外键→nx_printer_device）',
  `nx_prr_before_count` int NOT NULL COMMENT '加纸前数量（张）',
  `nx_prr_add_count` int NOT NULL COMMENT '增加数量（张）',
  `nx_prr_after_count` int NOT NULL COMMENT '加纸后数量（张）',
  `nx_prr_operator_id` int NOT NULL COMMENT '操作人ID（外键→nx_market_manager）',
  `nx_prr_operator_name` varchar(50) DEFAULT NULL COMMENT '操作人姓名',
  `nx_prr_refill_type` tinyint DEFAULT '1' COMMENT '加纸类型（1-正常加纸 2-初始化 3-手动校准）',
  `nx_prr_remark` varchar(200) DEFAULT NULL COMMENT '备注',
  `nx_prr_create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`nx_prr_id`),
  KEY `idx_device_id` (`nx_prr_device_id`),
  KEY `idx_operator_id` (`nx_prr_operator_id`),
  KEY `idx_create_time` (`nx_prr_create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='加纸记录表';

-- ----------------------------
-- Table structure for nx_printer_device
-- ----------------------------
DROP TABLE IF EXISTS `nx_printer_device`;
CREATE TABLE `nx_printer_device` (
  `nx_pd_id` int NOT NULL AUTO_INCREMENT COMMENT '设备ID',
  `nx_pd_market_id` int NOT NULL COMMENT '所属市场ID（外键→sys_city_market）',
  `nx_pd_device_no` varchar(50) NOT NULL COMMENT '设备编号（唯一，格式：PD202410140001）',
  `nx_pd_device_name` varchar(100) DEFAULT NULL COMMENT '设备名称（如"1号打印机"）',
  `nx_pd_model` varchar(50) DEFAULT 'Epson LQ-730K' COMMENT '设备型号',
  `nx_pd_location` varchar(100) DEFAULT NULL COMMENT '设备位置（如"北区出口"）',
  `nx_pd_paper_count` int DEFAULT '0' COMMENT '当前纸张数量（张）',
  `nx_pd_paper_max` int DEFAULT '1000' COMMENT '纸张最大容量（张）',
  `nx_pd_status` tinyint DEFAULT '1' COMMENT '设备状态（0-离线 1-正常 2-故障 3-缺纸）',
  `nx_pd_qr_code` varchar(200) DEFAULT NULL COMMENT '设备二维码URL',
  `nx_pd_install_date` datetime DEFAULT NULL COMMENT '安装日期',
  `nx_pd_last_update_time` datetime DEFAULT NULL COMMENT '余量最后更新时间',
  `nx_pd_create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`nx_pd_id`),
  UNIQUE KEY `uk_device_no` (`nx_pd_device_no`),
  KEY `idx_market_id` (`nx_pd_market_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='打印设备表';

-- ----------------------------
-- Table structure for nx_printer_user
-- ----------------------------
DROP TABLE IF EXISTS `nx_printer_user`;
CREATE TABLE `nx_printer_user` (
  `nx_printer_user_id` int NOT NULL AUTO_INCREMENT COMMENT '订货用户id',
  `nx_printer_wx_avartra_url` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订货用户微信头像',
  `nx_printer_wx_nick_name` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订货用户微信昵称',
  `nx_printer_wx_open_id` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订货用户微信openid',
  `nx_printer_wx_phone` varchar(15) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订货户微信手机号码',
  `nx_printer_join_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '零售商用户加入日期',
  `nx_printer_nx_distributer_id` int DEFAULT NULL COMMENT '批发商id',
  `nx_printer_nx_purchaser_user_id` int DEFAULT NULL COMMENT '批发商用户id',
  `nx_printer_nx_community_id` int DEFAULT NULL COMMENT '批发商id',
  `nx_printer_nx_comm_purchaser_user_id` int DEFAULT NULL COMMENT '批发商用户id',
  `nx_printer_url_change` int DEFAULT NULL COMMENT '批发商用户id',
  `nx_printer_admin` int DEFAULT NULL COMMENT '0 seller, 1nxpurchaser 2 gbpurchaser ',
  `nx_printer_gb_distributer_id` int DEFAULT NULL COMMENT '批发商id',
  `nx_printer_gb_department_id` int DEFAULT NULL COMMENT '批发商用户id',
  `nx_printer_gb_department_user_id` int DEFAULT NULL COMMENT '批发商用户id',
  `nx_printer_device_id` varchar(40) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '批发商用户id',
  `nx_printer_device_bill_id` varchar(40) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '批发商用户id',
  PRIMARY KEY (`nx_printer_user_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_promote
-- ----------------------------
DROP TABLE IF EXISTS `nx_promote`;
CREATE TABLE `nx_promote` (
  `nx_promote_id` int NOT NULL AUTO_INCREMENT,
  `nx_promote_cg_id` int DEFAULT NULL,
  `nx_orignal_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_promote_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_promote_standard` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_promote_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_promote_expired` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_promote_storage` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_promote_words` varchar(1000) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_promote_recommand_goods` varchar(1000) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_promote_file_path` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_promote_community_id` int DEFAULT NULL,
  `nx_promote_cg_father_id` int DEFAULT NULL,
  PRIMARY KEY (`nx_promote_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_prompt
-- ----------------------------
DROP TABLE IF EXISTS `nx_prompt`;
CREATE TABLE `nx_prompt` (
  `nx_prompt_id` int NOT NULL AUTO_INCREMENT COMMENT 'Prompt ID',
  `nx_prompt_key` varchar(100) NOT NULL COMMENT 'Prompt 唯一键（如：OCR_IMAGE, OCR_EXCEL）',
  `nx_prompt_name` varchar(100) DEFAULT NULL COMMENT 'Prompt 显示名称',
  `nx_prompt_content` text COMMENT 'Prompt 具体内容',
  `nx_prompt_category` varchar(50) DEFAULT NULL COMMENT 'Prompt 分类（如：OCR, EXCEL）',
  `nx_prompt_api_path` varchar(200) DEFAULT NULL COMMENT '关联的 API 接口路径',
  `nx_prompt_version` int DEFAULT '1' COMMENT 'Prompt 版本号',
  `nx_prompt_last_updated` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  `nx_prompt_created_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `nx_prompt_status` tinyint(1) DEFAULT '1' COMMENT '状态（1=启用，0=禁用）',
  `nx_prompt_description` varchar(500) DEFAULT NULL COMMENT '描述说明',
  PRIMARY KEY (`nx_prompt_id`),
  KEY `idx_category` (`nx_prompt_category`),
  KEY `idx_api_path` (`nx_prompt_api_path`),
  KEY `idx_status` (`nx_prompt_status`),
  KEY `idx_nx_prompt_key` (`nx_prompt_key`),
  KEY `idx_nx_prompt_key_status` (`nx_prompt_key`,`nx_prompt_status`)
) ENGINE=InnoDB AUTO_INCREMENT=26 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统 Prompt 表';

-- ----------------------------
-- Table structure for nx_purchase_standard
-- ----------------------------
DROP TABLE IF EXISTS `nx_purchase_standard`;
CREATE TABLE `nx_purchase_standard` (
  `nx_purchase_standard_id` int NOT NULL,
  `nx_PS_name` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_PS_nx_goods_id` int DEFAULT NULL,
  PRIMARY KEY (`nx_purchase_standard_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_res_com_goods_daily
-- ----------------------------
DROP TABLE IF EXISTS `nx_res_com_goods_daily`;
CREATE TABLE `nx_res_com_goods_daily` (
  `nx_res_com_goods_daily_id` int NOT NULL AUTO_INCREMENT,
  `nx_RCGD_restraunt_father_id` int DEFAULT NULL,
  `nx_RCGD_restraunt_id` int DEFAULT NULL,
  `nx_RCGD_com_goods_id` int DEFAULT NULL,
  `nx_RCGD_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RCGD_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RCGD_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`nx_res_com_goods_daily_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=228 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_res_com_goods_month
-- ----------------------------
DROP TABLE IF EXISTS `nx_res_com_goods_month`;
CREATE TABLE `nx_res_com_goods_month` (
  `nx_res_com_goods_month_id` int NOT NULL AUTO_INCREMENT,
  `nx_RCGM_restraunt_father_id` int DEFAULT NULL,
  `nx_RCGM_restraunt_id` int DEFAULT NULL,
  `nx_RCGM_com_goods_id` int DEFAULT NULL,
  `nx_RCGM_month` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RCGM_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RCGM_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`nx_res_com_goods_month_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=227 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_res_com_goods_week
-- ----------------------------
DROP TABLE IF EXISTS `nx_res_com_goods_week`;
CREATE TABLE `nx_res_com_goods_week` (
  `nx_res_com_goods_week_id` int NOT NULL AUTO_INCREMENT,
  `nx_RCGW_restraunt_father_id` int DEFAULT NULL,
  `nx_RCGW_restraunt_id` int DEFAULT NULL,
  `nx_RCGW_com_goods_id` int DEFAULT NULL,
  `nx_RCGW_week` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RCGW_weight` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RCGW_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`nx_res_com_goods_week_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=227 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_restraunt
-- ----------------------------
DROP TABLE IF EXISTS `nx_restraunt`;
CREATE TABLE `nx_restraunt` (
  `nx_restraunt_id` int NOT NULL AUTO_INCREMENT COMMENT '订货部门id',
  `nx_restraunt_name` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订货部门名称',
  `nx_restraunt_father_id` int DEFAULT NULL COMMENT '订货部门上级id',
  `nx_restraunt_type` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订货部门类型',
  `nx_restraunt_sub_amount` int DEFAULT NULL COMMENT '订货部门子部门数量',
  `nx_restraunt_com_id` int DEFAULT NULL COMMENT '订货部门批发商id',
  `nx_restraunt_file_path` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_restraunt_is_group_dep` tinyint DEFAULT NULL COMMENT '是客户吗',
  `nx_restraunt_print_name` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_restraunt_show_weeks` tinyint DEFAULT '1',
  `nx_restraunt_settle_type` tinyint DEFAULT NULL,
  `nx_restraunt_attr_name` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '客户简称',
  `nx_restraunt_lat` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_restraunt_lng` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_restraunt_min_time` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_restraunt_max_time` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_restraunt_address` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_restraunt_navigation_address` varchar(300) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_restraunt_number` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_restraunt_service_level` tinyint DEFAULT NULL,
  `nx_restraunt_driver_id` int DEFAULT NULL,
  `nx_restraunt_owe_box_number` int DEFAULT '0',
  `nx_restraunt_delivery_box_number` int DEFAULT '0',
  `nx_restraunt_working_status` tinyint DEFAULT NULL,
  `nx_restraunt_delivery_cost` varchar(6) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_restraunt_delivery_limit` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_restraunt_unPay_total` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_restraunt_click_count` int DEFAULT NULL,
  `nx_restraunt_add_count` int DEFAULT NULL,
  `nx_restraunt_pay_total` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_restraunt_profit_total` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_restraunt_profit_percent` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`nx_restraunt_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_restraunt_bill
-- ----------------------------
DROP TABLE IF EXISTS `nx_restraunt_bill`;
CREATE TABLE `nx_restraunt_bill` (
  `nx_restraunt_bill_id` int NOT NULL AUTO_INCREMENT,
  `nx_RB_com_id` int DEFAULT NULL,
  `nx_RB_restraunt_id` int DEFAULT NULL,
  `nx_RB_driver_user_id` int DEFAULT NULL,
  `nx_RB_total` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RB_status` tinyint DEFAULT NULL,
  `nx_RB_produce_time` varchar(30) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RB_issue_user_id` int DEFAULT NULL,
  `nx_RB_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RB_month` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RB_day` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RB_week` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RB_trade_no` varchar(32) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RB_service_level` tinyint DEFAULT NULL,
  `nx_RB_pay_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RB_apply_pay_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RB_goods_total` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RB_delivery_total` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RB_profit_total` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RB_pay_full_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RB_delivery_date` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RB_profit_percent` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`nx_restraunt_bill_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_restraunt_com_goods
-- ----------------------------
DROP TABLE IF EXISTS `nx_restraunt_com_goods`;
CREATE TABLE `nx_restraunt_com_goods` (
  `nx_restraunt_com_goods_id` int NOT NULL AUTO_INCREMENT,
  `nx_RCG_restraunt_father_id` int DEFAULT NULL,
  `nx_RCG_restraunt_id` int DEFAULT NULL,
  `nx_RCG_com_goods_id` int DEFAULT NULL,
  `nx_RCG_com_goods_father_id` int DEFAULT NULL,
  `nx_RCG_com_goods_name` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RCG_com_goods_pinyin` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RCG_com_goods_py` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RCG_com_goods_standardname` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RCG_com_goods_detail` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RCG_com_goods_brand` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RCG_com_goods_place` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RCG_order_price` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RCG_order_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RCG_order_remark` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RCG_order_quantity` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RCG_order_standard` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RCG_order_user_id` int DEFAULT NULL,
  `nx_RCG_res_know_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RCG_res_contract_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RCG_res_contract_stop_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RCG_res_contract_order_quantity` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`nx_restraunt_com_goods_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_restraunt_orders
-- ----------------------------
DROP TABLE IF EXISTS `nx_restraunt_orders`;
CREATE TABLE `nx_restraunt_orders` (
  `nx_restraunt_orders_id` int NOT NULL AUTO_INCREMENT COMMENT '饭馆订单id',
  `nx_RO_nx_goods_id` int DEFAULT NULL COMMENT '饭馆订单nx商品id',
  `nx_RO_nx_goods_father_id` int DEFAULT NULL COMMENT '饭馆订单商品父id',
  `nx_RO_com_goods_id` int DEFAULT NULL COMMENT '饭馆区域商品id',
  `nx_RO_com_goods_father_id` int DEFAULT NULL COMMENT '区域父级商品id',
  `nx_RO_res_com_goods_id` int DEFAULT NULL COMMENT '饭馆id',
  `nx_RO_res_com_goods_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '饭馆商品价格',
  `nx_RO_quantity` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请数量',
  `nx_RO_standard` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请规格',
  `nx_RO_remark` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请备注',
  `nx_RO_weight` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单重量',
  `nx_RO_price` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单商品单价',
  `nx_RO_subtotal` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请商品小计',
  `nx_RO_restraunt_id` int DEFAULT NULL COMMENT '部门订单部门id',
  `nx_RO_restraunt_father_id` int DEFAULT NULL,
  `nx_RO_community_id` int DEFAULT NULL COMMENT '部门订单批发商id',
  `nx_RO_purchase_user_id` int DEFAULT NULL COMMENT '部门商品采购员id',
  `nx_RO_bill_id` int DEFAULT NULL COMMENT '部门订单账单id',
  `nx_RO_status` tinyint DEFAULT NULL COMMENT '部门订单申请商品状态',
  `nx_RO_order_user_id` int DEFAULT NULL COMMENT '部门订单订货用户id',
  `nx_RO_pick_user_id` int DEFAULT NULL COMMENT '部门订单商品称重用户id',
  `nx_RO_account_user_id` int DEFAULT NULL COMMENT '部门订单商品输入单价用户id',
  `nx_RO_purchase_goods_id` int DEFAULT NULL COMMENT '订单采购商品id',
  `nx_RO_buy_status` tinyint DEFAULT NULL COMMENT '部门订单商品进货状态',
  `nx_RO_apply_full_time` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RO_receive_full_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '收货时间',
  `nx_RO_apply_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请时间',
  `nx_RO_arrive_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单送达时间',
  `nx_RO_operation_time` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RO_arrive_what_day` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '星期几',
  `nx_RO_arrive_weeks_year` int DEFAULT NULL COMMENT '本年第几周',
  `nx_RO_delivery_date` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RO_sell_type` tinyint DEFAULT NULL COMMENT '1 日采2 出库3 供应商4 配送商',
  `nx_RO_is_agent` tinyint DEFAULT NULL,
  `nx_RO_number` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RO_cost_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RO_cost_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '成本小计',
  `nx_RO_expect_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '预计小计',
  `nx_RO_scale` varchar(6) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RO_profit` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RO_order_rank` tinyint DEFAULT NULL COMMENT '订单级别',
  `nx_RO_com_goods_standard_type` tinyint DEFAULT NULL COMMENT '商品的规格销售方式 0 商品规格，1订货规格。',
  `nx_RO_com_standard_id` int DEFAULT NULL COMMENT '销售规格id',
  `nx_RO_com_standard_name` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '销售规格名称',
  `nx_RO_com_standard_scale` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '销售规格比例',
  `nx_RO_com_standard_quantity` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订货销售规格数量',
  `nx_RO_print_times` int DEFAULT NULL COMMENT '订单打印次数',
  `nx_RO_com_distributer_id` int DEFAULT NULL COMMENT 'comGoods的批发商',
  `nx_RO_com_distributer_goods_id` int DEFAULT NULL,
  `nx_RO_order_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订货单价',
  `nx_RO_cost_percent` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RO_arrive_min_time` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RO_arrive_max_time` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RO_com_distributer_order_id` int DEFAULT NULL,
  PRIMARY KEY (`nx_restraunt_orders_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=29 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_restraunt_orders_history
-- ----------------------------
DROP TABLE IF EXISTS `nx_restraunt_orders_history`;
CREATE TABLE `nx_restraunt_orders_history` (
  `nx_restraunt_orders_history_id` int NOT NULL AUTO_INCREMENT COMMENT '饭馆订单id',
  `nx_ROH_res_com_goods_id` int DEFAULT NULL COMMENT '饭馆id',
  `nx_ROH_quantity` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请数量',
  `nx_ROH_standard` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请规格',
  `nx_ROH_remark` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请备注',
  `nx_ROH_restraunt_id` int DEFAULT NULL COMMENT '部门订单部门id',
  `nx_ROH_restraunt_father_id` int DEFAULT NULL,
  `nx_ROH_order_user_id` int DEFAULT NULL COMMENT '部门订单订货用户id',
  `nx_ROH_apply_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '部门订单申请时间',
  `nx_ROH_sell_type` tinyint DEFAULT NULL COMMENT '出货方式0,日采;1,出库;2,供货商;3,加工',
  PRIMARY KEY (`nx_restraunt_orders_history_id`)
) ENGINE=InnoDB AUTO_INCREMENT=27 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_restraunt_user
-- ----------------------------
DROP TABLE IF EXISTS `nx_restraunt_user`;
CREATE TABLE `nx_restraunt_user` (
  `nx_restraunt_user_id` int NOT NULL AUTO_INCREMENT COMMENT '订货部门用户id',
  `nx_RU_restaurant_id` int DEFAULT NULL COMMENT '订货部门id',
  `nx_RU_wx_avartra_url` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订货部门用户微信头像',
  `nx_RU_wx_nick_name` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订货部门用户微信昵称',
  `nx_RU_wx_open_id` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订货部门用户微信openid',
  `nx_RU_wx_phone` varchar(15) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订货部门用户微信手机号码',
  `nx_RU_admin` tinyint DEFAULT NULL COMMENT '订货部门用户是否是管理员',
  `nx_RU_com_id` int DEFAULT NULL COMMENT '订货部门批发商id',
  `nx_RU_url_change` tinyint DEFAULT NULL,
  `nx_RU_restaurant_father_id` int DEFAULT NULL,
  `nx_RU_join_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`nx_restraunt_user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_retailer
-- ----------------------------
DROP TABLE IF EXISTS `nx_retailer`;
CREATE TABLE `nx_retailer` (
  `nx_retailer_id` int NOT NULL AUTO_INCREMENT COMMENT '零售商id',
  `nx_retailer_name` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '零售商名称',
  `nx_retailer_lat` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_retailer_lng` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_retailer_img` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_retailer_goods_id` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_retailer_describe` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`nx_retailer_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_retailer_goods_shelf
-- ----------------------------
DROP TABLE IF EXISTS `nx_retailer_goods_shelf`;
CREATE TABLE `nx_retailer_goods_shelf` (
  `nx_retailer_goods_shelf_id` int NOT NULL AUTO_INCREMENT COMMENT '货架id',
  `nx_retailer_goods_shelf_name` varchar(20) DEFAULT NULL COMMENT '货架名称',
  `nx_retailer_goods_shelf_sort` int DEFAULT NULL COMMENT '货架排序',
  `nx_retailer_goods_shelf_retailer_id` int DEFAULT NULL COMMENT '批发商id',
  PRIMARY KEY (`nx_retailer_goods_shelf_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb3 COMMENT='用户与角色对应关系';

-- ----------------------------
-- Table structure for nx_retailer_goods_shelf_goods
-- ----------------------------
DROP TABLE IF EXISTS `nx_retailer_goods_shelf_goods`;
CREATE TABLE `nx_retailer_goods_shelf_goods` (
  `nx_retailer_goods_shelf_goods_id` int NOT NULL AUTO_INCREMENT COMMENT '货架商品id',
  `nx_RGSG_goods_name` varchar(100) DEFAULT NULL COMMENT '批发商商品id',
  `nx_RGSG_shelf_id` int DEFAULT NULL COMMENT '货架id',
  `nx_RGSG_sort` int DEFAULT NULL COMMENT '货架商品排序',
  PRIMARY KEY (`nx_retailer_goods_shelf_goods_id`)
) ENGINE=InnoDB AUTO_INCREMENT=40 DEFAULT CHARSET=utf8mb3 COMMENT='用户与角色对应关系';

-- ----------------------------
-- Table structure for nx_retailer_purchase_batch
-- ----------------------------
DROP TABLE IF EXISTS `nx_retailer_purchase_batch`;
CREATE TABLE `nx_retailer_purchase_batch` (
  `nx_retailer_purchase_batch_id` int NOT NULL AUTO_INCREMENT COMMENT '批发商进货批次id',
  `nx_RPB_status` tinyint DEFAULT NULL COMMENT '批发商进货批次状态',
  `nx_RPB_type` tinyint DEFAULT NULL COMMENT '批发商进货批次类型',
  `nx_RPB_time` varchar(12) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '批发商进货批次时间',
  `nx_RPB_pur_user_id` int DEFAULT NULL COMMENT '批发商进货采购员id',
  `nx_RPB_retailer_id` int DEFAULT NULL COMMENT '批发商id',
  `nx_RPB_date` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RPB_hour` varchar(4) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RPB_minute` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RPB_sell_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RPB_sell_user_img` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RPB_buy_user_id` int DEFAULT NULL COMMENT '买方用户id',
  `nx_RPB_sell_user_id` int DEFAULT NULL COMMENT '卖方用户id',
  `nx_RPB_supplier_id` int DEFAULT NULL COMMENT '供货商id',
  PRIMARY KEY (`nx_retailer_purchase_batch_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_retailer_purchase_goods
-- ----------------------------
DROP TABLE IF EXISTS `nx_retailer_purchase_goods`;
CREATE TABLE `nx_retailer_purchase_goods` (
  `nx_retailer_purchase_goods_id` int NOT NULL AUTO_INCREMENT COMMENT '零售商采购商品id',
  `nx_RPG_shelf_goods_id` int DEFAULT NULL COMMENT '零售商品id',
  `nx_RPG_shelf_id` int DEFAULT NULL COMMENT '零售货架id',
  `nx_RPG_quantity` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '采购数量',
  `nx_RPG_standard` varchar(6) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '采购规格',
  `nx_RPG_status` tinyint DEFAULT NULL COMMENT '采购状态',
  `nx_RPG_retailer_id` int DEFAULT NULL COMMENT '采购批发商id',
  `nx_RPG_purchase_type` tinyint DEFAULT NULL COMMENT '采购方式：“1 订单采购”“2 添加采购”',
  `nx_RPG_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '采购时间',
  `nx_RPG_batch_id` int DEFAULT NULL COMMENT '采购批次号',
  `nx_RPG_buy_user_id` int DEFAULT NULL COMMENT '采购方式为“采购”的采购员id',
  `nx_RPG_buy_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '采购单价',
  `nx_RPG_buy_quantity` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '采购数量',
  `nx_RPG_type_add_user_id` int DEFAULT NULL COMMENT '添加采购用户id',
  `nx_RPG_apply_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RPG_purchase_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '采购日期',
  `nx_RPG_input_type` tinyint DEFAULT NULL,
  `nx_RPG_buy_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_RPG_goods_name` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`nx_retailer_purchase_goods_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_retailer_user
-- ----------------------------
DROP TABLE IF EXISTS `nx_retailer_user`;
CREATE TABLE `nx_retailer_user` (
  `nx_retailer_user_id` int NOT NULL AUTO_INCREMENT COMMENT '零售商用户id',
  `nx_RETU_retailer_id` int DEFAULT NULL COMMENT '零售商id',
  `nx_RETU_wx_avartra_url` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '零售商用户微信头像',
  `nx_RETU_wx_nick_name` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '零售商用户微信昵称',
  `nx_RETU_wx_open_id` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '零售商用户微信openid',
  `nx_RETU_wx_phone` varchar(15) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '零售商用户微信手机号码',
  `nx_RETU_admin` tinyint DEFAULT NULL COMMENT '零售商用户是否是管理员',
  `nx_RETU_url_change` tinyint DEFAULT NULL COMMENT '零售商用户是否修改头像',
  `nx_RETU_join_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '零售商用户加入日期',
  PRIMARY KEY (`nx_retailer_user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_route
-- ----------------------------
DROP TABLE IF EXISTS `nx_route`;
CREATE TABLE `nx_route` (
  `nx_route_id` int NOT NULL COMMENT '线路id',
  `nx_route_name` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '线路名称',
  PRIMARY KEY (`nx_route_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_sell_user
-- ----------------------------
DROP TABLE IF EXISTS `nx_sell_user`;
CREATE TABLE `nx_sell_user` (
  `nx_sell_user_id` int NOT NULL AUTO_INCREMENT COMMENT '卖货用户id',
  `nx_SU_retailer_id` int DEFAULT NULL COMMENT '零售商id',
  `nx_SU_wx_avartra_url` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '卖货用户微信头像',
  `nx_SU_wx_nick_name` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '卖货用户微信昵称',
  `nx_SU_wx_open_id` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '卖货用户微信openid',
  `nx_SU_wx_phone` varchar(15) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '卖货户微信手机号码',
  `nx_SU_join_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '用户加入日期',
  `nx_SU_gb_dis_supplier_id` int DEFAULT NULL COMMENT 'gb供货商id',
  `nx_SU_nx_dis_id` int DEFAULT NULL COMMENT 'nxDistributerId',
  PRIMARY KEY (`nx_sell_user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_standard
-- ----------------------------
DROP TABLE IF EXISTS `nx_standard`;
CREATE TABLE `nx_standard` (
  `nx_standard_id` int NOT NULL AUTO_INCREMENT,
  `nx_standard_name` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_s_goods_id` int DEFAULT NULL,
  `nx_standard_file_path` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_standard_scale` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_standard_error` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `nx_standard_sort` int DEFAULT NULL,
  `nx_standard_weight` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`nx_standard_id`)
) ENGINE=InnoDB AUTO_INCREMENT=477 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_supplier
-- ----------------------------
DROP TABLE IF EXISTS `nx_supplier`;
CREATE TABLE `nx_supplier` (
  `nx_supplier_id` int NOT NULL AUTO_INCREMENT COMMENT '供货商id',
  `nx_supplier_name` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '供货商名称',
  `nx_supplier_father_goods_id` int DEFAULT NULL COMMENT '供货商商品类别id',
  `nx_supplier_payment_type` tinyint DEFAULT NULL COMMENT '供货商结算类别1现金，2记账',
  `nx_supplier_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '供货商加入时间',
  PRIMARY KEY (`nx_supplier_id`)
) ENGINE=InnoDB AUTO_INCREMENT=22 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for nx_trace_report
-- ----------------------------
DROP TABLE IF EXISTS `nx_trace_report`;
CREATE TABLE `nx_trace_report` (
  `nx_trace_report_id` int NOT NULL AUTO_INCREMENT COMMENT '溯源报告ID',
  `nx_TR_batch_id` int DEFAULT NULL COMMENT '采购批次ID（关联nx_distributer_purchase_batch）',
  `nx_TR_supplier_id` int DEFAULT NULL COMMENT '供应商ID（关联供应商表）',
  `nx_TR_supplier_name` varchar(200) DEFAULT NULL COMMENT '供应商名称（冗余字段，便于查询显示）',
  `nx_TR_supplier_contact` varchar(100) DEFAULT NULL COMMENT '供应商联系方式',
  `nx_TR_purchase_date` date DEFAULT NULL COMMENT '采购日期',
  `nx_TR_stock_in_date` date DEFAULT NULL COMMENT '入库日期',
  `nx_TR_valid_start_date` date DEFAULT NULL COMMENT '报告有效期开始日期',
  `nx_TR_valid_end_date` date DEFAULT NULL COMMENT '报告有效期结束日期',
  `nx_TR_report_type` varchar(50) DEFAULT NULL COMMENT '报告类型（image/pdf/excel等）',
  `nx_TR_file_path` varchar(500) DEFAULT NULL COMMENT '报告文件路径（单个文件）',
  `nx_TR_file_paths` text COMMENT '报告文件路径（多个文件，JSON格式存储）',
  `nx_TR_file_type` varchar(50) DEFAULT NULL COMMENT '报告文件类型（MIME类型或扩展名）',
  `nx_TR_distributer_id` int DEFAULT NULL COMMENT '配送商ID（关联配送商表）',
  `nx_TR_create_user_id` int DEFAULT NULL COMMENT '创建用户ID',
  `nx_TR_create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `nx_TR_update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `nx_TR_remark` varchar(500) DEFAULT NULL COMMENT '备注说明',
  PRIMARY KEY (`nx_trace_report_id`),
  KEY `idx_batch_id` (`nx_TR_batch_id`),
  KEY `idx_supplier_id` (`nx_TR_supplier_id`),
  KEY `idx_distributer_id` (`nx_TR_distributer_id`),
  KEY `idx_valid_date` (`nx_TR_valid_start_date`,`nx_TR_valid_end_date`),
  KEY `idx_create_time` (`nx_TR_create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='溯源报告表';

-- ----------------------------
-- Table structure for nx_weight_user
-- ----------------------------
DROP TABLE IF EXISTS `nx_weight_user`;
CREATE TABLE `nx_weight_user` (
  `nx_weight_user_id` int NOT NULL AUTO_INCREMENT COMMENT '称重员工ID',
  `nx_WU_user_type` tinyint NOT NULL DEFAULT '1' COMMENT '员工类型：1=配送商员工, 2=供货商员工',
  `nx_WU_wx_open_id` varchar(100) DEFAULT NULL COMMENT '微信openid',
  `nx_WU_wx_nick_name` varchar(100) DEFAULT NULL COMMENT '微信昵称',
  `nx_WU_wx_avartra_url` varchar(500) DEFAULT NULL COMMENT '微信头像',
  `nx_WU_wx_phone` varchar(20) DEFAULT NULL COMMENT '微信手机号',
  `nx_WU_login_phone` varchar(20) DEFAULT NULL COMMENT '登录手机号',
  `nx_WU_login_times` int DEFAULT '0' COMMENT '登录次数',
  `nx_WU_login_code` varchar(10) DEFAULT NULL COMMENT '登录验证码',
  `nx_WU_nx_distributer_id` int DEFAULT NULL COMMENT '关联配送商ID（当user_type=1时使用）',
  `nx_WU_user_id` int DEFAULT NULL COMMENT '关联供货商ID（当user_type=2时使用）',
  `nx_WU_status` tinyint DEFAULT '1' COMMENT '状态：0=禁用，1=启用',
  `nx_WU_join_date` varchar(20) DEFAULT NULL COMMENT '加入日期',
  `nx_WU_url_change` int DEFAULT NULL COMMENT 'URL变更标识',
  PRIMARY KEY (`nx_weight_user_id`),
  KEY `idx_user_type` (`nx_WU_user_type`),
  KEY `idx_login_phone` (`nx_WU_login_phone`),
  KEY `idx_wx_open_id` (`nx_WU_wx_open_id`),
  KEY `idx_distributer_id` (`nx_WU_nx_distributer_id`),
  KEY `idx_supplier_id` (`nx_WU_user_id`),
  KEY `idx_status` (`nx_WU_status`)
) ENGINE=InnoDB AUTO_INCREMENT=35 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='称重员工表';

-- ----------------------------
-- Table structure for nx_wx_orders
-- ----------------------------
DROP TABLE IF EXISTS `nx_wx_orders`;
CREATE TABLE `nx_wx_orders` (
  `nx_wx_orders_id` int NOT NULL AUTO_INCREMENT COMMENT '微信支付订单id',
  `nx_wx_orders_out_trade_no` varchar(32) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '随机字符串32位',
  `nx_wx_orders_body` varchar(128) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订单主体',
  `nx_wx_orders_detail` varchar(6000) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订单详细',
  `nx_wx_orders_attach` varchar(127) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '附加数据，如“深圳分店”',
  `nx_wx_orders_total_fee` int DEFAULT NULL COMMENT '支付金额单位“分”',
  `nx_wx_orders_spbill_create_ip` varchar(64) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '支付api的机器ip',
  PRIMARY KEY (`nx_wx_orders_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for qy_gb_dis_corp
-- ----------------------------
DROP TABLE IF EXISTS `qy_gb_dis_corp`;
CREATE TABLE `qy_gb_dis_corp` (
  `qy_gb_dis_corp_id` int NOT NULL AUTO_INCREMENT,
  `qy_gb_dis_corp_name` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `qy_gb_dis_corp_round_logo_url` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `qy_gb_dis_corp_permanent_code` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `qy_gb_dis_corp_access_token` varchar(500) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `qy_gb_dis_qy_corp_id` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `qy_gb_dis_corp_join_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`qy_gb_dis_corp_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=37 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for qy_gb_dis_corp_chat_record
-- ----------------------------
DROP TABLE IF EXISTS `qy_gb_dis_corp_chat_record`;
CREATE TABLE `qy_gb_dis_corp_chat_record` (
  `qy_gb_dis_corp_chat_record_id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `qy_gb_dis_qy_corp_id` varchar(100) COLLATE utf16_czech_ci DEFAULT NULL COMMENT '企业微信企业ID',
  `qy_gb_dis_corp_chat_record_msg_id` varchar(100) COLLATE utf16_czech_ci DEFAULT NULL COMMENT '消息ID',
  `qy_gb_dis_corp_chat_record_room_id` varchar(100) COLLATE utf16_czech_ci DEFAULT NULL COMMENT '群ID',
  `qy_gb_dis_corp_chat_record_from_user` varchar(100) COLLATE utf16_czech_ci DEFAULT NULL COMMENT '发送者',
  `qy_gb_dis_corp_chat_record_msg_type` varchar(20) COLLATE utf16_czech_ci DEFAULT NULL COMMENT '消息类型',
  `qy_gb_dis_corp_chat_record_content` text COLLATE utf16_czech_ci COMMENT '消息内容',
  `qy_gb_dis_corp_chat_record_msg_time` bigint DEFAULT NULL COMMENT '消息时间戳',
  `qy_gb_dis_corp_chat_record_is_replied` tinyint DEFAULT '0' COMMENT '是否已回复',
  `qy_gb_dis_corp_chat_record_reply_content` text COLLATE utf16_czech_ci COMMENT '回复内容',
  `qy_gb_dis_corp_chat_record_create_date` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`qy_gb_dis_corp_chat_record_id`),
  UNIQUE KEY `idx_msg_id` (`qy_gb_dis_corp_chat_record_msg_id`),
  KEY `idx_corp_room` (`qy_gb_dis_qy_corp_id`,`qy_gb_dis_corp_chat_record_room_id`),
  KEY `idx_msg_time` (`qy_gb_dis_corp_chat_record_msg_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci COMMENT='企业微信聊天记录表';

-- ----------------------------
-- Table structure for qy_gb_dis_corp_monitored_group
-- ----------------------------
DROP TABLE IF EXISTS `qy_gb_dis_corp_monitored_group`;
CREATE TABLE `qy_gb_dis_corp_monitored_group` (
  `qy_gb_dis_corp_monitored_group_id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `qy_gb_dis_qy_corp_id` varchar(100) COLLATE utf16_czech_ci DEFAULT NULL COMMENT '企业微信企业ID',
  `qy_gb_dis_corp_monitored_group_chat_id` varchar(100) COLLATE utf16_czech_ci DEFAULT NULL COMMENT '客户群ID',
  `qy_gb_dis_corp_monitored_group_chat_name` varchar(200) COLLATE utf16_czech_ci DEFAULT NULL COMMENT '客户群名称',
  `qy_gb_dis_corp_monitored_group_owner` varchar(100) COLLATE utf16_czech_ci DEFAULT NULL COMMENT '群主UserID',
  `qy_gb_dis_corp_monitored_group_member_count` int DEFAULT '0' COMMENT '群成员数量',
  `qy_gb_dis_corp_monitored_group_status` tinyint DEFAULT '1' COMMENT '监控状态 1启用 0禁用',
  `qy_gb_dis_corp_monitored_group_create_date` datetime DEFAULT NULL COMMENT '创建时间',
  `qy_gb_dis_corp_monitored_group_update_date` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`qy_gb_dis_corp_monitored_group_id`),
  UNIQUE KEY `idx_corp_chat` (`qy_gb_dis_qy_corp_id`,`qy_gb_dis_corp_monitored_group_chat_id`),
  KEY `idx_corp_id` (`qy_gb_dis_qy_corp_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci COMMENT='企业微信监控的客户群表';

-- ----------------------------
-- Table structure for qy_gb_dis_corp_msgaudit
-- ----------------------------
DROP TABLE IF EXISTS `qy_gb_dis_corp_msgaudit`;
CREATE TABLE `qy_gb_dis_corp_msgaudit` (
  `id` int NOT NULL AUTO_INCREMENT,
  `qy_gb_dis_qy_corp_id` varchar(100) NOT NULL COMMENT '企业微信企业ID',
  `qy_gb_dis_corp_msgaudit_secret` varchar(200) DEFAULT NULL COMMENT '企业微信应用Secret',
  `qy_gb_dis_corp_msgaudit_token` varchar(200) DEFAULT NULL COMMENT '企业微信应用Token',
  `qy_gb_dis_corp_msgaudit_encoding_aes_key` varchar(500) DEFAULT NULL COMMENT '企业微信消息加密密钥',
  `qy_gb_dis_corp_msgaudit_private_key` longtext COMMENT '企业微信会话存档私钥(PEM格式)',
  `qy_gb_dis_corp_msgaudit_access_token` varchar(500) DEFAULT NULL COMMENT '企业微信访问令牌',
  `qy_gb_dis_corp_msgaudit_token_expire_time` datetime DEFAULT NULL COMMENT '访问令牌过期时间',
  `qy_gb_dis_corp_msgaudit_status` tinyint DEFAULT '1' COMMENT '状态：1启用，0禁用',
  `qy_gb_dis_corp_msgaudit_create_date` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `qy_gb_dis_corp_msgaudit_update_date` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_corp_id` (`qy_gb_dis_qy_corp_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='企业微信会话存档配置表';

-- ----------------------------
-- Table structure for qy_gb_dis_corp_msgaudit_backup
-- ----------------------------
DROP TABLE IF EXISTS `qy_gb_dis_corp_msgaudit_backup`;
CREATE TABLE `qy_gb_dis_corp_msgaudit_backup` (
  `qy_gb_dis_corp_msgaudit_id` bigint NOT NULL DEFAULT '0' COMMENT '主键ID',
  `qy_gb_dis_qy_corp_id` varchar(100) COLLATE utf16_czech_ci DEFAULT NULL COMMENT '企业微信企业ID',
  `qy_gb_dis_corp_msgaudit_secret` varchar(200) COLLATE utf16_czech_ci DEFAULT NULL COMMENT '会话存档应用Secret',
  `qy_gb_dis_corp_msgaudit_token` varchar(100) COLLATE utf16_czech_ci DEFAULT NULL COMMENT '回调Token',
  `qy_gb_dis_corp_msgaudit_encoding_aes_key` varchar(400) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '回调加密Key',
  `qy_gb_dis_corp_msgaudit_private_key` longtext COLLATE utf16_czech_ci COMMENT '企业微信会话存档私钥(PEM格式)',
  `qy_gb_dis_corp_msgaudit_access_token` varchar(500) COLLATE utf16_czech_ci DEFAULT NULL COMMENT '访问令牌',
  `qy_gb_dis_corp_msgaudit_token_expire_time` datetime DEFAULT NULL COMMENT 'Token过期时间',
  `qy_gb_dis_corp_msgaudit_status` tinyint DEFAULT '1' COMMENT '状态 1启用 0禁用',
  `qy_gb_dis_corp_msgaudit_create_date` datetime DEFAULT NULL COMMENT '创建时间',
  `qy_gb_dis_corp_msgaudit_update_date` datetime DEFAULT NULL COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for qy_gb_dis_corp_msgaudit_copy1
-- ----------------------------
DROP TABLE IF EXISTS `qy_gb_dis_corp_msgaudit_copy1`;
CREATE TABLE `qy_gb_dis_corp_msgaudit_copy1` (
  `qy_gb_dis_corp_msgaudit_id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `qy_gb_dis_qy_corp_id` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '企业微信企业ID',
  `qy_gb_dis_corp_msgaudit_secret` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '会话存档应用Secret',
  `qy_gb_dis_corp_msgaudit_token` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '回调Token',
  `qy_gb_dis_corp_msgaudit_encoding_aes_key` varchar(400) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '回调加密Key',
  `qy_gb_dis_corp_msgaudit_private_key` text CHARACTER SET utf16 COLLATE utf16_czech_ci COMMENT '会话存档私钥',
  `qy_gb_dis_corp_msgaudit_access_token` varchar(500) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '访问令牌',
  `qy_gb_dis_corp_msgaudit_token_expire_time` datetime DEFAULT NULL COMMENT 'Token过期时间',
  `qy_gb_dis_corp_msgaudit_status` tinyint DEFAULT '1' COMMENT '状态 1启用 0禁用',
  `qy_gb_dis_corp_msgaudit_create_date` datetime DEFAULT NULL COMMENT '创建时间',
  `qy_gb_dis_corp_msgaudit_update_date` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`qy_gb_dis_corp_msgaudit_id`),
  UNIQUE KEY `idx_corp_id` (`qy_gb_dis_qy_corp_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci COMMENT='企业微信会话存档配置表';

-- ----------------------------
-- Table structure for qy_gb_dis_corp_reply_rule
-- ----------------------------
DROP TABLE IF EXISTS `qy_gb_dis_corp_reply_rule`;
CREATE TABLE `qy_gb_dis_corp_reply_rule` (
  `qy_gb_dis_corp_reply_rule_id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `qy_gb_dis_qy_corp_id` varchar(100) COLLATE utf16_czech_ci DEFAULT NULL COMMENT '企业微信企业ID',
  `qy_gb_dis_corp_reply_rule_name` varchar(100) COLLATE utf16_czech_ci DEFAULT NULL COMMENT '规则名称',
  `qy_gb_dis_corp_reply_rule_keyword` varchar(500) COLLATE utf16_czech_ci DEFAULT NULL COMMENT '关键词（多个用逗号分隔）',
  `qy_gb_dis_corp_reply_rule_match_type` tinyint DEFAULT NULL COMMENT '匹配类型 1完全匹配 2包含 3正则',
  `qy_gb_dis_corp_reply_rule_type` tinyint DEFAULT NULL COMMENT '回复类型 1文本 2图片 3图文',
  `qy_gb_dis_corp_reply_rule_content` text COLLATE utf16_czech_ci COMMENT '回复内容',
  `qy_gb_dis_corp_reply_rule_priority` int DEFAULT '0' COMMENT '优先级（数字越大越优先）',
  `qy_gb_dis_corp_reply_rule_status` tinyint DEFAULT '1' COMMENT '状态 1启用 0禁用',
  `qy_gb_dis_corp_reply_rule_create_date` datetime DEFAULT NULL COMMENT '创建时间',
  `qy_gb_dis_corp_reply_rule_update_date` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`qy_gb_dis_corp_reply_rule_id`),
  KEY `idx_corp_id` (`qy_gb_dis_qy_corp_id`),
  KEY `idx_status_priority` (`qy_gb_dis_corp_reply_rule_status`,`qy_gb_dis_corp_reply_rule_priority`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci COMMENT='企业微信消息回复规则表';

-- ----------------------------
-- Table structure for qy_gb_dis_corp_user
-- ----------------------------
DROP TABLE IF EXISTS `qy_gb_dis_corp_user`;
CREATE TABLE `qy_gb_dis_corp_user` (
  `qy_gb_dis_corp_user_id` int NOT NULL AUTO_INCREMENT,
  `qy_gb_dis_corp_user_name` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `qy_gb_dis_corp_user_url` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `qy_gb_dis_corp_open_user_id` varchar(40) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `qy_gb_dis_corp_qy_corp_id` int DEFAULT NULL,
  `qy_gb_distributer_id` int DEFAULT NULL,
  `qy_gb_dis_corp_session_key` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `qy_gb_dis_corp_user_join_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`qy_gb_dis_corp_user_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for qy_nx_dis_corp
-- ----------------------------
DROP TABLE IF EXISTS `qy_nx_dis_corp`;
CREATE TABLE `qy_nx_dis_corp` (
  `qy_nx_dis_corp_id` int NOT NULL AUTO_INCREMENT,
  `qy_nx_dis_corp_name` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `qy_nx_dis_corp_round_logo_url` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `qy_nx_dis_corp_permanent_code` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `qy_nx_dis_corp_access_token` varchar(500) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `qy_nx_dis_qy_corp_id` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `qy_nx_dis_corp_join_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`qy_nx_dis_corp_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=56 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for qy_nx_dis_corp_dj
-- ----------------------------
DROP TABLE IF EXISTS `qy_nx_dis_corp_dj`;
CREATE TABLE `qy_nx_dis_corp_dj` (
  `qy_nx_dis_corp_dj_id` int NOT NULL AUTO_INCREMENT,
  `qy_nx_dis_corp_dj_name` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `qy_nx_dis_corp_dj_round_logo_url` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `qy_nx_dis_corp_dj_permanent_code` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `qy_nx_dis_corp_dj_access_token` varchar(500) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `qy_nx_dis_qy_corp_dj_id` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`qy_nx_dis_corp_dj_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for qy_nx_dis_corp_user
-- ----------------------------
DROP TABLE IF EXISTS `qy_nx_dis_corp_user`;
CREATE TABLE `qy_nx_dis_corp_user` (
  `qy_nx_dis_corp_user_id` int NOT NULL AUTO_INCREMENT,
  `qy_nx_dis_corp_user_name` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `qy_nx_dis_corp_user_url` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `qy_nx_dis_corp_open_user_id` varchar(40) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `qy_nx_dis_corp_qy_corp_id` int DEFAULT NULL,
  `qy_nx_distributer_id` int DEFAULT NULL,
  `qy_nx_dis_corp_session_key` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `qy_nx_dis_corp_user_join_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`qy_nx_dis_corp_user_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=25 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for sys_business_type
-- ----------------------------
DROP TABLE IF EXISTS `sys_business_type`;
CREATE TABLE `sys_business_type` (
  `sys_business_type_id` int NOT NULL,
  `sys_business_type_name` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `sys_by_dis_type` tinyint DEFAULT NULL COMMENT 'nxDis 1, gbDis 2,',
  PRIMARY KEY (`sys_business_type_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for sys_city
-- ----------------------------
DROP TABLE IF EXISTS `sys_city`;
CREATE TABLE `sys_city` (
  `sys_city_id` int NOT NULL AUTO_INCREMENT,
  `sys_city_type` tinyint DEFAULT NULL,
  `sys_city_name` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `sys_city_father_id` int DEFAULT NULL,
  `sys_city_py` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `sys_city_pinyin` varchar(40) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `sys_city_level` varchar(20) COLLATE utf16_czech_ci DEFAULT NULL,
  PRIMARY KEY (`sys_city_id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for sys_city_market
-- ----------------------------
DROP TABLE IF EXISTS `sys_city_market`;
CREATE TABLE `sys_city_market` (
  `sys_city_market_id` int NOT NULL AUTO_INCREMENT,
  `sys_cm_city_id` int DEFAULT NULL,
  `sys_cm_market_name` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL,
  `sys_cm_machine_agent_id` int DEFAULT NULL,
  `sys_cm_register_gift_points` int DEFAULT '1000' COMMENT '配送商注册赠送试用点数',
  `sys_cm_points_per_yuan` int DEFAULT '100' COMMENT '一元兑换点数比例',
  `sys_cm_self_print_enabled` tinyint(1) DEFAULT '0' COMMENT '是否开通自助打印机器：0=关闭，1=开启',
  `sys_cm_manager_phone` varchar(20) COLLATE utf16_czech_ci DEFAULT NULL COMMENT '市场管理人员手机号',
  `sys_cm_area_coordinates` text COLLATE utf16_czech_ci COMMENT '市场区域范围坐标（JSON格式存储多边形坐标点）',
  `sys_cm_center_latitude` decimal(10,7) DEFAULT NULL COMMENT '市场中心点纬度',
  `sys_cm_center_longitude` decimal(10,7) DEFAULT NULL COMMENT '市场中心点经度',
  `sys_cm_delivery_radius` int DEFAULT '5000' COMMENT '市场配送半径（米），默认5公里',
  `sys_cm_pay_config_class` varchar(100) COLLATE utf16_czech_ci DEFAULT NULL COMMENT '市场支付配置类名（如：MyWxJjdhPayConfig）',
  `sys_cm_mini_app_id` varchar(50) COLLATE utf16_czech_ci DEFAULT NULL COMMENT '市场小程序AppID（如：wx58ba279bc3d04c4a）',
  PRIMARY KEY (`sys_city_market_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;

-- ----------------------------
-- Table structure for sys_config
-- ----------------------------
DROP TABLE IF EXISTS `sys_config`;
CREATE TABLE `sys_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `key` varchar(50) DEFAULT NULL COMMENT 'key',
  `value` varchar(2000) DEFAULT NULL COMMENT 'value',
  `status` tinyint DEFAULT '1' COMMENT '状态   0：隐藏   1：显示',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `key` (`key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COMMENT='系统配置信息表';

-- ----------------------------
-- Table structure for sys_menu
-- ----------------------------
DROP TABLE IF EXISTS `sys_menu`;
CREATE TABLE `sys_menu` (
  `menu_id` bigint NOT NULL AUTO_INCREMENT,
  `parent_id` bigint DEFAULT NULL COMMENT '父菜单ID，一级菜单为0',
  `name` varchar(50) DEFAULT NULL COMMENT '菜单名称',
  `url` varchar(200) DEFAULT NULL COMMENT '菜单URL',
  `perms` varchar(500) DEFAULT NULL COMMENT '授权(多个用逗号分隔，如：user:list,user:create)',
  `type` int DEFAULT NULL COMMENT '类型   0：目录   1：菜单   2：按钮',
  `icon` varchar(50) DEFAULT NULL COMMENT '菜单图标',
  `order_num` int DEFAULT NULL COMMENT '排序',
  `pro_type` tinyint DEFAULT NULL COMMENT '0 系统；1 community；2 连锁采购；3 批发商；',
  PRIMARY KEY (`menu_id`)
) ENGINE=InnoDB AUTO_INCREMENT=113 DEFAULT CHARSET=utf8mb3 COMMENT='菜单管理';

-- ----------------------------
-- Table structure for sys_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role` (
  `role_id` bigint NOT NULL AUTO_INCREMENT,
  `role_name` varchar(100) DEFAULT NULL COMMENT '角色名称',
  `remark` varchar(100) DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`role_id`)
) ENGINE=InnoDB AUTO_INCREMENT=35 DEFAULT CHARSET=utf8mb3 COMMENT='角色';

-- ----------------------------
-- Table structure for sys_role_menu
-- ----------------------------
DROP TABLE IF EXISTS `sys_role_menu`;
CREATE TABLE `sys_role_menu` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_id` bigint DEFAULT NULL COMMENT '角色ID',
  `menu_id` bigint DEFAULT NULL COMMENT '菜单ID',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=177 DEFAULT CHARSET=utf8mb3 COMMENT='角色与菜单对应关系';

-- ----------------------------
-- Table structure for sys_user
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `user_id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `password` varchar(100) DEFAULT NULL COMMENT '密码',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `mobile` varchar(100) DEFAULT NULL COMMENT '手机号',
  `status` tinyint DEFAULT NULL COMMENT '状态  0：禁用   1：正常',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `user_dis_user_id` int DEFAULT NULL,
  `user_dis_id` int DEFAULT NULL,
  PRIMARY KEY (`user_id`) USING BTREE,
  UNIQUE KEY `username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=428 DEFAULT CHARSET=utf8mb3 COMMENT='系统用户';

-- ----------------------------
-- Table structure for sys_user_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL COMMENT '用户ID',
  `role_id` bigint DEFAULT NULL COMMENT '角色ID',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=386 DEFAULT CHARSET=utf8mb3 COMMENT='用户与角色对应关系';

-- ----------------------------
-- View structure for v_daily_print_stats
-- ----------------------------
DROP VIEW IF EXISTS `v_daily_print_stats`;
CREATE ALGORITHM = UNDEFINED SQL SECURITY DEFINER VIEW `v_daily_print_stats` AS select `nx_machine_print_record`.`nx_pr_market_id` AS `market_id`,cast(`nx_machine_print_record`.`nx_pr_print_time` as date) AS `print_date`,count(0) AS `print_count`,sum(`nx_machine_print_record`.`nx_pr_paper_count`) AS `total_paper`,sum(`nx_machine_print_record`.`nx_pr_bill_total`) AS `total_amount`,count(distinct `nx_machine_print_record`.`nx_pr_device_id`) AS `device_count`,count(distinct `nx_machine_print_record`.`nx_pr_distributer_id`) AS `distributer_count` from `nx_machine_print_record` where (`nx_machine_print_record`.`nx_pr_print_status` = 1) group by `nx_machine_print_record`.`nx_pr_market_id`,cast(`nx_machine_print_record`.`nx_pr_print_time` as date);

-- ----------------------------
-- View structure for v_device_print_stats
-- ----------------------------
DROP VIEW IF EXISTS `v_device_print_stats`;
CREATE ALGORITHM = UNDEFINED SQL SECURITY DEFINER VIEW `v_device_print_stats` AS select `pr`.`nx_pr_device_id` AS `device_id`,`pd`.`nx_pd_device_name` AS `device_name`,`pd`.`nx_pd_device_no` AS `device_no`,cast(`pr`.`nx_pr_print_time` as date) AS `print_date`,count(0) AS `print_count`,sum(`pr`.`nx_pr_paper_count`) AS `total_paper`,sum(`pr`.`nx_pr_bill_total`) AS `total_amount` from (`nx_machine_print_record` `pr` left join `nx_machine_printer_device` `pd` on((`pr`.`nx_pr_device_id` = `pd`.`nx_pd_id`))) where (`pr`.`nx_pr_print_status` = 1) group by `pr`.`nx_pr_device_id`,`pd`.`nx_pd_device_name`,`pd`.`nx_pd_device_no`,cast(`pr`.`nx_pr_print_time` as date);

-- ----------------------------
-- View structure for v_distributer_print_stats
-- ----------------------------
DROP VIEW IF EXISTS `v_distributer_print_stats`;
CREATE ALGORITHM = UNDEFINED SQL SECURITY DEFINER VIEW `v_distributer_print_stats` AS select `nx_machine_print_record`.`nx_pr_distributer_id` AS `distributer_id`,`nx_machine_print_record`.`nx_pr_market_id` AS `market_id`,cast(`nx_machine_print_record`.`nx_pr_print_time` as date) AS `print_date`,count(0) AS `print_count`,sum(`nx_machine_print_record`.`nx_pr_paper_count`) AS `total_paper`,sum(`nx_machine_print_record`.`nx_pr_bill_total`) AS `total_amount` from `nx_machine_print_record` where (`nx_machine_print_record`.`nx_pr_print_status` = 1) group by `nx_machine_print_record`.`nx_pr_distributer_id`,`nx_machine_print_record`.`nx_pr_market_id`,cast(`nx_machine_print_record`.`nx_pr_print_time` as date);

SET FOREIGN_KEY_CHECKS = 1;
