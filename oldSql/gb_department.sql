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

 Date: 10/04/2026 22:55:29
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

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

SET FOREIGN_KEY_CHECKS = 1;
