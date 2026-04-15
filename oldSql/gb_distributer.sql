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

 Date: 10/04/2026 22:56:02
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

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

SET FOREIGN_KEY_CHECKS = 1;
