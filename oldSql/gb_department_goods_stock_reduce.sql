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

 Date: 11/04/2026 14:01:22
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

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

SET FOREIGN_KEY_CHECKS = 1;
