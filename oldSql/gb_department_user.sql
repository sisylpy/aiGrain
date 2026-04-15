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

 Date: 10/04/2026 22:55:43
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

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

SET FOREIGN_KEY_CHECKS = 1;
