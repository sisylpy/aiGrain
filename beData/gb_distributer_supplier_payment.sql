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

 Date: 24/04/2026 00:37:26
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

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

SET FOREIGN_KEY_CHECKS = 1;
