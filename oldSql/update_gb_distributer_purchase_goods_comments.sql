-- 更新 gb_distributer_purchase_goods 表字段注释
-- 根据实体类 GbDistributerPurchaseGoodsEntity 补充注释

USE ai_marketing;

-- 添加缺失的字段注释
ALTER TABLE `gb_distributer_purchase_goods` 
    MODIFY COLUMN `gb_DPG_dis_goods_grand_id` int DEFAULT NULL COMMENT '采购祖父级商品id',
    MODIFY COLUMN `gb_DPG_dis_goods_great_id` int DEFAULT NULL COMMENT '采购曾祖父级商品id',
    MODIFY COLUMN `gb_DPG_purchase_nx_distributer_id` int DEFAULT NULL COMMENT '采购Nx批发商id',
    MODIFY COLUMN `gb_DPG_purchase_nx_supplier_id` int DEFAULT NULL COMMENT '采购Nx供应商id',
    MODIFY COLUMN `gb_DPG_apply_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '申请日期',
    MODIFY COLUMN `gb_DPG_batch_id` int DEFAULT NULL COMMENT '采购批次号',
    MODIFY COLUMN `gb_DPG_pur_user_id` int DEFAULT NULL COMMENT '采购员id',
    MODIFY COLUMN `gb_DPG_buy_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '采购单价',
    MODIFY COLUMN `gb_DPG_buy_quantity` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '采购数量',
    MODIFY COLUMN `gb_DPG_dis_goods_price_id` int DEFAULT NULL COMMENT '采购商品价格表id',
    MODIFY COLUMN `gb_DPG_is_check` tinyint DEFAULT NULL COMMENT '是否检查',
    MODIFY COLUMN `gb_DPG_type_add_user_id` int DEFAULT NULL COMMENT '添加采购用户id',
    MODIFY COLUMN `gb_DPG_input_type` tinyint DEFAULT NULL COMMENT '输入类型',
    MODIFY COLUMN `gb_DPG_pay_type` tinyint DEFAULT NULL COMMENT '支付方式',
    MODIFY COLUMN `gb_DPG_buy_subtotal` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '采购小计',
    MODIFY COLUMN `gb_DPG_purchase_department_id` int DEFAULT NULL COMMENT '库房或中央厨房采购部门id',
    MODIFY COLUMN `gb_DPG_purchase_month` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '采购月份',
    MODIFY COLUMN `gb_DPG_purchase_year` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '采购年份',
    MODIFY COLUMN `gb_DPG_purchase_full_time` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '采购完整时间',
    MODIFY COLUMN `gb_DPG_purchase_week` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '采购周',
    MODIFY COLUMN `gb_DPG_purchase_week_year` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '采购周年份',
    MODIFY COLUMN `gb_DPG_buy_scale_price` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订货单位价格',
    MODIFY COLUMN `gb_DPG_buy_scale_quantity` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订货单位数量',
    MODIFY COLUMN `gb_DPG_buy_scale` varchar(10) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '订货单位系数',
    MODIFY COLUMN `gb_DPG_buy_price_reason` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '采购单价异常原因',
    MODIFY COLUMN `gb_DPG_weight_id` int DEFAULT NULL COMMENT '称重id',
    MODIFY COLUMN `gb_DPG_orders_finish_amount` int DEFAULT NULL COMMENT '订单完成数量',
    MODIFY COLUMN `gb_DPG_orders_bill_amount` int DEFAULT NULL COMMENT '订单账单数量',
    MODIFY COLUMN `gb_DPG_orders_weight_amount` int DEFAULT NULL COMMENT '订单称重数量',
    MODIFY COLUMN `gb_DPG_supplier_finish_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '供应商完成日期',
    MODIFY COLUMN `gb_DPG_stock_finish_date` varchar(20) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '库存完成日期';

-- 修正不准确的注释
ALTER TABLE `gb_distributer_purchase_goods` 
    MODIFY COLUMN `gb_DPG_pur_user_id` int DEFAULT NULL COMMENT '采购员id',
    MODIFY COLUMN `gb_DPG_waste_full_time` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '废弃时间',
    MODIFY COLUMN `gb_DPG_warn_full_time` varchar(200) CHARACTER SET utf16 COLLATE utf16_czech_ci DEFAULT NULL COMMENT '报警时间';
