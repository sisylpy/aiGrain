-- =====================================================
-- GB模块数据表结构 - 从老项目迁移到 ai-marketing
-- 来源: oldSql 目录下的 nongxinle 原表
-- 日期: 2026-04-11
-- =====================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 1. 批发商表 gb_distributer
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer`;
CREATE TABLE `gb_distributer` (
  `gb_distributer_id` int NOT NULL AUTO_INCREMENT COMMENT '批发商id',
  `gb_distributer_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '批发商名称',
  `gb_distributer_lan` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '批发商位置经度',
  `gb_distributer_lun` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '批发商位置纬度',
  `gb_distributer_business_type` int DEFAULT NULL COMMENT '批发商商业类型',
  `gb_distributer_manager` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_distributer_phone` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_distributer_address` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_distributer_img` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_distributer_settle_full_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '结算时间',
  `gb_distributer_settle_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '结算时间',
  `gb_distributer_settle_week` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '结算周',
  `gb_distributer_settle_month` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '结算月',
  `gb_distributer_settle_year` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '结算月',
  `gb_distributer_settle_times` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '结算月',
  `gb_distributer_time_quantum` tinyint DEFAULT NULL COMMENT '经营时间段',
  `gb_distributer_print_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '批发商名称',
  `gb_distributer_buy_quantity` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_distributer_sys_city_id` tinyint DEFAULT NULL COMMENT '批发商商业类型',
  `gb_distributer_nx_dis_id` tinyint DEFAULT NULL COMMENT '批发商商业类型',
  `gb_distributer_pick_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '批发商名称',
  `gb_distributer_record_seconds` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '批发商名称',
  `gb_distributer_stock_cycle` tinyint DEFAULT NULL COMMENT '库存显示周期',
  PRIMARY KEY (`gb_distributer_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_czech_ci;

