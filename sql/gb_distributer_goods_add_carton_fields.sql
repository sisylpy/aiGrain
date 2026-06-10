-- 为 gb_distributer_goods 添加外箱字段
ALTER TABLE `gb_distributer_goods`
    ADD COLUMN `gb_dg_carton_unit` varchar(20) DEFAULT NULL COMMENT '外箱名称' AFTER `gb_dg_quantity_days`,
    ADD COLUMN `gb_dg_items_per_carton` varchar(20) DEFAULT NULL COMMENT '外箱装数量' AFTER `gb_dg_carton_unit`;
