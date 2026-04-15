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

 Date: 10/04/2026 22:56:15
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

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

SET FOREIGN_KEY_CHECKS = 1;