-- ----------------------------
-- 2. 订货部门表 gb_department
-- ----------------------------
DROP TABLE IF EXISTS `gb_department`;
CREATE TABLE `gb_department` (
  `gb_department_id` int NOT NULL AUTO_INCREMENT COMMENT '订货部门id',
  `gb_department_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '订货部门名称',
  `gb_department_father_id` int DEFAULT NULL COMMENT '订货部门上级id',
  `gb_department_type` tinyint DEFAULT NULL COMMENT '订货部门类型',
  `gb_department_sub_amount` int DEFAULT NULL COMMENT '订货部门子部门数量',
  `gb_department_dis_id` int DEFAULT NULL COMMENT '订货部门批发商id',
  `gb_department_file_path` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_department_is_group_dep` tinyint DEFAULT NULL COMMENT '是客户吗',
  `gb_department_print_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_department_show_weeks` tinyint DEFAULT '1',
  `gb_department_settle_type` tinyint DEFAULT NULL,
  `gb_department_attr_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '客户简称',
  `gb_department_route_id` int DEFAULT NULL,
  `gb_department_settle_full_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '结算时间',
  `gb_department_settle_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '结算时间',
  `gb_department_settle_week` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '结算周',
  `gb_department_settle_month` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '结算月',
  `gb_department_settle_times` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '结算月',
  `gb_department_settle_year` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '结算月',
  `gb_department_dep_settle_id` int DEFAULT NULL COMMENT '结算月',
  `gb_department_level` int DEFAULT NULL COMMENT '加盟级别',
  `gb_department_sort` int DEFAULT NULL COMMENT '排序',
  `gb_department_print_set` int DEFAULT NULL COMMENT '排序',
  `gb_department_name_py` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '订货部门名称拼音',
  `gb_department_latitude` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_department_longitude` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DB_dep_father_id` int DEFAULT NULL,
  PRIMARY KEY (`gb_department_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=32 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_czech_ci;

-- ----------------------------
-- 3. 订货部门用户表 gb_department_user
-- ----------------------------
DROP TABLE IF EXISTS `gb_department_user`;
CREATE TABLE `gb_department_user` (
  `gb_department_user_id` int NOT NULL AUTO_INCREMENT COMMENT '订货部门用户id',
  `gb_DU_department_id` int DEFAULT NULL COMMENT '订货部门id',
  `gb_DU_wx_avartra_url` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '订货部门用户微信头像',
  `gb_DU_wx_nick_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '订货部门用户微信昵称',
  `gb_DU_wx_open_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '订货部门用户微信openid',
  `gb_DU_wx_phone` varchar(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '订货部门用户微信手机号码',
  `gb_DU_admin` tinyint DEFAULT NULL COMMENT '订货部门用户是否是管理员',
  `gb_DU_distributer_id` int DEFAULT NULL COMMENT '订货部门批发商id',
  `gb_DU_url_change` tinyint DEFAULT NULL,
  `gb_DU_department_father_id` int DEFAULT NULL,
  `gb_DU_join_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DU_print_device_id` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DU_print_bill_device_id` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DU_customer_service` int DEFAULT NULL,
  `gb_DU_login_times` int DEFAULT NULL,
  PRIMARY KEY (`gb_department_user_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_czech_ci;

-- ----------------------------
-- 4. 批发商用户表 gb_distributer_user
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_user`;
CREATE TABLE `gb_distributer_user` (
  `gb_distributer_user_id` int NOT NULL AUTO_INCREMENT COMMENT '批发商用户id',
  `gb_DIU_wx_avartra_url` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '用户名',
  `gb_DIU_wx_nick_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '登陆密码',
  `gb_DIU_wx_open_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DIU_wx_phone` varchar(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DIU_distributer_id` int DEFAULT NULL,
  `gb_DIU_admin` tinyint DEFAULT NULL,
  `gb_DIU_print_device_id` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DIU_url_change` tinyint DEFAULT NULL,
  `gb_DIU_print_bill_device_id` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DIU_qy_corp_user_id` int DEFAULT NULL COMMENT '企业用户id',
  `gb_DIU_login_times` int DEFAULT NULL COMMENT '企业用户id',
  PRIMARY KEY (`gb_distributer_user_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_czech_ci;

-- ----------------------------
-- 5. 批发商商品表 gb_distributer_goods
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_goods`;
CREATE TABLE `gb_distributer_goods` (
  `gb_distributer_goods_id` int NOT NULL AUTO_INCREMENT COMMENT '社区商品id',
  `gb_dg_dfg_goods_father_id` int DEFAULT NULL COMMENT '批发商父类商品id',
  `gb_dg_distributer_id` int DEFAULT NULL COMMENT '批发商id',
  `gb_dg_goods_status` tinyint DEFAULT NULL COMMENT '商品状态',
  `gb_dg_goods_is_weight` tinyint DEFAULT NULL COMMENT '是否称重',
  `gb_dg_goods_name` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '商品名称',
  `gb_dg_goods_detail` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '商品详细',
  `gb_dg_goods_standardname` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '商品规格',
  `gb_dg_goods_pinyin` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '社区商品拼音',
  `gb_dg_goods_py` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '社区商品拼音简拼',
  `gb_dg_nx_goods_id` int DEFAULT NULL COMMENT 'nxGoodsId',
  `gb_dg_nx_father_img` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '进货方式',
  `gb_dg_nx_father_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT 'nxGoodsFatherId',
  `gb_dg_nx_grand_id` int DEFAULT NULL COMMENT 'nxGoodsGrandid',
  `gb_dg_nx_great_grand_id` int DEFAULT NULL COMMENT 'nxGreatGrandid',
  `gb_dg_pull_off` tinyint DEFAULT NULL COMMENT '是否下架',
  `gb_dg_goods_brand` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_dg_goods_place` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_dg_nx_goods_father_color` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_dg_goods_standard_weight` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_dg_goods_type` tinyint DEFAULT NULL COMMENT '1 集采 2出库 3 自采',
  `gb_dg_goods_price` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '价格',
  `gb_dg_goods_lowest_price` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_dg_goods_highest_price` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_dg_nx_distributer_id` int DEFAULT NULL COMMENT '商品库父类id',
  `gb_dg_nx_distributer_goods_id` int DEFAULT NULL,
  `gb_dg_gb_department_id` int DEFAULT NULL COMMENT '库房或中央厨房id',
  `gb_dg_control_price` tinyint DEFAULT NULL COMMENT '是否控制价格',
  `gb_dg_control_fresh` tinyint DEFAULT NULL COMMENT '是否控制鲜度',
  `gb_dg_fresh_warn_hour` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '鲜度报警小时',
  `gb_dg_fresh_waste_hour` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '废弃报警小时',
  `gb_dg_goods_inventory_type` tinyint DEFAULT NULL COMMENT '盘库方式1 月，2周，3日',
  `gb_dg_gb_supplier_id` int DEFAULT NULL COMMENT '指定供货商id',
  `gb_dg_franchise_price_one` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_dg_franchise_price_two` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_dg_franchise_price_three` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_dg_franchise_price_one_update` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_dg_franchise_price_two_update` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_dg_franchise_price_three_update` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_dg_is_franchise_price` int DEFAULT NULL COMMENT '加盟商商品',
  `gb_dg_is_self_control` int DEFAULT NULL COMMENT '自制商品',
  `gb_dg_self_price` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '自制价格',
  `gb_dg_selling_price` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '销售价格',
  `gb_dg_goods_sort` int DEFAULT NULL COMMENT '商品状态',
  `gb_dg_goods_sons_sort` int DEFAULT NULL COMMENT '子商品顺序',
  `gb_dg_goods_is_hidden` int DEFAULT NULL COMMENT '是否显示',
  `gb_dg_nx_father_img_large` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '进货方式',
  `gb_dg_nx_distributer_goods_price` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_dg_dfg_goods_grand_id` int DEFAULT NULL COMMENT '批发商父类商品id',
  `gb_dg_dfg_goods_great_id` int DEFAULT NULL COMMENT '批发商父类商品id',
  `gb_dg_quantity_days` int DEFAULT NULL COMMENT '批发商父类商品id',
  PRIMARY KEY (`gb_distributer_goods_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=32 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_czech_ci;

-- ----------------------------
-- 6. 部门订单表 gb_department_orders
-- ----------------------------
DROP TABLE IF EXISTS `gb_department_orders`;
CREATE TABLE `gb_department_orders` (
  `gb_department_orders_id` int NOT NULL AUTO_INCREMENT COMMENT '部门订单id',
  `gb_DO_nx_goods_id` int DEFAULT NULL COMMENT '部门订单nx商品id',
  `gb_DO_nx_goods_father_id` int DEFAULT NULL COMMENT '部门订单商品父id',
  `gb_DO_dis_goods_id` int DEFAULT NULL COMMENT '部门订单社区商品id',
  `gb_DO_dis_goods_father_id` int DEFAULT NULL COMMENT '批发商父级商品id',
  `gb_DO_dep_dis_goods_id` int DEFAULT NULL,
  `gb_DO_quantity` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '部门订单申请数量',
  `gb_DO_standard` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '部门订单申请规格',
  `gb_DO_remark` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '部门订单申请备注',
  `gb_DO_weight` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '部门订单重量',
  `gb_DO_price` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '部门订单商品单价',
  `gb_DO_subtotal` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '部门订单申请商品小计',
  `gb_DO_department_id` int DEFAULT NULL COMMENT '部门订单部门id',
  `gb_DO_department_father_id` int DEFAULT NULL,
  `gb_DO_distributer_id` int DEFAULT NULL COMMENT '部门订单批发商id',
  `gb_DO_purchase_user_id` int DEFAULT NULL COMMENT '部门商品采购员id',
  `gb_DO_bill_id` int DEFAULT NULL COMMENT '部门订单账单id',
  `gb_DO_status` tinyint DEFAULT NULL COMMENT '部门订单申请商品状态',
  `gb_DO_order_user_id` int DEFAULT NULL COMMENT '部门订单订货用户id',
  `gb_DO_pick_user_id` int DEFAULT NULL COMMENT '部门订单商品称重用户id',
  `gb_DO_receive_user_id` int DEFAULT NULL COMMENT '收货用户id',
  `gb_DO_buy_status` tinyint DEFAULT NULL COMMENT '部门订单商品进货状态',
  `gb_DO_purchase_goods_id` int DEFAULT NULL COMMENT '订单采购商品id',
  `gb_DO_apply_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '部门订单申请时间',
  `gb_DO_apply_what_day` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '部门订单申请星期',
  `gb_DO_apply_arrive_date` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '部门订单申请时间',
  `gb_DO_apply_full_time` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DO_apply_only_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DO_arrive_only_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DO_arrive_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '部门订单送达时间',
  `gb_DO_arrive_weeks_year` int DEFAULT NULL COMMENT '本年第几周',
  `gb_DO_arrive_what_day` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '星期几',
  `gb_DO_goods_type` tinyint DEFAULT NULL COMMENT '配送商品0，自采购商品1',
  `gb_DO_operation_time` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DO_is_agent` tinyint DEFAULT NULL,
  `gb_DO_cost_price` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '进货单价',
  `gb_DO_cost_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '进货重量',
  `gb_DO_cost_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '进货小计',
  `gb_DO_nx_distributer_id` int DEFAULT NULL COMMENT 'comGoods的批发商商品id',
  `gb_DO_nx_distributer_goods_id` int DEFAULT NULL COMMENT 'comGoods的批发商',
  `gb_DO_dg_goods_sell_type` tinyint DEFAULT NULL COMMENT '按规格销售方式',
  `gb_DO_nx_department_order_id` int DEFAULT NULL COMMENT 'nxDepartmentId',
  `gb_DO_to_department_id` int DEFAULT NULL COMMENT '库房或者中央厨房部门id',
  `gb_DO_order_type` tinyint DEFAULT NULL COMMENT '订单类型',
  `gb_DO_return_user_id` int DEFAULT NULL COMMENT '库房或者中央厨房部门id',
  `gb_DO_ds_standard_id` int DEFAULT NULL COMMENT '订货单位id',
  `gb_DO_ds_standard_scale` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '订货单位比例',
  `gb_DO_scale_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '部门订单重量',
  `gb_DO_scale_price` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '部门订单重量',
  `gb_DO_weight_total_id` int DEFAULT NULL COMMENT '拣货单id',
  `gb_DO_selling_price` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '零售单价',
  `gb_DO_selling_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '零售小计',
  `gb_DO_weight_goods_id` int DEFAULT NULL COMMENT 'id',
  `gb_DO_price_different` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT 'id',
  `gb_DO_dgsr_return_id` int DEFAULT NULL COMMENT '库房或者中央厨房部门id',
  `gb_DO_dis_goods_grand_id` int DEFAULT NULL COMMENT '批发商父级商品id',
  `gb_DO_nx_goods_grand_id` int DEFAULT NULL COMMENT '批发商父级商品id',
  `gb_DO_nx_goods_great_id` int DEFAULT NULL COMMENT '批发商父级商品id',
  `gb_DO_print_standard` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT 'id',
  `gb_DO_cost_price_level` int DEFAULT NULL COMMENT '批发商父级商品id',
  `gb_DO_goods_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '商品名称',
  `gb_DO_dis_goods_great_id` int DEFAULT NULL COMMENT '批发商父级商品id',
  PRIMARY KEY (`gb_department_orders_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=93 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_czech_ci;

-- ----------------------------
-- 7. 部门配送商品表 gb_department_dis_goods
-- ----------------------------
DROP TABLE IF EXISTS `gb_department_dis_goods`;
CREATE TABLE `gb_department_dis_goods` (
  `gb_department_dis_goods_id` int NOT NULL AUTO_INCREMENT,
  `gb_DDG_department_father_id` int DEFAULT NULL,
  `gb_DDG_department_id` int DEFAULT NULL,
  `gb_DDG_dis_goods_id` int DEFAULT NULL,
  `gb_DDG_dis_goods_father_id` int DEFAULT NULL,
  `gb_DDG_dep_goods_name` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DDG_dep_goods_pinyin` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DDG_dep_goods_py` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DDG_dep_goods_standardname` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DDG_dep_goods_detail` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DDG_dep_goods_brand` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DDG_dep_goods_place` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DDG_gb_department_id` int DEFAULT NULL COMMENT '库房或中央厨房id',
  `gb_DDG_goods_type` tinyint DEFAULT NULL,
  `gb_DDG_nx_distributer_id` int DEFAULT NULL COMMENT '商品库父类id',
  `gb_DDG_nx_distributer_goods_id` int DEFAULT NULL,
  `gb_DDG_inventory_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DDG_inventory_full_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DDG_stock_total_weight` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DDG_stock_total_subtotal` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DDG_gb_supplier_id` int DEFAULT NULL COMMENT '指定供货商id',
  `gb_DDG_prepare_total_weight` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DDG_show_standard_name` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DDG_show_standard_id` int DEFAULT NULL,
  `gb_DDG_show_standard_scale` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DDG_level_price` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '加盟级别商品价格',
  `gb_DDG_prepare_status` int DEFAULT NULL,
  `gb_DDG_selling_price` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '加盟级别商品价格',
  `gb_DDG_show_standard_weight` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DDG_order_price` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DDG_order_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DDG_order_remark` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DDG_order_quantity` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DDG_order_standard` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DDG_print_standard` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DDG_order_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DDG_gb_dis_id` int DEFAULT NULL,
  `gb_DDG_dep_goods_pull_off` int DEFAULT NULL,
  `gb_DDG_dep_goods_status` int DEFAULT NULL,
  `gb_DDG_dis_goods_grand_id` int DEFAULT NULL,
  `gb_DDG_dis_goods_great_id` int DEFAULT NULL,
  `gb_DDG_order_goods_name` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DDG_order_price_level` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  PRIMARY KEY (`gb_department_dis_goods_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=75 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_czech_ci;

-- ----------------------------
-- 8. 批发商父类商品表 gb_distributer_father_goods
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_father_goods`;
CREATE TABLE `gb_distributer_father_goods` (
  `gb_distributer_father_goods_id` int NOT NULL AUTO_INCREMENT,
  `gb_dfg_father_goods_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_dfg_father_goods_img` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_dfg_father_goods_sort` int DEFAULT NULL,
  `gb_dfg_father_goods_color` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_dfg_fathers_father_id` int DEFAULT NULL,
  `gb_dfg_father_goods_level` tinyint DEFAULT NULL,
  `gb_dfg_distributer_id` int DEFAULT NULL,
  `gb_dfg_goods_amount` int DEFAULT NULL,
  `gb_dfg_nx_goods_id` int DEFAULT NULL,
  `gb_dfg_price_amount` int DEFAULT NULL,
  `gb_dfg_price_two_amount` int DEFAULT NULL,
  `gb_dfg_price_three_amount` int DEFAULT NULL,
  `gb_dfg_father_goods_img_large` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  PRIMARY KEY (`gb_distributer_father_goods_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=71 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_czech_ci;

-- ----------------------------
-- 9. 批发商商品规格表 gb_distributer_standard
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_standard`;
CREATE TABLE `gb_distributer_standard` (
  `gb_distributer_standard_id` int NOT NULL AUTO_INCREMENT,
  `gb_DS_dis_goods_id` int DEFAULT NULL,
  `gb_DS_standard_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DS_standard_file_path` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DS_standard_scale` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DS_standard_error` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DS_standard_sort` int DEFAULT NULL,
  `gb_DS_standard_weight` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  PRIMARY KEY (`gb_distributer_standard_id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_czech_ci;

-- ----------------------------
-- 10. 批发商别名表 gb_distributer_alias
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_alias`;
CREATE TABLE `gb_distributer_alias` (
  `gb_distributer_alias_id` int NOT NULL AUTO_INCREMENT,
  `gb_DA_dis_goods_id` int DEFAULT NULL,
  `gb_DA_alias_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DA_gb_alias_id` int DEFAULT NULL,
  `gb_DA_alias_pinyin` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DA_alias_py` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  PRIMARY KEY (`gb_distributer_alias_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_czech_ci;

-- ----------------------------
-- 11. 部门商品库存表 gb_department_goods_stock
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
  `gb_dgs_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '批次数量',
  `gb_dgs_price` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '批次单价',
  `gb_dgs_selling_price` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '销售单价',
  `gb_dgs_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '批次成本',
  `gb_dgs_rest_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '剩余数量',
  `gb_dgs_rest_weight_show_standard` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '剩余数量显示规格',
  `gb_dgs_rest_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '批次剩余成本',
  `gb_dgs_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '批次日期',
  `gb_dgs_full_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '盘库日期',
  `gb_dgs_out_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '出库日期',
  `gb_dgs_out_full_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '出库时间',
  `gb_dgs_out_hour` int DEFAULT NULL COMMENT '出货小时',
  `gb_dgs_receive_user_id` int DEFAULT NULL COMMENT '接收用户',
  `gb_dgs_status` tinyint DEFAULT NULL COMMENT '批次状态',
  `gb_dgs_gb_pur_goods_id` int DEFAULT NULL COMMENT '批次采购商品id',
  `gb_dgs_gb_price_goods_id` int DEFAULT NULL COMMENT '价格异常商品id',
  `gb_dgs_gb_price_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '价格异常价格',
  `gb_dgs_gb_price_subtotal_scale` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '价格异常价格',
  `gb_dgs_gb_goods_stock_id` int DEFAULT NULL COMMENT '批次采购商品id',
  `gb_dgs_gb_from_department_id` int DEFAULT NULL COMMENT '出库部门id',
  `gb_dgs_week` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '批次周',
  `gb_dgs_month` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '批次月',
  `gb_dgs_year` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '批次年',
  `gb_dgs_time_stamp` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '时间戳',
  `gb_dgs_warn_full_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '盘库日期',
  `gb_dgs_waste_full_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '盘库日期',
  `gb_dgs_warn_time_quantum_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '报警时间段名称',
  `gb_dgs_waste_time_quantum_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '废弃时间段名称',
  `gb_dgs_do_waste_full_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '执行废弃时间',
  `gb_dgs_inventory_full_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '盘库日期',
  `gb_dgs_inventory_date` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '盘库日期',
  `gb_dgs_inventory_week` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '盘库周',
  `gb_dgs_inventory_month` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '盘库月',
  `gb_dgs_inventory_year` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '盘库月',
  `gb_dgs_dep_settle_id` int DEFAULT NULL,
  `gb_dgs_from_dep_settle_id` int DEFAULT NULL COMMENT '出货部门settleId',
  `gb_dgs_return_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '退货数量',
  `gb_dgs_return_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '退货成本',
  `gb_dgs_produce_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '制作数量',
  `gb_dgs_produce_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '制作成本',
  `gb_dgs_loss_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '损耗数量',
  `gb_dgs_loss_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '损耗成本',
  `gb_dgs_waste_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '制作数量',
  `gb_dgs_waste_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '制作数量',
  `gb_dgs_between_price` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '利润单价',
  `gb_dgs_profit_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '利润小计',
  `gb_dgs_profit_weight` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '利润重量',
  `gb_dgs_selling_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '销售小计',
  `gb_dgs_weight_goods_id` int DEFAULT NULL COMMENT '备货商品id',
  `gb_dgs_after_profit_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '销售利润',
  `gb_dgs_cost_rate` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '成本率',
  `gb_dgs_rest_weight_show_standard_name` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '剩余数量显示规格',
  `gb_dgs_nx_distributer_id` int DEFAULT NULL COMMENT '备货商品id',
  `gb_dgs_produce_selling_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '销售小计',
  `gb_dgs_gb_dis_goods_grand_id` int DEFAULT NULL,
  `gb_dgs_gb_dis_goods_great_id` int DEFAULT NULL,
  `gb_dgs_stars` int DEFAULT NULL,
  `gb_dgs_nx_supplier_id` int DEFAULT NULL COMMENT '备货商品id',
  `gb_dgs_pur_user_id` int DEFAULT NULL COMMENT '备货商品id',
  PRIMARY KEY (`gb_department_goods_stock_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=75 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_czech_ci;

-- ----------------------------
-- 12. 批发商支付表 gb_distributer_pay
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_pay`;
CREATE TABLE `gb_distributer_pay` (
  `gb_distributer_pay_id` int NOT NULL AUTO_INCREMENT,
  `gb_gdp_gb_dis_id` int DEFAULT NULL,
  `gb_gdp_pay_subtotal` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_gdp_from_time` date DEFAULT NULL,
  `gb_gdp_stop_time` date DEFAULT NULL,
  `gb_gdp_pay_time` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_gdp_type` tinyint DEFAULT NULL,
  `gb_gdp_status` tinyint DEFAULT NULL,
  `gb_gdp_trade_no` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_gdp_gb_new_dis_id` int DEFAULT NULL,
  `gb_gdp_buy_quantity` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_gdp_img_url` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_gdp_sell_detail` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  PRIMARY KEY (`gb_distributer_pay_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_czech_ci;

-- ----------------------------
-- 13. 批发商模块表 gb_distributer_module
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_module`;
CREATE TABLE `gb_distributer_module` (
  `gb_distributer_module_id` int NOT NULL AUTO_INCREMENT,
  `gb_dm_fixed_supplier_number` int DEFAULT '-1',
  `gb_dm_purchase_number` int DEFAULT '-1',
  `gb_dm_stock_number` int DEFAULT '-1',
  `gb_dm_app_supplier_number` int DEFAULT '-1',
  `gb_dm_central_kitchen_number` int DEFAULT '-1',
  `gb_dm_direct_sales_number` int DEFAULT '-1',
  `gb_dm_franchisee_number` int DEFAULT '-1',
  `gb_dm_distributer_id` int NOT NULL DEFAULT '-1',
  PRIMARY KEY (`gb_distributer_module_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_czech_ci;

-- ----------------------------
-- 14. 批发商采购批次表 gb_distributer_purchase_batch
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_purchase_batch`;
CREATE TABLE `gb_distributer_purchase_batch` (
  `gb_distributer_purchase_batch_id` int NOT NULL AUTO_INCREMENT COMMENT '批发商进货批次id',
  `gb_DPB_status` tinyint DEFAULT NULL COMMENT '批发商进货批次状态',
  `gb_DPB_user_admin_type` tinyint DEFAULT NULL COMMENT '进货批次用户类型',
  `gb_DPB_time` varchar(12) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '批发商进货批次时间',
  `gb_DPB_pur_user_id` int DEFAULT NULL COMMENT '批发商进货采购员id',
  `gb_DPB_distributer_id` int DEFAULT NULL COMMENT '批发商id',
  `gb_DPB_date` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DPB_hour` varchar(4) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DPB_minute` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DPB_subtotal` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DPB_pur_department_id` int DEFAULT NULL COMMENT '采购部门id',
  `gb_DPB_pay_type` int DEFAULT NULL COMMENT '付款方式:0==现金; 1 ==记账，',
  `gb_DPB_pay_subtotal` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '付款金额',
  `gb_DPB_supplier_id` int DEFAULT NULL COMMENT '供货商商id',
  `gb_DPB_purchase_month` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DPB_purchase_year` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DPB_purchase_full_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DPB_gb_supplier_payment_id` int DEFAULT NULL,
  `gb_DPB_purchase_week` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DPB_seller_reply_full_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DPB_finish_full_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DPB_nx_distributer_id` int DEFAULT NULL COMMENT '供货商商id',
  `gb_DPB_buy_user_id` int DEFAULT NULL COMMENT '供货商商id',
  `gb_DPB_sell_user_id` int DEFAULT NULL COMMENT '供货商商id',
  `gb_DPB_buy_user_open_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DPB_sell_user_open_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DPB_purchase_type` tinyint DEFAULT NULL COMMENT '0 手动订货，1 自动订货',
  `gb_DPB_dep_bill_id` int DEFAULT NULL COMMENT '供货商商id',
  PRIMARY KEY (`gb_distributer_purchase_batch_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_czech_ci;

-- ----------------------------
-- 15. 批发商采购商品表 gb_distributer_purchase_goods
-- ----------------------------
DROP TABLE IF EXISTS `gb_distributer_purchase_goods`;
CREATE TABLE `gb_distributer_purchase_goods` (
  `gb_distributer_purchase_goods_id` int NOT NULL AUTO_INCREMENT COMMENT '批发商采购商品id',
  `gb_DPG_dis_goods_id` int DEFAULT NULL COMMENT '采购商品id',
  `gb_DPG_dis_goods_father_id` int DEFAULT NULL COMMENT '采购父级商品id',
  `gb_DPG_quantity` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '采购数量',
  `gb_DPG_standard` varchar(6) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '采购规格',
  `gb_DPG_status` tinyint DEFAULT NULL COMMENT '采购状态',
  `gb_DPG_distributer_id` int DEFAULT NULL COMMENT '采购批发商id',
  `gb_DPG_purchase_type` tinyint DEFAULT NULL COMMENT '采购方式："1 订单采购""2 添加采购"',
  `gb_DPG_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '采购时间',
  `gb_DPG_batch_id` int DEFAULT NULL COMMENT '采购批次号',
  `gb_DPG_pur_user_id` int DEFAULT NULL COMMENT '采购方式为"采购"的采购员id',
  `gb_DPG_buy_price` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '采购单价',
  `gb_DPG_buy_quantity` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '采购数量',
  `gb_DPG_orders_amount` int DEFAULT NULL COMMENT '订单采购的订单数量',
  `gb_DPG_type_add_user_id` int DEFAULT NULL COMMENT '添加采购用户id',
  `gb_DPG_apply_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DPG_purchase_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '采购日期',
  `gb_DPG_input_type` tinyint DEFAULT NULL,
  `gb_DPG_buy_subtotal` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL,
  `gb_DPG_purchase_department_id` int DEFAULT NULL COMMENT '库房或者中央厨房采购部门id',
  `gb_DPG_purchase_month` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '采购日期',
  `gb_DPG_purchase_year` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '采购日期',
  `gb_DPG_purchase_full_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '采购日期',
  `gb_DPG_dis_goods_price_id` int DEFAULT NULL COMMENT '采购商品价格表id',
  `gb_DPG_purchase_week` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '采购week',
  `gb_DPG_purchase_week_year` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '第几周',
  `gb_DPG_buy_scale_price` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '订货单位价格',
  `gb_DPG_buy_scale_quantity` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '订货单位单价',
  `gb_DPG_buy_scale` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '订货单位系数',
  `gb_DPG_buy_price_reason` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '采购单价异常原因',
  `gb_DPG_pay_type` tinyint DEFAULT NULL COMMENT '支付方式',
  `gb_DPG_is_check` tinyint DEFAULT NULL COMMENT '支付方式',
  `gb_DPG_waste_full_time` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '废弃时间',
  `gb_DPG_warn_full_time` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '报警时间',
  `gb_DPG_weight_id` int DEFAULT NULL COMMENT '称重disid',
  `gb_DPG_purchase_nx_distributer_id` int DEFAULT NULL COMMENT '库房或者中央厨房采购部门id',
  `gb_DPG_orders_finish_amount` int DEFAULT NULL COMMENT '订单采购的订单数量',
  `gb_DPG_orders_bill_amount` int DEFAULT NULL COMMENT 'bill的订单数量',
  `gb_DPG_purchase_nx_supplier_id` int DEFAULT NULL COMMENT 'jsSupplierId',
  `gb_DPG_dis_goods_grand_id` int DEFAULT NULL,
  `gb_DPG_supplier_finish_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '采购日期',
  `gb_DPG_stock_finish_date` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '采购日期',
  `gb_DPG_dis_goods_great_id` int DEFAULT NULL,
  `gb_DPG_orders_weight_amount` int DEFAULT NULL COMMENT '订单采购的订单数量',
  PRIMARY KEY (`gb_distributer_purchase_goods_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=88 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_czech_ci;

-- ----------------------------
-- 16. nx_alias 表（商品别名）
-- ----------------------------
DROP TABLE IF EXISTS `nx_alias`;
CREATE TABLE `nx_alias` (
  `nx_alias_id` int NOT NULL AUTO_INCREMENT COMMENT '别名id',
  `nx_alias_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '别名名称',
  `nx_als_goods_id` int DEFAULT NULL COMMENT '别名商品id',
  `nx_als_sort` int DEFAULT NULL COMMENT '别名排序',
  `nx_alias_pinyin` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '别名名称',
  `nx_alias_py` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_czech_ci DEFAULT NULL COMMENT '别名名称',
  PRIMARY KEY (`nx_alias_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=123 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_czech_ci;

SET FOREIGN_KEY_CHECKS = 1;
