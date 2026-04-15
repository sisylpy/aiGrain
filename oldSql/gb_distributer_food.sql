-- ===========================
-- gb_distributer_food 表结构规范化
-- 参考 gb_distributer_goods 表的字段命名规则
-- ===========================

DROP TABLE IF EXISTS `gb_distributer_food`;

CREATE TABLE `gb_distributer_food` (
  `gb_distributer_food_id` int NOT NULL AUTO_INCREMENT COMMENT '菜品id',
  `gb_df_distributer_id` int DEFAULT NULL COMMENT '所属配送商id',
  `gb_df_nx_food_id` int DEFAULT NULL COMMENT '老系统食品id',
  `gb_df_food_name` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '菜品名称',
  `gb_df_food_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '菜品价格',
  `gb_df_status` tinyint DEFAULT NULL COMMENT '状态',
  `gb_df_food_pinyin` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '菜品拼音',
  `gb_df_food_py` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '菜品拼音简拼',
  `gb_df_food_father_id` int DEFAULT NULL COMMENT '菜品分类父id',
  `gb_df_food_img` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '菜品图片',
  `gb_df_food_img_large` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '菜品大图',
  `gb_df_food_method` varchar(400) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '制作方法',
  `gb_df_food_detail` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '菜品详情',
  `gb_df_goods_sort` int DEFAULT NULL COMMENT '排序',
  PRIMARY KEY (`gb_distributer_food_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;


-- ===========================
-- gb_distributer_food_goods 表结构规范化
-- ===========================

DROP TABLE IF EXISTS `gb_distributer_food_goods`;

CREATE TABLE `gb_distributer_food_goods` (
  `gb_distributer_food_goods_id` int NOT NULL AUTO_INCREMENT COMMENT '菜品原料id',
  `gb_dfg_dis_id` int DEFAULT NULL COMMENT '所属配送商id',
  `gb_dfg_food_id` int DEFAULT NULL COMMENT '菜品id',
  `gb_dfg_dis_goods_id` int DEFAULT NULL COMMENT '原料商品id',
  `gb_dfg_goods_amount` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '原料数量',
  `gb_dfg_goods_name` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '原料名称',
  `gb_dfg_goods_standardname` varchar(50) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '原料规格',
  `gb_dfg_status` tinyint DEFAULT NULL COMMENT '状态',
  PRIMARY KEY (`gb_distributer_food_goods_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;


-- ===========================
-- gb_dep_food 表结构规范化（门店菜品）
-- ===========================

DROP TABLE IF EXISTS `gb_dep_food`;

CREATE TABLE `gb_dep_food` (
  `gb_dep_food_id` int NOT NULL AUTO_INCREMENT COMMENT '门店菜品id',
  `gb_df_dep_id` int DEFAULT NULL COMMENT '所属门店id',
  `gb_df_food_id` int DEFAULT NULL COMMENT '菜品id（关联gb_distributer_food）',
  `gb_df_dep_father_id` int DEFAULT NULL COMMENT '门店菜品分类父id',
  `gb_df_food_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '菜品价格',
  `gb_df_status` tinyint DEFAULT NULL COMMENT '状态',
  `gb_df_distributer_id` int DEFAULT NULL COMMENT '配送商id',
  `gb_df_nx_food_id` int DEFAULT NULL COMMENT '老系统食品id',
  PRIMARY KEY (`gb_dep_food_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;


-- ===========================
-- gb_dep_food_sales 表结构规范化（门店菜品销售）
-- ===========================

DROP TABLE IF EXISTS `gb_dep_food_sales`;

CREATE TABLE `gb_dep_food_sales` (
  `gb_dep_food_sales_id` int NOT NULL AUTO_INCREMENT COMMENT '门店菜品销售id',
  `gb_dfs_dep_id` int DEFAULT NULL COMMENT '所属门店id',
  `gb_dfs_food_id` int DEFAULT NULL COMMENT '菜品id',
  `gb_dfs_dep_father_id` int DEFAULT NULL COMMENT '门店菜品分类父id',
  `gb_dfs_amount` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '销售数量',
  `gb_dfs_settle_id` int DEFAULT NULL COMMENT '结算id',
  `gb_dfs_month` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '销售月份',
  `gb_dfs_full_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '完整日期',
  `gb_dfs_user_id` int DEFAULT NULL COMMENT '用户id',
  `gb_dfs_year` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '销售年份',
  `gb_dfs_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '小计金额',
  `gb_dfs_distributer_id` int DEFAULT NULL COMMENT '配送商id',
  PRIMARY KEY (`gb_dep_food_sales_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;


-- ===========================
-- gb_dep_dis_goods_settle 表结构规范化（门店配送商品结算）
-- ===========================

DROP TABLE IF EXISTS `gb_dep_dis_goods_settle`;

CREATE TABLE `gb_dep_dis_goods_settle` (
  `gb_dep_dis_goods_settle_id` int NOT NULL AUTO_INCREMENT COMMENT '结算id',
  `gb_ddgs_distributer_id` int DEFAULT NULL COMMENT '配送商id',
  `gb_ddgs_dis_goods_id` int DEFAULT NULL COMMENT '配送商品id',
  `gb_ddgs_dfg_goods_father_id` int DEFAULT NULL COMMENT '父类商品id',
  `gb_ddgs_dis_goods_name` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '商品名称',
  `gb_ddgs_dis_goods_standardname` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '商品规格',
  `gb_ddgs_dis_goods_standard_weight` varchar(100) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '标准重量',
  `gb_ddgs_dis_goods_type` tinyint DEFAULT NULL COMMENT '商品类型',
  `gb_ddgs_dis_goods_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '商品价格',
  `gb_ddgs_dis_goods_lowest_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '最低价格',
  `gb_ddgs_dis_goods_highest_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '最高价格',
  `gb_ddgs_dis_control_price` tinyint DEFAULT NULL COMMENT '是否控制价格',
  `gb_ddgs_dis_control_fresh` tinyint DEFAULT NULL COMMENT '是否控制鲜度',
  `gb_ddgs_dis_fresh_warn_hour` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '鲜度报警小时',
  `gb_ddgs_dis_fresh_waste_hour` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '废弃报警小时',
  `gb_ddgs_dis_goods_inventory_type` tinyint DEFAULT NULL COMMENT '盘库方式',
  `gb_ddgs_dis_goods_inventory_dep_id` int DEFAULT NULL COMMENT '盘库门店id',
  `gb_ddgs_status` tinyint DEFAULT NULL COMMENT '状态',
  `gb_ddgs_settle_department_id` int DEFAULT NULL COMMENT '结算门店id',
  `gb_ddgs_settle_department_father_id` int DEFAULT NULL COMMENT '结算门店父id',
  `gb_ddgs_settle_amount` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '结算数量',
  `gb_ddgs_settle_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '结算小计',
  `gb_ddgs_settle_id` int DEFAULT NULL COMMENT '结算单id',
  `gb_ddgs_settle_month` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '结算月份',
  `gb_ddgs_settle_year` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '结算年份',
  `gb_ddgs_sales_amount` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '销售数量',
  `gb_ddgs_sales_subtotal` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '销售小计',
  PRIMARY KEY (`gb_dep_dis_goods_settle_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf16 COLLATE=utf16_czech_ci;
