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

 Date: 11/04/2026 13:59:19
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

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

SET FOREIGN_KEY_CHECKS = 1;
