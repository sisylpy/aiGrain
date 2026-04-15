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

 Date: 11/04/2026 10:09:48
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

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

SET FOREIGN_KEY_CHECKS = 1;
