-- =====================================================
-- GB模块数据表结构 (4个表)
-- 来源: oldSql 目录
-- 日期: 2026-04-10
-- =====================================================

-- ----------------------------
-- 1. 批发商表 gb_distributer
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer`;
CREATE TABLE `gb_distributer` (
  `gb_distributer_id` int NOT NULL AUTO_INCREMENT COMMENT '批发商id',
  `gb_distributer_name` varchar(50) DEFAULT NULL COMMENT '批发商名称',
  `gb_distributer_lan` varchar(200) DEFAULT NULL COMMENT '批发商位置经度',
  `gb_distributer_lun` varchar(200) DEFAULT NULL COMMENT '批发商位置纬度',
  `gb_distributer_business_type` int DEFAULT NULL COMMENT '批发商商业类型',
  `gb_distributer_manager` varchar(100) DEFAULT NULL,
  `gb_distributer_phone` varchar(30) DEFAULT NULL,
  `gb_distributer_address` varchar(200) DEFAULT NULL,
  `gb_distributer_img` varchar(200) DEFAULT NULL,
  `gb_distributer_settle_full_time` varchar(20) DEFAULT NULL COMMENT '结算时间',
  `gb_distributer_settle_date` varchar(20) DEFAULT NULL COMMENT '结算日期',
  `gb_distributer_settle_week` varchar(10) DEFAULT NULL COMMENT '结算周',
  `gb_distributer_settle_month` varchar(10) DEFAULT NULL COMMENT '结算月',
  `gb_distributer_settle_year` varchar(10) DEFAULT NULL COMMENT '结算年',
  `gb_distributer_settle_times` varchar(10) DEFAULT NULL COMMENT '结算次数',
  `gb_distributer_time_quantum` tinyint DEFAULT NULL COMMENT '经营时间段',
  `gb_distributer_print_name` varchar(50) DEFAULT NULL COMMENT '打印名称',
  `gb_distributer_buy_quantity` varchar(20) DEFAULT NULL,
  `gb_distributer_sys_city_id` tinyint DEFAULT NULL,
  `gb_distributer_nx_dis_id` tinyint DEFAULT NULL,
  `gb_distributer_pick_name` varchar(50) DEFAULT NULL COMMENT '简称',
  `gb_distributer_record_seconds` varchar(10) DEFAULT NULL,
  `gb_distributer_stock_cycle` tinyint DEFAULT NULL COMMENT '库存显示周期',
  PRIMARY KEY (`gb_distributer_id`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- 2. 订货部门表 gb_department
-- ----------------------------
DROP TABLE IF EXISTS `gb_department`;
CREATE TABLE `gb_department` (
  `gb_department_id` int NOT NULL AUTO_INCREMENT COMMENT '订货部门id',
  `gb_department_name` varchar(100) DEFAULT NULL COMMENT '订货部门名称',
  `gb_department_father_id` int DEFAULT NULL COMMENT '订货部门上级id',
  `gb_department_type` tinyint DEFAULT NULL COMMENT '订货部门类型',
  `gb_department_sub_amount` int DEFAULT NULL COMMENT '订货部门子部门数量',
  `gb_department_dis_id` int DEFAULT NULL COMMENT '订货部门批发商id',
  `gb_department_file_path` varchar(200) DEFAULT NULL,
  `gb_department_is_group_dep` tinyint DEFAULT NULL COMMENT '是客户吗',
  `gb_department_print_name` varchar(200) DEFAULT NULL,
  `gb_department_show_weeks` tinyint DEFAULT '1',
  `gb_department_settle_type` tinyint DEFAULT NULL,
  `gb_department_attr_name` varchar(50) DEFAULT NULL COMMENT '客户简称',
  `gb_department_route_id` int DEFAULT NULL,
  `gb_department_settle_full_time` varchar(20) DEFAULT NULL COMMENT '结算时间',
  `gb_department_settle_date` varchar(20) DEFAULT NULL COMMENT '结算日期',
  `gb_department_settle_week` varchar(10) DEFAULT NULL COMMENT '结算周',
  `gb_department_settle_month` varchar(10) DEFAULT NULL COMMENT '结算月',
  `gb_department_settle_year` varchar(10) DEFAULT NULL COMMENT '结算年',
  `gb_department_settle_times` varchar(10) DEFAULT NULL COMMENT '结算次数',
  `gb_department_dep_settle_id` int DEFAULT NULL,
  `gb_department_level` int DEFAULT NULL COMMENT '加盟级别',
  `gb_department_sort` int DEFAULT NULL COMMENT '排序',
  `gb_department_print_set` int DEFAULT NULL,
  `gb_department_name_py` varchar(100) DEFAULT NULL COMMENT '名称拼音',
  `gb_department_latitude` varchar(100) DEFAULT NULL,
  `gb_department_longitude` varchar(100) DEFAULT NULL,
  `gb_DB_dep_father_id` int DEFAULT NULL,
  PRIMARY KEY (`gb_department_id`)
) ENGINE=InnoDB AUTO_INCREMENT=32 DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- 3. 订货部门用户表 gb_department_user
-- ----------------------------
DROP TABLE IF EXISTS `gb_department_user`;
CREATE TABLE `gb_department_user` (
  `gb_department_user_id` int NOT NULL AUTO_INCREMENT COMMENT '订货部门用户id',
  `gb_DU_department_id` int DEFAULT NULL COMMENT '订货部门id',
  `gb_DU_wx_avartra_url` varchar(200) DEFAULT NULL COMMENT '微信头像',
  `gb_DU_wx_nick_name` varchar(200) DEFAULT NULL COMMENT '微信昵称',
  `gb_DU_wx_open_id` varchar(100) DEFAULT NULL COMMENT '微信openid',
  `gb_DU_wx_phone` varchar(15) DEFAULT NULL COMMENT '微信手机号码',
  `gb_DU_admin` tinyint DEFAULT NULL COMMENT '是否管理员',
  `gb_DU_distributer_id` int DEFAULT NULL COMMENT '批发商id',
  `gb_DU_url_change` tinyint DEFAULT NULL,
  `gb_DU_department_father_id` int DEFAULT NULL,
  `gb_DU_join_date` varchar(20) DEFAULT NULL,
  `gb_DU_print_device_id` varchar(40) DEFAULT NULL,
  `gb_DU_print_bill_device_id` varchar(40) DEFAULT NULL,
  `gb_DU_customer_service` int DEFAULT NULL,
  `gb_DU_login_times` int DEFAULT NULL,
  PRIMARY KEY (`gb_department_user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- 4. 批发商用户表 gb_distributer_user
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_user`;
CREATE TABLE `gb_distributer_user` (
  `gb_distributer_user_id` int NOT NULL AUTO_INCREMENT COMMENT '批发商用户id',
  `gb_DIU_wx_avartra_url` varchar(200) DEFAULT NULL COMMENT '微信头像',
  `gb_DIU_wx_nick_name` varchar(200) DEFAULT NULL COMMENT '微信昵称',
  `gb_DIU_wx_open_id` varchar(100) DEFAULT NULL,
  `gb_DIU_wx_phone` varchar(15) DEFAULT NULL,
  `gb_DIU_distributer_id` int DEFAULT NULL,
  `gb_DIU_admin` tinyint DEFAULT NULL,
  `gb_DIU_print_device_id` varchar(40) DEFAULT NULL,
  `gb_DIU_url_change` tinyint DEFAULT NULL,
  `gb_DIU_print_bill_device_id` varchar(40) DEFAULT NULL,
  `gb_DIU_qy_corp_user_id` int DEFAULT NULL COMMENT '企业用户id',
  `gb_DIU_login_times` int DEFAULT NULL,
  PRIMARY KEY (`gb_distributer_user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4;
