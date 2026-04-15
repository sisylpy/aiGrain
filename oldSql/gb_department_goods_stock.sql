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

 Date: 11/04/2026 14:01:09
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

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

SET FOREIGN_KEY_CHECKS = 1;
